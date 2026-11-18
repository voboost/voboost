package ru.voboost.tests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Owner CAN TX ioctl1/PID metadata policy proof; not full native readiness. */
public final class CanTxPolicyGate {
    private CanTxPolicyGate() {}

    @FunctionalInterface
    interface ProcessReceipt {
        HostProcess.Result run(Path component, Path log, Map<String, String> environment)
                throws IOException, InterruptedException;
    }

    public static List<String> command() {
        return List.of("/usr/bin/make", "--no-print-directory", "--jobs=1",
                "test-selinux-rule-generation", "test-selinux-real-policy");
    }

    public static Path verify(Path component, Path work) throws IOException, InterruptedException {
        return verify(component, work, (directory, log, environment) ->
                HostProcess.runAt(directory, log, command(), 600000, environment));
    }

    static Path verify(Path component, Path work, ProcessReceipt process)
            throws IOException, InterruptedException {
        if (!component.isAbsolute() || !Files.isDirectory(component, LinkOption.NOFOLLOW_LINKS)
                || !work.isAbsolute()) {
            throw new IOException("BLOCKED: absolute owner source and policy work directories required");
        }
        Files.createDirectories(work);
        Path log = work.resolve("can-tx-pid-policy.log");
        if (Files.exists(log, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("fresh CAN TX/PID policy log required");
        }
        HostProcess.Result result = process.run(component, log,
                Map.of("TMPDIR", work.toString(), "MAKEFLAGS", ""));
        requirePassed(result);
        if (!Files.isRegularFile(log, LinkOption.NOFOLLOW_LINKS) || Files.size(log) == 0) {
            throw new IOException("retained CAN TX/PID policy log required");
        }
        return log;
    }

    static void requirePassed(HostProcess.Result result) throws IOException {
        String output = result.output();
        if (result.exit() != 0 || output.lines().anyMatch(line -> line.startsWith("FAIL:"))
                || count(output, "test_real_policy: OK") != 2
                || count(output, "policy rule generation: deterministic and current") != 1
                || output.lines().filter(line -> line.startsWith("roundtrip ok:")).count() != 2
                || output.lines().filter(line -> line.startsWith("inject ok:")).count() != 2
                || count(output, "metadata baseline ok: runtime and init all domain dir/file/process AV except getattr") != 1) {
            throw new IOException("CAN TX/PID metadata owner policy gate failed or was incomplete");
        }
    }

    private static long count(String output, String expected) {
        return output.lines().filter(expected::equals).count();
    }
}
