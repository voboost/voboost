package ru.voboost.tests.agents;

import java.io.IOException;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.voboost.voyemu.EmulatorFixture;
import ru.voboost.voyemu.EmulatorPreparation;

@Order(4)
@ExtendWith(EmulatorFixture.class)
class AgentForcedEv {
    @Test
    void returnsDirectlyToForcedEvFromSave(EmulatorPreparation prepared) throws Exception {
        if (prepared == null) throw new IOException("Run emulatorStart before the FEV case");
        prepared.installer.verifyForcedEvReturn(prepared.catalog);
    }

    @Test
    void preservesExplicitVehicleSettingsOnAndOff(EmulatorPreparation prepared) throws Exception {
        if (prepared == null) throw new IOException("Run emulatorStart before the FEV case");
        System.out.println("FEV VehicleSetting evidence: " + prepared.installer.verifyForcedEvOem(prepared.catalog));
    }

    @Test
    void preservesSelectionAndKeepsExplicitOffAcrossRestarts(EmulatorPreparation prepared) throws Exception {
        if (prepared == null) throw new IOException("Run emulatorStart before the FEV case");
        prepared.installer.verifyForcedEv(prepared.catalog);
    }
}
