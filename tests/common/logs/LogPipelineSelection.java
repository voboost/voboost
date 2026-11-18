package ru.voboost.tests.logging;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.List;

/** Closed owning selection and terminal evidence for the product log pipeline. */
public final class LogPipelineSelection {
    public static final String PRODUCER =
            "log::retention::tests::rotated_owner_logs_feed_the_installer_archive";
    public static final String FRAMED_STORE =
            "server::tests::framed_log_request_commits_through_the_real_store_before_reply";
    public static final String CONSUMER =
            "log_core::tests::consumes_rotated_product_log_export_and_publishes_exact_archive";

    public static final List<String> COMPOSITION = List.of(
            "state_missing_false_error_and_commit_are_observed_through_production_wrappers",
            "configuration_conflict_keeps_exact_bytes_and_maps_to_would_block",
            "logging_and_app_batch_use_the_actual_owner_writer_and_batch_identity",
            "committed_state_and_log_with_lost_reply_are_not_resent",
            "lost_configuration_and_batch_ack_preserve_commit_without_reconnecting",
            "wrapper_validation_rejects_targets_and_invalid_batches_before_connecting",
            "captured_choice_without_ack_keeps_owner_wal_and_never_reconnects");

    public static Path nativeRegistry(Path productRoot) throws IOException {
        Path registry = productRoot.resolve("../voboost-agents/build/native/registry.rs").normalize();
        if (!registry.isAbsolute() || !Files.isRegularFile(registry, LinkOption.NOFOLLOW_LINKS)
                || Files.size(registry) == 0) {
            throw new IOException("BLOCKED: generated native registry required; "
                    + "run voboost-agents ./gradlew generateNativeRegistry with its existing toolchain/cache");
        }
        return registry;
    }

    public static List<String> composition(Path cargo, String name) {
        if (!COMPOSITION.contains(name)) {
            throw new IllegalArgumentException("unknown composition test: " + name);
        }
        return owner(cargo, "composition::" + name, false);
    }

    public static List<String> producer(Path cargo) {
        return owner(cargo, PRODUCER, true);
    }

    public static List<String> framedStore(Path cargo) {
        return owner(cargo, FRAMED_STORE, false);
    }

    private static List<String> owner(Path cargo, String test, boolean ignored) {
        List<String> command = new ArrayList<>(List.of(cargo.toString(), "test", "--locked",
                "--offline", "-p", "voboost-daemon", "--bin", "voboost-daemon",
                test, "--", "--exact"));
        if (ignored) {
            command.add("--ignored");
        }
        return List.copyOf(command);
    }

    public static List<String> consumer() {
        return List.of("/bin/sh", "scripts/cargo-test-with-ephemeral-role-roots.sh",
                "--locked", "--offline", "--lib", CONSUMER, "--", "--exact", "--ignored");
    }

    public static void requirePassed(String output, String test) throws IOException {
        long rows = output.lines().filter(line -> line.equals("test " + test + " ... ok")).count();
        List<String> summaries = output.lines()
                .filter(line -> line.startsWith("test result:")).toList();
        if (rows != 1 || summaries.size() != 1
                || !summaries.get(0).matches(
                        "test result: ok\\. 1 passed; 0 failed; 0 ignored; 0 measured; "
                                + "[0-9]+ filtered out;(?: finished in [^\\r\\n]+)?")) {
            throw new IOException("owning test did not execute exactly once and pass: " + test);
        }
    }

    private LogPipelineSelection() {}
}
