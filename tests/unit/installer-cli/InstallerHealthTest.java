package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstallerHealthTest {
    private static final String VERSION = "1.0.0-beta1";

    @Test void focusedPwKeepsIdentityAndOwnerChecksWithoutGrantingWholeProductHealth() throws Exception {
        JsonObject degraded = healthy();
        degraded.addProperty("health", "degraded");
        degraded.addProperty("diagnostic", "unrelated injection quarantined");
        InstallerDriver.requireAgentCandidateStatus(degraded, VERSION);
        assertThrows(IOException.class, () -> InstallerDriver.requireHealthyStatus(degraded, VERSION));
        degraded.addProperty("daemonStatus", "stopped");
        assertThrows(IOException.class, () -> InstallerDriver.requireAgentCandidateStatus(degraded, VERSION));
        degraded.addProperty("daemonStatus", "running");
        degraded.addProperty("contentRelation", "different");
        assertThrows(IOException.class, () -> InstallerDriver.requireAgentCandidateStatus(degraded, VERSION));
    }

    private static JsonObject healthy() {
        JsonObject status = new JsonObject();
        status.addProperty("route", "management");
        status.addProperty("health", "healthy");
        status.addProperty("daemonStatus", "running");
        status.addProperty("installedVersion", VERSION);
        status.addProperty("contentRelation", "same");
        status.add("diagnostic", JsonNull.INSTANCE);
        return status;
    }

    @Test
    void healthyRunningVersionCannotSubstituteForUnavailableContentIdentity() throws Exception {
        InstallerDriver.requireHealthyStatus(healthy(), VERSION);
        for (String relation : List.of("unknown", "different", "unavailable", "")) {
            JsonObject status = healthy();
            status.addProperty("contentRelation", relation);
            assertThrows(IOException.class,
                    () -> InstallerDriver.requireHealthyStatus(status, VERSION), relation);
        }
        JsonObject missing = healthy();
        missing.remove("contentRelation");
        assertThrows(IOException.class,
                () -> InstallerDriver.requireHealthyStatus(missing, VERSION));
        JsonObject coerced = healthy();
        coerced.addProperty("contentRelation", true);
        assertThrows(IOException.class,
                () -> InstallerDriver.requireHealthyStatus(coerced, VERSION));
    }

    @Test
    void sameContentStillRequiresHealthyCurrentVersionAndNoDiagnostic() {        for (String field : List.of("route", "health", "daemonStatus", "installedVersion")) {
            JsonObject status = healthy();
            status.addProperty(field, "other");
            assertThrows(IOException.class,
                    () -> InstallerDriver.requireHealthyStatus(status, VERSION), field);
        }
        JsonObject diagnostic = healthy();
        diagnostic.addProperty("diagnostic", "PermissionDenied");
        assertThrows(IOException.class,
                () -> InstallerDriver.requireHealthyStatus(diagnostic, VERSION));
    }

    @Test
    void aResidentRestartThatReportsWaitingIsSampledUntilItRecovers() throws Exception {
        JsonArray attempts = new JsonArray();
        ArrayDeque<JsonObject> samples = new ArrayDeque<>();
        JsonObject waiting = healthy();
        waiting.addProperty("health", "degraded");
        waiting.addProperty("diagnostic",
                "resident status: status.json injection[1] is unhealthy: state=waiting reason=unrecognized");
        samples.add(waiting); samples.add(waiting); samples.add(healthy());
        List<Long> pauses = new ArrayList<>();

        JsonObject accepted = InstallerDriver.awaitHealthy(samples::poll, VERSION,
                () -> 0L, 1_000_000L, pauses::add, attempts);

        assertNotNull(accepted, "a recovered sample must be accepted before the deadline");
        assertTrue(accepted.get("diagnostic").isJsonNull());
        assertEquals(List.of(5000L, 5000L), pauses, "each retry pauses for the bounded interval");
        assertEquals(List.of("health=degraded", "health=degraded", "healthy"),
                attempts.asList().stream().map(JsonElement::getAsString).toList());
    }

    @Test
    void anUnhealthyStateAtTheDeadlineIsStillRejected() throws Exception {
        JsonArray attempts = new JsonArray();
        JsonObject degraded = healthy();
        degraded.addProperty("health", "degraded");
        List<Long> pauses = new ArrayList<>();
        long[] clock = {0L};

        JsonObject accepted = InstallerDriver.awaitHealthy(() -> {
            clock[0] += 20_000_000_000L;
            return degraded;
        }, VERSION, () -> clock[0], 50_000_000_000L, pauses::add, attempts);

        assertNull(accepted, "persistent unhealth is never accepted, however many samples agree");
        assertEquals(List.of("health=degraded", "health=degraded", "health=degraded"),
                attempts.asList().stream().map(JsonElement::getAsString).toList());
        assertEquals(List.of(5000L, 5000L), pauses);
    }

    @Test
    void aDifferentInstalledIdentityNeverSettlesByWaiting() throws Exception {
        JsonArray attempts = new JsonArray();
        JsonObject foreign = healthy();
        foreign.addProperty("installedVersion", "1.0.0-beta2");
        assertThrows(IOException.class, () -> InstallerDriver.awaitHealthy(() -> foreign, VERSION,
                () -> 0L, 1_000_000L, millis -> fail("an identity mismatch must not be retried"), attempts));
        assertEquals(0, attempts.size());

        JsonObject unknownContent = healthy();
        unknownContent.addProperty("contentRelation", "different");
        assertThrows(IOException.class, () -> InstallerDriver.awaitHealthy(() -> unknownContent, VERSION,
                () -> 0L, 1_000_000L, millis -> fail("an identity mismatch must not be retried"), attempts));
    }
}
