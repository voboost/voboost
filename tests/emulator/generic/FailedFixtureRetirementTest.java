package ru.voboost.tests.retirement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.ProductRun;
import ru.voboost.tests.RunFiles;
import ru.voboost.voyemu.OwnedSession;
import ru.voboost.voyemu.TestConfiguration;
import ru.voboost.voyemu.VoyemuClient;

/** Explicit registration of a real prior stop, never a VM stop/reset or acceptance executor. */
class FailedFixtureRetirementTest {
    private static Path input(String name) throws IOException {
        String value = System.getProperty(name);
        if (value == null || !Path.of(value).isAbsolute()) {
            throw new IOException("absolute " + name + " required");
        }
        return Path.of(value);
    }

    @Test
    void importsOnlyTheExistingCorrelatedStopAndRequiresANewBaseline() throws Exception {
        if (!"true".equals(System.getProperty("retireStoppedFailure"))) {
            throw new IOException("explicit retireFailedFixture entry required");
        }
        TestConfiguration config = TestConfiguration.fromSystemProperties();
        if (!"resume".equals(config.scenario())) {
            throw new IOException("retirement registration requires scenario=resume");
        }
        Path root = input("testWorkDir");
        Path work = input("resumeRun").toRealPath();
        try (ProductRun run = ProductRun.resumeForRetirement(root.resolve("machine-locks"),
                config.machine(), work)) {
            Path api = RunFiles.directory(work.resolve("retirement-api-" + UUID.randomUUID()));
            VoyemuClient client = VoyemuClient.process(config.voyemu(), api);
            OwnedSession stopped = OwnedSession.resume(client, config.profile(), config.machine(),
                    work.resolve("session"));
            Path proof = run.importExistingStop(stopped);
            if (!Files.isRegularFile(proof)) throw new IOException("retirement was not persisted");
        }
    }
}
