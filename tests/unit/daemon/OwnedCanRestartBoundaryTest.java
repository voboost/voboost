package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OwnedCanRestartBoundaryTest {
    @TempDir Path directory;

    private static JsonObject sealed() {
        return OwnedFullRestartTest.json("""
                {"schema":1,"producer":"closed_ecu_boundary","status":0,"boot_uuid":"%s",
                 "first_open":true,"valid":true,"all_writers_quiesced":true,"stage":"frozen","mode":2,"first_mode":2,
                 "session":"11","epoch":"12","nonce":"13","kernel_open_seq":"1","commit_seq":"4","on_transition_count":"2",
                 "first_commit_boottime_ns":"100000000","fence_seq":"4","fence_boottime_ns":"200000000",
                 "expires_boottime_ns":"900000000","capture_boottime_ns":"300000000","raw_admission_seq":"5",
                 "raw_queued":"0","raw_inflight_seq":"0","loss_count":"0","generated_rx_seq":"6",
                 "fence_generated_rx_seq":"6","semantic_queue":"0"}
                """.formatted(OwnedFullRestartTest.BEFORE));
    }

    private static JsonObject anchor() {
        JsonObject value = sealed();
        value.addProperty("stage", "none");
        value.addProperty("nonce", "0");
        value.addProperty("all_writers_quiesced", false);
        value.addProperty("capture_boottime_ns", "150000000");
        value.addProperty("session_id", OwnedFullRestartTest.SESSION);
        value.addProperty("owned_android_pid", 100);
        value.addProperty("guest_boot_id", OwnedFullRestartTest.BEFORE);
        value.addProperty("capability", "closed_ecu_boundary_v1");
        return value;
    }

    private static JsonObject expected(JsonObject history) {
        JsonObject result = new JsonObject();
        result.add("anchor", history);
        result.addProperty("native_terminal_boottime_ms", "151");
        return result;
    }

    private final class Fixture implements VoyemuClient.Transport {
        final OwnedFullRestartTest.Fixture base;
        final List<String> mutations = new ArrayList<>();
        JsonObject original;
        boolean lost;
        boolean coldOn;
        boolean changedAdmission;

        Fixture() throws IOException {
            var outer = new OwnedFullRestartTest();
            outer.directory = directory;
            base = outer.new Fixture();
        }

        private JsonObject restart(JsonObject request) {
            JsonObject receipt = base.receipt(request);
            receipt.getAsJsonObject("before").addProperty("android_pid", 100);
            JsonObject proof = request.getAsJsonObject("expected_can").deepCopy();
            JsonObject old = sealed();
            if (changedAdmission) old.addProperty("raw_admission_seq", "6");
            proof.add("prepared", old);
            JsonObject heartbeat = old.deepCopy();
            heartbeat.addProperty("generated_rx_seq", "7");
            proof.add("before_qnx_stop", heartbeat);
            heartbeat = heartbeat.deepCopy();
            heartbeat.addProperty("generated_rx_seq", "8");
            proof.add("before_android_stop", heartbeat);
            proof.addProperty("old_kernel_stopped", true);
            JsonObject fresh = sealed();
            fresh.addProperty("boot_uuid", OwnedFullRestartTest.AFTER);
            fresh.addProperty("stage", "none");
            fresh.addProperty("session", "21");
            fresh.addProperty("epoch", "1");
            fresh.addProperty("nonce", "0");
            fresh.addProperty("on_transition_count", coldOn ? "1" : "0");
            fresh.addProperty("all_writers_quiesced", false);
            proof.add("after", fresh);
            receipt.add("can_boundary", proof);
            return receipt;
        }

        @Override public VoyemuClient.ProcessResult execute(String bytes, Duration timeout) throws IOException, InterruptedException {
            JsonObject request = OwnedFullRestartTest.json(bytes);
            String op = request.get("op").getAsString();
            if (op.equals("vehicle.can.history")) return reply(request, anchor());
            if (op.equals("session.restart")) {
                mutations.add(op);
                original = request;
                assertEquals(expected(anchor()), request.get("expected_can"));
                assertEquals(OwnedFullRestartTest.expected(), request.get("expected_boot"));
                if (lost) return new VoyemuClient.ProcessResult(null, true, "", "lost");
                return reply(request, restart(request));
            }
            if (op.equals("operation.get") && original != null &&
                    original.get("request_id").equals(request.getAsJsonObject("target").get("request_id"))) {
                JsonObject record = new JsonObject();
                record.addProperty("operation_id", OwnedFullRestartTest.OPERATION);
                record.add("request_id", original.get("request_id"));
                record.add("op", original.get("op"));
                record.addProperty("scope", "session:" + OwnedFullRestartTest.SESSION);
                record.addProperty("status", "terminal");
                record.addProperty("outcome", "unknown");
                record.addProperty("runtime_outcome", "completed");
                record.add("runtime_receipt", restart(original));
                return reply(request, record);
            }
            return base.execute(bytes, timeout);
        }

        private VoyemuClient.ProcessResult reply(JsonObject request, JsonObject value) {
            return new VoyemuClient.ProcessResult(0, false, base.envelope(request, value).toString(), "");
        }

        OwnedSession start() throws Exception {
            return OwnedSession.start(new VoyemuClient(this), OwnedFullRestartTest.PROFILE, base.machine, base.work, false, true);
        }
    }

    @Test void restartsOnceWithOriginalHistoryAndIndependentTerminalTime() throws Exception {
        Fixture fixture = new Fixture();
        try (OwnedSession session = fixture.start()) {
            JsonObject history = session.readCanHistory();
            assertEquals(anchor(), history);
            assertTrue(fixture.mutations.isEmpty());
            assertEquals("completed", session.restartBoth(OwnedFullRestartTest.expected(), expected(history)).get("status").getAsString());
            assertEquals(OwnedFullRestartTest.AFTER, session.coldCanBoundaryReceipt().get("boot_uuid").getAsString());
        }
        assertEquals(List.of("session.restart"), fixture.mutations);
    }

    @Test void foreignAnchorOrTerminalAtAnchorCannotDispatch() throws Exception {
        Fixture fixture = new Fixture();
        try (OwnedSession session = fixture.start()) {
            JsonObject history = session.readCanHistory();
            JsonObject stale = expected(history);
            stale.addProperty("native_terminal_boottime_ms", "150");
            assertThrows(IllegalArgumentException.class, () -> session.restartBoth(OwnedFullRestartTest.expected(), stale));
            history.addProperty("session_id", "f".repeat(32));
            assertThrows(IllegalArgumentException.class, () -> session.restartBoth(OwnedFullRestartTest.expected(), expected(history)));
        }
        assertTrue(fixture.mutations.isEmpty());
    }

    @Test void lostRestartResolvesOnlyByLookup() throws Exception {
        Fixture fixture = new Fixture();
        fixture.lost = true;
        try (OwnedSession session = fixture.start()) {
            session.restartBoth(OwnedFullRestartTest.expected(), expected(session.readCanHistory()));
        }
        assertEquals(List.of("session.restart"), fixture.mutations);
    }

    @Test void newBootWithOnHistoryCannotConfirmRestart() throws Exception {
        Fixture fixture = new Fixture();
        fixture.coldOn = true;
        assertUnconfirmed(fixture);
    }

    @Test void rawAdmissionBetweenAnchorAndFenceCannotConfirmRestart() throws Exception {
        Fixture fixture = new Fixture();
        fixture.changedAdmission = true;
        assertUnconfirmed(fixture);
    }

    private void assertUnconfirmed(Fixture fixture) throws Exception {
        OwnedSession session = fixture.start();
        assertThrows(IOException.class, () -> session.restartBoth(OwnedFullRestartTest.expected(), expected(session.readCanHistory())));
        assertThrows(IOException.class, session::close);
        assertEquals(List.of("session.restart"), fixture.mutations);
    }
}
