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

class TboxRecoveryTest {
    @TempDir Path directory;
    private static final VoyemuClient.Session SESSION = new VoyemuClient.Session("a".repeat(32), "b".repeat(32));
    private static JsonObject observation() throws IOException {
        return RunFiles.parse("""
            {"format":"voyemu.ui.v1","profile":"profile","session_id":"SESSION","boot_id":"boot","source_instance":"source","revision":"1","screens":[{
             "screen":"primary","status":"ok","complete":true,"width":1920,"height":720,
             "windows":[{"id":"window","type":1,"bounds":{"x":0,"y":0,"width":1920,"height":720},"title":"Setting","package":"com.qinggan.app.setting"}],
             "nodes":[{"id":"tbox","window_id":"window","resource_id":"com.qinggan.app.setting:id/kv_tbox_mpu_res_version","package":"com.qinggan.app.setting",
             "class":"android.widget.LinearLayout","text":"TBOX version","visible":true,"enabled":true,"clickable":true,"actionable_candidate":true,
             "bounds":{"x":100,"y":200,"width":80,"height":40},"clipped_bounds":{"x":100,"y":200,"width":80,"height":40}}]}]}
            """.replace("SESSION", SESSION.id()));
    }
    private static UiTarget target(JsonObject observation) throws IOException { return UiTarget.select(observation, "profile", SESSION, "primary", node -> true); }
    @Test void onlyTheObservedTboxRowGetsOneFixedFiveTapRequest() throws Exception {
        VoyemuClient client = new VoyemuClient((bytes, timeout) -> { throw new AssertionError("builder must not dispatch"); });
        var request = RunFiles.parse(client.recoverSettings("c".repeat(32), "profile", SESSION, target(observation())).json());
        assertEquals("input.tap", request.get("op").getAsString()); assertEquals(5, request.get("count").getAsInt()); assertEquals(100, request.get("interval_ms").getAsInt());
        assertEquals(140, request.get("x").getAsInt()); assertEquals(220, request.get("y").getAsInt());
        for (String key : new String[]{"resource_id", "class"}) {
            JsonObject ui = observation(); ui.getAsJsonArray("screens").get(0).getAsJsonObject().getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty(key, "foreign");
            assertThrows(IllegalArgumentException.class, () -> client.recoverSettings("c".repeat(32), "profile", SESSION, target(ui)));
        }
    }
    @Test void retainedBurstCanOnlyBeLookedUpAndCannotBeSentAgain() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        VoyemuClient client = new VoyemuClient((bytes, timeout) -> { assertEquals("operation.get", RunFiles.parse(bytes).get("op").getAsString()); reads.incrementAndGet(); return new VoyemuClient.ProcessResult(1, false, "", "lookup unavailable"); });
        var original = client.recoverSettings("c".repeat(32), "profile", SESSION, target(observation()));
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
        var original = client.recoverSettings("c".repeat(32), "profile", SESSION, target(observation()));
        for (String altered : new String[]{original.json().replace("\"count\":5", "\"count\":4"), original.json().replace("\"count\":5", "\"count\":\"5\""), original.json().replace("\"interval_ms\":100", "\"interval_ms\":0"), original.json().replace("kv_tbox_mpu_res_version", "another_row")}) {
            Path path = Files.createTempDirectory(directory, "journal-"); String scope = "session:" + SESSION.id();
            try (Journal journal = Journal.open(path)) { journal.reserve(scope, "machine", original.requestId(), altered.getBytes(StandardCharsets.UTF_8)); }
            try (Journal journal = Journal.open(path)) { assertThrows(IOException.class, () -> client.reserved(journal, scope, "machine")); }
        }
    }
}
