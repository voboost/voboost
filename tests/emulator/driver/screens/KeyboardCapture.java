package ru.voboost.voyemu;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import ru.voboost.tests.RunFiles;

/** Source/ROI-fenced SDK pixels for keyboard routing only; never visual or input acceptance. */
public final class KeyboardCapture {
    private KeyboardCapture() {}
    public static final class Frame {
        private final JsonObject scope;
        private final PixelImage image;
        private final Path png,manifest;
        private final long started;
        private Frame(JsonObject scope,PixelImage image,Path png,Path manifest,long started){this.scope=scope.deepCopy();this.image=image;this.png=png;this.manifest=manifest;this.started=started;}
        public JsonObject scope(){return scope.deepCopy();}
        public PixelImage image(){return image;}
        public Path png(){return png;}
        public Path manifest(){return manifest;}
        public long started(){return started;}
        public void requireSameEpoch(Frame before)throws IOException {
            for(String field:List.of("session","boot","androidPid","source","window","target","rect"))
                if(!scope.get(field).equals(before.scope.get(field)))throw invalid("capture epoch/window/geometry changed");
            if(started<before.started)throw invalid("host capture chronology regressed");
        }
    }
    private static IOException invalid(String why){return new IOException("keyboard capture unavailable: "+why);}
    private static String text(JsonObject value,String key)throws IOException {
        if(!value.has(key)||!value.get(key).isJsonPrimitive()||!value.getAsJsonPrimitive(key).isString())throw invalid("text "+key);
        String result=value.get(key).getAsString();if(result.isEmpty()||result.length()>4096||result.indexOf('\0')>=0)throw invalid("bounded text "+key);return result;
    }
    private static JsonObject object(JsonObject value,String key)throws IOException {if(!value.has(key)||!value.get(key).isJsonObject())throw invalid("object "+key);return value.getAsJsonObject(key);}
    private static boolean flag(JsonObject value,String key,boolean expected){return value.has(key)&&value.get(key).isJsonPrimitive()&&value.getAsJsonPrimitive(key).isBoolean()&&value.get(key).getAsBoolean()==expected;}
    private static long integer(JsonObject value,String key,long maximum)throws IOException {
        if(!value.has(key)||!value.get(key).isJsonPrimitive()||!value.getAsJsonPrimitive(key).isNumber()||!value.get(key).getAsString().matches("0|[1-9][0-9]{0,19}"))throw invalid("integer "+key);
        BigInteger n=new BigInteger(value.get(key).getAsString());if(n.compareTo(BigInteger.valueOf(maximum))>0)throw invalid("integer bound "+key);return n.longValue();
    }
    private static double finite(JsonObject value,String key)throws IOException {
        if(!value.has(key)||!value.get(key).isJsonPrimitive()||!value.getAsJsonPrimitive(key).isNumber()||!Double.isFinite(value.get(key).getAsDouble()))throw invalid("finite "+key);return value.get(key).getAsDouble();
    }
    private static void subset(JsonObject expected,JsonObject actual)throws IOException {
        for(String key:expected.keySet())if(!expected.get(key).equals(actual.get(key)))throw invalid("expected source geometry differs: "+key);
    }
    public static Frame read(JsonObject result,Path directory,UiTarget target,String session,String boot,long androidPid,long started,long completed)throws IOException {
        target.requireCanvas();
        if(androidPid<1||androidPid>Integer.MAX_VALUE||started<0||completed<started||!directory.isAbsolute()||!directory.equals(directory.toRealPath())
                || !Files.getPosixFilePermissions(directory).equals(PosixFilePermissions.fromString("rwx------")))throw invalid("owned private capture directory/context required");
        try {
            Path manifest=directory.resolve("manifest.json"),png=directory.resolve("primary.png");
            if(!directory.toString().equals(text(result,"artifact_dir"))||!manifest.toString().equals(text(result,"manifest"))||!Files.isRegularFile(manifest,LinkOption.NOFOLLOW_LINKS)||Files.size(manifest)>65536)throw invalid("fixed capture artifacts differ");
            JsonObject disk=RunFiles.read(manifest),retained=result.deepCopy();retained.remove("artifact_dir");retained.remove("manifest");retained.remove("elapsed_ms");
            if(!disk.equals(retained))throw invalid("returned and retained native manifest differ");
            if(integer(result,"schema",1)!=1||!session.equals(text(result,"session_id"))||integer(result,"android_pid",Integer.MAX_VALUE)!=androidPid
                    || !boot.equals(text(result,"android_boot_id"))||!"parallel_fenced".equals(text(result,"capture_mode"))
                    || !flag(result,"accepted",true)||!flag(result,"frames_consistent",true)||!flag(result,"android_boot_identity_verified",true)
                    || !flag(result,"android_frames_verified",true)||!flag(result,"atomic",false)||!result.get("screens").toString().equals("[\"primary\"]"))throw invalid("actual same-boot SDK source fence differs");
            double elapsed=finite(result,"elapsed_ms"),skew=finite(result,"skew_upper_bound_ms");if(elapsed<0||elapsed>10000||skew<0||skew>500)throw invalid("native capture budget differs");
            if(!result.get("frames").isJsonArray()||result.getAsJsonArray("frames").size()!=1)throw invalid("one actual primary frame required");JsonObject frame=result.getAsJsonArray("frames").get(0).getAsJsonObject();
            if(!"primary".equals(text(frame,"screen"))||!"android_sdk".equals(text(frame,"source"))||!"primary.png".equals(text(frame,"file"))
                    ||integer(frame,"source_width",1920)!=1920||integer(frame,"source_height",720)!=720||integer(frame,"sdk_display_id",0)!=0||!frame.get("display_id").isJsonNull()||!flag(frame,"derived",true))throw invalid("primary ROI frame projection differs");
            JsonObject expected=target.expected(),rect=object(expected.getAsJsonObject("target"),"clipped_bounds");
            if(!rect.equals(frame.get("rect")))throw invalid("ROI rectangle differs");int width=(int)integer(rect,"width",1920),height=(int)integer(rect,"height",720);
            if(width<1||height<1||integer(frame,"width",1920)!=width||integer(frame,"height",720)!=height)throw invalid("ROI dimensions differ");
            JsonObject freshness=object(frame,"freshness");double age=finite(freshness,"age_upper_bound_ms");
            if(!flag(freshness,"source_clock_mapped",true)||!"sdk_frame_generation_estimate".equals(text(freshness,"basis"))||!flag(freshness,"within_limit",true)
                    ||integer(freshness,"max_age_ms",500)<1||age<0||age>freshness.get("max_age_ms").getAsInt())throw invalid("bounded SDK frame age unavailable");
            JsonObject roi=object(result,"roi"),before=object(roi,"before"),after=object(roi,"after");
            if(!flag(roi,"geometry_verified",true)||!flag(roi,"atomic",false)||!flag(roi,"pixels_match_node",false))throw invalid("native ROI geometry contract differs");
            for(String key:List.of("boot_id","source_instance","window","target","rect"))if(!before.get(key).equals(after.get(key)))throw invalid("native ROI changed during SDK capture");
            if(!boot.equals(text(before,"boot_id"))||!expected.get("source_instance").equals(before.get("source_instance"))||!rect.equals(before.get("rect")))throw invalid("native and independently observed ROI epochs differ");
            subset(object(expected,"window"),object(before,"window"));subset(object(expected,"target"),object(before,"target"));
            JsonObject scope=new JsonObject();scope.addProperty("session",session);scope.addProperty("boot",boot);scope.addProperty("androidPid",androidPid);
            scope.add("source",expected.get("source_instance"));scope.add("revision",expected.get("revision"));scope.add("window",expected.get("window"));scope.add("target",expected.get("target"));scope.add("rect",rect);
            PixelImage image=PixelImage.read(png,width,height);
            return new Frame(scope,image,png,manifest,started);
        } catch(RuntimeException malformed){throw new IOException("malformed keyboard capture manifest",malformed);}
    }
}
