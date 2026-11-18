package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.tests.RunFiles;

class StockFontReferenceTest {
    private static final String CAPTION="恢复出厂设置",ID="host-fixture",PROFILE="profile",SESSION="a".repeat(32);
    @TempDir Path product;
    private Path font(){return Path.of("../voboost-components/src/main/java/ru/voboost/components/font/Font_bold_ascii.ttf").toAbsolutePath().normalize();}
    Path directory(){return product.resolve("tests/emulator/agents/agent-settings/reference/"+ID);}
    Path captureDirectory(){return directory().resolve("capture");}
    Map<String,Path> inputs(){return Map.of("owning-dfp",font());}
    private JsonObject authority(){return SettingsOemMeasurementsTest.object("profile",PROFILE,"sessionId",SESSION,"bootId",SettingsOemMeasurementsTest.BOOT,
        "androidPid",42,"quickbootMode","cold","status",SettingsOemMeasurementsTest.object("route","absent"),"rootAbsence",SettingsOemMeasurementsTest.object(
            "schema",2,"source","voboost","status","ready","boot_id",SettingsOemMeasurementsTest.BOOT,"data",SettingsOemMeasurementsTest.object("root","absent")));}
    private JsonObject ui(boolean before)throws Exception {
        JsonObject tree=StockSettingsBaselineTest.ui(before);var node=tree.getAsJsonArray("screens").get(0).getAsJsonObject().getAsJsonArray("nodes").get(2).getAsJsonObject();
        var target=SettingsTextLayoutTest.target(CAPTION);var line=target.getAsJsonObject("textLayout").getAsJsonArray("lines").get(0).getAsJsonObject();line.addProperty("left",41);line.addProperty("right",209);
        node.add("layout",target.get("layout"));node.add("text_style",target.get("textStyle"));node.add("text_layout",target.get("textLayout"));return tree;
    }
    void setup()throws Exception {
        product=product.toRealPath();Files.createDirectories(captureDirectory());Files.setPosixFilePermissions(directory(),java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        RunFiles.write(directory().resolve("authority.json"),authority());RunFiles.write(directory().resolve("before.json"),ui(true));RunFiles.write(directory().resolve("after.json"),ui(false));
        var helper=new SettingsRasterCaptureTest();helper.work=captureDirectory();var capture=helper.capture();capture.addProperty("session_id",SESSION);capture.addProperty("android_boot_id",SettingsOemMeasurementsTest.BOOT);
        capture.getAsJsonObject("host_interval").addProperty("clock_id",SESSION);capture.getAsJsonArray("frames").get(0).getAsJsonObject().getAsJsonObject("host_interval").addProperty("clock_id",SESSION);helper.retain(capture);
        BufferedImage image=new BufferedImage(1920,720,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=image.createGraphics();
        graphics.drawImage(ImageIO.read(Path.of("tests/unit/agents/agent-settings/ui-raster/cjk-dfp-harfbuzz.png").toFile()),515+41,84+9,null);graphics.dispose();ImageIO.write(image,"png",captureDirectory().resolve("primary.png").toFile());
        var spec=SettingsOemMeasurementsTest.object("schema",1,"kind","emulator-stock-oem-golden","referenceId",ID,"reviewedBy","root");JsonArray profiles=new JsonArray();profiles.add(PROFILE);spec.add("profiles",profiles);
        var sample=SettingsOemMeasurementsTest.object("model","dfp28","family","button","captureDirectory","capture","authorityBefore","authority.json","authorityAfter","authority.json","uiBefore","before.json","uiAfter","after.json","caption",CAPTION,"resourceId",StockSettingsBaseline.Control.FACTORY_RESET.resource());
        var fonts=new JsonArray();fonts.add(SettingsOemMeasurementsTest.object("input","owning-dfp","sha256","305f942097a1b65d1dca1c357c0197acf62346eea83e2fbe95cc55f62821f000","faceIndex",0,"postscriptName","DFPKingGothicGB-Semibold"));sample.add("fonts",fonts);var samples=new JsonArray();samples.add(sample);spec.add("samples",samples);RunFiles.write(directory().resolve("reference.json"),spec);
    }
    SettingsRasterCapture.Frame frame()throws Exception {
        var a=authority();var authority=new StockSettingsBaseline.Authority(PROFILE,SESSION,SettingsOemMeasurementsTest.BOOT,42,"cold",a.getAsJsonObject("status"),a.getAsJsonObject("rootAbsence"),directory().resolve("authority.json"));
        JsonObject b=StockSettingsBaseline.measureReference(ui(true),authority,StockSettingsBaseline.Control.FACTORY_RESET.resource(),CAPTION),after=StockSettingsBaseline.measureReference(ui(false),authority,StockSettingsBaseline.Control.FACTORY_RESET.resource(),CAPTION);
        var manifest=RunFiles.read(captureDirectory().resolve("manifest.json"));manifest.addProperty("artifact_dir",captureDirectory().toString());manifest.addProperty("manifest",captureDirectory().resolve("manifest.json").toString());return SettingsRasterCapture.readStockArchive(manifest,captureDirectory(),b,after,"stock.reference",CAPTION);
    }
    @Test void independentHarfBuzzChineseGlyphsCalibrateAndVerifyWithoutTreatingHostFixtureAsHardware()throws Exception {
        setup();var ref=StockFontReference.load(product,ID,PROFILE,inputs());var proof=ref.verify("dfp28",frame(),CAPTION);
        assertTrue(proof.get("rasterMatch").getAsBoolean());assertFalse(proof.get("hardwareFidelityAccepted").getAsBoolean());assertFalse(proof.get("publicationApproval").getAsBoolean());assertTrue(proof.get("edgeRadiusPx").getAsInt()<=2);
    }
    @Test void missingUnreviewedForeignProfileSymlinkAndFontFaceCannotAdmitReference()throws Exception {
        setup();for(String mutation:new String[]{"reviewer","profile","font","face","traversal"}) {
            var original=RunFiles.read(directory().resolve("reference.json"));var value=original.deepCopy();var sample=value.getAsJsonArray("samples").get(0).getAsJsonObject();
            switch(mutation){case"reviewer"->value.addProperty("reviewedBy","candidate");case"profile"->value.getAsJsonArray("profiles").set(0,new com.google.gson.JsonPrimitive("foreign"));case"font"->sample.getAsJsonArray("fonts").get(0).getAsJsonObject().addProperty("sha256","0".repeat(64));case"face"->sample.getAsJsonArray("fonts").get(0).getAsJsonObject().addProperty("postscriptName","NotoSansCJKsc-Regular");case"traversal"->sample.addProperty("uiBefore","../before.json");}
            RunFiles.write(directory().resolve("reference.json"),value);assertThrows(IOException.class,()->StockFontReference.load(product,ID,PROFILE,inputs()),mutation);RunFiles.write(directory().resolve("reference.json"),original);
        }
        assertThrows(IOException.class,()->StockFontReference.load(product,"missing",PROFILE,inputs()));
        var path=directory().resolve("before.json");var saved=directory().resolve("saved.json");Files.move(path,saved);Files.createSymbolicLink(path,saved);assertThrows(IOException.class,()->StockFontReference.load(product,ID,PROFILE,inputs()));
    }
    @Test void clippingWrongFontWrongSpacingAndMissingChineseGlyphFailAfterIndependentCalibration()throws Exception {
        setup();var ref=StockFontReference.load(product,ID,PROFILE,inputs());Path png=captureDirectory().resolve("primary.png");BufferedImage good=ImageIO.read(png.toFile());
        for(String mutation:new String[]{"clip","missing","spacing","wrongFont"}) {
            var changed=new BufferedImage(1920,720,BufferedImage.TYPE_INT_RGB);var g=changed.createGraphics();g.drawImage(good,0,0,null);g.setColor(Color.BLACK);
            switch(mutation){case"clip"->g.fillRect(515+41,84,7,70);case"missing"->g.fillRect(515+41+56,84,28,70);
                case"spacing"->{g.fillRect(515+41+84,84,84,70);g.drawImage(good,515+41+88,84,515+41+172,154,515+41+84,84,515+41+168,154,null);}
                case"wrongFont"->{g.fillRect(515,84,250,70);g.drawImage(ImageIO.read(Path.of("tests/unit/agents/agent-settings/ui-raster/cjk-noto-sc-wrong-font.png").toFile()),515+41,84+9,null);}}
            g.dispose();ImageIO.write(changed,"png",png.toFile());assertThrows(IOException.class,()->ref.verify("dfp28",frame(),CAPTION),mutation);
        }
        ImageIO.write(good,"png",png.toFile());assertThrows(IOException.class,()->ref.verify("missing",frame(),CAPTION));assertThrows(IOException.class,()->ref.verify("dfp28",frame(),"恢复"));
    }
    @Test void stockCaptureMissingNativeLayoutForeignBootOrCachedFrameDoesNotCalibrate()throws Exception {
        setup();for(String mutation:new String[]{"layout","boot","cached"}) {
            Path path=mutation.equals("cached")?captureDirectory().resolve("manifest.json"):directory().resolve("before.json");var original=RunFiles.read(path);var changed=original.deepCopy();
            if(mutation.equals("layout"))changed.getAsJsonArray("screens").get(0).getAsJsonObject().getAsJsonArray("nodes").get(2).getAsJsonObject().remove("text_layout");
            if(mutation.equals("boot"))changed.addProperty("boot_id","11111111-1111-1111-1111-111111111111");
            if(mutation.equals("cached"))changed.getAsJsonArray("frames").get(0).getAsJsonObject().getAsJsonObject("freshness").addProperty("age_upper_bound_ms",200);
            RunFiles.write(path,changed);assertThrows(IOException.class,()->StockFontReference.load(product,ID,PROFILE,inputs()),mutation);RunFiles.write(path,original);
        }
    }
    @Test void repeatedStockResourceRequiresUniqueReviewedCaptionWithinTheActualWindow()throws Exception {
        setup();
        for(String file:List.of("before.json","after.json")){Path path=directory().resolve(file);var tree=RunFiles.read(path);tree.getAsJsonArray("screens").get(0).getAsJsonObject().getAsJsonArray("nodes").get(3).getAsJsonObject().addProperty("resource_id",StockSettingsBaseline.Control.FACTORY_RESET.resource());RunFiles.write(path,tree);}
        assertNotNull(StockFontReference.load(product,ID,PROFILE,inputs()));
        for(String file:List.of("before.json","after.json")){Path path=directory().resolve(file);var tree=RunFiles.read(path);tree.getAsJsonArray("screens").get(0).getAsJsonObject().getAsJsonArray("nodes").get(3).getAsJsonObject().addProperty("text",CAPTION);RunFiles.write(path,tree);}
        assertThrows(IOException.class,()->StockFontReference.load(product,ID,PROFILE,inputs()));
    }

}
