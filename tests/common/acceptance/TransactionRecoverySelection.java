package ru.voboost.tests;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Closed owning selection and terminal evidence for transaction recovery. */
public final class TransactionRecoverySelection {
    public record Suite(String packageName, String binary, String filter, List<String> cases) {
        private Suite(String binary, String filter, List<String> cases) {
            this(binary, binary, filter, cases);
        }
    }
    public static final List<Suite> SUITES = List.of(
        new Suite("voboost-install", "tests::production_install_driver_", List.of(
            "tests::production_install_driver_crosses_two_boots_and_executes_every_phase_once",
            "tests::production_install_driver_restarts_at_every_failed_mutation_phase")),
        new Suite("voboost-restore", "tests::production_restore_driver_", List.of(
            "tests::production_restore_driver_crosses_two_boots_and_completes_exact_order",
            "tests::production_restore_driver_retries_every_failed_mutating_phase")),
        new Suite("voboost-uninstall", "transaction::tests::production_uninstall_driver_", List.of(
            "transaction::tests::production_uninstall_driver_executes_every_mutation_before_terminal_handoff",
            "transaction::tests::production_uninstall_driver_restarts_every_failed_mutating_phase")),
        new Suite("voboost-daemon", "voboost-inject", "product_candidate::tests::generation_only_filesystem_restarts_converge_at_every_durable_phase", List.of(
            "product_candidate::tests::generation_only_filesystem_restarts_converge_at_every_durable_phase")));

    public static List<String> command(Path cargo, Suite suite) {
        if (!SUITES.contains(suite)) {
            throw new IllegalArgumentException("unknown transaction recovery suite");
        }
        return List.of(cargo.toString(), "test", "--locked", "--offline", "-p", suite.packageName(),
                "--bin", suite.binary(), suite.filter(), "--", "--test-threads=1");
    }

    public static void requirePassed(String output, Suite suite) throws IOException {
        if (!SUITES.contains(suite)) {
            throw new IllegalArgumentException("unknown transaction recovery suite");
        }
        Set<String> completed = new HashSet<>();
        String pending = null;
        String summary = null;
        for (String line : output.lines().toList()) {
            if (pending != null) {
                if (line.equals("ok")) {
                    completed.add(pending);
                    pending = null;
                } else {
                    RunFiles.parse(line);
                }
                continue;
            }
            if (line.startsWith("test result:")) {
                if (summary != null || completed.size() != suite.cases().size()) {
                    throw new IOException("missing or duplicate recovery terminal result");
                }
                summary = line;
            } else if (line.startsWith("test ")) {
                String name = null;
                String outcome = null;
                for (String required : suite.cases()) {
                    String prefix = "test " + required + " ... ";
                    if (line.startsWith(prefix)) {
                        name = required;
                        outcome = line.substring(prefix.length());
                        break;
                    }
                }
                if (name == null || summary != null || completed.contains(name)) {
                    throw new IOException("unknown or duplicate recovery case");
                }
                if (outcome.equals("ok")) {
                    completed.add(name);
                } else {
                    if (!outcome.isEmpty()) {
                        RunFiles.parse(outcome);
                    }
                    pending = name;
                }
            } else if (line.equals("ok") || line.equals("FAILED") || line.equals("ignored")) {
                throw new IOException("recovery terminal result has no pending case");
            }
        }
        if (pending != null || completed.size() != suite.cases().size() || summary == null
                || !summary.matches("test result: ok\\. " + suite.cases().size()
                        + " passed; 0 failed; 0 ignored; 0 measured; [0-9]+ filtered out;"
                        + "(?: finished in [^\\r\\n]+)?")) {
            throw new IOException("owning transaction recovery cases did not all execute once and pass: "
                    + suite.binary());
        }
    }

    private TransactionRecoverySelection() {}
}
