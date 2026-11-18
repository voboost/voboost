package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.tests.MountSession;
import ru.voboost.tests.ProductRun;
import ru.voboost.tests.RunFiles;

class EmulatorPreparationTest {
    @TempDir Path directory;

    @Test void stoppedRunAllowsMountCleanupWithoutRestartOrSecondStop() throws Exception {
        OwnedSessionTest harness = new OwnedSessionTest();
        harness.directory = directory;
        OwnedSessionTest.Fixture fixture = harness.new Fixture();
        Path work = RunFiles.directory(directory.resolve("session-1"));
        JsonObject receipt = new JsonObject();
        receipt.addProperty("work", "session-1");
        receipt.addProperty("session", OwnedSessionTest.SESSION);
        try (ProductRun run = ProductRun.open(directory.resolve("leases"), fixture.machine, work)) {
            run.start(new VoyemuClient(fixture), "profile", false, false);
            assertFalse(EmulatorPreparation.stopped(fixture.machine, directory, receipt,
                    new VoyemuClient(fixture), "profile"));
        }
        assertTrue(EmulatorPreparation.stopped(fixture.machine, directory, receipt,
                new VoyemuClient(fixture), "profile"));
        JsonObject foreign = receipt.deepCopy();
        foreign.addProperty("session", "d".repeat(32));
        assertThrows(IOException.class, () -> EmulatorPreparation.stopped(fixture.machine,
                directory, foreign, new VoyemuClient(fixture), "profile"));
        assertEquals(1, fixture.ops.stream().filter("session.start"::equals).count());
        assertEquals(1, fixture.ops.stream().filter("session.stop"::equals).count());
    }

    @Test void detachFailureCanResumeWithAnAlreadyDetachedOtherCandidate() throws Exception {
        Path selected = directory.resolve("candidate.dmg");
        JsonObject receipt = new JsonObject();
        receipt.addProperty("file", selected.toString());
        receipt.addProperty("manifest", "manifest");
        receipt.addProperty("release", "release");
        receipt.addProperty("catalog", "catalog");
        receipt.addProperty("mountDevice", "original-device");
        AtomicInteger detachments = new AtomicInteger();
        class Backend implements MountSession.Backend {
            boolean mounted = true;
            boolean fail = true;
            public void attach() { throw new AssertionError("cleanup cannot attach"); }
            public boolean mounted() { return mounted; }
            public void detach() throws IOException {
                detachments.incrementAndGet();
                if (fail) throw new IOException("detach failed");
                mounted = false;
            }
            public void removeWork() {}
        }
        Backend backend = new Backend();
        Path work = directory.resolve("mount");
        var candidate = EmulatorPreparation.Candidate.cleanup(selected, receipt, work.resolve("app"),
                new MountSession(work, backend), "original-device");
        assertThrows(IOException.class, candidate::close);
        backend.fail = false;
        candidate = EmulatorPreparation.Candidate.cleanup(selected, receipt, work.resolve("app"),
                new MountSession(work, backend), "original-device");
        candidate.close();
        var detached = EmulatorPreparation.Candidate.cleanup(selected, receipt, work.resolve("app"),
                new MountSession(work, backend), null);
        detached.close();
        assertEquals(2, detachments.get());
        assertThrows(IOException.class, () -> EmulatorPreparation.Candidate.cleanup(selected,
                receipt, work.resolve("app"), new MountSession(work, backend), "foreign-device"));
        assertThrows(IOException.class, () -> EmulatorPreparation.Candidate.cleanup(directory.resolve("other.dmg"),
                receipt, work.resolve("app"), new MountSession(work, backend), null));
    }

    @Test void detachedCandidateCleanupDoesNotRequireTheApplicationOrReattach() throws Exception {
        Path selected = Files.createFile(directory.resolve("candidate.dmg"));
        Path work = RunFiles.directory(directory.resolve("candidate"));
        JsonObject receipt = new JsonObject();
        receipt.addProperty("file", selected.toString());
        receipt.addProperty("manifest", "manifest");
        receipt.addProperty("release", "release");
        receipt.addProperty("catalog", "catalog");
        receipt.addProperty("mountDevice", "detached-device");
        var candidate = EmulatorPreparation.Candidate.claimForCleanup(work, selected, receipt);
        assertFalse(Files.exists(candidate.app()));
        candidate.close();
        EmulatorPreparation.Candidate.claimForCleanup(work, selected, receipt).close();
        assertFalse(Files.exists(work.resolve("volume")));
        assertFalse(Files.exists(work.resolve("attach.log")));
        assertFalse(Files.exists(work.resolve("detach.log")));
    }
}
