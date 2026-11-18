package ru.voboost.tests.installer.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Host runner for the real packaged installer CLI: keeps stdout (protocol and
 * content) separate from stderr (canonical JSON log records and error exit),
 * which is the split the CLI contract itself promises. Extends, not replaces,
 * the merged-output HostProcess contract owned by delivery.
 */
public final class CliHost {
    private CliHost() {}

    /** exit status plus independent stdout and stderr captures. */
    public record Result(int exit, String out, String err) {}

    public static final class Timeout extends IOException {
        public Timeout(String command) { super("Timed out: " + command); }
    }

    public static Result run(Path outLog, Path errLog, List<String> command, long timeoutMillis)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectOutput(outLog.toFile())
                .redirectError(errLog.toFile())
                .start();
        try {
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor();
                throw new Timeout(command.get(0));
            }
            return new Result(
                    process.exitValue(),
                    Files.readString(outLog, StandardCharsets.UTF_8),
                    Files.readString(errLog, StandardCharsets.UTF_8));
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            process.waitFor();
            Thread.currentThread().interrupt();
            throw interrupted;
        }
    }
}
