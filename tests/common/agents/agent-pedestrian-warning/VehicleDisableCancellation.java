package ru.voboost.voyemu;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Actual clean-stop counterfactual: FEV restores retained intent; PW adopts OEM after explicit stop. */
public final class VehicleDisableCancellation {

    public interface Port {
        void enable(boolean enabled) throws Exception;
        void select(boolean selected) throws Exception;
        VehiclePersistence.State observe() throws Exception;
        Path requireArmed() throws Exception;
        Path requireStopped() throws Exception;
        void warmCycle() throws Exception;
    }

    public record Proof(List<VehiclePersistence.State> states, List<Path> evidence) {
        public Proof {
            states = List.copyOf(states);
            evidence = List.copyOf(evidence);
        }
    }

    public static Proof run(Port port, boolean forcedEv) throws Exception {
        boolean wanted = forcedEv;
        List<VehiclePersistence.State> states = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        port.enable(true);
        port.select(!wanted);
        var baseline = port.observe();
        baseline.require(forcedEv, !wanted);
        states.add(baseline);
        port.select(wanted);
        var armed = port.observe();
        armed.require(forcedEv, wanted);
        armed.can().requireNext(baseline.can());
        states.add(armed);
        files.add(port.requireArmed());
        port.enable(false);
        files.add(port.requireStopped());
        port.warmCycle();
        var reset = port.observe();
        reset.require(forcedEv, !wanted);
        reset.can().requireNext(armed.can());
        states.add(reset);
        files.add(port.requireStopped());
        port.enable(true);
        var current = port.observe();
        current.can().requireNext(reset.can());
        current.require(forcedEv, forcedEv ? wanted : !wanted);
        states.add(current);
        if (!forcedEv) {
            port.select(wanted);
            var rearmed = port.observe();
            rearmed.require(false, wanted);
            rearmed.can().requireNext(current.can());
            states.add(rearmed);
        }
        files.add(port.requireArmed());
        return new Proof(states, files);
    }

    private VehicleDisableCancellation() {}
}
