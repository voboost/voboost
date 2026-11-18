package ru.voboost.tests;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class GateStatusTest {
    @Test
    void preservesDistinctEvidenceStates() {
        assertArrayEquals(new String[]{"PASS", "FAIL", "BLOCKED", "NOT_RUN", "NOT_APPLICABLE"},
                java.util.Arrays.stream(GateStatus.values()).map(Enum::name).toArray(String[]::new));
    }
}
