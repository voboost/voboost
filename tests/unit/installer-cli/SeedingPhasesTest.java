package ru.voboost.tests.installer.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeedingPhasesTest {
    @Test void cleanCyclesHaveSeparateDurableNormalRemovalPhases() throws Exception {
        assertEquals("seed-first", SeedingPhases.cycle(0));
        assertEquals("seed-second", SeedingPhases.cycle(1));
        for (String phase : List.of("seed-first", "seed-second", "seed-restore")) {
            assertTrue(SeedingPhases.contains(phase));
            assertFalse(InstallerDriver.uninstallPreparation(phase));
        }
    }

    @Test void baseLifecycleAndUnknownIndicesCannotStandInForCleanCycles() {
        for (String phase : List.of("first", "second", "cycle", "final", "preparation")) {
            assertFalse(SeedingPhases.contains(phase));
        }
        assertThrows(IOException.class, () -> SeedingPhases.cycle(-1));
        assertThrows(IOException.class, () -> SeedingPhases.cycle(2));
    }
}
