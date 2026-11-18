package ru.voboost.tests.agents;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.AcceptancePlan;

class I18nDisableRestartTest {
    private static final String OWNER="com.qinggan.app.setting",BOOT=DaemonRuntimeObservationTest.IDENTITY.boot();
    private static final AcceptancePlan.Key KEY=new AcceptancePlan.Key("agent/i18n-ru","android:"+OWNER);
    private static String processes(int pid,int birth){return "current-process-identities:\nschemaVersion=1\nbootId="+BOOT+"\nenforcing=Enforcing\nprocess=voboost-inject pid=12 starttime=22 argv0=voboost-inject\nprocess="+OWNER+" pid="+pid+" starttime="+birth+" argv0="+OWNER+"\nbootIdAfter="+BOOT+"\nenforcingAfter=Enforcing\nbootCompletedAfter=1\ncurrent-process-identities-end=complete\n";}
    private static JsonObject enabled()throws Exception {JsonObject input=AgentI18nRuResourceReadbacksTest.input();var data=input.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("runtime").getAsJsonObject("data");var readback=data.getAsJsonObject("i18nResourceReadbacks").deepCopy();readback.addProperty("kind","current-owner-resource-readback");data.add("i18nCurrentReadbacks",readback);return input;}
    private static JsonObject disabled(JsonObject input){var out=input.deepCopy();var files=out.getAsJsonObject("data").getAsJsonObject("files");files.getAsJsonObject("config").addProperty("revision","changed");files.getAsJsonObject("config").getAsJsonObject("data").getAsJsonObject("agents").getAsJsonObject("i18n-ru").addProperty("enabled",false);files.getAsJsonObject("runtime").getAsJsonObject("data").getAsJsonArray("injections").forEach(row->row.getAsJsonObject().addProperty("state","disabled"));return out;}
    @Test void currentEndpointAndNewProcessLifetimeAreVerified()throws Exception {var before=enabled();var result=I18nDisableRestart.check(KEY,BOOT,AgentI18nRuResourceReadbacksTest.read(before),AgentI18nRuResourceReadbacksTest.read(disabled(before)),processes(123,456),processes(124,457));assertEquals(124,result.after().pid());}
    @Test void desiredAloneCannotProveStopOrForeignTarget()throws Exception {var before=enabled();var after=disabled(before);var first=AgentI18nRuResourceReadbacksTest.read(before);var second=AgentI18nRuResourceReadbacksTest.read(after);
        assertThrows(IOException.class,()->I18nDisableRestart.check(KEY,BOOT,first,second,processes(123,456),processes(123,456)));
        assertThrows(IOException.class,()->I18nDisableRestart.check(KEY,BOOT,first,second,processes(999,456),processes(124,457)));
        assertThrows(IOException.class,()->I18nDisableRestart.check(new AcceptancePlan.Key("agent/i18n-ru","android:com.foreign"),BOOT,first,second,processes(123,456),processes(124,457)));
        assertThrows(IOException.class,()->I18nDisableRestart.check(KEY,BOOT,first,first,processes(123,456),processes(124,457)));
        assertThrows(IOException.class,()->I18nDisableRestart.check(KEY,BOOT,first,second,processes(123,456),processes(124,457).replace("Enforcing","Permissive")));
    }
}
