package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Full scenario behavior; host ports do not constitute native CAN or runtime evidence. */
class AgentForcedEvProgramTest {
    private static final class Port implements ForcedEvProgram.Port {
        boolean coldCap=true,admitted=true,enabled,armed,unknownMode,wrongOff,failCoverage,lateRead,slowUi,wrongUi,delayedReturn,returning;long clock;int raw=2,boots,reads,coverage,mutations;final List<String> calls=new ArrayList<>();
        public long now(){clock+=500_000_000;return clock;}
        public void pause(){clock+=500_000_000;}
        public ForcedEvProgram.Admission admit(){calls.add("admit");return ForcedEvProgram.Admission.observed(admitted,admitted,coldCap,admitted?"controlled complete host source":"current public coverage partial",List.of(Path.of("/controlled-host-fixture/admission")));}
        public void enableAgent(boolean value){calls.add("enable:"+value);enabled=value;mutations++;if(!value)armed=false;}
        public void selectOem(boolean value){calls.add("oem:"+value);raw=value?5:2;mutations++;if(enabled)armed=value;}
        public void openDrawer(){calls.add("open");mutations++;}
        public void switchForce(boolean value){calls.add("switch:"+value);raw=value?5:2;armed=value;mutations++;}
        public void nextMode()throws IOException {calls.add("next");mutations++;returning=raw==4&&armed;raw=raw==2||raw==5?3:raw==3?4:armed?5:2;if(unknownMode)throw new IOException("controlled unknown mode dispatch");}
        public void warmCycle(){calls.add("warm");mutations++;raw=enabled&&armed?5:2;}
        public void coldReboot(){calls.add("cold");mutations++;boots++;raw=enabled&&armed?5:2;}
        public ForcedEvProgram.Feedback feedback()throws Exception {
            calls.add("feedback");
            if (lateRead) clock+=20_000_000_000L;
            var state=snapshot();
            return new ForcedEvProgram.Feedback(state.can(), state.armed(), state.evidence());
        }
        public ForcedEvProgram.State observe()throws Exception {
            calls.add("observe");if(slowUi)clock+=20_000_000_000L;return snapshot();
        }
        private ForcedEvProgram.State snapshot()throws Exception {
            if(delayedReturn&&returning){raw=2;returning=false;}var value=VehicleCanTest.receipt();String boot=String.format("%08d-1111-1111-1111-111111111111",boots+1);value.addProperty("boot_id",boot);value.addProperty("guest_boot_id",boot);value.addProperty("source_instance","can-"+boots);value.getAsJsonObject("history").getAsJsonObject("next_cursor").addProperty("source_instance","can-"+boots);value.getAsJsonObject("clock").addProperty("sampled_ns",Integer.toString(30+reads++));value.getAsJsonObject("values").getAsJsonObject(VehicleCan.FEV).addProperty("raw",wrongOff&&coverage>0?5:raw);
            var mode=raw==3?ForcedEvProgram.Mode.HYBRID:raw==4?ForcedEvProgram.Mode.SAVE:ForcedEvProgram.Mode.ELECTRIC;
            return new ForcedEvProgram.State(VehicleCanTest.read(value),mode,raw==5&&!wrongUi,armed,Path.of("/controlled-host-fixture/state-"+reads));
        }
        public void beginContinuousOff(){calls.add("begin-off");coverage++;}
        public Path requireContinuousOff()throws IOException {calls.add("cover-off");if(failCoverage)throw new IOException("controlled source gap");return Path.of("/controlled-host-fixture/off-"+calls.size());}
        public Path finishContinuousOff()throws IOException {calls.add("finish-off");if(failCoverage)throw new IOException("controlled source gap");return Path.of("/controlled-host-fixture/off-final");}
    }
    @Test void ordinaryElectricDuringArmedReturnIsRejectedWithoutWaitingForCorrection()throws Exception {
        Port port=new Port();port.delayedReturn=true;
        IOException failure=assertThrows(IOException.class,()->ForcedEvProgram.run(port));
        assertTrue(failure.getMessage().contains("ordinary Electric"));
        assertEquals(3,port.calls.stream().filter("next"::equals).count());
    }
    @Test void unsupportedCoverageBlocksBeforeEveryMutation()throws Exception {
        Port port=new Port();port.admitted=false;var result=ForcedEvProgram.run(port);assertFalse(result.admitted());assertEquals(List.of("admit"),port.calls);assertEquals(0,port.mutations);assertTrue(result.states().isEmpty());
    }
    @Test void liveRxAndDurableIntentWithoutColdCapabilityAuthorizeZeroDispatch()throws Exception {
        Port port=new Port();port.coldCap=false;var result=ForcedEvProgram.run(port);assertFalse(result.admitted());assertEquals(List.of("admit"),port.calls);assertEquals(0,port.mutations);assertTrue(result.states().isEmpty());
    }
    @Test void fullProgramIncludesBaselineArmingModesExplicitOffWarmColdAndContinuousCoverage()throws Exception {
        Port port=new Port();var result=ForcedEvProgram.run(port);assertTrue(result.admitted());assertEquals(16,result.states().size());assertEquals(3,port.boots);assertTrue(result.evidence().contains(Path.of("/controlled-host-fixture/state-1")));
        assertEquals(6,port.calls.stream().filter("next"::equals).count());assertEquals(6,port.calls.stream().filter("cover-off"::equals).count());
        assertTrue(port.calls.indexOf("begin-off")<port.calls.lastIndexOf("switch:false"));assertEquals("finish-off",port.calls.get(port.calls.size()-1));assertFalse(port.armed);assertEquals(2,port.raw);
    }
    @Test void unknownModeDispatchStopsWithoutReplayOrCheckboxRollback()throws Exception {
        Port port=new Port();port.unknownMode=true;assertThrows(IOException.class,()->ForcedEvProgram.run(port));assertEquals(1,port.calls.stream().filter("next"::equals).count());assertEquals("next",port.calls.get(port.calls.size()-1));assertTrue(port.enabled);
    }
    @Test void lateMatchingObservationDoesNotUpgradeAnExpiredEffectWindow()throws Exception {
        Port port=new Port();port.lateRead=true;assertThrows(IOException.class,()->ForcedEvProgram.run(port));assertEquals(0,port.boots);assertEquals(0,port.calls.stream().filter("next"::equals).count());assertEquals("feedback",port.calls.get(port.calls.size()-1));
    }
    @Test void slowUiCollectionDoesNotRedefineConfirmedCanEffectTiming()throws Exception {
        Port port=new Port();port.slowUi=true;
        var result=ForcedEvProgram.run(port);
        assertTrue(result.admitted());assertEquals(16,result.states().size());
        assertEquals(6,port.calls.stream().filter("next"::equals).count());
        assertFalse(port.armed);
    }
    @Test void matchingCanDoesNotHideWrongUiState()throws Exception {
        Port port=new Port();port.wrongUi=true;
        IOException failure=assertThrows(IOException.class,()->ForcedEvProgram.run(port));
        assertTrue(failure.getMessage().contains("drawer/CAN/durable"));
        assertEquals(0,port.boots);
        assertEquals(0,port.calls.stream().filter("next"::equals).count());
    }
    @Test void reenabledOffOrCoverageGapCannotReachLaterTransitions()throws Exception {
        for(boolean gap:List.of(false,true)) {Port port=new Port();port.wrongOff=!gap;port.failCoverage=gap;assertThrows(IOException.class,()->ForcedEvProgram.run(port));assertFalse(port.calls.contains("finish-off"));assertTrue(port.enabled);assertEquals(3,port.calls.stream().filter("next"::equals).count());}
    }
}
