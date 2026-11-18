package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.tests.RunFiles;

class StockSettingsBaselineTest {
    private static final String SESSION="a".repeat(32),BOOT=SettingsOemMeasurementsTest.BOOT;
    @TempDir Path work;
    static JsonObject o(Object...args){return SettingsOemMeasurementsTest.object(args);}
    StockSettingsBaseline.Authority authority()throws IOException{return new StockSettingsBaseline.Authority("profile",SESSION,BOOT,42,"cold",o("route","absent"),o("schema",2,"source","voboost","status","ready","boot_id",BOOT,"data",o("root","absent")),work.resolve("authority.json"));}
    static JsonObject node(String identity,String parent,String kind,String resource,String text,int x,int y,int width,int height){var bounds=o("x",x,"y",y,"width",width,"height",height);return o("id","w:"+identity,"parent",parent,"window_id","w","class",kind,"resource_id",resource,"package",SystemLanguagePicker.OWNER,"present",true,"visible",true,"bridge_version",1,"text",text,"bounds",bounds,"clipped_bounds",bounds.deepCopy(),"layout",o("measured_width",width,"measured_height",height),"text_style",o("text_size_px",28,"text_scale_x",1,"letter_spacing",0));}
    static JsonObject ui(boolean before)throws Exception{
        var ui=SettingsOemMeasurementsTest.observation();ui.addProperty("session_id",SESSION);ui.addProperty("android_pid",42);ui.addProperty("observation_id",before?"stock-before":"stock-after");var interval=before?SettingsRasterCaptureTest.interval("1000000000","1010000000"):SettingsRasterCaptureTest.interval("1200000000","1210000000");interval.addProperty("clock_id",SESSION);ui.add("host_interval",interval);
        var surface=ui.getAsJsonArray("screens").get(0).getAsJsonObject();var nodes=surface.getAsJsonArray("nodes");nodes.remove(2);nodes.add(node("android.widget.TextView@aaaa","w:android.widget.LinearLayout@2222","android.widget.TextView",StockSettingsBaseline.Control.FACTORY_RESET.resource(),"恢复出厂设置",515,84,250,70));nodes.add(node("android.widget.Button@bbbb","w:android.widget.LinearLayout@2222","android.widget.Button",SystemLanguagePicker.OWNER+":id/btn_check_car_upgrade","检查更新",515,342,200,70));surface.getAsJsonArray("windows").get(0).getAsJsonObject().addProperty("node_count",4);return ui;
    }
    final class Port implements StockSettingsBaseline.Port {
        private final StockSettingsBaseline.Viewport expected;private int reads;private boolean changedBoot;
        Port(StockSettingsBaseline.Viewport expected){this.expected=expected;}
        public StockSettingsBaseline.Authority authority()throws Exception{var a=StockSettingsBaselineTest.this.authority();if(changedBoot&&reads>=2)return new StockSettingsBaseline.Authority(a.profile(),a.session(),"11111111-1111-1111-1111-111111111111",a.androidPid(),a.quickbootMode(),a.status(),a.rootAbsence(),a.receipt());return a;}
        public Path open(StockSettingsBaseline.Viewport view){assertEquals(expected,view);return work.resolve("navigation.json");}
        public JsonObject ui()throws Exception{return StockSettingsBaselineTest.ui(reads++==0);}
        public Path captureDirectory(StockSettingsBaseline.Viewport view){assertEquals(expected,view);return work;}
        public JsonObject capture(Path directory)throws Exception{var helper=new SettingsRasterCaptureTest();helper.work=directory;var c=helper.capture();c.addProperty("session_id",SESSION);c.addProperty("android_boot_id",BOOT);c.getAsJsonObject("host_interval").addProperty("clock_id",SESSION);c.getAsJsonArray("frames").get(0).getAsJsonObject().getAsJsonObject("host_interval").addProperty("clock_id",SESSION);helper.retain(c);return c;}
        public Path save(String name,JsonObject proof)throws Exception{Path file=work.resolve(name+".json");RunFiles.write(file,proof);return file;}
    }
    @Test void actualColdPreinstallStockPixelsBecomeReviewCandidateAndNeverAnAcceptedReference()throws Exception{var p=new Port(StockSettingsBaseline.Viewport.SYSTEM_ACTIONS);var result=StockSettingsBaseline.collect(p,StockSettingsBaseline.Viewport.SYSTEM_ACTIONS);assertEquals(1,result.frames().size());assertFalse(result.proof().get("referenceAccepted").getAsBoolean());assertTrue(result.proof().get("rootReviewRequired").getAsBoolean());assertFalse(result.proof().get("hardwareFidelityAccepted").getAsBoolean());assertFalse(result.proof().get("publicationApproval").getAsBoolean());assertFalse(result.frames().get(0).scope().has("generationId"));assertFalse(result.frames().get(0).scope().has("catalogIdentity"));}
    @Test void warmInstalledOrForeignBootAuthorityCannotCollectStockReferences()throws Exception{var a=authority();for(String mutation:new String[]{"warm","installed","foreignRootBoot"}){var status=a.status();var root=a.rootAbsence();String mode=a.quickbootMode();switch(mutation){case"warm"->mode="warm";case"installed"->status.addProperty("route","installed");case"foreignRootBoot"->root.addProperty("boot_id","11111111-1111-1111-1111-111111111111");}var bad=new StockSettingsBaseline.Authority(a.profile(),a.session(),a.boot(),a.androidPid(),mode,status,root,a.receipt());assertThrows(IOException.class,()->StockSettingsBaseline.authority(bad),mutation);}}
    @Test void wrongCaptionForeignSymbolLostBridgeAndInstalledContributionCannotBeBaseline()throws Exception{for(String mutation:new String[]{"caption","resource","bridge","duplicate","contribution"}){var tree=ui(true);var nodes=tree.getAsJsonArray("screens").get(0).getAsJsonObject().getAsJsonArray("nodes");var target=nodes.get(2).getAsJsonObject();switch(mutation){case"caption"->target.addProperty("text","恢复");case"resource"->target.addProperty("resource_id","foreign:id/reset");case"bridge"->target.addProperty("bridge_version",0);case"duplicate"->nodes.add(target.deepCopy());case"contribution"->nodes.get(1).getAsJsonObject().addProperty("text","Voboost 1.0.0");}assertThrows(IOException.class,()->StockSettingsBaseline.measure(tree,authority(),StockSettingsBaseline.Viewport.SYSTEM_ACTIONS),mutation);}}
    @Test void baselineCollectionRejectsAChangedBootInsteadOfPinningCurrentPixels()throws Exception{var port=new Port(StockSettingsBaseline.Viewport.SYSTEM_ACTIONS);port.changedBoot=true;assertThrows(IOException.class,()->StockSettingsBaseline.collect(port,StockSettingsBaseline.Viewport.SYSTEM_ACTIONS));}
    @Test void factoryResetAndUpdateAreOnlyReadSymbolsAndNoConfirmActionIsOffered()throws Exception{assertEquals(2,StockSettingsBaseline.Control.values().length);assertEquals(2,StockSettingsBaseline.Viewport.values().length);assertEquals("com.qinggan.app.setting:id/btn_factory_reset",StockSettingsBaseline.Control.FACTORY_RESET.resource());assertEquals("中文（简体）",StockSettingsBaseline.Control.LANGUAGE_CHINESE.caption());}
    @Test void originalSelectedChineseRadioProvidesActualSourceBoundCjkMeasurements()throws Exception{
        var tree=ui(true);var surface=tree.getAsJsonArray("screens").get(0).getAsJsonObject();var nodes=surface.getAsJsonArray("nodes");nodes.remove(3);nodes.remove(2);
        var group=node("com.pateo.material.widgets.QGRadioGroup@111a","w:android.widget.LinearLayout@2222","com.pateo.material.widgets.QGRadioGroup",SystemLanguagePicker.GROUP,"",515,100,600,70);nodes.add(group);
        var en=node("com.pateo.material.widgets.BoldRadioButton@111b","w:com.pateo.material.widgets.QGRadioGroup@111a","com.pateo.material.widgets.BoldRadioButton",SystemLanguagePicker.OWNER+":id/display_language_radio_english","English",815,100,300,70);en.addProperty("checkable",true);en.addProperty("checked",false);en.addProperty("enabled",true);nodes.add(en);
        var zh=node("com.pateo.material.widgets.BoldRadioButton@111c","w:com.pateo.material.widgets.QGRadioGroup@111a","com.pateo.material.widgets.BoldRadioButton",StockSettingsBaseline.Control.LANGUAGE_CHINESE.resource(),"中文（简体）",515,100,300,70);zh.addProperty("checkable",true);zh.addProperty("checked",true);zh.addProperty("enabled",true);nodes.add(zh);surface.getAsJsonArray("windows").get(0).getAsJsonObject().addProperty("node_count",5);
        var measured=StockSettingsBaseline.measure(tree,authority(),StockSettingsBaseline.Viewport.LANGUAGE_RADIO);assertEquals(1,measured.getAsJsonArray("rows").size());assertEquals("中文（简体）",measured.getAsJsonArray("rows").get(0).getAsJsonObject().get("value").getAsString());zh.addProperty("checked",false);en.addProperty("checked",true);assertThrows(IOException.class,()->StockSettingsBaseline.measure(tree,authority(),StockSettingsBaseline.Viewport.LANGUAGE_RADIO));
    }

    @Test void stockEnglishCollectionUsesOriginalEnglishCaptionAndNeverAdmitsGeneratedRussian()throws Exception {
        var tree=ui(true);tree.getAsJsonArray("screens").get(0).getAsJsonObject().getAsJsonArray("nodes").get(2).getAsJsonObject().addProperty("text","Factory reset");
        assertEquals("Factory reset",StockSettingsBaseline.measure(tree,authority(),StockSettingsBaseline.Viewport.SYSTEM_ACTIONS,"en").getAsJsonArray("rows").get(0).getAsJsonObject().get("actualText").getAsString());
        assertThrows(IOException.class,()->StockSettingsBaseline.measure(tree,authority(),StockSettingsBaseline.Viewport.SYSTEM_ACTIONS,"zh"));assertThrows(IOException.class,()->StockSettingsBaseline.measure(tree,authority(),StockSettingsBaseline.Viewport.SYSTEM_ACTIONS,"ru"));
    }

}
