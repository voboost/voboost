package ru.voboost.tests.agents;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;
import ru.voboost.tests.logging.InstallerLogs;

/** Actual Java endpoint state operations routed by inject to owner storage; no cached-status substitution. */
public final class RootVehicleState {

    public record Authority(String boot, String generation, String catalog, String process,
            String pid, String starttime, String epoch, String document) {}

    public record Operation(String agent, String key, String operation, String outcome,
            Boolean value, JsonObject fields) {
        public Operation {
            fields = fields.deepCopy();
        }
        @Override
        public JsonObject fields() {
            return fields.deepCopy();
        }
    }

    private static final Set<String> REQUIRED = Set.of("agentId", "key", "operation", "outcome",
            "process", "targetPid", "endpointEpoch");

    private static final Set<String> OPTIONAL = Set.of("value", "generationId", "catalogSha256",
            "targetStarttime", "documentGeneration", "bootId", "requestId");

    private static void require(boolean value, String reason) throws IOException {
        if (!value)
            throw new IOException("root vehicle operation: " + reason);
    }

    private static String text(JsonObject value, String key) throws IOException {
        require(value.has(key) && value.get(key).isJsonPrimitive()
                && value.getAsJsonPrimitive(key).isString(), key);
        return value.get(key).getAsString();
    }

    private static boolean integer(String value, boolean positive) {
        return value.matches(positive ? "[1-9][0-9]{0,19}" : "0|[1-9][0-9]{0,19}")
                && new BigInteger(value).bitLength() <= 64;
    }

    public static Operation read(JsonObject fields) throws IOException {
        Set<String> allowed = new HashSet<>(REQUIRED);
        allowed.addAll(OPTIONAL);
        require(fields.keySet().containsAll(REQUIRED) && allowed.containsAll(fields.keySet()),
                "closed fields");
        for (String key : fields.keySet())
            require(text(fields, key).length() <= 256, "field bound");
        String agent = text(fields, "agentId"), key = text(fields, "key"), operation = text(fields,
                "operation"), outcome = text(fields, "outcome");
        require((agent.equals("vehicle-drive-forced-ev") && key.equals("forced-ev-armed"))
                || (agent.equals("vehicle-drive-pedestrian-warning")
                && key.equals("pedestrian-warning-armed")), "fixed pair");
        require(Set.of("read", "write-readback").contains(operation) && Set.of("value", "missing",
                "unavailable").contains(outcome), "operation/outcome");
        String pid = text(fields, "targetPid");
        require(integer(pid, true)
                && new BigInteger(pid).compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0
                && integer(text(fields, "endpointEpoch"), true), "PID/epoch");
        require(text(fields, "process").matches("[A-Za-z][A-Za-z0-9_.:-]{0,254}"), "process");
        for (String optional : Set.of("targetStarttime", "documentGeneration"))
            if (fields.has(optional))
                require(integer(text(fields, optional), optional.equals("targetStarttime")),
                        optional);
        if (fields.has("bootId"))
            require(text(fields, "bootId").matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"),
                    "boot");
        require(fields.has("generationId") == fields.has("catalogSha256"),
                "resident identity pair");
        if (operation.equals("read"))
            require(fields.has("requestId") && integer(text(fields, "requestId"), false),
                    "read request");
        else
            require(!fields.has("requestId"), "write request");
        Boolean value = null;
        if (outcome.equals("value")) {
            String raw = text(fields, "value");
            require(Set.of("true", "false").contains(raw), "boolean triad");
            value = raw.equals("true");
        } else
            require(!fields.has("value"), "nonvalue carries value");
        return new Operation(agent, key, operation, outcome, value, fields.deepCopy());
    }

    public static void requireAuthority(Operation operation,
            Authority authority) throws IOException {
        for (var pair : java.util.Map.of("bootId", authority.boot(), "generationId",
                authority.generation(), "catalogSha256", authority.catalog(), "process",
                authority.process(), "targetPid", authority.pid(), "targetStarttime",
                authority.starttime(), "endpointEpoch", authority.epoch(), "documentGeneration",
                authority.document()).entrySet())
            require(pair.getValue().equals(text(operation.fields(), pair.getKey())),
                    "current endpoint " + pair.getKey());
    }
    /** Exact current desired context selects history; a later nonvalue within that context wins. */
    public static Operation recovered(String agent, Authority authority,
            InstallerLogs.Snapshot prior, InstallerLogs.Snapshot current) throws IOException {
        Operation latest = null;
        boolean read = false;
        for (var row : current.effectsAfter(prior)) {
            if (!row.fields().has("agentId"))
                continue;
            Operation operation = read(row.fields());
            if (!agent.equals(operation.agent()))
                continue;
            require(operation.fields().has("documentGeneration"),
                    "root operation desired context missing");
            if (!authority.document().equals(text(operation.fields(), "documentGeneration")))
                continue;
            requireAuthority(operation, authority);
            latest = operation;
            if (operation.operation().equals("read") && operation.outcome().equals("value"))
                read = true;
        }
        require(read && latest != null && latest.outcome().equals("value"),
                "fresh present read/recovery missing");
        return latest;
    }

    private RootVehicleState() {}
}
