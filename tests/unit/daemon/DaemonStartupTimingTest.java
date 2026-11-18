package ru.voboost.tests.logging;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.tests.RunFiles;

class DaemonStartupTimingTest {
    @TempDir Path directory;
    private static final String BOOT = "11111111-1111-1111-1111-111111111111", WRITER = "a".repeat(32);
    private JsonObject row(int sequence, String message, String fields) throws Exception {
        JsonObject row = RunFiles.parse("{\"schemaVersion\":2,\"source\":{\"kind\":\"daemon\"},\"level\":\"debug\",\"timestamp\":\"2026-10-30T00:00:00Z\"}");
        row.addProperty("sequence", sequence); row.addProperty("bootId", BOOT); row.addProperty("writerId", WRITER);
        row.addProperty("component", message.equals("storage endpoint available") ? "owner" : "native");
        row.addProperty("message", message); row.add("fields", RunFiles.parse(fields)); return row;
    }
    private List<JsonObject> rows() throws Exception {
        return List.of(row(1,"storage endpoint available","{\"processStartedBootMs\":\"1000\",\"endpointReadyBootMs\":\"1100\"}"),
            row(2,"native CAN receiver available","{\"observedBootMs\":\"1200\"}"),
            row(3,"native CAN sample received","{\"agent\":\"vehicle-drive-forced-ev\",\"signal\":\"IVI_SOC_MODESET\",\"value\":\"2\",\"observedBootMs\":\"9000\"}"),
            row(4,"native CAN sample received","{\"agent\":\"vehicle-drive-pedestrian-warning\",\"signal\":\"HUM_VSP_FUNCTION_SW\",\"value\":\"1\",\"observedBootMs\":\"9100\"}"));
    }
    private InstallerLogs.Snapshot read(List<JsonObject> rows) throws Exception {
        Path file = directory.resolve("records"); StringBuilder raw = new StringBuilder(); rows.forEach(row -> raw.append(row).append('\n')); Files.writeString(file,raw);
        return InstallerLogs.records(file, new InstallerLogs.CurrentSession(BOOT,WRITER,BigInteger.valueOf(4)),Instant.now(),file);
    }
    @Test void authenticatedCurrentWriterMeasuresFeedbackReadinessWithoutLatencyOrApplicationPass() throws Exception {
        JsonObject proof = NativeStartupTiming.measure(read(rows()));
        assertEquals("MEASURED_ACCEPTANCE_UNVERIFIED", proof.get("status").getAsString()); assertFalse(proof.get("functionalAcceptance").getAsBoolean());
        assertEquals("8000", proof.getAsJsonObject("vehicle-drive-forced-ev").get("afterProcessStartMs").getAsString());
        assertEquals("200", proof.getAsJsonObject("firstReceiverAvailable").get("afterProcessStartMs").getAsString());
    }
    @Test void drivingStyleFeedbackDoesNotProveEnergyModeReadiness() throws Exception {
        var samples = rows();
        samples.get(2).getAsJsonObject("fields").addProperty("signal", "DRIVING_MODE_SET_FB");
        JsonObject proof = NativeStartupTiming.measure(read(samples));
        assertEquals("UNVERIFIED", proof.get("status").getAsString());
        assertFalse(proof.get("functionalAcceptance").getAsBoolean());
        assertTrue(proof.get("reason").getAsString().contains("IVI_SOC_MODESET"));
    }
    @Test void missingClockBootstrapHistoryOrExportGapNeverProvesFirstObservation() throws Exception {
        var rows = rows(); rows.get(2).getAsJsonObject("fields").remove("observedBootMs");
        assertEquals("UNVERIFIED", NativeStartupTiming.measure(read(rows)).get("status").getAsString());
        rows = rows(); rows.get(0).addProperty("writerId", "b".repeat(32));
        assertEquals("UNVERIFIED", NativeStartupTiming.measure(read(rows)).get("status").getAsString());
        rows = rows(); assertEquals("UNVERIFIED", NativeStartupTiming.measure(read(List.of(rows.get(0), rows.get(2), rows.get(3)))).get("status").getAsString());
    }
    @Test void producerForgeryAndRegressingBootClockCannotBecomeMeasurement() throws Exception {
        var rows = rows(); rows.get(2).add("source",RunFiles.parse("{\"kind\":\"agent\",\"id\":\"vehicle-drive-forced-ev\"}"));
        assertThrows(IOException.class, () -> read(rows));
        var regressed = rows(); regressed.get(2).getAsJsonObject("fields").addProperty("observedBootMs","1099");
        assertEquals("UNVERIFIED", NativeStartupTiming.measure(read(regressed)).get("status").getAsString());
    }
}
