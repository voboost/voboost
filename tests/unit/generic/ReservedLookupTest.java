package ru.voboost.voyemu;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import voyemu.journal.Journal;
import static org.junit.jupiter.api.Assertions.*;

class ReservedLookupTest {
    @TempDir Path root;
    @Test void onlyExactReservedAttemptCanBeLookedUp() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        VoyemuClient c = new VoyemuClient((json, timeout) -> { calls.incrementAndGet(); return new VoyemuClient.ProcessResult(null, true, "", ""); });
        var original = c.start("request", "profile", "machine", null, false);
        try (Journal journal = Journal.open(root)) {
            journal.reserve("profile:profile", "identity", original.requestId(), original.json().getBytes(StandardCharsets.UTF_8));
            assertThrows(IOException.class, () -> c.lookupReserved("lookup", journal, "other", original));
            assertThrows(IOException.class, () -> c.lookupReserved("lookup", journal, "identity", c.start("request", "profile", "other", null, false)));
            assertThrows(IOException.class, () -> c.lookupReserved("lookup", journal, "identity", c.start("request", "other", "machine", null, false)));
            assertThrows(IllegalStateException.class, () -> c.execute(original, Duration.ofSeconds(1)));
            assertEquals(0, calls.get());
            assertEquals(VoyemuClient.State.UNKNOWN, c.execute(c.lookupReserved("lookup", journal, "identity", original), Duration.ofSeconds(1)).state());
            assertThrows(IllegalStateException.class, () -> c.execute(original, Duration.ofSeconds(1)));
            assertEquals(1, calls.get());
        }
    }
    @Test void corruptReservationCannotReachTransport() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        VoyemuClient c = new VoyemuClient((json, timeout) -> { calls.incrementAndGet(); throw new AssertionError(); });
        var original = c.start("request", "profile", "machine", null, false);
        Files.write(root.resolve("reservation"), new byte[] {1});
        try (Journal journal = Journal.open(root)) {
            assertThrows(IOException.class, () -> c.lookupReserved("lookup", journal, "identity", original));
            assertThrows(IOException.class, () -> journal.reserve("profile:profile", "identity", original.requestId(), original.json().getBytes(StandardCharsets.UTF_8)));
            assertEquals(0, calls.get());
        }
    }
    @Test void lookupResponsesNeverProveMutationOutcome() throws Exception {
        String id = "a".repeat(32);
        var c = new VoyemuClient((json, timeout) -> { throw new AssertionError(); });
        var original = c.start("request", "profile", "machine", null, false);
        try (Journal journal = Journal.open(root)) {
            journal.reserve("profile:profile", "identity", original.requestId(), original.json().getBytes(StandardCharsets.UTF_8));
            var query = c.lookupReserved("lookup", journal, "identity", original);
            for (String status : new String[] {"pending", "unknown", "terminal"}) {
                var result = com.google.gson.JsonParser.parseString("{\"operation_id\":\"" + id + "\",\"status\":\"" + status + "\"}").getAsJsonObject();
                var envelope = new com.google.gson.JsonObject(); envelope.addProperty("ok", true); envelope.add("result", result);
                var reply = new VoyemuClient.Reply(VoyemuClient.State.RESPONSE, query, 0, envelope, "");
                assertEquals(VoyemuClient.LookupAssessment.UNVERIFIED, VoyemuClient.assessReservedLookup(reply, original));
                for (String field : new String[] {"request_id", "op", "scope"}) {
                    result.addProperty(field, "foreign");
                    assertEquals(VoyemuClient.LookupAssessment.INVALID, VoyemuClient.assessReservedLookup(reply, original));
                    result.remove(field);
                }
                result.add("response", com.google.gson.JsonParser.parseString("{\"request_id\":\"foreign\",\"operation_id\":\"" + id + "\"}"));
                assertEquals(VoyemuClient.LookupAssessment.INVALID, VoyemuClient.assessReservedLookup(reply, original));
                result.remove("response"); result.remove("status");
                assertEquals(VoyemuClient.LookupAssessment.INVALID, VoyemuClient.assessReservedLookup(reply, original));
            }
            assertEquals(VoyemuClient.LookupAssessment.UNVERIFIED, VoyemuClient.assessReservedLookup(new VoyemuClient.Reply(VoyemuClient.State.UNKNOWN, query, null, null, "timeout"), original));
        }
    }
}
