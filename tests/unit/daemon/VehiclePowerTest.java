package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

class VehiclePowerTest {

    @Test

    void settledAsleepOffRequiresDisplayTransitionReady() throws Exception {
        var power = RunFiles.parse("{\"status\":\"ready\",\"complete\":true,\"wakefulness\":\"Asleep\",\"display_state\":\"OFF\",\"display_ready\":true}");
        assertTrue(VehiclePower.matches(power, false));
        assertFalse(VehiclePower.matches(power, true));
        power.addProperty("display_ready",false);assertFalse(VehiclePower.matches(power,false));
    }

    @Test

    void awakeRequiresOnAndTrueDisplayReady() throws Exception {
        var power = RunFiles.parse("{\"status\":\"ready\",\"complete\":true,\"wakefulness\":\"Awake\",\"display_state\":\"ON\",\"display_ready\":true}");
        assertTrue(VehiclePower.matches(power, true));
        power.addProperty("display_ready", false);
        assertFalse(VehiclePower.matches(power, true));
    }

    @Test

    void absentNullCoercedAndPartialNeverBecomeFalseDisplayReady() throws Exception {
        for (String ready : new String[] {
            "null", "\"false\"", "0"
        }) {
            var power = RunFiles.parse("{\"status\":\"ready\",\"complete\":true,\"wakefulness\":\"Asleep\",\"display_state\":\"OFF\",\"display_ready\":" + ready + "}");
            assertFalse(VehiclePower.matches(power, false));
        }
        var absent = RunFiles.parse("{\"status\":\"ready\",\"complete\":true,\"wakefulness\":\"Asleep\",\"display_state\":\"OFF\"}");
        assertFalse(VehiclePower.matches(absent, false));
    }
}
