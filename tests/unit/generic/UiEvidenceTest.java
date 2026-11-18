package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

class UiEvidenceTest {
    private static JsonObject surface(String extra) throws Exception {
        return RunFiles.parse("{\"nodes\":[{\"id\":\"row\",\"window_id\":\"window\",\"package\":\"com.qinggan.app.setting\",\"parent\":null,\"visible\":true,\"enabled\":true,\"clickable\":true},"
            + "{\"id\":\"caption\",\"window_id\":\"window\",\"package\":\"com.qinggan.app.setting\",\"parent\":\"row\",\"visible\":true,\"enabled\":true,\"clickable\":false,\"text\":\"Voboost\"}" + extra + "]}");
    }
    @Test void observedCaptionResolvesToItsActualClickableParent() throws Exception {
        JsonObject ui = surface("");
        assertTrue(UiEvidence.visibleText(ui, "com.qinggan.app.setting", "Voboost"));
        assertFalse(UiEvidence.visibleText(ui, "com.android.settings", "Voboost"));
        assertEquals("row", UiEvidence.actionForText(ui, "com.qinggan.app.setting", "Voboost"));
    }
    @Test void duplicateVisibleCaptionsAreAmbiguous() throws Exception {
        JsonObject ui = surface(""); ui.getAsJsonArray("nodes").add(ui.getAsJsonArray("nodes").get(1).deepCopy());
        assertThrows(IOException.class, () -> UiEvidence.actionForText(ui, "com.qinggan.app.setting", "Voboost"));
    }
    @Test void parentCannotCrossWindowOrPackageBoundaries() throws Exception {
        for (String key : java.util.List.of("window_id", "package")) {
            JsonObject ui = surface(""); ui.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty(key, "foreign");
            assertThrows(IOException.class, () -> UiEvidence.actionForText(ui, "com.qinggan.app.setting", "Voboost"));
        }
    }
    @Test void absenceRequiresCompletePositiveApplicationCoverageIncludingOffscreenLabels() throws Exception {
        JsonObject ui = surface(""); ui.addProperty("complete", true); ui.addProperty("status", "ok");
        ui.add("windows", RunFiles.parse("{\"values\":[{\"id\":\"window\",\"type\":1,\"package\":\"com.qinggan.app.setting\",\"read_complete\":true}]}").getAsJsonArray("values"));
        ui.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("present", true);
        assertThrows(IOException.class, () -> UiEvidence.requireAbsentText(ui, "com.qinggan.app.setting", "Voboost"));
        ui.getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("visible", false);
        assertThrows(IOException.class, () -> UiEvidence.requireAbsentText(ui, "com.qinggan.app.setting", "Voboost"));
        ui.getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("text", "System");
        UiEvidence.requireAbsentText(ui, "com.qinggan.app.setting", "Voboost");
        ui.addProperty("complete", false); assertThrows(IOException.class, () -> UiEvidence.requireAbsentText(ui, "com.qinggan.app.setting", "Voboost"));
        ui.addProperty("complete", true); ui.getAsJsonArray("windows").get(0).getAsJsonObject().addProperty("read_complete", false);
        assertThrows(IOException.class, () -> UiEvidence.requireAbsentText(ui, "com.qinggan.app.setting", "Voboost"));
    }
    @Test void cyclicAndDisabledParentsNeverAcquireInputAuthority() throws Exception {        JsonObject ui = surface(""); JsonObject row = ui.getAsJsonArray("nodes").get(0).getAsJsonObject();
        row.addProperty("clickable", false); row.addProperty("parent", "caption");
        assertThrows(IOException.class, () -> UiEvidence.actionForText(ui, "com.qinggan.app.setting", "Voboost"));
        row.addProperty("clickable", true); row.addProperty("enabled", false);
        assertThrows(IOException.class, () -> UiEvidence.actionForText(ui, "com.qinggan.app.setting", "Voboost"));
    }
    /** The accessibility shape: windows carry no read_complete and nodes no present, and identities are numbers. */
    private static JsonObject accessibilityObservation(boolean inputFocused) throws Exception {
        return RunFiles.parse("{\"screens\":[{\"screen\":\"primary\",\"status\":\"ok\",\"complete\":true,\"unavailable_windows\":[],"
            + "\"windows\":[{\"id\":16,\"package\":\"com.qinggan.app.vehiclesetting\",\"title\":\"New Vehicle Settings\",\"type\":1,\"active\":true,\"input_focused\":" + inputFocused + "},"
            + "{\"id\":4,\"package\":\"com.qinggan.app.launcher\",\"title\":\"\",\"type\":-1,\"active\":false,\"input_focused\":false}],"
            + "\"nodes\":[{\"id\":\"group\",\"window_id\":16,\"parent\":null,\"package\":\"com.qinggan.app.vehiclesetting\",\"resource_id\":\"com.qinggan.app.vehiclesetting:id/fragment_safe_hum_vsp\",\"class\":\"android.widget.LinearLayout\",\"visible\":true,\"enabled\":true,\"clickable\":false,\"bounds\":{\"x\":10,\"y\":20,\"width\":100,\"height\":80},\"clipped_bounds\":{\"x\":10,\"y\":20,\"width\":100,\"height\":80}},"
            + "{\"id\":\"control\",\"window_id\":16,\"parent\":\"group\",\"package\":\"com.qinggan.app.vehiclesetting\",\"resource_id\":\"com.qinggan.app.vehiclesetting:id/checkbox\",\"class\":\"android.widget.CheckBox\",\"text\":\"\",\"visible\":true,\"enabled\":true,\"clickable\":true,\"checkable\":true,\"checked\":true,\"bounds\":{\"x\":20,\"y\":30,\"width\":20,\"height\":20},\"clipped_bounds\":{\"x\":20,\"y\":30,\"width\":20,\"height\":20}}]}]}");
    }
    @Test void anAccessibilitySnapshotReachesTheVehicleReadersWithoutLosingItsOwnEvidence() throws Exception {
        JsonObject observation = accessibilityObservation(true);
        JsonObject surface = UiEvidence.surface(observation, "primary");
        var control = VehicleUi.feature(surface, VehicleUi.Feature.PEDESTRIAN_WARNING);
        assertEquals("control", control.nodeId()); assertTrue(control.checked());
        JsonObject window = UiEvidence.surface(observation, "primary").getAsJsonArray("windows").get(0).getAsJsonObject();
        assertTrue(window.get("read_complete").getAsBoolean()); assertEquals("16", window.get("id").getAsString());
        // The evidence itself stays as observed: presence and completeness are granted to the reader's copy only.
        assertFalse(observation.getAsJsonArray("screens").get(0).getAsJsonObject().getAsJsonArray("windows").get(0).getAsJsonObject().has("read_complete"));
        assertFalse(observation.getAsJsonArray("screens").get(0).getAsJsonObject().getAsJsonArray("nodes").get(0).getAsJsonObject().has("present"));
        assertTrue(observation.getAsJsonArray("screens").get(0).getAsJsonObject().getAsJsonArray("windows").get(0).getAsJsonObject().get("id").getAsJsonPrimitive().isNumber());
    }
    @Test void anApplicationWindowWithoutInputFocusIsNeverAccepted() throws Exception {
        assertThrows(IOException.class, () -> VehicleUi.feature(UiEvidence.surface(accessibilityObservation(false), "primary"), VehicleUi.Feature.PEDESTRIAN_WARNING));
    }
}
