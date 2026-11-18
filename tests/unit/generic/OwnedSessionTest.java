package ru.voboost.voyemu;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class OwnedSessionTest {
    @TempDir Path directory;
    static final String PROFILE = "profile", SESSION = "a".repeat(32), OWNER = "b".repeat(32);

    final class Fixture implements VoyemuClient.Transport {
        final List<String> ops = new ArrayList<>();
        final Path machine;
        boolean unknownStart, unknownReboot, failUi, wrongMachine, foreignLookup, wrongObserved, omitObserved, wrongOwner, ready = true;
        boolean restored, rejectUiOnce;
        String blockedEvidence, predicate = "android.ready", expectedRebootMode="warm";
        Path work;
        JsonObject original;
        final java.util.Map<String, JsonObject> retained = new java.util.HashMap<>();
        Fixture() throws IOException {
            machine = Files.createDirectory(directory.resolve("machine"));
            Files.writeString(machine.resolve("runtime.json"), "{\"kind\":\"portable-guest-runtime\",\"profile\":\"profile\"}");
        }
        JsonObject session() { return json("{\"id\":\"" + SESSION + "\",\"ownership_id\":\"" + OWNER + "\"}"); }
        JsonObject started() {
            JsonObject value = new JsonObject(); value.add("session", session());
            value.addProperty("profile", PROFILE); value.addProperty("background", true);
            value.addProperty("machine_seed", wrongMachine ? "/foreign" : machine.toString()); return value;
        }
        JsonObject rebooted() { return json("{\"session_id\":\"" + SESSION + "\",\"status\":\"completed\",\"scope\":\"guest_boot_changed\",\"boot_id\":\"22345678-1234-1234-1234-123456789abc\",\"application_ready\":false}"); }
        @Override public VoyemuClient.ProcessResult execute(String bytes, java.time.Duration timeout) throws IOException {
            JsonObject request = json(bytes); String op = request.get("op").getAsString(); ops.add(op);
            if (java.util.Set.of("session.start", "multimedia.reboot", "session.stop").contains(op))
                retained.put(request.get("request_id").getAsString(), request.deepCopy());
            JsonObject result = new JsonObject();
            switch (op) {
                case "info": result = json("{\"api\":\"voyemu.test\",\"schema\":1}"); break;
                case "profile.get": result.addProperty("id", PROFILE); break;
                case "session.status": assertEquals(session(), request.getAsJsonObject("session")); result.addProperty("session_id", SESSION);
                    result.addProperty("machine_seed", machine.toString()); result.addProperty("stopped", false); break;
                case "session.start":
                    assertFalse(request.get("with_cluster").getAsBoolean()); assertTrue(request.get("background").getAsBoolean());
                    assertEquals("resume", request.get("scenario").getAsString());
                    original = request;
                    if (unknownStart) return new VoyemuClient.ProcessResult(null, true, "", "timeout");
                    result = started(); break;
                case "operation.get": {
                    JsonObject sought = retained.get(request.getAsJsonObject("target").get("request_id").getAsString());
                    assertNotNull(sought);
                    result.addProperty("status", "terminal"); result.addProperty("outcome", "completed");
                    result.addProperty("operation_id", "c".repeat(32));
                    result.addProperty("request_id", foreignLookup ? "foreign" : sought.get("request_id").getAsString());
                    String originalOp = sought.get("op").getAsString();
                    assertEquals(sought.get("request_id"), request.getAsJsonObject("target").get("request_id"));
                    result.addProperty("profile", PROFILE); result.addProperty("ownership_id", wrongOwner ? "f".repeat(32) : OWNER);
                    result.addProperty("op", originalOp); result.addProperty("scope", originalOp.equals("session.start") ? "profile:" + PROFILE : "session:" + SESSION);
                    if (originalOp.equals("session.start") && !omitObserved) result.add("owned_session_observed", wrongObserved ? json("{\"id\":\"" + "d".repeat(32) + "\",\"ownership_id\":\"" + OWNER + "\"}") : session());
                    result.add("response", envelope(sought, originalOp.equals("session.start") ? started() : originalOp.equals("session.stop") ? json("{\"session_id\":\"" + SESSION + "\",\"accepted\":true,\"cleanup\":{\"verified\":true,\"supervisor_endpoint_closed\":true,\"supervisor_lock_released\":true}}") : rebooted())); break;
                }
                case "multimedia.reboot":
                    assertEquals(session(), request.getAsJsonObject("session"));
                    assertEquals(expectedRebootMode, request.get("mode").getAsString());
                    original = request;
                    if (unknownReboot) return new VoyemuClient.ProcessResult(null, true, "", "lost reboot response");
                    result = rebooted(); break;
                case "session.wait":
                    assertEquals("android.ready", request.get("predicate").getAsString());
                    result.addProperty("predicate", predicate); result.addProperty("ready", ready);
                    var status = json("{\"session_id\":\"" + SESSION + "\",\"stopped\":false}");
                    if (restored) status.add("deliveries", json("{\"android\":{\"quickboot\":{\"mode\":\"restored\"}}}"));
                    result.add("last_observation", status); break;
                case "session.ui":
                    if (rejectUiOnce) {
                        rejectUiOnce = false;
                        JsonObject rejected = envelope(request, new JsonObject());
                        rejected.remove("result");
                        rejected.addProperty("ok", false);
                        rejected.add("error", json("{\"code\":\"TREE_REJECTED\",\"message\":\"tree rejected\",\"outcome\":\"not_started\",\"retryable\":false}"));
                        return new VoyemuClient.ProcessResult(4, false, rejected.toString(), "");
                    }
                    if (failUi) return new VoyemuClient.ProcessResult(null, true, "", "lost UI reply");
                    result = json("{\"format\":\"voyemu.ui.v1\",\"profile\":\"profile\",\"session_id\":\"" + SESSION
                        + "\",\"screens\":[{\"screen\":\"primary\",\"status\":\"ok\",\"nodes\":[{\"class\":\"View\"}]}]}"); break;
                case "session.stop":
                    assertEquals(session(), request.getAsJsonObject("session"));
                    result = json("{\"session_id\":\"" + SESSION + "\",\"accepted\":true,\"cleanup\":{\"verified\":true,\"supervisor_endpoint_closed\":true,\"supervisor_lock_released\":true}}"); break;
                default: throw new AssertionError(op);
            }
            if ((op.equals("session.start") && "start-dispatch".equals(blockedEvidence))
                    || (op.equals("operation.get") && "start-lookup-0".equals(blockedEvidence)))
                Files.createDirectory(work.resolve(blockedEvidence + ".json"));
            return new VoyemuClient.ProcessResult(0, false, envelope(request, result).toString(), "");
        }
        OwnedSession start() throws Exception {
            work = Files.createDirectory(directory.resolve("work"));
            return OwnedSession.start(new VoyemuClient(this), PROFILE, machine, work, false);
        }
    }

    static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    static JsonObject envelope(JsonObject request, JsonObject result) {
        if ("session.ui".equals(request.get("op").getAsString()) && result.has("screens")) {
            result = ru.voboost.voyemu.SessionUiTestFrames.response(result, java.util.List.of("primary"));
        }
        JsonObject response = new JsonObject(); response.addProperty("schema", 1); response.add("request_id", request.get("request_id"));
        response.addProperty("operation_id", "c".repeat(32)); response.addProperty("ok", true); response.addProperty("elapsed_ms", 0);
        response.add("result", result); return response;
    }
    @Test void readyUiAndCleanupAreObserved() throws Exception {
        Fixture fixture = new Fixture();
        OwnedSession session = fixture.start();
        try (session) { session.verifyAndroidUi(); }
        session.close();
        assertEquals(List.of("info", "profile.get", "session.start", "session.status", "session.wait", "session.ui", "session.stop"), fixture.ops);
    }
    @Test void restoredUiRejectionUsesOneColdBootBeforeHandoff() throws Exception {
        Fixture fixture = new Fixture();
        fixture.restored = true;
        fixture.rejectUiOnce = true;
        fixture.expectedRebootMode = "cold";
        try (OwnedSession session = fixture.start()) { session.verifyTestUi(); }
        assertEquals(1, fixture.ops.stream().filter("multimedia.reboot"::equals).count());
        assertEquals(2, fixture.ops.stream().filter("session.ui"::equals).count());
        assertEquals(1, fixture.ops.stream().filter("session.stop"::equals).count());
    }
    @Test void failureStillStopsOnlyOwnedSession() throws Exception {
        Fixture fixture = new Fixture(); fixture.failUi = true;
        assertThrows(IOException.class, () -> { try (OwnedSession session = fixture.start()) { session.verifyAndroidUi(); } });
        assertEquals(1, fixture.ops.stream().filter("session.stop"::equals).count());
    }
    @Test void wrongMachineReceiptNeverAuthorizesStop() throws Exception {
        Fixture fixture = new Fixture(); fixture.wrongMachine = true;
        assertThrows(IOException.class, fixture::start);
        assertFalse(fixture.ops.contains("session.stop"));
        assertTrue(Files.exists(fixture.work.resolve("start-dispatch.json")));
    }
    @Test void unknownStartReconcilesWithoutRepeatingMutation() throws Exception {
        Fixture fixture = new Fixture(); fixture.unknownStart = true;
        try (OwnedSession session = fixture.start()) { session.verifyAndroidUi(); }
        assertEquals(1, fixture.ops.stream().filter("session.start"::equals).count());
        assertEquals(1, fixture.ops.stream().filter("operation.get"::equals).count());
        assertEquals(1, fixture.ops.stream().filter("session.stop"::equals).count());
    }
    @Test void terminalSuccessWithoutObservedRecoversOwnedSession() throws Exception {
        Fixture fixture = new Fixture(); fixture.unknownStart = true; fixture.omitObserved = true;
        try (OwnedSession ignored = fixture.start()) { }
        assertEquals(1, fixture.ops.stream().filter("session.start"::equals).count());
        assertEquals(1, fixture.ops.stream().filter("session.stop"::equals).count());
    }
    @Test void recoveredWrongMachineNeverAuthorizesStop() throws Exception {
        Fixture fixture = new Fixture(); fixture.unknownStart = true; fixture.omitObserved = true; fixture.wrongMachine = true;
        assertThrows(IOException.class, fixture::start);
        assertFalse(fixture.ops.contains("session.stop"));
        assertEquals(1, fixture.ops.stream().filter("session.start"::equals).count());
    }
    @Test void recordOwnershipMismatchNeverAuthorizesStop() throws Exception {
        Fixture fixture = new Fixture(); fixture.unknownStart = true; fixture.omitObserved = true; fixture.wrongOwner = true;
        assertThrows(IOException.class, fixture::start);
        assertFalse(fixture.ops.contains("session.stop"));
    }
    @Test void contradictoryRecoveredOwnershipNeverAuthorizesStop() throws Exception {
        Fixture fixture = new Fixture(); fixture.unknownStart = true; fixture.wrongObserved = true;
        assertThrows(IOException.class, fixture::start);
        assertFalse(fixture.ops.contains("session.stop"));
        assertEquals(1, fixture.ops.stream().filter("session.start"::equals).count());
        assertTrue(Files.exists(fixture.work.resolve("start-lookup-0.json")));
    }
    @Test void invalidLookupDoesNotAuthorizeForeignCleanup() throws Exception {
        Fixture fixture = new Fixture(); fixture.unknownStart = true; fixture.foreignLookup = true;
        assertThrows(IOException.class, fixture::start);
        assertFalse(fixture.ops.contains("session.stop"));
        assertEquals(1, fixture.ops.stream().filter("session.start"::equals).count());
    }
    @Test void missingReadinessCannotPassAsUiSuccess() throws Exception {
        Fixture fixture = new Fixture(); fixture.ready = false;
        assertThrows(IOException.class, () -> { try (OwnedSession session = fixture.start()) { session.verifyAndroidUi(); } });
        assertFalse(fixture.ops.contains("session.ui"));
        assertEquals(3, fixture.ops.stream().filter("session.wait"::equals).count());
        assertEquals(1, fixture.ops.stream().filter("session.stop"::equals).count());
    }
    @Test void successfulStartIsStoppedWhenEvidenceWriteFails() throws Exception {
        Fixture fixture = new Fixture(); fixture.blockedEvidence = "start-dispatch";
        assertThrows(IOException.class, fixture::start);
        assertEquals(1, fixture.ops.stream().filter("session.stop"::equals).count());
    }
    @Test void recoveredStartIsStoppedWhenEvidenceWriteFails() throws Exception {
        Fixture fixture = new Fixture(); fixture.unknownStart = true; fixture.blockedEvidence = "start-lookup-0";
        assertThrows(IOException.class, fixture::start);
        assertEquals(1, fixture.ops.stream().filter("session.stop"::equals).count());
    }
    @Test void aDifferentReadyPredicateCannotPass() throws Exception {
        Fixture fixture = new Fixture(); fixture.predicate = "session.reachable";
        assertThrows(IOException.class, () -> { try (OwnedSession session = fixture.start()) { session.verifyAndroidUi(); } });
        assertFalse(fixture.ops.contains("session.ui"));
        assertEquals(1, fixture.ops.stream().filter("session.stop"::equals).count());
    }


    @Test void separateOwnedWarmRebootsUseDistinctDurableRequestsAndDoNotClaimReadiness() throws Exception {
        Fixture fixture = new Fixture();
        try (OwnedSession session = fixture.start()) {
            JsonObject receipt = session.reboot();
            assertEquals("guest_boot_changed", receipt.get("scope").getAsString());
            assertFalse(receipt.get("application_ready").getAsBoolean());
            session.reboot();
        }
        assertEquals(2, fixture.ops.stream().filter("multimedia.reboot"::equals).count());
        assertEquals(1, fixture.ops.stream().filter("session.stop"::equals).count());
    }
    @Test void coldRebootUsesItsOwnDurableModeAndUnknownOutcomeOnlyLooksUp() throws Exception {
        Fixture fixture=new Fixture();fixture.expectedRebootMode="cold";fixture.unknownReboot=true;
        try(OwnedSession session=fixture.start()){assertEquals("completed",session.reboot(true).get("status").getAsString());}
        assertEquals(1,fixture.ops.stream().filter("multimedia.reboot"::equals).count());assertEquals(1,fixture.ops.stream().filter("operation.get"::equals).count());
    }
    @Test void unknownRebootRecoversOriginalRequestWithoutAnotherSend() throws Exception {
        Fixture fixture = new Fixture(); fixture.unknownReboot = true;
        try (OwnedSession session = fixture.start()) { assertEquals("completed", session.reboot().get("status").getAsString()); }
        assertEquals(1, fixture.ops.stream().filter("multimedia.reboot"::equals).count());
        assertEquals(1, fixture.ops.stream().filter("operation.get"::equals).count());
    }
    @Test void closedSessionCannotReboot() throws Exception {
        Fixture fixture = new Fixture(); OwnedSession session = fixture.start(); session.close();
        assertThrows(IOException.class, session::reboot);
        assertFalse(fixture.ops.contains("multimedia.reboot"));
    }
    @Test void unresolvedProductOperationPreventsStopUntilItsOwnerAllowsCleanup() throws Exception {
        Fixture fixture = new Fixture();
        OwnedSession session = fixture.start();
        boolean[] terminal = {false};
        session.beforeStop(() -> { if (!terminal[0]) throw new IOException("operation outcome unknown"); });
        assertThrows(IOException.class, session::close);
        assertFalse(fixture.ops.contains("session.stop"));
        assertThrows(IOException.class, () -> session.beforeStop(() -> {}));
        terminal[0] = true;
        session.close();
        assertEquals(1, fixture.ops.stream().filter("session.stop"::equals).count());
    }

}
