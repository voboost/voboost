package ru.voboost.tests.installer.gui;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.voboost.tests.RunFiles;

/** Native protocol/state fixtures only; these tests neither launch an app nor grant AX permission. */
class MacGuiTest {
    @TempDir Path directory;
    private static JsonObject node(String id, String parent, String role, String label, boolean enabled) {
        JsonObject node = new JsonObject(); node.addProperty("nodeToken", id);
        if (parent == null) node.add("parent", JsonNull.INSTANCE); else node.addProperty("parent", parent);
        node.addProperty("role", role); node.addProperty("subrole", ""); node.addProperty("title", label);
        for (String key : List.of("description", "identifier", "value")) node.addProperty(key, "");
        node.addProperty("enabled", enabled); node.addProperty("redacted", false);
        JsonArray frame = new JsonArray(); for (int value : new int[]{10, 20, 100, 30}) frame.add(value); node.add("frame", frame); return node;
    }
    private final class Fixture implements MacGui.Backend {
        final JsonObject config = MacGui.configuration(directory.resolve("Voboost Installer.app"), 100, "1000");
        final String window = UUID.randomUUID().toString();
        int calls, actions;
        boolean applied, blocked, unknown, timeout, duplicate, cycle, malformed, dialog, duplicateDialog;
        String swap;
        String lastObservation;
        public JsonObject exchange(JsonObject request, Duration timeout) throws Exception {
            ++calls; assertEquals(Duration.ofSeconds(25), timeout);
            assertEquals(config.get("ownerId"), request.get("ownerId"));
            String op = request.get("op").getAsString();
            JsonObject result = new JsonObject(); result.addProperty("schemaVersion", 1); result.add("ownerId", config.get("ownerId")); result.add("requestId", request.get("requestId"));
            if (op.equals("inspect") && blocked) {
                result.addProperty("status", "blocked"); result.addProperty("reason", "permission"); result.addProperty("actionDispatched", false); return result;
            }
            if (!op.equals("inspect")) {
                ++actions;
                Path record = work().resolve("actions").resolve(request.get("actionId").getAsString() + ".json");
                JsonObject durable = RunFiles.read(record);
                assertEquals("dispatched", durable.get("state").getAsString());
                assertEquals(request.get("actionId"), durable.get("actionId")); assertEquals(request.get("op"), durable.get("operation"));
                if (blocked) { result.addProperty("status", "blocked"); result.addProperty("reason", "unsupported"); result.addProperty("actionDispatched", false); return result; }
                applied = true;
                if (this.timeout) throw new IOException("fixture response deadline expired after dispatch");
                if (unknown) { result.addProperty("status", "unknown"); result.addProperty("reason", "unknown_action"); result.addProperty("actionDispatched", true); return result; }
                result.addProperty("status", "ok"); result.add("actionId", request.get("actionId")); result.addProperty("actionDispatched", true);
            } else {
                result.addProperty("status", "ok");
                if (!duplicate || lastObservation == null) lastObservation = UUID.randomUUID().toString();
                result.addProperty("observationId", lastObservation);
                String root = UUID.randomUUID().toString(), button = UUID.randomUUID().toString(); JsonArray nodes = new JsonArray();
                nodes.add(node(root, null, "AXWindow", "Voboost Installer", false));
                nodes.add(node(button, cycle ? button : root, "AXButton", applied ? "Installed" : "Install", true));
                if (malformed) nodes.get(1).getAsJsonObject().remove("enabled");
                                if (dialog) {
                    nodes.add(node(UUID.randomUUID().toString(), root, "AXButton", "Uninstall", true));
                    for (int index = 0; index < (duplicateDialog ? 2 : 1); index++) {
                        String group = UUID.randomUUID().toString();
                        nodes.add(node(group, root, "AXGroup", "", false));
                        nodes.add(node(UUID.randomUUID().toString(), group, "AXStaticText", "Uninstall Voboost?", false));
                        nodes.add(node(UUID.randomUUID().toString(), group, "AXStaticText", "Exact confirmation body", false));
                        nodes.add(node(UUID.randomUUID().toString(), group, "AXButton", "Cancel", true));
                        nodes.add(node(UUID.randomUUID().toString(), group, "AXButton", "Uninstall", true));
                    }
                }
                result.add("nodes", nodes);

            }
            result.addProperty("pid", swap != null && swap.equals("pid") ? 101 : 100);
            result.addProperty("startedMillis", swap != null && swap.equals("start") ? "1001" : "1000");
            result.addProperty("windowToken", swap != null && swap.equals("window") ? UUID.randomUUID().toString() : window);
            return result;
        }
        Path work() { return directory.resolve("work-" + config.get("ownerId").getAsString()); }
        MacGui open() throws Exception { return new MacGui(work(), config, this); }
    }
    @Test void axSuccessRemainsPendingUntilFreshExpectedReadbackAndForbidsAnotherMutation() throws Exception {
        Fixture fixture = new Fixture();
        try (MacGui gui = fixture.open()) {
            var before = gui.observe(); var target = before.target("AXButton", "Install"); var action = gui.press(target);
            assertEquals("accepted", RunFiles.read(action.record()).get("state").getAsString()); assertSame(action, gui.pending());
            int calls = fixture.calls; assertThrows(IOException.class, () -> gui.press(target)); assertEquals(calls, fixture.calls);
            assertThrows(IOException.class, () -> action.confirm(before, value -> true));
            var after = gui.observe(); assertTrue(after.visible("Installed"));
            assertThrows(IOException.class, () -> action.confirm(after, value -> value.visible("Install")));
            action.confirm(after, value -> value.visible("Installed")); assertNull(gui.pending());
            assertEquals("confirmed", RunFiles.read(action.record()).get("state").getAsString());
            assertThrows(IOException.class, () -> action.confirm(after, value -> true)); assertEquals(1, fixture.actions);
        }
    }
    @Test void pidStarttimeAndWindowSwapInvalidateOldTargetsBeforeAnyAction() throws Exception {
        for (String field : List.of("pid", "start", "window")) {
            Fixture fixture = new Fixture();
            try (MacGui gui = fixture.open()) {
                var target = gui.observe().target("AXButton", "Install"); fixture.swap = field;
                assertThrows(IOException.class, gui::observe);
                int calls = fixture.calls; assertThrows(IOException.class, () -> gui.press(target)); assertEquals(calls, fixture.calls); assertEquals(0, fixture.actions);
            }
        }
    }
    @Test void unknownAndTimeoutAreDurableAndNeverReplayEvenWhenTheUiActuallyChanged() throws Exception {
        for (boolean timeout : List.of(false, true)) {
            Fixture fixture = new Fixture(); fixture.timeout = timeout; fixture.unknown = !timeout;
            try (MacGui gui = fixture.open()) {
                var target = gui.observe().target("AXButton", "Install");
                assertThrows(MacGui.Unknown.class, () -> gui.press(target));
                assertNotNull(gui.pending()); assertEquals("unknown", RunFiles.read(gui.pending().record()).get("state").getAsString());
                int calls = fixture.calls; assertThrows(IOException.class, () -> gui.press(target)); assertEquals(calls, fixture.calls); assertEquals(1, fixture.actions);
                assertThrows(IOException.class, fixture::open);
                if (!timeout) { var after = gui.observe(); gui.pending().confirm(after, value -> value.visible("Installed")); assertNull(gui.pending()); }
            }
        }
    }
    @Test void unavailablePermissionIsBlockedWithoutPromptsOrImplicitAction() throws Exception {
        Fixture fixture = new Fixture(); fixture.blocked = true;
        try (MacGui gui = fixture.open()) {
            assertThrows(MacGui.Blocked.class, gui::observe); assertEquals(1, fixture.calls); assertEquals(0, fixture.actions);
            try (var records = Files.list(fixture.work().resolve("actions"))) { assertEquals(0, records.count()); }
        }
    }
    @Test void nativePreconditionRejectionProvesNotStartedAndDoesNotBecomeAccepted() throws Exception {
        Fixture fixture = new Fixture();
        try (MacGui gui = fixture.open()) {
            var target = gui.observe().target("AXButton", "Install"); fixture.blocked = true;
            assertThrows(MacGui.Blocked.class, () -> gui.press(target)); assertNull(gui.pending()); assertFalse(fixture.applied);
            try (var records = Files.list(fixture.work().resolve("actions"))) {
                List<Path> paths = records.toList(); assertEquals(1, paths.size()); assertEquals("rejected", RunFiles.read(paths.get(0)).get("state").getAsString());
            }
        }
    }
    @Test void staleDuplicateMalformedAndCyclicObservationsCannotAuthorizeActions() throws Exception {
        for (String fault : List.of("duplicate", "malformed", "cycle")) {
            Fixture fixture = new Fixture();
            try (MacGui gui = fixture.open()) {
                var target = gui.observe().target("AXButton", "Install");
                fixture.duplicate = fault.equals("duplicate"); fixture.malformed = fault.equals("malformed"); fixture.cycle = fault.equals("cycle");
                assertThrows(IOException.class, gui::observe);
                int calls = fixture.calls; assertThrows(IOException.class, () -> gui.press(target)); assertEquals(calls, fixture.calls); assertEquals(0, fixture.actions);
            }
        }
    }
    @Test void foreignSessionTargetIsRejectedLocally() throws Exception {
        Fixture first = new Fixture(), second = new Fixture();
        try (MacGui gui = first.open(); MacGui other = second.open()) {
            gui.observe(); var foreign = other.observe().target("AXButton", "Install"); int calls = first.calls;
            assertThrows(IOException.class, () -> gui.press(foreign)); assertEquals(calls, first.calls); assertEquals(0, first.actions);
        }
    }    private Path helper(String body) throws Exception {
        Path script = directory.resolve("helper-" + UUID.randomUUID());
        Files.writeString(script, "#!/bin/sh\nprintf '%s' \"$$\" > \"${2%/*}/pid\"\n" + body + "\n");
        assertTrue(script.toFile().setExecutable(true)); return script;
    }
    @Test void realPipeTimeoutTerminatesOwnedHelperWithoutReplayingRequest() throws Exception {
        Path work = Files.createDirectory(directory.resolve("timeout"));
        Path script = helper("while :; do :; done");
        long started = System.nanoTime();
        try (var backend = MacGui.processBackend(script, work.resolve("owner.json"), work)) {
            long ready = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            while (!Files.exists(work.resolve("pid")) && System.nanoTime() < ready) Thread.sleep(10);
            assertTrue(Files.exists(work.resolve("pid")));
            assertThrows(Exception.class, () -> backend.exchange(new JsonObject(), Duration.ofMillis(150)));
            assertThrows(IOException.class, () -> backend.exchange(new JsonObject(), Duration.ofMillis(150)));
        }
        assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(8)) < 0);
        long pid = Long.parseLong(Files.readString(work.resolve("pid")));
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
    }
    @Test void realPipeRejectsBoundedStderrOverflowAndStopsOwnedHelper() throws Exception {
        Path work = Files.createDirectory(directory.resolve("stderr"));
        Path script = helper("while :; do printf '0123456789012345678901234567890123456789012345678901234567890123456789' >&2; done");
        try (var backend = MacGui.processBackend(script, work.resolve("owner.json"), work)) {
            assertThrows(Exception.class, () -> backend.exchange(new JsonObject(), Duration.ofSeconds(3)));
        }
        assertTrue(Files.size(work.resolve("bridge.err")) <= 65536);
        long pid = Long.parseLong(Files.readString(work.resolve("pid")));
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
    }
    @Test void dialogTargetExcludesSameLabelOutsideAndRejectsForeignOrDuplicateDialog() throws Exception {
        Fixture fixture = new Fixture(); fixture.dialog = true;
        try (MacGui gui = fixture.open()) {
            var before = gui.observe();
            assertThrows(MacGui.Blocked.class, () -> before.target("AXButton", "Uninstall"));
            assertThrows(MacGui.Blocked.class, () -> before.dialogTarget("Uninstall Voboost?", "Foreign body", "Uninstall", "Cancel"));
            assertThrows(MacGui.Blocked.class, () -> before.dialogTarget("Uninstall Voboost?", "Exact confirmation body", "Uninstall", "Foreign cancel"));
            gui.press(before.dialogTarget("Uninstall Voboost?", "Exact confirmation body", "Uninstall", "Cancel"));
            assertEquals(1, fixture.actions);
        }
        Fixture duplicate = new Fixture(); duplicate.dialog = true; duplicate.duplicateDialog = true;
        try (MacGui gui = duplicate.open()) {
            var before = gui.observe();
            assertThrows(MacGui.Blocked.class, () -> before.dialogTarget("Uninstall Voboost?", "Exact confirmation body", "Uninstall", "Cancel"));
            assertEquals(0, duplicate.actions);
        }
    }
    @Test void realPipeCanReceiveLaunchReceiptWithoutSendingAnImplicitCommand() throws Exception {
        Path work = Files.createDirectory(directory.resolve("receive"));
        Path script = helper("printf '%s\\n' '{\"status\":\"fixture-only\"}'; IFS= read -r line; printf '%s\\n' \"$line\"");
        try (var backend = MacGui.processBackend(script, work.resolve("owner.json"), work)) {
            assertEquals("fixture-only", backend.receive(Duration.ofSeconds(3)).get("status").getAsString());
            assertTrue(backend.process().isAlive());
            JsonObject request = new JsonObject(); request.addProperty("explicit", true);
            assertEquals(request, backend.exchange(request, Duration.ofSeconds(3)));
        }
    }

}
