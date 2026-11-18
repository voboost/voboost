package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

class StartupObservationTest {
    private static final String BOOT = "11111111-1111-1111-1111-111111111111";
    private static String body(String processes) {
        return "u:r:su:s0\nbootCompleted=1\nbootId=" + BOOT + "\nenforcing=Enforcing\n\nprocesses:\nPID NAME\n\ncontexts:\nlegacy diagnostic\n\noem-target-processes:\ninventoryStatus=complete\n" + processes + "\ninventoryEnd=complete\nremaining fixed inspection\n";
    }
    @Test void noneAcceptsOnlyACompleteFullInventoryWithoutAnyVoboostProcess() throws Exception {
        StartupObservation.requireNoProcesses(body("  PID NAME\n 1 init\n 200 system_server"), BOOT);
        for (String name : new String[]{"voboost-daemon", "voboost-inject", "voboost-cluster-daemon", "ru.voboost", "ru.voboost:service", "/data/voboost/helper", "/data/voboost/generations/current/voboost-daemon"})
            assertThrows(IOException.class, () -> StartupObservation.requireNoProcesses(body("PID NAME\n1 init\n123 " + name), BOOT));
        for (String processes : new String[]{"", "PID NAME", "PID NAME\nerror", "PID NAME\n1 init\n1 second", "PID NAME\n9999999999 invalid"})
            assertThrows(IOException.class, () -> StartupObservation.requireNoProcesses(body(processes), BOOT));
    }
    @Test void unavailableFullInventoryCannotHideBehindAnEmptyLegacySection() {
        String unavailable = body("PID NAME\n1 init").replace("inventoryStatus=complete", "inventoryStatus=unavailable").replace("inventoryEnd=complete", "inventoryEnd=unavailable");
        assertThrows(IOException.class, () -> StartupObservation.requireNoProcesses(unavailable, BOOT));
        assertThrows(IOException.class, () -> StartupObservation.requireNoProcesses(body("PID NAME\n1 init").replace("inventoryStatus=complete", "inventoryStatus=unavailable"), BOOT));
    }
    @Test void staleIncompleteAndAmbiguousProcessFramesNeverProveAbsence() {
        String valid = body("PID NAME\n1 init");
        for (String invalid : new String[]{valid.replace("bootCompleted=1", "bootCompleted=0"), valid.replace("Enforcing", "Permissive"), valid.replace(BOOT, "22222222-2222-2222-2222-222222222222"), valid.replace("inventoryEnd=complete\n", ""), valid + valid, valid + "\noem-target-processes:\ninventoryStatus=complete\nPID NAME\n1 init\ninventoryEnd=complete\n"})
            assertThrows(IOException.class, () -> StartupObservation.requireNoProcesses(invalid, BOOT));
    }
    private static String identities(String rows) {
        return "current-process-identities:\nschemaVersion=1\nbootId=" + BOOT + "\nenforcing=Enforcing\n" + rows
            + "bootIdAfter=" + BOOT + "\nenforcingAfter=Enforcing\nbootCompletedAfter=1\ncurrent-process-identities-end=complete\n";
    }
    private static String noneOwner(String inventory) {
        return body(inventory)+identities("process=voboost-daemon pid=99 starttime=199 argv0=/data/voboost/voboost-daemon\nowner-executable pid=99 starttime=199 path=/data/voboost/voboost-daemon backing=8:9:0:0:700:1\nprocess=voboost-inject unavailable=missing-or-invalid\n");
    }
    @Test void nonePermitsOnlyTheOwnerBoundToExactExecutableAndFullInventoryPid()throws Exception {
        String valid=noneOwner("PID NAME\n1 init\n99 voboost-daemon\n100 com.qinggan.app.setting");
        assertEquals("99:199:/data/voboost/voboost-daemon:8:9:0:0:700:1",StartupObservation.requireNoneProcesses(valid,BOOT));
        for(String invalid:new String[]{valid.replace("99 voboost-daemon","98 voboost-daemon"),valid.replace("backing=8:9:0:0:700:1","backing=8:9:0:0:755:1"),valid.replace("backing=8:9:0:0:700:1","backing=8:9:0:0:700:2"),valid.replace("owner-executable pid=99","owner-executable pid=98"),valid.replace("owner-executable pid=99 starttime=199","owner-executable pid=99 starttime=200"),valid.replace("owner-executable pid=99 starttime=199 path=/data/voboost/voboost-daemon backing=8:9:0:0:700:1\n",""),valid.replace("argv0=/data/voboost/voboost-daemon","argv0=/data/voboost/generations/"+"a".repeat(64)+"/voboost-daemon")})
            assertThrows(IOException.class,()->StartupObservation.requireNoneProcesses(invalid,BOOT));
    }
    @Test void noneOwnerExceptionNeverAdmitsAnotherVoboostProcessOrAProducer()throws Exception {
        for(String name:new String[]{"voboost-inject","voboost-cluster-daemon","ru.voboost","ru.voboost:service","/data/voboost/helper","voboost-daemon"})
            assertThrows(IOException.class,()->StartupObservation.requireNoneProcesses(noneOwner("PID NAME\n1 init\n99 voboost-daemon\n101 "+name),BOOT));
        assertThrows(IOException.class,()->StartupObservation.requireNoneProcesses(noneOwner("PID NAME\n1 init\n99 voboost-daemon").replace("process=voboost-inject unavailable=missing-or-invalid","process=voboost-inject pid=101 starttime=201 argv0=/data/voboost/voboost-inject"),BOOT));
    }
    @Test void deferredSelectionRequiresUnchangedCurrentProcessLifetimes() throws Exception {
        String rows = "process=voboost-daemon pid=99 starttime=199 argv0=/data/voboost/voboost-daemon\nprocess=voboost-inject pid=100 starttime=200 argv0=/data/voboost/voboost-inject\nprocess=com.qinggan.app.setting pid=101 starttime=201 argv0=com.qinggan.app.setting\n";
        String before = identities(rows); StartupObservation.requireSameProcesses(before, before, BOOT);
        for (String after : new String[]{identities(rows.replace("starttime=201", "starttime=202")), identities(rows.replace("pid=101", "pid=102")), identities(rows.replace("process=com.qinggan.app.setting pid=101 starttime=201 argv0=com.qinggan.app.setting\n", "process=com.qinggan.app.setting unavailable=missing-or-invalid\n")), identities(rows + rows), before.replace("Enforcing", "Permissive")})
            assertThrows(IOException.class, () -> StartupObservation.requireSameProcesses(before, after, BOOT));
    }
    @Test void storedStartupDoesNotRequireOrPromoteAHistoricalDaemonPublication() throws Exception {
        var observation = RunFiles.parse("{\"schema\":2,\"source\":\"voboost\",\"status\":\"ready\",\"boot_id\":\"" + BOOT + "\",\"data\":{\"root\":\"present\",\"root_proof\":\"fixed-path-owner-mode-boot-fenced\",\"runtime_current\":false,\"files\":{\"config\":{\"status\":\"present\",\"current\":true,\"proof\":\"same-boot-retained-file\",\"revision\":\"current-input\",\"data\":{\"startup\":\"none\"}}}}}");
        assertEquals("none", StartupObservation.read(observation, BOOT).mode());
        assertThrows(IOException.class, () -> StartupObservation.read(observation, "foreign"));
        observation.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("config").addProperty("current", false);
        assertThrows(IOException.class, () -> StartupObservation.read(observation, BOOT));
    }    @Test void diagnosticTargetIdentityUsesExactBoundedObservedPidAndStarttime() throws Exception {
        String rows = "process=voboost-daemon pid=99 starttime=199 argv0=/data/voboost/voboost-daemon\nprocess=voboost-inject pid=100 starttime=200 argv0=/data/voboost/voboost-inject\nprocess=com.qinggan.app.setting pid=101 starttime=201 argv0=com.qinggan.app.setting\n";
        assertEquals(new StartupObservation.ProcessIdentity(101, "201", "com.qinggan.app.setting"), StartupObservation.identity(identities(rows), BOOT, "com.qinggan.app.setting"));
        assertThrows(IOException.class, () -> StartupObservation.identity(identities(rows), BOOT, "unknown"));
        for (String invalid : new String[]{rows.replace("pid=101", "pid=9999999999"), rows.replace("starttime=201", "starttime=18446744073709551616")})
            assertThrows(IOException.class, () -> StartupObservation.identity(identities(invalid), BOOT, "com.qinggan.app.setting"));
    }

@Test void exactVehicleInputIdentityDoesNotRequireAnUnrelatedSettingsProcess()throws Exception {
    String rows="process=voboost-inject pid=1703 starttime=139 argv0=/data/voboost/voboost-inject\n"
        +"process=com.qinggan.canbus.service pid=2509 starttime=757 argv0=com.qinggan.canbus.service\n"
        +"process=com.qinggan.app.setting unavailable=missing-or-invalid\n"
        +"process=com.qinggan.app.vehiclesetting pid=4761 starttime=1839 argv0=com.qinggan.app.vehiclesetting\n";
    String frame=identities(rows);
    assertEquals(new StartupObservation.ProcessIdentity(2509,"757","com.qinggan.canbus.service"),StartupObservation.identity(frame,BOOT,"com.qinggan.canbus.service"));
    assertEquals(new StartupObservation.ProcessIdentity(4761,"1839","com.qinggan.app.vehiclesetting"),StartupObservation.identity(frame,BOOT,"com.qinggan.app.vehiclesetting"));
    assertThrows(IOException.class,()->StartupObservation.identity(frame,BOOT,"com.qinggan.app.setting"));
    assertThrows(IOException.class,()->StartupObservation.identity(frame.replace("bootIdAfter="+BOOT,"bootIdAfter=foreign"),BOOT,"com.qinggan.canbus.service"));
    assertThrows(IOException.class,()->StartupObservation.identity(frame.replace("pid=2509","pid=9999999999"),BOOT,"com.qinggan.canbus.service"));
    assertThrows(IOException.class,()->StartupObservation.requireSameProcesses(frame,frame,BOOT));
}

}
