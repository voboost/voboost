package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VehiclePersistenceTest {
    private static final class Port implements VehiclePersistence.Port {
        boolean enabled,selected,wrongReset,wrongRestore,unknownSelect;int boots,observations,selections;final List<String> calls=new ArrayList<>();
        final boolean fev;Port(){this(false);}Port(boolean fev){this.fev=fev;}
        public void enable(boolean value){calls.add("enable:"+value);enabled=value;}
        public void select(boolean value)throws IOException {calls.add("select:"+value);selections++;selected=value;if(unknownSelect)throw new IOException("unknown dispatch");}
        public void reboot(){calls.add("reboot");boots++;selected=enabled?!wrongRestore:fev?wrongReset:!wrongReset;}
        public VehiclePersistence.State observe()throws Exception {
            calls.add("observe");JsonObject raw=VehicleCanTest.receipt();String boot=boots==0?VehicleCanTest.BOOT:boots==1?"22222222-2222-2222-2222-222222222222":"33333333-3333-3333-3333-333333333333";
            raw.addProperty("boot_id",boot);raw.addProperty("guest_boot_id",boot);raw.addProperty("source_instance","source-"+boots);raw.getAsJsonObject("history").getAsJsonObject("next_cursor").addProperty("source_instance","source-"+boots);
            raw.getAsJsonObject("clock").addProperty("sampled_ns",Integer.toString(30+observations++));raw.getAsJsonObject("values").getAsJsonObject(fev?VehicleCan.FEV:VehicleCan.PW).addProperty("raw",fev?(selected?5:2):(selected?2:1));
            return new VehiclePersistence.State(VehicleCanTest.read(raw),selected,Path.of("/controlled-host-fixture/state-"+observations));
        }
    }
    @Test void fevOemCounterfactualRequiresFiveTwoFiveFiveAndDoesNotGrantDrawerCoverage()throws Exception {
        Port port=new Port(true);var proof=VehiclePersistence.run(port,true);
        assertEquals(List.of(5,2,5,5),proof.states().stream().map(state->state.can().fev()).toList());
        assertEquals(2,port.boots);assertEquals(2,port.selections);
        Port noReset=new Port(true);noReset.wrongReset=true;assertThrows(IOException.class,()->VehiclePersistence.run(noReset,true));
        assertFalse(noReset.enabled);assertEquals(1,noReset.selections);
        Port noRestore=new Port(true);noRestore.wrongRestore=true;assertThrows(IOException.class,()->VehiclePersistence.run(noRestore,true));
        assertEquals(2,noRestore.selections);assertTrue(noRestore.enabled);
    }
    @Test void disabledResetAndEnabledRetentionRequireFourRealStatesAndTwoDifferentBoots()throws Exception {
        Port port=new Port();port.wrongRestore=true;var proof=VehiclePersistence.run(port,false);
        assertEquals(4,proof.states().size());assertEquals(List.of("enable:false","select:false","observe","reboot","observe","enable:true","select:false","observe","reboot","observe"),port.calls);
        assertEquals(2,port.boots);assertEquals(2,port.selections);
    }
    @Test void failedCounterfactualStopsBeforeAgentEnableAndNeverRollsBackCheckboxIntent()throws Exception {
        Port port=new Port();port.wrongReset=true;assertThrows(IOException.class,()->VehiclePersistence.run(port,false));
        assertFalse(port.calls.contains("enable:true"));assertEquals(1,port.selections);assertEquals(1,port.boots);
    }
    @Test void failedEnabledPersistenceAndUnknownSelectionNeverReplayOrDisableIntent()throws Exception {
        Port wrong=new Port();assertThrows(IOException.class,()->VehiclePersistence.run(wrong,false));assertEquals(2,wrong.selections);assertEquals("observe",wrong.calls.get(wrong.calls.size()-1));assertTrue(wrong.enabled);
        Port unknown=new Port();unknown.unknownSelect=true;assertThrows(IOException.class,()->VehiclePersistence.run(unknown,false));assertEquals(List.of("enable:false","select:false"),unknown.calls);assertEquals(1,unknown.selections);assertEquals(0,unknown.boots);
    }
}
