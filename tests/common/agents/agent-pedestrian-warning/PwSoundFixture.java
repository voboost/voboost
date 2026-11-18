package ru.voboost.voyemu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;

/** Closed validation of one explicit host-to-QNX stimulus; never Android native CAN or render evidence. */
final class PwSoundFixture {
    private PwSoundFixture() {}
    static long number(JsonObject value,String key,long max)throws IOException {
        JsonElement field=value==null?null:value.get(key);
        if(field==null||!field.isJsonPrimitive()||!field.getAsJsonPrimitive().isNumber()||!field.getAsString().matches("0|[1-9][0-9]{0,18}"))throw new IOException("invalid numeric sound fixture field: "+key);
        try{long n=Long.parseLong(field.getAsString());if(n>max)throw new IOException("sound fixture bound: "+key);return n;}catch(NumberFormatException e){throw new IOException("sound fixture overflow",e);}
    }
    static JsonObject object(JsonObject value,String key)throws IOException {
        if(value==null||!value.has(key)||!value.get(key).isJsonObject())throw new IOException("missing sound fixture object: "+key);return value.getAsJsonObject(key);
    }
    static String text(JsonObject value,String key)throws IOException {
        JsonElement field=value==null?null:value.get(key);if(field==null||!field.isJsonPrimitive()||!field.getAsJsonPrimitive().isString())throw new IOException("invalid sound fixture string: "+key);return field.getAsString();
    }
    static void bool(JsonObject value,String key,boolean expected)throws IOException {
        JsonElement field=value==null?null:value.get(key);if(field==null||!field.isJsonPrimitive()||!field.getAsJsonPrimitive().isBoolean()||field.getAsBoolean()!=expected)throw new IOException("sound fixture flag differs: "+key);
    }
    static void bootId(String value)throws IOException {if(value==null||!value.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"))throw new IOException("complete expected Linux boot UUID required");}
    static long generation(JsonObject status)throws IOException {long n=number(object(object(status,"deliveries"),"qnx"),"generation",0xffffffffL);if(n<1)throw new IOException("live QNX generation required");return n;}
    static void identity(JsonObject status,String profile,VoyemuClient.Session session,Path machine,JsonObject before)throws IOException {
        bool(status,"ok",true);bool(status,"stopped",false);
        if(!session.id().equals(text(status,"session_id"))||!machine.toString().equals(text(status,"machine_seed"))||number(status,"android_pid",Long.MAX_VALUE)<2||number(status,"qnx_pid",Long.MAX_VALUE)<2)throw new IOException("sound fixture owned machine identity differs");
        generation(status);
        if(before!=null&&(number(status,"android_pid",Long.MAX_VALUE)!=number(before,"android_pid",Long.MAX_VALUE)||number(status,"qnx_pid",Long.MAX_VALUE)!=number(before,"qnx_pid",Long.MAX_VALUE)||generation(status)!=generation(before)))throw new IOException("sound fixture guest incarnation changed");
    }
    static void system(JsonObject system,JsonObject status,String expected)throws IOException {
        bootId(expected);JsonObject value=object(system,"value");
        // Overall completeness includes unrelated services/activities/power; only these fenced identity fields authorize this stimulus.
        if((!"ready".equals(text(system,"status"))&&!"partial".equals(text(system,"status")))||!expected.equals(text(system,"boot_id"))||!expected.equals(text(value,"boot_id"))||number(value,"owned_android_pid",Long.MAX_VALUE)!=number(status,"android_pid",Long.MAX_VALUE))throw new IOException("sound fixture Linux boot fence differs");
    }
    static int value(JsonObject vehicle)throws IOException {return (int)number(object(object(vehicle,"state"),"raw_oem"),"sound_enable",1);}
    static void patch(JsonObject result,JsonObject request,VoyemuClient.Session session)throws IOException {
        bool(result,"ok",true);if(!session.id().equals(text(result,"session_id")))throw new IOException("sound fixture patch session differs");
        long expected=number(request,"expected_revision",0xfffffffeL)+1;
        if(number(result,"revision",0xffffffffL)!=expected||number(object(result,"state"),"sequence",0xffffffffL)!=expected||value(result)!=number(object(request,"values"),"sound_enable",1)||generation(result)!=number(request,"expected_generation",0xffffffffL))throw new IOException("sound fixture accepted patch differs");
    }
    static boolean delivered(JsonObject observed,JsonObject request)throws IOException {
        long wanted=number(request,"expected_revision",0xfffffffeL)+1;
        if(number(observed,"revision",0xffffffffL)!=wanted||number(object(observed,"state"),"sequence",0xffffffffL)!=wanted||value(observed)!=number(object(request,"values"),"sound_enable",1)||generation(observed)!=number(request,"expected_generation",0xffffffffL))throw new IOException("sound fixture source revision/state changed");
        JsonObject receipt=object(object(object(observed,"deliveries"),"qnx"),"vehicle");
        if(!"guest_acknowledged".equals(text(receipt,"status")))return false;
        return number(receipt,"source_sequence",0xffffffffL)==wanted&&number(receipt,"sequence",0xffffffffL)>0;
    }
    /** Keep only required scope fields in the durable context; full observations are separate evidence. */
    static JsonObject scope(JsonObject status)throws IOException {
        JsonObject r=new JsonObject();for(String key:new String[]{"ok","stopped","session_id","machine_seed","android_pid","qnx_pid"})r.add(key,status.get(key).deepCopy());
        JsonObject qnx=new JsonObject(),deliveries=new JsonObject();qnx.addProperty("generation",generation(status));deliveries.add("qnx",qnx);r.add("deliveries",deliveries);return r;
    }
    static JsonObject baseline(JsonObject vehicle)throws IOException {
        JsonObject r=new JsonObject(),state=new JsonObject(),raw=new JsonObject();r.addProperty("revision",number(vehicle,"revision",0xfffffffeL));state.addProperty("sequence",number(object(vehicle,"state"),"sequence",0xffffffffL));raw.addProperty("sound_enable",value(vehicle));state.add("raw_oem",raw);r.add("state",state);return r;
    }
    static JsonObject bootFence(JsonObject system)throws IOException {
        JsonObject r=new JsonObject(),value=new JsonObject();r.addProperty("status",text(system,"status"));r.addProperty("boot_id",text(system,"boot_id"));JsonObject observed=object(system,"value");value.addProperty("boot_id",text(observed,"boot_id"));value.addProperty("owned_android_pid",number(observed,"owned_android_pid",Long.MAX_VALUE));r.add("value",value);return r;
    }
    static void persist(Path path,JsonObject context)throws IOException {
        Files.createFile(path,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(path,context.toString()+"\n");try(FileChannel file=FileChannel.open(path,StandardOpenOption.WRITE)){file.force(true);}try(FileChannel parent=FileChannel.open(path.getParent(),StandardOpenOption.READ)){parent.force(true);}
    }
    static JsonObject context(Path path)throws IOException {
        if(Files.isSymbolicLink(path)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>32768)throw new IOException("sound fixture retained context missing/unsafe");
        try{return JsonParser.parseString(Files.readString(path)).getAsJsonObject();}catch(RuntimeException e){throw new IOException("sound fixture retained context malformed",e);}
    }
}
