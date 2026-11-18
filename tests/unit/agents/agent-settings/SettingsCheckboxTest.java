package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

class SettingsCheckboxTest {
    private static JsonObject surface() throws Exception { return RunFiles.parse("""
        {"nodes":[{"id":"checkbox","window_id":"window","package":"com.qinggan.app.setting","class":"android.widget.CheckBox",
        "text":"English keyboard","visible":true,"present":true,"enabled":true,"clickable":true,"checkable":true,"checked":false}]}
        """); }
    @Test void reportsActualPublicNodeCheckedState() throws Exception {
        var ui = surface(); assertFalse(SettingsCheckbox.read(ui, "English keyboard").checked());
        ui.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("checked", true);
        assertEquals(new SettingsCheckbox("checkbox", true), SettingsCheckbox.read(ui, "English keyboard"));
    }
    @Test void missingSemanticsDisabledOrAmbiguousControlsNeverAuthorizeAToggle() throws Exception {
        for (String field : new String[]{"checkable", "checked", "enabled", "present", "visible"}) {
            var ui = surface(); ui.getAsJsonArray("nodes").get(0).getAsJsonObject().remove(field);
            assertThrows(IOException.class, () -> SettingsCheckbox.read(ui, "English keyboard"));
        }
        var ui = surface(); ui.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("class", "android.widget.LinearLayout");
        assertThrows(IOException.class, () -> SettingsCheckbox.read(ui, "English keyboard"));
        var duplicate = surface(); duplicate.getAsJsonArray("nodes").add(duplicate.getAsJsonArray("nodes").get(0).deepCopy());
        assertThrows(IOException.class, () -> SettingsCheckbox.read(duplicate, "English keyboard"));
    }

    /**
     * The row shape observed on the vehicle image (reading 93 of run 13f): the OEM {@code ItemSwitcherView}
     * carries {@code hint_switch_title} and {@code hint_switch_button} as siblings inside one RelativeLayout,
     * and no ancestor of the label is clickable. The row's own CheckBox is the switch.
     */
    private static JsonObject switchRow() throws Exception { return RunFiles.parse("""
        {"nodes":[
         {"id":"92","parent":"91","window_id":"window","package":"com.qinggan.app.setting","class":"android.widget.RelativeLayout",
          "resource_id":"","text":"","visible":true,"present":true,"enabled":true,"clickable":false},
         {"id":"93","parent":"92","window_id":"window","package":"com.qinggan.app.setting","class":"android.widget.CheckBox",
          "resource_id":"com.qinggan.app.setting:id/hint_switch_button","text":"","visible":true,"present":true,"enabled":true,
          "clickable":true,"checkable":true,"checked":false},
         {"id":"94","parent":"92","window_id":"window","package":"com.qinggan.app.setting","class":"android.widget.TextView",
          "resource_id":"com.qinggan.app.setting:id/hint_switch_title","text":"Cyrillic in OEM fonts","visible":true,"present":true,
          "enabled":true,"clickable":false},
         {"id":"95","parent":"92","window_id":"window","package":"com.qinggan.app.setting","class":"android.widget.TextView",
          "resource_id":"com.qinggan.app.setting:id/hint_content_bottom","text":"Requires a restart","visible":true,"present":true,
          "enabled":true,"clickable":false},
         {"id":"91","parent":"90","window_id":"window","package":"com.qinggan.app.setting","class":"android.widget.LinearLayout",
          "resource_id":"","text":"","visible":true,"present":true,"enabled":true,"clickable":false}
        ]}
        """); }

    private static JsonObject node(JsonObject surface, String id) {
        for (var item : surface.getAsJsonArray("nodes")) {
            JsonObject candidate = item.getAsJsonObject();
            if (id.equals(candidate.get("id").getAsString())) return candidate;
        }
        throw new AssertionError("fixture node missing: " + id);
    }

    @Test void theSwitchOfAnItemSwitcherRowIsResolvedBesideItsLabel() throws Exception {
        var surface = switchRow();
        assertEquals(new SettingsCheckbox("93", false), SettingsCheckbox.read(surface, "Cyrillic in OEM fonts"));
        node(surface, "93").addProperty("checked", true);
        assertEquals(new SettingsCheckbox("93", true), SettingsCheckbox.read(surface, "Cyrillic in OEM fonts"));
    }

    @Test void aRowWithoutAnObservedSwitchOrLabelIsNeverAuthorized() throws Exception {
        var withoutSwitch = switchRow(); withoutSwitch.getAsJsonArray("nodes").remove(1);
        assertThrows(IOException.class, () -> SettingsCheckbox.read(withoutSwitch, "Cyrillic in OEM fonts"));
        var withoutLabel = switchRow(); withoutLabel.getAsJsonArray("nodes").remove(2);
        assertThrows(IOException.class, () -> SettingsCheckbox.read(withoutLabel, "Cyrillic in OEM fonts"));
        var invisibleLabel = switchRow(); node(invisibleLabel, "94").addProperty("visible", false);
        assertThrows(IOException.class, () -> SettingsCheckbox.read(invisibleLabel, "Cyrillic in OEM fonts"));
        var disabledLabel = switchRow(); node(disabledLabel, "94").addProperty("enabled", false);
        assertThrows(IOException.class, () -> SettingsCheckbox.read(disabledLabel, "Cyrillic in OEM fonts"));
        var appendedCopy = switchRow(); appendedCopy.getAsJsonArray("nodes").add(node(appendedCopy, "94").deepCopy());
        assertThrows(IOException.class, () -> SettingsCheckbox.read(appendedCopy, "Cyrillic in OEM fonts"));
    }

    @Test void aDisabledOrUnreachableSwitchBesideTheLabelStaysUnavailable() throws Exception {
        for (String field : new String[]{"checkable", "checked", "enabled", "present", "visible"}) {
            var surface = switchRow(); node(surface, "93").remove(field);
            assertThrows(IOException.class, () -> SettingsCheckbox.read(surface, "Cyrillic in OEM fonts"), field);
        }
        var notASwitch = switchRow(); node(notASwitch, "93").addProperty("class", "android.widget.TextView");
        assertThrows(IOException.class, () -> SettingsCheckbox.read(notASwitch, "Cyrillic in OEM fonts"));
    }

    @Test void onlyTheLabelOwnRowSuppliesTheSwitchAndASecondSwitchFailsClosed() throws Exception {
        var foreignRow = switchRow(); node(foreignRow, "93").addProperty("parent", "91");
        assertThrows(IOException.class, () -> SettingsCheckbox.read(foreignRow, "Cyrillic in OEM fonts"));
        var secondSwitch = switchRow();
        secondSwitch.getAsJsonArray("nodes").add(node(secondSwitch, "93").deepCopy());
        secondSwitch.getAsJsonArray("nodes").get(5).getAsJsonObject().addProperty("id", "96");
        assertThrows(IOException.class, () -> SettingsCheckbox.read(secondSwitch, "Cyrillic in OEM fonts"));
        var foreignOwner = switchRow(); node(foreignOwner, "92").addProperty("package", "com.qinggan.app.launcher");
        assertThrows(IOException.class, () -> SettingsCheckbox.read(foreignOwner, "Cyrillic in OEM fonts"));
    }

    /**
     * The schema the OEM Settings page is actually read with (run-13g evidence: the accessibility snapshot used by
     * {@code settingsUi}). It declares no {@code present} on any node and no {@code read_complete} on any window,
     * and it reports window identity as a number; the legacy viewserver dump -- which declares both -- never
     * exposes the Settings window at all. Presence and window completeness therefore come from what such a
     * snapshot does state: the snapshot is {@code ok} and complete, and the window is not declared unavailable.
     */
    private static JsonObject accessibilitySurface(String status, boolean complete, String unavailableWindows) throws Exception {
        return RunFiles.parse("""
            {"screen":"primary","status":"STATUS","complete":COMPLETE,"unavailable_windows":[UNAVAILABLE],
             "windows":[{"id":16,"type":1,"layer":0,"bounds":"0,0,1920,720","package":"com.qinggan.app.setting","title":"Settings"}],
             "nodes":[
              {"id":"92","parent":"91","window_id":16,"package":"com.qinggan.app.setting","class":"android.widget.RelativeLayout",
               "resource_id":"","text":"","visible":true,"enabled":true,"clickable":false},
              {"id":"93","parent":"92","window_id":16,"package":"com.qinggan.app.setting","class":"android.widget.CheckBox",
               "resource_id":"com.qinggan.app.setting:id/hint_switch_button","text":"","visible":true,"enabled":true,
               "clickable":true,"checkable":true,"checked":false},
              {"id":"94","parent":"92","window_id":16,"package":"com.qinggan.app.setting","class":"android.widget.TextView",
               "resource_id":"com.qinggan.app.setting:id/hint_switch_title","text":"Cyrillic in OEM fonts","visible":true,
               "enabled":true,"clickable":false},
              {"id":"95","parent":"92","window_id":16,"package":"com.qinggan.app.setting","class":"android.widget.TextView",
               "resource_id":"com.qinggan.app.setting:id/hint_content_bottom","text":"Requires a restart","visible":true,
               "enabled":true,"clickable":false},
              {"id":"91","parent":"90","window_id":16,"package":"com.qinggan.app.setting","class":"android.widget.LinearLayout",
               "resource_id":"","text":"","visible":true,"enabled":true,"clickable":false}
             ]}
            """.replace("STATUS", status).replace("COMPLETE", Boolean.toString(complete)).replace("UNAVAILABLE", unavailableWindows));
    }

    @Test void theAccessibilitySchemaOfTheOemSettingsPageResolvesTheRowSwitch() throws Exception {
        var surface = accessibilitySurface("ok", true, "");
        assertEquals(new SettingsCheckbox("93", false), SettingsCheckbox.read(surface, "Cyrillic in OEM fonts"));
        var on = accessibilitySurface("ok", true, ""); node(on, "93").addProperty("checked", true);
        assertEquals(new SettingsCheckbox("93", true), SettingsCheckbox.read(on, "Cyrillic in OEM fonts"));
        assertFalse(node(surface, "93").has("present"), "the observer surface is never mutated by the reader");
        assertTrue(surface.getAsJsonArray("windows").get(0).getAsJsonObject().get("id").getAsJsonPrimitive().isNumber());
    }

    @Test void anIncompleteOrPartialAccessibilitySnapshotNeverAuthorizesTheSwitch() throws Exception {
        assertThrows(IOException.class, () -> SettingsCheckbox.read(accessibilitySurface("partial", false, ""), "Cyrillic in OEM fonts"));
        assertThrows(IOException.class, () -> SettingsCheckbox.read(accessibilitySurface("ok", false, ""), "Cyrillic in OEM fonts"));
        assertThrows(IOException.class, () -> SettingsCheckbox.read(accessibilitySurface("partial", true, ""), "Cyrillic in OEM fonts"));
    }

    @Test void aWindowDeclaredUnavailableNeverSuppliesTheSwitch() throws Exception {
        assertThrows(IOException.class, () -> SettingsCheckbox.read(accessibilitySurface("ok", true, "\"16\""), "Cyrillic in OEM fonts"));
        assertThrows(IOException.class, () -> SettingsCheckbox.read(accessibilitySurface("ok", true, "16"), "Cyrillic in OEM fonts"));
        var foreignWindow = accessibilitySurface("ok", true, ""); node(foreignWindow, "93").addProperty("window_id", 17);
        assertThrows(IOException.class, () -> SettingsCheckbox.read(foreignWindow, "Cyrillic in OEM fonts"));
    }

    @Test void aNodeDeclaredAbsentByALegacySnapshotStaysRejected() throws Exception {
        var declared = switchRow(); declared.add("windows", RunFiles.parse(
            "{\"available\":[{\"id\":\"window\",\"type\":\"1\",\"read_complete\":true,\"package\":\"com.qinggan.app.setting\"}]}")
            .getAsJsonObject().getAsJsonArray("available"));
        assertEquals(new SettingsCheckbox("93", false), SettingsCheckbox.read(declared, "Cyrillic in OEM fonts"));
        node(declared, "93").addProperty("present", false);
        assertThrows(IOException.class, () -> SettingsCheckbox.read(declared, "Cyrillic in OEM fonts"));
    }
}
