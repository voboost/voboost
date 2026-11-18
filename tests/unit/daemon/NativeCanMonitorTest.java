package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigInteger;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeCanMonitorTest {
    private static final String BOOT = "11111111-1111-1111-1111-111111111111";
    private static JsonObject page(String records, String latest, String sampled) {
        return JsonParser.parseString("{\"schemaVersion\":1,\"producer\":\"actual_kernel_rx\",\"bootId\":\"" + BOOT
                + "\",\"writerId\":\"11111111111111111111111111111111\",\"receiverEpoch\":\"1\",\"status\":\"active\",\"earliestSeq\":\"1\",\"latestSeq\":\"" + latest
                + "\",\"nextCursor\":\"" + latest + "\",\"dropped\":\"0\",\"discarded\":\"0\",\"unsupportedDatagrams\":\"0\",\"errors\":\"0\",\"hasMore\":false,\"gap\":false,\"sampledBootMs\":\"" + sampled + "\",\"records\":[" + records + "]}").getAsJsonObject();
    }
    private static String record(int seq, int ms, int mode) {
        return "{\"seq\":\"" + seq + "\",\"receiveBootMs\":\"" + ms + "\",\"frameHex\":\"9a08000000000000" + String.format("%02x", (mode - 1) << 2) + "00\"}";
    }
    @Test void returnRejectsOrdinaryElectricEvenWhenForcedEvFollows(@TempDir Path dir) throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var monitor = new NativeCanMonitor(cursor -> count.getAndIncrement() == 0
                ? page(record(1, 100, 4), "1", "100")
                : page(record(2, 1100, 2) + "," + record(3, 1101, 5), "3", "1101"), BOOT, dir);
        monitor.beginReturn();
        assertThrows(Exception.class, monitor::endReturn);
        assertThrows(Exception.class, monitor::close);
    }

    @Test void returnAcceptsDirectForcedEvWithFreshTerminal(@TempDir Path dir) throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        try (var monitor = new NativeCanMonitor(cursor -> {
            int n = count.getAndIncrement();
            return n == 0 ? page(record(1, 100, 4), "1", "100")
                    : page(record(n + 1, 100 + n * 1000, 5), Integer.toString(n + 1), Integer.toString(100 + n * 1000));
        }, BOOT, dir)) {
            monitor.beginReturn();
            monitor.endReturn();
        }
    }

    @Test void retainedFirstFramesRemainSeparateFromContinuousCoverage() throws Exception {
        JsonObject p = page(record(1, 10, 5), "1", "11");
        var frames = new com.google.gson.JsonArray();
        JsonObject first = JsonParser.parseString(record(1, 10, 5)).getAsJsonObject();
        first.addProperty("receiverEpoch", "1"); frames.add(first);
        p.add("firstTargetFrames", frames);
        assertEquals(1, NativeCanJournal.read(p, BigInteger.ZERO, BOOT, false).records().size());
        first.addProperty("receiverEpoch", "2");
        assertThrows(Exception.class, () -> NativeCanJournal.read(p, BigInteger.ZERO, BOOT, false));
    }
    @Test void strictShapeOverflowAndContinuity() throws Exception {
        JsonObject p = page(record(1, 10, 5), "1", "11");
        assertEquals(BigInteger.ONE, NativeCanJournal.read(p, BigInteger.ZERO, BOOT, false).latest());
        for (String field : new String[]{"wrongBoot", "overflow", "gap", "terminal", "malformed", "missing"}) {
            JsonObject bad = p.deepCopy();
            switch (field) {
                case "wrongBoot" -> bad.addProperty("bootId", "22222222-2222-2222-2222-222222222222");
                case "overflow" -> bad.addProperty("receiverEpoch", "18446744073709551616");
                case "gap" -> bad.addProperty("gap", true);
                case "terminal" -> bad.addProperty("status", "draining");
                case "malformed" -> bad.getAsJsonArray("records").get(0).getAsJsonObject().addProperty("frameHex", "9a0a00");
                case "missing" -> bad.remove("sampledBootMs");
            }
            assertThrows(Exception.class, () -> NativeCanJournal.read(bad, BigInteger.ZERO, BOOT, false), field);
        }
    }
    @Test void rawOnAfterOffFailsAndWorkerFailureSurfaces(@TempDir Path dir) throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var monitor = new NativeCanMonitor(cursor -> switch (count.getAndIncrement()) {
            case 0 -> page(record(1, 100, 5), "1", "100");
            case 1 -> page(record(2, 1100, 2), "2", "1100");
            default -> page(record(3, 2100, 5), "3", "2100");
        }, BOOT, dir);
        monitor.beginOff();
        monitor.requireOff(101);
        Thread.sleep(1500);
        assertThrows(Exception.class, monitor::close);
    }
    @Test void sequenceGapAndMissingTerminalAreRejected() {
        JsonObject skipped = page(record(2, 10, 5), "2", "11");
        assertThrows(Exception.class, () -> NativeCanJournal.read(skipped, BigInteger.ZERO, BOOT, false));
        JsonObject missing = page("", "1", "11");
        assertThrows(Exception.class, () -> NativeCanJournal.read(missing, BigInteger.ZERO, BOOT, false));
        JsonObject stale = page(record(1, 12, 5), "1", "11");
        assertThrows(Exception.class, () -> NativeCanJournal.read(stale, BigInteger.ZERO, BOOT, false));
    }
    @Test void exceptionFromWorkerFailsClosed(@TempDir Path dir) throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var monitor = new NativeCanMonitor(cursor -> {
            if (count.getAndIncrement() == 0) return page(record(1, 100, 5), "1", "100");
            throw new java.io.IOException("injected observer failure");
        }, BOOT, dir);
        monitor.beginOff();
        assertThrows(Exception.class, () -> monitor.requireOff(101));
        assertThrows(Exception.class, monitor::close);
    }
    @Test void bootstrapWaitsForTerminal(@TempDir Path dir) throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var monitor = new NativeCanMonitor(cursor -> {
            JsonObject p = page(record(1, 100, 5), "1", "100");
            if (count.getAndIncrement() == 0) p.addProperty("status", "draining");
            return p;
        }, BOOT, dir);
        monitor.close();
        assertEquals(2, count.get());
        try (var files = java.nio.file.Files.list(dir)) { assertEquals(2, files.count()); }
    }
    @Test void bootstrapUnavailableRequiresNewEpoch(@TempDir Path dir) throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var monitor = new NativeCanMonitor(cursor -> {
            JsonObject p = page(record(1, 100, 5), "1", "100");
            if (count.getAndIncrement() == 0) p.addProperty("status", "unavailable");
            else p.addProperty("receiverEpoch", "2");
            return p;
        }, BOOT, dir);
        monitor.close();
        assertEquals(2, count.get());
    }
    @Test void bootstrapUnavailableCannotRecoverSameEpoch(@TempDir Path dir) {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        assertThrows(Exception.class, () -> new NativeCanMonitor(cursor -> {
            JsonObject p = page(record(1, 100, 5), "1", "100");
            if (count.getAndIncrement() == 0) p.addProperty("status", "unavailable");
            return p;
        }, BOOT, dir));
    }
    @Test void frozenWatermarkFailsImmediately(@TempDir Path dir) throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var monitor = new NativeCanMonitor(cursor -> count.getAndIncrement() == 0
                ? page(record(1, 100, 5), "1", "100") : page("", "1", "100"), BOOT, dir);
        monitor.beginOff();
        Thread.sleep(1600);
        assertThrows(Exception.class, () -> monitor.requireOff(0));
        assertThrows(Exception.class, monitor::close);
    }
    @Test void unfinishedWindowCannotCloseSuccessfully(@TempDir Path dir) throws Exception {
        var monitor = new NativeCanMonitor(cursor -> page(record(1, 100, 5), "1", "100"), BOOT, dir);
        monitor.beginOff();
        assertThrows(Exception.class, monitor::close);
    }
    @Test void finalTerminalMustIncludeLaterOn(@TempDir Path dir) throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var monitor = new NativeCanMonitor(cursor -> switch (count.getAndIncrement()) {
            case 0 -> page(record(1, 100, 5), "1", "100");
            case 1 -> page(record(2, 1100, 2), "2", "1100");
            default -> page(record(3, 2100, 5), "3", "2100");
        }, BOOT, dir);
        monitor.beginOff();
        monitor.requireOff(101);
        assertThrows(Exception.class, () -> monitor.endOff(101));
        assertThrows(Exception.class, monitor::close);
    }
    @Test void finalTerminalCompletesWindow(@TempDir Path dir) throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var monitor = new NativeCanMonitor(cursor -> switch (count.getAndIncrement()) {
            case 0 -> page(record(1, 100, 5), "1", "100");
            case 1 -> page(record(2, 1100, 2), "2", "1100");
            default -> page("", "2", "2100");
        }, BOOT, dir);
        monitor.beginOff();
        monitor.requireOff(101);
        monitor.endOff(101);
        monitor.close();
        assertTrue(count.get() >= 3);
    }
    @Test void reservedModeMustFailWindow(@TempDir Path dir) throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var monitor = new NativeCanMonitor(cursor -> switch (count.getAndIncrement()) {
            case 0 -> page(record(1, 100, 5), "1", "100");
            case 1 -> page(record(2, 1100, 2), "2", "1100");
            default -> page(record(3, 2100, 8), "3", "2100");
        }, BOOT, dir);
        monitor.beginOff();
        monitor.requireOff(101);
        assertThrows(Exception.class, () -> monitor.endOff(1200));
        assertThrows(Exception.class, monitor::close);
    }
    @Test void missingVcuFailsEvenWhenTerminalAdvances(@TempDir Path dir) throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var monitor = new NativeCanMonitor(cursor -> switch (count.getAndIncrement()) {
            case 0 -> page(record(1, 100, 5), "1", "100");
            case 1 -> page(record(2, 1100, 2), "2", "1100");
            case 2 -> page("", "2", "2100");
            default -> page("", "2", "4101");
        }, BOOT, dir);
        monitor.beginOff();
        monitor.requireOff(101);
        assertThrows(Exception.class, () -> monitor.requireOff(4000));
        assertThrows(Exception.class, monitor::close);
    }
    @Test void vcuGapInsidePageCannotBeHiddenByRecovery(@TempDir Path dir) throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var monitor = new NativeCanMonitor(cursor -> count.getAndIncrement() == 0
                ? page(record(1, 100, 5), "1", "100")
                : page(record(2, 2201, 2) + "," + record(3, 2202, 2), "3", "2202"), BOOT, dir);
        monitor.beginOff();
        assertThrows(Exception.class, () -> monitor.requireOff(101));
        assertThrows(Exception.class, monitor::close);
    }
    @Test void otherRecognizedModesDoNotImplyOn(@TempDir Path dir) throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var monitor = new NativeCanMonitor(cursor -> switch (count.getAndIncrement()) {
            case 0 -> page(record(1, 100, 5), "1", "100");
            case 1 -> page(record(2, 1100, 2), "2", "1100");
            default -> page(record(3, 2100, 3) + "," + record(4, 2100, 4), "4", "2100");
        }, BOOT, dir);
        monitor.beginOff();
        monitor.requireOff(101);
        monitor.endOff(101);
        monitor.close();
    }
    private static JsonObject boundary() {
        return JsonParser.parseString("{\"schema\":1,\"producer\":\"closed_ecu_boundary\",\"status\":0,\"boot_uuid\":\"" + BOOT + "\",\"first_open\":true,\"valid\":true,\"all_writers_quiesced\":false,\"stage\":\"none\",\"mode\":2,\"first_mode\":2,\"session\":\"11\",\"epoch\":\"1\",\"nonce\":\"0\",\"kernel_open_seq\":\"1\",\"commit_seq\":\"4\",\"on_transition_count\":\"0\",\"first_commit_boottime_ns\":\"1000000\",\"fence_seq\":\"0\",\"fence_boottime_ns\":\"0\",\"expires_boottime_ns\":\"0\",\"capture_boottime_ns\":\"100000000\",\"raw_admission_seq\":\"5\",\"raw_queued\":\"0\",\"raw_inflight_seq\":\"0\",\"loss_count\":\"0\",\"generated_rx_seq\":\"6\",\"fence_generated_rx_seq\":\"0\",\"semantic_queue\":\"0\",\"capability\":\"closed_ecu_boundary_v1\",\"session_id\":\"owned\",\"owned_android_pid\":123,\"guest_boot_id\":\"" + BOOT + "\"}").getAsJsonObject();
    }
    @Test void forgedColdWitnessRejected(@TempDir Path dir) throws Exception {
        var monitor = new NativeCanMonitor(cursor -> page(record(1, 100, 2), "1", "100"), BOOT, dir);
        JsonObject bad = boundary(); bad.addProperty("producer", "fabricated");
        try { assertThrows(Exception.class, () -> monitor.beginOffAfterCold(bad)); } finally { monitor.close(); }
    }
    @Test void coldWitnessAndFreshRxOpenWindow(@TempDir Path dir) throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var monitor = new NativeCanMonitor(cursor -> switch (count.getAndIncrement()) {
            case 0 -> page(record(1, 100, 2), "1", "100");
            case 1 -> page(record(2, 1100, 2), "2", "1100");
            default -> page(record(3, 2100, 2), "3", "2100");
        }, BOOT, dir);
        monitor.beginOffAfterCold(boundary());
        monitor.endOff(101);
        monitor.close();
    }
    @Test void coldWitnessRejectsOnSinceBootstrap(@TempDir Path dir) throws Exception {
        var monitor = new NativeCanMonitor(cursor -> page(record(1, 99, 5) + "," + record(2, 100, 2), "2", "100"), BOOT, dir);
        try { assertThrows(Exception.class, () -> monitor.beginOffAfterCold(boundary())); } finally { monitor.close(); }
    }
    private static JsonObject wakeWitness() {
        JsonObject after = boundary();
        after.addProperty("epoch", "2");
        after.addProperty("capture_boottime_ns", "900000000");
        after.addProperty("commit_seq", "6");
        return after;
    }

    @Test void warmBoundaryRequiresFreshPostWakeRx(@TempDir Path dir) throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        try (var monitor = new NativeCanMonitor(cursor -> switch (count.getAndIncrement()) {
            case 0 -> page(record(1, 100, 2), "1", "100");
            case 1 -> page(record(2, 1100, 2), "2", "1100");
            default -> page(record(3, 2100, 2), "3", "2100");
        }, BOOT, dir)) {
            monitor.beginOffAfterWarm(boundary(), wakeWitness());
            monitor.endOff(1100);
        }
    }

    @Test void warmBoundaryRejectsTransientOnBeforeResumingMonitor(@TempDir Path dir) throws Exception {
        try (var monitor = new NativeCanMonitor(cursor ->
                page(record(1, 900, 5) + "," + record(2, 901, 2), "2", "901"), BOOT, dir)) {
            assertThrows(Exception.class, () -> monitor.beginOffAfterWarm(boundary(), wakeWitness()));
        }
    }

    @Test void closedWitnessRejectsForeignBootLossAndOverflow() throws Exception {
        NativeCanMonitor.requireClosedEcuSource(boundary(), BOOT);
        for (String key : new String[]{"boot_uuid", "guest_boot_id", "loss_count", "on_transition_count", "kernel_open_seq", "first_commit_boottime_ns"}) {
            JsonObject bad = boundary();
            bad.addProperty(key, key.contains("boot_id") || key.equals("boot_uuid") ? "22222222-2222-2222-2222-222222222222" : key.equals("loss_count") ? "1" : "18446744073709551616");
            assertThrows(Exception.class, () -> NativeCanMonitor.requireClosedEcuSource(bad, BOOT), key);
        }
    }
    @Test void coldWitnessRequiresGlobalZeroAndFirstOpen(@TempDir Path dir) throws Exception {
        var monitor = new NativeCanMonitor(cursor -> page(record(1, 100, 2), "1", "100"), BOOT, dir);
        try {
            for (String key : new String[]{"on_transition_count", "first_open", "kernel_open_seq", "first_mode"}) {
                JsonObject bad = boundary();
                if (key.equals("first_open")) bad.addProperty(key, false);
                else if (key.equals("first_mode")) bad.addProperty(key, 5);
                else bad.addProperty(key, "2");
                assertThrows(Exception.class, () -> monitor.beginOffAfterCold(bad), key);
            }
        } finally { monitor.close(); }
    }
    @Test void warmBoundaryRejectsAnOnTransitionOrDiscontinuousSource() throws Exception {
        JsonObject before = boundary();
        before.addProperty("on_transition_count", "2");
        JsonObject after = wakeWitness();
        after.addProperty("on_transition_count", "2");
        NativeCanMonitor.requireWarmOffBoundary(before, after, BOOT);
        for (var entry : java.util.Map.of(
                "on_transition_count", "3", "loss_count", "1", "epoch", "3",
                "boot_uuid", "22222222-2222-2222-2222-222222222222",
                "session", "12", "kernel_open_seq", "2", "commit_seq", "3",
                "capture_boottime_ns", "90000000").entrySet()) {
            var bad = after.deepCopy();
            bad.addProperty(entry.getKey(), entry.getValue());
            assertThrows(Exception.class, () -> NativeCanMonitor.requireWarmOffBoundary(before, bad, BOOT),
                    entry.getKey());
        }
    }

    @Test void staleBaselineRejected(@TempDir Path dir) throws Exception {
        var monitor = new NativeCanMonitor(cursor -> page(record(1, 1, 5), "1", "5000"), BOOT, dir);
        try { assertThrows(Exception.class, monitor::beginOff); } finally { monitor.close(); }
    }
    @Test void epochAndWorkerFailureRejectWindow(@TempDir Path dir) throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var monitor = new NativeCanMonitor(cursor -> {
            if (count.getAndIncrement() == 0) return page(record(1, 100, 5), "1", "100");
            JsonObject next = page("", "1", "1100"); next.addProperty("receiverEpoch", "2"); return next;
        }, BOOT, dir);
        monitor.beginOff();
        assertThrows(Exception.class, () -> monitor.requireOff(101));
        assertThrows(Exception.class, monitor::close);
    }
}
