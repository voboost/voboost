package ru.voboost.voyemu;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ProcessOutputLimitTest {
    @TempDir Path work;
    private Path fixture(int bytes, int stderrBytes) throws Exception {
        Path executable = work.resolve("output-fixture");
        Files.writeString(executable, "#!/bin/sh\nprintf '%s' '{\"schema\":1,\"request_id\":\"reply\",\"operation_id\":\""
            + "a".repeat(32) + "\",\"ok\":true,\"elapsed_ms\":0,\"result\":{\"payload\":\"'\n"
            + "/usr/bin/head -c " + bytes + " /dev/zero | /usr/bin/tr '\\000' 'x'\n"
            + "printf '%s' '\"}}'\n" + (stderrBytes == 0 ? ":\n" : "/usr/bin/head -c " + stderrBytes + " /dev/zero >&2\n"));
        assertTrue(executable.toFile().setExecutable(true));
        return executable;
    }
    @Test void normalUiReplyMayExceedOneMiBButAllStreamsRemainBounded() throws Exception {
        VoyemuClient client = VoyemuClient.process(fixture(2 * 1024 * 1024, 0), work);
        var session = VoyemuClient.Session.attached("b".repeat(32));
        var ui = client.multimediaUi("reply", "profile", session, List.of("primary"), "all_attached", null, null, 1000);
        assertEquals(VoyemuClient.State.RESPONSE, client.execute(ui, Duration.ofSeconds(5)).state());
        assertEquals(VoyemuClient.State.UNKNOWN, client.execute(client.info("reply"), Duration.ofSeconds(5)).state());
        fixture(8 * 1024 * 1024 + 32768, 0);
        assertEquals(VoyemuClient.State.UNKNOWN, client.execute(ui, Duration.ofSeconds(5)).state());
        fixture(0, 1024 * 1024 + 1);
        assertEquals(VoyemuClient.State.UNKNOWN, client.execute(ui, Duration.ofSeconds(5)).state());
    }
}
