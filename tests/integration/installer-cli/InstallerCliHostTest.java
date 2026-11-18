package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Host integration cases over the real standalone installer CLI binary
 * (voboost-installer-cli built from voboost-installer/src-tauri with the cli
 * feature). Only device-free contracts are asserted: argument parsing, help
 * output, host-target rejection messages, and the exact stdout/stderr/exit
 * split. No install/uninstall operation is executed on this machine and no
 * USB or VM is touched. The supported Linux device path stays unexercised
 * here and remains owned by the emulator/device layers.
 */
class InstallerCliHostTest {
    private static final long TIMEOUT_MILLIS = 30000;

    private static Path binary() {
        String value = System.getProperty("installerCli");
        assertTrue(value != null && Path.of(value).isAbsolute(),
                "BLOCKED: absolute -PinstallerCli pointing at the real built CLI is required");
        Path path = Path.of(value);
        assertTrue(Files.isExecutable(path), "BLOCKED: installerCli is not executable: " + path);
        return path;
    }

    private static CliHost.Result run(Path work, List<String> arguments) throws Exception {
        Path out = Files.createTempFile(work, "cli-", ".out");
        Path err = Files.createTempFile(work, "cli-", ".err");
        try {
            return CliHost.run(out, err,
                    List.copyOf(java.util.stream.Stream.concat(
                            java.util.stream.Stream.of(binary().toString()),
                            arguments.stream()).toList()),
                    TIMEOUT_MILLIS);
        } finally {
            Files.deleteIfExists(out);
            Files.deleteIfExists(err);
        }
    }

    private static String errorMessage(String stderr) throws IOException {
        // Errors exit through one canonical JSON log record on stderr; contract:
        // schemaVersion 1, source.kind installer, source.id cli, level error.
        assertFalse(stderr.isBlank(),
                "expected a canonical JSON error record on stderr, got empty stderr");
        String firstLine = stderr.lines().findFirst().orElse("");
        assertTrue(firstLine.startsWith("{"),
                "expected a JSON object on the first stderr line, got: " + firstLine);
        com.google.gson.JsonObject record = com.google.gson.JsonParser.parseString(firstLine).getAsJsonObject();
        assertEquals(1, record.get("schemaVersion").getAsInt(), "log schema");
        assertEquals("installer", record.getAsJsonObject("source").get("kind").getAsString(),
                "canonical record source kind: " + firstLine);
        assertEquals("cli", record.getAsJsonObject("source").get("id").getAsString(),
                "canonical record source id: " + firstLine);
        assertEquals("error", record.get("level").getAsString());
        assertTrue(record.get("sequence").getAsLong() >= 1, "sequence");
        assertTrue(record.get("timestamp").getAsString().endsWith("Z"), "timestamp Z");
        return record.get("message").getAsString();
    }

    @Test
    void helpGoesToStdoutWithExitZero(@TempDir Path work) throws Exception {
        CliHost.Result result = run(work, List.of("--help"));
        assertEquals(0, result.exit(), result.out() + result.err());
        // stderr keeps the help run free of log noise at the default threshold.
        assertEquals("", result.err().trim());
        assertTrue(result.out().startsWith("Voboost Installer"), "help banner");
        for (String flag : new String[] {
                "-i, --install", "-r, --restore", "--update", "-u, --uninstall",
                "--observe", "--status", "--mode MODE", "--logs DIRECTORY",
                "--device ID", "--force", "--verify-package", "-h, --help"}) {
            assertTrue(result.out().contains(flag), "help must document " + flag);
        }
    }

    @Test
    void missingOperationIsAnErrorWithExitOne(@TempDir Path work) throws Exception {
        CliHost.Result result = run(work, List.of());
        assertEquals(1, result.exit());
        assertEquals("", result.out(), "no protocol content on failure");
        assertEquals("A CLI operation is required; use --help", errorMessage(result.err()));
    }

    @Test
    void unknownArgumentIsRejected(@TempDir Path work) throws Exception {
        CliHost.Result result = run(work, List.of("--nonsense", "--status"));
        assertEquals(1, result.exit());
        assertEquals("unknown argument: --nonsense", errorMessage(result.err()));
    }

    @Test
    void conflictingOperationsAreRejected(@TempDir Path work) throws Exception {
        // Two operations can never share one invocation; also check short forms.
        assertEquals("conflicting CLI operations",
                errorMessage(run(work, List.of("--install", "--restore")).err()));
        assertEquals("conflicting CLI operations",
                errorMessage(run(work, List.of("-i", "-u")).err()));
        assertEquals("conflicting CLI operations",
                errorMessage(run(work, List.of("--verify-package", "--status")).err()));
    }

    @Test
    void missingOptionValuesAreRejected(@TempDir Path work) throws Exception {
        assertEquals("--device requires a value",
                errorMessage(run(work, List.of("--status", "--device")).err()));
        assertEquals("--logs requires a directory",
                errorMessage(run(work, List.of("--status", "--logs")).err()));
        assertEquals("--mode requires interface, hidden, or none",
                errorMessage(run(work, List.of("--status", "--mode")).err()));
    }

    @Test
    void serialSelectionIsValidatedBeforeAnyDeviceWork(@TempDir Path work) throws Exception {
        // The exact serial grammar (ascii alphanumeric plus . - _ :) is checked
        // before any transport: nothing connects, nothing is installed.
        assertEquals("invalid device serial",
                errorMessage(run(work, List.of("--status", "--device", "bad serial!")).err()));
        assertEquals("--device may be supplied only once",
                errorMessage(run(work, List.of("--status", "--device", "abc", "--device", "abc")).err()));
    }

    @Test
    void flagPreconditionsAreRejectedBeforeAnyDeviceWork(@TempDir Path work) throws Exception {
        assertEquals("--force requires --install without emulator install acceptance",
                errorMessage(run(work, List.of("--status", "--force")).err()));
        assertEquals("--observe requires --device",
                errorMessage(run(work, List.of("--observe")).err()));
        assertEquals("--device requires a device operation",
                errorMessage(run(work, List.of("--verify-package", "--device", "abc")).err()));
        assertEquals("--emulator-install-acceptance requires --install and an exact emulator serial",
                errorMessage(run(work,
                        List.of("--emulator-install-acceptance", "--status", "--device", "emulator-5582")).err()));
    }

    @Test
    void logsDirectoryMustBeAbsoluteWithoutControlCharacters(@TempDir Path work) throws Exception {
        assertEquals("--logs requires an absolute directory without control characters",
                errorMessage(run(work, List.of("--status", "--logs", "relative/path")).err()));
        assertEquals("--logs requires an absolute directory without control characters",
                errorMessage(run(work, List.of("--status", "--logs=" + work + "/tab\tname")).err()));
    }

    @Test
    void modeAcceptsExactlyThreeValues(@TempDir Path work) throws Exception {
        assertEquals("--mode requires exactly interface, hidden, or none",
                errorMessage(run(work, List.of("--mode", "bogus", "--status")).err()));
        // Valid values pass parsing and fail only on the unsupported host target.
        for (String mode : new String[] {"interface", "hidden", "none"}) {
            CliHost.Result result = run(work, List.of("--mode=" + mode));
            assertEquals(1, result.exit());
            assertEquals("standalone startup-mode management is supported only by the Linux release target",
                    errorMessage(result.err()));
            assertEquals("", result.out(), "mode parsing happens before any device activity");
        }
    }

    @Test
    void deviceOperationsAreRejectedOnUnsupportedHostTarget(@TempDir Path work) throws Exception {
        // This is the real safety contract for this machine: the CLI must refuse
        // every device operation rather than probing or touching anything.
        assertEquals("standalone status is supported only by the Linux release target",
                errorMessage(run(work, List.of("--status", "--device=emulator-9999")).err()));
        assertEquals("standalone observation is supported only by the Linux release target",
                errorMessage(run(work, List.of("--observe", "--device=emulator-9999")).err()));
    }
}
