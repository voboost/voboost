package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class SettingsRemovalTest {
    private static JsonObject node(String id, String window, String label, boolean clickable) {
        JsonObject node = new JsonObject(); node.addProperty("id", id); node.addProperty("window_id", window);
        node.addProperty("package", "com.qinggan.app.setting"); node.addProperty("text", label);
        for (String field : new String[]{"present", "visible", "enabled"}) node.addProperty(field, true);
        node.addProperty("clickable", clickable); return node;
    }
    private static JsonObject dialog() {
        JsonObject surface = new JsonObject(); JsonArray nodes = new JsonArray(), windows = new JsonArray();
        JsonObject window = new JsonObject(); window.addProperty("id", "dialog"); window.addProperty("package", "com.qinggan.app.setting"); window.addProperty("read_complete", true); windows.add(window);
        nodes.add(node("message", "dialog", "Completely remove Voboost. After confirming, do not interact with the multimedia system and wait for one final multimedia reboot.", false));
        nodes.add(node("confirm", "dialog", "Remove", true)); nodes.add(node("cancel", "dialog", "Cancel", true));
        nodes.add(node("unrelated", "panel", "Remove", true));
        surface.add("nodes", nodes); surface.add("windows", windows); return surface;
    }
    @Test void confirmationUsesBothControlsInTheActualMessageWindow() throws Exception {
        var result = SettingsRemoval.confirmation(dialog());
        assertEquals("en", result.language()); assertEquals("confirm", result.confirm()); assertEquals("cancel", result.cancel()); assertEquals("dialog", result.window());
    }
    @Test void confirmationCannotUsePanelDescriptionOrForeignCancelControl() {
        for (String field : new String[]{"window_id", "package"}) {
            var ui = dialog(); ui.getAsJsonArray("nodes").get(2).getAsJsonObject().addProperty(field, "foreign");
            assertThrows(IOException.class, () -> SettingsRemoval.confirmation(ui));
        }
        var ui = dialog(); ui.getAsJsonArray("windows").get(0).getAsJsonObject().addProperty("read_complete", false);
        assertThrows(IOException.class, () -> SettingsRemoval.confirmation(ui));
    }
    @Test void duplicateDisabledAndMissingConfirmationNeverAuthorizeRemoval() {
        var duplicate = dialog(); duplicate.getAsJsonArray("nodes").add(node("other", "dialog", "Remove", true));
        assertThrows(IOException.class, () -> SettingsRemoval.confirmation(duplicate));
        for (String field : new String[]{"visible", "enabled", "present"}) {
            var ui = dialog(); ui.getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty(field, false);
            assertThrows(IOException.class, () -> SettingsRemoval.confirmation(ui));
        }
        var missing = dialog(); missing.getAsJsonArray("nodes").remove(0);
        assertThrows(IOException.class, () -> SettingsRemoval.confirmation(missing));
    }
    @Test void uninstallSectionHeadingCannotReplaceTheEnabledButton() throws Exception {
        var ui = dialog(); JsonArray nodes = new JsonArray();
        nodes.add(node("heading", "panel", "卸载", false)); nodes.add(node("button", "panel", "卸载", true)); ui.add("nodes", nodes);
        assertEquals("button", SettingsRemoval.button(ui));
        nodes.add(node("other", "panel", "卸载", true)); assertThrows(IOException.class, () -> SettingsRemoval.button(ui));
    }
}
