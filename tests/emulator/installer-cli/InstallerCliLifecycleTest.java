package ru.voboost.tests.installer.cli;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.voboost.voyemu.EmulatorFixture;
import ru.voboost.voyemu.EmulatorPreparation;
import ru.voboost.tests.MountSession;
import ru.voboost.tests.ProductRun;
import ru.voboost.tests.AcceptancePlan;
import ru.voboost.tests.AcceptanceReport;
import ru.voboost.tests.CaseEvidence;
import ru.voboost.tests.GateStatus;
import ru.voboost.tests.RunFiles;
import ru.voboost.tests.HostProcess;
import ru.voboost.tests.delivery.DeliveryLayout;
import ru.voboost.tests.delivery.DeliveryManifest;
import ru.voboost.voyemu.OwnedSession;
import ru.voboost.voyemu.TestConfiguration;
import ru.voboost.voyemu.VoyemuClient;

/** Real packaged CLI base lifecycle; individual agent functional acceptance remains a separate registry. */
@Order(1)
@ExtendWith(EmulatorFixture.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InstallerCliLifecycleTest {
    private static Path input(String key) throws IOException {
        String value = System.getProperty(key);
        if (value == null || !Path.of(value).isAbsolute()) throw new IOException("BLOCKED: absolute " + key + " required");
        return Path.of(value);
    }
    private static void host(Path work, String name, List<String> command) throws Exception {
        HostProcess.Result result = HostProcess.run(work.resolve(name + ".log"), command, 60000);
        if (result.exit() != 0) throw new IOException("host package gate failed: " + name + "; raw output retained at " + work);
    }

    private static boolean mounted(Path mount) throws IOException {
        return Files.exists(mount) && !Files.getAttribute(mount, "unix:dev").equals(Files.getAttribute(mount.getParent(), "unix:dev"));
    }
    private static boolean completed(Path work, ProductRun.Kind kind, String phase) throws IOException {
        try (var entries = Files.list(work.resolve("operations"))) {
            for (Path directory : entries.toList()) {
                JsonObject value = RunFiles.read(directory.resolve("operation.json"));
                if (kind.name().equals(value.get("kind").getAsString()) && "verified".equals(value.get("state").getAsString())
                        && (phase == null || phase.equals(value.getAsJsonObject("input").get("phase").getAsString()))) return true;
            }
        }
        return false;
    }

    private static boolean retainedVehicleReapply(Path work,String manifest,Path dmg)throws IOException {
        boolean found=false;
        try(var entries=Files.list(work.resolve("operations"))) {
            for(Path directory:entries.toList()) {
                JsonObject value=RunFiles.read(directory.resolve("operation.json")),input=value.getAsJsonObject("input");
                if("UPDATE".equals(value.get("kind").getAsString())&&input.has("phase")&&"vehicle-reapply".equals(input.get("phase").getAsString())) {
                    if(!manifest.equals(input.get("manifest").getAsString()))throw new IOException("reapply cleanup selected manifest differs");
                    found=true;
                }
            }
        }
        if(found&&!dmg.toRealPath().equals(Path.of(RunFiles.read(work.resolve("result.json")).get("candidateDmg").getAsString()).toRealPath()))
            throw new IOException("reapply cleanup requires the original selected DMG");
        return found;
    }

    /** Publish terminal subchecks immediately; a later failed case cannot erase real observations. */
    private static void recordFunctionalSource(int number,Path file) throws IOException { SourceCaseEvidence.record(number,file); }

    @Test
    @Tag("emulator-workflow")
    @Order(2)
    void updatesFromPreviousRelease(EmulatorPreparation prepared, EmulatorPreparation.Candidate previous)
            throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(previous != null, "Provide -PpreviousDmg for the update case");
        var prior = prepared.installer(previous);
        prior.installForLifecycle("previous");
        prior.restartBothAndVerify();
        var current = prepared.currentInstaller();
        current.updateVehiclePackage();
        current.requireHealthy();
        current.uninstallNormally("updated");
        current.requireAbsent();
    }

    @Test
    @Tag("emulator-workflow")
    @Order(2)
    void updatesAcrossDisconnectedInstaller(EmulatorPreparation prepared, EmulatorPreparation.Candidate previous) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(previous != null, "Provide -PpreviousDmg for the update case");
        var prior = prepared.installer(previous); prior.installForLifecycle("previous"); prior.restartBothAndVerify();
        var current = prepared.currentInstaller(); current.updateAcrossClientDisconnect(false);
        current.uninstallNormally("updated"); current.requireAbsent();
    }

    @Test
    @Tag("emulator-workflow")
    @Order(2)
    void reappliesAcrossDisconnectedInstaller(EmulatorPreparation prepared, EmulatorPreparation.Candidate previous) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(previous != null, "Provide -PpreviousDmg for the reapply case");
        var prior = prepared.installer(previous); prior.installForLifecycle("previous"); prior.restartBothAndVerify();
        var current = prepared.currentInstaller(); current.updateAcrossClientDisconnect(true);
        current.uninstallNormally("updated"); current.requireAbsent();
    }

    /** Packaged reinstall smoke test with signed input bytes and live package registration. */
    @Test
    @Tag("emulator-workflow")
    @Order(3)
    void reinstallsOverPreviousResourceRelease(EmulatorPreparation prepared, EmulatorPreparation.Candidate previous)
            throws Exception {
        if (previous == null) throw new IOException("Provide -PpreviousDmg for the clean reinstall case");
        Path priorResources = previous.app().resolve("Contents/Resources");
        Path currentResources = prepared.app.resolve("Contents/Resources");
        var currentArtifacts = DeliveryManifest.verify(currentResources.resolve("voboost.json")).artifacts();
        var priorArtifacts = DeliveryManifest.verify(priorResources.resolve("voboost.json")).artifacts();
        var changedOverlays = new java.util.LinkedHashSet<String>();
        for (var old : priorArtifacts) {
            if (!"resource-apk".equals(old.role())) continue;
            for (var now : currentArtifacts) {
                if ("resource-apk".equals(now.role()) && old.packageName().equals(now.packageName())
                        && Files.mismatch(priorResources.resolve(old.file()), currentResources.resolve(now.file())) != -1)
                    changedOverlays.add(old.packageName());
            }
        }
        if (changedOverlays.isEmpty()) throw new IOException("Both fixtures must deliver a common resource APK with different bytes");

        var current = prepared.currentInstaller();
        current.requireAbsent();
        var prior = prepared.installer(previous);
        prior.installForLifecycle("previous");
        // The fixed PackageManager observer supplies registration only. The before/after APK bytes
        // are compared on the mounted DMGs, and QNX delivery checks only the owned guest endpoint.
        String overlay = current.requireOneRegisteredResource(changedOverlays);
        prepared.session.verifyQnxDelivery();

        current.installForLifecycle("reinstall");
        current.observeCompletedInstalledOperation();
        current.requireOneRegisteredResource(Set.of(overlay));
        prepared.session.verifyQnxDelivery();
        current.uninstall("final");
        current.requireAbsent();
    }

    @Test
    @Order(1)
    void installsUninstallsReinstallsRebootsAndNormallyUninstalls(EmulatorPreparation prepared) throws Exception {
        if (prepared != null) {
            var installer = prepared.installer;
            installer.installForLifecycle("first");
            installer.requireHealthy();
            installer.uninstallNormally("cycle");
            installer.requireAbsent();
            installer.installForLifecycle("second");
            installer.rebootAndVerify();
            installer.requireHealthy();
            installer.uninstallFromSettings();
            installer.requireAbsent();
            installer.installForLifecycle("after-settings");
            installer.requireHealthy();
            installer.uninstallNormally("final");
            installer.requireAbsent();
            return;
        }
        TestConfiguration config = TestConfiguration.fromSystemProperties();
        boolean vehicleOnly = "true".equals(System.getProperty("vehicleOnly"));
        boolean stockReferenceOnly="true".equals(System.getProperty("stockReferenceOnly"));
        boolean failedCleanup = "true".equals(System.getProperty("failedInstallCleanup"));
        if(stockReferenceOnly&&(vehicleOnly||failedCleanup))throw new IOException("stock reference acquisition requires its dedicated non-resumed run");
        if (vehicleOnly && (failedCleanup || System.getProperty("resumeRun") != null))
            throw new IOException("vehicle-only run requires a dedicated product run without recovery/resume");
        if (failedCleanup && (System.getProperty("resumeRun") == null
                || !"resume".equals(config.scenario()))) {
            throw new IOException("failed install cleanup requires the same retained run/session");
        }
        Path dmg = input("candidateDmg"), machine = config.machine();
        if (!"true".equals(System.getProperty("withCluster"))) throw new IOException("BLOCKED: explicit -PwithCluster=true required for this candidate lifecycle");
        String preparation = System.getProperty("prepareExisting", "require-absent");
        InstallerDriver.requirePreparationMode(preparation,vehicleOnly);
        String vehicleStartAt=System.getProperty("vehicleStartAt","full");
        InstallerDriver.requireVehicleStartAt(vehicleStartAt,vehicleOnly,preparation);
        Path fixtureApk = Path.of("build/test-fixtures/launcher/voboost-launcher-fixture.apk").toAbsolutePath();
        JsonObject functionalPlan = RunFiles.read(Path.of("release/beta1-functional-plan.json").toAbsolutePath());
        JsonObject approvedFixture = functionalPlan.getAsJsonObject("fixture");
        if (!failedCleanup && !vehicleOnly && !stockReferenceOnly) InstallerDriver.requireLauncherFixture(fixtureApk,approvedFixture);
        config.verifyClusterImages();
        Path root = input("testWorkDir"); Files.createDirectories(root);
        boolean resume = System.getProperty("resumeRun") != null;
        if (resume && !"resume".equals(config.scenario())) throw new IOException("coordinator continuation requires scenario=resume");
        Path work = resume ? input("resumeRun").toRealPath()
            : Files.createTempDirectory(root, "installer-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Path packageWork = RunFiles.directory(work.resolve("package-" + java.util.UUID.randomUUID()));
        Path resultFile = work.resolve(resume ? "result-resume-" + java.util.UUID.randomUUID() + ".json" : "result.json");
        Path mount = packageWork.resolve("volume");
        MountSession packageMount = new MountSession(packageWork, new MountSession.Backend() {
            public void attach() throws Exception { host(packageWork, "attach", List.of("/usr/bin/hdiutil", "attach", "-readonly", "-nobrowse", "-mountpoint", mount.toString(), dmg.toString())); }
            public boolean mounted() throws Exception { return InstallerCliLifecycleTest.mounted(mount); }
            public void detach() throws Exception { host(packageWork, "detach", List.of("/usr/bin/hdiutil", "detach", mount.toString())); }
            public void removeWork() throws Exception { Files.deleteIfExists(mount); }
        });
        JsonObject report = new JsonObject(); report.addProperty("candidateDmg", dmg.toString()); report.addProperty("preparation", preparation);
        report.addProperty("agentFunctionalAcceptance", "not-run");
        report.addProperty("coordinatorContinuation", resume);
        Throwable primary = null; boolean guiOwnershipRetained = false;
        Path observerFile = System.getProperty("observerDmg") == null ? null : input("observerDmg");
        if (observerFile != null && (!resume || !failedCleanup))
            throw new IOException("An observer package is restricted to retained failed-install cleanup");
        try (EmulatorPreparation.Candidate observer = observerFile == null ? null
                : EmulatorPreparation.Candidate.open(RunFiles.directory(work.resolve("observer-" + java.util.UUID.randomUUID())), observerFile)) {
            packageMount.attach();
            Path app = mount.resolve("Voboost Installer.app"), resources = app.resolve("Contents/Resources");
            Path candidateBinary = app.resolve("Contents/MacOS/voboost-installer");
            Path binary = observer == null ? candidateBinary : observer.app().resolve("Contents/MacOS/voboost-installer");
            if (observer != null) report.addProperty("observerDmg", observer.file().toString());
            if (!Files.isRegularFile(binary, LinkOption.NOFOLLOW_LINKS) || !Files.isExecutable(binary)) throw new IOException("packaged installer executable missing");
            host(packageWork, "codesign", List.of("/usr/bin/codesign", "--verify", "--deep", "--strict", app.toString()));
            host(packageWork, "verify-package", List.of(candidateBinary.toString(), "--verify-package"));
            Path manifestFile = resources.resolve("voboost.json"), catalogFile = resources.resolve("agents.json");
            DeliveryManifest.Document manifest = DeliveryManifest.verify(manifestFile);
            String manifestIdentity = DeliveryLayout.sha256Hex(Files.readAllBytes(manifestFile));
            String catalogIdentity = DeliveryLayout.sha256Hex(Files.readAllBytes(catalogFile));
            report.addProperty("releaseIdentity", manifest.releaseIdentity());
            boolean reapplyCleanup=resume&&retainedVehicleReapply(work,manifestIdentity,dmg);
            if(reapplyCleanup&&!failedCleanup)throw new IOException("retained vehicle reapply permits only dedicated failed-install cleanup");
            String profile = config.profile(), scenario = config.scenario();
            Path ownedWork = ru.voboost.tests.RunFiles.directory(work.resolve("session"));
            VoyemuClient client = VoyemuClient.process(config.voyemu(), ownedWork);
            try (ProductRun run = resume ? ProductRun.resume(root.resolve("machine-locks"), machine, work)
                    : ProductRun.open(root.resolve("machine-locks"), machine, work)) {
                OwnedSession owned;
                if (resume) {
                    owned = run.resumeSession(client, profile);
                } else owned = run.start(client, profile, "fresh".equals(scenario), true);
                if (owned.cleanupVerified()) {
                    if (failedCleanup) {
                        if ((!run.hasFailedInstallHistory()&&!reapplyCleanup)
                                || !completed(work, ProductRun.Kind.UNINSTALL,
                                        "failed-install-cleanup")) {
                            throw new IOException("session cleanup lacks normal failed-install removal");
                        }
                    } else if (!completed(work, ProductRun.Kind.INSTALL, "first") || !completed(work, ProductRun.Kind.INSTALL, "second")
                            || !completed(work, ProductRun.Kind.UNINSTALL, "cycle") || !completed(work, ProductRun.Kind.REBOOT, null)
                            || !completed(work, ProductRun.Kind.UNINSTALL, "final"))
                        throw new IOException("verified session cleanup does not prove the missing product lifecycle");
                } else {
                    Path driverWork = RunFiles.directory(work.resolve("installer-" + java.util.UUID.randomUUID()));
                    InstallerDriver driver = new InstallerDriver(binary, driverWork, client, owned, profile, manifestIdentity, manifest.releaseIdentity(), catalogIdentity, run,config.voyemu());
                    // A prior Power probe may have saved an asleep SDK snapshot. This focused cut starts one ordinary reboot.
                    owned.verifyAndroidUi();
                    if(vehicleOnly&&"fev-finish".equals(vehicleStartAt))driver.rebootAndVerify();
                    if (!failedCleanup) driver.configureCandidatePackage(catalogFile);
                    if(!stockReferenceOnly&&!vehicleOnly&&!failedCleanup){Path expected=Path.of("tests/common/agents/agent-settings/settings-copy/"+manifest.releaseIdentity()+".json").toRealPath();driver.configureSettingsCopy(ru.voboost.tests.ui.SettingsCopySource.readExpected(expected,manifest.releaseIdentity(),catalogIdentity));report.addProperty("settingsExpectedData",expected.toString());}
                    if(!stockReferenceOnly&&!failedCleanup&&!"remove".equals(vehicleStartAt))report.addProperty("initialNativeBoot",driver.captureInitialNativeBoot().toString());
                    if(stockReferenceOnly){if(resume)driver.reconcileStockBaseline();driver.prepare(preparation);report.addProperty("stockReferenceCandidates",driver.collectStockSettingsBaselines().toString());report.addProperty("stockReferenceAcceptance","BLOCKED: candidate acquisition only; owner visual review and pinning required");}
                    else if (vehicleOnly) {
                        if("verify-installed".equals(preparation)) {
                            report.addProperty("existingNativeOperation",driver.observeCompletedInstalledOperation().toString());
                            if("remove".equals(vehicleStartAt))driver.requireRemovalIdentity();else if(Set.of("pw-icon","pw-readiness").contains(vehicleStartAt))report.addProperty("pwCandidateIdentity",driver.requireAgentCandidate().toString());else driver.requireHealthy();
                            report.addProperty("vehicleInstallation","existing selected kit; no install request dispatched by this run");
                        } else if("reapply".equals(preparation)) {
                            report.addProperty("vehicleReapply",driver.reapplyVehiclePackage().toString());
                            report.addProperty("vehicleInstallation","normal selected-package reinstallation verified in this run; no first-install claim");
                        } else {
                            driver.prepare(preparation);
                            if(Set.of("full","pw-icon").contains(vehicleStartAt))report.addProperty("pwStockBaseline",driver.capturePwStockBaseline().toString());
                            driver.install(InstallerDriver.VEHICLE_INSTALL_PHASE);
                            report.addProperty("vehicleInstallation","installed and verified in this run");
                        }
                        if(!"remove".equals(vehicleStartAt)) {
                        JsonObject candidateCatalog = RunFiles.read(catalogFile);
                        try {
                            report.addProperty("vehicleOnlyConfiguration", driver.configureVehicleOnly(candidateCatalog,Set.of("pw-icon","pw-readiness").contains(vehicleStartAt)).toString());
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();throw interrupted;
                        } catch (Exception failure) {
                            try {
                                var current=driver.observeRuntime(driver.status().get("activeGeneration").getAsString());
                                driver.startupProcesses(current.runtime().getAsJsonObject("publisher").get("bootId").getAsString());
                            }
                            catch (InterruptedException interrupted) {Thread.currentThread().interrupt();throw interrupted;}
                            catch (Exception diagnostic) {failure.addSuppressed(diagnostic);}
                            failure.addSuppressed(new IOException("diagnostic log archive retained at "+driver.captureDiagnosticLogs("vehicle-setup-failure")));
                            throw failure;
                        }
                        if (Set.of("pw-icon", "pw-readiness").contains(vehicleStartAt)
                                && !"verify-installed".equals(preparation)) {
                            report.addProperty("clusterActivationRestart", driver.restartBothAndVerify().toString());
                        } else if (!Set.of("pw-icon", "pw-readiness", "fev-finish").contains(vehicleStartAt)) {
                            driver.rebootAndVerify();
                        }
                        if ("pw-readiness".equals(vehicleStartAt)
                                || ("pw-icon".equals(vehicleStartAt) && !"verify-installed".equals(preparation))) {
                            report.addProperty("pwSavedDelivery", driver.awaitPwDeliveryAndStartCluster(candidateCatalog).toString());
                        }
                        if(Set.of("pw-icon","pw-readiness").contains(vehicleStartAt)||Set.of("verify-installed","reapply").contains(preparation)) {
                            report.addProperty("startupFeedbackReadinessStatus","NOT_RUN: existing-kit functional continuation; first-install/startup observations are separate retained evidence");
                        } else {
                            Path timing = driver.measureVehicleStartup();
                            report.addProperty("earlyStartupEvidence", timing.toString());
                            report.addProperty("startupFeedbackReadinessStatus", RunFiles.read(timing).get("status").getAsString());
                        }
                        report.addProperty("earlyStartupStatus", "UNVERIFIED: passive feedback readiness does not prove restore application or pre-OEM ordering");

                        List<Integer> vehicleCases=Set.of("pw-icon","pw-readiness").contains(vehicleStartAt)?List.of(14):"fev".equals(vehicleStartAt)?List.of(13):Set.of("fev-oem","fev-finish").contains(vehicleStartAt)?List.of(13,14):List.of(14,13);
                        for (int number : vehicleCases) {
                            Path proof;
                            try {
                                proof = number==14&&"pw-readiness".equals(vehicleStartAt)?driver.verifyPwReadinessOnly(candidateCatalog)
                                        : number==13&&Set.of("fev-oem","fev-finish").contains(vehicleStartAt)?driver.verifyForcedEvOem(candidateCatalog,"fev-finish".equals(vehicleStartAt))
                                        : number==14&&!"full".equals(vehicleStartAt)?driver.verifyPwIconOnly(candidateCatalog)
                                        : driver.verifyVehicleFunctional(candidateCatalog,number);
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();throw interrupted;
                            } catch (Exception failure) {
                                // The daemon reports an agent's controller progress only through the
                                // "native controller state changed" result field, and only when the value changes,
                                // so a vehicle failure is diagnosable only from the log the device still holds at
                                // that moment: 13p could not obtain it after the fact and the frozen divergence is
                                // exactly what needs the field. Read-only, and the failure is rethrown unchanged.
                                failure.addSuppressed(new IOException(
                                        "diagnostic log archive retained at " + driver.captureDiagnosticLogs("vehicle-case-" + number + "-failure")));
                                throw failure;
                            }
                            report.addProperty("vehicleCase" + number, proof.toString());
                            recordFunctionalSource(number, proof);
                            boolean forcedEv=number==13;
                            if(Set.of("pw-icon","pw-readiness").contains(vehicleStartAt)) {
                                report.addProperty("pedestrianPersistenceStatus", "NOT_RUN: focused rendered icon test only");
                            } else if("fev-finish".equals(vehicleStartAt)) {
                                report.addProperty(forcedEv?"forcedEvDisableCancellation":"pedestrianDisableCancellation","NOT_RUN: sleep/wake fixture remains unresolved");
                                report.addProperty(forcedEv?"forcedEvDesiredOffWarmCold":"pedestrianDesiredOffWarmCold","NOT_RUN: sleep/wake fixture remains unresolved");
                            } else {
                                report.addProperty(forcedEv?"forcedEvDisableCancellation":"pedestrianDisableCancellation", driver.verifyVehicleDisableCancellation(candidateCatalog, forcedEv).toString());
                                report.addProperty(forcedEv?"forcedEvDesiredOffWarmCold":"pedestrianDesiredOffWarmCold", driver.verifyVehicleDesiredOff(candidateCatalog, forcedEv).toString());
                            }
                        }
                        report.addProperty("desiredOffInstallationLockRestartAck", "BLOCKED: runner has no admitted installation lock, owner restart or Saved receipt observation API");
                        report.addProperty("vehicleOnlyFinalConfiguration", driver.configureVehicleOnly(candidateCatalog,Set.of("pw-icon","pw-readiness").contains(vehicleStartAt)).toString());

                        if(report.has("vehicleCase14")) {
                            JsonObject pw=RunFiles.read(Path.of(report.get("vehicleCase14").getAsString()));
                            report.addProperty("pwQnxIconStatus", pw.has("renderedPwIconAcceptance")&&pw.get("renderedPwIconAcceptance").getAsBoolean()
                                    ? "PASS: stock OFF glyph baseline and enabled-agent OFF suppression / natural ON absence retained in vehicleCase14" : "BLOCKED: rendered PW proof unavailable");
                        } else report.addProperty("pwQnxIconStatus","NOT_RUN: independent FEV continuation; prior positive PW icon baseline remains unconfirmed");
                        report.addProperty("vehicleOnlyAcceptance", "BLOCKED");
                        } else report.addProperty("vehicleOnlyAcceptance","NOT_RUN: normal removal only; prior feature receipts retained separately");
                        if (Set.of("pw-icon","pw-readiness").contains(vehicleStartAt)) {
                            report.addProperty("vehicleOnlyNormalRemoval", "NOT_RUN: focused rendered icon test only");
                        } else {
                            driver.uninstall("final"); driver.requireAbsent();
                            report.addProperty("vehicleOnlyNormalRemoval", "verified");
                        }
                    } else if (failedCleanup) {
                        if(reapplyCleanup) {
                            if(run.pending()!=null&&!(run.pending().kind()==ProductRun.Kind.UPDATE&&run.pending().input().has("phase")&&"vehicle-reapply".equals(run.pending().input().get("phase").getAsString()))
                                    &&!(run.pending().kind()==ProductRun.Kind.UNINSTALL&&run.pending().input().has("phase")&&"failed-install-cleanup".equals(run.pending().input().get("phase").getAsString())))
                                throw new IOException("reapply cleanup cannot reconcile another product operation");
                            driver.reconcilePending();
                            if(!completed(work,ProductRun.Kind.UNINSTALL,"failed-install-cleanup"))driver.requireHealthy();
                            report.addProperty("vehicleReapplyCleanup","selected reapply verified without replay; functional acceptance remains failed");
                        } else {
                            if (run.pending() != null
                                    && run.pending().kind() == ProductRun.Kind.UNINSTALL) {
                                driver.reconcilePending();
                            }
                            Path failure = driver.reconcileFailedInstall();
                            report.addProperty("failedInstallEvidence", failure.toString());
                        }
                        report.addProperty("failedInstallAcceptance", "FAILED");
                        if (!completed(work, ProductRun.Kind.UNINSTALL, "failed-install-cleanup")) {
                            driver.uninstall("failed-install-cleanup");
                        }
                        driver.requireAbsent();
                        report.addProperty("recoveryStatus", "normal-removal-verified");
                    } else {
                        if (run.hasFailedInstallHistory()) {
                            throw new IOException("failed install requires explicit recovery cleanup; "
                                    + "normal lifecycle cannot replay it");
                        }
                        if (resume) driver.reconcilePending();
                        if (!completed(work, ProductRun.Kind.UNINSTALL, "cycle")) {
                            if (!completed(work, ProductRun.Kind.INSTALL, "first")) {
                                if (completed(work, ProductRun.Kind.UNINSTALL, "preparation")) driver.requireAbsent();
                                else driver.prepare(preparation);
                                driver.install("first");
                            } else driver.requireHealthy();
                            driver.uninstall("cycle");
                        }
                        if (!completed(work, ProductRun.Kind.INSTALL, "second")) {
                            driver.requireAbsent();
                            driver.install("second");
                        }
                        if (completed(work, ProductRun.Kind.UNINSTALL, "final")) driver.requireAbsent();
                        else {
                            driver.requireHealthy();
                            Path settingsProof = driver.verifySettingsTab();
                            report.addProperty("settingsTabEvidence", settingsProof.toString());
                            InstallerDriver.LanguageProof languages = driver.verifySystemLanguageRoundtrip();
                            report.addProperty("languageOppositeEvidence", languages.opposite().toString());
                            report.addProperty("languageRestoredEvidence", languages.restored().toString());
                            JsonObject candidateCatalog = RunFiles.read(catalogFile);
                            Path enabled = InitialSeedingAdmission.thenIndependent(() -> {
                                Path seeding = driver.verifyInitialSeeding(candidateCatalog);
                                report.addProperty("initialSeedingAdmission", seeding.toString());
                                SourceCaseEvidence.record(24,seeding,binary,catalogFile);
                                return seeding;
                            }, () -> driver.verifyAgentEnablement(candidateCatalog),binary,catalogFile);
                            report.addProperty("agentEnablementEvidence", enabled.toString());
                            for(int number:List.of(13,14)) {
                                Path proof=driver.verifyVehicleFunctional(candidateCatalog,number);report.addProperty("vehicleCase"+number,proof.toString());recordFunctionalSource(number,proof);
                            }
                            Path navi=driver.verifyNaviSpacing(candidateCatalog);report.addProperty("naviSpacingEvidence",navi.toString());recordFunctionalSource(19,navi);
                            for(int number:List.of(9,10,11)) {
                                Path proof=driver.verifyKeyboardFunctional(candidateCatalog,number);report.addProperty("keyboardCase"+number,proof.toString());recordFunctionalSource(number,proof);
                            }
                            Path launcher=driver.verifyLauncherPersistence(candidateCatalog,fixtureApk,approvedFixture);
                            report.addProperty("launcherSemanticEvidence",launcher.toString());recordFunctionalSource(12,launcher);
                            Path classified=driver.verifyCatalogClassification(candidateCatalog,fixtureApk,approvedFixture);report.addProperty("catalogClassificationEvidence",classified.toString());recordFunctionalSource(18,classified);
                            Path outage=driver.verifyStateOutage(candidateCatalog);report.addProperty("stateOutageEvidence",outage.toString());SourceCaseEvidence.record(25,outage,binary,catalogFile,driver.currentOutageScope());
                            Path i18n=driver.verifyI18nFunctional(candidateCatalog);report.addProperty("i18nFunctionalEvidence",i18n.toString());recordFunctionalSource(22,i18n);
                            for(int number:List.of(20,21)){Path layout=driver.verifySettingsLayout(candidateCatalog,number);report.addProperty("settingsLayout"+number,layout.toString());recordFunctionalSource(number,layout);}
                            Path descriptions=driver.verifyStartupDescriptions(candidateCatalog);report.addProperty("startupDescriptionsEvidence",descriptions.toString());recordFunctionalSource(23,descriptions);
                            report.addProperty("i18nSuiteRestoration",driver.finishI18nSuite(candidateCatalog).toString());
                            Path hidden=driver.verifyHiddenStartup(candidateCatalog,ru.voboost.tests.agents.HiddenTargets.defaultEnabledFixture(candidateCatalog));report.addProperty("hiddenStartupEvidence",hidden.toString());
                            Path none=driver.verifyNoneStartup();report.addProperty("noneStartupEvidence",none.toString());
                            if(!completed(work,ProductRun.Kind.REBOOT,null))driver.rebootAndVerify();
                            Path removed=driver.uninstallFromSettings();report.addProperty("settingsUninstallEvidence",removed.toString());
                            guiOwnershipRetained=true;
                            var gui=ru.voboost.tests.installer.gui.GuiScenario.execute(input("guiLauncher"),input("guiObserver"),app,driver,run,owned,work.resolve("native-gui"));
                            report.addProperty("G01",gui.connected().toString());report.addProperty("G02",gui.installed().toString());report.addProperty("G02Reopened",gui.reopened().toString());report.addProperty("G06",gui.removed().toString());guiOwnershipRetained=false;
                            Path boots=driver.terminalBootEvidence();report.addProperty("everyGuestBootEvidence",boots.toString());SourceCaseEvidence.record(26,boots);
                            if(System.getProperty("checkRunId")!=null) {
                                var currentCases=AcceptancePlan.parse(functionalPlan,RunFiles.read(catalogFile)).cases();var registered=new java.util.HashSet<AcceptancePlan.Key>();for(var specification:currentCases)if(specification.registered())registered.add(specification.key());
                                Path inventory;
                                try{inventory=driver.collectTargetInventory(System.getProperty("checkRunId"),dmg);report.addProperty("targetInventoryEvidence",inventory.toString());}
                                catch(IOException unavailable){JsonObject blocked=new JsonObject();blocked.addProperty("reason",unavailable.toString());Path file=driverWork.resolve("target-inventory-blocked.json");RunFiles.write(file,blocked);for(var key:ru.voboost.tests.TargetOwnerContract.keys())if(registered.contains(key))CaseEvidence.record(key,new AcceptanceReport.Result(GateStatus.BLOCKED,"Current source-owned inventory unavailable",List.of(file)));throw unavailable;}
                                for(var key:ru.voboost.tests.TargetOwnerContract.keys()) {
                                    if(!registered.contains(key))continue;
                                    try{ru.voboost.tests.InventoryProof.read(inventory,root,System.getProperty("checkRunId"),dmg.toRealPath(),key);}
                                    catch(ru.voboost.tests.InventoryProof.OwnerRequired applicable){continue;}
                                    catch(IOException unavailable){CaseEvidence.record(key,new AcceptanceReport.Result(GateStatus.BLOCKED,"Fenced inventory unavailable: "+unavailable.getMessage(),List.of(inventory)));continue;}
                                    CaseEvidence.record(key,new AcceptanceReport.Result(GateStatus.NOT_APPLICABLE,"Exact source-owned target owner absent from fenced installed package/process inventory",List.of(inventory)));
                                }
                            }

                        }
                    }
                }
                report.addProperty("baseLifecycle",vehicleOnly||stockReferenceOnly ? "not-run" : failedCleanup ? "failed" : "passed");
            }
        } catch(Exception|Error failure) {
            primary=failure;report.addProperty("baseLifecycle","failed");report.addProperty("diagnostic",failure.toString());throw failure;
        } finally {
            try {if(!guiOwnershipRetained)packageMount.cleanup();else report.addProperty("mountedCandidateRetained",true);}
            catch(Exception cleanup) {
                report.addProperty("baseLifecycle","failed");report.addProperty("cleanupDiagnostic",cleanup.toString());
                if(primary!=null)primary.addSuppressed(cleanup);else throw cleanup;
            } finally {RunFiles.write(resultFile,report);}
        }
        if (stockReferenceOnly) return; // Acquisition is never full install/product acceptance.
        if (vehicleOnly) return; // A scoped run never publishes the full install/uninstall lifecycle.
        if (failedCleanup) return; // Cleanup success never publishes install acceptance.
        List<Path> evidence=new java.util.ArrayList<>(List.of(resultFile));
        try(var entries=Files.list(work.resolve("operations"))) {
            for(Path directory:entries.toList())evidence.add(directory.resolve("operation.json"));
        }
        if(report.has("agentEnablementEvidence"))
            CaseEvidence.record(new AcceptancePlan.Key("agent-enablement-conflict", "product"), new AcceptanceReport.Result(GateStatus.PASS,
                "Actual stable candidate checkbox off/on input, independently observed checked and complete canonical desired states, both keyboard conflict directions, original configuration restored; agent effects remain separate",
                List.of(resultFile, Path.of(report.get("agentEnablementEvidence").getAsString()))));
        if (report.has("hiddenStartupEvidence"))
            CaseEvidence.record(new AcceptancePlan.Key("hidden-tbox-recovery", "product"), new AcceptanceReport.Result(GateStatus.PASS,
                "Hidden next boot has complete tab absence and current enabled running target states; actual stock TBOX gesture restores the live tab without process restart",
                List.of(resultFile, Path.of(report.get("hiddenStartupEvidence").getAsString()))));
        if (report.has("noneStartupEvidence"))
            CaseEvidence.record(new AcceptancePlan.Key("none-no-processes", "product"), new AcceptanceReport.Result(GateStatus.PASS,
                "Observed Settings none radio persisted without immediate stop; the new boot has no daemon/APK processes; packaged computer installer restored interface with live UI proof",
                List.of(resultFile, Path.of(report.get("noneStartupEvidence").getAsString()))));
        for (String key : List.of("languageOppositeEvidence", "languageRestoredEvidence")) {
            if (report.has(key)) CaseEvidence.record(new AcceptancePlan.Key(key.equals("languageOppositeEvidence") ? "language-opposite" : "language-return", "product"),
                new AcceptanceReport.Result(GateStatus.PASS, "Checked OEM system picker, current process/resource locales and Voboost captions confirmed",
                    List.of(resultFile, Path.of(report.get(key).getAsString()))));
        }
        if (report.has("settingsTabEvidence"))
            CaseEvidence.record(new AcceptancePlan.Key("settings-tab", "product"), new AcceptanceReport.Result(GateStatus.PASS,
                "Observed OEM Setting tab opens the actual candidate version heading in the same ready Enforcing boot",
                List.of(resultFile, Path.of(report.get("settingsTabEvidence").getAsString()))));
        if (report.has("settingsUninstallEvidence"))
            CaseEvidence.record(new AcceptancePlan.Key("settings-uninstall", "product"), new AcceptanceReport.Result(GateStatus.PASS,
                "Actual Settings uninstall and OEM confirmation followed an armed read-only observer; fresh Enforcing boot and independent ten-check Android absence verified after Voboost Fixture absence",
                List.of(resultFile, Path.of(report.get("settingsUninstallEvidence").getAsString()))));
        for (String id : List.of("G01", "G02", "G06")) {
            if (report.has(id)) {
                List<Path> guiEvidence = new java.util.ArrayList<>(List.of(resultFile, Path.of(report.get(id).getAsString())));
                if (id.equals("G02")) guiEvidence.add(Path.of(report.get("G02Reopened").getAsString()));
                CaseEvidence.record(new AcceptancePlan.Key(id, "product"), new AcceptanceReport.Result(GateStatus.PASS,
                    "Genuine packaged native GUI route on the same coordinator-owned guest, independent backend readback and final owned cleanup verified", guiEvidence));
            }
        }
        for (String id : List.of("cli-install-first", "cli-uninstall", "cli-absence", "cli-install-second"))
            CaseEvidence.record(new AcceptancePlan.Key(id, "product"), new AcceptanceReport.Result(GateStatus.PASS,
                "Exact packaged CLI install/uninstall/reinstall; independent absence, ten-check Android receipt, Enforcing and owned cleanup verified", evidence));
    }
}
