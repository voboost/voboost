package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestConfigurationTest {
    @TempDir Path dir;
    @Test void resolvesDistinctRolesAndFallsBackToTheOnlyMappedMachine() throws Exception {
        Path executable = Files.createFile(dir.resolve("voyemu"));
        assertTrue(executable.toFile().setExecutable(true));
        Path installer = Files.createDirectory(dir.resolve("installer"));
        Path agents = Files.createDirectory(dir.resolve("agents"));
        for (Path machine : java.util.List.of(installer, agents)) {
            Files.writeString(machine.resolve("runtime.json"),
                    "{\"kind\":\"portable-guest-runtime\",\"profile\":\"firmware\"}");
        }
        Path shared = dir.resolve("shared.json");
        Path local = dir.resolve("local.json");
        Files.writeString(shared, "{\"machines\":[\"a\",\"b\"],\"roles\":{\"installer\":\"a\",\"agents\":\"b\"}}");
        String installerMapping = "\"a\":{\"path\":\"" + installer + "\"}";
        String agentMapping = "\"b\":{\"path\":\"" + agents + "\"}";
        var inputs = Map.of("scenario", "fresh", "voyemu", executable.toString());
        Files.writeString(local, "{\"machines\":{" + installerMapping + "," + agentMapping + "}}");
        assertEquals(installer.toRealPath(), TestConfiguration.resolveRole(shared, local, inputs, "installer").configuration().machine());
        assertEquals(agents.toRealPath(), TestConfiguration.resolveRole(shared, local, inputs, "agents").configuration().machine());
        Files.writeString(local, "{\"machines\":{" + agentMapping + "}}");
        assertEquals(agents.toRealPath(), TestConfiguration.resolveRole(shared, local, inputs, "installer").configuration().machine());
    }
    @Test void resolvesSelectedAndExplicitMachineAndRejectsInvalidInputs() throws Exception {
        Path executable = Files.createFile(dir.resolve("voyemu"));
        assertTrue(executable.toFile().setExecutable(true));
        Path machine = Files.createDirectory(dir.resolve("machine"));
        Path manifest = machine.resolve("runtime.json");
        Files.writeString(manifest, "{\"kind\":\"portable-guest-runtime\",\"profile\":\"firmware\"}");
        Path shared = dir.resolve("shared.json"), local = dir.resolve("local.json");
        Files.writeString(shared, "{\"machines\":[\"test\"]}");
        Files.writeString(local, "{\"voyemu\":\"" + executable + "\",\"machines\":{\"test\":{\"path\":\"" + machine + "\"}}}");
        var config = TestConfiguration.resolve(shared, local, Map.of("machineName", "test", "scenario", "resume"));
        assertEquals(machine.toRealPath(), config.machine());
        assertEquals("firmware", config.profile());
        assertThrows(IOException.class, () -> TestConfiguration.resolve(shared, local, Map.of("machineName", "missing", "scenario", "fresh")));
        assertThrows(IOException.class, () -> TestConfiguration.resolve(shared, local, Map.of("machineName", "test")));
        Files.delete(local);
        var explicit = Map.of("machineName", "test", "voyemu", executable.toString(), "machine", machine.toString(), "scenario", "fresh");
        assertEquals("firmware", TestConfiguration.resolve(shared, local, explicit).profile());
        assertThrows(IOException.class, () -> TestConfiguration.resolve(shared, local, Map.of("machineName", "test", "voyemu", executable.toString(), "scenario", "fresh")));
        Files.writeString(manifest, "{\"kind\":\"foreign\",\"profile\":\"firmware\"}");
        assertThrows(IOException.class, () -> TestConfiguration.resolve(shared, local, explicit));
        Files.writeString(manifest, "{\"kind\":\"portable-guest-runtime\",\"profile\":\"\"}");
        assertThrows(IOException.class, () -> TestConfiguration.resolve(shared, local, explicit));
    }
}
