package ru.voboost.voyemu;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import voyemu.journal.Journal;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Host tests for durable admission with a fake transport. Reserved mutations may reach
 * the fake transport once; recovery sends only read-only operation lookups.
 */
class AdmissionRunnerTest {
    static final Duration TIMEOUT = Duration.ofSeconds(5);

    /** Fake transport recording every request; can be scripted per call. */
    static final class FakeTransport implements VoyemuClient.Transport {
        final List<String> requests = new ArrayList<>();
        final List<java.util.function.Supplier<VoyemuClient.ProcessResult>> script = new ArrayList<>();
        final AtomicInteger remainingDispatches = new AtomicInteger(Integer.MAX_VALUE);
        @Override public VoyemuClient.ProcessResult execute(String request, Duration timeout) {
            requests.add(request);
            if (remainingDispatches.decrementAndGet() < 0) throw new AssertionError("single-send violated: extra transport dispatch");
            java.util.function.Supplier<VoyemuClient.ProcessResult> producer = script.isEmpty() ? () -> unknown("scripted") : script.remove(0);
            return producer.get();
        }
        static VoyemuClient.ProcessResult unknown(String diagnostic) { return new VoyemuClient.ProcessResult(null, true, "", diagnostic); }
        static VoyemuClient.ProcessResult ok(String stdout) { return new VoyemuClient.ProcessResult(0, false, stdout, ""); }
    }

    static String envelope(String requestJson) { return "{\"schema\":1,\"request_id\":\"<RID>\",\"operation_id\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"ok\":true,\"elapsed_ms\":1,\"result\":" + requestJson + "}"; }

    @TempDir Path dir;
    @TempDir Path journalDirUnused;

    Path domain(String name) throws IOException { Path p = dir.resolve(name); Files.createDirectories(p); return p; }

    String rid() { return UUID.randomUUID().toString().replace("-", ""); }

    static VoyemuClient.Session ownerSession() {
        return new VoyemuClient.Session("b".repeat(32), "c".repeat(32));
    }

    @Test void invalidArgumentsDoNotConsumeAdmission() throws Exception {
        FakeTransport transport = new FakeTransport();
        VoyemuClient client = new VoyemuClient(transport);
        var original = client.start(rid(), "profile", "machine", null, false);
        Path journalDirectory = domain("invalid-arguments");
        try (AdmissionRunner runner = AdmissionRunner.open(client, journalDirectory, "machine-1")) {
            assertThrows(NullPointerException.class, () -> runner.run(original, TIMEOUT, null));
            assertThrows(IllegalArgumentException.class, () -> runner.run(original, Duration.ZERO));
            assertThrows(IllegalArgumentException.class, () -> runner.run(original, Duration.ofMillis(-1)));
            assertFalse(Files.exists(journalDirectory.resolve("reservation")));
            assertTrue(transport.requests.isEmpty());
            assertEquals(VoyemuClient.State.UNKNOWN, runner.run(original, TIMEOUT).state());
            assertEquals(List.of(original.json()), transport.requests);
            assertThrows(IOException.class, () -> runner.run(original, TIMEOUT));
        }
    }

    @Test void failureBeforeDispatchDoesNotSendAndBlocksResendForever() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.remainingDispatches.set(1); // only the later read-only lookup may ever send
        VoyemuClient client = new VoyemuClient(transport);
        var original = client.start(rid(), "profile", "machine", null, false);
        try (AdmissionRunner runner = AdmissionRunner.open(client, domain("d1"), "machine-1")) {
            RuntimeException boom = assertThrows(RuntimeException.class,
                () -> runner.run(original, TIMEOUT, () -> { throw new RuntimeException("crash before dispatch"); }));
            assertEquals("crash before dispatch", boom.getMessage());
            assertEquals(0, transport.requests.size()); // durable record exists yet nothing was sent
            assertTrue(Files.exists(dir.resolve("d1").resolve("reservation")));
            assertThrows(IOException.class, () -> runner.run(original, TIMEOUT));
            // recovery is still possible while the lock is held: read-only lookup (server would report not found)
            String lookupId = rid();
            assertEquals(AdmissionRunner.Outcome.UNKNOWN, runner.recover(original, () -> lookupId, TIMEOUT).outcome);
        }
        // after lock release a fresh runner sees the CREATE_NEW fence: the reservation blocks a second send
        try (AdmissionRunner second = AdmissionRunner.open(client, domain("d1"), "machine-1")) {
            assertThrows(IOException.class, () -> second.run(original, TIMEOUT));
        }
        assertEquals(1, transport.requests.size()); // exactly the one operation.get
        assertTrue(transport.requests.get(0).contains("\"op\":\"operation.get\""));
    }

    @Test void timeoutUnknownOutcomeRecoversThroughOperationGet() throws Exception {
        FakeTransport transport = new FakeTransport();
        String lookupId = rid();
        // first: the real mutation send times out at the transport (unknown outcome)
        // second: operation.get answer with a terminal completed record
        transport.script.add(() -> FakeTransport.unknown("timeout")); // send dispatched, outcome unknown
        VoyemuClient client = new VoyemuClient(transport);
        var original = client.start(rid(), "profile", "machine", null, false);
        try (AdmissionRunner runner = AdmissionRunner.open(client, domain("d2"), "machine-1")) {
            VoyemuClient.Reply first = runner.run(original, TIMEOUT);
            assertEquals(VoyemuClient.State.UNKNOWN, first.state()); // timeout: outcome unknown
            assertEquals(1, transport.requests.size());
            assertEquals(original.json(), transport.requests.get(0)); // exact reserved bytes were sent
            String requestId = original.requestId();
            String record = "{\"operation_id\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"status\":\"terminal\",\"request_id\":\"" + requestId
                + "\",\"op\":\"session.start\",\"scope\":\"profile:profile\",\"outcome\":\"completed\","
                + "\"response\":{\"schema\":1,\"request_id\":\"" + requestId + "\",\"operation_id\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"ok\":true,\"elapsed_ms\":1,\"result\":{}}}";
            transport.script.add(() -> FakeTransport.ok(envelope(record).replace("<RID>", lookupId)));
            var result = runner.recover(original, () -> lookupId, TIMEOUT);
            assertEquals(AdmissionRunner.Outcome.SUCCESS, result.outcome);
            assertEquals(2, transport.requests.size());
            assertTrue(transport.requests.get(1).contains("\"op\":\"operation.get\""));
            assertTrue(transport.requests.get(1).contains("\"target\":{\"request_id\":\"" + original.requestId() + "\"}"));
            // no replay ever: a second run call stays fenced
            assertThrows(IOException.class, () -> runner.run(original, TIMEOUT));
        }
    }

    @Test void confirmedNotStartedFailureIsFailureEverythingElseStaysUnknown() throws Exception {
        for (String outcome : new String[] {"unknown", "completed"}) {
            FakeTransport transport = new FakeTransport();
            VoyemuClient client = new VoyemuClient(transport);
            var original = client.reboot(rid(), "profile", ownerSession(), true, 1000);
            String requestId = original.requestId();
            String lookupId = rid();
            try (AdmissionRunner runner = AdmissionRunner.open(client, domain("fail-" + outcome), "machine-1")) {
                assertEquals(VoyemuClient.State.UNKNOWN, runner.run(original, TIMEOUT).state()); // transport returns unknown
                String record = "{\"operation_id\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"status\":\"terminal\","
                    + "\"request_id\":\"" + requestId + "\",\"outcome\":\"" + outcome + "\","
                    + "\"response\":{\"schema\":1,\"request_id\":\"" + requestId + "\",\"operation_id\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"ok\":false,\"elapsed_ms\":1,"
                    + "\"error\":{\"code\":\"X\",\"message\":\"x\",\"retryable\":false,\"outcome\":\"" + outcome + "\"}}}";
                transport.script.add(() -> FakeTransport.ok(envelope(record).replace("<RID>", lookupId)));
                var result = runner.recover(original, () -> lookupId, TIMEOUT);
                assertEquals(AdmissionRunner.Outcome.UNKNOWN, result.outcome);
            }
            assertEquals(2, transport.requests.size());
        }
    }

    @Test void restartAfterCrashRecoversButNewMutationIsBlockedByReservationFence() throws Exception {
        FakeTransport transport = new FakeTransport();
        VoyemuClient client = new VoyemuClient(transport);
        var original = client.start(rid(), "profile", "machine", null, false);
        String lookupId = rid();
        try (AdmissionRunner first = AdmissionRunner.open(client, domain("restart"), "machine-1")) {
            assertEquals(VoyemuClient.State.UNKNOWN, first.run(original, TIMEOUT).state()); // send, outcome unknown
        }
        // process "restart": a second runner instance over the same domain
        try (AdmissionRunner second = AdmissionRunner.open(client, domain("restart"), "machine-1")) {
            assertThrows(IOException.class, () -> second.run(original, TIMEOUT)); // reservation fence
            assertThrows(IOException.class, () -> second.run(client.start(rid(), "profile", "machine", null, false), TIMEOUT));
            String requestId = original.requestId();
            String record = "{\"operation_id\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"status\":\"pending\",\"request_id\":\"" + requestId + "\"}";
            transport.script.add(() -> FakeTransport.ok(envelope(record).replace("<RID>", lookupId)));
            var result = second.recover(original, () -> lookupId, TIMEOUT);
            assertEquals(AdmissionRunner.Outcome.PENDING, result.outcome);
        }
        assertEquals(2, transport.requests.size()); // one mutation send + one read-only lookup max
    }

    @Test void foreignReplyIsRejectedWithoutFalsePositive() throws Exception {
        FakeTransport transport = new FakeTransport();
        VoyemuClient client = new VoyemuClient(transport);
        var original = client.start(rid(), "profile", "machine", null, false);
        String lookupId = rid();
        try (AdmissionRunner runner = AdmissionRunner.open(client, domain("foreign"), "machine-1")) {
            assertEquals(VoyemuClient.State.UNKNOWN, runner.run(original, TIMEOUT).state());
            // a reply claiming a different request id cannot correlate: classify fails closed
            String record = "{\"operation_id\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"status\":\"terminal\",\"request_id\":\"ffffffffffffffffffffffffffffffff\",\"outcome\":\"completed\",\"response\":{\"schema\":1,\"request_id\":\"ffffffffffffffffffffffffffffffff\",\"operation_id\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"ok\":true,\"elapsed_ms\":1,\"result\":{}}}";
            String mismmatched = envelope(record).replace("<RID>", "ffffffffffffffffffffffffffffffff");
            transport.script.add(() -> FakeTransport.ok(mismmatched)); // wrong outer request_id -> execute returns UNKNOWN
            var result = runner.recover(original, () -> lookupId, TIMEOUT);
            assertEquals(AdmissionRunner.Outcome.UNKNOWN, result.outcome);
            assertEquals(VoyemuClient.State.UNKNOWN, result.lookupReply().state());
        }
        assertEquals(2, transport.requests.size());
    }

    @Test void identityBindingRejectsForeignIdentityLabel() throws Exception {
        FakeTransport transport = new FakeTransport();
        VoyemuClient client = new VoyemuClient(transport);
        var original = client.start(rid(), "profile", "machine", null, false);
        try (AdmissionRunner runner = AdmissionRunner.open(client, domain("identity"), "machine-1")) {
            assertEquals(VoyemuClient.State.UNKNOWN, runner.run(original, TIMEOUT).state());
        }
        // a foreign identity label cannot retrieve the reservation: inspect fails closed
        try (AdmissionRunner foreign = AdmissionRunner.open(client, domain("identity"), "machine-2")) {
            assertThrows(IOException.class, () -> foreign.recover(original, () -> rid(), TIMEOUT));
        }
        assertEquals(1, transport.requests.size()); // only the mutation send; foreign lookup never dispatched
    }

    @Test void concurrentRunnersCannotDoubleSendOrDoubleReserve() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.remainingDispatches.set(1);
        VoyemuClient client = new VoyemuClient(transport);
        // session-scoped mutation so both runners target the same domain scope
        var original = client.stop(rid(), "profile", ownerSession());
        try (AdmissionRunner runner = AdmissionRunner.open(client, domain("concurrent"), "machine-1")) {
            CountDownLatch start = new CountDownLatch(1);
            List<Thread> threads = new ArrayList<>();
            AtomicInteger sent = new AtomicInteger(), refused = new AtomicInteger();
            for (int i = 0; i < 4; i++) {
                Thread t = new Thread(() -> {
                    try { start.await(); runner.run(original, TIMEOUT); sent.incrementAndGet(); }
                    catch (Throwable expected) { refused.incrementAndGet(); }
                });
                t.setDaemon(true); threads.add(t); t.start();
            }
            start.countDown();
            for (Thread t : threads) t.join(10000);
            assertEquals(1, sent.get()); // exactly one run call may proceed
            assertEquals(3, refused.get()); // every other concurrent send was refused
            assertTrue(Files.exists(dir.resolve("concurrent").resolve("reservation")));
            // a concurrent second process cannot even join the domain: the journal lock is exclusive
            assertEquals(IOException.class, assertThrows(IOException.class,
                () -> AdmissionRunner.open(client, domain("concurrent"), "machine-1")).getClass());
        }
        assertEquals(1, transport.requests.size()); // one mutation send; nothing else was permitted
    }

    @Test void cleanupWithoutFalsePassAndClosedRunnerRefusesWork() throws Exception {
        FakeTransport transport = new FakeTransport();
        VoyemuClient client = new VoyemuClient(transport);
        var original = client.start(rid(), "profile", "machine", null, false);
        AdmissionRunner runner = AdmissionRunner.open(client, domain("closed"), "machine-1");
        assertEquals(VoyemuClient.State.UNKNOWN, runner.run(original, TIMEOUT).state());
        runner.close();
        assertThrows(IOException.class, () -> runner.run(original, TIMEOUT));
        assertThrows(IOException.class, () -> runner.recover(original, () -> rid(), TIMEOUT));
        assertEquals(1, transport.requests.size());
        // reservation survives close and keeps blocking every new mutation attempt
        try (AdmissionRunner fresh = AdmissionRunner.open(client, domain("closed"), "machine-1")) {
            assertThrows(IOException.class, () -> fresh.run(client.start(rid(), "profile", "machine", null, false), TIMEOUT));
        }
    }
}
