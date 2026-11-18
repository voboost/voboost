package ru.voboost.voyemu;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Complete case13 program; every live, warm and cold source capability is admitted before mutation. */
public final class ForcedEvProgram {
    public enum Mode { ELECTRIC, HYBRID, SAVE }
    public record Admission(boolean continuousFeedback,boolean durableIntent,String reason,List<Path> evidence) {
        public Admission {evidence=List.copyOf(evidence);if(reason==null||reason.isBlank())throw new IllegalArgumentException("explicit admission reason required");}
public boolean ready(){return continuousFeedback&&durableIntent;}
        /** Complete program authority requires both actual live RX and the separately verified cold boundary. */
        public static Admission observed(boolean liveRx,boolean durableIntent,boolean coldBoundary,String reason,List<Path> evidence){return new Admission(liveRx&&coldBoundary,durableIntent,reason,evidence);}
    }
    public record State(VehicleCan.Snapshot can,Mode mode,Boolean switchOn,Boolean armed,Path evidence) {
        public State {if(can==null||mode==null||evidence==null)throw new IllegalArgumentException("actual CAN/drawer/intent evidence required");}
        void require(int raw,Boolean intent)throws IOException {
            Mode expected=switch(raw){case 2,5->Mode.ELECTRIC;case 3->Mode.HYBRID;case 4->Mode.SAVE;default->throw new IOException("undeclared FEV getter state");};
            if(can.fev()!=raw||mode!=expected||mode==Mode.ELECTRIC&&!Boolean.valueOf(raw==5).equals(switchOn)||intent!=null&&!intent.equals(armed))throw new IOException("actual drawer/CAN/durable FEV intent differs");
        }
    }
    public record Feedback(VehicleCan.Snapshot can, Boolean armed, Path evidence) {
        public Feedback { if (can == null || evidence == null) throw new IllegalArgumentException("CAN and intent evidence required"); }
        boolean matches(int raw, Boolean expected) {
            return can.fev() == raw && (expected == null || expected.equals(armed));
        }
    }
    public interface Port {
        Admission admit()throws Exception;
        void enableAgent(boolean enabled)throws Exception;
        void selectOem(boolean on)throws Exception;
        void openDrawer()throws Exception;
        void switchForce(boolean on)throws Exception;
        void nextMode()throws Exception;
        void warmCycle()throws Exception;
        void coldReboot()throws Exception;
        Feedback feedback()throws Exception;
        State observe()throws Exception;
        void beginContinuousOff()throws Exception;
        Path requireContinuousOff()throws Exception;
        Path finishContinuousOff()throws Exception;
        default long now(){return System.nanoTime();}
        default void pause()throws InterruptedException{Thread.sleep(50);}
    }
    public record Proof(boolean admitted,String reason,List<State> states,List<Path> evidence) {
        public Proof {states=List.copyOf(states);evidence=List.copyOf(evidence);}
    }
    private static State read(Port port,List<State> states,List<Path> evidence,int raw,Boolean armed)throws Exception {return read(port,states,evidence,raw,armed,false);}
    private static State read(Port port,List<State> states,List<Path> evidence,int raw,Boolean armed,boolean cold)throws Exception {
        return read(port,states,evidence,raw,armed,cold,false);
    }
    private static State read(Port port,List<State> states,List<Path> evidence,int raw,Boolean armed,boolean cold,boolean returningToElectric)throws Exception {
        long deadline=port.now()+java.time.Duration.ofSeconds(10).toNanos();IOException mismatch=null;
        while(port.now()<deadline) {
            Feedback feedback=port.feedback();
            if(port.now()>=deadline)throw new IOException("FEV feedback completed after the effect deadline; no input replay");
            if(!states.isEmpty()) {
                var prior=states.get(states.size()-1).can();
                if(cold)feedback.can().requireNewBoot(prior);else feedback.can().requireNext(prior);
            }
            if(returningToElectric&&feedback.can().fev()==2)
                throw new IOException("Returning to Electric exposed ordinary Electric before Forced EV");
            if (!feedback.matches(raw, armed)) {
                mismatch = new IOException("actual CAN or durable FEV intent differs");
                port.pause();
                continue;
            }
            // UI collection has its own bounded port operations; it cannot redefine CAN effect timing.
            State state=port.observe();
            state.can().requireNext(feedback.can());
            state.require(raw,armed);
            evidence.add(feedback.evidence());
            states.add(state);
            return state;
        }
        throw new IOException("actual FEV effect not independently confirmed within bounded readback; no input replay",mismatch);
    }
    public static Proof run(Port port)throws Exception {
        Admission admission=port.admit();List<State> states=new ArrayList<>();List<Path> evidence=new ArrayList<>(admission.evidence());
        if(!admission.ready())return new Proof(false,admission.reason(),states,evidence);
        // Counterfactual baseline: an OEM selection alone must not survive either restart form.
        port.enableAgent(false);port.selectOem(true);read(port,states,evidence,5,null);
        port.coldReboot();read(port,states,evidence,2,null,true);
        port.selectOem(true);read(port,states,evidence,5,null);port.warmCycle();read(port,states,evidence,2,null);
        port.enableAgent(true);port.openDrawer();
        State initial=port.observe();
        if(initial.mode()!=Mode.ELECTRIC||initial.switchOn()==null)throw new IOException("drawer ARM preparation requires actual Electric mode");
        if(initial.switchOn()){port.switchForce(false);read(port,states,evidence,2,false);}
        port.switchForce(true);read(port,states,evidence,5,true);
        port.nextMode();read(port,states,evidence,3,true);port.nextMode();read(port,states,evidence,4,true);port.nextMode();read(port,states,evidence,5,true,false,true);
        port.warmCycle();read(port,states,evidence,5,true);port.coldReboot();read(port,states,evidence,5,true,true);
        // Coverage starts BEFORE the explicit OFF input and must span every subsequent transition.
        port.beginContinuousOff();port.switchForce(false);read(port,states,evidence,2,false);evidence.add(port.requireContinuousOff());
        port.nextMode();read(port,states,evidence,3,false);evidence.add(port.requireContinuousOff());
        port.nextMode();read(port,states,evidence,4,false);evidence.add(port.requireContinuousOff());
        port.nextMode();read(port,states,evidence,2,false);evidence.add(port.requireContinuousOff());
        port.warmCycle();read(port,states,evidence,2,false);evidence.add(port.requireContinuousOff());
        port.coldReboot();read(port,states,evidence,2,false,true);evidence.add(port.requireContinuousOff());
        evidence.add(port.finishContinuousOff());for(State state:states)evidence.add(state.evidence());
        return new Proof(true,"Actual disabled baseline, armed drawer modes and explicit OFF covered by gap-checked live native RX plus source-bound closed ECU warm/cold witnesses; no physical or offline native reception claim",states,evidence);
    }
}
