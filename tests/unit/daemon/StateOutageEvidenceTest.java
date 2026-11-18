package ru.voboost.tests.installer.cli;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import ru.voboost.tests.agents.RootVehicleState;
import ru.voboost.tests.agents.StateOutageEvidence;
import ru.voboost.tests.logging.InstallerLogs;

class StateOutageEvidenceTest {

    private static final RootVehicleState.Authority AUTHORITY = new RootVehicleState.Authority(FaultWindowTest.BOOT, "b".repeat(64), "c".repeat(64), "com.qinggan.systemservice", "41", "514", "8", "17");

    private static InstallerLogs.Diagnostic diagnostic(int sequence, String event, String outcome,
            Integer attempt) {
        JsonObject fields = new JsonObject();
        if (attempt != null)
            fields.addProperty("attempt", attempt);
        JsonObject source = new JsonObject();
        source.addProperty("bootId", AUTHORITY.boot());
        source.addProperty("process", AUTHORITY.process());
        source.addProperty("targetPid", AUTHORITY.pid());
        source.addProperty("targetStarttime", AUTHORITY.starttime());
        source.addProperty("endpointEpoch", AUTHORITY.epoch());
        source.addProperty("desiredDocumentGeneration", AUTHORITY.document());
        return new InstallerLogs.Diagnostic(BigInteger.valueOf(sequence), StateOutageEvidence.AGENT,
                event, outcome, fields, source);
    }

    private static InstallerLogs.Snapshot snapshot(int head, InstallerLogs.Diagnostic ... rows) {
        Set<BigInteger> ids = new java.util.HashSet<>();
        for (int i = 1; i <= head; i++)
            ids.add(BigInteger.valueOf(i));
        return new InstallerLogs.Snapshot(AUTHORITY.boot(), "a".repeat(32),
                BigInteger.valueOf(head), BigInteger.valueOf(head), ids, Instant.EPOCH,
                List.of(rows), List.of(), Path.of("evidence"));
    }

    private static FaultWindow window() throws Exception {
        var request = FaultWindowTest.request();
        request.addProperty("kind", "vehicle-state-reply-loss");
        var window = new FaultWindow(request, "emulator-5588", "candidate");
        var armed = FaultWindowTest.envelope("armed");
        armed.getAsJsonObject("body").addProperty("kind", "vehicle-state-reply-loss");
        window.observe(armed.toString());
        return window;
    }

    @Test

    void twoDistinctBoundedActualRetriesRequireTheCurrentJavaEndpoint() throws Exception {
        var prior = snapshot(1);
        var first = diagnostic(2, "agent.start.state-read", "unavailable-retry", 1);
        var second = diagnostic(3, "agent.start.state-read", "unavailable-retry", 2);
        assertEquals(Set.of(1, 2), StateOutageEvidence.retries(prior, snapshot(3, first, second),
                AUTHORITY));
        assertThrows(IOException.class, () -> StateOutageEvidence.retries(prior, snapshot(2, first),
                AUTHORITY));
        second.fields().addProperty("attempt", 1);
        assertThrows(IOException.class, () -> StateOutageEvidence.retries(prior, snapshot(3, first,
                second), AUTHORITY));
        second.fields().addProperty("attempt", 21);
        assertThrows(IOException.class, () -> StateOutageEvidence.retries(prior, snapshot(3, first,
                second), AUTHORITY));
        second.fields().addProperty("attempt", 2);
        second.sourceFields().addProperty("targetPid", "42");
        assertThrows(IOException.class, () -> StateOutageEvidence.retries(prior, snapshot(3, first,
                second), AUTHORITY));
    }

    @Test

    void quietWindowNeedsCompleteLiveTailAndNoNewRetry() throws Exception {
        var prior = snapshot(1);
        var window = window();
        StateOutageEvidence.quiet(prior, snapshot(1), AUTHORITY, window);
        var incomplete = new InstallerLogs.Snapshot(AUTHORITY.boot(), "a".repeat(32),
                BigInteger.TWO, BigInteger.ONE, Set.of(BigInteger.ONE), Instant.EPOCH, List.of(),
                List.of(), Path.of("partial"));
        assertThrows(IOException.class, () -> StateOutageEvidence.quiet(prior, incomplete,
                AUTHORITY, window));
        assertThrows(IOException.class, () -> StateOutageEvidence.quiet(prior, snapshot(2,
                diagnostic(2, "agent.start.state-read", "unavailable-retry", 3)), AUTHORITY,
                window));
    }

    @Test

    void checkboxSubstepsAreRestrictedToDeclaredCurrentPwOutage() throws Exception {
        JsonObject scope = new JsonObject();
        scope.addProperty("phase", "vehicle-state-outage");
        scope.addProperty("agent", StateOutageEvidence.AGENT);
        scope.addProperty("priorBoot", AUTHORITY.boot());
        scope.addProperty("cancel", false);
        InstallerDriver.requireOutageCheckboxScope(scope, StateOutageEvidence.AGENT, true,
                AUTHORITY.boot());
        assertThrows(IOException.class, () -> InstallerDriver.requireOutageCheckboxScope(scope,
                StateOutageEvidence.AGENT, false, AUTHORITY.boot()));
        scope.addProperty("cancel", true);
        InstallerDriver.requireOutageCheckboxScope(scope, StateOutageEvidence.AGENT, false,
                AUTHORITY.boot());
        assertThrows(IOException.class, () -> InstallerDriver.requireOutageCheckboxScope(scope,
                "launcher", true, AUTHORITY.boot()));
        assertThrows(IOException.class, () -> InstallerDriver.requireOutageCheckboxScope(scope,
                StateOutageEvidence.AGENT, true, "foreign"));
    }
}
