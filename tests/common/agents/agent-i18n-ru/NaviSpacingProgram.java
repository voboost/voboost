package ru.voboost.voyemu;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Five actual committed samples for case19; pixel spacing/font provenance remain independent. */
public final class NaviSpacingProgram {
    public record Admission(boolean available,String reason,List<Path> evidence){public Admission{evidence=List.copyOf(evidence);}}
    public record Sample(String stage,String committed,Path evidence){public Sample{if(evidence==null)throw new IllegalArgumentException("actual sample evidence required");}}
    public record Proof(boolean semanticComplete,String reason,List<Sample> samples,List<Path> evidence){public Proof{samples=List.copyOf(samples);evidence=List.copyOf(evidence);}}
    public interface Port {
        Admission admit()throws Exception;
        Sample type(String stage,String text,String prefix)throws Exception;
        Path resetOwned(String committed)throws Exception;
        Path layout(String language,String committed)throws Exception;
        void retain(Sample sample)throws Exception;
    }
    private static Sample type(Port port,List<Sample> samples,List<Path> evidence,String stage,String text,String prefix)throws Exception {
        Sample actual=port.type(stage,text,prefix);if(!stage.equals(actual.stage())||!actual.committed().equals(prefix+text))throw new IOException("actual Navi committed codepoints differ");samples.add(actual);evidence.add(actual.evidence());port.retain(actual);return actual;
    }
    public static Proof run(Port port)throws Exception {
        Admission admission=port.admit();List<Path> evidence=new ArrayList<>(admission.evidence());List<Sample> samples=new ArrayList<>();
        if(!admission.available())return new Proof(false,admission.reason(),samples,evidence);
        type(port,samples,evidence,"moscow","Москва","");evidence.add(port.resetOwned("Москва"));
        type(port,samples,evidence,"yo","Ёё","");evidence.add(port.resetOwned("Ёё"));
        type(port,samples,evidence,"mixed-prefix","Москва","");evidence.add(port.layout("en","Москва"));
        type(port,samples,evidence,"mixed-latin","moscow","Москва");evidence.add(port.layout("ru","Москваmoscow"));
        type(port,samples,evidence,"mixed-yo","ё","Москваmoscow");
        return new Proof(true,"Five actual samples retain exact Moscow/Yo/mixed codepoints and UTF16 end selections; pixel spacing and font provenance pending",samples,evidence);
    }
    private NaviSpacingProgram(){}
}
