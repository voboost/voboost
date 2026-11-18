package ru.voboost.tests.logging;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.tests.HostProcess;
import ru.voboost.tests.RunFiles;

class InstallerCliLogsTest {
    @TempDir Path directory;
    private static final String BOOT="11111111-1111-1111-1111-111111111111", FOREIGN_BOOT="22222222-2222-2222-2222-222222222222", WRITER="a".repeat(32), OTHER_WRITER="b".repeat(32);
    private static final BigInteger MAX=new BigInteger("18446744073709551615");
    private static final Instant NOW = Instant.parse("2026-10-28T17:00:00Z");
    private JsonObject row(String sequence, String locale) throws Exception {
        JsonObject record = RunFiles.parse("{\"schemaVersion\":1,\"sequence\":" + sequence + ",\"timestamp\":\"2026-10-28T17:00:00Z\",\"source\":{\"kind\":\"agent\",\"id\":\"ui-qinggan\"},\"level\":\"info\",\"component\":\"ui-qinggan\",\"message\":\"\",\"fields\":{}}");
        var diagnostic = RunFiles.parse("{\"agent\":\"ui-qinggan\",\"event\":\"ui.compose\",\"outcome\":\"complete\",\"fields\":{\"defaultLocale\":\"" + locale + "-US\",\"resourceLocale\":\"" + locale + "-US\",\"effectiveLanguage\":\"" + locale + "\",\"elapsed_ms\":1}}");
        record.addProperty("schemaVersion",2);record.addProperty("bootId",BOOT);record.addProperty("writerId",WRITER);
        record.addProperty("message", "VOBDBG1 " + diagnostic);
        record.add("fields", RunFiles.parse("{\"process\":\"com.qinggan.app.setting\",\"bootId\":\"boot\",\"targetPid\":\"10\",\"targetStarttime\":\"20\",\"endpointEpoch\":\"3\"}"));
        record.getAsJsonObject("fields").addProperty("bootId",BOOT);return record;
    }
    private InstallerLogs.Snapshot read(String raw, String boot) throws Exception {
        Path path = directory.resolve("records-" + java.util.UUID.randomUUID()); Files.writeString(path, raw);
        var scope=new InstallerLogs.CurrentSession(boot.equals("boot")?BOOT:FOREIGN_BOOT,WRITER,MAX); var first=InstallerLogs.records(path,scope,NOW,path); return InstallerLogs.records(path,new InstallerLogs.CurrentSession(scope.boot(),WRITER,first.sequence()),NOW,path);
    }
    private JsonObject sessionContext(String writer,String sequence)throws Exception {
        JsonObject session=new JsonObject();session.addProperty("availability","available");session.addProperty("bootId",BOOT);session.addProperty("writerId",writer);session.add("sequence",new com.google.gson.JsonPrimitive(new com.google.gson.internal.LazilyParsedNumber(sequence)));JsonObject context=new JsonObject();context.add("currentLogSession",session);return context;
    }
    private InstallerLogs.Snapshot scoped(String raw,String writer,String head)throws Exception {
        Path path=directory.resolve("scoped-"+java.util.UUID.randomUUID());Files.writeString(path,raw);return InstallerLogs.records(path,InstallerLogs.currentSession(sessionContext(writer,head),BOOT),NOW,path);
    }
    @Test void canonicalUnattributedImplantDiagnosticsRemainOpaqueAndNeverBecomeAgentEvidence()throws Exception {
        var record=row("1","en");record.addProperty("component","implant");record.add("source",RunFiles.parse("{\"kind\":\"daemon\"}"));
        record.add("fields",RunFiles.parse("{\"declaredSource\":\"runtime\",\"producerTimestampMs\":\"18446744073709551615\",\"producerClock\":\"unix\"}"));
        var snapshot=scoped(record+"\n",WRITER,"1");assertTrue(snapshot.diagnostics().isEmpty());assertTrue(snapshot.effects().isEmpty());assertEquals(BigInteger.ONE,snapshot.sequence());
        record.getAsJsonObject("fields").addProperty("targetPid","41");assertThrows(IOException.class,()->scoped(record+"\n",WRITER,"1"));
    }
    @Test void initialSeedingSealRequiresClosedRootAuthorityAndCanonicalOperationFloor() throws Exception {
        JsonObject record=row("5","en");record.addProperty("component","initial-seeding");record.addProperty("message","initial-state-capture-sealed");record.add("source",RunFiles.parse("{\"kind\":\"daemon\"}"));record.add("fields",RunFiles.parse("{\"transactionId\":\""+"a".repeat(64)+"\",\"candidate\":\""+"b".repeat(64)+"\",\"lastSequence\":\"4\"}"));
        var snapshot=scoped(record+"\n",WRITER,"5");assertEquals(1,snapshot.daemonEvents().size());assertEquals("initial-state-capture-sealed",snapshot.daemonEvents().get(0).message());assertTrue(snapshot.effects().isEmpty());
        for(String invalid:List.of("agentSource","credential","future","badCandidate","numericFloor","zeroFloor")){JsonObject bad=record.deepCopy();switch(invalid){case "agentSource"->bad.add("source",RunFiles.parse("{\"kind\":\"agent\",\"id\":\"ui-qinggan\"}"));case "credential"->bad.getAsJsonObject("fields").addProperty("ticket","must-not-export");case "future"->bad.getAsJsonObject("fields").addProperty("lastSequence","5");case "badCandidate"->bad.getAsJsonObject("fields").addProperty("candidate","invalid");case "numericFloor"->bad.getAsJsonObject("fields").addProperty("lastSequence",4);case "zeroFloor"->bad.getAsJsonObject("fields").addProperty("lastSequence","0");}assertThrows(IOException.class,()->scoped(bad+"\n",WRITER,"5"),invalid);}
    }
    @Test void nativeCommandPhasesKeepCurrentWriterAuthorityWithoutTreatingAcceptanceAsEcuEffect()throws Exception {
        JsonObject record=row("1","en");record.addProperty("component","native");record.addProperty("message","native command phase changed");record.addProperty("level","debug");record.add("source",RunFiles.parse("{\"kind\":\"daemon\"}"));record.add("fields",RunFiles.parse("{\"agent\":\"vehicle-drive-pedestrian-warning\",\"commandId\":\"1\",\"state\":\"HUM_VSP_FUNCTION_SW\",\"value\":\"1\",\"phase\":\"accepted\",\"observedBootMs\":\"123\"}"));
        var snapshot=scoped(record+"\n",WRITER,"1");assertEquals(1,snapshot.daemonEvents().size());assertTrue(snapshot.effects().isEmpty());assertTrue(snapshot.diagnostics().isEmpty());
        for(String invalid:List.of("agentSource","credential","unknownPhase","badTime")){JsonObject bad=record.deepCopy();switch(invalid){case "agentSource"->bad.add("source",RunFiles.parse("{\"kind\":\"agent\",\"id\":\"vehicle-drive-pedestrian-warning\"}"));case "credential"->bad.getAsJsonObject("fields").addProperty("ownerSession","must-not-export");case "unknownPhase"->bad.getAsJsonObject("fields").addProperty("phase","applied");case "badTime"->bad.getAsJsonObject("fields").addProperty("observedBootMs","1.5");}assertThrows(IOException.class,()->scoped(bad+"\n",WRITER,"1"),invalid);}
    }
    @Test void canonicalCurrentWriterRootStateOperationsRetainExplicitFalseAndRejectAgentForgery()throws Exception {
        JsonObject record=row("1","en");record.addProperty("component","agent-state");record.addProperty("message","vehicle-state-observed");record.add("source",RunFiles.parse("{\"kind\":\"daemon\"}"));
        record.add("fields",RunFiles.parse("{\"agentId\":\"vehicle-drive-pedestrian-warning\",\"key\":\"pedestrian-warning-armed\",\"operation\":\"read\",\"outcome\":\"value\",\"value\":\"false\",\"process\":\"com.qinggan.systemservice\",\"targetPid\":\"41\",\"endpointEpoch\":\"8\",\"requestId\":\"7\"}"));
        var current=scoped(record+"\n",WRITER,"1");assertEquals(1,current.effects().size());assertEquals("false",current.effects().get(0).fields().get("value").getAsString());
        record.add("source",RunFiles.parse("{\"kind\":\"agent\",\"id\":\"vehicle-drive-pedestrian-warning\"}"));
        assertThrows(IOException.class,()->scoped(record+"\n",WRITER,"1"));
    }
    @Test void negativeAssertionsRequireCompleteTailThroughLiveWatermark()throws Exception {
        var prior=scoped(row("5","en")+"\n",WRITER,"10");
        var partial=scoped(row("11","en")+"\n",WRITER,"12");
        assertEquals(1,partial.after(prior).size());
        assertThrows(IOException.class,()->partial.requireCompleteExportAfter(prior));
        var complete=scoped(row("11","en")+"\n"+row("12","en")+"\n",WRITER,"12");
        complete.requireCompleteExportAfter(prior);
        var gap=scoped(row("11","en")+"\n"+row("13","en")+"\n",WRITER,"13");
        assertThrows(IOException.class,()->gap.requireCompleteExportAfter(prior));
    }
    @Test void liveWatermarkNeverSkipsAnActuallyExportedFutureRow()throws Exception {
        var prior=scoped(row("5","en")+"\n",WRITER,"10");assertEquals(BigInteger.valueOf(5),prior.sequence());assertEquals(BigInteger.TEN,prior.watermark());
        var current=scoped(row("6","zh")+"\n"+row("5","en")+"\n",WRITER,"12");assertEquals(BigInteger.valueOf(6),current.newlyExportedAfter(prior).get(0).sequence());
        assertThrows(InstallerLogs.LocalePending.class,()->InstallerLogs.renderLocale(prior,current,"zh"));
        var fresh=scoped(row("6","en")+"\n"+row("11","zh")+"\n",WRITER,"12");
        assertEquals(BigInteger.valueOf(11),InstallerLogs.renderLocale(prior,fresh,"zh").sequence());
        var gap=scoped(row("11","en")+"\n"+row("13","zh")+"\n",WRITER,"13");
        assertThrows(IOException.class,()->InstallerLogs.renderLocale(prior,gap,"zh"));
    }
    @Test void explicitCurrentScopeOverridesHistoricalTimestampSequenceAndWriterOrder()throws Exception {
        JsonObject historical=row("9007199254740993","en");historical.addProperty("writerId",OTHER_WRITER);historical.addProperty("timestamp","2099-01-01T00:00:00Z");
        var current=scoped(row("1","zh")+"\n"+historical+"\n",WRITER,"2");assertEquals(BigInteger.ONE,current.sequence());assertEquals(1,current.diagnostics().size());assertEquals("zh",current.diagnostics().get(0).fields().get("effectiveLanguage").getAsString());
        JsonObject legacy=row("999","en");legacy.addProperty("schemaVersion",1);legacy.remove("bootId");legacy.remove("writerId");
        var onlyHistory=scoped(legacy+"\n"+historical+"\n",WRITER,"2");assertEquals(BigInteger.ZERO,onlyHistory.sequence());assertTrue(onlyHistory.diagnostics().isEmpty());assertThrows(IOException.class,()->InstallerLogs.renderLocale(onlyHistory,onlyHistory,"en"));
    }
    @Test void sameBootWriterRestartAndUnavailableSessionCannotBorrowHistoricalEvidence()throws Exception {
        var before=scoped(row("100","en")+"\n",WRITER,"100");JsonObject fresh=row("1","zh");fresh.addProperty("writerId",OTHER_WRITER);var after=scoped(fresh+"\n",OTHER_WRITER,"1");assertThrows(IOException.class,()->after.after(before));
        JsonObject unavailable=RunFiles.parse("{\"currentLogSession\":{\"availability\":\"unavailable\",\"reason\":\"owner-unavailable-or-changed\"}}");assertThrows(IOException.class,()->InstallerLogs.currentSession(unavailable,BOOT));assertThrows(IOException.class,()->InstallerLogs.currentSession(new JsonObject(),BOOT));
    }
    @Test void observedAdbLogSessionCannotProvideCausalOrNegativeEvidence() throws Exception {
        JsonObject observed = RunFiles.parse("""
            {"availability":"available","bootId":"%s","writerId":"%s",
             "highestObservedSequence":18446744073709551615,"authority":"observed-open-android-segment"}
            """.formatted(BOOT, WRITER));
        JsonObject context = new JsonObject();
        context.add("observedLogSession", observed);
        for (boolean mixed : List.of(false, true)) {
            if (mixed) context.add("currentLogSession", sessionContext(WRITER, MAX.toString()).get("currentLogSession"));
            IOException blocked = assertThrows(IOException.class, () -> InstallerLogs.currentSession(context, BOOT));
            assertTrue(blocked.getMessage().contains("causal and negative checks are inconclusive"));
        }
    }

    @Test void sessionAndRecordU64RemainExactAndStrict()throws Exception {
        assertEquals(MAX,InstallerLogs.currentSession(sessionContext(WRITER,MAX.toString()),BOOT).watermark());var exact=scoped(row(MAX.toString(),"en")+"\n",WRITER,MAX.toString());assertEquals(MAX,exact.sequence());
        for(String bad:List.of("-1","1.0","1e1","18446744073709551616")){assertThrows(IOException.class,()->InstallerLogs.currentSession(sessionContext(WRITER,bad),BOOT));}
        JsonObject stringCounter=sessionContext(WRITER,"1");stringCounter.getAsJsonObject("currentLogSession").addProperty("sequence","1");assertThrows(IOException.class,()->InstallerLogs.currentSession(stringCounter,BOOT));
        assertThrows(IOException.class,()->scoped(row("2","en")+"\n",WRITER,"1"));
        JsonObject wrong=sessionContext(WRITER,"1");wrong.getAsJsonObject("currentLogSession").addProperty("bootId",FOREIGN_BOOT);assertThrows(IOException.class,()->InstallerLogs.currentSession(wrong,BOOT));
    }
    @Test void freshRenderRequiresNewSequenceSameBootAndActualProcessResourceLocales() throws Exception {
        var prior = read(row("9007199254740992", "en") + "\n", "boot");
        var current = read(row("9007199254740992", "en") + "\n" + row("9007199254740993", "zh") + "\n", "boot");
        assertEquals(new BigInteger("9007199254740993"), InstallerLogs.renderLocale(prior, current, "zh").sequence());
        assertThrows(IOException.class, () -> InstallerLogs.renderLocale(prior, prior, "en"));
        assertThrows(IOException.class, () -> InstallerLogs.renderLocale(prior, current, "en"));
        JsonObject foreign = row("9007199254740993", "zh"); foreign.getAsJsonObject("fields").addProperty("process", "com.qinggan.app.vehiclesetting");
        assertThrows(IOException.class, () -> InstallerLogs.renderLocale(prior, read(foreign + "\n", "boot"), "zh"));
        assertThrows(IOException.class, () -> InstallerLogs.renderLocale(prior, read(row("9007199254740993", "zh") + "\n", "other-boot"), "zh"));
    }
    @Test void malformedDuplicateAndUnterminatedRecordsCannotProvideProof() throws Exception {
        String valid = row("1", "en") + "\n";
        JsonObject forged = row("1", "en"); forged.getAsJsonObject("source").addProperty("id", "forged");
        for (String raw : List.of(valid + valid, valid.replace("\"sequence\":1", "\"sequence\":\"1\""), valid.strip(), forged + "\n"))
            assertThrows(IOException.class, () -> read(raw, "boot"));
    }
    @Test void processStreamingRetainsTheArtifactAndRejectsAnOversizedOutput() throws Exception {
        Path data = directory.resolve("data"); Files.writeString(data, "01234567890123456789");
        assertEquals(0, HostProcess.produceFile(directory, directory.resolve("copy"), directory.resolve("err"), List.of("/bin/cat", data.toString()), 5000, 20));
        assertThrows(IOException.class, () -> HostProcess.produceFile(directory, directory.resolve("oversized"), directory.resolve("oversized.err"), List.of("/bin/cat", data.toString()), 5000, 5));
    }
    @Test void nativeCompressedArchiveBindsTheCollectionAndExactCandidate() throws Exception {
        archiveRoundTrip(0, "verified-stream", null, true);
    }
    @Test void largeSystemDiagnosticPreservesVerifiedProductStream() throws Exception {
        archiveRoundTrip(5 * 1024 * 1024, "verified-stream", null, true);
    }
    @Test void completeDiagnosticArchiveKeepsTheExactCurrentProductScope() throws Exception {
        archiveRoundTrip(0, "complete", "complete", true);
    }
    @Test void partialDiagnosticArchiveCannotProvideFunctionalEvidence() throws Exception {
        archiveRoundTrip(0, "partial", "partial", false);
    }
    @Test void failedDiagnosticArchiveCannotProvideFunctionalEvidence() throws Exception {
        archiveRoundTrip(0, "failed", "failed", false);
    }
    @Test void completeEnvelopeCannotOverrideAnIncompleteBundle() throws Exception {
        archiveRoundTrip(0, "complete", "partial", false);
    }
    @Test void legacyEnvelopeCannotOverrideANewPartialBundle() throws Exception {
        archiveRoundTrip(0, "verified-stream", "partial", false);
    }
    private void archiveRoundTrip(int diagnosticBytes, String completeness, String bundleStatus,
            boolean accepted) throws Exception {
        String raw = row("1", "en") + "\n", context = "{\"schemaVersion\":1,\"manifestSha256\":\"candidate\",\"releaseIdentity\":\"1.0.0-beta1\",\"logSource\":\"root-owned-segments\"}";
        JsonObject scopedContext=RunFiles.parse(context);scopedContext.add("currentLogSession",sessionContext(WRITER,"1").getAsJsonObject("currentLogSession"));if (diagnosticBytes > 0) {
            JsonObject bundle = new JsonObject(); bundle.addProperty("body", "x".repeat(diagnosticBytes));
            scopedContext.add("diagnosticBundle", bundle);
        }
        if (bundleStatus != null) {
            JsonObject bundle = new JsonObject();
            bundle.addProperty("completeness", bundleStatus);
            scopedContext.add("diagnosticBundle", bundle);
        }
        context=scopedContext.toString();
        JsonObject manifest = RunFiles.parse("{\"schemaVersion\":1,\"format\":\"VBOOSTLOG1\",\"files\":[\"records.jsonl\",\"context.json\"],\"header\":{\"schemaVersion\":1},\"hostEnvelope\":{\"deviceSerial\":\"emulator-5588\",\"installerVersion\":\"1.0.0-beta1\",\"completeness\":\"verified-stream\",\"collectedAtUtc\":\"2026-10-28T17:00:00Z\"}}");
        manifest.getAsJsonObject("hostEnvelope").addProperty("completeness", completeness);
        manifest.getAsJsonObject("header").addProperty("recordsLength", raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        manifest.getAsJsonObject("header").addProperty("contextLength", context.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        Files.writeString(directory.resolve("records.jsonl"), raw); Files.writeString(directory.resolve("context.json"), context); Files.writeString(directory.resolve("manifest.json"), manifest.toString());
        Path archive = directory.resolve("logs.tar.xz");
        assertEquals(0, HostProcess.runAt(directory, directory.resolve("pack.log"), List.of("/usr/bin/tar", "--format=ustar", "-cJf", archive.toString(), "manifest.json", "records.jsonl", "context.json"), 30000, java.util.Map.of()).exit());
        if (!accepted) {
            assertThrows(IOException.class, () -> InstallerLogs.read(archive, directory, "emulator-5588",
                    "1.0.0-beta1", "candidate", BOOT, NOW.minusSeconds(1), NOW.plusSeconds(1)));
            return;
        }
        var result = InstallerLogs.read(archive, directory, "emulator-5588", "1.0.0-beta1", "candidate", BOOT, NOW.minusSeconds(1), NOW.plusSeconds(1));
        assertEquals(BigInteger.ONE, result.sequence());
        assertThrows(IOException.class, () -> InstallerLogs.read(archive, directory, "foreign", "1.0.0-beta1", "candidate", "boot", NOW.minusSeconds(1), NOW.plusSeconds(1)));
        assertThrows(IOException.class, () -> InstallerLogs.read(archive, directory, "emulator-5588", "1.0.0-beta1", "candidate", "boot", NOW.plusSeconds(2), NOW.plusSeconds(3)));
    }    private JsonObject fault(String sequence) throws Exception {
        JsonObject record = row(sequence, "en"); record.add("source", RunFiles.parse("{\"kind\":\"daemon\"}"));
        record.addProperty("component", "emulator-diagnostic"); record.addProperty("message", "fault-effect");
        record.add("fields", RunFiles.parse("""
            {"token":"token","kind":"startup-mutation-lock","effect":"startup-mutation-rejected","source":"settings","requestId":"7",
            "bootId":"boot","manifestSha256":"manifest","generationId":"generation","catalogSha256":"catalog","targetPid":"41","targetStarttime":"514","process":"com.qinggan.app.setting"}
            """)); record.getAsJsonObject("fields").addProperty("bootId",BOOT);return record;
    }
    @Test void canonicalRootEffectsRemainBoundedFreshAndIndependentOfAgentDiagnostics() throws Exception {
        var prior = read(fault("9007199254740992") + "\n", "boot");
        var current = read(fault("9007199254740992") + "\n" + fault("9007199254740993") + "\n", "boot");
        assertTrue(current.diagnostics().isEmpty()); assertEquals(1, current.effectsAfter(prior).size());
        assertEquals(new BigInteger("9007199254740993"), current.effectsAfter(prior).get(0).sequence());
        current.effects().get(0).fields().addProperty("process", "foreign");
        assertEquals("com.qinggan.app.setting", current.effects().get(0).fields().get("process").getAsString());
        assertThrows(IOException.class, () -> current.effectsAfter(read(fault("1") + "\n", "other")));
    }
    @Test void agentSpoofForeignBootNumericOrExtraFaultFieldsCannotBecomeRootEvidence() throws Exception {
        for (String target : new String[]{"source", "component", "level", "boot", "number", "extra"}) {
            var record = fault("1");
            switch (target) {
                case "source" -> record.add("source", RunFiles.parse("{\"kind\":\"agent\",\"id\":\"settings\"}"));
                case "component", "level" -> record.addProperty(target, "foreign");
                case "boot" -> record.getAsJsonObject("fields").addProperty("bootId", "foreign");
                case "number" -> record.getAsJsonObject("fields").addProperty("targetPid", 41);
                case "extra" -> record.getAsJsonObject("fields").addProperty("unknown", "extra");
            }
            assertThrows(IOException.class, () -> read(record + "\n", "boot"));
        }
    }

    @Test void authenticatedAgentEventMayOmitEmptyDiagnosticFields() throws Exception {
        JsonObject record = row("1", "en");
        JsonObject body = RunFiles.parse(record.get("message").getAsString().substring(8));
        body.remove("fields");
        record.addProperty("message", "VOBDBG1 " + body);
        var snapshot = scoped(record + "\n", WRITER, "1");
        assertEquals(1, snapshot.diagnostics().size());
        assertTrue(snapshot.diagnostics().get(0).fields().isEmpty());
        for (String invalid : List.of("null", "[]", "false")) {
            body.add("fields", com.google.gson.JsonParser.parseString(invalid));
            record.addProperty("message", "VOBDBG1 " + body);
            assertThrows(IOException.class, () -> scoped(record + "\n", WRITER, "1"));
        }
    }
}
