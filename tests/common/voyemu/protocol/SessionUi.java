package ru.voboost.voyemu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/** Projects a validated multimedia observation while the caller retains the raw session receipt. */
public final class SessionUi {
    private static final List<String> SCREENS = List.of("primary", "secondary");
    private static final List<String> METADATA = List.of("source", "source_clock_domain", "host_interval",
            "boot_id", "android_pid", "observer_pid", "source_instance", "revision", "observation_id",
            "vehicle_revision", "selector", "read_attempts", "transient_rejections", "observer_epoch",
            "source_timestamp_ms", "started_uptime_ms", "completed_uptime_ms");

    public static final class Unavailable extends IOException {
        private final String code;
        private final boolean retryable;
        Unavailable(String code, String message, boolean retryable) {
            super(code + ": " + message);
            this.code = code;
            this.retryable = retryable;
        }
        public String code() { return code; }
        public boolean retryable() { return retryable; }
    }

    private SessionUi() {}

    public static JsonObject multimedia(JsonObject wire, String profile, String session,
                                        List<String> selected) throws IOException {
        if (selected.isEmpty() || !SCREENS.containsAll(selected) || Set.copyOf(selected).size() != selected.size()
                || !"voyemu.session.ui.v1".equals(text(wire, "format"))
                || !profile.equals(text(wire, "profile")) || !session.equals(text(wire, "session_id"))
                || !bool(wire, "atomic_ui_state", false) || !wire.has("cluster") || !wire.get("cluster").isJsonNull()
                || !array(wire, "multimedia") || wire.getAsJsonArray("multimedia").size() != 2) {
            throw new IOException("session UI identity or screen selection differs");
        }
        JsonArray screens = new JsonArray();
        JsonObject identity = null;
        Unavailable unavailable = null;
        boolean complete = true;
        for (int index = 0; index < SCREENS.size(); index++) {
            String screen = SCREENS.get(index);
            JsonElement item = wire.getAsJsonArray("multimedia").get(index);
            if (!selected.contains(screen)) {
                if (!item.isJsonNull()) throw new IOException("unselected UI slot is populated");
                continue;
            }
            if (!item.isJsonObject()) throw new IOException("selected UI slot is missing");
            JsonObject slot = item.getAsJsonObject();
            String status = text(slot, "status");
            if (!screen.equals(text(slot, "screen")) || !array(slot, "nodes") || !array(slot, "windows")
                    || !bool(slot, "complete", status.equals("ok"))) {
                throw new IOException("malformed multimedia UI slot");
            }
            complete &= status.equals("ok");
            if (status.equals("unavailable") || status.equals("unsupported")) {
                if (!slot.getAsJsonArray("nodes").isEmpty() || !slot.getAsJsonArray("windows").isEmpty())
                    throw new IOException("unavailable UI slot contains a tree");
                String code = text(slot, "error_code");
                JsonObject error = slot.has("error") && slot.get("error").isJsonObject()
                        ? slot.getAsJsonObject("error") : new JsonObject();
                if (!slot.has("error_code") && !slot.has("error")) {
                    if (screen.equals("primary") || unavailable == null) {
                        unavailable = new Unavailable(status.equals("unsupported") ? "TEST_UNSUPPORTED" : "TREE_UNAVAILABLE",
                                "multimedia screen has no observation", false);
                    }
                } else if (!code.matches("[A-Z][A-Z0-9_]{1,63}") || !code.equals(text(error, "code")))
                    throw new IOException("unavailable UI slot lacks a correlated error");
                String message = text(error, "message").replaceAll("\\p{Cntrl}", " ");
                if (!code.isEmpty() && (unavailable == null || screen.equals("primary"))) {
                    unavailable = new Unavailable(code, message.substring(0, Math.min(512, message.length())),
                            "unknown".equals(text(error, "outcome")) && bool(error, "retryable", true));
                }
                if (screen.equals("primary")) throw unavailable;
            } else {
                if (!(status.equals("ok") || status.equals("partial")) || !"android.ui".equals(text(slot, "source"))
                        || slot.has("error_code") || slot.has("error"))
                    throw new IOException("unexpected multimedia UI source or status");
                if (identity == null) identity = slot;
                else for (String key : List.of("boot_id", "android_pid", "observer_pid", "source_instance", "observation_id"))
                    if (identity.get(key) == null || !identity.get(key).equals(slot.get(key)))
                        throw new IOException("multimedia UI slots come from different observations");
            }
            JsonObject surface = slot.deepCopy();
            if (slot.has("observer_source")) surface.add("source", slot.get("observer_source").deepCopy());
            screens.add(surface);
        }
        if (identity == null) throw unavailable == null ? new IOException("no multimedia UI observed") : unavailable;
        if (!bool(wire, "complete", complete) || !(complete ? "ready" : "partial").equals(text(wire, "status")))
            throw new IOException("multimedia UI completeness differs");
        JsonObject result = new JsonObject();
        result.addProperty("format", "voyemu.ui.v1");
        result.addProperty("profile", profile);
        result.addProperty("session_id", session);
        result.addProperty("status", complete ? "ready" : "partial");
        result.addProperty("complete", complete);
        result.addProperty("atomic_ui_state", false);
        for (String key : METADATA) if (identity.has(key)) result.add(key, identity.get(key).deepCopy());
        if (!result.has("completed_uptime_ms") && result.has("source_timestamp_ms"))
            result.add("completed_uptime_ms", result.get("source_timestamp_ms"));
        result.add("screens", screens);
        return result;
    }

    private static String text(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString() : "";
    }

    private static boolean bool(JsonObject value, String key, boolean expected) {
        JsonElement element = value.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                && element.getAsBoolean() == expected;
    }

    private static boolean array(JsonObject value, String key) {
        return value.has(key) && value.get(key).isJsonArray();
    }
}
