package voyemu.journal;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class JournalTest {
    /** Per-child wall-clock budget for every concurrent-JVM probe; all waits are bounded by it. */
    static final long CHILD_BUDGET_MILLIS = 10_000;
    /** Shorter budget for cases where the child is known to misbehave; keeps the suite bounded and fast. */
    static final long MISBEHAVED_CHILD_BUDGET_MILLIS = 3_000;
    @TempDir Path testRoot;
    @Test void hostJournal() throws Exception { main(new String[] { testRoot.toString() }); }
    interface Action { void run() throws Exception; }
    static void fails(Action a) throws Exception { try { a.run(); throw new AssertionError("expected failure"); } catch (IOException expected) {} }
    static void check(boolean v) { if (!v) throw new AssertionError(); }

    // Child JVM modes used by the bounded concurrency probes; "locked" holds a real Journal lock.
    static void child(String mode, Path dir) throws Exception {
        switch (mode) {
            case "silent" -> Thread.sleep(CHILD_BUDGET_MILLIS * 10);
            case "garbage" -> { System.out.println("SURPRISE"); System.out.flush(); Thread.sleep(CHILD_BUDGET_MILLIS * 10); }
            case "exit" -> { return; }
            default -> { try (Journal j = Journal.open(dir)) { System.out.println("LOCKED"); System.out.flush(); System.in.read(); } return; }
        }
    }

    static Process spawn(Path root, String mode, String dir) throws Exception {
        String cp = Path.of(JournalTest.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            + java.io.File.pathSeparator
            + Path.of(Journal.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path journalDir = root.resolve(dir);
        Files.createDirectories(journalDir);
        return new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-cp", cp, JournalTest.class.getName(),
            root.toString(), mode, dir).redirectErrorStream(true).start();
    }

    /**
     * Await the LOCKED line without ever blocking the reader thread: drain only ready bytes so a
     * silent child cannot hang readLine and a chatty child cannot fill the pipe. JVM tooling banners
     * (JAVA_TOOL_OPTIONS, _JAVA_OPTIONS, JDK_JAVA_OPTIONS) are tolerated; anything else fails fast.
     */
    static void awaitLocked(Process p, long budgetMillis) throws Exception {
        InputStream in = p.getInputStream();
        ByteArrayOutputStream collected = new ByteArrayOutputStream();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMillis);
        while (System.nanoTime() < deadline) {
            for (int n = in.available(); n > 0; n--) {
                int b = in.read();
                if (b < 0) break;
                collected.write(b);
            }
            String text = collected.toString(StandardCharsets.UTF_8);
            for (int start = 0, nl; (nl = text.indexOf('\n', start)) >= 0; start = nl + 1) {
                String line = text.substring(start, nl);
                if (line.equals("LOCKED")) return;
                if (!line.contains("Picked up ")) throw new AssertionError("unexpected child output: " + line);
            }
            if (!p.isAlive() && in.available() == 0) throw new AssertionError("child exited without LOCKED");
            Thread.sleep(10);
        }
        throw new AssertionError("child did not print LOCKED within " + budgetMillis + " ms budget");
    }

    /** Terminate a child and prove it is gone inside the same bounded budget; never leaves processes behind. */
    static void stop(Process p) throws Exception {
        p.destroyForcibly();
        if (!p.waitFor(CHILD_BUDGET_MILLIS, TimeUnit.MILLISECONDS)) throw new AssertionError("child process did not stop");
    }

    /** Run a misbehaving child, require awaitLocked to fail within the bounded budget, and clean it up. */
    static void misbehaved(String mode, Path root, String dir) throws Exception {
        Process p = spawn(root, mode, dir);
        boolean failed = false;
        try { awaitLocked(p, MISBEHAVED_CHILD_BUDGET_MILLIS); }
        catch (AssertionError expected) { failed = true; }
        finally { stop(p); }
        if (!failed) throw new AssertionError("expected " + mode + " child to fail");
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args[0]); Files.createDirectories(root);
        if (args.length > 1) { child(args[1], root.resolve(args[2])); return; }
        Path normal = Files.createDirectory(root.resolve("normal"));
        byte[] request = {0, 1, -1, 42};
        try (Journal j = Journal.open(normal)) {
            j.reserve("scope", "machine", "id", request);
            fails(() -> j.reserve("scope", "machine", "id", request));
            fails(() -> Journal.open(normal));
        }
        try (Journal j = Journal.open(normal)) {
            Journal.Record r = j.inspect("scope", "machine");
            check(r.requestId.equals("id") && Arrays.equals(r.request(), request));
            fails(() -> j.inspect("scope", "other"));
            fails(() -> j.reserve("scope", "machine", "id", request));
        }
        Path malformed = Files.createDirectory(root.resolve("malformed"));
        try (Journal j = Journal.open(malformed)) {
            fails(() -> j.reserve("\uD800", "machine", "id", request));
            fails(() -> j.reserve("scope", "\uD800", "id", request));
            fails(() -> j.reserve("scope", "machine", "\uD800", request));
        }
        Path invalid = Files.createDirectory(root.resolve("invalid-utf8"));
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(invalid.resolve("reservation")))) {
            out.writeInt(0x564a3031); out.writeInt(1); out.writeByte(0xff);
            out.writeInt(7); out.writeBytes("machine"); out.writeInt(2); out.writeBytes("id");
            out.writeInt(0); out.writeInt(~0x564a3031);
        }
        try (Journal j = Journal.open(invalid)) { fails(() -> j.inspect("\uFFFD", "machine")); }
        Path oversized = Files.createDirectory(root.resolve("oversized"));
        try (RandomAccessFile file = new RandomAccessFile(oversized.resolve("reservation").toFile(), "rw")) { file.write(Files.readAllBytes(normal.resolve("reservation"))); file.setLength(1024L * 1024 * 1024); }
        try (Journal j = Journal.open(oversized)) { fails(() -> j.inspect("scope", "machine")); }
        byte[] complete = Files.readAllBytes(normal.resolve("reservation"));
        for (int cut = 0; cut < complete.length; cut++) {
            Path d = Files.createDirectory(root.resolve("cut" + cut));
            Files.write(d.resolve("reservation"), Arrays.copyOf(complete, cut));
            try (Journal j = Journal.open(d)) {
                fails(() -> j.inspect("scope", "machine"));
                fails(() -> j.reserve("scope", "machine", "id", request));
            }
        }
        // A live child holds the lock; reading its LOCKED line and stopping it are both bounded.
        Process p = spawn(root, "locked", "concurrent");
        try {
            awaitLocked(p, CHILD_BUDGET_MILLIS);
            fails(() -> Journal.open(root.resolve("concurrent")));
        } finally { stop(p); }
        try (Journal j = Journal.open(root.resolve("concurrent"))) { j.reserve("scope", "machine", "after-crash", request); }
        try (Journal j = Journal.open(root.resolve("concurrent"))) { fails(() -> j.reserve("scope", "machine", "again", request)); }
        // Chatty children cannot stall the probe on the LOCKED line and cannot fill the pipe.
        misbehaved("garbage", root, "locked-misbehaved");
        // A child that never reports must fail inside the budget, not block readLine forever.
        misbehaved("silent", root, "silent");
        // A child that exits without LOCKED must fail cleanly as an ended stream, not as EOF success.
        misbehaved("exit", root, "exited");
        System.out.println("host tests passed: restart, bounded concurrent process, every truncation, identity and immutable reservation");
    }
}
