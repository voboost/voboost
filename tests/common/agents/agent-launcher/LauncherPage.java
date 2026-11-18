package ru.voboost.tests.agents;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import ru.voboost.tests.RunFiles;
import ru.voboost.tests.installer.cli.StartupObservation;

/** A nonce-correlated root launcher page, independently tied to the current live target. */
public final class LauncherPage {
    public static final String OWNER = "com.qinggan.app.launcher";
    public record Identity(String boot, String daemonPid, String daemonStarttime, String pid, String starttime,
                           String epoch, String generation, String catalog, String document) {}
    public record Item(int position, String label, List<Integer> bounds) { public Item { bounds = List.copyOf(bounds); } }
    private final JsonObject receipt, observation;
    private final List<Item> items;
    private LauncherPage(JsonObject receipt, JsonObject observation, List<Item> items) { this.receipt = receipt; this.observation = observation; this.items = List.copyOf(items); }
    private static IOException invalid(String reason) { return new IOException("launcher page unavailable: " + reason); }
    private static String text(JsonObject value, String field) throws IOException {
        if (!value.has(field) || !value.get(field).isJsonPrimitive() || !value.getAsJsonPrimitive(field).isString()) throw invalid("text " + field);
        String result = value.get(field).getAsString();
        if (result.isEmpty() || result.length() > 256 || result.codePoints().anyMatch(Character::isISOControl)) throw invalid("bounded text " + field); return result;
    }
    private static BigInteger decimal(JsonObject value, String field, boolean string, boolean positive) throws IOException {
        JsonElement item = value.get(field);
        if (item == null || !item.isJsonPrimitive() || string && !item.getAsJsonPrimitive().isString() || !string && !item.getAsJsonPrimitive().isNumber()
                || !item.getAsString().matches(positive ? "[1-9][0-9]{0,19}" : "0|[1-9][0-9]{0,19}")) throw invalid("integer " + field);
        BigInteger number = new BigInteger(item.getAsString()); if (number.bitLength() > 64) throw invalid("u64 " + field); return number;
    }
    private static int number(JsonObject value, String field, int maximum) throws IOException {
        BigInteger result = decimal(value, field, false, false); if (result.compareTo(BigInteger.valueOf(maximum)) > 0) throw invalid("bound " + field); return result.intValue();
    }
    private static boolean flag(JsonObject value, String field, boolean expected) { return value.has(field) && value.get(field).isJsonPrimitive() && value.getAsJsonPrimitive(field).isBoolean() && value.get(field).getAsBoolean() == expected; }
    private static JsonObject object(JsonObject value, String field) throws IOException { if (!value.has(field) || !value.get(field).isJsonObject()) throw invalid("object " + field); return value.getAsJsonObject(field); }
    private static void fields(JsonObject value, String... fields) throws IOException { if (!value.keySet().equals(Set.of(fields))) throw invalid("closed fields"); }
    private static List<Integer> bounds(JsonObject value, String field) throws IOException {
        if (!value.has(field) || !value.get(field).isJsonArray() || value.getAsJsonArray(field).size() != 4) throw invalid("bounds");
        List<Integer> result = new ArrayList<>();
        for (JsonElement item : value.getAsJsonArray(field)) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isNumber() || !item.getAsString().matches("-?[0-9]{1,5}")) throw invalid("integer bounds");
            int number = item.getAsInt(); if (Math.abs(number) > 16384) throw invalid("bounds range"); result.add(number);
        }
        if (result.get(2) <= result.get(0) || result.get(3) <= result.get(1)) throw invalid("empty bounds"); return List.copyOf(result);
    }
    public static Identity identity(RuntimeObservation runtime, StartupObservation.ProcessIdentity process) throws IOException {
        String[] publisher = runtime.publisher().split(":", -1);
        List<JsonObject> targets = runtime.targets("launcher").stream().filter(row -> OWNER.equals(row.get("process").getAsString()) && "active".equals(row.get("state").getAsString())).toList();
        if (publisher.length != 3 || targets.size() != 1 || !runtime.desired("launcher")) throw invalid("current launcher runtime identity");
        JsonObject endpoint = object(targets.get(0), "currentEndpoint");
        if (!Integer.toString(process.pid()).equals(text(endpoint, "targetPid")) || !process.starttime().equals(text(endpoint, "targetStarttime"))) throw invalid("independent target process differs");
        return new Identity(publisher[0], publisher[1], publisher[2], text(endpoint, "targetPid"), text(endpoint, "targetStarttime"),
            text(endpoint, "endpointEpoch"), text(endpoint, "generationId"), text(endpoint, "catalogSha256"), text(endpoint, "desiredDocumentGeneration"));
    }
    public static LauncherPage read(String raw, String nonce, Identity identity) throws IOException {
        if (raw == null || raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 16384 || nonce == null || !nonce.matches("[0-9a-f]{64}")) throw invalid("bounded nonce receipt");
        try {
            JsonObject value = RunFiles.parse(raw);
            Set<String> observed = Set.of("schemaVersion", "requestId", "bootId", "daemonPid", "daemonStarttime", "targetPid", "targetStarttime", "endpointEpoch", "generationId", "catalogSha256", "documentGeneration", "captureStartedBootMs", "captureFinishedBootMs", "outcome", "reason", "observation");
            if (!value.keySet().equals(observed) || number(value, "schemaVersion", 1) != 1 || !nonce.equals(text(value, "requestId"))
                    || !identity.boot().equals(text(value, "bootId")) || !"observed".equals(text(value, "outcome")) || !"observed".equals(text(value, "reason"))) throw invalid("receipt scope or unavailable outcome");
            for (var pair : java.util.Map.of("daemonPid", identity.daemonPid(), "daemonStarttime", identity.daemonStarttime(), "targetPid", identity.pid(), "targetStarttime", identity.starttime(), "endpointEpoch", identity.epoch()).entrySet())
                if (!decimal(value, pair.getKey(), true, true).toString().equals(pair.getValue())) throw invalid("live owner differs");
            if (!identity.generation().equals(text(value, "generationId")) || !identity.catalog().equals(text(value, "catalogSha256"))
                    || !identity.document().equals(decimal(value, "documentGeneration", false, true).toString())) throw invalid("current authority differs");
            BigInteger started = decimal(value, "captureStartedBootMs", false, false), finished = decimal(value, "captureFinishedBootMs", false, false);
            if (finished.compareTo(started) < 0 || finished.subtract(started).compareTo(BigInteger.valueOf(10000)) > 0) throw invalid("capture interval");
            JsonObject observation = object(value, "observation");
            fields(observation, "outerCurrentItem", "firstPosition", "pageCount", "offsetX", "itemCount", "scrollState", "computingLayout", "window", "grid", "items");
            int count = number(observation, "itemCount", 4096), first = number(observation, "firstPosition", 4096), pages = number(observation, "pageCount", 342);
            if (count < 1 || first >= count || first % 12 != 0 || pages != (count - 1) / 12 + 1 || number(observation, "outerCurrentItem", 1) != 1
                    || number(observation, "scrollState", 0) != 0 || !flag(observation, "computingLayout", false)) throw invalid("settled page metrics");
            number(observation, "offsetX", 100000);
            JsonObject window = object(observation, "window"), grid = object(observation, "grid");
            fields(window, "activityClass", "windowType", "displayId", "focused", "attached", "shown", "token", "bounds");
            fields(grid, "resource", "className", "layoutManagerClass", "bounds");
            if (!(OWNER + ".activity.MainActivity").equals(text(window, "activityClass")) || number(window, "windowType", 1) != 1 || number(window, "displayId", 0) != 0
                    || !flag(window, "focused", true) || !flag(window, "attached", true) || !flag(window, "shown", true) || text(window, "token").isBlank()
                    || !(OWNER + ":id/all_app_view").equals(text(grid, "resource")) || !"com.qinggan.launcher.base.allapp.AllAppBarView".equals(text(grid, "className"))
                    || !"com.qinggan.launcher.base.custom.PagerGridLayoutManager".equals(text(grid, "layoutManagerClass"))) throw invalid("actual OEM grid/window");
            bounds(window, "bounds"); bounds(grid, "bounds");
            if (!observation.has("items") || !observation.get("items").isJsonArray() || observation.getAsJsonArray("items").size() != Math.min(12, count - first)) throw invalid("complete current page items");
            List<Item> items = new ArrayList<>();
            for (JsonElement row : observation.getAsJsonArray("items")) {
                JsonObject item = row.getAsJsonObject(); fields(item, "position", "label", "bounds");
                int position = number(item, "position", 4095);
                if (position != first + items.size()) throw invalid("contiguous unique positions");
                items.add(new Item(position, text(item, "label"), bounds(item, "bounds")));
            }
            return new LauncherPage(value.deepCopy(), observation.deepCopy(), items);
        } catch (RuntimeException malformed) { throw new IOException("malformed launcher page receipt", malformed); }
    }
    public int first() { return observation.get("firstPosition").getAsInt(); }
    public int count() { return observation.get("itemCount").getAsInt(); }
    public int pages() { return observation.get("pageCount").getAsInt(); }
    public boolean beginning() { return first() == 0 && observation.get("offsetX").getAsInt() == 0; }
    public List<Item> items() { return items; }
    public JsonObject receipt() { return receipt.deepCopy(); }
    public JsonObject observation() { return observation.deepCopy(); }
    public void requireSameLifetime(LauncherPage before) throws IOException {
        for (String field : List.of("bootId", "daemonPid", "daemonStarttime", "targetPid", "targetStarttime", "endpointEpoch", "generationId", "catalogSha256", "documentGeneration"))
            if (!receipt.get(field).equals(before.receipt.get(field))) throw invalid("observation crossed process/authority lifetime");
        if (receipt.get("requestId").equals(before.receipt.get("requestId")) || decimal(receipt, "captureStartedBootMs", false, false).compareTo(decimal(before.receipt, "captureFinishedBootMs", false, false)) < 0
                || decimal(receipt,"captureFinishedBootMs",false,false).subtract(decimal(before.receipt,"captureStartedBootMs",false,false)).compareTo(BigInteger.valueOf(180000))>0)
            throw invalid("observation freshness");
    }
}
