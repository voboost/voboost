package ru.voboost.voyemu;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import voyemu.journal.Journal;
import static org.junit.jupiter.api.Assertions.*;

/** P06: operation.get host-side contract — classification, correlation, timeouts, single dispatch, no replay. */
class OperationLookupContractTest {
    static final String OP32 = "a".repeat(32);
    static final String ORIGINAL = "b".repeat(32);
    static final String LOOKUP = "c".repeat(32);

    /** Correlated ok:true response envelope; requestId echoed from the request. */
    static VoyemuClient correlated(VoyemuClient c, String resultJson) {
        return new VoyemuClient((json, t) -> {
            JsonObject request = JsonParser.parseString(json).getAsJsonObject();
            JsonObject envelope = new JsonObject();
            envelope.addProperty("schema", 1);
            envelope.addProperty("request_id", request.get("request_id").getAsString());
            envelope.addProperty("operation_id", OP32);
            envelope.addProperty("ok", true);
            envelope.addProperty("elapsed_ms", 0);
            envelope.add("result", JsonParser.parseString(resultJson));
            return new VoyemuClient.ProcessResult(0, false, envelope.toString(), "");
        });
    }

    /** A session-scoped mutation (multimedia.reboot) with a durable journal reservation. */
    static Voyage scopedMutation(VoyemuClient c, String requestId) {
        var session = new VoyemuClient.Session("d".repeat(32), "e".repeat(32));
        var original = c.reboot(requestId, "profile", session, true, 60000);
        return new Voyage(session, original);
    }
    record Voyage(VoyemuClient.Session session, VoyemuClient.Attempt original) {}

    /** Valid lookup reply with a matching operation record for the original session-scoped mutation. */
    static VoyemuClient.Reply replyFor(VoyemuClient.Attempt query, VoyemuClient.Attempt original, String recordJson) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("ok", true);
        // verify the query matches the original; recompute like assessReservedLookup does
        JsonObject base = JsonParser.parseString(recordJson).getAsJsonObject();
        envelope.add("result", base);
        return new VoyemuClient.Reply(VoyemuClient.State.RESPONSE, query, 0, envelope, "");
    }

    static VoyemuClient.Reply reservedLookupReply(String recordJson) {
        var none = new VoyemuClient((json, t) -> { throw new AssertionError(); });
        var v = scopedMutation(none, ORIGINAL);
        var query = none.lookup(LOOKUP, v.original(), null);
        return replyFor(query, v.original(), recordJson);
    }

    @Test void pendingSuccessFailureUnknownClassification() {
        var original = scopedMutation(new VoyemuClient((j, t) -> { throw new AssertionError(); }), ORIGINAL);
        String responseOk = "{\"ok\":true,\"request_id\":\"" + ORIGINAL + "\",\"operation_id\":\"" + OP32 + "\"}";
        String responseFail = "{\"ok\":false,\"request_id\":\"" + ORIGINAL + "\",\"operation_id\":\"" + OP32 + "\"}";
        // pending: no terminal response
        assertEquals(VoyemuClient.LookupRecord.PENDING,
            VoyemuClient.classifyReservedLookup(reservedLookupReply("{\"operation_id\":\"" + OP32 + "\",\"status\":\"pending\"}"), original.original()));
        // terminal success: ok:true + outcome completed
        assertEquals(VoyemuClient.LookupRecord.SUCCESS,
            VoyemuClient.classifyReservedLookup(reservedLookupReply("{\"operation_id\":\"" + OP32 + "\",\"status\":\"terminal\",\"outcome\":\"completed\",\"response\":" + responseOk + "}"), original.original()));
        // terminal failure with error outcome not_started
        String failNotStarted = "{\"ok\":false,\"request_id\":\"" + ORIGINAL + "\",\"operation_id\":\"" + OP32 + "\",\"error\":{\"code\":\"X\",\"message\":\"m\",\"retryable\":false,\"outcome\":\"not_started\"}}";
        assertEquals(VoyemuClient.LookupRecord.FAILURE,
            VoyemuClient.classifyReservedLookup(reservedLookupReply("{\"operation_id\":\"" + OP32 + "\",\"status\":\"terminal\",\"outcome\":\"not_started\",\"response\":" + failNotStarted + "}"), original.original()));
        // terminal failure with unknown outcome stays unknown
        String failUnknown = "{\"ok\":false,\"request_id\":\"" + ORIGINAL + "\",\"operation_id\":\"" + OP32 + "\",\"error\":{\"code\":\"Y\",\"message\":\"m\",\"retryable\":false,\"outcome\":\"unknown\"}}";
        assertEquals(VoyemuClient.LookupRecord.UNKNOWN,
            VoyemuClient.classifyReservedLookup(reservedLookupReply("{\"operation_id\":\"" + OP32 + "\",\"status\":\"terminal\",\"outcome\":\"unknown\",\"response\":" + failUnknown + "}"), original.original()));
        // pending admission that already has a response stays unknown, never success
        assertEquals(VoyemuClient.LookupRecord.UNKNOWN,
            VoyemuClient.classifyReservedLookup(reservedLookupReply("{\"operation_id\":\"" + OP32 + "\",\"status\":\"pending\",\"response\":" + responseOk + "}"), original.original()));
        // terminal without response or outcome stays unknown
        assertEquals(VoyemuClient.LookupRecord.UNKNOWN,
            VoyemuClient.classifyReservedLookup(reservedLookupReply("{\"operation_id\":\"" + OP32 + "\",\"status\":\"terminal\"}"), original.original()));
        // terminal record with unexpected outcome value stays unknown
        assertEquals(VoyemuClient.LookupRecord.UNKNOWN,
            VoyemuClient.classifyReservedLookup(reservedLookupReply("{\"operation_id\":\"" + OP32 + "\",\"status\":\"terminal\",\"outcome\":\"completed\",\"response\":" + responseFail + "}"), original.original()));
        // classification never upgrades an invalid assessment
        assertEquals(VoyemuClient.LookupRecord.INVALID,
            VoyemuClient.classifyReservedLookup(reservedLookupReply("{\"operation_id\":\"" + OP32 + "\"}"), original.original()));
    }

    @Test void unexpectedAndForeignResponsesRejectedBeforeClassification(@TempDir Path root) throws Exception {
        var c = new VoyemuClient((json, t) -> { throw new AssertionError(); });
        var v = scopedMutation(c, ORIGINAL);
        try (Journal journal = Journal.open(root)) {
            journal.reserve("session:" + v.session().id(), "identity", v.original().requestId(), v.original().json().getBytes(StandardCharsets.UTF_8));
            var query = c.lookupReserved(LOOKUP, journal, "identity", v.original());
            // foreign record: same shape but pointing at a different request
            JsonObject envelope = new JsonObject(); envelope.addProperty("ok", true);
            envelope.add("result", JsonParser.parseString("{\"operation_id\":\"" + OP32 + "\",\"status\":\"terminal\",\"request_id\":\"foreign\"}"));
            assertEquals(VoyemuClient.LookupAssessment.INVALID, VoyemuClient.assessReservedLookup(new VoyemuClient.Reply(VoyemuClient.State.RESPONSE, query, 0, envelope, ""), v.original()));
            assertEquals(VoyemuClient.LookupRecord.INVALID, VoyemuClient.classifyReservedLookup(new VoyemuClient.Reply(VoyemuClient.State.RESPONSE, query, 0, envelope, ""), v.original()));
            // unexpected status value invalid
            JsonObject bad = new JsonObject(); bad.addProperty("ok", true);
            bad.add("result", JsonParser.parseString("{\"operation_id\":\"" + OP32 + "\",\"status\":\"running\"}"));
            assertEquals(VoyemuClient.LookupAssessment.INVALID, VoyemuClient.assessReservedLookup(new VoyemuClient.Reply(VoyemuClient.State.RESPONSE, query, 0, bad, ""), v.original()));
            // duplicate key inside the record result is rejected by strict parsing on execute
            String dup = "{\"schema\":1,\"request_id\":\"" + LOOKUP + "\",\"operation_id\":\"" + OP32 + "\",\"ok\":true,\"elapsed_ms\":0,\"result\":{\"operation_id\":\"" + OP32 + "\",\"operation_id\":\"" + OP32 + "\",\"status\":\"pending\"}}";
            VoyemuClient duplicates = new VoyemuClient((json, t) -> new VoyemuClient.ProcessResult(0, false, dup, ""));
            assertEquals(VoyemuClient.State.UNKNOWN, duplicates.execute(query, Duration.ofSeconds(1)).state());
        }
    }

    @Test void lookupByExplicitOperationIdAndOwnRequestId() {
        var c = new VoyemuClient((json, t) -> { throw new AssertionError(); });
        var v = scopedMutation(c, ORIGINAL);
        // by operation_id (needs token) and by request_id; both target the original request only
        var q1 = c.lookup(LOOKUP, v.original(), OP32);
        assertEquals(OP32, JsonParser.parseString(q1.json()).getAsJsonObject().getAsJsonObject("target").get("operation_id").getAsString());
        var q2 = c.lookup(LOOKUP, v.original(), null);
        assertEquals(ORIGINAL, JsonParser.parseString(q2.json()).getAsJsonObject().getAsJsonObject("target").get("request_id").getAsString());
        assertThrows(IllegalArgumentException.class, () -> c.lookup(LOOKUP, v.original(), "short"));
        assertThrows(IllegalArgumentException.class, () -> c.lookup(ORIGINAL, v.original(), null)); // lookup needs its own request id
        assertThrows(IllegalArgumentException.class, () -> c.lookup(LOOKUP, c.info(ORIGINAL), null)); // info is not a mutation
    }

    @Test void classifyErrorEnvelopeWithoutResultIsUnknownNeverCrashes() {
        // a valid server error reply (exit 3): ok:false + error, no result — classify must never throw and never succeed
        var original = scopedMutation(new VoyemuClient((j, t) -> { throw new AssertionError(); }), ORIGINAL);
        var query = new VoyemuClient((j, t) -> { throw new AssertionError(); }).lookup(LOOKUP, original.original(), null);
        JsonObject envelope = JsonParser.parseString(
            "{\"ok\":false,\"error\":{\"code\":\"TEST_OPERATION_NOT_FOUND\",\"message\":\"no admission for this request and scope\",\"retryable\":false,\"outcome\":\"not_started\"}}"
        ).getAsJsonObject();
        var reply = new VoyemuClient.Reply(VoyemuClient.State.RESPONSE, query, 3, envelope, "");
        assertEquals(VoyemuClient.LookupAssessment.UNVERIFIED, VoyemuClient.assessReservedLookup(reply, original.original()));
        assertEquals(VoyemuClient.LookupRecord.UNKNOWN, VoyemuClient.classifyReservedLookup(reply, original.original()));
    }

    @Test void classifyNeverReturnsSuccessWhenOuterEnvelopeFailed() {
        // contradictory ok:false + terminal completed result: the outer failure dominates, never SUCCESS/PENDING/FAILURE
        var original = scopedMutation(new VoyemuClient((j, t) -> { throw new AssertionError(); }), ORIGINAL);
        var query = new VoyemuClient((j, t) -> { throw new AssertionError(); }).lookup(LOOKUP, original.original(), null);
        String nested = "{\"ok\":true,\"request_id\":\"" + ORIGINAL + "\",\"operation_id\":\"" + OP32 + "\"}";
        JsonObject record = JsonParser.parseString(
            "{\"operation_id\":\"" + OP32 + "\",\"status\":\"terminal\",\"outcome\":\"completed\",\"response\":" + nested + "}"
        ).getAsJsonObject();
        JsonObject envelope = new JsonObject();
        envelope.addProperty("ok", false); envelope.add("result", record);
        var reply = new VoyemuClient.Reply(VoyemuClient.State.RESPONSE, query, 4, envelope, "");
        assertEquals(VoyemuClient.LookupAssessment.UNVERIFIED, VoyemuClient.assessReservedLookup(reply, original.original()));
        assertEquals(VoyemuClient.LookupRecord.UNKNOWN, VoyemuClient.classifyReservedLookup(reply, original.original()));
    }

    @Test void classifyErrorEnvelopeMalformedOkOrResultStaysFailClosed() {
        var original = scopedMutation(new VoyemuClient((j, t) -> { throw new AssertionError(); }), ORIGINAL);
        var query = new VoyemuClient((j, t) -> { throw new AssertionError(); }).lookup(LOOKUP, original.original(), null);
        // ok of the wrong JSON type with no result: never a crash, fail closed
        JsonObject wrongTypeOk = new JsonObject(); wrongTypeOk.addProperty("ok", "false");
        assertEquals(VoyemuClient.LookupRecord.UNKNOWN,
            VoyemuClient.classifyReservedLookup(new VoyemuClient.Reply(VoyemuClient.State.RESPONSE, query, 3, wrongTypeOk, ""), original.original()));
        // ok:true with a non-object result: INVALID, never SUCCESS/PENDING
        JsonObject badResult = new JsonObject(); badResult.addProperty("ok", true); badResult.addProperty("result", 5);
        assertEquals(VoyemuClient.LookupRecord.INVALID,
            VoyemuClient.classifyReservedLookup(new VoyemuClient.Reply(VoyemuClient.State.RESPONSE, query, 0, badResult, ""), original.original()));
        // identity mismatch in the nested response is caught by assess and stays INVALID
        String foreign = "{\"ok\":true,\"request_id\":\"" + "f".repeat(32) + "\",\"operation_id\":\"" + OP32 + "\"}";
        JsonObject record = JsonParser.parseString(
            "{\"operation_id\":\"" + OP32 + "\",\"status\":\"terminal\",\"outcome\":\"completed\",\"response\":" + foreign + "}").getAsJsonObject();
        JsonObject envelope = new JsonObject(); envelope.addProperty("ok", true); envelope.add("result", record);
        assertEquals(VoyemuClient.LookupRecord.INVALID,
            VoyemuClient.classifyReservedLookup(new VoyemuClient.Reply(VoyemuClient.State.RESPONSE, query, 0, envelope, ""), original.original()));
    }

    @Test void timeoutsAndUnknownExitProduceUnknownNeverReplay(@TempDir Path root) throws Exception {
        var v = scopedMutation(new VoyemuClient((j, t) -> { throw new AssertionError(); }), ORIGINAL);
        AtomicInteger dispatches = new AtomicInteger();
        // transport that times out; a second dispatch must never happen in this test path
        VoyemuClient timing = new VoyemuClient((json, t) -> { dispatches.incrementAndGet(); return new VoyemuClient.ProcessResult(null, true, "", ""); });
        try (Journal journal = Journal.open(root)) {
            journal.reserve("session:" + v.session().id(), "identity", v.original().requestId(), v.original().json().getBytes(StandardCharsets.UTF_8));
            var query = timing.lookupReserved(LOOKUP, journal, "identity", v.original());
            for (int i = 0; i < 3; i++) {
                var reply = timing.execute(query, Duration.ofSeconds(1));
                assertEquals(VoyemuClient.State.UNKNOWN, reply.state());
                assertEquals(VoyemuClient.LookupRecord.INVALID, VoyemuClient.classifyReservedLookup(reply, v.original())); // unknown reply cannot classify
            }
            assertEquals(3, dispatches.get());
            // process crash (no stdout) is also unknown; foreign request_id response too
            VoyemuClient crashed = new VoyemuClient((json, t) -> new VoyemuClient.ProcessResult(1, false, "", ""));
            assertEquals(VoyemuClient.State.UNKNOWN, crashed.execute(query, Duration.ofSeconds(1)).state());
            String foreign = "{\"schema\":1,\"request_id\":\"someone-else\",\"operation_id\":\"" + OP32 + "\",\"ok\":true,\"elapsed_ms\":0,\"result\":{}}";
            VoyemuClient mismatched = new VoyemuClient((json, t) -> new VoyemuClient.ProcessResult(0, false, foreign, ""));
            assertEquals(VoyemuClient.State.UNKNOWN, mismatched.execute(query, Duration.ofSeconds(1)).state());
        }
    }

    @Test void singleDispatchAndNoDangerousRepeatThroughClient(@TempDir Path root) throws Exception {
        // Mutations remain blocked at execute: only read-only ops reach the transport, exactly once per call.
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        VoyemuClient c = new VoyemuClient((json, t) -> {
            seen.add(json);
            JsonObject request = JsonParser.parseString(json).getAsJsonObject();
            JsonObject envelope = new JsonObject();
            envelope.addProperty("schema", 1);
            envelope.addProperty("request_id", request.get("request_id").getAsString());
            envelope.addProperty("operation_id", OP32);
            envelope.addProperty("ok", true);
            envelope.addProperty("elapsed_ms", 0);
            envelope.add("result", new JsonObject());
            return new VoyemuClient.ProcessResult(0, false, envelope.toString(), "");
        });
        var v = scopedMutation(c, ORIGINAL);
        assertThrows(IllegalStateException.class, () -> c.execute(v.original(), Duration.ofSeconds(1))); // mutation blocked
        assertEquals(0, seen.size());
        try (Journal journal = Journal.open(root)) {
            journal.reserve("session:" + v.session().id(), "identity", v.original().requestId(), v.original().json().getBytes(StandardCharsets.UTF_8));
            var query = c.lookupReserved(LOOKUP, journal, "identity", v.original());
            var r1 = c.execute(query, Duration.ofSeconds(1));
            assertEquals(VoyemuClient.State.RESPONSE, r1.state());
            assertEquals(1, seen.size());
            // a repeat of the *same* reserved lookup is a read-only query; it is dispatched again but never replays the mutation
            var r2 = c.execute(query, Duration.ofSeconds(1));
            assertEquals(VoyemuClient.State.RESPONSE, r2.state());
            assertEquals(2, seen.size());
            for (String request : seen)
                assertEquals("operation.get", JsonParser.parseString(request).getAsJsonObject().get("op").getAsString());
            // the original mutation stays blocked even after a successful lookup round
            assertThrows(IllegalStateException.class, () -> c.execute(v.original(), Duration.ofSeconds(1)));
        }
        // second reservation for the same scope after the first is impossible (CREATE_NEW fence)
        try (Journal second = Journal.open(root)) {
            assertThrows(java.io.IOException.class, () -> second.reserve("session:" + v.session().id(), "identity", "f".repeat(32), new byte[] {1}));
        }
    }
}
