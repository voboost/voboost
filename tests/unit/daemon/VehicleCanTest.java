package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

class VehicleCanTest {
    static final String BOOT="11111111-1111-1111-1111-111111111111", SESSION="a".repeat(32);
    static JsonObject receipt()throws IOException {
        return RunFiles.parse("""
        {"schema":1,"source":"android:qg.canbus","status":"ready","boot_id":"BOOT","guest_boot_id":"BOOT","session_id":"SESSION",
         "atomic_guest_state":false,"source_instance":"source-one","pid":123,"service_started_elapsed_ns":"10","revision":"2",
         "clock":{"domain":"android.elapsedRealtimeNanos","sampled_ns":"30"},
         "host_interval":{"domain":"host.steady","clock_id":"host.steady:SESSION","start_ns":"100","end_ns":"110"},
         "values":{"vehicle_state.IVI_SOC_MODESET":{"status":"observed","raw":2,"basis":"ICanBusService.getVehicleState"},
                   "vehicle_state.HUM_VSP_FUNCTION_SW":{"status":"observed","raw":1,"basis":"ICanBusService.getVehicleState"}},
         "acc":{"status":"observed","raw":2,"basis":"ICanBusService.getAccStatus"},
         "transport":{"registration":"registered","binder_alive":true,"client_manager_cache":"not_observed"},
         "coverage":{"begins":"service_construction","delivery":"journal_replay","vehicle_state_setter":true,"other_feedback_paths":"partial","survives_process_death":false},
         "history":{"oldest_seq":"1","latest_seq":"2","dropped_count":"0","next_cursor":{"source_instance":"source-one","after_seq":"2"},"has_more":false,"gap":null},"events":[]}
        """.replace("BOOT",BOOT).replace("SESSION",SESSION));
    }
    static VehicleCan.Snapshot read(JsonObject value)throws IOException {return VehicleCan.read(value,SESSION,value.get("boot_id").getAsString());}
    @Test void actualFixedGettersRemainSeparateFromContinuousCoverage()throws Exception {
        var state=read(receipt());assertFalse(state.selected(false));assertFalse(state.selected(true));
        assertTrue(VehicleCan.continuousOffBlocker(state).contains("partial"));
        var changed=state.receipt();changed.addProperty("pid",999);assertEquals(123,state.pid());assertEquals(123,state.receipt().get("pid").getAsInt());
    }
    @Test void missingUnavailableOrForeignReceiptsNeverBecomeOff()throws Exception {
        for(String key:List.of("boot_id","guest_boot_id","session_id","source_instance","pid","clock","host_interval","transport","values","coverage","history")) {
            JsonObject value=receipt();value.remove(key);assertThrows(IOException.class,()->VehicleCan.read(value,SESSION,BOOT),key);
        }
        for(String key:List.of("status","guest_boot_id","session_id")) {
            JsonObject value=receipt();value.addProperty(key,"unavailable");assertThrows(IOException.class,()->VehicleCan.read(value,SESSION,BOOT));
        }
        JsonObject value=receipt();value.getAsJsonObject("values").getAsJsonObject(VehicleCan.PW).addProperty("raw",0);assertThrows(IOException.class,()->read(value));
    }
    @Test void outingUsesTheDriveModeGetterRatherThanTheBcmChangePulse() throws Exception {
        JsonObject receipt = receipt();
        JsonObject values = receipt.getAsJsonObject("values");
        JsonObject mode = values.getAsJsonObject(VehicleCan.FEV).deepCopy();
        mode.addProperty("raw", 1);
        values.add("vehicle_state.BCM_DRIVEMODE_CHANGE", mode.deepCopy());
        assertThrows(IOException.class, () -> VehicleCan.driveMode(VehicleCan.readPower(receipt, SESSION, BOOT)));
        mode.addProperty("raw", 4);
        values.add("vehicle_state.DRIVING_MODE_SET", mode);
        assertEquals(4, VehicleCan.driveMode(VehicleCan.readPower(receipt, SESSION, BOOT)));
        receipt.getAsJsonObject("acc").addProperty("raw", 0);
        assertThrows(IOException.class, () -> VehicleCan.driveMode(VehicleCan.readPower(receipt, SESSION, BOOT)));
    }

    @Test void sleepingPowerReadDoesNotRequireUnavailableFeatureGetters()throws Exception {
        JsonObject sleeping=receipt();
        for(String key:List.of(VehicleCan.FEV,VehicleCan.PW)) {
            JsonObject feature=sleeping.getAsJsonObject("values").getAsJsonObject(key);
            feature.addProperty("status","unavailable");
            feature.addProperty("raw",-1);
        }
        sleeping.getAsJsonObject("acc").addProperty("raw",0);
        var power=VehicleCan.readPower(sleeping,SESSION,BOOT);
        assertEquals(0,power.acc());
        assertTrue(power.featureGettersPending());
        assertFalse(VehicleCan.readPower(receipt(),SESSION,BOOT).featureGettersPending());
        sleeping.getAsJsonObject("values").getAsJsonObject(VehicleCan.PW).addProperty("raw",2);
        assertThrows(IOException.class,()->VehicleCan.readPower(sleeping,SESSION,BOOT).featureGettersPending());
        assertThrows(IOException.class,()->VehicleCan.read(sleeping,SESSION,BOOT));
        sleeping.getAsJsonObject("acc").addProperty("status","unavailable");
        assertThrows(IOException.class,()->VehicleCan.readPower(sleeping,SESSION,BOOT));
    }
    @Test void canonicalCountersAndHistoryGapsAreFailClosed()throws Exception {
        for(String revision:List.of("02","-1","9223372036854775808","2.0")) {JsonObject value=receipt();value.addProperty("revision",revision);assertThrows(IOException.class,()->read(value));}
        JsonObject overflow=receipt();overflow.getAsJsonObject("history").addProperty("gap","overflow");assertThrows(IOException.class,()->read(overflow));
        JsonObject wrong=receipt();wrong.getAsJsonObject("history").getAsJsonObject("next_cursor").addProperty("source_instance","foreign");assertThrows(IOException.class,()->read(wrong));
    }
    @Test void sameBootNeedsFreshSampleAndExactSourceButNewBootAllowsResetRevision()throws Exception {
        var first=read(receipt());assertThrows(IOException.class,()->first.requireNext(first));JsonObject next=receipt();next.getAsJsonObject("clock").addProperty("sampled_ns","31");read(next).requireNext(first);
        next.addProperty("pid",124);assertThrows(IOException.class,()->read(next).requireNext(first));
        JsonObject cold=receipt();cold.addProperty("boot_id","22222222-2222-2222-2222-222222222222");cold.addProperty("guest_boot_id",cold.get("boot_id").getAsString());cold.addProperty("source_instance","source-two");cold.addProperty("revision","0");cold.getAsJsonObject("history").addProperty("latest_seq","0");cold.getAsJsonObject("history").getAsJsonObject("next_cursor").addProperty("source_instance","source-two");cold.getAsJsonObject("history").getAsJsonObject("next_cursor").addProperty("after_seq","0");read(cold).requireNewBoot(first);
    }
}
