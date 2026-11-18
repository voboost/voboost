package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

class VehicleArmedTest {
    private static final String BOOT=VehicleCanTest.BOOT,KEY="state/vehicle-drive-forced-ev_forced-ev-armed";
    private static JsonObject observation()throws IOException{return RunFiles.parse("{\"schema\":2,\"source\":\"voboost\",\"status\":\"ready\",\"boot_id\":\""+BOOT+"\",\"data\":{\"root\":\"present\",\"root_proof\":\"fixed-path-owner-mode-boot-fenced\",\"files\":{\""+KEY+"\":{\"status\":\"present\",\"current\":true,\"proof\":\"same-boot-retained-file\",\"revision\":\"actual-source-revision\",\"data\":true}}}}");}
    @Test void actualDurableBooleanIsReadWithoutPromotingItToOperationTimeProof()throws Exception {JsonObject o=observation();assertTrue(VehicleArmed.read(o,BOOT));o.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject(KEY).addProperty("data",false);assertFalse(VehicleArmed.read(o,BOOT));}
    @Test void absentUnavailableMalformedAndForeignNeverBecomeFalse()throws Exception {
        for(String status:List.of("absent","unavailable","unknown")){JsonObject o=observation();o.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject(KEY).addProperty("status",status);assertThrows(IOException.class,()->VehicleArmed.read(o,BOOT));}
        for(String field:List.of("current","data","proof","revision")){JsonObject o=observation();o.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject(KEY).remove(field);assertThrows(IOException.class,()->VehicleArmed.read(o,BOOT));}
        assertThrows(IOException.class,()->VehicleArmed.read(observation(),"foreign"));JsonObject o=observation();o.addProperty("schema","2");assertThrows(IOException.class,()->VehicleArmed.read(o,BOOT));
    }    @Test void fixedPedestrianReadCannotBorrowTheFevKey()throws Exception {
        var original=observation();assertThrows(IOException.class,()->VehicleArmed.readPedestrian(original,BOOT));
        var files=original.getAsJsonObject("data").getAsJsonObject("files");var state=files.remove(KEY);files.add("state/vehicle-drive-pedestrian-warning_pedestrian-warning-armed",state);
        assertTrue(VehicleArmed.readPedestrian(original,BOOT));state.getAsJsonObject().addProperty("data",false);assertFalse(VehicleArmed.readPedestrian(original,BOOT));
        assertThrows(IOException.class,()->VehicleArmed.read(original,BOOT));
    }
    private static final ru.voboost.tests.agents.RuntimeObservation.Identity ID =
        new ru.voboost.tests.agents.RuntimeObservation.Identity(BOOT,"release","manifest","catalog","generation");
    private static JsonObject nativeObservation()throws IOException {
        return RunFiles.parse("""
            {"schema":2,"source":"voboost","status":"ready","boot_id":"BOOT","data":{
              "root":"present","root_proof":"fixed-path-owner-mode-boot-fenced","runtime_current":true,
              "files":{"config":{"status":"present","revision":"revision","data":{"agents":{}}},
              "runtime":{"status":"present","current":true,"provenance":{"current":true,"proof":"same-boot-live-publisher","age_ms":20},
              "data":{"publisher":{"bootId":"BOOT","daemonPid":"1706","daemonStarttime":"183","publishedAtBootMs":"36680"},
              "releaseIdentity":"release","manifestSha256":"manifest","catalogSha256":"catalog","generationId":"generation",
              "state":"ready","killed":false,"panic":false,"injections":[],
              "persistentVehicleState":{"bootId":"BOOT","observedAtBootMs":36720,"forcedEv":{"outcome":"value","value":false},"pedestrianWarning":{"outcome":"value","value":true}}}}}}}
            """.replace("BOOT",BOOT));
    }
    private static JsonObject publication(JsonObject o){return o.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("runtime").getAsJsonObject("data");}
    private static JsonObject nativeRoot(JsonObject o){return publication(o).getAsJsonObject("persistentVehicleState");}
    @Test void currentNativeOwnerSnapshotReadsBothFixedFlagsWithoutLegacyFiles()throws Exception {
        var current=ru.voboost.tests.agents.RuntimeObservation.read(nativeObservation(),ID);
        assertFalse(VehicleArmed.read(current));assertTrue(VehicleArmed.readPedestrian(current));
    }
    @Test void nativeRootRejectsMissingUnavailableForeignAndUnboundedSamples()throws Exception {
        for(String outcome:List.of("missing","unavailable","unknown")) {
            var o=nativeObservation();var flag=nativeRoot(o).getAsJsonObject("forcedEv");flag.addProperty("outcome",outcome);flag.remove("value");
            assertThrows(IOException.class,()->VehicleArmed.read(ru.voboost.tests.agents.RuntimeObservation.read(o,ID)));
        }
        for(String field:List.of("bootId","observedAtBootMs","forcedEv")) {
            var o=nativeObservation();nativeRoot(o).remove(field);
            assertThrows(IOException.class,()->VehicleArmed.read(ru.voboost.tests.agents.RuntimeObservation.read(o,ID)));
        }
        for(String time:List.of("36679","41681","-1","18446744073709551616")) {
            var o=nativeObservation();nativeRoot(o).addProperty("observedAtBootMs",new java.math.BigInteger(time));
            assertThrows(IOException.class,()->VehicleArmed.read(ru.voboost.tests.agents.RuntimeObservation.read(o,ID)),time);
        }
        var foreign=nativeObservation();nativeRoot(foreign).addProperty("bootId","foreign");
        assertThrows(IOException.class,()->VehicleArmed.read(ru.voboost.tests.agents.RuntimeObservation.read(foreign,ID)));
        var wrongType=nativeObservation();nativeRoot(wrongType).getAsJsonObject("forcedEv").addProperty("value","false");
        assertThrows(IOException.class,()->VehicleArmed.read(ru.voboost.tests.agents.RuntimeObservation.read(wrongType,ID)));
        for(String identity:List.of("releaseIdentity","manifestSha256","catalogSha256","generationId")) {
            var o=nativeObservation();publication(o).addProperty(identity,"foreign");
            assertThrows(IOException.class,()->VehicleArmed.read(ru.voboost.tests.agents.RuntimeObservation.read(o,ID)));
        }
    }
    @Test void durableReadMustFollowTheActualPostInputGuestCanClock()throws Exception {
        var canRaw=VehicleCanTest.receipt();canRaw.getAsJsonObject("clock").addProperty("sampled_ns","36680000000");
        var afterInput=VehicleCanTest.read(canRaw);
        var cached=ru.voboost.tests.agents.RuntimeObservation.read(nativeObservation(),ID);
        assertThrows(IOException.class,()->VehicleArmed.requireAfter(cached,afterInput));
        var later=nativeObservation();publication(later).getAsJsonObject("publisher").addProperty("publishedAtBootMs","36700");
        VehicleArmed.requireAfter(ru.voboost.tests.agents.RuntimeObservation.read(later,ID),afterInput);
        canRaw.addProperty("boot_id","foreign");canRaw.addProperty("guest_boot_id","foreign");
        var foreign=VehicleCan.read(canRaw,afterInput.session(),"foreign");
        assertThrows(IOException.class,()->VehicleArmed.requireAfter(ru.voboost.tests.agents.RuntimeObservation.read(later,ID),foreign));
    }


}
