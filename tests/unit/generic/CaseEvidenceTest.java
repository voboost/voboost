package ru.voboost.tests;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CaseEvidenceTest {
    @TempDir Path directory;
    @Test void coordinatorConsumesOnlyTheCurrentRunAndRetainedEvidence() throws Exception {
        String priorId = System.getProperty("checkRunId"), priorWork = System.getProperty("testWorkDir");
        try {
            String run = UUID.randomUUID().toString();
            System.setProperty("checkRunId", run); System.setProperty("testWorkDir", directory.toString());
            Path raw = Files.writeString(directory.resolve("observed.json"), "{}");
            CaseEvidence.record(new AcceptancePlan.Key("I01", "product"),
                new AcceptanceReport.Result(GateStatus.PASS, "Actual observation", List.of(raw)));
            var records = CandidateRoute.records(directory, run);
            assertEquals(1, records.size()); assertEquals("I01", records.get(0).get("id").getAsString());
            assertThrows(IOException.class, () -> CandidateRoute.records(directory, UUID.randomUUID().toString()));
            assertThrows(IOException.class, () -> CaseEvidence.record(new AcceptancePlan.Key("I02", "product"),
                new AcceptanceReport.Result(GateStatus.PASS, "Missing observation", List.of(directory.resolve("missing")))));
            assertEquals(1, CandidateRoute.records(directory, run).size(), "rejected evidence must not publish a receipt");
        } finally {
            restore("checkRunId", priorId); restore("testWorkDir", priorWork);
        }
    }
    @Test void unexpectedFilesCannotBeSilentlySkippedByTheVerdict() throws Exception {
        Path cases = RunFiles.directory(directory.resolve("cases")); Files.writeString(cases.resolve("unexpected"), "{}");
        assertThrows(IOException.class, () -> CandidateRoute.records(directory, UUID.randomUUID().toString()));
    }
    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key); else System.setProperty(key, value);
    }
}
