package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StartupDescriptionsTest {
    private static final class Port implements StartupDescriptions.Port {
        String language="en",mode="interface";boolean restorable=true,russian=true,wrong,unknown,lateFailure;final List<String> calls=new ArrayList<>();final List<StartupDescriptions.Row> retained=new ArrayList<>();
        private Path evidence(){return Path.of("/controlled-host-fixture/step-"+calls.size());}
        public StartupDescriptions.Admission preflight(){return new StartupDescriptions.Admission(restorable,"controlled original-locale restoration",List.of(evidence()));}
        public StartupDescriptions.Admission admit(String value){calls.add("admit:"+value);return new StartupDescriptions.Admission(!value.equals("ru")||russian,"controlled host locale admission",List.of(evidence()));}
        public void language(String value){calls.add("language:"+value);language=value;}
        public void retain(StartupDescriptions.Row row){retained.add(row);}
        public void select(String value)throws IOException {calls.add("select:"+value);mode=value;if(lateFailure&&retained.size()==1)throw new IOException("controlled later failure");if(unknown)throw new IOException("controlled unknown input");}
        public StartupDescriptions.Observation observe(){calls.add("observe");return new StartupDescriptions.Observation(language,wrong?"foreign":mode,evidence());}
        public Path returnFromNoneByComputer(){assertEquals("none",mode);calls.add("computer-return");mode="interface";return evidence();}
        public Path cancelUninstall(String language){calls.add("cancel:"+language);return evidence();}
        public Path rejectStartupMutation(String language){calls.add("reject-radio:"+language);return evidence();}
    }
    @Test void allNineProfilesCancelRejectedRadioAndComputerOnlyReturnsArePartOfTheWholeProgram()throws Exception {
        Port port=new Port();var proof=StartupDescriptions.run(port);assertTrue(proof.semanticComplete());assertEquals(18,proof.rows().size());assertEquals(3,port.calls.stream().filter("computer-return"::equals).count());assertEquals(3,port.calls.stream().filter(s->s.startsWith("cancel:")).count());assertEquals(3,port.calls.stream().filter(s->s.startsWith("reject-radio:")).count());
        assertEquals(6,port.calls.stream().filter("select:hidden"::equals).count());
    }
    @Test void unadmittedRussianContributionStaysBlockedAndNeverSelectsAnotherLanguageAsItsProof()throws Exception {
        Port port=new Port();port.russian=false;var proof=StartupDescriptions.run(port);assertFalse(proof.semanticComplete());assertFalse(port.calls.contains("language:ru"));assertEquals(1,proof.rows().stream().filter(r->r.language().equals("ru")&&r.status().equals("BLOCKED")).count());assertEquals(2,port.calls.stream().filter("computer-return"::equals).count());
    }
    @Test void unsupportedOriginalRussianCannotBeChangedWhenRestorationIsUnproven()throws Exception {Port port=new Port();port.restorable=false;var proof=StartupDescriptions.run(port);assertFalse(proof.started());assertFalse(proof.semanticComplete());assertTrue(port.calls.isEmpty());assertEquals(1,port.retained.size());assertEquals("all",port.retained.get(0).language());}
    @Test void laterFailurePreservesTheEarlierRealProfileRow()throws Exception {
        Port port=new Port();port.lateFailure=true;assertThrows(IOException.class,()->StartupDescriptions.run(port));assertEquals(1,port.retained.size());assertEquals("interface",port.retained.get(0).mode());assertEquals("PASS",port.retained.get(0).status());assertFalse(port.calls.contains("computer-return"));
    }
    @Test void wrongReadbackOrUnknownInputStopsWithoutReplayOrAlternativeUiAction()throws Exception {
        for(boolean unknown:List.of(false,true)){Port port=new Port();port.unknown=unknown;port.wrong=!unknown;assertThrows(IOException.class,()->StartupDescriptions.run(port));assertFalse(port.calls.contains("computer-return"));assertFalse(port.calls.stream().anyMatch(s->s.startsWith("cancel:")));assertEquals(unknown?1:2,port.calls.stream().filter(s->s.startsWith("select:")).count());}
    }
}
