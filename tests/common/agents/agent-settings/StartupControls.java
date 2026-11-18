package ru.voboost.voyemu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** The observed OEM Settings startup group; labels identify controls, never input coordinates. */
public record StartupControls(String selected, String language, Map<String, String> targets) {
    public StartupControls { targets = Map.copyOf(targets); }
    private static final String OWNER = "com.qinggan.app.setting";
    private static final Map<String, String> LABELS = Map.of(
        "Normal", "interface", "Hidden", "hidden", "Do not start", "none",
        "Нормально", "interface", "Скрытно", "hidden", "Не запускать", "none",
        "正常启动", "interface", "隐藏启动", "hidden", "不启动", "none");
    public static StartupControls read(JsonObject observed) throws IOException {
        JsonObject surface = UiEvidence.readable(observed);
        Map<String, String> targets = new HashMap<>(); String selected = null, language = null, parent = null, window = null;
        for (JsonElement item : surface.getAsJsonArray("nodes")) {
            JsonObject node = item.getAsJsonObject(); String mode = LABELS.get(text(node, "text"));
            if (mode == null || !OWNER.equals(text(node, "package")) || !yes(node, "present") || !yes(node, "visible") || !yes(node, "checkable")) continue;
            String caption = text(node, "text");
            String currentLanguage = Set.of("Normal", "Hidden", "Do not start").contains(caption) ? "en"
                : Set.of("Нормально", "Скрытно", "Не запускать").contains(caption) ? "ru" : "zh";
            if (language != null && !language.equals(currentLanguage)) throw new IOException("startup group mixes resource languages");
            language = currentLanguage;
            String id = text(node, "id"), row = text(node, "parent"), currentWindow = text(node, "window_id");
            if (id.isBlank() || row.isBlank() || currentWindow.isBlank() || !yes(node, "enabled")
                    || !node.has("checked") || !node.get("checked").isJsonPrimitive() || !node.getAsJsonPrimitive("checked").isBoolean()
                    || targets.putIfAbsent(mode, id) != null)
                throw new IOException("startup radio is incomplete or ambiguous");
            if (parent != null && (!parent.equals(row) || !window.equals(currentWindow))) throw new IOException("startup radios belong to different groups");
            parent = row; window = currentWindow;
            if (yes(node, "checked")) {
                if (selected != null) throw new IOException("multiple startup radios are checked");
                selected = mode;
            }
        }
        if (!targets.keySet().equals(Set.of("interface", "hidden", "none")) || selected == null)
            throw new UiEvidence.Unavailable("complete unique startup group is not observed");
        int groups = 0, windows = 0;
        for (JsonElement item : surface.getAsJsonArray("nodes")) {
            JsonObject node = item.getAsJsonObject();
            if (parent.equals(text(node, "id")) && window.equals(text(node, "window_id")) && OWNER.equals(text(node, "package"))
                    && yes(node, "present") && yes(node, "visible")) ++groups;
        }
        for (JsonElement item : surface.getAsJsonArray("windows")) {
            JsonObject candidate = item.getAsJsonObject();
            if (window.equals(text(candidate, "id")) && OWNER.equals(text(candidate, "package")) && "1".equals(text(candidate, "type"))) ++windows;
        }
        if (groups != 1 || windows != 1) throw new IOException("startup group/window identity differs");
        return new StartupControls(selected, language, targets);
    }
    private static String text(JsonObject node, String key) {
        JsonElement value = node.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }
    private static boolean yes(JsonObject node, String key) {
        JsonElement value = node.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
    }
}
