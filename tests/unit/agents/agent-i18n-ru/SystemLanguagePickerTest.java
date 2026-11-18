package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

class SystemLanguagePickerTest {
    private JsonObject surface() throws Exception {
        return RunFiles.parse("{\"windows\":[{\"id\":\"w\",\"package\":\"com.qinggan.app.setting\",\"type\":1}],\"nodes\":["
            + "{\"id\":\"g\",\"window_id\":\"w\",\"package\":\"com.qinggan.app.setting\",\"resource_id\":\"com.qinggan.app.setting:id/display_language_radio_group\",\"parent\":null,\"visible\":true},"
            + "{\"id\":\"en\",\"window_id\":\"w\",\"package\":\"com.qinggan.app.setting\",\"resource_id\":\"com.qinggan.app.setting:id/display_language_radio_english\",\"parent\":\"g\",\"visible\":true,\"checkable\":true,\"checked\":true},"
            + "{\"id\":\"zh\",\"window_id\":\"w\",\"package\":\"com.qinggan.app.setting\",\"resource_id\":\"com.qinggan.app.setting:id/display_language_radio_chinese\",\"parent\":\"g\",\"visible\":true,\"checkable\":true,\"checked\":false}]}");
    }
    @Test void actualGroupDefinesTheOriginalSelectionAndOppositeTarget() throws Exception {
        JsonObject ui = surface(); var picker = SystemLanguagePicker.read(ui);
        assertEquals("en", picker.language()); assertEquals("zh", picker.target("zh"));
        ui.getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("checked", false);
        ui.getAsJsonArray("nodes").get(2).getAsJsonObject().addProperty("checked", true);
        assertEquals("zh", SystemLanguagePicker.read(ui).language());
    }
    @Test void generatedRussianContributionIsASeparateObservedChildAndNeverAnEnglishAlias()throws Exception {
        JsonObject ui=surface();var nodes=ui.getAsJsonArray("nodes");nodes.get(1).getAsJsonObject().addProperty("checked",false);
        nodes.add(RunFiles.parse("{\"id\":\"generated-ru\",\"window_id\":\"w\",\"package\":\"com.qinggan.app.setting\",\"resource_id\":\"\",\"text\":\"Русский\",\"parent\":\"g\",\"visible\":true,\"enabled\":true,\"checkable\":true,\"checked\":true}"));
        var selected=SystemLanguagePicker.read(ui);assertEquals("ru",selected.language());assertEquals("generated-ru",selected.target("ru"));assertEquals("en",selected.target("en"));
        nodes.get(3).getAsJsonObject().addProperty("resource_id","com.qinggan.app.setting:id/display_language_radio_russian");assertThrows(IOException.class,()->SystemLanguagePicker.read(ui));
    }
    @Test void russianTextOnAnOemResourceCannotBecomeAGeneratedContribution()throws Exception {
        JsonObject ui=surface();ui.getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("text","Русский");ui.getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("enabled",true);
        assertThrows(IOException.class,()->SystemLanguagePicker.read(ui));
        var nativeRadio=ui.getAsJsonArray("nodes").get(2).getAsJsonObject();nativeRadio.addProperty("resource_id","com.qinggan.app.setting:id/display_language_radio_russian");nativeRadio.addProperty("visible",false);assertThrows(IOException.class,()->SystemLanguagePicker.read(ui));
    }
    @Test void missingSelectionIsAWaitableTransitionButConflictingSelectionsAreAnError() throws Exception {
        JsonObject ui = surface(); ui.getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("checked", false);
        assertThrows(SystemLanguagePicker.Unavailable.class, () -> SystemLanguagePicker.read(ui));
        ui.getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("checked", true);
        ui.getAsJsonArray("nodes").get(2).getAsJsonObject().addProperty("checked", true);
        IOException error = assertThrows(IOException.class, () -> SystemLanguagePicker.read(ui));
        assertFalse(error instanceof SystemLanguagePicker.Unavailable);
    }
    @Test void foreignWindowCannotSupplyTheLanguageGroupOrItsRadio() throws Exception {
        JsonObject ui = surface(); ui.getAsJsonArray("windows").get(0).getAsJsonObject().addProperty("package", "com.android.settings");
        assertThrows(IOException.class, () -> SystemLanguagePicker.read(ui));
        JsonObject moved = surface(); moved.getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("window_id", "foreign");
        assertThrows(IOException.class, () -> SystemLanguagePicker.read(moved));
    }
    @Test void cyclicAncestryIsNotASelectedLanguageAndCannotBeRetriedAsAValidControl() throws Exception {
        JsonObject ui = surface(); ui.getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("parent", "en");
        IOException error = assertThrows(IOException.class, () -> SystemLanguagePicker.read(ui));
        assertFalse(error instanceof SystemLanguagePicker.Unavailable);
    }
}
