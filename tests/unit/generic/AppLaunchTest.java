package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.tests.RunFiles;
import voyemu.journal.Journal;

class AppLaunchTest {
    @TempDir Path directory;
    private final VoyemuClient client = new VoyemuClient((request, timeout) -> { throw new AssertionError("unreserved transport forbidden"); });
    private final VoyemuClient.Session owner = new VoyemuClient.Session("a".repeat(32), "b".repeat(32));
    private final String boot = "12345678-1234-1234-1234-123456789abc";

    @Test void launchIsOwnedBootFencedAndNeverRunsThroughTheReadPath() throws Exception {
        var launch = client.launch("launch", "profile", owner, "com.qinggan.app.setting", boot, "primary");
        var request = RunFiles.parse(launch.json());
        assertEquals("multimedia.launch", request.get("op").getAsString());
        assertEquals(boot, request.get("boot_id").getAsString());
        assertFalse(request.has("shell")); assertFalse(request.has("arguments"));
        assertThrows(IllegalStateException.class, () -> client.execute(launch, Duration.ofSeconds(1)));
    }
    @Test void readerAndInvalidTargetsCannotLaunch() {
        assertThrows(IllegalStateException.class, () -> client.launch("launch", "profile", VoyemuClient.Session.attached("a".repeat(32)), "com.qinggan.app.setting", boot, "primary"));
        for (String target : java.util.List.of("setting", "com.qinggan.app.setting;reboot", "com..setting", "1com.setting"))
            assertThrows(IllegalArgumentException.class, () -> client.launch("launch", "profile", owner, target, boot, "primary"));
        assertThrows(IllegalArgumentException.class, () -> client.launch("launch", "profile", owner, "com.qinggan.app.setting", "old-boot", "primary"));
    }
    @Test void retainedLaunchCanOnlyBeLookedUpAndCannotGainExtraArguments() throws Exception {
        var launch = client.launch("c".repeat(32), "profile", owner, "com.qinggan.app.setting", boot, "primary");
        String identity = "/prepared-machine", scope = "session:" + owner.id();
        Path first = Files.createDirectory(directory.resolve("first"));
        try (Journal journal = Journal.open(first)) { journal.reserve(scope, identity, launch.requestId(), launch.json().getBytes(StandardCharsets.UTF_8)); }
        try (Journal journal = Journal.open(first)) {
            var retained = client.reserved(journal, scope, identity);
            assertEquals(launch.json(), retained.json());
            assertEquals(VoyemuClient.State.UNKNOWN, client.executeReserved(retained, Duration.ofSeconds(1), journal, identity).state());
            assertFalse(Files.exists(first.resolve("dispatch")));
        }
        var altered = RunFiles.parse(launch.json()); altered.addProperty("shell", "reboot");
        Path second = Files.createDirectory(directory.resolve("second"));
        try (Journal journal = Journal.open(second)) { journal.reserve(scope, identity, launch.requestId(), altered.toString().getBytes(StandardCharsets.UTF_8)); }
        try (Journal journal = Journal.open(second)) { assertThrows(java.io.IOException.class, () -> client.reserved(journal, scope, identity)); }
    }
}
