package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Reviewed image controls exercise routing only; they never constitute live keyboard proof. */
class ReviewedKeyboardTest {
    private static final Path MAPS = Path.of("tests/common/agents/agent-keyboard/reviewed").toAbsolutePath().normalize();
    private static PixelImage image(String name) throws IOException { return PixelImage.read(MAPS.resolve(name), 1920, 352); }
    @Test void exactReviewedEnglishAndRussianPixelsSelectOnlyTheirActualKeys() throws Exception {
        PixelImage first = image("en-first.png");
        var q = ReviewedKeyboard.compare(MAPS, first, first, "Q", new JsonObject());
        assertEquals(400, q.x()); assertEquals(414, q.y()); assertEquals("", q.nextBaseline());
        PixelImage russian = image("ru-lower.png");
        var key = ReviewedKeyboard.compare(MAPS, russian, russian, "о", new JsonObject());
        assertEquals("о", key.label()); assertTrue(key.x() >= 0 && key.y() >= 368);
    }
    @Test void transitionRequiresASeparatelyReviewedEdge() throws Exception {
        PixelImage lower = image("ru-lower.png");
        var control = ReviewedKeyboard.compare(MAPS, lower, lower, "switch", new JsonObject());
        assertEquals("en-lower.png", control.nextBaseline());
        PixelImage upper = image("ru-first.png");
        assertThrows(IOException.class, () -> ReviewedKeyboard.compare(MAPS, upper, upper, "switch", new JsonObject()));
    }
    @Test void differentFramesWrongCaseAndUnsupportedInputDoNotAuthorizePoint() throws Exception {
        PixelImage first = image("en-first.png"), lower = image("en-lower.png");
        assertThrows(IOException.class, () -> ReviewedKeyboard.compare(MAPS, first, lower, "Q", new JsonObject()));
        assertThrows(IOException.class, () -> ReviewedKeyboard.compare(MAPS, first, first, "q", new JsonObject()));
        assertThrows(IOException.class, () -> ReviewedKeyboard.compare(MAPS, first, first, "space", new JsonObject()));
        assertThrows(IOException.class, () -> ReviewedKeyboard.compare(MAPS, lower, lower, "ё", new JsonObject()));
    }
    @Test void oneChangedReviewedKeyPixelFailsClosed(@org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        var raster = javax.imageio.ImageIO.read(MAPS.resolve("en-first.png").toFile());
        raster.setRGB(400, 46, raster.getRGB(400, 46) ^ 0x00ffffff);
        Path changed = temp.resolve("changed.png").toAbsolutePath();
        javax.imageio.ImageIO.write(raster, "png", changed.toFile());
        PixelImage damaged = PixelImage.read(changed, 1920, 352);
        assertThrows(IOException.class, () -> ReviewedKeyboard.compare(MAPS, damaged, damaged, "Q", new JsonObject()));
    }

    private static final VoyemuClient.Session SESSION = new VoyemuClient.Session("a".repeat(32), "b".repeat(32));
    private static UiTarget target(String revision) throws Exception {
        JsonObject tree = KeyboardCaptureTest.observation(); tree.addProperty("revision", revision);
        var surface = tree.getAsJsonArray("screens").get(0).getAsJsonObject();
        JsonObject rect = ru.voboost.tests.RunFiles.parse("{\"x\":0,\"y\":368,\"width\":1920,\"height\":352}");
        var canvas = surface.getAsJsonArray("nodes").get(1).getAsJsonObject();
        canvas.add("bounds", rect.deepCopy()); canvas.add("clipped_bounds", rect.deepCopy());
        surface.getAsJsonArray("windows").get(1).getAsJsonObject().add("bounds", rect.deepCopy());
        return UiTarget.selectKeyboardCanvas(tree, "profile", SESSION);
    }
    private static KeyboardCapture.Frame frame(Path parent, String name, UiTarget target, long started) throws Exception {
        Path dir = parent.resolve(name); java.nio.file.Files.createDirectory(dir);
        java.nio.file.Files.setPosixFilePermissions(dir, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        java.nio.file.Files.copy(MAPS.resolve("en-first.png"), dir.resolve("primary.png"));
        JsonObject expected = target.expected();
        JsonObject before = new JsonObject(); before.add("boot_id", expected.get("boot_id"));
        before.add("source_instance", expected.get("source_instance")); before.add("window", expected.get("window"));
        before.add("target", expected.get("target")); before.add("rect", expected.getAsJsonObject("target").get("clipped_bounds"));
        JsonObject roi = new JsonObject(); roi.addProperty("geometry_verified", true); roi.addProperty("atomic", false);
        roi.addProperty("pixels_match_node", false); roi.add("before", before); roi.add("after", before.deepCopy());
        JsonObject freshness = new JsonObject(); freshness.addProperty("source_clock_mapped", true);
        freshness.addProperty("basis", "sdk_frame_generation_estimate"); freshness.addProperty("within_limit", true);
        freshness.addProperty("max_age_ms", 500); freshness.addProperty("age_upper_bound_ms", 1);
        JsonObject image = new JsonObject(); image.addProperty("screen", "primary"); image.addProperty("source", "android_sdk");
        image.addProperty("file", "primary.png"); image.addProperty("source_width", 1920); image.addProperty("source_height", 720);
        image.addProperty("sdk_display_id", 0); image.add("display_id", com.google.gson.JsonNull.INSTANCE);
        image.addProperty("derived", true); image.addProperty("width", 1920); image.addProperty("height", 352);
        image.add("rect", before.get("rect")); image.add("freshness", freshness);
        var frames = new com.google.gson.JsonArray(); frames.add(image);
        var screens = new com.google.gson.JsonArray(); screens.add("primary");
        JsonObject manifest = new JsonObject(); manifest.addProperty("schema", 1); manifest.addProperty("session_id", SESSION.id());
        manifest.addProperty("android_pid", 42); manifest.add("android_boot_id", expected.get("boot_id"));
        manifest.addProperty("capture_mode", "parallel_fenced"); manifest.addProperty("accepted", true);
        manifest.addProperty("frames_consistent", true); manifest.addProperty("android_boot_identity_verified", true);
        manifest.addProperty("android_frames_verified", true); manifest.addProperty("atomic", false);
        manifest.addProperty("skew_upper_bound_ms", 1); manifest.add("screens", screens); manifest.add("frames", frames); manifest.add("roi", roi);
        java.nio.file.Files.writeString(dir.resolve("manifest.json"), manifest.toString());
        JsonObject result = manifest.deepCopy(); result.addProperty("artifact_dir", dir.toString());
        result.addProperty("manifest", dir.resolve("manifest.json").toString()); result.addProperty("elapsed_ms", 1);
        return KeyboardCapture.read(result, dir, target, SESSION.id(), expected.get("boot_id").getAsString(), 42, started, started + 1);
    }
    @Test void actualRequestIsFencedSingleUseAndStaleOrForeignPidCannotDispatch(@org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        UiTarget target = target("three"); long start = System.nanoTime();
        var first = frame(temp, "one", target("one"), start); var second = frame(temp, "two", target("two"), start + 1);
        assertEquals("en-first.png", ReviewedKeyboard.layout(MAPS, first, second, target, "profile", SESSION, 42));
        var match = ReviewedKeyboard.match(MAPS, first, second, target, "profile", SESSION, 42, "Q");
        var client = new VoyemuClient((bytes, timeout) -> { throw new AssertionError("request builder cannot dispatch"); });
        JsonObject request = ru.voboost.tests.RunFiles.parse(client.tapKeyboard("c".repeat(32), "profile", SESSION, target, match, 42).json());
        assertEquals("input.gesture", request.get("op").getAsString());
        var down = request.getAsJsonArray("points").get(0).getAsJsonObject();
        assertEquals(400, down.get("x").getAsInt()); assertEquals(414, down.get("y").getAsInt());
        assertEquals(target.expected(), request.getAsJsonObject("expected"));
        JsonObject invalid = request.deepCopy();
        invalid.getAsJsonArray("points").get(1).getAsJsonObject().addProperty("x", 1);
        assertThrows(IOException.class, () -> ReviewedKeyboard.validateGesture(invalid));
        JsonObject foreign = request.deepCopy();
        foreign.getAsJsonObject("expected").getAsJsonObject("target").addProperty("resource_id", "foreign:id/other");
        assertThrows(IOException.class, () -> ReviewedKeyboard.validateGesture(foreign));
        assertThrows(IOException.class, () -> client.tapKeyboard("d".repeat(32), "profile", SESSION, target, match, 42));
        assertThrows(IOException.class, () -> ReviewedKeyboard.match(MAPS, first, second, target, "profile", SESSION, 43, "Q"));
        long stale = System.nanoTime() - 3_000_000_000L;
        var old1 = frame(temp, "old1", target("one"), stale); var old2 = frame(temp, "old2", target("two"), stale + 1);
        assertThrows(IOException.class, () -> ReviewedKeyboard.match(MAPS, old1, old2, target, "profile", SESSION, 42, "Q"));
    }

}
