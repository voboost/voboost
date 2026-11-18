package ru.voboost.tests.installer.cli;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import ru.voboost.tests.RunFiles;
import ru.voboost.voyemu.PixelImage;

/** Native-mode boot screenshot, independently fenced by two current owned hierarchy observations. */
public final class GuestBootCapture {
    private static void require(boolean value,String reason)throws IOException{if(!value)throw new IOException("guest boot capture: "+reason);}
    private static String text(JsonObject v,String k)throws IOException{JsonElement e=v.get(k);require(e!=null&&e.isJsonPrimitive()&&e.getAsJsonPrimitive().isString(),k);return e.getAsString();}
    private static int number(JsonObject v,String k)throws IOException{JsonElement e=v.get(k);require(e!=null&&e.isJsonPrimitive()&&e.getAsJsonPrimitive().isNumber()&&e.getAsString().matches("0|[1-9][0-9]{0,9}"),k);try{return Integer.parseInt(e.getAsString());}catch(NumberFormatException bad){throw new IOException("capture integer overflow",bad);}}
    private static boolean yes(JsonObject v,String k,boolean wanted){JsonElement e=v.get(k);return e!=null&&e.isJsonPrimitive()&&e.getAsJsonPrimitive().isBoolean()&&e.getAsBoolean()==wanted;}
    private static JsonObject object(JsonObject v,String k)throws IOException{require(v.has(k)&&v.get(k).isJsonObject(),k);return v.getAsJsonObject(k);}
    private static BigInteger instant(JsonObject interval,String key,String session)throws IOException{
        require("host.steady".equals(text(interval,"clock_domain"))&&session.equals(text(interval,"clock_id")),"host capture clock");String n=text(interval,key);require(n.matches("0|[1-9][0-9]{0,19}")&&new BigInteger(n).bitLength()<=64,"host interval");return new BigInteger(n);
    }
    public static Path read(JsonObject capture,Path directory,JsonObject before,JsonObject after,String profile,String session,String boot,int pid)throws IOException{
        try{
            require(directory.isAbsolute()&&directory.toRealPath().equals(directory),"canonical capture directory");
            for(JsonObject ui:List.of(before,after))require("voyemu.ui.v1".equals(text(ui,"format"))&&profile.equals(text(ui,"profile"))&&session.equals(text(ui,"session_id"))&&boot.equals(text(ui,"boot_id"))&&number(ui,"android_pid")==pid,"same current native UI scope");
            require(!text(before,"observation_id").equals(text(after,"observation_id")),"distinct boot hierarchies");
            require(yes(capture,"accepted",true)&&number(capture,"schema")==1&&session.equals(text(capture,"session_id"))&&boot.equals(text(capture,"android_boot_id"))&&number(capture,"android_pid")==pid&&yes(capture,"android_boot_identity_verified",true)&&yes(capture,"android_frames_verified",true)&&yes(capture,"exported_frame_verified",false),"original capture boot authority");
            Path manifest=directory.resolve("manifest.json");JsonObject retained=capture.deepCopy();retained.remove("artifact_dir");retained.remove("manifest");retained.remove("elapsed_ms");require(directory.toString().equals(text(capture,"artifact_dir"))&&manifest.toString().equals(text(capture,"manifest"))&&RunFiles.read(manifest).equals(retained),"manifest is the actual capture result");
            BigInteger start=instant(object(capture,"host_interval"),"start_ns",session),end=instant(object(capture,"host_interval"),"end_ns",session);
            require(start.compareTo(instant(object(before,"host_interval"),"end_ns",session))>=0&&end.compareTo(start)>=0&&instant(object(after,"host_interval"),"start_ns",session).compareTo(end)>=0,"hierarchy/capture operation order");
            require(capture.has("frames")&&capture.get("frames").isJsonArray()&&capture.getAsJsonArray("frames").size()==1,"one primary source");JsonObject frame=capture.getAsJsonArray("frames").get(0).getAsJsonObject();
            require("primary".equals(text(frame,"screen"))&&"android_sdk".equals(text(frame,"source"))&&"primary.png".equals(text(frame,"file"))&&yes(frame,"derived",false)&&number(frame,"sdk_display_id")==0,"original SDK frame");
            for(String key:List.of("width","source_width"))require(number(frame,key)==1920,"original frame width");for(String key:List.of("height","source_height"))require(number(frame,key)==720,"original frame height");
            JsonObject rect=object(frame,"rect");require(number(rect,"x")==0&&number(rect,"y")==0&&number(rect,"width")==1920&&number(rect,"height")==720,"uncropped source");
            JsonObject age=object(frame,"freshness");require(yes(age,"within_limit",true)&&yes(age,"source_clock_mapped",true)&&"sdk_frame_generation_estimate".equals(text(age,"basis"))&&number(age,"max_age_ms")>=1&&number(age,"max_age_ms")<=500,"bounded fresh source");
            JsonElement bound=age.get("age_upper_bound_ms");require(bound!=null&&bound.isJsonPrimitive()&&bound.getAsJsonPrimitive().isNumber()&&Double.isFinite(bound.getAsDouble())&&bound.getAsDouble()>=0&&bound.getAsDouble()<=number(age,"max_age_ms"),"frame age bound");
            Path png=directory.resolve("primary.png");PixelImage.read(png,1920,720);return manifest;
        }catch(IllegalStateException|NullPointerException|IllegalArgumentException bad){throw new IOException("malformed guest boot capture",bad);}
    }
    private GuestBootCapture(){}
}
