package ru.voboost.tests;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class HostProcessTest {
    @Test void rejectsFakeVerifierAndStopsTimeout() throws Exception {
        Path directory = Path.of("build", "process-test");
        Files.createDirectories(directory);
        Path log = Files.createTempFile(directory, "process-", ".log");
        try {
            var crash = HostProcess.run(log, List.of("/bin/sh", "-c", "exit 139"), 5000);
            assertThrows(IllegalStateException.class, () -> DeliveryChecks.requireInventoryRejection(crash.exit(), crash.output()));
            var fake = HostProcess.run(log, List.of("/bin/sh", "-c", "echo 'error: other' >&2; exit 1"), 5000);
            assertThrows(IllegalStateException.class, () -> DeliveryChecks.requireInventoryRejection(fake.exit(), fake.output()));
            assertThrows(HostProcess.Timeout.class, () -> HostProcess.run(log, List.of("/bin/sleep", "5"), 50));
        } finally { Files.deleteIfExists(log); }
    }
}
