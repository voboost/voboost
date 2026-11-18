package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OwnedFullRestartTest {
    @TempDir Path directory;
    static final String PROFILE="profile",SESSION="a".repeat(32),OWNER="b".repeat(32),OPERATION="c".repeat(32);
    static final String BEFORE="11111111-1111-4111-8111-111111111111",AFTER="22222222-2222-4222-8222-222222222222";
    static JsonObject json(String s){return JsonParser.parseString(s).getAsJsonObject();}
    static JsonObject expected(){return json("{\"android\":\""+BEFORE+"\",\"qnx_generation\":\"4\",\"qnx_pid\":200}");}
    final class Fixture implements VoyemuClient.Transport {
        final Path machine,work;final List<String> operations=new ArrayList<>();final Map<String,JsonObject> requests=new HashMap<>();
        boolean supported=true,unknown,pending,badReceipt,badMachine;
        Fixture()throws IOException{machine=Files.createDirectory(directory.resolve("machine"));work=Files.createDirectory(directory.resolve("work"));Files.writeString(machine.resolve("runtime.json"),"{\"kind\":\"portable-guest-runtime\",\"profile\":\"profile\",\"qnx\":{}}");}
        JsonObject owner(){return json("{\"id\":\""+SESSION+"\",\"ownership_id\":\""+OWNER+"\"}");}
        JsonObject start(){JsonObject r=new JsonObject();r.add("session",owner());r.addProperty("profile",PROFILE);r.addProperty("machine_seed",machine.toString());r.addProperty("background",true);return r;}
        JsonObject stop(){return json("{\"session_id\":\""+SESSION+"\",\"accepted\":true,\"cleanup\":{\"verified\":true,\"supervisor_endpoint_closed\":true,\"supervisor_lock_released\":true}}");}
        JsonObject envelope(JsonObject request,JsonObject result){JsonObject r=new JsonObject();r.addProperty("schema", 1);r.add("request_id",request.get("request_id"));r.addProperty("operation_id",OPERATION);r.addProperty("ok",true);r.addProperty("elapsed_ms",0);r.add("result",result);return r;}
        JsonObject receipt(JsonObject request){
            JsonObject r=json("{\"schema\":1,\"ok\":true,\"session_id\":\""+SESSION+"\",\"operation_id\":\""+OPERATION+"\",\"status\":\"completed\",\"accepted\":true,\"mode\":\"cold\",\"scope\":\"android_qnx_cold_boot_changed\",\"old_processes_stopped\":true,\"guest_acknowledged\":true,\"authority_retained\":true,\"application_ready\":false,\"before\":{\"android_boot_id\":\""+BEFORE+"\",\"qnx\":{\"kind\":\"owned_cold_incarnation\",\"generation\":\"4\",\"qemu_pid\":200}},\"after\":{\"android_boot_id\":\""+AFTER+"\",\"qnx\":{\"kind\":\"owned_cold_incarnation\",\"generation\":\"5\",\"qemu_pid\":201}}}");
            r.add("request_id",request.get("request_id"));JsonObject identity=new JsonObject();identity.addProperty("seed",badMachine?"/foreign":machine.toString());r.add("machine_identity",identity);
            if(badReceipt)r.getAsJsonObject("after").getAsJsonObject("qnx").addProperty("generation","4");return r;
        }
        @Override public VoyemuClient.ProcessResult execute(String bytes,java.time.Duration timeout){
            JsonObject request=json(bytes);String op=request.get("op").getAsString();operations.add(op);JsonObject result=new JsonObject();
            if(List.of("session.start","session.restart","session.stop").contains(op))requests.put(request.get("request_id").getAsString(),request.deepCopy());
            switch(op){
                case "info": result.addProperty("api","voyemu.test");result.addProperty("schema", 1);JsonArray operations=new JsonArray();if(supported)operations.add("session.restart");result.add("operations",operations);break;
                case "profile.get":result.addProperty("id",PROFILE);break;
                case "session.start":assertTrue(request.get("with_cluster").getAsBoolean());result=start();break;
                case "session.status":result.addProperty("session_id",SESSION);result.addProperty("machine_seed",machine.toString());result.addProperty("stopped",false);break;
                case "session.restart":assertEquals(owner(),request.get("session"));assertEquals(expected(),request.get("expected_boot"));assertFalse(request.has("mode"));if(unknown)return new VoyemuClient.ProcessResult(null,true,"","lost reply");result=receipt(request);break;
                case "session.stop":result=stop();break;
                case "session.screenshot":assertEquals(List.of("cluster"),java.util.stream.StreamSupport.stream(request.getAsJsonArray("screens").spliterator(),false).map(x->x.getAsString()).toList());assertFalse(request.has("max_age_ms"));assertEquals(7,request.get("after_sequence").getAsLong());result.addProperty("accepted",true);break;
                case "operation.get":{
                    JsonObject original=requests.get(request.getAsJsonObject("target").get("request_id").getAsString());assertNotNull(original);String originalOp=original.get("op").getAsString();
                    result.addProperty("operation_id",OPERATION);result.add("request_id",original.get("request_id"));result.addProperty("op",originalOp);result.addProperty("scope",originalOp.equals("session.start")?"profile:"+PROFILE:"session:"+SESSION);result.addProperty("profile",PROFILE);result.addProperty("ownership_id",OWNER);result.addProperty("status",pending&&originalOp.equals("session.restart")?"pending":"terminal");result.addProperty("outcome",originalOp.equals("session.restart")?"unknown":"completed");
                    if(originalOp.equals("session.restart")){result.addProperty("runtime_outcome",pending?"unknown":"completed");JsonObject r=receipt(original);if(pending){r.addProperty("status","pending");r.addProperty("accepted",false);}result.add("runtime_receipt",r);if(!pending){JsonObject failure=envelope(original,new JsonObject());failure.remove("result");failure.addProperty("ok",false);failure.add("error",json("{\"code\":\"SESSION_RESTART_UNKNOWN\",\"message\":\"lost\",\"retryable\":false,\"outcome\":\"unknown\"}"));result.add("response",failure);}}
                    else result.add("response",envelope(original,originalOp.equals("session.start")?start():stop()));break;
                }
                default:throw new AssertionError(op);
            }
            return new VoyemuClient.ProcessResult(0,false,envelope(request,result).toString(),"");
        }
        OwnedSession startOwned()throws Exception{return OwnedSession.start(new VoyemuClient(this),PROFILE,machine,work,false,true);}
    }
    @Test void completedBothGuestReceiptKeepsAuthorityAndDoesNotAssertReadiness()throws Exception{
        Fixture f=new Fixture();try(OwnedSession owned=f.startOwned()){var lease=owned.session();JsonObject result=owned.restartBoth(expected());assertEquals(lease,owned.session());assertFalse(result.get("application_ready").getAsBoolean());assertEquals(result,owned.restartReceipt());assertEquals(1,f.operations.stream().filter("session.restart"::equals).count());assertFalse(f.operations.contains("multimedia.reboot"));}
    }
    @Test void lostReplyUsesDurableRuntimeReceiptWithoutResending()throws Exception{
        Fixture f=new Fixture();f.unknown=true;try(OwnedSession owned=f.startOwned()){assertEquals("completed",owned.restartBoth(expected()).get("status").getAsString());}assertEquals(1,f.operations.stream().filter("session.restart"::equals).count());assertEquals(1,f.operations.stream().filter("operation.get"::equals).count());
    }
    @Test void pendingActionCanResumeOnlyByLookupUnderSameAuthority()throws Exception{
        Fixture f=new Fixture();f.unknown=true;f.pending=true;OwnedSession first=f.startOwned();assertThrows(IOException.class,()->first.restartBoth(expected()));assertThrows(IOException.class,first::close);f.pending=false;try(OwnedSession resumed=OwnedSession.resume(new VoyemuClient(f),PROFILE,f.machine,f.work)){assertEquals("completed",resumed.restartReceipt().get("status").getAsString());}assertEquals(1,f.operations.stream().filter("session.restart"::equals).count());assertEquals(1,f.operations.stream().filter("session.stop"::equals).count());
    }
    @Test void partialOrForeignMachineReceiptRetainsOwnerForReconciliation()throws Exception{
        Fixture f=new Fixture();f.badReceipt=true;OwnedSession owned=f.startOwned();assertThrows(IOException.class,()->owned.restartBoth(expected()));assertThrows(IOException.class,owned::close);assertFalse(f.operations.contains("session.stop"));assertEquals(1,f.operations.stream().filter("session.restart"::equals).count());
    }
    @Test void foreignMachineReceiptCannotAuthorizeCleanup()throws Exception{
        Fixture f=new Fixture();f.badMachine=true;OwnedSession owned=f.startOwned();assertThrows(IOException.class,()->owned.restartBoth(expected()));assertThrows(IOException.class,owned::close);assertFalse(f.operations.contains("session.stop"));
    }
    @Test void absentCapabilityNeverDispatchesAFullRestart()throws Exception{
        Fixture f=new Fixture();f.supported=false;try(OwnedSession owned=f.startOwned()){assertThrows(IOException.class,()->owned.restartBoth(expected()));}assertFalse(f.operations.contains("session.restart"));
    }
    @Test void clusterCaptureIsReadOnlyAndSequenceBounded()throws Exception{
        Fixture f=new Fixture();try(OwnedSession owned=f.startOwned()){assertTrue(owned.captureCluster(directory.resolve("capture"),7).get("accepted").getAsBoolean());}assertEquals(1,f.operations.stream().filter("session.screenshot"::equals).count());
    }
    @Test void fullPrimaryCaptureIsReadOnlyWithoutSelectorOrCrop()throws Exception {
        VoyemuClient client=new VoyemuClient((bytes,timeout)->{
            JsonObject request=json(bytes);assertEquals("session.screenshot",request.get("op").getAsString());assertEquals("primary",request.getAsJsonArray("screens").get(0).getAsString());assertEquals(500,request.get("max_age_ms").getAsInt());assertEquals(10000,request.get("timeout_ms").getAsInt());assertFalse(request.has("selector"));assertFalse(request.has("rect"));assertFalse(request.getAsJsonObject("session").has("ownership_id"));
            JsonObject response=new JsonObject();response.addProperty("schema", 1);response.add("request_id",request.get("request_id"));response.addProperty("operation_id",OPERATION);response.addProperty("ok",true);response.addProperty("elapsed_ms",0);response.add("result",new JsonObject());return new VoyemuClient.ProcessResult(0,false,response.toString(),"");
        });
        var session=VoyemuClient.Session.attached(SESSION);assertEquals(VoyemuClient.State.RESPONSE,client.execute(client.capturePrimary("primary","profile",session,directory.resolve("capture")),java.time.Duration.ofSeconds(20)).state());
        assertThrows(IllegalArgumentException.class,()->client.capturePrimary("primary","profile",session,Path.of("relative")));assertThrows(IllegalArgumentException.class,()->client.capturePrimary("primary","profile",session,directory.resolve("x/../capture")));
    }
    @Test void invalidBootOrReadOnlyAuthorityCannotBuildMutation()throws Exception{
        VoyemuClient client=new VoyemuClient((request,timeout)->{throw new AssertionError("must not dispatch");});var owner=new VoyemuClient.Session(SESSION,OWNER);
        assertThrows(IllegalStateException.class,()->client.restartBoth("request",PROFILE,VoyemuClient.Session.attached(SESSION),expected(),180000));
        for(String value:List.of("0","04","-1","18446744073709551616")){JsonObject bad=expected();bad.addProperty("qnx_generation",value);assertThrows(IllegalArgumentException.class,()->client.restartBoth("request",PROFILE,owner,bad,180000));}
        assertThrows(IllegalStateException.class,()->client.execute(client.restartBoth("request",PROFILE,owner,expected(),180000),java.time.Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,()->client.captureCluster("capture",PROFILE,owner,directory.resolve("capture"),-1));
    }
}
