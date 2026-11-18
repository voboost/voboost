package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NaviSpacingProgramTest {
    private static final class Port implements NaviSpacingProgram.Port {
        boolean available=true,unknown,wrong;String value="";final List<String> calls=new ArrayList<>();final List<NaviSpacingProgram.Sample> retained=new ArrayList<>();
        Path path(){return Path.of("/controlled-host-fixture/"+calls.size());}
        public NaviSpacingProgram.Admission admit(){calls.add("admit");return new NaviSpacingProgram.Admission(available,"controlled editor capability",List.of(path()));}
        public NaviSpacingProgram.Sample type(String stage,String text,String prefix)throws IOException{assertEquals(prefix,value);calls.add("type:"+stage);value+=wrong?"foreign":text;if(unknown)throw new IOException("controlled unknown key");return new NaviSpacingProgram.Sample(stage,value,path());}
        public Path resetOwned(String committed){assertEquals(committed,value);calls.add("reset:"+committed);value="";return path();}
        public Path layout(String language,String committed){assertEquals(committed,value);calls.add("layout:"+language);return path();}
        public void retain(NaviSpacingProgram.Sample sample){retained.add(sample);}
    }
    @Test void allFiveExactSamplesAndObservedLayoutTransitionsAreRequired()throws Exception {Port port=new Port();var proof=NaviSpacingProgram.run(port);assertTrue(proof.semanticComplete());assertEquals(List.of("Москва","Ёё","Москва","Москваmoscow","Москваmoscowё"),proof.samples().stream().map(NaviSpacingProgram.Sample::committed).toList());assertEquals(2,port.calls.stream().filter(c->c.startsWith("reset:")).count());assertEquals(List.of("layout:en","layout:ru"),port.calls.stream().filter(c->c.startsWith("layout:")).toList());assertEquals(5,port.retained.size());}
    @Test void missingEditorMetadataBlocksBeforeKeysResetOrLayoutMutation()throws Exception {Port port=new Port();port.available=false;var proof=NaviSpacingProgram.run(port);assertFalse(proof.semanticComplete());assertEquals(List.of("admit"),port.calls);}
    @Test void wrongCodepointsOrUnknownKeyNeverAuthorizeResetReplayOrNextSample()throws Exception {for(boolean unknown:List.of(false,true)){Port port=new Port();port.unknown=unknown;port.wrong=!unknown;assertThrows(IOException.class,()->NaviSpacingProgram.run(port));assertEquals(List.of("admit","type:moscow"),port.calls);assertTrue(port.retained.isEmpty());}}
}
