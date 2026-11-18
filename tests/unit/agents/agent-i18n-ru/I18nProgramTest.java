package ru.voboost.tests.agents;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class I18nProgramTest {
    private static final class Port implements I18nProgram.Port {
        boolean available=true,unknown;int observations,localeCalls;final List<String> calls=new ArrayList<>();
        Path path(){return Path.of("/controlled-host-fixture/"+calls.size());}
        I18nObservation proof(int revision,String locale,boolean enabled,boolean restored)throws Exception {
            JsonObject input=I18nObservationTest.input(revision,locale,enabled),cluster=I18nObservationTest.cluster(input);if(revision>=14)cluster.addProperty("desiredRevision",revision==14?8:9);
            var targets=cluster.getAsJsonArray("agents").get(0).getAsJsonObject().getAsJsonArray("targets");
            if(revision==14||restored)for(var item:targets){var process=item.getAsJsonObject().getAsJsonObject("process");process.addProperty("pid",process.get("pid").getAsInt()+100);process.addProperty("birthIdentity",process.get("birthIdentity").getAsString()+"-new");process.addProperty("endpointEpoch",process.get("endpointEpoch").getAsString()+"-new");}
            var identity=DaemonRuntimeObservationTest.IDENTITY;
            if(restored){cluster.addProperty("bootId","qnx-after");cluster.addProperty("daemonEpoch","epoch-after");input.addProperty("boot_id","after");input.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("runtime").getAsJsonObject("data").getAsJsonObject("publisher").addProperty("bootId","after");identity=new RuntimeObservation.Identity("after","candidate","manifest","catalog","generation");}
            return I18nObservation.read(RuntimeObservation.read(input,identity));
        }
        public I18nProgram.Admission admit(){calls.add("admit");return new I18nProgram.Admission(available,"controlled host capability admission",List.of(path()));}
        public Path enabled(boolean value)throws Exception{calls.add("enabled:"+value);if(unknown)throw new IOException("controlled unknown dispatch");return path();}
        public Path locale(String value){calls.add("locale:"+value);return path();}
        public I18nObservation observe()throws Exception{return switch(++observations){case 1->proof(10,"English",true,false);case 2->proof(15,"Chinese",true,false);default->proof(17,"Russian",true,true);};}
        public I18nObservation awaitLocale(String locale,I18nObservation before)throws Exception{int revision=switch(++localeCalls){case 1->11;case 2->12;case 3->13;default->16;};return proof(revision,switch(locale){case "ru"->"Russian";case "en"->"English";default->"Chinese";},true,false);}
        public I18nObservation awaitDisabledRestart(I18nObservation before)throws Exception{return proof(14,"Chinese",false,false);}
        public Path verifyRussianAbsent(){calls.add("ru-absent");return path();}
        public Path restartBoth(){calls.add("restart-both");return path();}
        public Path verifyRestoredRussian(){calls.add("restored-picker-render");return path();}
        public Path retain(String stage,I18nObservation observed){calls.add("retain:"+stage);return path();}
    }
    @Test void missingFullMachineCapabilityBlocksBeforeEveryMutation()throws Exception {Port port=new Port();port.available=false;var proof=I18nProgram.run(port);assertFalse(proof.accepted());assertNull(proof.current());assertEquals(List.of("admit"),port.calls);}
    @Test void wholeProgramIncludesAllLocalesDisableRestartAndBothBootRestoration()throws Exception {Port port=new Port();var proof=I18nProgram.run(port);assertTrue(proof.accepted());assertNotNull(proof.current());assertEquals(List.of("locale:ru","locale:en","locale:zh","locale:ru"),port.calls.stream().filter(c->c.startsWith("locale:")).toList());assertEquals(1,port.calls.stream().filter("restart-both"::equals).count());assertTrue(port.calls.indexOf("ru-absent")<port.calls.indexOf("restart-both"));assertTrue(port.calls.contains("retain:reboot-restoration"));}
    @Test void unknownAcceptedInputIsNeverReplayedOrFollowedByAnotherMutation()throws Exception {Port port=new Port();port.unknown=true;assertThrows(IOException.class,()->I18nProgram.run(port));assertEquals(List.of("admit","enabled:true"),port.calls);}
}
