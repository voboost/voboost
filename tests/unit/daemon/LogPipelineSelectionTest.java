package ru.voboost.tests.logging;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class LogPipelineSelectionTest {
    @TempDir Path directory;
    private static final String OWNER =
            "log::retention::tests::rotated_owner_logs_feed_the_installer_archive";

    @Test
    void producerUsesTheResidentOwnerBinary() {
        var command = LogPipelineSelection.producer(Path.of("/trusted/cargo"));
        assertEquals("voboost-daemon", command.get(command.indexOf("--bin") + 1));
    }

    @Test
    void producerUsesTheActualIgnoredOwnerExportTest() {
        var command = LogPipelineSelection.producer(Path.of("/trusted/cargo"));
        assertTrue(command.contains(OWNER));
        assertTrue(command.contains("--exact"));
        assertTrue(command.contains("--ignored"));
    }

    @Test
    void anEmptyFilterOrIgnoredTestIsNotPass() {
        assertThrows(IOException.class, () -> LogPipelineSelection.requirePassed(
                "test result: ok. 0 passed; 0 failed; 0 ignored; 0 measured; 7 filtered out;\n",
                OWNER));
        assertThrows(IOException.class, () -> LogPipelineSelection.requirePassed(
                "test " + OWNER + " ... ignored\n"
                        + "test result: ok. 0 passed; 0 failed; 1 ignored; "
                        + "0 measured; 7 filtered out;\n",
                OWNER));
    }

    @Test
    void anotherBinaryTestCannotBorrowTheTerminalCount() {
        assertThrows(IOException.class, () -> LogPipelineSelection.requirePassed(
                "test canonical_log::tests::"
                        + "debug_rotation_cannot_evict_early_info_or_error ... ok\n"
                        + "test result: ok. 1 passed; 0 failed; 0 ignored; "
                        + "0 measured; 7 filtered out;\n",
                OWNER));
    }

    @Test
    void oneExactTerminalPassIsAcceptedAndAdditionalSummariesAreRejected() throws Exception {
        String output = "test " + OWNER + " ... ok\n"
                + "test result: ok. 1 passed; 0 failed; 0 ignored; 0 measured; 7 filtered out; "
                + "finished in 3.82s\n";
        LogPipelineSelection.requirePassed(output, OWNER);
        assertThrows(IOException.class, () -> LogPipelineSelection.requirePassed(
                output + "test result: ok. 0 passed; 0 failed; 0 ignored; "
                        + "0 measured; 0 filtered out;\n",
                OWNER));
    }

    @Test
    void consumerSelectsTheExactIgnoredArchiveTest() {
        var command = LogPipelineSelection.consumer();
        assertTrue(command.contains(LogPipelineSelection.CONSUMER));
        assertTrue(command.contains("--lib"));
        assertTrue(command.contains("--exact"));
        assertTrue(command.contains("--ignored"));
    }

    @Test
    void framedStoreUsesTheSameOwnerWithoutAnIgnoredFilter() {
        var command = LogPipelineSelection.framedStore(Path.of("/trusted/cargo"));
        assertEquals("voboost-daemon", command.get(command.indexOf("--bin") + 1));
        assertTrue(command.contains(LogPipelineSelection.FRAMED_STORE));
        assertTrue(command.contains("--exact"));
        assertFalse(command.contains("--ignored"));
    }
    @Test
    void compositionSelectsEachExactProductionWrapperContract() {
        assertEquals(7, LogPipelineSelection.COMPOSITION.size());
        for (String name : LogPipelineSelection.COMPOSITION) {
            var command = LogPipelineSelection.composition(Path.of("/trusted/cargo"), name);
            assertEquals("voboost-daemon", command.get(command.indexOf("--bin") + 1));
            assertTrue(command.contains("composition::" + name));
            assertTrue(command.contains("--exact"));
            assertFalse(command.contains("--ignored"));
        }
        assertThrows(IllegalArgumentException.class, () -> LogPipelineSelection.composition(
                Path.of("/trusted/cargo"), "canonical_log::tests::legacy"));
    }
    @Test
    void capturedChoiceWithoutAcknowledgementIsRequiredByTheOwnerComposition() {
        String name = "captured_choice_without_ack_keeps_owner_wal_and_never_reconnects";
        assertTrue(LogPipelineSelection.COMPOSITION.contains(name));
        var command = LogPipelineSelection.composition(Path.of("/trusted/cargo"), name);
        assertEquals("voboost-daemon", command.get(command.indexOf("--bin") + 1));
        assertTrue(command.contains("composition::" + name));
        assertTrue(command.contains("--exact"));
        assertFalse(command.contains("--ignored"));
    }

    @Test
    void nativeRegistryRequiresTheActualNonemptyGeneratedFile() throws Exception {
        Path product = Files.createDirectory(directory.resolve("voboost"));
        Path registry = directory.resolve("voboost-agents/build/native/registry.rs");
        IOException missing = assertThrows(IOException.class, () -> LogPipelineSelection.nativeRegistry(product));
        assertTrue(missing.getMessage().contains("BLOCKED"));
        assertTrue(missing.getMessage().contains("generateNativeRegistry"));
        Files.createDirectories(registry.getParent());
        Files.createFile(registry);
        assertThrows(IOException.class, () -> LogPipelineSelection.nativeRegistry(product));
        Files.writeString(registry, "// Generated registry fixture\n");
        assertEquals(registry, LogPipelineSelection.nativeRegistry(product));
        Files.delete(registry);
        Files.createSymbolicLink(registry, Files.writeString(directory.resolve("other.rs"), "other"));
        assertThrows(IOException.class, () -> LogPipelineSelection.nativeRegistry(product));
    }

}
