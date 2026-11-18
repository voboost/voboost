package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClusterStartTest {
    private static VoyemuClient client() { return new VoyemuClient((request, timeout) -> { throw new AssertionError("request must not dispatch"); }); }
    @Test void clusterStartsWithoutAnAutomaticStop() {
        VoyemuClient client = client();
        JsonObject basic = JsonParser.parseString(client.start("request", "profile", "machine", null, true).json()).getAsJsonObject();
        assertFalse(basic.get("with_cluster").getAsBoolean()); assertFalse(basic.has("seconds")); assertTrue(basic.get("background").getAsBoolean());
        JsonObject cluster = JsonParser.parseString(client.start("request", "profile", "machine", null, true, true).json()).getAsJsonObject();
        assertTrue(cluster.get("with_cluster").getAsBoolean()); assertFalse(cluster.has("seconds"));
    }
    @Test void missingClusterManifestRejectsBeforeAnyDispatch(@TempDir Path work) throws Exception {
        Path machine = Files.createDirectory(work.resolve("machine"));
        Files.writeString(machine.resolve("runtime.json"), "{\"kind\":\"portable-guest-runtime\",\"profile\":\"profile\"}");
        assertThrows(IOException.class, () -> OwnedSession.start(client(), "profile", machine, work, true, true));
    }
    @Test void explicitClusterSessionFailureUsesTheSameOwnedStop(@TempDir Path work) throws Exception {
        Path machine = Files.createDirectory(work.resolve("machine"));
        Files.writeString(machine.resolve("runtime.json"), "{\"kind\":\"portable-guest-runtime\",\"profile\":\"profile\",\"qnx\":{\"images\":{}}}");
        List<String> ops = new ArrayList<>();
        String session = "a".repeat(32), owner = "b".repeat(32);
        VoyemuClient client = new VoyemuClient((bytes, timeout) -> {
            JsonObject request = JsonParser.parseString(bytes).getAsJsonObject();
            String op = request.get("op").getAsString(); ops.add(op);
            JsonObject result = new JsonObject();
            switch (op) {
                case "info": result.addProperty("api", "voyemu.test"); result.addProperty("schema", 1); break;
                case "profile.get": result.addProperty("id", "profile"); break;
                case "session.start":
                    assertTrue(request.get("with_cluster").getAsBoolean()); assertFalse(request.has("seconds"));
                    JsonObject owned = new JsonObject(); owned.addProperty("id", session); owned.addProperty("ownership_id", owner);
                    result.add("session", owned); result.addProperty("profile", "profile"); result.addProperty("background", true); result.addProperty("machine_seed", machine.toString()); break;
                case "session.status": result.addProperty("session_id", session); break;
                case "session.wait": result.addProperty("predicate", "wrong"); result.addProperty("ready", true);
                    JsonObject observation = new JsonObject(); observation.addProperty("session_id", session); result.add("last_observation", observation); break;
                case "session.stop":
                    assertEquals(session, request.getAsJsonObject("session").get("id").getAsString());
                    assertEquals(owner, request.getAsJsonObject("session").get("ownership_id").getAsString());
                    result.addProperty("session_id", session); result.addProperty("accepted", true);
                    result.add("cleanup", JsonParser.parseString("{\"verified\":true,\"supervisor_endpoint_closed\":true,\"supervisor_lock_released\":true}")); break;
                default: throw new AssertionError(op);
            }
            JsonObject response = new JsonObject(); response.addProperty("schema", 1); response.add("request_id", request.get("request_id"));
            response.addProperty("operation_id", "c".repeat(32)); response.addProperty("elapsed_ms", 0); response.addProperty("ok", true); response.add("result", result);
            return new VoyemuClient.ProcessResult(0, false, response.toString(), "");
        });
        assertThrows(IOException.class, () -> { try (OwnedSession owned = OwnedSession.start(client, "profile", machine, work, true, true)) { owned.verifyAndroidUi(); } });
        assertEquals(1, ops.stream().filter("session.start"::equals).count()); assertEquals(1, ops.stream().filter("session.stop"::equals).count());
    }
}
