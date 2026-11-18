package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.GateStatus;
import ru.voboost.tests.RunFiles;

class SourceCaseEvidenceTest {
    @Test void oemFevSubsetCannotGrantTheSeparateDrawerProgram()throws Exception {
        var proof=RunFiles.parse("{\"caseNumber\":13,\"semanticFunctionalStatus\":\"PARTIAL\",\"oemPersistenceStatus\":\"PASS\",\"functionalAcceptance\":false,\"reason\":\"drawer not observed\"}");
        assertEquals(GateStatus.BLOCKED,SourceCaseEvidence.evaluate(13,proof,FILE).result().status());
        proof.addProperty("functionalAcceptance",true);
        assertEquals(GateStatus.BLOCKED,SourceCaseEvidence.evaluate(13,proof,FILE).result().status());
    }

    private static final Path FILE=Path.of("/controlled-host-fixture/terminal.json");
    @Test void actualCase22BlockedProofPublishesItsClosedPlanKey()throws Exception {
        var proof=RunFiles.parse("{\"caseNumber\":22,\"semanticFunctionalStatus\":\"BLOCKED\",\"functionalAcceptance\":false,\"reason\":\"owned Android+QNX restart API unavailable\"}");
        var result=SourceCaseEvidence.evaluate(22,proof,FILE);assertEquals("chinese-language-persistence",result.key().id());assertEquals(GateStatus.BLOCKED,result.result().status());assertTrue(result.result().reason().contains("restart API"));
    }
    @Test void semanticPassCannotOverrideAnExplicitUnacceptedFunctionalOutcome()throws Exception {
        var proof=RunFiles.parse("{\"caseNumber\":14,\"semanticFunctionalStatus\":\"PASS\",\"functionalAcceptance\":false,\"reason\":\"partial semantic result\"}");assertEquals(GateStatus.BLOCKED,SourceCaseEvidence.evaluate(14,proof,FILE).result().status());proof.addProperty("functionalAcceptance",true);assertEquals(GateStatus.BLOCKED,SourceCaseEvidence.evaluate(14,proof,FILE).result().status());proof.addProperty("renderedPwIconAcceptance",true);assertEquals(GateStatus.BLOCKED,SourceCaseEvidence.evaluate(14,proof,FILE).result().status());
    }
    @Test void case19ExactInputPassRemainsBlockedUntilMandatoryFontAndPixelAcceptance()throws Exception {var proof=RunFiles.parse("{\"caseNumber\":19,\"semanticInputStatus\":\"PASS\",\"functionalAcceptance\":false,\"reason\":\"five exact actual samples retained\"}");var result=SourceCaseEvidence.evaluate(19,proof,FILE);assertEquals("navi-input-spacing",result.key().id());assertEquals(GateStatus.BLOCKED,result.result().status());assertTrue(result.result().reason().contains("subcheck PASS"));}
    @Test void bothLayoutProgramsPublishTheCorrectClosedKeysAndRemainVisuallyBlocked()throws Exception {for(int number:new int[]{20,21}){var proof=RunFiles.parse("{\"caseNumber\":"+number+",\"semanticFunctionalStatus\":\"PASS\",\"functionalAcceptance\":false,\"reason\":\"actual exported metrics retained\"}");var result=SourceCaseEvidence.evaluate(number,proof,FILE);assertEquals(number==20?"control-layout":"action-button-layout",result.key().id());assertEquals(GateStatus.BLOCKED,result.result().status());}}
    @Test void catalogSemanticPassStillRequiresStockIconAcceptance()throws Exception {
        var proof=RunFiles.parse("{\"caseNumber\":18,\"semanticFunctionalStatus\":\"PASS\",\"functionalAcceptance\":false,\"reason\":\"independent fixture and scan agree\"}");
        var result=SourceCaseEvidence.evaluate(18,proof,FILE);assertEquals("catalog-classification",result.key().id());assertEquals(GateStatus.BLOCKED,result.result().status());assertTrue(result.result().reason().contains("subcheck PASS"));
    }
    @Test void anotherCaseCannotBorrowThisNumberedVerdict()throws Exception {var proof=RunFiles.parse("{\"caseNumber\":22,\"semanticFunctionalStatus\":\"PASS\",\"functionalAcceptance\":true,\"reason\":\"actual different case\"}");assertThrows(IOException.class,()->SourceCaseEvidence.evaluate(14,proof,FILE));}
    @Test void unknownCaseOrMissingTerminalReasonCannotProduceAnAcceptanceReceipt()throws Exception {assertThrows(IOException.class,()->SourceCaseEvidence.evaluate(99,new com.google.gson.JsonObject(),FILE));assertThrows(IOException.class,()->SourceCaseEvidence.evaluate(14,RunFiles.parse("{\"caseNumber\":14,\"semanticFunctionalStatus\":\"BLOCKED\"}"),FILE));}    @Test void actualOutageProgramCannotBorrowMissingSourceBoundHostAdmission()throws Exception {
        var proof=RunFiles.parse("{\"caseNumber\":25,\"semanticFunctionalStatus\":\"PASS\",\"functionalAcceptance\":false,\"reason\":\"actual current Java outage complete; owning host attestation pending\"}");
        var result=SourceCaseEvidence.evaluate(25,proof,FILE);assertEquals("state-outage-recovery",result.key().id());assertEquals(GateStatus.BLOCKED,result.result().status());
    }

    @Test void wholeOutageCannotPassWithOnlyTheFourPwFaultPrograms()throws Exception {
        var proof=RunFiles.parse("{\"caseNumber\":25,\"semanticFunctionalStatus\":\"PASS\",\"functionalAcceptance\":true,\"reason\":\"four PW faults passed\",\"experiments\":[\"read-recovery\",\"read-cancellation\",\"write-recovery\",\"write-cancellation\"]}");
        assertEquals(GateStatus.BLOCKED,SourceCaseEvidence.evaluate(25,proof,FILE).result().status());
    }

}
