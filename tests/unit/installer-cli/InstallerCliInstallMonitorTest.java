package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstallerCliInstallMonitorTest {
    private static final String MANIFEST = "a".repeat(64), TOKEN = "c".repeat(64);
    private static final String BEFORE = "12345678-1234-1234-1234-123456789abc", FIRST = "22345678-1234-1234-1234-123456789abc", SECOND = "32345678-1234-1234-1234-123456789abc";
    private static final String CLEAN = "42345678-1234-1234-1234-123456789abc";
    private static CliProcess.Control control() { return new CliProcess.Control(System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos(), null); }
    private static String arm() { return "install-acceptance-armed {\"schemaVersion\":1,\"transactionId\":\"" + MANIFEST + "\",\"manifestSha256\":\"" + MANIFEST + "\",\"attemptToken\":\"" + TOKEN + "\"}"; }
    private static String reboot(int count) {
        JsonObject row = new JsonObject();
        row.addProperty("schemaVersion", 1); row.addProperty("sequence", count); row.addProperty("timestamp", "2026-10-28T00:00:00.000Z");
        JsonObject source = new JsonObject(); source.addProperty("kind", "installer"); source.addProperty("id", "desktop"); row.add("source", source);
        row.addProperty("level", "info"); row.addProperty("component", "installer");
        row.addProperty("message", "Restarting the head unit (reboot " + count + "). Waiting for reconnection; install will continue automatically.");
        row.add("fields", new JsonObject()); return row.toString();
    }
    private static class Observer implements InstallMonitor.Observer {
        final List<String> decisions = new ArrayList<>();
        boolean fail;
        public InstallMonitor.Boot verifyBoot(int count, String before, CliProcess.Control control) throws Exception {
            if (fail) throw new IOException("UI verification failed");
            return new InstallMonitor.Boot(count == 1 ? FIRST : SECOND, count);
        }
        public InstallMonitor.Boot currentBoot(CliProcess.Control control) { return new InstallMonitor.Boot(FIRST, 1); }
        public String check(JsonObject request, CliProcess.Control control) {
            String decision = request.get("decision").getAsString(); decisions.add(decision);
            JsonObject receipt = request.deepCopy(); receipt.remove("action"); receipt.addProperty("schemaVersion", 1);
            receipt.addProperty("state", decision.equals("status") ? "waiting" : decision.equals("accept") ? "accepted" : "rejected");
            receipt.addProperty("expiresMonotonicMs", "1000"); return receipt.toString();
        }
    }
    @Test void ordinaryInstallNeedsNoSpecialAcceptanceProtocol() throws Exception {
        Observer observer = new Observer(); InstallMonitor monitor = new InstallMonitor(MANIFEST, BEFORE, observer);
        monitor.line(CliProcess.Stream.ERR, reboot(1), control());
        assertTrue(observer.decisions.isEmpty());
        assertThrows(IOException.class, () -> monitor.requireComplete(0));
        monitor.line(CliProcess.Stream.ERR, reboot(2), control());
        monitor.line(CliProcess.Stream.OUT, "Install completed: transaction " + MANIFEST, control());
        monitor.requireComplete(0);
        assertTrue(observer.decisions.isEmpty());
    }
    private static String info(int sequence, String message) {
        JsonObject row = com.google.gson.JsonParser.parseString(reboot(sequence)).getAsJsonObject();
        row.addProperty("message", message);
        return row.toString();
    }
    private static String cleanReceipt(String boot, String qnx) {
        return "clean-install-verified {\"android\":\"absent\",\"bootId\":\"" + boot
                + "\",\"qnx\":\"" + qnx + "\",\"schemaVersion\":1}";
    }
    @Test void reinstallUsesTheFreshCleanBootAsBaselineAndKeepsQnxStatus() throws Exception {
        String transfer = "Sending the verified installation files to the head unit";
        for (String qnx : List.of("verified", "stock-structure", "not-applicable")) {
            Observer observer = new Observer() {
                @Override public InstallMonitor.Boot verifyBoot(int count, String previous, CliProcess.Control control) throws Exception {
                    assertEquals(count == 1 ? CLEAN : FIRST, previous);
                    return super.verifyBoot(count, previous, control);
                }
            };
            InstallMonitor monitor = new InstallMonitor(MANIFEST, BEFORE, observer);
            monitor.line(CliProcess.Stream.ERR, info(1, "Clean install preflight passed; Android absence verified before staging"), control());
            assertThrows(IOException.class, monitor::requireCleanPreflight);
            monitor.line(CliProcess.Stream.ERR, info(2, cleanReceipt(CLEAN, qnx)), control());
            assertThrows(IOException.class, monitor::requireCleanPreflight);
            monitor.line(CliProcess.Stream.ERR, info(3, transfer), control());
            assertEquals(qnx, monitor.requireCleanPreflight().get("qnx").getAsString());
            monitor.line(CliProcess.Stream.ERR, info(4,
                    "Restarting the head unit (reboot 1). Waiting for reconnection; install will continue automatically."), control());
            monitor.line(CliProcess.Stream.ERR, info(5,
                    "Restarting the head unit (reboot 2). Waiting for reconnection; install will continue automatically."), control());
            monitor.line(CliProcess.Stream.OUT, "Install completed: transaction " + MANIFEST, control());
            monitor.requireComplete(0);
        }
    }

    @Test void reinstallRejectsLateMalformedAndStaleCleanReceipts() throws Exception {
        String marker = "Clean install preflight passed; Android absence verified before staging";
        String transfer = "Starting Voboost installation; progress will continue through restarts";
        InstallMonitor late = new InstallMonitor(MANIFEST, BEFORE, new Observer());
        late.line(CliProcess.Stream.ERR, info(1, transfer), control());
        assertThrows(IOException.class, () -> late.line(CliProcess.Stream.ERR, info(2, marker), control()));
        InstallMonitor missing = new InstallMonitor(MANIFEST, BEFORE, new Observer());
        missing.line(CliProcess.Stream.ERR, info(1, marker), control());
        assertThrows(IOException.class, () -> missing.line(CliProcess.Stream.ERR, info(2, transfer), control()));
        for (String invalid : List.of(cleanReceipt("foreign", "verified"), cleanReceipt(CLEAN, "absent"),
                cleanReceipt(CLEAN, "verified").replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"))) {
            InstallMonitor monitor = new InstallMonitor(MANIFEST, BEFORE, new Observer());
            monitor.line(CliProcess.Stream.ERR, info(1, marker), control());
            assertThrows(IOException.class, () -> monitor.line(CliProcess.Stream.ERR, info(2, invalid), control()));
        }
        InstallMonitor stale = new InstallMonitor(MANIFEST, BEFORE, new Observer());
        stale.line(CliProcess.Stream.ERR, info(1, marker), control());
        stale.line(CliProcess.Stream.ERR, info(2, cleanReceipt(BEFORE, "verified")), control());
        stale.line(CliProcess.Stream.ERR, info(3, transfer), control());
        assertThrows(IOException.class, stale::requireCleanPreflight);
    }

    @Test void failedBootVerificationCannotBeAccepted() throws Exception {
        Observer observer = new Observer(); observer.fail = true;
        InstallMonitor monitor = new InstallMonitor(MANIFEST, BEFORE, observer);
        assertThrows(IOException.class, () -> monitor.line(CliProcess.Stream.ERR, reboot(1), control()));
        assertTrue(observer.decisions.isEmpty());
        assertThrows(IOException.class, () -> monitor.requireComplete(0));
    }
    @Test void rejectsDuplicateProgressOrCompletionWithoutBoots() throws Exception {
        InstallMonitor monitor = new InstallMonitor(MANIFEST, BEFORE, new Observer());
        monitor.line(CliProcess.Stream.ERR, reboot(1), control());
        assertThrows(IOException.class, () -> monitor.line(CliProcess.Stream.ERR, reboot(1), control()));
        InstallMonitor unverified = new InstallMonitor(MANIFEST, BEFORE, new Observer());
        unverified.line(CliProcess.Stream.OUT, "Install completed: transaction " + MANIFEST, control());
        assertThrows(IOException.class, () -> unverified.requireComplete(0));
        assertThrows(IOException.class, () -> monitor.requireComplete(1));
    }

    @Test void rejectsCoercedLoggerTypesAndMalformedBoots() throws Exception {
        for (String bad : List.of(
                reboot(1).replace("\"sequence\":1", "\"sequence\":1.5"),
                reboot(1).replace("\"sequence\":1", "\"sequence\":\"1\""),
                reboot(1).replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""),
                reboot(1).replace("2026-10-28T00:00:00.000Z", "badZ"),
                reboot(1).replace("2026-10-28T00:00:00.000Z", "2026-02-30T00:00:00.000Z"),
                reboot(1).replace("\"level\":\"info\"", "\"level\":\"trace\""))) {
            Observer observer = new Observer();
            InstallMonitor monitor = new InstallMonitor(MANIFEST, BEFORE, observer);
            assertThrows(IOException.class, () -> monitor.line(CliProcess.Stream.ERR, bad, control()));
            assertTrue(observer.decisions.isEmpty());
        }
        Observer malformed = new Observer() {
            @Override public InstallMonitor.Boot verifyBoot(int count, String before, CliProcess.Control control) {
                return new InstallMonitor.Boot(count == 1 ? FIRST : "garbage", count);
            }
        };
        InstallMonitor monitor = new InstallMonitor(MANIFEST, BEFORE, malformed); monitor.line(CliProcess.Stream.ERR, reboot(1), control());
        assertThrows(IllegalArgumentException.class, () -> monitor.line(CliProcess.Stream.ERR, reboot(2), control()));
        assertThrows(IOException.class, () -> monitor.requireComplete(0));
    }
    @Test void cancellationDuringBootVerificationNeverStartsFurtherRpcs() throws Exception {
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        Observer blocking = new Observer() {
            @Override public InstallMonitor.Boot verifyBoot(int count, String before, CliProcess.Control control) throws Exception {
                entered.countDown(); Thread.sleep(20000); return new InstallMonitor.Boot(FIRST, 1);
            }
            @Override public InstallMonitor.Boot currentBoot(CliProcess.Control control) { calls.incrementAndGet(); return new InstallMonitor.Boot(FIRST, 1); }
            @Override public String check(JsonObject request, CliProcess.Control control) { calls.incrementAndGet(); return super.check(request, control); }
        };
        InstallMonitor monitor = new InstallMonitor(MANIFEST, BEFORE, blocking);
        CliProcess.Control control = control();
        java.util.concurrent.atomic.AtomicReference<Throwable> error = new java.util.concurrent.atomic.AtomicReference<>();
        Thread worker = new Thread(() -> { try { monitor.line(CliProcess.Stream.ERR, reboot(1), control); } catch (Throwable failure) { error.set(failure); } });
        worker.start(); assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
        control.close(); worker.interrupt(); worker.join(2000);
        assertFalse(worker.isAlive()); assertInstanceOf(InterruptedException.class, error.get()); assertEquals(0, calls.get());
    }
}
