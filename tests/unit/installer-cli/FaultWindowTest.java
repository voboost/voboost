package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

class FaultWindowTest {
    static final String BOOT = "11111111-1111-1111-1111-111111111111";
    static JsonObject request() throws Exception { return RunFiles.parse("""
        {"action":"fault-window","kind":"startup-mutation-lock","bootId":"BOOT","manifestSha256":"MANIFEST","generationId":"GENERATION","catalogSha256":"CATALOG","targetPid":41,"targetStarttime":"514"}
        """.replace("BOOT", BOOT).replace("MANIFEST", "a".repeat(64)).replace("GENERATION", "b".repeat(64)).replace("CATALOG", "c".repeat(64))); }
    static JsonObject envelope(String phase) throws Exception {
        JsonObject body = request(); body.remove("action"); body.addProperty("schemaVersion", 1); body.addProperty("phase", phase);
        body.addProperty("token", "d".repeat(64)); body.addProperty("releaseIdentity", "candidate");
        body.addProperty("ownerPid", 42); body.addProperty("ownerStarttime", "500"); body.addProperty("daemonPid", 43); body.addProperty("daemonStarttime", "400");
        body.addProperty("startedMonotonicMs", "1000"); body.addProperty("expiresMonotonicMs", "91000");
        if (phase.equals("closed")) { body.addProperty("cleanup", true); body.addProperty("reason", "eof"); }
        JsonObject envelope = new JsonObject(); envelope.addProperty("schemaVersion", 1); envelope.addProperty("serial", "emulator-5588"); envelope.addProperty("phase", phase); envelope.add("body", body); return envelope;
    }
    private FaultWindow window() throws Exception { return new FaultWindow(request(), "emulator-5588", "candidate"); }
    @Test void onlyArmedLiveWindowAndExactEofCleanupAreAccepted() throws Exception {
        FaultWindow window = window(); assertThrows(IOException.class, window::requireLive);
        window.observe(envelope("armed").toString()); window.requireLive();
        window.release(); assertThrows(IOException.class, window::requireLive); assertThrows(IOException.class, window::release);
        window.observe(envelope("closed").toString()); window.requireClosed();
        assertEquals("eof", window.closed().get("reason").getAsString());
        assertThrows(IOException.class, () -> window.observe(envelope("closed").toString()));
    }
    @Test void earlyExpiredForeignOrChangedCleanupIsRejected() throws Exception {
        FaultWindow early = window(); early.observe(envelope("armed").toString());
        assertThrows(IOException.class, () -> early.observe(envelope("closed").toString()));
        for (String field : new String[]{"token", "bootId", "targetStarttime", "reason"}) {
            FaultWindow window = window(); window.observe(envelope("armed").toString()); window.release();
            var closed = envelope("closed"); closed.getAsJsonObject("body").addProperty(field, field.equals("reason") ? "expired" : "foreign");
            assertThrows(IOException.class, () -> window.observe(closed.toString()));
        }
        FaultWindow changed = window(); changed.observe(envelope("armed").toString()); changed.release();
        var cleanup = envelope("closed"); cleanup.getAsJsonObject("body").addProperty("cleanup", false);
        assertThrows(IOException.class, () -> changed.observe(cleanup.toString()));
    }
    @Test void boundedStrictProductProcessAndDeadlineFieldsAreRequired() throws Exception {
        for (String field : new String[]{"serial", "schemaVersion", "phase"}) {
            var value = envelope("armed"); value.addProperty(field, "foreign");
            assertThrows(IOException.class, () -> window().observe(value.toString()));
        }
        for (String field : new String[]{"targetPid", "ownerPid", "daemonPid", "expiresMonotonicMs", "ownerStarttime", "extra"}) {
            var value = envelope("armed"); value.getAsJsonObject("body").addProperty(field, "0");
            assertThrows(IOException.class, () -> window().observe(value.toString()));
        }
        String duplicate = envelope("armed").toString().replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1");
        assertThrows(IOException.class, () -> window().observe(duplicate));
        var unknown = request(); unknown.addProperty("command", "forbidden"); assertThrows(IOException.class, () -> new FaultWindow(unknown, "emulator-5588", "candidate"));
        assertThrows(IOException.class, () -> new FaultWindow(request(), "physical", "candidate"));
    }    @Test void startupEffectRequiresFreshExactTokenTargetProductAndRootSequence() throws Exception {
        var window = window(); window.observe(envelope("armed").toString());
        JsonObject fields = request(); fields.remove("action"); fields.addProperty("targetPid", "41");
        fields.addProperty("token", "d".repeat(64)); fields.addProperty("effect", "startup-mutation-rejected"); fields.addProperty("source", "settings");
        fields.addProperty("requestId", "7"); fields.addProperty("process", "com.qinggan.app.setting");
        var prior = new ru.voboost.tests.logging.InstallerLogs.Snapshot(BOOT, "a".repeat(32), java.math.BigInteger.ONE, java.math.BigInteger.ONE, java.util.Set.of(java.math.BigInteger.ONE), java.time.Instant.EPOCH, java.util.List.of(), java.util.List.of(), java.nio.file.Path.of("evidence"));
        var effect = new ru.voboost.tests.logging.InstallerLogs.RootEffect(java.math.BigInteger.TWO, fields);
        var current = new ru.voboost.tests.logging.InstallerLogs.Snapshot(BOOT, "a".repeat(32), java.math.BigInteger.TWO, java.math.BigInteger.TWO, java.util.Set.of(java.math.BigInteger.ONE, java.math.BigInteger.TWO), java.time.Instant.EPOCH, java.util.List.of(), java.util.List.of(effect), java.nio.file.Path.of("evidence"));
        assertEquals(1, window.startupRejections(prior, current).size()); assertTrue(window.startupRejections(current, current).isEmpty());
        for (String key : new String[]{"process", "targetPid", "targetStarttime", "manifestSha256", "effect", "source"}) {
            JsonObject foreign = fields.deepCopy(); foreign.addProperty(key, "foreign");
            var changed = new ru.voboost.tests.logging.InstallerLogs.Snapshot(BOOT, "a".repeat(32), java.math.BigInteger.TWO, java.math.BigInteger.TWO, java.util.Set.of(java.math.BigInteger.ONE, java.math.BigInteger.TWO), java.time.Instant.EPOCH, java.util.List.of(), java.util.List.of(new ru.voboost.tests.logging.InstallerLogs.RootEffect(java.math.BigInteger.TWO, foreign)), java.nio.file.Path.of("evidence"));
            assertThrows(IOException.class, () -> window.startupRejections(prior, changed));
        }
    }
    @Test void retainedCleanupRequiresTheExactTwoCompleteReceiptsAndCannotReopenTheWindow() throws Exception {
        String armed = envelope("armed").toString(), closed = envelope("closed").toString();
        assertEquals("closed", FaultWindow.cleanup(armed + "\n" + closed + "\n", request(), "emulator-5588", "candidate").get("phase").getAsString());
        for (String raw : new String[]{armed + "\n", closed + "\n", armed + "\n" + closed, armed + "\n" + closed + "\n\n", armed + "\n" + armed + "\n" + closed + "\n", closed + "\n" + armed + "\n"})
            assertThrows(IOException.class, () -> FaultWindow.cleanup(raw, request(), "emulator-5588", "candidate"));
    }
    @Test void currentJavaStateReplyLossRequiresExactTokenEndpointAndUniqueRequest()throws Exception {
        var request=request();request.addProperty("kind","vehicle-state-reply-loss");
        var window=new FaultWindow(request,"emulator-5588","candidate");var armed=envelope("armed");armed.getAsJsonObject("body").addProperty("kind","vehicle-state-reply-loss");window.observe(armed.toString());
        JsonObject fields=request.deepCopy();fields.remove("action");fields.addProperty("targetPid","41");fields.addProperty("token","d".repeat(64));fields.addProperty("effect","state-reply-dropped");fields.addProperty("source","vehicle-drive-pedestrian-warning");fields.addProperty("requestId","18446744073709551615");fields.addProperty("process","com.qinggan.canbus.service");
        var prior=new ru.voboost.tests.logging.InstallerLogs.Snapshot(BOOT,"a".repeat(32),java.math.BigInteger.ONE,java.math.BigInteger.ONE,java.util.Set.of(java.math.BigInteger.ONE),java.time.Instant.EPOCH,java.util.List.of(),java.util.List.of(),java.nio.file.Path.of("evidence"));
        var current=new ru.voboost.tests.logging.InstallerLogs.Snapshot(BOOT,"a".repeat(32),java.math.BigInteger.TWO,java.math.BigInteger.TWO,java.util.Set.of(java.math.BigInteger.ONE,java.math.BigInteger.TWO),java.time.Instant.EPOCH,java.util.List.of(),java.util.List.of(new ru.voboost.tests.logging.InstallerLogs.RootEffect(java.math.BigInteger.TWO,fields)),java.nio.file.Path.of("evidence"));
        assertEquals(1,window.stateReplyLosses("vehicle-drive-pedestrian-warning",prior,current).size());
        assertTrue(window.stateReplyLosses("vehicle-drive-forced-ev",prior,current).isEmpty());
        for(String key:new String[]{"process","targetPid","targetStarttime","manifestSha256","kind","effect","source","requestId"}) {
            var bad=fields.deepCopy();bad.addProperty(key,"foreign");
            var changed=new ru.voboost.tests.logging.InstallerLogs.Snapshot(BOOT,"a".repeat(32),java.math.BigInteger.TWO,java.math.BigInteger.TWO,java.util.Set.of(java.math.BigInteger.ONE,java.math.BigInteger.TWO),java.time.Instant.EPOCH,java.util.List.of(),java.util.List.of(new ru.voboost.tests.logging.InstallerLogs.RootEffect(java.math.BigInteger.TWO,bad)),java.nio.file.Path.of("evidence"));
            assertThrows(IOException.class,()->window.stateReplyLosses("vehicle-drive-pedestrian-warning",prior,changed));
        }
        var oldTarget=fields.deepCopy();oldTarget.addProperty("process","com.qinggan.systemservice");
        var oldReceipt=new ru.voboost.tests.logging.InstallerLogs.Snapshot(BOOT,"a".repeat(32),java.math.BigInteger.TWO,java.math.BigInteger.TWO,java.util.Set.of(java.math.BigInteger.ONE,java.math.BigInteger.TWO),java.time.Instant.EPOCH,java.util.List.of(),java.util.List.of(new ru.voboost.tests.logging.InstallerLogs.RootEffect(java.math.BigInteger.TWO,oldTarget)),java.nio.file.Path.of("evidence"));
        assertThrows(IOException.class,()->window.stateReplyLosses("vehicle-drive-pedestrian-warning",prior,oldReceipt));
        var duplicate=new ru.voboost.tests.logging.InstallerLogs.Snapshot(BOOT,"a".repeat(32),java.math.BigInteger.valueOf(3),java.math.BigInteger.valueOf(3),java.util.Set.of(java.math.BigInteger.ONE,java.math.BigInteger.TWO,java.math.BigInteger.valueOf(3)),java.time.Instant.EPOCH,java.util.List.of(),java.util.List.of(new ru.voboost.tests.logging.InstallerLogs.RootEffect(java.math.BigInteger.TWO,fields),new ru.voboost.tests.logging.InstallerLogs.RootEffect(java.math.BigInteger.valueOf(3),fields)),java.nio.file.Path.of("evidence"));
        assertThrows(IOException.class,()->window.stateReplyLosses("vehicle-drive-pedestrian-warning",prior,duplicate));
    }
    @Test void payloadAdmissionRequiresTheSameLiveProducerLifetime()throws Exception {
        var window=window();window.observe(envelope("armed").toString());window.requirePublisher(BOOT+":43:400");
        for(String publisher:new String[]{BOOT+":44:400",BOOT+":43:401","foreign:43:400",BOOT+":43"})assertThrows(IOException.class,()->window.requirePublisher(publisher));
        window.release();assertThrows(IOException.class,()->window.requirePublisher(BOOT+":43:400"));
    }

}
