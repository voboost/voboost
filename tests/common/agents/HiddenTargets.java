package ru.voboost.tests.agents;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Startup availability of enabled running targets, never evidence of each agent's functional effect. */
public final class HiddenTargets {
    public static final class Pending extends IOException { public Pending(String reason) { super(reason); } }
    private HiddenTargets() {}
    public static Set<String> defaultEnabledFixture(JsonObject catalog) throws IOException {
        Set<String> result = new HashSet<>();
        try {
            for (JsonElement value : catalog.getAsJsonArray("agents")) {
                JsonObject agent = value.getAsJsonObject(), activation = agent.getAsJsonObject("activation");
                if (!"stable".equals(agent.get("status").getAsString()) || !"checkbox".equals(activation.get("type").getAsString())) continue;
                JsonElement enabled = activation.get("default");
                if (enabled == null || !enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean()) throw new IOException("signed activation default is unavailable");
                if (enabled.getAsBoolean()) result.add(agent.get("id").getAsString());
            }
        } catch (RuntimeException malformed) { throw new IOException("malformed default startup fixture", malformed); }
        if (result.isEmpty()) throw new IOException("hidden startup requires an explicit nonempty enabled-agent fixture");
        return Set.copyOf(result);
    }
    public static JsonObject snapshot(JsonObject catalog, RuntimeObservation current, Set<String> prerequisiteEnabled) throws IOException {
        if (prerequisiteEnabled.isEmpty()) throw new IOException("hidden startup requires a nonempty expected enabled set");
        JsonObject result = new JsonObject();
        try {
            for (JsonElement value : catalog.getAsJsonArray("agents")) {
                JsonObject agent = value.getAsJsonObject();
                if (!"stable".equals(agent.get("status").getAsString()) || agent.get("id").getAsString().equals("settings")) continue;
                if (!"checkbox".equals(agent.getAsJsonObject("activation").get("type").getAsString())) throw new IOException("explicit stable activation recipe required");
                String id = agent.get("id").getAsString(); if (result.has(id)) throw new IOException("duplicate prior stable agent");
                result.add(id, current.desiredConfiguration(id));
            }
        } catch (RuntimeException malformed) { throw new IOException("malformed prior agent inventory", malformed); }
        for (String id : prerequisiteEnabled) if (!result.has(id) || !result.getAsJsonObject(id).get("enabled").getAsBoolean())
            throw new IOException("hidden startup prerequisite agent is not enabled: " + id);
        return result;
    }
    public static void requireSameConfiguration(JsonObject expected, JsonObject catalog, RuntimeObservation current) throws IOException {
        Set<String> enabled = new HashSet<>();
        for (String id : expected.keySet()) if (expected.getAsJsonObject(id).get("enabled").getAsBoolean()) enabled.add(id);
        if (enabled.isEmpty()) throw new IOException("hidden startup has no preserved prior enabled-agent prerequisite");
        JsonObject actual = snapshot(catalog, current, enabled);
        if (!expected.equals(actual)) throw new IOException("hidden startup changed the authoritative prior agent desired state or parameters");
    }
    public static JsonArray read(JsonObject catalog, RuntimeObservation current, Set<String> processes, JsonObject expected, ClusterObservation nativeObservation) throws IOException {
        requireSameConfiguration(expected, catalog, current);
        JsonArray evidence = new JsonArray(); Set<String> ids = new HashSet<>();
        try {
            for (JsonElement value : catalog.getAsJsonArray("agents")) {
                JsonObject agent = value.getAsJsonObject(); String id = agent.get("id").getAsString();
                if (!ids.add(id)) throw new IOException("candidate agent is duplicated");
                if (!"stable".equals(agent.get("status").getAsString()) || id.equals("settings")) continue;
                if (!"checkbox".equals(agent.getAsJsonObject("activation").get("type").getAsString()))
                    throw new IOException("stable agent startup activation requires an explicit recipe: " + id);
                boolean enabled = current.desired(id); Set<String> targets = new HashSet<>();
                for (JsonElement targetValue : agent.getAsJsonArray("targets")) {
                    JsonObject target = targetValue.getAsJsonObject(); String process = target.get("process").getAsString(), platform = target.get("platform").getAsString();
                    if (!Set.of("android", "qnx").contains(platform) || !targets.add(platform + ":" + process)) throw new IOException("candidate startup target differs");
                    boolean nativeTarget = platform.equals("qnx");
                    if (enabled && nativeTarget) {
                        if (nativeObservation == null) throw new IOException("BLOCKED: exact current authenticated QNX identity is required");
                        nativeObservation.requireCurrent(current, id, process);
                    }
                    boolean running = nativeTarget ? enabled : processes.contains(process); JsonObject observed = null;
                    if (!nativeTarget) for (JsonObject row : current.targets(id)) if (process.equals(row.get("process").getAsString())) {
                        if (observed != null) throw new IOException("ambiguous startup target publication"); observed = row;
                    }
                    if (enabled && running && !nativeTarget && (observed == null || !"active".equals(observed.get("state").getAsString())))
                        throw new Pending("enabled running target is not active while hidden: " + id + "/" + platform + ":" + process);
                    JsonObject row = new JsonObject(); row.addProperty("agent", id); row.addProperty("target", platform + ":" + process);
                    row.addProperty("desired", enabled); row.addProperty("running", running);
                    row.addProperty("observedState", nativeTarget ? enabled ? "applied-with-current-native-identity" : "disabled-not-observed" : observed == null ? "not-published" : observed.get("state").getAsString());
                    row.addProperty("functionalAcceptance", false); evidence.add(row);
                }
                if (targets.isEmpty()) throw new IOException("stable startup target inventory is empty");
            }
        } catch (RuntimeException malformed) { throw new IOException("malformed candidate startup inventory", malformed); }
        if (evidence.isEmpty()) throw new IOException("candidate startup checkbox inventory is empty");
        return evidence;
    }
}
