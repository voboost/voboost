package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

/** Builds public requests only. No SDK frame, GUI or emulator is captured. */
class KeyboardCaptureTest {
    private static final VoyemuClient.Session SESSION=new VoyemuClient.Session("a".repeat(32),"b".repeat(32));
    static JsonObject observation()throws Exception {
        JsonObject tree=LauncherDragTest.observation(),surface=NavigationInputTest.surface();NavigationInputTest.editor(surface,"");
        surface.addProperty("screen","primary");surface.addProperty("width",1920);surface.addProperty("height",720);surface.addProperty("sdk_display_id",0);surface.addProperty("display_id",0);
        JsonObject keyboard=new JsonObject();keyboard.addProperty("id","canvas");keyboard.addProperty("window_id","ime");keyboard.addProperty("package","com.qinggan.app.qgime");
        keyboard.addProperty("resource_id","com.qinggan.app.qgime:id/alpha_view1");keyboard.addProperty("class","android.inputmethodservice.KeyboardView");keyboard.addProperty("visible",true);keyboard.addProperty("enabled",true);
        keyboard.addProperty("clickable",false);keyboard.addProperty("actionable_candidate",false);JsonObject bounds=RunFiles.parse("{\"x\":145,\"y\":440,\"width\":1775,\"height\":280}");keyboard.add("bounds",bounds);keyboard.add("clipped_bounds",bounds.deepCopy());surface.getAsJsonArray("nodes").add(keyboard);
        surface.getAsJsonArray("windows").get(0).getAsJsonObject().addProperty("title","Navi");
        JsonObject ime=surface.getAsJsonArray("windows").get(1).getAsJsonObject();ime.addProperty("title","IME");ime.add("bounds",bounds.deepCopy());ime.addProperty("active",false);ime.addProperty("input_focused",false);ime.addProperty("layer",2);
        surface.getAsJsonArray("windows").get(0).getAsJsonObject().addProperty("layer",1);tree.getAsJsonArray("screens").set(0,surface);return tree;
    }
    private static UiTarget canvas(JsonObject ui)throws IOException{return UiTarget.selectKeyboardCanvas(ui,"profile",SESSION);}
    @Test void capturesOnlyObservedPrimaryKeyboardRoiWithCurrentSourceAndBoundedSdkAge()throws Exception {
        VoyemuClient client=new VoyemuClient((bytes,timeout)->{throw new AssertionError("builder must not execute capture");});
        UiTarget canvas=canvas(observation());JsonObject request=RunFiles.parse(client.captureKeyboard("c".repeat(32),"profile",SESSION,canvas,Path.of("/project/.local/build/tmp/keyboard-fixture"),500).json());
        assertEquals("session.screenshot",request.get("op").getAsString());assertEquals("[\"primary\"]",request.get("screens").toString());
        assertEquals("com.qinggan.app.qgime:id/alpha_view1",request.getAsJsonObject("selector").get("resource_id").getAsString());
        assertEquals(500,request.get("max_age_ms").getAsInt());assertEquals(10000,request.get("timeout_ms").getAsInt());assertFalse(request.has("rect"));
        assertThrows(IllegalArgumentException.class,()->client.tap("d".repeat(32),"profile",SESSION,canvas));
        assertThrows(IllegalArgumentException.class,()->client.pageLauncher("d".repeat(32),"profile",SESSION,canvas,true));
    }
    @Test void unsupportedEditorForeignCanvasAndPartialGeometryDoNotCreateCaptureAuthority()throws Exception {
        JsonObject unsupported=observation();unsupported.getAsJsonArray("screens").get(0).getAsJsonObject().getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("editor_status","unsupported");assertThrows(IOException.class,()->canvas(unsupported));
        JsonObject foreign=observation();foreign.getAsJsonArray("screens").get(0).getAsJsonObject().getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("package","foreign.app");assertThrows(IOException.class,()->canvas(foreign));
        JsonObject partial=observation();partial.getAsJsonArray("screens").get(0).getAsJsonObject().addProperty("complete",false);assertThrows(IOException.class,()->canvas(partial));
        JsonObject clipped=observation();clipped.getAsJsonArray("screens").get(0).getAsJsonObject().getAsJsonArray("nodes").get(1).getAsJsonObject().getAsJsonObject("clipped_bounds").addProperty("width",1700);assertThrows(IOException.class,()->canvas(clipped));
    }
    @Test void wrongSessionRelativePathAndUnboundedFreshnessNeverReachTransport()throws Exception {
        VoyemuClient client=new VoyemuClient((bytes,timeout)->{throw new AssertionError("builder must not execute capture");});UiTarget canvas=canvas(observation());
        assertThrows(IllegalArgumentException.class,()->client.captureKeyboard("c".repeat(32),"foreign",SESSION,canvas,Path.of("/project/new"),500));
        assertThrows(IllegalArgumentException.class,()->client.captureKeyboard("c".repeat(32),"profile",SESSION,canvas,Path.of("relative"),500));
        assertThrows(IllegalArgumentException.class,()->client.captureKeyboard("c".repeat(32),"profile",SESSION,canvas,Path.of("/project/new"),501));
    }
}
