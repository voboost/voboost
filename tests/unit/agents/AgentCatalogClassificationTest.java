package ru.voboost.tests.agents;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import ru.voboost.tests.installer.cli.FixtureClassification;
import ru.voboost.tests.logging.InstallerLogs;

class AgentCatalogClassificationTest {

    private static final String BOOT = "abcdefab-cdef-abcd-efab-cdefabcdefab",
            FIXTURE = "ru.voboost.tests.launcherfixture";

    private static final LauncherPage.Identity OWNER = new LauncherPage.Identity(BOOT, "1", "2",
            "3", "4", "5", "g", "c", "d");

    private static final FixtureClassification PLATFORM = new FixtureClassification(BOOT, true,
            10123, 0L, false, false);

    private static JsonObject fields(String name, int uid, int flags, Boolean application) {
        JsonObject value = new JsonObject();
        value.addProperty("package_name", name);
        value.addProperty("uid", uid);
        value.addProperty("flags", flags);
        if (application != null)
            value.addProperty("application_uid", application);
        return value;
    }

    private static InstallerLogs.Diagnostic row(int seq, String event, String outcome,
            JsonObject fields) {
        JsonObject source = new JsonObject();
        source.addProperty("process", LauncherPage.OWNER);
        source.addProperty("bootId", BOOT);
        source.addProperty("targetPid", "3");
        source.addProperty("targetStarttime", "4");
        source.addProperty("endpointEpoch", "5");
        return new InstallerLogs.Diagnostic(BigInteger.valueOf(seq), "launcher-apps", event,
                outcome, fields, source);
    }

    private static List<InstallerLogs.Diagnostic> rows() {
        JsonObject start = new JsonObject();
        start.addProperty("generation", 12);
        JsonObject end = start.deepCopy();
        end.addProperty("candidates", 1);
        end.addProperty("applications", 1);
        return new ArrayList<>(List.of(row(1, "catalog.scan", "started", start), row(2,
                "catalog.candidate", "identity-observed", fields("system.app", 1000, 1, false)),
                row(3, "catalog.candidate", "skipped-non-application-uid", fields("system.app",
                1000, 1, null)), row(4, "catalog.candidate", "identity-observed", fields(FIXTURE,
                10123, 0, true)), row(5, "catalog.candidate", "qualified", fields(FIXTURE, 10123, 0,
                null)), row(6, "catalog.scan", "complete", end)));
    }

    @Test

    void freshCompleteNonvacuousScanMatchesIndependentPlatform() throws Exception {
        var proof = CatalogClassification.read(rows(), OWNER, PLATFORM);
        assertEquals(List.of("system.app"), proof.excluded());
        assertEquals(List.of(FIXTURE), proof.qualified());
    }

    @Test

    void missingBoundariesDecisionsOrNonvacuousExclusionFail() {
        for (int removed : new int[] {
            0, 1, 2, 3, 4, 5
        }) {
            var rows = rows();
            rows.remove(removed);
            assertThrows(IOException.class, () -> CatalogClassification.read(rows, OWNER,
                    PLATFORM));
        }
    }

    @Test

    void staleSourceChangedUidFlagsAndWrongOutcomeFail() {
        var stale = rows();
        stale.get(2).sourceFields().addProperty("targetStarttime", "9");
        assertThrows(IOException.class, () -> CatalogClassification.read(stale, OWNER, PLATFORM));
        var uid = rows();
        uid.get(3).fields().addProperty("uid", 10124);
        assertThrows(IOException.class, () -> CatalogClassification.read(uid, OWNER, PLATFORM));
        var flags = rows();
        flags.get(3).fields().addProperty("flags", 128);
        assertThrows(IOException.class, () -> CatalogClassification.read(flags, OWNER, PLATFORM));
        var wrong = rows();
        wrong.set(2, row(3, "catalog.candidate", "qualified", fields("system.app", 1000, 1, null)));
        assertThrows(IOException.class, () -> CatalogClassification.read(wrong, OWNER, PLATFORM));
    }

    @Test

    void scannerCannotSubstituteAnotherOtherwiseValidUserIdentityForIndependentPlatform() {
        var rows = rows();
        rows.get(3).fields().addProperty("uid", 10124);
        rows.get(4).fields().addProperty("uid", 10124);
        assertThrows(IOException.class, () -> CatalogClassification.read(rows, OWNER, PLATFORM));
    }

    @Test

    void numericStringsChangedGenerationAndCandidateCountFail() {
        var string = rows();
        string.get(3).fields().addProperty("uid", "10123");
        assertThrows(IOException.class, () -> CatalogClassification.read(string, OWNER, PLATFORM));
        var generation = rows();
        generation.get(5).fields().addProperty("generation", 13);
        assertThrows(IOException.class, () -> CatalogClassification.read(generation, OWNER,
                PLATFORM));
        var count = rows();
        count.get(5).fields().addProperty("candidates", 2);
        assertThrows(IOException.class, () -> CatalogClassification.read(count, OWNER, PLATFORM));
    }

    @Test

    void updatedSystemApplicationCannotBeQualified() {
        var rows = rows();
        rows.get(1).fields().addProperty("uid", 10100);
        rows.get(1).fields().addProperty("flags", 128);
        rows.get(1).fields().addProperty("application_uid", true);
        rows.set(2, row(3, "catalog.candidate", "skipped-system-application", fields("system.app",
                10100, 128, null)));
        assertDoesNotThrow(() -> CatalogClassification.read(rows, OWNER, PLATFORM));
        rows.set(2, row(3, "catalog.candidate", "qualified", fields("system.app", 10100, 128,
                null)));
        assertThrows(IOException.class, () -> CatalogClassification.read(rows, OWNER, PLATFORM));
    }
}
