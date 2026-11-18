package ru.voboost.tests;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AcceptancePlanTest {
    @TempDir Path directory;
    private static JsonObject plan() throws IOException {
        return RunFiles.parse("{\"schemaVersion\":2,\"cases\":[{\"number\":1,\"id\":\"install\",\"required\":true,\"observable\":\"Actual installation\"}],\"integrationCases\":[],\"guiCases\":[],\"agentCases\":[{\"id\":\"launcher\",\"required\":true,\"observable\":\"Actual windows\"}]}");
    }
    private static JsonObject catalog(String id) throws IOException {
        return RunFiles.parse("{\"schemaVersion\":2,\"agents\":[{\"id\":\"" + id + "\",\"status\":\"stable\",\"targets\":[{\"platform\":\"android\",\"process\":\"system_server\"},{\"platform\":\"qnx\",\"process\":\"gauge\"}]}]}");
    }
    private void prerequisites(AcceptanceReport report, Path evidence, AcceptanceReport.Result pass) throws Exception {
        report.identify("12345678-1234-1234-1234-123456789abc", evidence, "fixture-manifest", "host fixture");
        for (AcceptanceReport.Prerequisite key : AcceptanceReport.Prerequisite.values()) report.prerequisite(key, pass);
    }
    @Test void everyCandidateTargetRequiresIndependentEvidence() throws Exception {
        AcceptancePlan plan = AcceptancePlan.parse(plan(), catalog("launcher"));
        assertEquals(3, plan.cases().size());
        AcceptanceReport report = new AcceptanceReport(plan);
        Path evidence = Files.writeString(directory.resolve("observed.json"), "{}");
        AcceptanceReport.Result pass = new AcceptanceReport.Result(GateStatus.PASS, "Observed actual effect", List.of(evidence));
        prerequisites(report, evidence, pass);
        for (AcceptancePlan.Case item : plan.cases().subList(0, 2)) report.record(item.key(), pass);
        report.cleanup(pass);
        assertEquals(GateStatus.BLOCKED, report.verdict());
        assertFalse(report.json().get("fullAcceptance").getAsBoolean());
        report.record(plan.cases().get(2).key(), pass);
        assertEquals(GateStatus.PASS, report.verdict());
        assertTrue(report.json().get("fullAcceptance").getAsBoolean());
        assertThrows(IOException.class, () -> report.record(plan.cases().get(2).key(), pass));
    }
    @Test void importedExclusionRemainsBlocked() throws Exception {
        Path proof = Files.writeString(directory.resolve("unrelated-import"), "unrelated");
        var result = CandidateRoute.importedResult(GateStatus.NOT_APPLICABLE, "Claimed absent", List.of(proof));
        assertEquals(GateStatus.BLOCKED, result.status());
    }
    @Test void arbitraryEvidenceCannotAuthorizeNonApplicability() throws Exception {
        AcceptancePlan plan = AcceptancePlan.parse(plan(), catalog("launcher"));
        AcceptanceReport report = new AcceptanceReport(plan);
        Path proof = Files.writeString(directory.resolve("unrelated-proof"), "unrelated");
        AcceptanceReport.Result absent = new AcceptanceReport.Result(GateStatus.NOT_APPLICABLE, "Claimed absent", List.of(proof));
        assertThrows(IOException.class, () -> report.record(plan.cases().get(0).key(), absent));
        assertThrows(IOException.class, () -> report.record(plan.cases().get(1).key(), absent));
        assertEquals(GateStatus.BLOCKED, report.verdict());
    }
    @Test void unresolvedAgentTargetIsExplicitlyBlocked() throws Exception {
        AcceptancePlan plan = AcceptancePlan.parse(plan(), catalog("launcher"));
        AcceptanceReport report = new AcceptanceReport(plan);
        assertEquals("BLOCKED", report.json().getAsJsonArray("cases").get(1).getAsJsonObject().get("status").getAsString());
        assertEquals("NOT_RUN", report.json().getAsJsonArray("cases").get(0).getAsJsonObject().get("status").getAsString());
    }
    @Test void newStableAgentCannotDisappearFromTheVerdict() throws Exception {
        AcceptancePlan plan = AcceptancePlan.parse(plan(), catalog("new-agent"));
        assertEquals(2, plan.cases().stream().filter(item -> !item.registered()).count());
        AcceptanceReport report = new AcceptanceReport(plan);
        Path file = Files.writeString(directory.resolve("proof"), "observed");
        assertThrows(IOException.class, () -> report.record(plan.cases().get(1).key(),
            new AcceptanceReport.Result(GateStatus.PASS, "Not implemented", List.of(file))));
        assertEquals("BLOCKED", report.json().getAsJsonArray("cases").get(1).getAsJsonObject().get("status").getAsString());
    }
    @Test void focusedSuccessIsNeverFullAcceptance() throws Exception {
        AcceptancePlan plan = AcceptancePlan.parse(plan(), catalog("launcher"));
        AcceptanceReport report = AcceptanceReport.focused(plan, AcceptancePlan.Layer.EMULATOR);
        Path file = Files.writeString(directory.resolve("proof"), "observed");
        AcceptanceReport.Result pass = new AcceptanceReport.Result(GateStatus.PASS, "Observed", List.of(file));
        prerequisites(report, file, pass);
        for (AcceptancePlan.Case item : plan.cases()) report.record(item.key(), pass);
        report.cleanup(pass);
        assertEquals(GateStatus.PASS, report.verdict());
        assertFalse(report.json().get("fullAcceptance").getAsBoolean());
    }
    @Test void duplicatedOrMissingTargetIsNotSilentlyCollapsed() throws Exception {
        JsonObject catalog = catalog("launcher");
        var targets = catalog.getAsJsonArray("agents").get(0).getAsJsonObject().getAsJsonArray("targets");
        targets.add(targets.get(0).deepCopy());
        assertThrows(IOException.class, () -> AcceptancePlan.parse(plan(), catalog));
        catalog.getAsJsonArray("agents").get(0).getAsJsonObject().add("targets", new com.google.gson.JsonArray());
        assertThrows(IOException.class, () -> AcceptancePlan.parse(plan(), catalog));
    }
    @Test void missingCurrentPrerequisitesPreventFullSuccess() throws Exception {
        AcceptancePlan plan = AcceptancePlan.parse(plan(), catalog("launcher"));
        AcceptanceReport report = new AcceptanceReport(plan);
        Path proof = Files.writeString(directory.resolve("proof"), "observed");
        AcceptanceReport.Result pass = new AcceptanceReport.Result(GateStatus.PASS, "Observed", List.of(proof));
        for (AcceptancePlan.Case item : plan.cases()) report.record(item.key(), pass);
        report.cleanup(pass);
        assertEquals(GateStatus.BLOCKED, report.verdict());
        prerequisites(report, proof, pass);
        assertEquals(GateStatus.PASS, report.verdict());
    }
    @Test void proofAndCleanupAreRequiredAndFailureCannotBeOverwritten() throws Exception {
        AcceptancePlan plan = AcceptancePlan.parse(plan(), catalog("launcher"));
        AcceptanceReport report = new AcceptanceReport(plan);
        assertThrows(IllegalArgumentException.class, () -> new AcceptanceReport.Result(GateStatus.PASS, "No proof", List.of()));
        AcceptanceReport.Result fail = new AcceptanceReport.Result(GateStatus.FAIL, "Actual invariant failed", List.of());
        report.record(plan.cases().get(0).key(), fail);
        assertThrows(IOException.class, () -> report.record(plan.cases().get(0).key(), fail));
        assertEquals(GateStatus.FAIL, report.verdict());
    }    @Test void nativeGuiSubsetCannotEstablishFullAcceptance() throws Exception {
        JsonObject input = plan(); var gui = input.getAsJsonArray("guiCases");
        for (int index = 1; index <= 11; index++) {
            JsonObject item = new JsonObject(); item.addProperty("id", String.format(java.util.Locale.ROOT, "G%02d", index));
            item.addProperty("required", true); item.addProperty("observable", "Actual native GUI case"); gui.add(item);
        }
        AcceptancePlan all = AcceptancePlan.parse(input, catalog("launcher"));
        AcceptanceReport report = new AcceptanceReport(all);
        Path proof = Files.writeString(directory.resolve("host-admission-fixture.json"), "{}");
        AcceptanceReport.Result fixture = new AcceptanceReport.Result(GateStatus.PASS, "Controlled host admission fixture; no GUI execution", List.of(proof));
        prerequisites(report, proof, fixture); report.cleanup(fixture);
        for (AcceptancePlan.Case item : all.cases()) if (!item.key().id().matches("G[0-9]{2}")) report.record(item.key(), fixture);
        assertEquals(GateStatus.BLOCKED, report.verdict());
        for (String id : List.of("G01", "G02", "G06")) report.record(new AcceptancePlan.Key(id, "product"), fixture);
        assertEquals(GateStatus.BLOCKED, report.verdict()); assertFalse(report.json().get("fullAcceptance").getAsBoolean());
        assertEquals(8, java.util.stream.StreamSupport.stream(report.json().getAsJsonArray("cases").spliterator(), false)
            .filter(row -> row.getAsJsonObject().get("id").getAsString().matches("G[0-9]{2}")
                && !"PASS".equals(row.getAsJsonObject().get("status").getAsString())).count());
    }

    @Test void removedRetainedProofPreventsFinalAcceptance() throws Exception {
        for (String missing : List.of("candidate", "prerequisite", "case", "cleanup")) {
            AcceptancePlan specification = AcceptancePlan.parse(plan(), catalog("launcher"));
            AcceptanceReport report = new AcceptanceReport(specification);
            Path candidate = Files.writeString(directory.resolve(missing + "-candidate"), "candidate");
            Path prerequisite = Files.writeString(directory.resolve(missing + "-prerequisite"), "proof");
            Path observed = Files.writeString(directory.resolve(missing + "-case"), "proof");
            Path cleanup = Files.writeString(directory.resolve(missing + "-cleanup"), "proof");
            report.identify("12345678-1234-1234-1234-123456789abc", candidate, "fixture", "host");
            for (AcceptanceReport.Prerequisite key : AcceptanceReport.Prerequisite.values()) {
                report.prerequisite(key, new AcceptanceReport.Result(GateStatus.PASS, "Observed", List.of(prerequisite)));
            }
            for (AcceptancePlan.Case item : specification.cases()) {
                report.record(item.key(), new AcceptanceReport.Result(GateStatus.PASS, "Observed", List.of(observed)));
            }
            report.cleanup(new AcceptanceReport.Result(GateStatus.PASS, "Observed", List.of(cleanup)));
            assertEquals(GateStatus.PASS, report.verdict());
            Path removed = switch (missing) {
                case "candidate" -> candidate;
                case "prerequisite" -> prerequisite;
                case "case" -> observed;
                default -> cleanup;
            };
            Files.delete(removed);
            assertEquals(GateStatus.BLOCKED, report.verdict(), missing);
            assertFalse(report.json().get("fullAcceptance").getAsBoolean(), missing);
        }
    }

}
