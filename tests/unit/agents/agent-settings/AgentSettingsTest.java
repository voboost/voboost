package ru.voboost.tests.agents;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

class AgentSettingsTest {
    private static JsonObject catalog() throws Exception { return RunFiles.parse("""
        {"agents":[{"id":"keyboard-en","status":"stable","activation":{"type":"checkbox"},"disables":["keyboard-ru"],"title":{"en":"English keyboard"}},
        {"id":"keyboard-ru","status":"stable","activation":{"type":"checkbox"},"disables":["keyboard-en"],"title":{"en":"Russian keyboard"}},
        {"id":"ui","kind":"executor"}]}
        """); }
    private static JsonObject desired() throws Exception { return RunFiles.parse("""
        {"keyboard-en":{"enabled":false,"options":{"custom":"preserved"}},"keyboard-ru":{"enabled":true,"options":{}}}
        """); }
    @Test void catalogConflictsDisableOnlyDeclaredPeerAndPreserveEveryOption() throws Exception {
        var original = desired(); var next = AgentSettings.transition(catalog(), original, "keyboard-en", true);
        assertFalse(original.getAsJsonObject("keyboard-en").get("enabled").getAsBoolean());
        assertTrue(next.getAsJsonObject("keyboard-en").get("enabled").getAsBoolean());
        assertFalse(next.getAsJsonObject("keyboard-ru").get("enabled").getAsBoolean());
        assertEquals(original.getAsJsonObject("keyboard-en").get("options"), next.getAsJsonObject("keyboard-en").get("options"));
        var disabled = AgentSettings.transition(catalog(), original, "keyboard-en", false); assertEquals(original, disabled);
        assertEquals("English keyboard", AgentSettings.title(AgentSettings.checkboxes(catalog()).get("keyboard-en"), "en"));
    }
    @Test void vehicleOnlyRetainsCompleteCatalogAndOptionsButEnablesExactlyTwo() throws Exception {
        JsonObject catalog = catalog(), before = desired();
        catalog.getAsJsonArray("agents").add(RunFiles.parse("""
            {"id":"settings","status":"stable","activation":{"type":"config","key":"voboost-startup","value":"interface"}}
            """));
        assertFalse(AgentSettings.checkboxes(catalog).containsKey("settings"));

        for (String id : java.util.List.of("vehicle-drive-forced-ev", "vehicle-drive-pedestrian-warning")) {
            JsonObject entry = RunFiles.parse("{\"status\":\"stable\",\"activation\":{\"type\":\"checkbox\"},\"disables\":[]}");
            entry.addProperty("id", id); catalog.getAsJsonArray("agents").add(entry);
            before.add(id, RunFiles.parse("{\"enabled\":false,\"options\":{\"retained\":42}}"));
        }
        JsonObject unchanged = catalog.deepCopy(), original = before.deepCopy();
        JsonObject next = AgentSettings.vehicleOnly(catalog, before);
        assertEquals(unchanged, catalog); assertEquals(original, before);
        assertEquals(before.keySet(), next.keySet());
        assertFalse(next.has("settings"));
        for (String id : next.keySet()) {
            assertEquals(id.startsWith("vehicle-drive-"), next.getAsJsonObject(id).get("enabled").getAsBoolean());
            assertEquals(before.getAsJsonObject(id).get("options"), next.getAsJsonObject(id).get("options"));
        }
        assertThrows(IOException.class, () -> AgentSettings.vehicleOnly(catalog(), desired()));
    }

    @Test void missingDuplicateOrUnknownRowsNeverProduceAnExpectedPassSnapshot() throws Exception {
        var catalog = catalog(); catalog.getAsJsonArray("agents").add(catalog.getAsJsonArray("agents").get(0).deepCopy());
        assertThrows(IOException.class, () -> AgentSettings.checkboxes(catalog));
        var missing = desired(); missing.remove("keyboard-ru");
        assertThrows(IOException.class, () -> AgentSettings.transition(catalog(), missing, "keyboard-en", true));
        assertThrows(IOException.class, () -> AgentSettings.transition(catalog(), desired(), "unknown", true));
        assertThrows(IOException.class, () -> AgentSettings.title(AgentSettings.checkboxes(catalog()).get("keyboard-en"), "zh"));
    }
    @Test void malformedConflictsAndNonbooleanDesiredStateAreRejected() throws Exception {
        for (String conflict : new String[]{"keyboard-en", "unknown"}) {
            var catalog = catalog(); catalog.getAsJsonArray("agents").get(0).getAsJsonObject().getAsJsonArray("disables").set(0, new com.google.gson.JsonPrimitive(conflict));
            assertThrows(IOException.class, () -> AgentSettings.transition(catalog, desired(), "keyboard-en", true));
        }
        var before = desired(); before.getAsJsonObject("keyboard-en").addProperty("enabled", "false");
        assertThrows(IOException.class, () -> AgentSettings.transition(catalog(), before, "keyboard-en", true));
    }
}
