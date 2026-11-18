package ru.voboost.tests.installer.cli;

import java.io.IOException;
import java.util.Set;

/** Distinct durable phases prevent the base lifecycle from satisfying either clean seeding cycle. */
public final class SeedingPhases {
    private static final Set<String> PHASES = Set.of(
            "seed-first", "seed-second", "seed-restore");

    public static String cycle(int index) throws IOException {
        return switch (index) {
            case 0 -> "seed-first";
            case 1 -> "seed-second";
            default -> throw new IOException("exactly two initial seeding cycles required");
        };
    }

    public static boolean contains(String phase) {
        return PHASES.contains(phase);
    }

    private SeedingPhases() {}
}
