package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.tests.RunFiles;
import ru.voboost.tests.GateStatus;

class InitialSeedingAdmissionTest {
    @Test
    void missingEarlyCapturePublishesBlockedWithoutAuthorizingMutation() throws Exception {
        var proof = InitialSeedingAdmission.blocked("session", "/owned/machine",
                "emulator-5554", "same-candidate");
        assertFalse(proof.get("mutationStarted").getAsBoolean());
        assertFalse(proof.get("functionalAcceptance").getAsBoolean());
        var result = SourceCaseEvidence.evaluate(24, proof, Path.of("/owned/admission.json"));
        assertEquals("initial-state-seeding", result.key().id());
        assertEquals(GateStatus.BLOCKED, result.result().status());
        assertTrue(result.result().reason().contains("post-export watermark"));
    }

    @Test
    void unboundAdmissionCannotProduceEvidence() {
        assertThrows(IOException.class,
                () -> InitialSeedingAdmission.blocked("", "/owned/machine", "serial", "candidate"));
    }
    @TempDir Path directory;

    private Path admission() throws Exception {
        Path file = directory.resolve("admission.json");
        RunFiles.write(file, InitialSeedingAdmission.blocked("session",
                "/owned/machine", "emulator-5554", "same-candidate"));
        return file;
    }

    @Test
    void blockedBeforeMutationRunsTheIndependentCaseExactlyOnce() throws Exception {
        int[] calls = {0};
        String result = InitialSeedingAdmission.thenIndependent(this::admission, () -> {
            calls[0]++;
            return "independent complete";
        });
        assertEquals("independent complete", result);
        assertEquals(1, calls[0]);
    }

    @Test
    void unknownDispatchStopsWithoutIndependentCaseOrReplay() {
        int[] calls = {0};
        IOException unknown = new IOException("dispatched product outcome unknown");
        Exception observed = assertThrows(IOException.class,
                () -> InitialSeedingAdmission.thenIndependent(() -> {
                    calls[0]++;
                    throw unknown;
                }, () -> {
                    calls[0] += 100;
                    return null;
                }));
        assertSame(unknown, observed);
        assertEquals(1, calls[0]);
    }

    @Test
    void mutatedBlockedReceiptCannotContinueIndependentCases() throws Exception {
        Path file = admission();
        var proof = RunFiles.read(file);
        proof.addProperty("mutationStarted", true);
        RunFiles.write(file, proof);
        assertThrows(IOException.class,
                () -> InitialSeedingAdmission.thenIndependent(() -> file, () -> {
                    fail("unknown mutation must retain ownership and stop");
                    return null;
                }));
    }

    @Test
    void configurationWithoutNormalUiSettersIsRejectedBeforeCleanup() throws Exception {
        var catalog = RunFiles.parse(
                "{\"agents\":[{\"id\":\"agent\",\"status\":\"stable\","
                        + "\"activation\":{\"type\":\"checkbox\"},\"disables\":[]}]}");

        var agents = RunFiles.parse(
                "{\"agent\":{\"enabled\":true,\"options\":{}}}");
        InitialSeedingAdmission.requireRestorableConfiguration(catalog, agents, "interface", "interface");
        agents.getAsJsonObject("agent").getAsJsonObject("options").addProperty("custom", true);
        assertThrows(IOException.class, () -> InitialSeedingAdmission.requireRestorableConfiguration(
                catalog, agents, "interface", "interface"));
        var noOptions = RunFiles.parse(
                "{\"agent\":{\"enabled\":true,\"options\":{}}}");
        assertThrows(IOException.class, () -> InitialSeedingAdmission.requireRestorableConfiguration(
                catalog, noOptions, "hidden", "interface"));
    }

    @Test
    void case24CannotPublishAcceptanceWithoutTheMissingEarlyCapture() throws Exception {
        var proof = InitialSeedingAdmission.blocked("session", "/owned/machine", "serial", "kit");
        proof.addProperty("semanticFunctionalStatus", "PASS");
        proof.addProperty("functionalAcceptance", true);
        assertThrows(IOException.class,
                () -> SourceCaseEvidence.evaluate(24, proof, Path.of("/owned/admission.json")));
    }

    @Test
    void normalRestorationRejectsConflictsAndMissingStableRows() throws Exception {
        var catalog = RunFiles.parse("""
                {"agents":[
                  {"id":"keyboard-en","status":"stable","activation":{"type":"checkbox"},
                   "disables":["keyboard-ru"]},
                  {"id":"keyboard-ru","status":"stable","activation":{"type":"checkbox"},
                   "disables":["keyboard-en"]}
                ]}
                """);
        var agents = RunFiles.parse("""
                {"keyboard-en":{"enabled":true,"options":{}},
                 "keyboard-ru":{"enabled":false,"options":{}}}
                """);
        InitialSeedingAdmission.requireRestorableConfiguration(catalog, agents,
                "interface", "interface");
        agents.getAsJsonObject("keyboard-ru").addProperty("enabled", true);
        assertThrows(IOException.class, () -> InitialSeedingAdmission.requireRestorableConfiguration(
                catalog, agents, "interface", "interface"));
        agents.remove("keyboard-ru");
        assertThrows(IOException.class, () -> InitialSeedingAdmission.requireRestorableConfiguration(
                catalog, agents, "interface", "interface"));
    }

    @Test
    void capturedReceiptRejectsScopeAndTimingChanges() throws Exception {
        var receipt = RunFiles.parse("""
                {"schemaVersion":1,"bootId":"11111111-1111-4111-8111-111111111111",
                 "writerId":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                 "transactionId":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                 "candidate":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                 "initialWriterFloor":5,"installState":"unseeded","captureState":"complete",
                 "operations":[
                   {"key":"vehicle-drive-forced-ev/forced-ev-armed","operation":"read","outcome":"missing","sequence":6},
                   {"key":"vehicle-drive-forced-ev/forced-ev-armed","operation":"write-readback","outcome":"value","value":false,"sequence":7},
                   {"key":"vehicle-drive-pedestrian-warning/pedestrian-warning-armed","operation":"read","outcome":"missing","sequence":8},
                   {"key":"vehicle-drive-pedestrian-warning/pedestrian-warning-armed","operation":"write-readback","outcome":"value","value":false,"sequence":9}
                 ]}
                """);
        var scope = new ru.voboost.tests.agents.InitialSeedingProgram.Scope("s", "m", "d", "c".repeat(64));
        String boot = "11111111-1111-4111-8111-111111111111";
        String writer = "a".repeat(32), transaction = "b".repeat(64);
        var snapshot = seal(boot, writer);
        InitialSeedingAdmission.capture(receipt, scope, boot, writer, transaction, Path.of("receipt"), snapshot);
        var missingSeal = new ru.voboost.tests.logging.InstallerLogs.Snapshot(boot, writer,
                java.math.BigInteger.TEN, java.math.BigInteger.TEN, java.util.Set.of(java.math.BigInteger.TEN),
                java.time.Instant.EPOCH, java.util.List.of(), java.util.List.of(), Path.of("log"));
        assertThrows(IOException.class, () -> InitialSeedingAdmission.capture(receipt, scope, boot,
                writer, transaction, Path.of("receipt"), missingSeal));
        assertThrows(IOException.class, () -> InitialSeedingAdmission.capture(receipt, scope, boot,
                writer, transaction, Path.of("receipt"), seal(boot, "d".repeat(32))));
        var wrongFields = snapshot.daemonEvents().get(0).fields().deepCopy();
        wrongFields.addProperty("lastSequence", "8");
        var badSeal = new ru.voboost.tests.logging.InstallerLogs.Snapshot(boot, writer,
                java.math.BigInteger.TEN, java.math.BigInteger.TEN, java.util.Set.of(java.math.BigInteger.TEN),
                java.time.Instant.EPOCH, java.util.List.of(), java.util.List.of(), Path.of("log"),
                java.util.List.of(new ru.voboost.tests.logging.InstallerLogs.DaemonEvent(java.math.BigInteger.TEN,
                        "initial-seeding", "initial-state-capture-sealed", wrongFields)));
        assertThrows(IOException.class, () -> InitialSeedingAdmission.capture(receipt, scope, boot,
                writer, transaction, Path.of("receipt"), badSeal));
        for (String field : new String[] {"bootId", "writerId", "transactionId", "candidate", "installState", "captureState"}) {
            var changed = receipt.deepCopy();
            changed.addProperty(field, "unknown");
            assertThrows(IOException.class, () -> InitialSeedingAdmission.capture(changed, scope, boot, writer, transaction, Path.of("receipt"), snapshot), field);
        }
        var late = receipt.deepCopy();
        late.addProperty("initialWriterFloor", 6);
        assertThrows(IOException.class, () -> InitialSeedingAdmission.capture(late, scope, boot, writer, transaction, Path.of("receipt"), snapshot));
        var seeded = receipt.deepCopy();
        seeded.addProperty("installState", "seeded");
        assertThrows(IOException.class, () -> InitialSeedingAdmission.capture(seeded, scope, boot, writer, transaction, Path.of("receipt"), snapshot));
    }

    @Test
    void nullScopeCannotBecomeAnUnknownOutcome() throws Exception {
        var scope = new ru.voboost.tests.agents.InitialSeedingProgram.Scope("s", "m", "d", "c".repeat(64));
        var receipt = RunFiles.parse("""
                {"schemaVersion":1,"bootId":"11111111-1111-4111-8111-111111111111",
                 "writerId":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                 "transactionId":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                 "candidate":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                 "initialWriterFloor":0,"installState":"unseeded","captureState":"complete","operations":[]}
                """);
        assertThrows(IOException.class, () -> InitialSeedingAdmission.capture(receipt, scope, null,
                "a".repeat(32), "b".repeat(64), Path.of("receipt"), seal("11111111-1111-4111-8111-111111111111", "a".repeat(32))));
    }

    private static ru.voboost.tests.logging.InstallerLogs.Snapshot seal(String boot, String writer) throws IOException {
        var event = new ru.voboost.tests.logging.InstallerLogs.DaemonEvent(java.math.BigInteger.TEN,
                "initial-seeding", "initial-state-capture-sealed",
                RunFiles.parse("{\"transactionId\":\"" + "b".repeat(64) + "\",\"candidate\":\"" + "c".repeat(64) + "\",\"lastSequence\":\"9\"}"));
        return new ru.voboost.tests.logging.InstallerLogs.Snapshot(boot, writer, java.math.BigInteger.TEN,
                java.math.BigInteger.TEN, java.util.Set.of(java.math.BigInteger.TEN), java.time.Instant.EPOCH,
                java.util.List.of(), java.util.List.of(), Path.of("log"), java.util.List.of(event));
    }

    @Test
    void terminalCannotContinueFromBooleanOnlyOrUnknownMutation() throws Exception {
        var forged = InitialSeedingAdmission.blocked("session", "/machine", "emulator-5554", "a".repeat(64));
        forged.addProperty("semanticFunctionalStatus", "PASS");
        forged.addProperty("functionalAcceptance", true);
        forged.addProperty("mutationStarted", true);
        Path file = directory.resolve("forged.json"); RunFiles.write(file, forged);
        int[] calls = {0};
        assertThrows(IOException.class, () -> InitialSeedingAdmission.thenIndependent(() -> file, () -> { calls[0]++; return null; }, null, null));
        assertEquals(0, calls[0]);
        forged.addProperty("semanticFunctionalStatus", "BLOCKED");
        forged.addProperty("functionalAcceptance", false);
        RunFiles.write(file, forged);
        assertThrows(IOException.class, () -> InitialSeedingAdmission.thenIndependent(() -> file, () -> { calls[0]++; return null; }, null, null));
        assertEquals(0, calls[0]);
        forged.addProperty("mutationStarted", false); RunFiles.write(file, forged);
        assertEquals("continued", InitialSeedingAdmission.thenIndependent(() -> file, () -> { calls[0]++; return "continued"; }, null, null));
        assertEquals(1, calls[0]);
    }

    @Test
    void terminalReevaluatesTwoCleanCyclesAndCannotReplayUnknown() throws Exception {
        var fixture = new TerminalFixture(directory);
        FixtureProof result = fixture.make();
        assertTrue(InitialSeedingAdmission.requireTerminal(result.proof(), result.installer(), result.catalog()));
        Path anotherCli=directory.resolve("another-installer");Files.writeString(anotherCli,"other");anotherCli.toFile().setExecutable(true);
        assertThrows(IOException.class, () -> InitialSeedingAdmission.requireTerminal(result.proof(), anotherCli, result.catalog()));
        Path anotherCatalog=directory.resolve("another-catalog.json");Files.writeString(anotherCatalog,"{}\n");
        assertThrows(IOException.class, () -> InitialSeedingAdmission.requireTerminal(result.proof(), result.installer(), anotherCatalog));
        int[] calls = {0};
        Path proofPath = fixture.write("terminal.json", result.proof());
        assertEquals("independent", InitialSeedingAdmission.thenIndependent(() -> proofPath, () -> { calls[0]++; return "independent"; }, result.installer(), result.catalog()));
        assertEquals(1, calls[0]);
        for (String attack : new String[] {"wrongCandidate", "wrongWriter", "wrongTransaction", "missingSeal", "sameBoot", "badRestore", "unknownOperation", "wrongOem", "forgedOutput", "fakeAbsence"}) {
            FixtureProof next = new TerminalFixture(Files.createTempDirectory(directory, "case24-")).make();
            var proof = next.proof(); var sample = proof.getAsJsonArray("samples").get(0).getAsJsonObject();
            switch (attack) {
                case "wrongCandidate" -> proof.getAsJsonObject("scope").addProperty("candidate", "d".repeat(64));
                case "wrongWriter" -> sample.getAsJsonObject("capture").addProperty("writer", "d".repeat(32));
                case "wrongTransaction" -> sample.getAsJsonObject("capture").addProperty("transaction", "e".repeat(64));
                case "forgedOutput" -> {var op=RunFiles.read(Path.of(sample.get("installOperation").getAsString()));String out=op.getAsJsonObject("input").getAsJsonObject("process").get("stdout").getAsString();Path work=Path.of(proof.getAsJsonObject("scope").get("machine").getAsString()).getParent();Files.writeString(work.resolve(out),"Install completed: transaction "+"f".repeat(64)+"\n");}
                case "fakeAbsence" -> {var p=Path.of(sample.getAsJsonObject("absence").get("receipt").getAsString());var r=RunFiles.read(p);r.getAsJsonArray("checks").remove(0);Files.writeString(p,r.toString());}
                case "missingSeal" -> Files.writeString(Path.of(sample.getAsJsonObject("capture").get("logsArchive").getAsString()), "bad");
                case "sameBoot" -> sample.getAsJsonObject("capture").addProperty("boot", sample.getAsJsonObject("absence").get("boot").getAsString());
                case "badRestore" -> {var p=Path.of(proof.get("restorationEvidence").getAsString());var restore=RunFiles.read(p);restore.addProperty("runningStartup","none");Files.writeString(p,restore.toString());}
                case "unknownOperation" -> {var p=Path.of(sample.get("installOperation").getAsString());var op=RunFiles.read(p);op.addProperty("state","unknown");Files.writeString(p,op.toString());}
                case "wrongOem" -> sample.getAsJsonObject("oem").addProperty("forcedEv", true);
            }
            assertThrows(IOException.class, () -> InitialSeedingAdmission.requireTerminal(proof, next.installer(), next.catalog()), attack);
        }
    }

    private record FixtureProof(com.google.gson.JsonObject proof, Path installer, Path catalog) {}
    private static final class TerminalFixture {
        final Path work; final String session = "a".repeat(32), candidate = "b".repeat(64), writer = "c".repeat(32);
        final String run = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", version = "1.0.0-beta1";
        final String[] boots = {"11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222", "33333333-3333-4333-8333-333333333333", "44444444-4444-4444-8444-444444444444", "55555555-5555-4555-8555-555555555555", "66666666-6666-4666-8666-666666666666", "77777777-7777-4777-8777-777777777777"};
        String catalog; int serial;
        TerminalFixture(Path work) throws Exception { this.work = work.toRealPath(); }
        Path write(String name, com.google.gson.JsonObject value) throws Exception { Path p=work.resolve(name); Files.writeString(p,value.toString()+"\n");return p; }
        com.google.gson.JsonObject json(String text) throws Exception { return RunFiles.parse(text); }
        String text(Path p) { return p.toString(); }
        com.google.gson.JsonObject base(String boot, String root) throws Exception {return json("{\"schema\":2,\"source\":\"voboost\",\"status\":\"ready\",\"boot_id\":\""+boot+"\",\"data\":{\"root\":\""+root+"\",\"root_proof\":\"fixed-path-owner-mode-boot-fenced\",\"files\":{}}}");}
        com.google.gson.JsonObject file(com.google.gson.JsonElement data) throws Exception {var f=json("{\"status\":\"present\",\"current\":true,\"proof\":\"same-boot-retained-file\",\"revision\":\"r\"}");f.add("data",data);return f;}
        com.google.gson.JsonObject root(String boot,boolean fev,boolean pw) throws Exception {var x=base(boot,"present");var files=x.getAsJsonObject("data").getAsJsonObject("files");files.add("state/vehicle-drive-forced-ev_forced-ev-armed",file(new com.google.gson.JsonPrimitive(fev)));files.add("state/vehicle-drive-pedestrian-warning_pedestrian-warning-armed",file(new com.google.gson.JsonPrimitive(pw)));return x;}
        com.google.gson.JsonObject runtime(String boot) throws Exception {var x=base(boot,"present");var data=x.getAsJsonObject("data");data.addProperty("runtime_current",true);var files=data.getAsJsonObject("files");var conf=json("{\"status\":\"present\",\"revision\":\"r\",\"data\":{\"startup\":\"interface\",\"agents\":{\"agent\":{\"enabled\":true,\"options\":{}}}}}"); files.add("config",conf);var run=json("{\"status\":\"present\",\"current\":true,\"provenance\":{\"current\":true,\"proof\":\"same-boot-live-publisher\",\"age_ms\":10},\"data\":{\"publisher\":{\"bootId\":\""+boot+"\",\"daemonPid\":1,\"daemonStarttime\":2},\"releaseIdentity\":\""+version+"\",\"manifestSha256\":\""+candidate+"\",\"catalogSha256\":\""+catalog+"\",\"generationId\":\"g\",\"state\":\"ready\",\"killed\":false,\"panic\":false,\"startupMode\":\"interface\",\"injections\":[]}}");files.add("runtime",run);return x;}
        Path op(int n,String kind,String phase,String prior) throws Exception {Path d=work.resolve("operations").resolve(String.format("%06d",n));Files.createDirectories(d);var o=json("{\"schemaVersion\":1,\"runId\":\""+run+"\",\"sessionId\":\""+session+"\",\"kind\":\""+kind+"\",\"state\":\"verified\",\"input\":{\"phase\":\""+phase+"\",\"manifest\":\""+candidate+"\",\"device\":\"emulator-5554\",\"priorBoot\":\""+prior+"\"},\"evidence\":[\"dummy\"]}");var process=new com.google.gson.JsonObject();var command=new com.google.gson.JsonArray();command.add(work.resolve("installer").toString());if(kind.equals("INSTALL")){command.add("--install");command.add("--emulator-install-acceptance");}else{command.add("--uninstall");command.add("--emulator-cache-recovery-acceptance");}command.add("--device");command.add("emulator-5554");process.add("command",command);String stdout="stdout-"+n;process.addProperty("stdout",stdout);process.addProperty("stderr","dummy");o.getAsJsonObject("input").add("process",process);o.getAsJsonArray("evidence").add(stdout);String body=kind.equals("INSTALL")?"Install completed: transaction "+candidate+"\n":Files.readString(receipt(n==1?boots[1]:n==3?boots[3]:boots[5]));Files.writeString(work.resolve(stdout),body);Path p=d.resolve("operation.json");Files.writeString(p,o.toString());return p;}
        Path receipt(String boot) throws Exception {return write("absence-"+(serial++)+".json",json("{\"transactionId\":\""+candidate+"\",\"bootId\":\""+boot+"\",\"checks\":[\"root\",\"app-data\",\"init-file\",\"package\",\"app-op\",\"mounts\",\"processes\",\"init-services\",\"localization-overlays\",\"policy\"]}"));}
        Path oem(String boot, boolean fev, boolean wanted) throws Exception {
            String group=fev?"power_mode_electric_force":"fragment_safe_hum_vsp";
            var ui=json("{\"format\":\"voyemu.ui.v1\",\"profile\":\"profile\",\"session_id\":\""+session+"\",\"boot_id\":\""+boot+"\",\"source_instance\":\"source\",\"revision\":\"1\",\"screens\":[{\"screen\":\"primary\",\"status\":\"ok\",\"complete\":true,\"windows\":[{\"id\":\"w\",\"package\":\"com.qinggan.app.vehiclesetting\",\"type\":1,\"active\":true,\"input_focused\":true,\"read_complete\":true}],\"nodes\":[{\"id\":\"group\",\"window_id\":\"w\",\"parent\":null,\"package\":\"com.qinggan.app.vehiclesetting\",\"resource_id\":\"com.qinggan.app.vehiclesetting:id/"+group+"\"},{\"id\":\"c\",\"window_id\":\"w\",\"parent\":\"group\",\"package\":\"com.qinggan.app.vehiclesetting\",\"visible\":true,\"enabled\":true,\"checkable\":true,\"checked\":"+wanted+",\"bounds\":{\"x\":1,\"y\":1,\"width\":10,\"height\":10},\"clipped_bounds\":{\"x\":1,\"y\":1,\"width\":10,\"height\":10}}]}]}");
            var can=json("{\"schema\":1,\"source\":\"android:qg.canbus\",\"status\":\"ready\",\"boot_id\":\""+boot+"\",\"guest_boot_id\":\""+boot+"\",\"session_id\":\""+session+"\",\"atomic_guest_state\":false,\"source_instance\":\"source-one\",\"pid\":123,\"service_started_elapsed_ns\":\"10\",\"revision\":\"2\",\"clock\":{\"domain\":\"android.elapsedRealtimeNanos\",\"sampled_ns\":\"30\"},\"host_interval\":{\"domain\":\"host.steady\",\"clock_id\":\"host.steady:"+session+"\",\"start_ns\":\"100\",\"end_ns\":\"110\"},\"values\":{\"vehicle_state.IVI_SOC_MODESET\":{\"status\":\"observed\",\"raw\":"+(fev&&wanted?5:2)+",\"basis\":\"ICanBusService.getVehicleState\"},\"vehicle_state.HUM_VSP_FUNCTION_SW\":{\"status\":\"observed\",\"raw\":"+(!fev&&wanted?2:1)+",\"basis\":\"ICanBusService.getVehicleState\"}},\"acc\":{\"status\":\"observed\",\"raw\":2,\"basis\":\"ICanBusService.getAccStatus\"},\"transport\":{\"registration\":\"registered\",\"binder_alive\":true,\"client_manager_cache\":\"not_observed\"},\"coverage\":{\"begins\":\"service_construction\",\"delivery\":\"journal_replay\",\"vehicle_state_setter\":true,\"other_feedback_paths\":\"partial\",\"survives_process_death\":false},\"history\":{\"oldest_seq\":\"1\",\"latest_seq\":\"2\",\"dropped_count\":\"0\",\"next_cursor\":{\"source_instance\":\"source-one\",\"after_seq\":\"2\"},\"has_more\":false,\"gap\":null},\"events\":[]}");
            var x=new com.google.gson.JsonObject();x.addProperty("bootId",boot);x.addProperty("checked",wanted);x.add("ui",ui);x.add("can",can);return write("oem-"+(serial++)+".json",x);
        }
        Path archive(String boot) throws Exception {
            Path pack=work.resolve("pack-"+(serial++));Files.createDirectory(pack);String ts="2026-10-28T17:00:00Z";
            var seal=json("{\"schemaVersion\":2,\"bootId\":\""+boot+"\",\"writerId\":\""+writer+"\",\"sequence\":10,\"timestamp\":\""+ts+"\",\"source\":{\"kind\":\"daemon\"},\"level\":\"info\",\"component\":\"initial-seeding\",\"message\":\"initial-state-capture-sealed\",\"fields\":{\"transactionId\":\""+candidate+"\",\"candidate\":\""+candidate+"\",\"lastSequence\":\"9\"}}");String rows=seal+"\n";
            var context=json("{\"schemaVersion\":1,\"manifestSha256\":\""+candidate+"\",\"releaseIdentity\":\""+version+"\",\"logSource\":\"root-owned-segments\",\"currentLogSession\":{\"availability\":\"available\",\"bootId\":\""+boot+"\",\"writerId\":\""+writer+"\",\"sequence\":10}}");String body=context.toString();
            var manifest=json("{\"schemaVersion\":1,\"format\":\"VBOOSTLOG1\",\"files\":[\"records.jsonl\",\"context.json\"],\"header\":{\"schemaVersion\":1},\"hostEnvelope\":{\"deviceSerial\":\"emulator-5554\",\"installerVersion\":\""+version+"\",\"completeness\":\"verified-stream\",\"collectedAtUtc\":\""+ts+"\"}}"); manifest.getAsJsonObject("header").addProperty("recordsLength",rows.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);manifest.getAsJsonObject("header").addProperty("contextLength",body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            Files.writeString(pack.resolve("records.jsonl"),rows);Files.writeString(pack.resolve("context.json"),body);Files.writeString(pack.resolve("manifest.json"),manifest.toString());Path out=work.resolve("logs-"+(serial++)+".tar.xz");var proc=new ProcessBuilder("/usr/bin/tar","-cJf",out.toString(),"manifest.json","records.jsonl","context.json").directory(pack.toFile()).start();if(proc.waitFor()!=0)throw new IOException("test tar failed");return out;
        }
        FixtureProof make() throws Exception {
            Files.writeString(work.resolve("dummy"),"evidence");Files.writeString(work.resolve("machine"),"m");
            var runJson=json("{\"schemaVersion\":1,\"runId\":\""+run+"\",\"machine\":\""+work.resolve("machine")+"\",\"work\":\""+work+"\",\"state\":\"ready\"}");write("run.json",runJson);
            var cat=json("{\"agents\":[{\"id\":\"agent\",\"status\":\"stable\",\"activation\":{\"type\":\"checkbox\"},\"disables\":[]}]}");Path catPath=write("catalog.json",cat);catalog="d".repeat(64);
            Path installer=work.resolve("installer"), packageCatalog=work.resolve("package-agents.json");Files.writeString(installer,"test cli");installer.toFile().setExecutable(true);Files.copy(catPath,packageCatalog);

            var proof=json("{\"caseNumber\":24,\"semanticFunctionalStatus\":\"PASS\",\"functionalAcceptance\":true,\"mutationStarted\":true,\"reason\":\"complete\",\"releaseIdentity\":\""+version+"\",\"catalogIdentity\":\""+catalog+"\",\"scope\":{\"session\":\""+session+"\",\"machine\":\""+work.resolve("machine")+"\",\"serial\":\"emulator-5554\",\"candidate\":\""+candidate+"\"},\"samples\":[]}");proof.addProperty("candidateCatalogPath",text(catPath));proof.addProperty("originalRuntimeEvidence",text(write("original.json",runtime(boots[0]))));
            for(int i=0;i<2;i++){String absent=boots[1+i*2],boot=boots[2+i*2],prior=boots[i*2],phase=i==0?"seed-first":"seed-second";boolean fev=i==1,sound=i==0,pw=!sound;
                var sample=json("{\"baseline\":{\"forcedEv\":"+fev+",\"pedestrianSound\":"+sound+"},\"absence\":{\"boot\":\""+absent+"\"},\"oem\":{\"boot\":\""+absent+"\",\"forcedEv\":"+fev+",\"pedestrianSound\":"+sound+"},\"capture\":{\"boot\":\""+boot+"\",\"writer\":\""+writer+"\",\"transaction\":\""+candidate+"\",\"logsStartedAt\":\"2026-10-28T16:59:59Z\",\"logsEndedAt\":\"2026-10-28T17:00:01Z\"}}");
                var absence=sample.getAsJsonObject("absence");absence.addProperty("uninstallOperation",text(op(i*2+1,"UNINSTALL",phase,prior)));absence.addProperty("receipt",text(receipt(absent)));absence.addProperty("rootObservationPath",text(write("absent-"+i+".json",base(absent,"absent"))));
                var oem=sample.getAsJsonObject("oem");oem.addProperty("forcedEvEvidence",text(oem(absent,true,fev)));oem.addProperty("pedestrianEvidence",text(oem(absent,false,sound)));sample.addProperty("installOperation",text(op(i*2+2,"INSTALL",phase,absent)));sample.addProperty("rootObservationPath",text(write("root-"+i+".json",root(boot,fev,pw))));
                var receipt=json("{\"schemaVersion\":1,\"bootId\":\""+boot+"\",\"writerId\":\""+writer+"\",\"transactionId\":\""+candidate+"\",\"candidate\":\""+candidate+"\",\"initialWriterFloor\":5,\"installState\":\"unseeded\",\"captureState\":\"complete\",\"operations\":[{\"key\":\"vehicle-drive-forced-ev/forced-ev-armed\",\"operation\":\"read\",\"outcome\":\"missing\",\"sequence\":6},{\"key\":\"vehicle-drive-forced-ev/forced-ev-armed\",\"operation\":\"write-readback\",\"outcome\":\"value\",\"value\":"+fev+",\"sequence\":7},{\"key\":\"vehicle-drive-pedestrian-warning/pedestrian-warning-armed\",\"operation\":\"read\",\"outcome\":\"missing\",\"sequence\":8},{\"key\":\"vehicle-drive-pedestrian-warning/pedestrian-warning-armed\",\"operation\":\"write-readback\",\"outcome\":\"value\",\"value\":"+pw+",\"sequence\":9}]}");
                var official=base(boot,"present");var fixed=file(receipt);fixed.addProperty("current_writer_verified",false);official.getAsJsonObject("data").getAsJsonObject("files").add("initial-seeding",fixed);var cap=sample.getAsJsonObject("capture");cap.addProperty("officialObservationPath",text(write("capture-"+i+".json",official)));cap.addProperty("logsArchive",text(archive(boot)));Path logs=work.resolve("logsdir-"+i);Files.createDirectory(logs);cap.addProperty("logsDirectory",text(logs));proof.getAsJsonArray("samples").add(sample);
            }
            var restore=json("{\"startup\":\"interface\",\"runningStartup\":\"interface\",\"agents\":{\"agent\":{\"enabled\":true,\"options\":{}}},\"bootId\":\""+boots[6]+"\"}");restore.addProperty("restoreUninstallOperation",text(op(5,"UNINSTALL","seed-restore",boots[4])));restore.addProperty("restoreUninstallReceipt",text(receipt(boots[5])));restore.addProperty("restoreRootAbsenceEvidence",text(write("restore-absent.json",base(boots[5],"absent"))));restore.addProperty("restoreInstallOperation",text(op(6,"INSTALL","seed-restore",boots[5])));restore.addProperty("runtimeOfficialEvidence",text(write("restored-runtime.json",runtime(boots[6]))));proof.addProperty("restorationEvidence",text(write("restore.json",restore)));return new FixtureProof(proof,installer,packageCatalog);
        }
    }

}
