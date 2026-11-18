package ru.voboost.tests.agents;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

class HiddenTargetsTest {
    private static com.google.gson.JsonObject catalog() throws IOException {
        return RunFiles.parse("{\"agents\":[{\"id\":\"launcher\",\"status\":\"stable\",\"activation\":{\"type\":\"checkbox\",\"default\":true},\"targets\":[{\"platform\":\"android\",\"process\":\"com.qinggan.app.launcher\"},{\"platform\":\"android\",\"process\":\"not.running\"}]}]}");
    }
    @Test void startupProofIsDynamicAndNeverPromotesInjectionStateToAgentEffects() throws Exception {
        var current = RuntimeObservation.read(DaemonRuntimeObservationTest.observation(), DaemonRuntimeObservationTest.IDENTITY);
        var rows = HiddenTargets.read(catalog(), current, Set.of("com.qinggan.app.launcher"), HiddenTargets.snapshot(catalog(), current, Set.of("launcher")), null);
        assertEquals(2, rows.size()); assertTrue(rows.get(0).getAsJsonObject().get("running").getAsBoolean());
        assertFalse(rows.get(1).getAsJsonObject().get("running").getAsBoolean());
        for (var row : rows) assertFalse(row.getAsJsonObject().get("functionalAcceptance").getAsBoolean());
    }
    @Test void enabledRunningAndroidAndQnxTargetsNeedTheirCurrentActiveRows() throws Exception {
        var current = RuntimeObservation.read(DaemonRuntimeObservationTest.observation(), DaemonRuntimeObservationTest.IDENTITY);
        assertThrows(HiddenTargets.Pending.class, () -> HiddenTargets.read(catalog(), current, Set.of("com.qinggan.app.launcher", "not.running"), HiddenTargets.snapshot(catalog(), current, Set.of("launcher")), null));
        var nativeCatalog = catalog(); nativeCatalog.getAsJsonArray("agents").get(0).getAsJsonObject().getAsJsonArray("targets").get(1).getAsJsonObject().addProperty("platform", "qnx");
        assertTrue(assertThrows(IOException.class, () -> HiddenTargets.read(nativeCatalog, current, Set.of("com.qinggan.app.launcher"), HiddenTargets.snapshot(nativeCatalog, current, Set.of("launcher")), null)).getMessage().contains("authenticated QNX identity"));
    }
    @Test void rebootCannotResetThePriorEnabledSetOrParametersAndStillPass() throws Exception {
        var original = DaemonRuntimeObservationTest.observation();
        var current = RuntimeObservation.read(original, DaemonRuntimeObservationTest.IDENTITY);
        var expected = HiddenTargets.snapshot(catalog(), current, Set.of("launcher"));
        var entry = original.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("config").getAsJsonObject("data").getAsJsonObject("agents").getAsJsonObject("launcher");
        entry.addProperty("enabled", false);
        var reset = RuntimeObservation.read(original, DaemonRuntimeObservationTest.IDENTITY);
        assertThrows(IOException.class, () -> HiddenTargets.read(catalog(), reset, Set.of("com.qinggan.app.launcher"), expected, null));
        entry.addProperty("enabled", true); entry.getAsJsonObject("options").addProperty("layout", "changed");
        var changed = RuntimeObservation.read(original, DaemonRuntimeObservationTest.IDENTITY);
        assertThrows(IOException.class, () -> HiddenTargets.read(catalog(), changed, Set.of("com.qinggan.app.launcher"), expected, null));
        assertThrows(IOException.class, () -> HiddenTargets.snapshot(catalog(), current, Set.of()));
    }
    @Test void losingOneOfTwoPriorEnabledAgentsCannotShrinkTheExpectedSet() throws Exception {
        var input = DaemonRuntimeObservationTest.observation();
        var agents = input.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("config").getAsJsonObject("data").getAsJsonObject("agents");
        agents.add("font-cyrillic", RunFiles.parse("{\"enabled\":true,\"options\":{}}"));
        var two = catalog(); var font = two.getAsJsonArray("agents").get(0).deepCopy().getAsJsonObject(); font.addProperty("id", "font-cyrillic"); two.getAsJsonArray("agents").add(font);
        var before = RuntimeObservation.read(input, DaemonRuntimeObservationTest.IDENTITY);
        var expected = HiddenTargets.snapshot(two, before, Set.of("launcher", "font-cyrillic"));
        agents.getAsJsonObject("font-cyrillic").addProperty("enabled", false);
        var oneLost = RuntimeObservation.read(input, DaemonRuntimeObservationTest.IDENTITY);
        assertThrows(IOException.class, () -> HiddenTargets.requireSameConfiguration(expected, two, oneLost));
        agents.getAsJsonObject("launcher").addProperty("enabled", false);
        var allLost = RuntimeObservation.read(input, DaemonRuntimeObservationTest.IDENTITY);
        assertThrows(IOException.class, () -> HiddenTargets.requireSameConfiguration(expected, two, allLost));
    }
    @Test void unknownActivationOrDuplicateSignedTargetsCannotShrinkTheStartupInventory() throws Exception {
        var current = RuntimeObservation.read(DaemonRuntimeObservationTest.observation(), DaemonRuntimeObservationTest.IDENTITY);
        var changed = catalog(); var agent = changed.getAsJsonArray("agents").get(0).getAsJsonObject(); agent.getAsJsonObject("activation").addProperty("type", "future");
        assertThrows(IOException.class, () -> HiddenTargets.read(changed, current, Set.of("com.qinggan.app.launcher"), HiddenTargets.snapshot(catalog(), current, Set.of("launcher")), null));
        var duplicate = catalog(); var targets = duplicate.getAsJsonArray("agents").get(0).getAsJsonObject().getAsJsonArray("targets"); targets.add(targets.get(0).deepCopy());
        assertThrows(IOException.class, () -> HiddenTargets.read(duplicate, current, Set.of("com.qinggan.app.launcher"), HiddenTargets.snapshot(catalog(), current, Set.of("launcher")), null));
    }
}
