package ru.voboost.voyemu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Actual Navi editor and IME evidence. Missing metadata never becomes an empty field or an inferred caret. */
public final class NavigationInput {
    public static final String NAVI = "com.pateonavi.naviapp";
    public static final String SEARCH_EDITOR = NAVI + ":id/dsie_normal_search";
    private NavigationInput() {}
    public static final class CanvasRequired extends IOException {
        public CanvasRequired() { super("live semantic keyboard alphabet absent; reviewed canvas route required"); }
    }
    public record Editor(JsonObject node, JsonObject window, String value, int start, int end, boolean hint) {
        public Editor { node = node.deepCopy(); window = window.deepCopy(); }
        @Override public JsonObject node() { return node.deepCopy(); }
        @Override public JsonObject window() { return window.deepCopy(); }
        public void requirePrefix(String expected) throws IOException {
            if (expected == null || expected.length() > 128 || !value.equals(expected) || hint && !expected.isEmpty()
                    || start != expected.length() || end != expected.length()) throw unavailable("committed prefix or actual UTF-16 end caret differs");
        }
        public void requireSame(Editor prior) throws IOException {
            if (text(node, "resource_id").isBlank()) throw unavailable("editor resource unavailable");
            for (String field : List.of("window_id", "resource_id", "package", "class", "bounds", "clipped_bounds"))
                if (!node.has(field) || !node.get(field).equals(prior.node.get(field))) throw unavailable("editor identity or geometry changed");
            for (String field : List.of("id", "package", "type", "bounds"))
                if (!window.has(field) || !window.get(field).equals(prior.window.get(field))) throw unavailable("editor window changed");
        }
    }
    private static UiEvidence.Unavailable unavailable(String reason) { return new UiEvidence.Unavailable("Navi input unavailable: " + reason); }
    private static boolean yes(JsonObject value, String key) { return value.has(key) && value.get(key).isJsonPrimitive() && value.getAsJsonPrimitive(key).isBoolean() && value.get(key).getAsBoolean(); }
    private static boolean no(JsonObject value, String key) { return value.has(key) && value.get(key).isJsonPrimitive() && value.getAsJsonPrimitive(key).isBoolean() && !value.get(key).getAsBoolean(); }
    private static String text(JsonObject value, String key) { return value.has(key) && value.get(key).isJsonPrimitive() && value.getAsJsonPrimitive(key).isString() ? value.get(key).getAsString() : ""; }
    private static int integer(JsonObject value, String key) throws IOException {
        if (!value.has(key) || !value.get(key).isJsonPrimitive() || !value.getAsJsonPrimitive(key).isNumber()
                || !value.get(key).getAsString().matches("-?[0-9]{1,9}")) throw unavailable("actual integer " + key + " missing");
        return value.get(key).getAsInt();
    }
    private static void complete(JsonObject surface) throws IOException {
        if (!yes(surface, "complete") || !"ok".equals(text(surface, "status")) || !surface.has("nodes") || !surface.get("nodes").isJsonArray()
                || !surface.has("windows") || !surface.get("windows").isJsonArray() || surface.getAsJsonArray("nodes").size() > 12000) throw unavailable("complete current UI coverage required");
    }
    private static boolean windowReadComplete(JsonObject surface, JsonObject window) {
        if (window.has("read_complete")) return yes(window, "read_complete");
        // Default Accessibility reports window read coverage on the complete surface.
        return Set.of("api28_default_display", "api30_default_display").contains(text(surface, "scope"))
                && surface.has("unavailable_windows") && surface.get("unavailable_windows").isJsonArray()
                && surface.getAsJsonArray("unavailable_windows").isEmpty();
    }
    private static JsonObject window(JsonObject surface, JsonObject node, int type, boolean focused) throws IOException {
        JsonObject found = null;
        for (JsonElement item : surface.getAsJsonArray("windows")) {
            JsonObject value = item.getAsJsonObject();
            if (value.has("id") && value.get("id").equals(node.get("window_id"))) {
                if (found != null) throw unavailable("duplicate window"); found = value;
            }
        }
        if (found == null || !text(node, "package").equals(text(found, "package")) || integer(found, "type") != type || !windowReadComplete(surface, found)
                || focused && (!yes(found, "active") || !yes(found, "input_focused"))) throw unavailable("current window owner/focus differs");
        return found;
    }
    private static List<JsonObject> owned(JsonObject surface) throws IOException {
        complete(surface); List<JsonObject> result = new ArrayList<>();
        for (JsonElement item : surface.getAsJsonArray("nodes")) {
            JsonObject node = item.getAsJsonObject();
            if (NAVI.equals(text(node, "package")) && yes(node, "visible") && yes(node, "enabled")) {
                window(surface, node, 1, true); result.add(node);
            }
        }
        return result;
    }
    public static Editor editor(JsonObject surface) throws IOException {
        try {
            List<JsonObject> editors = owned(surface).stream().filter(node -> "android.widget.EditText".equals(text(node, "class")) && yes(node, "focused")).toList();
            if (editors.size() != 1) throw unavailable("unique focused editor required");
            JsonObject node = editors.get(0);
            if (!no(node, "text_redacted") || !"available".equals(text(node, "editor_status"))
                    || !"available".equals(text(node, "selection_status")) || !"utf16".equals(text(node, "selection_unit"))
                    || !node.has("text") || !node.get("text").isJsonPrimitive() || !node.getAsJsonPrimitive("text").isString()
                    || !node.has("hint") || !node.get("hint").isJsonPrimitive() || !node.getAsJsonPrimitive("hint").isString()
                    || !yes(node, "showing_hint") && !no(node, "showing_hint")) throw unavailable("non-password committed text/hint/selection metadata unavailable");
            String value = text(node, "text"), hint = text(node, "hint");
            if (value.length() > 4096 || hint.length() > 4096) throw unavailable("text exceeds bound");
            boolean showing = yes(node, "showing_hint");
            if (showing) {
                if (!value.isEmpty() && !value.equals(hint)) throw unavailable("hint differs from displayed text"); value = "";
            }
            int start = integer(node, "selection_start"), end = integer(node, "selection_end");
            if (start < 0 || end < 0 || start > value.length() || end > value.length()) throw unavailable("UTF-16 selection unavailable or out of range");
            return new Editor(node, window(surface, node, 1, true), value, start, end, showing);
        } catch (RuntimeException malformed) { throw new IOException("malformed Navi editor observation", malformed); }
    }
    /** Returns the next observed safety, search-entry or search-focus action. */
    public static JsonObject setup(JsonObject surface) throws IOException {
        List<JsonObject> nodes = owned(surface);
        List<JsonObject> titles = nodes.stream().filter(node -> (NAVI + ":id/agree_title").equals(text(node, "resource_id")) && "android.widget.TextView".equals(text(node, "class")) && !text(node, "text").isBlank()).toList();
        List<JsonObject> bodies = nodes.stream().filter(node -> (NAVI + ":id/agree_context").equals(text(node, "resource_id")) && "android.widget.TextView".equals(text(node, "class")) && !text(node, "text").isBlank()).toList();
        List<JsonObject> agrees = nodes.stream().filter(node -> (NAVI + ":id/btn_agree").equals(text(node, "resource_id")) && "android.widget.TextView".equals(text(node, "class")) && yes(node, "clickable") && (!text(node, "text").isBlank() || !text(node, "description").isBlank())).toList();
        if (!titles.isEmpty() || !bodies.isEmpty() || !agrees.isEmpty()) {
            if (titles.size() != 1 || bodies.size() != 1 || agrees.size() != 1
                    || !titles.get(0).get("window_id").equals(agrees.get(0).get("window_id"))
                    || !bodies.get(0).get("window_id").equals(agrees.get(0).get("window_id"))
                    || nodes.stream().anyMatch(node -> "android.widget.EditText".equals(text(node, "class")))) throw unavailable("safety dialog is incomplete or ambiguous");
            return agrees.get(0).deepCopy();
        }
        List<JsonObject> editors = nodes.stream()
                .filter(node -> "android.widget.EditText".equals(text(node, "class"))).toList();
        if (!editors.isEmpty()) {
            if (editors.size() != 1 || !SEARCH_EDITOR.equals(text(editors.get(0), "resource_id")))
                throw unavailable("unique normal-search editor required");
            JsonObject editor = editors.get(0);
            if (yes(editor, "focused")) return null;
            if (!no(editor, "focused") || !yes(editor, "clickable")) throw unavailable("normal-search focus action unavailable");
            JsonObject header = searchHeader(nodes, editor);
            if (nodes.stream().filter(node -> searchBackSibling(node, editor)
                    && text(header, "id").equals(text(node, "parent"))).count() != 1)
                throw unavailable("unique normal-search back sibling required to focus editor");
            return editor.deepCopy();
        }
        List<JsonObject> entries = nodes.stream().filter(node -> (NAVI + ":id/ll_normal_search").equals(text(node, "resource_id")) && "android.widget.LinearLayout".equals(text(node, "class")) && yes(node, "clickable") && !text(node, "description").isBlank()).toList();
        List<JsonObject> labels = nodes.stream().filter(node -> (NAVI + ":id/tv_normal_search").equals(text(node, "resource_id")) && "android.widget.TextView".equals(text(node, "class")) && !text(node, "text").isBlank()).toList();
        if (entries.isEmpty() && labels.isEmpty()) return null;
        if (entries.size() != 1 || labels.size() != 1 || !entries.get(0).get("window_id").equals(labels.get(0).get("window_id"))
                || !entries.get(0).get("id").equals(labels.get(0).get("parent"))) throw unavailable("search entry relation is incomplete or ambiguous");
        return entries.get(0).deepCopy();
    }
    private static JsonObject searchHeader(List<JsonObject> nodes, JsonObject editor) throws IOException {
        String parent = text(editor, "parent");
        List<JsonObject> matches = nodes.stream().filter(node -> !parent.isBlank() && parent.equals(text(node, "id"))
                && editor.get("window_id").equals(node.get("window_id"))
                && (NAVI + ":id/ll_normal_search").equals(text(node, "resource_id"))).toList();
        if (matches.size() != 1) throw unavailable("unique normal-search header unavailable");
        return matches.get(0);
    }
    private static boolean searchBackSibling(JsonObject node, JsonObject editor) {
        return (NAVI + ":id/iv_back").equals(text(node, "resource_id"))
                && "android.widget.ImageView".equals(text(node, "class")) && yes(node, "clickable")
                && editor.get("window_id").equals(node.get("window_id"))
                && !text(editor, "parent").isBlank() && text(editor, "parent").equals(text(node, "parent"));
    }
    public static Editor searchEditor(JsonObject surface) throws IOException {
        Editor editor = editor(surface);
        if (!SEARCH_EDITOR.equals(text(editor.node, "resource_id"))) throw unavailable("probed normal-search editor resource differs");
        return editor;
    }
    /** Actual A9 search-header back control. */
    public static JsonObject searchBack(JsonObject surface) throws IOException {
        Editor editor = searchEditor(surface);
        List<JsonObject> matches = owned(surface).stream().filter(node -> searchBackSibling(node, editor.node)).toList();
        if (matches.size() != 1) throw unavailable("unique actual search-header back sibling unavailable");
        return matches.get(0).deepCopy();
    }
    /** A layout change is an actual unique IME control, never a guessed canvas point. */
    public static JsonObject switchLayout(JsonObject surface, String language) throws IOException {
        editor(surface);
        String pattern = switch (language) {
            case "en" -> "EN|English|英语|Английский";
            case "ru" -> "RU|Русский|Russian|俄语";
            default -> throw unavailable("closed keyboard layout required");
        };
        List<JsonObject> matches = new ArrayList<>();
        for (JsonElement item : surface.getAsJsonArray("nodes")) {
            JsonObject node = item.getAsJsonObject(); String owner = text(node,"package");
            if (!owner.matches("com\\.qinggan\\.app\\.qgime(?:\\.second)?") || !yes(node,"visible") || !yes(node,"enabled") || !yes(node,"clickable")) continue;
            window(surface,node,2,false);
            String label=text(node,"text").isEmpty()?text(node,"description"):text(node,"text");
            if (label.matches(pattern)) matches.add(node);
        }
        if (matches.isEmpty()) throw new CanvasRequired();
        if (matches.size()!=1) throw unavailable("requested layout switch is ambiguous");
        return matches.get(0).deepCopy();
    }
    public static JsonObject key(JsonObject surface, String character) throws IOException {
        editor(surface); // Every key starts from a fresh current focused non-password editor.
        if (character == null || character.codePointCount(0, character.length()) != 1 || !character.matches("[A-Za-zА-Яа-яЁё]")) throw unavailable("closed alphabet key required");
        boolean russian = character.matches("[А-Яа-яЁё]");
        Set<String> alphabet = new HashSet<>(); List<JsonObject> matches = new ArrayList<>();
        for (JsonElement item : surface.getAsJsonArray("nodes")) {
            JsonObject node = item.getAsJsonObject(); String owner = text(node, "package");
            if (!owner.matches("com\\.qinggan\\.app\\.qgime(?:\\.second)?") || !yes(node, "visible") || !yes(node, "enabled") || !yes(node, "clickable")) continue;
            window(surface, node, 2, false);
            String label = text(node, "text").isEmpty() ? text(node, "description") : text(node, "text");
            if (label.matches(russian ? "[А-Яа-яЁё]" : "[A-Za-z]")) alphabet.add(label);
            if (label.equals(character)) matches.add(node);
        }
        if (alphabet.size() < 8) throw new CanvasRequired();
        if (matches.size() != 1) throw unavailable("unique exact visible keyboard key missing");
        return matches.get(0).deepCopy();
    }
}
