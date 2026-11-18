package ru.voboost.voyemu;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ru.voboost.tests.logging.InstallerLogs;

/** Correlates current focused Navi editor with its actual in-process Paint diagnostics. */
public final class NaviFontReceipt {
    private static final String SOURCE = "b90b145194f1206b3d515e05d2bc2f27f2411aa0b6897f61755d7b812fe083c1";
    private static final String OUTPUT = "4f03e27101265730adf4634746350eb44aef725e8d8601a48a366a03cf8b6bd0";
    private static final String AGENT = "font-cyrillic";

    public record Identity(String boot, String targetPid, String targetStarttime, String generationId, String catalogSha256) {
        public Identity {
            if (boot == null || !boot.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                    || targetPid == null || !targetPid.matches("[1-9][0-9]{0,9}")
                    || targetStarttime == null || !targetStarttime.matches("[1-9][0-9]{0,19}")
                    || generationId == null || !generationId.matches("[0-9a-f]{64}")
                    || catalogSha256 == null || !catalogSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("current Navi process and product identities required");
            }
        }
    }

    public record Sample(double width, int left, int top, int right, int bottom) {}
    public record Paint(String captureId, double textSize, double scaleX, double letterSpacing,
            double skewX, boolean fakeBold, int x, int y, int width, int height,
            Map<String, Sample> samples) {
        public Paint { samples = Map.copyOf(samples); }
    }

    private NaviFontReceipt() {}

    public static Paint read(InstallerLogs.Snapshot before, InstallerLogs.Snapshot after,
            KeyboardInput.Frame frame, Identity identity, String expectedText) throws IOException {
        if (!identity.boot().equals(before.boot()) || !identity.boot().equals(after.boot())
                || !identity.boot().equals(text(frame.tree(), "boot_id"))) throw invalid("boot differs");
        NavigationInput.Editor editor = NavigationInput.searchEditor(frame.surface());
        editor.requirePrefix(expectedText);
        Map<String, Map<String, InstallerLogs.Diagnostic>> captures = new HashMap<>();
        for (InstallerLogs.Diagnostic record : after.after(before)) {
            if (!AGENT.equals(record.agent()) || !record.event().startsWith("font.visual.")
                    || !"captured".equals(record.outcome())) continue;
            JsonObject source = record.sourceFields();
            if (!identity.boot().equals(text(source, "bootId"))
                    || !NavigationInput.NAVI.equals(text(source, "process"))
                    || !identity.targetPid().equals(text(source, "targetPid"))
                    || !identity.targetStarttime().equals(text(source, "targetStarttime"))
                    || !identity.generationId().equals(text(source, "generationId"))
                    || !identity.catalogSha256().equals(text(source, "catalogSha256"))) continue;
            JsonObject fields = record.fields();
            String id = text(fields, "capture_id"), epoch = scalar(fields, "epoch");
            if (!id.matches("[1-9][0-9]*:[1-9][0-9]*") || !id.startsWith(epoch + ":")) throw invalid("capture identity");
            String type = record.event();
            if ("font.visual.sample".equals(type)) type += ":" + text(fields, "sample_id");
            if (!Set.of("font.visual.view", "font.visual.layout", "font.visual.paint",
                    "font.visual.sample:cyrillic", "font.visual.sample:yo", "font.visual.sample:latin").contains(type)) continue;
            if (captures.computeIfAbsent(id, ignored -> new HashMap<>()).put(type, record) != null) throw invalid("duplicate capture part");
        }
        Paint result = null;
        for (Map.Entry<String, Map<String, InstallerLogs.Diagnostic>> candidate : captures.entrySet()) {
            Map<String, InstallerLogs.Diagnostic> parts = candidate.getValue();
            if (parts.size() != 6) continue;
            List<String> order = List.of("font.visual.view", "font.visual.layout", "font.visual.paint",
                    "font.visual.sample:cyrillic", "font.visual.sample:yo", "font.visual.sample:latin");
            for (int i = 1; i < order.size(); i++) {
                if (parts.get(order.get(i)).sequence().compareTo(parts.get(order.get(i - 1)).sequence()) <= 0)
                    throw invalid("capture part order");
            }
            JsonObject view = parts.get("font.visual.view").fields();
            JsonObject layout = parts.get("font.visual.layout").fields();
            JsonObject paint = parts.get("font.visual.paint").fields();
            if (!"com.pateonavi.naviapp:id/dsie_normal_search".equals(text(view, "view_id"))
                    || !"android.widget.EditText".equals(text(layout, "accessibility_class"))
                    || !text(editor.node(), "class").equals(text(view, "view_class"))
                    || !"cyrillic".equals(text(view, "script"))
                    || !flag(view, "attached") || !flag(view, "focused")
                    || integer(view, "text_length") != expectedText.length()
                    || !flag(layout, "ellipsis_scan_complete") || integer(layout, "ellipsis_count") != 0
                    || integer(layout, "line_count") != 1
                    || !"corrected".equals(text(paint, "provenance"))
                    || !"asset-regular".equals(text(paint, "loader"))
                    || !SOURCE.equals(text(paint, "source_sha256")) || !OUTPUT.equals(text(paint, "output_sha256"))) continue;
            JsonObject bounds = editor.node().getAsJsonObject("bounds");
            int x = integer(layout, "screen_x"), y = integer(layout, "screen_y");
            int width = integer(layout, "width"), height = integer(layout, "height");
            if (x != integer(bounds, "x") || y != integer(bounds, "y") || width != integer(bounds, "width")
                    || height != integer(bounds, "height") || width != integer(view, "layout_width")
                    || height != integer(view, "layout_height")) continue;
            Map<String, Sample> samples = new HashMap<>();
            for (String sample : List.of("cyrillic", "yo", "latin")) {
                JsonObject metrics = parts.get("font.visual.sample:" + sample).fields();
                if (number(metrics, "width") <= 0 || integer(metrics, "bounds_right") <= integer(metrics, "bounds_left")
                        || integer(metrics, "bounds_bottom") <= integer(metrics, "bounds_top")) throw invalid("sample bounds");
                samples.put(sample, new Sample(number(metrics, "width"), integer(metrics, "bounds_left"),
                        integer(metrics, "bounds_top"), integer(metrics, "bounds_right"), integer(metrics, "bounds_bottom")));
            }
            double size = number(paint, "text_size"), scale = number(paint, "scale_x");
            if (size <= 0 || size > 256 || scale <= 0 || scale > 4) throw invalid("Paint dimensions");
            if (result != null) throw invalid("ambiguous matching captures");
            result = new Paint(candidate.getKey(), size, scale, number(paint, "letter_spacing"),
                    number(paint, "skew_x"), bool(paint, "fake_bold"), x, y, width, height, samples);
        }
        if (result == null) throw invalid("current correlated corrected Paint unavailable");
        return result;
    }

    private static IOException invalid(String reason) { return new IOException("Navi font receipt unavailable: " + reason); }
    private static String text(JsonObject value, String key) {
        return value != null && value.has(key) && value.get(key).isJsonPrimitive()
                && value.getAsJsonPrimitive(key).isString() ? value.get(key).getAsString() : "";
    }
    private static String scalar(JsonObject value, String key) {
        return value != null && value.has(key) && value.get(key).isJsonPrimitive() ? value.get(key).getAsString() : "";
    }
    private static int integer(JsonObject value, String key) throws IOException {
        String s = scalar(value, key);
        if (value == null || !value.has(key) || !value.get(key).isJsonPrimitive()
                || !value.getAsJsonPrimitive(key).isNumber() || !s.matches("-?[0-9]{1,9}")) throw invalid("integer " + key);
        return Integer.parseInt(s);
    }
    private static double number(JsonObject value, String key) throws IOException {
        try {
            if (value == null || !value.has(key) || !value.get(key).isJsonPrimitive()
                    || !value.getAsJsonPrimitive(key).isNumber()) throw invalid("number " + key);
            double result = value.get(key).getAsDouble();
            if (!Double.isFinite(result)) throw invalid("finite " + key);
            return result;
        } catch (NumberFormatException malformed) { throw invalid("number " + key); }
    }
    private static boolean bool(JsonObject value, String key) throws IOException {
        if (value == null || !value.has(key) || !value.get(key).isJsonPrimitive()
                || !value.getAsJsonPrimitive(key).isBoolean()) throw invalid("boolean " + key);
        return value.get(key).getAsBoolean();
    }
    private static boolean flag(JsonObject value, String key) throws IOException { return bool(value, key); }
}
