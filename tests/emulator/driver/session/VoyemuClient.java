package ru.voboost.voyemu;

import com.google.gson.*;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Narrow current-protocol client. An unknown mutation must only be reconciled, never replayed. */
public final class VoyemuClient {
    static final int SCHEMA = 1;
    public record Session(String id, String ownershipId) {
        public Session { token(id); if (ownershipId != null) token(ownershipId); }
        public static Session attached(String id) { return new Session(id, null); }
        JsonObject json() { JsonObject j = new JsonObject(); j.addProperty("id", id); if (ownershipId != null) j.addProperty("ownership_id", ownershipId); return j; }
        void requireOwner() { if (ownershipId == null) throw new IllegalStateException("read-only session"); }
    }
    public static final class Attempt {
        private final String requestId, op, profile, json;
        private final Session session;
        private final boolean dispatchable;
        private Attempt(String requestId, String op, String profile, Session session, String json) {
            this(requestId, op, profile, session, json, true);
        }
        private Attempt(String requestId, String op, String profile, Session session, String json, boolean dispatchable) {
            this.requestId=requestId; this.op=op; this.profile=profile; this.session=session; this.json=json;
            this.dispatchable = dispatchable;
        }
        public String requestId() { return requestId; }
        public String op() { return op; }
        public String profile() { return profile; }
        public Session session() { return session; }
        public String json() { return json; }
    }
    /** RESPONSE confirms only the outer response envelope, never an operation outcome. */
    public enum State { RESPONSE, UNKNOWN }
    public record Reply(State state, Attempt attempt, Integer exitCode, JsonObject response, String diagnostic) {}
    public record ProcessResult(Integer exitCode, boolean timedOut, String stdout, String stderr) {}
    public interface Transport { ProcessResult execute(String request, Duration timeout) throws IOException, InterruptedException; }
    private static final Set<String> READ_OPERATIONS = Set.of("info", "profile.get", "session.status", "operation.get", "session.ui", "session.wait",
        "vehicle.can.get", "vehicle.can.history", "multimedia.system", "multimedia.phone.state", "vehicle.get", "session.screenshot");
    private static final Set<String> MUTATIONS = Set.of("session.start", "session.restart", "multimedia.reboot", "session.stop", "input.tap", "input.gesture", "input.key.press", "multimedia.launch", "vehicle.set");
    private final Transport transport;

    public VoyemuClient(Transport transport) { this.transport = Objects.requireNonNull(transport); }
    private static void token(String s) { if (s == null || !s.matches("[0-9a-f]{32}")) throw new IllegalArgumentException("invalid session token"); }
    private static void required(String s) { if (s == null || s.isBlank()) throw new IllegalArgumentException("empty value"); }
    private static JsonObject base(String id, String op, String profile, Session session) {
        required(id); JsonObject j = new JsonObject(); j.addProperty("schema", SCHEMA); j.addProperty("request_id", id); j.addProperty("op", op);
        if (profile != null) { required(profile); j.addProperty("profile", profile); }
        if (session != null) j.add("session", session.json()); return j;
    }
    private static Attempt attempt(String id, String op, String profile, Session s, JsonObject j) { return new Attempt(id, op, profile, s, j.toString()); }
    public Attempt info(String id) { return attempt(id,"info",null,null,base(id,"info",null,null)); }
    public Attempt profileGet(String id, String profile) { Objects.requireNonNull(profile); return attempt(id,"profile.get",profile,null,base(id,"profile.get",profile,null)); }
    public Attempt start(String id, String profile, String machine, Path machinePath, boolean fresh) {
        return start(id, profile, machine, machinePath, fresh, false);
    }
    public Attempt start(String id, String profile, String machine, Path machinePath, boolean fresh, boolean withCluster) {
        Objects.requireNonNull(profile); if ((machine == null) == (machinePath == null)) throw new IllegalArgumentException("choose one prepared machine");
        JsonObject j = base(id,"session.start",profile,null); if (machine != null) { required(machine); j.addProperty("machine",machine); }
        else { if (!machinePath.isAbsolute()) throw new IllegalArgumentException("absolute machine path required"); j.addProperty("machine_path",machinePath.toString()); }
        j.addProperty("scenario",fresh ? "fresh" : "resume"); j.addProperty("boot","auto");
        j.addProperty("background",true); j.addProperty("with_cluster",withCluster);
        return attempt(id,"session.start",profile,null,j);
    }
    public Attempt status(String id, String profile, Session s) { Objects.requireNonNull(profile); Objects.requireNonNull(s); return attempt(id,"session.status",profile,s,base(id,"session.status",profile,s)); }
    public Attempt systemRead(String id, String profile, Session session, int timeoutMs) {
        if (timeoutMs < 0 || timeoutMs > 60000) throw new IllegalArgumentException("system observation timeout");
        return observation(id, "multimedia.system", profile, session, timeoutMs);
    }
    public Attempt canRead(String id, String profile, Session session, int timeoutMs) {
        if (timeoutMs < 1 || timeoutMs > 10000) throw new IllegalArgumentException("CAN observation timeout");
        return observation(id, "vehicle.can.get", profile, session, timeoutMs);
    }
    public Attempt outingCanRead(String id, String profile, Session session) {
        JsonObject request = base(id, "vehicle.can.get", profile, Objects.requireNonNull(session));
        var states = new com.google.gson.JsonArray();
        states.add("DRIVING_MODE_SET");
        request.add("states", states);
        request.addProperty("timeout_ms", 5000);
        return attempt(id, "vehicle.can.get", profile, session, request);
    }

    /** Actual sealed closed-ECU capability/source identity, before any product or fixture mutation. */
    public Attempt readCanHistory(String id,String profile,Session session,int timeoutMs) {
        if(timeoutMs<1||timeoutMs>15000)throw new IllegalArgumentException("CAN boundary timeout");return observation(id,"vehicle.can.history",profile,session,timeoutMs);
    }
    public Attempt phoneRead(String id, String profile, Session session) {
        return observation(id, "multimedia.phone.state", profile, session, null);
    }
    /** Only the existing QNX PW mask stimulus; not a general vehicle mutator. */
    public Attempt setPwSoundFixture(String id,String profile,Session session,boolean enabled,long revision,long generation) {
        Objects.requireNonNull(profile);Objects.requireNonNull(session).requireOwner();
        if(revision<0||revision>=0xffffffffL||generation<1||generation>0xffffffffL)throw new IllegalArgumentException("bounded vehicle revision/QNX generation required");
        JsonObject request=base(id,"vehicle.set",profile,session),values=new JsonObject();values.addProperty("sound_enable",enabled?1:0);request.add("values",values);
        request.addProperty("expected_revision",revision);request.addProperty("expected_generation",generation);return attempt(id,"vehicle.set",profile,session,request);
    }
    public Attempt setOutingSpeed(String id, String profile, Session session, int speed, long revision) {
        Objects.requireNonNull(session).requireOwner();
        if (speed < 0 || speed > 255 || revision < 0 || revision >= 0xffffffffL)
            throw new IllegalArgumentException("bounded Outing speed and vehicle revision required");
        JsonObject request = base(id, "vehicle.set", Objects.requireNonNull(profile), session);
        JsonObject values = new JsonObject();
        values.addProperty("speed", speed);
        request.add("values", values);
        request.addProperty("expected_revision", revision);
        return attempt(id, "vehicle.set", profile, session, request);
    }

    public Attempt vehicleRead(String id, String profile, Session session) {
        return observation(id, "vehicle.get", profile, session, null);
    }
    private Attempt observation(String id, String operation, String profile, Session session, Integer timeoutMs) {
        Objects.requireNonNull(profile); Objects.requireNonNull(session);
        JsonObject request = base(id, operation, profile, session);
        if (timeoutMs != null) request.addProperty("timeout_ms", timeoutMs);
        return attempt(id, operation, profile, session, request);
    }
    /** Observe the server's Android readiness predicate without changing guest state. */
    public Attempt androidReady(String id, String profile, Session session, int timeoutMs) {
        Objects.requireNonNull(profile); Objects.requireNonNull(session);
        if (timeoutMs < 1 || timeoutMs > 60000) throw new IllegalArgumentException("timeout");
        JsonObject request = base(id, "session.wait", profile, session);
        request.addProperty("predicate", "android.ready"); request.addProperty("timeout_ms", timeoutMs);
        return attempt(id, "session.wait", profile, session, request);
    }
    public Attempt qnxDelivery(String id, String profile, Session session, int timeoutMs) {
        if (timeoutMs < 1 || timeoutMs > 60000) throw new IllegalArgumentException("timeout");
        JsonObject request = base(id, "session.wait", profile, session);
        request.addProperty("predicate", "qnx.delivery");
        request.addProperty("revision", 1);
        request.addProperty("timeout_ms", timeoutMs);
        return attempt(id, "session.wait", profile, session, request);
    }

    /** Read-only Android UI tree observation. */
    public Attempt multimediaUi(String id, String profile, Session s, List<String> screens, String scope, String resourceId, String resourcePackage, int timeoutMs) {
        Objects.requireNonNull(profile); Objects.requireNonNull(s); Objects.requireNonNull(screens);
        if (screens.isEmpty() || screens.size() > 2) throw new IllegalArgumentException("one or two Android screens");
        if (new HashSet<>(screens).size() != screens.size() || !Set.of("primary","secondary").containsAll(screens)) throw new IllegalArgumentException("screens must be distinct primary/secondary names");
        if (scope != null && !Set.of("all_attached","interactive").contains(scope)) throw new IllegalArgumentException("unsupported UI scope");
        if (timeoutMs < 1 || timeoutMs > 30000) throw new IllegalArgumentException("timeout");
        JsonObject j = base(id,"session.ui",profile,s);
        JsonArray list = new JsonArray(); for (String screen : screens) { required(screen); list.add(screen); }
        j.add("screens", list);
        if (scope != null) j.addProperty("scope", scope);
        if (resourceId != null) {
            int separator = resourceId.indexOf(":id/");
            if (separator < 1 || !isIdentifier(resourceId.substring(0, separator), true) || !isIdentifier(resourceId.substring(separator + 4), false)) throw new IllegalArgumentException("fully qualified resource ID required");
            JsonObject selector = new JsonObject(); selector.addProperty("resource_id", resourceId);
            if (resourcePackage != null) { if (!isIdentifier(resourcePackage, true)) throw new IllegalArgumentException("package identifier required"); selector.addProperty("package", resourcePackage); }
            j.add("selector", selector);
        } else if (resourcePackage != null) throw new IllegalArgumentException("selector requires resource_id");
        j.addProperty("timeout_ms", timeoutMs);
        return attempt(id,"session.ui",profile,s,j);
    }
    public static JsonObject readResult(Attempt attempt, JsonObject result) throws IOException {
        if (!"session.ui".equals(attempt.op())) return result;
        JsonArray screens = parseObject(attempt.json()).getAsJsonArray("screens");
        List<String> selected = new ArrayList<>();
        for (JsonElement screen : screens) selected.add(screen.getAsString());
        return SessionUi.multimedia(result, attempt.profile, attempt.session.id(), selected);
    }

    static boolean isIdentifier(String value, boolean pkg) {
        if (value.isEmpty()) return false;
        for (char c : value.toCharArray()) { boolean letter = (c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9')||c=='_'; if (!letter && !(pkg && c=='.')) return false; }
        return true;
    }
    public Attempt launch(String id, String profile, Session session, String packageName, String bootId, String screen) {
        Objects.requireNonNull(session).requireOwner();
        if (packageName == null || packageName.length() > 255
                || !packageName.matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")
                || bootId == null || !bootId.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                || !Set.of("primary", "secondary").contains(screen)) throw new IllegalArgumentException("fenced Android application target required");
        JsonObject request = base(id, "multimedia.launch", Objects.requireNonNull(profile), session);
        request.addProperty("package", packageName); request.addProperty("boot_id", bootId); request.addProperty("screen", screen);
        return attempt(id, "multimedia.launch", profile, session, request);
    }
    /** A retained-authority power cycle of both guests; dispatch requires its own durable journal. */
    public Attempt restartBoth(String id, String profile, Session session, JsonObject expected, int timeoutMs) {
        return restartBoth(id,profile,session,expected,null,timeoutMs);
    }
    public Attempt restartBoth(String id,String profile,Session session,JsonObject expected,JsonObject can,int timeoutMs) {
        Objects.requireNonNull(profile); Objects.requireNonNull(session).requireOwner();
        if (timeoutMs < 1 || timeoutMs > 180000) throw new IllegalArgumentException("restart timeout");
        validateRestartExpected(expected);
        JsonObject request=base(id,"session.restart",profile,session);request.add("expected_boot",expected.deepCopy());request.addProperty("timeout_ms",timeoutMs);if(can!=null){try{CanRestartBoundary.expected(can, expected.get("android").getAsString(), session.id());}catch(IOException invalid){throw new IllegalArgumentException(invalid);}request.add("expected_can",can.deepCopy());}
        return attempt(id,"session.restart",profile,session,request);
    }
    static void validateRestartExpected(JsonObject expected) {
        if(expected==null||!expected.keySet().equals(Set.of("android","qnx_generation","qnx_pid"))||!string(expected,"android")||!expected.get("android").getAsString().matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"))throw new IllegalArgumentException("expected Android/QNX boots required");
        restartGeneration(expected.get("qnx_generation"));restartPid(expected.get("qnx_pid"));
    }
    private static java.math.BigInteger restartGeneration(JsonElement value) {
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()||!value.getAsString().matches("[1-9][0-9]{0,19}"))throw new IllegalArgumentException("canonical QNX generation required");
        java.math.BigInteger result=new java.math.BigInteger(value.getAsString());if(result.bitLength()>64)throw new IllegalArgumentException("QNX generation overflow");return result;
    }
    private static long restartPid(JsonElement value) {
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber()||!value.getAsString().matches("[1-9][0-9]{0,18}"))throw new IllegalArgumentException("QNX process identity required");
        long result=Long.parseLong(value.getAsString());if(result<2)throw new IllegalArgumentException("QNX process identity required");return result;
    }
    /** Validate the closed receipt; this does not establish application readiness or machine identity. */
    static JsonObject restartReceipt(JsonObject receipt,Attempt original,String operationId)throws IOException {
        try {
            JsonObject expected=parseObject(original.json()).getAsJsonObject("expected_boot");validateRestartExpected(expected);
            if(!"session.restart".equals(original.op())||receipt==null||!string(receipt,"session_id")||!original.session().id().equals(receipt.get("session_id").getAsString())||!string(receipt,"request_id")||!original.requestId().equals(receipt.get("request_id").getAsString())||!string(receipt,"operation_id")||!operationId.equals(receipt.get("operation_id").getAsString()))throw new IOException("restart action correlation differs");
            for(String key:List.of("ok","accepted","authority_retained","old_processes_stopped","guest_acknowledged"))if(!bool(receipt,key)||!receipt.get(key).getAsBoolean())throw new IOException("restart completion is unverified");
            if(!"completed".equals(receipt.get("status").getAsString())||!"cold".equals(receipt.get("mode").getAsString())||!"android_qnx_cold_boot_changed".equals(receipt.get("scope").getAsString()))throw new IOException("restart scope/status differs");
            JsonObject before=receipt.getAsJsonObject("before"),after=receipt.getAsJsonObject("after"),beforeQnx=before.getAsJsonObject("qnx"),afterQnx=after.getAsJsonObject("qnx");
            if(!expected.get("android").equals(before.get("android_boot_id"))||!expected.get("qnx_generation").equals(beforeQnx.get("generation"))||restartPid(expected.get("qnx_pid"))!=restartPid(beforeQnx.get("qemu_pid"))||!string(after,"android_boot_id")||!after.get("android_boot_id").getAsString().matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")||after.get("android_boot_id").equals(before.get("android_boot_id"))||!"owned_cold_incarnation".equals(afterQnx.get("kind").getAsString())||restartGeneration(afterQnx.get("generation")).compareTo(restartGeneration(beforeQnx.get("generation")))<=0)throw new IOException("both fresh cold boot identities required");
            restartPid(afterQnx.get("qemu_pid"));JsonObject request=parseObject(original.json());if(request.has("expected_can"))CanRestartBoundary.restarted(receipt,expected,request.getAsJsonObject("expected_can"));return receipt.deepCopy();
        }catch(RuntimeException malformed){throw new IOException("malformed full restart receipt",malformed);}
    }
    /** Observe only the original QNX framebuffer; no Android-only age option or guest mutation. */
    public Attempt captureCluster(String id,String profile,Session session,Path directory,long afterSequence) {
        Objects.requireNonNull(profile);Objects.requireNonNull(session);
        if(directory==null||!directory.isAbsolute()||!directory.equals(directory.normalize())||directory.toString().codePoints().anyMatch(Character::isISOControl)||afterSequence<0)throw new IllegalArgumentException("bounded cluster capture path/sequence required");
        JsonObject request=base(id,"session.screenshot",profile,session);JsonArray screens=new JsonArray();screens.add("cluster");request.add("screens",screens);request.addProperty("artifact_dir",directory.toString());request.addProperty("after_sequence",afterSequence);request.addProperty("timeout_ms",10000);
        return attempt(id,"session.screenshot",profile,session,request);
    }
    /** Exact source-pixel region, without padding or host resampling. */
    public record ClusterRegion(int x, int y, int width, int height) {
        public ClusterRegion {
            if (x < 0 || y < 0 || width < 1 || height < 1
                    || (long) x + width > 1920 || (long) y + height > 720)
                throw new IllegalArgumentException("cluster region outside source raster");
        }
    }
    public Attempt captureCluster(String id, String profile, Session session, Path directory,
            long afterSequence, ClusterRegion region) {
        Objects.requireNonNull(region);
        if (afterSequence < 1) throw new IllegalArgumentException("fresh cluster region boundary required");
        Attempt full = captureCluster(id, profile, session, directory, afterSequence);
        JsonObject request = JsonParser.parseString(full.json()).getAsJsonObject();
        JsonObject rect = new JsonObject();
        rect.addProperty("x", region.x());
        rect.addProperty("y", region.y());
        rect.addProperty("width", region.width());
        rect.addProperty("height", region.height());
        request.add("rect", rect);
        return attempt(id, "session.screenshot", profile, session, request);
    }
    /** Full original Android frame for independent raster/OEM evidence; readonly and age bounded. */
    public Attempt capturePrimary(String id,String profile,Session session,Path directory) {
        Objects.requireNonNull(profile);Objects.requireNonNull(session);
        if(directory==null||!directory.isAbsolute()||!directory.equals(directory.normalize())||directory.toString().codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("new absolute primary capture path required");
        JsonObject request=base(id,"session.screenshot",profile,session);JsonArray screens=new JsonArray();screens.add("primary");request.add("screens",screens);request.addProperty("artifact_dir",directory.toString());request.addProperty("timeout_ms",10000);request.addProperty("max_age_ms",500);
        return attempt(id,"session.screenshot",profile,session,request);
    }
    public Attempt reboot(String id, String profile, Session s, boolean cold, int timeoutMs) {
        Objects.requireNonNull(profile); Objects.requireNonNull(s).requireOwner(); if (timeoutMs < 1 || timeoutMs > 180000) throw new IllegalArgumentException("timeout");
        JsonObject j=base(id,"multimedia.reboot",profile,s); j.addProperty("mode",cold ? "cold" : "warm"); j.addProperty("timeout_ms",timeoutMs); return attempt(id,"multimedia.reboot",profile,s,j);
    }
    public Attempt tap(String id, String profile, Session session, UiTarget target) {
        Objects.requireNonNull(session).requireOwner(); Objects.requireNonNull(target).requireScope(profile, session);
        target.requireAction(false);
        JsonObject request = base(id, "input.tap", profile, session);
        request.addProperty("screen", target.screen());
        if (target.inputScope() != null) request.addProperty("scope", target.inputScope());
        if (target.selection() == null) {
            request.addProperty("x", target.x()); request.addProperty("y", target.y());
        } else request.add("selector", target.selection());
        request.add("expected", target.expected());
        return attempt(id, "input.tap", profile, session, request);
    }
    /** One reviewed IME press; gesture fencing permits the non-clickable observed keyboard view. */
    public Attempt tapKeyboard(String id, String profile, Session session, UiTarget target,
            ReviewedKeyboard.Match match, long androidPid) throws IOException {
        Objects.requireNonNull(session).requireOwner();
        ReviewedKeyboard.Point point = Objects.requireNonNull(match).claim(target, profile, session, androidPid);
        JsonObject request = base(id, "input.gesture", profile, session);
        request.addProperty("screen", target.screen());
        if (target.inputScope() != null) request.addProperty("scope", target.inputScope());
        JsonArray points = new JsonArray();
        for (int time : new int[] {0, 80}) {
            JsonObject entry = new JsonObject(); entry.addProperty("x", point.x()); entry.addProperty("y", point.y());
            entry.addProperty("at_ms", time); points.add(entry);
        }
        request.add("points", points); request.add("expected", target.expected());
        ReviewedKeyboard.validateGesture(request);
        return attempt(id, "input.gesture", profile, session, request);
    }
    /** Host artifact creation only; the API requires a new private path under its own project build/tmp. */
    public Attempt captureKeyboard(String id,String profile,Session session,UiTarget canvas,Path directory,int maxAgeMs) {
        Objects.requireNonNull(session);canvas.requireScope(profile,session);canvas.requireCanvas();
        if(directory==null || !directory.isAbsolute() || !directory.equals(directory.normalize()) || directory.toString().codePoints().anyMatch(Character::isISOControl)
                || maxAgeMs<1 || maxAgeMs>500) throw new IllegalArgumentException("new bounded keyboard capture path/age required");
        JsonObject expected=canvas.expected(),target=expected.getAsJsonObject("target"),selector=new JsonObject();
        selector.add("resource_id",target.get("resource_id"));selector.add("package",target.get("package"));
        JsonObject request=base(id,"session.screenshot",profile,session);JsonArray screens=new JsonArray();screens.add("primary");request.add("screens",screens);
        request.add("selector",selector);request.add("expected",expected);
        request.addProperty("artifact_dir",directory.toString());request.addProperty("timeout_ms",10000);request.addProperty("max_age_ms",maxAgeMs);
        return attempt(id,"session.screenshot",profile,session,request);
    }
    public Attempt closeVehicleDrawer(String id,String profile,Session session,UiTarget target) {
        Objects.requireNonNull(session).requireOwner();target.requireScope(profile,session);target.requireDrawerClose();JsonObject request=base(id,"input.gesture",profile,session);request.addProperty("screen","primary");if(target.inputScope()!=null)request.addProperty("scope",target.inputScope());request.add("expected",target.expected());request.add("points",target.closePoints());return attempt(id,"input.gesture",profile,session,request);
    }
    public Attempt openVehicleDrawer(String id,String profile,Session session,UiTarget target) {
        Objects.requireNonNull(session).requireOwner();target.requireScope(profile,session);target.requireDrawer();
        JsonObject request=base(id,"input.gesture",profile,session);request.addProperty("screen","primary");if(target.inputScope()!=null)request.addProperty("scope",target.inputScope());request.add("expected",target.expected());request.add("points",DrawerGesture.points(target.expected(),target.viewportWidth()));
        return attempt(id,"input.gesture",profile,session,request);
    }
    public Attempt pageLauncher(String id,String profile,Session session,UiTarget grid,boolean next) {
        Objects.requireNonNull(session).requireOwner();grid.requireScope(profile,session);grid.requirePage();
        JsonObject request=base(id,"input.gesture",profile,session);request.addProperty("screen","primary");request.addProperty("scope","all_attached");
        request.add("expected",grid.expected());request.add("points",LauncherPageSwipe.points(grid.expected(),next));
        LauncherPageSwipe.validate(request);return attempt(id,"input.gesture",profile,session,request);
    }
    public Attempt dragLauncher(String id, String profile, Session session, UiTarget target, int page,
            List<Integer> item, List<Integer> first, List<Integer> grid) {
        Objects.requireNonNull(session).requireOwner(); target.requireScope(profile, session); target.requireDrag();
        JsonObject request = base(id, "input.gesture", profile, session);
        request.addProperty("screen", "primary"); request.addProperty("scope", "all_attached"); request.addProperty("intent", "drag");
        request.add("expected", target.expected()); request.add("points", LauncherDrag.points(page, item, first, grid));
        LauncherDrag.validate(request); return attempt(id,"input.gesture",profile,session,request);
    }
    public Attempt scrollSettings(String id, String profile, Session session, UiTarget target) {
        return scrollSettings(id, profile, session, target, false);
    }
    public Attempt scrollSettings(String id, String profile, Session session, UiTarget target, boolean towardStart) {
        Objects.requireNonNull(session).requireOwner(); target.requireScope(profile, session); target.requireAction(true);
        JsonObject request = base(id, "input.gesture", profile, session);
        request.addProperty("screen", target.screen());
        if (target.inputScope() != null) request.addProperty("scope", target.inputScope());
        request.addProperty("intent", "scroll"); request.add("expected", target.expected());
        request.add("points", settingsScrollPoints(target.expected(), towardStart));
        return attempt(id, "input.gesture", profile, session, request);
    }
    private static com.google.gson.JsonArray settingsScrollPoints(JsonObject expected, boolean towardStart) {
        JsonObject target = expected.getAsJsonObject("target"), window = expected.getAsJsonObject("window");
        if (!UiTarget.validScroll(target,window)) throw new IllegalArgumentException("Settings scroll target differs");
        JsonObject bounds = target.getAsJsonObject("clipped_bounds");
        int x = bounds.get("x").getAsInt(), y = bounds.get("y").getAsInt();
        int width = bounds.get("width").getAsInt(), height = bounds.get("height").getAsInt();
        if (x < 0 || y < 0 || width < 2 || height < 8 || width > 3840 || height > 2160)
            throw new IllegalArgumentException("Settings scroll geometry differs");
        var points = new com.google.gson.JsonArray();
        for (int index = 0; index <= 10; ++index) {
            JsonObject point = new JsonObject(); point.addProperty("x", x + width / 2);
            point.addProperty("y", towardStart ? y + height / 4 + height * index / 20 : y + height * 3 / 4 - height * index / 20);
            point.addProperty("at_ms", index * 30); points.add(point);
        }
        return points;
    }
    /** One stock five-tap recovery gesture, restricted to its observed OEM Settings row. */
    public Attempt recoverSettings(String id, String profile, Session session, UiTarget target) {
        Attempt single = tap(id, profile, session, target);
        JsonObject request = com.google.gson.JsonParser.parseString(single.json()).getAsJsonObject();
        requireSettingsBurstTarget(request, Set.of("kv_tbox_mpu_res_version"));
        return settingsBurst(id, profile, session, request);
    }
    /** One stock engineering-menu gesture on an observed hardware or software version row. */
    public Attempt openSettingsEngineeringMenu(String id, String profile, Session session, UiTarget target) {
        Attempt single = tap(id, profile, session, target);
        JsonObject request = com.google.gson.JsonParser.parseString(single.json()).getAsJsonObject();
        requireSettingsBurstTarget(request, Set.of("kv_hw_version", "kv_sw_version"));
        return settingsBurst(id, profile, session, request);
    }
    private Attempt settingsBurst(String id, String profile, Session session, JsonObject request) {
        request.addProperty("count", 5);
        request.addProperty("interval_ms", 100);
        return attempt(id, "input.tap", profile, session, request);
    }
    private static void requireSettingsBurstTarget(JsonObject request, Set<String> rows) {
        JsonObject expected = request.getAsJsonObject("expected"), target = expected.getAsJsonObject("target"), window = expected.getAsJsonObject("window");
        if (!"primary".equals(request.get("screen").getAsString()) || request.has("selector")
                || rows.stream().noneMatch(row -> ("com.qinggan.app.setting:id/" + row).equals(target.get("resource_id").getAsString()))
                || !"com.qinggan.app.setting".equals(target.get("package").getAsString())
                || !"android.widget.LinearLayout".equals(target.get("class").getAsString())
                || !"com.qinggan.app.setting".equals(window.get("package").getAsString())
                || !window.get("type").isJsonPrimitive() || !window.getAsJsonPrimitive("type").isNumber() || !"1".equals(window.get("type").getAsString()))
            throw new IllegalArgumentException("five-tap input requires an exact observed stock Settings version row");
    }
    /** One SDK Power press; guest power and CAN effects must be independently observed. */
    public Attempt powerKey(String id,String profile,Session session) {
        Objects.requireNonNull(profile);Objects.requireNonNull(session).requireOwner();
        JsonObject request=base(id,"input.key.press",profile,session);request.addProperty("key","Power");
        return attempt(id,"input.key.press",profile,session,request);
    }
    static boolean mutation(Attempt attempt) { return MUTATIONS.contains(attempt.op); }
    public Attempt stop(String id, String profile, Session s) { Objects.requireNonNull(profile); Objects.requireNonNull(s).requireOwner(); return attempt(id,"session.stop",profile,s,base(id,"session.stop",profile,s)); }
    public Attempt lookup(String id, Attempt original, String operationId) {
        Objects.requireNonNull(original); if (original.profile == null) throw new IllegalArgumentException("lookup needs original scope");
        if (id.equals(original.requestId)) throw new IllegalArgumentException("lookup needs its own request id");
        JsonObject j=base(id,"operation.get",original.profile,original.op.equals("session.start") ? null : original.session);
        if (!original.op.equals("session.start") && original.session == null) throw new IllegalArgumentException("session scope required");
        JsonObject target=new JsonObject(); if (operationId == null) target.addProperty("request_id",original.requestId); else { token(operationId); target.addProperty("operation_id",operationId); } j.add("target",target);
        return attempt(id,"operation.get",original.profile,original.session,j);
    }
    /** Build only a read-only lookup after checking an existing reservation against the original attempt.
     * The identity is a caller-provided label, not proof of the remote machine identity.
     */
    public Attempt lookupReserved(String lookupId, voyemu.journal.Journal journal, String identity, Attempt original) throws IOException {
        Objects.requireNonNull(journal); Objects.requireNonNull(original);
        if (!MUTATIONS.contains(original.op)) throw new IllegalArgumentException("mutation required");
        String scope = original.op.equals("session.start") ? "profile:" + original.profile : "session:" + original.session.id();
        voyemu.journal.Journal.Record record = journal.inspect(scope, identity);
        if (!record.requestId.equals(original.requestId) || !Arrays.equals(record.request(), original.json.getBytes(StandardCharsets.UTF_8))) throw new IOException("reservation does not match original request");
        return lookup(lookupId, original, null);
    }
    /** Reconstruct only a lookup capability; a retained request can never be dispatched again. */
    public Attempt reserved(voyemu.journal.Journal journal, String scope, String identity) throws IOException {
        voyemu.journal.Journal.Record record = journal.inspect(scope, identity);
        String raw = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(record.request())).toString();
        JsonObject input = parseObject(raw);
        try {
            String operation = input.get("op").getAsString(), profile = input.get("profile").getAsString();
            if (!record.requestId.matches("[0-9a-f]{32}") || !record.requestId.equals(input.get("request_id").getAsString()))
                throw new IOException("reserved request identity differs");
            Attempt validated;
            if (operation.equals("session.start")) {
                String machine = input.has("machine") ? input.get("machine").getAsString() : null;
                Path path = input.has("machine_path") ? Path.of(input.get("machine_path").getAsString()) : null;
                String scenario = input.get("scenario").getAsString();
                if (!Set.of("fresh", "resume").contains(scenario)) throw new IOException("reserved scenario differs");
                validated = start(record.requestId, profile, machine, path, scenario.equals("fresh"),
                    input.get("with_cluster").getAsBoolean());
            } else {
                JsonObject session = input.getAsJsonObject("session");
                Session owned = new Session(session.get("id").getAsString(), session.get("ownership_id").getAsString());
                if (operation.equals("session.stop")) validated = stop(record.requestId, profile, owned);
                else if (operation.equals("multimedia.reboot")) {
                    String mode = input.get("mode").getAsString();
                    if (!Set.of("warm", "cold").contains(mode)) throw new IOException("reserved reboot mode differs");
                    validated = reboot(record.requestId, profile, owned, mode.equals("cold"), input.get("timeout_ms").getAsInt());
                } else if (operation.equals("session.restart")) {
                    validated=restartBoth(record.requestId,profile,owned,input.getAsJsonObject("expected_boot"),input.has("expected_can")?input.getAsJsonObject("expected_can"):null,input.get("timeout_ms").getAsInt());
                } else if(operation.equals("vehicle.set")) {
                    JsonObject values=input.getAsJsonObject("values");
                    if(values==null||!values.keySet().equals(Set.of("sound_enable")))throw new IOException("retained vehicle fixture field differs");
                    long value=PwSoundFixture.number(values,"sound_enable",1);
                    validated=setPwSoundFixture(record.requestId,profile,owned,value==1,PwSoundFixture.number(input,"expected_revision",0xfffffffeL),PwSoundFixture.number(input,"expected_generation",0xffffffffL));
                } else if (operation.equals("input.key.press")) {
                    validated=powerKey(record.requestId,profile,owned);
                } else if (operation.equals("multimedia.launch")) {
                    validated = launch(record.requestId, profile, owned, input.get("package").getAsString(),
                        input.get("boot_id").getAsString(), input.get("screen").getAsString());
                } else if (operation.equals("input.tap")) {
                    // The API scope is present only for an all_attached observation; an omitted scope means the default source.
                    Set<String> coordinate = input.has("scope")
                        ? Set.of("schema", "request_id", "op", "profile", "session", "screen", "scope", "x", "y", "expected")
                        : Set.of("schema", "request_id", "op", "profile", "session", "screen", "x", "y", "expected");
                    Set<String> selector = input.has("scope")
                        ? Set.of("schema", "request_id", "op", "profile", "session", "screen", "scope", "selector", "expected")
                        : Set.of("schema", "request_id", "op", "profile", "session", "screen", "selector", "expected");
                    Set<String> burst = new HashSet<>(coordinate); burst.add("count"); burst.add("interval_ms");
                    boolean repeated = input.keySet().equals(burst);
                    if ((!input.keySet().equals(coordinate) && !input.keySet().equals(selector) && !repeated)
                            || !input.get("expected").isJsonObject() || !input.getAsJsonObject("expected").keySet().equals(
                                Set.of("boot_id", "source_instance", "revision", "window", "target")))
                        throw new IOException("retained UI request shape differs");
                    if (repeated) {
                        for (var parameter : java.util.Map.of("count", "5", "interval_ms", "100").entrySet()) {
                            JsonElement value = input.get(parameter.getKey());
                            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !parameter.getValue().equals(value.getAsString()))
                                throw new IOException("retained Settings burst parameters differ");
                        }
                        requireSettingsBurstTarget(input, Set.of("kv_tbox_mpu_res_version", "kv_hw_version", "kv_sw_version"));
                    }
                    validated = new Attempt(record.requestId, operation, profile, owned, raw, false);
                } else if (operation.equals("input.gesture")) {
                    if (!input.has("intent")) {
                        JsonObject selected=input.getAsJsonObject("expected").getAsJsonObject("target");
                        if(selected.has("resource_id") && (VehicleDrawer.OWNER+":id/line_view").equals(selected.get("resource_id").getAsString()))DrawerGesture.validate(input);
                        else if(selected.has("package")&&VehicleDrawer.OWNER.equals(selected.get("package").getAsString())&&selected.has("class")&&"android.widget.FrameLayout".equals(selected.get("class").getAsString()))DrawerGesture.validateClose(input);
                        else if(selected.has("resource_id") && selected.get("resource_id").getAsString().endsWith(":id/alpha_view1")) ReviewedKeyboard.validateGesture(input);
                        else LauncherPageSwipe.validate(input);
                    } else if ("drag".equals(input.get("intent").getAsString())) {
                        LauncherDrag.validate(input);
                    } else {
                    if (!input.keySet().equals(input.has("scope")
                                ? Set.of("schema", "request_id", "op", "profile", "session", "screen", "scope", "intent", "expected", "points")
                                : Set.of("schema", "request_id", "op", "profile", "session", "screen", "intent", "expected", "points"))
                            || !"primary".equals(input.get("screen").getAsString())
                            || (input.has("scope") && !"all_attached".equals(input.get("scope").getAsString()))
                            || !"scroll".equals(input.get("intent").getAsString())
                            || !settingsScrollPoints(input.getAsJsonObject("expected"), false).equals(input.get("points"))
                                && !settingsScrollPoints(input.getAsJsonObject("expected"), true).equals(input.get("points")))
                        throw new IOException("retained Settings scroll differs");
                    }
                    validated = new Attempt(record.requestId, operation, profile, owned, raw, false);
                } else throw new IOException("reserved operation is not supported");
            }
            if (!scope.equals(operation.equals("session.start") ? "profile:" + profile : "session:" + validated.session.id())
                    || !parseObject(validated.json).equals(input)) throw new IOException("reserved request shape or scope differs");
            return new Attempt(record.requestId, validated.op, validated.profile, validated.session, raw, false);
        } catch (RuntimeException malformed) { throw new IOException("invalid reserved request", malformed); }
    }

    /** No lookup result establishes machine identity or permission to replay a mutation. */
    public enum LookupAssessment { INVALID, UNVERIFIED }
    /** Report-only classification of a reserved lookup record. No class authorizes replaying the original mutation. */
    public enum LookupRecord { INVALID, PENDING, SUCCESS, FAILURE, UNKNOWN }
    public static LookupAssessment assessReservedLookup(Reply reply, Attempt original) {
        Objects.requireNonNull(reply); Objects.requireNonNull(original);
        try {
            Attempt query = reply.attempt();
            JsonObject request = parseObject(query.json);
            String scope = original.op.equals("session.start") ? "profile:" + original.profile : "session:" + original.session.id();
            if (!MUTATIONS.contains(original.op)
                || !query.op.equals("operation.get") || !Objects.equals(query.profile, original.profile)
                || (!original.op.equals("session.start") && !Objects.equals(query.session, original.session))
                || !request.has("target") || !request.get("target").isJsonObject()
                || !string(request.getAsJsonObject("target"), "request_id")
                || !original.requestId.equals(request.getAsJsonObject("target").get("request_id").getAsString())) return LookupAssessment.INVALID;
            if (reply.state() != State.RESPONSE || reply.response() == null) return LookupAssessment.UNVERIFIED;
            JsonObject envelope = reply.response();
            if (!bool(envelope, "ok") || !envelope.get("ok").getAsBoolean()) return LookupAssessment.UNVERIFIED;
            if (!envelope.has("result") || !envelope.get("result").isJsonObject()) return LookupAssessment.INVALID;
            JsonObject record = envelope.getAsJsonObject("result");
            if (!string(record,"operation_id") || !record.get("operation_id").getAsString().matches("[0-9a-f]{32}") || !string(record,"status")
                || !Set.of("pending", "unknown", "terminal").contains(record.get("status").getAsString())) return LookupAssessment.INVALID;
            for (String key : new String[] {"request_id", "op", "scope"}) {
                if (record.has(key)) {
                    String expected = key.equals("request_id") ? original.requestId : key.equals("op") ? original.op : scope;
                    if (!string(record,key) || !expected.equals(record.get(key).getAsString())) return LookupAssessment.INVALID;
                }
            }
            if (record.has("response")) {
                if (!record.get("response").isJsonObject()) return LookupAssessment.INVALID;
                JsonObject nested = record.getAsJsonObject("response");
                if (!string(nested,"request_id") || !original.requestId.equals(nested.get("request_id").getAsString())
                    || !string(nested,"operation_id") || !record.get("operation_id").getAsString().equals(nested.get("operation_id").getAsString())) return LookupAssessment.INVALID;
            }
            return LookupAssessment.UNVERIFIED;
        } catch (IOException | RuntimeException e) { return LookupAssessment.INVALID; }
    }
    /** Report-only classification of a validated lookup record; never authorizes a replay of the original mutation.
     * A record is only classified when the reserved lookup reply already passed assessReservedLookup,
     * otherwise the classification is INVALID. Server contract (voyemu api.cpp): terminal records carry
     * response + outcome("completed"|"not_started"|"unknown"), non-terminal records carry status pending/unknown. */
    public static LookupRecord classifyReservedLookup(Reply reply, Attempt original) {
        Objects.requireNonNull(reply); Objects.requireNonNull(original);
        if (assessReservedLookup(reply, original) != LookupAssessment.UNVERIFIED) return LookupRecord.INVALID;
        if (reply.state() != State.RESPONSE || reply.response() == null) return LookupRecord.INVALID;
        JsonObject envelope = reply.response();
        // fail closed: an error envelope (ok:false) or a malformed ok key never yields an affirmative record;
        // a valid error reply carries no result, so the classification is UNKNOWN (neither success nor a confirmed failure)
        if (!bool(envelope, "ok")) return LookupRecord.UNKNOWN;
        if (!envelope.get("ok").getAsBoolean()) return LookupRecord.UNKNOWN;
        if (!envelope.has("result") || !envelope.get("result").isJsonObject() || !string(envelope.getAsJsonObject("result"), "status")) return LookupRecord.UNKNOWN;
        JsonObject record = envelope.getAsJsonObject("result");
        if("session.restart".equals(original.op())&&record.has("runtime_receipt")) {
            try {
                String scope="session:"+original.session().id();
                if(!string(record,"request_id")||!original.requestId().equals(record.get("request_id").getAsString())||!string(record,"op")||!original.op().equals(record.get("op").getAsString())||!string(record,"scope")||!scope.equals(record.get("scope").getAsString()))return LookupRecord.INVALID;
                if(string(record,"runtime_outcome")&&"completed".equals(record.get("runtime_outcome").getAsString())) {
                    restartReceipt(record.getAsJsonObject("runtime_receipt"),original,record.get("operation_id").getAsString());
                    if(record.has("response")&&!record.getAsJsonObject("response").get("ok").getAsBoolean()&&record.getAsJsonObject("response").has("error")&&"not_started".equals(record.getAsJsonObject("response").getAsJsonObject("error").get("outcome").getAsString()))return LookupRecord.INVALID;
                    return LookupRecord.SUCCESS;
                }
            }catch(IOException|RuntimeException malformed){return LookupRecord.INVALID;}
        }
        String status = record.get("status").getAsString();
        if ("pending".equals(status)) return record.has("response") ? LookupRecord.UNKNOWN : LookupRecord.PENDING; // admission may have completed while status stayed pending
        if ("unknown".equals(status)) return LookupRecord.UNKNOWN;
        // terminal: response and outcome are mandatory; missing, malformed, inconsistent or unexpected shapes stay unknown/invalid
        if (!"terminal".equals(status)) return LookupRecord.INVALID;
        if (!record.has("response") || !record.get("response").isJsonObject() || !record.has("outcome") || !string(record,"outcome")) return LookupRecord.UNKNOWN;
        JsonObject nested = record.getAsJsonObject("response");
        if (!bool(nested,"ok")) return LookupRecord.UNKNOWN;
        String outcome = record.get("outcome").getAsString();
        if (!nested.get("ok").getAsBoolean()) {
            // only a definitive not_started outcome is a confirmed safe failure; anything else is unknown
            if (!"not_started".equals(outcome)) return LookupRecord.UNKNOWN;
            if (nested.has("error") && nested.get("error").isJsonObject()
                && string(nested.getAsJsonObject("error"), "outcome")
                && !outcome.equals(nested.getAsJsonObject("error").get("outcome").getAsString())) return LookupRecord.UNKNOWN;
            return LookupRecord.FAILURE;
        }
        return "completed".equals(outcome) ? LookupRecord.SUCCESS : LookupRecord.UNKNOWN;
    }
    static JsonObject reservedLookupResult(Reply reply,Attempt original)throws IOException {
        if(classifyReservedLookup(reply,original)!=LookupRecord.SUCCESS)throw new IOException("reserved action has no completed result");
        JsonObject record=reply.response().getAsJsonObject("result");
        if("session.restart".equals(original.op())&&record.has("runtime_receipt"))return restartReceipt(record.getAsJsonObject("runtime_receipt"),original,record.get("operation_id").getAsString());
        JsonObject result=record.getAsJsonObject("response").getAsJsonObject("result");if(result==null)throw new IOException("completed action result missing");return result.deepCopy();
    }
    private static JsonObject parseObject(String json) throws IOException {
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setLenient(false);
        JsonElement value = readValue(reader);
        if (reader.peek() != JsonToken.END_DOCUMENT || !value.isJsonObject()) throw new IOException("expected one JSON object");
        return value.getAsJsonObject();
    }
    private static JsonElement readValue(JsonReader reader) throws IOException {
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                reader.beginObject();
                JsonObject object = new JsonObject();
                Set<String> names = new HashSet<>();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (!names.add(name)) throw new IOException("duplicate JSON key: " + name);
                    object.add(name, readValue(reader));
                }
                reader.endObject();
                return object;
            case BEGIN_ARRAY:
                reader.beginArray();
                JsonArray array = new JsonArray();
                while (reader.hasNext()) array.add(readValue(reader));
                reader.endArray();
                return array;
            case STRING: return new JsonPrimitive(reader.nextString());
            case NUMBER: return new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN: return new JsonPrimitive(reader.nextBoolean());
            case NULL: reader.nextNull(); return JsonNull.INSTANCE;
            default: throw new IOException("unexpected JSON token");
        }
    }
    /** Bounded raw transport output, so a rejected envelope is diagnosable from the evidence file alone. */
    private static String excerpt(String text) {
        if (text == null) return "<none>";
        String bounded = text.length() > 512 ? text.substring(0, 512) + "..." : text;
        return '"' + bounded.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r") + '"';
    }
    private static boolean string(JsonObject j, String key) { return j.has(key) && j.get(key).isJsonPrimitive() && j.getAsJsonPrimitive(key).isString(); }
    private static boolean bool(JsonObject j, String key) { return j.has(key) && j.get(key).isJsonPrimitive() && j.getAsJsonPrimitive(key).isBoolean(); }
    private static boolean number(JsonObject j, String key) { return j.has(key) && j.get(key).isJsonPrimitive() && j.getAsJsonPrimitive(key).isNumber(); }
    public synchronized Reply execute(Attempt a, Duration timeout) throws InterruptedException {
        Objects.requireNonNull(a); if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout");
        if (!READ_OPERATIONS.contains(a.op)) throw new IllegalStateException("operation blocked until durable cross-process admission exists");
        try {
            ProcessResult p=transport.execute(a.json, timeout);            return finish(a, p);
        } catch (IOException | RuntimeException e) { return new Reply(State.UNKNOWN,a,null,null,e.toString()); }
    }
    /** Shared response-correlation validation for one dispatched transport result. */
    private Reply finish(Attempt a, ProcessResult p) throws IOException {
            if (p.timedOut || p.exitCode == null) return new Reply(State.UNKNOWN,a,p.exitCode,null,p.stderr);
            JsonObject j=parseObject(p.stdout);
            if (!number(j,"schema") || !Integer.toString(SCHEMA).equals(j.get("schema").getAsString()) || !string(j,"request_id") || !a.requestId.equals(j.get("request_id").getAsString()) || !string(j,"operation_id") || !j.get("operation_id").getAsString().matches("[0-9a-f]{32}") || !bool(j,"ok") || !number(j,"elapsed_ms") || !Double.isFinite(j.get("elapsed_ms").getAsDouble()) || j.get("elapsed_ms").getAsDouble()<0) throw new IllegalArgumentException("response correlation");
            boolean ok=j.get("ok").getAsBoolean();
            if ((ok && ((p.exitCode != 0 && !readinessDeadline(a, p, j)) || !j.has("result") || !j.get("result").isJsonObject() || j.has("error"))) || (!ok && ((p.exitCode != 3 && p.exitCode != 4) || !j.has("error") || !j.get("error").isJsonObject() || j.has("result"))))
                throw new IllegalArgumentException("response status: exit=" + p.exitCode + " ok=" + ok
                    + " stdout=" + excerpt(p.stdout) + " stderr=" + excerpt(p.stderr));
            if (!ok) { JsonObject e=j.getAsJsonObject("error"); if (!string(e,"code") || !string(e,"message") || !bool(e,"retryable") || !string(e,"outcome") || !Set.of("not_started", "unknown").contains(e.get("outcome").getAsString())) throw new IllegalArgumentException("error envelope"); }
            return new Reply(State.RESPONSE,a,p.exitCode,j,p.stderr);
    }
    private static boolean readinessDeadline(Attempt attempt, ProcessResult process, JsonObject response) {
        if (process.exitCode != 4 || !"session.wait".equals(attempt.op) || attempt.session == null
                || !response.has("result") || !response.get("result").isJsonObject()) return false;
        JsonObject result = response.getAsJsonObject("result");
        JsonObject request = JsonParser.parseString(attempt.json).getAsJsonObject();
        String expected = request.get("predicate").getAsString();
        if (!Set.of("android.ready", "qnx.delivery").contains(expected) || !string(result, "predicate")
                || !expected.equals(result.get("predicate").getAsString())
                || !bool(result, "ready") || result.get("ready").getAsBoolean()
                || !bool(result, "timed_out") || !result.get("timed_out").getAsBoolean()
                || !result.has("last_observation") || !result.get("last_observation").isJsonObject()) return false;
        JsonObject observation = result.getAsJsonObject("last_observation");
        return string(observation, "session_id")
                && attempt.session.id().equals(observation.get("session_id").getAsString());
    }

    /** Mutation dispatch requires proven durable cross-process admission: this process must hold the
     * journal lock and the reservation must contain the exact request id and request bytes for the
     * bound scope/identity. Any mismatch or unreadable reservation fails closed with UNKNOWN and
     * nothing is dispatched. The private process mutation path is reachable only after the
     * journal has durably consumed the exact reservation. */
    public synchronized Reply executeReserved(Attempt a, Duration timeout, voyemu.journal.Journal journal, String identity) throws InterruptedException {
        Objects.requireNonNull(a); Objects.requireNonNull(journal); if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout");
        if (!MUTATIONS.contains(a.op)) throw new IllegalStateException("operation blocked: unknown mutation");
        try {
            if (!a.dispatchable) throw new IOException("retained request permits lookup only");
            String scope = a.op.equals("session.start") ? "profile:" + a.profile : "session:" + Objects.requireNonNull(a.session).id();
            journal.claim(scope, identity, a.requestId, a.json().getBytes(StandardCharsets.UTF_8));
            ProcessResult p = transport instanceof ProcessTransport process
                ? process.executeClaimed(a.json, timeout) : transport.execute(a.json, timeout);
            return finish(a, p);
        } catch (IOException | RuntimeException e) { return new Reply(State.UNKNOWN, a, null, null, "unproven admission: " + e); }
    }
    public static VoyemuClient process(Path executable, Path work) { return new VoyemuClient(new ProcessTransport(executable, work)); }
    /** CLI request and output files live in the caller-owned work directory. */
    private static final class ProcessTransport implements Transport {
        private final Path executable, work;
        public ProcessTransport(Path executable, Path work) { if (!executable.isAbsolute() || !work.isAbsolute()) throw new IllegalArgumentException("absolute paths required"); this.executable=executable; this.work=work; }
        public ProcessResult execute(String request, Duration timeout) throws IOException, InterruptedException {
            return execute(request, timeout, false);
        }
        private ProcessResult executeClaimed(String request, Duration timeout) throws IOException, InterruptedException {
            return execute(request, timeout, true);
        }
        private ProcessResult execute(String request, Duration timeout, boolean claimed) throws IOException, InterruptedException {
        JsonObject requestObject;
        try { requestObject=parseObject(request); } catch (RuntimeException e) { throw new IOException("invalid request",e); }
        Set<String> allowed = claimed ? MUTATIONS : READ_OPERATIONS;
        if (!string(requestObject,"op") || !allowed.contains(requestObject.get("op").getAsString())) throw new IOException("operation blocked before process dispatch");
        if (!Files.isDirectory(work)) throw new IOException("work directory must already exist");
        Path dir=Files.createTempDirectory(work,"voyemu-",java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")));
        Path in=dir.resolve("request.json"), out=dir.resolve("stdout"), err=dir.resolve("stderr");
        Process p=null; boolean terminated=false;
        try {
            Files.createFile(in,java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
            Files.createFile(out,java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
            Files.createFile(err,java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
            Files.writeString(in,request,StandardCharsets.UTF_8);
            p=new ProcessBuilder(executable.toString(),"test","--request",in.toString()).redirectOutput(out.toFile()).redirectError(err.toFile()).start();
            boolean done;
            try { done=p.waitFor(timeout.toMillis(),TimeUnit.MILLISECONDS); }
            catch (InterruptedException e) {
                p.destroyForcibly();
                // Preserve files unless termination is observed. Do not wait indefinitely while interrupted.
                try { terminated=p.waitFor(2,TimeUnit.SECONDS); } catch (InterruptedException again) { e.addSuppressed(again); }
                throw e;
            }
            if (!done) { p.destroyForcibly(); terminated=p.waitFor(2,TimeUnit.SECONDS); }
            else terminated=true;
            return new ProcessResult(done ? p.exitValue() : null,!done,terminated ? read(out, requestObject.get("op").getAsString().equals("session.ui") ? 8 * 1024 * 1024 + 32768 : 1024 * 1024) : "",terminated ? read(err, 1024 * 1024) : "process termination unconfirmed; files retained at " + dir);
        } finally {
            if (p == null || terminated) { Files.deleteIfExists(in); Files.deleteIfExists(out); Files.deleteIfExists(err); Files.deleteIfExists(dir); }
        }
        }
        private static String read(Path p, int maximum) throws IOException { if (!Files.exists(p)) return ""; if (Files.size(p)>maximum) throw new IOException("process output exceeds limit"); return Files.readString(p,StandardCharsets.UTF_8); }
    }
}
