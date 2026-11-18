package ru.voboost.voyemu;

import com.google.gson.JsonObject;

/** Exact public Android display power sample; independently observed CAN ACC remains separate. */
public final class VehiclePower {

    public static boolean matches(JsonObject power, boolean awake) {
        return string(power, "status", "ready") && flag(power, "complete", true) && flag(power,
                "display_ready", true) && string(power, "wakefulness", awake ? "Awake" : "Asleep")
                && string(power, "display_state", awake ? "ON" : "OFF");
    }

    private static boolean string(JsonObject object, String key, String expected) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive()
                && object.getAsJsonPrimitive(key).isString()
                && expected.equals(object.get(key).getAsString());
    }

    private static boolean flag(JsonObject object, String key, boolean expected) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive()
                && object.getAsJsonPrimitive(key).isBoolean()
                && object.get(key).getAsBoolean() == expected;
    }

    private VehiclePower() {}
}
