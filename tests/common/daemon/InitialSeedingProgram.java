package ru.voboost.tests.agents;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Two normal clean product lifecycles on one retained machine; never a root-state writer. */
public final class InitialSeedingProgram {

    public record Scope(String session, String machine, String serial, String candidate) {}

    public record Admission(boolean available, String reason, List<Path> evidence) {
        public Admission {
            evidence = List.copyOf(evidence);
        }
    }

    public record Baseline(boolean forcedEv, boolean pedestrianSound) {}

    public record Absence(Scope scope, String boot, boolean normalRemoval,
            boolean managedRootAbsent, boolean agentKeysAbsent, List<Path> evidence) {
        public Absence {
            evidence = List.copyOf(evidence);
        }
    }

    public record Oem(Scope scope, String boot, Baseline value, List<Path> evidence) {
        public Oem {
            evidence = List.copyOf(evidence);
        }
    }
    /** Floor must be authenticated at T2 before first reads; a post-export owner watermark cannot substitute. */
    public record Installed(Scope scope, String boot, String writer, BigInteger initialWriterFloor,
            boolean t2NoStateSeeding, List<Path> evidence) {
        public Installed {
            evidence = List.copyOf(evidence);
        }
    }

    public record StateOperation(String key, String operation, String outcome, Boolean value,
            BigInteger sequence) {}

    public record FirstRead(Scope scope, String boot, String writer,
            List<StateOperation> operations, List<Path> evidence) {
        public FirstRead {
            operations = List.copyOf(operations);
            evidence = List.copyOf(evidence);
        }
    }

    public record RootRead(Scope scope, String boot, boolean forcedEvArmed, boolean pedestrianArmed,
            List<Path> evidence) {
        public RootRead {
            evidence = List.copyOf(evidence);
        }
    }

    public record Sample(Baseline baseline, Absence absence, Oem oem, Installed installed,
            FirstRead first, RootRead root) {}

    public record Result(boolean semanticComplete, boolean started, String reason,
            List<Sample> samples, List<Path> evidence) {
        public Result {
            samples = List.copyOf(samples);
            evidence = List.copyOf(evidence);
        }
    }

    public interface Port {
        Scope scope();
        /** Must prove normal cleanup/install authority, OEM controls, first-read capture and restoration before any mutation. */
        Admission admit() throws Exception;
        Absence removeNormallyAndVerify(int cycle) throws Exception;
        void selectOem(Baseline baseline) throws Exception;
        Oem readOem() throws Exception;
        Installed installSameCandidate(int cycle) throws Exception;
        FirstRead firstRead(Installed installed) throws Exception;
        RootRead readRoot(Installed installed) throws Exception;
        List<Path> restoreSuiteInstallationAndConfig() throws Exception;
    }

    private static final List<Baseline> BASELINES = List.of(new Baseline(false, true),
            new Baseline(true, false));

    private static final String FEV = "vehicle-drive-forced-ev/forced-ev-armed",
            PW = "vehicle-drive-pedestrian-warning/pedestrian-warning-armed";

    private static void require(boolean value, String reason) throws IOException {
        if (!value)
            throw new IOException("initial seeding: " + reason);
    }

    private static boolean boot(String value) {
        return value != null && value.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    }

    private static void first(FirstRead first, Installed installed, String key,
            boolean expected) throws IOException {
        StateOperation read = null, commit = null;
        for (var operation : first.operations())
            if (key.equals(operation.key())) {
                require(operation.sequence() != null && operation.sequence().signum() > 0
                        && operation.sequence().bitLength() <= 64, "operation sequence");
                if ("read".equals(operation.operation()) && read == null)
                    read = operation;
                else if ("write-readback".equals(operation.operation()) && commit == null)
                    commit = operation;
            }
        require(read != null && commit != null, "fresh first-read/commit pair missing for " + key);
        require("missing".equals(read.outcome()) && read.value() == null,
                "initial read was not authoritative ABSENT");
        require("value".equals(commit.outcome())
                && Boolean.valueOf(expected).equals(commit.value()),
                "commit differs from initial OEM input");
        require(read.sequence().compareTo(installed.initialWriterFloor()) > 0
                && commit.sequence().compareTo(read.sequence()) > 0,
                "first read/commit not causally after authenticated T2 initial writer floor");
    }

    public static Result run(Port port) throws Exception {
        Scope scope = port.scope();
        Admission admission = port.admit();
        if (!admission.available())
            return new Result(false, false, admission.reason(), List.of(), admission.evidence());
        require(scope != null && scope.session() != null && scope.machine() != null
                && scope.serial() != null && scope.candidate() != null
                && !admission.evidence().isEmpty(),
                "retained authority and admission evidence required");
        List<Sample> samples = new ArrayList<>();
        List<Path> evidence = new ArrayList<>(admission.evidence());
        String previousInstalledBoot = null;
        for (int index = 0; index < BASELINES.size(); index++) {
            Baseline baseline = BASELINES.get(index);
            Absence absent = port.removeNormallyAndVerify(index);
            require(scope.equals(absent.scope()) && boot(absent.boot()) && absent.normalRemoval()
                    && absent.managedRootAbsent() && absent.agentKeysAbsent()
                    && !absent.evidence().isEmpty(),
                    "normal clean absence required before OEM baseline");
            if (previousInstalledBoot != null)
                require(!previousInstalledBoot.equals(absent.boot()),
                        "second cleanup requires a separately observed normal removal boot");
            port.selectOem(baseline);
            Oem oem = port.readOem();
            require(scope.equals(oem.scope()) && absent.boot().equals(oem.boot())
                    && baseline.equals(oem.value()) && !oem.evidence().isEmpty(),
                    "independent pre-install OEM baseline differs");
            Installed installed = port.installSameCandidate(index);
            require(scope.equals(installed.scope()) && boot(installed.boot())
                    && !installed.boot().equals(absent.boot()) && installed.writer() != null
                    && installed.writer().matches("[0-9a-f]{32}")
                    && installed.initialWriterFloor() != null
                    && installed.initialWriterFloor().signum() >= 0
                    && installed.initialWriterFloor().bitLength() <= 64
                    && installed.t2NoStateSeeding() && !installed.evidence().isEmpty(),
                    "same candidate install/T2-no-seeding/current-writer capture missing");
            FirstRead first = port.firstRead(installed);
            require(scope.equals(first.scope()) && installed.boot().equals(first.boot())
                    && installed.writer().equals(first.writer()) && !first.evidence().isEmpty(),
                    "first-read source scope differs");
            first(first, installed, FEV, baseline.forcedEv());
            first(first, installed, PW, !baseline.pedestrianSound());
            RootRead root = port.readRoot(installed);
            require(scope.equals(root.scope()) && installed.boot().equals(root.boot())
                    && root.forcedEvArmed() == baseline.forcedEv()
                    && root.pedestrianArmed() != baseline.pedestrianSound()
                    && !root.evidence().isEmpty(),
                    "independent present boolean root readback differs");
            samples.add(new Sample(baseline, absent, oem, installed, first, root));
            previousInstalledBoot = installed.boot();
        }
        // A failure or unknown action escapes immediately. No finally-based mutation/replay is permitted.
        List<Path> restored = port.restoreSuiteInstallationAndConfig();
        require(!restored.isEmpty(), "normal suite restoration proof missing");
        evidence.addAll(restored);
        return new Result(true, true,
                "Both clean ABSENT initial inputs committed and independently read back; suite restored", samples, evidence);
    }

    private InitialSeedingProgram() {}
}
