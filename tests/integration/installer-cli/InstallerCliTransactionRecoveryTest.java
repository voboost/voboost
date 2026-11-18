package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.AcceptancePlan;
import ru.voboost.tests.AcceptanceReport;
import ru.voboost.tests.CaseEvidence;
import ru.voboost.tests.CanTxPolicyGate;
import ru.voboost.tests.GateStatus;
import ru.voboost.tests.HostProcess;
import ru.voboost.tests.RunFiles;
import ru.voboost.tests.RustBoundary;
import ru.voboost.tests.TransactionRecoverySelection;

/** Runs the owning production transaction drivers against their filesystem and transport fixtures. */
class InstallerCliTransactionRecoveryTest {

    @Test void productionTransactionsRecoverAtTheirPersistedMutationBoundaries() throws Exception {
        Path cargo = RustBoundary.executable("cargo"), rustc = RustBoundary.executable("rustc");
        Path root = Path.of("").toRealPath();
        Path parent = Path.of(System.getProperty("testWorkDir")); Files.createDirectories(parent);
        Path work = RunFiles.directory(parent.resolve("transaction-recovery-" + UUID.randomUUID()));
        Map<String, String> environment = Map.of("RUSTC", rustc.toString(), "RUSTC_WRAPPER", "", "RUSTC_WORKSPACE_WRAPPER", "",
            "TMPDIR", work.toString(), "CARGO_TERM_COLOR", "never",
            "CARGO_TARGET_DIR", root.resolve("../.build-cache/inject-host").normalize().toString());
        List<Path> evidence = new ArrayList<>();
        evidence.add(CanTxPolicyGate.verify(root.resolve("../voboost-inject").normalize(), work));
        for (TransactionRecoverySelection.Suite suite : TransactionRecoverySelection.SUITES) {
            Path log = work.resolve(suite.binary() + ".log"); evidence.add(log);
            HostProcess.Result result = HostProcess.runAt(root.resolve("../voboost-inject").normalize(), log,
                TransactionRecoverySelection.command(cargo, suite), 600000, environment);
            assertEquals(0, result.exit(), "production recovery suite failed; " + log);
            TransactionRecoverySelection.requirePassed(result.output(), suite);
        }
        CaseEvidence.record(new AcceptancePlan.Key("I03", "product"), new AcceptanceReport.Result(GateStatus.PASS,
            "Owner CAN TX ioctl1/PID metadata policy gate passes; real install/restore/uninstall drivers retry failed mutation phases; signed generation update recovers at persisted phase and crash cuts",
            evidence));
    }
}
