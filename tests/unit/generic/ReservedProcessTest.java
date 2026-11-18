package ru.voboost.voyemu;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ReservedProcessTest {
    private static String quote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
    @Test void onlyClaimedMutationReachesTheRealProcess(@TempDir Path work) throws Exception {
        Path calls = work.resolve("calls");
        Path executable = work.resolve("fixture");
        Path captured = work.resolve("captured-request.json");
        String response = "{\"schema\":1,\"request_id\":\"" + "b".repeat(32)
            + "\",\"operation_id\":\"" + "a".repeat(32) + "\",\"ok\":true,\"elapsed_ms\":0,\"result\":{}}";
        Files.writeString(executable, "#!/bin/sh\ncp \"$3\" " + quote(captured.toString())
            + "\nprintf 'session.start\\n' >> " + quote(calls.toString())
            + "\nprintf '%s\\n' " + quote(response) + "\n");
        assertTrue(executable.toFile().setExecutable(true));
        VoyemuClient client = VoyemuClient.process(executable, work);
        var start = client.start("b".repeat(32), "profile", "machine", null, false);
        assertThrows(IllegalStateException.class, () -> client.execute(start, Duration.ofSeconds(5)));
        assertFalse(Files.exists(calls));
        Path domain = Files.createDirectory(work.resolve("journal"));
        try (AdmissionRunner runner = AdmissionRunner.open(client, domain, "machine")) {
            var reply = runner.run(start, Duration.ofSeconds(5));
            assertEquals(VoyemuClient.State.RESPONSE, reply.state(), reply.diagnostic());
            assertThrows(java.io.IOException.class, () -> runner.run(start, Duration.ofSeconds(5)));
        }
        assertEquals("session.start\n", Files.readString(calls));
        assertEquals(start.json(), Files.readString(captured));
    }
}
