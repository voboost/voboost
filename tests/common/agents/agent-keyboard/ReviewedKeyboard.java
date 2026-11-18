package ru.voboost.voyemu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import ru.voboost.tests.RunFiles;

/** Historical reviewed pixels authorize only a matching current canvas key, never an input result. */
public final class ReviewedKeyboard {
    private static final int WIDTH = 1920, HEIGHT = 352;
    private record Key(String label, int left, int top, int right, int bottom, int x, int y) {}
    private record Layout(String png, List<Key> keys) {}
    public static final class Match {
        private final String label, baseline, next;
        private final int x, y;
        private final JsonObject scope;
        private String revision;
        private long expires;
        private boolean used;
        private Match(String label, String baseline, String next, int x, int y, JsonObject scope) {
            this.label = label; this.baseline = baseline; this.next = next; this.x = x; this.y = y;
            this.scope = scope.deepCopy();
        }
        public String label() { return label; }
        public String baseline() { return baseline; }
        public String nextBaseline() { return next; }
        int x() { return x; }
        int y() { return y; }
        public JsonObject scope() { return scope.deepCopy(); }
        /** Consume immediately before dispatch; an uncertain dispatch must never reuse this token. */
        public synchronized Point claim(UiTarget target, String profile, VoyemuClient.Session session,
                long androidPid) throws IOException {
            long now = System.nanoTime();
            if (used || expires == 0 || now > expires || now < expires - 2_000_000_000L)
                throw invalid("key receipt expired or already consumed");
            target.requireCanvas(); target.requireScope(profile, session);
            JsonObject expected = target.expected();
            if (!text(expected, "revision").equals(revision) || !session.id().equals(text(scope, "session")) || !scope.get("androidPid").getAsString().equals(Long.toString(androidPid))
                    || !scope.get("boot").equals(expected.get("boot_id")) || !scope.get("source").equals(expected.get("source_instance"))
                    || !scope.get("window").equals(expected.get("window")) || !scope.get("target").equals(expected.get("target")))
                throw invalid("key receipt and fresh canvas differ");
            used = true;
            return new Point(x, y);
        }
    }
    public record Point(int x, int y) {}
    private ReviewedKeyboard() {}
    private static IOException invalid(String reason) { return new IOException("reviewed keyboard unavailable: " + reason); }
    private static String text(JsonObject object, String key) throws IOException {
        if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isString()) throw invalid("text " + key);
        return object.get(key).getAsString();
    }
    private static int integer(JsonElement value) throws IOException {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("0|[1-9][0-9]{0,3}")) throw invalid("bounded pixel integer");
        return value.getAsInt();
    }
    private static byte[] bytes(Path path, int limit) throws IOException {
        if (!path.isAbsolute() || !path.equals(path.toRealPath()) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.size(path) > limit) throw invalid("reviewed ordinary bounded file");
        byte[] data = Files.readAllBytes(path);
        if (data.length > limit) throw invalid("reviewed file grew");
        return data;
    }
    private static List<Layout> load(Path directory) throws IOException {
        try {
            byte[] map = bytes(directory.resolve("reviewed-keyboard-maps.json"), 128 * 1024);
            JsonObject root = RunFiles.parse(new String(map, java.nio.charset.StandardCharsets.UTF_8));
            if (integer(root.get("schema")) != 1 || !"Classic".equals(text(root, "theme"))
                    || root.getAsJsonArray("layouts").size() != 8) throw invalid("reviewed map version");
            List<Layout> result = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("layouts")) {
                JsonObject item = element.getAsJsonObject();
                String filename = text(item, "baselinePng");
                if (!filename.matches("(?:en|ru)-(?:only-)?(?:first|lower|upper-lock)\\.png")
                        || integer(item.get("width")) != WIDTH || integer(item.get("height")) != HEIGHT
                        || !item.get("bounds").toString().equals("[0,368,1920,720]")) throw invalid("reviewed image geometry");
                List<Key> keys = new ArrayList<>();
                for (JsonElement entry : item.getAsJsonArray("keys")) {
                    JsonObject key = entry.getAsJsonObject(); String label = text(key, "label");
                    JsonArray r = key.getAsJsonArray("region"), c = key.getAsJsonArray("center");
                    if (r.size() != 4 || c.size() != 2) throw invalid("reviewed key geometry");
                    int left = integer(r.get(0)), top = integer(r.get(1)), right = integer(r.get(2)), bottom = integer(r.get(3));
                    int x = integer(c.get(0)), y = integer(c.get(1));
                    if (left >= right || top >= bottom || right > WIDTH || bottom > HEIGHT || x < left || x >= right || y < top || y >= bottom)
                        throw invalid("reviewed key bounds");
                    keys.add(new Key(label, left, top, right, bottom, x, y));
                }
                if (keys.size() < 26 || keys.size() > 40 || keys.stream().map(Key::label).distinct().count() != keys.size()) throw invalid("reviewed key inventory");
                result.add(new Layout(filename, List.copyOf(keys)));
            }
            return List.copyOf(result);
        } catch (RuntimeException malformed) { throw new IOException("malformed reviewed keyboard map", malformed); }
    }
    private static boolean sameRegions(PixelImage actual, PixelImage expected, List<Key> keys) {
        if (actual.width() != WIDTH || actual.height() != HEIGHT) return false;
        for (Key key : keys) for (int y = key.top; y < key.bottom; y++) for (int x = key.left; x < key.right; x++)
            if (actual.pixel(x, y) != expected.pixel(x, y)) return false;
        return true;
    }
    /** Two independently fenced captures, followed by a fresh current canvas target, are mandatory. */
    public static Match match(Path directory, KeyboardCapture.Frame first, KeyboardCapture.Frame second,
            UiTarget current, String profile, VoyemuClient.Session session, long androidPid, String label) throws IOException {
        JsonObject scope = requireCurrent(first, second, current, profile, session, androidPid);
        Match match = compare(directory, first.image(), second.image(), label, scope);
        match.revision = text(current.expected(), "revision");
        match.expires = first.started() + 2_000_000_000L;
        if (System.nanoTime() > match.expires) throw invalid("captures expired during pixel comparison");
        return match;
    }

    /** Classifies a current layout without creating a touch token. */
    public static String layout(Path directory, KeyboardCapture.Frame first, KeyboardCapture.Frame second,
            UiTarget current, String profile, VoyemuClient.Session session, long androidPid) throws IOException {
        requireCurrent(first, second, current, profile, session, androidPid);
        List<Layout> layouts = matchingLayouts(directory, first.image(), second.image());
        if (layouts.size() != 1 || System.nanoTime() > first.started() + 2_000_000_000L)
            throw invalid("current pixels do not uniquely match a fresh reviewed layout");
        return layouts.get(0).png;
    }

    private static JsonObject requireCurrent(KeyboardCapture.Frame first, KeyboardCapture.Frame second,
            UiTarget current, String profile, VoyemuClient.Session session, long androidPid) throws IOException {
        long now = System.nanoTime();
        second.requireSameEpoch(first); current.requireCanvas(); current.requireScope(profile, session);
        if (second.started() <= first.started() || now < second.started() || now - second.started() > 2_000_000_000L
                || now - first.started() > 2_000_000_000L || first.png().equals(second.png())) throw invalid("two fresh captures required");
        JsonObject scope = second.scope(), expected = current.expected();
        JsonObject rect = scope.getAsJsonObject("rect");
        if (integer(rect.get("x")) != 0 || integer(rect.get("y")) != 368
                || integer(rect.get("width")) != WIDTH || integer(rect.get("height")) != HEIGHT
                || first.scope().get("revision").equals(scope.get("revision"))
                || scope.get("revision").equals(expected.get("revision"))
                || first.scope().get("revision").equals(expected.get("revision"))
                || !session.id().equals(text(scope, "session")) || androidPid < 1
                || !scope.get("androidPid").getAsString().equals(Long.toString(androidPid))
                || !scope.get("boot").equals(expected.get("boot_id")) || !scope.get("source").equals(expected.get("source_instance"))
                || !scope.get("window").equals(expected.get("window")) || !scope.get("target").equals(expected.get("target")))
            throw invalid("fresh canvas scope or historical crop differs");
        return scope;
    }
    /** Lookup reconstruction validates a closed keyboard gesture; it never reissues it. */
    static void validateGesture(JsonObject request) throws IOException {
        try {
            Set<String> keys = request.has("scope")
                    ? Set.of("schema", "request_id", "op", "profile", "session", "screen", "scope", "expected", "points")
                    : Set.of("schema", "request_id", "op", "profile", "session", "screen", "expected", "points");
            JsonObject expected = request.getAsJsonObject("expected"), target = expected.getAsJsonObject("target");
            JsonObject window = expected.getAsJsonObject("window"), rect = target.getAsJsonObject("clipped_bounds");
            String owner = text(target, "package");
            if (!request.keySet().equals(keys) || !"input.gesture".equals(text(request, "op"))
                    || !"primary".equals(text(request, "screen"))
                    || !expected.keySet().equals(Set.of("boot_id", "source_instance", "revision", "window", "target"))
                    || text(expected, "boot_id").isBlank() || text(expected, "source_instance").isBlank()
                    || text(expected, "revision").isBlank() || text(target, "class").isBlank()
                    || !target.get("window_id").equals(window.get("id"))
                    || request.has("scope") && !"all_attached".equals(text(request, "scope"))
                    || !owner.matches("com\\.qinggan\\.app\\.qgime(?:\\.second)?")
                    || !(owner + ":id/alpha_view1").equals(text(target, "resource_id"))
                    || !owner.equals(text(window, "package")) || integer(window.get("type")) != 2
                    || !target.get("bounds").equals(target.get("clipped_bounds"))
                    || integer(rect.get("x")) != 0 || integer(rect.get("y")) != 368
                    || integer(rect.get("width")) != WIDTH || integer(rect.get("height")) != HEIGHT)
                throw invalid("gesture target differs");
            JsonArray points = request.getAsJsonArray("points");
            if (points.size() != 2) throw invalid("one key gesture required");
            JsonObject down = points.get(0).getAsJsonObject(), up = points.get(1).getAsJsonObject();
            if (!down.keySet().equals(Set.of("x", "y", "at_ms")) || !up.keySet().equals(down.keySet())
                    || integer(down.get("at_ms")) != 0 || integer(up.get("at_ms")) != 80
                    || !down.get("x").equals(up.get("x")) || !down.get("y").equals(up.get("y"))
                    || integer(down.get("x")) >= WIDTH || integer(down.get("y")) < 368
                    || integer(down.get("y")) >= 720) throw invalid("key gesture coordinates differ");
        } catch (RuntimeException malformed) { throw new IOException("malformed reviewed keyboard gesture", malformed); }
    }
    private static List<Layout> matchingLayouts(Path directory, PixelImage first, PixelImage second) throws IOException {
        List<Layout> matching = new ArrayList<>();
        for (Layout layout : load(directory)) {
            PixelImage reference = PixelImage.read(directory.resolve(layout.png), WIDTH, HEIGHT);
            if (sameRegions(first, reference, layout.keys) && sameRegions(second, reference, layout.keys))
                matching.add(layout);
        }
        return matching;
    }

    static Match compare(Path directory, PixelImage first, PixelImage second, String label, JsonObject scope) throws IOException {
        if (label == null || !(label.matches("[A-Za-zА-Яа-яЁё]") || Set.of("switch", "shift").contains(label))) throw invalid("closed key required");
        Match result = null;
        for (Layout layout : matchingLayouts(directory, first, second)) {
            List<Key> selected = layout.keys.stream().filter(key -> key.label.equals(label)).toList();
            if (selected.size() != 1) continue;
            String next = switch (layout.png + ":" + label) {
                case "en-first.png:shift" -> "en-upper-lock.png";
                case "en-upper-lock.png:shift" -> "en-lower.png";
                case "en-lower.png:switch" -> "ru-lower.png";
                case "ru-lower.png:shift" -> "ru-first.png";
                case "ru-lower.png:switch" -> "en-lower.png";
                case "en-only-first.png:shift" -> "en-only-upper-lock.png";
                case "en-only-upper-lock.png:shift" -> "en-only-lower.png";
                case "en-only-lower.png:switch" -> "en-only-first.png";
                default -> "";
            };
            if (Set.of("switch", "shift").contains(label) && next.isEmpty()) continue;
            if (result != null) throw invalid("ambiguous reviewed layouts");
            Key key = selected.get(0);
            result = new Match(label, layout.png, next, key.x, 368 + key.y, scope);
        }
        if (result == null) throw invalid("current pixels do not uniquely match reviewed key/layout");
        return result;
    }
}
