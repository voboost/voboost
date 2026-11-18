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

class ProductRunTest {
    @TempDir Path directory;
    private OwnedSessionTest.Fixture fixture() throws Exception {
        OwnedSessionTest harness = new OwnedSessionTest(); harness.directory = directory;
        return harness.new Fixture();
    }
    private ProductRun open(OwnedSessionTest.Fixture fixture) throws Exception {
        return ProductRun.open(directory.resolve("leases"), fixture.machine, RunFiles.directory(directory.resolve("run")));
    }
    private static JsonObject input() { JsonObject input = new JsonObject(); input.addProperty("operation", "install"); return input; }

    @Test void unknownProductMutationRetainsOwnerAndNeverStopsTheGuest() throws Exception {
        OwnedSessionTest.Fixture fixture = fixture();
        ProductRun run = open(fixture);
        OwnedSession owned = fixture.start(); run.attach(owned);
        try (ProductRun.Operation operation = run.reserve(ProductRun.Kind.INSTALL, input())) { operation.dispatch(); }
        assertThrows(IOException.class, run::close);
        assertFalse(fixture.ops.contains("session.stop"));
        assertThrows(IOException.class, () -> ProductRun.open(directory.resolve("leases"), fixture.machine,
            RunFiles.directory(directory.resolve("next"))));
        assertEquals("unknown", RunFiles.read(directory.resolve("run/operations/000001/operation.json")).get("state").getAsString());
    }
    @Test void verifiedTerminalEvidenceAllowsCleanupAndNextRun() throws Exception {
        OwnedSessionTest.Fixture fixture = fixture();
        ProductRun run = open(fixture); run.attach(fixture.start());
        Path proof = Files.writeString(directory.resolve("run/proof.json"), "{\"verified\":true}");
        try (ProductRun.Operation operation = run.reserve(ProductRun.Kind.INSTALL, input())) {
            operation.dispatch();
            assertThrows(IOException.class, operation::dispatch);
            operation.complete(proof);
        }
        run.close();
        assertEquals(1, fixture.ops.stream().filter("session.stop"::equals).count());
        assertEquals("complete", RunFiles.read(directory.resolve("run/run.json")).get("state").getAsString());
        ProductRun next = ProductRun.open(directory.resolve("leases"), fixture.machine, RunFiles.directory(directory.resolve("next")));
        assertThrows(IOException.class, next::close);
    }
    @Test void stoppedSessionCanFinishRunCleanupWithoutASecondStopOrNewMutation() throws Exception {
        OwnedSessionTest.Fixture fixture = fixture();
        ProductRun run = open(fixture);
        OwnedSession session = fixture.start();
        run.attach(session);
        session.close();
        run.releaseLease();
        try (ProductRun resumed = ProductRun.resume(directory.resolve("leases"), fixture.machine,
                directory.resolve("run"))) {
            OwnedSession stopped = OwnedSession.resume(new VoyemuClient(fixture), "profile",
                    fixture.machine, fixture.work);
            assertTrue(stopped.cleanupVerified());
            resumed.attach(stopped);
            assertThrows(IOException.class, resumed::requireTestReady);
            assertThrows(IOException.class, () -> resumed.reserve(ProductRun.Kind.INSTALL, input()));
            assertThrows(IOException.class, () -> resumed.beginGui(directory.resolve("run")));
        }
        assertEquals(1, fixture.ops.stream().filter("session.stop"::equals).count());
        assertEquals("complete", RunFiles.read(directory.resolve("run/run.json")).get("state").getAsString());
    }
    @Test void failureBeforeDispatchDoesNotStrandAnOwnedGuest() throws Exception {
        OwnedSessionTest.Fixture fixture = fixture();
        try (ProductRun run = open(fixture)) {
            run.attach(fixture.start());
            try (ProductRun.Operation ignored = run.reserve(ProductRun.Kind.UNINSTALL, input())) { }
        }
        assertEquals(1, fixture.ops.stream().filter("session.stop"::equals).count());
        assertEquals("not-dispatched", RunFiles.read(directory.resolve("run/operations/000001/operation.json")).get("state").getAsString());
    }
    @Test void resumesTheSameUnknownOperationWithoutReplayingTheGuestMutation() throws Exception {
        OwnedSessionTest.Fixture fixture = fixture();
        ProductRun first = open(fixture);
        first.attach(fixture.start());
        try (ProductRun.Operation operation = first.reserve(ProductRun.Kind.INSTALL, input())) { operation.dispatch(); }
        assertThrows(IOException.class, first::close);
        Path work = directory.resolve("run");
        try (ProductRun resumed = ProductRun.resume(directory.resolve("leases"), fixture.machine, work)) {
            OwnedSession session = OwnedSession.resume(new VoyemuClient(fixture), "profile", fixture.machine, fixture.work);
            resumed.attach(session);
            ProductRun.Operation pending = resumed.pending();
            assertNotNull(pending);
            assertEquals(ProductRun.Kind.INSTALL, pending.kind());
            assertThrows(IOException.class, pending::dispatch);
            assertThrows(IOException.class, () -> resumed.reserve(ProductRun.Kind.INSTALL, input()));
            Path proof = Files.writeString(work.resolve("reconciled.json"), "{\"observed\":true}");
            pending.complete(proof);
        }
        assertEquals(1, fixture.ops.stream().filter("session.start"::equals).count());
        assertEquals(1, fixture.ops.stream().filter("session.stop"::equals).count());
    }

    @Test void unavailablePreflightWithoutDispatchDoesNotLeaveAFalsePendingOwner() throws Exception {
        OwnedSessionTest.Fixture fixture = fixture();
        try (ProductRun run = open(fixture)) {
            VoyemuClient unavailable = new VoyemuClient((bytes, timeout) -> {
                assertEquals("info", RunFiles.parse(bytes).get("op").getAsString());
                return new VoyemuClient.ProcessResult(1, false, "", "unavailable");
            });
            assertThrows(IOException.class, () -> run.start(unavailable, "profile", false, false));
        }
        assertEquals("complete", RunFiles.read(directory.resolve("run/run.json")).get("state").getAsString());
        assertFalse(fixture.ops.contains("session.stop"));
        ProductRun next = ProductRun.open(directory.resolve("leases"), fixture.machine, RunFiles.directory(directory.resolve("next")));
        assertThrows(IOException.class, next::close);
    }

    @Test void explicitStartRejectionReleasesOwnerWithoutStoppingAGuest() throws Exception {
        OwnedSessionTest.Fixture fixture = fixture();
        VoyemuClient rejected = new VoyemuClient((bytes, timeout) -> {
            JsonObject request = RunFiles.parse(bytes);
            if (!"session.start".equals(request.get("op").getAsString())) return fixture.execute(bytes, timeout);
            fixture.ops.add("session.start");
            JsonObject response = OwnedSessionTest.envelope(request, new JsonObject());
            response.addProperty("ok", false);
            response.remove("result");
            response.add("error", OwnedSessionTest.json("{\"code\":\"VEHICLE_STARTUP\",\"message\":\"missing scenario\",\"outcome\":\"not_started\",\"retryable\":false}"));
            return new VoyemuClient.ProcessResult(3, false, response.toString(), "");
        });
        try (ProductRun run = open(fixture)) {
            assertThrows(IOException.class, () -> run.start(rejected, "profile", false, false));
        }
        assertEquals("complete", RunFiles.read(directory.resolve("run/run.json")).get("state").getAsString());
        assertFalse(fixture.ops.contains("session.stop"));
        ProductRun next = ProductRun.open(directory.resolve("leases"), fixture.machine, RunFiles.directory(directory.resolve("next")));
        assertThrows(IOException.class, next::close);
    }

    @Test void recoveredStartRejectionReleasesOwnerWithoutReplay() throws Exception {
        OwnedSessionTest.Fixture fixture = fixture();
        fixture.unknownStart = true;
        VoyemuClient rejected = new VoyemuClient((bytes, timeout) -> {
            JsonObject request = RunFiles.parse(bytes);
            var result = fixture.execute(bytes, timeout);
            if (!"operation.get".equals(request.get("op").getAsString())) return result;
            JsonObject response = RunFiles.parse(result.stdout());
            JsonObject record = response.getAsJsonObject("result");
            record.addProperty("outcome", "not_started");
            record.remove("owned_session_observed");
            record.remove("ownership_id");
            JsonObject nested = record.getAsJsonObject("response");
            nested.addProperty("ok", false);
            nested.remove("result");
            nested.add("error", OwnedSessionTest.json("{\"code\":\"VEHICLE_STARTUP\",\"message\":\"missing scenario\",\"outcome\":\"not_started\",\"retryable\":false}"));
            return new VoyemuClient.ProcessResult(0, false, response.toString(), "");
        });
        try (ProductRun run = open(fixture)) {
            assertThrows(IOException.class, () -> run.start(rejected, "profile", false, false));
        }
        assertEquals("complete", RunFiles.read(directory.resolve("run/run.json")).get("state").getAsString());
        assertEquals(1, fixture.ops.stream().filter("session.start"::equals).count());
        assertFalse(fixture.ops.contains("session.stop"));
    }
    @Test void pendingGuiRetainsSameGuestAndBlocksGenericResumeWithoutReplay() throws Exception {
        OwnedSessionTest.Fixture fixture = fixture(); ProductRun run = open(fixture);
        OwnedSession session = fixture.start(); run.attach(session);
        Path scope = RunFiles.directory(directory.resolve("run/gui")); run.beginGui(scope);
        assertThrows(IOException.class, () -> run.beginGui(scope));
        assertThrows(IOException.class, session::close);
        assertFalse(fixture.ops.contains("session.stop"));
        assertThrows(IOException.class, run::close);
        assertThrows(IOException.class, () -> ProductRun.resume(directory.resolve("leases"), fixture.machine, directory.resolve("run")));
        assertEquals(1, fixture.ops.stream().filter("session.start"::equals).count());
        assertFalse(fixture.ops.contains("session.stop"));
        assertEquals("pending", RunFiles.read(directory.resolve("run/gui-owner.json")).get("state").getAsString());
    }
    @Test void verifiedGuiAndProductEvidenceReleaseOnlyTheOriginalGuestOnce() throws Exception {
        OwnedSessionTest.Fixture fixture = fixture(); ProductRun run = open(fixture); run.attach(fixture.start());
        Path scope = RunFiles.directory(directory.resolve("run/gui")); run.beginGui(scope);
        Path proof = Files.writeString(scope.resolve("proof.json"), "{\"hostFixture\":true}");
        try (ProductRun.Operation operation = run.reserve(ProductRun.Kind.INSTALL, input())) {
            operation.dispatch();
            assertThrows(IOException.class, () -> run.completeGui(proof));
            operation.complete(proof);
        }
        assertThrows(IOException.class, () -> run.completeGui(directory.resolve("foreign")));
        Path unrelated = Files.writeString(directory.resolve("run/unrelated.json"), "{}");
        assertThrows(IOException.class, () -> run.completeGui(unrelated));
        run.completeGui(proof);
        assertThrows(IOException.class, () -> run.beginGui(scope));
        run.close();
        assertEquals(1, fixture.ops.stream().filter("session.start"::equals).count());
        assertEquals(1, fixture.ops.stream().filter("session.stop"::equals).count());
        assertEquals("complete", RunFiles.read(directory.resolve("run/gui-owner.json")).get("state").getAsString());
    }
    @Test void foreignGuiScopeIsRejectedBeforeRetainingAnIdleGuest() throws Exception {
        OwnedSessionTest.Fixture fixture = fixture(); ProductRun run = open(fixture); run.attach(fixture.start());
        assertThrows(IOException.class, () -> run.beginGui(directory));
        run.close(); assertEquals(1, fixture.ops.stream().filter("session.stop"::equals).count());
    }
    @Test void sharedGuiRecipesRejectForeignSessionBeforeAnyLaunchOrSecondOwner() throws Exception {
        OwnedSessionTest.Fixture fixture = fixture(); ProductRun run = open(fixture); run.attach(fixture.start());
        OwnedSessionTest foreignHarness = new OwnedSessionTest(); foreignHarness.directory = RunFiles.directory(directory.resolve("foreign"));
        OwnedSessionTest.Fixture other = foreignHarness.new Fixture();
        try (OwnedSession foreign = other.start()) {
            assertThrows(IOException.class, () -> ru.voboost.tests.installer.gui.GuiScenario.execute(null, null, null, null, run, foreign, directory.resolve("run/gui")));
            assertFalse(Files.exists(directory.resolve("run/gui-owner.json")));
            assertEquals(1, fixture.ops.stream().filter("session.start"::equals).count());
            assertFalse(fixture.ops.contains("session.stop"));
        }
        run.close(); assertEquals(1, fixture.ops.stream().filter("session.stop"::equals).count());
    }

    @Test
    void failedInstallRemainsFailedAndOnlyNormalCleanupCanReleaseTheOwner() throws Exception {
        OwnedSessionTest.Fixture fixture = fixture();
        ProductRun run = open(fixture);
        run.attach(fixture.start());
        JsonObject input = new JsonObject();
        input.addProperty("manifest", "a".repeat(64));
        input.addProperty("device", "emulator-5554");
        input.addProperty("priorBoot", "12345678-1234-1234-1234-123456789abc");
        JsonObject receipt = new JsonObject();
        receipt.addProperty("schemaVersion", 1);
        receipt.addProperty("resolution", "failed-reconciled");
        receipt.addProperty("acceptance", "FAILED");
        receipt.addProperty("functionalAcceptance", false);
        for (String key : java.util.List.of("manifest", "device", "priorBoot")) {
            receipt.add(key, input.get(key));
        }
        receipt.addProperty("bootId", "22345678-1234-1234-1234-123456789abc");
        receipt.addProperty("nativePhase", "install-complete");
        receipt.addProperty("nativeOutcome", "complete");
        receipt.addProperty("diagnostic", "Install failed: installed content identity is unavailable");
        Path proof = directory.resolve("run/failed-install.json");
        RunFiles.write(proof, receipt);
        try (ProductRun.Operation operation = run.reserve(ProductRun.Kind.INSTALL, input)) {
            operation.dispatch();
            operation.failInstall(proof);
        }
        assertNull(run.pending());
        assertTrue(run.hasFailedInstallHistory());
        assertThrows(IOException.class, () -> run.reserve(ProductRun.Kind.INSTALL, input));
        assertThrows(IOException.class, run::close);
        assertFalse(fixture.ops.contains("session.stop"));
        assertEquals("failed-reconciled", RunFiles.read(directory.resolve(
                "run/operations/000001/operation.json")).get("state").getAsString());
        ProductRun resumed = ProductRun.resume(directory.resolve("leases"), fixture.machine,
                directory.resolve("run"));
        resumed.attach(OwnedSession.resume(new VoyemuClient(fixture), "profile",
                fixture.machine, fixture.work));
        assertNull(resumed.pending());
        assertTrue(resumed.hasFailedInstallHistory());
        assertThrows(IOException.class, () -> resumed.reserve(ProductRun.Kind.UPDATE, input));
        JsonObject cleanupInput = input.deepCopy();
        cleanupInput.addProperty("phase", "failed-install-cleanup");
        cleanupInput.addProperty("priorBoot", "22345678-1234-1234-1234-123456789abc");
        JsonObject rejection = new JsonObject();
        rejection.addProperty("schemaVersion", 1);
        rejection.addProperty("kind", "installer-uninstall-rejected");
        rejection.addProperty("functionalAcceptance", false);
        rejection.addProperty("bootId", cleanupInput.get("priorBoot").getAsString());
        rejection.addProperty("diagnostic", "Emulator acceptance failed: Cluster provisioning is missing; signed removal cannot be verified");
        for (String key : java.util.List.of("device", "manifest")) rejection.add(key, cleanupInput.get(key));
        Path rejectionFile = directory.resolve("run/rejected-uninstall.json");
        RunFiles.write(rejectionFile, rejection);
        try (ProductRun.Operation rejected = resumed.reserve(ProductRun.Kind.UNINSTALL, cleanupInput)) {
            rejected.dispatch();
            rejected.rejectUninstall(rejectionFile);
        }
        assertNull(resumed.pending());
        assertTrue(resumed.hasFailedInstallHistory());
        assertThrows(IOException.class, resumed::requireTestReady);
        assertThrows(IOException.class, () -> resumed.reserve(ProductRun.Kind.UPDATE, input));
        Path absence = Files.writeString(directory.resolve("run/absence.json"),
                "{\"transactionId\":\"" + "b".repeat(64) + "\",\"bootId\":"
                        + "\"32345678-1234-1234-1234-123456789abc\",\"checks\":[\"root\","
                        + "\"app-data\",\"init-file\",\"package\",\"app-op\",\"mounts\","
                        + "\"processes\",\"init-services\",\"localization-overlays\",\"policy\"]}\n");
        try (ProductRun.Operation cleanup = resumed.reserve(ProductRun.Kind.UNINSTALL,
                cleanupInput)) {
            cleanup.dispatch();
            assertThrows(IOException.class, () -> cleanup.complete(proof));
            cleanup.complete(absence);
        }
        resumed.close();
        assertEquals(1, fixture.ops.stream().filter("session.stop"::equals).count());
        assertEquals("failed-reconciled", RunFiles.read(directory.resolve(
                "run/operations/000001/operation.json")).get("state").getAsString());
    }
}
