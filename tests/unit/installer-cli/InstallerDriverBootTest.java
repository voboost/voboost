package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.voyemu.OwnedSession;
import ru.voboost.voyemu.VoyemuClient;

/** Host fixture models API28's supported UI scope; it never runs a guest or an installation. */
class InstallerDriverBootTest {
    @TempDir Path directory;
    private static final String SESSION = "a".repeat(32), OWNER = "b".repeat(32), MANIFEST = "c".repeat(64);
    private static final String BEFORE = "12345678-1234-1234-1234-123456789abc", AFTER = "22345678-1234-1234-1234-123456789abc";
    private final List<String> scopes = new ArrayList<>();
    private boolean missingWindow;
    private boolean staleFirstReadiness;
    private boolean partialFirstLauncherRead;
    private int readinessReads;
    private int completeUiReads;
    private JsonObject status() {
        JsonObject result = json("{\"ok\":true,\"session_id\":\"" + SESSION + "\",\"android_pid\":42,\"qnx_pid\":43,\"stopped\":false,\"deliveries\":{\"qnx\":{\"pid\":43,\"bounded\":false},\"android\":{\"pid\":42,\"serial\":\"emulator-5588\",\"guest_acknowledged\":true,\"external_reboot\":{\"boot_id\":\"" + AFTER + "\"},\"home\":{\"status\":\"visible_on_primary\",\"component\":\"com.qinggan.app.launcher/com.qinggan.app.launcher.activity.MainActivity\",\"display_id\":0},\"displays\":{\"status\":\"both_guest_displays_ready\"}}}}");
        result.addProperty("machine_seed", directory.resolve("machine").toString()); return result;
    }
    @Test void api28UsesSupportedAllAttachedAndPositiveLauncherWindowProof() throws Exception {
        exercise();
        assertEquals(List.of("all_attached","all_attached"), scopes);
    }
    @Test void waitsForTheCurrentBootWhenTheFirstReadyDeliveryIsStale() throws Exception {
        staleFirstReadiness = true;
        exercise();
        assertEquals(List.of("all_attached", "all_attached"), scopes);
        assertTrue(readinessReads >= 2);
    }
    @Test void retriesPartialLauncherWindowWithinTheSameBoot() throws Exception {
        partialFirstLauncherRead = true;
        exercise();
        assertEquals(List.of("all_attached", "all_attached", "all_attached"), scopes);
        assertEquals(2, completeUiReads);
    }
    @Test void visibleLauncherNodeWithoutTheRequiredWindowDoesNotAuthorizeBoot() throws Exception {
        missingWindow = true;
        assertThrows(java.io.IOException.class, this::exercise);
    }
    private void exercise() throws Exception {
        Path machine = Files.createDirectory(directory.resolve("machine")), ownedWork = Files.createDirectory(directory.resolve("owned"));
        Files.writeString(machine.resolve("runtime.json"), "{\"kind\":\"portable-guest-runtime\",\"profile\":\"profile\",\"qnx\":{}}");
        Path originals=Files.createDirectories(directory.resolve("oem-data")),profile=Files.createDirectories(originals.resolve("profiles/profile"));
        Files.writeString(profile.resolve("profile-qnx.json"),"{\"originals\":{\"system_la.img\":{\"file\":\"system_la.img\"}}}");
        Files.writeString(Files.createDirectories(originals.resolve(".local/profiles/profile/qnx")).resolve("system_la.img"),"host original role fixture");
        Path voyemu=originals.resolve("voyemu");Files.writeString(voyemu,"host executable identity fixture");
        JsonObject operation = json("{\"operation\":\"install\",\"transactionId\":\"" + MANIFEST + "\",\"rebootCount\":1,\"bootId\":\"" + AFTER + "\"}");
        JsonObject wrapper = new JsonObject(); wrapper.addProperty("schemaVersion", 1); wrapper.addProperty("serial", "emulator-5588");
        wrapper.addProperty("body", "bootCompleted=1\nbootId=" + AFTER + "\nenforcing=Enforcing\noperation:\n" + operation + "\n");
        Path response = directory.resolve("response.json"); Files.writeString(response, wrapper + "\n");
        Path binary = directory.resolve("installer-fixture");
        Files.writeString(binary, "#!/bin/sh\ncat '" + response + "'\n"); assertTrue(binary.toFile().setExecutable(true));
        VoyemuClient client = new VoyemuClient((bytes, timeout) -> {
            JsonObject request = json(bytes), result = new JsonObject();
            switch (request.get("op").getAsString()) {
                case "info": result = json("{\"api\":\"voyemu.test\",\"schema\":1}"); break;
                case "profile.get": result.addProperty("id", "profile"); break;
                case "session.start":
                    result.add("session", json("{\"id\":\"" + SESSION + "\",\"ownership_id\":\"" + OWNER + "\"}"));
                    result.addProperty("profile", "profile"); result.addProperty("machine_seed", machine.toString()); result.addProperty("background", true); break;
                case "session.status": result = status(); break;
                case "session.wait": {
                    JsonObject observation = status();
                    if (++readinessReads == 1 && staleFirstReadiness) {
                        observation.getAsJsonObject("deliveries").getAsJsonObject("android")
                                .getAsJsonObject("external_reboot").addProperty("boot_id", BEFORE);
                    }
                    result.addProperty("predicate", "android.ready");
                    result.addProperty("ready", true);
                    result.add("last_observation", observation);
                    break;
                }
                case "session.ui":
                    String scope = request.get("scope").getAsString(); scopes.add(scope);
                    if (!scope.equals("all_attached")) {
                        JsonObject rejected = envelope(request, new JsonObject()); rejected.remove("result"); rejected.addProperty("ok", false);
                        rejected.add("error", json("{\"code\":\"TEST_UNSUPPORTED\",\"message\":\"interactive requires API30\",\"outcome\":\"not_started\",\"retryable\":false}"));
                        return new VoyemuClient.ProcessResult(4, false, rejected.toString(), "");
                    }
                    result = json("{\"format\":\"voyemu.ui.v1\",\"profile\":\"profile\",\"session_id\":\"" + SESSION + "\",\"android_pid\":42,\"boot_id\":\"" + AFTER + "\",\"screens\":[{\"screen\":\"primary\",\"status\":\"partial\",\"windows\":[{\"id\":\"window\",\"package\":\"com.qinggan.app.launcher\",\"title\":\"" + (missingWindow ? "other" : "com.qinggan.app.launcher/com.qinggan.app.launcher.activity.MainActivity") + "\",\"read_complete\":true}],\"nodes\":[{\"package\":\"com.qinggan.app.launcher\",\"window_id\":\"window\",\"present\":true,\"visible\":true}]}]}"); boolean partialLauncher = partialFirstLauncherRead && scopes.size() == 1;
                    if (partialLauncher) {
                        result.getAsJsonArray("screens").get(0).getAsJsonObject()
                                .getAsJsonArray("windows").get(0).getAsJsonObject()
                                .addProperty("read_complete", false);
                    } else {
                        completeUiReads++;
                    }
                    boolean beforeCapture = completeUiReads == 1;
                    result.addProperty("observation_id", partialLauncher ? "partial" : beforeCapture ? "before" : "after");
                    JsonObject uiClock = GuestBootCaptureTest.clock(beforeCapture ? "10" : "40", beforeCapture ? "20" : "50");
                    uiClock.addProperty("clock_id", SESSION);
                    result.add("host_interval", uiClock);
                    break;
                case "session.screenshot": {
                    Path target=Files.createDirectory(Path.of(request.get("artifact_dir").getAsString()),java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")));GuestBootCaptureTest capture=new GuestBootCaptureTest();capture.directory=target;result=capture.capture();result.addProperty("session_id",SESSION);result.addProperty("android_boot_id",AFTER);JsonObject clock=GuestBootCaptureTest.clock("21","39");clock.addProperty("clock_id",SESSION);result.add("host_interval",clock);capture.retain(result);break;
                }
                case "session.stop":
                    result = json("{\"session_id\":\"" + SESSION + "\",\"accepted\":true,\"cleanup\":{\"verified\":true,\"supervisor_endpoint_closed\":true,\"supervisor_lock_released\":true}}"); break;
                default: throw new AssertionError(request);
            }
            return new VoyemuClient.ProcessResult(0, false, envelope(request, result).toString(), "");
        });
        try (OwnedSession owned = OwnedSession.start(client, "profile", machine, ownedWork, false, true)) {
            InstallerDriver driver = new InstallerDriver(binary, ru.voboost.tests.RunFiles.directory(directory.resolve("driver")), client, owned, "profile", MANIFEST, "fixture", "d".repeat(64),null,voyemu);
            driver.configureEmulatorDriver(binary);
            CliProcess.Control control = new CliProcess.Control(System.nanoTime() + Duration.ofSeconds(10).toNanos(), null);
            try { assertEquals(AFTER, driver.verifyBoot(1, BEFORE, control).id()); }
            finally { control.close(); }
        }
    }
    private static JsonObject envelope(JsonObject request, JsonObject result) {
        if ("session.ui".equals(request.get("op").getAsString()) && result.has("screens")) {
            result = ru.voboost.voyemu.SessionUiTestFrames.response(result, java.util.List.of("primary"));
        }
        JsonObject value = new JsonObject(); value.addProperty("schema", 1); value.add("request_id", request.get("request_id"));
        value.addProperty("operation_id", "e".repeat(32)); value.addProperty("ok", true); value.addProperty("elapsed_ms", 0); value.add("result", result); return value;
    }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    @Test void restoredCheckpointCannotSubstituteForCurrentColdStockBaseline(){assertTrue(InstallerDriver.stockColdRequired(false,"restore_requested"));assertFalse(InstallerDriver.stockColdRequired(false,"cold"));assertFalse(InstallerDriver.stockColdRequired(false,"sdk_cold_fallback"));assertTrue(InstallerDriver.stockColdRequired(true,"cold"));}
    @Test void stockColdReconciliationRequiresTheActualCompletedReceiptAndFreshOwnerBoot()throws Exception {
        JsonObject input=json("{\"priorBoot\":\""+BEFORE+"\"}"),receipt=json("{\"status\":\"completed\",\"scope\":\"guest_boot_changed\",\"session_id\":\""+SESSION+"\",\"boot_id_before\":\""+BEFORE+"\",\"boot_id\":\""+AFTER+"\",\"android_pid\":42}");InstallerDriver.requireStockReboot(input,receipt,AFTER,SESSION,42);
        for(String key:List.of("status","scope","session_id","boot_id_before","boot_id")){JsonObject wrong=receipt.deepCopy();wrong.addProperty(key,"foreign");assertThrows(java.io.IOException.class,()->InstallerDriver.requireStockReboot(input,wrong,AFTER,SESSION,42));}assertThrows(java.io.IOException.class,()->InstallerDriver.requireStockReboot(input,receipt,AFTER,SESSION,43));
    }    @Test void systemMenuScrollUsesOnlyTheFreshObservedOemMenuContainer()throws Exception {
        JsonObject tree=json("""
            {"format":"voyemu.ui.v1","profile":"profile","session_id":"SESSION","boot_id":"boot","source_instance":"source","revision":"1","screens":[{
            "screen":"primary","status":"ok","complete":true,"width":1920,"height":720,
            "windows":[{"id":"window","type":1,"bounds":{"x":0,"y":0,"width":1920,"height":720},"title":"Setting","package":"com.qinggan.app.setting"}],
            "nodes":[{"id":"menu","window_id":"window","resource_id":"com.qinggan.app.setting:id/layout_menu_setting_left","package":"com.qinggan.app.setting",
            "class":"android.widget.ScrollView","visible":true,"enabled":true,"clickable":false,"actionable_candidate":false,"scrollable":true,
            "bounds":{"x":175,"y":0,"width":268,"height":720},"clipped_bounds":{"x":175,"y":0,"width":268,"height":720}}]}]}
            """.replace("SESSION",SESSION));
        var session=new VoyemuClient.Session(SESSION,OWNER);assertNotNull(InstallerDriver.systemMenuScroll(tree,"profile",session));
        JsonObject wrong=tree.deepCopy();wrong.getAsJsonArray("screens").get(0).getAsJsonObject().getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("resource_id","com.qinggan.app.setting:id/network_scroll");assertThrows(java.io.IOException.class,()->InstallerDriver.systemMenuScroll(wrong,"profile",session));
        assertThrows(java.io.IOException.class,()->InstallerDriver.systemMenuScroll(tree,"foreign",session));
    }

}
