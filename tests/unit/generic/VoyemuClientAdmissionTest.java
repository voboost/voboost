package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import voyemu.journal.Journal;

class VoyemuClientAdmissionTest {
    @TempDir Path directory;
    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    @Test void directUnknownDispatchCannotRepeatOrResumeAfterReopen() throws Exception {
        AtomicInteger sends = new AtomicInteger();
        VoyemuClient client = new VoyemuClient((request, timeout) -> {
            assertTrue(java.nio.file.Files.exists(directory.resolve("dispatch")));
            sends.incrementAndGet();
            return new VoyemuClient.ProcessResult(null, true, "", "timeout");
        });
        var attempt = client.start("a".repeat(32), "profile", "machine", null, false);
        try (Journal journal = Journal.open(directory)) {
            journal.reserve("profile:profile", "owner", attempt.requestId(), attempt.json().getBytes(StandardCharsets.UTF_8));
            assertEquals(VoyemuClient.State.UNKNOWN, client.executeReserved(attempt, TIMEOUT, journal, "owner").state());
            assertEquals(VoyemuClient.State.UNKNOWN, client.executeReserved(attempt, TIMEOUT, journal, "owner").state());
            assertEquals(1, sends.get());
        }
        try (Journal reopened = Journal.open(directory)) {
            assertEquals(VoyemuClient.State.UNKNOWN, client.executeReserved(attempt, TIMEOUT, reopened, "owner").state());
        }
        assertEquals(1, sends.get());
    }

    @Test void reservationReopenedBeforeDispatchIsLookupOnly() throws Exception {
        AtomicInteger sends = new AtomicInteger();
        VoyemuClient client = new VoyemuClient((request, timeout) -> {
            sends.incrementAndGet();
            return new VoyemuClient.ProcessResult(null, true, "", "timeout");
        });
        var attempt = client.start("a".repeat(32), "profile", "machine", null, false);
        try (Journal journal = Journal.open(directory)) {
            journal.reserve("profile:profile", "owner", attempt.requestId(), attempt.json().getBytes(StandardCharsets.UTF_8));
        }
        try (Journal journal = Journal.open(directory)) {
            assertEquals(VoyemuClient.State.UNKNOWN, client.executeReserved(attempt, TIMEOUT, journal, "owner").state());
            assertNotNull(client.lookupReserved("b".repeat(32), journal, "owner", attempt));
        }
        assertEquals(0, sends.get());
    }

    @Test void claimedButUnsentRequestCannotResumeAfterCrash() throws Exception {
        AtomicInteger sends = new AtomicInteger();
        VoyemuClient client = new VoyemuClient((request, timeout) -> {
            sends.incrementAndGet();
            return new VoyemuClient.ProcessResult(null, true, "", "timeout");
        });
        var attempt = client.start("a".repeat(32), "profile", "machine", null, false);
        try (Journal journal = Journal.open(directory)) {
            byte[] request = attempt.json().getBytes(StandardCharsets.UTF_8);
            journal.reserve("profile:profile", "owner", attempt.requestId(), request);
            journal.claim("profile:profile", "owner", attempt.requestId(), request);
            assertEquals(VoyemuClient.State.UNKNOWN, client.executeReserved(attempt, TIMEOUT, journal, "owner").state());
        }
        try (Journal journal = Journal.open(directory)) {
            assertEquals(VoyemuClient.State.UNKNOWN, client.executeReserved(attempt, TIMEOUT, journal, "owner").state());
            assertNotNull(client.lookupReserved("b".repeat(32), journal, "owner", attempt));
        }
        assertEquals(0, sends.get());
    }

    @Test void independentClientsSharingReservationCannotDoubleSend() throws Exception {
        AtomicInteger sends = new AtomicInteger();
        VoyemuClient.Transport transport = (request, timeout) -> {
            sends.incrementAndGet();
            throw new java.io.IOException("response lost");
        };
        VoyemuClient first = new VoyemuClient(transport), second = new VoyemuClient(transport);
        var attempt = first.start("a".repeat(32), "profile", "machine", null, false);
        try (Journal journal = Journal.open(directory)) {
            journal.reserve("profile:profile", "owner", attempt.requestId(), attempt.json().getBytes(StandardCharsets.UTF_8));
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger completed = new AtomicInteger();
            Thread[] workers = {new Thread(() -> dispatch(first, attempt, journal, start, completed)),
                new Thread(() -> dispatch(second, attempt, journal, start, completed))};
            for (Thread worker : workers) { worker.setDaemon(true); worker.start(); }
            start.countDown();
            for (Thread worker : workers) { worker.join(5000); assertFalse(worker.isAlive()); }
            assertEquals(2, completed.get());
            assertEquals(1, sends.get());
        }
    }

    private static void dispatch(VoyemuClient client, VoyemuClient.Attempt attempt, Journal journal,
            CountDownLatch start, AtomicInteger completed) {
        try {
            start.await();
            client.executeReserved(attempt, TIMEOUT, journal, "owner");
            completed.incrementAndGet();
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
