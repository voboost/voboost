package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import voyemu.journal.Journal;

class RetainedRequestTest {
    @TempDir Path directory;
    @Test void retainedMutationIsUsableForLookupButNeverDispatch() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        VoyemuClient client = new VoyemuClient((request, timeout) -> {
            assertEquals("operation.get", ru.voboost.tests.RunFiles.parse(request).get("op").getAsString());
            reads.incrementAndGet();
            return new VoyemuClient.ProcessResult(1, false, "", "lookup unavailable");
        });
        String identity = "/prepared-machine", scope = "profile:profile";
        VoyemuClient.Attempt original = client.start("a".repeat(32), "profile", "machine", null, false);
        Path journalDirectory = Files.createDirectory(directory.resolve("operation"));
        try (Journal journal = Journal.open(journalDirectory)) {
            journal.reserve(scope, identity, original.requestId(), original.json().getBytes(StandardCharsets.UTF_8));
        }
        try (Journal journal = Journal.open(journalDirectory)) {
            VoyemuClient.Attempt retained = client.reserved(journal, scope, identity);
            assertEquals(original.json(), retained.json());
            assertEquals(VoyemuClient.State.UNKNOWN, client.executeReserved(retained, Duration.ofSeconds(1), journal, identity).state());
            assertFalse(Files.exists(journalDirectory.resolve("dispatch")));
            assertEquals(0, reads.get());
            client.execute(client.lookupReserved("b".repeat(32), journal, identity, retained), Duration.ofSeconds(1));
            assertEquals(1, reads.get());
        }
    }
    @Test void retainedIdentityAndRequestShapeAreNotRebound() throws Exception {
        VoyemuClient client = new VoyemuClient((request, timeout) -> { throw new AssertionError("no transport allowed"); });
        Path journalDirectory = Files.createDirectory(directory.resolve("operation"));
        String identity = "/prepared-machine", scope = "profile:profile";
        VoyemuClient.Attempt original = client.start("a".repeat(32), "profile", "machine", null, false);
        String altered = original.json().replace("\"background\":true", "\"background\":false");
        try (Journal journal = Journal.open(journalDirectory)) {
            journal.reserve(scope, identity, original.requestId(), altered.getBytes(StandardCharsets.UTF_8));
        }
        try (Journal journal = Journal.open(journalDirectory)) {
            assertThrows(java.io.IOException.class, () -> client.reserved(journal, scope, "/another-machine"));
            assertThrows(java.io.IOException.class, () -> client.reserved(journal, scope, identity));
        }
    }
}
