package ru.voboost.voyemu;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.RunFiles;

class UiTargetTest {
    private static final VoyemuClient.Session SESSION = VoyemuClient.Session.attached("a".repeat(32));
    private static JsonObject observation() throws Exception {
        return RunFiles.parse(("""
            {"format":"voyemu.ui.v1","profile":"machine","session_id":"%s",
             "boot_id":"boot","source_instance":"source","revision":"1","screens":[{
             "screen":"primary","status":"ok","complete":true,"width":1920,"height":720,
             "windows":[{"id":"window","type":1,"bounds":{"x":0,"y":0,"width":1920,"height":720},"title":"Setting","package":"com.qinggan.app.setting"}],
             "nodes":[{"window_id":"window","resource_id":"com.qinggan.app.setting:id/control","package":"com.qinggan.app.setting",
             "class":"android.widget.CheckBox","text":"Agent","visible":true,"enabled":true,"clickable":true,"actionable_candidate":true,
             "bounds":{"x":100,"y":200,"width":80,"height":40},"clipped_bounds":{"x":100,"y":200,"width":80,"height":40}}]}]}
            """).formatted(SESSION.id()));
    }
    private static JsonObject surface(JsonObject value) { return value.getAsJsonArray("screens").get(0).getAsJsonObject(); }
    private static JsonObject node(JsonObject value) { return surface(value).getAsJsonArray("nodes").get(0).getAsJsonObject(); }
    private static UiTarget target(JsonObject value) throws IOException {
        return UiTarget.select(value, "machine", SESSION, "primary", n -> "Agent".equals(n.get("text").getAsString()));
    }
    @Test void derivesInputFromTheObservedGeometryAndKeepsItsProofImmutable() throws Exception {
        JsonObject value = observation(); UiTarget target = target(value);
        assertEquals(140, target.x()); assertEquals(220, target.y()); assertNull(target.selection());
        target.expected().remove("window"); node(value).addProperty("text", "changed");
        assertEquals("Agent", target.expected().getAsJsonObject("target").get("text").getAsString());
        target.requireScope("machine", SESSION);
        assertThrows(IllegalArgumentException.class, () -> target.requireScope("other", SESSION));
        assertThrows(IllegalArgumentException.class, () -> target.requireScope("machine", VoyemuClient.Session.attached("b".repeat(32))));
    }
    @Test void rejectsAmbiguousOrStaleScopeInsteadOfTakingTheFirstMatch() throws Exception {
        JsonObject value = observation(); surface(value).getAsJsonArray("nodes").add(node(value).deepCopy());
        assertThrows(IOException.class, () -> target(value));
        JsonObject changed = observation(); changed.addProperty("session_id", "b".repeat(32));
        assertThrows(IOException.class, () -> target(changed));
        JsonObject duplicateWindow = observation(); var windows = surface(duplicateWindow).getAsJsonArray("windows"); windows.add(windows.get(0).deepCopy());
        assertThrows(IOException.class, () -> target(duplicateWindow));
    }
    @Test void rejectsPartialCoverageDisabledControlsAndOutOfBoundsGeometry() throws Exception {
        for (String field : new String[]{"visible", "enabled", "clickable", "actionable_candidate"}) {
            JsonObject value = observation(); node(value).addProperty(field, false);
            assertThrows(IOException.class, () -> target(value), field);
        }
        JsonObject partial = observation(); surface(partial).addProperty("complete", false);
        assertThrows(IOException.class, () -> target(partial));
        JsonObject outside = observation(); node(outside).getAsJsonObject("clipped_bounds").addProperty("x", 1910);
        assertThrows(IOException.class, () -> target(outside));
        JsonObject decimal = observation(); node(decimal).getAsJsonObject("clipped_bounds").addProperty("width", 1.5);
        assertThrows(IOException.class, () -> target(decimal));
    }
    @Test void arbitraryBindingCannotOpenTheFinitePhoneInputPath() throws Exception {
        JsonObject value = observation(); JsonObject screen = surface(value), selected = node(value);
        screen.addProperty("complete", false); screen.addProperty("status", "partial");
        JsonObject window = screen.getAsJsonArray("windows").get(0).getAsJsonObject();
        window.addProperty("package", "com.qinggan.bluetoothphone"); window.addProperty("type", 2010);
        selected.addProperty("package", "com.qinggan.bluetoothphone"); selected.addProperty("resource_id", "com.qinggan.bluetoothphone:id/answer_iv");
        window.add("verified_binding", new JsonObject());
        assertThrows(IOException.class, () -> target(value));
    }
}
