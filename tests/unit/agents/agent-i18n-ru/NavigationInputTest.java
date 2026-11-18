package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Public UI-shaped fixtures only; no keyboard or emulator execution. */
class NavigationInputTest {
    private static JsonObject bounds() {
        JsonObject result = new JsonObject(); result.addProperty("x", 10); result.addProperty("y", 20);
        result.addProperty("width", 100); result.addProperty("height", 40); return result;
    }
    private static JsonObject window(String id, String owner, int type) {
        JsonObject node = new JsonObject(); node.addProperty("id", id); node.addProperty("package", owner); node.addProperty("type", type);
        node.add("bounds", bounds()); node.addProperty("read_complete", true); node.addProperty("active", true); node.addProperty("input_focused", true); return node;
    }
    private static JsonObject node(String id, String window, String owner, String klass, String label) {
        JsonObject node = new JsonObject(); node.addProperty("id", id); node.addProperty("window_id", window); node.addProperty("package", owner);
        node.addProperty("class", klass); node.addProperty("text", label); node.addProperty("description", ""); node.addProperty("visible", true);
        node.addProperty("enabled", true); node.addProperty("clickable", true); node.addProperty("focused", true); node.addProperty("text_redacted", false);
        node.add("bounds", bounds()); node.add("clipped_bounds", bounds()); node.addProperty("resource_id", owner + ":id/" + id); return node;
    }
    static JsonObject surface() {
        JsonObject result = new JsonObject(); result.addProperty("complete", true); result.addProperty("status", "ok");
        JsonArray windows = new JsonArray(); windows.add(window("navi", NavigationInput.NAVI, 1)); windows.add(window("ime", "com.qinggan.app.qgime", 2));
        result.add("windows", windows); result.add("nodes", new JsonArray()); return result;
    }
    static JsonObject editor(JsonObject ui, String text) {
        JsonObject node = node("search", "navi", NavigationInput.NAVI, "android.widget.EditText", text);
        node.addProperty("editor_status", "available"); node.addProperty("selection_status", "available"); node.addProperty("selection_unit", "utf16");
        node.addProperty("hint", ""); node.addProperty("showing_hint", false); node.addProperty("selection_start", text.length()); node.addProperty("selection_end", text.length());
        ui.getAsJsonArray("nodes").add(node); return node;
    }
    @Test void defaultAccessibilityWindowCoverageComesFromTheSurface() throws Exception {
        JsonObject ui = surface();
        JsonObject first = editor(ui, "Москва");
        ui.getAsJsonArray("windows").get(0).getAsJsonObject().remove("read_complete");
        ui.add("unavailable_windows", new JsonArray());
        for (String scope : List.of("api28_default_display", "api30_default_display")) {
            ui.addProperty("scope", scope);
            var prior = NavigationInput.editor(ui);
            first.addProperty("id", "another-observation");
            NavigationInput.editor(ui).requireSame(prior);
        }
        ui.getAsJsonArray("unavailable_windows").add("navi");
        assertThrows(IOException.class, () -> NavigationInput.editor(ui));
        ui.add("unavailable_windows", new JsonArray());
        ui.addProperty("scope", "all_attached");
        assertThrows(IOException.class, () -> NavigationInput.editor(ui));
        ui.addProperty("scope", "api28_default_display");
        ui.getAsJsonArray("windows").get(0).getAsJsonObject().addProperty("read_complete", false);
        assertThrows(IOException.class, () -> NavigationInput.editor(ui));
    }

    @Test void observedNormalSearchEditorCanReceiveExactlyTheFocusAction() throws Exception {
        JsonObject ui = surface();
        JsonObject input = editor(ui, "");
        input.addProperty("resource_id", NavigationInput.SEARCH_EDITOR);
        input.addProperty("parent", "header");
        input.addProperty("focused", false);
        JsonObject header = node("header", "navi", NavigationInput.NAVI, "android.widget.RelativeLayout", "");
        header.addProperty("resource_id", NavigationInput.NAVI + ":id/ll_normal_search");
        JsonObject back = node("back", "navi", NavigationInput.NAVI, "android.widget.ImageView", "");
        back.addProperty("resource_id", NavigationInput.NAVI + ":id/iv_back");
        back.addProperty("parent", "header");
        ui.getAsJsonArray("nodes").add(header);
        ui.getAsJsonArray("nodes").add(back);
        assertEquals(input, NavigationInput.setup(ui));
        input.addProperty("focused", true);
        assertNull(NavigationInput.setup(ui));
        input.addProperty("focused", false);
        back.addProperty("parent", "foreign");
        assertThrows(IOException.class, () -> NavigationInput.setup(ui));
    }

    @Test void normalSearchResetUsesOnlyTheProbedBackSiblingAndNeverAnotherNaviEditor()throws Exception {
        JsonObject ui=surface();var input=editor(ui,"Москва");input.addProperty("resource_id",NavigationInput.NAVI+":id/dsie_normal_search");input.addProperty("parent","search-header");
        var back=node("back","navi",NavigationInput.NAVI,"android.widget.ImageView","");back.addProperty("resource_id",NavigationInput.NAVI+":id/iv_back");back.addProperty("parent","search-header");ui.getAsJsonArray("nodes").add(back);
        assertEquals("back",NavigationInput.searchBack(ui).get("id").getAsString());
        back.addProperty("parent","foreign-header");assertThrows(IOException.class,()->NavigationInput.searchBack(ui));back.addProperty("parent","search-header");input.addProperty("resource_id",NavigationInput.NAVI+":id/foreign-editor");assertThrows(IOException.class,()->NavigationInput.searchBack(ui));
        input.addProperty("resource_id",NavigationInput.NAVI+":id/dsie_normal_search");input.remove("parent");back.remove("parent");assertThrows(IOException.class,()->NavigationInput.searchBack(ui));
    }
    @Test void layoutSwitchUsesOnlyUniqueCurrentImeControlAndPreservesEditorAdmission() throws Exception {
        JsonObject ui=surface();editor(ui,"москва");
        assertThrows(NavigationInput.CanvasRequired.class,()->NavigationInput.switchLayout(ui,"en"));
        JsonObject button=node("switch","ime","com.qinggan.app.qgime","android.widget.Button","EN");ui.getAsJsonArray("nodes").add(button);
        assertEquals("switch",NavigationInput.switchLayout(ui,"en").get("id").getAsString());
        assertThrows(IOException.class,()->NavigationInput.switchLayout(ui,"any"));
        ui.getAsJsonArray("nodes").add(button.deepCopy());assertThrows(IOException.class,()->NavigationInput.switchLayout(ui,"en"));
        ui.getAsJsonArray("nodes").remove(ui.getAsJsonArray("nodes").size()-1);
        ui.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("selection_status","unsupported");
        assertThrows(IOException.class,()->NavigationInput.switchLayout(ui,"en"));
    }
    @Test void exactCommittedPrefixUsesObservedUtf16OffsetsIncludingSupplementaryCharacters() throws Exception {
        for (String value : List.of("", "moscow", "Москва", "Ёё", "Москваmoscowё", "A😀Б")) {
            JsonObject ui = surface(); editor(ui, value); var observed = NavigationInput.editor(ui); observed.requirePrefix(value);
            assertThrows(IOException.class, () -> observed.requirePrefix(value + "x"));
        }
    }
    @Test void missingUnsupportedRedactedUnknownOrMiddleCaretNeverBecomesEmptyOrEnd() throws Exception {
        for (String field : List.of("editor_status", "selection_status", "selection_unit", "selection_start", "selection_end", "showing_hint", "text_redacted", "hint")) {
            JsonObject ui = surface(); editor(ui, "Москва").remove(field); assertThrows(IOException.class, () -> NavigationInput.editor(ui));
        }
        for (String field : List.of("editor_status", "selection_status")) {
            JsonObject ui = surface(); editor(ui, "").addProperty(field, "unsupported"); assertThrows(IOException.class, () -> NavigationInput.editor(ui));
        }
        JsonObject unknown = surface(); JsonObject node = editor(unknown, ""); node.addProperty("selection_start", -1); node.addProperty("selection_end", -1);
        assertThrows(IOException.class, () -> NavigationInput.editor(unknown));
        JsonObject middle = surface(); JsonObject inside = editor(middle, "Москва"); inside.addProperty("selection_start", 2); inside.addProperty("selection_end", 2);
        var observed = NavigationInput.editor(middle); assertThrows(IOException.class, () -> observed.requirePrefix("Москва"));
        JsonObject redacted = surface(); editor(redacted, "private").addProperty("text_redacted", true); assertThrows(IOException.class, () -> NavigationInput.editor(redacted));
    }
    @Test void visibleHintIsNotCommittedTextAndDoesNotInventCaret() throws Exception {
        JsonObject ui = surface(); JsonObject node = editor(ui, "Search"); node.addProperty("hint", "Search"); node.addProperty("showing_hint", true);
        node.addProperty("selection_start", 0); node.addProperty("selection_end", 0);
        NavigationInput.editor(ui).requirePrefix(""); assertEquals("", NavigationInput.editor(ui).value());
        node.addProperty("text", "different"); assertThrows(IOException.class, () -> NavigationInput.editor(ui));
    }
    @Test void duplicateEditorForeignWindowPartialCoverageAndWindowFocusFailClosed() throws Exception {
        JsonObject duplicate = surface(); JsonObject node = editor(duplicate, ""); duplicate.getAsJsonArray("nodes").add(node.deepCopy());
        assertThrows(IOException.class, () -> NavigationInput.editor(duplicate));
        for (String field : List.of("active", "input_focused", "read_complete")) {
            JsonObject ui = surface(); editor(ui, ""); ui.getAsJsonArray("windows").get(0).getAsJsonObject().addProperty(field, false);
            assertThrows(IOException.class, () -> NavigationInput.editor(ui));
        }
        JsonObject ui = surface(); editor(ui, ""); ui.addProperty("complete", false); assertThrows(IOException.class, () -> NavigationInput.editor(ui));
    }
    @Test void semanticKeyRequiresLiveImeAlphabetAndExactUniqueRequestedCharacter() throws Exception {
        JsonObject ui = surface(); editor(ui, "");
        for (char character : "moscwxab".toCharArray()) ui.getAsJsonArray("nodes").add(node("key" + character, "ime", "com.qinggan.app.qgime", "android.widget.Button", "" + character));
        assertEquals("keym", NavigationInput.key(ui, "m").get("id").getAsString());
        ui.getAsJsonArray("nodes").add(node("other-m", "ime", "com.qinggan.app.qgime", "android.widget.Button", "m"));
        assertThrows(IOException.class, () -> NavigationInput.key(ui, "m"));
        JsonObject canvas = surface(); editor(canvas, ""); assertThrows(IOException.class, () -> NavigationInput.key(canvas, "m"));
    }
    @Test void observedSafetyAndSearchRelationsCannotUseForeignOrPartialControls() throws Exception {
        JsonObject ui = surface();
        for (String id : List.of("agree_title", "agree_context", "btn_agree")) ui.getAsJsonArray("nodes").add(node(id, "navi", NavigationInput.NAVI, "android.widget.TextView", "Observed " + id));
        assertEquals("btn_agree", NavigationInput.setup(ui).get("id").getAsString());
        ui.getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("window_id", "foreign"); assertThrows(IOException.class, () -> NavigationInput.setup(ui));
        JsonObject search = surface(); JsonObject row = node("ll_normal_search", "navi", NavigationInput.NAVI, "android.widget.LinearLayout", ""); row.addProperty("description", "Actual search");
        JsonObject label = node("tv_normal_search", "navi", NavigationInput.NAVI, "android.widget.TextView", "Search"); label.addProperty("parent", "ll_normal_search");
        search.getAsJsonArray("nodes").add(row); search.getAsJsonArray("nodes").add(label);
        assertEquals("ll_normal_search", NavigationInput.setup(search).get("id").getAsString());
        label.addProperty("parent", "other"); assertThrows(IOException.class, () -> NavigationInput.setup(search));
    }
}
