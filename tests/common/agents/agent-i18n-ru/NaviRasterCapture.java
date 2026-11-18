package ru.voboost.voyemu;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import ru.voboost.tests.RunFiles;

/** Fresh full SDK frame between two actual observations of the same focused Navi editor. */
public final class NaviRasterCapture {
    public static final class Frame {
        private final PixelImage image;
        private final Path png, manifest;
        private final NavigationInput.Editor editor;
        private Frame(PixelImage image, Path png, Path manifest, NavigationInput.Editor editor) {
            this.image = image; this.png = png; this.manifest = manifest; this.editor = editor;
        }
        public PixelImage image() { return image; }
        public Path png() { return png; }
        public Path manifest() { return manifest; }
        public NavigationInput.Editor editor() { return editor; }
    }
    private NaviRasterCapture() {}
    private static IOException invalid(String reason) { return new IOException("Navi raster capture unavailable: " + reason); }
    private static String text(JsonObject value, String key) throws IOException {
        if (value == null || !value.has(key) || !value.get(key).isJsonPrimitive()
                || !value.getAsJsonPrimitive(key).isString() || value.get(key).getAsString().isBlank()) throw invalid("text " + key);
        return value.get(key).getAsString();
    }
    private static JsonObject object(JsonObject value, String key) throws IOException {
        if (value == null || !value.has(key) || !value.get(key).isJsonObject()) throw invalid("object " + key);
        return value.getAsJsonObject(key);
    }
    private static BigInteger integer(JsonObject value, String key, boolean string) throws IOException {
        if (value == null || !value.has(key) || !value.get(key).isJsonPrimitive()
                || (string ? !value.getAsJsonPrimitive(key).isString() : !value.getAsJsonPrimitive(key).isNumber())
                || !value.get(key).getAsString().matches("0|[1-9][0-9]{0,19}")) throw invalid("integer " + key);
        BigInteger result = new BigInteger(value.get(key).getAsString());
        if (result.bitLength() > 64) throw invalid("integer bound " + key);
        return result;
    }
    private static boolean flag(JsonObject value, String key, boolean expected) {
        return value.has(key) && value.get(key).isJsonPrimitive() && value.getAsJsonPrimitive(key).isBoolean()
                && value.get(key).getAsBoolean() == expected;
    }
    private static JsonObject clock(JsonObject value, String session) throws IOException {
        if (!"host.steady".equals(text(value, "clock_domain")) || !session.equals(text(value, "clock_id"))
                || integer(value, "end_ns", true).compareTo(integer(value, "start_ns", true)) < 0) throw invalid("clock interval");
        return value;
    }
    public static Frame read(JsonObject capture, Path directory, KeyboardInput.Frame before, KeyboardInput.Frame after,
            String profile, String session, String boot, long androidPid, String expectedText) throws IOException {
        try {
            JsonObject first = before.tree(), last = after.tree();
            for (String key : List.of("profile", "session_id", "boot_id", "source_instance"))
                if (!text(first, key).equals(text(last, key))) throw invalid("editor source changed: " + key);
            if (!"voyemu.ui.v1".equals(text(first, "format")) || !"voyemu.ui.v1".equals(text(last, "format"))
                    || !profile.equals(text(first, "profile")) || !session.equals(text(first, "session_id"))
                    || !boot.equals(text(first, "boot_id")) || androidPid < 1
                    || !integer(first, "android_pid", false).equals(BigInteger.valueOf(androidPid))
                    || !integer(last, "android_pid", false).equals(BigInteger.valueOf(androidPid))
                    || text(first, "observation_id").equals(text(last, "observation_id"))) throw invalid("current editor scope");
            NavigationInput.Editor initial = NavigationInput.searchEditor(before.surface());
            NavigationInput.Editor current = NavigationInput.searchEditor(after.surface());
            initial.requirePrefix(expectedText); current.requireSame(initial); current.requirePrefix(expectedText);
            JsonObject start = clock(object(first, "host_interval"), session), end = clock(object(last, "host_interval"), session);
            JsonObject interval = clock(object(capture, "host_interval"), session);
            if (integer(start, "end_ns", true).compareTo(integer(interval, "start_ns", true)) > 0
                    || integer(interval, "end_ns", true).compareTo(integer(end, "start_ns", true)) > 0) throw invalid("capture not bracketed");
            if (!directory.isAbsolute() || !directory.equals(directory.toRealPath())
                    || !Files.getPosixFilePermissions(directory).equals(PosixFilePermissions.fromString("rwx------"))) throw invalid("private capture directory");
            Path manifest = directory.resolve("manifest.json"), png = directory.resolve("primary.png");
            if (!directory.toString().equals(text(capture, "artifact_dir")) || !manifest.toString().equals(text(capture, "manifest"))
                    || !Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS) || Files.size(manifest) > 65536) throw invalid("capture artifacts");
            JsonObject retained = capture.deepCopy(); retained.remove("artifact_dir"); retained.remove("manifest"); retained.remove("elapsed_ms");
            if (!retained.equals(RunFiles.read(manifest)) || !integer(capture, "schema", false).equals(BigInteger.TWO)
                    || !session.equals(text(capture, "session_id")) || !boot.equals(text(capture, "android_boot_id"))
                    || !integer(capture, "android_pid", false).equals(BigInteger.valueOf(androidPid))
                    || !"parallel_fenced".equals(text(capture, "capture_mode")) || !flag(capture, "accepted", true)
                    || !flag(capture, "frames_consistent", true) || !flag(capture, "android_boot_identity_verified", true)
                    || !flag(capture, "android_frames_verified", true) || !flag(capture, "atomic", false)
                    || !capture.get("screens").toString().equals("[\"primary\"]") || capture.getAsJsonArray("frames").size() != 1) throw invalid("native capture scope");
            JsonObject frame = capture.getAsJsonArray("frames").get(0).getAsJsonObject(), rect = object(frame, "rect");
            if (!"primary".equals(text(frame, "screen")) || !"android_sdk".equals(text(frame, "source"))
                    || !"primary.png".equals(text(frame, "file")) || !flag(frame, "derived", false)
                    || !integer(frame, "width", false).equals(BigInteger.valueOf(1920))
                    || !integer(frame, "height", false).equals(BigInteger.valueOf(720))
                    || !integer(frame, "source_width", false).equals(BigInteger.valueOf(1920))
                    || !integer(frame, "source_height", false).equals(BigInteger.valueOf(720))
                    || !integer(frame, "sdk_display_id", false).equals(BigInteger.ZERO)
                    || !integer(rect, "x", false).equals(BigInteger.ZERO) || !integer(rect, "y", false).equals(BigInteger.ZERO)
                    || !integer(rect, "width", false).equals(BigInteger.valueOf(1920))
                    || !integer(rect, "height", false).equals(BigInteger.valueOf(720))) throw invalid("full SDK projection");
            JsonObject frameClock = clock(object(frame, "host_interval"), session), age = object(frame, "freshness");
            if (integer(frameClock, "start_ns", true).compareTo(integer(interval, "start_ns", true)) < 0
                    || integer(frameClock, "end_ns", true).compareTo(integer(interval, "end_ns", true)) > 0
                    || !flag(age, "source_clock_mapped", true) || !flag(age, "within_limit", true)
                    || !"sdk_frame_generation_estimate".equals(text(age, "basis"))
                    || integer(age, "max_age_ms", false).signum() <= 0
                    || integer(age, "max_age_ms", false).compareTo(BigInteger.valueOf(500)) > 0
                    || !age.has("age_upper_bound_ms") || !age.get("age_upper_bound_ms").isJsonPrimitive()
                    || !age.getAsJsonPrimitive("age_upper_bound_ms").isNumber()) throw invalid("SDK frame freshness");
            BigDecimal bound = new BigDecimal(age.get("age_upper_bound_ms").getAsString());
            if (bound.signum() < 0 || bound.compareTo(new BigDecimal(integer(age, "max_age_ms", false))) > 0
                    || new BigDecimal(integer(frameClock, "end_ns", true)).subtract(bound.multiply(BigDecimal.valueOf(1000000)))
                        .compareTo(new BigDecimal(integer(start, "end_ns", true))) < 0) throw invalid("SDK frame may predate editor");
            return new Frame(PixelImage.read(png, 1920, 720), png, manifest, current);
        } catch (RuntimeException malformed) { throw new IOException("malformed Navi raster capture", malformed); }
    }
}
