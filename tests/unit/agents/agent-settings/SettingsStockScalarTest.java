package ru.voboost.tests.agents;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

class SettingsStockScalarTest {
    private static final String BOOT="11111111-1111-1111-1111-111111111111",SESSION="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static JsonObject ui(String caption,String id)throws Exception{return RunFiles.parse("{\"format\":\"voyemu.ui.v1\",\"boot_id\":\""+BOOT+"\",\"profile\":\"a9\",\"session_id\":\""+SESSION+"\",\"android_pid\":123,\"observation_id\":\""+id+"\",\"source_instance\":\"source\",\"revision\":\"1\",\"source_clock_domain\":\"android.uptime\",\"started_uptime_ms\":1,\"completed_uptime_ms\":2,\"screens\":[{\"screen\":\"primary\",\"status\":\"ok\",\"nodes\":[{\"id\":\"reset\",\"window_id\":\"window\",\"resource_id\":\"com.qinggan.app.setting:id/btn_factory_reset\",\"package\":\"com.qinggan.app.setting\",\"text\":\""+caption+"\",\"visible\":true,\"present\":true,\"enabled\":true}]}]}");}
    private static String processes(){return "current-process-identities:\nschemaVersion=1\nbootId="+BOOT+"\nenforcing=Enforcing\nprocess=voboost-inject pid=12 starttime=22 argv0=voboost-inject\nprocess=com.qinggan.app.setting pid=20 starttime=30 argv0=com.qinggan.app.setting\nbootIdAfter="+BOOT+"\nenforcingAfter=Enforcing\nbootCompletedAfter=1\ncurrent-process-identities-end=complete\n";}
    private static SettingsStockScalar.Sample sample(JsonObject ui,String locale)throws Exception{return SettingsStockScalar.read(ui,BOOT,"a9",SESSION,123,"locale-observation-v1\nbootId="+BOOT+"\nconfig: "+locale+"-rUS\n",processes());}
    @Test void originalAndDisabledSameLocaleVisibleScalarCompare()throws Exception {SettingsStockScalar.requireStockRestored(sample(ui("Factory reset","one"),"en"),sample(ui("Factory reset","two"),"en"));}
    @Test void differentValueLocaleStaleAndForeignOwnerFail()throws Exception {
        var stock=sample(ui("Factory reset","one"),"en");
        assertThrows(IOException.class,()->SettingsStockScalar.requireStockRestored(stock,sample(ui("Reset","two"),"en")));
        assertThrows(IOException.class,()->SettingsStockScalar.requireStockRestored(stock,sample(ui("Factory reset","two"),"zh")));
        assertThrows(IOException.class,()->SettingsStockScalar.requireStockRestored(stock,sample(ui("Factory reset","one"),"en")));
        var foreign=ui("Factory reset","two");foreign.getAsJsonArray("screens").get(0).getAsJsonObject().getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("package","foreign");assertThrows(IOException.class,()->sample(foreign,"en"));
    }
}
