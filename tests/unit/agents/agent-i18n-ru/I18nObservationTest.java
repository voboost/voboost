package ru.voboost.tests.agents;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

class I18nObservationTest {
    static JsonObject input(int revision,String locale,boolean enabled)throws Exception {
        JsonObject value=DaemonRuntimeObservationTest.observation();
        var files=value.getAsJsonObject("data").getAsJsonObject("files");files.getAsJsonObject("config").getAsJsonObject("data").getAsJsonObject("agents").add("i18n-ru",RunFiles.parse("{\"enabled\":"+enabled+",\"options\":{}}"));
        JsonObject cluster=RunFiles.parse("{\"schemaVersion\":1,\"domain\":\"cluster\",\"deliveryState\":\"idle\",\"snapshotRevision\":"+revision+",\"observationRevision\":"+revision+",\"desiredRevision\":7,\"bootId\":\"qnx-before\",\"daemonEpoch\":\"epoch-before\",\"generationId\":\"native-generation\",\"catalogSha256\":\"native-catalog\",\"desiredSha256\":\"native-desired\"}");
        JsonObject agent=RunFiles.parse("{\"id\":\"i18n-ru\",\"enabled\":"+enabled+",\"state\":\""+(enabled?"applied":"disabled")+"\"}");JsonArray targets=new JsonArray();int pid=40;
        for(String owner:new String[]{"gauge","point","light"}) {
            JsonObject target=RunFiles.parse("{\"id\":\"i18n-ru\",\"targetProcess\":\""+owner+"\",\"state\":\""+(enabled?"applied":"disabled")+"\",\"reason\":\"confirmed "+locale+" locale and selected dictionary\",\"process\":{\"pid\":"+(++pid)+",\"birthIdentity\":\"birth-"+owner+"\",\"endpointEpoch\":\"endpoint-"+owner+"\",\"lifecycleGeneration\":1}}");targets.add(target);
        }
        agent.add("targets",targets);JsonArray agents=new JsonArray();agents.add(agent);cluster.add("agents",agents);files.getAsJsonObject("runtime").getAsJsonObject("data").add("cluster",cluster);return value;
    }
    static JsonObject cluster(JsonObject value){return value.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("runtime").getAsJsonObject("data").getAsJsonObject("cluster");}
    private static I18nObservation read(JsonObject value)throws Exception{return I18nObservation.read(RuntimeObservation.read(value,DaemonRuntimeObservationTest.IDENTITY));}
    @Test void eachRealOwnerMustConfirmTheSelectedDictionaryInAnIndependentResponse()throws Exception {
        var before=read(input(10,"English",true));var next=read(input(11,"Russian",true));next.requireLocale("ru",before);
        assertThrows(IOException.class,()->before.requireLocale("en",before));
        var wrong=input(11,"Russian",true);cluster(wrong).getAsJsonArray("agents").get(0).getAsJsonObject().getAsJsonArray("targets").get(1).getAsJsonObject().addProperty("reason","aggregate applied");assertThrows(IOException.class,()->read(wrong).requireLocale("ru",before));
    }
    @Test void missingDuplicateUnknownOrNullOwnerCannotBeReplacedByAnAggregate()throws Exception {
        for(String mutation:new String[]{"missing","duplicate","unknown","null"}) {
            var value=input(10,"Russian",true);var targets=cluster(value).getAsJsonArray("agents").get(0).getAsJsonObject().getAsJsonArray("targets");
            switch(mutation){case "missing"->targets.remove(0);case "duplicate"->targets.add(targets.get(0).deepCopy());case "unknown"->targets.get(0).getAsJsonObject().addProperty("targetProcess","other");case "null"->targets.get(0).getAsJsonObject().add("process",com.google.gson.JsonNull.INSTANCE);}
            assertThrows(IOException.class,()->read(value));
        }
    }
    @Test void disabledFlagWithoutIndependentOwnerRestartIsInsufficient()throws Exception {
        var before=read(input(10,"Chinese",true));var disabled=input(11,"Chinese",false);cluster(disabled).addProperty("desiredRevision",8);assertThrows(IOException.class,()->read(disabled).requireDisabledRestart(before));
        var targets=cluster(disabled).getAsJsonArray("agents").get(0).getAsJsonObject().getAsJsonArray("targets");
        for(var value:targets){var process=value.getAsJsonObject().getAsJsonObject("process");process.addProperty("pid",process.get("pid").getAsInt()+100);process.addProperty("birthIdentity",process.get("birthIdentity").getAsString()+"-new");process.addProperty("endpointEpoch",process.get("endpointEpoch").getAsString()+"-new");}
        read(disabled).requireDisabledRestart(before);
    }
    @Test void retainedAdmissionWindowRejectsOwnerRestartAndCounterRegression()throws Exception {
        var before=read(input(10,"Russian",true));read(input(11,"Russian",true)).requireCurrentWindow(before);
        assertThrows(IOException.class,()->read(input(9,"Russian",true)).requireCurrentWindow(before));
        var changed=input(11,"Russian",true);cluster(changed).getAsJsonArray("agents").get(0).getAsJsonObject().getAsJsonArray("targets").get(0).getAsJsonObject().getAsJsonObject("process").addProperty("birthIdentity","restarted");assertThrows(IOException.class,()->read(changed).requireCurrentWindow(before));
    }
    @Test void changedPublisherPidCannotSubstituteForAnActualAndroidReboot()throws Exception {
        var before=read(input(10,"Russian",true));var next=input(11,"Russian",true);cluster(next).addProperty("bootId","qnx-after");cluster(next).addProperty("daemonEpoch","epoch-after");
        var publication=next.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("runtime").getAsJsonObject("data");publication.getAsJsonObject("publisher").addProperty("daemonPid",101);
        for(var value:cluster(next).getAsJsonArray("agents").get(0).getAsJsonObject().getAsJsonArray("targets")){var process=value.getAsJsonObject().getAsJsonObject("process");process.addProperty("birthIdentity",process.get("birthIdentity").getAsString()+"-after");process.addProperty("endpointEpoch",process.get("endpointEpoch").getAsString()+"-after");}
        assertThrows(IOException.class,()->read(next).requireRestored(before));
    }
    @Test void staleRootOrChangedNativeScopeNeverSuppliesLocaleProof()throws Exception {
        var before=read(input(10,"English",true));for(String key:new String[]{"bootId","daemonEpoch","catalogSha256","generationId"}) {var next=input(11,"Russian",true);cluster(next).addProperty(key,"different");assertThrows(IOException.class,()->read(next).requireLocale("ru",before));}
        var stale=input(11,"Russian",true);stale.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("runtime").getAsJsonObject("provenance").addProperty("current",false);assertThrows(IOException.class,()->read(stale));
    }
}
