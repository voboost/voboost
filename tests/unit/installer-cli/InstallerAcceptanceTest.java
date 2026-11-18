package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstallerAcceptanceTest {
    private static final String MANIFEST = "a".repeat(64), TOKEN = "c".repeat(64);
    private static final String BEFORE = "12345678-1234-1234-1234-123456789abc", AFTER = "22345678-1234-1234-1234-123456789abc";
    private static String arm() {
        return "install-acceptance-armed {\"schemaVersion\":1,\"transactionId\":\"" + MANIFEST
            + "\",\"manifestSha256\":\"" + MANIFEST + "\",\"attemptToken\":\"" + TOKEN + "\"}";
    }
    private static String receipt(String decision, String state) {
        return "{\"schemaVersion\":1,\"transactionId\":\"" + MANIFEST + "\",\"manifestSha256\":\"" + MANIFEST
            + "\",\"attemptToken\":\"" + TOKEN + "\",\"bootId\":\"" + AFTER
            + "\",\"rebootCount\":1,\"decision\":\"" + decision + "\",\"state\":\"" + state + "\",\"expiresMonotonicMs\":\"1000\"}";
    }
    private static InstallerAcceptance waiting() throws Exception {
        var protocol = new InstallerAcceptance(MANIFEST, BEFORE);
        protocol.observeArm(arm());
        protocol.statusRequest(AFTER, 1);
        protocol.observeWaiting(receipt("status", "waiting"));
        return protocol;
    }
    @Test void requiresCurrentStatusBeforeExplicitAcceptAndCorrelatedReceipt() throws Exception {
        var protocol = waiting();
        assertFalse(protocol.accepted());
        JsonObject action = protocol.decisionRequest(true);
        assertEquals("install-acceptance", action.get("action").getAsString());
        assertEquals("accept", action.get("decision").getAsString());
        assertFalse(action.has("schemaVersion"));
        assertFalse(protocol.accepted());
        protocol.observeDecision(receipt("accept", "accepted"));
        assertTrue(protocol.accepted());
        assertThrows(IOException.class, () -> protocol.observeDecision(receipt("accept", "accepted")));
        assertThrows(IOException.class, () -> protocol.decisionRequest(true));
    }
    @Test void failedVerificationCanOnlyConsumeAnExplicitReject() throws Exception {
        var protocol = waiting();
        assertEquals("reject", protocol.decisionRequest(false).get("decision").getAsString());
        assertThrows(IOException.class, () -> protocol.observeDecision(receipt("accept", "accepted")));
        protocol.observeDecision(receipt("reject", "rejected"));
        assertFalse(protocol.accepted());
    }
    @Test void rejectsPrematureRepeatedAndForeignArmsAndBoots() throws Exception {
        var protocol = new InstallerAcceptance(MANIFEST, BEFORE);
        assertThrows(IOException.class, () -> protocol.decisionRequest(true));
        assertThrows(IOException.class, () -> protocol.statusRequest(AFTER, 1));
        assertThrows(IOException.class, () -> protocol.observeArm(arm().replace(MANIFEST, "b".repeat(64))));
        assertTrue(protocol.observeArm(arm()));
        assertThrows(IOException.class, () -> protocol.observeArm(arm()));
        assertThrows(IOException.class, () -> protocol.statusRequest(BEFORE, 1));
        assertThrows(IOException.class, () -> protocol.statusRequest(AFTER, 2));
        protocol.statusRequest(AFTER, 1);
        assertThrows(IOException.class, () -> protocol.statusRequest(AFTER, 1));
    }
    @Test void rejectsWrongIdentityShapeStateAndLeaseWithoutAccepting() throws Exception {
        for (String bad : List.of(
                receipt("status", "waiting").replace(TOKEN, "d".repeat(64)),
                receipt("status", "waiting").replace(AFTER, BEFORE),
                receipt("status", "accepted"), receipt("accept", "waiting"),
                receipt("status", "waiting").replace("\"1000\"", "\"0\""),
                receipt("status", "waiting").replace("\"1000\"", "\"18446744073709551616\""),
                receipt("status", "waiting").replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"),
                receipt("status", "waiting").replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""),
                receipt("status", "waiting").replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"unknown\":1"))) {
            var protocol = new InstallerAcceptance(MANIFEST, BEFORE);
            protocol.observeArm(arm()); protocol.statusRequest(AFTER, 1);
            assertThrows(IOException.class, () -> protocol.observeWaiting(bad), bad);
            assertFalse(protocol.accepted());
        }
        var protocol = waiting(); protocol.decisionRequest(true);
        assertThrows(IOException.class, () -> protocol.observeDecision(receipt("accept", "accepted").replace("\"1000\"", "\"1001\"")));
        assertFalse(protocol.accepted());
    }
}
