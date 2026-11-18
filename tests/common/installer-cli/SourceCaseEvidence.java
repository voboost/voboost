package ru.voboost.tests.installer.cli;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import ru.voboost.tests.AcceptancePlan;
import ru.voboost.tests.AcceptanceReport;
import ru.voboost.tests.CaseEvidence;
import ru.voboost.tests.GateStatus;
import ru.voboost.tests.RunFiles;

/** One closed mapping from actual numbered recipe outcomes to retained product verdicts. */
public final class SourceCaseEvidence {
    public record Entry(AcceptancePlan.Key key,AcceptanceReport.Result result){}
    public static Entry evaluate(int number,JsonObject proof,Path file)throws IOException {return evaluate(number,proof,file,null,null);}
    public static Entry evaluate(int number,JsonObject proof,Path file,Path verifiedInstaller,Path actualCatalog)throws IOException {return evaluate(number,proof,file,verifiedInstaller,actualCatalog,null);}
    public static Entry evaluate(int number,JsonObject proof,Path file,Path verifiedInstaller,Path actualCatalog,JsonObject currentRunScope)throws IOException {
        if(!proof.has("caseNumber")||!proof.get("caseNumber").isJsonPrimitive()||!proof.getAsJsonPrimitive("caseNumber").isNumber()||!Integer.toString(number).equals(proof.get("caseNumber").getAsString()))throw new IOException("terminal proof belongs to another numbered case");
        String id=switch(number){case 9->"font-cyrillic";case 10->"keyboard-en";case 11->"keyboard-ru-en";case 12->"launcher-persistence";case 13->"forced-ev-persistence";case 14->"pedestrian-warning-persistence";case 18->"catalog-classification";case 19->"navi-input-spacing";case 20->"control-layout";case 21->"action-button-layout";case 22->"chinese-language-persistence";case 23->"startup-descriptions";case 24->"initial-state-seeding";case 25->"state-outage-recovery";case 26->"every-guest-reboot";case 27->"root-boundary";default->throw new IOException("closed functional case required");};
        if(number==25) {
            String reason=text(proof,"reason");
            boolean claimed="PASS".equals(text(proof,"semanticFunctionalStatus"))&&proof.has("functionalAcceptance")&&proof.get("functionalAcceptance").isJsonPrimitive()&&proof.getAsJsonPrimitive("functionalAcceptance").isBoolean()&&proof.get("functionalAcceptance").getAsBoolean();
            if(!claimed||!proof.has("subchecks")||!proof.get("subchecks").isJsonObject()||!proof.getAsJsonObject("subchecks").keySet().equals(ru.voboost.tests.agents.StateOutageEvidence.REQUIRED_PROGRAMS))return new Entry(new AcceptancePlan.Key(id,"product"),new AcceptanceReport.Result(GateStatus.BLOCKED,reason+"; all six native PW fault and FEV/PW disabled warm programs are required",List.of(file)));
            ru.voboost.tests.agents.StateOutageEvidence.requireWhole(proof,actualCatalog,currentRunScope);return new Entry(new AcceptancePlan.Key(id,"product"),new AcceptanceReport.Result(GateStatus.PASS,"All six actual native fault/disabled warm programs and original-state restoration re-evaluated",List.of(file)));
        }
        if(number==26) {
            if("PASS".equals(text(proof,"semanticFunctionalStatus"))){GuestBootLedger.readProof(proof);return new Entry(new AcceptancePlan.Key(id,"product"),new AcceptanceReport.Result(GateStatus.PASS,"Native-mode every-boot readiness, Enforcing, owned PID/session and original capture/hierarchy evidence re-evaluated",List.of(file)));}
            return new Entry(new AcceptancePlan.Key(id,"product"),new AcceptanceReport.Result(GateStatus.BLOCKED,text(proof,"reason"),List.of(file)));
        }
        if(number==24) {
            if("PASS".equals(text(proof,"semanticFunctionalStatus"))) {
                InitialSeedingAdmission.requireTerminal(proof,verifiedInstaller,actualCatalog);
                return new Entry(new AcceptancePlan.Key(id,"product"),new AcceptanceReport.Result(GateStatus.PASS,"Two normal selected-package clean installations, authenticated first reads/commits, independent OEM/root states and complete normal suite restoration re-evaluated",List.of(file)));
            }
            InitialSeedingAdmission.requireUnmutatedBlocked(proof);
        }
        GateStatus status=GateStatus.BLOCKED;String reason;
        if(number==12)reason="Semantic fixture lifecycle "+(proof.has("semanticPersistenceVerified")&&proof.get("semanticPersistenceVerified").isJsonPrimitive()&&proof.getAsJsonPrimitive("semanticPersistenceVerified").isBoolean()&&proof.get("semanticPersistenceVerified").getAsBoolean()?"PASS":"BLOCKED")+"; mandatory icon/pixel review pending";
        else if(number==18)reason="Semantic package/catalog subcheck "+text(proof,"semanticFunctionalStatus")+": "+text(proof,"reason")+"; mandatory stock icon/binding visual acceptance pending";
        else if(number==14){
            reason="PW persistence semantic "+text(proof,"semanticFunctionalStatus")+": "+text(proof,"reason")+"; required original rendered three-phase icon proof has not been admitted";
            if(proof.has("renderedPwProof")) {
                if(!proof.get("renderedPwProof").isJsonObject())throw new IOException("typed actual PW raster proof required");
                var rendered=ru.voboost.tests.ui.PedestrianWarningPixels.readProof(proof.getAsJsonObject("renderedPwProof"));
                if("PASS".equals(text(proof,"semanticFunctionalStatus"))&&proof.has("functionalAcceptance")&&proof.get("functionalAcceptance").isJsonPrimitive()&&proof.getAsJsonPrimitive("functionalAcceptance").isBoolean()&&proof.get("functionalAcceptance").getAsBoolean()){
                    status=GateStatus.PASS;reason="Actual PW persistence and original glyph visible/hidden/restored counterfactual re-evaluated on fresh same-owner QNX sequences "+rendered.visibleSequence()+"/"+rendered.hiddenSequence()+"/"+rendered.restoredSequence();
                }
            }
        }

        else if(number<=11||number==19)reason="Semantic input subcheck "+text(proof,"semanticInputStatus")+": "+text(proof,"reason")+"; required visual/font acceptance pending";
        else if(number==20||number==21)reason="Semantic layout/action subcheck "+text(proof,"semanticFunctionalStatus")+": "+text(proof,"reason")+"; full font/native draw/visual acceptance remains pending";
        else if(number==22)reason="Semantic language/native subcheck "+text(proof,"semanticFunctionalStatus")+": "+text(proof,"reason")+"; copy/dialog/retry-error and mandatory native draw/visual acceptance remain separate";
        else if(number==23)reason="Semantic startup profiles "+text(proof,"semanticFunctionalStatus")+": "+text(proof,"reason")+"; full startup-description/native draw acceptance remains pending";
        else {if("PASS".equals(text(proof,"semanticFunctionalStatus"))&&proof.has("functionalAcceptance")&&proof.get("functionalAcceptance").isJsonPrimitive()&&proof.getAsJsonPrimitive("functionalAcceptance").isBoolean()&&proof.get("functionalAcceptance").getAsBoolean())status=GateStatus.PASS;reason=text(proof,"reason");}
        return new Entry(new AcceptancePlan.Key(id,"product"),new AcceptanceReport.Result(status,reason,List.of(file)));
    }
    public static void record(int number,Path file)throws IOException {Entry entry=evaluate(number,RunFiles.read(file),file);CaseEvidence.record(entry.key(),entry.result());}
    public static void record(int number,Path file,Path verifiedInstaller,Path actualCatalog)throws IOException {Entry entry=evaluate(number,RunFiles.read(file),file,verifiedInstaller,actualCatalog);CaseEvidence.record(entry.key(),entry.result());}
    public static void record(int number,Path file,Path verifiedInstaller,Path actualCatalog,JsonObject currentScope)throws IOException {Entry entry=evaluate(number,RunFiles.read(file),file,verifiedInstaller,actualCatalog,currentScope);CaseEvidence.record(entry.key(),entry.result());}
    private static String text(JsonObject value,String key)throws IOException {if(!value.has(key)||!value.get(key).isJsonPrimitive()||!value.getAsJsonPrimitive(key).isString()||value.get(key).getAsString().isBlank())throw new IOException("actual terminal proof field missing: "+key);return value.get(key).getAsString();}
    private SourceCaseEvidence(){}
}
