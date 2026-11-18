package ru.voboost.tests.agents;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class StateOutageScopeTest {
    private static JsonObject current() {
        JsonObject scope=new JsonObject();for(String key:java.util.List.of("runId","work","machine","sessionId","bootId","serial","manifest","catalog","releaseIdentity","generation","publisher","writerId","ownerPid","ownerStarttime","inputPid","inputStarttime","installer","driver"))scope.addProperty(key,"current-"+key);return scope;
    }
    @Test void retainedComponentsCannotBorrowAnotherRunCandidateSessionBootOrLifetime()throws Exception {
        JsonObject trusted=current();StateOutageEvidence.requireScope(trusted.deepCopy(),trusted);
        for(String key:trusted.keySet()){JsonObject foreign=trusted.deepCopy();foreign.addProperty(key,"previous-"+key);assertThrows(IOException.class,()->StateOutageEvidence.requireScope(foreign,trusted),key);}
        assertThrows(IOException.class,()->StateOutageEvidence.requireScope(null,trusted));assertThrows(IOException.class,()->StateOutageEvidence.requireScope(new JsonObject(),new JsonObject()));
    }
    @Test void stoppedWarmReceiptsMustNameTheCurrentlyOwnedRootAndInputProcesses()throws Exception {
        String boot="11111111-1111-1111-1111-111111111111",body="current-process-identities:\nschemaVersion=1\nbootId="+boot+"\nenforcing=Enforcing\nprocess=voboost-inject pid=43 starttime=400 argv0=/data/voboost/voboost-inject\nprocess=com.qinggan.app.setting pid=45 starttime=600 argv0=com.qinggan.app.setting\nprocess=voboost-daemon pid=44 starttime=123 argv0=/data/voboost/voboost-daemon\nprocess=com.qinggan.canbus.service pid=41 starttime=514 argv0=com.qinggan.canbus.service\nbootIdAfter="+boot+"\nenforcingAfter=Enforcing\nbootCompletedAfter=1\ncurrent-process-identities-end=complete\n";
        JsonObject trusted=current();trusted.addProperty("bootId",boot);trusted.addProperty("ownerPid","44");trusted.addProperty("ownerStarttime","123");trusted.addProperty("inputPid","41");trusted.addProperty("inputStarttime","514");StateOutageEvidence.requireCurrentProcesses(body,trusted);
        for(String replacement:java.util.List.of(body.replace("pid=44 starttime=123","pid=46 starttime=123"),body.replace("pid=41 starttime=514","pid=41 starttime=515")))assertThrows(IOException.class,()->StateOutageEvidence.requireCurrentProcesses(replacement,trusted));
    }
    @Test void everyActualAccEdgeRetainsTheCurrentInputPid()throws Exception {
        JsonObject trusted=current();trusted.addProperty("inputPid","41");var can=new ru.voboost.voyemu.VehicleCan.Snapshot("boot","session","source",41,"1","2","3",2,1,new JsonObject());StateOutageEvidence.requireCurrentCan(can,trusted);var replaced=new ru.voboost.voyemu.VehicleCan.Snapshot("boot","session","source",42,"1","2","3",2,1,new JsonObject());assertThrows(IOException.class,()->StateOutageEvidence.requireCurrentCan(replaced,trusted));
    }
}
