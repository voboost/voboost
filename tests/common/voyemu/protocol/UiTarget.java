package ru.voboost.voyemu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** A unique actionable node and its source/window identity from an actual API observation. */
public final class UiTarget {
    private final String profile, session, screen;
    private final JsonObject expected;
    private final JsonObject selection;
    private final int x, y;
    private final String inputScope;
    private enum Action { TAP, SETTINGS_SCROLL, LAUNCHER_DRAG, LAUNCHER_PAGE, KEYBOARD_CANVAS, DRAWER_HANDLE, DRAWER_CLOSE }
    private Action action = Action.TAP;
    private int viewportWidth;
    private com.google.gson.JsonArray closePoints;
    private UiTarget(String profile, String session, String screen, JsonObject expected, JsonObject selection, int x, int y, String inputScope) {
        this.profile = profile; this.session = session; this.screen = screen; this.expected = expected; this.selection = selection;
        this.x = x; this.y = y; this.inputScope = inputScope;
    }

    /** The API scope of the observation that produced this target; null means the default (Accessibility) source. */
    public String inputScope() { return inputScope; }

    public static UiTarget select(JsonObject observation, String profile, VoyemuClient.Session session,
            String screen, Predicate<JsonObject> selector) throws IOException {
        return select(observation, profile, session, screen, selector, Action.TAP);
    }

    /** Only a complete observed OEM Settings scroll container can supply a scroll gesture. */
    public static UiTarget selectScrollable(JsonObject observation, String profile, VoyemuClient.Session session,
            String screen, Predicate<JsonObject> selector) throws IOException {
        return select(observation, profile, session, screen, selector, Action.SETTINGS_SCROLL);
    }

    /** One observed primary Launcher item; destination geometry is validated separately by the finite recipe. */
    public static UiTarget selectLauncherDrag(JsonObject observation, String profile, VoyemuClient.Session session,
            Predicate<JsonObject> selector) throws IOException {
        return select(observation, profile, session, "primary", selector, Action.LAUNCHER_DRAG);
    }

    public static UiTarget selectLauncherPage(JsonObject observation, String profile, VoyemuClient.Session session) throws IOException {
        return select(observation, profile, session, "primary", node -> "com.qinggan.app.launcher:id/all_app_view".equals(text(node,"resource_id")), Action.LAUNCHER_PAGE);
    }

    public static UiTarget selectKeyboardCanvas(JsonObject observation,String profile,VoyemuClient.Session session) throws IOException {
        return select(observation,profile,session,"primary",node -> text(node,"package").matches("com\\.qinggan\\.app\\.qgime(?:\\.second)?")
            && (text(node,"package")+":id/alpha_view1").equals(text(node,"resource_id")),Action.KEYBOARD_CANVAS);
    }
    public static UiTarget selectDrawerHandle(JsonObject observation,String profile,VoyemuClient.Session session) throws IOException {
        String selected=VehicleDrawer.handle(UiEvidence.surface(observation,"primary"));
        return select(observation,profile,session,"primary",node->selected.equals(text(node,"id")),Action.DRAWER_HANDLE);
    }
    public static UiTarget selectDrawerClose(JsonObject observation,String profile,VoyemuClient.Session session)throws IOException {
        var close=VehicleDrawer.close(UiEvidence.surface(observation,"primary"));UiTarget result=select(observation,profile,session,"primary",node->close.nodeId().equals(text(node,"id")),Action.DRAWER_CLOSE);
        result.closePoints=DrawerGesture.closePoints(close.startX(),close.y(),close.endX());return result;
    }
    private static UiTarget select(JsonObject observation,String profile,VoyemuClient.Session session,
            String screen,Predicate<JsonObject> selector,Action action) throws IOException {
        boolean scroll=action==Action.SETTINGS_SCROLL,drag=action==Action.LAUNCHER_DRAG,page=action==Action.LAUNCHER_PAGE,canvas=action==Action.KEYBOARD_CANVAS,drawer=action==Action.DRAWER_HANDLE,drawerClose=action==Action.DRAWER_CLOSE;
        try {
            if (!Set.of("primary", "secondary").contains(screen) || !profile.equals(text(observation, "profile"))
                    || !session.id().equals(text(observation, "session_id")) || !"voyemu.ui.v1".equals(text(observation, "format")))
                throw new IOException("UI observation scope differs");
            JsonObject surface = null;
            for (JsonElement item : observation.getAsJsonArray("screens")) {
                JsonObject candidate = item.getAsJsonObject();
                if (screen.equals(text(candidate, "screen"))) {
                    if (surface != null) throw new IOException("ambiguous UI screen");
                    surface = candidate;
                }
            }
            if (surface == null || !Set.of("ok", "partial").contains(text(surface, "status")))
                throw new IOException("UI screen unavailable");
            JsonObject selected = null;
            for (JsonElement item : surface.getAsJsonArray("nodes")) {
                JsonObject node = item.getAsJsonObject();
                if (selector.test(node)) {
                    if (selected != null) throw new IOException("ambiguous UI target");
                    selected = node;
                }
            }
            if (selected == null) throw new IOException("UI target not observed");
            if (!yes(selected, "visible") || !yes(selected, "enabled") || !(page || canvas || drawer || drawerClose || (scroll ? yes(selected, "scrollable") : drag ? yes(selected, "clickable") || yes(selected, "long_clickable") : yes(selected, "clickable"))))
                throw new IOException("UI target is not actionable");
            JsonObject window = null;
            for (JsonElement item : surface.getAsJsonArray("windows")) {
                JsonObject candidate = item.getAsJsonObject();
                if (candidate.get("id").equals(selected.get("window_id"))) {
                    if (window != null) throw new IOException("ambiguous UI window");
                    window = candidate;
                }
            }
            if (window == null || text(window, "package").isBlank() || !text(window, "package").equals(text(selected, "package")))
                throw new IOException("UI node/window owner differs");
            if (scroll && (!"primary".equals(screen) || !validScroll(selected,window)))
                throw new IOException("scroll requires the observed OEM Settings ScrollView");
            if (drag && (!"primary".equals(screen) || !"com.qinggan.app.launcher".equals(text(selected, "package"))
                    || integer(window, "type") != 1 || !selected.get("bounds").equals(selected.get("clipped_bounds"))))
                throw new IOException("drag requires a complete unclipped observed primary Launcher item");
            if (page && (!"primary".equals(screen) || !"com.qinggan.app.launcher".equals(text(selected,"package"))
                    || integer(window,"type")!=1 || !selected.get("bounds").equals(selected.get("clipped_bounds"))
                    || !Set.of("com.qinggan.launcher.base.allapp.AllAppBarView","androidx.recyclerview.widget.RecyclerView").contains(text(selected,"class"))))
                throw new IOException("page input requires the exact observed primary Launcher grid");
            if (canvas && (!"primary".equals(screen) || integer(window,"type")!=2 || !selected.get("bounds").equals(selected.get("clipped_bounds"))
                    || text(selected,"class").isBlank() || !text(selected,"package").matches("com\\.qinggan\\.app\\.qgime(?:\\.second)?")
                    || !(text(selected,"package")+":id/alpha_view1").equals(text(selected,"resource_id")))) throw new IOException("exact unclipped live keyboard canvas required");
            if (drawer && (!"primary".equals(screen) || !VehicleDrawer.OWNER.equals(text(selected,"package")) || !(VehicleDrawer.OWNER+":id/line_view").equals(text(selected,"resource_id")) || !"android.view.View".equals(text(selected,"class")) || integer(window,"type")!=3 || !selected.get("bounds").equals(selected.get("clipped_bounds")))) throw new IOException("actual observed vertical drawer receiver required");
            if(drawerClose&&(!"primary".equals(screen)||!VehicleDrawer.OWNER.equals(text(selected,"package"))||!text(selected,"resource_id").isEmpty()||!"android.widget.FrameLayout".equals(text(selected,"class"))||integer(window,"type")!=3||!selected.get("bounds").equals(window.get("bounds"))))throw new IOException("observed normal drawer root required");
            if (canvas) NavigationInput.editor(surface);
            boolean phoneBinding = !scroll && window.has("verified_binding") && "primary".equals(screen)
                && "com.qinggan.bluetoothphone".equals(text(window, "package"))
                && Set.of("com.qinggan.bluetoothphone:id/answer_iv", "com.qinggan.bluetoothphone:id/mini_answer",
                    "com.qinggan.bluetoothphone:id/hangup_calling_single_iv", "com.qinggan.bluetoothphone:id/calling_callout_calloff_tv")
                    .contains(text(selected, "resource_id"));
            if (phoneBinding) requirePhoneBinding(observation, surface, window, selected);
            if (!phoneBinding && (!yes(surface, "complete") || !"ok".equals(text(surface, "status")) || (!scroll && !drag && !page && !canvas && !drawer && !drawerClose && !yes(selected, "actionable_candidate"))))
                throw new IOException("UI input coverage is incomplete or unsupported for this target");
            JsonObject expected = new JsonObject();
            for (String key : List.of("boot_id", "source_instance", "revision")) {
                String value = text(observation, key);
                if (value.isBlank() || value.length() > 256) throw new IOException("UI source identity unavailable");
                expected.addProperty(key, value);
            }
            JsonObject windowProof = project(window, List.of("id", "type", "layer", "bounds", "title", "package",
                "read_complete", "root_id", "node_count"));
            if (phoneBinding) windowProof.add("verified_binding", window.get("verified_binding").deepCopy());
            else {
                windowProof.remove("read_complete"); windowProof.remove("root_id"); windowProof.remove("node_count");
            }
            for (String key : List.of("id", "type", "bounds", "title", "package"))
                if (!windowProof.has(key)) throw new IOException("UI window identity incomplete");
            JsonObject targetProof = project(selected, List.of("window_id", "resource_id", "package", "class", "text", "bounds", "clipped_bounds"));
            if (phoneBinding && selected.has("id")) targetProof.add("id", selected.get("id").deepCopy());
            for (String key : List.of("window_id", "resource_id", "package", "class", "bounds", "clipped_bounds"))
                if (!targetProof.has(key)) throw new IOException("UI target identity incomplete");
            expected.add("window", windowProof); expected.add("target", targetProof);
            JsonObject bounds = selected.getAsJsonObject("clipped_bounds");
            int left = integer(bounds, "x"), top = integer(bounds, "y"), width = integer(bounds, "width"), height = integer(bounds, "height");
            if (left < 0 || top < 0 || width < 1 || height < 1 || (long) left + width > integer(surface, "width")
                    || (long) top + height > integer(surface, "height")) throw new IOException("UI target geometry is outside the screen");
            JsonObject selection = null;
            if (phoneBinding) {
                selection = new JsonObject(); selection.addProperty("resource_id", text(selected, "resource_id"));
                selection.addProperty("package", text(selected, "package"));
            }
            UiTarget result = new UiTarget(profile, session.id(), screen, expected, selection, left + width / 2, top + height / 2,
                "all_attached".equals(text(surface, "scope")) ? "all_attached" : null);
            result.action = action; result.viewportWidth=integer(surface,"width"); return result;
        } catch (RuntimeException malformed) { throw new IOException("malformed UI observation", malformed); }
    }

    private static void requirePhoneBinding(JsonObject observation, JsonObject surface, JsonObject window, JsonObject selected) throws IOException {
        if (integer(window, "type") != 2010 || text(selected, "id").isBlank() || !yes(window, "read_complete"))
            throw new IOException("Phone target binding incomplete");
        JsonObject binding = window.getAsJsonObject("verified_binding");
        if (integer(binding, "schema") != 1 || !"api28_wms_process_viewdump_v1".equals(text(binding, "basis"))
                || !text(observation, "boot_id").equals(text(binding, "boot_id"))
                || !text(observation, "source_instance").equals(text(binding, "observer_epoch"))
                || !text(surface, "display_epoch").equals(text(binding, "display_epoch"))
                || integer(surface, "display_id") != 0 || integer(binding, "display_id") != 0
                || !window.get("id").equals(binding.get("window_id"))
                || !text(window, "package").equals(text(binding, "package"))
                || !text(window, "package").equals(text(binding, "process_name"))
                || integer(binding, "session_uid") != 1000 || integer(binding, "owner_uid") != 1000
                || integer(binding, "session_pid") < 1 || text(binding, "session_id").isBlank()
                || text(binding, "window_token").isBlank() || text(binding, "window_client").isBlank()
                || text(binding, "root_id").isBlank() || !text(binding, "root_id").equals(text(window, "root_id"))
                || integer(binding, "node_count") < 1 || integer(binding, "node_count") > 12000
                || integer(window, "node_count") != integer(binding, "node_count"))
            throw new IOException("Phone target binding scope differs");
        for (String key : List.of("read_complete", "process_verified", "current_focus", "touchable", "focusable", "visible", "has_surface", "drawn", "view_visible", "wm_order_verified"))
            if (!yes(binding, key)) throw new IOException("Phone target binding is not actionable");
        if (!binding.has("obscured") || !binding.get("obscured").isJsonPrimitive()
                || !binding.getAsJsonPrimitive("obscured").isBoolean() || binding.get("obscured").getAsBoolean())
            throw new IOException("Phone target occlusion is unknown");
        int count = 0, roots = 0;
        for (JsonElement element : surface.getAsJsonArray("nodes")) {
            JsonObject node = element.getAsJsonObject();
            if (!window.get("id").equals(node.get("window_id"))) continue;
            ++count;
            if (text(binding, "root_id").equals(text(node, "id")) && node.has("parent") && node.get("parent").isJsonNull()
                    && text(window, "package").equals(text(node, "package"))) ++roots;
        }
        if (count != integer(binding, "node_count") || roots != 1) throw new IOException("Phone window tree incomplete");
    }

    void requireScope(String profile, VoyemuClient.Session session) {
        if (!this.profile.equals(profile) || !this.session.equals(session.id())) throw new IllegalArgumentException("UI target belongs to another session");
    }
    static boolean validScroll(JsonObject target,JsonObject window) {
        String owner=text(target,"package");
        if(!owner.equals(text(window,"package"))||!"android.widget.ScrollView".equals(text(target,"class"))||!window.has("type")||!"1".equals(window.get("type").getAsString()))return false;
        if(owner.equals("com.qinggan.app.setting"))return true;
        return owner.equals(VehicleUi.OWNER)&&Set.of(owner+":id/menu_container",owner+":id/fragment_drive_preference_scroll",owner+":id/fragment_scrollview").contains(text(target,"resource_id"));
    }
    void requireDrawerClose(){if(action!=Action.DRAWER_CLOSE||closePoints==null)throw new IllegalArgumentException("drawer close target differs");}
    com.google.gson.JsonArray closePoints(){requireDrawerClose();return closePoints.deepCopy();}
    void requireDrawer(){if(action!=Action.DRAWER_HANDLE)throw new IllegalArgumentException("drawer target action differs");}
    int viewportWidth(){requireDrawer();return viewportWidth;}
    void requireAction(boolean scroll) { if(action!=(scroll?Action.SETTINGS_SCROLL:Action.TAP)) throw new IllegalArgumentException("UI target action differs"); }
    void requireDrag() { if(action!=Action.LAUNCHER_DRAG) throw new IllegalArgumentException("Launcher drag target required"); }
    void requirePage() { if(action!=Action.LAUNCHER_PAGE) throw new IllegalArgumentException("Launcher page target required"); }
    void requireCanvas() { if(action!=Action.KEYBOARD_CANVAS) throw new IllegalArgumentException("keyboard canvas target required"); }
    String screen() { return screen; }
    int x() { return x; }
    int y() { return y; }
    JsonObject expected() { return expected.deepCopy(); }
    JsonObject selection() { return selection == null ? null : selection.deepCopy(); }
    private static JsonObject project(JsonObject value, List<String> keys) {
        JsonObject result = new JsonObject();
        for (String key : keys) if (value.has(key)) result.add(key, value.get(key).deepCopy());
        return result;
    }
    private static String text(JsonObject value, String key) {
        return value.has(key) && value.get(key).isJsonPrimitive() && value.getAsJsonPrimitive(key).isString()
            ? value.get(key).getAsString() : "";
    }
    private static boolean yes(JsonObject value, String key) {
        return value.has(key) && value.get(key).isJsonPrimitive() && value.getAsJsonPrimitive(key).isBoolean() && value.get(key).getAsBoolean();
    }
    private static int integer(JsonObject value, String key) throws IOException {
        if (!value.has(key) || !value.get(key).isJsonPrimitive() || !value.getAsJsonPrimitive(key).isNumber()
                || !value.get(key).getAsString().matches("-?[0-9]{1,9}")) throw new IOException("UI integer geometry required");
        return value.get(key).getAsInt();
    }
}
