package ru.voboost.voyemu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Observed Settings removal controls; confirmation requires the actual dialog message and both actions. */
public final class SettingsRemoval {
    private static final String OWNER = "com.qinggan.app.setting";
    private record Copy(String language, String button, String message, String confirm, String cancel) {}
    private static final List<Copy> COPIES = List.of(
        new Copy("en", "Uninstall", "Completely remove Voboost. After confirming, do not interact with the multimedia system and wait for one final multimedia reboot.", "Remove", "Cancel"),
        new Copy("zh", "卸载", "完全卸载 Voboost。确认后请勿操作车机，等待最后一次车机重启。", "卸载", "取消"),
        new Copy("ru", "Удалить", "Полностью удалить Voboost. После подтверждения ничего не нажимайте и дождитесь одного завершающего перезапуска мультимедиа.", "Удалить", "Отмена"));
    public record Confirmation(String language, String confirm, String cancel, String window) {}
    private SettingsRemoval() {}

    public static String button(JsonObject observed) throws IOException {
        JsonObject surface = UiEvidence.readable(observed);
        Set<String> targets = new HashSet<>();
        for (Copy copy : COPIES) {
            try { targets.add(action(surface, copy.button())); }
            catch (UiEvidence.Unavailable pending) { }
        }
        if (targets.size() != 1) throw new UiEvidence.Unavailable("unique Settings uninstall button is not observed");
        return targets.iterator().next();
    }

    public static Confirmation confirmation(JsonObject observed) throws IOException {
        JsonObject surface = UiEvidence.readable(observed);
        JsonObject message = null; Copy selected = null;
        for (JsonElement item : surface.getAsJsonArray("nodes")) {
            JsonObject node = item.getAsJsonObject();
            if (!OWNER.equals(text(node, "package")) || !yes(node, "present") || !yes(node, "visible")) continue;
            for (Copy copy : COPIES) if (copy.message().equals(text(node, "text"))) {
                if (message != null) throw new IOException("removal dialog message is ambiguous");
                message = node; selected = copy;
            }
        }
        if (message == null) throw new UiEvidence.Unavailable("removal confirmation message is not observed");
        String window = text(message, "window_id");
        int windows = 0;
        for (JsonElement item : surface.getAsJsonArray("windows")) {
            JsonObject candidate = item.getAsJsonObject();
            if (window.equals(text(candidate, "id")) && OWNER.equals(text(candidate, "package")) && yes(candidate, "read_complete")) ++windows;
        }
        if (window.isBlank() || windows != 1) throw new IOException("removal dialog window is incomplete or ambiguous");
        JsonObject dialog = surface.deepCopy(); JsonArray nodes = new JsonArray();
        for (JsonElement item : surface.getAsJsonArray("nodes"))
            if (window.equals(text(item.getAsJsonObject(), "window_id"))) nodes.add(item);
        dialog.add("nodes", nodes);
        String confirm = action(dialog, selected.confirm()), cancel = action(dialog, selected.cancel());
        if (confirm.equals(cancel)) throw new IOException("removal confirmation actions are not independent");
        return new Confirmation(selected.language(), confirm, cancel, window);
    }

    private static String action(JsonObject surface, String label) throws IOException {
        Set<String> targets = new HashSet<>();
        for (JsonElement item : surface.getAsJsonArray("nodes")) {
            JsonObject candidate = item.getAsJsonObject();
            if (!label.equals(text(candidate, "text")) || !OWNER.equals(text(candidate, "package"))
                    || !yes(candidate, "visible") || !yes(candidate, "present")) continue;
            JsonObject single = surface.deepCopy();
            for (JsonElement other : single.getAsJsonArray("nodes")) {
                JsonObject node = other.getAsJsonObject();
                if (label.equals(text(node, "text")) && !text(candidate, "id").equals(text(node, "id"))) node.addProperty("text", "");
            }
            try { targets.add(UiEvidence.actionForText(single, OWNER, label)); }
            catch (UiEvidence.Unavailable pending) { }
        }
        if (targets.isEmpty()) throw new UiEvidence.Unavailable("removal action is not observed and enabled");
        if (targets.size() != 1) throw new IOException("removal action is ambiguous");
        return targets.iterator().next();
    }
    private static String text(JsonObject node, String key) { JsonElement value = node.get(key); return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : ""; }
    private static boolean yes(JsonObject node, String key) { JsonElement value = node.get(key); return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean(); }
}
