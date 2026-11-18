package ru.voboost.tests.agents;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** A current official daemon publication and persisted config, bound to one observed boot. */
public final class RuntimeObservation {
    public record Identity(String boot, String release, String manifest, String catalog, String generation) {}
    public static class Unavailable extends IOException {
        private Unavailable(String reason) { super(reason); }
    }
    public static final class Stale extends Unavailable {
        private final String publisher;
        private Stale(String publisher) {
            super("runtime publication is stale");
            this.publisher = publisher;
        }
        public String publisher() { return publisher; }
    }
    private final JsonObject config, runtime;
    private final String publisher, revision;
    private RuntimeObservation(JsonObject config, JsonObject runtime, String publisher, String revision) {
        this.config = config.deepCopy(); this.runtime = runtime.deepCopy(); this.publisher = publisher; this.revision = revision;
    }
    public static RuntimeObservation read(JsonObject observation, Identity identity) throws IOException {
        return read(observation, identity, null);
    }

    public static RuntimeObservation read(JsonObject observation, Identity identity, String expectedPublisher)
            throws IOException {
        return read(observation, identity, expectedPublisher, false);
    }

    public static RuntimeObservation readForAdmission(JsonObject observation, Identity identity,
            String expectedPublisher) throws IOException {
        return read(observation, identity, expectedPublisher, true);
    }

    private static RuntimeObservation read(JsonObject observation, Identity identity, String expectedPublisher,
            boolean admitStaleOwner) throws IOException {
        try {
            if (!number(observation, "schema", "2") || !"voboost".equals(text(observation, "source"))
                    || !"ready".equals(text(observation, "status")) || !identity.boot().equals(text(observation, "boot_id")))
                throw new IOException("official runtime observation scope differs");
            JsonObject data = object(observation, "data"), files = object(data, "files");
            JsonObject config = object(files, "config"), runtime = object(files, "runtime");
            JsonObject provenance = object(runtime, "provenance");
            if (temporarilyUnavailable(data, config, runtime, provenance))
                throw new Unavailable("official runtime publication temporarily unavailable");
            JsonObject publication = object(runtime, "data"), publisher = object(publication, "publisher");
            if (!"present".equals(text(data, "root")) || !"fixed-path-owner-mode-boot-fenced".equals(text(data, "root_proof"))
                    || !"present".equals(text(runtime, "status"))
                    || !identity.boot().equals(text(publisher, "bootId")) || !positive(publisher, "daemonPid") || !positive(publisher, "daemonStarttime")
                    || !identity.release().equals(text(publication, "releaseIdentity")) || !identity.manifest().equals(text(publication, "manifestSha256"))
                    || !identity.catalog().equals(text(publication, "catalogSha256")) || !identity.generation().equals(text(publication, "generationId"))
                    || !"ready".equals(text(publication, "state")) || !no(publication, "killed") || !no(publication, "panic"))
                throw new IOException("official live publisher does not match current candidate and boot");
            String owner = identity.boot() + ":" + publisher.get("daemonPid").getAsString() + ":" + publisher.get("daemonStarttime").getAsString();
            if (expectedPublisher != null && !expectedPublisher.equals(owner))
                throw new IOException("official runtime publisher changed during observation");
            if ((admitStaleOwner || expectedPublisher != null) && temporarilyStale(data, runtime, provenance))
                throw new Stale(owner);
            if (!yes(data, "runtime_current") || !yes(runtime, "current") || !yes(provenance, "current")
                    || !"same-boot-live-publisher".equals(text(provenance, "proof")))
                throw new IOException("official live publisher does not match current candidate and boot");
            JsonElement age = provenance.get("age_ms");
            if (age == null || !age.isJsonPrimitive() || !age.getAsJsonPrimitive().isNumber()
                    || !Double.isFinite(age.getAsDouble()) || age.getAsDouble() < 0 || age.getAsDouble() > 5000)
                throw new IOException("runtime publication freshness unavailable");
            if (configReadUnavailable(data, config))
                throw new Unavailable("official configuration temporarily unavailable during read");
            if (!"present".equals(text(config, "status")) || text(config, "revision").isBlank())
                throw new IOException("official configuration unavailable or invalid");
            if (!publication.has("injections") || !publication.get("injections").isJsonArray()) throw new IOException("runtime target rows unavailable");
            Set<String> keys = new java.util.HashSet<>();
            for (JsonElement value : publication.getAsJsonArray("injections")) {
                JsonObject row = value.getAsJsonObject();
                String id = text(row, "id"), process = text(row, "process"), state = text(row, "state");
                if (id.isBlank() || process.isBlank() || state.isBlank() || !keys.add(id + "\u0000" + process))
                    throw new IOException("runtime target identity is missing or duplicated");
            }
            return new RuntimeObservation(object(config, "data"), publication, owner, text(config, "revision"));
        } catch (RuntimeException malformed) { throw new IOException("malformed official runtime observation", malformed); }
    }
    private static boolean temporarilyStale(JsonObject data, JsonObject runtime, JsonObject provenance) {
        JsonElement age = provenance.get("age_ms");
        return no(data, "runtime_current") && no(data, "complete") && no(runtime, "current")
                && no(provenance, "current") && "stale".equals(text(provenance, "status"))
                && "historical-publication".equals(text(provenance, "proof"))
                && "publication-stale".equals(text(provenance, "reason"))
                && number(provenance, "max_age_ms", "5000")
                && age != null && age.isJsonPrimitive() && age.getAsJsonPrimitive().isNumber()
                && Double.isFinite(age.getAsDouble()) && age.getAsDouble() > 5000;
    }

    private static boolean temporarilyUnavailable(JsonObject data, JsonObject config, JsonObject runtime,
            JsonObject provenance) {
        if (!"present".equals(text(data, "root"))
                || !"fixed-path-owner-mode-boot-fenced".equals(text(data, "root_proof"))
                || !no(data, "runtime_current") || !no(data, "complete")
                || !"present".equals(text(config, "status"))
                || !"unavailable".equals(text(runtime, "status")) || !no(runtime, "current")
                || runtime.has("data") || !"unavailable".equals(text(provenance, "status"))
                || !"runtime-unavailable".equals(text(provenance, "reason"))
                || !runtime.has("errors") || !runtime.get("errors").isJsonArray()
                || runtime.getAsJsonArray("errors").size() != 1
                || !runtime.getAsJsonArray("errors").get(0).isJsonObject()) {
            return false;
        }
        JsonObject error = runtime.getAsJsonArray("errors").get(0).getAsJsonObject();
        return "PASSIVE_READ_UNAVAILABLE".equals(text(error, "code"))
                && "metadata".equals(text(error, "stage")) && "3".equals(text(error, "check"));
    }

    private static boolean configReadUnavailable(JsonObject data, JsonObject config) {
        if (!no(data, "complete") || !config.keySet().equals(Set.of("status", "errors"))
                || !"unavailable".equals(text(config, "status")) || !config.get("errors").isJsonArray()
                || config.getAsJsonArray("errors").size() != 1
                || !config.getAsJsonArray("errors").get(0).isJsonObject()) return false;
        JsonObject error = config.getAsJsonArray("errors").get(0).getAsJsonObject();
        return error.keySet().equals(Set.of("check", "code", "errno", "stage"))
                && "PASSIVE_READ_UNAVAILABLE".equals(text(error, "code"))
                && "read".equals(text(error, "stage")) && "14".equals(text(error, "check"))
                && error.get("errno").isJsonNull();
    }

    public boolean desired(String id) throws IOException {
        JsonObject agents = object(config, "agents");
        if (!agents.has(id)) return false;
        JsonObject entry = object(agents, id);
        if (!yes(entry, "enabled") && !no(entry, "enabled")) throw new IOException("persisted desired value is not boolean");
        return yes(entry, "enabled");
    }
    public JsonObject desiredConfiguration(String id) throws IOException {
        JsonObject agents = object(config, "agents");
        if (!agents.has(id)) {
            JsonObject absent = new JsonObject(); absent.addProperty("enabled", false); absent.add("options", new JsonObject()); return absent;
        }
        JsonObject entry = object(agents, id);
        if (!entry.keySet().equals(Set.of("enabled", "options")) || (!yes(entry, "enabled") && !no(entry, "enabled"))
                || !entry.get("options").isJsonObject()) throw new IOException("canonical agent configuration unavailable: " + id);
        return entry.deepCopy();
    }
    /** Android injection rows only; native Cluster targets have a separate authenticated envelope. */
    public List<JsonObject> targets(String id) {
        List<JsonObject> rows = new ArrayList<>();
        for (JsonElement value : runtime.getAsJsonArray("injections")) {
            JsonObject row = value.getAsJsonObject();
            if (id.equals(text(row, "id"))) rows.add(row.deepCopy());
        }
        return List.copyOf(rows);
    }
    public void requireDeferredFrom(RuntimeObservation before) throws IOException {
        if (!runningStartup().equals(before.runningStartup())) throw new IOException("startup selection changed the effective mode");
        requireRetainedTargetsFrom(before);
    }
    public void requireRetainedTargetsFrom(RuntimeObservation before) throws IOException {
        if (!publisher.equals(before.publisher)) throw new IOException("startup selection replaced the live publisher");
        for (JsonElement value : before.runtime.getAsJsonArray("injections")) {
            JsonObject prior = value.getAsJsonObject();
            if (!"active".equals(text(prior, "state"))) continue;
            JsonObject current = null;
            for (JsonElement next : runtime.getAsJsonArray("injections")) {
                JsonObject row = next.getAsJsonObject();
                if (text(prior, "id").equals(text(row, "id")) && text(prior, "process").equals(text(row, "process"))) current = row;
            }
            if (current == null || !"active".equals(text(current, "state")) || !positive(prior, "lifecycleGeneration")
                    || !positive(current, "lifecycleGeneration")
                    || !prior.get("lifecycleGeneration").getAsString().equals(current.get("lifecycleGeneration").getAsString()))
                throw new IOException("startup selection deactivated or replaced an existing active injection");
        }
    }
    public String startup() throws IOException {
        String mode = text(config, "startup");
        if (!Set.of("interface", "hidden", "none").contains(mode)) throw new IOException("persisted startup mode unavailable");
        return mode;
    }
    public String runningStartup() throws IOException {
        String mode = text(runtime, "startupMode");
        if (!Set.of("interface", "hidden", "none").contains(mode)) throw new IOException("live startup mode unavailable");
        return mode;
    }
    public String publisher() { return publisher; }
    public String revision() { return revision; }
    public JsonObject runtime() { return runtime.deepCopy(); }
    private static JsonObject object(JsonObject value, String key) throws IOException {
        if (!value.has(key) || !value.get(key).isJsonObject()) throw new IOException("runtime observation object missing: " + key);
        return value.getAsJsonObject(key);
    }
    private static String text(JsonObject value, String key) {
        return value.has(key) && value.get(key).isJsonPrimitive() && value.getAsJsonPrimitive(key).isString() ? value.get(key).getAsString() : "";
    }
    private static boolean number(JsonObject value, String key, String expected) {
        return value.has(key) && value.get(key).isJsonPrimitive() && value.getAsJsonPrimitive(key).isNumber() && expected.equals(value.get(key).getAsString());
    }
    private static boolean positive(JsonObject value, String key) {
        JsonElement item = value.get(key);
        if (item == null || !item.isJsonPrimitive() || !item.getAsString().matches("[1-9][0-9]{0,19}")) return false;
        return new BigInteger(item.getAsString()).bitLength() <= 64;
    }
    private static boolean yes(JsonObject value, String key) { return bool(value, key, true); }
    private static boolean no(JsonObject value, String key) { return bool(value, key, false); }
    private static boolean bool(JsonObject value, String key, boolean expected) {
        return value.has(key) && value.get(key).isJsonPrimitive() && value.getAsJsonPrimitive(key).isBoolean() && value.get(key).getAsBoolean() == expected;
    }
}
