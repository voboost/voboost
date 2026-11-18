package ru.voboost.tests.agents;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import ru.voboost.tests.RunFiles;
import ru.voboost.tests.logging.InstallerLogs;

class DaemonVehicleStateTest {

    private static final String BOOT = "11111111-1111-1111-1111-111111111111",
            AGENT = "vehicle-drive-pedestrian-warning";

    private static final RootVehicleState.Authority AUTHORITY = new RootVehicleState.Authority(BOOT,
            "generation", "catalog", "com.qinggan.systemservice", "41", "514", "8", "17");

    private static JsonObject fields(String outcome) throws Exception {
        var fields = RunFiles.parse("{\"agentId\":\"vehicle-drive-pedestrian-warning\",\"key\":\"pedestrian-warning-armed\",\"operation\":\"read\",\"outcome\":\"" + outcome + "\",\"process\":\"com.qinggan.systemservice\",\"targetPid\":\"41\",\"targetStarttime\":\"514\",\"endpointEpoch\":\"8\",\"generationId\":\"generation\",\"catalogSha256\":\"catalog\",\"bootId\":\"" + BOOT + "\",\"requestId\":\"7\"}");
        fields.addProperty("documentGeneration", "17");
        if (outcome.equals("value"))
            fields.addProperty("value", "false");
        return fields;
    }

    private static InstallerLogs.Snapshot snapshot(int head, InstallerLogs.RootEffect ... effects) {
        var ids = new java.util.HashSet<BigInteger>();
        for (int i = 1; i <= head; i++)
            ids.add(BigInteger.valueOf(i));
        return new InstallerLogs.Snapshot(BOOT, "a".repeat(32), BigInteger.valueOf(head),
                BigInteger.valueOf(head), ids, Instant.EPOCH, List.of(), List.of(effects),
                Path.of("evidence"));
    }

    @Test

    void explicitFalseMissingAndUnavailableRemainDistinct() throws Exception {
        assertEquals(false, RootVehicleState.read(fields("value")).value());
        for (String outcome : List.of("missing", "unavailable")) {
            var state = RootVehicleState.read(fields(outcome));
            assertNull(state.value());
            assertEquals(outcome, state.outcome());
        }
        RootVehicleState.requireAuthority(RootVehicleState.read(fields("value")), AUTHORITY);
    }

    @Test

    void malformedPairsBooleansRequestsAndForeignAuthorityFail() throws Exception {
        for (String field : List.of("key", "value", "requestId", "operation", "outcome")) {
            var invalid = fields("value");
            invalid.addProperty(field, "foreign");
            assertThrows(IOException.class, () -> RootVehicleState.read(invalid));
        }
        var wrong = fields("value");
        wrong.addProperty("targetStarttime", "515");
        assertThrows(IOException.class,
                () -> RootVehicleState.requireAuthority(RootVehicleState.read(wrong), AUTHORITY));
        var extra = fields("value");
        extra.addProperty("command", "forbidden");
        assertThrows(IOException.class, () -> RootVehicleState.read(extra));
        var booleanValue = fields("value");
        booleanValue.addProperty("value", false);
        assertThrows(IOException.class, () -> RootVehicleState.read(booleanValue));
    }

    @Test

    void realFreshReadRequiredAndLaterUnavailableWins() throws Exception {
        var prior = snapshot(1);
        var read = new InstallerLogs.RootEffect(BigInteger.TWO, fields("value"));
        assertEquals(false, RootVehicleState.recovered(AGENT, AUTHORITY, prior, snapshot(2,
                read)).value());
        var unavailable = new InstallerLogs.RootEffect(BigInteger.valueOf(3),
                fields("unavailable"));
        assertThrows(IOException.class, () -> RootVehicleState.recovered(AGENT, AUTHORITY, prior,
                snapshot(3, read, unavailable)));
        var write = fields("value");
        write.addProperty("operation", "write-readback");
        write.remove("requestId");
        assertThrows(IOException.class, () -> RootVehicleState.recovered(AGENT, AUTHORITY, prior,
                snapshot(2, new InstallerLogs.RootEffect(BigInteger.TWO, write))));
        assertThrows(IOException.class, () -> RootVehicleState.recovered(AGENT, AUTHORITY,
                snapshot(2, read), snapshot(2, read)));
    }

    @Test

    void delayedOldDesiredContextCannotSubstituteForOrPoisonCurrentRecovery() throws Exception {
        var prior = snapshot(1);
        var old = fields("value");
        old.addProperty("documentGeneration", "16");
        assertThrows(IOException.class, () -> RootVehicleState.recovered(AGENT, AUTHORITY, prior,
                snapshot(2, new InstallerLogs.RootEffect(BigInteger.TWO, old))));
        var current = new InstallerLogs.RootEffect(BigInteger.TWO, fields("value"));
        var late = fields("unavailable");
        late.addProperty("documentGeneration", "16");
        assertEquals(false, RootVehicleState.recovered(AGENT, AUTHORITY, prior, snapshot(3, current,
                new InstallerLogs.RootEffect(BigInteger.valueOf(3), late))).value());
        late.remove("documentGeneration");
        assertThrows(IOException.class, () -> RootVehicleState.recovered(AGENT, AUTHORITY, prior,
                snapshot(3, current, new InstallerLogs.RootEffect(BigInteger.valueOf(3), late))));
    }
}
