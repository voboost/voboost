package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.HostProcess;
import ru.voboost.tests.RunFiles;

/** Existing out-of-process dispatcher fixtures retain their real timeout and cleanup behavior. */
class InstallerCliDispatcherContractTest {
    @Test void dispatcherProcessContractsRemainCoveredAtTheIntegrationLayer() throws Exception {
        Path root = Path.of("").toRealPath();
        Path reports = Path.of(System.getProperty("testWorkDir")); Files.createDirectories(reports);
        Path work = RunFiles.directory(reports.resolve("dispatcher-" + UUID.randomUUID()));
        Path log = work.resolve("node.log");
        HostProcess.Result result = HostProcess.runAt(root, log,
            List.of("node", "--test", "--test-reporter=tap", "tools/tests/emulator-gate.test.ts"), 1800000,
            Map.of("TMPDIR", work.toString()));
        assertEquals(0, result.exit(), "dispatcher contract failure; " + log);
        assertTrue(result.output().lines().anyMatch(line -> line.matches("# tests [1-9][0-9]*")), "empty dispatcher suite");
        assertTrue(result.output().lines().anyMatch(line -> line.equals("# fail 0")));
        assertTrue(result.output().lines().anyMatch(line -> line.equals("# skipped 0")));
    }
}
