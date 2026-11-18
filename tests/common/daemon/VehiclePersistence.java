package ru.voboost.voyemu;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Counterfactual control, not log inference: the same actual OEM selection with the agent disabled/enabled. */
public final class VehiclePersistence {
    public record State(VehicleCan.Snapshot can,boolean checked,Path evidence) {
        public State { if(can==null||evidence==null)throw new IllegalArgumentException("retained real state required"); }
        public void require(boolean forcedEv,boolean wanted)throws IOException {
            if(checked!=wanted||can.selected(forcedEv)!=wanted)throw new IOException("OEM checked state and independent CAN getter disagree with expected selection");
        }
    }
    public interface Port {
        void enable(boolean enabled)throws Exception;
        void select(boolean selected)throws Exception;
        void reboot()throws Exception;
        State observe()throws Exception;
    }
    public record Proof(List<State> states) {public Proof {states=List.copyOf(states);}}
    public static Proof run(Port port,boolean forcedEv)throws Exception {
        boolean wanted=forcedEv;List<State> states=new ArrayList<>();
        port.enable(false);port.select(wanted);State baseline=port.observe();baseline.require(forcedEv,wanted);states.add(baseline);
        port.reboot();State reset=port.observe();reset.can().requireNewBoot(baseline.can());reset.require(forcedEv,!wanted);states.add(reset);
        port.enable(true);port.select(wanted);State armed=port.observe();armed.can().requireNext(reset.can());armed.require(forcedEv,wanted);states.add(armed);
        port.reboot();State retained=port.observe();retained.can().requireNewBoot(armed.can());retained.require(forcedEv,wanted);states.add(retained);
        return new Proof(states);
    }
}
