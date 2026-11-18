package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Controlled host transitions test dispatch/effect ordering, never functional keyboard acceptance. */
class AgentKeyboardInputTest {
    private static final class Port implements KeyboardInput.Port {
        String value = ""; int taps, canvases, reads; long time;
        boolean canvas, unknown, ignored, wrong, changed, pidChanged, pidMissing;
        public KeyboardInput.Frame observe() {
            JsonObject ui = NavigationInputTest.surface(); NavigationInputTest.editor(ui, value);
            if (changed && taps > 0) ui.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("resource_id", NavigationInput.NAVI + ":id/foreign");
            if (!canvas) for (char key : "abcdefghijklmnopqrstuvwxyzАБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя".toCharArray()) {
                JsonObject node = new JsonObject(); node.addProperty("id", "key-" + key); node.addProperty("window_id", "ime");
                node.addProperty("package", "com.qinggan.app.qgime"); node.addProperty("text", "" + key);
                node.addProperty("visible", true); node.addProperty("enabled", true); node.addProperty("clickable", true);
                ui.getAsJsonArray("nodes").add(node);
            }
            JsonObject tree = new JsonObject(); if (!pidMissing) tree.addProperty("android_pid", pidChanged && taps > 0 ? 43 : 42); tree.addProperty("boot_id", "boot"); tree.addProperty("profile", "fixture"); tree.addProperty("session_id", "a".repeat(32)); tree.addProperty("source_instance", "source"); tree.addProperty("observation_id", "observation-" + (reads + 1)); var screens = new com.google.gson.JsonArray(); ui.addProperty("screen", "primary"); screens.add(ui); tree.add("screens", screens);
            return new KeyboardInput.Frame(tree, Path.of("/controlled-host-fixture/observation-" + ++reads));
        }
        private void apply(String key) throws IOException {
            if (!ignored) value += wrong ? "x" : key;
            if (unknown) throw new IOException("controlled unknown dispatch");
        }
        public void tap(KeyboardInput.Frame frame, JsonObject node) throws IOException { ++taps; apply(node.get("text").getAsString()); }
        public void canvas(KeyboardInput.Frame frame, NavigationInput.Editor editor, String key) throws IOException { ++canvases; editor.requirePrefix(value); apply(key); }
        public long now() { time += 50_000_000; return time; }
        public void pause() { time += 50_000_000; }
    }
    @Test void exactPerKeyReadbackPreservesCommittedMixedPrefix() throws Exception {
        Port port = new Port(); port.value = "Москва";
        var proof = new KeyboardInput(port).type("moscow", "Москва");
        assertEquals("Москваmoscow", proof.committed()); assertEquals(6, port.taps); assertEquals(0, port.canvases);
        assertTrue(proof.evidence().size() >= 13);
    }
    @Test void onlySemanticLookupAbsenceChoosesCanvasBeforeDispatch() throws Exception {
        Port port = new Port(); port.canvas = true;
        assertEquals("Ёё", new KeyboardInput(port).type("Ёё", "").committed());
        assertEquals(0, port.taps); assertEquals(2, port.canvases);
    }
    @Test void unknownSemanticOrCanvasInputNeverReplaysOrChangesRoute() throws Exception {
        for (boolean canvas : new boolean[]{false, true}) {
            Port port = new Port(); port.canvas = canvas; port.unknown = true;
            assertThrows(IOException.class, () -> new KeyboardInput(port).type("moscow", ""));
            assertEquals(1, port.taps + port.canvases); assertEquals(canvas ? 1 : 0, port.canvases);
        }
    }
    @Test void acceptedButIgnoredInputWaitsWithoutSendingASecondKey() throws Exception {
        Port port = new Port(); port.ignored = true;
        assertThrows(IOException.class, () -> new KeyboardInput(port).type("moscow", ""));
        assertEquals(1, port.taps); assertEquals(0, port.canvases); assertTrue(port.reads > 3);
    }
    @Test void unexpectedCommitOrEditorDriftStopsBeforeTheNextKey() throws Exception {
        for (boolean wrong : new boolean[]{false, true}) {
            Port port = new Port(); port.wrong = wrong; port.changed = !wrong;
            assertThrows(IOException.class, () -> new KeyboardInput(port).type("moscow", ""));
            assertEquals(1, port.taps); assertEquals(0, port.canvases);
        }
    }
    @Test void wrongInitialPrefixOrUnsupportedTextNeverDispatches() throws Exception {
        Port port = new Port(); port.value = "foreign";
        assertThrows(IOException.class, () -> new KeyboardInput(port).type("moscow", ""));
        assertEquals(0, port.taps + port.canvases);
        assertThrows(IOException.class, () -> new KeyboardInput(port).type(";arbitrary", "foreign"));
        assertEquals(0, port.taps + port.canvases);
    }
    @Test void changedAndroidPidStopsBeforeNextKey() throws Exception {
        Port port = new Port(); port.pidChanged = true;
        assertThrows(IOException.class, () -> new KeyboardInput(port).type("moscow", ""));
        assertEquals(1, port.taps);
    }

    @Test void missingAndroidPidBlocksBeforeDispatch() throws Exception {
        Port port = new Port(); port.pidMissing = true;
        assertThrows(IOException.class, () -> new KeyboardInput(port).type("moscow", ""));
        assertEquals(0, port.taps + port.canvases);
    }

}
