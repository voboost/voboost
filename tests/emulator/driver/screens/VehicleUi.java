package ru.voboost.voyemu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** Exact current OEM feature relationships; captions and coordinates never substitute for a checked child. */
public final class VehicleUi {
    public static final String OWNER="com.qinggan.app.vehiclesetting";
    public enum Feature {
        FORCED_EV("power_mode_electric_force","rb_menu_drive_preference","fragment_drive_preference_scroll"),
        PEDESTRIAN_WARNING("fragment_safe_hum_vsp","rb_menu_safety_maintenance","fragment_scrollview");
        final String group,menu,scroll;
        Feature(String group,String menu,String scroll){this.group=group;this.menu=menu;this.scroll=scroll;}
        public String menu(){return OWNER+":id/"+menu;}
        public String scroll(){return OWNER+":id/"+scroll;}
    }
    public record Control(String nodeId,boolean checked,boolean enabled) {}
    private VehicleUi(){}
    private static String text(JsonObject o,String k){return o.has(k)&&o.get(k).isJsonPrimitive()&&o.getAsJsonPrimitive(k).isString()?o.get(k).getAsString():"";}
    private static boolean yes(JsonObject o,String k){return o.has(k)&&o.get(k).isJsonPrimitive()&&o.getAsJsonPrimitive(k).isBoolean()&&o.get(k).getAsBoolean();}
    private static IOException unavailable(String s){return new UiEvidence.Unavailable("OEM vehicle UI unavailable: "+s);}
    private static Map<String,JsonObject> nodes(JsonObject surface)throws IOException {
        if(!yes(surface,"complete")||!"ok".equals(text(surface,"status"))||!surface.has("nodes")||!surface.get("nodes").isJsonArray()||surface.getAsJsonArray("nodes").size()>12000)throw unavailable("complete surface required");
        Map<String,JsonObject> nodes=new HashMap<>();for(JsonElement item:surface.getAsJsonArray("nodes")) {
            JsonObject node=item.getAsJsonObject();String id=text(node,"id");if(id.isEmpty()||nodes.put(id,node)!=null)throw unavailable("duplicate/missing node identity");
        }return nodes;
    }
    private static void window(JsonObject surface,JsonObject node)throws IOException {
        JsonObject selected=null;for(JsonElement item:surface.getAsJsonArray("windows")) {
            JsonObject candidate=item.getAsJsonObject();if(candidate.get("id").equals(node.get("window_id"))) {if(selected!=null)throw unavailable("duplicate window");selected=candidate;}
        }
        if(selected==null||!OWNER.equals(text(selected,"package"))||!selected.has("type")||!"1".equals(selected.get("type").getAsString())||!yes(selected,"read_complete")||!yes(selected,"active")||!yes(selected,"input_focused"))throw unavailable("current focused application window required");
    }
    private static boolean laidOut(JsonObject node) {
        if(!node.has("bounds")||!node.has("clipped_bounds")||!node.get("bounds").isJsonObject()
                ||!node.get("bounds").equals(node.get("clipped_bounds")))return false;
        JsonObject bounds=node.getAsJsonObject("bounds");
        return bounds.has("width")&&bounds.has("height")&&bounds.get("width").getAsInt()>0&&bounds.get("height").getAsInt()>0;
    }
    public static Control feature(JsonObject surface,Feature feature)throws IOException {
        try {
            var nodes=nodes(surface);JsonObject root=null;
            for(JsonObject node:nodes.values()) if(OWNER.equals(text(node,"package"))&&(OWNER+":id/"+feature.group).equals(text(node,"resource_id"))) {if(root!=null)throw unavailable("duplicate feature group");root=node;}
            if(root==null)throw unavailable("feature group absent");window(surface,root);
            JsonObject selected=null;for(JsonObject node:nodes.values()) {
                if(!OWNER.equals(text(node,"package"))||!yes(node,"checkable"))continue;
                JsonObject parent=node;HashSet<String> seen=new HashSet<>();boolean child=false;
                while(parent.has("parent")&&!parent.get("parent").isJsonNull()) {
                    String id=text(parent,"parent");if(id.isEmpty()||!seen.add(id))throw unavailable("cyclic/malformed ancestry");
                    if(id.equals(text(root,"id"))) {child=true;break;}parent=nodes.get(id);if(parent==null)throw unavailable("missing ancestor");
                }
                if(child) {if(selected!=null)throw unavailable("duplicate checkable child");selected=node;}
            }
            if(selected==null||!yes(selected,"visible")||!laidOut(selected)||!selected.has("checked")||!selected.get("checked").isJsonPrimitive()||!selected.getAsJsonPrimitive("checked").isBoolean())throw unavailable("actual visible checked child required");
            window(surface,selected);if(!selected.get("window_id").equals(root.get("window_id")))throw unavailable("group/control window differs");
            if(!selected.has("bounds")||!selected.has("clipped_bounds")||!selected.get("bounds").equals(selected.get("clipped_bounds")))throw unavailable("clipped control");
            return new Control(text(selected,"id"),selected.get("checked").getAsBoolean(),yes(selected,"enabled"));
        }catch(IllegalStateException|NullPointerException malformed){throw new IOException("malformed OEM vehicle hierarchy",malformed);}
    }
    public static Control electricRadio(JsonObject surface)throws IOException {
        String resource=OWNER+":id/fragment_radio_button_power_mode_electric";String id=uniqueResource(surface,resource);JsonObject node=nodes(surface).get(id);
        if(!yes(node,"checkable")||!node.has("checked")||!node.get("checked").isJsonPrimitive()||!node.getAsJsonPrimitive("checked").isBoolean())throw unavailable("actual Electric radio checked state required");
        return new Control(id,node.get("checked").getAsBoolean(),yes(node,"enabled"));
    }
    /** Exact OEM FEV consent topology; no generic Agree/OK button selection. */
    public static String forceConsent(JsonObject surface)throws IOException {
        var nodes=nodes(surface);JsonObject selected=null;
        for(JsonObject node:nodes.values())if(OWNER.equals(text(node,"package"))&&(OWNER+":id/disclaimer_agree").equals(text(node,"resource_id"))&&yes(node,"visible")) {if(selected!=null)throw unavailable("duplicate FEV consent");selected=node;}
        if(selected==null)return null;JsonObject dialog=nodes.get(text(selected,"parent")),background=dialog==null?null:nodes.get(text(dialog,"parent"));
        if(dialog==null||background==null||!"android.widget.Button".equals(text(selected,"class"))||!yes(selected,"enabled")||!yes(selected,"clickable")||!(OWNER+":id/fragment_forceev_dialog").equals(text(dialog,"resource_id"))||!(OWNER+":id/view_bg").equals(text(background,"resource_id"))||!"android.widget.RelativeLayout".equals(text(dialog,"class"))||!"android.widget.RelativeLayout".equals(text(background,"class"))||!OWNER.equals(text(dialog,"package"))||!OWNER.equals(text(background,"package"))||!selected.get("window_id").equals(dialog.get("window_id"))||!selected.get("window_id").equals(background.get("window_id")))throw unavailable("fixed FEV consent ancestry/owner differs");
        boolean owned=false;for(JsonElement item:surface.getAsJsonArray("windows")){JsonObject w=item.getAsJsonObject();if(w.get("id").equals(selected.get("window_id"))&&OWNER.equals(text(w,"package"))&&yes(w,"read_complete")&&yes(w,"active")&&yes(w,"input_focused")){if(owned)throw unavailable("duplicate consent window");owned=true;}}
        if(!owned)throw unavailable("current complete focused FEV consent window required");return text(selected,"id");
    }
    public record Consent(String window,UiTarget target) {}
    /** A visible disclaimer can precede its first window layout; null target permits observation only. */
    public static Consent forceConsentTarget(JsonObject observation,String profile,VoyemuClient.Session session)throws IOException {
        JsonObject surface=UiEvidence.surface(observation,"primary");String id=forceConsent(surface);
        if(id==null)return null;
        JsonObject button=nodes(surface).get(id),window=null;
        String windowId=text(button,"window_id");
        for(JsonElement item:surface.getAsJsonArray("windows"))if(windowId.equals(text(item.getAsJsonObject(),"id")))window=item.getAsJsonObject();
        if(window==null)throw new IOException("actual FEV consent window missing");
        JsonObject bounds=window.getAsJsonObject("bounds"),clip=button.getAsJsonObject("clipped_bounds");
        if(bounds.get("width").getAsInt()<=0||bounds.get("height").getAsInt()<=0
                ||clip.get("width").getAsInt()<=0||clip.get("height").getAsInt()<=0||!yes(button,"actionable_candidate"))
            return new Consent(windowId,null);
        return new Consent(windowId,UiTarget.select(observation,profile,session,"primary",node->id.equals(text(node,"id"))));
    }
    public static String uniqueResource(JsonObject surface,String resource)throws IOException {
        String selected=null;for(JsonObject node:nodes(surface).values()) if(OWNER.equals(text(node,"package"))&&resource.equals(text(node,"resource_id"))&&yes(node,"visible")&&yes(node,"enabled")&&yes(node,"clickable")) {
            window(surface,node);if(!laidOut(node)||!yes(node,"actionable_candidate"))throw unavailable("resource has no current actionable layout");if(selected!=null)throw unavailable("duplicate resource");selected=text(node,"id");
        }if(selected==null)throw unavailable("resource not visible: "+resource);return selected;
    }
}
