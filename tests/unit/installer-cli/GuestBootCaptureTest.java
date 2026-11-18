package ru.voboost.tests.installer.cli;
import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.tests.RunFiles;
class GuestBootCaptureTest {
    @TempDir Path directory;
    static JsonObject j(String value)throws IOException{return RunFiles.parse(value);}
    static JsonObject clock(String from,String to)throws IOException{return j("{\"clock_domain\":\"host.steady\",\"clock_id\":\"session\",\"start_ns\":\""+from+"\",\"end_ns\":\""+to+"\"}");}
    static JsonObject ui(boolean before)throws IOException{JsonObject v=j("{\"format\":\"voyemu.ui.v1\",\"profile\":\"profile\",\"session_id\":\"session\",\"boot_id\":\"boot\",\"android_pid\":42}");v.addProperty("observation_id",before?"one":"two");v.add("host_interval",clock(before?"10":"40",before?"20":"50"));return v;}
    JsonObject capture()throws IOException {
        directory=directory.toRealPath();ImageIO.write(new BufferedImage(1920,720,BufferedImage.TYPE_INT_RGB),"png",directory.resolve("primary.png").toFile());
        JsonObject c=j("{\"schema\":1,\"accepted\":true,\"session_id\":\"session\",\"android_boot_id\":\"boot\",\"android_pid\":42,\"android_boot_identity_verified\":true,\"android_frames_verified\":true,\"exported_frame_verified\":false,\"frames\":[{\"screen\":\"primary\",\"source\":\"android_sdk\",\"file\":\"primary.png\",\"derived\":false,\"sdk_display_id\":0,\"width\":1920,\"height\":720,\"source_width\":1920,\"source_height\":720,\"rect\":{\"x\":0,\"y\":0,\"width\":1920,\"height\":720},\"freshness\":{\"source_clock_mapped\":true,\"within_limit\":true,\"basis\":\"sdk_frame_generation_estimate\",\"max_age_ms\":500,\"age_upper_bound_ms\":40}}]}");c.add("host_interval",clock("21","39"));retain(c);c.addProperty("elapsed_ms",12.5);return c;
    }
    void retain(JsonObject c)throws IOException{JsonObject disk=c.deepCopy();disk.remove("artifact_dir");disk.remove("manifest");disk.remove("elapsed_ms");RunFiles.write(directory.resolve("manifest.json"),disk);c.addProperty("artifact_dir",directory.toString());c.addProperty("manifest",directory.resolve("manifest.json").toString());}
    @Test void actualOriginalFrameIsBoundToCurrentOrderedBootHierarchies()throws Exception{JsonObject c=capture();assertEquals(directory.resolve("manifest.json"),GuestBootCapture.read(c,directory,ui(true),ui(false),"profile","session","boot",42));}
    @Test void staleBootPidHierarchyOrDerivedFrameCannotPass()throws Exception {
        JsonObject original=capture();for(String kind:new String[]{"boot","pid","derived","stale","clock","hierarchy","manifest"}){JsonObject c=original.deepCopy(),before=ui(true),after=ui(false);switch(kind){case "boot"->c.addProperty("android_boot_id","foreign");case "pid"->c.addProperty("android_pid",43);case "derived"->c.getAsJsonArray("frames").get(0).getAsJsonObject().addProperty("derived",true);case "stale"->c.getAsJsonArray("frames").get(0).getAsJsonObject().getAsJsonObject("freshness").addProperty("age_upper_bound_ms",501);case "clock"->c.add("host_interval",clock("1","39"));case "hierarchy"->after.addProperty("observation_id","one");case "manifest"->c.addProperty("visible_effect_verified",true);}if(!kind.equals("manifest"))retain(c);assertThrows(IOException.class,()->GuestBootCapture.read(c,directory,before,after,"profile","session","boot",42),kind);}
    }
}
