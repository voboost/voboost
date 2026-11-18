package ru.voboost.tests.installer.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import ru.voboost.tests.HostProcess;
import ru.voboost.tests.RunFiles;

/** Test-only native GUI observation. A successful AX action is not device-operation acceptance. */
public final class MacGui implements AutoCloseable {
    interface Backend extends AutoCloseable {
        JsonObject exchange(JsonObject request, Duration timeout) throws Exception;
        default JsonObject receive(Duration timeout) throws Exception { throw new IOException("receive unsupported"); }
        default ProcessHandle process() { throw new IllegalStateException("no native process"); }
        @Override default void close() throws Exception {}
    }
    public static final class Blocked extends IOException { Blocked(String reason) { super("BLOCKED: native GUI " + reason); } }
    public static final class Unknown extends IOException { Unknown(String reason, Throwable cause) { super("UNKNOWN: native GUI " + reason, cause); } }
    private static final Set<String> NODE_FIELDS = Set.of("nodeToken", "parent", "role", "subrole", "title", "description", "identifier", "value", "enabled", "frame", "redacted");
    private final Path work;
    private final String owner, started;
    private final int pid;
    private final Backend backend;
    private final Set<String> observations = new HashSet<>();
    private String window;
    private long sequence;
    private Observation latest;
    private Action pending;
    private boolean closed;

    /** The caller owns the already launched process; this method neither launches nor activates it. */
    public static MacGui attach(Path executable, Path app, Process owned, Path work) throws Exception {
        return attach(executable, app, owned.toHandle(), work);
    }
    static MacGui attach(Path executable, Path app, ProcessHandle owned, Path work) throws Exception {
        if (!System.getProperty("os.name").equals("Mac OS X")) throw new Blocked("requires macOS");
        app = app.toRealPath(); executable = executable.toRealPath();
        if (!app.getFileName().toString().equals("Voboost Installer.app") || !Files.getFileStore(app).isReadOnly()
                || !Files.isRegularFile(executable, LinkOption.NOFOLLOW_LINKS) || !Files.isExecutable(executable)
                || !owned.isAlive() || owned.pid() > Integer.MAX_VALUE) throw new Blocked("owned packaged app or bridge is unavailable");
        String actual = owned.info().command().orElseThrow(() -> new Blocked("owned executable identity is unavailable"));
        if (!Path.of(actual).toRealPath().equals(app.resolve("Contents/MacOS/voboost-installer"))) throw new Blocked("process executable differs");
        String start = Long.toString(owned.info().startInstant().orElseThrow(() -> new Blocked("process start time is unavailable")).toEpochMilli());
        fresh(work);
        HostProcess.Result signature = HostProcess.run(work.resolve("codesign.log"), List.of("/usr/bin/codesign", "--verify", "--deep", "--strict", app.toString()), 60000);
        if (signature.exit() != 0) throw new Blocked("packaged app signature failed");
        JsonObject config = configuration(app, (int) owned.pid(), start);
        RunFiles.write(work.resolve("owner.json"), config);
        return new MacGui(work, config, new Pipe(executable, work.resolve("owner.json"), work), false);
    }
    static JsonObject configuration(Path app, int pid, String started) {
        JsonObject value = new JsonObject(); value.addProperty("schemaVersion", 1); value.addProperty("ownerId", UUID.randomUUID().toString());
        value.addProperty("coordinatorPid", ProcessHandle.current().pid()); value.addProperty("appPath", app.toAbsolutePath().normalize().toString());
        value.addProperty("pid", pid); value.addProperty("startedMillis", started); value.addProperty("windowTitle", "Voboost Installer"); return value;
    }
    MacGui(Path work, JsonObject config, Backend backend) throws Exception { this(work, config, backend, true); }
    private MacGui(Path work, JsonObject config, Backend backend, boolean prepare) throws Exception {
        if (prepare) { fresh(work); RunFiles.write(work.resolve("owner.json"), config); }
        this.work = work.toRealPath(); this.backend = backend;
        owner = text(config, "ownerId"); started = text(config, "startedMillis"); pid = positiveInt(config, "pid");
        if (!uuid(owner) || !started.matches("[1-9][0-9]{0,18}")) throw new IOException("native GUI owner identity is invalid");
        RunFiles.directory(work.resolve("actions"));
    }
    private static void fresh(Path work) throws IOException {
        RunFiles.directory(work);
        try (var files = Files.list(work)) { if (files.findAny().isPresent()) throw new IOException("native GUI evidence directory must be fresh; retained actions cannot replay"); }
    }
    public final class Observation {
        private final long ordinal;
        private final String id, scope;
        private final List<JsonObject> nodes;
        private final Path evidence;
        private Observation(String id, List<JsonObject> nodes, Path evidence) {
            this.ordinal = sequence; this.id = id; this.scope = window; this.nodes = List.copyOf(nodes); this.evidence = evidence;
        }
        public Path evidence() { return evidence; }
        public boolean visible(String text) { return nodes.stream().anyMatch(node -> label(node, text)); }
        public Target target(String role, String text) throws IOException {
            List<JsonObject> matches = nodes.stream().filter(node -> role.equals(MacGui.text(node, "role")) && label(node, text) && yes(node, "enabled") && !yes(node, "redacted")).toList();
            if (matches.size() != 1) throw new Blocked("unique enabled control not observed: " + role);
            return new Target(this, matches.get(0));
        }
        public String uniqueTextStarting(String prefix) throws IOException {
            Set<String> matches = new HashSet<>();
            for (JsonObject node : nodes) for (String field : List.of("title", "description", "value")) {
                String value = MacGui.text(node, field);
                if (value.startsWith(prefix) && value.length() > prefix.length()) matches.add(value);
            }
            if (matches.size() != 1) throw new Blocked("unique native device identification unavailable");
            return matches.iterator().next();
        }
        public boolean enabled(String role, String text) {
            return nodes.stream().filter(node -> role.equals(MacGui.text(node, "role")) && label(node, text) && yes(node, "enabled")).count() == 1;
        }
        /** Uses the smallest observed subtree containing every exact dialog anchor; ambiguous controls fail closed. */
        public Target dialogTarget(String title, String body, String button, String cancel) throws IOException {
            Map<String, JsonObject> byId = new HashMap<>();
            for (JsonObject node : nodes) byId.put(MacGui.text(node, "nodeToken"), node);
            List<List<JsonObject>> candidates = new ArrayList<>();
            for (JsonObject ancestor : nodes) {
                String id = MacGui.text(ancestor, "nodeToken"); List<JsonObject> subtree = new ArrayList<>();
                for (JsonObject node : nodes) {
                    JsonObject current = node;
                    while (current != null) {
                        if (id.equals(MacGui.text(current, "nodeToken"))) { subtree.add(node); break; }
                        current = current.get("parent").isJsonNull() ? null : byId.get(MacGui.text(current, "parent"));
                    }
                }
                if (subtree.stream().anyMatch(node -> label(node, title)) && subtree.stream().anyMatch(node -> label(node, body))
                        && subtree.stream().anyMatch(node -> "AXButton".equals(MacGui.text(node, "role")) && label(node, cancel))) candidates.add(subtree);
            }
            int minimum = candidates.stream().mapToInt(List::size).min().orElseThrow(() -> new Blocked("exact native dialog not observed"));
            List<List<JsonObject>> scopes = candidates.stream().filter(nodes -> nodes.size() == minimum).toList();
            if (scopes.size() != 1) throw new Blocked("native dialog scope is ambiguous");
            List<JsonObject> controls = scopes.get(0).stream().filter(node -> "AXButton".equals(MacGui.text(node, "role")) && label(node, button) && yes(node, "enabled")).toList();
            if (controls.size() != 1) throw new Blocked("native dialog control is ambiguous or unavailable");
            return new Target(this, controls.get(0));
        }
        private Target windowTarget() throws IOException {
            List<JsonObject> roots = nodes.stream().filter(node -> node.get("parent").isJsonNull()).toList();
            if (roots.size() != 1) throw new Blocked("main window root is unavailable");
            return new Target(this, roots.get(0));
        }
    }
    public final class Target {
        private final Observation source;
        private final JsonObject node;
        private Target(Observation source, JsonObject node) { this.source = source; this.node = node.deepCopy(); }
    }
    public final class Action {
        private final String id, operation;
        private final Observation before;
        private final Path record;
        private JsonObject ledger;
        private boolean terminal;
        private Action(String operation, Observation before) throws IOException {
            this.id = UUID.randomUUID().toString(); this.operation = operation; this.before = before;
            record = work.resolve("actions").resolve(id + ".json");
            ledger = new JsonObject(); ledger.addProperty("schemaVersion", 1); ledger.addProperty("ownerId", owner);
            ledger.addProperty("actionId", id); ledger.addProperty("operation", operation); state("reserved");
        }
        private void state(String value) throws IOException { ledger.addProperty("state", value); RunFiles.write(record, ledger); }
        public Path record() { return record; }
        /** Caller supplies the scenario's actual expected UI readback, independently of AX success. */
        public void confirm(Observation after, java.util.function.Predicate<Observation> expected) throws IOException {
            if (closed || pending != this || terminal || after != latest || after.ordinal <= before.ordinal
                    || !after.scope.equals(before.scope) || !expected.test(after)) throw new IOException("fresh native GUI action readback is not verified");
            JsonObject proof = new JsonObject(); proof.addProperty("ownerId", owner); proof.addProperty("actionId", id);
            proof.addProperty("before", before.evidence.toString()); proof.addProperty("after", after.evidence.toString());
            RunFiles.write(work.resolve("actions").resolve(id + ".evidence.json"), proof);
            state("confirmed"); terminal = true; pending = null;
        }
    }
    public Action pending() { return pending; }
    public Observation observe() throws Exception {
        requireOpen(); latest = null; JsonObject request = request("inspect"); JsonObject result = exchange(request);
        if (!result.keySet().equals(Set.of("schemaVersion", "ownerId", "requestId", "pid", "startedMillis", "windowToken", "status", "nodes", "observationId"))) throw new IOException("native GUI observation fields differ");
        scope(result);
        String id = text(result, "observationId");
        if (!uuid(id) || observations.size() >= 10000 || !observations.add(id) || !result.get("nodes").isJsonArray()) throw new IOException("native GUI observation is stale or malformed");
        JsonArray rows = result.getAsJsonArray("nodes");
        if (rows.size() < 1 || rows.size() > 3000) throw new IOException("native GUI node bound differs");
        List<JsonObject> nodes = new ArrayList<>(); Map<String, String> parents = new HashMap<>(); int roots = 0;
        for (JsonElement item : rows) {
            if (!item.isJsonObject()) throw new IOException("native GUI node is not an object");
            JsonObject node = item.getAsJsonObject(); String token = text(node, "nodeToken");
            if (!node.keySet().equals(NODE_FIELDS) || !uuid(token) || parents.containsKey(token)) throw new IOException("native GUI node identity is invalid or duplicated");
            for (String field : Set.of("role", "subrole", "title", "description", "identifier", "value"))
                if (!string(node, field) || text(node, field).length() > 4096 || text(node, field).indexOf('\0') >= 0) throw new IOException("native GUI text bound differs");
            if (!bool(node, "enabled") || !bool(node, "redacted")) throw new IOException("native GUI control semantics are unavailable");
            String parent = null;
            if (node.get("parent").isJsonNull()) {
                ++roots;
                if (!text(node, "role").equals("AXWindow") || !text(node, "title").equals("Voboost Installer")) throw new IOException("native GUI main window root differs");
            } else { parent = text(node, "parent"); if (!uuid(parent)) throw new IOException("native GUI parent identity differs"); }
            if (!node.get("frame").isJsonNull()) {
                if (!node.get("frame").isJsonArray() || node.getAsJsonArray("frame").size() != 4) throw new IOException("native GUI geometry unavailable");
                int index = 0;
                for (JsonElement dimension : node.getAsJsonArray("frame")) {
                    if (!dimension.isJsonPrimitive() || !dimension.getAsJsonPrimitive().isNumber() || !Double.isFinite(dimension.getAsDouble())
                            || Math.abs(dimension.getAsDouble()) > 1000000 || index >= 2 && dimension.getAsDouble() < 0) throw new IOException("native GUI geometry exceeds bound");
                    ++index;
                }
            }
            parents.put(token, parent); nodes.add(node.deepCopy());
        }
        if (roots != 1) throw new IOException("native GUI window root is ambiguous");
        for (String token : parents.keySet()) {
            Set<String> seen = new HashSet<>(); String cursor = token;
            while (cursor != null) {
                if (!parents.containsKey(cursor) || !seen.add(cursor) || seen.size() > 49) throw new IOException("native GUI ancestry is incomplete or cyclic");
                cursor = parents.get(cursor);
            }
        }
        ++sequence; Path evidence = work.resolve("observation-" + sequence + ".json"); RunFiles.write(evidence, result);
        return latest = new Observation(id, nodes, evidence);
    }
    public Action press(Target target) throws Exception { return dispatch("press", target, null); }
    public Action setValue(Target target, String value) throws Exception {
        if (value == null || value.length() > 4096 || value.indexOf('\0') >= 0) throw new IOException("bounded native GUI value required");
        return dispatch("set-value", target, value);
    }
    public Action resize(Observation observation, boolean minimum) throws Exception { return dispatch("resize", observation.windowTarget(), minimum ? "minimum" : "default"); }
    private Action dispatch(String operation, Target target, String value) throws Exception {
        requireOpen();
        if (pending != null) throw new IOException("native GUI action is nonterminal; replay and other mutations are forbidden");
        if (target == null || target.source != latest || !target.source.scope.equals(window)) throw new IOException("native GUI target observation is stale or foreign");
        Action action = new Action(operation, target.source); pending = action;
        JsonObject request = request(operation); request.addProperty("actionId", action.id); request.addProperty("observationId", target.source.id);
        request.addProperty("nodeToken", text(target.node, "nodeToken"));
        if (operation.equals("set-value")) request.addProperty("value", value);
        if (operation.equals("resize")) request.addProperty("preset", value);
        action.state("dispatched");
        try {
            JsonObject result = exchange(request); scope(result);
            if (!result.keySet().equals(Set.of("schemaVersion", "ownerId", "requestId", "pid", "startedMillis", "windowToken", "status", "actionId", "actionDispatched"))
                    || !action.id.equals(text(result, "actionId")) || !yes(result, "actionDispatched")) throw new IOException("native GUI action receipt differs");
            action.state("accepted"); return action;
        } catch (Blocked rejected) {
            action.state("rejected"); action.terminal = true; pending = null; throw rejected;
        } catch (Exception uncertain) { action.state("unknown"); throw new Unknown("action outcome requires read-only reconciliation; no replay", uncertain); }
    }
    private JsonObject request(String operation) {
        JsonObject value = new JsonObject(); value.addProperty("schemaVersion", 1); value.addProperty("ownerId", owner);
        value.addProperty("requestId", UUID.randomUUID().toString()); value.addProperty("op", operation);
        if (window == null) value.add("windowToken", JsonNull.INSTANCE); else value.addProperty("windowToken", window); return value;
    }
    private JsonObject exchange(JsonObject request) throws Exception {
        JsonObject result = backend.exchange(request, Duration.ofSeconds(25));
        if (!one(result, "schemaVersion") || !owner.equals(text(result, "ownerId")) || !text(request, "requestId").equals(text(result, "requestId"))) throw new IOException("native GUI response owner/request differs");
        if (!"ok".equals(text(result, "status"))) {
            if (!result.keySet().equals(Set.of("schemaVersion", "ownerId", "requestId", "status", "reason", "actionDispatched")) || !bool(result, "actionDispatched")) throw new IOException("native GUI failure frame differs");
            if ("blocked".equals(text(result, "status")) && !yes(result, "actionDispatched")) throw new Blocked(text(result, "reason"));
            throw new Unknown("observer reports an uncertain action", null);
        }
        return result;
    }
    private void scope(JsonObject result) throws IOException {
        String token = text(result, "windowToken");
        if (positiveInt(result, "pid") != pid || !started.equals(text(result, "startedMillis")) || !uuid(token) || window != null && !window.equals(token)) throw new IOException("native GUI PID/starttime/window changed");
        if (window == null) window = token;
    }
    private void requireOpen() throws IOException { if (closed) throw new IOException("native GUI observer is closed"); }
    @Override public void close() throws Exception { if (!closed) { closed = true; backend.close(); } }
    private static boolean label(JsonObject node, String expected) { return Set.of("title", "description", "value").stream().anyMatch(field -> expected.equals(text(node, field))); }
    private static boolean uuid(String value) { try { return UUID.fromString(value).toString().equals(value); } catch (IllegalArgumentException ignored) { return false; } }
    private static boolean string(JsonObject value, String key) { JsonElement field = value.get(key); return field != null && field.isJsonPrimitive() && field.getAsJsonPrimitive().isString(); }
    private static String text(JsonObject value, String key) { return string(value, key) ? value.get(key).getAsString() : ""; }
    private static boolean bool(JsonObject value, String key) { JsonElement field = value.get(key); return field != null && field.isJsonPrimitive() && field.getAsJsonPrimitive().isBoolean(); }
    private static boolean yes(JsonObject value, String key) { return bool(value, key) && value.get(key).getAsBoolean(); }
    private static boolean one(JsonObject value, String key) { return value.has(key) && value.get(key).isJsonPrimitive() && value.getAsJsonPrimitive(key).isNumber() && value.get(key).getAsString().equals("1"); }
    private static int positiveInt(JsonObject value, String key) throws IOException {
        JsonElement field = value.get(key);
        if (field == null || !field.isJsonPrimitive() || !field.getAsJsonPrimitive().isNumber() || !field.getAsString().matches("[1-9][0-9]{0,9}")
                || new BigInteger(field.getAsString()).compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) throw new IOException("native GUI positive PID required");
        return field.getAsInt();
    }
    private static final class Pipe implements Backend {
        private final Process process;
        private final BufferedReader output;
        private final ExecutorService reader = Executors.newSingleThreadExecutor(task -> { Thread thread = new Thread(task, "gui-observer-output"); thread.setDaemon(true); return thread; });
        private final ExecutorService errors = Executors.newSingleThreadExecutor(task -> { Thread thread = new Thread(task, "gui-observer-errors"); thread.setDaemon(true); return thread; });
        private final java.util.concurrent.atomic.AtomicReference<IOException> error = new java.util.concurrent.atomic.AtomicReference<>();
        private final Path log;
        private boolean closed;
        private Pipe(Path executable, Path owner, Path work) throws IOException {
            log = work.resolve("bridge.out"); Files.createFile(log);
            Path diagnostic = work.resolve("bridge.err"); Files.createFile(diagnostic);
            process = new ProcessBuilder(executable.toString(), "--owner", owner.toString()).start();
            output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8.newDecoder()));
            errors.submit(() -> {
                int total = 0;
                try (var stream = process.getErrorStream(); var file = Files.newOutputStream(diagnostic)) {
                    byte[] bytes = new byte[4096]; int count;
                    while ((count = stream.read(bytes)) >= 0) {
                        if (count > 65536 - total) throw new IOException("native GUI stderr exceeds bound");
                        file.write(bytes, 0, count); total += count;
                    }
                } catch (IOException failure) { error.compareAndSet(null, failure); process.destroyForcibly(); }
            });
        }
        public synchronized JsonObject receive(Duration timeout) throws Exception { return exchange(null, timeout); }
        public ProcessHandle process() { return process.toHandle(); }
        public synchronized JsonObject exchange(JsonObject request, Duration timeout) throws Exception {
            if (closed || !process.isAlive()) throw new IOException("native GUI bridge is unavailable");
            byte[] bytes = request == null ? new byte[0] : (request + "\n").getBytes(StandardCharsets.UTF_8); if (bytes.length > 65536) throw new IOException("native GUI request exceeds bound");
            var next = reader.submit(() -> {
                process.getOutputStream().write(bytes); process.getOutputStream().flush();
                StringBuilder line = new StringBuilder(); int character;
                while ((character = output.read()) >= 0) {
                    if (character == '\n') return line.toString();
                    if (character == '\r' || line.length() >= 2 * 1024 * 1024) throw new IOException("native GUI output exceeds bound or framing differs");
                    line.append((char) character);
                }
                throw new IOException("native GUI output ended before a complete receipt");
            });
            try {
                String raw = next.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
                if (error.get() != null) throw error.get();
                Files.writeString(log, raw + "\n", StandardOpenOption.APPEND); return RunFiles.parse(raw);
            } catch (Exception failure) {
                next.cancel(true);
                boolean interrupted = failure instanceof InterruptedException;
                try { terminate(false); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
                if (interrupted) Thread.currentThread().interrupt();
                throw failure;
            }
        }
        private void terminate(boolean graceful) throws Exception {
            if (closed) return; closed = true;
            boolean interrupted = Thread.interrupted();
            try {
                if (graceful) {
                    process.getOutputStream().close();
                    if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly();
                } else process.destroyForcibly();
                if (!process.waitFor(2, TimeUnit.SECONDS)) throw new IOException("native GUI bridge termination unverified");
            } finally {
                reader.shutdownNow(); errors.shutdown();
                process.getOutputStream().close(); output.close(); process.getErrorStream().close();
                if (!reader.awaitTermination(2, TimeUnit.SECONDS) || !errors.awaitTermination(2, TimeUnit.SECONDS))
                    throw new IOException("native GUI bridge stream termination unverified");
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
        public synchronized void close() throws Exception { terminate(true); }
    }
    static Backend processBackend(Path executable, Path owner, Path work) throws IOException { return new Pipe(executable, owner, work); }
}
