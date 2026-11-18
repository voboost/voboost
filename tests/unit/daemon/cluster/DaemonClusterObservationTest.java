package ru.voboost.tests.agents;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

class DaemonClusterObservationTest {
    private static JsonObject catalog() throws IOException {
        return RunFiles.parse("{\"agents\":[{\"id\":\"font-cyrillic\",\"status\":\"stable\",\"activation\":{\"type\":\"checkbox\",\"default\":true},\"targets\":[{\"platform\":\"qnx\",\"process\":\"gauge\"}]}]}");
    }
    private static JsonObject input(int nativeRevision) throws IOException {
        JsonObject value = DaemonRuntimeObservationTest.observation();
        value.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("config").getAsJsonObject("data").getAsJsonObject("agents")
            .add("font-cyrillic", RunFiles.parse("{\"enabled\":true,\"options\":{}}"));
        runtime(value).add("cluster", RunFiles.parse("""
            {"schemaVersion":1,"domain":"cluster","snapshotRevision":10,"catalogSha256":"native-catalog","generationId":"native-generation",
             "desiredRevision":7,"desiredSha256":"native-desired","observationRevision":REVISION,"bootId":"native-boot","daemonEpoch":"native-epoch","deliveryState":"idle",
             "agents":[{"id":"font-cyrillic","targets":[{"id":"font-cyrillic","targetProcess":"gauge","state":"applied","reason":"actual-readback",
              "process":{"pid":42,"birthIdentity":"process-birth","executableSha256":"existing-native-attestation","endpointEpoch":"process-endpoint","lifecycleGeneration":3}}],
              "enabled":true,"options":{},"state":"applied","reason":"all-selected-targets-applied","process":null}]}
            """.replace("REVISION", Integer.toString(nativeRevision))));
        JsonObject nativeValue = cluster(value);
        nativeValue.addProperty("catalogSha256", "a".repeat(64)); nativeValue.addProperty("generationId", "b".repeat(64));
        nativeValue.addProperty("daemonEpoch", "c".repeat(64)); nativeValue.addProperty("desiredSha256", "d".repeat(64));
        process(value).addProperty("birthIdentity", "e".repeat(64)); process(value).addProperty("executableSha256", "f".repeat(64)); process(value).addProperty("endpointEpoch", "1".repeat(64));
        syncAggregate(value);
        return value;
    }
    private static void syncAggregate(JsonObject input) {
        var agent = cluster(input).getAsJsonArray("agents").get(0).getAsJsonObject(); var targets = agent.getAsJsonArray("targets");
        agent.add("process", targets.size() == 1 ? targets.get(0).getAsJsonObject().get("process").deepCopy() : com.google.gson.JsonNull.INSTANCE);
    }
    private static JsonObject runtime(JsonObject value) { return value.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("runtime").getAsJsonObject("data"); }
    private static JsonObject cluster(JsonObject value) { return runtime(value).getAsJsonObject("cluster"); }
    private static JsonObject process(JsonObject value) { return cluster(value).getAsJsonArray("agents").get(0).getAsJsonObject().getAsJsonArray("targets").get(0).getAsJsonObject().getAsJsonObject("process"); }
    private static RuntimeObservation current(JsonObject input) throws IOException { return RuntimeObservation.read(input, DaemonRuntimeObservationTest.IDENTITY); }
    private static JsonObject expected() throws IOException { return HiddenTargets.snapshot(catalog(), current(input(10)), Set.of("font-cyrillic")); }
    private static JsonObject pwInput(int revision) throws IOException {
        return RunFiles.parse(input(revision).toString().replace("font-cyrillic", "vehicle-drive-pedestrian-warning")
                .replace("gauge", "light"));
    }
    private static JsonObject pwCatalog() throws IOException {
        return RunFiles.parse(catalog().toString().replace("font-cyrillic", "vehicle-drive-pedestrian-warning")
                .replace("gauge", "light"));
    }
    private static void savedPedestrian(JsonObject value, Boolean armed) throws IOException {
        runtime(value).getAsJsonObject("publisher").addProperty("publishedAtBootMs", "100");
        JsonObject state=RunFiles.parse("{\"bootId\":\"boot\",\"observedAtBootMs\":\"101\",\"forcedEv\":{\"outcome\":\"missing\"},\"pedestrianWarning\":{\"outcome\":\"missing\"}}");
        if(armed!=null) { var flag=state.getAsJsonObject("pedestrianWarning");flag.addProperty("outcome","value");flag.addProperty("value",armed); }
        runtime(value).add("persistentVehicleState",state);
    }
    @Test void pedestrianStartupResolvesObserveFromBootBoundSavedChoice() throws Exception {
        var catalog=pwCatalog();
        catalog.getAsJsonArray("agents").get(0).getAsJsonObject().add("options",
                com.google.gson.JsonParser.parseString("[{\"key\":\"intent\",\"default\":\"observe\"}]"));
        for(Boolean armed:new Boolean[]{true,false,null}) {
            var value=pwInput(10);savedPedestrian(value,armed);
            var options=cluster(value).getAsJsonArray("agents").get(0).getAsJsonObject().getAsJsonObject("options");
            options.addProperty("intent",armed==null?"observe":armed?"on":"off");
            var current=current(value);var expected=AgentSettings.snapshot(catalog,current);
            assertNotNull(ClusterObservation.readPwStartup(current,catalog,expected));
            options.addProperty("intent","wrong");
            assertThrows(IOException.class,()->ClusterObservation.readPwStartup(current(value),catalog,expected));
            runtime(value).getAsJsonObject("persistentVehicleState").addProperty("bootId","foreign");
            assertThrows(IOException.class,()->ClusterObservation.readPwStartup(current(value),catalog,expected));
        }
    }
    private static void passThrough(JsonObject value) {
        var agent = cluster(value).getAsJsonArray("agents").get(0).getAsJsonObject();
        agent.addProperty("state", "waiting-ui");
        var target = agent.getAsJsonArray("targets").get(0).getAsJsonObject();
        target.addProperty("state", "waiting-ui");
        target.addProperty("reason", "PW visual pass-through: actual enabled");
    }
    @Test void pwSelectionWaitsForTheRequestedIntentOnTheSameLightProcess() throws Exception {
        var catalog = pwCatalog();
        catalog.getAsJsonArray("agents").get(0).getAsJsonObject().add("options",
                com.google.gson.JsonParser.parseString("[{\"key\":\"intent\",\"default\":\"observe\"}]"));
        var previous = pwInput(10);
        savedPedestrian(previous, null);
        cluster(previous).getAsJsonArray("agents").get(0).getAsJsonObject()
                .getAsJsonObject("options").addProperty("intent", "observe");
        var before = current(previous);
        var applied = ClusterObservation.readPwStartup(before, catalog, AgentSettings.snapshot(catalog, before));

        var requested = previous.deepCopy();
        requested.getAsJsonObject("data").getAsJsonObject("files").getAsJsonObject("config")
                .getAsJsonObject("data").getAsJsonObject("agents").getAsJsonObject("vehicle-drive-pedestrian-warning")
                .getAsJsonObject("options").addProperty("intent", "on");
        var expected = AgentSettings.snapshot(catalog, current(requested));
        assertThrows(ClusterObservation.Pending.class,
                () -> applied.advancePwSelection(current(requested), catalog, expected));
        cluster(requested).getAsJsonArray("agents").get(0).getAsJsonObject()
                .getAsJsonObject("options").addProperty("intent", "on");
        cluster(requested).addProperty("observationRevision", 11);
        assertThrows(IOException.class, () -> applied.advancePwSelection(current(requested), catalog, expected));
        cluster(requested).addProperty("desiredRevision", 8);
        assertEquals(1, applied.advancePwSelection(current(requested), catalog, expected).advances());
        process(requested).addProperty("pid", 43);
        syncAggregate(requested);
        assertThrows(IOException.class, () -> applied.advancePwSelection(current(requested), catalog, expected));
    }

    @Test void pwDeliveryDistinguishesTransientAndTerminalStates() throws Exception {
        var catalog = pwCatalog();
        var value = pwInput(10);
        var original = current(value);
        var expected = AgentSettings.snapshot(catalog, original);
        var applied = ClusterObservation.read(original, catalog, expected);
        for (String state : java.util.List.of("pending", "unavailable")) {
            cluster(value).addProperty("deliveryState", state);
            assertThrows(ClusterObservation.Pending.class, () -> applied.confirmPw(current(value), catalog, expected));
        }
        for (String state : java.util.List.of("failed", "setup-required", "unknown")) {
            cluster(value).addProperty("deliveryState", state);
            var failure = assertThrows(IOException.class, () -> applied.confirmPw(current(value), catalog, expected));
            assertFalse(failure instanceof ClusterObservation.Pending);
        }
        cluster(value).addProperty("deliveryState", "idle");
        var agent = cluster(value).getAsJsonArray("agents").get(0).getAsJsonObject();
        var target = agent.getAsJsonArray("targets").get(0).getAsJsonObject();
        for (String state : java.util.List.of("failed", "quarantined", "reload-required")) {
            agent.addProperty("state", state);
            target.addProperty("state", state);
            var failure = assertThrows(IOException.class, () -> applied.confirmPw(current(value), catalog, expected));
            assertFalse(failure instanceof ClusterObservation.Pending);
        }
    }

    @Test void pendingDeliveryFromAnotherAndroidOwnerStopsImmediately() throws Exception {
        var catalog = pwCatalog();
        var before = current(pwInput(10));
        var expected = AgentSettings.snapshot(catalog, before);
        var applied = ClusterObservation.read(before, catalog, expected);
        var pending = pwInput(11);
        var runtime = pending.getAsJsonObject("data").getAsJsonObject("files")
                .getAsJsonObject("runtime").getAsJsonObject("data");
        runtime.getAsJsonObject("publisher").addProperty("daemonPid", 72);
        cluster(pending).addProperty("deliveryState", "pending");
        var failure = assertThrows(IOException.class, () -> applied.confirmPw(current(pending), catalog, expected));
        assertEquals("native startup Android owner changed", failure.getMessage());
    }

    @Test void pwStartupRestartsItsProofWhenQnxReplacesTheRetainedReceipt() throws Exception {
        var catalog = pwCatalog();
        var initial = current(pwInput(10));
        var expected = AgentSettings.snapshot(catalog, initial);
        var previous = ClusterObservation.read(initial, catalog, expected)
                .advance(current(pwInput(11)), catalog, expected);
        var replacement = pwInput(12);
        cluster(replacement).addProperty("bootId", "new-native-boot");
        cluster(replacement).addProperty("daemonEpoch", "2".repeat(64));
        assertThrows(IOException.class, () -> previous.confirmPw(current(replacement), catalog, expected));
        var restarted = previous.advancePwStartup(current(replacement), catalog, expected);
        assertEquals(0, restarted.advances());
        cluster(replacement).addProperty("observationRevision", 13);
        var first = restarted.advancePwStartup(current(replacement), catalog, expected);
        cluster(replacement).addProperty("observationRevision", 14);
        first.advancePwStartup(current(replacement), catalog, expected)
                .requireCurrent(current(replacement), "vehicle-drive-pedestrian-warning", "light");
    }

    @Test void pwRestoreRejectsGenericOwnerTransitionAndUnrelatedUnknownReasons() throws Exception {
        var catalog=pwCatalog();var initial=current(pwInput(10));
        var expected=AgentSettings.snapshot(catalog,initial);var applied=ClusterObservation.read(initial,catalog,expected);
        for(String reason:java.util.List.of("PW visual pass-through: stale live observation","PW visual pass-through or owner transition")) {
            var unknown=pwInput(11);passThrough(unknown);
            cluster(unknown).getAsJsonArray("agents").get(0).getAsJsonObject().getAsJsonArray("targets").get(0).getAsJsonObject().addProperty("reason",reason);
            assertThrows(ClusterObservation.Pending.class,()->applied.advancePwRestore(current(unknown),catalog,expected));
        }
    }
    @Test void pwRestoreAllowsSuccessfulUnknownOnlyAfterAppliedAndRejectsInitialUnknown() throws Exception {
        var catalog=pwCatalog();var initial=current(pwInput(10));var expected=AgentSettings.snapshot(catalog,initial);
        var applied=ClusterObservation.read(initial,catalog,expected);
        var restore=pwInput(11);passThrough(restore);
        cluster(restore).getAsJsonArray("agents").get(0).getAsJsonObject().getAsJsonArray("targets").get(0).getAsJsonObject().addProperty("reason","PW visual pass-through: live state unknown");
        assertThrows(ClusterObservation.Pending.class,()->ClusterObservation.read(current(restore),catalog,expected));
        var first=applied.advancePwRestore(current(restore),catalog,expected);
        var second=restore.deepCopy();cluster(second).addProperty("observationRevision",12);
        first.advancePwRestore(current(second),catalog,expected).requireCurrent(current(second),"vehicle-drive-pedestrian-warning","light");
        assertThrows(ClusterObservation.Pending.class,()->first.advancePwRestore(current(restore),catalog,expected));
        for(String state:java.util.List.of("failed","reload-required")) {
            var failed=second.deepCopy();cluster(failed).getAsJsonArray("agents").get(0).getAsJsonObject().getAsJsonArray("targets").get(0).getAsJsonObject().addProperty("state",state);
            assertThrows(ClusterObservation.Pending.class,()->first.advancePwRestore(current(failed),catalog,expected));
        }
    }
    @Test void pwRestoreRequiresPriorAppliedAndSameProcessLifetime() throws Exception {
        var catalog = pwCatalog();
        var initial = current(pwInput(10));
        var expected = AgentSettings.snapshot(catalog, initial);
        var applied = ClusterObservation.read(initial, catalog, expected);
        assertThrows(ClusterObservation.Pending.class, () -> applied.advancePwRestore(current(pwInput(11)), catalog, expected));
        var second = pwInput(11); passThrough(second);
        assertThrows(ClusterObservation.Pending.class, () -> ClusterObservation.read(current(second), catalog, expected));
        var next = applied.advancePwRestore(current(second), catalog, expected);
        var third = pwInput(12); passThrough(third);
        var last = current(third);
        next.advancePwRestore(last, catalog, expected).requireCurrent(last, "vehicle-drive-pedestrian-warning", "light");
        var changed = pwInput(13); passThrough(changed);
        process(changed).addProperty("pid", 43); syncAggregate(changed);
        assertThrows(IOException.class, () -> next.advancePwRestore(current(changed), catalog, expected));
        var unavailable = pwInput(13); passThrough(unavailable);
        cluster(unavailable).getAsJsonArray("agents").get(0).getAsJsonObject().getAsJsonArray("targets")
                .get(0).getAsJsonObject().addProperty("reason", "PW visual node/type capability unavailable");
        assertThrows(ClusterObservation.Pending.class,
                () -> next.advancePwRestore(current(unavailable), catalog, expected));
    }

    @Test void pwDeliveryCanRequireStartupWithoutGrantingAppliedEvidence() throws Exception {
        var catalog = pwCatalog();
        var initial = current(pwInput(10));
        var expected = AgentSettings.snapshot(catalog, initial);
        assertFalse(ClusterObservation.pwNeedsStartup(initial, catalog, expected));

        var waiting = pwInput(11);
        var agent = cluster(waiting).getAsJsonArray("agents").get(0).getAsJsonObject();
        agent.addProperty("state", "waiting-process");
        var target = agent.getAsJsonArray("targets").get(0).getAsJsonObject();
        target.addProperty("state", "waiting-process");
        target.addProperty("reason", "startup-controller-unavailable");
        target.add("process", com.google.gson.JsonNull.INSTANCE);
        syncAggregate(waiting);
        assertTrue(ClusterObservation.pwNeedsStartup(current(waiting), catalog, expected));
        assertThrows(ClusterObservation.Pending.class, () -> ClusterObservation.read(current(waiting), catalog, expected));

        cluster(waiting).addProperty("deliveryState", "pending");
        assertThrows(ClusterObservation.Pending.class, () -> ClusterObservation.pwNeedsStartup(current(waiting), catalog, expected));
        cluster(waiting).addProperty("deliveryState", "idle");
        target.addProperty("reason", "awaiting-qnx-target-ack");
        assertThrows(ClusterObservation.Pending.class, () -> ClusterObservation.pwNeedsStartup(current(waiting), catalog, expected));
        target.addProperty("reason", "startup-controller-awaiting-revision");
        assertThrows(ClusterObservation.Pending.class, () -> ClusterObservation.pwNeedsStartup(current(waiting), catalog, expected));
    }

    @Test void unchangedAppliedPedestrianWarningNeedsNoSyntheticStartupRevision() throws Exception {
        var catalog = pwCatalog();
        var current = current(pwInput(10));
        var expected = AgentSettings.snapshot(catalog, current);
        var applied = ClusterObservation.readPwStartup(current, catalog, expected);
        assertEquals(0, applied.advances());
        applied.requireCurrentPw(current);
        var repeated = applied.confirmPw(current, catalog, expected);
        assertEquals(0, repeated.advances());
        repeated.requireCurrentPw(current);
        var foreign = pwInput(10);
        process(foreign).addProperty("pid", 43);
        syncAggregate(foreign);
        assertThrows(IOException.class, () -> repeated.confirmPw(current(foreign), catalog, expected));
    }

    @Test void currentPwCanRetainAReceiptButCannotMakeItFreshOrChangeOwner() throws Exception {
        var catalog = pwCatalog();
        var initial = current(pwInput(10));
        var expected = AgentSettings.snapshot(catalog, initial);
        var applied = ClusterObservation.read(initial, catalog, expected)
                .advance(current(pwInput(11)), catalog, expected)
                .advance(current(pwInput(12)), catalog, expected);
        var same = current(pwInput(12));
        var confirmed = applied.confirmPw(same, catalog, expected);
        assertEquals(2, confirmed.advances());
        confirmed.requireCurrent(same, "vehicle-drive-pedestrian-warning", "light");
        assertThrows(ClusterObservation.Pending.class, () -> confirmed.advance(same, catalog, expected));
        var foreign = pwInput(13);
        process(foreign).addProperty("pid", 43);
        syncAggregate(foreign);
        assertThrows(IOException.class, () -> confirmed.confirmPw(current(foreign), catalog, expected));
    }

    @Test void otherAgentsWaitingUiCannotBecomePwPassThrough() throws Exception {
        var expected = expected();
        var applied = ClusterObservation.read(current(input(10)), catalog(), expected);
        var waiting = input(11);
        var agent = cluster(waiting).getAsJsonArray("agents").get(0).getAsJsonObject();
        agent.addProperty("state", "waiting-ui");
        agent.getAsJsonArray("targets").get(0).getAsJsonObject().addProperty("state", "waiting-ui");
        assertThrows(IOException.class, () -> applied.advancePwRestore(current(waiting), catalog(), expected));
        assertThrows(ClusterObservation.Pending.class, () -> ClusterObservation.read(current(waiting), catalog(), expected));
    }

    @Test void twoStableNativeResponseAdvancesRemainStartupAvailabilityOnly() throws Exception {
        var expected = expected(); var first = ClusterObservation.read(current(input(10)), catalog(), expected);
        assertThrows(IOException.class, () -> first.requireCurrent(current(input(10)), "font-cyrillic", "gauge"));
        var second = first.advance(current(input(11)), catalog(), expected);
        assertThrows(IOException.class, () -> second.requireCurrent(current(input(11)), "font-cyrillic", "gauge"));
        var thirdRuntime = current(input(12)); var third = second.advance(thirdRuntime, catalog(), expected);
        third.requireCurrent(thirdRuntime, "font-cyrillic", "gauge");
        var rows = HiddenTargets.read(catalog(), thirdRuntime, Set.of(), expected, third);
        assertEquals("applied-with-current-native-identity", rows.get(0).getAsJsonObject().get("observedState").getAsString());
        assertThrows(IOException.class, () -> HiddenTargets.read(catalog(), current(input(13)), Set.of(), expected, third));
        assertEquals(2, third.advances()); assertFalse(third.evidence().get("functionalAcceptance").getAsBoolean());
    }
    @Test void rootRepublishOrCachedReceiptCannotAdvanceNativeEvidence() throws Exception {
        var expected = expected(); var first = ClusterObservation.read(current(input(10)), catalog(), expected);
        var cached = input(10); cluster(cached).addProperty("snapshotRevision", 999); runtime(cached).addProperty("state", "ready");
        assertThrows(ClusterObservation.Pending.class, () -> first.advance(current(cached), catalog(), expected));
        assertThrows(IOException.class, () -> first.advance(current(input(9)), catalog(), expected));
    }
    @Test void growingCountersCannotHideAnotherAndroidOwnerOrNativeBootEpochGeneration() throws Exception {
        var expected = expected(); var first = ClusterObservation.read(current(input(10)), catalog(), expected);
        var foreignOwner = input(11); runtime(foreignOwner).getAsJsonObject("publisher").addProperty("daemonPid", 101);
        assertTrue(assertThrows(IOException.class, () -> first.advance(current(foreignOwner), catalog(), expected)).getMessage().contains("Android owner"));
        for (String key : new String[]{"bootId", "daemonEpoch", "catalogSha256", "generationId", "desiredSha256"}) {
            var changed = input(11); cluster(changed).addProperty(key, key.equals("bootId") ? "another-boot" : "2".repeat(64));
            assertThrows(IOException.class, () -> first.advance(current(changed), catalog(), expected));
        }
    }
    @Test void currentNativeProcessLifetimeAndEndpointCannotBeReboundByARevision() throws Exception {
        var expected = expected(); var first = ClusterObservation.read(current(input(10)), catalog(), expected);
        for (String key : new String[]{"pid", "birthIdentity", "executableSha256", "endpointEpoch", "lifecycleGeneration"}) {
            var changed = input(11);
            if (key.equals("pid") || key.equals("lifecycleGeneration")) process(changed).addProperty(key, 43);
            else process(changed).addProperty(key, "3".repeat(64));
            syncAggregate(changed);
            assertTrue(assertThrows(IOException.class, () -> first.advance(current(changed), catalog(), expected)).getMessage().contains("process lifetime"));
        }
    }
    @Test void appliedAggregateCannotReplaceExactCurrentTargetProcessAndDesiredState() throws Exception {
        var expected = expected(); var missing = input(10); cluster(missing).getAsJsonArray("agents").get(0).getAsJsonObject().getAsJsonArray("targets").remove(0); syncAggregate(missing);
        assertThrows(ClusterObservation.Pending.class, () -> ClusterObservation.read(current(missing), catalog(), expected));
        var disabled = input(10); cluster(disabled).getAsJsonArray("agents").get(0).getAsJsonObject().addProperty("enabled", false);
        assertThrows(IOException.class, () -> ClusterObservation.read(current(disabled), catalog(), expected));
        var noProcess = input(10); cluster(noProcess).getAsJsonArray("agents").get(0).getAsJsonObject().getAsJsonArray("targets").get(0).getAsJsonObject().add("process", com.google.gson.JsonNull.INSTANCE); syncAggregate(noProcess);
        assertThrows(ClusterObservation.Pending.class, () -> ClusterObservation.read(current(noProcess), catalog(), expected));
        var duplicate = input(10); var targets = cluster(duplicate).getAsJsonArray("agents").get(0).getAsJsonObject().getAsJsonArray("targets"); targets.add(targets.get(0).deepCopy()); syncAggregate(duplicate);
        assertThrows(IOException.class, () -> ClusterObservation.read(current(duplicate), catalog(), expected));
    }
    @Test void aSharedPidCannotProveTwoDifferentNativeTargets() throws Exception {
        var catalog = catalog(); var signedTargets = catalog.getAsJsonArray("agents").get(0).getAsJsonObject().getAsJsonArray("targets");
        signedTargets.add(RunFiles.parse("{\"platform\":\"qnx\",\"process\":\"point\"}"));
        var input = input(10); var targets = cluster(input).getAsJsonArray("agents").get(0).getAsJsonObject().getAsJsonArray("targets");
        var point = targets.get(0).deepCopy().getAsJsonObject(); point.addProperty("targetProcess", "point"); targets.add(point); syncAggregate(input);
        var expected = HiddenTargets.snapshot(catalog, current(input), Set.of("font-cyrillic"));
        assertTrue(assertThrows(IOException.class, () -> ClusterObservation.read(current(input), catalog, expected)).getMessage().contains("one native PID"));
    }
    @Test void invalidCounterOrUnconfirmedDeliveryCannotSupplyNativeIdentity() throws Exception {
        var expected = expected(); var pending = input(10); cluster(pending).addProperty("deliveryState", "pending");
        assertThrows(ClusterObservation.Pending.class, () -> ClusterObservation.read(current(pending), catalog(), expected));
        for (String counter : new String[]{"0", "9007199254740992", "-1", "1.5"}) {
            var invalid = input(10); cluster(invalid).add("observationRevision", new com.google.gson.JsonPrimitive(new java.math.BigDecimal(counter)));
            assertThrows(IOException.class, () -> ClusterObservation.read(current(invalid), catalog(), expected));
        }
    }
    @Test void androidReadyCannotAdmitPwWithoutTheLightAcknowledgement() throws Exception {
        String id = "vehicle-drive-pedestrian-warning";
        JsonObject value = input(10);
        JsonObject config = value.getAsJsonObject("data").getAsJsonObject("files")
            .getAsJsonObject("config").getAsJsonObject("data").getAsJsonObject("agents");
        config.add(id, RunFiles.parse("{\"enabled\":true,\"options\":{}}"));
        JsonObject catalog = RunFiles.parse("{\"agents\":[{\"id\":\"" + id + "\",\"status\":\"stable\",\"activation\":{\"type\":\"checkbox\"},\"targets\":[{\"platform\":\"qnx\",\"process\":\"light\"}]}]}");
        JsonObject agent = cluster(value).getAsJsonArray("agents").get(0).getAsJsonObject();
        agent.addProperty("id", id);
        JsonObject target = agent.getAsJsonArray("targets").get(0).getAsJsonObject();
        target.addProperty("id", id);
        target.addProperty("targetProcess", "light");
        target.addProperty("state", "waiting-process");
        target.addProperty("reason", "awaiting-qnx-target-ack");
        target.add("process", com.google.gson.JsonNull.INSTANCE);
        agent.addProperty("state", "waiting-process");
        agent.add("process", com.google.gson.JsonNull.INSTANCE);
        var current = current(value);
        var expected = AgentSettings.snapshot(catalog, current);
        assertEquals("ready", current.runtime().get("state").getAsString());
        assertThrows(ClusterObservation.Pending.class, () -> ClusterObservation.read(current, catalog, expected));
        cluster(value).addProperty("deliveryState", "pending");
        assertThrows(ClusterObservation.Pending.class, () -> ClusterObservation.read(current(value), catalog, expected));
    }
    @Test void executorCatalogRowsAreNotUserAgentPrerequisites() throws Exception {
        JsonObject catalog = catalog();
        catalog.getAsJsonArray("agents").add(RunFiles.parse("{\"id\":\"ui-qinggan\",\"kind\":\"executor\",\"targets\":[]}"));
        var current = current(input(10));
        var expected = AgentSettings.snapshot(catalog, current);
        assertTrue(ClusterObservation.required(catalog, expected));
        assertNotNull(ClusterObservation.read(current, catalog, expected));
    }
}
