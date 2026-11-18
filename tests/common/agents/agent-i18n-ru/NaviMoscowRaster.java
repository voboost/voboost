package ru.voboost.voyemu;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import ru.voboost.tests.RunFiles;

/** Exact reviewed Moscow ink, separate from current Paint and input-effect proof. */
public final class NaviMoscowRaster {
    private NaviMoscowRaster() {}
    private static IOException invalid(String reason) { return new IOException("Navi Moscow raster unavailable: " + reason); }
    private static int number(JsonObject value, String key, int maximum) throws IOException {
        if (!value.has(key) || !value.get(key).isJsonPrimitive() || !value.getAsJsonPrimitive(key).isNumber()
                || !value.get(key).getAsString().matches("0|[1-9][0-9]{0,3}")) throw invalid("geometry " + key);
        int result = value.get(key).getAsInt(); if (result > maximum) throw invalid("geometry bound " + key); return result;
    }
    private static boolean ink(int pixel) {
        return (pixel >>> 24) == 255 && (pixel >>> 16 & 255) >= 192
                && (pixel >>> 8 & 255) >= 192 && (pixel & 255) >= 192;
    }
    private static int[] rect(JsonObject value) throws IOException {
        int x = number(value, "x", 1920), y = number(value, "y", 720);
        int width = number(value, "width", 1920), height = number(value, "height", 720);
        if (width < 1 || height < 1 || x + width > 1920 || y + height > 720) throw invalid("editor rectangle");
        return new int[] {x, y, width, height};
    }
    /** The PNG is not current proof; frame and Paint must independently come from this run. */
    public static JsonObject compare(Path fixture, NaviRasterCapture.Frame current, NaviFontReceipt.Paint paint) throws IOException {
        try {
            Path meta = fixture.resolve("moscow-reference.json"), png = fixture.resolve("moscow-reference.png");
            if (!fixture.isAbsolute() || !fixture.equals(fixture.toRealPath())
                    || !Files.isRegularFile(meta, LinkOption.NOFOLLOW_LINKS) || Files.size(meta) > 4096) throw invalid("reference fixture");
            JsonObject reference = RunFiles.read(meta);
            if (!reference.get("schema").getAsString().equals("1")
                    || !"com.pateonavi.naviapp".equals(reference.get("owner").getAsString())
                    || !"com.pateonavi.naviapp:id/dsie_normal_search".equals(reference.get("resourceId").getAsString())
                    || !"android.widget.EditText".equals(reference.get("class").getAsString())
                    || !"Москва".equals(reference.get("text").getAsString())) throw invalid("historical reference contract");
            NavigationInput.Editor editor = current.editor(); editor.requirePrefix("Москва");
            JsonObject actual = editor.node().getAsJsonObject("bounds");
            if (!actual.equals(editor.node().get("clipped_bounds"))) throw invalid("clipped editor");
            int[] currentRect = rect(actual), referenceRect = rect(reference.getAsJsonObject("bounds"));
            if (paint.x() != currentRect[0] || paint.y() != currentRect[1] || paint.width() != currentRect[2]
                    || paint.height() != currentRect[3]) throw invalid("Paint editor identity differs");
            int count = compareMasks(PixelImage.read(png, 1920, 720), referenceRect, current.image(), currentRect);
            JsonObject result = new JsonObject(); result.addProperty("criterion", "reviewed-historical-moscow-ink");
            result.addProperty("reference", png.toString()); result.addProperty("actual", current.png().toString());
            result.addProperty("paintCaptureId", paint.captureId()); result.addProperty("inkPixels", count);
            result.addProperty("visualAcceptance", false); result.addProperty("functionalAcceptance", false);
            return result;
        } catch (RuntimeException malformed) { throw new IOException("malformed Navi Moscow raster", malformed); }
    }
    static int compareMasks(PixelImage reference, int[] expected, PixelImage actual, int[] observed) throws IOException {
        if (expected.length != 4 || observed.length != 4 || expected[2] != observed[2] || expected[3] != observed[3])
            throw invalid("reference/current editor size differs");
        int count = 0;
        for (int y = 0; y < expected[3]; y++) for (int x = 0; x < expected[2]; x++) {
            boolean a = ink(reference.pixel(expected[0] + x, expected[1] + y));
            boolean b = ink(actual.pixel(observed[0] + x, observed[1] + y));
            if (a != b) throw invalid("reviewed glyph or spacing differs");
            if (a) count++;
        }
        if (count < 40) throw invalid("reviewed glyph ink unavailable");
        return count;
    }
}
