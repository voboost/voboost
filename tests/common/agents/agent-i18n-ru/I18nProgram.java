package ru.voboost.tests.agents;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Complete case22 sequence. A missing owned Android+QNX restart blocks before any mutation. */
public final class I18nProgram {
    public record Admission(boolean available,String reason,List<Path> evidence){public Admission{evidence=List.copyOf(evidence);if(reason==null||reason.isBlank())throw new IllegalArgumentException("explicit admission reason required");}}
    public record Proof(boolean accepted,String reason,List<Path> evidence,I18nObservation current){public Proof{evidence=List.copyOf(evidence);}}
    public interface Port {
        Admission admit()throws Exception;
        Path enabled(boolean enabled)throws Exception;
        Path locale(String locale)throws Exception;
        I18nObservation observe()throws Exception;
        I18nObservation awaitLocale(String locale,I18nObservation before)throws Exception;
        I18nObservation awaitDisabledRestart(I18nObservation before)throws Exception;
        Path verifyRussianAbsent()throws Exception;
        Path restartBoth()throws Exception;
        Path verifyRestoredRussian()throws Exception;
        Path retain(String stage,I18nObservation observation)throws Exception;
    }
    public static Proof run(Port port)throws Exception {
        Admission admission=port.admit();List<Path> evidence=new ArrayList<>(admission.evidence());
        if(!admission.available())return new Proof(false,admission.reason(),evidence,null);
        evidence.add(port.enabled(true));I18nObservation current=port.observe();
        for(String locale:List.of("ru","en","zh")) {
            I18nObservation before=current;evidence.add(port.locale(locale));current=port.awaitLocale(locale,before);current.requireLocale(locale,before);evidence.add(port.retain("locale-"+locale,current));
        }
        I18nObservation active=current;evidence.add(port.enabled(false));current=port.awaitDisabledRestart(active);current.requireDisabledRestart(active);evidence.add(port.verifyRussianAbsent());evidence.add(port.retain("disabled-owner-restart",current));
        evidence.add(port.enabled(true));I18nObservation before=port.observe();before.requireSameScope(current);before.requireAdvance(current);evidence.add(port.locale("ru"));current=port.awaitLocale("ru",before);current.requireLocale("ru",before);evidence.add(port.retain("reboot-armed",current));
        I18nObservation armed=current;evidence.add(port.restartBoth());evidence.add(port.verifyRestoredRussian());current=port.observe();current.requireRestored(armed);evidence.add(port.retain("reboot-restoration",current));
        return new Proof(true,"Actual OEM picker and Android process/resource locale; all three native owners applied, disabled/restarted and restored after changed Android/QNX boots",evidence,current);
    }
    private I18nProgram(){}
}
