package ru.voboost.voyemu;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Whole case23 semantic sequence; missing Russian/native admission is explicit and never caller-attested PASS. */
public final class StartupDescriptions {
    public record Admission(boolean available,String reason,List<Path> evidence) {public Admission {evidence=List.copyOf(evidence);if(reason==null||reason.isBlank())throw new IllegalArgumentException("explicit admission reason required");}}
    public record Observation(String language,String mode,Path evidence) {public Observation {if(evidence==null)throw new IllegalArgumentException("retained actual UI/root proof required");}}
    public record Row(String language,String mode,String status,String reason,List<Path> evidence) {public Row {evidence=List.copyOf(evidence);}}
    public interface Port {
        default Admission preflight()throws Exception{return new Admission(true,"No additional source precondition",List.of());}
        Admission admit(String language)throws Exception;
        void language(String language)throws Exception;
        void select(String mode)throws Exception;
        Observation observe()throws Exception;
        Path returnFromNoneByComputer()throws Exception;
        Path cancelUninstall(String language)throws Exception;
        Path rejectStartupMutation(String language)throws Exception;
        default void retain(Row row)throws Exception{}
    }
    public record Proof(boolean semanticComplete,boolean started,List<Row> rows) {public Proof {rows=List.copyOf(rows);}}
    public static Proof run(Port port)throws Exception {
        List<Row> rows=new ArrayList<>();boolean complete=true;
        Admission initial=port.preflight();if(!initial.available()){Row row=new Row("all","all","BLOCKED",initial.reason(),initial.evidence());rows.add(row);port.retain(row);return new Proof(false,false,rows);}
        for(String language:List.of("en","ru","zh")) {
            Admission admission=port.admit(language);
            if(!admission.available()) {complete=false;Row row=new Row(language,"all","BLOCKED",admission.reason(),admission.evidence());rows.add(row);port.retain(row);continue;}
            port.language(language);
            for(String mode:List.of("interface","hidden","none")) {
                if(mode.equals("interface"))port.select("hidden"); // Require a changed interface selection as well.
                port.select(mode);Observation actual=port.observe();
                if(!language.equals(actual.language())||!mode.equals(actual.mode()))throw new IOException("startup UI language/mode differs from actual root selection");
                Row profile=new Row(language,mode,"PASS","Actual selected description and deferred control/root/process readback",List.of(actual.evidence()));rows.add(profile);port.retain(profile);
                if(mode.equals("none")) {
                    Path returned=port.returnFromNoneByComputer();Observation current=port.observe();
                    if(!language.equals(current.language())||!"interface".equals(current.mode()))throw new IOException("computer-only return from none lacks actual interface readback");
                    Row returnedRow=new Row(language,"computer-return","PASS","Packaged computer installer restored interface",List.of(returned,current.evidence()));rows.add(returnedRow);port.retain(returnedRow);
                }
            }
            Row cancelled=new Row(language,"cancel","PASS","Actual OEM dialog cancellation preserves the installation",List.of(port.cancelUninstall(language)));rows.add(cancelled);port.retain(cancelled);
            Row rejected=new Row(language,"rejected-radio","PASS","Rejected startup persistence retains canonical selection and configuration",List.of(port.rejectStartupMutation(language)));rows.add(rejected);port.retain(rejected);
        }
        return new Proof(complete,true,rows);
    }
}
