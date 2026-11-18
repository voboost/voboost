package ru.voboost.voyemu;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Real-key orchestration: one dispatch, then exact editor effect. No text injection and no retry after uncertain input. */
public final class KeyboardInput {
    public record Frame(JsonObject tree, Path evidence) {
        public Frame { tree = tree.deepCopy(); if (evidence == null) throw new IllegalArgumentException("retained UI evidence required"); }
        @Override public JsonObject tree() { return tree.deepCopy(); }
        public JsonObject surface() throws IOException { return UiEvidence.surface(tree, "primary"); }
    }
    public interface Port {
        Frame observe() throws Exception;
        void tap(Frame frame, JsonObject observedKey) throws Exception;
        void canvas(Frame frame, NavigationInput.Editor editor, String key) throws Exception;
        default long now() { return System.nanoTime(); }
        default void pause() throws InterruptedException { Thread.sleep(50); }
    }
    public record Proof(String committed, Frame finalFrame, List<Path> evidence) {
        public Proof { evidence = List.copyOf(evidence); }
    }
    private final Port port;
    public KeyboardInput(Port port) { this.port = java.util.Objects.requireNonNull(port); }
    private static void scope(Frame frame, Frame first, java.util.Set<String> seen) throws IOException {
        JsonObject tree = frame.tree(), initial = first.tree();
        for (String field : List.of("boot_id", "profile", "session_id", "source_instance")) {
            if (!tree.has(field) || !tree.get(field).isJsonPrimitive() || !tree.getAsJsonPrimitive(field).isString()
                    || tree.get(field).getAsString().isBlank() || tree.get(field).getAsString().length() > 256
                    || !tree.get(field).equals(initial.get(field))) throw new IOException("keyboard observation boot/session/source changed");
        }
        if (!tree.has("android_pid") || !tree.get("android_pid").isJsonPrimitive()
                || !tree.getAsJsonPrimitive("android_pid").isNumber()
                || !tree.get("android_pid").getAsString().matches("[1-9][0-9]{0,9}")
                || new java.math.BigInteger(tree.get("android_pid").getAsString()).compareTo(java.math.BigInteger.valueOf(Integer.MAX_VALUE)) > 0
                || !tree.get("android_pid").equals(initial.get("android_pid")))
            throw new IOException("keyboard observation Android PID changed or unavailable");
        if (!tree.has("observation_id") || !tree.get("observation_id").isJsonPrimitive()
                || !tree.getAsJsonPrimitive("observation_id").isString() || tree.get("observation_id").getAsString().isBlank()
                || !seen.add(tree.get("observation_id").getAsString())) throw new IOException("keyboard observation is stale or lacks identity");
    }
    public Proof type(String input, String prefix) throws Exception {
        if (input == null || prefix == null || input.isEmpty() || input.length() + prefix.length() > 128
                || !input.matches("[A-Za-zА-Яа-яЁё]+")) throw new IOException("closed bounded keyboard input required");
        List<Path> evidence = new ArrayList<>(); Frame frame = port.observe(), first = frame; java.util.Set<String> seen = new java.util.HashSet<>(); scope(frame, first, seen); evidence.add(frame.evidence());
        NavigationInput.Editor identity = NavigationInput.editor(frame.surface()); identity.requirePrefix(prefix);
        String committed = prefix;
        for (String character : input.codePoints().mapToObj(point -> new String(Character.toChars(point))).toList()) {
            frame = port.observe(); scope(frame, first, seen); evidence.add(frame.evidence());
            NavigationInput.Editor before = NavigationInput.editor(frame.surface()); before.requireSame(identity); before.requirePrefix(committed);
            JsonObject key;
            try { key = NavigationInput.key(frame.surface(), character); }
            catch (NavigationInput.CanvasRequired missing) {
                // Only pre-dispatch semantic absence selects the reviewed-pixel path.
                port.canvas(frame, before, character); key = null;
            }
            if (key != null) port.tap(frame, key);
            String expected = committed + character;
            long deadline = port.now() + Duration.ofSeconds(5).toNanos();
            boolean observed = false;
            while (port.now() < deadline) {
                frame = port.observe(); scope(frame, first, seen); evidence.add(frame.evidence());
                NavigationInput.Editor after = NavigationInput.editor(frame.surface()); after.requireSame(identity);
                if (port.now() >= deadline) break;
                if (after.value().equals(expected)) { after.requirePrefix(expected); observed = true; break; }
                if (!after.value().equals(committed)) throw new IOException("unexpected committed text after actual key; no replay authorized");
                after.requirePrefix(committed); port.pause();
            }
            if (!observed) throw new IOException("actual keyboard effect not observed within deadline; no key replay authorized");
            committed = expected;
        }
        return new Proof(committed, frame, evidence);
    }
}
