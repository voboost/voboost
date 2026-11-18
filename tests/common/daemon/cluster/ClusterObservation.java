package ru.voboost.tests.agents;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Current native startup availability from the existing owner-fenced, receipt-verified Cluster publication. */
public final class ClusterObservation {
    public static final class Pending extends IOException { public Pending(String message) { super(message); } }
    private static final String PW = "vehicle-drive-pedestrian-warning";
    private static final String PW_LIGHT = PW + "/light";
    private static final BigInteger MAX_REVISION = BigInteger.valueOf(9007199254740991L);
    private record Process(String pid, String birth, String executable, String endpoint, String lifecycle) {}
    private final String publisher, boot, daemon, generation, desired;
    private final BigInteger revision;
    private final Map<String, Process> targets;
    private final JsonObject snapshot;
    private final int advances;
    private final boolean pwRestored;
    private ClusterObservation(String publisher, String boot, String daemon, String generation, String desired,
            BigInteger revision, Map<String, Process> targets, JsonObject snapshot, int advances, boolean pwRestored) {
        this.publisher = publisher; this.boot = boot; this.daemon = daemon; this.generation = generation; this.desired = desired;
        this.revision = revision; this.targets = Map.copyOf(targets); this.snapshot = snapshot.deepCopy(); this.advances = advances;this.pwRestored=pwRestored;
    }
    public static boolean required(JsonObject catalog, JsonObject expected) throws IOException {
        return !requiredTargets(catalog, expected).isEmpty();
    }
    private static Set<String> requiredTargets(JsonObject catalog, JsonObject expected) throws IOException {
        Set<String> result = new HashSet<>();
        try {
            for (JsonObject agent : AgentSettings.checkboxes(catalog).values()) {
                String id = text(agent, "id");
                if (!expected.has(id) || !expected.getAsJsonObject(id).get("enabled").getAsBoolean()) continue;
                for (JsonElement value : agent.getAsJsonArray("targets")) {
                    JsonObject target = value.getAsJsonObject();
                    if ("qnx".equals(text(target, "platform")) && !result.add(id + "/" + text(target, "process")))
                        throw new IOException("duplicate required native target");
                }
            }
        } catch (RuntimeException malformed) { throw new IOException("malformed required native startup targets", malformed); }
        return Set.copyOf(result);
    }
    public static ClusterObservation read(RuntimeObservation current, JsonObject catalog, JsonObject expected) throws IOException {
        return read(current, catalog, expected, false);
    }
    private static ClusterObservation read(RuntimeObservation current, JsonObject catalog, JsonObject expected,
            boolean pwRestore) throws IOException {
        return read(current, catalog, expected, pwRestore, false, false);
    }

    /** Observe persisted PW delivery before admitting a single new QNX startup in the fixture. */
    public static boolean pwNeedsStartup(RuntimeObservation current, JsonObject catalog, JsonObject expected) throws IOException {
        return read(current, catalog, expected, false, true, true).targets.isEmpty();
    }

    private static ClusterObservation read(RuntimeObservation current, JsonObject catalog, JsonObject expected,
            boolean pwRestore, boolean pwDeliveryOnly, boolean pwIntentPending) throws IOException {
        if (!expected.equals(AgentSettings.snapshot(catalog, current)))
            throw new IOException("native readiness changed the authoritative agent configuration");
        Set<String> required = requiredTargets(catalog, expected);
        if (required.isEmpty()) throw new IOException("no enabled native startup target was selected");
        if (pwDeliveryOnly && !required.equals(Set.of(PW_LIGHT)))
            throw new IOException("PW delivery requires only the selected light target");
        try {
            JsonObject runtime = current.runtime();
            if (!runtime.has("cluster") || !runtime.get("cluster").isJsonObject()) throw new Pending("current native Cluster snapshot unavailable");
            JsonObject cluster = runtime.getAsJsonObject("cluster");
            if (!cluster.get("schemaVersion").isJsonPrimitive() || !cluster.getAsJsonPrimitive("schemaVersion").isNumber()
                    || !"1".equals(cluster.get("schemaVersion").getAsString()) || !"cluster".equals(text(cluster, "domain")))
                throw new IOException("native startup snapshot schema/domain differs");
            String delivery = text(cluster, "deliveryState");
            switch (delivery) {
                case "idle" -> { }
                case "pending", "unavailable" -> throw new Pending("native delivery is " + delivery);
                case "failed", "setup-required" -> throw new IOException("native delivery is " + delivery);
                default -> throw new IOException("invalid native delivery state: " + delivery);
            }
            positive(cluster, "snapshotRevision", MAX_REVISION);
            BigInteger revision = positive(cluster, "observationRevision", MAX_REVISION);
            String boot = text(cluster, "bootId"), daemon = protocolIdentity(cluster, "daemonEpoch");
            if (boot.length() > 128) throw new IOException("native boot identity exceeds protocol bound");
            String generation = protocolIdentity(cluster, "catalogSha256") + ":" + protocolIdentity(cluster, "generationId");
            BigInteger desiredRevision = pwDeliveryOnly
                    ? positive(cluster, "desiredRevision", MAX_REVISION) : nonnegative(cluster, "desiredRevision", MAX_REVISION);
            String desired = desiredRevision + ":" + protocolIdentity(cluster, "desiredSha256");
            Map<String, Process> processes = new HashMap<>(); Map<String, String> processNames = new HashMap<>(); Set<String> agents = new HashSet<>();
            boolean awaitingPwStartup = false;
            for (JsonElement item : cluster.getAsJsonArray("agents")) {
                JsonObject agent = item.getAsJsonObject(); String id = text(agent, "id");
                if (!agents.add(id)) throw new IOException("duplicate native startup agent");
                if (!expected.has(id)) continue;
                JsonObject wanted = expected.getAsJsonObject(id);
                if (!agent.has("enabled") || !agent.get("enabled").isJsonPrimitive() || !agent.getAsJsonPrimitive("enabled").isBoolean()
                        || agent.get("enabled").getAsBoolean() != wanted.get("enabled").getAsBoolean()) {
                    if (pwIntentPending && PW.equals(id) && agent.has("enabled")
                            && agent.get("enabled").isJsonPrimitive() && agent.getAsJsonPrimitive("enabled").isBoolean())
                        throw new Pending("QNX has not confirmed whether the PW agent is enabled");
                    throw new IOException("native startup desired enabled state differs from the prior fixture");
                }
                JsonObject nativeOptions = agent.getAsJsonObject("options");
                JsonObject signedAgent = null;
                for (JsonElement candidate : catalog.getAsJsonArray("agents")) if (id.equals(text(candidate.getAsJsonObject(), "id"))) signedAgent = candidate.getAsJsonObject();
                if (signedAgent == null) throw new IOException("native startup agent is not in the candidate");
                JsonObject options = new JsonObject();
                if (signedAgent.has("options")) for (JsonElement optionValue : signedAgent.getAsJsonArray("options")) {
                    JsonObject option = optionValue.getAsJsonObject(); String key = text(option, "key");
                    JsonElement value = wanted.getAsJsonObject("options").get(key);
                    if (value == null) value = option.get("default");
                    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || options.has(key))
                        throw new IOException("native option projection unavailable");
                    options.add(key, value.deepCopy());
                }
                if (PW.equals(id) && options.has("intent") && "observe".equals(options.get("intent").getAsString()))
                    options.addProperty("intent", ru.voboost.voyemu.VehicleArmed.pedestrianIntent(current));
                if (!options.equals(nativeOptions)) {
                    if (pwIntentPending && PW.equals(id) && onlyIntentDiffers(options, nativeOptions))
                        throw new Pending("QNX has not confirmed the current PW intent");
                    throw new IOException("native startup options differ from the prior fixture");
                }
                JsonElement aggregate = agent.get("process");
                JsonElement expectedAggregate = agent.getAsJsonArray("targets").size() == 1
                    ? agent.getAsJsonArray("targets").get(0).getAsJsonObject().get("process") : com.google.gson.JsonNull.INSTANCE;
                if (aggregate == null || !aggregate.equals(expectedAggregate)) throw new IOException("native aggregate process differs from exact targets");
                Set<String> seenTargets = new HashSet<>();
                for (JsonElement targetValue : agent.getAsJsonArray("targets")) {
                    JsonObject target = targetValue.getAsJsonObject(); String key = id + "/" + text(target, "targetProcess");
                    if (!seenTargets.add(key) || !id.equals(text(target, "id"))) throw new IOException("duplicate or foreign native startup target");
                    if (!required.contains(key)) continue;
                    if (pwDeliveryOnly && PW_LIGHT.equals(key)
                            && "waiting-process".equals(text(agent, "state")) && "waiting-process".equals(text(target, "state"))
                            && "startup-controller-unavailable".equals(text(target, "reason"))
                            && target.has("process") && target.get("process").isJsonNull()) {
                        awaitingPwStartup = true;
                        continue;
                    }
                    if (!pwRestore && PW_LIGHT.equals(key)) {
                        String state = text(target, "state");
                        if (Set.of("failed", "quarantined", "reload-required").contains(state))
                            throw new IOException("PW light " + state + ": " + text(target, "reason"));
                    }
                    boolean applied = "applied".equals(text(agent, "state")) && "applied".equals(text(target, "state"))
                        && !(pwRestore && "vehicle-drive-pedestrian-warning/light".equals(key));
                    boolean restored = pwRestore && "vehicle-drive-pedestrian-warning/light".equals(key)
                        && "waiting-ui".equals(text(agent, "state")) && "waiting-ui".equals(text(target, "state"))
                        && restoreReason(text(target,"reason"));
                    if ((!applied && !restored)
                            || !target.has("process") || !target.get("process").isJsonObject())
                        throw new Pending("enabled native target is not applied with a current process: " + key);
                    JsonObject process = target.getAsJsonObject("process");
                    Process identity = new Process(positive(process, "pid", BigInteger.valueOf(Integer.MAX_VALUE)).toString(),
                        protocolIdentity(process, "birthIdentity"), protocolIdentity(process, "executableSha256"), protocolIdentity(process, "endpointEpoch"), positive(process, "lifecycleGeneration", MAX_REVISION).toString());
                    String targetName = text(target, "targetProcess"), priorName = processNames.putIfAbsent(identity.pid(), targetName);
                    if (priorName != null && !priorName.equals(targetName)) throw new IOException("one native PID cannot witness different target processes");
                    if (processes.putIfAbsent(key, identity) != null) throw new IOException("duplicate native startup identity");
                }
            }
            if (!awaitingPwStartup && !processes.keySet().equals(required))
                throw new Pending("native startup target inventory is incomplete");
            return new ClusterObservation(current.publisher(), boot, daemon, generation, desired, revision, processes, cluster, 0, false);
        } catch (RuntimeException malformed) { throw new IOException("malformed typed native startup snapshot", malformed); }
    }
    public ClusterObservation advance(RuntimeObservation current, JsonObject catalog, JsonObject expected) throws IOException {
        return advance(current, catalog, expected, false);
    }
    public static ClusterObservation readPwStartup(RuntimeObservation current, JsonObject catalog, JsonObject expected) throws IOException {
        return read(current, catalog, expected, false, false, true);
    }

    private static boolean onlyIntentDiffers(JsonObject expected, JsonObject observed) {
        if (observed == null) return false;
        JsonObject prior = observed.deepCopy();
        JsonObject wanted = expected.deepCopy();
        Set<String> intents = Set.of("observe", "on", "off");
        return intents.contains(textOrEmpty(prior.remove("intent"))) && intents.contains(textOrEmpty(wanted.remove("intent")))
                && prior.equals(wanted);
    }

    private static String textOrEmpty(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }

    public ClusterObservation advancePwSelection(RuntimeObservation current, JsonObject catalog, JsonObject expected) throws IOException {
        if (!targets.containsKey(PW_LIGHT)) throw new IOException("current PW light observation required");
        if (!publisher.equals(current.publisher())) throw new IOException("native startup Android owner changed");
        return verifyNext(readPwStartup(current, catalog, expected), false, true, true);
    }

    public ClusterObservation advancePwStartup(RuntimeObservation current, JsonObject catalog, JsonObject expected) throws IOException {
        if (!targets.containsKey(PW_LIGHT)) throw new IOException("current PW light observation required");
        ClusterObservation next = readPwStartup(current, catalog, expected);
        if (publisher.equals(next.publisher) && generation.equals(next.generation) && desired.equals(next.desired)
                && (!boot.equals(next.boot) || !daemon.equals(next.daemon))) {
            return next;
        }
        return verifyNext(next, false, true, false);
    }

    private static boolean restoreReason(String reason) {
        return Set.of("PW visual pass-through: live state unknown","PW visual pass-through: actual enabled").contains(reason);
    }
    private boolean pwApplied()throws IOException {
        for(JsonElement value:snapshot.getAsJsonArray("agents")) {
            JsonObject agent=value.getAsJsonObject();if(!"vehicle-drive-pedestrian-warning".equals(text(agent,"id")))continue;
            for(JsonElement item:agent.getAsJsonArray("targets")) {
                JsonObject target=item.getAsJsonObject();if("light".equals(text(target,"targetProcess")))return "applied".equals(text(agent,"state"))&&"applied".equals(text(target,"state"));
            }
        }
        return false;
    }
    /** Fresh restore-success ACKs anchored to this same process's preceding Applied state.
     * The caller must independently require actual CAN ON and fixed-path root armed=false. */
    public ClusterObservation advancePwRestore(RuntimeObservation current, JsonObject catalog, JsonObject expected) throws IOException {
        if (!targets.containsKey("vehicle-drive-pedestrian-warning/light") || (!pwRestored && !pwApplied()))
            throw new IOException("an applied PW light observation must precede restoration");
        return advance(current, catalog, expected, true);
    }
    private ClusterObservation advance(RuntimeObservation current, JsonObject catalog, JsonObject expected,
            boolean pwRestore) throws IOException {
        return observe(current, catalog, expected, pwRestore, true);
    }

    /** Verify the current applied PW state without requiring a new courier response. */
    public ClusterObservation confirmPw(RuntimeObservation current, JsonObject catalog, JsonObject expected) throws IOException {
        if (!targets.containsKey(PW_LIGHT)) throw new IOException("current PW light observation required");
        if (!publisher.equals(current.publisher())) throw new IOException("native startup Android owner changed");
        return observe(current, catalog, expected, false, false);
    }

    private ClusterObservation observe(RuntimeObservation current, JsonObject catalog, JsonObject expected,
            boolean pwRestore, boolean fresh) throws IOException {
        ClusterObservation next = read(current, catalog, expected, pwRestore);
        return verifyNext(next, pwRestore, fresh, false);
    }

    private ClusterObservation verifyNext(ClusterObservation next, boolean pwRestore, boolean fresh, boolean selection) throws IOException {
        if (!publisher.equals(next.publisher)) throw new IOException("native startup Android owner changed");
        if (!boot.equals(next.boot) || !daemon.equals(next.daemon) || !generation.equals(next.generation))
            throw new IOException("native startup boot/daemon/generation changed");
        if (!selection && !desired.equals(next.desired)) throw new IOException("native startup desired authority changed");
        if (selection && positive(next.snapshot, "desiredRevision", MAX_REVISION)
                .compareTo(nonnegative(snapshot, "desiredRevision", MAX_REVISION)) <= 0)
            throw new IOException("PW selection did not advance the native desired revision");
        if (!targets.equals(next.targets)) throw new IOException("native startup process lifetime or endpoint changed");
        int relation = next.revision.compareTo(revision);
        if (relation < 0) throw new IOException("native startup observation revision regressed");
        if (relation == 0 && fresh) throw new Pending("cached native observation cannot prove a new response");
        int observedAdvances = relation > 0 ? advances + 1 : advances;
        return new ClusterObservation(next.publisher, next.boot, next.daemon, next.generation, next.desired,
            next.revision, next.targets, next.snapshot, observedAdvances, pwRestore);
    }
    public void requireCurrentPw(RuntimeObservation current) throws IOException {
        if (!publisher.equals(current.publisher()) || !snapshot.equals(current.runtime().get("cluster"))
                || !targets.containsKey(PW_LIGHT))
            throw new IOException("current owner-bound PW light receipt is required");
    }

    public String publisher() { return publisher; }
    public int advances() { return advances; }
    public void requireCurrent(RuntimeObservation current, String agent, String process) throws IOException {
        if (advances < 2 || !publisher.equals(current.publisher()) || !snapshot.equals(current.runtime().get("cluster"))
                || !targets.containsKey(agent + "/" + process))
            throw new IOException("two current owner-bound native observation advances are required");
    }
    public JsonObject evidence() {
        JsonObject value = new JsonObject(); value.addProperty("publisher", publisher); value.addProperty("nativeBoot", boot);
        value.addProperty("nativeDaemonEpoch", daemon); value.addProperty("nativeGeneration", generation);
        value.addProperty("observationRevision", revision); value.addProperty("observedAdvances", advances);
        value.addProperty("functionalAcceptance", false); value.add("snapshot", snapshot.deepCopy()); return value;
    }
    /** Format of existing signed protocol identities; no file hashing or firmware compatibility decision. */
    private static String protocolIdentity(JsonObject object, String key) throws IOException {
        String identity = text(object, key);
        if (!identity.matches("[0-9a-f]{64}")) throw new IOException("native protocol identity format differs: " + key);
        return identity;
    }
    private static String text(JsonObject object, String key) throws IOException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()
                || value.getAsString().length() > 256 || value.getAsString().chars().anyMatch(c -> c < 32 || c > 126))
            throw new IOException("bounded native startup identity required: " + key);
        return value.getAsString();
    }
    private static BigInteger nonnegative(JsonObject object, String key, BigInteger maximum) throws IOException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("[0-9]{1,20}"))
            throw new IOException("native startup integer required: " + key);
        BigInteger result = new BigInteger(value.getAsString());
        if (result.compareTo(maximum) > 0) throw new IOException("native startup integer exceeds bound: " + key);
        return result;
    }
    private static BigInteger positive(JsonObject object, String key, BigInteger maximum) throws IOException {
        BigInteger value = nonnegative(object, key, maximum);
        if (value.signum() == 0) throw new Pending("positive native startup identity unavailable: " + key);
        return value;
    }
}
