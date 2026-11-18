package ru.voboost.tests.agents;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import ru.voboost.tests.logging.InstallerLogs;

/** Complete native OEM binding evidence, independent of screenshot review. */
public final class StockBindProvenance {
    public record Authority(String boot, String generation, String catalog, String pid, String starttime, String epoch) {}
    private static final String PROCESS = "com.qinggan.app.launcher";
    private static final List<String> ID = List.of("code_generation", "target_pid", "process", "launcher_generation", "screen_id", "contract");
    private static final List<String> BEAN = List.of("bean_id", "token", "package_name", "icon_resource", "icon_resource_name", "registration");
    private static final List<String> TARGET = List.of("item_view", "icon_view", "name_view", "item_tag", "icon_tag", "drawable_id", "drawable_class", "geometry", "state", "label", "drawable_state");
    private static final List<String> COMPLETE = List.of("boundary_unchanged", "token_unchanged", "resource_unchanged", "original_completed", "tag_matches_bean", "theme_matches_background");
    private static IOException bad(String reason) { return new IOException("stock binding unavailable: " + reason); }
    private static void check(boolean condition, String reason) throws IOException { if (!condition) throw bad(reason); }
    private static List<String> fields(String first, List<String> rest) { var result = new ArrayList<String>(); result.add(first); result.addAll(rest); return result; }
    private static List<String> fields(String first, String second, List<String> rest) { var result = new ArrayList<String>(); result.add(first); result.add(second); result.addAll(rest); return result; }
    private static void exact(JsonObject value, List<String> keys) throws IOException { check(value != null && value.keySet().equals(new HashSet<>(keys)), "field set"); }
    private static String text(JsonObject value, String key) throws IOException {
        JsonElement e = value.get(key); check(e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() && e.getAsString().length() <= 256, key); return e.getAsString();
    }
    private static String nonempty(JsonObject value, String key) throws IOException { String s = text(value, key); check(!s.isEmpty(), key); return s; }
    private static int number(JsonObject value, String key) throws IOException {
        JsonElement e = value.get(key); check(e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() && e.getAsString().matches("0|[1-9][0-9]{0,9}"), key);
        try { return Integer.parseInt(e.getAsString()); } catch (NumberFormatException ex) { throw bad(key); }
    }
    private static boolean bool(JsonObject value, String key) throws IOException { JsonElement e = value.get(key); check(e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean(), key); return e.getAsBoolean(); }
    private static void equal(JsonObject a, JsonObject b, List<String> keys) throws IOException { for (String k : keys) check(a.has(k) && a.get(k).equals(b.get(k)), k + " differs"); }
    private static InstallerLogs.Diagnostic one(List<InstallerLogs.Diagnostic> rows, String event, String outcome) throws IOException {
        var matches = rows.stream().filter(r -> event.equals(r.event()) && outcome.equals(r.outcome())).toList(); check(matches.size() == 1, event + " cardinality"); return matches.get(0);
    }
    private static String source(JsonObject source, String key) throws IOException { return nonempty(source, key); }
    public static List<String> read(InstallerLogs.Snapshot before, InstallerLogs.Snapshot current, Authority a) throws IOException {
        check(a != null && a.boot() != null && a.boot().matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}") && a.boot().equals(before.boot()) && a.boot().equals(current.boot()), "boot");
        check(a.generation() != null && a.generation().matches("[0-9a-f]{64}") && a.catalog() != null && a.catalog().matches("[0-9a-f]{64}") && a.pid() != null && a.pid().matches("[1-9][0-9]*") && a.starttime() != null && a.starttime().matches("[1-9][0-9]*") && a.epoch() != null && a.epoch().matches("[1-9][0-9]*"), "authority");
        List<InstallerLogs.Diagnostic> fresh = current.after(before); var stock = new ArrayList<InstallerLogs.Diagnostic>();
        for (var row : fresh) if ("launcher-apps".equals(row.agent()) && row.event().startsWith("stock.bind.")) {
            JsonObject s = row.sourceFields(); check(PROCESS.equals(source(s,"process")) && a.boot().equals(source(s,"bootId")) && a.pid().equals(source(s,"targetPid")) && a.starttime().equals(source(s,"targetStarttime")) && a.epoch().equals(source(s,"endpointEpoch")), "diagnostic source"); stock.add(row);
        }
        var result = new ArrayList<String>();
        for (int screen = 0; screen < 2; screen++) {
            InstallerLogs.Diagnostic inventory = null;
            for (var row : stock) if ("stock.bind.inventory".equals(row.event()) && "observed".equals(row.outcome()) && number(row.fields(), "screen_id") == screen) inventory = row;
            check(inventory != null, "screen inventory missing"); JsonObject f = inventory.fields();
            var inventoryKeys = fields("inventory_id", ID); inventoryKeys.add("count"); exact(f, inventoryKeys);
            String inventoryId = nonempty(f,"inventory_id"); int count = number(f,"count");
            check(a.generation().equals(nonempty(f,"code_generation")) && a.pid().equals(Integer.toString(number(f,"target_pid"))) && PROCESS.equals(nonempty(f,"process")) && number(f,"launcher_generation") > 0 && Set.of("qinggan-base-v1","qinggan-nxp-v1").contains(nonempty(f,"contract")) && count > 0 && count <= 64, "inventory identity");
            for (var row : stock) if ("stock.bind.inventory".equals(row.event()) && "invalidated".equals(row.outcome()) && row.fields().has("inventory_id") && inventoryId.equals(text(row.fields(),"inventory_id")) && row.sequence().compareTo(inventory.sequence()) > 0) throw bad("inventory invalidated");
            BigInteger registration = null;
            for (var event : current.daemonEvents()) if ("runtime".equals(event.component()) && "desired-snapshot-queued".equals(event.message()) && event.sequence().compareTo(inventory.sequence()) < 0) {
                JsonObject q = event.fields();
                if (q.has("process") && q.has("generationId") && q.has("catalogSha256") && q.has("targetPid") && q.has("targetStarttime") && PROCESS.equals(text(q,"process")) && a.generation().equals(text(q,"generationId")) && a.catalog().equals(text(q,"catalogSha256")) && a.pid().equals(text(q,"targetPid")) && a.starttime().equals(text(q,"targetStarttime"))) registration = event.sequence();
            }
            check(registration != null, "queued registration");
            var items = new ArrayList<InstallerLogs.Diagnostic>();
            for (var row : stock) if ("stock.bind.inventory.item".equals(row.event()) && row.fields().has("inventory_id") && inventoryId.equals(text(row.fields(),"inventory_id"))) items.add(row);
            check(items.size() == count, "inventory count"); items.sort(java.util.Comparator.comparing(r -> r.fields().get("index").getAsInt()));
            Set<String> tokens = new HashSet<>(), beans = new HashSet<>();
            for (int i = 0; i < items.size(); i++) {
                var item = items.get(i); JsonObject v = item.fields(); var itemKeys = fields("inventory_id", "index", BEAN.subList(0,5)); exact(v,itemKeys);
                check("observed".equals(item.outcome()) && number(v,"index") == i && item.sequence().compareTo(registration) > 0 && item.sequence().compareTo(inventory.sequence()) < 0 && tokens.add(nonempty(v,"token")) && beans.add(nonempty(v,"bean_id")) && number(v,"icon_resource") > 0, "inventory item"); text(v,"package_name"); nonempty(v,"icon_resource_name");
                boolean admitted = false;
                for (var bind : stock) {
                    if (!"stock.bind.identity".equals(bind.event()) || !bind.fields().has("inventory_id") || !inventoryId.equals(text(bind.fields(),"inventory_id")) || bind.sequence().compareTo(inventory.sequence()) <= 0) continue;
                    JsonObject b = bind.fields(); exact(b,fields("bind_id","inventory_id",ID)); check("observed".equals(bind.outcome()),"bind outcome"); equal(b,f,ID); String bindId = nonempty(b,"bind_id");
                    var parts = new ArrayList<InstallerLogs.Diagnostic>(); for (var row : stock) if (row.fields().has("bind_id") && bindId.equals(text(row.fields(),"bind_id"))) parts.add(row);
                    var possible = parts.stream().filter(r -> "stock.bind.bean".equals(r.event()) && "before".equals(r.outcome()) && r.fields().has("bean_id") && r.fields().get("bean_id").equals(v.get("bean_id"))).toList(); if (possible.isEmpty()) continue;
                    var b0=one(parts,"stock.bind.bean","before"); var b1=one(parts,"stock.bind.bean","boundary"); var b2=one(parts,"stock.bind.bean","after");
                    var t0=one(parts,"stock.bind.target","before"); var t1=one(parts,"stock.bind.target","boundary"); var t2=one(parts,"stock.bind.target","after"); var done=one(parts,"stock.bind.complete","completed");
                    for (var bean : List.of(b0,b1,b2)) { exact(bean.fields(),fields("bind_id",BEAN)); check("oem-unregistered".equals(text(bean.fields(),"registration")),"registered bean"); equal(bean.fields(),v,BEAN.subList(0,5)); }
                    for (var target : List.of(t0,t1,t2)) { exact(target.fields(),fields("bind_id",TARGET)); for (String key : TARGET) if ("label".equals(key)) text(target.fields(),key); else nonempty(target.fields(),key); }
                    check(b0.fields().equals(b1.fields()) && t0.fields().equals(t1.fields()),"original boundary changed");
                    var completeKeys=fields("bind_id",COMPLETE); completeKeys.add("managed_drawable"); exact(done.fields(),completeKeys); for (String key : COMPLETE) check(bool(done.fields(),key),key); check(!bool(done.fields(),"managed_drawable"),"managed drawable");
                    boolean themeMatched=false; var order=new ArrayList<InstallerLogs.Diagnostic>(List.of(bind,b0,t0,b1,t1));
                    for (var theme : parts) if ("stock.bind.theme".equals(theme.event()) && "returned".equals(theme.outcome())) { exact(theme.fields(),List.of("bind_id","icon_resource","drawable_id","drawable_class")); order.add(theme); if (theme.fields().get("icon_resource").equals(v.get("icon_resource")) && !"null".equals(nonempty(theme.fields(),"drawable_id")) && theme.fields().get("drawable_id").equals(t2.fields().get("drawable_id")) && theme.fields().get("drawable_class").equals(t2.fields().get("drawable_class"))) themeMatched=true; }
                    check(themeMatched && v.get("bean_id").equals(t2.fields().get("item_tag")),"native drawable binding"); order.addAll(List.of(b2,t2,done)); for(int n=1;n<order.size();n++) check(order.get(n).sequence().compareTo(order.get(n-1).sequence())>0,"bind order"); admitted=true; break;
                }
                check(admitted,"stock item bind missing"); result.add(screen + ":" + text(v,"token"));
            }
        }
        return List.copyOf(result);
    }
    private StockBindProvenance() {}
}
