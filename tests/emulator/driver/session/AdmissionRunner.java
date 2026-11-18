package ru.voboost.voyemu;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import voyemu.journal.Journal;

/**
 * Minimal durable admission/operation lookup coordinator for Voyemu tests.
 *
 * Fail-closed rules proven by construction and by {@code AdmissionRunnerTest}:
 * <ul>
 *   <li>The exact original request bytes are journaled durably (Journal.reserve,
 *       file + directory force) BEFORE any dispatch through the transport.</li>
 *   <li>Each runner instance may dispatch at most one request (single-send guard),
 *       enforced with an AtomicBoolean so the guarantee holds even under concurrent use.</li>
 *   <li>Scope and identity binding: only the exact original mutation attempt may be
 *       sent, and only with the machine/session/ownership identity recorded in the
 *       reservation; VoyemuClient.lookupReserved re-verifies the reservation bytes.</li>
 *   <li>Recovery after unknown outcome: only read-only operation.get lookups built by
 *       lookupReserved are permitted. The original mutation can NEVER be re-dispatched
 *       by this class, even after a restart: reserve is called at most once per instance
 *       and Journal's CREATE_NEW fence blocks every later reservation for the same dir.</li>
 *   <li>A foreign reply (one that does not correlate to our attempt) is rejected;
 *       no affirmative result ever authorizes a replay.</li>
 * </ul>
 *
 * Public {@link VoyemuClient#execute} remains read-only. Reserved mutations reach the
 * private process path only after Journal.claim has consumed the exact durable reservation.
 */
public final class AdmissionRunner implements AutoCloseable {
    /** Terminal report-only outcome the coordinator exposes to callers. */
    public enum Outcome { PENDING, SUCCESS, FAILURE, UNKNOWN }

    /** Final result of one coordinated dispatch attempt; never a replay authorization. */
    public static final class AttemptResult {
        public final Outcome outcome;
        final VoyemuClient.Reply reply;
        AttemptResult(Outcome outcome, VoyemuClient.Reply reply) { this.outcome = outcome; this.reply = reply; }
        public VoyemuClient.Reply lookupReply() { return reply; }
    }

    private final VoyemuClient client;
    private final Journal journal;
    private final String identity;
    private final AtomicBoolean dispatched = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * @param journalDirectory dedicated journal directory for this mutation domain (must exist)
     * @param identity caller-supplied machine/ownership identity label, bound into the reservation
     */
    public static AdmissionRunner open(VoyemuClient client, java.nio.file.Path journalDirectory, String identity) throws IOException {
        Objects.requireNonNull(client); Objects.requireNonNull(journalDirectory);
        if (identity == null || identity.isEmpty()) throw new IOException("identity label required");
        return new AdmissionRunner(client, Journal.open(journalDirectory), identity);
    }

    private AdmissionRunner(VoyemuClient client, Journal journal, String identity) {
        this.client = client; this.journal = journal; this.identity = identity;
    }

    private static void requireMutation(VoyemuClient.Attempt attempt) {
        if (!VoyemuClient.mutation(attempt))
            throw new IllegalArgumentException("coordinator only accepts a mutation attempt");
        if (!attempt.requestId().matches("[0-9a-f]{32}")) throw new IllegalArgumentException("random request id required");
    }

    /**
     * Durable then single send. If the caller aborts (exception from beforeDispatch),
     * the reservation still stands and no other send is possible: {@link #run} can never
     * be entered twice and the journal fence blocks a fresh reservation in this domain.
     */
    public VoyemuClient.Reply run(VoyemuClient.Attempt original, Duration timeout, Runnable beforeDispatch) throws IOException, InterruptedException {
        Objects.requireNonNull(original); Objects.requireNonNull(timeout); Objects.requireNonNull(beforeDispatch);
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout");
        requireMutation(original);
        if (closed.get()) throw new IOException("runner closed");
        if (!dispatched.compareAndSet(false, true)) throw new IOException("mutation already dispatched; never resend");
        // 1. durable record of the exact request BEFORE any dispatch
        journal.reserve(scope(original), identity, original.requestId(),
            original.json().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // 2. exactly one dispatch (the hook may simulate a crash before dispatch); only the proven reservation path may send
        beforeDispatch.run();
        return client.executeReserved(original, timeout, journal, identity);
    }

    public VoyemuClient.Reply run(VoyemuClient.Attempt original, Duration timeout) throws IOException, InterruptedException {
        return run(original, timeout, () -> {});
    }

    /** Reconcile an unknown outcome with read-only op lookup; never replays the mutation.
     * Recovery is valid in a new process/instance: lookupReserved re-verifies the durable
     * reservation bytes, scope and identity before any lookup is sent; a missing, corrupt or
     * foreign reservation fails closed with IOException and nothing is dispatched. */
    public AttemptResult recover(VoyemuClient.Attempt original, Supplier<String> lookupId, Duration timeout) throws IOException, InterruptedException {
        Objects.requireNonNull(original); Objects.requireNonNull(lookupId); Objects.requireNonNull(timeout);
        requireMutation(original);
        if (closed.get()) throw new IOException("runner closed");
        VoyemuClient.Attempt query = client.lookupReserved(lookupId.get(), journal, identity, original);
        VoyemuClient.Reply reply = client.execute(query, timeout);
        if (reply.attempt() != query) throw new IOException("foreign reply: lookup answer did not correlate");
        switch (VoyemuClient.classifyReservedLookup(reply, original)) {
            case PENDING: return new AttemptResult(Outcome.PENDING, reply);
            case SUCCESS: return new AttemptResult(Outcome.SUCCESS, reply);
            case FAILURE: return new AttemptResult(Outcome.FAILURE, reply);
            default: return new AttemptResult(Outcome.UNKNOWN, reply);
        }
    }

    static String scope(VoyemuClient.Attempt attempt) {
        return attempt.op().equals("session.start") ? "profile:" + attempt.profile()
            : "session:" + Objects.requireNonNull(attempt.session()).id();
    }

    @Override public void close() throws IOException {
        if (closed.compareAndSet(false, true)) journal.close();
    }
}
