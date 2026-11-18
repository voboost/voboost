package ru.voboost.voyemu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import ru.voboost.tests.RunFiles;

/** Actual fresh SDK pixels bracketed by the same authenticated OEM role and live View object. */
public final class SettingsRasterCapture {
    private SettingsRasterCapture(){}
    public static final class Frame {
        private final PixelImage image;private final JsonObject scope;private final Path png,manifest;private final int x,y,width,height;
        private Frame(PixelImage image,JsonObject scope,Path png,Path manifest,int x,int y,int width,int height){this.image=image;this.scope=scope.deepCopy();this.png=png;this.manifest=manifest;this.x=x;this.y=y;this.width=width;this.height=height;}
        public PixelImage image(){return image;}public JsonObject scope(){return scope.deepCopy();}public Path png(){return png;}public Path manifest(){return manifest;}public int x(){return x;}public int y(){return y;}public int width(){return width;}public int height(){return height;}
    }
    private static IOException invalid(String reason){return new IOException("Settings raster capture unavailable: "+reason);}
    private static String text(JsonObject o,String k)throws IOException{JsonElement v=o.get(k);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString()||v.getAsString().isBlank())throw invalid("text "+k);return v.getAsString();}
    private static JsonObject object(JsonObject o,String k)throws IOException{if(!o.has(k)||!o.get(k).isJsonObject())throw invalid("object "+k);return o.getAsJsonObject(k);}
    private static boolean flag(JsonObject o,String k,boolean expected){JsonElement v=o.get(k);return v!=null&&v.isJsonPrimitive()&&v.getAsJsonPrimitive().isBoolean()&&v.getAsBoolean()==expected;}
    private static BigInteger integer(JsonObject o,String k,boolean string)throws IOException{JsonElement v=o.get(k);if(v==null||!v.isJsonPrimitive()||string&&!v.getAsJsonPrimitive().isString()||!string&&!v.getAsJsonPrimitive().isNumber()||!v.getAsString().matches("0|[1-9][0-9]{0,19}"))throw invalid("integer "+k);BigInteger n=new BigInteger(v.getAsString());if(n.bitLength()>64)throw invalid("integer bound "+k);return n;}
    private static int small(JsonObject o,String k,int max)throws IOException{BigInteger n=integer(o,k,false);if(n.compareTo(BigInteger.valueOf(max))>0)throw invalid("bound "+k);return n.intValue();}
    private static JsonObject role(JsonObject proof,String role,String value)throws IOException{JsonObject found=null;for(JsonElement item:proof.getAsJsonArray("rows")){JsonObject row=item.getAsJsonObject();if(role.equals(text(row,"role"))&&value.equals(row.has("value")?text(row,"value"):"")){if(found!=null)throw invalid("ambiguous source role");found=row;}}if(found==null)throw invalid("source role missing");return found;}
    private static void clock(JsonObject interval,String session)throws IOException{if(!"host.steady".equals(text(interval,"clock_domain"))||!session.equals(text(interval,"clock_id"))||integer(interval,"end_ns",true).compareTo(integer(interval,"start_ns",true))<0)throw invalid("host clock identity or interval");}
    public static Frame read(JsonObject capture,Path directory,JsonObject before,JsonObject after,String role,String value)throws IOException{
        return readBound(capture,directory,before,after,role,value,List.of("profile","sessionId","bootId","sourceInstance","generationId","catalogIdentity","documentGeneration","captureId","androidPid"),false);
    }
    static Frame readStock(JsonObject capture,Path directory,JsonObject before,JsonObject after,String role,String value)throws IOException{
        if(!flag(before,"stockOem",true)||!flag(after,"stockOem",true))throw invalid("actual pre-install stock measurements required");
        return readBound(capture,directory,before,after,role,value,List.of("profile","sessionId","bootId","sourceInstance","androidPid"),false);
    }
    static Frame readStockArchive(JsonObject capture,Path directory,JsonObject before,JsonObject after,String role,String value)throws IOException{
        if(!flag(before,"stockOem",true)||!flag(after,"stockOem",true))throw invalid("archived stock proof required");
        return readBound(capture,directory,before,after,role,value,List.of("profile","sessionId","bootId","sourceInstance","androidPid"),true);
    }
    private static Frame readBound(JsonObject capture,Path directory,JsonObject before,JsonObject after,String role,String value,List<String> identities,boolean sourceArchive)throws IOException{
        try{
            if(!flag(before,"viewMetricsVerified",true)||!flag(after,"viewMetricsVerified",true))throw invalid("validated actual owner measurements required");
            for(String key:identities)if(!before.has(key)||!before.get(key).equals(after.get(key)))throw invalid("source epoch differs "+key);
            for(String key:List.of("caseNumber","language","surface","phase","rootIdentity","windowId","targetPid","targetStarttime","endpointEpoch","sourceRootObserved","rootBounds"))if(before.has(key)||after.has(key))if(!before.has(key)||!before.get(key).equals(after.get(key)))throw invalid("source window changed "+key);
            if(text(before,"observationId").equals(text(after,"observationId")))throw invalid("reused actual View observation");
            JsonObject selected=role(before,role,value),selectedAfter=role(after,role,value);for(String key:List.of("nodeId","bounds","clippedBounds","layout","textStyle"))if(!selected.has(key)||!selected.get(key).equals(selectedAfter.get(key)))throw invalid("actual View changed "+key);
            for(String key:List.of("textLayout","actualText","checked"))if(selected.has(key)||selectedAfter.has(key))if(!selected.has(key)||!selected.get(key).equals(selectedAfter.get(key)))throw invalid("actual View changed "+key);
            JsonObject rect=object(selected,"bounds");int x=small(rect,"x",1920),y=small(rect,"y",720),width=small(rect,"width",1920),height=small(rect,"height",720);if(width<1||height<1||x+width>1920||y+height>720||!rect.equals(selected.get("clippedBounds"))||small(object(selected,"layout"),"measured_width",1920)!=width||small(object(selected,"layout"),"measured_height",720)!=height)throw invalid("clipped/offscreen role");
            String session=text(before,"sessionId");JsonObject start=object(before,"hostInterval"),end=object(after,"hostInterval"),interval=object(capture,"host_interval");clock(start,session);clock(end,session);clock(interval,session);
            if(integer(start,"end_ns",true).compareTo(integer(interval,"start_ns",true))>0||integer(interval,"end_ns",true).compareTo(integer(end,"start_ns",true))>0)throw invalid("pixels not bracketed by actual owner observations");
            if(!directory.isAbsolute()||!directory.equals(directory.toRealPath())||!sourceArchive&&!Files.getPosixFilePermissions(directory).equals(PosixFilePermissions.fromString("rwx------")))throw invalid("owned private canonical capture directory");
            Path manifest=directory.resolve("manifest.json"),png=directory.resolve("primary.png");if(!directory.toString().equals(text(capture,"artifact_dir"))||!manifest.toString().equals(text(capture,"manifest"))||!Files.isRegularFile(manifest,LinkOption.NOFOLLOW_LINKS)||Files.size(manifest)>65536)throw invalid("fixed capture artifacts");JsonObject retained=capture.deepCopy();retained.remove("artifact_dir");retained.remove("manifest");retained.remove("elapsed_ms");if(!retained.equals(RunFiles.read(manifest)))throw invalid("retained/returned manifest differs");
            if(small(capture,"schema",1)!=1||!session.equals(text(capture,"session_id"))||!text(before,"bootId").equals(text(capture,"android_boot_id"))||!before.get("androidPid").equals(capture.get("android_pid"))||!"parallel_fenced".equals(text(capture,"capture_mode"))||!flag(capture,"accepted",true)||!flag(capture,"frames_consistent",true)||!flag(capture,"android_boot_identity_verified",true)||!flag(capture,"android_frames_verified",true)||!flag(capture,"atomic",false)||!capture.get("screens").toString().equals("[\"primary\"]"))throw invalid("capture boot/process/source proof");
            if(!capture.has("frames")||!capture.get("frames").isJsonArray()||capture.getAsJsonArray("frames").size()!=1)throw invalid("one actual primary frame required");JsonObject frame=capture.getAsJsonArray("frames").get(0).getAsJsonObject();
            if(!"primary".equals(text(frame,"screen"))||!"android_sdk".equals(text(frame,"source"))||!"primary.png".equals(text(frame,"file"))||!flag(frame,"derived",false)||small(frame,"source_width",1920)!=1920||small(frame,"source_height",720)!=720||small(frame,"width",1920)!=1920||small(frame,"height",720)!=720||small(frame,"sdk_display_id",0)!=0)throw invalid("original full primary SDK frame required");
            JsonObject full=object(frame,"rect");if(small(full,"x",0)!=0||small(full,"y",0)!=0||small(full,"width",1920)!=1920||small(full,"height",720)!=720)throw invalid("source frame projection");
            JsonObject age=object(frame,"freshness"),frameInterval=object(frame,"host_interval");clock(frameInterval,session);if(integer(frameInterval,"start_ns",true).compareTo(integer(interval,"start_ns",true))<0||integer(frameInterval,"end_ns",true).compareTo(integer(interval,"end_ns",true))>0)throw invalid("frame clock lies outside capture operation");JsonElement bound=age.get("age_upper_bound_ms");if(!flag(age,"source_clock_mapped",true)||!flag(age,"within_limit",true)||!"sdk_frame_generation_estimate".equals(text(age,"basis"))||small(age,"max_age_ms",500)<1||bound==null||!bound.isJsonPrimitive()||!bound.getAsJsonPrimitive().isNumber()||!Double.isFinite(bound.getAsDouble())||bound.getAsDouble()<0||bound.getAsDouble()>age.get("max_age_ms").getAsInt())throw invalid("bounded source frame age proof");
            BigDecimal earliest=new BigDecimal(integer(frameInterval,"end_ns",true)).subtract(new BigDecimal(bound.getAsString()).multiply(BigDecimal.valueOf(1000000)));if(earliest.compareTo(new BigDecimal(integer(start,"end_ns",true)))<0)throw invalid("cached frame could predate the owning View observation");
            JsonObject scope=before.deepCopy();scope.remove("rows");scope.addProperty("role",role);scope.addProperty("value",value);scope.add("target",selected.deepCopy());scope.addProperty("earliestFrameHostNs",earliest.toPlainString());return new Frame(PixelImage.read(png,1920,720),scope,png,manifest,x,y,width,height);
        }catch(RuntimeException malformed){throw new IOException("malformed Settings raster capture",malformed);}
    }
}
