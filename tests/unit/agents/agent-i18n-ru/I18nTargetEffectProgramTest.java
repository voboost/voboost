package ru.voboost.tests.agents;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.tests.AcceptancePlan;
import ru.voboost.tests.GateStatus;
import ru.voboost.tests.RunFiles;

class I18nTargetEffectProgramTest {
    @TempDir Path root;
    private Path source()throws Exception {
        JsonObject input=AgentI18nRuResourceReadbacksTest.input();
        var data=input.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("runtime").getAsJsonObject("data");
        var current=data.getAsJsonObject("i18nResourceReadbacks").deepCopy();current.addProperty("kind","current-owner-resource-readback");data.add("i18nCurrentReadbacks",current);
        Path file=root.resolve("runtime.json");RunFiles.write(file,input);return file;
    }
    @Test void exactCurrentOwnerStillBlocksWithoutDisabledAndRebootEffects()throws Exception {
        var key=new AcceptancePlan.Key("agent/i18n-ru","android:com.qinggan.app.setting");
        var result=I18nTargetEffectProgram.evaluate(key,DaemonRuntimeObservationTest.IDENTITY,source());
        assertEquals(GateStatus.BLOCKED,result.result().status());assertEquals("com.qinggan.app.setting",result.enabled().owner());
    }
    @Test void rejectsForeignTargetBooleanOnlyAndForeignEndpoint()throws Exception {
        Path file=source();
        assertThrows(IOException.class,()->I18nTargetEffectProgram.evaluate(new AcceptancePlan.Key("agent/i18n-ru","android:com.foreign"),DaemonRuntimeObservationTest.IDENTITY,file));
        assertThrows(IOException.class,()->I18nTargetEffectProgram.evaluate(new AcceptancePlan.Key("agent/phone","android:com.qinggan.app.setting"),DaemonRuntimeObservationTest.IDENTITY,file));
        JsonObject input=RunFiles.read(file);var data=input.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("runtime").getAsJsonObject("data");
        data.getAsJsonObject("i18nCurrentReadbacks").getAsJsonArray("owners").get(0).getAsJsonObject().getAsJsonObject("binding").addProperty("endpointEpoch","1");RunFiles.write(file,input);
        assertThrows(IOException.class,()->I18nTargetEffectProgram.evaluate(new AcceptancePlan.Key("agent/i18n-ru","android:com.qinggan.app.setting"),DaemonRuntimeObservationTest.IDENTITY,file));
        RunFiles.write(file,RunFiles.parse("{\"functionalAcceptance\":true}"));
        assertThrows(IOException.class,()->I18nTargetEffectProgram.evaluate(new AcceptancePlan.Key("agent/i18n-ru","android:com.qinggan.app.setting"),DaemonRuntimeObservationTest.IDENTITY,file));
    }
}
