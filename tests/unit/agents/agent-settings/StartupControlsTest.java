package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

class StartupControlsTest {
    private JsonObject surface() throws IOException {
        JsonObject result = RunFiles.parse("{\"windows\":[{\"id\":\"window\",\"package\":\"com.qinggan.app.setting\",\"type\":1}],\"nodes\":[{\"id\":\"group\",\"window_id\":\"window\",\"package\":\"com.qinggan.app.setting\",\"present\":true,\"visible\":true}]}");
        for (String caption : new String[]{"Normal", "Hidden", "Do not start"}) {
            JsonObject node = RunFiles.parse("{\"id\":\"" + caption + "\",\"parent\":\"group\",\"window_id\":\"window\",\"package\":\"com.qinggan.app.setting\",\"present\":true,\"visible\":true,\"enabled\":true,\"checkable\":true,\"checked\":false}");
            node.addProperty("text", caption); node.addProperty("checked", caption.equals("Normal")); result.getAsJsonArray("nodes").add(node);
        }
        return result;
    }
    @Test void readsOnlyTheObservedUniqueSiblingRadioGroup() throws Exception {
        var result = StartupControls.read(surface()); assertEquals("interface", result.selected());
        assertEquals("Do not start", result.targets().get("none"));
        assertThrows(UnsupportedOperationException.class, () -> result.targets().clear());
    }
    @Test void supportsActualChineseAndRussianCaptionsWithoutAssumingEnglish() throws Exception {
        for (String[] captions : new String[][]{{"正常启动", "隐藏启动", "不启动"}, {"Нормально", "Скрытно", "Не запускать"}}) {
            var ui = surface(); for (int i = 0; i < 3; ++i) ui.getAsJsonArray("nodes").get(i + 1).getAsJsonObject().addProperty("text", captions[i]);
            assertEquals("interface", StartupControls.read(ui).selected());
        }
    }
    @Test void neverInfersUncheckedFromMissingOrNonbooleanState() throws Exception {
        for (String state : new String[]{"missing", "false", "null"}) {
            var ui = surface(); var node = ui.getAsJsonArray("nodes").get(2).getAsJsonObject();
            if (state.equals("missing")) node.remove("checked"); else if (state.equals("null")) node.add("checked", com.google.gson.JsonNull.INSTANCE); else node.addProperty("checked", state);
            assertThrows(IOException.class, () -> StartupControls.read(ui));
        }
    }
    @Test void duplicateLabelsCrossWindowAndMissingGroupCannotAcquireAuthority() throws Exception {
        var duplicate = surface(); duplicate.getAsJsonArray("nodes").add(duplicate.getAsJsonArray("nodes").get(1).deepCopy());
        assertThrows(IOException.class, () -> StartupControls.read(duplicate));
        for (String key : new String[]{"parent", "window_id", "package"}) {
            var ui = surface(); ui.getAsJsonArray("nodes").get(2).getAsJsonObject().addProperty(key, "foreign");
            assertThrows(IOException.class, () -> StartupControls.read(ui));
        }
        var missing = surface(); missing.getAsJsonArray("nodes").remove(0); assertThrows(IOException.class, () -> StartupControls.read(missing));
    }
    @Test void transitionWithoutOneSelectedRadioOrWithTwoSelectedNeverPasses() throws Exception {
        var ui = surface(); ui.getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("checked", false);
        assertThrows(UiEvidence.Unavailable.class, () -> StartupControls.read(ui));
        ui.getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("checked", true);
        ui.getAsJsonArray("nodes").get(2).getAsJsonObject().addProperty("checked", true);
        assertThrows(IOException.class, () -> StartupControls.read(ui));
    }
}
