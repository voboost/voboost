package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class FailedInstallReconciliationTest {
    private static final String MANIFEST = "a".repeat(64);
    private static final String PRIOR = "12345678-1234-1234-1234-123456789abc";
    private static final String BOOT = "22345678-1234-1234-1234-123456789abc";

    private static JsonObject input() {
        JsonObject value = new JsonObject();
        value.addProperty("manifest", MANIFEST);
        value.addProperty("device", "emulator-5554");
        value.addProperty("priorBoot", PRIOR);
        JsonObject process = new JsonObject();
        com.google.gson.JsonArray command = new com.google.gson.JsonArray();
        for (String argument : List.of("/candidate/voboost-installer", "--install",
                "--device", "emulator-5554")) command.add(argument);
        process.add("command", command);
        value.add("process", process);
        return value;
    }
    private static JsonObject nativeReport() {
        JsonObject value = new JsonObject();
        value.addProperty("schemaVersion", 1);
        value.addProperty("operation", "install");
        value.addProperty("transactionId", MANIFEST);
        value.addProperty("phase", "install-complete");
        value.addProperty("outcome", "complete");
        value.addProperty("rebootCount", 2);
        value.addProperty("bootId", BOOT);
        value.add("error", JsonNull.INSTANCE);
        return value;
    }
    private static JsonObject runner() {
        JsonObject value = new JsonObject();
        value.addProperty("baseLifecycle", "failed");
        value.addProperty("diagnostic", "java.io.IOException: installer process lacks verified "
                + "two-boot terminal completion");
        return value;
    }
    private static String failure() {
        JsonObject value = new JsonObject();
        value.addProperty("schemaVersion", 1);
        value.addProperty("sequence", 907);
        value.addProperty("timestamp", "2026-10-29T20:25:20.816Z");
        JsonObject source = new JsonObject();
        source.addProperty("kind", "installer");
        source.addProperty("id", "desktop");
        value.add("source", source);
        value.addProperty("level", "error");
        value.addProperty("component", "installer");
        value.addProperty("message", "Install failed: installed content identity is unavailable");
        value.add("fields", new JsonObject());
        return value + "\n";
    }
    private static JsonObject prove(JsonObject report, String stdout, String stderr,
            JsonObject result) throws Exception {
        return FailedInstallReconciliation.prove(input(), report, BOOT, true, true,
                stdout, stderr, result);
    }

    @Test void interruptedObserverResolvesOnlyACompletedNativeInstallAsFailed() throws Exception {
        JsonObject timing = new JsonObject();
        timing.addProperty("schemaVersion", 1);
        timing.addProperty("startedAt", "2026-11-13T18:36:06Z");
        timing.addProperty("exitedAt", "2026-11-13T18:37:05Z");
        timing.addProperty("monotonicElapsedNanos", 59000000000L);
        timing.addProperty("scope", "launch-through-termination-observation");
        timing.addProperty("status", "interrupted");
        timing.addProperty("exit", 137);
        JsonObject proof = FailedInstallReconciliation.proveInterrupted(input(), nativeReport(), BOOT, true, true, timing);
        assertEquals("FAILED", proof.get("acceptance").getAsString());
        assertFalse(proof.get("functionalAcceptance").getAsBoolean());
        for (String field : java.util.List.of("status", "exit", "exitedAt", "scope")) {
            JsonObject bad = timing.deepCopy(); bad.remove(field);
            assertThrows(Exception.class, () -> FailedInstallReconciliation.proveInterrupted(input(), nativeReport(), BOOT, true, true, bad));
        }
        JsonObject running = nativeReport(); running.addProperty("outcome", "running");
        assertThrows(Exception.class, () -> FailedInstallReconciliation.proveInterrupted(input(), running, BOOT, true, true, timing));
        assertThrows(Exception.class, () -> FailedInstallReconciliation.proveInterrupted(input(), nativeReport(), BOOT, true, false, timing));
    }

    @Test
    void terminalReadinessFailureRemainsFailedAndMustMatchTheRecordedProcess() throws Exception {
        String message = "Install failed: device operation completed, but readiness was not confirmed: "
                + "300-second device wait deadline expired";
        String stderr = failure().replace("Install failed: installed content identity is unavailable", message);
        JsonObject process = runner();
        process.addProperty("schemaVersion", 1);
        process.add("command", input().getAsJsonObject("process").get("command").deepCopy());
        process.addProperty("exit", 1);
        process.addProperty("acceptanceVerified", false);
        process.addProperty("diagnostic", "java.io.IOException: installer reported terminal failure: " + message);
        JsonObject proof = prove(nativeReport(), "", stderr, process);
        assertEquals("FAILED", proof.get("acceptance").getAsString());
        assertFalse(proof.get("functionalAcceptance").getAsBoolean());
        assertEquals(message, proof.get("diagnostic").getAsString());
        assertThrows(IOException.class, () -> prove(nativeReport(), "", failure(), process));
        JsonObject accepted = process.deepCopy(); accepted.addProperty("acceptanceVerified", true);
        assertThrows(IOException.class, () -> prove(nativeReport(), "", stderr, accepted));
    }

    @Test
    void ordinaryProductionCliNeedsNoAcceptanceArmToResolveAsFailed() throws Exception {
        JsonObject result = runner();
        result.addProperty("diagnostic", "java.io.IOException: installer reported terminal failure: "
                + "Install failed: installed content identity is unavailable");
        JsonObject proof = prove(nativeReport(), "", failure(), result);
        assertEquals("FAILED", proof.get("acceptance").getAsString());
        assertFalse(proof.get("functionalAcceptance").getAsBoolean());
    }

    @Test
    void knownNonzeroOrdinaryProcessResolvesAsFailedAndUnknownExitDoesNot() throws Exception {
        JsonObject process = runner();
        process.addProperty("schemaVersion", 1);
        process.add("command", input().getAsJsonObject("process").get("command").deepCopy());
        process.addProperty("exit", 1);
        process.addProperty("acceptanceVerified", false);
        JsonObject proof = prove(nativeReport(), "", failure(), process);
        assertEquals("FAILED", proof.get("acceptance").getAsString());
        assertFalse(proof.get("functionalAcceptance").getAsBoolean());
        JsonObject unknown = process.deepCopy();
        unknown.remove("exit");
        assertThrows(IOException.class, () -> prove(nativeReport(), "", failure(), unknown));
        JsonObject textExit = process.deepCopy();
        textExit.addProperty("exit", "1");
        assertThrows(IOException.class, () -> prove(nativeReport(), "", failure(), textExit));
        JsonObject accepted = process.deepCopy();
        accepted.addProperty("acceptanceVerified", true);
        assertThrows(IOException.class, () -> prove(nativeReport(), "", failure(), accepted));
        JsonObject foreign = process.deepCopy();
        foreign.getAsJsonArray("command").set(0, new com.google.gson.JsonPrimitive("/foreign/installer"));
        assertThrows(IOException.class, () -> prove(nativeReport(), "", failure(), foreign));
    }

    @Test
    void terminalMutationResolvesOnlyAsFailedAcceptanceWithoutInventingExitCode() throws Exception {
        StringBuilder stderr = new StringBuilder();
        for (int reboot = 1; reboot <= 2; reboot++) {
            JsonObject event = com.google.gson.JsonParser.parseString(failure()).getAsJsonObject();
            event.addProperty("sequence", reboot);
            event.addProperty("level", "info");
            event.addProperty("message", "Restarting the head unit (reboot " + reboot
                    + "). Waiting for reconnection; install will continue automatically.");
            stderr.append(event).append('\n');
        }
        JsonObject proof = prove(nativeReport(), "", stderr + failure(), runner());
        assertEquals("failed-reconciled", proof.get("resolution").getAsString());
        assertEquals("FAILED", proof.get("acceptance").getAsString());
        assertFalse(proof.get("functionalAcceptance").getAsBoolean());
        assertFalse(proof.has("exitCode"));
        FailedInstallReconciliation.require(proof, input());
    }

    @Test
    void nativeRunningForeignOrIncompleteProofCannotReleaseTheOwner() {
        for (String field : List.of("transactionId", "phase", "outcome", "bootId")) {
            JsonObject report = nativeReport();
            report.addProperty(field, "other");
            assertThrows(IOException.class,
                    () -> prove(report, "", failure(), runner()), field);
        }
        JsonObject count = nativeReport();
        count.addProperty("rebootCount", "2");
        assertThrows(IOException.class,
                () -> prove(count, "", failure(), runner()));
        assertThrows(IOException.class, () -> FailedInstallReconciliation.prove(input(),
                nativeReport(), BOOT, true, false, "", failure(), runner()));
    }

    @Test
    void unknownRunnerOrMissingTerminalFailureRemainsUnknown() {
        JsonObject unknown = runner();
        unknown.addProperty("diagnostic", "CLI process/helper termination unconfirmed");
        assertThrows(IOException.class,
                () -> prove(nativeReport(), "", failure(), unknown));
        assertThrows(IOException.class,
                () -> prove(nativeReport(), "", "", runner()));
        JsonObject foreign = input();
        foreign.getAsJsonObject("process").getAsJsonArray("command").set(3,
                new com.google.gson.JsonPrimitive("emulator-5556"));
        assertThrows(IOException.class, () -> FailedInstallReconciliation.prove(foreign,
                nativeReport(), BOOT, true, true, "", failure(), runner()));
    }

    @Test
    void failedReceiptCannotBeRewrittenAsSuccessOrAnotherEndpoint() throws Exception {
        JsonObject proof = prove(nativeReport(), "", failure(), runner());
        proof.addProperty("functionalAcceptance", true);
        assertThrows(IOException.class, () -> FailedInstallReconciliation.require(proof, input()));
        JsonObject wrong = prove(nativeReport(), "", failure(), runner());
        wrong.addProperty("device", "emulator-5556");
        assertThrows(IOException.class, () -> FailedInstallReconciliation.require(wrong, input()));
    }
    @Test
    void normalOnlyEnvelopeBindsEndpointAndPreservesDistinctUninstallTransaction() throws Exception {
        JsonObject receipt = new JsonObject();
        receipt.addProperty("transactionId", "b".repeat(64));
        receipt.addProperty("bootId", BOOT);
        com.google.gson.JsonArray checks = new com.google.gson.JsonArray();
        for (String check : List.of("root", "app-data", "init-file", "package", "app-op",
                "mounts", "processes", "init-services", "localization-overlays", "policy")) checks.add(check);
        receipt.add("checks", checks);
        JsonObject response = new JsonObject();
        response.addProperty("schemaVersion", 1);
        response.addProperty("serial", "emulator-5554");
        response.add("uninstall", receipt);
        JsonObject observed = FailedInstallReconciliation.normalReceipt(response.toString(),
                "emulator-5554", PRIOR);
        assertEquals("b".repeat(64), observed.get("transactionId").getAsString());
        assertThrows(IOException.class, () -> FailedInstallReconciliation.normalReceipt(
                response.toString(), "emulator-5556", PRIOR));
        assertThrows(IOException.class, () -> FailedInstallReconciliation.normalReceipt(
                response.toString(), "emulator-5554", BOOT));
        receipt.remove("transactionId");
        receipt.addProperty("state", "emergency-removed");
        assertThrows(IOException.class, () -> FailedInstallReconciliation.normalReceipt(
                response.toString(), "emulator-5554", PRIOR));
    }

}
