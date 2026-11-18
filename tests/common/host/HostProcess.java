package ru.voboost.tests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class HostProcess {
    private HostProcess() {}
    public record Result(int exit, String output) {}
    public static final class Timeout extends IOException {
        public Timeout(String command) { super("Timed out: " + command); }
    }
    public static Result run(Path log, List<String> command, long timeoutMillis) throws IOException, InterruptedException {
        return run(log, command, timeoutMillis, java.util.Map.of());
    }
    public static Result run(Path log, List<String> command, long timeoutMillis, java.util.Map<String, String> environment)
            throws IOException, InterruptedException {
        return runAt(null, log, command, timeoutMillis, environment);
    }
    public static Result runAt(Path directory, Path log, List<String> command, long timeoutMillis, java.util.Map<String, String> environment)
            throws IOException, InterruptedException {
        if (command.isEmpty() || timeoutMillis < 1 || timeoutMillis > 3600000) throw new IllegalArgumentException("bounded command duration required");
        if (directory != null && (!directory.isAbsolute() || !Files.isDirectory(directory))) throw new IOException("absolute command directory required");
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
        if (directory != null) builder.directory(directory.toFile());
        builder.environment().putAll(environment);
        Process process = builder.start();
        try {
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                terminate(process);
                throw new Timeout(command.get(0));
            }
            if (Files.size(log) > 32L * 1024 * 1024) throw new IOException("Host output exceeds read bound; retained log: " + log);
            return new Result(process.exitValue(), Files.readString(log));
        } catch (InterruptedException interrupted) {
            terminate(process);
            Thread.currentThread().interrupt();
            throw interrupted;
        }
    }
    /** Stream a bounded artifact to disk without loading it into the Java heap. */
    public static int produceFile(Path directory, Path output, Path error, List<String> command,
            long timeoutMillis, long maxBytes) throws IOException, InterruptedException {
        if (!directory.isAbsolute() || !Files.isDirectory(directory) || command.isEmpty()
                || timeoutMillis < 1 || timeoutMillis > 3600000 || maxBytes < 1 || maxBytes > 401L * 1024 * 1024
                || Files.exists(output) || Files.exists(error)) throw new IllegalArgumentException("fresh bounded process artifacts required");
        Process process = new ProcessBuilder(command).directory(directory.toFile())
            .redirectOutput(output.toFile()).redirectError(error.toFile()).start();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        try {
            while (true) {
                if (Files.size(output) > maxBytes || Files.size(error) > 1024 * 1024)
                    throw new IOException("Process artifact exceeds bound; retained at " + output);
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new Timeout(command.get(0));
                if (process.waitFor(Math.max(1, Math.min(100, TimeUnit.NANOSECONDS.toMillis(remaining))), TimeUnit.MILLISECONDS)) {
                    if (Files.size(output) > maxBytes || Files.size(error) > 1024 * 1024)
                        throw new IOException("Process artifact exceeds bound; retained at " + output);
                    return process.exitValue();
                }
            }
        } catch (IOException | InterruptedException failure) {
            terminate(process);
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw failure;
        }
    }

    private static void terminate(Process process) {
        List<ProcessHandle> descendants = process.descendants().toList();
        for (int index = descendants.size() - 1; index >= 0; --index) descendants.get(index).destroyForcibly();
        process.destroyForcibly();
    }
}
