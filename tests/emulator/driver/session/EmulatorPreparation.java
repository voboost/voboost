package ru.voboost.voyemu;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import ru.voboost.tests.HostProcess;
import ru.voboost.tests.MountSession;
import ru.voboost.tests.ProductRun;
import ru.voboost.tests.RunFiles;
import ru.voboost.tests.delivery.DeliveryLayout;
import ru.voboost.tests.delivery.DeliveryManifest;
import ru.voboost.tests.installer.cli.InstallerDriver;

/** Persistent guest preparation and ownership of a separate test session. */
public final class EmulatorPreparation implements AutoCloseable {
    private static final java.util.concurrent.locks.ReentrantLock START = new java.util.concurrent.locks.ReentrantLock(true);

    public record Candidate(Path file, Path app, MountSession mount, String manifest,
                            String release, String catalog) implements AutoCloseable {
        public static Candidate open(Path work, Path file) throws Exception {
            if (Files.exists(work.resolve("volume"), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Candidate mount path is already in use: " + work);
            }
            MountSession mount = MountSession.readOnlyDmg(work, file);
            try {
                check(work, "verify-dmg", List.of("/usr/bin/hdiutil", "verify", file.toString()));
                mount.attach();
                mountDevice(work.resolve("volume"));
                Path app = work.resolve("volume/Voboost Installer.app");
                check(work, "codesign", List.of("/usr/bin/codesign", "--verify", "--deep", "--strict", app.toString()));
                check(work, "verify-package", List.of(binary(app).toString(), "--verify-package"));
                Path manifest = app.resolve("Contents/Resources/voboost.json");
                Path catalog = EmulatorPreparation.catalog(app);
                String identity = DeliveryLayout.sha256Hex(Files.readAllBytes(manifest));
                String release = DeliveryManifest.verify(manifest).releaseIdentity();
                return new Candidate(file, app, mount, identity, release,
                        DeliveryLayout.sha256Hex(Files.readAllBytes(catalog)));
            } catch (Exception | Error failure) {
                try { mount.cleanup(); }
                catch (Exception cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        public JsonObject receipt() throws IOException {
            JsonObject result = new JsonObject();
            result.addProperty("file", file.toString());
            result.addProperty("manifest", manifest);
            result.addProperty("release", release);
            result.addProperty("catalog", catalog);
            result.addProperty("mountDevice", mountDevice(app.getParent()));
            return result;
        }

        public static Candidate claim(Path work, Path selected, JsonObject receipt) throws Exception {
            if (!selected.toString().equals(field(receipt, "file"))) throw new IOException("Prepared DMG differs");
            MountSession mount = MountSession.readOnlyDmg(work, selected);
            mount.claimAttached();
            Path volume = work.resolve("volume");
            if (!mountDevice(volume).equals(field(receipt, "mountDevice"))) {
                throw new IOException("Candidate mount identity changed: " + work);
            }
            return new Candidate(selected, volume.resolve("Voboost Installer.app"), mount,
                    field(receipt, "manifest"), field(receipt, "release"), field(receipt, "catalog"));
        }

        public static Candidate claimForCleanup(Path work, Path selected, JsonObject receipt) throws Exception {
            Path volume = work.resolve("volume");
            if (Files.isSymbolicLink(volume)) throw new IOException("Candidate mount path is a symlink");
            String device = null;
            if (Files.exists(volume) && !Files.getAttribute(volume, "unix:dev")
                    .equals(Files.getAttribute(work, "unix:dev"))) device = mountDevice(volume);
            return cleanup(selected, receipt, volume.resolve("Voboost Installer.app"),
                    MountSession.readOnlyDmg(work, selected), device);
        }

        static Candidate cleanup(Path selected, JsonObject receipt, Path app, MountSession mount,
                String mountedDevice) throws IOException {
            if (!selected.toString().equals(field(receipt, "file"))
                    || mountedDevice != null && !mountedDevice.equals(field(receipt, "mountDevice"))) {
                throw new IOException("Prepared candidate cleanup identity differs");
            }
            return new Candidate(selected, app, mount, field(receipt, "manifest"),
                    field(receipt, "release"), field(receipt, "catalog"));
        }

        private static String mountDevice(Path volume) throws IOException {
            if (!Files.getFileStore(volume).isReadOnly()) throw new IOException("Candidate mount is writable");
            return Files.getAttribute(volume, "unix:dev").toString();
        }

        @Override public void close() throws Exception {
            mount.cleanup();
        }
    }

    public record Lane(String role, String machineName, TestConfiguration configuration,
                       boolean agents, boolean pedestrianWarning, boolean cluster) {
        JsonObject identity() {
            JsonObject value = new JsonObject();
            value.addProperty("role", role);
            value.addProperty("machineName", machineName);
            value.addProperty("machine", configuration.machine().toString());
            value.addProperty("profile", configuration.profile());
            value.addProperty("scenario", configuration.scenario());
            value.addProperty("cluster", cluster);
            return value;
        }
    }

    public final Path work, app;
    public final ProductRun run;
    public final OwnedSession session;
    public final InstallerDriver installer;
    public final JsonObject catalog;
    public final String machineName;
    private final Lane lane;
    private final Candidate candidate;
    private final VoyemuClient client;
    private final JsonObject originalAgents;

    private EmulatorPreparation(Lane lane, Path work, Candidate candidate, ProductRun run,
            OwnedSession session, VoyemuClient client, InstallerDriver installer, JsonObject originalAgents)
            throws IOException {
        this.lane = lane;
        this.candidate = candidate;
        this.work = work;
        this.app = candidate == null ? null : candidate.app();
        this.run = run;
        this.session = session;
        this.client = client;
        this.installer = installer;
        this.catalog = app == null || session.cleanupVerified() ? null : RunFiles.read(catalog(app));
        this.machineName = lane.machineName();
        this.originalAgents = originalAgents;
    }

    public static void prepare(Lane lane, Path work, Path locks, Candidate candidate, String existing)
            throws Exception {
        if (!lane.cluster()) return;
        lane.configuration().verifyClusterImages();
        try (ProductRun run = ProductRun.open(locks, lane.configuration().machine(), work)) {
            var client = client(lane, work);
            var session = launch(lane, run, client);
            session.verifyAndroidReady();
            session.verifyQnxDelivery();
            var installer = driver(lane, work, candidate, client, run, session);
            if (lane.agents()) installer.prepareAgentPackage(existing);
            else installer.prepare(existing);
        }
    }

    public static EmulatorPreparation start(Lane lane, Path work, Path locks, Candidate candidate)
            throws Exception {
        ProductRun run = ProductRun.open(locks, lane.configuration().machine(), work);
        InstallerDriver.PedestrianWarningSession pw = null;
        try {
            var client = client(lane, work);
            var session = launch(lane, run, client);
            session.verifyTestUi();
            if (lane.cluster()) session.verifyQnxDelivery();
            InstallerDriver installer = candidate == null ? null : driver(lane, work, candidate, client, run, session);
            if (lane.agents()) {
                installer.requireAgentCandidate();
                var android = session.verifyAndroidReady().getAsJsonObject("deliveries")
                        .getAsJsonObject("android");
                // Quick Boot can restore an Android publisher from an earlier QNX boot.
                if ("restored".equals(field(android.getAsJsonObject("quickboot"), "mode"))) {
                    installer.restartBothAndVerify();
                }
                if (lane.pedestrianWarning()) {
                    pw = installer.pedestrianWarningSession(RunFiles.read(catalog(candidate.app())));
                    pw.prepare();
                }
            }
            return new EmulatorPreparation(lane, work, candidate, run, session, client, installer,
                    pw == null ? null : pw.originalState());
        } catch (Exception | Error failure) {
            try {
                if (pw != null) pw.close();
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            try { run.close(); }
            catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public JsonObject receipt() {
        JsonObject value = new JsonObject();
        value.addProperty("work", work.getFileName().toString());
        value.addProperty("session", session.session().id());
        if (originalAgents != null) value.add("originalAgents", originalAgents);
        return value;
    }

    public static EmulatorPreparation claim(Lane lane, Path parent, Path locks, Candidate candidate,
            JsonObject receipt, boolean reconcile) throws Exception {
        return claim(lane, parent, locks, candidate, receipt, reconcile, false);
    }

    public static EmulatorPreparation claimForStop(Lane lane, Path parent, Path locks, Candidate candidate,
            JsonObject receipt) throws Exception {
        return claim(lane, parent, locks, candidate, receipt, false, true);
    }

    private static Path sessionWork(Path parent, JsonObject receipt) throws IOException {
        String name = field(receipt, "work");
        if (!name.matches("session-[0-9]+")) throw new IOException("Invalid prepared session directory");
        return RunFiles.directory(parent.resolve(name));
    }

    public static boolean stopped(Lane lane, Path parent, JsonObject receipt) throws Exception {
        Path work = sessionWork(parent, receipt);
        return stopped(lane.configuration().machine(), parent, receipt, client(lane, work),
                lane.configuration().profile());
    }

    static boolean stopped(Path machine, Path parent, JsonObject receipt, VoyemuClient client,
            String profile) throws Exception {
        Path work = sessionWork(parent, receipt).toRealPath();
        JsonObject record = RunFiles.read(work.resolve("run.json"));
        if (!"complete".equals(field(record, "state"))) return false;
        if (!record.keySet().equals(java.util.Set.of("schemaVersion", "runId", "machine", "work", "state"))
                || !record.get("schemaVersion").isJsonPrimitive()
                || !record.getAsJsonPrimitive("schemaVersion").isNumber()
                || !"1".equals(record.get("schemaVersion").getAsString())
                || !field(record, "runId").matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                || !work.toString().equals(field(record, "work"))
                || !machine.toRealPath().toString().equals(field(record, "machine"))) {
            throw new IOException("Completed emulator run identity differs");
        }
        OwnedSession stopped = OwnedSession.resume(client, profile, machine, work.resolve("session"));
        if (!field(receipt, "session").equals(stopped.session().id())) {
            throw new IOException("Completed emulator session identity differs");
        }
        stopped.retainedStopProof(machine, work.resolve("session"));
        return true;
    }

    private static EmulatorPreparation claim(Lane lane, Path parent, Path locks, Candidate candidate,
            JsonObject receipt, boolean reconcile, boolean stopping) throws Exception {
        Path work = sessionWork(parent, receipt);
        var client = client(lane, work);
        ProductRun run = ProductRun.resume(locks, lane.configuration().machine(), work);
        try {
            var session = run.resumeSession(client, lane.configuration().profile());
            if (run.pending() != null && !reconcile || session.cleanupVerified() && !stopping
                    || !session.session().id().equals(field(receipt, "session"))) {
                throw new IOException("Prepared session is unavailable or requires reconciliation");
            }
            var installer = candidate == null || session.cleanupVerified()
                    ? null : driver(lane, work, candidate, client, run, session);
            return new EmulatorPreparation(lane, work, candidate, run, session, client, installer,
                    receipt.getAsJsonObject("originalAgents"));
        } catch (Exception | Error failure) {
            try { run.releaseLease(); }
            catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public InstallerDriver currentInstaller() throws Exception {
        return installer(candidate);
    }

    public InstallerDriver installer(Candidate candidate) throws Exception {
        return driver(lane, work, candidate, client, run, session);
    }

    @Override public void close() throws Exception {
        try (run) {
            if (originalAgents != null && run.pending() == null && !session.cleanupVerified()) {
                installer.restoreAgentSelection(catalog, originalAgents);
            }
        }
    }

    private static VoyemuClient client(Lane lane, Path work) throws IOException {
        return VoyemuClient.process(lane.configuration().voyemu(), RunFiles.directory(work.resolve("api")));
    }

    private static OwnedSession launch(Lane lane, ProductRun run, VoyemuClient client) throws Exception {
        // Voyemu locks session creation globally, then locks each started session independently.
        START.lockInterruptibly();
        try {
            return run.start(client, lane.configuration().profile(), "fresh".equals(lane.configuration().scenario()),
                    lane.cluster());
        } finally {
            START.unlock();
        }
    }

    private static InstallerDriver driver(Lane lane, Path work, Candidate candidate, VoyemuClient client,
            ProductRun run, OwnedSession session) throws Exception {
        var installer = new InstallerDriver(binary(candidate.app()),
                RunFiles.directory(work.resolve("observations-" + UUID.randomUUID())), client, session,
                lane.configuration().profile(), candidate.manifest(), candidate.release(), candidate.catalog(),
                run, lane.configuration().voyemu(), false);
        String driver = System.getProperty("emulatorDriver");
        if (driver == null) throw new IOException("emulatorDriver must name the built host test tool");
        installer.configureEmulatorDriver(Path.of(driver));
        installer.configureCandidatePackage(catalog(candidate.app()));
        return installer;
    }

    private static Path binary(Path app) {
        return app.resolve("Contents/MacOS/voboost-installer");
    }

    private static Path catalog(Path app) {
        return app.resolve("Contents/Resources/agents.json");
    }

    private static void check(Path work, String name, List<String> command) throws Exception {
        Path log = work.resolve(name + ".log");
        if (HostProcess.run(log, command, 60_000).exit() != 0) {
            throw new IOException("Candidate " + name + " failed. See " + log);
        }
    }

    public static String field(JsonObject value, String key) throws IOException {
        var field = value.get(key);
        if (field == null || !field.isJsonPrimitive() || !field.getAsJsonPrimitive().isString()) {
            throw new IOException("Prepared emulator field is missing: " + key);
        }
        return field.getAsString();
    }
}
