package ru.voboost.tests.agents;

import java.io.IOException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.voboost.tests.RunFiles;
import ru.voboost.voyemu.EmulatorFixture;
import ru.voboost.voyemu.EmulatorPreparation;

@Tag("emulator-workflow")
@ExtendWith(EmulatorFixture.class)
class AgentKeyboard {
    @Test
    void englishOnlyCommitsLatinText(EmulatorPreparation prepared) throws Exception {
        verify(prepared, 10, "moscow");
    }

    @Test
    void russianAndEnglishSwitchAndPreserveText(EmulatorPreparation prepared) throws Exception {
        verify(prepared, 11, "Москваmoscow");
    }

    private static void verify(EmulatorPreparation prepared, int number, String committed) throws Exception {
        if (prepared == null) throw new IOException("Run emulatorStart before the keyboard case");
        var file = prepared.installer.verifyKeyboardFunctional(prepared.catalog, number);
        var proof = RunFiles.read(file);
        if (!"PASS".equals(proof.get("semanticInputStatus").getAsString())
                || !committed.equals(proof.get("committed").getAsString())) {
            throw new IOException("Keyboard case " + number + ": " + proof.get("reason").getAsString() + ". Evidence: " + file);
        }
    }
}
