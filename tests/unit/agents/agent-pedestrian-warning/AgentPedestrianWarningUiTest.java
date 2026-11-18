package ru.voboost.tests.agents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;
import ru.voboost.voyemu.UiEvidence;
import ru.voboost.voyemu.VehicleUi;

class AgentPedestrianWarningUiTest {
    private static JsonObject recordedSurface() throws IOException {
        // VehicleSetting, Free A9 6.0.3, after selecting OFF. Only the relevant subtree is retained.
        Path dump = Path.of("tests/unit/agents/agent-pedestrian-warning",
                "agent-pedestrian-warning-ui.json").toAbsolutePath();
        return UiEvidence.readable(RunFiles.read(dump));
    }

    @Test
    void findsCheckedControlInRecordedVehicleSettings() throws Exception {
        var control = VehicleUi.feature(recordedSurface(), VehicleUi.Feature.PEDESTRIAN_WARNING);
        assertEquals(new VehicleUi.Control("57", false, true), control);
    }

    @Test
    void rejectsIncompleteOrAmbiguousRecordedSurface() throws Exception {
        var incomplete = recordedSurface();
        incomplete.addProperty("complete", false);
        assertThrows(UiEvidence.Unavailable.class, () -> VehicleUi.feature(incomplete, VehicleUi.Feature.PEDESTRIAN_WARNING));

        var ambiguous = recordedSurface();
        var nodes = ambiguous.getAsJsonArray("nodes");
        var extra = nodes.get(2).getAsJsonObject().deepCopy();
        extra.addProperty("id", "58");
        nodes.add(extra);
        assertThrows(UiEvidence.Unavailable.class, () -> VehicleUi.feature(ambiguous, VehicleUi.Feature.PEDESTRIAN_WARNING));
    }
}
