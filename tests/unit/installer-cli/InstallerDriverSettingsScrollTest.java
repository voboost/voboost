package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

/**
 * Regression for the OEM Settings page shape observed on the vehicle image: the left menu and the content
 * pane are each their own outermost {@code com.qinggan.app.setting} ScrollView, so the structural
 * "outermost content container" test alone leaves two candidates and the scroll gesture is refused as
 * ambiguous. The left menu is identified by observation -- it is the scroll container of the appended
 * Voboost entry -- and only that one is excluded.
 */
class InstallerDriverSettingsScrollTest {
    private static final String MENU_SCROLL = "28", MENU_ENTRY = "39", CONTENT_SCROLL = "45";

    /** Two outermost setting ScrollViews; parents that are not setting ScrollViews, exactly as observed. */
    private static JsonObject surface(boolean withMenuEntry) throws IOException {
        String entry = withMenuEntry ? """
            {"id":"39","parent":"30","window_id":"window","package":"com.qinggan.app.setting","class":"android.widget.RadioButton",
             "text":"Voboost","visible":true,"enabled":true,"scrollable":false,
             "bounds":{"x":175,"y":494,"width":268,"height":100},"clipped_bounds":{"x":175,"y":494,"width":268,"height":100}},""" : "";
        return RunFiles.parse("""
            {"format":"voyemu.ui.v1","profile":"profile","session_id":"session","screens":[{
             "screen":"primary","status":"ok","complete":true,
             "nodes":[
              {"id":"28","parent":"27","window_id":"window","resource_id":"com.qinggan.app.setting:id/layout_menu_setting_left",
               "package":"com.qinggan.app.setting","class":"android.widget.ScrollView","visible":true,"enabled":true,"scrollable":true,
               "bounds":{"x":175,"y":0,"width":268,"height":720},"clipped_bounds":{"x":175,"y":0,"width":268,"height":720}},
              {"id":"27","parent":"","window_id":"window","resource_id":"com.qinggan.app.setting:id/content_main_left_menu_layout",
               "package":"com.qinggan.app.setting","class":"android.widget.RelativeLayout","visible":true,"enabled":true,"scrollable":false,
               "bounds":{"x":175,"y":0,"width":268,"height":720},"clipped_bounds":{"x":175,"y":0,"width":268,"height":720}},
              ENTRY
              {"id":"30","parent":"29","window_id":"window","resource_id":"com.qinggan.app.setting:id/menu_radio_group",
               "package":"com.qinggan.app.setting","class":"android.widget.RadioGroup","visible":true,"enabled":true,"scrollable":false,
               "bounds":{"x":175,"y":0,"width":268,"height":720},"clipped_bounds":{"x":175,"y":0,"width":268,"height":720}},
              {"id":"29","parent":"28","window_id":"window","resource_id":"com.qinggan.app.setting:id/content_main_left_menu",
               "package":"com.qinggan.app.setting","class":"android.widget.LinearLayout","visible":true,"enabled":true,"scrollable":false,
               "bounds":{"x":175,"y":0,"width":268,"height":720},"clipped_bounds":{"x":175,"y":0,"width":268,"height":720}},
              {"id":"45","parent":"44","window_id":"window","resource_id":"",
               "package":"com.qinggan.app.setting","class":"android.widget.ScrollView","visible":true,"enabled":true,"scrollable":true,
               "bounds":{"x":485,"y":50,"width":1380,"height":670},"clipped_bounds":{"x":485,"y":50,"width":1380,"height":670}},
              {"id":"44","parent":"","window_id":"window","resource_id":"com.qinggan.app.setting:id/main_contentView",
               "package":"com.qinggan.app.setting","class":"android.widget.LinearLayout","visible":true,"enabled":true,"scrollable":false,
               "bounds":{"x":485,"y":50,"width":1380,"height":670},"clipped_bounds":{"x":485,"y":50,"width":1380,"height":670}}
             ]}]}
            """.replace("ENTRY", entry)).getAsJsonArray("screens").get(0).getAsJsonObject();
    }

    private static JsonObject node(JsonObject surface, String id) {
        for (JsonElement item : surface.getAsJsonArray("nodes")) {
            JsonObject candidate = item.getAsJsonObject();
            if (id.equals(candidate.get("id").getAsString())) return candidate;
        }
        throw new AssertionError("fixture node missing: " + id);
    }

    private static int contentCandidates(JsonObject surface, String menu) {
        int selected = 0;
        for (JsonElement item : surface.getAsJsonArray("nodes")) {
            JsonObject candidate = item.getAsJsonObject();
            if (InstallerDriver.isSettingContentScroll(surface, candidate)
                    && (menu.isBlank() || !menu.equals(candidate.get("id").getAsString()))) selected++;
        }
        return selected;
    }

    @Test void theStructuralTestAloneWouldSelectBothScrollViews() throws Exception {
        JsonObject surface = surface(true);
        assertTrue(InstallerDriver.isSettingContentScroll(surface, node(surface, MENU_SCROLL)));
        assertTrue(InstallerDriver.isSettingContentScroll(surface, node(surface, CONTENT_SCROLL)));
        assertEquals(2, contentCandidates(surface, ""));
    }

    @Test void theObservedMenuContainerIsTheOnlyExcludedScrollView() throws Exception {
        JsonObject surface = surface(true);
        assertEquals(MENU_SCROLL, InstallerDriver.menuScrollNodeId(surface));
        assertEquals(1, contentCandidates(surface, InstallerDriver.menuScrollNodeId(surface)));
        assertFalse(InstallerDriver.isSettingContentScroll(surface, node(surface, MENU_ENTRY)));
    }

    @Test void withoutTheMenuEntryNothingIsExcludedSoAnAmbiguousObservationStillFailsClosed() throws Exception {
        JsonObject surface = surface(false);
        assertEquals("", InstallerDriver.menuScrollNodeId(surface));
        assertEquals(2, contentCandidates(surface, InstallerDriver.menuScrollNodeId(surface)));
    }

    @Test void nonSettingOrNonScrollableNodesAreNeverContentCandidates() throws Exception {
        JsonObject surface = surface(true);
        for (String field : new String[]{"scrollable", "visible", "enabled"}) {
            JsonObject candidate = node(surface, CONTENT_SCROLL).deepCopy();
            candidate.addProperty(field, false);
            assertFalse(InstallerDriver.isSettingContentScroll(surface, candidate), field);
        }
        JsonObject foreign = node(surface, CONTENT_SCROLL).deepCopy();
        foreign.addProperty("package", "com.qinggan.app.launcher");
        assertFalse(InstallerDriver.isSettingContentScroll(surface, foreign));
    }
}
