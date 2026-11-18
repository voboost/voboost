package ru.voboost.tests.agents;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.installer.cli.StartupObservation;

class VehicleInputReadyTest {
    private static final String BOOT="12345678-1234-1234-1234-123456789abc",AGENT="vehicle-drive-pedestrian-warning",PROCESS="com.qinggan.app.vehiclesetting";
    private static final RuntimeObservation.Identity ID=new RuntimeObservation.Identity(BOOT,"candidate","manifest","catalog","generation");
    private static JsonObject fixture()throws Exception {
        JsonObject raw=DaemonRuntimeObservationTest.observation();raw.addProperty("boot_id",BOOT);
        var config=raw.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("config").getAsJsonObject("data").getAsJsonObject("agents");
        config.add(AGENT,ru.voboost.tests.RunFiles.parse("{\"enabled\":true,\"options\":{}}"));
        var publication=raw.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("runtime").getAsJsonObject("data");
        var publisher=publication.getAsJsonObject("publisher");publisher.addProperty("bootId",BOOT);
        var rows=publication.getAsJsonArray("injections");rows.remove(0);
        JsonObject row=ru.voboost.tests.RunFiles.parse("""
            {"id":"vehicle-drive-pedestrian-warning","process":"com.qinggan.app.vehiclesetting","state":"active","reason":"ready","documentGeneration":123,
             "currentEndpoint":{"targetPid":"4436","targetStarttime":"3436","endpointEpoch":"22","generationId":"generation","catalogSha256":"catalog","desiredDocumentGeneration":"123",
             "acceptedStatus":{"documentGeneration":"123","receipt":{"bootId":"BOOT","daemonPid":"100","daemonStarttime":"18446744073709551615","sequence":"9"}}}}
            """.replace("BOOT",BOOT));rows.add(row);return raw;
    }
    private static JsonObject row(JsonObject raw){return raw.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("runtime").getAsJsonObject("data").getAsJsonArray("injections").get(0).getAsJsonObject();}
    private static void check(JsonObject raw)throws Exception {VehicleInputReady.require(RuntimeObservation.read(raw,ID),AGENT,PROCESS,new StartupObservation.ProcessIdentity(4436,"3436",PROCESS));}
    @Test void onlyCurrentAcceptedReadyInputPermitsTheTap()throws Exception {check(fixture());}
    @Test void checkboxEnabledWithoutTargetAndOldOrForeignAcceptedEndpointNeverPermitTap()throws Exception {
        var absent=fixture();absent.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("runtime").getAsJsonObject("data").getAsJsonArray("injections").remove(0);
        assertThrows(IOException.class,()->check(absent));
        for(String field:new String[]{"targetPid","targetStarttime","catalogSha256","generationId","desiredDocumentGeneration"}) {
            var raw=fixture();row(raw).getAsJsonObject("currentEndpoint").addProperty(field,"other");assertThrows(IOException.class,()->check(raw),field);
        }
        var waiting=fixture();row(waiting).addProperty("state","waiting");assertThrows(IOException.class,()->check(waiting));
        var old=fixture();row(old).getAsJsonObject("currentEndpoint").getAsJsonObject("acceptedStatus").addProperty("documentGeneration","122");assertThrows(IOException.class,()->check(old));
    }
}
