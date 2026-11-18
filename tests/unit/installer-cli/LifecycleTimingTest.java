package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.tests.RunFiles;

class LifecycleTimingTest {
    @TempDir Path work;

    private String event(int sequence, String at, String message) {
        JsonObject row = new JsonObject(); row.addProperty("schemaVersion", 1); row.addProperty("sequence", sequence);
        row.addProperty("timestamp", "2026-11-11T" + at + "Z"); row.add("source", source());
        row.addProperty("component", "installer"); row.addProperty("message", message); return row + "\n";
    }
    private JsonObject source() { JsonObject source = new JsonObject(); source.addProperty("kind", "installer"); return source; }
    private Path log(String name, String contents) throws Exception { Path file = work.resolve(name); Files.writeString(file, contents); return file; }

    @Test void retainedInstallMessagesMeasureObservedStagesWithoutInventingProcessTime() throws Exception {
        Path file = log("car.log", event(1,"05:05:49.619","Connecting to the head unit over USB")
                + event(14,"05:05:51.736","Connected to the head unit")
                + event(38,"05:05:51.800","Verifying the installation package")
                + event(170,"05:09:11.500","Sending the verified installation files to the head unit")
                + event(194,"05:09:11.909","Transferring the installation package to the head unit")
                + event(887,"05:09:31.175","All installation files were transferred and verified")
                + "Install completed: transaction " + "a".repeat(64) + "\n");
        JsonObject report = LifecycleTiming.read("install", "car-clean-install", file, null, "unavailable");
        assertEquals("terminal-output-observed", report.get("status").getAsString());
        assertEquals("unavailable", report.getAsJsonObject("process").get("status").getAsString());
        var stages = report.getAsJsonArray("stages");
        assertEquals(2117, stages.get(0).getAsJsonObject().get("wallMs").getAsLong());
        var transfer = stages.asList().stream().map(value -> value.getAsJsonObject())
                .filter(value -> value.get("stage").getAsString().equals("transfer")).findFirst().orElseThrow();
        assertEquals(19675, transfer.get("wallMs").getAsLong());
        assertEquals("end-not-recorded", stages.get(stages.size()-1).getAsJsonObject().get("status").getAsString());
        assertFalse(stages.get(stages.size()-1).getAsJsonObject().has("wallMs"));
        assertTrue(LifecycleTiming.markdown(LifecycleTiming.report(List.of(report))).contains("not total command time"));
    }

    @Test void failedDeletionNeverBecomesCompletedFromInstallOrElapsedLaneTime() throws Exception {
        Path file = log("uninstall.err", event(1,"09:58:25.882","Connecting to the head unit over USB")
                + event(9,"09:58:25.884","Connected to the head unit")
                + event(18,"09:58:25.926","Removing Voboost and restoring the head unit")
                + event(63,"09:58:26.676","Uninstall request published; daemon service start requested; waiting for the device to accept removal"));
        Path out = log("uninstall.out", "");
        JsonObject report = LifecycleTiming.read("uninstall", "cycle", out, file, "unknown");
        assertEquals("unfinished", report.get("status").getAsString());
        assertFalse(report.get("terminalOutputObserved").getAsBoolean());
        var last = report.getAsJsonArray("stages").get(2).getAsJsonObject();
        assertEquals("android-removal", last.get("stage").getAsString());
        assertEquals("end-not-recorded", last.get("status").getAsString());
        assertEquals(750, last.get("observedAtLeastWallMs").getAsLong());
        assertFalse(last.has("wallMs"));
    }

    @Test void clockRegressionAndUnparseableRowsCannotManufactureIntervals() throws Exception {
        Path file = log("clock.log", event(1,"10:00:10","Connecting to the head unit over USB")
                + "{incomplete\n" + event(2,"10:00:09","Connected to the head unit"));
        JsonObject report = LifecycleTiming.read("update", "offline-update", file, null, "unknown");
        assertFalse(report.has("observedEventWallMs"));
        assertFalse(report.getAsJsonArray("stages").get(0).getAsJsonObject().has("wallMs"));
        assertEquals(2, report.getAsJsonArray("warnings").size());
    }

    @Test void processClockIsIndependentAndNonzeroExitOverridesTerminalOutput() throws Exception {
        Path out = work.resolve("cli.out"), err = work.resolve("cli.err");
        var result = CliProcess.run(List.of("/bin/sh", "-c", "printf '%s\\n' 'Install completed: transaction " + "a".repeat(64) + "'; exit 7"),
                out, err, Duration.ofSeconds(3), (stream,line,control) -> null);
        assertEquals(7, result.exit());
        JsonObject measured = RunFiles.read(work.resolve("cli.out.timing.json"));
        assertEquals("exited", measured.get("status").getAsString());
        assertTrue(measured.get("monotonicElapsedNanos").getAsLong() > 0);
        JsonObject report = LifecycleTiming.read("install", "first", out, err, "unknown");
        assertEquals("process-failed", report.get("status").getAsString());
        assertTrue(report.get("terminalOutputObserved").getAsBoolean());
        assertFalse(report.has("observedEventWallMs"));
    }

    @Test void interruptedProcessRetainsTerminationTimingAndCannotPass() throws Exception {
        Path out = work.resolve("cli.out"), err = work.resolve("cli.err");
        assertThrows(Exception.class, () -> CliProcess.run(List.of("/bin/sh", "-c", "exec sleep 10"),
                out, err, Duration.ofMillis(100), (stream,line,control) -> null));
        JsonObject measured = RunFiles.read(work.resolve("cli.out.timing.json"));
        assertEquals("interrupted", measured.get("status").getAsString());
        assertEquals("launch-through-termination-observation", measured.get("scope").getAsString());
        assertEquals("process-interrupted", LifecycleTiming.read("uninstall", "cycle", out, err, "unknown").get("status").getAsString());
    }

    @Test void discoversOnlyBoundOperationStreamsAndKeepsCommandAndPhase() throws Exception {
        Path session = work.resolve("session"), operation = session.resolve("operations/000001");
        Files.createDirectories(operation);
        Files.writeString(session.resolve("cli.out"), "Update completed: transaction " + "a".repeat(64) + "\n");
        Files.writeString(session.resolve("cli.err"), event(1,"10:00:00","Copying the verified Voboost update"));
        Files.writeString(operation.resolve("operation.json"), "{\"kind\":\"UPDATE\",\"state\":\"verified\",\"input\":{\"phase\":\"offline-update\",\"process\":{\"stdout\":\"cli.out\",\"stderr\":\"cli.err\"}}}\n");
        var commands = LifecycleTiming.discover(work);
        assertEquals(1, commands.size()); assertEquals("update", commands.get(0).get("command").getAsString());
        assertEquals("offline-update", commands.get(0).get("phase").getAsString());
        assertEquals("terminal-output-observed", commands.get(0).get("status").getAsString());
        assertEquals("[\"install\",\"uninstall\"]", LifecycleTiming.report(commands).get("unobservedCommands").toString());
    }

    @Test void qnxRemovalMessagesRemainSeparateFromAndroidRemovalAndTerminalReceipt() throws Exception {
        Path out = log("uninstall.out", "{\"transactionId\":\"" + "b".repeat(64)
                + "\",\"bootId\":\"11111111-1111-1111-1111-111111111111\",\"checks\":[\"root\"]}\n");
        Path err = log("uninstall.err", event(1,"10:00:00","Removing Voboost and restoring the head unit")
                + event(2,"10:00:01","Cluster recovery: requesting a clean QNX boot for removal")
                + event(3,"10:00:03","Cluster recovery: waiting for the signed QNX removal receipt")
                + event(4,"10:00:04","Cluster recovery: signed new-boot absence verified and accepted by root"));
        JsonObject report = LifecycleTiming.read("uninstall", "final", out, err, "verified");
        assertEquals("terminal-output-observed", report.get("status").getAsString());
        var stages = report.getAsJsonArray("stages");
        assertEquals("qnx-removal", stages.get(1).getAsJsonObject().get("stage").getAsString());
        assertEquals(3000, stages.get(1).getAsJsonObject().get("wallMs").getAsLong());
        assertEquals("milestone-only", stages.get(2).getAsJsonObject().get("status").getAsString());
        assertFalse(LifecycleTiming.report(List.of(report)).get("functionalAcceptance").getAsBoolean());
    }

    @Test void normalCliAndSettingsObserverWrappersRetainTheirTerminalOutput() throws Exception {
        String receipt = "{\"transactionId\":\"" + "b".repeat(64)
                + "\",\"bootId\":\"11111111-1111-1111-1111-111111111111\",\"checks\":[\"root\"]}";
        for (String key : List.of("uninstall", "absence")) {
            Path out = log(key + ".out", "{\"schemaVersion\":1,\"serial\":\"emulator-5574\",\"" + key + "\":" + receipt + "}\n");
            assertEquals("terminal-output-observed", LifecycleTiming.read("uninstall", key, out, null, "verified").get("status").getAsString());
        }
    }
}
