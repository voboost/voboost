package ru.voboost.tests.delivery;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.CanTxPolicyGate;

/** Standalone diagnostic for the same source-policy prerequisite used by I03. */
class CanTxPolicyTest {
    @Test void requiresActualOwnerCanTxAndPidPolicyGate() throws Exception {
        Path root = Path.of("").toRealPath();
        Path work = Path.of(System.getProperty("testWorkDir")).resolve("can-tx-policy-" + UUID.randomUUID());
        CanTxPolicyGate.verify(root.resolve("../voboost-inject").normalize(), work);
    }
}
