package ru.voboost.voyemu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Actual OEM drawer power card and mode/switch state, independent of global primary/secondary focus. */
public final class VehicleDrawer {
    public static final String OWNER="com.qinggan.app.launcher";
    public record State(String cardId,String switchId,ForcedEvProgram.Mode mode,Boolean forceSelected,String geometry) {}
    private VehicleDrawer(){}
    private static String text(JsonObject o,String k){return o.has(k)&&o.get(k).isJsonPrimitive()&&o.getAsJsonPrimitive(k).isString()?o.get(k).getAsString():"";}
    private static boolean yes(JsonObject o,String k){return o.has(k)&&o.get(k).isJsonPrimitive()&&o.getAsJsonPrimitive(k).isBoolean()&&o.get(k).getAsBoolean();}
    private static UiEvidence.Unavailable unavailable(String reason){return new UiEvidence.Unavailable("OEM drawer unavailable: "+reason);}
    private static Map<String,JsonObject> nodes(JsonObject surface)throws IOException {
        if(!yes(surface,"complete")||!"ok".equals(text(surface,"status"))||!surface.has("nodes")||!surface.get("nodes").isJsonArray()||surface.getAsJsonArray("nodes").size()>12000)throw unavailable("complete public surface required");
        Map<String,JsonObject> result=new HashMap<>();for(JsonElement item:surface.getAsJsonArray("nodes")){JsonObject node=item.getAsJsonObject();String id=text(node,"id");if(id.isEmpty()||result.put(id,node)!=null)throw unavailable("duplicate/missing node identity");}return result;
    }
    private static JsonObject window(JsonObject surface,JsonObject node,boolean focused)throws IOException {
        JsonObject found=null;for(JsonElement item:surface.getAsJsonArray("windows")){JsonObject candidate=item.getAsJsonObject();if(candidate.get("id").equals(node.get("window_id"))){if(found!=null)throw unavailable("duplicate window");found=candidate;}}
        if(found==null||!OWNER.equals(text(found,"package"))||!found.has("type")||!"3".equals(found.get("type").getAsString())||!windowReadComplete(surface,found)||focused&&(!yes(found,"active")||!yes(found,"input_focused")))throw unavailable("actual OEM drawer window/focus required");return found;
    }
    private static boolean windowReadComplete(JsonObject surface,JsonObject window) {
        if(window.has("read_complete"))return yes(window,"read_complete");
        return "accessibility_application_root".equals(text(surface,"coverage_basis"))
            && "api28_default_display".equals(text(surface,"scope"))
            && yes(surface,"complete") && "ok".equals(text(surface,"status"))
            && surface.has("unavailable_windows") && surface.get("unavailable_windows").isJsonArray()
            && surface.getAsJsonArray("unavailable_windows").isEmpty();
    }
    private static final Set<String> WARNING = Set.of("PED Alarm", "行人报警音");
    private static final Set<String> SPARE = Set.of("Easy Exit", "便捷下车");

    /** A9 OEM ped_alarm card; CAN supplies its state independently. */
    public static String pedestrianWarningCard(JsonObject surface) throws IOException {
        return card(surface, WARNING);
    }

    public static JsonObject pedestrianWarningIcon(JsonObject surface) throws IOException {
        String card = pedestrianWarningCard(surface);
        Map<String, JsonObject> inventory = nodes(surface);
        int[] parent = rect(inventory.get(card));
        JsonObject selected = null;
        for (JsonObject node : inventory.values()) {
            if (!card.equals(text(node, "parent"))
                    || !(OWNER + ":id/iv_des").equals(text(node, "resource_id"))) continue;
            if (!OWNER.equals(text(node, "package")) || !yes(node, "visible")
                    || !"android.widget.ImageView".equals(text(node, "class")))
                throw unavailable("PW icon topology differs");
            window(surface, node, true);
            int[] bounds = rect(node);
            if (!node.get("window_id").equals(inventory.get(card).get("window_id"))
                    || bounds[0] < parent[0] || bounds[1] < parent[1]
                    || bounds[2] > parent[2] || bounds[3] > parent[3] || selected != null)
                throw unavailable("PW icon is ambiguous or outside its card");
            selected = node;
        }
        if (selected == null) throw unavailable("PW icon absent");
        return selected.deepCopy();
    }

    private static String card(JsonObject surface, Set<String> titles) throws IOException {
        Map<String, JsonObject> inventory = nodes(surface);
        JsonObject selected = null;
        for (JsonObject title : inventory.values()) {
            if (!OWNER.equals(text(title, "package")) || !yes(title, "visible")
                    || !(OWNER + ":id/tv_title").equals(text(title, "resource_id"))
                    || !titles.contains(text(title, "text"))) continue;
            window(surface, title, true);
            JsonObject card = inventory.get(text(title, "parent"));
            if (card == null || !(OWNER + ":id/rl_root").equals(text(card, "resource_id"))
                    || !"android.widget.RelativeLayout".equals(text(card, "class"))
                    || !OWNER.equals(text(card, "package")) || !yes(card, "visible")
                    || !yes(card, "enabled") || !yes(card, "clickable")
                    || !text(card, "window_id").equals(text(title, "window_id")))
                throw unavailable("PW card topology differs");
            rect(card);
            if (selected != null) throw unavailable("ambiguous PW card");
            selected = card;
        }
        if (selected == null) throw unavailable("PW card absent; drawer Edit required");
        return text(selected, "id");
    }
    /** Only a PW card still in the OEM edit library can be added. */
    public static String pedestrianWarningAdd(JsonObject surface) throws IOException {
        return pedestrianWarningEdit(surface, true);
    }

    /** The OEM edit overlay moves the card between the library and the active workspace. */
    public static String pedestrianWarningEdit(JsonObject surface, boolean inLibrary) throws IOException {
        return editCard(surface, inLibrary, WARNING);
    }

    public static String spareEdit(JsonObject surface, boolean inLibrary) throws IOException {
        return editCard(surface, inLibrary, SPARE);
    }

    public static JsonObject spareBounds(JsonObject surface) throws IOException {
        return nodes(surface).get(card(surface, SPARE)).getAsJsonObject("bounds").deepCopy();
    }

    public static boolean warningWorkspaceFull(JsonObject surface) throws IOException {
        var inventory = nodes(surface);
        var warning = inventory.get(card(surface, WARNING));
        var workspace = inventory.values().stream()
                .filter(node -> (OWNER + ":id/workspace").equals(text(node, "resource_id")) && yes(node, "visible"))
                .toList();
        if (workspace.size() != 1) throw unavailable("one visible widget workspace required");
        var size = warning.getAsJsonObject("bounds");
        int width = size.get("width").getAsInt();
        int height = size.get("height").getAsInt();
        var bounds = workspace.get(0).getAsJsonObject("bounds");
        int capacity = (bounds.get("width").getAsInt() / width) * (bounds.get("height").getAsInt() / height);
        int used = 0;
        String workspaceId = text(workspace.get(0), "id");
        for (var node : inventory.values()) {
            if (!(OWNER + ":id/top_view").equals(text(node, "resource_id")) || !yes(node, "visible")) continue;
            if (!descendsFrom(inventory, node, workspaceId)) continue;
            var sizeUsed = node.getAsJsonObject("bounds");
            used += (sizeUsed.get("width").getAsInt() / width) * (sizeUsed.get("height").getAsInt() / height);
        }
        if (capacity < 1 || used > capacity) throw unavailable("widget workspace geometry differs");
        return used == capacity;
    }

    private static boolean descendsFrom(Map<String, JsonObject> nodes, JsonObject node, String ancestor)
            throws IOException {
        var seen = new HashSet<String>();
        for (var current = node; current != null; current = nodes.get(text(current, "parent"))) {
            String id = text(current, "id");
            if (!seen.add(id)) throw unavailable("cyclic edit ancestry");
            if (ancestor.equals(id)) return true;
        }
        return false;
    }

    private static String editCard(JsonObject surface, boolean inLibrary, Set<String> titles) throws IOException {
        String card = card(surface, titles);
        Map<String, JsonObject> inventory = nodes(surface);
        JsonObject ancestor = inventory.get(card);
        Set<String> seen = new HashSet<>();
        String container = inLibrary ? "rv_library" : "component_added_container";
        boolean found = false;
        while (ancestor != null) {
            String id = text(ancestor, "id");
            if (!seen.add(id)) throw unavailable("cyclic PW library ancestry");
            if ((OWNER + ":id/" + container).equals(text(ancestor, "resource_id"))) {
                found = true;
                break;
            }
            ancestor = inventory.get(text(ancestor, "parent"));
        }
        if (!found) throw unavailable("PW card is outside the expected edit container");
        String selected = null;
        for (JsonObject node : inventory.values()) {
            if (!card.equals(text(node, "parent"))
                    || !(OWNER + ":id/top_view").equals(text(node, "resource_id"))) continue;
            if (!OWNER.equals(text(node, "package")) || !"android.view.View".equals(text(node, "class"))
                    || !yes(node, "visible") || !yes(node, "enabled") || !yes(node, "clickable"))
                throw unavailable("PW edit overlay is not actionable");
            window(surface, node, true);
            rect(node);
            if (selected != null) throw unavailable("ambiguous PW edit overlay");
            selected = text(node, "id");
        }
        if (selected == null) throw unavailable("PW edit overlay absent");
        return selected;
    }
    public static State read(JsonObject surface)throws IOException {
        try {
            Map<String,JsonObject> nodes=nodes(surface);JsonObject title=null;ForcedEvProgram.Mode mode=null;
            for(JsonObject node:nodes.values())if(OWNER.equals(text(node,"package"))&&(OWNER+":id/tv_title").equals(text(node,"resource_id"))&&yes(node,"visible")) {
                ForcedEvProgram.Mode parsed=switch(text(node,"text")){case "Electric","纯电优先"->ForcedEvProgram.Mode.ELECTRIC;case "Hybrid","油电混合"->ForcedEvProgram.Mode.HYBRID;case "Save","保电模式"->ForcedEvProgram.Mode.SAVE;default->null;};
                if(parsed!=null){if(title!=null)throw unavailable("duplicate power title");title=node;mode=parsed;}
            }
            if(title==null)throw unavailable("power card not visible");JsonObject owner=window(surface,title,true),card=title;Set<String> seen=new HashSet<>();
            while(!(OWNER+":id/rl_root").equals(text(card,"resource_id"))) {
                String parent=text(card,"parent");if(parent.isEmpty()||!seen.add(parent)||!nodes.containsKey(parent))throw unavailable("power title ancestry unavailable");card=nodes.get(parent);
            }
            if(!OWNER.equals(text(card,"package"))||!"android.widget.RelativeLayout".equals(text(card,"class"))||!yes(card,"visible")||!yes(card,"enabled")||!yes(card,"clickable")||!card.get("window_id").equals(owner.get("id"))||!card.get("bounds").equals(card.get("clipped_bounds")))throw unavailable("actual clickable unclipped power card required");
            rect(card);
            JsonObject control=null;for(JsonObject node:nodes.values())if(OWNER.equals(text(node,"package"))&&(OWNER+":id/force_ev_switcher").equals(text(node,"resource_id"))&&yes(node,"visible")) {if(control!=null)throw unavailable("duplicate force switch");control=node;}
            Boolean selected=null;String switchId="";
            if(control!=null) {
                if(!control.get("window_id").equals(owner.get("id"))||!yes(control,"enabled")||!yes(control,"clickable")||!control.has("selected")||!control.get("selected").isJsonPrimitive()||!control.getAsJsonPrimitive("selected").isBoolean()||!control.get("bounds").equals(control.get("clipped_bounds")))throw unavailable("actual force switch state/geometry required");
                rect(control);selected=control.get("selected").getAsBoolean();switchId=text(control,"id");
            }
            if(mode==ForcedEvProgram.Mode.ELECTRIC&&selected==null)throw unavailable("Electric mode switch selected state missing");
            JsonObject signature=new JsonObject();signature.add("window",geometry(owner,"id","type","package","bounds","active","input_focused"));signature.add("card",geometry(card,"resource_id","class","package","bounds","clipped_bounds"));signature.add("title",geometry(title,"resource_id","class","package","bounds","clipped_bounds"));if(control!=null)signature.add("switch",geometry(control,"resource_id","class","package","bounds","clipped_bounds"));
            return new State(text(card,"id"),switchId,mode,selected,signature.toString());
        }catch(IllegalStateException|NullPointerException malformed){throw new IOException("malformed actual drawer hierarchy",malformed);}
    }
    /** Node identities are observation-local; they cannot define layout stability across reads. */
    private static JsonObject geometry(JsonObject source,String... fields) {
        JsonObject result=new JsonObject();
        for(String field:fields)if(source.has(field))result.add(field,source.get(field).deepCopy());
        return result;
    }
    public record Close(String nodeId,int startX,int y,int endX) {}
    private static int[] rect(JsonObject node)throws IOException {
        if(!node.has("bounds")||!node.has("clipped_bounds")||!node.get("bounds").equals(node.get("clipped_bounds")))throw unavailable("unclipped background geometry required");
        JsonObject b=node.getAsJsonObject("bounds");int x=b.get("x").getAsInt(),y=b.get("y").getAsInt(),w=b.get("width").getAsInt(),h=b.get("height").getAsInt();
        if(x<0||y<0||w<1||h<1||w>3840||h>2160)throw unavailable("background bounds");return new int[]{x,y,x+w,y+h};
    }
    /** Choose only an observed empty normal-mode drawer background rectangle, never a control. */
    public static Close close(JsonObject surface)throws IOException {
        var nodes=nodes(surface);JsonObject root=null,content=null,background=null,edit=null;
        for(JsonObject node:nodes.values())if(OWNER.equals(text(node,"package"))&&yes(node,"visible")) {
            String resource=text(node,"resource_id");
            if((!node.has("parent")||node.get("parent").isJsonNull())&&resource.isEmpty()&&"android.widget.FrameLayout".equals(text(node,"class"))&&yes(node,"enabled")){window(surface,node,true);if(root!=null)throw unavailable("duplicate drawer root");root=node;}
            if((OWNER+":id/screen_up_vehicle_hiboard_new").equals(resource)){if(content!=null)throw unavailable("duplicate drawer content");content=node;}
            if((OWNER+":id/view_screen_up_vehicle_hiboard_bg").equals(resource)){if(background!=null)throw unavailable("duplicate drawer background");background=node;}
            if((OWNER+":id/screen_up_edit_btn").equals(resource)&&yes(node,"clickable")&&yes(node,"enabled")&&Set.of("Edit","编辑").contains(text(node,"text"))){if(edit!=null)throw unavailable("duplicate drawer Edit control");edit=node;}
        }
        if(root==null||content==null||background==null||edit==null||!"android.widget.RelativeLayout".equals(text(content,"class"))||!"android.widget.ImageView".equals(text(background,"class"))||yes(background,"clickable")||yes(background,"scrollable")||yes(background,"long_clickable")||!text(content,"parent").equals(text(root,"id"))||!text(background,"parent").equals(text(content,"id"))||!root.get("bounds").equals(content.get("bounds"))||!root.get("bounds").equals(background.get("bounds")))throw unavailable("normal-mode drawer background topology required");
        int[] whole=rect(root);java.util.List<int[]> blockers=new java.util.ArrayList<>();java.util.TreeSet<Integer> xs=new java.util.TreeSet<>(),ys=new java.util.TreeSet<>();xs.add(whole[0]);xs.add(whole[2]);ys.add(whole[1]);ys.add(whole[3]);
        for(JsonObject node:nodes.values())if(node!=root&&node!=content&&node!=background&&yes(node,"visible")&&node.get("window_id").equals(root.get("window_id"))) {
            int[] r=rect(node);int left=Math.max(whole[0],r[0]),top=Math.max(whole[1],r[1]),right=Math.min(whole[2],r[2]),bottom=Math.min(whole[3],r[3]);
            if(left<right&&top<bottom){blockers.add(new int[]{left,top,right,bottom});xs.add(left);xs.add(right);ys.add(top);ys.add(bottom);}
        }
        if(xs.size()>256||ys.size()>256)throw unavailable("drawer background partition bound");var xx=new java.util.ArrayList<>(xs);var yy=new java.util.ArrayList<>(ys);Close chosen=null;long area=0;
        for(int x=0;x<xx.size()-1;x++)for(int y=0;y<yy.size()-1;y++) {
            int l=xx.get(x),r=xx.get(x+1),t=yy.get(y),b=yy.get(y+1);if(r-l<64||b-t<4)continue;boolean blocked=false;
            for(int[] other:blockers)if(other[0]<r&&other[2]>l&&other[1]<b&&other[3]>t){blocked=true;break;}
            long size=(long)(r-l)*(b-t);if(!blocked&&size>area){area=size;chosen=new Close(text(root,"id"),r-2,(t+b)/2,l+2);}
        }
        if(chosen==null)throw unavailable("proven empty background region absent");return chosen;
    }
    public static String handle(JsonObject surface)throws IOException {
        String selected=null;for(JsonObject node:nodes(surface).values())if(OWNER.equals(text(node,"package"))&&(OWNER+":id/line_view").equals(text(node,"resource_id"))&&"android.view.View".equals(text(node,"class"))&&yes(node,"visible")&&yes(node,"enabled")) {
            JsonObject window=window(surface,node,false),b=window.getAsJsonObject("bounds");
            if(b.get("x").getAsInt()!=0||b.get("height").getAsInt()<=b.get("width").getAsInt())continue;
            rect(node);if(selected!=null)throw unavailable("ambiguous left drawer receiver");selected=text(node,"id");
        }if(selected==null)throw unavailable("actual left vertical receiver absent");return selected;
    }
}
