package ru.voboost.tests;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Expands the product requirement plan against the actual candidate catalog. */
public final class AcceptancePlan {
    public enum Layer { INTEGRATION, EMULATOR }
    public record Key(String id, String target) {
        public Key {
            if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9/-]{0,159}")
                    || target == null || target.isBlank() || target.length() > 256)
                throw new IllegalArgumentException("invalid acceptance case identity");
        }
    }
    public record Case(Key key, Layer layer, String observable, boolean registered) {}
    private final List<Case> cases;
    private AcceptancePlan(List<Case> cases) { this.cases = List.copyOf(cases); }
    public List<Case> cases() { return cases; }
    public List<Case> layer(Layer layer) { return cases.stream().filter(item -> item.layer() == layer).toList(); }

    public static AcceptancePlan read(Path plan, Path catalog) throws IOException {
        return parse(RunFiles.read(plan), RunFiles.read(catalog));
    }

    public static AcceptancePlan parse(JsonObject plan, JsonObject catalog) throws IOException {
        try {
            if (!number(plan, "schemaVersion", "2") || !number(catalog, "schemaVersion", "2"))
                throw new IOException("acceptance plan and catalog schema 2 required");
            List<Case> cases = new ArrayList<>(); Set<Key> keys = new HashSet<>();
            Set<String> ids = new HashSet<>(); Set<Integer> numbers = new HashSet<>();
            for (JsonElement element : array(plan, "cases")) {
                JsonObject item = element.getAsJsonObject(); String id = text(item, "id");
                if (!ids.add(id)) throw new IOException("duplicate functional requirement");
                if (!item.has("number") || !item.get("number").isJsonPrimitive()
                        || !item.getAsJsonPrimitive("number").isNumber()
                        || !item.get("number").getAsString().matches("[1-9][0-9]{0,3}")
                        || !numbers.add(item.get("number").getAsInt())) throw new IOException("invalid functional case number");
                if (required(item)) add(cases, keys, new Case(new Key(id, "product"), Layer.EMULATOR, text(item, "observable"), true));
            }
            for (String group : List.of("integrationCases", "guiCases")) {
                for (JsonElement element : array(plan, group)) {
                    JsonObject item = element.getAsJsonObject(); String id = text(item, "id");
                    if (!ids.add(id)) throw new IOException("duplicate product requirement");
                    if (required(item)) add(cases, keys, new Case(new Key(id, "product"),
                        group.equals("integrationCases") ? Layer.INTEGRATION : Layer.EMULATOR, text(item, "observable"), true));
                }
            }
            Map<String, String> agents = new HashMap<>();
            for (JsonElement element : array(plan, "agentCases")) {
                JsonObject item = element.getAsJsonObject(); String id = text(item, "id");
                if (!required(item) || agents.putIfAbsent(id, text(item, "observable")) != null)
                    throw new IOException("agent requirements must be required and unique");
            }
            Set<String> catalogIds = new HashSet<>();
            for (JsonElement element : array(catalog, "agents")) {
                JsonObject agent = element.getAsJsonObject(); String id = text(agent, "id");
                if (!id.matches("[a-z][a-z0-9-]{0,127}") || !catalogIds.add(id)) throw new IOException("invalid candidate agent identity");
                if (!"stable".equals(text(agent, "status"))) continue;
                boolean registered = agents.containsKey(id);
                String observable = registered ? agents.get(id) : "Required candidate agent has no registered scenario";
                int targets = 0;
                for (JsonElement value : array(agent, "targets")) {
                    JsonObject target = value.getAsJsonObject(); String platform = text(target, "platform");
                    String process = text(target, "process");
                    if (!Set.of("android", "qnx").contains(platform) || !process.matches("[A-Za-z0-9_.:-]{1,200}"))
                        throw new IOException("invalid candidate target identity");
                    add(cases, keys, new Case(new Key("agent/" + id, platform + ":" + process), Layer.EMULATOR, observable, registered));
                    ++targets;
                }
                if (targets == 0) throw new IOException("stable agent has no targets");
            }
            if (cases.isEmpty()) throw new IOException("empty required acceptance plan");
            return new AcceptancePlan(cases);
        } catch (IllegalStateException | IllegalArgumentException error) {
            throw new IOException("invalid acceptance input", error);
        }
    }

    private static void add(List<Case> cases, Set<Key> keys, Case item) throws IOException {
        if (!keys.add(item.key())) throw new IOException("duplicate acceptance case/target");
        cases.add(item);
    }
    private static Iterable<JsonElement> array(JsonObject value, String key) throws IOException {
        if (!value.has(key) || !value.get(key).isJsonArray()) throw new IOException("required array missing: " + key);
        return value.getAsJsonArray(key);
    }
    private static boolean required(JsonObject value) throws IOException {
        if (!value.has("required") || !value.get("required").isJsonPrimitive() || !value.getAsJsonPrimitive("required").isBoolean())
            throw new IOException("required must be a boolean");
        return value.get("required").getAsBoolean();
    }
    private static boolean number(JsonObject value, String key, String expected) {
        return value.has(key) && value.get(key).isJsonPrimitive() && value.getAsJsonPrimitive(key).isNumber()
            && expected.equals(value.get(key).getAsString());
    }
    private static String text(JsonObject value, String key) throws IOException {
        if (!value.has(key) || !value.get(key).isJsonPrimitive() || !value.getAsJsonPrimitive(key).isString()
                || value.get(key).getAsString().isBlank()) throw new IOException("required text missing: " + key);
        return value.get(key).getAsString();
    }
}
