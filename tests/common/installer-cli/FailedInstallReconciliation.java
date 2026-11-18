package ru.voboost.tests.installer.cli;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Set;

/** Resolves only a completed native mutation whose desktop acceptance explicitly failed. */
public final class FailedInstallReconciliation {
    private static final String FAILURE =
            "Install failed: installed content identity is unavailable";
    private static final String READINESS =
            "Install failed: device operation completed, but readiness was not confirmed: ";
    private static boolean completedFailure(String message) {
        return message != null && (message.startsWith(FAILURE)
                || message.startsWith(READINESS) && message.length() > READINESS.length());
    }
    private static final String INTERRUPTED = "Installer acceptance interrupted after completed native installation";
    private static final Set<String> FIELDS = Set.of("schemaVersion", "resolution", "acceptance",
            "functionalAcceptance", "manifest", "device", "priorBoot", "bootId",
            "nativePhase", "nativeOutcome", "diagnostic");

    public static JsonObject prove(JsonObject input, JsonObject nativeReport, String boot,
            boolean ready, boolean enforcing, String stdout, String stderr, JsonObject runner)
            throws Exception {
        String manifest = text(input, "manifest");
        String prior = text(input, "priorBoot");
        String device = text(input, "device");
        requireNative(input, nativeReport, boot, ready, enforcing);
        if (stdout.length() > 32 * 1024 * 1024 || stderr.length() > 32 * 1024 * 1024) {
            throw new IOException("retained install transcript exceeds bound");
        }
        var command = InstallerDriver.installCommand(input);
        if (!"failed".equals(text(runner, "baseLifecycle"))) {
            throw new IOException("retained install is not a failed runner");
        }
        if (runner.has("command") && (!command.equals(runner.get("command"))
                || !runner.keySet().equals(Set.of("schemaVersion", "command", "exit", "acceptanceVerified",
                        "baseLifecycle", "diagnostic"))
                || !number(runner, "schemaVersion", "1")
                || !runner.has("exit") || !runner.get("exit").isJsonPrimitive()
                || !runner.getAsJsonPrimitive("exit").isNumber()
                || !runner.get("exit").getAsString().matches("(?:0|[1-9][0-9]{0,2})")
                || runner.get("exit").getAsInt() > 255
                || !runner.has("acceptanceVerified") || !runner.get("acceptanceVerified").isJsonPrimitive()
                || !runner.getAsJsonPrimitive("acceptanceVerified").isBoolean()
                || runner.get("acceptanceVerified").getAsBoolean())) {
            throw new IOException("retained failed CLI process result differs");
        }
        String result = text(runner, "diagnostic");
        String reported = "java.io.IOException: installer reported terminal failure: ";
        if (!result.equals("java.io.IOException: installer process lacks verified accepted "
                + "two-boot completion") && !result.equals("java.io.IOException: installer process "
                + "lacks verified two-boot terminal completion") && !(result.startsWith(reported) && completedFailure(result.substring(reported.length())))) {
            throw new IOException("retained runner has no source-bound completed CLI failure");
        }
        String failure = InstallMonitor.retainedTerminalFailure(manifest, prior, stderr);
        if (!completedFailure(failure) || result.startsWith(reported)
                && !result.substring(reported.length()).equals(failure)) {
            throw new IOException("retained CLI has no matching terminal post-install failure");
        }
        return failed(input, boot, failure);
    }

    /** A killed desktop observer cannot grant acceptance, even after native completion. */
    public static JsonObject proveInterrupted(JsonObject input, JsonObject nativeReport, String boot,
            boolean ready, boolean enforcing, JsonObject timing) throws Exception {
        requireNative(input, nativeReport, boot, ready, enforcing);
        InstallerDriver.installCommand(input);
        if (!timing.keySet().equals(Set.of("schemaVersion", "startedAt", "exitedAt",
                "monotonicElapsedNanos", "scope", "status", "exit"))
                || !number(timing, "schemaVersion", "1")
                || !"launch-through-termination-observation".equals(text(timing, "scope"))
                || !"interrupted".equals(text(timing, "status"))
                || !number(timing, "exit", "137")
                || !timing.get("monotonicElapsedNanos").isJsonPrimitive()
                || !timing.getAsJsonPrimitive("monotonicElapsedNanos").isNumber()
                || !timing.get("monotonicElapsedNanos").getAsString().matches("[1-9][0-9]{0,18}")
                || !java.time.Instant.parse(text(timing, "exitedAt"))
                        .isAfter(java.time.Instant.parse(text(timing, "startedAt")))) {
            throw new IOException("interrupted installer has no terminal process timing");
        }
        return failed(input, boot, INTERRUPTED);
    }

    private static void requireNative(JsonObject input, JsonObject nativeReport, String boot,
            boolean ready, boolean enforcing) throws IOException {
        String manifest = text(input, "manifest"), prior = text(input, "priorBoot"), device = text(input, "device");
        if (!manifest.matches("[0-9a-f]{64}") || !InstallerDriver.emulator(device)
                || !uuid(prior) || !uuid(boot) || prior.equals(boot) || !ready || !enforcing
                || !"install".equals(text(nativeReport, "operation"))
                || !manifest.equals(text(nativeReport, "transactionId"))
                || !boot.equals(text(nativeReport, "bootId"))
                || !"install-complete".equals(text(nativeReport, "phase"))
                || !"complete".equals(text(nativeReport, "outcome"))
                || !number(nativeReport, "schemaVersion", "1")
                || !number(nativeReport, "rebootCount", "2")
                || !nativeReport.has("error") || !nativeReport.get("error").isJsonNull()) {
            throw new IOException("failed install has no matching terminal native proof");
        }
    }

    private static JsonObject failed(JsonObject input, String boot, String diagnostic) throws IOException {
        JsonObject proof = new JsonObject();
        proof.addProperty("schemaVersion", 1);
        proof.addProperty("resolution", "failed-reconciled");
        proof.addProperty("acceptance", "FAILED");
        proof.addProperty("functionalAcceptance", false);
        for (String key : Set.of("manifest", "device", "priorBoot")) proof.add(key, input.get(key).deepCopy());
        proof.addProperty("bootId", boot);
        proof.addProperty("nativePhase", "install-complete");
        proof.addProperty("nativeOutcome", "complete");
        proof.addProperty("diagnostic", diagnostic);
        require(proof, input);
        return proof;
    }

    public static void require(JsonObject proof, JsonObject input) throws IOException {
        if (!proof.keySet().equals(FIELDS) || !number(proof, "schemaVersion", "1")
                || !"failed-reconciled".equals(text(proof, "resolution"))
                || !"FAILED".equals(text(proof, "acceptance"))
                || !proof.get("functionalAcceptance").isJsonPrimitive()
                || !proof.getAsJsonPrimitive("functionalAcceptance").isBoolean()
                || proof.get("functionalAcceptance").getAsBoolean()
                || !"install-complete".equals(text(proof, "nativePhase"))
                || !"complete".equals(text(proof, "nativeOutcome"))
                || !(completedFailure(text(proof, "diagnostic")) || INTERRUPTED.equals(text(proof, "diagnostic")))
                || !uuid(text(proof, "bootId"))
                || !text(proof, "manifest").matches("[0-9a-f]{64}")
                || !InstallerDriver.emulator(text(proof, "device"))
                || !uuid(text(proof, "priorBoot"))) {
            throw new IOException("failed install reconciliation receipt differs");
        }
        for (String key : Set.of("manifest", "device", "priorBoot")) {
            if (!text(input, key).equals(text(proof, key))) {
                throw new IOException("failed install reconciliation identity differs: " + key);
            }
        }
        if (text(proof, "bootId").equals(text(proof, "priorBoot"))) {
            throw new IOException("failed install reconciliation did not cross a boot");
        }
    }

    public static JsonObject normalReceipt(String raw, String serial, String priorBoot)
            throws IOException {
        if (raw.length() > 8192 || !InstallerDriver.emulator(serial)) {
            throw new IOException("normal-only uninstall envelope exceeds its authority");
        }
        JsonObject response = ru.voboost.tests.RunFiles.parse(raw);
        if (!response.keySet().equals(Set.of("schemaVersion", "serial", "uninstall"))
                || !number(response, "schemaVersion", "1")
                || !serial.equals(text(response, "serial"))
                || !response.get("uninstall").isJsonObject()) {
            throw new IOException("normal-only uninstall envelope identity differs");
        }
        return InstallerDriver.absenceReceipt(response.get("uninstall").toString(), false,
                priorBoot);
    }

    public static void requireNormalCleanup(java.nio.file.Path file, JsonObject input)
            throws IOException {
        if (!"failed-install-cleanup".equals(text(input, "phase"))
                || java.nio.file.Files.size(file) > 4096) {
            throw new IOException("failed install requires its normal cleanup receipt");
        }
        InstallerDriver.absenceReceipt(java.nio.file.Files.readString(file), false,
                text(input, "priorBoot"));
    }

    private static boolean uuid(String value) {
        return value.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    }
    private static String text(JsonObject value, String key) throws IOException {
        if (!value.has(key) || !value.get(key).isJsonPrimitive()
                || !value.getAsJsonPrimitive(key).isString()) {
            throw new IOException("failed install field missing: " + key);
        }
        return value.get(key).getAsString();
    }
    private static boolean number(JsonObject value, String key, String expected) {
        return value.has(key) && value.get(key).isJsonPrimitive()
                && value.getAsJsonPrimitive(key).isNumber()
                && expected.equals(value.get(key).getAsString());
    }
    private FailedInstallReconciliation() {}
}
