package ru.voboost.tests.installer.cli;

import ru.voboost.tests.agents.NativeStateOutageAdmission;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeStateOutageAdmissionTest {
    private static final String BOOT = "11111111-1111-1111-1111-111111111111";
    private static final String TOKEN = "a".repeat(64), GENERATION = "b".repeat(64), CATALOG = "c".repeat(64), WRITER = "d".repeat(32);
    private static NativeStateOutageAdmission.Scope scope() {
        return new NativeStateOutageAdmission.Scope("native-state-read-unavailable", TOKEN, BOOT, GENERATION, CATALOG, WRITER, 44, 123, 41, 514, 9, 90001);
    }
    private static JsonObject receipt(long at, long reads, long drops, long waits, boolean stopped, boolean released, boolean closed, long recovered) {
        JsonObject v = new JsonObject();
        v.addProperty("kind", "native-state-read-unavailable"); v.addProperty("token", TOKEN); v.addProperty("bootId", BOOT);
        v.addProperty("generationId", GENERATION); v.addProperty("catalogSha256", CATALOG); v.addProperty("ownerWriterId", WRITER);
        v.addProperty("ownerPid", 44); v.addProperty("ownerStarttime", 123); v.addProperty("targetPid", 41); v.addProperty("targetStarttime", 514);
        v.addProperty("deadlineMonotonicMs", 90001); v.addProperty("observedMonotonicMs", at); v.addProperty("reads", reads); v.addProperty("droppedReads", drops);
        v.addProperty("writes", 0); v.addProperty("failedWrites", 0); v.addProperty("waitStateSteps", waits); v.addProperty("recoveredValues", recovered);
        if (recovered == 0) v.add("recoveredValue", com.google.gson.JsonNull.INSTANCE); else v.addProperty("recoveredValue", true);
        v.addProperty("leaseRevision", 7); v.addProperty("stopped", stopped); v.addProperty("released", released); v.addProperty("closed", closed); return v;
    }
    private static JsonObject withLease(JsonObject v, int lease) { v.addProperty("leaseRevision", lease); return v; }
    private static List<JsonObject> evidence() {
        return List.of(receipt(100, 0, 0, 0, false, false, false, 0), receipt(200, 2, 2, 2, false, false, false, 0),
                receipt(300, 2, 2, 2, true, false, false, 0), receipt(1400, 2, 2, 2, true, false, false, 0),
                receipt(1500, 2, 2, 2, true, true, false, 0), withLease(receipt(1600, 3, 2, 2, false, true, false, 1), 8),
                withLease(receipt(1700, 3, 2, 2, false, true, true, 1), 8));
    }
    private static JsonObject target() {
        JsonObject endpoint = new JsonObject(); endpoint.addProperty("targetPid", "41"); endpoint.addProperty("targetStarttime", "514");
        endpoint.addProperty("endpointEpoch", "9"); endpoint.addProperty("generationId", GENERATION); endpoint.addProperty("catalogSha256", CATALOG);
        JsonObject row = new JsonObject(); row.addProperty("id", "vehicle-drive-pedestrian-warning"); row.addProperty("process", "com.qinggan.canbus.service"); row.addProperty("state", "active"); row.add("currentEndpoint", endpoint); return row;
    }
    private static JsonObject root(boolean value) {
        JsonObject state = new JsonObject(); state.addProperty("status", "present"); state.addProperty("current", true); state.addProperty("proof", "same-boot-retained-file"); state.addProperty("revision", "42"); state.addProperty("data", value);
        JsonObject files = new JsonObject(); files.add("state/vehicle-drive-pedestrian-warning_pedestrian-warning-armed", state);
        JsonObject data = new JsonObject(); data.addProperty("root", "present"); data.addProperty("root_proof", "fixed-path-owner-mode-boot-fenced"); data.add("files", files);
        JsonObject v = new JsonObject(); v.addProperty("schema", 2); v.addProperty("source", "voboost"); v.addProperty("status", "ready"); v.addProperty("boot_id", BOOT); v.add("data", data); return v;
    }
    private static NativeStateOutageAdmission.Observations observations(JsonObject before, JsonObject after) {
        var writer = new ru.voboost.tests.logging.InstallerLogs.CurrentSession(BOOT, WRITER, java.math.BigInteger.ONE);
        var owner = new StartupObservation.ProcessIdentity(44, "123", "/data/voboost/voboost-daemon");
        var input = new StartupObservation.ProcessIdentity(41, "514", "com.qinggan.canbus.service");
        return new NativeStateOutageAdmission.Observations(before, after, writer, writer, owner, owner, input, input);
    }
    private static FaultWindow cleanup(List<JsonObject> receipts) throws Exception { return cleanup("native-state-read-unavailable", receipts); }
    private static FaultWindow cleanup(String kind, List<JsonObject> receipts) throws Exception {
        JsonObject request = new JsonObject(); request.addProperty("action", "fault-window"); request.addProperty("kind", kind);
        request.addProperty("bootId", BOOT); request.addProperty("manifestSha256", "e".repeat(64)); request.addProperty("generationId", GENERATION);
        request.addProperty("catalogSha256", CATALOG); request.addProperty("targetPid", 41); request.addProperty("targetStarttime", "514");
        var window = new ru.voboost.tests.installer.cli.FaultWindow(request, "emulator-5588", "candidate");
        JsonObject body = request.deepCopy(); body.remove("action"); body.addProperty("schemaVersion", 1); body.addProperty("phase", "armed");
        body.addProperty("token", TOKEN); body.addProperty("releaseIdentity", "candidate"); body.addProperty("ownerWriterId", WRITER);
        body.addProperty("ownerPid", 42); body.addProperty("ownerStarttime", "500"); body.addProperty("daemonPid", 43); body.addProperty("daemonStarttime", "400");
        body.addProperty("startedMonotonicMs", "1"); body.addProperty("expiresMonotonicMs", "90001");
        JsonObject arm = new JsonObject(); arm.addProperty("schemaVersion", 1); arm.addProperty("serial", "emulator-5588"); arm.addProperty("phase", "armed"); arm.add("body", body);
        window.observe(arm.toString());
        boolean releaseSent = false;
        for (int i = 0; i < receipts.size(); i++) {
            String phase;
            if (i == receipts.size() - 1) { window.release(); phase = "native-closed"; }
            else if (!releaseSent && receipts.get(i).get("released").getAsBoolean()) { window.reserveNativeControl(FaultWindow.NativeControl.RELEASE); phase = "released"; releaseSent = true; }
            else { window.reserveNativeControl(FaultWindow.NativeControl.SNAPSHOT); phase = "snapshot"; }
            JsonObject event = new JsonObject(); event.addProperty("schemaVersion", 1); event.addProperty("serial", "emulator-5588"); event.addProperty("phase", phase); event.add("body", receipts.get(i));
            window.observeNative(event.toString());
        }
        JsonObject end = arm.deepCopy(); end.addProperty("phase", "closed"); end.getAsJsonObject("body").addProperty("phase", "closed");
        end.getAsJsonObject("body").addProperty("cleanup", true); end.getAsJsonObject("body").addProperty("reason", "eof"); window.observe(end.toString()); return window;
    }
    @Test void retainedNativeStreamReusesTheAdmissionAndRejectsTruncationOrForeignEnvelope()throws Exception {
        FaultWindow original=cleanup(evidence());StringBuilder raw=new StringBuilder();
        JsonObject envelope=new JsonObject();envelope.addProperty("schemaVersion",1);envelope.addProperty("serial","emulator-5588");envelope.addProperty("phase","armed");envelope.add("body",original.armed());raw.append(envelope).append('\n');boolean released=false;
        var receipts=original.nativeReceipts();for(int i=0;i<receipts.size();i++){JsonObject row=receipts.get(i);String phase=i==receipts.size()-1?"native-closed":!released&&row.get("released").getAsBoolean()?"released":"snapshot";if(phase.equals("released"))released=true;envelope.addProperty("phase",phase);envelope.add("body",row);raw.append(envelope).append('\n');}
        envelope.addProperty("phase","closed");envelope.add("body",original.closed());raw.append(envelope).append('\n');
        var directory=java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/tmp/native-recorded-"+java.util.UUID.randomUUID()).toAbsolutePath());var file=directory.resolve("stdout.jsonl");
        try {
            java.nio.file.Files.writeString(file,raw);var reread=FaultWindow.readNative(file,original.request(),"emulator-5588","candidate");assertTrue(NativeStateOutageAdmission.admit(scope(),reread,true,root(true),root(true),observations(target(),target())).recoveredValue());
            java.nio.file.Files.writeString(file,raw.toString().strip());assertThrows(IOException.class,()->FaultWindow.readNative(file,original.request(),"emulator-5588","candidate"));
            java.nio.file.Files.writeString(file,raw.toString().replace("emulator-5588","emulator-5598"));assertThrows(IOException.class,()->FaultWindow.readNative(file,original.request(),"emulator-5588","candidate"));
        }finally{java.nio.file.Files.deleteIfExists(file);java.nio.file.Files.deleteIfExists(directory);}
    }
    @Test void actual_closed_receipts_prove_retry_cancel_recovery_and_no_replay() throws Exception {
        assertTrue(NativeStateOutageAdmission.admit(scope(), cleanup(evidence()), true, root(true), root(true), observations(target(), target())).recoveredValue());
    }
    @Test void bounded_polling_receipts_do_not_turn_a_real_effect_into_false_failure() throws Exception {
        var rows = new java.util.ArrayList<>(evidence());
        rows.add(1, receipt(150, 1, 1, 1, false, false, false, 0));
        rows.add(6, receipt(1550, 2, 2, 2, true, true, false, 0));
        assertTrue(NativeStateOutageAdmission.admit(scope(), cleanup(rows), true, root(true), root(true), observations(target(), target())).recoveredValue());
    }

    @Test void write_loss_requires_two_real_failed_writes_and_accepts_false_recovery() throws Exception {
        var s = scope();
        var writeScope = new NativeStateOutageAdmission.Scope("native-state-write-unavailable", s.token(), s.boot(), s.generation(), s.catalog(), s.ownerWriter(), s.ownerPid(), s.ownerStart(), s.targetPid(), s.targetStart(), s.endpointEpoch(), s.deadline());
        var rows = new java.util.ArrayList<JsonObject>();
        int i = 0; for (JsonObject original : evidence()) {
            JsonObject row = original.deepCopy(); row.addProperty("kind", "native-state-write-unavailable"); row.addProperty("droppedReads", 0);
            if (i++ > 0) { row.addProperty("writes", 2); row.addProperty("failedWrites", 2); }
            if (!row.get("recoveredValue").isJsonNull()) row.addProperty("recoveredValue", false);
            rows.add(row);
        }
        assertFalse(NativeStateOutageAdmission.admit(writeScope, cleanup("native-state-write-unavailable", rows), true, root(false), root(false), observations(target(), target())).recoveredValue());
        JsonObject noEffect = rows.get(1).deepCopy(); noEffect.addProperty("failedWrites", 0); rows.set(1, noEffect);
        assertThrows(IOException.class, () -> NativeStateOutageAdmission.admit(writeScope, cleanup("native-state-write-unavailable", rows), true, root(false), root(false), observations(target(), target())));
    }

    @Test void rejects_wrong_owner_stale_generation_target_timeout_unknown_and_replay() throws Exception {
        for (String key : List.of("ownerWriterId", "ownerPid", "generationId", "targetPid", "targetStarttime", "observedMonotonicMs", "closed")) {
            var rows = new java.util.ArrayList<>(evidence()); JsonObject bad = rows.get(6).deepCopy();
            if (key.equals("closed")) bad.addProperty(key, false); else if (key.equals("observedMonotonicMs")) bad.addProperty(key, 90001);
            else if (key.equals("ownerWriterId") || key.equals("generationId")) bad.addProperty(key, "f".repeat(key.equals("ownerWriterId") ? 32 : 64));
            else bad.addProperty(key, 999);
            rows.set(6, bad); assertThrows(IOException.class, () -> NativeStateOutageAdmission.admit(scope(), cleanup(rows), true, root(true), root(true), observations(target(), target())), key);
        }
        var rows = new java.util.ArrayList<>(evidence()); JsonObject replay = rows.get(3).deepCopy(); replay.addProperty("reads", 3); replay.addProperty("droppedReads", 3); rows.set(3, replay);
        assertThrows(IOException.class, () -> NativeStateOutageAdmission.admit(scope(), cleanup(rows), true, root(true), root(true), observations(target(), target())));
        var replayBeforeRelease = new java.util.ArrayList<>(evidence()); JsonObject between = replayBeforeRelease.get(4).deepCopy(); between.addProperty("reads", 3); between.addProperty("droppedReads", 3); replayBeforeRelease.set(4, between);
        assertThrows(IOException.class, () -> NativeStateOutageAdmission.admit(scope(), cleanup(replayBeforeRelease), true, root(true), root(true), observations(target(), target())));
        assertThrows(IOException.class, () -> NativeStateOutageAdmission.admit(scope(), cleanup(evidence()), true, root(true), null, observations(target(), target())));
        assertThrows(IOException.class, () -> NativeStateOutageAdmission.admit(scope(), cleanup(evidence()), true, root(false), root(true), observations(target(), target())));
        JsonObject staleEndpoint = target(); staleEndpoint.getAsJsonObject("currentEndpoint").addProperty("endpointEpoch", "10");
        assertThrows(IOException.class, () -> NativeStateOutageAdmission.admit(scope(), cleanup(evidence()), true, root(true), root(true), observations(target(), staleEndpoint)));
    }
}
