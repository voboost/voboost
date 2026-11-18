package ru.voboost.voyemu;

import com.google.gson.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the bounded session.ui wait. Realistic trees are derived from the
 * Voyemu api.cpp contract; the fake transport is the unit seam, but it applies the
 * same response envelope checks as VoyemuClientTest against a stub, so these tests
 * never assert runtime guest behaviour.
 */
public final class UiTreeWaitTest {
    static void check(boolean b) { if (!b) throw new AssertionError(); }
    static void rejects(Runnable r) { try { r.run(); throw new AssertionError(); } catch (IllegalArgumentException expected) {} }

    private static final VoyemuClient.Session SESSION = new VoyemuClient.Session("a".repeat(32), "b".repeat(32));
    private static final VoyemuClient.Session FOREIGN = new VoyemuClient.Session("c".repeat(32), "d".repeat(32));
    private static final VoyemuClient CLIENT = new VoyemuClient((json, t) -> { throw new AssertionError("direct transport use not covered by this test"); });
    private static String operation() { return "f".repeat(32); }

    /** Envelope for a successful read that also passed VoyemuClient's correlation checks. */
    private static String ok(String requestId, JsonObject result) {
        return "{\"schema\":1,\"request_id\":\"" + requestId + "\",\"operation_id\":\"" + operation()
            + "\",\"ok\":true,\"elapsed_ms\":1,\"result\":" + result.toString() + "}";
    }
    private static String error(String requestId, String code, String message, boolean retryable, String outcome) { return errorEnvelope(requestId, code, message, retryable, outcome); }
    private static String errorEnvelope(String requestId, String code, String message, boolean retryable, String outcome) {
        return "{\"schema\":1,\"request_id\":\"" + requestId + "\",\"operation_id\":\"" + operation()
            + "\",\"ok\":false,\"elapsed_ms\":3,\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message
            + "\",\"retryable\":" + retryable + ",\"outcome\":\"" + outcome + "\"}}";
    }

    /** Minimally shaped voyemu.ui.v1 result mirroring the api.cpp contract: surfaces gain
     *  screen/matched_count/selection as the server emits them for a selected primary read. */
    private static JsonObject tree(String sessionId, String resourceId, String... nodeResources) {
        JsonObject nodeless = screen(nodeResources);
        JsonObject result = new JsonObject();
        result.addProperty("format", "voyemu.ui.v1");
        result.addProperty("profile", "p");
        result.addProperty("session_id", sessionId);
        result.addProperty("complete", true);
        JsonArray screens = new JsonArray(); screens.add(nodeless);
        result.add("screens", screens);
        return result;
    }
    private static JsonObject screen(String... nodeResources) {
        JsonObject s = new JsonObject();
        s.addProperty("screen", "primary");
        s.addProperty("status", "ok");
        s.addProperty("selection", "resource_id");
        s.addProperty("matched_count", nodeResources.length);
        JsonArray nodes = new JsonArray();
        for (String resource : nodeResources) { JsonObject n = new JsonObject(); n.addProperty("resource_id", resource); n.addProperty("package", "example"); nodes.add(n); }
        s.add("nodes", nodes);
        return s;
    }
    private static JsonObject screenSelection(String selection, String... nodeResources) {
        JsonObject s = screen(nodeResources); s.addProperty("selection", selection); return s;
    }

    /** Fake Session-bound transport: interprets VoyemuClient attempts for session.ui. */
    private static final class StubTransport implements VoyemuClient.Transport {
        interface Handler { String handle(JsonObject request); }
        final Handler handler;
        final AtomicInteger calls = new AtomicInteger();
        StubTransport(Handler handler) { this.handler = handler; }
        @Override public VoyemuClient.ProcessResult execute(String request, Duration timeout) {
            calls.incrementAndGet();
            JsonObject parsed = JsonParser.parseString(request).getAsJsonObject();
            String response = handler.handle(parsed);
            JsonObject envelope = JsonParser.parseString(response).getAsJsonObject();
            if (envelope.has("result") && "voyemu.ui.v1".equals(envelope.getAsJsonObject("result").get("format").getAsString())) {
                envelope.add("result", SessionUiTestFrames.response(envelope.getAsJsonObject("result"), List.of("primary", "secondary")));
                response = envelope.toString();
            }
            // The real CLI exits 3 (TEST_UNSUPPORTED) or 4 (other errors) for error envelopes; the client requires 3/4.
            return new VoyemuClient.ProcessResult(response.contains("\"ok\":false")
                ? (response.contains("TEST_UNSUPPORTED") ? 3 : 4) : 0, false, response, "");
        }
    }

    /** A wait source built on the real client attempts for session.ui. */
    private static UiTreeWait.Builder waitOn(StubTransport transport) {
        VoyemuClient client = new VoyemuClient(transport);
        AtomicLong clock = new AtomicLong(0);
        AtomicInteger sleeps = new AtomicInteger();
        return UiTreeWait.on((requestId, timeoutMs) -> {
                VoyemuClient.Attempt a = client.multimediaUi(requestId, "p", SESSION, List.of("primary", "secondary"), null, "example:id/panel", "example", timeoutMs);
                try { return client.execute(a, Duration.ofMillis(timeoutMs)); } catch (InterruptedException e) { throw new RuntimeException(e); }
            }, "p", SESSION)
            .requestIdPrefix("wait")
            .selector("example:id/panel", "example")
            .pollInterval(Duration.ofMillis(200))
            .timing(() -> clock.addAndGet(1_000_000L) /* each observed step costs ~1ms of monotonic time */, millis -> { sleeps.incrementAndGet(); clock.addAndGet(millis * 1_000_000L); });
    }

    @Test
    void leftScreenOnlyPresenceIsNotAmbiguousAcrossScreens() throws Exception {
        // F5: only the primary surface is counted; an identical secondary hit must not create ambiguity
        StubTransport transport = new StubTransport(request -> {
            JsonObject primary = screen("example:id/panel");
            JsonObject secondary = new JsonObject();
            secondary.addProperty("screen", "secondary"); secondary.addProperty("status", "ok");
            secondary.addProperty("selection", "resource_id"); secondary.addProperty("matched_count", 1);
            JsonArray secondaryNodes = new JsonArray(); secondaryNodes.add(node("example:id/panel"));
            secondary.add("nodes", secondaryNodes);
            JsonObject result = new JsonObject(); result.addProperty("format", "voyemu.ui.v1"); result.addProperty("profile", "p");
            result.addProperty("session_id", SESSION.id()); result.addProperty("complete", true);
            JsonArray screens = new JsonArray(); screens.add(primary); screens.add(secondary); result.add("screens", screens);
            return ok(request.get("request_id").getAsString(), result);
        });
        UiTreeWait.Result result = waitOn(transport).build().present(Duration.ofSeconds(1));
        check(result.status() == UiTreeWait.Status.REACHED);
    }

    private static JsonObject node(String resource) { JsonObject n = new JsonObject(); n.addProperty("resource_id", resource); n.addProperty("package", "example"); return n; }

    @Test
    void reachesWhenNewTreeMatches() throws Exception {
        StubTransport transport = new StubTransport(request -> {
            int index = Integer.parseInt(request.get("request_id").getAsString().split("-")[1]);
            return ok(request.get("request_id").getAsString(), index < 3 ? tree(SESSION.id(), null) : tree(SESSION.id(), null, "example:id/panel"));
        });
        UiTreeWait.Result result = waitOn(transport).build().present(Duration.ofSeconds(2));
        check(result.status() == UiTreeWait.Status.REACHED);
        check(result.polls() == 3);
        check(transport.calls.get() == 3);
        check(result.lastResult() != null && result.lastResult().get("format").getAsString().equals("voyemu.ui.v1"));
    }

    @Test
    void timesOutWhenConditionNeverHolds() throws Exception {
        StubTransport transport = new StubTransport(request -> ok(request.get("request_id").getAsString(), tree(SESSION.id(), null)));
        UiTreeWait.Result result = waitOn(transport).pollInterval(Duration.ofMillis(5)).build().present(Duration.ofMillis(50));
        check(result.status() == UiTreeWait.Status.TIMEOUT);
        check(result.polls() > 1 && result.polls() < 100); // repeated polling, no busy loop
        // at most one transport call per poll cycle: the final cycle may end on the
        // no-budget-remaining deadline check without polling the source again
        check(transport.calls.get() >= (int) result.polls() - 1 && transport.calls.get() <= (int) result.polls());
        check(result.lastResult() == null);
        check(result.diagnostic() != null && result.diagnostic().contains("poll"));
    }

    @Test
    void reachesAbsence() throws Exception {
        StubTransport transport = new StubTransport(request -> {
            int index = Integer.parseInt(request.get("request_id").getAsString().split("-")[1]);
            return ok(request.get("request_id").getAsString(), index < 2 ? tree(SESSION.id(), null, "example:id/panel") : tree(SESSION.id(), null));
        });
        UiTreeWait.Result result = waitOn(transport).build().absent(Duration.ofSeconds(2));
        check(result.status() == UiTreeWait.Status.REACHED && result.polls() == 2);
    }

    @Test
    void ambiguousWhenTwoMatches() throws Exception {
        // realistic ui_tree.test.cpp shape: resource_id selection, server-crossed matched_count, no per-node flags
        StubTransport transport = new StubTransport(request -> {
            JsonObject s = screen("example:id/panel", "example:id/panel");
            JsonObject result = new JsonObject(); result.addProperty("format", "voyemu.ui.v1"); result.addProperty("profile", "p");
            result.addProperty("session_id", SESSION.id()); result.addProperty("complete", true);
            JsonArray screens = new JsonArray(); screens.add(s); result.add("screens", screens);
            return ok(request.get("request_id").getAsString(), result);
        });
        UiTreeWait.Result result = waitOn(transport).build().present(Duration.ofSeconds(1));
        check(result.status() == UiTreeWait.Status.AMBIGUOUS);
        check(result.diagnostic().contains("matched 2"));
    }

    @Test
    void resource_idModeCountsAllSelectedNodesWithoutMatchedFlags() throws Exception {
        // F1 regression: the real accessibility surface never emits a per-node matched flag in
        // resource_id mode; the element is selected, so presence must reach and absence must not confirm
        StubTransport transport = new StubTransport(request -> {
            JsonObject s = new JsonObject(); s.addProperty("screen", "primary"); s.addProperty("status", "ok");
            s.addProperty("selection", "resource_id"); s.addProperty("matched_count", 1);
            JsonArray nodes = new JsonArray(); nodes.add(node("example:id/panel")); s.add("nodes", nodes);
            JsonObject result = new JsonObject(); result.addProperty("format", "voyemu.ui.v1"); result.addProperty("profile", "p");
            result.addProperty("session_id", SESSION.id()); result.addProperty("complete", true);
            JsonArray screens = new JsonArray(); screens.add(s); result.add("screens", screens);
            return ok(request.get("request_id").getAsString(), result);
        });
        UiTreeWait.Builder wait = waitOn(transport);
        check(wait.build().present(Duration.ofSeconds(1)).status() == UiTreeWait.Status.REACHED);
        // the absent() direction must fail closed: the element is provably there
        UiTreeWait.Result absent = wait.build().absent(Duration.ofSeconds(1));
        check(absent.status() == UiTreeWait.Status.TIMEOUT);
        check(absent.diagnostic().contains("still present"));
    }

    @Test
    void loadedAnswerCountMustAgreeWithNodes() throws Exception {
        // fail closed: a server answer whose matched_count disagrees with the nodes is malformed
        StubTransport transport = new StubTransport(request -> {
            JsonObject s = screen("example:id/panel");
            s.addProperty("matched_count", 2); // inconsistent with one selected node
            JsonObject result = new JsonObject(); result.addProperty("format", "voyemu.ui.v1"); result.addProperty("profile", "p");
            result.addProperty("session_id", SESSION.id()); result.addProperty("complete", true);
            JsonArray screens = new JsonArray(); screens.add(s); result.add("screens", screens);
            return ok(request.get("request_id").getAsString(), result);
        });
        UiTreeWait.Result result = waitOn(transport).build().present(Duration.ofSeconds(1));
        check(result.status() == UiTreeWait.Status.ERROR);
        check(result.diagnostic().contains("malformed tree"));
    }

    @Test
    void rejectsForeignSessionIdentity() throws Exception {
        StubTransport transport = new StubTransport(request -> ok(request.get("request_id").getAsString(), tree(FOREIGN.id(), null, "example:id/panel")));
        UiTreeWait.Result result = waitOn(transport).build().present(Duration.ofSeconds(1));
        check(result.status() == UiTreeWait.Status.UNSUPPORTED);
        check(result.diagnostic().contains("session"));
    }

    @Test
    void pendingIsRetriedAndNotFailed() throws Exception {
        StubTransport transport = new StubTransport(request -> {
            int index = Integer.parseInt(request.get("request_id").getAsString().split("-")[1]);
            String id = request.get("request_id").getAsString();
            return index == 1 ? errorEnvelope(id, "TREE_TIMEOUT", "tree read not finished", true, "unknown")
                              : ok(id, tree(SESSION.id(), null, "example:id/panel"));
        });
        UiTreeWait.Result result = waitOn(transport).build().present(Duration.ofSeconds(2));
        check(result.status() == UiTreeWait.Status.REACHED && result.polls() == 2);
    }

    @Test
    void inconsistentPendingEnvelopeIsAnError() throws Exception {
        StubTransport transport = new StubTransport(request -> errorEnvelope(request.get("request_id").getAsString(), "TREE_TIMEOUT", "tree read not finished", false, "not_started"));
        UiTreeWait.Result result = waitOn(transport).build().present(Duration.ofSeconds(1));
        check(result.status() == UiTreeWait.Status.ERROR);
    }

    @Test
    void explicitUnsupportedIsTerminal() throws Exception {
        StubTransport transport = new StubTransport(request -> errorEnvelope(request.get("request_id").getAsString(), "TEST_UNSUPPORTED", "running native session predates UI observer support", false, "not_started"));
        UiTreeWait.Result result = waitOn(transport).build().present(Duration.ofSeconds(5));
        check(result.status() == UiTreeWait.Status.UNSUPPORTED);
        check(transport.calls.get() == 1); // no infinite retries
    }

    @Test
    void unknownOutcomeErrorIsTerminal() throws Exception {
        StubTransport transport = new StubTransport(request -> errorEnvelope(request.get("request_id").getAsString(), "TREE_PROTOCOL", "bounded node arrays required", false, "not_started"));
        UiTreeWait.Result result = waitOn(transport).build().present(Duration.ofSeconds(5));
        check(result.status() == UiTreeWait.Status.ERROR);
        check(transport.calls.get() == 1);
    }

    @Test
    void uncorrelatedClientStateIsAnError() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        VoyemuClient broken = new VoyemuClient((json, t) -> { calls.incrementAndGet(); return new VoyemuClient.ProcessResult(0, false, "{\"schema\":2}", ""); });
        VoyemuClient.Attempt attempt = broken.multimediaUi("probe", "p", SESSION, List.of("primary"), null, null, null, 1000);
        UiTreeWait wait = UiTreeWait.on((requestId, timeoutMs) -> {
                VoyemuClient.Attempt a = broken.multimediaUi(requestId, "p", SESSION, List.of("primary"), null, null, null, timeoutMs);
                try { return broken.execute(a, Duration.ofMillis(timeoutMs)); } catch (InterruptedException e) { throw new RuntimeException(e); }
            }, "p", SESSION).requestIdPrefix("wait").build();
        UiTreeWait.Result result = wait.present(Duration.ofSeconds(1));
        check(result.status() == UiTreeWait.Status.ERROR);
        check(calls.get() == 1);
    }

    @Test
    void malformedTreeIsAnError() throws Exception {
        JsonObject partial = new JsonObject();
        partial.addProperty("format", "voyemu.ui.v1"); partial.addProperty("profile", "p");
        partial.addProperty("session_id", SESSION.id()); partial.addProperty("complete", true);
        partial.add("screens", new JsonArray()); // empty screens array is malformed
        StubTransport transport = new StubTransport(request -> ok(request.get("request_id").getAsString(), partial));
        UiTreeWait.Result result = waitOn(transport).build().present(Duration.ofSeconds(1));
        check(result.status() == UiTreeWait.Status.ERROR);
    }

    @Test
    void treeChangeTriggersReaches() throws Exception {
        // The condition flips because the screen inventory changes between polls.
        StubTransport transport = new StubTransport(request -> {
            int index = Integer.parseInt(request.get("request_id").getAsString().split("-")[1]);
            return ok(request.get("request_id").getAsString(), index < 4 ? tree(SESSION.id(), null) : tree(SESSION.id(), null, "example:id/panel"));
        });
        UiTreeWait.Result result = waitOn(transport).build().present(Duration.ofSeconds(2));
        check(result.status() == UiTreeWait.Status.REACHED && result.polls() == 4);
    }

    @Test
    void mismatchedCountIsMalformed() throws Exception {
        // fail closed: a selected screen whose own crossed matched_count disagrees is rejected
        StubTransport transport = new StubTransport(request -> {
            JsonObject s = screen("example:id/panel", "example:id/panel");
            s.addProperty("matched_count", 1); // inconsistent with two selected nodes on the server
            JsonObject result = new JsonObject(); result.addProperty("format", "voyemu.ui.v1"); result.addProperty("profile", "p");
            result.addProperty("session_id", SESSION.id()); result.addProperty("complete", true);
            JsonArray screens = new JsonArray(); screens.add(s); result.add("screens", screens);
            return ok(request.get("request_id").getAsString(), result);
        });
        UiTreeWait.Result result = waitOn(transport).build().present(Duration.ofSeconds(1));
        check(result.status() == UiTreeWait.Status.ERROR);
        check(result.diagnostic().contains("malformed tree result"));
    }

    @Test
    void unsupportedSecondaryDoesNotSpoilValidPrimary() throws Exception {
        // F2 regression: the exact ui_tree.test.cpp API28 payload — primary ok, secondary unsupported
        StubTransport transport = new StubTransport(request -> {
            JsonObject primary = screen("example:id/panel");
            JsonObject secondary = new JsonObject();
            secondary.addProperty("screen", "secondary");
            secondary.addProperty("status", "unsupported");
            secondary.addProperty("reason", "api28_secondary_tree_not_verified");
            JsonObject result = new JsonObject(); result.addProperty("format", "voyemu.ui.v1"); result.addProperty("profile", "p");
            result.addProperty("session_id", SESSION.id());
            result.addProperty("complete", false); // unsupported keeps the whole observation incomplete
            JsonArray screens = new JsonArray(); screens.add(primary); screens.add(secondary); result.add("screens", screens);
            return ok(request.get("request_id").getAsString(), result);
        });
        UiTreeWait.Result present = waitOn(transport).build().present(Duration.ofSeconds(1));
        check(present.status() == UiTreeWait.Status.REACHED); // presence on a verified primary screen
        // absence is not confirmable: the observation is provably incomplete (all_complete=false per server)
        UiTreeWait.Result absent = waitOn(transport).build().absent(Duration.ofSeconds(200));
        check(absent.status() == UiTreeWait.Status.TIMEOUT);
    }

    @Test
    void allScreensUnsupportedIsUnsupported() throws Exception {
        // fail closed: nothing was verified, the observation cannot support any claim
        StubTransport transport = new StubTransport(request -> {
            JsonObject primary = new JsonObject();
            primary.addProperty("screen", "primary"); primary.addProperty("status", "unsupported");
            JsonObject result = new JsonObject(); result.addProperty("format", "voyemu.ui.v1"); result.addProperty("profile", "p");
            result.addProperty("session_id", SESSION.id()); result.addProperty("complete", false);
            JsonArray screens = new JsonArray(); screens.add(primary); result.add("screens", screens);
            return ok(request.get("request_id").getAsString(), result);
        });
        UiTreeWait.Result result = waitOn(transport).build().present(Duration.ofSeconds(1));
        check(result.status() == UiTreeWait.Status.UNSUPPORTED);
        check(result.diagnostic().contains("TEST_UNSUPPORTED"));
    }

    @Test
    void testBusyIsPendingNotTerminal() throws Exception {
        // F3 regression: retryable=true, outcome=unknown is the server's contract pending state
        // and must be waited out until the caller's deadline, never treated as a terminal failure
        StubTransport alwaysBusy = new StubTransport(request -> errorEnvelope(request.get("request_id").getAsString(), "TEST_BUSY", "guest transaction in progress", true, "unknown"));
        UiTreeWait.Result result = waitOn(alwaysBusy).build().present(Duration.ofSeconds(2));
        check(result.status() == UiTreeWait.Status.TIMEOUT); // waited out, not terminal ERROR
        check(result.polls() > 1);
        check(result.diagnostic().contains("TEST_BUSY"));
    }

    @Test
    void retryableUnknownIsPendingForAnyCode() throws Exception {
        // contract rule: pending ⇔ retryable==true && outcome==unknown
        StubTransport transport = new StubTransport(request -> errorEnvelope(request.get("request_id").getAsString(), "CAN_TIMEOUT", "wait state", true, "unknown"));
        UiTreeWait.Result result = waitOn(transport).build().present(Duration.ofMillis(200));
        check(result.status() == UiTreeWait.Status.TIMEOUT);
    }

    @Test
    void contextSelectionMatchedFlagIsAuthoritative() throws Exception {
        JsonObject s = screenSelection("resource_id_with_tree_context", "example:id/other");
        s.get("nodes").getAsJsonArray().get(0).getAsJsonObject().addProperty("matched", false);
        JsonObject n = node("example:id/panel"); n.addProperty("matched", true);
        s.get("nodes").getAsJsonArray().add(n);
        s.addProperty("matched_count", 1);
        JsonObject result = new JsonObject(); result.addProperty("format", "voyemu.ui.v1"); result.addProperty("profile", "p");
        result.addProperty("session_id", SESSION.id()); result.addProperty("complete", true);
        JsonArray screens = new JsonArray(); screens.add(s); result.add("screens", screens);
        // the raw array contains two same-package nodes; only the flagged one counts
        StubTransport transport = new StubTransport(request -> ok(request.get("request_id").getAsString(), result));
        UiTreeWait.Result reached = waitOn(transport).build().present(Duration.ofSeconds(1));
        check(reached.status() == UiTreeWait.Status.REACHED);
    }

    @Test
    void contextSelectionMissingMatchedFlagIsMalformed() throws Exception {
        // real accessibility-mode payload (no per-node matched) mislabelled as context selection
        // is rejected: context-selection nodes mandatorily carry the matched boolean (ObserverMain.java:542)
        JsonObject s = screenSelection("resource_id_with_tree_context", "example:id/panel");
        s.addProperty("matched_count", 1);
        JsonObject result = new JsonObject(); result.addProperty("format", "voyemu.ui.v1"); result.addProperty("profile", "p");
        result.addProperty("session_id", SESSION.id()); result.addProperty("complete", true);
        JsonArray screens = new JsonArray(); screens.add(s); result.add("screens", screens);
        StubTransport transport = new StubTransport(request -> ok(request.get("request_id").getAsString(), result));
        UiTreeWait.Result malformed = waitOn(transport).build().present(Duration.ofSeconds(1));
        check(malformed.status() == UiTreeWait.Status.ERROR);
        check(malformed.diagnostic().contains("matched flag"));
    }

    @Test
    void deadlineBoundPerCallBudget() throws Exception {
        List<Integer> budgets = Collections.synchronizedList(new ArrayList<>());
        StubTransport transport = new StubTransport(request -> {
            budgets.add(request.get("timeout_ms").getAsInt());
            return ok(request.get("request_id").getAsString(), tree(SESSION.id(), null));
        });
        UiTreeWait.Result result = waitOn(transport).build().present(Duration.ofMillis(500));
        check(result.status() == UiTreeWait.Status.TIMEOUT);
        for (int budget : budgets) check(budget <= 500 && budget >= 1);
    }

    @Test
    void lastCallNeverExceedsRemainingDeadline() throws Exception {
        // F4 regression: with ~60 ms left the budget must be 60, not the old 75 ms floor
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        AtomicLong clock = new AtomicLong();
        AtomicInteger sleeps = new AtomicInteger();
        StubTransport transport = new StubTransport(request -> {
            order.add("poll:" + request.get("timeout_ms").getAsInt());
            clock.addAndGet(260 * 1_000_000L); // each poll consumes 260 ms of monotonic time
            return ok(request.get("request_id").getAsString(), tree(SESSION.id(), null));
        });
        UiTreeWait.Result result = waitOn(transport)
            .timing(clock::get, m -> { sleeps.incrementAndGet(); clock.addAndGet(m * 1_000_000L); })
            .build().present(Duration.ofMillis(500));
        check(result.status() == UiTreeWait.Status.TIMEOUT);
        check(order.size() == 2); // second poll happens with ~40 ms left
        int first = Integer.parseInt(order.get(0).split(":")[1]);
        int second = Integer.parseInt(order.get(1).split(":")[1]);
        check(first == 500); // the whole deadline is available up front
        check(second <= 240 && second < 75); // never more than remaining; the old fixed floor returned 75
    }

    @Test
    void exhaustedDeadlineEndsWithoutAnotherPoll() throws Exception {
        // F4 regression: with only sub-millisecond time left, the wait must stop, not poll with ≥1 ms budget
        AtomicInteger polls = new AtomicInteger();
        List<Integer> budgets = Collections.synchronizedList(new ArrayList<>());
        AtomicLong clock = new AtomicLong();
        StubTransport transport = new StubTransport(request -> { polls.incrementAndGet(); budgets.add(request.get("timeout_ms").getAsInt()); clock.addAndGet(499 * 1_000_000L); return ok(request.get("request_id").getAsString(), tree(SESSION.id(), null)); });
        UiTreeWait.Result result = waitOn(transport)
            .timing(clock::get, m -> clock.addAndGet(Math.max(1, m) * 1_000_000L))
            .build().present(Duration.ofMillis(500));
        check(result.status() == UiTreeWait.Status.TIMEOUT);
        check(polls.get() == 1); // the deadline is gone; no second poll despite a non-ERROR answer
    }

    @Test
    void noBusyLoopWhenSourceAlwaysPending() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger sleeps = new AtomicInteger();
        AtomicLong clock = new AtomicLong();
        UiTreeWait stepping = UiTreeWait.on((requestId, timeoutMs) -> {
                attempts.incrementAndGet();
                clock.addAndGet(1_000_000L); // the read itself consumes time on the fake clock
                return new VoyemuClient.Reply(VoyemuClient.State.RESPONSE, null, 0,
                    JsonParser.parseString(errorEnvelope(requestId, "TREE_TIMEOUT", "still busy", true, "unknown")).getAsJsonObject(), "");
            }, "p", SESSION)
            .requestIdPrefix("wait")
            .pollInterval(Duration.ofMillis(100))
            .timing(clock::get, millis -> { sleeps.incrementAndGet(); clock.addAndGet(millis * 1_000_000L); })
            .maxPolls(20000)
            .build();
        UiTreeWait.Result result = stepping.until(true, Duration.ofMillis(1000));
        check(result.status() == UiTreeWait.Status.TIMEOUT);
        // A busy loop would return thousands of polls inside the 1000 ms deadline;
        // with a 100 ms poll interval the loop must stay near 9 attempts and
        // every attempt except the last must be preceded by a sleep of that size.
        check(attempts.get() > 1 && attempts.get() <= 11);
        check(sleeps.get() == attempts.get() - 1 || sleeps.get() == attempts.get());
        check(clock.get() >= 1_000_000_000L); // the whole deadline really elapsed on the fake clock
        check(result.diagnostic() != null && result.diagnostic().contains("TREE_TIMEOUT"));
    }

    @Test
    void absentRequiresVerifiedEmptyObservation() throws Exception {
        AtomicInteger count = new AtomicInteger();
        StubTransport transport = new StubTransport(request -> {
            int index = count.incrementAndGet();
            JsonObject result = tree(SESSION.id(), null);
            if (index == 1) result.getAsJsonArray("screens").get(0).getAsJsonObject().addProperty("status", "partial");
            return ok(request.get("request_id").getAsString(), result);
        });
        UiTreeWait.Result result = waitOn(transport).build().absent(Duration.ofSeconds(1));
        check(result.status() == UiTreeWait.Status.REACHED && result.polls() == 2);
    }

    @Test
    void argumentValidation() {
        rejects(() -> UiTreeWait.on((r, t) -> null, "p", SESSION).selector("nocolon").build());
        rejects(() -> UiTreeWait.on((r, t) -> null, "p", SESSION).pollInterval(Duration.ZERO).build());
        rejects(() -> UiTreeWait.on((r, t) -> null, "p", SESSION).maxPolls(0).build());
        try { UiTreeWait.on((r, t) -> null, "p", SESSION).build().present(Duration.ZERO); throw new AssertionError(); }
        catch (InterruptedException unexpected) { throw new AssertionError(unexpected); }
        catch (IllegalArgumentException expected) {}
    }

    @Test
    void uiReadAttemptIsReadOnlyAndWellFormed() {
        VoyemuClient client = new VoyemuClient((json, t) -> { throw new AssertionError(); });
        VoyemuClient.Attempt a = client.multimediaUi("read", "p", SESSION, List.of("primary"), "all_attached", "example:id/panel", "example.id", 5000);
        check(a.op().equals("session.ui"));
        JsonObject request = JsonParser.parseString(a.json()).getAsJsonObject();
        check(request.get("op").getAsString().equals("session.ui") && request.get("schema").getAsInt() == 1);
        check(request.get("session").getAsJsonObject().get("id").getAsString().equals(SESSION.id()));
        check(request.get("screens").getAsJsonArray().get(0).getAsString().equals("primary"));
        check(request.get("scope").getAsString().equals("all_attached"));
        JsonObject selector = request.get("selector").getAsJsonObject();
        check(selector.get("resource_id").getAsString().equals("example:id/panel"));
        check(selector.get("package").getAsString().equals("example.id"));
        check(request.get("timeout_ms").getAsInt() == 5000);
        rejects(() -> client.multimediaUi("read", "p", SESSION, List.of(), null, null, null, 5000));
        rejects(() -> client.multimediaUi("read", "p", SESSION, List.of("cluster"), null, null, null, 5000));
        rejects(() -> client.multimediaUi("read", "p", SESSION, List.of("primary", "primary"), null, null, null, 5000));
        rejects(() -> client.multimediaUi("read", "p", SESSION, List.of("primary"), "bogus", null, null, 5000));
        rejects(() -> client.multimediaUi("read", "p", SESSION, List.of("primary"), null, "bad", null, 5000));
        rejects(() -> client.multimediaUi("read", "p", SESSION, List.of("primary"), null, "example:id/name", "bad package", 5000));
        rejects(() -> client.multimediaUi("read", "p", SESSION, List.of("primary"), null, null, "example", 5000));
        rejects(() -> client.multimediaUi("read", "p", SESSION, List.of("primary"), null, null, null, 0));
        rejects(() -> client.multimediaUi("read", "p", SESSION, List.of("primary"), null, null, null, 30001));
    }

    @Test
    void clientAcceptsUiAttemptThroughReadonlyGate() throws Exception {
        StubTransport transport = new StubTransport(request -> ok(request.get("request_id").getAsString(), tree(SESSION.id(), null, "example:id/panel")));
        VoyemuClient client = new VoyemuClient(transport);
        VoyemuClient.Attempt a = client.multimediaUi("read", "p", SESSION, List.of("primary", "secondary"), null, "example:id/panel", null, 5000);
        VoyemuClient.Reply reply = client.execute(a, Duration.ofSeconds(5));
        check(reply.state() == VoyemuClient.State.RESPONSE);
        check(transport.calls.get() == 1);
        // a mutation attempt still cannot pass through; the read-only gate remains closed
        VoyemuClient.Attempt mutation = client.reboot("reboot", "p", SESSION, true, 1000);
        try { client.execute(mutation, Duration.ofSeconds(1)); throw new AssertionError(); }
        catch (IllegalStateException expected) {}
        check(transport.calls.get() == 1); // no additional transport call for the rejected mutation
    }
}
