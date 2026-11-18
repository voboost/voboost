package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

class SettingsMeasurementsTest {
    private static JsonObject surface(int number)throws Exception {
        JsonObject ui=RunFiles.parse("{\"complete\":true,\"status\":\"ok\",\"windows\":[{\"id\":\"w\",\"package\":\"com.qinggan.app.setting\",\"type\":1,\"read_complete\":true}],\"nodes\":[]}");
        String[] classes=number==20?new String[]{"checkbox.CheckboxPrimitive","radio.RadioPrimitive","section.Section"}:new String[]{"button.ButtonPrimitive"};
        for(String klass:classes){int width=klass.startsWith("checkbox")?83:klass.startsWith("section")?1364:300,height=klass.startsWith("checkbox")?42:klass.startsWith("section")?500:70;ui.getAsJsonArray("nodes").add(RunFiles.parse("{\"id\":\""+klass+"\",\"window_id\":\"w\",\"package\":\"com.qinggan.app.setting\",\"class\":\"ru.voboost.components."+klass+"\",\"present\":true,\"visible\":true,\"layout\":{\"measured_width\":"+width+",\"measured_height\":"+height+",\"min_width\":0,\"min_height\":0,\"padding_left\":0,\"padding_top\":0,\"padding_right\":0,\"padding_bottom\":0,\"scroll_x\":0,\"scroll_y\":0,\"params_type\":\"basic\",\"layout_width\":-2,\"layout_height\":-2},\"text_style\":{\"text_size_px\":null,\"text_scale_x\":null,\"letter_spacing\":null}}"));}
        if(number==20){var label=ui.getAsJsonArray("nodes").get(2).getAsJsonObject().deepCopy();label.addProperty("id","section-label");label.addProperty("class","android.widget.TextView");label.addProperty("parent","section.Section");label.getAsJsonObject("layout").addProperty("measured_width",200);label.getAsJsonObject("layout").addProperty("measured_height",40);label.add("text_style",RunFiles.parse("{\"text_size_px\":32,\"text_scale_x\":1,\"letter_spacing\":0}"));ui.getAsJsonArray("nodes").add(label);}
        ui.addProperty("scope","all_attached");var window=ui.getAsJsonArray("windows").get(0).getAsJsonObject();window.addProperty("root_id","decor");window.addProperty("node_count",ui.getAsJsonArray("nodes").size()+1);
        for(var value:ui.getAsJsonArray("nodes")){var node=value.getAsJsonObject();node.addProperty("bridge_version",1);if(!node.has("parent"))node.addProperty("parent","decor");}
        var root=RunFiles.parse("{\"id\":\"decor\",\"window_id\":\"w\",\"class\":\"android.widget.FrameLayout\",\"package\":\"com.qinggan.app.setting\",\"present\":true,\"visible\":true,\"bridge_version\":1}");ui.getAsJsonArray("nodes").add(root);
        return ui;

    }
    @Test void explicitCompleteOwnerWindowIsMeasuredEvenWhenForeignWallpaperIsOpaque()throws Exception {
        var ui=surface(20);ui.addProperty("scope","all_attached");ui.addProperty("complete",false);ui.addProperty("status","partial");
        var result=SettingsMeasurements.read(ui,20);assertTrue(result.get("viewMetricsVerified").getAsBoolean());assertFalse(result.get("visualAcceptance").getAsBoolean());
    }
    @Test void actualPixelPrimitiveMetricsAreRetainedWithoutPretendingToObserveCanvasInk()throws Exception {var result=SettingsMeasurements.read(surface(20),20);assertTrue(result.get("viewMetricsVerified").getAsBoolean());assertEquals(4,result.getAsJsonArray("rows").size());assertFalse(result.get("visualAcceptance").getAsBoolean());}
    @Test void zeroPublicViewPaddingNeverBecomesThePrivateCanvasButtonPadding()throws Exception {var result=SettingsMeasurements.read(surface(21),21);assertEquals(0,result.getAsJsonArray("rows").get(0).getAsJsonObject().getAsJsonObject("layout").get("padding_left").getAsInt());assertFalse(result.get("canvasTextPaddingVerified").getAsBoolean());}
    @Test void missingNullOrStringMetricsNeverDefaultToZero()throws Exception {for(String mutation:new String[]{"missing","null","string"}){var ui=surface(20);var layout=ui.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("layout");switch(mutation){case "missing"->layout.remove("padding_left");case "null"->layout.add("padding_left",com.google.gson.JsonNull.INSTANCE);case "string"->layout.addProperty("padding_left","0");}assertThrows(IOException.class,()->SettingsMeasurements.read(ui,20));}}
    @Test void visibleTextMetricsAndAncestorVisibilityAreObservedRatherThanInferred()throws Exception {var ui=surface(20);var label=ui.getAsJsonArray("nodes").get(3).getAsJsonObject();label.getAsJsonObject("text_style").add("text_size_px",com.google.gson.JsonNull.INSTANCE);assertThrows(IOException.class,()->SettingsMeasurements.read(ui,20));var hiddenUi=surface(20);hiddenUi.getAsJsonArray("nodes").get(2).getAsJsonObject().addProperty("visible",false);JsonObject hidden=hiddenUi;assertThrows(IOException.class,()->SettingsMeasurements.read(hidden,20));}
    @Test void wrongPixelSizeForeignOwnerAndDuplicateIdentityCannotMeasureTheContract()throws Exception {for(String mutation:new String[]{"size","owner","duplicate"}){var ui=surface(20);var node=ui.getAsJsonArray("nodes").get(0).getAsJsonObject();switch(mutation){case "size"->node.getAsJsonObject("layout").addProperty("measured_width",84);case "owner"->node.addProperty("package","foreign");case "duplicate"->ui.getAsJsonArray("nodes").add(node.deepCopy());}assertThrows(IOException.class,()->SettingsMeasurements.read(ui,20));}}
    private static JsonObject observation()throws Exception {
        var result=RunFiles.parse("{\"format\":\"voyemu.ui.v1\",\"profile\":\"profile\",\"session_id\":\"session\",\"boot_id\":\"boot\",\"observation_id\":\"fresh-observation\",\"source_instance\":\"observer\",\"source_clock_domain\":\"android.uptime\",\"started_uptime_ms\":\"100\",\"completed_uptime_ms\":\"120\",\"screens\":[]}");
        var primary=surface(20);primary.addProperty("screen","primary");result.getAsJsonArray("screens").add(primary);return result;
    }
    @Test void realOwnerWindowProjectionHasExplicitScopeWithoutClaimingGlobalOrVisualCoverage()throws Exception {
        var input=observation();var primary=input.getAsJsonArray("screens").get(0).getAsJsonObject();primary.addProperty("complete",false);primary.addProperty("status","partial");primary.getAsJsonArray("windows").add(RunFiles.parse("{\"id\":\"wallpaper\",\"package\":\"foreign\",\"type\":3,\"read_complete\":false}"));
        var result=SettingsMeasurements.readObservation(input,"profile","session","boot",20);assertEquals("complete_settings_application_window",result.get("measurementScope").getAsString());assertFalse(result.get("sourceSurfaceComplete").getAsBoolean());assertFalse(result.get("visualAcceptance").getAsBoolean());assertEquals("partial",primary.get("status").getAsString());
    }
    @Test void foreignBootSessionScopeAndSourceClocksCannotSatisfyARealObservation()throws Exception {
        for(String mutation:new String[]{"boot_id","session_id","profile","format","source_instance","source_clock_domain","reversed","scope","accessibility"}){
            var input=observation();switch(mutation){case "reversed"->input.addProperty("completed_uptime_ms","99");case "scope"->input.getAsJsonArray("screens").get(0).getAsJsonObject().addProperty("scope","api28_default_display");case "accessibility"->input.getAsJsonArray("screens").get(0).getAsJsonObject().getAsJsonArray("nodes").get(0).getAsJsonObject().remove("bridge_version");default->input.addProperty(mutation,"");}
            assertThrows(IOException.class,()->SettingsMeasurements.readObservation(input,"profile","session","boot",20),mutation);
        }
    }
    @Test void partialOwnerRootCountBridgeAndAncestryCannotBeLaunderedAsComplete()throws Exception {
        for(String mutation:new String[]{"incomplete","count","root","parent","cycle","duplicateWindow","anotherOwner","foreignOwner","disconnectedRoot"}){
            var ui=surface(20);var window=ui.getAsJsonArray("windows").get(0).getAsJsonObject();var node=ui.getAsJsonArray("nodes").get(0).getAsJsonObject();
            switch(mutation){case "incomplete"->window.addProperty("read_complete",false);case "count"->window.addProperty("node_count",50);case "root"->window.addProperty("root_id","absent");case "parent"->node.addProperty("parent","absent");case "cycle"->node.addProperty("parent",node.get("id").getAsString());case "duplicateWindow"->ui.getAsJsonArray("windows").add(window.deepCopy());case "anotherOwner"->{var second=window.deepCopy();second.addProperty("id","another");ui.getAsJsonArray("windows").add(second);}case "disconnectedRoot"->node.remove("parent");case "foreignOwner"->node.addProperty("package","foreign");}
            assertThrows(IOException.class,()->SettingsMeasurements.read(ui,20),mutation);
        }
    }

}
