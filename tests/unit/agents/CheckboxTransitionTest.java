package ru.voboost.tests.agents;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

class CheckboxTransitionTest {
    private static final String BOOT="11111111-1111-1111-1111-111111111111",SESSION="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static JsonObject catalog()throws Exception{return RunFiles.parse("{\"agents\":[{\"id\":\"i18n-ru\",\"status\":\"stable\",\"activation\":{\"type\":\"checkbox\"},\"title\":{\"en\":\"Russian\"}}]}");}
    private static JsonObject ui(boolean checked,String id)throws Exception{return RunFiles.parse("{\"format\":\"voyemu.ui.v1\",\"boot_id\":\""+BOOT+"\",\"profile\":\"a9\",\"session_id\":\""+SESSION+"\",\"android_pid\":123,\"observation_id\":\""+id+"\",\"source_instance\":\"source\",\"revision\":\"1\",\"screens\":[{\"screen\":\"primary\",\"status\":\"ok\",\"nodes\":[{\"id\":\"checkbox\",\"window_id\":\"window\",\"package\":\"com.qinggan.app.setting\",\"class\":\"android.widget.CheckBox\",\"text\":\"Russian\",\"visible\":true,\"present\":true,\"enabled\":true,\"clickable\":true,\"checkable\":true,\"checked\":"+checked+"}]}]}");}
    private static CheckboxTransition.Proof check(JsonObject before,JsonObject after,String agent)throws Exception{return CheckboxTransition.check(catalog(),agent,"en",BOOT,"a9",SESSION,123,before,after,false);}
    @Test void exactCatalogCheckboxMovesFromCheckedToUnchecked()throws Exception {assertEquals("i18n-ru",check(ui(true,"before"),ui(false,"after"),"i18n-ru").agent());}
    @Test void staleForeignAndUnchangedSnapshotsFail()throws Exception {
        assertThrows(IOException.class,()->check(ui(true,"same"),ui(false,"same"),"i18n-ru"));
        assertThrows(IOException.class,()->check(ui(true,"before"),ui(true,"after"),"i18n-ru"));
        assertThrows(IOException.class,()->check(ui(true,"before"),ui(false,"after"),"keyboard-en"));
        var foreign=ui(false,"after");foreign.addProperty("android_pid",124);assertThrows(IOException.class,()->check(ui(true,"before"),foreign,"i18n-ru"));
    }
}
