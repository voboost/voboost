package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.tests.ProductRun;
import ru.voboost.tests.RunFiles;
import ru.voboost.tests.installer.cli.InstallerDriver;

/** Executes the real driver and CLI child boundary against controlled host observations, never a VM. */
class StartupReconciliationTest {
    @TempDir Path directory;
    private static final String BOOT = "11111111-1111-1111-1111-111111111111", MANIFEST = "a".repeat(64), CATALOG = "b".repeat(64);
    private OwnedSessionTest.Fixture fixture;
    private ProductRun run;
    private Path work, driverWork;
    private InstallerDriver driver(String actualMode, String boot, int publisher) throws Exception {
        return driver(actualMode, boot, publisher, "startup-radio", "none");
    }
    private InstallerDriver driver(String actualMode, String boot, int publisher, String phase, String desired) throws Exception {
        OwnedSessionTest harness = new OwnedSessionTest(); harness.directory = directory; fixture = harness.new Fixture();
        work = RunFiles.directory(directory.resolve("run")); run = ProductRun.open(directory.resolve("leases"), fixture.machine, work);
        OwnedSession owned = fixture.start(); run.attach(owned);
        JsonObject input = RunFiles.parse("{\"manifest\":\"" + MANIFEST + "\",\"device\":\"emulator-5588\",\"phase\":\"startup-radio\",\"originalStartup\":\"interface\",\"desiredStartup\":\"none\",\"priorPublisher\":\"" + BOOT + ":10:20\",\"priorRunningStartup\":\"interface\"}");
        input.addProperty("phase", phase); input.addProperty("desiredStartup", desired);
        try (var operation = run.reserve(ProductRun.Kind.CONFIGURE, input)) { operation.observePriorBoot(BOOT); operation.dispatch(); }
        Path cli = Files.createDirectory(work.resolve("cli"));
        Files.writeString(cli.resolve("status.json"), "{\"deviceSerial\":\"emulator-5588\",\"route\":\"management\",\"health\":\"healthy\",\"daemonStatus\":\"running\",\"installedVersion\":\"1.0.0-beta1\",\"contentRelation\":\"same\",\"activeGeneration\":\"generation\",\"startupMode\":\"" + actualMode + "\"}\n");
        String body = "bootCompleted=1\nbootId=" + boot + "\nenforcing=Enforcing\noperation:\n";
        JsonObject framed = RunFiles.parse("{\"schemaVersion\":1,\"serial\":\"emulator-5588\"}"); framed.addProperty("body", body);
        Files.writeString(cli.resolve("boot.json"), framed.toString() + "\n");
        String observed = """
            {"schema":2,"source":"voboost","status":"ready","boot_id":"BOOT","data":{
              "root":"present","root_proof":"fixed-path-owner-mode-boot-fenced","runtime_current":true,
              "files":{"config":{"status":"present","current":true,"proof":"same-boot-retained-file","revision":"current-config","data":{"startup":"MODE","agents":{}}},
              "runtime":{"status":"present","current":true,"provenance":{"current":true,"proof":"same-boot-live-publisher","age_ms":10},
              "data":{"publisher":{"bootId":"BOOT","daemonPid":PUBLISHER,"daemonStarttime":"20"},
              "releaseIdentity":"1.0.0-beta1","manifestSha256":"MANIFEST","catalogSha256":"CATALOG","generationId":"generation",
              "startupMode":"interface","state":"ready","killed":false,"panic":false,"injections":[]}}}}}
            """.replace("BOOT", boot).replace("MODE", actualMode).replace("PUBLISHER", Integer.toString(publisher)).replace("MANIFEST", MANIFEST).replace("CATALOG", CATALOG);
        Files.writeString(cli.resolve("observe.json"), observed);
        Path executable = cli.resolve("installer");
        Files.writeString(executable, "#!/bin/sh\ncase \"$1\" in\n--status) cat " + quote(cli.resolve("status.json"))
            + ";;\n--observe) cat " + quote(cli.resolve("observe.json")) + ";;\n--emulator-check) cat " + quote(cli.resolve("boot.json"))
            + ";;\n*) printf '%s\\n' \"$*\" >> " + quote(work.resolve("mutations.log")) + "; exit 77;;\nesac\n");
        assertTrue(executable.toFile().setExecutable(true, true));
        VoyemuClient client = new VoyemuClient((bytes, timeout) -> {
            JsonObject request = RunFiles.parse(bytes);
            assertEquals("session.status", request.get("op").getAsString());
            JsonObject result = RunFiles.parse("{\"session_id\":\"" + owned.session().id() + "\",\"android_pid\":42,\"qnx_pid\":43,\"ok\":true,\"stopped\":false,\"deliveries\":{\"android\":{\"pid\":42,\"serial\":\"emulator-5588\"},\"qnx\":{\"pid\":43,\"bounded\":true}}}");
            return new VoyemuClient.ProcessResult(0, false, OwnedSessionTest.envelope(request, result).toString(), "");
        });
        driverWork = RunFiles.directory(work.resolve("driver"));
        InstallerDriver driver = new InstallerDriver(executable, driverWork, client, owned, "profile", MANIFEST, "1.0.0-beta1", CATALOG, run);
        driver.configureEmulatorDriver(executable);
        return driver;
    }
    private static String quote(Path file) { return "'" + file.toString().replace("'", "'\\''") + "'"; }
    @Test void sameBootConfirmedStartupClosesOnlyTheRetainedOperationWithoutReplayingInput() throws Exception {
        InstallerDriver driver = driver("none", BOOT, 10); driver.reconcilePending();
        assertNull(run.pending()); assertFalse(Files.exists(work.resolve("mutations.log")));
        List<Path> proofs; try (var paths = Files.list(driverWork)) { proofs = paths.filter(path -> path.getFileName().toString().startsWith("reconciled-")).toList(); }
        assertEquals(1, proofs.size()); assertFalse(RunFiles.read(proofs.get(0)).get("functionalAcceptance").getAsBoolean());
        run.close(); assertEquals(1, fixture.ops.stream().filter("session.stop"::equals).count());
    }
    @Test void confirmedTboxResultClosesOnlyTheOperationWithoutReplayingTheFiveTapGesture() throws Exception {
        InstallerDriver driver = driver("interface", BOOT, 10, "tbox-recovery", "interface"); driver.reconcilePending();
        assertNull(run.pending()); assertFalse(Files.exists(work.resolve("mutations.log")));
        try (var paths = Files.list(driverWork)) {
            Path proof = paths.filter(path -> path.getFileName().toString().startsWith("reconciled-")).findFirst().orElseThrow();
            assertFalse(RunFiles.read(proof).get("functionalAcceptance").getAsBoolean());
        }
        run.close(); assertEquals(1, fixture.ops.stream().filter("session.stop"::equals).count());
    }
    @Test void unchangedHiddenModeDoesNotAuthorizeAnotherTboxBurstOrShutdown() throws Exception {
        InstallerDriver driver = driver("hidden", BOOT, 10, "tbox-recovery", "interface");
        assertTrue(assertThrows(IOException.class, driver::reconcilePending).getMessage().contains("not independently confirmed"));
        assertThrows(IOException.class, run::close); assertFalse(Files.exists(work.resolve("mutations.log"))); assertFalse(fixture.ops.contains("session.stop"));
    }
    @Test void replacementPublisherCannotCompleteTheOriginalLiveTboxRecovery() throws Exception {
        InstallerDriver driver = driver("interface", BOOT, 11, "tbox-recovery", "interface");
        assertTrue(assertThrows(IOException.class, driver::reconcilePending).getMessage().contains("no original live publisher"));
        assertThrows(IOException.class, run::close); assertFalse(Files.exists(work.resolve("mutations.log"))); assertFalse(fixture.ops.contains("session.stop"));
    }
    @Test void differentPersistedValueHoldsOwnershipWithoutModeReplayOrGuestStop() throws Exception {
        InstallerDriver driver = driver("interface", BOOT, 10);
        assertTrue(assertThrows(IOException.class, driver::reconcilePending).getMessage().contains("not independently confirmed")); assertNotNull(run.pending());
        assertThrows(IOException.class, run::close);
        assertFalse(Files.exists(work.resolve("mutations.log"))); assertFalse(fixture.ops.contains("session.stop"));
    }
    @Test void replacementPublisherCannotProveTheDeferredMutationOrPermitShutdown() throws Exception {
        InstallerDriver driver = driver("none", BOOT, 11);
        assertTrue(assertThrows(IOException.class, driver::reconcilePending).getMessage().contains("replaced the original live publisher")); assertThrows(IOException.class, run::close);
        assertFalse(Files.exists(work.resolve("mutations.log"))); assertFalse(fixture.ops.contains("session.stop"));
    }
    @Test void differentBootCannotCompleteOrReplayTheRetainedNextBootSelection() throws Exception {
        InstallerDriver driver = driver("none", "22222222-2222-2222-2222-222222222222", 10);
        assertTrue(assertThrows(IOException.class, driver::reconcilePending).getMessage().contains("crossed the original boot")); assertThrows(IOException.class, run::close);
        assertFalse(Files.exists(work.resolve("mutations.log"))); assertFalse(fixture.ops.contains("session.stop"));
    }
}
