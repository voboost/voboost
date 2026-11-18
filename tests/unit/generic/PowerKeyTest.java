package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import voyemu.journal.Journal;

/** SDK dispatch receipts never establish the actual guest display or CAN effect. */
class PowerKeyTest {
    @TempDir Path directory;
    static final String SESSION="a".repeat(32),OWNER="b".repeat(32),REQUEST="c".repeat(32),OPERATION="d".repeat(32);
    static JsonObject json(String text){return JsonParser.parseString(text).getAsJsonObject();}
    static JsonObject request(){return json("{\"schema\":1,\"request_id\":\""+REQUEST+"\",\"op\":\"input.key.press\",\"profile\":\"profile\",\"session\":{\"id\":\""+SESSION+"\",\"ownership_id\":\""+OWNER+"\"},\"key\":\"Power\"}");}
    static JsonObject receipt(){return json("{\"ok\":true,\"sdk_completed\":true,\"visible_effect_verified\":false,\"key\":\"Power\",\"phase\":\"press\"}");}

    @Test void existingNativePowerRequestCanBeReconstructedOnlyForLookup()throws Exception {
        VoyemuClient client=new VoyemuClient((bytes,timeout)->{throw new AssertionError("reconstruction must not dispatch");});
        Path work=Files.createDirectory(directory.resolve("journal"));
        try(Journal journal=Journal.open(work)) {
            journal.reserve("session:"+SESSION,"machine",REQUEST,request().toString().getBytes(StandardCharsets.UTF_8));
            var retained=client.reserved(journal,"session:"+SESSION,"machine");
            assertEquals("input.key.press",retained.op());assertEquals(request(),json(retained.json()));
            assertEquals(VoyemuClient.State.UNKNOWN,client.executeReserved(retained,Duration.ofSeconds(1),journal,"machine").state());
        }
    }

    @Test void builderOnlyExposesTheOwnerBoundPowerPress()throws Exception {
        VoyemuClient client=new VoyemuClient((bytes,timeout)->{throw new AssertionError("unjournaled mutation must not dispatch");});
        var attempt=client.powerKey(REQUEST,"profile",new VoyemuClient.Session(SESSION,OWNER));
        assertEquals(request(),json(attempt.json()));
        assertThrows(IllegalStateException.class,()->client.execute(attempt,Duration.ofSeconds(1)));
        assertThrows(IllegalStateException.class,()->client.powerKey(REQUEST,"profile",VoyemuClient.Session.attached(SESSION)));
    }

    @Test void retainedPowerRejectsForeignKeysScopeAndArbitraryInputFields()throws Exception {
        VoyemuClient client=new VoyemuClient((bytes,timeout)->{throw new AssertionError("invalid reservation must not dispatch");});
        int ordinal=0;
        for(String mutation:List.of("key","key-case","expected","screen","text","session")) {
            JsonObject changed=request();
            switch(mutation) {
                case "key":changed.addProperty("key","Sleep");break;
                case "key-case":changed.addProperty("key","power");break;
                case "session":changed.getAsJsonObject("session").addProperty("id","e".repeat(32));break;
                case "expected":changed.add("expected",new JsonObject());break;
                default:changed.addProperty(mutation,"foreign");
            }
            Path work=Files.createDirectory(directory.resolve("invalid-"+(++ordinal)));
            try(Journal journal=Journal.open(work)) {
                journal.reserve("session:"+SESSION,"machine",REQUEST,changed.toString().getBytes(StandardCharsets.UTF_8));
                assertThrows(IOException.class,()->client.reserved(journal,"session:"+SESSION,"machine"),mutation);
            }
        }
    }

    @Test void sdkReceiptRequiresAnExactPressAndCannotClaimItsVisibleEffect()throws Exception {
        OwnedSession.powerKeyReceipt(receipt());
        for(String field:List.of("ok","sdk_completed","visible_effect_verified","key","phase")) {
            JsonObject missing=receipt();missing.remove(field);assertThrows(IOException.class,()->OwnedSession.powerKeyReceipt(missing),field);
            JsonObject changed=receipt();changed.addProperty(field,"foreign");assertThrows(IOException.class,()->OwnedSession.powerKeyReceipt(changed),field);
        }
        JsonObject unproved=receipt();unproved.addProperty("sdk_completed",false);assertThrows(IOException.class,()->OwnedSession.powerKeyReceipt(unproved));
        JsonObject inventedEffect=receipt();inventedEffect.addProperty("visible_effect_verified",true);assertThrows(IOException.class,()->OwnedSession.powerKeyReceipt(inventedEffect));
    }

    final class Fixture implements VoyemuClient.Transport {
        final Path machine,work;final List<String> operations=new ArrayList<>();final Map<String,JsonObject> requests=new HashMap<>();
        boolean lost,pending,wrongReceipt;
        Fixture()throws IOException {
            machine=Files.createTempDirectory(directory,"machine-");work=Files.createTempDirectory(directory,"work-");
            Files.writeString(machine.resolve("runtime.json"),"{\"kind\":\"portable-guest-runtime\",\"profile\":\"profile\"}");
        }
        JsonObject owner(){return json("{\"id\":\""+SESSION+"\",\"ownership_id\":\""+OWNER+"\"}");}
        JsonObject start(){JsonObject value=new JsonObject();value.add("session",owner());value.addProperty("profile","profile");value.addProperty("machine_seed",machine.toString());value.addProperty("background",true);return value;}
        JsonObject stop(){return json("{\"session_id\":\""+SESSION+"\",\"accepted\":true,\"cleanup\":{\"verified\":true,\"supervisor_endpoint_closed\":true,\"supervisor_lock_released\":true}}");}
        JsonObject envelope(JsonObject request,JsonObject result){JsonObject value=new JsonObject();value.addProperty("schema", 1);value.add("request_id",request.get("request_id"));value.addProperty("operation_id",OPERATION);value.addProperty("ok",true);value.addProperty("elapsed_ms",0);value.add("result",result);return value;}
        JsonObject result(JsonObject request){String op=request.get("op").getAsString();if(op.equals("session.start"))return start();if(op.equals("session.stop"))return stop();JsonObject value=receipt();if(wrongReceipt)value.addProperty("key","GoHome");return value;}
        public VoyemuClient.ProcessResult execute(String bytes,Duration timeout) {
            JsonObject request=json(bytes);String op=request.get("op").getAsString();operations.add(op);JsonObject value=new JsonObject();
            switch(op) {
                case "info":value=json("{\"api\":\"voyemu.test\",\"schema\":1}");break;
                case "profile.get":value.addProperty("id","profile");break;
                case "session.status":assertEquals(owner(),request.get("session"));value.addProperty("session_id",SESSION);value.addProperty("machine_seed",machine.toString());value.addProperty("stopped",false);break;
                case "session.start":case "session.stop":case "input.key.press":
                    if(!op.equals("session.start"))assertEquals(owner(),request.get("session"));
                    requests.put(request.get("request_id").getAsString(),request.deepCopy());value=result(request);
                    if(op.equals("input.key.press")){assertEquals("Power",request.get("key").getAsString());if(lost)return new VoyemuClient.ProcessResult(null,true,"","controlled lost reply");}break;
                case "operation.get": {
                    JsonObject original=requests.get(request.getAsJsonObject("target").get("request_id").getAsString());assertNotNull(original);
                    String originalOp=original.get("op").getAsString();value.addProperty("operation_id",OPERATION);value.add("request_id",original.get("request_id"));value.addProperty("op",originalOp);
                    value.addProperty("profile","profile");value.addProperty("ownership_id",OWNER);value.addProperty("scope",originalOp.equals("session.start")?"profile:profile":"session:"+SESSION);
                    if(originalOp.equals("session.start"))value.add("owned_session_observed",owner());else assertEquals(owner(),request.get("session"));
                    boolean waiting=pending&&originalOp.equals("input.key.press");value.addProperty("status",waiting?"pending":"terminal");
                    if(!waiting){value.addProperty("outcome","completed");value.add("response",envelope(original,result(original)));}break;
                }
                default:throw new AssertionError(op);
            }
            return new VoyemuClient.ProcessResult(0,false,envelope(request,value).toString(),"");
        }
        OwnedSession startOwned()throws Exception{return OwnedSession.start(new VoyemuClient(this),"profile",machine,work,false);}
        long presses(){return operations.stream().filter("input.key.press"::equals).count();}
    }

    @Test void confirmedSdkPressUsesOneAdmissionAndDoesNotAssertGuestEffect()throws Exception {
        Fixture f=new Fixture();try(OwnedSession owned=f.startOwned()) {
            JsonObject proof=owned.powerKey();assertTrue(proof.get("sdk_completed").getAsBoolean());assertFalse(proof.get("visible_effect_verified").getAsBoolean());
            assertTrue(Files.isDirectory(f.work.resolve("powerkey-1")));
        }
        assertEquals(1,f.presses());assertEquals(1,f.operations.stream().filter("session.stop"::equals).count());
    }

    @Test void lostReplyRecoversTheSamePressByLookupOnly()throws Exception {
        Fixture f=new Fixture();f.lost=true;try(OwnedSession owned=f.startOwned()){assertTrue(owned.powerKey().get("sdk_completed").getAsBoolean());}
        assertEquals(1,f.presses());assertEquals(1,f.operations.stream().filter("operation.get"::equals).count());
    }

    @Test void unknownPressBlocksAnotherKeyAndResumesOnlyTheRetainedAuthority()throws Exception {
        Fixture f=new Fixture();f.lost=true;f.pending=true;OwnedSession first=f.startOwned();
        assertThrows(IOException.class,first::powerKey);assertThrows(IOException.class,first::powerKey);assertThrows(IOException.class,first::close);
        assertEquals(1,f.presses());assertFalse(f.operations.contains("session.stop"));
        f.pending=false;
        try(OwnedSession resumed=OwnedSession.resume(new VoyemuClient(f),"profile",f.machine,f.work)){assertEquals(SESSION,resumed.session().id());}
        assertEquals(1,f.presses());assertEquals(1,f.operations.stream().filter("session.stop"::equals).count());
    }

    @Test void malformedSdkReceiptCannotReleaseTheOwnedSession()throws Exception {
        Fixture f=new Fixture();f.wrongReceipt=true;OwnedSession owned=f.startOwned();assertThrows(IOException.class,owned::powerKey);assertThrows(IOException.class,owned::close);
        assertEquals(1,f.presses());assertFalse(f.operations.contains("session.stop"));
    }

    @Test void anotherRetainedOwnerCannotResumeThisSessionOrSendAKey()throws Exception {
        Fixture f=new Fixture();try(OwnedSession first=f.startOwned()) {
            JsonObject foreign=request();foreign.getAsJsonObject("session").addProperty("ownership_id","f".repeat(32));
            try(Journal journal=Journal.open(Files.createDirectory(f.work.resolve("powerkey-1")))) {
                journal.reserve("session:"+SESSION,f.machine.toString(),REQUEST,foreign.toString().getBytes(StandardCharsets.UTF_8));
            }
            assertThrows(IOException.class,()->OwnedSession.resume(new VoyemuClient(f),"profile",f.machine,f.work));
            assertEquals(0,f.presses());
        }
    }
}
