package ru.voboost.tests.agents;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Desired checkbox transitions from the candidate catalog; never an agent-effect oracle. */
public final class AgentSettings {
    private AgentSettings() {}
    public static Map<String, JsonObject> checkboxes(JsonObject catalog) throws IOException {
        Map<String, JsonObject> entries = new LinkedHashMap<>();
        try {
            for (JsonElement item : catalog.getAsJsonArray("agents")) {
                JsonObject agent = item.getAsJsonObject();
                if (!agent.has("activation") || !agent.get("activation").isJsonObject()
                        || !"checkbox".equals(agent.getAsJsonObject("activation").get("type").getAsString())
                        || !"stable".equals(agent.get("status").getAsString())) continue;
                String id = agent.get("id").getAsString();
                if (!id.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*") || entries.putIfAbsent(id, agent.deepCopy()) != null)
                    throw new IOException("candidate checkbox identity is invalid or duplicated");
            }
        } catch (RuntimeException malformed) { throw new IOException("candidate checkbox catalog is malformed", malformed); }
        if (entries.isEmpty()) throw new IOException("candidate stable checkbox inventory is empty");
        return java.util.Collections.unmodifiableMap(entries);
    }
    public static JsonObject snapshot(JsonObject catalog, RuntimeObservation runtime) throws IOException {
        JsonObject result = new JsonObject();
        for (String id : checkboxes(catalog).keySet()) result.add(id, runtime.desiredConfiguration(id));
        return result;
    }
    public static JsonObject transition(JsonObject catalog, JsonObject before, String id, boolean enabled) throws IOException {
        Map<String, JsonObject> entries = checkboxes(catalog);
        if (!before.keySet().equals(entries.keySet()) || !entries.containsKey(id)) throw new IOException("checkbox transition inventory differs");
        JsonObject result = before.deepCopy();
        for (String key : result.keySet()) {
            JsonObject value = result.getAsJsonObject(key);
            if (!value.keySet().equals(Set.of("enabled", "options")) || !value.get("enabled").isJsonPrimitive()
                    || !value.getAsJsonPrimitive("enabled").isBoolean() || !value.get("options").isJsonObject())
                throw new IOException("checkbox transition requires canonical desired configuration");
        }
        result.getAsJsonObject(id).addProperty("enabled", enabled);
        if (enabled) {
            JsonObject entry = entries.get(id);
            if (!entry.has("disables") || !entry.get("disables").isJsonArray()) throw new IOException("candidate conflict inventory unavailable");
            Set<String> seen = new java.util.HashSet<>();
            for (JsonElement value : entry.getAsJsonArray("disables")) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IOException("candidate conflict identity differs");
                String conflict = value.getAsString();
                if (conflict.equals(id) || !seen.add(conflict) || !result.has(conflict)) throw new IOException("candidate conflict inventory differs");
                result.getAsJsonObject(conflict).addProperty("enabled", false);
            }
        }
        return result;
    }
    public static JsonObject vehicleOnly(JsonObject catalog, JsonObject before) throws IOException {
        Set<String> enabled = Set.of("vehicle-drive-forced-ev", "vehicle-drive-pedestrian-warning");
        var entries = checkboxes(catalog);
        if (!entries.keySet().containsAll(enabled)) throw new IOException("candidate vehicle checkbox pair unavailable");
        JsonObject result = before.deepCopy();
        for (String id : entries.keySet()) result = transition(catalog, result, id, false);
        for (String id : enabled) result = transition(catalog, result, id, true);
        for (String id : entries.keySet())
            if (result.getAsJsonObject(id).get("enabled").getAsBoolean() != enabled.contains(id))
                throw new IOException("candidate conflicts prevent vehicle-only configuration");
        return result;
    }

    public static String title(JsonObject entry, String language) throws IOException {
        JsonObject titles = entry.has("title") && entry.get("title").isJsonObject() ? entry.getAsJsonObject("title") : new JsonObject();
        JsonElement value = titles.get(language);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank())
            throw new IOException("candidate checkbox title unavailable for observed language");
        return value.getAsString();
    }
}
