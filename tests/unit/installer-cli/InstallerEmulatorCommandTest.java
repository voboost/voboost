package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InstallerEmulatorCommandTest {
    @Test void bindsBothExecutablesAndRejectsForeignCandidateOrDevice() throws Exception {
        var expected = EmulatorCommand.of(Path.of("/tools/emulator-driver"), Path.of("/package/voboost-installer"),
                Path.of("/work/request.json"), "emulator-5588");
        JsonArray command = new JsonArray();
        expected.forEach(command::add);
        EmulatorCommand.require(command, Path.of("/tools/emulator-driver"), Path.of("/package/voboost-installer"),
                Path.of("/work/request.json"), "emulator-5588");
        for (String serial : new String[] {"1234567", "emulator-5589", "emulator-05588"})
            assertThrows(IOException.class, () -> EmulatorCommand.of(Path.of("/tools/emulator-driver"),
                    Path.of("/package/voboost-installer"), Path.of("/work/request.json"), serial));
        assertThrows(IOException.class, () -> EmulatorCommand.require(command, Path.of("/tools/emulator-driver"),
                Path.of("/another/voboost-installer"), Path.of("/work/request.json"), "emulator-5588"));
    }
}

