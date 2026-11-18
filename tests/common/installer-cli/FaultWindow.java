package ru.voboost.tests.installer.cli;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;
import ru.voboost.tests.RunFiles;

/** Existing packaged diagnostic authority; EOF releases the bounded window, never a command payload. */
public final class FaultWindow {
    private static final Set<String> REQUEST = Set.of("action", "kind", "bootId", "manifestSha256", "generationId", "catalogSha256", "targetPid", "targetStarttime");
    private static final Set<String> BODY = Set.of("schemaVersion", "phase", "kind", "token", "bootId", "releaseIdentity", "manifestSha256", "generationId", "catalogSha256", "ownerPid", "ownerStarttime", "daemonPid", "daemonStarttime", "targetPid", "targetStarttime", "startedMonotonicMs", "expiresMonotonicMs");
    private static final Set<String> NATIVE_BODY = Set.of("kind", "token", "bootId", "generationId", "catalogSha256", "ownerWriterId",
            "ownerPid", "ownerStarttime", "targetPid", "targetStarttime", "deadlineMonotonicMs", "observedMonotonicMs",
            "reads", "droppedReads", "writes", "failedWrites", "waitStateSteps", "recoveredValues", "recoveredValue", "leaseRevision",
            "stopped", "released", "closed");
    private final JsonObject request;
    private final String serial, release;
    private JsonObject armed, closed;
    private long started;
    private boolean released;
    private boolean nativeReleaseRequested, nativeReleaseConfirmed, nativeTerminal;
    private int pendingNativeSnapshots;
    private final java.util.List<JsonObject> nativeReceipts = new java.util.ArrayList<>();
    public enum NativeControl { SNAPSHOT, RELEASE }


    public FaultWindow(JsonObject request, String serial, String release) throws IOException {
        if (!request.keySet().equals(REQUEST) || !"fault-window".equals(text(request, "action"))
                || !Set.of("startup-mutation-lock", "vehicle-state-reply-loss", "native-state-read-unavailable", "native-state-write-unavailable", "uninstall-transport-loss").contains(text(request, "kind"))
                || !text(request, "bootId").matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                || !InstallerDriver.emulator(serial) || release == null || release.isBlank() || release.length() > 128
                || !pid(request, "targetPid") || !positive(request, "targetStarttime")) throw new IOException("fixed diagnostic request identity differs");
        for (String key : Set.of("manifestSha256", "generationId", "catalogSha256"))
            if (!text(request, key).matches("[0-9a-f]{64}")) throw new IOException("diagnostic product authority is unavailable");
        this.request = request.deepCopy(); this.serial = serial; this.release = release;
    }
    /** Read-only reconstruction of an already retained exact terminal stream; never dispatches or reopens a window. */
    public static JsonObject cleanup(String raw, JsonObject request, String serial, String release) throws IOException {
        if (raw.length() > 16384 || !raw.endsWith("\n")) throw new IOException("bounded complete diagnostic stream required");
        String[] lines = raw.split("\n", -1);
        if (lines.length != 3 || !lines[2].isEmpty()) throw new IOException("one arm and one terminal diagnostic receipt required");
        FaultWindow window = new FaultWindow(request, serial, release);
        window.observe(lines[0]); window.release(); window.observe(lines[1]); return window.closed();
    }
    /** Re-read a retained terminal native stream; this issues no control or device command. */
    public static FaultWindow readNative(java.nio.file.Path stdout,JsonObject request,String serial,String release)throws IOException {
        if(!stdout.isAbsolute()||!java.nio.file.Files.isRegularFile(stdout,java.nio.file.LinkOption.NOFOLLOW_LINKS)||java.nio.file.Files.size(stdout)>131072)throw new IOException("bounded retained native stdout required");
        String raw=java.nio.file.Files.readString(stdout);if(!raw.endsWith("\n"))throw new IOException("native terminal stream is truncated");
        String[] lines=raw.split("\n",-1);if(lines.length<8||lines.length>18)throw new IOException("native terminal stream count differs");
        FaultWindow window=new FaultWindow(request,serial,release);window.observe(lines[0]);
        for(int i=1;i<lines.length-1;i++) {
            String phase=text(RunFiles.parse(lines[i]),"phase");
            if("snapshot".equals(phase)){window.reserveNativeControl(NativeControl.SNAPSHOT);window.observeNative(lines[i]);}
            else if("released".equals(phase)){window.reserveNativeControl(NativeControl.RELEASE);window.observeNative(lines[i]);}
            else if("native-closed".equals(phase)){window.release();window.observeNative(lines[i]);}
            else if("closed".equals(phase)&&i==lines.length-2)window.observe(lines[i]);
            else throw new IOException("unexpected retained native terminal phase");
        }
        window.requireClosed();return window;
    }
    public JsonObject request() { return request.deepCopy(); }
    public synchronized void observe(String line) throws IOException {
        if (line.length() > 8192 || line.indexOf('\r') >= 0 || line.indexOf('\0') >= 0) throw new IOException("bounded diagnostic receipt required");
        JsonObject envelope = RunFiles.parse(line);
        String phase = armed == null ? "armed" : "closed";
        if (closed != null || !envelope.keySet().equals(Set.of("schemaVersion", "serial", "phase", "body"))
                || !one(envelope, "schemaVersion") || !serial.equals(text(envelope, "serial")) || !phase.equals(text(envelope, "phase"))
                || !envelope.get("body").isJsonObject()) throw new IOException("diagnostic envelope identity differs");
        JsonObject body = envelope.getAsJsonObject("body"); Set<String> fields = new HashSet<>(BODY);
        if (Set.of("native-state-read-unavailable", "native-state-write-unavailable").contains(text(request, "kind"))) fields.add("ownerWriterId");
        if (phase.equals("closed")) fields.addAll(Set.of("cleanup", "reason"));
        if (!body.keySet().equals(fields) || !one(body, "schemaVersion") || !phase.equals(text(body, "phase"))
                || !release.equals(text(body, "releaseIdentity")) || !text(body, "token").matches("[0-9a-f]{64}")
                || !pid(body, "ownerPid") || !pid(body, "daemonPid") || body.get("ownerPid").equals(body.get("daemonPid")))
            throw new IOException("diagnostic receipt authority differs");
        for (String key : Set.of("kind", "bootId", "manifestSha256", "generationId", "catalogSha256", "targetPid", "targetStarttime"))
            if (!request.get(key).equals(body.get(key))) throw new IOException("diagnostic target authority differs");
        if (fields.contains("ownerWriterId") && !text(body, "ownerWriterId").matches("[0-9a-f]{32}")) throw new IOException("diagnostic owner writer differs");
        for (String key : Set.of("ownerStarttime", "daemonStarttime", "startedMonotonicMs", "expiresMonotonicMs"))
            if (!positive(body, key)) throw new IOException("diagnostic process or deadline differs");
        if (!new BigInteger(text(body, "expiresMonotonicMs")).subtract(new BigInteger(text(body, "startedMonotonicMs"))).equals(BigInteger.valueOf(90000)))
            throw new IOException("diagnostic maximum window differs");
        if (phase.equals("armed")) { armed = body.deepCopy(); started = System.nanoTime(); }
        else {
            if (!released || !yes(body, "cleanup") || !"eof".equals(text(body, "reason"))
                    || nativeKind() && !nativeTerminal) throw new IOException("diagnostic cleanup missing, early or expired");
            JsonObject same = body.deepCopy(); same.remove("cleanup"); same.remove("reason"); same.addProperty("phase", "armed");
            if (!same.equals(armed)) throw new IOException("diagnostic cleanup identity changed");
            closed = body.deepCopy();
        }
    }
    public synchronized void requireLive() throws IOException {
        if (armed == null || closed != null || released || System.nanoTime() - started >= java.time.Duration.ofSeconds(60).toNanos())
            throw new IOException("diagnostic conservative dispatch window is not live");
    }
    synchronized void reserveNativeControl(NativeControl command) throws IOException {
        requireLive();
        if (command == null || !Set.of("native-state-read-unavailable", "native-state-write-unavailable").contains(text(armed, "kind")))
            throw new IOException("native diagnostic control requires its authenticated armed window");
        if (command == NativeControl.RELEASE) {
            if (nativeReleaseRequested || pendingNativeSnapshots != 0) throw new IOException("native fault release cannot be replayed or overtake a snapshot");
            nativeReleaseRequested = true;
        } else {
            if (nativeReleaseRequested && !nativeReleaseConfirmed || pendingNativeSnapshots != 0) throw new IOException("native snapshot reply pending");
            pendingNativeSnapshots++;
        }
    }
    private boolean nativeKind() {
        return armed != null && Set.of("native-state-read-unavailable", "native-state-write-unavailable").contains(text(armed, "kind"));
    }
    synchronized JsonObject observeNative(String line) throws IOException {
        if (!nativeKind() || closed != null || nativeTerminal || line == null || line.length() > 8192) throw new IOException("native receipt scope unavailable");
        JsonObject envelope = RunFiles.parse(line);
        if (!envelope.keySet().equals(Set.of("schemaVersion", "serial", "phase", "body")) || !one(envelope, "schemaVersion")
                || !serial.equals(text(envelope, "serial")) || !envelope.has("body") || !envelope.get("body").isJsonObject())
            throw new IOException("native receipt envelope differs");
        String phase = text(envelope, "phase"); JsonObject body = envelope.getAsJsonObject("body");
        if (!body.keySet().equals(NATIVE_BODY)) throw new IOException("native receipt fields differ");
        for (String key : Set.of("ownerPid", "ownerStarttime", "targetPid", "targetStarttime", "deadlineMonotonicMs", "observedMonotonicMs"))
            if (!nativeInteger(body, key, true)) throw new IOException("native receipt identity is not canonical");
        for (String key : Set.of("reads", "droppedReads", "writes", "failedWrites", "waitStateSteps", "recoveredValues"))
            if (!nativeInteger(body, key, false)) throw new IOException("native receipt counter is not canonical");
        for (String key : Set.of("stopped", "released", "closed"))
            if (!body.get(key).isJsonPrimitive() || !body.getAsJsonPrimitive(key).isBoolean()) throw new IOException("native receipt phase is not boolean");
        if (!body.get("leaseRevision").isJsonNull() && !nativeInteger(body, "leaseRevision", true)) throw new IOException("native lease is not canonical");
        if (!body.get("recoveredValue").isJsonNull() && (!body.get("recoveredValue").isJsonPrimitive() || !body.getAsJsonPrimitive("recoveredValue").isBoolean())) throw new IOException("native recovered value differs");
        for (String key : Set.of("kind", "token", "bootId", "generationId", "catalogSha256", "ownerWriterId"))
            if (!text(armed, key).equals(text(body, key))) throw new IOException("native receipt scope differs");
        if (!body.has("targetPid") || !body.get("targetPid").isJsonPrimitive() || !body.getAsJsonPrimitive("targetPid").isNumber()
                || !armed.get("targetPid").equals(body.get("targetPid"))
                || !body.has("targetStarttime") || !body.get("targetStarttime").getAsString().equals(text(armed, "targetStarttime"))
                || !body.has("deadlineMonotonicMs") || !body.get("deadlineMonotonicMs").getAsString().equals(text(armed, "expiresMonotonicMs")))
            throw new IOException("native target or deadline differs");
        BigInteger observed = new BigInteger(body.get("observedMonotonicMs").getAsString());
        if (observed.compareTo(new BigInteger(text(armed, "startedMonotonicMs"))) < 0
                || observed.compareTo(new BigInteger(text(armed, "expiresMonotonicMs"))) >= 0
                || new BigInteger(body.get("ownerPid").getAsString()).compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0)
            throw new IOException("native receipt time or owner PID differs");
        if (!nativeReceipts.isEmpty()) {
            JsonObject previous = nativeReceipts.get(nativeReceipts.size() - 1);
            if (!body.get("ownerPid").equals(previous.get("ownerPid")) || !body.get("ownerStarttime").equals(previous.get("ownerStarttime")))
                throw new IOException("native owner incarnation changed");
            for (String key : Set.of("observedMonotonicMs", "reads", "droppedReads", "writes", "failedWrites", "waitStateSteps", "recoveredValues"))
                if (new BigInteger(body.get(key).getAsString()).compareTo(new BigInteger(previous.get(key).getAsString())) < 0)
                    throw new IOException("native receipt counters or time regressed");
        }
        if ("snapshot".equals(phase)) {
            if (released || pendingNativeSnapshots != 1 || yes(body, "closed") || yes(body, "released") != nativeReleaseConfirmed)
                throw new IOException("unsolicited native snapshot");
            pendingNativeSnapshots--;
        } else if ("released".equals(phase)) {
            if (released || !nativeReleaseRequested || nativeReleaseConfirmed || pendingNativeSnapshots != 0 || !yes(body, "released") || yes(body, "closed"))
                throw new IOException("unexpected native release receipt");
            nativeReleaseConfirmed = true;
        } else if ("native-closed".equals(phase)) {
            if (!released || !nativeReleaseConfirmed || pendingNativeSnapshots != 0 || !yes(body, "released") || !yes(body, "closed"))
                throw new IOException("native terminal receipt missing cleanup order");
            nativeTerminal = true;
        } else throw new IOException("unknown native receipt phase");
        nativeReceipts.add(body.deepCopy());
        return body.deepCopy();
    }
    public synchronized java.util.List<JsonObject> nativeReceipts() throws IOException {
        requireClosed();
        if (!nativeKind() || !nativeTerminal) throw new IOException("native terminal receipt unavailable");
        return nativeReceipts.stream().map(JsonObject::deepCopy).toList();
    }
    synchronized void requireNativeWindow() throws IOException {
        requireLive();
        if (!Set.of("native-state-read-unavailable", "native-state-write-unavailable").contains(text(armed, "kind")))
            throw new IOException("native diagnostic window required");
    }

    public void requirePublisher(String publisher)throws IOException {
        requireLive();String[] fields=publisher==null?new String[0]:publisher.split(":",-1);
        if(fields.length!=3||!text(armed,"bootId").equals(fields[0])||!armed.get("daemonPid").getAsString().equals(fields[1])||!text(armed,"daemonStarttime").equals(fields[2]))
            throw new IOException("diagnostic current producer lifetime differs");
    }
    public synchronized void release() throws IOException { if (armed == null || closed != null || released) throw new IOException("diagnostic release out of order"); released = true; }
    public synchronized void requireClosed() throws IOException { if (closed == null) throw new IOException("diagnostic terminal cleanup is unverified"); }
    public synchronized JsonObject armed() throws IOException { if (armed == null) throw new IOException("diagnostic was not armed"); return armed.deepCopy(); }
    public synchronized JsonObject closed() throws IOException { requireClosed(); return closed.deepCopy(); }
    public java.util.List<ru.voboost.tests.logging.InstallerLogs.RootEffect> startupRejections(
            ru.voboost.tests.logging.InstallerLogs.Snapshot prior, ru.voboost.tests.logging.InstallerLogs.Snapshot current) throws IOException {
        if (armed == null || !"startup-mutation-lock".equals(text(armed, "kind"))) throw new IOException("startup rejection requires its exact diagnostic window");
        java.util.List<ru.voboost.tests.logging.InstallerLogs.RootEffect> result = new java.util.ArrayList<>();
        for (var row : current.effectsAfter(prior)) {
            JsonObject fields = row.fields(); if (!text(armed, "token").equals(text(fields, "token"))) continue;
            for (String key : Set.of("kind", "bootId", "manifestSha256", "generationId", "catalogSha256", "targetStarttime"))
                if (!text(armed, key).equals(text(fields, key))) throw new IOException("startup rejection effect authority differs");
            if (!text(fields, "targetPid").equals(armed.get("targetPid").getAsString())
                    || !"com.qinggan.app.setting".equals(text(fields, "process")) || !"settings".equals(text(fields, "source"))
                    || !"startup-mutation-rejected".equals(text(fields, "effect")) || !text(fields, "requestId").matches("(?:0|[1-9][0-9]{0,19})")
                    || new BigInteger(text(fields, "requestId")).bitLength() > 64)
                throw new IOException("startup rejection effect target or operation differs");
            result.add(row);
        }
        return java.util.List.copyOf(result);
    }
    /** Real dropped replies from the fixed Java endpoint, not native codec/controller evidence. */
    public java.util.List<ru.voboost.tests.logging.InstallerLogs.RootEffect> stateReplyLosses(
            String agent, ru.voboost.tests.logging.InstallerLogs.Snapshot prior,
            ru.voboost.tests.logging.InstallerLogs.Snapshot current) throws IOException {
        if (armed == null || !"vehicle-state-reply-loss".equals(text(armed,"kind"))
                || !Set.of("vehicle-drive-forced-ev","vehicle-drive-pedestrian-warning").contains(agent))
            throw new IOException("state reply loss requires the exact current Java diagnostic scope");
        var result = new java.util.ArrayList<ru.voboost.tests.logging.InstallerLogs.RootEffect>();
        var requests = new java.util.HashSet<String>();
        for (var row : current.effectsAfter(prior)) {
            JsonObject fields=row.fields();if(!text(armed,"token").equals(text(fields,"token")))continue;
            for(String key:Set.of("kind","bootId","manifestSha256","generationId","catalogSha256","targetStarttime"))
                if(!text(armed,key).equals(text(fields,key)))throw new IOException("state reply effect authority differs");
            String source=text(fields,"source"),requestId=text(fields,"requestId");
            if(!text(fields,"targetPid").equals(armed.get("targetPid").getAsString())
                    ||!"com.qinggan.canbus.service".equals(text(fields,"process"))
                    ||!"state-reply-dropped".equals(text(fields,"effect"))
                    ||!Set.of("vehicle-drive-forced-ev","vehicle-drive-pedestrian-warning").contains(source)
                    ||!requestId.matches("0|[1-9][0-9]{0,19}")||new BigInteger(requestId).bitLength()>64)
                throw new IOException("state reply effect target/request differs");
            if(agent.equals(source)) {
                if(!requests.add(requestId))throw new IOException("duplicate dropped state request identity");
                result.add(row);
            }
        }
        return java.util.List.copyOf(result);
    }

    private static boolean nativeInteger(JsonObject body, String key, boolean positive) {
        JsonElement field = body.get(key);
        if (field == null || !field.isJsonPrimitive() || !field.getAsJsonPrimitive().isNumber()) return false;
        String raw = field.getAsString();
        return raw.matches(positive ? "[1-9][0-9]{0,19}" : "0|[1-9][0-9]{0,19}") && new BigInteger(raw).bitLength() <= 64;
    }
    private static String text(JsonObject value, String key) { JsonElement field = value.get(key); return field != null && field.isJsonPrimitive() && field.getAsJsonPrimitive().isString() ? field.getAsString() : ""; }
    private static boolean yes(JsonObject value, String key) { JsonElement field = value.get(key); return field != null && field.isJsonPrimitive() && field.getAsJsonPrimitive().isBoolean() && field.getAsBoolean(); }
    private static boolean one(JsonObject value, String key) { JsonElement field = value.get(key); return field != null && field.isJsonPrimitive() && field.getAsJsonPrimitive().isNumber() && field.getAsString().equals("1"); }
    private static boolean pid(JsonObject value, String key) {
        JsonElement field = value.get(key); if (field == null || !field.isJsonPrimitive() || !field.getAsJsonPrimitive().isNumber()) return false;
        String number = field.getAsString(); return number.matches("[1-9][0-9]{0,9}") && new BigInteger(number).compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0;
    }
    private static boolean positive(JsonObject value, String key) { String number = text(value, key); return number.matches("[1-9][0-9]{0,19}") && new BigInteger(number).bitLength() <= 64; }
}
