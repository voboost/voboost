package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.tests.RunFiles;

class SettingsRasterCaptureTest {
    @TempDir Path work;
    static JsonObject j(String json)throws IOException{return RunFiles.parse(json);}
    static JsonObject interval(String a,String b)throws IOException{return j("{\"clock_domain\":\"host.steady\",\"clock_id\":\"session\",\"start_ns\":\""+a+"\",\"end_ns\":\""+b+"\"}");}
    static JsonObject observed(boolean before)throws IOException{
        JsonObject proof=j("{\"viewMetricsVerified\":true,\"profile\":\"profile\",\"sessionId\":\"session\",\"bootId\":\"boot\",\"sourceInstance\":\"observer\",\"generationId\":\"generation\",\"catalogIdentity\":\"catalog\",\"documentGeneration\":\"revision\",\"captureId\":\"capture\",\"androidPid\":42,\"rows\":[{\"role\":\"uninstall.action\",\"value\":\"uninstall\",\"nodeId\":\"window:button@aa\",\"bounds\":{\"x\":500,\"y\":100,\"width\":150,\"height\":70},\"clippedBounds\":{\"x\":500,\"y\":100,\"width\":150,\"height\":70},\"layout\":{\"measured_width\":150,\"measured_height\":70},\"textStyle\":{\"text_size_px\":28}}]}");
        proof.addProperty("observationId",before?"before":"after");proof.add("hostInterval",before?interval("1000000000","1010000000"):interval("1200000000","1210000000"));return proof;
    }
    JsonObject capture()throws Exception{
        work=work.toRealPath();Files.setPosixFilePermissions(work,PosixFilePermissions.fromString("rwx------"));ImageIO.write(new BufferedImage(1920,720,BufferedImage.TYPE_INT_RGB),"png",work.resolve("primary.png").toFile());
        JsonObject c=j("{\"schema\":1,\"session_id\":\"session\",\"android_boot_id\":\"boot\",\"android_pid\":42,\"capture_mode\":\"parallel_fenced\",\"accepted\":true,\"frames_consistent\":true,\"android_boot_identity_verified\":true,\"android_frames_verified\":true,\"atomic\":false,\"screens\":[\"primary\"],\"frames\":[{\"screen\":\"primary\",\"source\":\"android_sdk\",\"file\":\"primary.png\",\"derived\":false,\"source_width\":1920,\"source_height\":720,\"width\":1920,\"height\":720,\"sdk_display_id\":0,\"rect\":{\"x\":0,\"y\":0,\"width\":1920,\"height\":720},\"freshness\":{\"source_clock_mapped\":true,\"within_limit\":true,\"basis\":\"sdk_frame_generation_estimate\",\"max_age_ms\":500,\"age_upper_bound_ms\":50}}]}");
        c.add("host_interval",interval("1020000000","1190000000"));c.getAsJsonArray("frames").get(0).getAsJsonObject().add("host_interval",interval("1030000000","1180000000"));retain(c);c.addProperty("artifact_dir",work.toString());c.addProperty("manifest",work.resolve("manifest.json").toString());return c;
    }
    void retain(JsonObject c)throws IOException{var disk=c.deepCopy();disk.remove("artifact_dir");disk.remove("manifest");disk.remove("elapsed_ms");RunFiles.write(work.resolve("manifest.json"),disk);}
    SettingsRasterCapture.Frame read(JsonObject c,JsonObject before,JsonObject after)throws IOException{return SettingsRasterCapture.read(c,work,before,after,"uninstall.action","uninstall");}
    @Test void fixedRetainedSdkPixelsAreBoundToSameLiveRoleAndCurrentSource()throws Exception{var result=read(capture(),observed(true),observed(false));assertEquals(1920,result.image().width());assertEquals(500,result.x());assertEquals(150,result.width());assertEquals("1130000000",result.scope().get("earliestFrameHostNs").getAsString());assertEquals(work.resolve("primary.png"),result.png());}
    @Test void foreignBootProcessSourceOrReusedActualViewObservationCannotPass()throws Exception{var c=capture();for(String key:new String[]{"profile","sessionId","bootId","sourceInstance","generationId","catalogIdentity","documentGeneration","captureId","androidPid"}){var after=observed(false);after.addProperty(key,"foreign");assertThrows(IOException.class,()->read(c,observed(true),after),key);}var reused=observed(false);reused.addProperty("observationId","before");assertThrows(IOException.class,()->read(c,observed(true),reused));}
    @Test void clippingScalingChangedLayoutAndDifferentObjectAreRejected()throws Exception{var c=capture();for(String key:new String[]{"nodeId","bounds","clippedBounds","layout","textStyle"}){var a=observed(false);a.getAsJsonArray("rows").get(0).getAsJsonObject().remove(key);assertThrows(IOException.class,()->read(c,observed(true),a),key);}var b=observed(true);var a=observed(false);for(var p:new JsonObject[]{b,a})p.getAsJsonArray("rows").get(0).getAsJsonObject().getAsJsonObject("clippedBounds").addProperty("width",149);assertThrows(IOException.class,()->read(c,b,a));}
    @Test void staleSdkFrameMayNotPredateActualOwnerObservationEvenWithinApiAgeLimit()throws Exception{var c=capture();c.getAsJsonArray("frames").get(0).getAsJsonObject().getAsJsonObject("freshness").addProperty("age_upper_bound_ms",200);retain(c);assertThrows(IOException.class,()->read(c,observed(true),observed(false)));}
    @Test void intervalForeignClockUnmappedOrUnboundedAgeAndWrongPidAreRejected()throws Exception{var original=capture();for(String mutation:new String[]{"clock","frameOutside","notBracketed","unmapped","unbounded","pid","derived"}){var c=original.deepCopy();var f=c.getAsJsonArray("frames").get(0).getAsJsonObject();switch(mutation){case "clock"->c.getAsJsonObject("host_interval").addProperty("clock_id","foreign");case "frameOutside"->f.getAsJsonObject("host_interval").addProperty("end_ns","1200000000");case "notBracketed"->c.getAsJsonObject("host_interval").addProperty("start_ns","1000000000");case "unmapped"->f.getAsJsonObject("freshness").addProperty("source_clock_mapped",false);case "unbounded"->f.getAsJsonObject("freshness").addProperty("max_age_ms",501);case "pid"->c.addProperty("android_pid",43);case "derived"->f.addProperty("derived",true);}retain(c);assertThrows(IOException.class,()->read(c,observed(true),observed(false)),mutation);}}
    @Test void alteredManifestSymlinkPixelsAndNonPrivateArtifactsFailClosed()throws Exception{var c=capture();var changed=c.deepCopy();changed.addProperty("accepted",false);retain(changed);assertThrows(IOException.class,()->read(c,observed(true),observed(false)));retain(c);Path png=work.resolve("primary.png"),saved=work.resolve("saved.png");Files.move(png,saved);Files.createSymbolicLink(png,saved);assertThrows(IOException.class,()->read(c,observed(true),observed(false)));Files.delete(png);Files.move(saved,png);Files.setPosixFilePermissions(work,PosixFilePermissions.fromString("rwxr-xr-x"));assertThrows(IOException.class,()->read(c,observed(true),observed(false)));}
    @Test void changingWindowRootEndpointNativeLayoutOrActualCaptionBreaksThePixelBracket()throws Exception {
        var capture=capture();
        for(String key:java.util.List.of("caseNumber","language","rootIdentity","windowId","targetPid","targetStarttime","endpointEpoch","surface","phase","sourceRootObserved")){
            var before=observed(true);var after=observed(false);before.addProperty(key,"same");after.addProperty(key,"different");assertThrows(IOException.class,()->read(capture,before,after),key);
        }
        for(String key:java.util.List.of("actualText","textLayout","checked")){
            var before=observed(true);var after=observed(false);before.getAsJsonArray("rows").get(0).getAsJsonObject().addProperty(key,"same");after.getAsJsonArray("rows").get(0).getAsJsonObject().addProperty(key,"different");assertThrows(IOException.class,()->read(capture,before,after),key);
        }
    }

}
