package ru.voboost.tests.installer.cli;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import ru.voboost.tests.RunFiles;

/** Re-evaluation of native-mode boot evidence, without claiming legacy prepared-inventory equivalence. */
public final class GuestBootLedger {
    private static void require(boolean value,String why)throws IOException{if(!value)throw new IOException("guest boot ledger: "+why);}
    private static String text(JsonObject v,String k)throws IOException{JsonElement e=v.get(k);require(e!=null&&e.isJsonPrimitive()&&e.getAsJsonPrimitive().isString(),k);return e.getAsString();}
    private static JsonObject object(JsonObject v,String k)throws IOException{require(v.has(k)&&v.get(k).isJsonObject(),k);return v.getAsJsonObject(k);}
    private static boolean flag(JsonObject v,String k,boolean value){JsonElement e=v.get(k);return e!=null&&e.isJsonPrimitive()&&e.getAsJsonPrimitive().isBoolean()&&e.getAsBoolean()==value;}
    private static int number(JsonObject v,String k)throws IOException{JsonElement e=v.get(k);require(e!=null&&e.isJsonPrimitive()&&e.getAsJsonPrimitive().isNumber()&&e.getAsString().matches("[1-9][0-9]{0,9}"),k);try{return Integer.parseInt(e.getAsString());}catch(NumberFormatException bad){throw new IOException("ledger integer overflow",bad);}}
    private static Path path(JsonObject v,String k)throws IOException{Path p=Path.of(text(v,k));require(p.isAbsolute()&&Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&p.toRealPath().equals(p),"ordinary retained "+k);return p;}
    public static void boot(Path file,String expected)throws IOException{
        require(expected.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")&&Files.size(file)<=32768,"bounded boot UUID/observation");String body=Files.readString(file);require(body.startsWith("bootCompleted=1\nbootId="+expected+"\nenforcing=Enforcing\noperation:\n"),"same ready Enforcing boot");
    }
    private static JsonObject nativeResult(JsonObject record,String field,Path work)throws IOException {
        Path source=path(record,field);require(source.getParent().toRealPath().equals(work.toRealPath()),"native wire observation outside its capture work");JsonObject wire=RunFiles.read(source,8*1024*1024+32768),response=object(wire,"response");require("RESPONSE".equals(text(wire,"state"))&&flag(response,"ok",true),"actual successful native wire observation required");return object(response,"result");
    }
    public static JsonObject readCapture(Path file)throws IOException {
        JsonObject v=RunFiles.read(file);String boot=text(v,"bootId"),session=text(v,"sessionId"),profile=text(v,"profile");int pid=number(v,"androidPid");
        require(number(v,"schemaVersion")==2&&"native_owned_ready_capture_v1".equals(text(v,"backendContract"))&&session.matches("[0-9a-f]{32}")&&!profile.isBlank(),"native backend scope");
        boot(path(v,"bootBefore"),boot);boot(path(v,"bootAfter"),boot);
        JsonObject ready=nativeResult(v,"readiness",file.getParent()),status=nativeResult(v,"statusAfter",file.getParent()),current=object(ready,"last_observation");require(flag(ready,"ready",true)&&"android.ready".equals(text(ready,"predicate")),"actual Android readiness");
        for(JsonObject observation:new JsonObject[]{status,current}){
            require(flag(observation,"ok",true)&&flag(observation,"stopped",false)&&session.equals(text(observation,"session_id"))&&pid==number(observation,"android_pid"),"owned boot status");JsonObject android=object(object(observation,"deliveries"),"android");require(pid==number(android,"pid")&&text(android,"serial").matches("emulator-[0-9]{4,5}"),"native Android delivery");
        }
        require(text(status,"machine_seed").equals(text(current,"machine_seed")),"same selected machine");
        JsonObject beforeUi=ru.voboost.voyemu.SessionUi.multimedia(nativeResult(v,"beforeUi",file.getParent()),profile,session,java.util.List.of("primary"));
        JsonObject afterUi=ru.voboost.voyemu.SessionUi.multimedia(nativeResult(v,"afterUi",file.getParent()),profile,session,java.util.List.of("primary"));
        GuestBootCapture.read(nativeResult(v,"capture",file.getParent()),Path.of(text(v,"captureDirectory")),beforeUi,afterUi,profile,session,boot,pid);return v;
    }
    public static void readProof(JsonObject proof)throws IOException {
        require(number(proof,"caseNumber")==26&&number(proof,"schemaVersion")==1&&"native_owned_ready_capture_v1".equals(text(proof,"backendContract"))&&flag(proof,"functionalAcceptance",true)&&"PASS".equals(text(proof,"semanticFunctionalStatus")),"terminal complete native proof");
        String session=text(proof,"sessionId"),profile=text(proof,"profile");require(proof.has("observedBoots")&&proof.get("observedBoots").isJsonArray()&&proof.has("captures")&&proof.get("captures").isJsonArray(),"boot inventory");
        require(proof.getAsJsonArray("observedBoots").size()>0&&proof.getAsJsonArray("observedBoots").size()<=256&&proof.getAsJsonArray("captures").size()<=256,"bounded boot count");Set<String> observed=new HashSet<>(),captured=new HashSet<>();
        for(JsonElement entry:proof.getAsJsonArray("observedBoots")){JsonObject row=entry.getAsJsonObject();String id=text(row,"bootId");require(observed.add(id),"duplicate observed boot");boot(path(row,"observation"),id);}
        for(JsonElement entry:proof.getAsJsonArray("captures")){Path file=Path.of(entry.getAsString());JsonObject row=readCapture(file);require(session.equals(text(row,"sessionId"))&&profile.equals(text(row,"profile"))&&captured.add(text(row,"bootId")),"foreign or duplicate boot capture");}
        require(observed.equals(captured),"a complete observed boot has no original current capture");
    }
    private GuestBootLedger(){}
}
