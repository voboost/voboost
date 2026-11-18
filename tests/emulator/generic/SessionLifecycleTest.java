package ru.voboost.tests.installer.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.voboost.voyemu.EmulatorFixture;
import ru.voboost.voyemu.EmulatorPreparation;
import ru.voboost.voyemu.OwnedSession;
import ru.voboost.tests.ProductRun;
import ru.voboost.voyemu.TestConfiguration;
import ru.voboost.voyemu.VoyemuClient;

/** Real background session smoke; does not establish product acceptance. */
@Order(0)
@ExtendWith(EmulatorFixture.class)
class SessionLifecycleTest {
    @Test void ownsReadyAndroidAndStopsItsSession(EmulatorPreparation prepared) throws Exception {
        if (prepared != null) {
            prepared.session.verifyAndroidUi();
            return;
        }
        TestConfiguration config = TestConfiguration.fromSystemProperties();
        Path workRoot = Path.of(System.getProperty("testWorkDir"));
        Files.createDirectories(workRoot);
        Path work = Files.createTempDirectory(workRoot, "session-",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        VoyemuClient client = VoyemuClient.process(config.voyemu(), work);
        try (ProductRun run = ProductRun.open(workRoot.resolve("machine-locks"), config.machine(), work)) {
            OwnedSession session = run.start(client, config.profile(), "fresh".equals(config.scenario()), false);
            session.verifyAndroidUi();
        }
    }
}
