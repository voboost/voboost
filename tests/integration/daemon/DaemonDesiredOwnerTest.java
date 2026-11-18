package ru.voboost.tests.agents;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.HostProcess;
import ru.voboost.tests.RunFiles;

/** Root-owned cases execute production owner framing, dispatch and durable storage on the host. */
class DaemonDesiredOwnerTest {
    @Test void offRemainsAuthoritativeAcrossLockReconcileRestartAndStaleInput() throws Exception {
        Path root = Path.of("").toRealPath();
        String cargo = System.getProperty("cargo"), rustc = System.getProperty("rustc");
        assertNotNull(cargo, "BLOCKED: -Pcargo absolute trusted tool path required");
        assertNotNull(rustc, "BLOCKED: -Prustc absolute trusted tool path required");
        assertTrue(Path.of(cargo).isAbsolute() && Path.of(rustc).isAbsolute(), "absolute tool paths required");
        Path reports = Path.of(System.getProperty("testWorkDir")); Files.createDirectories(reports);
        Path work = RunFiles.directory(reports.resolve("desired-owner-" + UUID.randomUUID()));
        for (String name : List.of("lock-reconcile-off", "restart-off", "latest-off-stale-on")) {
            Path scenario = root.resolve("tests/integration/daemon/" + name + ".json");
            Path receipt = work.resolve(name + ".json"), log = work.resolve(name + ".log");
            var result = HostProcess.runAt(root.resolve("../voboost-inject").normalize(), log,
                    List.of(cargo, "test", "--offline", "--locked", "--release", "-p", "voboost-daemon", "--bin", "voboost-daemon",
                            "composition::product_desired_owner_scenario", "--", "--exact", "--nocapture"), 600000,
                    Map.of("RUSTC", rustc, "CARGO_TARGET_DIR", root.resolve("../.build-cache/inject-host").normalize().toString(),
                            "TMPDIR", work.toString(), "VOBOOST_DESIRED_OWNER_SCENARIO", scenario.toString(),
                            "VOBOOST_DESIRED_OWNER_RECEIPT", receipt.toString()));
            assertEquals(0, result.exit(), "owner RPC scenario failed: " + log);
            assertTrue(Files.isRegularFile(receipt), "BLOCKED: actual owner RPC harness did not produce receipt; " + log);
            JsonObject actual = RunFiles.read(receipt);
            assertEquals(1, actual.get("schemaVersion").getAsInt());
            JsonObject config = actual.getAsJsonObject("config");
            assertEquals(2, config.get("schemaVersion").getAsInt());
            assertFalse(config.getAsJsonObject("agents").getAsJsonObject("launcher").get("enabled").getAsBoolean());
            long revision = config.get("userRevision").getAsLong();
            assertTrue(revision > 0);
            JsonObject stamp = config.getAsJsonObject("fieldRevisions").getAsJsonObject("agents/launcher/enabled");
            assertEquals(revision, stamp.get("revision").getAsLong());
            assertEquals(name.equals("latest-off-stale-on") ? 9 : 8, stamp.getAsJsonObject("input").get("requestId").getAsInt());
            assertEquals(RunFiles.read(scenario).getAsJsonArray("steps").size(), actual.getAsJsonArray("exchanges").size());
            int saved = 0, read = 0;
            boolean superseded = false;
            for (var item : actual.getAsJsonArray("exchanges")) {
                JsonObject exchange = item.getAsJsonObject();
                String operation = exchange.get("operation").getAsString();
                if ("read-desired".equals(operation)) {
                    JsonObject outcome = exchange.getAsJsonObject("outcome");
                    assertEquals("desired-saved", outcome.get("status").getAsString());
                    assertEquals(revision, outcome.get("revision").getAsLong());
                    JsonObject observed = JsonParser.parseString(outcome.get("config").getAsString()).getAsJsonObject();
                    assertEquals(config, observed);
                    read++;
                }
                if (!"save-desired".equals(operation)) continue;
                JsonObject outcome = exchange.getAsJsonObject("outcome");
                assertEquals("desired-saved", outcome.get("status").getAsString());
                assertTrue(outcome.get("revision").getAsLong() <= revision);
                JsonObject acknowledged = JsonParser.parseString(outcome.get("config").getAsString()).getAsJsonObject();
                assertEquals(outcome.get("revision").getAsLong(), acknowledged.get("userRevision").getAsLong());
                if ("superseded".equals(outcome.get("disposition").getAsString())) {
                    superseded = true;
                    assertFalse(acknowledged.getAsJsonObject("agents").getAsJsonObject("launcher").get("enabled").getAsBoolean());
                }
                saved++;
            }
            assertEquals(name.equals("latest-off-stale-on") ? 3 : 1, saved);
            assertEquals(name.equals("latest-off-stale-on"), superseded);
            assertEquals(name.equals("restart-off") ? 2 : 1, read);
        }
    }
}
