package ru.voboost.voyemu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;

/** The public Checkbox semantic node; desired state is verified independently through the daemon. */
public record SettingsCheckbox(String target, boolean checked) {
    private static final String OWNER = "com.qinggan.app.setting";
    private static final String SWITCH = "android.widget.CheckBox";

    public static SettingsCheckbox read(JsonObject observed, String label) throws IOException {
        JsonObject surface = UiEvidence.readable(observed);
        JsonObject found = switchOfRow(surface, label);
        if (!SWITCH.equals(text(found, "class")) || !yes(found, "checkable")
                || !yes(found, "present") || !yes(found, "visible") || !yes(found, "enabled")
                || !found.has("checked") || !found.get("checked").isJsonPrimitive() || !found.getAsJsonPrimitive("checked").isBoolean())
            throw new UiEvidence.Unavailable("actual Settings checkbox state is not observable");
        return new SettingsCheckbox(text(found, "id"), found.get("checked").getAsBoolean());
    }

    /**
     * The observed switch of a labelled row. Rows built by the OEM {@code ItemSwitcherView} place the label and
     * the CheckBox as siblings inside the row, so the clickable-ancestor lookup alone can never reach the switch
     * of such a row. The switch is then resolved from the label's observed parent instead. Both routes stay
     * observation-based -- text, ordering and geometry never supply the control.
     */
    private static JsonObject switchOfRow(JsonObject surface, String label) throws IOException {
        JsonObject labelled = labelledNode(surface, label);
        JsonObject ancestor = null;
        try {
            ancestor = node(surface, UiEvidence.actionForText(surface, OWNER, label));
        } catch (UiEvidence.Unavailable noAncestorSwitch) {
            // The label of an ItemSwitcherView row is not carried inside its switch.
        }
        if (ancestor != null && SWITCH.equals(text(ancestor, "class"))) return ancestor;
        return siblingSwitch(surface, labelled);
    }

    /** The unique visible, enabled label of the row; an absent or ambiguous label stays fatal. */
    private static JsonObject labelledNode(JsonObject surface, String label) throws IOException {
        JsonObject selected = null;
        for (JsonElement item : surface.getAsJsonArray("nodes")) {
            JsonObject node = item.getAsJsonObject();
            if (OWNER.equals(text(node, "package")) && yes(node, "visible") && label.equals(text(node, "text"))) {
                if (selected != null) throw new IOException("ambiguous visible control label");
                selected = node;
            }
        }
        if (selected == null) throw new UiEvidence.Unavailable("control label not observed");
        if (!yes(selected, "enabled")) throw new UiEvidence.Unavailable("labelled control is not ready");
        return selected;
    }

    /** The single observed CheckBox beside the label, in the label's own row parent, window and package. */
    private static JsonObject siblingSwitch(JsonObject surface, JsonObject labelled) throws IOException {
        String parentId = text(labelled, "parent");
        JsonObject parent = node(surface, parentId);
        if (parentId.isBlank() || parent == null || !OWNER.equals(text(parent, "package"))
                || !text(labelled, "window_id").equals(text(parent, "window_id")))
            throw new UiEvidence.Unavailable("no observed actionable control ancestor");
        JsonObject found = null;
        for (JsonElement item : surface.getAsJsonArray("nodes")) {
            JsonObject node = item.getAsJsonObject();
            if (parentId.equals(text(node, "parent")) && SWITCH.equals(text(node, "class"))) {
                if (found != null) throw new IOException("row carries several observed switches");
                found = node;
            }
        }
        if (found == null) throw new UiEvidence.Unavailable("no observed actionable control ancestor");
        return found;
    }

    private static JsonObject node(JsonObject surface, String id) throws IOException {
        if (id == null || id.isBlank()) return null;
        JsonObject found = null;
        for (JsonElement item : surface.getAsJsonArray("nodes")) {
            JsonObject node = item.getAsJsonObject();
            if (id.equals(text(node, "id"))) {
                if (found != null) throw new IOException("Settings checkbox node is duplicated");
                found = node;
            }
        }
        return found;
    }
    private static String text(JsonObject node, String key) { JsonElement value = node.get(key); return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : ""; }
    private static boolean yes(JsonObject node, String key) { JsonElement value = node.get(key); return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean(); }
}
