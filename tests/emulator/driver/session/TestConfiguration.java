package ru.voboost.voyemu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/** Resolves explicit emulator inputs without acquiring or changing a session. */
public record TestConfiguration(Path voyemu, Path machine, String profile, String scenario) {
    public static TestConfiguration resolve(Path shared, Path local, Map<String, String> overrides) throws IOException {
        try {
            JsonObject data = read(shared, false);
            JsonObject mapping = read(local, true);
            keys(data, Set.of("machines", "roles"));
            keys(mapping, Set.of("voyemu", "machines"));
            String selected = overrides.get("machineName");
            JsonObject machineData = new JsonObject();
            if (selected != null) {
                if (!selected.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")) throw new IllegalArgumentException("invalid machineName");
                JsonElement declared = data.get("machines");
                if (declared == null || !declared.isJsonArray()) throw new IllegalArgumentException("shared machines must be an array");
                int matches = 0;
                for (JsonElement item : declared.getAsJsonArray()) {
                    if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("invalid machine identifier");
                    if (selected.equals(item.getAsString())) matches++;
                }
                if (matches != 1) throw new IllegalArgumentException("missing or duplicate shared declaration for " + selected);
                if (!overrides.containsKey("machine")) machineData = selected(mapping, selected, "local mapping");
                keys(machineData, Set.of("path"));
            }
            Path binary = absolute(value(overrides, "voyemu", mapping, "voyemu"));
            Path machine = absolute(value(overrides, "machine", machineData, "path"));
            String scenario = overrides.get("scenario");
            if (scenario == null || !Set.of("fresh", "resume").contains(scenario)) throw new IllegalArgumentException("scenario must be fresh or resume");
            if (!Files.isRegularFile(binary) || !Files.isExecutable(binary)) throw new IllegalArgumentException("Voyemu executable unavailable");
            if (!Files.isDirectory(machine)) throw new IllegalArgumentException("machine unavailable");
            Path canonicalMachine = machine.toRealPath();
            JsonObject manifest = JsonParser.parseString(Files.readString(canonicalMachine.resolve("runtime.json"))).getAsJsonObject();
            String profile = string(manifest, "profile");
            if (!"portable-guest-runtime".equals(string(manifest, "kind")) || profile == null || !profile.matches("[a-z0-9][a-z0-9._-]{0,127}"))
                throw new IllegalArgumentException("prepared machine profile differs");
            return new TestConfiguration(binary, canonicalMachine, profile, scenario);
        } catch (RuntimeException e) { throw new IOException("BLOCKED: invalid Voyemu inputs: " + e.getMessage(), e); }
    }
    private static JsonObject read(Path path, boolean optional) throws IOException {
        if (optional && !Files.exists(path)) return new JsonObject();
        JsonElement value = JsonParser.parseString(Files.readString(path));
        if (!value.isJsonObject()) throw new IllegalArgumentException("configuration must be an object: " + path);
        return value.getAsJsonObject();
    }
    private static JsonObject selected(JsonObject data, String name, String description) {
        JsonElement machines = data.get("machines");
        if (machines == null || !machines.isJsonObject() || !machines.getAsJsonObject().has(name)
                || !machines.getAsJsonObject().get(name).isJsonObject())
            throw new IllegalArgumentException("missing " + description + " for " + name);
        return machines.getAsJsonObject().getAsJsonObject(name);
    }
    public void verifyClusterImages() throws IOException {
        JsonObject runtime = read(machine.resolve("runtime.json"), false);
        JsonElement qnx = runtime.get("qnx");
        if (qnx == null || !qnx.isJsonObject()) throw new IOException("Prepared machine has no QNX configuration");
        JsonElement images = qnx.getAsJsonObject().get("images");
        if (images == null || !images.isJsonObject() || images.getAsJsonObject().isEmpty()) {
            throw new IOException("Prepared machine has no QNX images");
        }
        for (var image : images.getAsJsonObject().entrySet()) {
            String filename = string(image.getValue().getAsJsonObject(), "file");
            if (filename == null) throw new IOException("Missing QNX image path: " + image.getKey());
            Path file = machine.resolve(filename).normalize();
            if (!file.startsWith(machine) || !Files.isRegularFile(file) || Files.size(file) == 0) {
                throw new IOException("Prepared QNX image unavailable: " + image.getKey());
            }
        }
    }

    public record Selection(String machineName, TestConfiguration configuration) {}

    public static Selection forRole(String role) throws IOException {
        return resolveRole(configPath("voyemuConfig"), configPath("voyemuLocalConfig"), overrides(), role);
    }

    static Selection resolveRole(Path shared, Path local, Map<String, String> inputs, String role) throws IOException {
        if (!Set.of("installer", "agents").contains(role)) throw new IOException("Unknown emulator role " + role);
        var overrides = new java.util.HashMap<>(inputs);
        JsonObject data = read(shared, false);
        JsonObject mapping = read(local, true);
        JsonObject roles = data.getAsJsonObject("roles");
        String name = overrides.get("machineName");
        if (name == null && roles != null) {
            keys(roles, Set.of("installer", "agents"));
            name = string(roles, role);
            String other = string(roles, "installer".equals(role) ? "agents" : "installer");
            if (name == null) name = other;
            JsonObject paths = mapping.getAsJsonObject("machines");
            if (!overrides.containsKey("machine") && other != null && paths != null
                    && !paths.has(name) && paths.has(other)) name = other;
        }
        if (name == null && data.getAsJsonArray("machines").size() == 1) {
            name = data.getAsJsonArray("machines").get(0).getAsString();
        }
        if (name == null) throw new IOException("No emulator machine selected for " + role);
        overrides.put("machineName", name);
        return new Selection(name, resolve(shared, local, overrides));
    }

    public static TestConfiguration fromSystemProperties() throws IOException {
        return resolve(configPath("voyemuConfig"), configPath("voyemuLocalConfig"), overrides());
    }

    private static Path configPath(String key) {
        return Path.of(System.getProperty(key));
    }

    private static java.util.HashMap<String, String> overrides() {
        var values = new java.util.HashMap<String, String>();
        for (String key : Set.of("voyemu", "machine", "machineName", "scenario")) {
            String value = System.getProperty(key);
            if (value != null) values.put(key, value);
        }
        return values;
    }

    private static String value(Map<String, String> overrides, String key, JsonObject data, String field) {
        String value = overrides.containsKey(key) ? overrides.get(key) : string(data, field);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("missing " + key);
        return value;
    }
    private static String string(JsonObject data, String key) {
        JsonElement value = data.get(key);
        if (value == null) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("expected string: " + key);
        return value.getAsString();
    }
    private static Path absolute(String value) {
        Path path = Path.of(value);
        if (!path.isAbsolute()) throw new IllegalArgumentException("absolute path required: " + value);
        return path;
    }
    private static void keys(JsonObject data, Set<String> allowed) {
        if (!allowed.containsAll(data.keySet())) throw new IllegalArgumentException("unknown configuration key");
    }
}
