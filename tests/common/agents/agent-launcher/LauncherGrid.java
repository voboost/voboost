package ru.voboost.tests.agents;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ru.voboost.voyemu.UiEvidence;

/** Cross-check native direct page metrics against the independently observed actual primary hierarchy. */
public record LauncherGrid(JsonObject grid, List<Item> items) {
    public record Item(int position, String label, JsonObject node) {
        public Item { node = node.deepCopy(); }
        @Override public JsonObject node() { return node.deepCopy(); }
    }
    public LauncherGrid { grid = grid.deepCopy(); items = List.copyOf(items); }
    @Override public JsonObject grid() { return grid.deepCopy(); }
    private static String text(JsonObject value,String field) { return value.has(field) && value.get(field).isJsonPrimitive() && value.getAsJsonPrimitive(field).isString()?value.get(field).getAsString():""; }
    private static boolean yes(JsonObject value,String field) { return value.has(field) && value.get(field).isJsonPrimitive() && value.getAsJsonPrimitive(field).isBoolean() && value.get(field).getAsBoolean(); }
    private static IOException invalid(String reason) { return new IOException("independent Launcher hierarchy unavailable: "+reason); }
    public static List<Integer> rectangle(JsonObject node) throws IOException {
        if(!node.has("bounds") || !node.get("bounds").isJsonObject()) throw invalid("bounds missing");
        JsonObject b=node.getAsJsonObject("bounds"); int[] values=new int[4]; int index=0;
        for(String field:List.of("x","y","width","height")) {
            if(!b.has(field) || !b.get(field).isJsonPrimitive() || !b.getAsJsonPrimitive(field).isNumber() || !b.get(field).getAsString().matches("-?[0-9]{1,5}")) throw invalid("integer bounds missing");
            values[index++]=b.get(field).getAsInt();
        }
        if(values[0]<0 || values[1]<0 || values[2]<=0 || values[3]<=0 || (long)values[0]+values[2]>1920 || (long)values[1]+values[3]>720) throw invalid("bounds outside primary screen");
        return List.of(values[0],values[1],values[0]+values[2],values[1]+values[3]);
    }
    private static List<Integer> nativeBounds(JsonObject value) { return value.getAsJsonArray("bounds").asList().stream().map(JsonElement::getAsInt).toList(); }
    public static LauncherGrid read(JsonObject ui,LauncherPage page) throws IOException {
        try {
            JsonObject surface=UiEvidence.surface(ui,"primary"), nativePage=page.observation();
            if(!yes(surface,"complete") || !"ok".equals(text(surface,"status")) || !ui.get("boot_id").equals(page.receipt().get("bootId"))) throw invalid("complete same-boot primary scope required");
            List<JsonObject> windows=new ArrayList<>();
            for(JsonElement element:surface.getAsJsonArray("windows")) {
                JsonObject window=element.getAsJsonObject();
                if(LauncherPage.OWNER.equals(text(window,"package")) && window.has("type") && window.get("type").isJsonPrimitive() && window.getAsJsonPrimitive("type").isNumber() && "1".equals(window.get("type").getAsString())) windows.add(window);
            }
            if(windows.size()!=1) throw invalid("unique primary Launcher window required");
            JsonObject window=windows.get(0);
            for(JsonElement element:surface.getAsJsonArray("windows")) {
                JsonObject other=element.getAsJsonObject();
                if(!other.get("id").equals(window.get("id")) && (yes(other,"active") || yes(other,"input_focused"))) throw invalid("another primary window owns input focus");
            }

            if(!window.get("type").isJsonPrimitive() || !window.getAsJsonPrimitive("type").isNumber() || !"1".equals(window.get("type").getAsString())
                    || !yes(window,"read_complete") || !rectangle(window).equals(nativeBounds(nativePage.getAsJsonObject("window")))) throw invalid("native and public window differ");
            Map<String,JsonObject> nodes=new HashMap<>(); List<JsonObject> grids=new ArrayList<>();
            for(JsonElement element:surface.getAsJsonArray("nodes")) {
                JsonObject node=element.getAsJsonObject();String id=text(node,"id");
                if(id.isEmpty() || nodes.putIfAbsent(id,node)!=null) throw invalid("node identity missing or duplicate");
                if(LauncherPage.OWNER.equals(text(node,"package")) && window.get("id").equals(node.get("window_id"))
                        && (LauncherPage.OWNER+":id/all_app_view").equals(text(node,"resource_id")) && yes(node,"visible") && yes(node,"enabled")) grids.add(node);
            }
            if(grids.size()!=1) throw invalid("unique actual grid required"); JsonObject grid=grids.get(0);
            if(!Set.of("com.qinggan.launcher.base.allapp.AllAppBarView","androidx.recyclerview.widget.RecyclerView").contains(text(grid,"class"))
                    || !rectangle(grid).equals(nativeBounds(nativePage.getAsJsonObject("grid"))) || !grid.get("bounds").equals(grid.get("clipped_bounds"))) throw invalid("native/public grid geometry or class differs");
            List<Item> result=new ArrayList<>();
            for(LauncherPage.Item item:page.items()) {
                List<JsonObject> roots=new ArrayList<>();
                for(JsonObject node:nodes.values()) if(text(grid,"id").equals(text(node,"parent")) && LauncherPage.OWNER.equals(text(node,"package"))
                        && window.get("id").equals(node.get("window_id")) && yes(node,"visible") && yes(node,"enabled")
                        && (yes(node,"clickable") || yes(node,"long_clickable")) && rectangle(node).equals(item.bounds()) && node.get("bounds").equals(node.get("clipped_bounds"))) roots.add(node);
                if(roots.size()!=1) throw invalid("actual item geometry is missing or ambiguous"); JsonObject root=roots.get(0); int labels=0;
                for(JsonObject label:nodes.values()) {
                    if(!(LauncherPage.OWNER+":id/item_name").equals(text(label,"resource_id")) || !item.label().equals(text(label,"text"))
                            || !LauncherPage.OWNER.equals(text(label,"package")) || !window.get("id").equals(label.get("window_id")) || !yes(label,"visible")) continue;
                    JsonObject at=label; Set<String> seen=new HashSet<>();
                    while(at!=null) {
                        String id=text(at,"id"); if(!seen.add(id)) throw invalid("cyclic item ancestry");
                        if(!LauncherPage.OWNER.equals(text(at,"package")) || !window.get("id").equals(at.get("window_id"))) throw invalid("item ancestry crossed owner/window");
                        if(id.equals(text(root,"id"))) { labels++; break; } at=nodes.get(text(at,"parent"));
                    }
                }
                if(labels!=1) throw invalid("exact native item label lacks one independent actual descendant");
                result.add(new Item(item.position(),item.label(),root));
            }
            return new LauncherGrid(grid,result);
        } catch(RuntimeException malformed) { throw new IOException("malformed independent Launcher hierarchy",malformed); }
    }
}
