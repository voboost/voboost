package ru.voboost.voyemu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Relations between actual observed nodes; text never supplies coordinates or input authority. */
public final class UiEvidence {
    private UiEvidence() {}
    public static final class Unavailable extends IOException {
        public Unavailable(String reason) { super(reason); }
    }

    /**
     * The same surface expressed in the node schema the semantic readers require. The legacy viewserver dump
     * marks every node it exports with {@code present} and every window with {@code read_complete}; the
     * accessibility snapshot -- the only observation that exposes the OEM Settings window at all -- exports
     * only live re-read nodes and declares neither, so a reader that demands them can never be satisfied
     * there. Presence and window completeness are therefore granted from what such a snapshot does state:
     * the window was read, is not declared unavailable, and the snapshot itself is complete and ok. Window
     * identity is stringified because the readers compare identity as text. Nothing else is added, removed
     * or relaxed; a surface that already declares both fields is returned unchanged.
     */
    public static JsonObject readable(JsonObject surface) {
        JsonArray nodes = surface.getAsJsonArray("nodes"), windows = surface.getAsJsonArray("windows");
        boolean presenceDeclared = false, completenessDeclared = false;
        if (nodes != null) for (JsonElement item : nodes) if (item.getAsJsonObject().has("present")) { presenceDeclared = true; break; }
        if (windows != null) for (JsonElement item : windows) if (item.getAsJsonObject().has("read_complete")) { completenessDeclared = true; break; }
        if (presenceDeclared && completenessDeclared) return surface;
        JsonObject copy = surface.deepCopy();
        boolean readSnapshot = yes(copy, "complete") && "ok".equals(text(copy, "status"));
        Set<String> unavailable = new HashSet<>();
        if (copy.getAsJsonArray("unavailable_windows") != null)
            for (JsonElement item : copy.getAsJsonArray("unavailable_windows")) {
                String id = identity(item);
                if (!id.isBlank()) unavailable.add(id);
            }
        Set<String> readWindows = new HashSet<>();
        if (copy.getAsJsonArray("windows") != null) for (JsonElement item : copy.getAsJsonArray("windows")) {
            JsonObject window = item.getAsJsonObject();
            String id = identity(window.get("id"));
            window.add("id", new JsonPrimitive(id));
            if (id.isBlank() || !readSnapshot || unavailable.contains(id)) continue;
            readWindows.add(id);
            if (!completenessDeclared) window.addProperty("read_complete", true);
        }
        if (copy.getAsJsonArray("nodes") != null) for (JsonElement item : copy.getAsJsonArray("nodes")) {
            JsonObject node = item.getAsJsonObject();
            String window = identity(node.get("window_id"));
            node.add("window_id", new JsonPrimitive(window));
            if (!presenceDeclared) node.addProperty("present", readWindows.contains(window));
        }
        return copy;
    }

    /** Observed identity as text; the accessibility schema reports window ids as numbers. */
    private static String identity(JsonElement value) {
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    public static JsonObject surface(JsonObject observation, String screen) throws IOException {
        JsonObject result = null;
        for (JsonElement element : observation.getAsJsonArray("screens")) {
            JsonObject candidate = element.getAsJsonObject();
            if (screen.equals(text(candidate, "screen"))) {
                if (result != null) throw new IOException("ambiguous observed surface");
                result = candidate;
            }
        }
        if (result == null || !Set.of("ok", "partial").contains(text(result, "status")))
            throw new IOException("observed surface unavailable");
        // Every reader of a surface reads the schema readable() establishes: the accessibility snapshot, the only
        // observation that exposes the OEM windows at all, exports neither present nor read_complete. Adapting here
        // rather than at each reader keeps a single boundary, and readable() returns an already-declared surface
        // unchanged, so the readers that adapt again are not disturbed.
        return readable(result);
    }

    public static void requireAbsentText(JsonObject surface, String owner, String expected) throws IOException {
        if (!yes(surface, "complete") || !"ok".equals(text(surface, "status")))
            throw new Unavailable("incomplete UI coverage cannot prove control absence");
        Set<String> windows = new HashSet<>();
        for (JsonElement item : surface.getAsJsonArray("windows")) {
            JsonObject window = item.getAsJsonObject();
            if (owner.equals(text(window, "package")) && "1".equals(window.get("type").getAsString())) {
                if (!yes(window, "read_complete")) throw new Unavailable("application window coverage unavailable");
                if (!windows.add(text(window, "id"))) throw new IOException("application window identity is duplicated");
            }
        }
        boolean observed = false;
        for (JsonElement item : surface.getAsJsonArray("nodes")) {
            JsonObject node = item.getAsJsonObject();
            if (!owner.equals(text(node, "package"))) continue;
            if (expected.equals(text(node, "text"))) throw new IOException("required absent control still exists in the complete tree");
            if (yes(node, "present") && yes(node, "visible") && windows.contains(text(node, "window_id"))) observed = true;
        }
        if (windows.isEmpty() || !observed) throw new Unavailable("application content is not positively observed");
    }

    public static boolean visibleText(JsonObject surface, String owner, String expected) {
        for (JsonElement element : surface.getAsJsonArray("nodes")) {
            JsonObject node = element.getAsJsonObject();
            if (owner.equals(text(node, "package")) && yes(node, "visible") && expected.equals(text(node, "text"))) return true;
        }
        return false;
    }

    public static String actionForText(JsonObject surface, String owner, String expected) throws IOException {
        JsonObject selected = null;
        for (JsonElement element : surface.getAsJsonArray("nodes")) {
            JsonObject node = element.getAsJsonObject();
            if (owner.equals(text(node, "package")) && yes(node, "visible") && expected.equals(text(node, "text"))) {
                if (selected != null) throw new IOException("ambiguous visible control label");
                selected = node;
            }
        }
        if (selected == null) throw new Unavailable("control label not observed");
        String window = text(selected, "window_id"); Set<String> visited = new HashSet<>();
        while (true) {
            String id = text(selected, "id");
            if (id.isBlank() || !visited.add(id) || !owner.equals(text(selected, "package"))
                    || !window.equals(text(selected, "window_id")))
                throw new IOException("control ancestor identity or visibility differs");
            if (!yes(selected, "visible") || !yes(selected, "enabled")) throw new Unavailable("control ancestor is not ready");
            if (yes(selected, "clickable")) return id;
            String parent = text(selected, "parent"); JsonObject found = null;
            for (JsonElement element : surface.getAsJsonArray("nodes")) {
                JsonObject node = element.getAsJsonObject();
                if (!parent.isBlank() && parent.equals(text(node, "id"))) {
                    if (found != null) throw new IOException("ambiguous control ancestor");
                    found = node;
                }
            }
            if (found == null) throw new Unavailable("no observed actionable control ancestor");
            selected = found;
        }
    }
    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }
    private static boolean yes(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
    }
}
