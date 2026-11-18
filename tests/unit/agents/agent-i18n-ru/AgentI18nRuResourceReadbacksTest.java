package ru.voboost.tests.agents;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

class AgentI18nRuResourceReadbacksTest {
    static JsonObject input() throws Exception {
        JsonObject input=DaemonRuntimeObservationTest.observation();
        input.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("config").getAsJsonObject("data").getAsJsonObject("agents").add("i18n-ru",RunFiles.parse("{\"enabled\":true,\"options\":{}}"));
        var data=input.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("runtime").getAsJsonObject("data");
        JsonArray injections=data.getAsJsonArray("injections"), owners=new JsonArray();
        for (var entry:I18nResourceReadbacks.PROFILES.entrySet()) {
            String owner=entry.getKey(), profile=entry.getValue().get(0);
            var binding=RunFiles.parse("{\"targetPid\":\"123\",\"targetStarttime\":\"456\",\"endpointEpoch\":\"789\",\"generationId\":\"generation\",\"catalogSha256\":\"catalog\",\"desiredDocumentGeneration\":\"3\"}");
            JsonObject row=new JsonObject();row.addProperty("id","i18n-ru");row.addProperty("process",owner);row.addProperty("state","active");row.add("currentEndpoint",binding.deepCopy());injections.add(row);
            JsonObject proof=new JsonObject();proof.addProperty("owner",owner);proof.addProperty("profile",profile);proof.addProperty("probeRevision","9");proof.addProperty("ageMs",1);proof.add("binding",binding);owners.add(proof);
        }
        JsonObject report=new JsonObject();report.addProperty("schema",1);report.addProperty("kind","forced-ru-resource-readback");report.add("owners",owners);data.add("i18nResourceReadbacks",report);return input;
    }
    static RuntimeObservation read(JsonObject input)throws Exception{return RuntimeObservation.read(input,DaemonRuntimeObservationTest.IDENTITY);}
    static JsonArray rows(JsonObject input){return input.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("runtime").getAsJsonObject("data").getAsJsonObject("i18nResourceReadbacks").getAsJsonArray("owners");}
    @Test void requiresSevenDistinctFreshMatchingActualEndpoints() throws Exception {
        var input=input();assertEquals(7,I18nResourceReadbacks.read(read(input)).size());
        var publication=input.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("runtime").getAsJsonObject("data");
        publication.getAsJsonArray("injections").get(1).getAsJsonObject().getAsJsonObject("currentEndpoint").add("acceptedStatus",new JsonObject());
        assertEquals(7,I18nResourceReadbacks.read(read(input)).size());
        var missing=input();rows(missing).remove(0);assertThrows(IOException.class,()->I18nResourceReadbacks.read(read(missing)));
        var disabled=input();disabled.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("config").getAsJsonObject("data").getAsJsonObject("agents").getAsJsonObject("i18n-ru").addProperty("enabled",false);assertThrows(IOException.class,()->I18nResourceReadbacks.read(read(disabled)));
        var duplicate=input();rows(duplicate).add(rows(duplicate).get(0).deepCopy());assertThrows(IOException.class,()->I18nResourceReadbacks.read(read(duplicate)));
        for(String key:new String[]{"targetPid","targetStarttime","endpointEpoch","generationId","catalogSha256","desiredDocumentGeneration"}){var wrong=input();rows(wrong).get(0).getAsJsonObject().getAsJsonObject("binding").addProperty(key,"1");assertThrows(IOException.class,()->I18nResourceReadbacks.read(read(wrong)),key);}
        var stale=input();rows(stale).get(0).getAsJsonObject().addProperty("ageMs",30001);assertThrows(IOException.class,()->I18nResourceReadbacks.read(read(stale)));
    }
    @Test void forcedRuProofNeverClaimsActualCurrentLocale() throws Exception {
        var input=input();var result=I18nResourceReadbacks.read(read(input));assertEquals("forced-ru-resource-readback",result.kind());
        assertThrows(IOException.class,()->I18nResourceReadbacks.readCurrent(read(input)));
        var data=input.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("runtime").getAsJsonObject("data");
        JsonObject current=data.getAsJsonObject("i18nResourceReadbacks").deepCopy();current.addProperty("kind","current-owner-resource-readback");data.add("i18nCurrentReadbacks",current);
        assertEquals(7,I18nResourceReadbacks.readCurrent(read(input)).size());
        current.addProperty("kind","forced-ru-resource-readback");assertThrows(IOException.class,()->I18nResourceReadbacks.readCurrent(read(input)));
    }
    @Test void requestedLocaleCannotBorrowAnotherLocaleOwnerProof() throws Exception {
        var input=input();var data=input.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("runtime").getAsJsonObject("data");
        JsonObject current=data.getAsJsonObject("i18nResourceReadbacks").deepCopy();data.add("i18nCurrentReadbacks",current);
        for(String locale:List.of("ru","en","zh")) {
            current.addProperty("kind",locale.equals("ru")?"current-owner-resource-readback":"current-"+locale+"-owner-resource-readback");
            assertEquals(7,I18nResourceReadbacks.readCurrent(read(input),locale).size());
            for(String other:List.of("ru","en","zh")) if(!other.equals(locale))assertThrows(IOException.class,()->I18nResourceReadbacks.readCurrent(read(input),other));
        }
    }
    @Test void currentOwnerReceiptMustFollowThisLocaleDispatchFence() throws Exception {
        var input=input();var data=input.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("runtime").getAsJsonObject("data");
        JsonObject current=data.getAsJsonObject("i18nResourceReadbacks").deepCopy();current.addProperty("kind","current-en-owner-resource-readback");data.add("i18nCurrentReadbacks",current);
        assertThrows(IOException.class,()->I18nResourceReadbacks.readCurrent(read(input),"en",5000));
        assertEquals(7,I18nResourceReadbacks.readCurrent(read(input),"en",5001).size());
        current.getAsJsonArray("owners").get(0).getAsJsonObject().addProperty("ageMs",2);
        assertThrows(IOException.class,()->I18nResourceReadbacks.readCurrent(read(input),"en",5001));
    }
    @Test void guestClockFenceRequiresLaterSamePublisherAndEveryOwnerAfterIt() throws Exception {
        var input=input();var data=input.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("runtime").getAsJsonObject("data");
        var publisher=data.getAsJsonObject("publisher");publisher.addProperty("publishedAtBootMs","100000");
        JsonObject current=data.getAsJsonObject("i18nResourceReadbacks").deepCopy();current.addProperty("kind","current-en-owner-resource-readback");data.add("i18nCurrentReadbacks",current);
        var first=read(input);assertThrows(IOException.class,()->I18nResourceReadbacks.afterPublication(first,read(input)));
        publisher.addProperty("publishedAtBootMs","100010");var second=read(input);var fence=I18nResourceReadbacks.afterPublication(first,second);
        assertThrows(IOException.class,()->I18nResourceReadbacks.readCurrentAfter(second,"en",fence));
        publisher.addProperty("publishedAtBootMs","100020");assertEquals(7,I18nResourceReadbacks.readCurrentAfter(read(input),"en",fence).size());
        current.getAsJsonArray("owners").get(0).getAsJsonObject().addProperty("ageMs",10);assertThrows(IOException.class,()->I18nResourceReadbacks.readCurrentAfter(read(input),"en",fence));
        publisher.addProperty("daemonPid",101);assertThrows(IOException.class,()->I18nResourceReadbacks.readCurrentAfter(read(input),"en",fence));
    }
    @Test void readbackIsBoundToExactTargetAndCannotBeExpanded()throws Exception {
        var result=I18nResourceReadbacks.read(read(input()));
        var target=result.requireTarget("com.qinggan.app.setting");
        assertEquals("com.qinggan.app.setting",target.owner());
        assertFalse(target.targetPid().isBlank());
        assertFalse(target.endpointEpoch().isBlank());
        assertThrows(IOException.class,()->result.requireTarget("com.qinggan.foreign"));
        assertThrows(UnsupportedOperationException.class,()->result.targets().clear());
    }
}
