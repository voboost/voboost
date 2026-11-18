package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.tests.RunFiles;

class GuestBootLedgerTest {
    @TempDir Path directory;
    private static final String SESSION="a".repeat(32),BOOT="12345678-1234-1234-1234-123456789abc";
    private void wire(JsonObject proof,String field,JsonObject result)throws IOException {
        JsonObject response=new JsonObject();response.addProperty("ok",true);response.add("result",result);JsonObject envelope=new JsonObject();envelope.addProperty("state","RESPONSE");envelope.add("response",response);Path file=directory.resolve(field+".json");RunFiles.write(file,envelope);proof.addProperty(field,file.toString());
    }
    private JsonObject ui(boolean before)throws IOException {
        JsonObject value=GuestBootCaptureTest.ui(before);value.addProperty("session_id",SESSION);value.addProperty("boot_id",BOOT);value.getAsJsonObject("host_interval").addProperty("clock_id",SESSION);value.addProperty("nativeLayoutFixture","x".repeat(3_000_000));
        value.add("screens", RunFiles.parse("{\"screens\":[{\"screen\":\"primary\",\"status\":\"ok\"}]}").get("screens"));
        return ru.voboost.voyemu.SessionUiTestFrames.response(value, java.util.List.of("primary"));
    }
    @Test void largeIndependentWireObservationsKeepTheCompactRecordBoundedAndRechecked()throws Exception {
        directory=directory.toRealPath();Path captureDirectory=RunFiles.directory(directory.resolve("capture"));GuestBootCaptureTest source=new GuestBootCaptureTest();source.directory=captureDirectory;JsonObject capture=source.capture();capture.addProperty("session_id",SESSION);capture.addProperty("android_boot_id",BOOT);capture.getAsJsonObject("host_interval").addProperty("clock_id",SESSION);source.retain(capture);
        JsonObject status=RunFiles.parse("{\"ok\":true,\"stopped\":false,\"session_id\":\""+SESSION+"\",\"android_pid\":42,\"machine_seed\":\"/owned/machine\",\"deliveries\":{\"android\":{\"pid\":42,\"serial\":\"emulator-5588\"}}}"),ready=new JsonObject();ready.addProperty("ready",true);ready.addProperty("predicate","android.ready");ready.add("last_observation",status.deepCopy());
        Path boot=directory.resolve("boot.txt");Files.writeString(boot,"bootCompleted=1\nbootId="+BOOT+"\nenforcing=Enforcing\noperation:\n{}\n");JsonObject proof=new JsonObject();proof.addProperty("schemaVersion",2);proof.addProperty("backendContract","native_owned_ready_capture_v1");proof.addProperty("profile","profile");proof.addProperty("sessionId",SESSION);proof.addProperty("bootId",BOOT);proof.addProperty("androidPid",42);proof.addProperty("bootBefore",boot.toString());proof.addProperty("bootAfter",boot.toString());proof.addProperty("captureDirectory",captureDirectory.toString());
        wire(proof,"beforeUi",ui(true));wire(proof,"afterUi",ui(false));wire(proof,"readiness",ready);wire(proof,"statusAfter",status);wire(proof,"capture",capture);Path file=directory.resolve("proof.json");RunFiles.write(file,proof);assertTrue(Files.size(file)<4096);assertEquals(BOOT,GuestBootLedger.readCapture(file).get("bootId").getAsString());
        Files.delete(directory.resolve("beforeUi.json"));assertThrows(IOException.class,()->GuestBootLedger.readCapture(file));
    }
}
