package ru.voboost.tests;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TargetOwnerContractTest {
    private static final String RUN = "12345678-1234-1234-1234-123456789abc";
    private static final AcceptancePlan.Key KEY = new AcceptancePlan.Key("agent/media-popup-external-apps", "android:com.qinggan.app.islandapp");
    @TempDir Path dir;
    private TargetInventory.CrossCheck inventory(String rows, String packages) throws Exception {
        Path primary = Files.writeString(dir.resolve("primary"), "bootId=" + RUN + "\npackage-inventory-v1\n" + rows + "end\n");
        Path system = Files.writeString(dir.resolve("system"), "bootId=" + RUN + "\npackage-list-v1\n" + packages + "end\n");
        return TargetInventory.crossCheck(TargetInventory.capture(RUN, "session", "emulator-5554", RUN, primary), system);
    }
    @Test void allExplicitContractsHaveUniqueKeys() {
        var keys=TargetOwnerContract.keys();assertEquals(15,keys.size());assertEquals(keys.size(),new java.util.HashSet<>(keys).size());
        for(var key:keys)assertNotNull(TargetOwnerContract.owner(key));
    }
    @Test void explicitMappingsFollowAgentOwnedHostGuards() throws Exception {
        for (var pair : java.util.Map.of(
                KEY, "com.qinggan.app.islandapp",
                new AcceptancePlan.Key("agent/steering-media-app-switcher", "android:com.qinggan.keymanager.service"), "com.qinggan.keymanager.service",
                new AcceptancePlan.Key("agent/steering-media-session", "android:com.qinggan.keymanager.service"), "com.qinggan.keymanager.service").entrySet()) {
            var owner = TargetOwnerContract.owner(pair.getKey());
            assertEquals(pair.getValue(), owner.packageName());
            String source = Files.readString(Path.of("..").resolve(owner.source()));
            assertTrue(source.contains("HOST_PACKAGE = \"" + owner.packageName() + "\""));
            assertTrue(source.contains("getPackageName()"));
        }
        for (String path : java.util.List.of("mediaappswitcher/SteeringMediaAppSwitcherAgent.java", "mediasession/SteeringMediaSessionAgent.java")) {
            String source = Files.readString(Path.of("../voboost-agents/src/main/java/ru/voboost/agents/steering").resolve(path));
            assertTrue(source.contains("SteeringKeyManagerProfile.require("));
        }
    }
    @Test void outingMappingsRequireExactProcessAndContextPackage() throws Exception {
        Path directory=Path.of("../voboost-agents/src/main/java/ru/voboost/agents/vehicle/drive/outing");
        String probe=Files.readString(directory.resolve("VehicleDriveOutingRuntimeProbe.java"));
        assertTrue(probe.contains("expectedProcess.equals(observedProcess)"));assertTrue(probe.contains("expectedProcess.equals(observedPackage)"));
        for(String[] row:new String[][]{{"com.qinggan.app.vehicle","VehicleDriveOutingPolicyProfile.java","VehicleDriveOutingPolicyRuntime.java"},{"com.qinggan.app.vehiclesetting","VehicleDriveOutingPolicySettingsProfile.java","VehicleDriveOutingSettingsRuntime.java"}}) {
            var key=new AcceptancePlan.Key("agent/vehicle-drive-outing","android:"+row[0]);assertEquals(row[0],TargetOwnerContract.owner(key).packageName());
            assertTrue(Files.readString(directory.resolve(row[1])).contains("PROCESS = \""+row[0]+"\""));
            assertTrue(Files.readString(directory.resolve(row[2])).contains("process, host.getPackageName(), context.targetClassLoader()"));
        }
    }
    @Test void loginMappingRequiresExactProcessAndHostPackage() throws Exception {
        Path directory=Path.of("../voboost-agents/src/main/java/ru/voboost/agents/login/activationskip");
        String policy=Files.readString(directory.resolve("LoginActivationSkipProfilePolicy.java"));String agent=Files.readString(directory.resolve("LoginActivationSkipAgent.java"));
        var key=new AcceptancePlan.Key("agent/login-activation-skip","android:com.qinggan.app.login");
        assertEquals("com.qinggan.app.login",TargetOwnerContract.owner(key).packageName());
        assertTrue(policy.contains("TARGET_PROCESS = \"com.qinggan.app.login\""));assertTrue(policy.contains("TARGET_PROCESS.equals(process)"));
        assertTrue(agent.contains("isSupportedProcess(process)"));assertTrue(agent.contains("isSupportedProcess(hostPackage)"));
    }
    @Test void mediaMappingsFollowRuntimeSelectionAndHostGuard() throws Exception {
        Path directory=Path.of("../voboost-agents/src/main/java/ru/voboost/agents/media/apps");
        String agent=Files.readString(directory.resolve("MediaAppsAgent.java"));
        String base=Files.readString(directory.resolve("MediaAppsRuntimeBase.java"));
        assertTrue(base.contains("expectedHostPackage.equals(actualPackage)"));
        for(String[] row:new String[][]{{"com.qinggan.app.launcher","MediaAppsSourceRuntime.java"},{"com.qinggan.media","MediaAppsWindowRuntime.java"}}) {
            var key=new AcceptancePlan.Key("agent/media-apps","android:"+row[0]);var owner=TargetOwnerContract.owner(key);
            assertEquals(row[0],owner.packageName());assertTrue(agent.contains("\""+row[0]+"\".equals(process)"));
            String runtime=Files.readString(directory.resolve(row[1]));assertTrue(runtime.contains("HOST = \""+row[0]+"\""));assertTrue(runtime.contains("super(MediaAppsAgent.AGENT_ID, HOST)"));
        }
    }
    @Test void i18nMappingsFollowAdmissionAndHostPackageGuard() throws Exception {
        String source=Files.readString(Path.of("../voboost-agents/src/main/java/ru/voboost/agents/i18n/ru/I18nRuAdmission.java"));
        String agent=Files.readString(Path.of("../voboost-agents/src/main/java/ru/voboost/agents/i18n/ru/I18nRuAgent.java"));
        assertTrue(source.contains("targetPackage.equals(actualPackage)"));
        assertTrue(agent.contains("verifyTargetProcess(context.androidContext().getPackageName())"));
        for(String owner:java.util.List.of("com.qinggan.app.setting","com.qinggan.app.launcher","com.qinggan.app.vehiclesetting","com.qinggan.app.settingnewwork","com.qinggan.systemui","com.qinggan.bluetoothphone","com.qinggan.media")) {
            var key=new AcceptancePlan.Key("agent/i18n-ru","android:"+owner);
            assertEquals(owner,TargetOwnerContract.owner(key).packageName());assertTrue(source.contains("\""+owner+"\""));
            assertNull(TargetOwnerContract.owner(new AcceptancePlan.Key("agent/font-cyrillic",key.target())));
        }
        assertNull(TargetOwnerContract.owner(new AcceptancePlan.Key("agent/i18n-ru","android:com.example.unknown")));
    }
    @Test void onlyExactSourceOwnedKeyCanReportAbsentOwner() throws Exception {
        var absent = inventory("android\tandroid\n", "package:android\n");
        assertEquals(TargetOwnerContract.Decision.OWNER_ABSENT, TargetOwnerContract.evaluate(KEY, absent, RUN, "session", "emulator-5554", RUN));
        assertEquals(TargetOwnerContract.Decision.UNKNOWN, TargetOwnerContract.evaluate(new AcceptancePlan.Key("agent/font-cyrillic", KEY.target()), absent, RUN, "session", "emulator-5554", RUN));
        assertThrows(IOException.class, () -> TargetOwnerContract.evaluate(KEY, absent, RUN, "other", "emulator-5554", RUN));
        Files.writeString(absent.source(), "truncated");
        assertThrows(IOException.class, () -> TargetOwnerContract.evaluate(KEY, absent, RUN, "session", "emulator-5554", RUN));
    }
    @Test void presentOwnerOrProcessKeepsTargetRequired() throws Exception {
        var present = inventory("android\tandroid\ncom.qinggan.app.islandapp\tcom.qinggan.app.islandapp\n", "package:android\npackage:com.qinggan.app.islandapp\n");
        assertEquals(TargetOwnerContract.Decision.REQUIRED, TargetOwnerContract.evaluate(KEY, present, RUN, "session", "emulator-5554", RUN));
        var other = inventory("android\tandroid\ncom.example.app\tcom.qinggan.app.islandapp\n", "package:android\npackage:com.example.app\n");
        assertEquals(TargetOwnerContract.Decision.REQUIRED, TargetOwnerContract.evaluate(KEY, other, RUN, "session", "emulator-5554", RUN));
    }
}
