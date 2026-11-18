package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstallerCliInstallTerminalTest {
    private static final String MANIFEST = "a".repeat(64);
    private static final String BEFORE = "12345678-1234-1234-1234-123456789abc";
    private static final String FIRST = "22345678-1234-1234-1234-123456789abc";
    private static final String SECOND = "32345678-1234-1234-1234-123456789abc";
    private static final String FAILURE =
            "Install failed: installed content identity is unavailable";

    private static final class Observer implements InstallMonitor.Observer {
        @Override
        public InstallMonitor.Boot verifyBoot(int count, String previous,
                CliProcess.Control control) {
            return new InstallMonitor.Boot(count == 1 ? FIRST : SECOND, count);
        }

        @Override
        public InstallMonitor.Boot currentBoot(CliProcess.Control control) {
            return new InstallMonitor.Boot(FIRST, 1);
        }

        @Override
        public String check(JsonObject request, CliProcess.Control control) {
            JsonObject receipt = request.deepCopy();
            receipt.remove("action");
            receipt.addProperty("schemaVersion", 1);
            receipt.addProperty("state", request.get("decision").getAsString().equals("status")
                    ? "waiting" : "accepted");
            receipt.addProperty("expiresMonotonicMs", "1000");
            return receipt.toString();
        }
    }

    private static CliProcess.Control control() {
        return new CliProcess.Control(System.nanoTime() + Duration.ofSeconds(5).toNanos(), null);
    }

    private static String arm() {
        return "install-acceptance-armed {\"schemaVersion\":1,\"transactionId\":\""
                + MANIFEST + "\",\"manifestSha256\":\"" + MANIFEST
                + "\",\"attemptToken\":\"" + "c".repeat(64) + "\"}";
    }

    private static String complete(String manifest) {
        return "Install completed: transaction " + manifest;
    }

    private static String log(int sequence, String level, String message) {
        JsonObject row = new JsonObject();
        row.addProperty("schemaVersion", 1);
        row.addProperty("sequence", sequence);
        row.addProperty("timestamp", "2026-10-29T20:25:20.816Z");
        JsonObject source = new JsonObject();
        source.addProperty("kind", "installer");
        source.addProperty("id", "desktop");
        row.add("source", source);
        row.addProperty("level", level);
        row.addProperty("component", "installer");
        row.addProperty("message", message);
        row.add("fields", new JsonObject());
        return row.toString();
    }

    private static String reboot(int count) {
        return log(count, "info", "Restarting the head unit (reboot " + count
                + "). Waiting for reconnection; install will continue automatically.");
    }

    private static InstallMonitor accepted() throws Exception {
        InstallMonitor monitor = new InstallMonitor(MANIFEST, BEFORE, new Observer());
        monitor.line(CliProcess.Stream.OUT, arm(), control());
        monitor.line(CliProcess.Stream.ERR, reboot(1), control());
        monitor.line(CliProcess.Stream.ERR, reboot(2), control());
        return monitor;
    }

    @Test
    void acceptedTwoBootsAndZeroExitStillRequireTheExactTerminalReceipt() throws Exception {
        InstallMonitor monitor = accepted();
        assertThrows(IOException.class, () -> monitor.requireComplete(0));
        monitor.line(CliProcess.Stream.OUT, complete(MANIFEST), control());
        monitor.requireComplete(0);
        assertThrows(IOException.class, () -> monitor.requireComplete(1));
    }

    @Test
    void foreignAndDuplicateTerminalReceiptsCannotCompleteAnotherTransaction() throws Exception {
        InstallMonitor foreign = accepted();
        assertThrows(IOException.class, () -> foreign.line(CliProcess.Stream.OUT,
                complete("b".repeat(64)), control()));
        InstallMonitor duplicate = accepted();
        duplicate.line(CliProcess.Stream.OUT, complete(MANIFEST), control());
        assertThrows(IOException.class, () -> duplicate.line(CliProcess.Stream.OUT,
                complete(MANIFEST), control()));
    }

    @Test
    void lateIdentityFailureRemainsFailureEvenAfterAReceiptAndZeroExit() throws Exception {
        InstallMonitor monitor = accepted();
        monitor.line(CliProcess.Stream.OUT, complete(MANIFEST), control());
        monitor.line(CliProcess.Stream.ERR, log(3, "error", FAILURE), control());
        IOException error = assertThrows(IOException.class, () -> monitor.requireComplete(0));
        assertTrue(error.getMessage().contains("installed content identity is unavailable"));
    }

    @Test
    void plainCliFailureSurfaceRetainsTheIdentityDiagnostic() throws Exception {
        InstallMonitor monitor = accepted();
        monitor.line(CliProcess.Stream.ERR, FAILURE, control());
        IOException error = assertThrows(IOException.class, () -> monitor.requireComplete(1));
        assertTrue(error.getMessage().contains("installed content identity is unavailable"));
    }

    @Test
    void receiptMayPrecedeRebootDiagnosticsDrainingFromTheOtherStream() throws Exception {
        InstallMonitor monitor = new InstallMonitor(MANIFEST, BEFORE, new Observer());
        monitor.line(CliProcess.Stream.OUT, arm(), control());
        monitor.line(CliProcess.Stream.OUT, complete(MANIFEST), control());
        assertThrows(IOException.class, () -> monitor.requireComplete(0));
        monitor.line(CliProcess.Stream.ERR, reboot(1), control());
        monitor.line(CliProcess.Stream.ERR, reboot(2), control());
        monitor.requireComplete(0);
    }

    @Test
    void realProcessLateFailureCannotBePromotedToSuccessByItsExitCode(@TempDir Path work)
            throws Exception {
        for (int exit : List.of(0, 1)) {
            Path run = ru.voboost.tests.RunFiles.directory(work.resolve("exit-" + exit));
            Path stdout = Files.writeString(run.resolve("source-out"), arm() + "\n"
                    + complete(MANIFEST) + "\n");
            Path stderr = Files.writeString(run.resolve("source-err"), reboot(1) + "\n"
                    + reboot(2) + "\n" + log(3, "error", FAILURE) + "\n");
            InstallMonitor monitor = new InstallMonitor(MANIFEST, BEFORE, new Observer());
            var result = CliProcess.run(List.of("/bin/sh", "-c",
                    "cat \"$1\"; cat \"$2\" >&2; exit \"$3\"", "install-fixture",
                    stdout.toString(), stderr.toString(), Integer.toString(exit)),
                    run.resolve("out"), run.resolve("err"), Duration.ofSeconds(5), monitor);
            assertEquals(exit, result.exit());
            assertThrows(IOException.class, () -> monitor.requireComplete(result.exit()));
            assertEquals(Files.readString(stderr), Files.readString(result.err()));
        }
    }
}
