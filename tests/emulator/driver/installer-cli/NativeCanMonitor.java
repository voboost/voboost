package ru.voboost.tests.installer.cli;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/** Owns one continuous, boot-fenced native RX window and its captured pages. */
public final class NativeCanMonitor implements AutoCloseable {
    @FunctionalInterface public interface Observer { JsonObject read(String after) throws Exception; }
    private static final long PERIOD_MS = 1000, MAX_SILENCE_MS = 5000;
    private final Observer observer;
    private final String boot;
    private final Path work;
    private final AtomicLong requests = new AtomicLong();
    private final Thread worker;
    private NativeCanJournal.Page page;
    private Throwable failure;
    private boolean closing, window, offSeen, everMode5, returning, returned;
    private int lastMode = -1;
    private BigInteger lastModeTime;
    private String bootstrapWriter;
    private BigInteger bootstrapEpoch = BigInteger.ZERO, unavailableEpoch = BigInteger.ZERO;
    private long observedAt, readStartedAt;

    public NativeCanMonitor(Observer observer, String expectedBoot, Path work) throws Exception {
        this.observer = java.util.Objects.requireNonNull(observer);
        this.boot = java.util.Objects.requireNonNull(expectedBoot);
        this.work = java.util.Objects.requireNonNull(work);
        Files.createDirectories(work);
        try (var files = Files.list(work)) {
            if (files.findAny().isPresent()) throw new IOException("native CAN evidence directory must be fresh");
        }
        long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        while (!poll(true)) {
            if (System.nanoTime() >= deadline) throw new IOException("native CAN bootstrap did not reach terminal EAGAIN");
            Thread.sleep(250);
        }
        worker = new Thread(this::run, "native-can-monitor");
        worker.setDaemon(true);
        worker.start();
    }
    private boolean poll(boolean initial) throws Exception {
        BigInteger cursor;
        synchronized (this) { cursor = initial ? BigInteger.ZERO : page.latest(); }
        long started = System.nanoTime();
        JsonObject raw = observer.read(cursor.toString());
        long ended = System.nanoTime();
        if (ended - started > Duration.ofMillis(MAX_SILENCE_MS).toNanos()) throw new IOException("native CAN read exceeded coverage budget");
        Path capture = work.resolve(String.format("native-can-%08d.json", requests.incrementAndGet()));
        Files.writeString(capture, raw.toString() + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        if (initial) {
            boolean ready = NativeCanJournal.bootstrap(raw, boot);
            String writer = raw.get("writerId").getAsString();
            BigInteger epoch = new BigInteger(raw.get("receiverEpoch").getAsString());
            if (bootstrapWriter != null && !bootstrapWriter.equals(writer) || epoch.compareTo(bootstrapEpoch) < 0)
                throw new IOException("native CAN bootstrap identity changed");
            bootstrapWriter = writer;
            bootstrapEpoch = epoch;
            if (raw.get("status").getAsString().equals("unavailable") && epoch.signum() > 0) unavailableEpoch = epoch;
            if (!ready) return false;
            if (unavailableEpoch.signum() > 0 && epoch.compareTo(unavailableEpoch) <= 0)
                throw new IOException("native CAN unavailable receiver did not reopen");
        }
        NativeCanJournal.Page next = NativeCanJournal.read(raw, cursor, boot, initial);
        synchronized (this) {
            if (!initial) {
                if (!page.writer().equals(next.writer()) || !page.epoch().equals(next.epoch())
                        || !page.errors().equals(next.errors()) || next.dropped().compareTo(page.dropped()) < 0
                        || next.discarded().compareTo(page.discarded()) < 0 || next.sampled().compareTo(page.sampled()) < 0)
                    throw new IOException("native CAN lifetime, counters or clock changed");
                long elapsed = Duration.ofNanos(ended - observedAt).toMillis();
                BigInteger advance = next.sampled().subtract(page.sampled());
                if (elapsed > MAX_SILENCE_MS || advance.signum() <= 0
                        || advance.compareTo(BigInteger.valueOf(elapsed + 2000)) > 0)
                    throw new IOException("native CAN stale or discontinuous boot clock");
            }
            for (JsonElement entry : next.records()) {
                String frame = entry.getAsJsonObject().get("frameHex").getAsString();
                BigInteger received = new BigInteger(entry.getAsJsonObject().get("receiveBootMs").getAsString());
                if (!initial && received.compareTo(page.sampled()) < 0)
                    throw new IOException("native CAN record predates preceding terminal watermark");
                if (frame.startsWith("9a08") && frame.length() == 20) {
                    int mode = ((Integer.parseInt(frame.substring(16, 18), 16) >> 2) & 7) + 1;
                    if (mode == 5) everMode5 = true;
                    if (returning && mode == 2) throw new IOException("ordinary Electric observed before Forced EV on return");
                    if (returning && mode == 5) returned = true;
                    if (window && mode == 8) throw new IOException("native CAN reserved VCU mode");
                    if (window && lastModeTime != null
                            && received.subtract(lastModeTime).compareTo(BigInteger.valueOf(2000)) > 0)
                        throw new IOException("native CAN VCU feedback gap");
                    if (window && offSeen && mode == 5) throw new IOException("native CAN ON after OFF");
                    if (window && mode == 2) offSeen = true;
                    lastMode = mode;
                    lastModeTime = received;
                }
            }
            if (window && (lastModeTime == null
                    || next.sampled().subtract(lastModeTime).compareTo(BigInteger.valueOf(2000)) > 0))
                throw new IOException("native CAN VCU feedback stale");
            page = next;
            observedAt = ended;
            readStartedAt = started;
            notifyAll();
        }
        return true;
    }
    private void run() {
        try {
            while (true) {
                synchronized (this) {
                    if (closing) return;
                    wait(PERIOD_MS);
                    if (closing) return;
                }
                poll(false);
            }
        } catch (Throwable e) {
            synchronized (this) { if (!closing || !(e instanceof InterruptedException)) failure = e; notifyAll(); }
        }
    }
    private void healthy() throws IOException {
        if (failure != null) throw new IOException("native CAN observer failed", failure);
        if (closing || !worker.isAlive()) throw new IOException("native CAN observer stopped");
        if (System.nanoTime() - observedAt > Duration.ofMillis(MAX_SILENCE_MS).toNanos()) throw new IOException("native CAN observation stale");
    }
    public synchronized void beginOff() throws IOException {
        healthy();
        if (window || lastMode != 5 || lastModeTime == null
                || page.sampled().subtract(lastModeTime).compareTo(BigInteger.valueOf(2000)) > 0) throw new IOException("native CAN window needs a prior RX ON baseline");
        window = true;
    }
    public synchronized void beginReturn() throws IOException {
        healthy();
        if (returning || lastMode != 4 || lastModeTime == null)
            throw new IOException("native CAN return requires Save mode");
        returning = true;
        returned = false;
    }

    public synchronized void endReturn() throws Exception {
        if (!returning) throw new IOException("native CAN return window not started");
        long end = System.nanoTime(), deadline = end + Duration.ofSeconds(10).toNanos();
        while (true) {
            healthy();
            if (returned && lastMode == 5 && readStartedAt > end) {
                returning = false;
                return;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new IOException("native CAN direct Forced EV return missing");
            wait(Math.max(1, Math.min(1000, Duration.ofNanos(remaining).toMillis())));
        }
    }

    private static BigInteger counter(JsonObject witness, String name) throws IOException {
        var value = witness.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || !value.getAsString().matches("0|[1-9][0-9]{0,19}")) throw new IOException("invalid boundary counter " + name);
        BigInteger result = new BigInteger(value.getAsString());
        if (result.bitLength() > 64) throw new IOException("boundary counter overflow " + name);
        return result;
    }
    private static String witnessText(JsonObject witness, String name) throws IOException {
        var value = witness.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isEmpty())
            throw new IOException("invalid boundary string " + name);
        return value.getAsString();
    }
    private static boolean witnessBool(JsonObject witness, String name) throws IOException {
        var value = witness.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new IOException("invalid boundary boolean " + name);
        return value.getAsBoolean();
    }
    private static int witnessInt(JsonObject witness, String name) throws IOException {
        var value = witness.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("0|[1-9][0-9]{0,9}")) throw new IOException("invalid boundary number " + name);
        try { return Integer.parseInt(value.getAsString()); } catch (NumberFormatException e) { throw new IOException("boundary number overflow", e); }
    }
    private static void boundary(JsonObject witness, String boot, boolean cold) throws IOException {
        if (witness == null || !witness.keySet().equals(java.util.Set.of("schema", "producer", "status", "boot_uuid", "first_open", "valid", "all_writers_quiesced", "stage", "mode", "first_mode", "session", "epoch", "nonce", "kernel_open_seq", "commit_seq", "on_transition_count", "first_commit_boottime_ns", "fence_seq", "fence_boottime_ns", "expires_boottime_ns", "capture_boottime_ns", "raw_admission_seq", "raw_queued", "raw_inflight_seq", "loss_count", "generated_rx_seq", "fence_generated_rx_seq", "semantic_queue", "capability", "session_id", "owned_android_pid", "guest_boot_id")))
            throw new IOException("closed ECU boundary shape differs");
        if (boot == null || !boot.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
            throw new IOException("closed ECU boot UUID invalid");
        if (witnessInt(witness, "schema") != 1 || witnessInt(witness, "status") != 0 || witnessInt(witness, "mode") < 1 || witnessInt(witness, "mode") > 5 || cold && witnessInt(witness, "mode") != 2
                || !witnessText(witness, "producer").equals("closed_ecu_boundary") || !witnessText(witness, "capability").equals("closed_ecu_boundary_v1")
                || !witnessText(witness, "boot_uuid").equals(boot) || !witnessText(witness, "guest_boot_id").equals(boot)
                || witnessText(witness, "session_id").isEmpty() || witnessInt(witness, "owned_android_pid") < 1
                || !witnessBool(witness, "valid") || !witnessText(witness, "stage").equals("none") || witnessBool(witness, "all_writers_quiesced"))
            throw new IOException("closed ECU boundary identity or mode differs");
        boolean firstOpen = witnessBool(witness, "first_open");
        int firstMode = witnessInt(witness, "first_mode");
        if (firstMode < 1 || firstMode > 5) throw new IOException("closed ECU first mode invalid");
        for (String key : java.util.List.of("session", "epoch", "nonce", "kernel_open_seq", "commit_seq", "on_transition_count", "first_commit_boottime_ns", "fence_seq", "fence_boottime_ns", "expires_boottime_ns", "capture_boottime_ns", "raw_admission_seq", "raw_queued", "raw_inflight_seq", "loss_count", "generated_rx_seq", "fence_generated_rx_seq", "semantic_queue")) counter(witness, key);
        for (String key : java.util.List.of("session", "epoch", "kernel_open_seq", "commit_seq", "first_commit_boottime_ns", "capture_boottime_ns"))
            if (counter(witness, key).signum() == 0) throw new IOException("closed ECU missing positive " + key);
        if (counter(witness, "loss_count").signum() != 0
                || counter(witness, "raw_queued").compareTo(BigInteger.valueOf(32)) > 0
                || counter(witness, "first_commit_boottime_ns").compareTo(counter(witness, "capture_boottime_ns")) > 0
                || counter(witness, "raw_inflight_seq").compareTo(counter(witness, "raw_admission_seq")) > 0
                || counter(witness, "fence_generated_rx_seq").compareTo(counter(witness, "generated_rx_seq")) > 0
                || cold && (!firstOpen || !counter(witness, "kernel_open_seq").equals(BigInteger.ONE) || firstMode != 2 || counter(witness, "on_transition_count").signum() != 0))
            throw new IOException("closed ECU loss, chronology or cold history differs");
    }
    public static void requireClosedEcuSource(JsonObject witness, String boot) throws IOException {
        boundary(witness, boot, false);
    }
    private void awaitAfterWitness(JsonObject witness) throws Exception {
        long start = System.nanoTime(), deadline = start + Duration.ofSeconds(10).toNanos();
        BigInteger minimum = counter(witness, "capture_boottime_ns").divide(BigInteger.valueOf(1000000));
        while (true) {
            healthy();
            if (readStartedAt > start && page.sampled().compareTo(minimum) > 0) return;
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new IOException("native CAN terminal after witness missing");
            wait(Math.max(1, Math.min(1000, Duration.ofNanos(remaining).toMillis())));
        }
    }
    public synchronized void beginOffAfterCold(JsonObject witness) throws Exception {
        boundary(witness, boot, true);
        beginOffAfterBoundary(witness);
    }

    public synchronized void beginOffAfterWarm(JsonObject before, JsonObject after) throws Exception {
        requireWarmOffBoundary(before, after, boot);
        beginOffAfterBoundary(after);
    }

    private void beginOffAfterBoundary(JsonObject witness) throws Exception {
        healthy();
        if (window || everMode5 || lastMode != 2 || lastModeTime == null
                || page.sampled().subtract(lastModeTime).compareTo(BigInteger.valueOf(2000)) > 0)
            throw new IOException("native CAN OFF baseline unavailable after restart");
        window = true;
        offSeen = true;
        awaitAfterWitness(witness);
        if (lastMode != 2 || lastModeTime == null
                || page.sampled().subtract(lastModeTime).compareTo(BigInteger.valueOf(2000)) > 0)
            throw new IOException("native CAN OFF is not fresh after witness");
    }

    static void requireWarmOffBoundary(JsonObject before, JsonObject after, String boot) throws IOException {
        boundary(before, boot, false);
        boundary(after, boot, false);
        if (witnessInt(before, "mode") != 2 || witnessInt(after, "mode") != 2)
            throw new IOException("closed ECU warm witness is not OFF");
        for (String key : java.util.List.of("session", "kernel_open_seq", "on_transition_count",
                "first_commit_boottime_ns", "first_mode", "first_open", "session_id", "owned_android_pid"))
            if (!before.get(key).equals(after.get(key)))
                throw new IOException("closed ECU identity or ON count changed: " + key);
        if (!counter(after, "epoch").equals(counter(before, "epoch").add(BigInteger.ONE))
                || counter(after, "capture_boottime_ns").compareTo(counter(before, "capture_boottime_ns")) <= 0
                || counter(after, "commit_seq").compareTo(counter(before, "commit_seq")) <= 0
                || counter(after, "generated_rx_seq").compareTo(counter(before, "generated_rx_seq")) < 0
                || counter(after, "raw_admission_seq").compareTo(counter(before, "raw_admission_seq")) < 0)
            throw new IOException("closed ECU warm epoch or counters differ");
    }

    /** Requires a fresh terminal watermark after the independent public CAN observation. */
    public synchronized void requireOff(long minimumBootMs) throws Exception {
        if (minimumBootMs < 0 || !window) throw new IOException("native CAN OFF window not started");
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (true) {
            healthy();
            if (offSeen && page.sampled().compareTo(BigInteger.valueOf(minimumBootMs)) > 0) return;
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new IOException("native CAN OFF or fresh terminal watermark missing");
            wait(Math.max(1, Math.min(1000, Duration.ofNanos(remaining).toMillis())));
        }
    }
    /** Closes only after a new terminal read begins after the caller ended the window. */
    public synchronized void endOff(long minimumBootMs) throws Exception {
        if (minimumBootMs < 0 || !window) throw new IOException("native CAN OFF window not started");
        long end = System.nanoTime(), deadline = end + Duration.ofSeconds(10).toNanos();
        while (true) {
            healthy();
            if (offSeen && readStartedAt > end && page.sampled().compareTo(BigInteger.valueOf(minimumBootMs)) > 0) {
                window = false;
                return;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new IOException("native CAN final terminal watermark missing");
            wait(Math.max(1, Math.min(1000, Duration.ofNanos(remaining).toMillis())));
        }
    }
    @Override public void close() throws Exception {
        synchronized (this) { closing = true; notifyAll(); }
        worker.join(5000);
        if (worker.isAlive()) throw new IOException("native CAN observer termination unconfirmed");
        if (failure != null) throw new IOException("native CAN observer failed", failure);
        if (window || returning) throw new IOException("native CAN window was not ended with a final terminal read");
    }
}
