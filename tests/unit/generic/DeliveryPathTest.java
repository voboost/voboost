package ru.voboost.tests;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DeliveryPathTest {
    @Test void rejectsSymlinkToExecutableAndDirectory() throws Exception {
        Path parent = Path.of("build", "path-test");
        Files.createDirectories(parent);
        Path work = Files.createTempDirectory(parent, "test-");
        try {
            Path executable = Files.writeString(work.resolve("executable"), "test");
            Path directory = Files.createDirectory(work.resolve("directory"));
            Path fileLink = Files.createSymbolicLink(work.resolve("file-link"), executable.toAbsolutePath());
            Path directoryLink = Files.createSymbolicLink(work.resolve("directory-link"), directory.toAbsolutePath());
            DeliveryChecks.requireRegular(executable);
            DeliveryChecks.requireDirectory(directory);
            assertThrows(IllegalStateException.class, () -> DeliveryChecks.requireRegular(fileLink));
            assertThrows(IllegalStateException.class, () -> DeliveryChecks.requireDirectory(directoryLink));
            assertThrows(IllegalStateException.class, () -> DeliveryChecks.requireRegular(directory));
            Files.delete(fileLink);
            Files.delete(directoryLink);
            Files.delete(executable);
            Files.delete(directory);
        } finally { Files.delete(work); }
    }
}
