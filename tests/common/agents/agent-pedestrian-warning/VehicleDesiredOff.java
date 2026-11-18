package ru.voboost.voyemu;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Drives ordinary Settings input and power operations; fixture tests validate orchestration only. */
public final class VehicleDesiredOff {
    public interface Port {
        void enable(boolean enabled) throws Exception;
        void select(boolean selected) throws Exception;
        void warmCycle() throws Exception;
        void coldReboot() throws Exception;
        State observe() throws Exception;
    }

    public record State(String boot, String publisher, boolean enabled, int targets,
            VehiclePersistence.State vehicle, Path evidence) {
        public State {
            if (boot == null || boot.isBlank() || publisher == null || publisher.isBlank()
                    || vehicle == null || evidence == null || targets < 0)
                throw new IllegalArgumentException("current desired and vehicle observation required");
        }
        private void requireOff() throws IOException {
            if (enabled || targets != 0 || !boot.equals(vehicle.can().boot()))
                throw new IOException("latest OFF was not retained or disabled target remains published");
        }
    }

    public record Proof(List<State> states) {
        public Proof { states = List.copyOf(states); }
    }

    public static Proof run(Port port, boolean forcedEv) throws Exception {
        List<State> states = new ArrayList<>();
        port.enable(true);
        port.select(forcedEv);
        State armed = port.observe();
        if (!armed.enabled() || armed.targets() == 0)
            throw new IOException("enabled baseline has no published vehicle target");
        armed.vehicle().require(forcedEv, forcedEv);
        states.add(armed);
        port.enable(false);
        State stopped = port.observe();
        stopped.requireOff();
        if (!stopped.boot().equals(armed.boot()) || !stopped.publisher().equals(armed.publisher()))
            throw new IOException("OFF input crossed boot or owner lifetime");
        states.add(stopped);
        port.warmCycle();
        State warm = port.observe();
        warm.requireOff();
        warm.vehicle().can().requireNext(stopped.vehicle().can());
        if (!warm.publisher().equals(stopped.publisher()))
            throw new IOException("warm cycle changed owner lifetime");
        warm.vehicle().require(forcedEv, !forcedEv);
        states.add(warm);
        port.coldReboot();
        State cold = port.observe();
        cold.requireOff();
        cold.vehicle().can().requireNewBoot(warm.vehicle().can());
        cold.vehicle().require(forcedEv, !forcedEv);
        states.add(cold);
        return new Proof(states);
    }

    private VehicleDesiredOff() {}
}
