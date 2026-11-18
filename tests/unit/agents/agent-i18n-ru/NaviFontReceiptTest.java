package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.logging.InstallerLogs;

class NaviFontReceiptTest {
    private static final String BOOT = "11111111-1111-1111-1111-111111111111";
    private static final String WRITER = "a".repeat(32), GENERATION = "b".repeat(64), CATALOG = "c".repeat(64);
    private static final NaviFontReceipt.Identity ID = new NaviFontReceipt.Identity(BOOT, "45", "90", GENERATION, CATALOG);
    private static final class Fixture {
        final List<InstallerLogs.Diagnostic> rows = new ArrayList<>();
        final JsonObject source = new JsonObject();
        final JsonObject ui = NavigationInputTest.surface();
        Fixture() {
            source.addProperty("bootId", BOOT); source.addProperty("process", NavigationInput.NAVI);
            source.addProperty("targetPid", "45"); source.addProperty("targetStarttime", "90");
            source.addProperty("generationId", GENERATION); source.addProperty("catalogSha256", CATALOG);
            var editor = NavigationInputTest.editor(ui, "Москва");
            editor.addProperty("resource_id", NavigationInput.NAVI + ":id/dsie_normal_search");
            JsonObject view = fields(); view.addProperty("view_id", NavigationInput.NAVI + ":id/dsie_normal_search");
            view.addProperty("view_class", "android.widget.EditText"); view.addProperty("script", "cyrillic");
            view.addProperty("attached", true); view.addProperty("focused", true); view.addProperty("text_length", 6);
            view.addProperty("layout_width", 100); view.addProperty("layout_height", 40); add("view", view);
            JsonObject layout = fields(); layout.addProperty("accessibility_class", "android.widget.EditText");
            layout.addProperty("screen_x", 10); layout.addProperty("screen_y", 20); layout.addProperty("width", 100);
            layout.addProperty("height", 40); layout.addProperty("line_count", 1); layout.addProperty("ellipsis_count", 0);
            layout.addProperty("ellipsis_scan_complete", true); add("layout", layout);
            JsonObject paint = fields(); paint.addProperty("provenance", "corrected"); paint.addProperty("loader", "asset-regular");
            paint.addProperty("source_sha256", "b90b145194f1206b3d515e05d2bc2f27f2411aa0b6897f61755d7b812fe083c1");
            paint.addProperty("output_sha256", "4f03e27101265730adf4634746350eb44aef725e8d8601a48a366a03cf8b6bd0");
            paint.addProperty("text_size", 24); paint.addProperty("scale_x", 1); paint.addProperty("letter_spacing", 0);
            paint.addProperty("skew_x", 0); paint.addProperty("fake_bold", false); add("paint", paint);
            for (String id : List.of("cyrillic", "yo", "latin")) {
                JsonObject sample = fields(); sample.addProperty("sample_id", id); sample.addProperty("width", 10);
                sample.addProperty("bounds_left", 0); sample.addProperty("bounds_right", 10);
                sample.addProperty("bounds_top", -10); sample.addProperty("bounds_bottom", 1); add("sample", sample);
            }
        }
        JsonObject fields() { JsonObject f = new JsonObject(); f.addProperty("capture_id", "1:1"); f.addProperty("epoch", 1); return f; }
        void add(String event, JsonObject fields) { rows.add(new InstallerLogs.Diagnostic(BigInteger.valueOf(rows.size()+1), "font-cyrillic", "font.visual."+event, "captured", fields, source)); }
        InstallerLogs.Snapshot before() { return snapshot(0, List.of(), Set.of()); }
        InstallerLogs.Snapshot after() { return snapshot(6, rows, Set.of(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(3), BigInteger.valueOf(4), BigInteger.valueOf(5), BigInteger.valueOf(6))); }
        InstallerLogs.Snapshot snapshot(int n, List<InstallerLogs.Diagnostic> d, Set<BigInteger> seq) {
            return new InstallerLogs.Snapshot(BOOT, WRITER, BigInteger.valueOf(n), BigInteger.valueOf(n), seq, Instant.EPOCH, d, List.of(), Path.of("/fixture"));
        }
        KeyboardInput.Frame frame() { JsonObject tree = new JsonObject(); tree.addProperty("boot_id", BOOT); ui.addProperty("screen", "primary"); JsonArray screens = new JsonArray(); screens.add(ui); tree.add("screens", screens); return new KeyboardInput.Frame(tree, Path.of("/fixture/ui")); }
        NaviFontReceipt.Paint read() throws IOException { return NaviFontReceipt.read(before(), after(), frame(), ID, "Москва"); }
    }
    @Test void currentOwnedEditorCorrelatesRealPaintParts() throws Exception {
        var paint = new Fixture().read(); assertEquals(24, paint.textSize()); assertEquals(100, paint.width());
    }
    @Test void wrongSourceFontProcessOrGeometryFailsClosed() {
        for (int change = 0; change < 4; change++) {
            Fixture f = new Fixture();
            switch (change) {
                case 0 -> f.source.addProperty("targetPid", "46");
                case 1 -> f.rows.get(2).fields().addProperty("output_sha256", "d".repeat(64));
                case 2 -> f.rows.get(1).fields().addProperty("width", 99);
                case 3 -> f.rows.get(3).fields().addProperty("capture_id", "1:2");
            }
            assertThrows(IOException.class, f::read);
        }
    }
    @Test void unsupportedAndTruncatedSamplesFailClosed() {
        Fixture f = new Fixture(); f.rows.get(4).fields().addProperty("bounds_right", 0);
        assertThrows(IOException.class, f::read);
        Fixture missing = new Fixture(); missing.rows.remove(5); assertThrows(IOException.class, missing::read);
    }
}
