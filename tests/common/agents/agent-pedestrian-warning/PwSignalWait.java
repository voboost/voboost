package ru.voboost.tests.installer.cli;

import java.io.IOException;
import java.time.Duration;

/** Waits only for current QNX source delivery, without collecting unrelated vehicle signals. */
final class PwSignalWait {
    record Observation(String androidBoot, String qnxGeneration,
            boolean acknowledged, long modelSequence, long acknowledgedSequence) {}

    interface Port {
        Observation read() throws Exception;
        long now();
        void pause(long nanos) throws Exception;
    }

    static String generation(com.google.gson.JsonElement value) throws IOException {
        if (value == null || !value.isJsonPrimitive()
                || !(value.getAsJsonPrimitive().isNumber() || value.getAsJsonPrimitive().isString()))
            throw new IOException("PW QNX generation is unavailable");
        String number = value.getAsString();
        if (!number.matches("[1-9][0-9]{0,19}") || new java.math.BigInteger(number).bitLength() > 64)
            throw new IOException("PW QNX generation is invalid");
        return number;
    }

    static Observation await(Port port, String boot, String generation) throws Exception {
        long deadline = port.now() + Duration.ofSeconds(5).toNanos();
        Observation last = null;
        while (port.now() < deadline) {
            last = port.read();
            if (!boot.equals(last.androidBoot()) || !generation.equals(last.qnxGeneration()))
                throw new IOException("PW source epoch changed while awaiting propagation");
            if (last.modelSequence() < 1 || last.acknowledgedSequence() < 0
                    || last.acknowledgedSequence() > last.modelSequence())
                throw new IOException("PW signal observation is malformed");
            if (port.now() >= deadline) break;
            if (last.acknowledged()
                    && last.acknowledgedSequence() == last.modelSequence()) return last;
            long remaining = deadline - port.now();
            if (remaining <= 0) break;
            port.pause(Math.min(Duration.ofMillis(100).toNanos(), remaining));
        }
        throw new IOException("PW QNX source delivery did not complete: " + last);
    }

    private PwSignalWait() {}
}
