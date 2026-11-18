package ru.voboost.tests;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CanTxPolicyGateTest {
    @TempDir Path directory;

    @Test void capturedCanDenialStopsBeforeAnyPassReceipt() throws Exception {
        String captured = "roundtrip ok: stock policy\nFAIL: native CAN open missing through bounds\n";
        AtomicBoolean published = new AtomicBoolean();
        Path component = Files.createDirectory(directory.resolve("component"));
        Path work = directory.resolve("work");
        assertThrows(IOException.class, () -> {
            CanTxPolicyGate.verify(component, work, (owner, log, environment) -> {
                assertEquals(component, owner);
                assertEquals(work.toString(), environment.get("TMPDIR"));
                Files.writeString(log, captured);
                return new HostProcess.Result(2, captured);
            });
            published.set(true);
        });
        assertFalse(published.get());
        assertFalse(Files.exists(work.resolve("cases")));
        assertEquals(captured, Files.readString(work.resolve("can-tx-pid-policy.log")));
    }

    @Test void fixedOwnerCommandCannotSelectCargoOrAnotherTarget() {
        assertEquals(List.of("/usr/bin/make", "--no-print-directory", "--jobs=1",
                "test-selinux-rule-generation", "test-selinux-real-policy"), CanTxPolicyGate.command());
    }

    @Test void incompleteOrContradictorySuccessCannotPass() {
        String positive = positive();
        for (String invalid : List.of("", positive.replace("test_real_policy: OK\n", ""),
                positive.replace("metadata baseline ok:", "missing baseline:"),
                positive + "test_real_policy: OK\n", positive + "FAIL: native CAN ioctl1 missing\n")) {
            assertThrows(IOException.class, () -> CanTxPolicyGate.requirePassed(new HostProcess.Result(0, invalid)));
        }
        assertThrows(IOException.class, () -> CanTxPolicyGate.requirePassed(new HostProcess.Result(1, positive)));
    }

    @Test void completeOwnerReceiptRetainsEvidenceWithoutPublishingAcceptance() throws Exception {
        Path component = Files.createDirectory(directory.resolve("component"));
        Path work = directory.resolve("work");
        Path log = CanTxPolicyGate.verify(component, work, (owner, output, environment) -> {
            Files.writeString(output, positive());
            return new HostProcess.Result(0, positive());
        });
        assertTrue(Files.isRegularFile(log));
        assertFalse(Files.exists(work.resolve("cases")));
        assertThrows(IOException.class, () -> CanTxPolicyGate.verify(component, work));
    }

    private static String positive() {
        String run = "roundtrip ok: retained policy\ninject ok: generated policy re-read\ntest_real_policy: OK\n";
        return "policy rule generation: deterministic and current\n" + run
                + "metadata baseline ok: runtime and init all domain dir/file/process AV except getattr\n" + run;
    }
}
