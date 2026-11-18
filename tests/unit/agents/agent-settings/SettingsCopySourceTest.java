package ru.voboost.tests.ui;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

class SettingsCopySourceTest {
    private static final Path FIXTURE=Path.of("tests/common/agents/agent-settings/settings-copy/1.0.0-beta1.json");
    private static final String RELEASE="1.0.0-beta1", CATALOG="selected-catalog-identity";
    private static SettingsCopySource.Proof decode(JsonObject value)throws IOException {return SettingsCopySource.decode(value,FIXTURE,RELEASE,CATALOG);}
    @Test void readsVersionedOwningCopyAndBindsSelectedRuntimeCatalog()throws Exception {
        var result=SettingsCopySource.readExpected(FIXTURE,RELEASE,CATALOG);
        assertFalse(result.startupDescription("hidden","zh").isBlank());
        assertFalse(result.startupDescription("none","en").isBlank());
        assertEquals(CATALOG,result.catalogIdentity());result.requireCatalogIdentity(CATALOG);
        assertThrows(IOException.class,()->result.requireCatalogIdentity("other-catalog"));
        assertThrows(IOException.class,()->result.requireCatalogIdentity(null));
        assertThrows(IOException.class,()->result.startupDescription("unknown","en"));
        assertThrows(IOException.class,()->SettingsCopySource.readExpected(FIXTURE,"1.0.0-beta2",CATALOG));
        assertThrows(IOException.class,()->SettingsCopySource.readExpected(FIXTURE,RELEASE,""));
    }
    @Test void rejectsMissingLanguageAndUnknownDtoFields()throws Exception {
        JsonObject missing=RunFiles.read(FIXTURE);missing.getAsJsonObject("copy").getAsJsonObject("languages").remove("zh");assertThrows(IOException.class,()->decode(missing));
        JsonObject extra=RunFiles.read(FIXTURE);extra.getAsJsonObject("copy").getAsJsonObject("languages").getAsJsonObject("en").addProperty("unexpected",true);assertThrows(IOException.class,()->decode(extra));
        JsonObject geometry=RunFiles.read(FIXTURE);geometry.getAsJsonObject("copy").getAsJsonObject("geometry").remove("RADIO_HEIGHT");assertThrows(IOException.class,()->decode(geometry));
        JsonObject modes=RunFiles.read(FIXTURE);modes.getAsJsonObject("copy").getAsJsonObject("languages").getAsJsonObject("ru").getAsJsonArray("startup").get(2).getAsJsonObject().addProperty("value","hidden");assertThrows(IOException.class,()->decode(modes));
    }
}
