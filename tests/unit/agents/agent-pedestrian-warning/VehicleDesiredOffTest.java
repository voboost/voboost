package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Controlled fixtures prove runner rejection and ordering, never runtime behavior. */
class VehicleDesiredOffTest {
    private static final class Port implements VehicleDesiredOff.Port {
        final List<String> actions = new ArrayList<>();
        boolean enabled, selected, wrongOff, wrongWarm, sameBoot, unknownWarm;
        int boot, samples;
        boolean forcedEv = true;
        public void enable(boolean value) { actions.add("enable:" + value); enabled = value; }
        public void select(boolean value) { actions.add("select:" + value); selected = value; }
        public void warmCycle() throws IOException {
            actions.add("warm");
            if (unknownWarm) throw new IOException("unknown warm outcome");
            selected = !forcedEv;
        }
        public void coldReboot() { actions.add("cold"); if (!sameBoot) boot++; selected = !forcedEv; }
        public VehicleDesiredOff.State observe() {
            actions.add("observe"); samples++;
            String lifetime = "boot-" + boot;
            var can = new VehicleCan.Snapshot(lifetime, "session", "source-" + boot, 12, "1",
                    Integer.toString(samples), Integer.toString(samples), selected ? 5 : 2,
                    selected ? 2 : 1, new JsonObject());
            var vehicle = new VehiclePersistence.State(can, selected, Path.of("vehicle-" + samples));
            return new VehicleDesiredOff.State(lifetime, "owner-" + boot + (wrongWarm && samples == 3 ? "-changed" : ""),
                    enabled || wrongOff && samples > 1, enabled ? 1 : 0, vehicle, Path.of("desired-" + samples));
        }
    }

    @Test void offSurvivesWarmAndNewBootWithoutReenabling() throws Exception {
        Port port = new Port();
        assertEquals(4, VehicleDesiredOff.run(port, true).states().size());
        assertEquals(List.of("enable:true", "select:true", "observe", "enable:false", "observe",
                "warm", "observe", "cold", "observe"), port.actions);
        assertFalse(port.enabled);
    }

    @Test void pedestrianOffRetainsUserChoiceWhileOemResetsOn() throws Exception {
        Port port = new Port(); port.forcedEv = false;
        var proof = VehicleDesiredOff.run(port, false);
        assertEquals(4, proof.states().size());
        assertTrue(proof.states().get(3).vehicle().checked());
        assertFalse(port.enabled);
    }

    @Test void staleOffOrChangedWarmOwnerStopsBeforeColdDispatch() {
        Port stale = new Port(); stale.wrongOff = true;
        assertThrows(IOException.class, () -> VehicleDesiredOff.run(stale, true));
        assertFalse(stale.actions.contains("warm"));
        Port changed = new Port(); changed.wrongWarm = true;
        assertThrows(IOException.class, () -> VehicleDesiredOff.run(changed, true));
        assertFalse(changed.actions.contains("cold"));
    }

    @Test void unknownPowerNeverReplaysAndSameBootNeverCountsAsCold() {
        Port unknown = new Port(); unknown.unknownWarm = true;
        assertThrows(IOException.class, () -> VehicleDesiredOff.run(unknown, true));
        assertEquals(1, unknown.actions.stream().filter("warm"::equals).count());
        assertFalse(unknown.actions.contains("cold"));
        Port same = new Port(); same.sameBoot = true;
        assertThrows(IOException.class, () -> VehicleDesiredOff.run(same, true));
        assertFalse(same.enabled);
    }
}
