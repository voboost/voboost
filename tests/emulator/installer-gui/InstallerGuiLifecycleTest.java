package ru.voboost.tests.installer.gui;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.voboost.voyemu.EmulatorFixture;
import ru.voboost.voyemu.EmulatorPreparation;
import ru.voboost.tests.AcceptancePlan;
import ru.voboost.tests.AcceptanceReport;
import ru.voboost.tests.CaseEvidence;
import ru.voboost.tests.GateStatus;
import ru.voboost.tests.HostProcess;
import ru.voboost.tests.MountSession;
import ru.voboost.tests.ProductRun;
import ru.voboost.tests.RunFiles;
import ru.voboost.tests.delivery.DeliveryLayout;
import ru.voboost.tests.delivery.DeliveryManifest;
import ru.voboost.tests.installer.cli.InstallerDriver;
import ru.voboost.voyemu.OwnedSession;
import ru.voboost.voyemu.TestConfiguration;
import ru.voboost.voyemu.VoyemuClient;

/** Explicit packaged native GUI subset. Other GUI cases remain required and unexecuted. */
@Order(2)
@ExtendWith(EmulatorFixture.class)
class InstallerGuiLifecycleTest {
    private static Path input(String key) throws IOException {
        String raw = System.getProperty(key);
        if (raw == null || !Path.of(raw).isAbsolute()) throw new IOException("BLOCKED: absolute " + key + " required");
        return Path.of(raw);
    }
    private static void host(Path work, String name, List<String> command) throws Exception {
        if (HostProcess.run(work.resolve(name + ".log"), command, 60000).exit() != 0) throw new IOException("candidate package check failed: " + name);
    }
    private static boolean mounted(Path mount) throws IOException {
        return Files.exists(mount) && !Files.getAttribute(mount, "unix:dev").equals(Files.getAttribute(mount.getParent(), "unix:dev"));
    }
    @Test void genuineGuiConnectsInstallsReopensCancelsAndUninstalls(EmulatorPreparation prepared) throws Exception {
        if (prepared != null) {
            GuiScenario.execute(input("guiLauncher"), input("guiObserver"), prepared.app,
                    prepared.currentInstaller(), prepared.run, prepared.session, prepared.work.resolve("gui"));
            return;
        }
        if (!"true".equals(System.getProperty("nativeGuiLifecycle"))) throw new IOException("BLOCKED: explicit nativeGuiLifecycle=true required");
        if (System.getProperty("resumeRun") != null) throw new IOException("BLOCKED: retained native GUI owner needs scenario-specific read-only reconciliation, never replay");
        if (!"true".equals(System.getProperty("withCluster"))) throw new IOException("BLOCKED: withCluster=true required");
        Path dmg = input("candidateDmg"), root = input("testWorkDir"), launcher = input("guiLauncher"), observer = input("guiObserver");
        if (!Files.isExecutable(launcher) || !Files.isExecutable(observer)) throw new IOException("BLOCKED: compiled native test tools required");
        TestConfiguration config = TestConfiguration.fromSystemProperties();
        Files.createDirectories(root);
        Path work = Files.createTempDirectory(root, "native-gui-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Path packageWork = RunFiles.directory(work.resolve("package")), mount = packageWork.resolve("volume");
        MountSession packageMount = new MountSession(packageWork, new MountSession.Backend() {
            public void attach() throws Exception { host(packageWork, "attach", List.of("/usr/bin/hdiutil", "attach", "-readonly", "-nobrowse", "-mountpoint", mount.toString(), dmg.toString())); }
            public boolean mounted() throws Exception { return InstallerGuiLifecycleTest.mounted(mount); }
            public void detach() throws Exception { host(packageWork, "detach", List.of("/usr/bin/hdiutil", "detach", mount.toString())); }
            public void removeWork() throws Exception { Files.deleteIfExists(mount); }
        });
        JsonObject result = new JsonObject(); result.addProperty("schemaVersion", 1); result.addProperty("candidateDmg", dmg.toString());
        result.addProperty("fullGuiAcceptance", false); result.addProperty("status", "not-run");
        Path g01 = null, g02 = null, reopened = null, g06 = null;
        boolean launchAttempted = false, safe = false;
        ProductRun run = null;
        Throwable primary = null;
        try {
            packageMount.attach();
            Path app = mount.resolve("Voboost Installer.app"), binary = app.resolve("Contents/MacOS/voboost-installer"), resources = app.resolve("Contents/Resources");
            host(packageWork, "codesign", List.of("/usr/bin/codesign", "--verify", "--deep", "--strict", app.toString()));
            host(packageWork, "verify-package", List.of(binary.toString(), "--verify-package"));
            Path manifestFile = resources.resolve("voboost.json"), catalogFile = resources.resolve("agents.json");
            DeliveryManifest.Document manifest = DeliveryManifest.verify(manifestFile);
            // Existing installed-state observer identities, never firmware compatibility gates.
            String manifestIdentity = DeliveryLayout.sha256Hex(Files.readAllBytes(manifestFile));
            String catalogIdentity = DeliveryLayout.sha256Hex(Files.readAllBytes(catalogFile));
            Path sessionWork = RunFiles.directory(work.resolve("session"));
            VoyemuClient client = VoyemuClient.process(config.voyemu(), sessionWork);
            run = ProductRun.open(root.resolve("machine-locks"), config.machine(), work);
            OwnedSession session = run.start(client, config.profile(), "fresh".equals(config.scenario()), true);
            session.verifyAndroidUi();
            InstallerDriver readOnly = new InstallerDriver(binary, RunFiles.directory(work.resolve("readback")), client, session, config.profile(),
                manifestIdentity, manifest.releaseIdentity(), catalogIdentity, run);
            if ("uninstall".equals(System.getProperty("prepareExisting"))) {
                readOnly.prepare("uninstall");
            }
            launchAttempted = true;
            GuiScenario.Evidence evidence = GuiScenario.execute(launcher, observer, app, readOnly, run, session, work.resolve("gui"));
            g01 = evidence.connected(); g02 = evidence.installed(); reopened = evidence.reopened(); g06 = evidence.removed();
            run.close(); run = null;
            safe = true; result.addProperty("status", "passed-selected-subset");
            result.addProperty("G01", g01.toString()); result.addProperty("G02", g02.toString()); result.addProperty("G02Reopened", reopened.toString()); result.addProperty("G06", g06.toString());
        } catch (Exception | Error failure) {
            primary = failure; result.addProperty("status", "failed-or-blocked"); result.addProperty("diagnostic", failure.toString());
            result.addProperty("ownershipRetained", launchAttempted || run != null);
            throw failure;
        } finally {
            // Unknown app/product dispatch retains both mounted bytes and the guest owner; no cleanup mutation follows.
            try { if (safe || !launchAttempted && run == null) packageMount.cleanup(); }
            catch (Exception cleanup) { result.addProperty("status", "failed-cleanup"); result.addProperty("cleanupDiagnostic", cleanup.toString()); if (primary != null) primary.addSuppressed(cleanup); else throw cleanup; }
            finally { RunFiles.write(work.resolve("result.json"), result); }
        }
        Path report = work.resolve("result.json");
        CaseEvidence.record(new AcceptancePlan.Key("G01", "product"), new AcceptanceReport.Result(GateStatus.PASS,
            "Genuine packaged native welcome/consent/connected UI and unchanged independent selected-device absence", List.of(report, g01)));
        CaseEvidence.record(new AcceptancePlan.Key("G02", "product"), new AcceptanceReport.Result(GateStatus.PASS,
            "One actual GUI install, independent healthy runtime and genuine app reopen into exact-device management", List.of(report, g02, reopened)));
        CaseEvidence.record(new AcceptancePlan.Key("G06", "product"), new AcceptanceReport.Result(GateStatus.PASS,
            "Actual native GUI removal cancellation kept healthy state; one confirmed GUI removal independently became absent; owned cleanup verified", List.of(report, g06)));
    }
}
