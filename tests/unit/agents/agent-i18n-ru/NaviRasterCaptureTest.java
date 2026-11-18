package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Controlled host manifests exercise the frame fence, not an actual device capture. */
class NaviRasterCaptureTest {
    private static JsonObject clock(long start, long end) {
        JsonObject value = new JsonObject(); value.addProperty("clock_domain", "host.steady"); value.addProperty("clock_id", "session");
        value.addProperty("start_ns", Long.toString(start)); value.addProperty("end_ns", Long.toString(end)); return value;
    }
    private static KeyboardInput.Frame observation(String id, long start, long end) {
        JsonObject surface = NavigationInputTest.surface(), node = NavigationInputTest.editor(surface, "Москва");
        node.addProperty("resource_id", NavigationInput.NAVI + ":id/dsie_normal_search"); surface.addProperty("screen", "primary");
        JsonArray screens = new JsonArray(); screens.add(surface); JsonObject tree = new JsonObject();
        tree.addProperty("format", "voyemu.ui.v1"); tree.addProperty("profile", "profile"); tree.addProperty("session_id", "session"); tree.addProperty("boot_id", "boot");
        tree.addProperty("source_instance", "observer"); tree.addProperty("observation_id", id); tree.addProperty("android_pid", 42);
        tree.add("host_interval", clock(start, end)); tree.add("screens", screens);
        return new KeyboardInput.Frame(tree, Path.of("/controlled-host-fixture/" + id));
    }
    private static JsonObject capture(Path dir) throws Exception {
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
        Files.copy(Path.of("tests/unit/agents/agent-i18n-ru/navi-raster/moscow-reference.png"), dir.resolve("primary.png"));
        JsonObject age = new JsonObject(); age.addProperty("source_clock_mapped", true); age.addProperty("within_limit", true);
        age.addProperty("basis", "sdk_frame_generation_estimate"); age.addProperty("max_age_ms", 500); age.addProperty("age_upper_bound_ms", 50);
        JsonObject rect = new JsonObject(); rect.addProperty("x", 0); rect.addProperty("y", 0); rect.addProperty("width", 1920); rect.addProperty("height", 720);
        JsonObject frame = new JsonObject(); frame.addProperty("screen", "primary"); frame.addProperty("source", "android_sdk");
        frame.addProperty("file", "primary.png"); frame.addProperty("derived", false); frame.addProperty("width", 1920);
        frame.addProperty("height", 720); frame.addProperty("source_width", 1920); frame.addProperty("source_height", 720);
        frame.addProperty("sdk_display_id", 0); frame.add("rect", rect); frame.add("freshness", age); frame.add("host_interval", clock(1_250_000_000L, 1_300_000_000L));
        JsonArray frames = new JsonArray(); frames.add(frame); JsonArray screens = new JsonArray(); screens.add("primary");
        JsonObject result = new JsonObject(); result.addProperty("schema", 2); result.addProperty("session_id", "session");
        result.addProperty("android_boot_id", "boot"); result.addProperty("android_pid", 42); result.addProperty("capture_mode", "parallel_fenced");
        result.addProperty("accepted", true); result.addProperty("frames_consistent", true); result.addProperty("android_boot_identity_verified", true);
        result.addProperty("android_frames_verified", true); result.addProperty("atomic", false); result.add("screens", screens); result.add("frames", frames);
        result.add("host_interval", clock(1_200_000_000L, 1_400_000_000L));
        Files.writeString(dir.resolve("manifest.json"), result.toString()); result.addProperty("artifact_dir", dir.toString());
        result.addProperty("manifest", dir.resolve("manifest.json").toString()); result.addProperty("elapsed_ms", 1); return result;
    }
    private static NaviRasterCapture.Frame read(JsonObject capture, Path dir, KeyboardInput.Frame before, KeyboardInput.Frame after) throws Exception {
        return NaviRasterCapture.read(capture, dir, before, after, "profile", "session", "boot", 42, "Москва");
    }
    @Test void actualShapeAndReadBracketsAdmitPixels(@TempDir Path directory) throws Exception {
        JsonObject capture = capture(directory); var before = observation("before", 1_000_000_000L, 1_100_000_000L);
        var after = observation("after", 1_500_000_000L, 1_600_000_000L);
        assertEquals(1920, read(capture, directory, before, after).image().width());
        assertThrows(IOException.class, () -> read(capture, directory, before, before));
        assertThrows(IOException.class, () -> NaviRasterCapture.read(capture, directory, before, after, "profile", "session", "boot", 43, "Москва"));
        var late = observation("late", 1_260_000_000L, 1_270_000_000L);
        assertThrows(IOException.class, () -> read(capture, directory, late, after));
    }
    @Test void nativeManifestTamperingFailsBeforePixels(@TempDir Path directory) throws Exception {
        JsonObject capture = capture(directory); var before = observation("before", 1_000_000_000L, 1_100_000_000L);
        var after = observation("after", 1_500_000_000L, 1_600_000_000L);
        capture.addProperty("android_pid", 43);
        assertThrows(IOException.class, () -> read(capture, directory, before, after));
    }
}
