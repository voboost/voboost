package ru.voboost.tests;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeliveryChecksTest {
    @Test void rejectsCrashAndArbitraryFailure() {
        assertThrows(IllegalStateException.class, () -> DeliveryChecks.requireInventoryRejection(139, "segmentation fault"));
        assertThrows(IllegalStateException.class, () -> DeliveryChecks.requireInventoryRejection(1, "error: unrelated failure"));
        DeliveryChecks.requireInventoryRejection(1, "error: closure inventory differs from voboost.json");
    }
    @Test void rejectsExtraPackageEntry() {
        assertThrows(IllegalStateException.class, () -> DeliveryChecks.requireExactNames(Set.of("a", "extra"), Set.of("a")));
    }
    @Test void preservesWorkForAttachedOrUnknownMount() {
        assertFalse(DeliveryChecks.mayRemoveWork(true, false));
        assertFalse(DeliveryChecks.mayRemoveWork(false, true));
        assertTrue(DeliveryChecks.mayRemoveWork(false, false));
    }
}
