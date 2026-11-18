package ru.voboost.voyemu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.List;

/** Builds protocol responses from the tree samples shared with the semantic tests. */
public final class SessionUiTestFrames {
    private SessionUiTestFrames() {}

    public static JsonObject response(JsonObject tree, List<String> selected) {
        JsonObject frame = new JsonObject();
        frame.addProperty("format", "voyemu.session.ui.v1");
        for (String key : List.of("profile", "session_id")) frame.add(key, tree.get(key));
        frame.addProperty("atomic_ui_state", false);
        frame.add("cluster", JsonNull.INSTANCE);
        JsonArray slots = new JsonArray();
        JsonArray screens = tree.has("screens") ? tree.getAsJsonArray("screens") : new JsonArray();
        boolean complete = true;
        boolean observed = false;
        for (String screen : List.of("primary", "secondary")) {
            if (!selected.contains(screen)) {
                slots.add(JsonNull.INSTANCE);
                continue;
            }
            JsonObject slot = new JsonObject();
            for (JsonElement element : screens) {
                JsonObject candidate = element.getAsJsonObject();
                if (screen.equals(candidate.get("screen").getAsString())) slot = candidate.deepCopy();
            }
            if (!slot.has("screen")) {
                if (screen.equals("primary")) {
                    slots.add(JsonNull.INSTANCE);
                    complete = false;
                    continue;
                }
                slot.addProperty("screen", screen);
                slot.addProperty("status", "unsupported");
            }
            String status = slot.get("status").getAsString();
            boolean ok = "ok".equals(status);
            complete &= ok;
            observed |= ok || "partial".equals(status);
            slot.addProperty("complete", ok);
            if (!slot.has("windows")) slot.add("windows", new JsonArray());
            if (!slot.has("nodes")) slot.add("nodes", new JsonArray());
            for (var entry : tree.entrySet()) {
                if (!List.of("format", "profile", "session_id", "complete", "status", "screens", "atomic_ui_state").contains(entry.getKey()))
                    slot.add(entry.getKey(), entry.getValue());
            }
            slot.addProperty("source", "android.ui");
            for (String key : List.of("boot_id", "android_pid", "observer_pid", "source_instance", "observation_id"))
                if (!slot.has(key)) slot.addProperty(key, "fixture");
            slots.add(slot);
        }
        frame.add("multimedia", slots);
        frame.addProperty("complete", complete);
        frame.addProperty("status", complete ? "ready" : observed ? "partial" : "unavailable");
        return frame;
    }
}
