package ru.voboost.tests.installer.cli;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.time.Instant;
import ru.voboost.tests.agents.RuntimeObservation;
import ru.voboost.voyemu.VehicleArmed;
import ru.voboost.voyemu.VehicleCan;
import ru.voboost.voyemu.VehicleUi;
import ru.voboost.voyemu.UiEvidence;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import ru.voboost.tests.agents.InitialSeedingProgram;
import ru.voboost.tests.logging.InstallerLogs;
import java.util.Set;
import java.util.concurrent.Callable;
import ru.voboost.tests.RunFiles;
import ru.voboost.tests.agents.AgentSettings;

/** Honest admission for the currently missing early install capture; never infers it from an export. */
public final class InitialSeedingAdmission {
    public static final String MISSING =
            "Authenticated T2 current-writer capture before the first relevant state read, "
                    + "together with same-candidate install-no-state-seeding proof, is unavailable; "
                    + "post-export watermark is not an initial-read floor";

    public static JsonObject blocked(String session, String machine, String serial,
            String candidate) throws IOException {
        for (String value : new String[] {session, machine, serial, candidate}) {
            if (value == null || value.isBlank()) {
                throw new IOException("initial seeding admission requires retained run scope");
            }
        }
        JsonObject proof = new JsonObject();
        proof.addProperty("caseNumber", 24);
        proof.addProperty("semanticFunctionalStatus", "BLOCKED");
        proof.addProperty("functionalAcceptance", false);
        proof.addProperty("mutationStarted", false);
        proof.addProperty("session", session);
        proof.addProperty("machine", machine);
        proof.addProperty("serial", serial);
        proof.addProperty("candidate", candidate);
        proof.addProperty("reason", MISSING);
        return proof;
    }

    public static void requireRestorableConfiguration(JsonObject catalog, JsonObject agents,
            String startup,
            String runningStartup) throws IOException {
        if (agents == null || agents.isEmpty() || !"interface".equals(startup)
                || !"interface".equals(runningStartup)) {
            throw new IOException("normal seeding restore requires the current interface suite");
        }
        if (!AgentSettings.checkboxes(catalog).keySet().equals(agents.keySet())) {
            throw new IOException("complete candidate suite configuration required");
        }
        for (String id : agents.keySet()) {
            if (!agents.get(id).isJsonObject()) {
                throw new IOException("canonical suite agent configuration required");
            }
            JsonObject config = agents.getAsJsonObject(id);
            if (!config.keySet().equals(Set.of("enabled", "options"))
                    || !config.get("enabled").isJsonPrimitive()
                    || !config.getAsJsonPrimitive("enabled").isBoolean()
                    || !config.get("options").isJsonObject()) {
                throw new IOException("canonical suite agent configuration required");
            }
            if (config.get("enabled").getAsBoolean()
                    && !AgentSettings.transition(catalog, agents, id, true).equals(agents)) {
                throw new IOException("suite configuration conflicts with normal checkbox semantics");
            }
            if (!config.getAsJsonObject("options").isEmpty()) {
                throw new IOException("normal UI option restore is unavailable for " + id);
            }
        }
    }

    /** Parsed only after the fixed root-owned observer authenticated the receipt and boot. */
    public record Capture(InitialSeedingProgram.Installed installed,
            InitialSeedingProgram.FirstRead first) {}

    public static Capture capture(JsonObject receipt, InitialSeedingProgram.Scope scope,
            String boot, String writer, String transaction, Path evidence,
            InstallerLogs.Snapshot snapshot) throws IOException {
        if (receipt == null || scope == null || evidence == null || snapshot == null || boot == null
                || writer == null || transaction == null || scope.candidate() == null
                || scope.session() == null || scope.session().isBlank()
                || scope.machine() == null || scope.machine().isBlank()
                || scope.serial() == null || scope.serial().isBlank()
                || !receipt.keySet().equals(Set.of("schemaVersion", "bootId", "writerId",
                        "transactionId", "candidate", "initialWriterFloor", "installState",
                        "captureState", "operations"))
                || !number(receipt, "schemaVersion").equals(BigInteger.ONE)
                || !boot.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                || !writer.matches("[0-9a-f]{32}")
                || !transaction.matches("[0-9a-f]{64}")
                || !scope.candidate().matches("[0-9a-f]{64}")
                || !boot.equals(string(receipt, "bootId"))
                || !writer.equals(string(receipt, "writerId"))
                || !transaction.equals(string(receipt, "transactionId"))
                || !scope.candidate().equals(string(receipt, "candidate"))
                || !"unseeded".equals(string(receipt, "installState"))
                || !"complete".equals(string(receipt, "captureState"))) {
            throw new IOException("initial seeding receipt scope or authority differs");
        }
        BigInteger floor = number(receipt, "initialWriterFloor");
        if (!receipt.get("operations").isJsonArray()
                || receipt.getAsJsonArray("operations").size() != 4)
            throw new IOException("initial seeding receipt operations incomplete");
        List<InitialSeedingProgram.StateOperation> operations = new ArrayList<>();
        Set<String> keys = Set.of("vehicle-drive-forced-ev/forced-ev-armed",
                "vehicle-drive-pedestrian-warning/pedestrian-warning-armed");
        java.util.Map<String, Boolean> read = new java.util.HashMap<>();
        BigInteger previous = floor;
        for (var item : receipt.getAsJsonArray("operations")) {
            if (!item.isJsonObject()) throw new IOException("invalid initial seeding operation");
            JsonObject row = item.getAsJsonObject();
            String key = string(row, "key"), operation = string(row, "operation"), outcome = string(row, "outcome");
            BigInteger sequence = number(row, "sequence");
            if (!keys.contains(key) || sequence.compareTo(previous) <= 0)
                throw new IOException("initial seeding operation order differs");
            previous = sequence;
            Boolean value = null;
            if ("read".equals(operation)) {
                if (!row.keySet().equals(Set.of("key", "operation", "outcome", "sequence"))
                        || !"missing".equals(outcome) || read.putIfAbsent(key, false) != null)
                    throw new IOException("first state read is not uniquely absent");
            } else if ("write-readback".equals(operation)) {
                if (!row.keySet().equals(Set.of("key", "operation", "outcome", "value", "sequence"))
                        || !"value".equals(outcome) || !Boolean.FALSE.equals(read.get(key))
                        || !row.get("value").isJsonPrimitive() || !row.getAsJsonPrimitive("value").isBoolean())
                    throw new IOException("first state commit is not a boolean readback");
                value = row.get("value").getAsBoolean();
                read.put(key, true);
            } else throw new IOException("unknown initial seeding operation");
            operations.add(new InitialSeedingProgram.StateOperation(key, operation, outcome, value, sequence));
        }
        if (read.size() != 2 || read.containsValue(false))
            throw new IOException("initial seeding receipt has incomplete key pairs");
        if (!boot.equals(snapshot.boot()) || !writer.equals(snapshot.writerId())
                || snapshot.watermark() == null || snapshot.sequence() == null)
            throw new IOException("initial seeding seal writer scope differs");
        int seals = 0;
        for (var event : snapshot.daemonEvents()) {
            if (!"initial-seeding".equals(event.component())
                    || !"initial-state-capture-sealed".equals(event.message())) continue;
            JsonObject fields = event.fields();
            if (fields == null || !fields.keySet().equals(Set.of("transactionId", "candidate", "lastSequence"))
                    || !transaction.equals(string(fields, "transactionId"))
                    || !scope.candidate().equals(string(fields, "candidate"))
                    || !previous.toString().equals(string(fields, "lastSequence"))
                    || event.sequence() == null || event.sequence().compareTo(previous) <= 0
                    || event.sequence().compareTo(snapshot.sequence()) > 0
                    || event.sequence().compareTo(snapshot.watermark()) > 0
                    || !snapshot.exportedSequences().contains(event.sequence()))
                throw new IOException("initial seeding seal is not a current writer export");
            seals++;
        }
        if (seals != 1) throw new IOException("unique initial seeding seal missing");
        var installed = new InitialSeedingProgram.Installed(scope, boot, writer, floor, true, List.of(evidence));
        var first = new InitialSeedingProgram.FirstRead(scope, boot, writer, operations, List.of(evidence));
        return new Capture(installed, first);
    }

    private static String string(JsonObject value, String key) throws IOException {
        if (!value.has(key) || !value.get(key).isJsonPrimitive()
                || !value.getAsJsonPrimitive(key).isString())
            throw new IOException("initial seeding string missing: " + key);
        return value.get(key).getAsString();
    }

    private static BigInteger number(JsonObject value, String key) throws IOException {
        if (!value.has(key) || !value.get(key).isJsonPrimitive()
                || !value.getAsJsonPrimitive(key).isNumber()
                || !value.get(key).getAsString().matches("0|[1-9][0-9]{0,19}"))
            throw new IOException("initial seeding sequence missing: " + key);
        BigInteger number = new BigInteger(value.get(key).getAsString());
        if (number.bitLength() > 64) throw new IOException("initial seeding sequence overflow");
        return number;
    }

    /** Re-reads retained operations, runtime observations and authenticated owner logs. */
    public static boolean requireTerminal(JsonObject proof, Path verifiedInstaller, Path actualCatalog) throws IOException {
        try {
            return verifyTerminal(proof, verifiedInstaller, actualCatalog);
        } catch (IOException failure) {
            throw failure;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("case24 verification interrupted", interrupted);
        } catch (Exception failure) {
            throw new IOException("case24 terminal evidence cannot be re-evaluated", failure);
        }
    }

    private static boolean verifyTerminal(JsonObject proof, Path verifiedInstaller, Path actualCatalog) throws Exception {
        if (proof == null) throw new IOException("case24 terminal proof missing");
        if ("BLOCKED".equals(string(proof, "semanticFunctionalStatus"))) {
            requireUnmutatedBlocked(proof);
            return false;
        }
        if (!number(proof, "caseNumber").equals(BigInteger.valueOf(24))
                || !"PASS".equals(string(proof, "semanticFunctionalStatus"))
                || !booleanField(proof, "functionalAcceptance") || !booleanField(proof, "mutationStarted")
                || verifiedInstaller == null || actualCatalog == null
                || !verifiedInstaller.isAbsolute() || !actualCatalog.isAbsolute()
                || !Files.isRegularFile(verifiedInstaller, LinkOption.NOFOLLOW_LINKS)
                || !Files.isExecutable(verifiedInstaller) || !Files.isRegularFile(actualCatalog, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("case24 requires the selected verified package");
        Path cli = verifiedInstaller.toRealPath(), packageCatalog = actualCatalog.toRealPath();
        JsonObject rawScope = object(proof, "scope");
        if (!rawScope.keySet().equals(Set.of("session", "machine", "serial", "candidate")))
            throw new IOException("case24 retained scope differs");
        var scope = new InitialSeedingProgram.Scope(string(rawScope, "session"), string(rawScope, "machine"),
                string(rawScope, "serial"), string(rawScope, "candidate"));
        String release = string(proof, "releaseIdentity"), catalog = string(proof, "catalogIdentity");
        if (!scope.session().matches("[0-9a-f]{32}") || !Path.of(scope.machine()).isAbsolute()
                || !InstallerDriver.emulator(scope.serial()) || !scope.candidate().matches("[0-9a-f]{64}")
                || !catalog.matches("[0-9a-f]{64}") || release.isBlank())
            throw new IOException("case24 retained candidate scope differs");
        Path catalogPath = path(proof, "candidateCatalogPath", false);
        if (Files.size(catalogPath) > 4 * 1024 * 1024 || Files.mismatch(catalogPath, packageCatalog) != -1)
            throw new IOException("retained catalog differs from selected verified package");
        JsonObject candidateCatalog = RunFiles.read(catalogPath);
        if (!proof.has("samples") || !proof.get("samples").isJsonArray() || proof.getAsJsonArray("samples").size() != 2)
            throw new IOException("two complete clean seeding cycles required");
        String[] phases = {"seed-first", "seed-second"};
        boolean[] expectedFev = {false, true}, expectedSound = {true, false};
        Path work = null; String runId = null, priorInstalledBoot = null; int priorSequence = 0;
        RuntimeObservation original = null; String originalBoot = null;
        for (int index = 0; index < 2; index++) {
            var element = proof.getAsJsonArray("samples").get(index);
            if (!element.isJsonObject()) throw new IOException("case24 sample must be an object");
            JsonObject sample = element.getAsJsonObject(), baseline = object(sample, "baseline");
            if (booleanField(baseline, "forcedEv") != expectedFev[index]
                    || booleanField(baseline, "pedestrianSound") != expectedSound[index])
                throw new IOException("case24 OEM baseline order differs");
            JsonObject absence = object(sample, "absence"), oem = object(sample, "oem"), captureRef = object(sample, "capture");
            String absentBoot = string(absence, "boot"), boot = string(captureRef, "boot");
            if (!uuid(absentBoot) || !uuid(boot) || absentBoot.equals(boot)
                    || (priorInstalledBoot != null && priorInstalledBoot.equals(absentBoot))
                    || !absentBoot.equals(string(oem, "boot"))
                    || booleanField(oem, "forcedEv") != expectedFev[index]
                    || booleanField(oem, "pedestrianSound") != expectedSound[index])
                throw new IOException("case24 clean boot or OEM scope differs");
            Path uninstall = path(absence, "uninstallOperation", false);
            Path cycleWork = operationWork(uninstall);
            if (work == null) {
                work = cycleWork; runId = runId(work, scope);
                JsonObject originalRaw = RunFiles.read(inWork(path(proof, "originalRuntimeEvidence", false), work));
                originalBoot = string(originalRaw, "boot_id");
                original = runtime(originalRaw, release, scope.candidate(), catalog);
                inWork(catalogPath, work);
            }
            if (!work.equals(cycleWork)) throw new IOException("case24 operation crossed run owner");
            priorSequence = operation(uninstall, work, runId, scope, "UNINSTALL", phases[index],
                    priorInstalledBoot == null ? originalBoot : priorInstalledBoot, priorSequence, cli);
            JsonObject absentReceipt = RunFiles.read(inWork(path(absence, "receipt", false), work));
            InstallerDriver.requireAbsence(absentReceipt);
            if (!absentReceipt.equals(RunFiles.read(processOutput(uninstall, work))))
                throw new IOException("normal removal receipt differs from retained CLI stdout");
            if (!absentBoot.equals(string(absentReceipt, "bootId"))) throw new IOException("normal removal boot differs");
            rootAbsent(RunFiles.read(inWork(path(absence, "rootObservationPath", false), work)), absentBoot);
            oem(inWork(path(oem, "forcedEvEvidence", false), work), scope.session(), absentBoot,
                    VehicleUi.Feature.FORCED_EV, expectedFev[index], true);
            oem(inWork(path(oem, "pedestrianEvidence", false), work), scope.session(), absentBoot,
                    VehicleUi.Feature.PEDESTRIAN_WARNING, expectedSound[index], false);
            priorSequence = operation(path(sample, "installOperation", false), work, runId, scope,
                    "INSTALL", phases[index], absentBoot, priorSequence, cli);
            String writer = string(captureRef, "writer"), transaction = string(captureRef, "transaction");
            if (!scope.candidate().equals(transaction)) throw new IOException("install transaction differs from candidate");
            Path official = inWork(path(captureRef, "officialObservationPath", false), work);
            JsonObject receipt = seedingObservation(RunFiles.read(official), boot);
            Path archive = inWork(path(captureRef, "logsArchive", false), work);
            Path directory = inWork(path(captureRef, "logsDirectory", true), work);
            Instant start = Instant.parse(string(captureRef, "logsStartedAt"));
            Instant end = Instant.parse(string(captureRef, "logsEndedAt"));
            InstallerLogs.Snapshot snapshot = InstallerLogs.read(archive, directory, scope.serial(), release,
                    scope.candidate(), boot, start, end);
            Capture captured = capture(receipt, scope, boot, writer, transaction, official, snapshot);
            requireCommit(captured.first(), "vehicle-drive-forced-ev/forced-ev-armed", expectedFev[index]);
            requireCommit(captured.first(), "vehicle-drive-pedestrian-warning/pedestrian-warning-armed", !expectedSound[index]);
            JsonObject root = RunFiles.read(inWork(path(sample, "rootObservationPath", false), work));
            if (VehicleArmed.read(root, boot) != expectedFev[index]
                    || VehicleArmed.readPedestrian(root, boot) == expectedSound[index])
                throw new IOException("case24 independent root readback differs");
            priorInstalledBoot = boot;
        }
        JsonObject restored = RunFiles.read(inWork(path(proof, "restorationEvidence", false), work));
        Path restoreUninstall = inWork(path(restored, "restoreUninstallOperation", false), work);
        priorSequence = operation(restoreUninstall, work, runId, scope, "UNINSTALL", "seed-restore", priorInstalledBoot, priorSequence, cli);
        JsonObject restoreReceipt = RunFiles.read(inWork(path(restored, "restoreUninstallReceipt", false), work));
        InstallerDriver.requireAbsence(restoreReceipt);
        if (!restoreReceipt.equals(RunFiles.read(processOutput(restoreUninstall, work))))
            throw new IOException("restore removal receipt differs from retained CLI stdout");
        String restoreBoot = string(restoreReceipt, "bootId");
        if (!uuid(restoreBoot) || restoreBoot.equals(priorInstalledBoot)) throw new IOException("restore removal boot differs");
        rootAbsent(RunFiles.read(inWork(path(restored, "restoreRootAbsenceEvidence", false), work)), restoreBoot);
        operation(path(restored, "restoreInstallOperation", false), work, runId, scope,
                "INSTALL", "seed-restore", restoreBoot, priorSequence, cli);
        JsonObject restoredRaw = RunFiles.read(inWork(path(restored, "runtimeOfficialEvidence", false), work));
        if (!string(restored, "bootId").equals(string(restoredRaw, "boot_id"))) throw new IOException("restore runtime boot differs");
        RuntimeObservation current = runtime(restoredRaw, release, scope.candidate(), catalog);
        JsonObject expectedAgents = AgentSettings.snapshot(candidateCatalog, original);
        requireRestorableConfiguration(candidateCatalog, expectedAgents, original.startup(), original.runningStartup());
        if (!original.startup().equals(current.startup()) || !original.runningStartup().equals(current.runningStartup())
                || !expectedAgents.equals(AgentSettings.snapshot(candidateCatalog, current))
                || !original.startup().equals(string(restored, "startup"))
                || !original.runningStartup().equals(string(restored, "runningStartup"))
                || !expectedAgents.equals(object(restored, "agents")))
            throw new IOException("normal suite restoration differs from original official configuration");
        return true;
    }

    private static JsonObject object(JsonObject value, String name) throws IOException {
        if (value == null || !value.has(name) || !value.get(name).isJsonObject()) throw new IOException("case24 object missing: " + name);
        return value.getAsJsonObject(name);
    }
    private static boolean booleanField(JsonObject value, String name) throws IOException {
        if (value == null || !value.has(name) || !value.get(name).isJsonPrimitive() || !value.getAsJsonPrimitive(name).isBoolean())
            throw new IOException("case24 boolean missing: " + name);
        return value.get(name).getAsBoolean();
    }
    private static boolean uuid(String value) { return value != null && value.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"); }
    private static Path path(JsonObject value, String field, boolean directory) throws IOException {
        Path path;
        try { path = Path.of(string(value, field)); } catch (RuntimeException error) { throw new IOException("invalid case24 evidence path", error); }
        if (!path.isAbsolute() || !path.normalize().equals(path)
                || (directory ? !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) : !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)))
            throw new IOException("canonical retained case24 evidence required: " + field);
        return path.toRealPath();
    }
    private static Path inWork(Path value, Path work) throws IOException {
        if (!value.startsWith(work)) throw new IOException("case24 evidence crossed retained work scope");
        return value;
    }
    private static Path operationWork(Path operation) throws IOException {
        Path directory = operation.getParent(), operations = directory == null ? null : directory.getParent();
        if (!operation.getFileName().toString().equals("operation.json") || directory == null
                || !directory.getFileName().toString().matches("[0-9]{6}") || operations == null
                || !operations.getFileName().toString().equals("operations") || operations.getParent() == null)
            throw new IOException("case24 retained operation path differs");
        return operations.getParent().toRealPath();
    }
    private static String runId(Path work, InitialSeedingProgram.Scope scope) throws IOException {
        JsonObject run = RunFiles.read(work.resolve("run.json"));
        if (!number(run, "schemaVersion").equals(BigInteger.ONE)
                || !Path.of(scope.machine()).toRealPath().equals(Path.of(string(run, "machine")).toRealPath())
                || !work.toString().equals(string(run, "work")) || !"ready".equals(string(run, "state"))
                || !uuid(string(run, "runId"))) throw new IOException("case24 run owner differs or is not idle");
        return string(run, "runId");
    }
    private static int operation(Path file, Path work, String runId, InitialSeedingProgram.Scope scope,
            String kind, String phase, String priorBoot, int priorSequence,
            Path verifiedInstaller) throws IOException {
        if (!operationWork(file).equals(work)) throw new IOException("case24 operation crossed run");
        int sequence = Integer.parseInt(file.getParent().getFileName().toString());
        JsonObject operation = RunFiles.read(file), input = object(operation, "input");
        if (!number(operation, "schemaVersion").equals(BigInteger.ONE)
                || sequence <= priorSequence || !runId.equals(string(operation, "runId"))
                || !scope.session().equals(string(operation, "sessionId")) || !kind.equals(string(operation, "kind"))
                || !"verified".equals(string(operation, "state")) || !phase.equals(string(input, "phase"))
                || !scope.candidate().equals(string(input, "manifest")) || !scope.serial().equals(string(input, "device"))
                || (priorBoot != null && !priorBoot.equals(string(input, "priorBoot")))
                || !operation.has("evidence") || !operation.get("evidence").isJsonArray() || operation.getAsJsonArray("evidence").isEmpty())
            throw new IOException("case24 normal operation lacks verified retained authority");
        JsonObject process = object(input, "process");
        if (!process.has("command") || !process.get("command").isJsonArray()) throw new IOException("case24 process command missing");
        var command = process.getAsJsonArray("command");
        List<String> expected = "INSTALL".equals(kind)
                ? List.of("--install", "--emulator-install-acceptance", "--device", scope.serial())
                : List.of("--uninstall", "--emulator-cache-recovery-acceptance", "--device", scope.serial());
        if (command.size() != expected.size() + 1 || !command.get(0).isJsonPrimitive()
                || !command.get(0).getAsJsonPrimitive().isString() || !Path.of(command.get(0).getAsString()).isAbsolute())
            throw new IOException("case24 closed installer command differs");
        if (!Path.of(command.get(0).getAsString()).toRealPath().equals(verifiedInstaller))
            throw new IOException("case24 operation used another executable");
        for (int i = 0; i < expected.size(); i++)
            if (!command.get(i + 1).isJsonPrimitive() || !command.get(i + 1).getAsJsonPrimitive().isString()
                    || !expected.get(i).equals(command.get(i + 1).getAsString()))
                throw new IOException("case24 closed installer argument differs");
        Path stdout = processOutput(file, work);
        if ("INSTALL".equals(kind)) {
            if (Files.size(stdout) > 4 * 1024 * 1024) throw new IOException("case24 installer stdout exceeds bound");
            long terminals;
            try (var lines = Files.lines(stdout)) {
                terminals = lines.filter(line -> line.equals("Install completed: transaction " + scope.candidate())).count();
            }
            if (terminals != 1) throw new IOException("actual install terminal transaction missing or duplicated");
        }
        for (var item : operation.getAsJsonArray("evidence")) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) throw new IOException("operation evidence path differs");
            Path relative = Path.of(item.getAsString());
            Path target = work.resolve(relative).normalize();
            if (relative.isAbsolute() || !target.startsWith(work) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                    || !target.toRealPath().startsWith(work)) throw new IOException("operation evidence crossed retained run");
        }
        return sequence;
    }
    private static Path processOutput(Path operationFile, Path work) throws IOException {
        JsonObject operation = RunFiles.read(operationFile), process = object(object(operation, "input"), "process");
        Path relative = Path.of(string(process, "stdout")), output = work.resolve(relative).normalize();
        if (relative.isAbsolute() || !output.startsWith(work) || !Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS)
                || !output.toRealPath().startsWith(work)) throw new IOException("case24 process stdout crossed run scope");
        boolean retained = false;
        if (operation.has("evidence") && operation.get("evidence").isJsonArray())
            for (var item : operation.getAsJsonArray("evidence"))
                if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()
                        && item.getAsString().equals(relative.toString())) retained = true;
        if (!retained) throw new IOException("case24 CLI stdout is not terminal evidence");
        return output.toRealPath();
    }
    private static void rootAbsent(JsonObject observation, String boot) throws IOException {
        JsonObject data = object(observation, "data");
        if (!number(observation, "schema").equals(BigInteger.TWO) || !"voboost".equals(string(observation, "source"))
                || !"ready".equals(string(observation, "status")) || !boot.equals(string(observation, "boot_id"))
                || !"absent".equals(string(data, "root")) || !"fixed-path-owner-mode-boot-fenced".equals(string(data, "root_proof")))
            throw new IOException("case24 independent managed-root absence differs");
    }
    private static void oem(Path file, String session, String boot, VehicleUi.Feature feature, boolean wanted, boolean fev) throws IOException {
        JsonObject observation = RunFiles.read(file), ui = object(observation, "ui"), can = object(observation, "can");
        if (!boot.equals(string(observation, "bootId")) || !boot.equals(string(ui, "boot_id"))
                || !session.equals(string(ui, "session_id")) || !"voyemu.ui.v1".equals(string(ui, "format"))
                || booleanField(observation, "checked") != wanted
                || VehicleUi.feature(UiEvidence.surface(ui, "primary"), feature).checked() != wanted
                || VehicleCan.read(can, session, boot).selected(fev) != wanted)
            throw new IOException("case24 OEM UI and CAN baseline differ");
    }
    private static JsonObject seedingObservation(JsonObject observation, String boot) throws IOException {
        JsonObject data = object(observation, "data"), file = object(object(data, "files"), "initial-seeding");
        if (!number(observation, "schema").equals(BigInteger.TWO) || !"voboost".equals(string(observation, "source"))
                || !"ready".equals(string(observation, "status")) || !boot.equals(string(observation, "boot_id"))
                || !"present".equals(string(data, "root")) || !"fixed-path-owner-mode-boot-fenced".equals(string(data, "root_proof"))
                || !"present".equals(string(file, "status")) || !booleanField(file, "current")
                || !"same-boot-retained-file".equals(string(file, "proof")) || booleanField(file, "current_writer_verified")
                || string(file, "revision").isBlank()) throw new IOException("case24 fixed root receipt unavailable");
        return object(file, "data");
    }
    private static void requireCommit(InitialSeedingProgram.FirstRead first, String key, boolean expected) throws IOException {
        for (var operation : first.operations()) if (key.equals(operation.key()) && "write-readback".equals(operation.operation())) {
            if (!Boolean.valueOf(expected).equals(operation.value())) throw new IOException("first commit differs from OEM baseline");
            return;
        }
        throw new IOException("first commit missing");
    }
    private static RuntimeObservation runtime(JsonObject raw, String release, String manifest, String catalog) throws IOException {
        JsonObject publication = object(object(object(raw, "data"), "files"), "runtime");
        String generation = string(object(publication, "data"), "generationId");
        return RuntimeObservation.read(raw, new RuntimeObservation.Identity(string(raw, "boot_id"), release, manifest, catalog, generation));
    }

    public static <T> T thenIndependent(Callable<Path> seeding, Callable<T> independent,
            Path verifiedInstaller, Path actualCatalog) throws Exception {
        requireTerminal(RunFiles.read(seeding.call()), verifiedInstaller, actualCatalog);
        return independent.call();
    }

    /** A blocked admission can continue; an unknown dispatch exception escapes without replay. */
    public static <T> T thenIndependent(Callable<Path> seeding,
            Callable<T> independent) throws Exception {
        JsonObject proof = RunFiles.read(seeding.call());
        requireUnmutatedBlocked(proof);
        return independent.call();
    }

    static void requireUnmutatedBlocked(JsonObject proof) throws IOException {
        if (!proof.has("caseNumber") || !proof.get("caseNumber").isJsonPrimitive()
                || !proof.getAsJsonPrimitive("caseNumber").isNumber()
                || !"24".equals(proof.get("caseNumber").getAsString())
                || !proof.has("semanticFunctionalStatus")
                || !proof.get("semanticFunctionalStatus").isJsonPrimitive()
                || !proof.getAsJsonPrimitive("semanticFunctionalStatus").isString()
                || !"BLOCKED".equals(proof.get("semanticFunctionalStatus").getAsString())) {
            throw new IOException("only an actual unmutated case24 admission may continue");
        }
        for (String field : new String[] {"mutationStarted", "functionalAcceptance"}) {
            if (!proof.has(field) || !proof.get(field).isJsonPrimitive()
                    || !proof.getAsJsonPrimitive(field).isBoolean()
                    || proof.get(field).getAsBoolean()) {
                throw new IOException("case24 has no safe unmutated admission: " + field);
            }
        }
    }

    private InitialSeedingAdmission() {}
}
