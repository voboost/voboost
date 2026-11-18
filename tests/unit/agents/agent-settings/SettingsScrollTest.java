package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.tests.RunFiles;
import voyemu.journal.Journal;

class SettingsScrollTest {
    @TempDir Path directory;
    private static final VoyemuClient.Session SESSION = new VoyemuClient.Session("a".repeat(32), "b".repeat(32));
    private static JsonObject observation() throws IOException {
        return RunFiles.parse("""
            {"format":"voyemu.ui.v1","profile":"profile","session_id":"SESSION","boot_id":"boot","source_instance":"source","revision":"1","screens":[{
             "screen":"primary","status":"ok","complete":true,"width":1920,"height":720,
             "windows":[{"id":"window","type":1,"bounds":{"x":0,"y":0,"width":1920,"height":720},"title":"Setting","package":"com.qinggan.app.setting"}],
             "nodes":[{"id":"scroll","window_id":"window","resource_id":"com.qinggan.app.setting:id/kv_tbox_mpu_res_version","package":"com.qinggan.app.setting",
             "class":"android.widget.ScrollView","text":"TBOX version","visible":true,"enabled":true,"clickable":false,"actionable_candidate":false,"scrollable":true,
             "bounds":{"x":100,"y":200,"width":80,"height":40},"clipped_bounds":{"x":100,"y":200,"width":80,"height":40}}]}]}
            """.replace("SESSION", SESSION.id()));
    }
    private static UiTarget target(JsonObject observation) throws IOException { return UiTarget.selectScrollable(observation, "profile", SESSION, "primary", node -> true); }
    @Test void scrollUsesOnlyTheCompleteObservedContainerAndCannotBeTapped() throws Exception {
        VoyemuClient client = new VoyemuClient((bytes, timeout) -> { throw new AssertionError("builder must not dispatch"); });
        UiTarget target = target(observation());
        var request = RunFiles.parse(client.scrollSettings("c".repeat(32), "profile", SESSION, target).json());
        assertEquals("input.gesture", request.get("op").getAsString()); assertEquals("scroll", request.get("intent").getAsString());
        var points = request.getAsJsonArray("points"); assertEquals(11, points.size());
        assertEquals(230, points.get(0).getAsJsonObject().get("y").getAsInt());
        assertEquals(210, points.get(10).getAsJsonObject().get("y").getAsInt());
        assertThrows(IllegalArgumentException.class, () -> client.tap("c".repeat(32), "profile", SESSION, target));
        for (String field : new String[]{"scrollable", "visible", "enabled"}) {
            var ui = observation(); ui.getAsJsonArray("screens").get(0).getAsJsonObject().getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty(field, false);
            assertThrows(IOException.class, () -> target(ui));
        }
        var partial = observation(); partial.getAsJsonArray("screens").get(0).getAsJsonObject().addProperty("complete", false);
        assertThrows(IOException.class, () -> target(partial));
    }
    @Test void reverseScrollIsAnExactFiniteObservedTraceAndRetainsWithoutReplay() throws Exception {
        VoyemuClient client = new VoyemuClient((bytes, timeout) -> { throw new AssertionError("builder and recovery must not dispatch"); });
        var original = client.scrollSettings("c".repeat(32), "profile", SESSION, target(observation()), true);
        var request = RunFiles.parse(original.json()); var points = request.getAsJsonArray("points");
        assertEquals(210, points.get(0).getAsJsonObject().get("y").getAsInt());
        assertEquals(230, points.get(10).getAsJsonObject().get("y").getAsInt());
        assertEquals(300, points.get(10).getAsJsonObject().get("at_ms").getAsInt());
        Path path = Files.createDirectory(directory.resolve("reverse-journal")); String scope = "session:" + SESSION.id();
        try (Journal journal = Journal.open(path)) { journal.reserve(scope, "machine", original.requestId(), original.json().getBytes(StandardCharsets.UTF_8)); }
        try (Journal journal = Journal.open(path)) { assertEquals(original.json(), client.reserved(journal, scope, "machine").json()); }
    }
    @Test void retainedBurstCanOnlyBeLookedUpAndCannotBeSentAgain() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        VoyemuClient client = new VoyemuClient((bytes, timeout) -> { assertEquals("operation.get", RunFiles.parse(bytes).get("op").getAsString()); reads.incrementAndGet(); return new VoyemuClient.ProcessResult(1, false, "", "lookup unavailable"); });
        var original = client.scrollSettings("c".repeat(32), "profile", SESSION, target(observation()));
        Path path = Files.createDirectory(directory.resolve("journal")); String scope = "session:" + SESSION.id();
        try (Journal journal = Journal.open(path)) { journal.reserve(scope, "machine", original.requestId(), original.json().getBytes(StandardCharsets.UTF_8)); }
        try (Journal journal = Journal.open(path)) {
            var retained = client.reserved(journal, scope, "machine"); assertEquals(original.json(), retained.json());
            assertEquals(VoyemuClient.State.UNKNOWN, client.executeReserved(retained, Duration.ofSeconds(1), journal, "machine").state());
            assertFalse(Files.exists(path.resolve("dispatch"))); assertEquals(0, reads.get());
            client.execute(client.lookupReserved("d".repeat(32), journal, "machine", retained), Duration.ofSeconds(1)); assertEquals(1, reads.get());
        }
    }
    @Test void retainedForeignTargetsOrAlteredCountsFailBeforeLookupOrDispatch() throws Exception {
        VoyemuClient client = new VoyemuClient((bytes, timeout) -> { throw new AssertionError("no transport allowed"); });
        var original = client.scrollSettings("c".repeat(32), "profile", SESSION, target(observation()));
        for (String altered : new String[]{original.json().replace("\"intent\":\"scroll\"", "\"intent\":\"drag\""), original.json().replace("\"at_ms\":300", "\"at_ms\":301"), original.json().replace("android.widget.ScrollView", "android.widget.Button")}) {
            Path path = Files.createTempDirectory(directory, "journal-"); String scope = "session:" + SESSION.id();
            try (Journal journal = Journal.open(path)) { journal.reserve(scope, "machine", original.requestId(), altered.getBytes(StandardCharsets.UTF_8)); }
            try (Journal journal = Journal.open(path)) { assertThrows(IOException.class, () -> client.reserved(journal, scope, "machine")); }
        }
    }
}
