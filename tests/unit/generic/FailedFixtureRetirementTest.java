package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.tests.ProductRun;
import ru.voboost.tests.RunFiles;

class FailedFixtureRetirementTest {
    @TempDir Path directory;

    private OwnedSessionTest.Fixture failedRun() throws Exception {
        OwnedSessionTest harness = new OwnedSessionTest();
        harness.directory = directory;
        OwnedSessionTest.Fixture fixture = harness.new Fixture();
        Path work = RunFiles.directory(directory.resolve("run"));
        fixture.work = work.resolve("session");
        ProductRun run = ProductRun.open(directory.resolve("leases"), fixture.machine, work);
        run.start(new VoyemuClient(fixture), "profile", false, false);
        JsonObject input = new JsonObject();
        input.addProperty("manifest", "a".repeat(64));
        input.addProperty("device", "emulator-5554");
        input.addProperty("priorBoot", "12345678-1234-1234-1234-123456789abc");
        JsonObject proof = input.deepCopy();
        proof.addProperty("schemaVersion", 1);
        proof.addProperty("resolution", "failed-reconciled");
        proof.addProperty("acceptance", "FAILED");
        proof.addProperty("functionalAcceptance", false);
        proof.addProperty("bootId", "22345678-1234-1234-1234-123456789abc");
        proof.addProperty("nativePhase", "install-complete");
        proof.addProperty("nativeOutcome", "complete");
        proof.addProperty("diagnostic",
                "Install failed: installed content identity is unavailable");
        Path evidence = work.resolve("failure.json");
        RunFiles.write(evidence, proof);
        try (var install = run.reserve(ProductRun.Kind.INSTALL, input)) {
            install.dispatch();
            install.failInstall(evidence);
        }
        input.addProperty("phase", "failed-install-cleanup");
        try (var uninstall = run.reserve(ProductRun.Kind.UNINSTALL, input)) {
            uninstall.dispatch();
        }
        assertThrows(IOException.class, run::close);
        return fixture;
    }

    private OwnedSession resume(OwnedSessionTest.Fixture fixture) throws Exception {
        return OwnedSession.resume(new VoyemuClient(fixture), "profile", fixture.machine,
                fixture.work);
    }

    @Test
    void existingStopRetiresFailureWithoutChangingOperationsOrAllowingOldBaseline()
            throws Exception {
        var fixture = failedRun();
        resume(fixture).close(); // Trusted operator stop, before registration.
        Path work = directory.resolve("run");
        Path installRecord = work.resolve("operations/000001/operation.json");
        Path uninstallRecord = work.resolve("operations/000002/operation.json");
        byte[] install = Files.readAllBytes(installRecord);
        byte[] uninstall = Files.readAllBytes(uninstallRecord);
        try (var run = ProductRun.resumeForRetirement(directory.resolve("leases"),
                fixture.machine, work)) {
            assertThrows(IOException.class,
                    () -> run.pending().complete(work.resolve("failure.json")));
            assertThrows(IOException.class, () -> run.pending().dispatch());
            Path proof = run.importExistingStop(resume(fixture));
            assertTrue(RunFiles.read(proof).get("baselineRequired").getAsBoolean());
            assertEquals("FAILED", RunFiles.read(proof).get("acceptance").getAsString());
        }
        assertArrayEquals(install, Files.readAllBytes(installRecord));
        assertArrayEquals(uninstall, Files.readAllBytes(uninstallRecord));
        assertEquals("retired-failed",
                RunFiles.read(work.resolve("run.json")).get("state").getAsString());
        assertThrows(IOException.class, () -> ProductRun.resume(directory.resolve("leases"),
                fixture.machine, work));
        assertThrows(IOException.class, () -> ProductRun.open(directory.resolve("leases"),
                fixture.machine, RunFiles.directory(directory.resolve("new-run"))));
        try (var repeat = ProductRun.resumeForRetirement(directory.resolve("leases"),
                fixture.machine, work)) {
            repeat.importExistingStop(resume(fixture));
        }
        assertEquals(1, fixture.ops.stream().filter("session.start"::equals).count());
        assertEquals(1, fixture.ops.stream().filter("session.stop"::equals).count());
    }

    @Test
    void liveSessionCannotBeRetiredByImportAndDoesNotTriggerStop() throws Exception {
        var fixture = failedRun();
        ProductRun run = ProductRun.resumeForRetirement(directory.resolve("leases"),
                fixture.machine, directory.resolve("run"));
        assertThrows(IOException.class, () -> run.importExistingStop(resume(fixture)));
        assertFalse(Files.exists(directory.resolve("run/retirement.json")));
        assertThrows(IOException.class, run::close);
        assertFalse(fixture.ops.contains("session.stop"));
    }

    @Test
    void foreignLookupCannotManufactureRetirementOrReplayStop() throws Exception {
        var fixture = failedRun();
        resume(fixture).close();
        fixture.foreignLookup = true;
        assertThrows(IOException.class, () -> resume(fixture));
        assertFalse(Files.exists(directory.resolve("run/retirement.json")));
        assertEquals(1, fixture.ops.stream().filter("session.stop"::equals).count());
    }
    @Test
    void unverifiedCleanupCannotBeImportedOrCauseASecondStop() throws Exception {
        var fixture = failedRun();
        resume(fixture).close();
        VoyemuClient client = new VoyemuClient((bytes, timeout) -> {
            var result = fixture.execute(bytes, timeout);
            JsonObject request = RunFiles.parse(bytes);
            if (!"operation.get".equals(request.get("op").getAsString())) return result;
            JsonObject response = RunFiles.parse(result.stdout());
            JsonObject record = response.getAsJsonObject("result");
            if ("session.stop".equals(record.get("op").getAsString())) {
                record.getAsJsonObject("response").getAsJsonObject("result")
                        .getAsJsonObject("cleanup").addProperty("verified", false);
            }
            return new VoyemuClient.ProcessResult(result.exitCode(), result.timedOut(),
                    response.toString(), result.stderr());
        });
        assertThrows(IOException.class, () -> OwnedSession.resume(client, "profile",
                fixture.machine, fixture.work));
        assertFalse(Files.exists(directory.resolve("run/retirement.json")));
        assertEquals(1, fixture.ops.stream().filter("session.stop"::equals).count());
    }
    @Test
    void unknownStopLookupNeverDispatchesAnotherStopOrRetiresTheFixture() throws Exception {
        var fixture = failedRun();
        resume(fixture).close();
        VoyemuClient client = new VoyemuClient((bytes, timeout) -> {
            JsonObject request = RunFiles.parse(bytes);
            if ("operation.get".equals(request.get("op").getAsString())) {
                JsonObject original = fixture.retained.get(request.getAsJsonObject("target")
                        .get("request_id").getAsString());
                if (original != null && "session.stop".equals(original.get("op").getAsString())) {
                    return new VoyemuClient.ProcessResult(null, true, "", "lookup unavailable");
                }
            }
            return fixture.execute(bytes, timeout);
        });
        assertThrows(IOException.class, () -> OwnedSession.resume(client, "profile",
                fixture.machine, fixture.work));
        assertFalse(Files.exists(directory.resolve("run/retirement.json")));
        assertEquals(1, fixture.ops.stream().filter("session.stop"::equals).count());
    }
}
