package ru.voboost.tests.logging;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.AcceptancePlan;
import ru.voboost.tests.AcceptanceReport;
import ru.voboost.tests.CaseEvidence;
import ru.voboost.tests.GateStatus;
import ru.voboost.tests.HostProcess;
import ru.voboost.tests.RunFiles;
import ru.voboost.tests.RustBoundary;

/** Current owner writer/export feeds the real desktop parser and archive; no peer-auth claim. */
class DaemonLogPipelineTest {
    @Test
    void rotatedLogsSurviveTheActualCrossComponentExportAndArchive() throws Exception {
        Path cargo = RustBoundary.executable("cargo");
        Path rustc = RustBoundary.executable("rustc");
        Path root = Path.of("").toRealPath();
        Path registry = LogPipelineSelection.nativeRegistry(root);
        Path parent = Path.of(System.getProperty("testWorkDir"));
        Files.createDirectories(parent);
        Path work = RunFiles.directory(parent.resolve("log-pipeline-" + UUID.randomUUID()));
        Path stream = work.resolve("daemon-export.bin");
        Map<String, String> environment = new HashMap<>(Map.of(
                "RUSTC", rustc.toString(), "RUSTC_WRAPPER", "", "RUSTC_WORKSPACE_WRAPPER", "",
                "TMPDIR", work.toString(), "CARGO_TERM_COLOR", "never",
                "VOBOOST_TEST_LOG_EXPORT", stream.toString(),
                "VOBOOST_NATIVE_REGISTRY", registry.toString()));
        environment.put("CARGO_TARGET_DIR",
                root.resolve("../.build-cache/inject-host").normalize().toString());
        Path inject = root.resolve("../voboost-inject").normalize();

        List<Path> compositionLogs = new ArrayList<>();
        for (String name : LogPipelineSelection.COMPOSITION) {
            Path log = work.resolve("composition-" + name + ".log");
            var composed = HostProcess.runAt(inject, log,
                    LogPipelineSelection.composition(cargo, name), 300000, environment);
            assertEquals(0, composed.exit(), "owner wrapper composition failed; " + log);
            LogPipelineSelection.requirePassed(composed.output(), "composition::" + name);
            compositionLogs.add(log);
        }

        Path storeLog = work.resolve("store.log");

        var stored = HostProcess.runAt(inject, storeLog,
                LogPipelineSelection.framedStore(cargo), 300000, environment);
        assertEquals(0, stored.exit(), "owner framed Store failed; " + storeLog);
        LogPipelineSelection.requirePassed(stored.output(), LogPipelineSelection.FRAMED_STORE);

        Path producerLog = work.resolve("producer.log");
        var produced = HostProcess.runAt(inject, producerLog,
                LogPipelineSelection.producer(cargo), 300000, environment);
        assertEquals(0, produced.exit(), "current owner producer failed; " + producerLog);
        LogPipelineSelection.requirePassed(produced.output(), LogPipelineSelection.PRODUCER);
        assertTrue(Files.isRegularFile(stream, LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.size(stream) > 55);

        Path installer = root.resolve("../voboost-installer").normalize();
        environment.put("CARGO_TARGET_DIR",
                root.resolve("../.build-cache/installer-cargo").normalize().toString());
        environment.put("PATH",
                cargo.getParent() + System.getProperty("path.separator") + System.getenv("PATH"));
        String sdk = System.getenv("ANDROID_SDK_ROOT") == null
                ? System.getenv("ANDROID_HOME") : System.getenv("ANDROID_SDK_ROOT");
        if (sdk != null) {
            environment.put("ANDROID_SDK_ROOT", sdk);
        }
        Path consumerLog = work.resolve("consumer.log");
        var consumed = HostProcess.runAt(installer, consumerLog,
                LogPipelineSelection.consumer(), 300000, environment);
        assertEquals(0, consumed.exit(), "desktop consumer failed; " + consumerLog);
        LogPipelineSelection.requirePassed(consumed.output(), LogPipelineSelection.CONSUMER);
        try (var files = Files.list(work.resolve("archives"))) {
            List<Path> archives = files.toList();
            assertEquals(1, archives.size());
            assertTrue(archives.get(0).getFileName().toString().endsWith(".tar.xz"));
            List<Path> evidence = new ArrayList<>(compositionLogs);
            evidence.addAll(List.of(storeLog, producerLog, consumerLog, stream, archives.get(0)));
            CaseEvidence.record(new AcceptancePlan.Key("I05", "product"),
                    new AcceptanceReport.Result(GateStatus.PASS,
                            "Current owner writer rotates above 8 MiB; bounded "
                                    + "8 MiB host retention, "
                                    + "real owner export, desktop parser and archive preserve "
                                    + "exact "
                                    + "schema2 records/context. Framed Store commits before reply. "
                                    + "Production wrappers use real framed Store/storage with "
                                    + "host-only connection/peer admission substitution. "
                                    + "Peer authentication and "
                                    + "physical "
                                    + "128 MiB stress are not verified by this host gate.",
                            evidence));
        }
    }
}
