package ru.voboost.tests.installer.cli;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InstallPreparationTest {

    private static final String MANIFEST = "a".repeat(64),
            BOOT = "12345678-1234-1234-1234-123456789abc", SERIAL = "emulator-5554";

    private static List<JsonObject> rows() {
        List<JsonObject> rows = new ArrayList<>();
        int sequence = 0;
        for (String step : List.of("wait-device", "root", "wait-device", "remount",
                "relax-first-phase", "verify-write-access", "enforce-selinux")) {
            JsonObject row = new JsonObject();
            row.addProperty("schemaVersion", 1);
            row.addProperty("deviceSerial", SERIAL);
            row.addProperty("sequence", ++ sequence);
            row.addProperty("context", sequence == 7 ? "observation" : "staging");
            row.addProperty("step", step);
            row.addProperty("attempt", 1);
            row.addProperty("transactionId", MANIFEST);
            if (sequence == 7)
                row.add("manifestSha256", JsonNull.INSTANCE);
            else
                row.addProperty("manifestSha256", MANIFEST);
            for (String key : List.of("journal", "bootId", "phase", "rebootCount", "statusSequence",
                    "journalSha256", "error"))
                row.add(key, JsonNull.INSTANCE);
            if (sequence == 7) {
                row.addProperty("journal", "recovery/" + MANIFEST + "/install-journal.json");
                row.addProperty("bootId", BOOT);
                row.addProperty("phase", "install-runtime-apply");
                row.addProperty("rebootCount", 0);
                row.addProperty("statusSequence", 9);
                row.addProperty("journalSha256", "opaque-existing-producer-field");
            }
            row.addProperty("result", "success");
            row.addProperty("exitCode", 0);
            row.addProperty("errorTruncated", false);
            row.addProperty("stdoutSha256", "opaque-existing-producer-field");
            row.addProperty("stderrSha256", "opaque-existing-producer-field");
            row.addProperty("stdoutBytes", 0);
            row.addProperty("stderrBytes", 0);
            rows.add(row);
        }
        return rows;
    }

    @Test

    void exactProductionOperationSequenceAndIndependentlyBoundJournalProveAuthority() throws Exception {
        var proof = InstallPreparation.read(rows(), SERIAL, MANIFEST, Set.of(BOOT));
        assertTrue(proof.readiness());
        assertTrue(proof.enforcing());
        assertTrue(proof.acceptedJournal());
        assertEquals(7, proof.receipts());
        var empty = InstallPreparation.read(List.of(), SERIAL, MANIFEST, Set.of(BOOT));
        assertFalse(empty.readiness());
        assertFalse(empty.enforcing());
        assertFalse(empty.acceptedJournal());
    }

    @Test

    void missingOrFailedAuthorityStepsAreNotReadiness() throws Exception {
        var missing = rows();
        missing.get(3).addProperty("step", "wait-device");
        assertFalse(InstallPreparation.read(missing, SERIAL, MANIFEST, Set.of(BOOT)).readiness());
        var failed = rows();
        failed.get(3).addProperty("result", "failed");
        failed.get(3).addProperty("exitCode", 1);
        failed.get(3).addProperty("error", "permission denied");
        assertFalse(InstallPreparation.read(failed, SERIAL, MANIFEST, Set.of(BOOT)).readiness());
    }

    @Test

    void foreignSerialManifestBootAndPartialJournalAreRejected() {
        assertThrows(IOException.class, () -> InstallPreparation.read(rows(), "emulator-5556",
                MANIFEST, Set.of(BOOT)));
        assertThrows(IOException.class, () -> InstallPreparation.read(rows(), SERIAL, "other",
                Set.of(BOOT)));
        assertThrows(IOException.class, () -> InstallPreparation.read(rows(), SERIAL, MANIFEST,
                Set.of()));
        var partial = rows();
        partial.get(6).add("phase", JsonNull.INSTANCE);
        assertThrows(IOException.class, () -> InstallPreparation.read(partial, SERIAL, MANIFEST,
                Set.of(BOOT)));
    }

    @Test

    void gapsCoercedNumbersUnknownFieldsAndBadExitAreRejected() {
        for (int variant = 0; variant < 5; variant++) {
            var rows = rows();
            switch (variant) {
                case 0 -> rows.get(2).addProperty("sequence", 99);
                case 1 -> rows.get(0).addProperty("sequence", "1");
                case 2 -> rows.get(0).addProperty("attempt", 4294967296L);
                case 3 -> rows.get(0).addProperty("extra", true);
                default -> rows.get(0).addProperty("exitCode", 1);
            }
            assertThrows(IOException.class, () -> InstallPreparation.read(rows, SERIAL, MANIFEST,
                    Set.of(BOOT)));
        }
    }

    private static InstallMonitor monitor() {
        return new InstallMonitor(MANIFEST, BOOT, new InstallMonitor.Observer() {
            public InstallMonitor.Boot verifyBoot(int count, String before,
                    CliProcess.Control control) {
                throw new AssertionError("unexpected action");
            }
            public InstallMonitor.Boot currentBoot(CliProcess.Control control) {
                throw new AssertionError("unexpected action");
            }
            public String check(JsonObject request, CliProcess.Control control) {
                throw new AssertionError("unexpected action");
            }
        });
    }

    private static String log(int seq, JsonObject receipt, String level) {
        JsonObject row = new JsonObject();
        row.addProperty("schemaVersion", 1);
        row.addProperty("sequence", seq);
        row.addProperty("timestamp", "2026-10-29T00:00:00.000Z");
        JsonObject source = new JsonObject();
        source.addProperty("kind", "installer");
        source.addProperty("id", "cli");
        row.add("source", source);
        row.addProperty("level", level);
        row.addProperty("component", "installer");
        row.addProperty("message", "Install preparation receipt: " + receipt);
        row.add("fields", new JsonObject());
        return row.toString();
    }

    @Test

    void actualMonitorConsumesOnlyCanonicalDebugStderr() throws Exception {
        var monitor = monitor();
        int sequence = 0;
        var control = new CliProcess.Control(System.nanoTime() + 10_000_000_000L, null);
        for (var row : rows())
            monitor.line(CliProcess.Stream.ERR, log(++ sequence, row, "debug"), control);
        assertTrue(monitor.preparationProof(SERIAL).readiness());
        assertThrows(IOException.class, () -> monitor().line(CliProcess.Stream.OUT, log(1,
                rows().get(0), "debug"), control));
        assertThrows(IOException.class, () -> monitor().line(CliProcess.Stream.ERR, log(1,
                rows().get(0), "info"), control));
    }
}
