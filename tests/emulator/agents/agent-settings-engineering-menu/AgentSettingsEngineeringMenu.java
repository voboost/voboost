package ru.voboost.tests.agents;

import java.io.IOException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.voboost.voyemu.EmulatorFixture;
import ru.voboost.voyemu.EmulatorPreparation;

@ExtendWith(EmulatorFixture.class)
class AgentSettingsEngineeringMenu {
    @Test
    @Tag("emulator-workflow")
    void opensBothStockMenusOnlyWhenEnabled(EmulatorPreparation prepared) throws Exception {
        if (prepared == null) throw new IOException("Run emulatorStart before the Settings engineering menu case");
        prepared.installer.verifySettingsEngineeringMenu(prepared.catalog);
    }
}

