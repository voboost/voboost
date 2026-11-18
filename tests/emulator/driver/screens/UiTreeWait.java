package ru.voboost.voyemu;

import com.google.gson.*;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.function.LongSupplier;

/**
 * Bounded wait for a concrete left-screen UI state, observed through the read-only
 * <code>session.ui</code> operation of the real Voyemu schema-1 JSON API
 * (<code>output.tree</code> is a rejected legacy name and is never issued).
 *
 * <p>Distinguishes five outcomes: {@link Status#REACHED} (the condition held on a verified, session-identified,
 * left-screen observation; the right/secondary screen is never counted because the server cannot prove
 * a unique left/right conductor per screen in the same observation), {@link Status#UNSUPPORTED} (the server
 * answered <code>TEST_UNSUPPORTED</code>, or returned a result this version cannot identify),
 * {@link Status#ERROR} (a definitive non-unsupported error reply, an uncorrelatable client state, or a malformed
 * tree), {@link Status#AMBIGUOUS} (the selector matched more than one node) and {@link Status#TIMEOUT}
 * (the overall deadline elapsed before the condition was observed; retryable unknown-outcome server states
 * such as TEST_BUSY/TREE_TIMEOUT/TREE_TRANSPORT are waited out inside this budget and only ever surface as
 * TIMEOUT).
 *
 * <p>Every accepted observation must carry the wait's own session id and profile. Selection follows the
 * Voyemu contract exactly: in <code>resource_id</code> mode the server selects every reported node and no
 * node flag is consulted, in <code>resource_id_with_tree_context</code> mode the mandatory node
 * <code>matched</code> boolean is authoritative, and both are crossed against the surface
 * <code>matched_count</code>. A poll is sent only after a measurable sleep since the previous poll and its
 * per-call budget never exceeds the time remaining until the deadline, so repeated polling cannot
 * degenerate into a busy loop and cannot overshoot the deadline.
 */
public final class UiTreeWait {
    public enum Status { REACHED, UNSUPPORTED, ERROR, AMBIGUOUS, TIMEOUT }
    public record Result(Status status, long polls, JsonObject lastResult, String diagnostic) { public Result { Objects.requireNonNull(status); } }
    /** Supplies one UI observation. Implementations must not mutate anything and must correlate each reply to {@code requestId} at most {@code timeoutMs}. */
    public interface Source { VoyemuClient.Reply read(String requestId, int timeoutMs); }

    /** Contract-computed pending condition: the server marks retryable unknown-outcome states
     *  (TEST_BUSY, TREE_TIMEOUT, TREE_TRANSPORT); everything else is terminal. */

    private final Source source;
    private final String profile;
    private final VoyemuClient.Session session;
    private final String resourceId;
    private final String resourcePackage;
    private final String requestIdPrefix;
    private final long pollIntervalMs;
    private final LongSupplier nanoClock;
    private final Sleeper sleeper;
    private final int maxPolls;

    @FunctionalInterface public interface Sleeper { void sleep(long millis) throws InterruptedException; }

    private UiTreeWait(Source source, String profile, VoyemuClient.Session session, String resourceId, String resourcePackage,
                       String requestIdPrefix, long pollIntervalMs, LongSupplier nanoClock, Sleeper sleeper, int maxPolls) {
        if (maxPolls < 1) throw new IllegalArgumentException("at least one poll required");
        this.source = Objects.requireNonNull(source); this.profile = Objects.requireNonNull(profile);
        this.session = Objects.requireNonNull(session); this.requestIdPrefix = Objects.requireNonNull(requestIdPrefix);
        this.resourceId = resourceId; this.resourcePackage = resourcePackage;
        if (pollIntervalMs < 1) throw new IllegalArgumentException("poll interval must be positive");
        this.pollIntervalMs = pollIntervalMs; this.nanoClock = Objects.requireNonNull(nanoClock);
        this.sleeper = Objects.requireNonNull(sleeper); this.maxPolls = maxPolls;
    }

    public static Builder on(Source source, String profile, VoyemuClient.Session session) { return new Builder(source, profile, session); }

    public static final class Builder {
        private final Source source; private final String profile; private final VoyemuClient.Session session;
        private String resourceId; private String resourcePackage;
        private String requestIdPrefix = "ui-wait";
        private long pollIntervalMs = 250;
        private LongSupplier nanoClock = System::nanoTime;
        private Sleeper sleeper = millis -> Thread.sleep(millis);
        private int maxPolls = Integer.MAX_VALUE;
        private Builder(Source source, String profile, VoyemuClient.Session session) { this.source = source; this.profile = profile; this.session = session; }
        /** Waits for this fully qualified resource id ("package:id/name"); absent means the whole tree. */
        public Builder selector(String resourceId) { return selector(resourceId, null); }
        public Builder selector(String resourceId, String resourcePackage) {
            if (resourceId != null) {
                int separator = resourceId.indexOf(":id/");
                if (resourceId.length() > 256 || separator < 1
                    || !VoyemuClient.isIdentifier(resourceId.substring(0, separator), true)
                    || !VoyemuClient.isIdentifier(resourceId.substring(separator + 4), false)) throw new IllegalArgumentException("fully qualified resource ID required");
            } else if (resourcePackage != null) throw new IllegalArgumentException("selector requires resource_id");
            this.resourceId = resourceId; this.resourcePackage = resourcePackage; return this;
        }
        public Builder requestIdPrefix(String prefix) { this.requestIdPrefix = prefix; return this; }
        public Builder pollInterval(Duration interval) { this.pollIntervalMs = interval.toMillis(); return this; }
        /** Test seams: monotonic clock and sleep; production defaults are System::nanoTime and Thread::sleep. */
        public Builder timing(LongSupplier clock, Sleeper sleep) { this.nanoClock = clock; this.sleeper = sleep; return this; }
        public Builder maxPolls(int limit) { this.maxPolls = limit; return this; }
        public UiTreeWait build() { return new UiTreeWait(source, profile, session, resourceId, resourcePackage, requestIdPrefix, pollIntervalMs, nanoClock, sleeper, maxPolls); }
    }

    private static String string(JsonObject j, String key) {
        return j != null && j.has(key) && j.get(key).isJsonPrimitive() && j.getAsJsonPrimitive(key).isString() ? j.get(key).getAsString() : null;
    }
    private static boolean boolValue(JsonObject j, String key) {
        return j != null && j.has(key) && j.get(key).isJsonPrimitive() && j.getAsJsonPrimitive(key).isBoolean();
    }
    private static boolean trueBool(JsonObject j, String key) { return boolValue(j, key) && j.getAsJsonPrimitive(key).getAsBoolean(); }

    private boolean nodeMatches(JsonObject node, boolean matchedFlagAuthoritative) throws MalformedTree {
        if (resourceId == null) return true;
        if (matchedFlagAuthoritative) {
            // viewserver contract: every node must carry the authoritative matched boolean
            if (!boolValue(node, "matched")) throw new MalformedTree("matched flag");
            return trueBool(node, "matched");
        }
        // accessibility contract: the server selects every node and guarantees resource identity
        if (!resourceId.equals(string(node, "resource_id"))) return false;
        if (resourcePackage != null && !resourcePackage.equals(string(node, "package"))) return false;
        return true;
    }

    private static final class MalformedTree extends Exception { MalformedTree(String message) { super(message); } }

    /** Counts selector matches on the verified primary (left) screen only; rejects malformed, unverifiable
     *  or selector-inconsistent shapes instead of guessing (fail closed). */
    private long countMatches(JsonObject result) throws MalformedTree {
        JsonElement screensElement = result.get("screens");
        if (screensElement == null || !screensElement.isJsonArray() || screensElement.getAsJsonArray().isEmpty())
            throw new MalformedTree("screens");
        long count = 0;
        boolean verifiedPrimary = false;
        Set<String> screenNames = new HashSet<>();
        for (JsonElement element : screensElement.getAsJsonArray()) {
            if (!element.isJsonObject()) throw new MalformedTree("screen");
            JsonObject screen = element.getAsJsonObject();
            String name = string(screen, "screen");
            if (name == null || !(name.equals("primary") || name.equals("secondary")) || !screenNames.add(name))
                throw new MalformedTree("screen name");
            String status = string(screen, "status");
            if ("unsupported".equals(status) || "unavailable".equals(status)) continue; // mixed reply: the server only marks the whole observation incomplete
            if (status == null || !(status.equals("ok") || status.equals("partial"))) throw new MalformedTree("screen status");
            JsonElement nodesElement = screen.get("nodes");
            if (nodesElement == null || !nodesElement.isJsonArray() || nodesElement.getAsJsonArray().size() > 12000) throw new MalformedTree("nodes");
            boolean contextSelection = "resource_id_with_tree_context".equals(string(screen, "selection"));
            long matchedCount = -1;
            if (resourceId != null) {
                // the server always reports selection and a bounded matched_count for a selected read
                if (!contextSelection && !"resource_id".equals(string(screen, "selection"))) throw new MalformedTree("selection");
                JsonElement matchedCountElement = screen.get("matched_count");
                if (matchedCountElement == null || !matchedCountElement.isJsonPrimitive() || !matchedCountElement.getAsJsonPrimitive().isNumber())
                    throw new MalformedTree("matched_count");
                matchedCount = matchedCountElement.getAsJsonPrimitive().getAsNumber().longValue();
                if (matchedCount < 0 || matchedCount > 64) throw new MalformedTree("matched_count");
            }
            long screenCount = 0;
            for (JsonElement nodeElement : nodesElement.getAsJsonArray()) {
                if (!nodeElement.isJsonObject()) throw new MalformedTree("node");
                if (nodeMatches(nodeElement.getAsJsonObject(), contextSelection)) ++screenCount;
            }
            // crossed opt-in check: a selected screen must agree with its own matched_count
            if (resourceId != null && screenCount != matchedCount) throw new MalformedTree("matched count mismatch");
            if (name.equals("primary")) { verifiedPrimary = true; count += screenCount; }
        }
        if (!verifiedPrimary) throw new MalformedTree("verified primary screen missing");
        return count;
    }

    /**
     * Waits until {@code wanted} and the presence of the selector agree, or until the
     * deadline elapses. {@code wanted=false} therefore waits for verified absence.
     */
    public Result until(boolean wanted, Duration deadline) throws InterruptedException {
        if (deadline.isNegative() || deadline.isZero()) throw new IllegalArgumentException("deadline");
        final long startNanos = nanoClock.getAsLong();
        final long stopNanos = startNanos + deadline.toNanos();
        long polls = 0;
        String lastCause = "condition never observed";
        for (;;) {
            ++polls;
            long remainingNanos = stopNanos - nanoClock.getAsLong();
            if (remainingNanos <= 0) return new Result(Status.TIMEOUT, polls, null, lastCause + "; polls=" + polls);
            int callBudgetMs = (int) Math.min(30000L, remainingNanos / 1_000_000L);
            // a single call never outlives the deadline: with less than 1 ms left the wait ends instead
            if (callBudgetMs < 1) return new Result(Status.TIMEOUT, polls, null, lastCause + "; polls=" + polls);
            VoyemuClient.Reply reply = source.read(requestIdPrefix + "-" + polls, callBudgetMs);
            if (reply == null) return new Result(Status.ERROR, polls, null, "source produced no reply");
            if (reply.state() != VoyemuClient.State.RESPONSE || reply.response() == null)
                return new Result(Status.ERROR, polls, null, "uncorrelated client state: " + reply.diagnostic());
            JsonObject envelope = reply.response();
            if (!trueBool(envelope, "ok")) {
                JsonElement errorElement = envelope.get("error");
                if (errorElement == null || !errorElement.isJsonObject()) return new Result(Status.ERROR, polls, null, "malformed error envelope");
                JsonObject error = errorElement.getAsJsonObject();
                String code = string(error, "code"); String message = string(error, "message"); String outcome = string(error, "outcome");
                if (code == null || message == null || outcome == null || !boolValue(error, "retryable"))
                    return new Result(Status.ERROR, polls, null, "malformed error envelope");
                boolean retryable = trueBool(error, "retryable");
                lastCause = code + ": " + message;
                if (code.equals("TEST_UNSUPPORTED")) {
                    if (retryable || !"not_started".equals(outcome)) return new Result(Status.ERROR, polls, null, "inconsistent unsupported error envelope: " + lastCause);
                    return new Result(Status.UNSUPPORTED, polls, null, lastCause);
                }
                // contract: retryable unknown-outcome states (TEST_BUSY, TREE_TIMEOUT, TREE_TRANSPORT)
                // are pending and must be waited out; every other shape is terminal
                if (!(retryable && "unknown".equals(outcome)))
                    return new Result(Status.ERROR, polls, null, "inconsistent error envelope: " + lastCause);
                // pending: fall through to the deadline check
            } else {
                JsonElement resultElement = envelope.get("result");
                if (resultElement == null || !resultElement.isJsonObject()) return new Result(Status.ERROR, polls, null, "missing result");
                JsonObject wire = resultElement.getAsJsonObject();
                if (!"voyemu.session.ui.v1".equals(string(wire, "format"))) return new Result(Status.UNSUPPORTED, polls, wire, "unreadable result format");
                if (!session.id().equals(string(wire, "session_id")) || !profile.equals(string(wire, "profile")))
                    return new Result(Status.UNSUPPORTED, polls, wire, "result does not identify this session; refusing foreign observations");
                JsonObject result = null;
                try {
                    result = VoyemuClient.readResult(reply.attempt(), wire);
                } catch (SessionUi.Unavailable unavailable) {
                    lastCause = unavailable.getMessage();
                    if (unavailable.code().equals("TEST_UNSUPPORTED")) return new Result(Status.UNSUPPORTED, polls, wire, lastCause);
                    if (!unavailable.retryable()) return new Result(Status.ERROR, polls, wire, lastCause);
                } catch (IOException malformed) {
                    return new Result(Status.ERROR, polls, wire, "malformed tree result: " + malformed.getMessage());
                }
                if (result != null) {
                    long matches;
                    try { matches = countMatches(result); }
                    catch (MalformedTree malformed) { return new Result(Status.ERROR, polls, result, "malformed tree result: " + malformed.getMessage()); }
                    if (resourceId != null && matches > 64) return new Result(Status.ERROR, polls, result, "selector match budget exceeded");
                    if (resourceId != null && matches > 1) return new Result(Status.AMBIGUOUS, polls, result, "selector matched " + matches + " nodes on the primary screen");
                    boolean present = matches > 0;
                    boolean complete = trueBool(result.getAsJsonArray("screens").get(0).getAsJsonObject(), "complete");
                    if (present == wanted) {
                        // presence on a verified observation always proves the condition;
                        // absence is only accepted when the server confirmed the empty sample complete
                        if (present || complete) return new Result(Status.REACHED, polls, result, null);
                        lastCause = "empty observation incomplete: absence cannot be verified yet";
                    } else if (!present && !complete) {
                        lastCause = "empty observation incomplete: absence cannot be verified yet";
                    } else {
                        lastCause = wanted ? "selected element not present in a verified observation" : "selected element still present in a verified observation";
                    }
                }
            }
            if (polls >= maxPolls) return new Result(Status.TIMEOUT, polls, null, lastCause + "; polls=" + polls);
            long beforeSleepNanos = nanoClock.getAsLong();
            long remainingMs = (stopNanos - beforeSleepNanos) / 1_000_000L;
            if (remainingMs <= 0) return new Result(Status.TIMEOUT, polls, null, lastCause + "; polls=" + polls);
            long sleepMs = Math.min(pollIntervalMs, remainingMs);
            sleeper.sleep(Math.max(1L, sleepMs));
            // the injected/real clock advances during sleep; a next-poll whose deadline has
            // passed is caught by the remainingNanos check at the top of the loop
        }
    }

    public Result present(Duration deadline) throws InterruptedException { return until(true, deadline); }
    public Result absent(Duration deadline) throws InterruptedException { return until(false, deadline); }
}
