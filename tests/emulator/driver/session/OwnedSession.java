package ru.voboost.voyemu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import ru.voboost.tests.RunFiles;

/** One background Android session, owned and stopped through durable API admissions. */
public final class OwnedSession implements AutoCloseable {
    /** A correlated API response proves that this operation did not execute. */
    public static final class NotStarted extends IOException {
        private final String code;

        private NotStarted(String operation) { this(operation, null); }

        private NotStarted(String operation, String code) {
            super(operation + " rejected before execution" + (code == null ? "" : " (" + code + ")"));
            this.code = code;
        }

        public boolean inputCoverageUnavailable() {
            return "TEST_INPUT_BLOCKED".equals(code);
        }
    }
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(180);
    private final VoyemuClient client;
    private final String profile;
    private final Path machine, work;
    private VoyemuClient.Session session;
    private boolean closeAttempted, uncertainMutation, cleanupVerified;
    private StopCheck stopCheck;
    private int mutationSequence, readSequence;
    private String evidencePrefix = "";
    private JsonObject lastReboot;
    private JsonObject lastRestart;
    private JsonObject retainedStop;

    @FunctionalInterface public interface StopCheck { void check() throws IOException; }

    /** Product operation ownership is installed once and cannot be replaced by a later scenario. */
    public synchronized void beforeStop(StopCheck check) throws IOException {
        if (closeAttempted || stopCheck != null || check == null) throw new IOException("stop guard already installed or session closing");
        stopCheck = check;
    }

    private OwnedSession(VoyemuClient client, String profile, Path machine, Path work) {
        this.client = client; this.profile = profile; this.machine = machine; this.work = work;
    }

    public static OwnedSession start(VoyemuClient client, String profile, Path machine, Path work, boolean fresh)
            throws IOException, InterruptedException {
        return start(client, profile, machine, work, fresh, false);
    }

    public static OwnedSession start(VoyemuClient client, String profile, Path machine, Path work, boolean fresh, boolean withCluster)
            throws IOException, InterruptedException {
        if (!machine.isAbsolute() || !work.isAbsolute() || !Files.isDirectory(work))
            throw new IllegalArgumentException("absolute prepared machine and existing work directory required");
        Path canonicalMachine = machine.toRealPath();
        JsonObject manifest = JsonParser.parseString(Files.readString(canonicalMachine.resolve("runtime.json"))).getAsJsonObject();
        if (!profile.equals(text(manifest, "profile")) || !"portable-guest-runtime".equals(text(manifest, "kind")))
            throw new IOException("prepared machine profile differs");
        if (withCluster && (!manifest.has("qnx") || !manifest.get("qnx").isJsonObject()))
            throw new IOException("prepared machine has no QNX runtime");
        OwnedSession owned = new OwnedSession(client, profile, canonicalMachine, work);
        try {
            JsonObject info = owned.read(client.info(id()), "info");
            if (!"voyemu.test".equals(text(info, "api")) || !new com.google.gson.JsonPrimitive(VoyemuClient.SCHEMA).equals(info.get("schema")))
                throw new IOException("Current Voyemu schema required");
            JsonObject selected = owned.read(client.profileGet(id(), profile), "profile");
            if (!profile.equals(text(selected, "id"))) throw new IOException("profile response differs");
            JsonObject started = owned.mutate(client.start(id(), profile, null, canonicalMachine, fresh, withCluster), "start");
            if (!profile.equals(text(started, "profile")) || !canonicalMachine.toString().equals(text(started, "machine_seed"))
                    || !truth(started, "background")) throw new IOException("started machine identity differs");
            if (owned.session == null) throw new IOException("owned session missing");
            JsonObject status = owned.read(client.status(id(), profile, owned.session), "status");
            if (!owned.session.id().equals(text(status, "session_id"))) throw new IOException("foreign session status");
            return owned;
        } catch (IOException | InterruptedException | RuntimeException failure) {
            try { owned.close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    /** Reconcile existing API journals without resending any original mutation. */
    public static OwnedSession resume(VoyemuClient client, String profile, Path machine, Path work)
            throws IOException, InterruptedException {
        Path canonical = machine.toRealPath();
        OwnedSession owned = new OwnedSession(client, profile, canonical, work);
        owned.evidencePrefix = "resume-" + id() + "-";
        java.util.List<Path> mutations;
        try (var entries = Files.list(work)) {
            mutations = entries.filter(path -> path.getFileName().toString().matches("start|(?:reboot|restart|sound|tap|scroll|launch|drag|page|drawer|powerkey)-[1-9][0-9]*|stop(?:-[1-9][0-9]*)?"))
                .sorted(java.util.Comparator.comparingInt(path -> {
                    String name = path.getFileName().toString();
                    if (name.equals("start")) return 0;
                    if (name.equals("stop")) return Integer.MAX_VALUE;
                    return Integer.parseInt(name.substring(name.indexOf('-') + 1));
                })).toList();
        }
        if (mutations.isEmpty() || !mutations.get(0).getFileName().toString().equals("start"))
            throw new IOException("retained start reservation required");
        for (Path directory : mutations) {
            String name = directory.getFileName().toString();
            String scope = name.equals("start") ? "profile:" + profile
                : owned.session == null ? null : "session:" + owned.session.id();
            if (scope == null) throw new IOException("retained operation lacks proven session");
            try (voyemu.journal.Journal journal = voyemu.journal.Journal.open(directory)) {
                VoyemuClient.Attempt original = client.reserved(journal, scope, canonical.toString());
                if (!profile.equals(original.profile()) || owned.closeAttempted)
                    throw new IOException("retained operation scope or order differs");
                if (!name.equals("start") && !owned.session.equals(original.session()))
                    throw new IOException("retained operation ownership differs");
                boolean terminal = false;
                for (int query = 0; query < 12; ++query) {
                    VoyemuClient.Attempt lookup = client.lookupReserved(id(), journal, canonical.toString(), original);
                    VoyemuClient.Reply reply = client.execute(lookup, Duration.ofSeconds(5));
                    VoyemuClient.LookupRecord outcome = VoyemuClient.classifyReservedLookup(reply, original);
                    if (outcome == VoyemuClient.LookupRecord.SUCCESS) {
                        JsonObject record = object(reply.response(), "result");
                        JsonObject result=VoyemuClient.reservedLookupResult(reply,original);
                        if (name.equals("start")) {
                            if (!original.requestId().equals(text(record, "request_id"))
                                    || !"session.start".equals(text(record, "op"))
                                    || !scope.equals(text(record, "scope"))) throw new IOException("retained start correlation differs");
                            owned.observeOwnership(reply, true);
                        } else if (original.op().equals("multimedia.reboot")) {
                            if (!"completed".equals(text(result, "status")) || !owned.session.id().equals(text(result, "session_id")))
                                throw new IOException("retained reboot is not completed");
                            owned.lastReboot = result.deepCopy();
                        } else if (original.op().equals("session.restart")) {
                            owned.verifyRestart(result,original,text(record,"operation_id"));owned.lastRestart=result.deepCopy();
                        } else if(original.op().equals("vehicle.set")) {
                            if (name.startsWith("outing-speed-")) owned.outingSpeedReceipt(result, original);
                            else if (name.startsWith("sound-")) owned.resumePwSound(result, original, work.resolve(name + "-context.json"));
                            else throw new IOException("unknown retained vehicle fixture scope");
                        } else if (original.op().equals("input.key.press")) {
                            powerKeyReceipt(result);
                        } else if (original.op().equals("session.stop")) {
                            JsonObject cleanup = object(result, "cleanup");
                            if (!owned.session.id().equals(text(result, "session_id")) || !truth(result, "accepted")
                                    || !truth(cleanup, "verified") || !truth(cleanup, "supervisor_endpoint_closed")
                                    || !truth(cleanup, "supervisor_lock_released")) throw new IOException("retained stop cleanup is unverified");
                            owned.closeAttempted = true;
                            owned.cleanupVerified = true;
                            owned.retainedStop = result.deepCopy();
                        }
                        owned.save(name + "-lookup-" + query, reply);
                        terminal = true; break;
                    }
                    owned.save(name + "-lookup-" + query, reply);
                    if (outcome == VoyemuClient.LookupRecord.FAILURE) {
                        if (name.equals("start")) throw new NotStarted("start");
                        terminal = true; break;
                    }
                    if (outcome == VoyemuClient.LookupRecord.INVALID) throw new IOException("retained lookup correlation differs");
                    Thread.sleep(250);
                }
                if (!terminal) throw new IOException("retained API mutation remains unknown; no replay or cleanup permitted");
            }
            if (!name.equals("start") && !name.equals("stop"))
                owned.mutationSequence = Math.max(owned.mutationSequence, Integer.parseInt(name.substring(name.indexOf('-') + 1)));
        }
        if (owned.session == null) throw new IOException("retained session ownership unavailable");
        if (!owned.closeAttempted) {
            JsonObject status = owned.read(client.status(id(), profile, owned.session), "status");
            if (!owned.session.id().equals(text(status, "session_id")) || truth(status, "stopped")
                    || !canonical.toString().equals(text(status, "machine_seed"))) throw new IOException("retained machine status differs");
        }
        return owned;
    }

    /** Existing successful stop, freshly correlated through the retained API journal/lookup. */
    public JsonObject retainedStopProof(Path expectedMachine, Path expectedWork) throws IOException {
        if (!cleanupVerified || !closeAttempted || retainedStop == null
                || !machine.equals(expectedMachine.toRealPath())
                || !work.toRealPath().equals(expectedWork.toRealPath())) {
            throw new IOException("matching retained stop cleanup proof required");
        }
        return retainedStop.deepCopy();
    }

    public VoyemuClient.Session session() { return session; }
    public boolean cleanupVerified() { return cleanupVerified; }

    /** One durable reboot; its response does not prove Android or product readiness. */
    public JsonObject reboot() throws IOException, InterruptedException { return reboot(false); }
    public JsonObject reboot(boolean cold) throws IOException, InterruptedException {
        if (closeAttempted || uncertainMutation) throw new IOException("owned session closed or mutation unresolved");
        JsonObject result = mutate(client.reboot(id(), profile, session, cold, 60000), "reboot");
        lastReboot = result.deepCopy();
        return result;
    }

    /** Both cold guest boots under the retained authority; readiness remains a separate observation. */
    public JsonObject restartBoth(JsonObject expectedBoot)throws IOException,InterruptedException {return restartBoth(expectedBoot,null);}
    public JsonObject restartBoth(JsonObject expectedBoot,JsonObject expectedCan)throws IOException,InterruptedException {
        if(closeAttempted||uncertainMutation||session==null)throw new IOException("owned session closed or mutation unresolved");
        JsonObject info=read(client.info(id()),"restart-info");boolean supported=false;
        if(info.has("operations")&&info.get("operations").isJsonArray())for(JsonElement operation:info.getAsJsonArray("operations"))if(operation.isJsonPrimitive()&&operation.getAsJsonPrimitive().isString()&&"session.restart".equals(operation.getAsString()))supported=true;
        if(!supported)throw new IOException("BLOCKED: current Voyemu does not advertise owned full-machine restart");
        JsonObject result=mutate(client.restartBoth(id(),profile,session,expectedBoot,expectedCan,180000),"restart");lastRestart=result.deepCopy();return result;
    }
    public JsonObject readCanHistory()throws IOException,InterruptedException {
        if(closeAttempted||session==null)throw new IOException("owned session unavailable");JsonObject record=read(client.readCanHistory(id(),profile,session,15000),"can-boundary-capability");
        if(!session.id().equals(PwSoundFixture.text(record,"session_id"))||!"closed_ecu_boundary_v1".equals(PwSoundFixture.text(record,"capability")))throw new IOException("actual CAN boundary capability unavailable");CanRestartBoundary.source(record,PwSoundFixture.text(record,"guest_boot_id"));return record;
    }
    public JsonObject coldCanBoundaryReceipt()throws IOException {
        return PwSoundFixture.object(PwSoundFixture.object(restartReceipt(),"can_boundary"),"after").deepCopy();
    }
    public JsonObject restartReceipt()throws IOException {
        if(lastRestart==null)throw new IOException("completed full-machine restart receipt unavailable");return lastRestart.deepCopy();
    }
    public JsonObject captureCluster(Path directory, long afterSequence, VoyemuClient.ClusterRegion region)
            throws IOException, InterruptedException {
        if (closeAttempted || session == null) throw new IOException("owned session unavailable");
        return read(client.captureCluster(id(), profile, session, directory, afterSequence, region), "cluster-capture");
    }
    public JsonObject captureCluster(Path artifactDirectory,long afterSequence)throws IOException,InterruptedException {
        if(closeAttempted||session==null)throw new IOException("owned session unavailable");return read(client.captureCluster(id(),profile,session,artifactDirectory,afterSequence),"cluster-capture");
    }
    private void verifyRestart(JsonObject result,VoyemuClient.Attempt original,String operationId)throws IOException {
        VoyemuClient.restartReceipt(result,original,operationId);
        JsonObject identity=object(result,"machine_identity");
        if(!machine.toString().equals(text(identity,"seed")))throw new IOException("full restart concrete machine differs");
    }
    public JsonObject outingVehicle() throws IOException, InterruptedException {
        if (closeAttempted || session == null) throw new IOException("owned Outing session unavailable");
        return read(client.vehicleRead(id(), profile, session), "outing-vehicle");
    }

    private static long outingNumber(JsonObject object, String field, long max) throws IOException {
        JsonElement value = object == null ? null : object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("0|[1-9][0-9]{0,9}") || value.getAsLong() > max)
            throw new IOException("Invalid Outing fixture field: " + field);
        return value.getAsLong();
    }

    public static int outingSpeed(JsonObject vehicle) throws IOException {
        JsonObject raw = object(object(vehicle, "state"), "raw_oem");
        int speed = (int) outingNumber(raw, "speed", 255);
        if (outingNumber(raw, "speed10x", 2550) != speed * 10L)
            throw new IOException("Outing speed and deci-km/h inputs differ");
        return speed;
    }

    private long outingSpeedReceipt(JsonObject result, VoyemuClient.Attempt original) throws IOException {
        JsonObject request = JsonParser.parseString(original.json()).getAsJsonObject();
        JsonObject values = object(request, "values");
        long before = outingNumber(request, "expected_revision", 0xfffffffeL);
        long after = outingNumber(result, "revision", 0xffffffffL);
        if (!session.id().equals(text(result, "session_id")) || values == null || values.size() != 1
                || after != before + 1 || outingSpeed(result) != outingNumber(values, "speed", 255))
            throw new IOException("retained Outing speed patch receipt differs");
        return after;
    }

    /** Returns only after the owned Android adapter acknowledges this exact speed revision. */
    public JsonObject setOutingSpeed(int speed) throws IOException, InterruptedException {
        if (closeAttempted || uncertainMutation || session == null)
            throw new IOException("owned session closed or mutation unresolved");
        JsonObject current = outingVehicle();
        long revision = outingNumber(current, "revision", 0xffffffffL);
        if (outingSpeed(current) != speed) {
            var attempt = client.setOutingSpeed(id(), profile, session, speed, revision);
            revision = outingSpeedReceipt(mutate(attempt, "outing-speed"), attempt);
        }
        long deadline = System.nanoTime() + Duration.ofSeconds(12).toNanos();
        do {
            JsonObject readback = outingVehicle();
            if (outingNumber(readback, "revision", 0xffffffffL) != revision || outingSpeed(readback) != speed)
                throw new IOException("Outing speed was superseded before Android acknowledgment");
            JsonObject android = object(object(readback, "deliveries"), "android");
            if (truth(android, "guest_acknowledged")
                    && outingNumber(android, "sequence", 0xffffffffL) == revision
                    && "owned full-input adapter".equals(text(android, "source"))) return readback;
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        throw new IOException("Android did not acknowledge the exact Outing speed revision");
    }

    /** Explicit fixture only; callers restore a known original value after confirmed test completion. */
    public JsonObject setPwSoundFixture(boolean enabled,String expectedBoot)throws IOException,InterruptedException {
        if(closeAttempted||uncertainMutation||session==null)throw new IOException("owned session closed or mutation unresolved");
        PwSoundFixture.bootId(expectedBoot);
        JsonObject before=read(client.status(id(),profile,session),"sound-status-before");
        PwSoundFixture.identity(before,profile,session,machine,null);
        JsonObject system = awaitPwSoundBoot(before, expectedBoot, "sound-system-before");
        JsonObject vehicle=read(client.vehicleRead(id(),profile,session),"sound-vehicle-before");
        long revision=PwSoundFixture.number(vehicle,"revision",0xfffffffeL),generation=PwSoundFixture.generation(before);
        int original=PwSoundFixture.value(vehicle);
        JsonObject context=new JsonObject();context.addProperty("expected_boot",expectedBoot);context.addProperty("original_value",original);context.add("before_status",PwSoundFixture.scope(before));context.add("before_vehicle",PwSoundFixture.baseline(vehicle));context.add("before_system",PwSoundFixture.bootFence(system));
        String domain="sound-"+(mutationSequence+1);Path contextFile=work.resolve(domain+"-context.json");PwSoundFixture.persist(contextFile,context);
        mutate(client.setPwSoundFixture(id(),profile,session,enabled,revision,generation),"sound");return pwSoundFixtureReceipt();
    }
    private void resumePwSound(JsonObject result, VoyemuClient.Attempt original, Path contextFile)
            throws IOException, InterruptedException {
        JsonObject request = JsonParser.parseString(original.json()).getAsJsonObject();
        PwSoundFixture.patch(result, request, session);
        JsonObject context = PwSoundFixture.context(contextFile);
        try (var files = Files.newDirectoryStream(work, "*sound-delivery-proof-*.json")) {
            for (Path file : files) {
                JsonObject proof = RunFiles.read(file);
                if (!result.equals(proof.get("patch_receipt"))) continue;
                String boot = text(context, "expected_boot");
                JsonObject before = object(context, "before_status");
                JsonObject after = object(proof, "after_status");
                PwSoundFixture.identity(before, profile, session, machine, null);
                PwSoundFixture.identity(after, profile, session, machine, before);
                PwSoundFixture.system(object(proof, "system_after"), after, boot);
                if (!boot.equals(text(proof, "boot_id")) || !truth(proof, "accepted")
                        || !before.equals(object(proof, "before_status"))
                        || !PwSoundFixture.delivered(object(proof, "delivery_receipt"), request)
                        || !PwSoundFixture.delivered(after, request)) {
                    throw new IOException("retained sound fixture delivery evidence differs");
                }
                lastPwSound = proof;
                return;
            }
        }
        verifyPwSound(result, original, contextFile);
    }

    private void verifyPwSound(JsonObject result,VoyemuClient.Attempt original,Path contextFile)throws IOException,InterruptedException {
        JsonObject context=PwSoundFixture.context(contextFile),request=JsonParser.parseString(original.json()).getAsJsonObject();
        PwSoundFixture.patch(result,request,session);
        JsonObject before=object(context,"before_status"),vehicleBefore=object(context,"before_vehicle");String expectedBoot=text(context,"expected_boot");
        PwSoundFixture.identity(before,profile,session,machine,null);PwSoundFixture.system(object(context,"before_system"),before,expectedBoot);
        if(PwSoundFixture.number(vehicleBefore,"revision",0xfffffffeL)!=PwSoundFixture.number(request,"expected_revision",0xfffffffeL)||PwSoundFixture.value(vehicleBefore)!=PwSoundFixture.number(context,"original_value",1)||PwSoundFixture.generation(before)!=PwSoundFixture.number(request,"expected_generation",0xffffffffL))throw new IOException("sound fixture retained context differs");
        long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();
        do {
            JsonObject status=read(client.status(id(),profile,session),"sound-status-after");PwSoundFixture.identity(status,profile,session,machine,before);
            JsonObject vehicle=read(client.vehicleRead(id(),profile,session),"sound-delivery");
            if(PwSoundFixture.delivered(vehicle,request)) {
                JsonObject system = awaitPwSoundBoot(status, expectedBoot, "sound-system-after");
                JsonObject finalStatus=read(client.status(id(),profile,session),"sound-final-status");PwSoundFixture.identity(finalStatus,profile,session,machine,before);if(!PwSoundFixture.delivered(finalStatus,request))throw new IOException("sound fixture delivery changed at final fence");
                JsonObject proof=new JsonObject();proof.addProperty("accepted",true);proof.addProperty("visible_effect_verified",false);proof.addProperty("android_native_vsp_verified",false);proof.addProperty("field","raw_oem.sound_enable");proof.addProperty("boot_id",expectedBoot);proof.addProperty("original_value",PwSoundFixture.value(vehicleBefore));proof.add("value",request.getAsJsonObject("values").get("sound_enable"));proof.add("patch_receipt",result.deepCopy());proof.add("delivery_receipt",vehicle);proof.add("before_status",before.deepCopy());proof.add("after_status",finalStatus);proof.add("system_after",system);
                RunFiles.write(work.resolve(evidencePrefix + "sound-delivery-proof-" + (++readSequence) + ".json"), proof);lastPwSound=proof;return;
            }
            Thread.sleep(100);
        }while(System.nanoTime()<deadline);
        throw new IOException("sound fixture fresh QNX delivery remains unverified; no restore or replay permitted");
    }
    private JsonObject awaitPwSoundBoot(JsonObject status, String expectedBoot, String evidence)
            throws IOException, InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        do {
            JsonObject system = read(client.systemRead(id(), profile, session, 1000), evidence);
            if (!"unavailable".equals(PwSoundFixture.text(system, "status"))) {
                PwSoundFixture.system(system, status, expectedBoot);
                return system;
            }
            if (!PwSoundFixture.text(system, "boot_id").isEmpty() || !system.has("value")
                    || !system.get("value").isJsonNull()) {
                throw new IOException("malformed unavailable sound fixture boot observation");
            }
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        throw new IOException("sound fixture Linux boot observation remained unavailable");
    }

    private JsonObject lastPwSound;
    public JsonObject pwSoundFixtureReceipt()throws IOException {
        if(lastPwSound==null||uncertainMutation)throw new IOException("confirmed sound fixture receipt unavailable");return lastPwSound.deepCopy();
    }
    public JsonObject launch(String packageName, String bootId, String screen) throws IOException, InterruptedException {
        if (closeAttempted || uncertainMutation) throw new IOException("owned session closed or mutation unresolved");
        return mutate(client.launch(id(), profile, session, packageName, bootId, screen), "launch");
    }

    public JsonObject closeVehicleDrawer(UiTarget target)throws IOException,InterruptedException {
        if(closeAttempted||uncertainMutation)throw new IOException("owned session closed or mutation unresolved");return mutate(client.closeVehicleDrawer(id(),profile,session,target),"drawer");
    }
    public JsonObject openVehicleDrawer(UiTarget target) throws IOException,InterruptedException {
        if(closeAttempted||uncertainMutation)throw new IOException("owned session closed or mutation unresolved");
        return mutate(client.openVehicleDrawer(id(),profile,session,target),"drawer");
    }
    public JsonObject pageLauncher(UiTarget grid,boolean next) throws IOException,InterruptedException {
        if(closeAttempted || uncertainMutation) throw new IOException("owned session closed or mutation unresolved");
        return mutate(client.pageLauncher(id(),profile,session,grid,next),"page");
    }

    public JsonObject dragLauncher(UiTarget target, int page, List<Integer> item, List<Integer> first, List<Integer> grid) throws IOException, InterruptedException {
        if (closeAttempted || uncertainMutation) throw new IOException("owned session closed or mutation unresolved");
        return mutate(client.dragLauncher(id(), profile, session, target, page, item, first, grid), "drag");
    }

    public JsonObject tap(UiTarget target) throws IOException, InterruptedException {
        if (closeAttempted || uncertainMutation) throw new IOException("owned session closed or mutation unresolved");
        return mutate(client.tap(id(), profile, session, target), "tap");
    }

    /** Dispatch one reviewed IME key; unknown results remain in the existing mutation journal. */
    public JsonObject tapKeyboard(UiTarget target, ReviewedKeyboard.Match match, long androidPid)
            throws IOException, InterruptedException {
        if (closeAttempted || uncertainMutation) throw new IOException("owned session closed or mutation unresolved");
        return mutate(client.tapKeyboard(id(), profile, session, target, match, androidPid), "tap");
    }

    /** One journaled SDK Power press, with no claim about its guest effect. */
    public JsonObject powerKey()throws IOException,InterruptedException {
        if(closeAttempted||uncertainMutation||session==null)throw new IOException("owned session closed or mutation unresolved");
        return mutate(client.powerKey(id(),profile,session),"powerkey");
    }
    static void powerKeyReceipt(JsonObject result)throws IOException {
        JsonElement visible=result==null?null:result.get("visible_effect_verified");
        if(!truth(result,"ok")||!truth(result,"sdk_completed")||!"Power".equals(text(result,"key"))
                ||!"press".equals(text(result,"phase"))||visible==null||!visible.isJsonPrimitive()
                ||!visible.getAsJsonPrimitive().isBoolean()||visible.getAsBoolean())
            throw new IOException("SDK Power dispatch receipt differs; guest effects remain unverified");
    }

    public JsonObject scrollSettings(UiTarget target) throws IOException, InterruptedException {
        if (closeAttempted || uncertainMutation) throw new IOException("owned session closed or mutation unresolved");
        return mutate(client.scrollSettings(id(), profile, session, target), "scroll");
    }

    public JsonObject scrollSettingsToStart(UiTarget target) throws IOException, InterruptedException {
        if (closeAttempted || uncertainMutation) throw new IOException("owned session closed or mutation unresolved");
        return mutate(client.scrollSettings(id(), profile, session, target, true), "scroll");
    }

    public JsonObject recoverSettings(UiTarget target) throws IOException, InterruptedException {
        if (closeAttempted || uncertainMutation) throw new IOException("owned session closed or mutation unresolved");
        return mutate(client.recoverSettings(id(), profile, session, target), "tap");
    }

    public JsonObject openSettingsEngineeringMenu(UiTarget target) throws IOException, InterruptedException {
        if (closeAttempted || uncertainMutation) throw new IOException("owned session closed or mutation unresolved");
        JsonObject result = mutate(client.openSettingsEngineeringMenu(id(), profile, session, target), "tap");
        if (!truth(result, "pre_dispatch_fenced") || !"5".equals(String.valueOf(result.get("requested_count")))
                || !"5".equals(String.valueOf(result.get("dispatched_count"))) || !"100".equals(String.valueOf(result.get("interval_ms"))))
            throw new IOException("five-tap engineering gesture receipt differs");
        return result;
    }

    public JsonObject rebootReceipt() throws IOException {
        if (lastReboot == null) throw new IOException("completed reboot receipt unavailable");
        return lastReboot.deepCopy();
    }

    /** Requires the Android boot readiness predicate for this session. */
    public JsonObject verifyAndroidReady() throws IOException, InterruptedException {
        JsonObject ready = null;
        for (int attempt = 0; attempt < 3; ++attempt) {
            ready = read(client.androidReady(id(), profile, session, 60000), "ready-" + attempt);
            JsonObject observed = object(ready, "last_observation");
            if (!session.id().equals(text(observed, "session_id"))) throw new IOException("foreign readiness observation");
            if (truth(observed, "stopped")) throw new IOException("owned session stopped before readiness");
            if (!"android.ready".equals(text(ready, "predicate"))) throw new IOException("readiness predicate differs");
            if (truth(ready, "ready")) break;
        }
        if (ready == null || !truth(ready, "ready")) throw new IOException("Android readiness deadline expired");
        return object(ready, "last_observation");
    }

    /** Requires Android readiness and a nonempty, session-correlated primary UI tree. */
    public void verifyAndroidUi() throws IOException, InterruptedException {
        verifyAndroidReady();
        verifyAndroidTree();
    }

    /** A rejected UI tree after checkpoint restore can be recovered with one owned cold boot. */
    public void verifyTestUi() throws IOException, InterruptedException {
        JsonObject status = verifyAndroidReady();
        try {
            verifyAndroidTree();
        } catch (Rejected rejected) {
            var quickboot = object(object(object(status, "deliveries"), "android"), "quickboot");
            if (!"TREE_REJECTED".equals(rejected.code) || !"restored".equals(text(quickboot, "mode"))) {
                throw rejected;
            }
            reboot(true);
            verifyAndroidUi();
        }
    }

    private void verifyAndroidTree() throws IOException, InterruptedException {
        JsonObject ui = read(client.multimediaUi(id(), profile, session, List.of("primary"), "all_attached", null, null, 30000), "ui");
        if (!"voyemu.ui.v1".equals(text(ui, "format")) || !profile.equals(text(ui, "profile"))
                || !session.id().equals(text(ui, "session_id")) || !ui.has("screens") || !ui.get("screens").isJsonArray())
            throw new IOException("UI observation identity or format differs");
        int primary = 0;
        for (JsonElement value : ui.getAsJsonArray("screens")) {
            if (!value.isJsonObject()) throw new IOException("malformed UI screen");
            JsonObject screen = value.getAsJsonObject();
            if ("primary".equals(text(screen, "screen"))) {
                ++primary;
                if (!("ok".equals(text(screen, "status")) || "partial".equals(text(screen, "status")))
                        || !screen.has("nodes") || !screen.get("nodes").isJsonArray() || screen.getAsJsonArray("nodes").isEmpty())
                    throw new IOException("primary UI tree unavailable");
            }
        }
        if (primary != 1) throw new IOException("one primary UI screen required");
    }

    /** Wait for the QNX guest to acknowledge delivery in this session. */
    public void verifyQnxDelivery() throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 3; ++attempt) {
            JsonObject result = read(client.qnxDelivery(id(), profile, session, 60000), "qnx-ready-" + attempt);
            JsonObject observed = object(result, "last_observation");
            if (!session.id().equals(text(observed, "session_id")) || truth(observed, "stopped")
                    || !"qnx.delivery".equals(text(result, "predicate"))) {
                throw new IOException("QNX readiness observation differs from the owned session");
            }
            if (truth(result, "ready")) return;
        }
        throw new IOException("QNX guest delivery readiness deadline expired");
    }

    private JsonObject read(VoyemuClient.Attempt attempt, String evidence) throws IOException, InterruptedException {
        VoyemuClient.Reply reply = client.execute(attempt, CALL_TIMEOUT);
        save(evidence + "-" + (++readSequence), reply);
        try {
            return VoyemuClient.readResult(attempt, successful(reply));
        } catch (SessionUi.Unavailable failure) {
            throw new Rejected(failure.code(), failure.getMessage());
        }
    }

    private JsonObject mutate(VoyemuClient.Attempt original, String domain) throws IOException, InterruptedException {
        String directory = domain.equals("start") ? domain : domain + "-" + (++mutationSequence);
        Path journal = Files.createDirectory(work.resolve(directory));
        try (AdmissionRunner runner = AdmissionRunner.open(client, journal, machine.toString())) {
            uncertainMutation = true;
            VoyemuClient.Reply reply = runner.run(original, CALL_TIMEOUT);
            if (domain.equals("start")) {
                try {
                    observeOwnership(reply, false);
                    if (session != null) uncertainMutation = false;
                } finally { save(directory + "-dispatch", reply); }
            } else save(directory + "-dispatch", reply);
            if (reply.state() == VoyemuClient.State.RESPONSE && truth(reply.response(), "ok")) {
                JsonObject result = successful(reply);
                if(domain.equals("restart"))verifyRestart(result,original,text(reply.response(),"operation_id"));
                if(domain.equals("sound"))verifyPwSound(result,original,work.resolve(directory+"-context.json"));
                if(domain.equals("powerkey"))powerKeyReceipt(result);
                if (!domain.equals("reboot") || "completed".equals(text(result, "status"))) {
                    uncertainMutation = false;
                    return result;
                }
            }
            if (reply.state() == VoyemuClient.State.RESPONSE
                    && "not_started".equals(text(object(reply.response(), "error"), "outcome"))) {
                uncertainMutation = false;
                throw new NotStarted(domain, text(object(reply.response(), "error"), "code"));
            }
            for (int query = 0; query < 12; ++query) {
                AdmissionRunner.AttemptResult recovered = runner.recover(original, OwnedSession::id, Duration.ofSeconds(5));
                try {
                    if (VoyemuClient.assessReservedLookup(recovered.lookupReply(), original) == VoyemuClient.LookupAssessment.INVALID)
                        throw new IOException("operation lookup identity differs");
                    if (domain.equals("start") && recovered.outcome == AdmissionRunner.Outcome.SUCCESS) {
                        JsonObject record = object(recovered.lookupReply().response(), "result");
                        if (!original.requestId().equals(text(record, "request_id")) || !"session.start".equals(text(record, "op"))
                                || !("profile:" + profile).equals(text(record, "scope"))) throw new IOException("operation lookup identity incomplete");
                        observeOwnership(recovered.lookupReply(), true);
                        if (session != null) uncertainMutation = false;
                    }
                } finally { save(directory + "-lookup-" + query, recovered.lookupReply()); }
                if (recovered.outcome == AdmissionRunner.Outcome.SUCCESS) {
                    JsonObject record = object(recovered.lookupReply().response(), "result");
                    JsonObject result=VoyemuClient.reservedLookupResult(recovered.lookupReply(),original);
                    if(domain.equals("restart"))verifyRestart(result,original,text(record,"operation_id"));
                    if(domain.equals("sound"))verifyPwSound(result,original,work.resolve(directory+"-context.json"));
                    if(domain.equals("powerkey"))powerKeyReceipt(result);
                    if (domain.equals("reboot") && !"completed".equals(text(result, "status")))
                        throw new IOException("terminal reboot observation is incomplete; session retained");
                    uncertainMutation = false;
                    return result;
                }
                if (recovered.outcome == AdmissionRunner.Outcome.FAILURE) {
                    uncertainMutation = false;
                    throw new NotStarted(domain);
                }
                Thread.sleep(250);
            }
            throw new IOException(domain + " outcome remains unknown; original request must not be replayed");
        }
    }

    private void observeOwnership(VoyemuClient.Reply reply, boolean lookup) throws IOException {
        if (reply.state() != VoyemuClient.State.RESPONSE || reply.response() == null) return;
        JsonObject envelope = reply.response();
        JsonObject observed = new JsonObject(), record = new JsonObject();
        if (lookup) {
            record = object(envelope, "result");
            observed = object(record, "owned_session_observed");
            envelope = object(record, "response");
        }
        if (!truth(envelope, "ok")) return;
        JsonObject result = object(envelope, "result");
        if (!profile.equals(text(result, "profile")) || !machine.toString().equals(text(result, "machine_seed"))
                || !truth(result, "background")) throw new IOException("started machine identity differs");
        JsonObject value = object(result, "session");
        String identity = text(value, "id"), ownership = text(value, "ownership_id");
        if (identity == null || ownership == null) throw new IOException("owned session missing");
        VoyemuClient.Session verified;
        try { verified = new VoyemuClient.Session(identity, ownership); }
        catch (IllegalArgumentException bad) { throw new IOException("malformed ownership receipt", bad); }
        if (lookup) {
            if (!profile.equals(text(record, "profile")) || !ownership.equals(text(record, "ownership_id")))
                throw new IOException("recovered ownership differs");
            if (record.has("owned_session_observed") && (!identity.equals(text(observed, "id")) || !ownership.equals(text(observed, "ownership_id"))))
                throw new IOException("recovered ownership differs");
        }
        session = verified;
        uncertainMutation = false;
        saveJson("owned-session", value);
    }

    @Override public void close() throws IOException, InterruptedException {
        if (session == null) return;
        if (closeAttempted) {
            if (!cleanupVerified) throw new IOException("stop outcome remains unverified; reconcile the original request");
            return;
        }
        if (uncertainMutation) throw new IOException("mutation outcome unknown; owned session retained for reconciliation");
        if (stopCheck != null) stopCheck.check();
        closeAttempted = true;
        JsonObject stopped = mutate(client.stop(id(), profile, session), "stop");
        JsonObject cleanup = object(stopped, "cleanup");
        if (!session.id().equals(text(stopped, "session_id")) || !truth(stopped, "accepted")
                || !truth(cleanup, "verified") || !truth(cleanup, "supervisor_endpoint_closed")
                || !truth(cleanup, "supervisor_lock_released")) throw new IOException("owned session cleanup unverified");
        cleanupVerified = true;
    }

    private void save(String name, VoyemuClient.Reply reply) throws IOException {
        JsonObject evidence = new JsonObject();
        evidence.addProperty("state", reply.state().name());
        if (reply.response() != null) evidence.add("response", reply.response());
        evidence.addProperty("diagnostic", reply.diagnostic());
        saveJson(name, evidence);
    }
    private void saveJson(String name, JsonObject value) throws IOException {
        Path file = work.resolve(evidencePrefix + name + ".json");
        Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(file, value.toString() + "\n");
    }
    private static final class Rejected extends IOException {
        private final String code;

        private Rejected(String code, String message) {
            super("Voyemu rejected the request: " + code + " (" + message + ")");
            this.code = code;
        }
    }

    private static JsonObject successful(VoyemuClient.Reply reply) throws IOException {
        if (reply.state() != VoyemuClient.State.RESPONSE) {
            throw new IOException("Voyemu request failed or outcome unknown: " + reply.diagnostic());
        }
        if (!truth(reply.response(), "ok")) {
            JsonObject error = object(reply.response(), "error");
            throw new Rejected(text(error, "code"), text(error, "message"));
        }
        JsonObject result = object(reply.response(), "result");
        if (result == null) throw new IOException("Voyemu result missing");
        return result;
    }
    private static String id() { return UUID.randomUUID().toString().replace("-", ""); }
    private static JsonObject object(JsonObject value, String key) {
        return value != null && value.has(key) && value.get(key).isJsonObject() ? value.getAsJsonObject(key) : null;
    }
    private static String text(JsonObject value, String key) {
        JsonElement field = value == null ? null : value.get(key);
        return field != null && field.isJsonPrimitive() && field.getAsJsonPrimitive().isString() ? field.getAsString() : null;
    }
    private static boolean truth(JsonObject value, String key) {
        JsonElement field = value == null ? null : value.get(key);
        return field != null && field.isJsonPrimitive() && field.getAsJsonPrimitive().isBoolean() && field.getAsBoolean();
    }
}
