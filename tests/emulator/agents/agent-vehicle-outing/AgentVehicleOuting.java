package ru.voboost.tests.agents;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.voboost.voyemu.EmulatorFixture;
import ru.voboost.voyemu.EmulatorPreparation;

@ExtendWith(EmulatorFixture.class)
class AgentVehicleOuting {
    @Test
    void appliesThreePoliciesAtTheStockSpeedThreshold(EmulatorPreparation prepared) throws Exception {
        if (prepared == null) throw new IOException("Run emulatorStart before the Outing case");
        System.out.println("Outing evidence: " + prepared.installer.verifyOuting(prepared.catalog));
    }
}
