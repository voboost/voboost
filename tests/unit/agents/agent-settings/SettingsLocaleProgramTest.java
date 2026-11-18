package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettingsLocaleProgramTest {
    private static final class Port implements SettingsLocaleProgram.Port {
        boolean restorable=true,russian=true,metrics=true,unknown;final List<String> calls=new ArrayList<>();final List<SettingsLocaleProgram.Row> rows=new ArrayList<>();Path path(){return Path.of("/controlled-host-fixture/"+calls.size());}
        public SettingsLocaleProgram.Admission preflight(){calls.add("preflight");return new SettingsLocaleProgram.Admission(restorable,"controlled original-locale ownership",List.of(path()));}
        public SettingsLocaleProgram.Admission admit(String language){calls.add("admit:"+language);return new SettingsLocaleProgram.Admission(!language.equals("ru")||russian,"controlled native locale admission",List.of(path()));}
        public Path language(String language)throws IOException{calls.add("language:"+language);if(unknown)throw new IOException("controlled unknown locale dispatch");return path();}
        public Path measure(String language)throws Exception{calls.add("measure:"+language);if(!metrics)throw new UiEvidence.Unavailable("controlled missing real metric");return path();}
        public Path cancel(String language){calls.add("cancel:"+language);return path();}
        public Path restore(){calls.add("restore");return path();}
        public void retain(SettingsLocaleProgram.Row row){rows.add(row);}
    }
    @Test void actionProgramRequiresAllThreeActualLocaleMeasurementsAndCancellations()throws Exception {Port port=new Port();var proof=SettingsLocaleProgram.run(21,port);assertTrue(proof.semanticComplete());assertEquals(3,port.calls.stream().filter(c->c.startsWith("measure:")).count());assertEquals(3,port.calls.stream().filter(c->c.startsWith("cancel:")).count());assertEquals("restore",port.calls.get(port.calls.size()-1));assertEquals(3,port.rows.size());}
    @Test void unadmittedRussianIsBlockedRatherThanReplacedByEnglish()throws Exception {Port port=new Port();port.russian=false;var proof=SettingsLocaleProgram.run(20,port);assertFalse(proof.semanticComplete());assertFalse(port.calls.contains("language:ru"));assertEquals(2,port.calls.stream().filter(c->c.startsWith("measure:")).count());assertTrue(port.calls.contains("restore"));assertFalse(port.calls.stream().anyMatch(c->c.startsWith("cancel:")));}
    @Test void unrestorableOriginalLocaleBlocksBeforeAnyLanguageOrCancelInput()throws Exception {Port port=new Port();port.restorable=false;assertFalse(SettingsLocaleProgram.run(21,port).semanticComplete());assertEquals(List.of("preflight"),port.calls);}
    @Test void missingMetricsRemainBlockedAndUnknownInputNeverTriggersAlternativeMutation()throws Exception {Port port=new Port();port.metrics=false;assertFalse(SettingsLocaleProgram.run(21,port).semanticComplete());assertFalse(port.calls.stream().anyMatch(c->c.startsWith("cancel:")));port=new Port();port.unknown=true;Port current=port;assertThrows(IOException.class,()->SettingsLocaleProgram.run(21,current));assertFalse(port.calls.contains("restore"));assertEquals(1,port.calls.stream().filter(c->c.startsWith("language:")).count());}
}
