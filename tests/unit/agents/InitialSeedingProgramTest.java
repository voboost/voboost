package ru.voboost.tests.agents;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Policy orchestration evidence only; no mock result is functional acceptance. */
class InitialSeedingProgramTest {

    private static final Path EVIDENCE = Path.of("/host-policy/evidence.json");

    private static final InitialSeedingProgram.Scope SCOPE = new InitialSeedingProgram.Scope("s",
            "machine", "emulator-5554", "candidate");

    private static String boot(int value) {
        return String.format("%08d-1111-1111-1111-111111111111", value);
    }

    private static final class Port implements InitialSeedingProgram.Port {
        final List<String> actions = new ArrayList<>();
        boolean admitted = true, badAbsent, badOem, badCandidate, badFirst, badWriter, badRoot,
                unknownInstall;
        int cycle;
        InitialSeedingProgram.Baseline baseline;
        public InitialSeedingProgram.Scope scope() {
            return SCOPE;
        }
        public InitialSeedingProgram.Admission admit() {
            actions.add("admit");
            return new InitialSeedingProgram.Admission(admitted, "missing lifecycle capability",
                    List.of(EVIDENCE));
        }
        public InitialSeedingProgram.Absence removeNormallyAndVerify(int index) {
            cycle = index;
            actions.add("remove" + index);
            return new InitialSeedingProgram.Absence(SCOPE, boot(index * 2 + 1), true, true,
                    !badAbsent, List.of(EVIDENCE));
        }
        public void selectOem(InitialSeedingProgram.Baseline value) {
            actions.add("oem" + cycle);
            baseline = value;
        }
        public InitialSeedingProgram.Oem readOem() {
            actions.add("read-oem" + cycle);
            return new InitialSeedingProgram.Oem(SCOPE, boot(cycle * 2 + 1),
                    badOem ? new InitialSeedingProgram.Baseline(!baseline.forcedEv(),
                    baseline.pedestrianSound()) : baseline, List.of(EVIDENCE));
        }
        public InitialSeedingProgram.Installed installSameCandidate(int index) throws IOException {
            actions.add("install" + index);
            if (unknownInstall)
                throw new IOException("unknown install outcome");
            return new InitialSeedingProgram.Installed(badCandidate ? new InitialSeedingProgram.Scope("s", "other-machine", "emulator-5554", "candidate") : SCOPE, boot(cycle * 2 + 2), "a".repeat(32), BigInteger.ZERO, true, List.of(EVIDENCE));
        }
        public InitialSeedingProgram.FirstRead firstRead(InitialSeedingProgram.Installed installed) {
            actions.add("first" + cycle);
            List<InitialSeedingProgram.StateOperation> rows = new ArrayList<>();
            int seq = 1;
            for (String key : List.of("vehicle-drive-forced-ev/forced-ev-armed",
                    "vehicle-drive-pedestrian-warning/pedestrian-warning-armed")) {
                rows.add(new InitialSeedingProgram.StateOperation(key, "read",
                        badFirst ? "value" : "missing", badFirst ? false : null,
                        BigInteger.valueOf(seq++)));
                rows.add(new InitialSeedingProgram.StateOperation(key, "write-readback", "value",
                        key.contains("forced-ev/") ? baseline.forcedEv() : !baseline.pedestrianSound(), BigInteger.valueOf(seq++)));
            }
            return new InitialSeedingProgram.FirstRead(SCOPE, installed.boot(),
                    badWriter ? "b".repeat(32) : installed.writer(), rows, List.of(EVIDENCE));
        }
        public InitialSeedingProgram.RootRead readRoot(InitialSeedingProgram.Installed installed) {
            actions.add("root" + cycle);
            return new InitialSeedingProgram.RootRead(SCOPE, installed.boot(),
                    badRoot ? !baseline.forcedEv() : baseline.forcedEv(),
                    !baseline.pedestrianSound(), List.of(EVIDENCE));
        }
        public List<Path> restoreSuiteInstallationAndConfig() {
            actions.add("restore");
            return List.of(EVIDENCE);
        }
    }

    @Test

    void unsupportedAdmissionDispatchesNothing() throws Exception {
        Port port = new Port();
        port.admitted = false;
        var result = InitialSeedingProgram.run(port);
        assertFalse(result.started());
        assertFalse(result.semanticComplete());
        assertEquals(List.of("admit"), port.actions);
    }

    @Test

    void twoSequentialCleanBaselinesUseOneAuthorityAndRestoreOnlyAfterBoth() throws Exception {
        Port port = new Port();
        var result = InitialSeedingProgram.run(port);
        assertTrue(result.semanticComplete());
        assertEquals(2, result.samples().size());
        assertEquals(List.of("admit", "remove0", "oem0", "read-oem0", "install0", "first0", "root0",
                "remove1", "oem1", "read-oem1", "install1", "first1", "root1", "restore"),
                port.actions);
    }

    @Test

    void absenceAndOemReadbackAreRequiredBeforeInstall() {
        for (int fault = 0; fault < 2; fault++) {
            Port port = new Port();
            port.badAbsent = fault == 0;
            port.badOem = fault == 1;
            assertThrows(IOException.class, () -> InitialSeedingProgram.run(port));
            assertFalse(port.actions.contains("install0"));
            assertFalse(port.actions.contains("restore"));
        }
    }

    @Test

    void anotherMachineCurrentValueOrWriterCannotProveFirstRead() {
        for (int fault = 0; fault < 3; fault++) {
            Port port = new Port();
            port.badCandidate = fault == 0;
            port.badFirst = fault == 1;
            port.badWriter = fault == 2;
            assertThrows(IOException.class, () -> InitialSeedingProgram.run(port));
            assertFalse(port.actions.contains("remove1"));
            assertFalse(port.actions.contains("restore"));
        }
    }

    @Test

    void wrongRootValueAndUnknownInstallNeverReplayOrAttemptCleanup() {
        for (boolean unknown : new boolean[] {
            false, true
        }) {
            Port port = new Port();
            port.unknownInstall = unknown;
            port.badRoot = !unknown;
            assertThrows(IOException.class, () -> InitialSeedingProgram.run(port));
            assertEquals(1, port.actions.stream().filter("install0"::equals).count());
            assertFalse(port.actions.contains("remove1"));
            assertFalse(port.actions.contains("restore"));
        }
    }
}
