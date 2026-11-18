package ru.voboost.voyemu;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Whole EN/RU/ZH measurement/cancel sequence with owned original-locale restoration. */
public final class SettingsLocaleProgram {
    public record Admission(boolean available,String reason,List<Path> evidence){public Admission{evidence=List.copyOf(evidence);}}
    public record Row(String language,String status,String reason,List<Path> evidence){public Row{evidence=List.copyOf(evidence);}}
    public record Proof(boolean semanticComplete,List<Row> rows,List<Path> evidence){public Proof{rows=List.copyOf(rows);evidence=List.copyOf(evidence);}}
    public interface Port {
        Admission preflight()throws Exception;
        Admission admit(String language)throws Exception;
        Path language(String language)throws Exception;
        Path measure(String language)throws Exception;
        Path cancel(String language)throws Exception;
        Path restore()throws Exception;
        void retain(Row row)throws Exception;
    }
    public static Proof run(int number,Port port)throws Exception {
        if(number!=20&&number!=21)throw new IllegalArgumentException("closed Settings layout case required");
        List<Row> rows=new ArrayList<>();List<Path> evidence=new ArrayList<>();Admission initial=port.preflight();evidence.addAll(initial.evidence());
        if(!initial.available()){Row row=new Row("all","BLOCKED",initial.reason(),initial.evidence());rows.add(row);port.retain(row);return new Proof(false,rows,evidence);}
        boolean complete=true;
        for(String language:List.of("en","ru","zh")) {
            Admission admission=port.admit(language);List<Path> files=new ArrayList<>(admission.evidence());
            if(!admission.available()){complete=false;Row row=new Row(language,"BLOCKED",admission.reason(),files);rows.add(row);port.retain(row);continue;}
            files.add(port.language(language));Row row;
            try{files.add(port.measure(language));if(number==21)files.add(port.cancel(language));row=new Row(language,"PASS","Actual exported metrics"+(number==21?" and observed Cancel with independent unchanged product readback":"")+"; mandatory canvas/font/visual acceptance separate",files);}
            catch(UiEvidence.Unavailable unavailable){complete=false;row=new Row(language,"BLOCKED",unavailable.getMessage(),files);}
            rows.add(row);evidence.addAll(files);port.retain(row);
        }
        evidence.add(port.restore());return new Proof(complete,rows,evidence);
    }
    private SettingsLocaleProgram(){}
}
