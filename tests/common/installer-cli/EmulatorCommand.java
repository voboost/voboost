package ru.voboost.tests.installer.cli;

import com.google.gson.JsonArray;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Exact dispatch binding for the host-only test tool and the selected ordinary installer. */
public final class EmulatorCommand {
    private EmulatorCommand() {}

    public static List<String> of(Path driver, Path candidate, Path request, String serial) throws IOException {
        if (driver == null || candidate == null || request == null || !driver.isAbsolute()
                || !candidate.isAbsolute() || !request.isAbsolute() || !InstallerDriver.emulator(serial))
            throw new IOException("absolute test driver, candidate, request and exact emulator required");
        return List.of(driver.toString(), "--emulator-check", request.toString(), "--device", serial,
                "--candidate", candidate.toString());
    }

    public static void require(JsonArray command, Path driver, Path candidate, Path request, String serial)
            throws IOException {
        List<String> expected = of(driver, candidate, request, serial);
        if (command == null || command.size() != expected.size())
            throw new IOException("test driver command differs");
        for (int index = 0; index < expected.size(); index++)
            if (!command.get(index).isJsonPrimitive()
                    || !command.get(index).getAsJsonPrimitive().isString()
                    || !expected.get(index).equals(command.get(index).getAsString()))
                throw new IOException("test driver command differs");
    }
}

