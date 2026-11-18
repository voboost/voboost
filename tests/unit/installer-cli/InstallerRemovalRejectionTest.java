package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

class InstallerRemovalRejectionTest {
    private static final String BOOT = "11111111-1111-4111-8111-111111111111";
    private static final String MANIFEST = "a".repeat(64);
    private static JsonObject input() throws Exception {
        return RunFiles.parse("{\"manifest\":\"" + MANIFEST + "\",\"device\":\"emulator-5574\",\"phase\":\"failed-install-cleanup\",\"priorBoot\":\"" + BOOT
            + "\",\"process\":{\"command\":[\"/installer\",\"--emulator-check\",\"/request.json\",\"--device\",\"emulator-5574\",\"--candidate\",\"/installer\"]}}");
    }
    private static JsonObject report() throws Exception {
        return RunFiles.parse("{\"operation\":\"install\",\"phase\":\"install-complete\",\"outcome\":\"complete\",\"transactionId\":\""+MANIFEST+"\",\"bootId\":\""+BOOT+"\"}");
    }
    private static JsonObject timing() throws Exception {
        return RunFiles.parse("{\"schemaVersion\":1,\"startedAt\":\"2026-11-13T18:50:24Z\",\"exitedAt\":\"2026-11-13T18:50:26Z\",\"monotonicElapsedNanos\":2000000000,\"scope\":\"launch-through-exit-observation\",\"status\":\"exited\",\"exit\":1}");
    }
    private static String stderr() throws Exception {
        JsonObject row = RunFiles.parse("{\"schemaVersion\":1,\"source\":{\"kind\":\"installer\",\"id\":\"desktop\"},\"level\":\"error\",\"message\":\"Emulator acceptance failed: Cluster provisioning is missing; signed removal cannot be verified\"}");
        return row + "\n";
    }
    @Test void onlyKnownPreAdmissionRejectionCanReleaseTheAttempt() throws Exception {
        var proof = InstallerRemovalRejection.prove(input(), report(), BOOT, "", stderr(), timing());
        assertFalse(proof.get("functionalAcceptance").getAsBoolean());
        InstallerRemovalRejection.require(proof, input());
        for (String field : java.util.List.of("exit", "status", "scope", "exitedAt")) {
            JsonObject bad = timing(); bad.remove(field);
            assertThrows(Exception.class, () -> InstallerRemovalRejection.prove(input(), report(), BOOT, "", stderr(), bad));
        }
        JsonObject removed = report(); removed.addProperty("operation", "uninstall");
        assertThrows(Exception.class, () -> InstallerRemovalRejection.prove(input(), removed, BOOT, "", stderr(), timing()));
        assertThrows(Exception.class, () -> InstallerRemovalRejection.prove(input(), report(), BOOT, "receipt", stderr(), timing()));
        assertThrows(Exception.class, () -> InstallerRemovalRejection.prove(input(), report(), "22222222-2222-4222-8222-222222222222", "", stderr(), timing()));
        assertFalse(InstallerRemovalRejection.reported(stderr().replace("Cluster provisioning is missing", "Transport failed")));
    }
}
