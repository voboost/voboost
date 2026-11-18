package ru.voboost.voyemu;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleDisableCancellationTest {

    private static final class Port implements VehicleDisableCancellation.Port {
        final List<Boolean> values;
        final List<String> actions = new ArrayList<>();
        boolean enabled, unknownWarm;
        int observed;
        Port(Boolean ... values) {
            this.values = List.of(values);
        }
        public void enable(boolean value) {
            enabled = value;
            actions.add("enable:" + value);
        }
        public void select(boolean value) {
            actions.add("select:" + value);
        }
        public VehiclePersistence.State observe() {
            boolean selected = values.get(observed++);
            actions.add("observe:" + selected);
            var can = new VehicleCan.Snapshot("boot", "session", "source", 12, "1",
                    Integer.toString(observed), Integer.toString(observed), selected ? 5 : 2,
                    selected ? 2 : 1, new JsonObject());
            return new VehiclePersistence.State(can, selected, Path.of("sample-" + observed));
        }
        public Path requireArmed() {
            assertTrue(enabled);
            actions.add("armed");
            return Path.of("armed");
        }
        public Path requireStopped() {
            assertFalse(enabled);
            actions.add("stopped");
            return Path.of("stopped");
        }
        public void warmCycle() throws Exception {
            actions.add("warm");
            if (unknownWarm)
                throw new IOException("unknown power outcome");
        }
    }

    @Test

    void forcedEvRestoresRetainedIntentAfterDisabledWarmReset() throws Exception {
        Port port = new Port(false, true, false, true);
        var proof = VehicleDisableCancellation.run(port, true);
        assertEquals(4, proof.states().size());
        assertEquals(2, port.actions.stream().filter("stopped"::equals).count());
        assertEquals(2, port.actions.stream().filter("armed"::equals).count());
    }

    @Test

    void pedestrianAdoptsCurrentOemAfterCleanStopThenRequiresExplicitRearm() throws Exception {
        Port port = new Port(true, false, true, true, false);
        var proof = VehicleDisableCancellation.run(port, false);
        assertEquals(5, proof.states().size());
        assertEquals(2, port.actions.stream().filter("select:false"::equals).count());
        assertThrows(IOException.class, () -> VehicleDisableCancellation.run(new Port(true, false,
                true, false, false), false));
    }

    @Test

    void unknownWarmDoesNotReplayPowerOrReenableTheCheckbox() {
        Port port = new Port(false, true);
        port.unknownWarm = true;
        assertThrows(IOException.class, () -> VehicleDisableCancellation.run(port, true));
        assertEquals(1, port.actions.stream().filter("warm"::equals).count());
        assertEquals(1, port.actions.stream().filter("enable:true"::equals).count());
        assertFalse(port.enabled);
    }
}
