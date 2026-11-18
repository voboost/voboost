package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OwnedPwSoundFixtureTest {
    @TempDir Path directory;
    static final String SESSION="a".repeat(32),OWNER="b".repeat(32),OPERATION="c".repeat(32),BOOT="11111111-1111-4111-8111-111111111111",OTHER="22222222-2222-4222-8222-222222222222";
    static JsonObject json(String s){return JsonParser.parseString(s).getAsJsonObject();}
    final class Fixture implements VoyemuClient.Transport {
        final Path machine,work;final List<String> operations=new ArrayList<>();final Map<String,JsonObject> requests=new HashMap<>(), results=new HashMap<>();
        long revision=9;int sound=0;boolean lost,pending,staleAck,changedBoot,changedQnx,changedRevision,partialSystem;JsonObject patch; int unavailableBefore, unavailableAfter;
        Fixture()throws IOException{machine=Files.createDirectory(directory.resolve("machine"));work=ru.voboost.tests.RunFiles.directory(directory.resolve("work"));Files.writeString(machine.resolve("runtime.json"),"{\"kind\":\"portable-guest-runtime\",\"profile\":\"profile\",\"qnx\":{}}");}
        JsonObject owner(){return json("{\"id\":\""+SESSION+"\",\"ownership_id\":\""+OWNER+"\"}");}
        JsonObject envelope(JsonObject request,JsonObject result){JsonObject r=new JsonObject();r.addProperty("schema", 1);r.add("request_id",request.get("request_id"));r.addProperty("operation_id",OPERATION);r.addProperty("ok",true);r.addProperty("elapsed_ms",0);r.add("result",result);return r;}
        JsonObject start(){JsonObject r=new JsonObject();r.add("session",owner());r.addProperty("profile","profile");r.addProperty("machine_seed",machine.toString());r.addProperty("background",true);return r;}
        JsonObject stop(){return json("{\"session_id\":\""+SESSION+"\",\"accepted\":true,\"cleanup\":{\"verified\":true,\"supervisor_endpoint_closed\":true,\"supervisor_lock_released\":true}}");}
        JsonObject vehicle(boolean queued){long generation=changedQnx&&patch!=null?5:4;return json("{\"revision\":"+revision+",\"state\":{\"sequence\":"+revision+",\"raw_oem\":{\"sound_enable\":"+sound+"}},\"deliveries\":{\"qnx\":{\"generation\":"+generation+",\"vehicle\":{\"status\":\""+(queued?"queued":"guest_acknowledged")+"\",\"sequence\":42,\"source_sequence\":"+(staleAck?revision-1:revision)+"}}}}");}
        JsonObject status(){JsonObject r=vehicle(false);r.addProperty("ok",true);r.addProperty("session_id",SESSION);r.addProperty("machine_seed",machine.toString());r.addProperty("stopped",false);r.addProperty("android_pid",100);r.addProperty("qnx_pid",200);return r;}
        @Override public VoyemuClient.ProcessResult execute(String bytes,Duration timeout){JsonObject request=json(bytes);String op=request.get("op").getAsString();operations.add(op);JsonObject result=new JsonObject();
            switch(op){
                case "info":result.addProperty("api","voyemu.test");result.addProperty("schema", 1);break;
                case "profile.get":result.addProperty("id","profile");break;
                case "session.start":requests.put(request.get("request_id").getAsString(),request);result=start();break;
                case "session.stop":result=stop();break;
                case "session.status":result=status();break;
                case "vehicle.get":result=vehicle(false);if(changedRevision&&patch!=null)result.addProperty("revision",revision+1);break;
                case "multimedia.system":
                    if (patch == null ? unavailableBefore-- > 0 : unavailableAfter-- > 0) {
                        result = json("{\"status\":\"unavailable\",\"boot_id\":\"\",\"value\":null}");
                        break;
                    }
                    String boot=changedBoot&&patch!=null?OTHER:BOOT;result=json("{\"status\":\"ready\",\"boot_id\":\""+boot+"\",\"value\":{\"boot_id\":\""+boot+"\",\"owned_android_pid\":100}}");if(partialSystem){result.addProperty("status","partial");result.getAsJsonObject("value").addProperty("complete",false);}break;
                case "vehicle.set":assertEquals(owner(),request.get("session"));assertEquals(java.util.Set.of("sound_enable"),request.getAsJsonObject("values").keySet());assertEquals(revision,request.get("expected_revision").getAsLong());assertEquals(4,request.get("expected_generation").getAsLong());requests.put(request.get("request_id").getAsString(),request);sound=request.getAsJsonObject("values").get("sound_enable").getAsInt();revision++;patch=vehicle(true);patch.addProperty("ok",true);patch.addProperty("session_id",SESSION);result=patch;results.put(request.get("request_id").getAsString(), result);if(lost)return new VoyemuClient.ProcessResult(null,true,"","lost");break;
                case "operation.get":JsonObject original=requests.get(request.getAsJsonObject("target").get("request_id").getAsString());assertNotNull(original);String originalOp=original.get("op").getAsString();result.addProperty("operation_id",OPERATION);result.add("request_id",original.get("request_id"));result.addProperty("op",originalOp);result.addProperty("profile","profile");result.addProperty("ownership_id",OWNER);result.addProperty("scope",originalOp.equals("session.start")?"profile:profile":"session:"+SESSION);result.addProperty("status",pending?"pending":"terminal");if(!pending){result.addProperty("outcome","completed");result.add("response",envelope(original,originalOp.equals("session.start")?start():results.get(original.get("request_id").getAsString())));}break;
                default:throw new AssertionError(op);
            }return new VoyemuClient.ProcessResult(0,false,envelope(request,result).toString(),"");
        }
        OwnedSession startOwned()throws Exception{return OwnedSession.start(new VoyemuClient(this),"profile",machine,work,false,true);}
    }
    @Test void unavailableBootObservationRetriesReadsWithoutRepeatingTheMutation() throws Exception {
        Fixture fixture = new Fixture();
        fixture.unavailableBefore = 1;
        fixture.unavailableAfter = 1;
        try (OwnedSession owned = fixture.startOwned()) {
            assertTrue(owned.setPwSoundFixture(true, BOOT).get("accepted").getAsBoolean());
        }
        int mutation = fixture.operations.indexOf("vehicle.set");
        assertEquals(2, fixture.operations.subList(0, mutation).stream().filter("multimedia.system"::equals).count());
        assertEquals(2, fixture.operations.subList(mutation + 1, fixture.operations.size())
                .stream().filter("multimedia.system"::equals).count());
        assertEquals(1, fixture.operations.stream().filter("vehicle.set"::equals).count());
    }

    @Test void partialUnrelatedInventoriesKeepTheExactBootFenceWithoutClaimingTheirCompleteness()throws Exception {
        Fixture f=new Fixture();f.partialSystem=true;
        try(OwnedSession owned=f.startOwned()) {
            JsonObject proof=owned.setPwSoundFixture(true,BOOT);
            assertTrue(proof.get("accepted").getAsBoolean());
            assertFalse(proof.get("visible_effect_verified").getAsBoolean());
            assertFalse(proof.get("android_native_vsp_verified").getAsBoolean());
            owned.setPwSoundFixture(false,BOOT);
        }
        assertEquals(2,f.operations.stream().filter("vehicle.set"::equals).count());
    }
    @Test void partialSystemNeverAcceptsForeignBootPidOrUnavailableMetadata()throws Exception {
        JsonObject status=json("{\"android_pid\":100}");
        JsonObject system=json("{\"status\":\"partial\",\"boot_id\":\""+BOOT+"\",\"value\":{\"boot_id\":\""+BOOT+"\",\"owned_android_pid\":100}}");
        for(int wrong=0;wrong<4;wrong++) {
            JsonObject changed=system.deepCopy();
            if(wrong==0)changed.addProperty("boot_id",OTHER);
            if(wrong==1)changed.getAsJsonObject("value").addProperty("boot_id",OTHER);
            if(wrong==2)changed.getAsJsonObject("value").addProperty("owned_android_pid",101);
            if(wrong==3)changed.addProperty("status","unavailable");
            assertThrows(IOException.class,()->PwSoundFixture.system(changed,status,BOOT));
        }
    }
    @Test void explicitKnownRestoreUsesOnlySoundFieldAndFreshAck()throws Exception {
        Fixture f=new Fixture();try(OwnedSession owned=f.startOwned()){JsonObject p=owned.setPwSoundFixture(true,BOOT);assertTrue(p.get("accepted").getAsBoolean());assertEquals(0,p.get("original_value").getAsInt());assertEquals(1,p.get("value").getAsInt());assertFalse(p.get("visible_effect_verified").getAsBoolean());assertFalse(p.get("android_native_vsp_verified").getAsBoolean());assertEquals(p,owned.pwSoundFixtureReceipt());owned.setPwSoundFixture(false,BOOT);assertEquals(0,f.sound);}assertEquals(2,f.operations.stream().filter("vehicle.set"::equals).count());
    }
    @Test void lostMutationReplyOnlyLooksUpBeforeIndependentDelivery()throws Exception {
        Fixture f=new Fixture();f.lost=true;try(OwnedSession owned=f.startOwned()){assertTrue(owned.setPwSoundFixture(true,BOOT).get("accepted").getAsBoolean());}assertEquals(1,f.operations.stream().filter("vehicle.set"::equals).count());assertEquals(1,f.operations.stream().filter("operation.get"::equals).count());
    }
    @Test void unresolvedMutationNeverRestoresAndResumeOnlyLooksUp()throws Exception {
        Fixture f=new Fixture();f.lost=true;f.pending=true;OwnedSession first=f.startOwned();assertThrows(IOException.class,()->first.setPwSoundFixture(true,BOOT));assertThrows(IOException.class,()->first.setPwSoundFixture(false,BOOT));assertThrows(IOException.class,first::close);f.pending=false;try(OwnedSession restored=OwnedSession.resume(new VoyemuClient(f),"profile",f.machine,f.work)){assertEquals(1,restored.pwSoundFixtureReceipt().get("value").getAsInt());}assertEquals(1,f.operations.stream().filter("vehicle.set"::equals).count());
    }
    @Test void resumeUsesCompletedEvidenceAfterLaterSoundChange() throws Exception {
        Fixture fixture = new Fixture();
        OwnedSession first = fixture.startOwned();
        first.setPwSoundFixture(true, BOOT);
        first.setPwSoundFixture(false, BOOT);
        try (OwnedSession resumed = OwnedSession.resume(new VoyemuClient(fixture), "profile", fixture.machine, fixture.work)) {
            assertEquals(0, resumed.pwSoundFixtureReceipt().get("value").getAsInt());
            assertEquals(2, fixture.operations.stream().filter("vehicle.set"::equals).count());
        }
    }

    @Test void staleLinuxBootRejectsBeforeDispatch()throws Exception {
        Fixture f=new Fixture();try(OwnedSession owned=f.startOwned()){assertThrows(IOException.class,()->owned.setPwSoundFixture(true,OTHER));}assertFalse(f.operations.contains("vehicle.set"));
    }
    @Test void changedGuestCannotAuthorizeRestoreOrCleanup()throws Exception {
        Fixture f=new Fixture();f.changedBoot=true;OwnedSession owned=f.startOwned();assertThrows(IOException.class,()->owned.setPwSoundFixture(true,BOOT));assertThrows(IOException.class,()->owned.setPwSoundFixture(false,BOOT));assertThrows(IOException.class,owned::close);assertEquals(1,f.operations.stream().filter("vehicle.set"::equals).count());assertFalse(f.operations.contains("session.stop"));
    }
    @Test void savedQueuedDeliveryDoesNotReuseThePreviousPresentationAcknowledgement() throws Exception {
        var observed=ru.voboost.tests.RunFiles.read(Path.of("tests/unit/agents/agent-pedestrian-warning/agent-pedestrian-warning-mcu-delivery-queued.json"));
        var request=json("{\"expected_revision\":2,\"expected_generation\":2,\"values\":{\"sound_enable\":1}}");
        assertFalse(PwSoundFixture.delivered(observed,request));
        var receipt=observed.getAsJsonObject("deliveries").getAsJsonObject("qnx").getAsJsonObject("vehicle");
        receipt.addProperty("status","guest_acknowledged");receipt.addProperty("source_sequence",2);
        assertFalse(PwSoundFixture.delivered(observed,request));
        receipt.addProperty("source_sequence",3);
        assertTrue(PwSoundFixture.delivered(observed,request));
    }

    @Test void freshAckForAnotherRevisionCannotComplete()throws Exception {
        Fixture f=new Fixture();f.staleAck=true;OwnedSession owned=f.startOwned();assertThrows(IOException.class,()->owned.setPwSoundFixture(true,BOOT));assertThrows(IOException.class,owned::close);assertFalse(f.operations.contains("session.stop"));
    }
    @Test void changedQnxOrHostRevisionCannotComplete()throws Exception {
        Fixture f=new Fixture();f.changedRevision=true;OwnedSession owned=f.startOwned();assertThrows(IOException.class,()->owned.setPwSoundFixture(true,BOOT));assertThrows(IOException.class,owned::close);
    }
    @Test void arbitraryVehicleFieldAndUnjournaledMutationAreRejected()throws Exception {
        VoyemuClient client=new VoyemuClient((request,t)->{throw new AssertionError("must not dispatch");});var session=new VoyemuClient.Session(SESSION,OWNER);var a=client.setPwSoundFixture("d".repeat(32),"profile",session,true,9,4);assertThrows(IllegalStateException.class,()->client.execute(a,Duration.ofSeconds(1)));JsonObject raw=json(a.json());raw.getAsJsonObject("values").addProperty("speed",50);Path journal=Files.createDirectory(directory.resolve("journal"));try(var j=voyemu.journal.Journal.open(journal)){j.reserve("session:"+SESSION,"identity",a.requestId(),raw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));assertThrows(IOException.class,()->client.reserved(j,"session:"+SESSION,"identity"));}assertThrows(IllegalStateException.class,()->client.setPwSoundFixture("id","profile",VoyemuClient.Session.attached(SESSION),true,9,4));
    }
}
