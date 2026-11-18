package ru.voboost.tests.ui;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.HostProcess;
import ru.voboost.tests.AcceptancePlan;
import ru.voboost.tests.AcceptanceReport;
import ru.voboost.tests.CaseEvidence;
import ru.voboost.tests.GateStatus;
import ru.voboost.tests.RunFiles;

/** Actual React screens with the owning frontend harness; not a native-window or guest acceptance. */
class FrontendCompositionTest {
    private static final List<String> SCREENS = List.of("connection-install-screens", "log-controls", "log-viewer",
        "management-actions", "operation-screens", "startup-routing", "uninstall-content");

    @Test void composedScreensRetainTheirBehaviorThroughTheRealFrontend() throws Exception {
        Path repository = Path.of("../voboost-installer").toRealPath();
        Path root = Path.of(System.getProperty("testWorkDir")); Files.createDirectories(root);
        Path work = RunFiles.directory(root.resolve("frontend-composition-" + UUID.randomUUID()));
        Path report = work.resolve("vitest.json");
        List<String> command = new ArrayList<>(List.of("node", repository.resolve("node_modules/vitest/vitest.mjs").toString(),
            "--config", "config/vite.config.ts", "--run"));
        for (String screen : SCREENS) command.add("tests/" + screen + ".test.tsx");
        command.addAll(List.of("--reporter=json", "--outputFile=" + report));
        HostProcess.Result result = HostProcess.runAt(repository, work.resolve("frontend.log"), command, 300000,
            Map.of("TMPDIR", work.toString(), "CI", "true", "NO_COLOR", "1"));
        assertEquals(0, result.exit(), "frontend composition failed; evidence: " + work);
        JsonObject observed = RunFiles.read(report);
        assertTrue(observed.get("success").getAsBoolean(), "frontend runner did not report success");
        assertEquals(0, observed.get("numFailedTests").getAsInt());
        assertEquals(0, observed.get("numPendingTests").getAsInt(), "required composition cases must not be skipped");
        for (String screen : SCREENS) {
            String suffix = "/tests/" + screen + ".test.tsx"; int matching = 0;
            for (JsonElement value : observed.getAsJsonArray("testResults")) {
                JsonObject suite = value.getAsJsonObject();
                if (!suite.get("name").getAsString().replace('\\', '/').endsWith(suffix)) continue;
                ++matching;
                assertEquals("passed", suite.get("status").getAsString(), screen);
                assertFalse(suite.getAsJsonArray("assertionResults").isEmpty(), "required suite is empty: " + screen);
                for (JsonElement assertion : suite.getAsJsonArray("assertionResults"))
                    assertEquals("passed", assertion.getAsJsonObject().get("status").getAsString(), screen);
            }
            assertEquals(1, matching, "required composition suite must execute exactly once: " + screen);
        }
        Path components = Path.of("../voboost-components").toRealPath();
        Path componentLog = work.resolve("components.log");
        var requiredVisualSuites = VisualReport.sourceSuites(components.resolve("src/main"));
        long visualStarted = System.currentTimeMillis();
        HostProcess.Result rendered = HostProcess.runAt(components, componentLog, List.of(
            components.resolve("gradlew").toString(), "testVisual", "--rerun-tasks", "--offline", "--console=plain",
            "--no-configuration-cache", "--init-script", Path.of("tests/integration/generic/verify-components.gradle").toAbsolutePath().toString()),
            600000, Map.of("TMPDIR", work.toString(), "CI", "true"));
        List<Path> evidence = new ArrayList<>(List.of(report, work.resolve("frontend.log"), componentLog));
        Path retained = RunFiles.directory(work.resolve("component-results"));
        VisualReport.Result visual = VisualReport.retain(components.resolve("build/test-results/testVisual"),
            components.resolve("build/outputs/roborazzi"), retained, requiredVisualSuites, visualStarted, rendered.exit());
        evidence.addAll(visual.evidence());
        assertTrue(visual.passed(), "OEM component visual verification incomplete: " + visual.problems() + "; " + visual.report());
        assertEquals(0, rendered.exit(), "OEM component visual verification failed; " + componentLog);
        CaseEvidence.record(new AcceptancePlan.Key("I04", "product"), new AcceptanceReport.Result(GateStatus.PASS,
            "Actual installer screens retain behavior; OEM component compositions match existing visual baselines without recording new ones", evidence));
    }
}
