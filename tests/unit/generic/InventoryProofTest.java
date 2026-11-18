package ru.voboost.tests;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InventoryProofTest {
    private static final String RUN = "12345678-1234-1234-1234-123456789abc";
    private static final String PRODUCT = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String SESSION = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final AcceptancePlan.Key KEY = new AcceptancePlan.Key("agent/media-popup-external-apps", "android:com.qinggan.app.islandapp");
    @TempDir Path root;
    private static String name(String value) {
        return java.util.Map.ofEntries(
            java.util.Map.entry("beforeStatusRequest","inventory-beforeStatus-request-1.json"), java.util.Map.entry("rawBeforeStatus","inventory-beforeStatus-raw-2.json"),
            java.util.Map.entry("beforeSystemRequest","inventory-beforeSystem-request-4.json"), java.util.Map.entry("rawBeforeSystem","inventory-beforeSystem-raw-5.json"),
            java.util.Map.entry("inventoryRequest","inventory-inventory-request-7.json"), java.util.Map.entry("inventoryOutput","cli-8.out"), java.util.Map.entry("inventoryError","cli-8.err"),
            java.util.Map.entry("listRequest","inventory-list-request-10.json"), java.util.Map.entry("listOutput","cli-11.out"), java.util.Map.entry("listError","cli-11.err"),
            java.util.Map.entry("afterSystemRequest","inventory-afterSystem-request-13.json"), java.util.Map.entry("rawAfterSystem","inventory-afterSystem-raw-14.json"),
            java.util.Map.entry("afterStatusRequest","inventory-afterStatus-request-16.json"), java.util.Map.entry("rawAfterStatus","inventory-afterStatus-raw-17.json")
        ).getOrDefault(value,value);
    }
    private Path file(String path, String content) throws Exception { return Files.writeString(root.resolve(name(path)), content); }
    private Path json(String name, JsonObject value) throws Exception { return file(name, value.toString()); }
    private JsonObject status() throws Exception { return RunFiles.parse("{\"ok\":true,\"stopped\":false,\"session_id\":\""+SESSION+"\",\"machine_seed\":\""+root+"\",\"android_pid\":42,\"deliveries\":{\"android\":{\"serial\":\"emulator-5554\",\"pid\":42}}}"); }
    private JsonObject system() throws Exception { return RunFiles.parse("{\"status\":\"ready\",\"boot_id\":\""+RUN+"\",\"value\":{\"boot_id\":\""+RUN+"\",\"owned_android_pid\":42}}"); }
    private Path receipt(Path candidate) throws Exception {
        JsonObject proof = new JsonObject(); proof.addProperty("schemaVersion",1); proof.addProperty("checkRunId",RUN); proof.addProperty("productRunId",PRODUCT);
        proof.addProperty("sessionId",SESSION); proof.addProperty("machine",root.toString()); proof.addProperty("serial","emulator-5554"); proof.addProperty("bootId",RUN); proof.addProperty("androidPid",42); proof.addProperty("candidate",candidate.toString());
        JsonArray evidence = new JsonArray();
        for (String field : new String[]{"beforeStatus","afterStatus","beforeSystem","afterSystem"}) {
            JsonObject observed=field.endsWith("Status")?status():system();
            Path path=json(field,observed); proof.addProperty(field,path.toString()); evidence.add(root.relativize(path).toString());
            JsonObject request=new JsonObject();request.addProperty("schema",2);request.addProperty("request_id",field);request.addProperty("op",field.endsWith("Status")?"session.status":"multimedia.system");JsonObject identity=new JsonObject();identity.addProperty("id",SESSION);request.add("session",identity);
            Path requestPath=json(field+"Request",request);proof.addProperty(field+"Request",requestPath.toString());evidence.add(root.relativize(requestPath).toString());
            JsonObject response=new JsonObject();response.addProperty("schema",2);response.addProperty("request_id",field);response.addProperty("ok",true);response.add("result",observed.deepCopy());JsonObject envelope=new JsonObject();envelope.addProperty("state","RESPONSE");envelope.add("response",response);envelope.addProperty("diagnostic","");
            String raw="raw"+Character.toUpperCase(field.charAt(0))+field.substring(1);Path rawPath=json(raw,envelope);proof.addProperty(raw,rawPath.toString());evidence.add(root.relativize(rawPath).toString());
        }
        for (String name : new String[]{"inventory","list"}) {
            boolean inventory=name.equals("inventory"); String body="bootId="+RUN+"\n"+(inventory?"package-inventory-v1\nandroid\tandroid\n":"package-list-v1\npackage:android\n")+"end\n";
            JsonObject request=new JsonObject();request.addProperty("action",inventory?"package-inventory":"package-list");
            JsonObject output=new JsonObject();output.addProperty("schemaVersion",1);output.addProperty("serial","emulator-5554");output.addProperty("body",body);
            Path[] files={json(name+"Request",request),json(name+"Output",output),file(name+"Error",""),file(name+"Body",body)};
            String[] suffix={"Request","Output","Error","Body"};
            for(int i=0;i<files.length;i++){proof.addProperty(name+suffix[i],files[i].toString());evidence.add(root.relativize(files[i]).toString());}
            proof.addProperty(name+"Exit",0);
            Path binary=root.resolve("package-fixture/volume/Voboost Installer.app/Contents/MacOS/voboost-installer");
            JsonObject execution=new JsonObject();execution.addProperty("schemaVersion",1);execution.addProperty("candidate",candidate.toString());execution.addProperty("binary",binary.toString());execution.addProperty("driver",root.resolve("emulator-driver").toString());execution.addProperty("request",files[0].toString());execution.addProperty("stdout",files[1].toString());execution.addProperty("stderr",files[2].toString());execution.addProperty("exit",0);
            JsonArray argv=new JsonArray();for(String arg:ru.voboost.tests.installer.cli.EmulatorCommand.of(root.resolve("emulator-driver"),binary,files[0],"emulator-5554"))argv.add(arg);execution.add("argv",argv);
            Path executionFile=json(name+"Execution",execution);proof.addProperty(name+"Execution",executionFile.toString());evidence.add(root.relativize(executionFile).toString());
        }
        JsonArray events=new JsonArray();
        for(String[] row:new String[][]{{"before-status","beforeStatusRequest","rawBeforeStatus"},{"before-system","beforeSystemRequest","rawBeforeSystem"},{"inventory","inventoryRequest","inventoryOutput","inventoryError"},{"list","listRequest","listOutput","listError"},{"after-system","afterSystemRequest","rawAfterSystem"},{"after-status","afterStatusRequest","rawAfterStatus"}}){
            JsonObject event=new JsonObject();event.addProperty("step",row[0]);event.add("request",proof.get(row[1]));event.add("output",proof.get(row[2]));if(row.length==4){event.add("error",proof.get(row[3]));event.add("execution",proof.get(row[0]+"Execution"));event.addProperty("exit",0);}events.add(event);
        }
        proof.add("orderedEvents",events);
        JsonObject run=new JsonObject();run.addProperty("schemaVersion",1);run.addProperty("state","ready");run.addProperty("runId",PRODUCT);run.addProperty("machine",root.toString());run.addProperty("work",root.toString());json("run.json",run);
        JsonObject link=new JsonObject();link.addProperty("schemaVersion",1);link.addProperty("checkRunId",RUN);link.addProperty("productRunId",PRODUCT);link.addProperty("candidate",candidate.toString());link.addProperty("machine",root.toString());link.addProperty("run",root.resolve("run.json").toString());Path linkPath=json("check-link.json",link);proof.addProperty("link",linkPath.toString());evidence.add("check-link.json");
        Path operation=Files.createDirectories(root.resolve("operations/000001")).resolve("operation.json");proof.addProperty("operation",operation.toString());
        Path receipt=root.resolve("proof.json"); evidence.add("proof.json");
        JsonObject input=new JsonObject();input.addProperty("phase","target-inventory");input.addProperty("checkRunId",RUN);input.addProperty("candidate",candidate.toString());
        JsonObject op=new JsonObject();op.addProperty("schemaVersion",1);op.addProperty("runId",PRODUCT);op.addProperty("sessionId",SESSION);op.addProperty("kind","CONFIGURE");op.addProperty("state","verified");op.add("input",input);op.add("evidence",evidence);
        Files.writeString(operation,op.toString()); Files.writeString(receipt,proof.toString());return receipt;
    }
    @Test void validatesLinkedOwnerAbsenceAndRejectsWrongScopeOrChangedSources() throws Exception {
        Path candidate=file("candidate.dmg","candidate"), receipt=receipt(candidate);
        var verified=InventoryProof.read(receipt,root,RUN,candidate,KEY);
        assertEquals("com.qinggan.app.islandapp", verified.owner());
        JsonObject plan=RunFiles.parse("{\"schemaVersion\":2,\"cases\":[],\"integrationCases\":[],\"guiCases\":[],\"agentCases\":[{\"id\":\"media-popup-external-apps\",\"required\":true,\"observable\":\"actual\"}]}");
        JsonObject catalog=RunFiles.parse("{\"schemaVersion\":2,\"agents\":[{\"id\":\"media-popup-external-apps\",\"status\":\"stable\",\"targets\":[{\"platform\":\"android\",\"process\":\"com.qinggan.app.islandapp\"}]}]}");
        AcceptanceReport report=new AcceptanceReport(AcceptancePlan.parse(plan,catalog));
        assertThrows(IOException.class,()->report.recordVerifiedAbsence(verified));
        AcceptanceReport foreignRun=new AcceptanceReport(AcceptancePlan.parse(plan,catalog));
        foreignRun.identify(PRODUCT,candidate,"fixture","fixture");
        assertThrows(IOException.class,()->foreignRun.recordVerifiedAbsence(verified));
        AcceptanceReport foreignCandidate=new AcceptanceReport(AcceptancePlan.parse(plan,catalog));
        foreignCandidate.identify(RUN,file("other-candidate.dmg","other"),"fixture","fixture");
        assertThrows(IOException.class,()->foreignCandidate.recordVerifiedAbsence(verified));
        report.identify(RUN,candidate,"fixture","fixture");
        report.recordVerifiedAbsence(verified);
        assertEquals("NOT_APPLICABLE", report.json().getAsJsonArray("cases").get(0).getAsJsonObject().get("status").getAsString());
        assertThrows(IOException.class,()->report.recordVerifiedAbsence(verified));
        var pass=new AcceptanceReport.Result(GateStatus.PASS,"fixture",java.util.List.of(candidate));
        for(var prerequisite:AcceptanceReport.Prerequisite.values()) report.prerequisite(prerequisite,pass);
        report.cleanup(pass);
        assertEquals(GateStatus.PASS,report.verdict());
        Files.writeString(root.resolve("listBody"),"changed");
        assertEquals(GateStatus.BLOCKED,report.verdict());
        assertEquals("BLOCKED",report.json().getAsJsonArray("cases").get(0).getAsJsonObject().get("status").getAsString());
        Files.writeString(root.resolve("listBody"),"bootId="+RUN+"\npackage-list-v1\npackage:android\nend\n");
        assertThrows(IOException.class,()->InventoryProof.read(receipt,root,PRODUCT,candidate,KEY));
        assertThrows(IOException.class,()->InventoryProof.read(receipt,root,RUN,candidate,new AcceptancePlan.Key("agent/font-cyrillic",KEY.target())));
        Files.writeString(root.resolve("listBody"),"truncated");
        assertThrows(IOException.class,()->InventoryProof.read(receipt,root,RUN,candidate,KEY));
    }
    @Test void rawEnvelopeAndLiveStatusMustMatch() throws Exception {
        Path candidate=file("candidate.dmg","candidate"), receipt=receipt(candidate);
        JsonObject raw=RunFiles.read(root.resolve(name("rawBeforeStatus")));
        raw.getAsJsonObject("response").getAsJsonObject("result").addProperty("android_pid",43);
        Files.writeString(root.resolve(name("rawBeforeStatus")),raw.toString());
        assertThrows(IOException.class,()->InventoryProof.read(receipt,root,RUN,candidate,KEY));
    }
    @Test void checkLinkAndProductJournalMustAgree() throws Exception {
        Path candidate=file("candidate.dmg","candidate"), receipt=receipt(candidate);
        JsonObject link=RunFiles.read(root.resolve("check-link.json"));link.addProperty("productRunId",RUN);
        Files.writeString(root.resolve("check-link.json"),link.toString());
        assertThrows(IOException.class,()->InventoryProof.read(receipt,root,RUN,candidate,KEY));
    }
    @Test void reorderedTimelineAndWrongRequestFailClosed() throws Exception {
        Path candidate=file("candidate.dmg","candidate"), receipt=receipt(candidate);
        JsonObject proof=RunFiles.read(receipt);var events=proof.getAsJsonArray("orderedEvents");var first=events.get(0);events.set(0,events.get(1));events.set(1,first);Files.writeString(receipt,proof.toString());
        assertThrows(IOException.class,()->InventoryProof.read(receipt,root,RUN,candidate,KEY));
        receipt(candidate);Path request=root.resolve(name("beforeStatusRequest"));JsonObject value=RunFiles.read(request);value.addProperty("op","multimedia.system");Files.writeString(request,value.toString());
        assertThrows(IOException.class,()->InventoryProof.read(receipt,root,RUN,candidate,KEY));
    }
    @Test void replayedPreCaptureObservationCannotStandInForPostCapture() throws Exception {
        Path candidate=file("candidate.dmg","candidate"), receipt=receipt(candidate);
        JsonObject proof=RunFiles.read(receipt);
        proof.add("afterStatus",proof.get("beforeStatus"));proof.add("afterStatusRequest",proof.get("beforeStatusRequest"));proof.add("rawAfterStatus",proof.get("rawBeforeStatus"));
        JsonObject last=proof.getAsJsonArray("orderedEvents").get(5).getAsJsonObject();last.add("request",proof.get("beforeStatusRequest"));last.add("output",proof.get("rawBeforeStatus"));
        Files.writeString(receipt,proof.toString());assertThrows(IOException.class,()->InventoryProof.read(receipt,root,RUN,candidate,KEY));
    }
    @Test void reusedNativeRequestIdFailsEvenInDistinctFiles() throws Exception {
        Path candidate=file("candidate.dmg","candidate"), receipt=receipt(candidate);
        Path request=root.resolve(name("afterStatusRequest")), raw=root.resolve(name("rawAfterStatus"));JsonObject r=RunFiles.read(request);r.addProperty("request_id","beforeStatus");Files.writeString(request,r.toString());
        JsonObject response=RunFiles.read(raw);response.getAsJsonObject("response").addProperty("request_id","beforeStatus");Files.writeString(raw,response.toString());
        assertThrows(IOException.class,()->InventoryProof.read(receipt,root,RUN,candidate,KEY));
    }
    @Test void incompleteOrSchemaLessJournalCannotAuthorize() throws Exception {
        Path candidate=file("candidate.dmg","candidate"), receipt=receipt(candidate);JsonObject run=RunFiles.read(root.resolve("run.json"));run.remove("schemaVersion");Files.writeString(root.resolve("run.json"),run.toString());
        assertThrows(IOException.class,()->InventoryProof.read(receipt,root,RUN,candidate,KEY));
        receipt(candidate);Path original=root.resolve("operations/000001/operation.json"), second=Files.createDirectories(root.resolve("operations/000002")).resolve("operation.json");Files.move(original,second);JsonObject proof=RunFiles.read(receipt);proof.addProperty("operation",second.toString());Files.writeString(receipt,proof.toString());
        assertThrows(IOException.class,()->InventoryProof.read(receipt,root,RUN,candidate,KEY));
    }
    @Test void presentOwnerRemainsRequiredForItsEffect() throws Exception {
        Path candidate=file("candidate.dmg","candidate"), receipt=receipt(candidate);
        for(String field:java.util.List.of("inventory","list")) {
            Path output=root.resolve(name(field+"Output")), body=root.resolve(field+"Body");JsonObject envelope=RunFiles.read(output);
            String extra=field.equals("inventory")?"com.qinggan.app.islandapp\tcom.qinggan.app.islandapp\n":"package:com.qinggan.app.islandapp\n";
            String contents=Files.readString(body).replace("end\n",extra+"end\n");Files.writeString(body,contents);envelope.addProperty("body",contents);Files.writeString(output,envelope.toString());
        }
        assertThrows(InventoryProof.OwnerRequired.class,()->InventoryProof.read(receipt,root,RUN,candidate,KEY));
    }
    @Test void commandExecutionMustMatchCandidateAndExactArgv() throws Exception {
        Path candidate=file("candidate.dmg","candidate"), receipt=receipt(candidate);
        Path execution=root.resolve("inventoryExecution");JsonObject value=RunFiles.read(execution);value.getAsJsonArray("argv").set(4,new com.google.gson.JsonPrimitive("emulator-5556"));Files.writeString(execution,value.toString());
        assertThrows(IOException.class,()->InventoryProof.read(receipt,root,RUN,candidate,KEY));
    }
    @Test void earlierUniquelyNamedPostCaptureRequestCannotSatisfyFence() throws Exception {
        Path candidate=file("candidate.dmg","candidate"), receipt=receipt(candidate);
        Path previous=root.resolve(name("afterStatusRequest"));Path early=root.resolve("inventory-afterStatus-request-3.json");Files.copy(previous,early);
        JsonObject proof=RunFiles.read(receipt);proof.addProperty("afterStatusRequest",early.toString());proof.getAsJsonArray("orderedEvents").get(5).getAsJsonObject().addProperty("request",early.toString());Files.writeString(receipt,proof.toString());
        Path journal=root.resolve("operations/000001/operation.json");JsonObject operation=RunFiles.read(journal);operation.getAsJsonArray("evidence").add(root.relativize(early).toString());Files.writeString(journal,operation.toString());
        assertThrows(IOException.class,()->InventoryProof.read(receipt,root,RUN,candidate,KEY));
    }
    @Test void normalProductRunCloseDoesNotInvalidateLastInventory() throws Exception {
        Path candidate=file("candidate.dmg","candidate"), receipt=receipt(candidate);
        var verified=InventoryProof.read(receipt,root,RUN,candidate,KEY);
        Path runFile=root.resolve("run.json");JsonObject run=RunFiles.read(runFile);run.addProperty("state","complete");Files.writeString(runFile,run.toString());
        assertDoesNotThrow(verified::revalidate);
    }
    @Test void laterProductMutationInvalidatesPreviouslyVerifiedAbsence() throws Exception {
        Path candidate=file("candidate.dmg","candidate"), receipt=receipt(candidate);
        var verified=InventoryProof.read(receipt,root,RUN,candidate,KEY);
        JsonObject later=new JsonObject();later.addProperty("schemaVersion",1);later.addProperty("runId",PRODUCT);later.addProperty("sessionId",SESSION);later.addProperty("kind","INSTALL");later.addProperty("state","verified");later.add("input",new JsonObject());later.add("evidence",new JsonArray());
        Files.writeString(Files.createDirectories(root.resolve("operations/000002")).resolve("operation.json"),later.toString());
        assertThrows(IOException.class,()->InventoryProof.read(receipt,root,RUN,candidate,KEY));
        assertThrows(IOException.class,verified::revalidate);
    }
    @Test void retainedRequestMustNotContainOwnershipCredential() throws Exception {
        Path candidate=file("candidate.dmg","candidate"), receipt=receipt(candidate);
        Path request=root.resolve(name("beforeStatusRequest"));JsonObject value=RunFiles.read(request);value.getAsJsonObject("session").addProperty("ownership_id","fixture-only");Files.writeString(request,value.toString());
        assertThrows(IOException.class,()->InventoryProof.read(receipt,root,RUN,candidate,KEY));
    }
    @Test void unverifiedOrMismatchedOperationCannotAuthorize() throws Exception {
        Path candidate=file("candidate.dmg","candidate"), receipt=receipt(candidate);
        Path operation=root.resolve("operations/000001/operation.json");JsonObject op=RunFiles.read(operation);op.addProperty("state","dispatched");Files.writeString(operation,op.toString());
        assertThrows(IOException.class,()->InventoryProof.read(receipt,root,RUN,candidate,KEY));
    }
}
