package ru.voboost.voyemu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Actual exported View layout/text metrics. Canvas text padding is not View padding. */
public final class SettingsMeasurements {
    private static final String OWNER="com.qinggan.app.setting",BASE="ru.voboost.components.";
    public static JsonObject read(JsonObject surface,int number)throws IOException {
        if(!Set.of(20,21).contains(number))throw new IOException("closed layout case required");
        JsonObject source=surface;surface=ownerSurface(surface);
        Set<String> classes=number==20?Set.of(BASE+"checkbox.Checkbox",BASE+"checkbox.CheckboxPrimitive",BASE+"radio.Radio",BASE+"radio.RadioPrimitive",BASE+"section.Section"):Set.of(BASE+"button.Button",BASE+"button.ButtonPrimitive");
        Set<String> required=number==20?Set.of(BASE+"checkbox.CheckboxPrimitive",BASE+"radio.RadioPrimitive",BASE+"section.Section","android.widget.TextView"):Set.of(BASE+"button.ButtonPrimitive");
        Set<String> seen=new HashSet<>(),roles=new HashSet<>();JsonArray rows=new JsonArray();java.util.Map<String,JsonObject> all=new java.util.HashMap<>();Set<String> componentRoots=new HashSet<>();
        try {
            if(surface.getAsJsonArray("nodes").size()>12000)throw new IOException("measurement node bound exceeded");
            for(JsonElement value:surface.getAsJsonArray("nodes")){JsonObject node=value.getAsJsonObject();String id=text(node,"id");if(id.isBlank()||all.putIfAbsent(id,node)!=null)throw new IOException("ambiguous complete measurement hierarchy");if(OWNER.equals(text(node,"package"))&&classes.contains(text(node,"class")))componentRoots.add(id);}
            for(JsonElement value:surface.getAsJsonArray("nodes")) {
                JsonObject node=value.getAsJsonObject();String kind=text(node,"class");if(!OWNER.equals(text(node,"package"))||!classes.contains(kind)&&!kind.equals("android.widget.TextView"))continue;
                if(!eligible(node,all,componentRoots))continue;
                String id=text(node,"id"),window=text(node,"window_id");if(id.isBlank()||window.isBlank()||!seen.add(id))throw new IOException("ambiguous measured component identity");
                int windows=0;for(JsonElement candidate:surface.getAsJsonArray("windows")){JsonObject owned=candidate.getAsJsonObject();if(window.equals(text(owned,"id"))&&OWNER.equals(text(owned,"package"))&&"1".equals(text(owned,"type"))&&yes(owned,"read_complete"))windows++;}
                if(windows!=1)throw new IOException("measured component window owner differs");
                if(!node.has("layout")||!node.get("layout").isJsonObject())throw new UiEvidence.Unavailable("actual exported layout metrics missing");JsonObject layout=node.getAsJsonObject("layout");
                int width=integer(layout,"measured_width",false),height=integer(layout,"measured_height",false);
                if(width<1||height<1)throw new UiEvidence.Unavailable("component is not actually measured");
                for(String key:Set.of("min_width","min_height","padding_left","padding_top","padding_right","padding_bottom"))integer(layout,key,false);
                integer(layout,"scroll_x",true);integer(layout,"scroll_y",true);String params=text(layout,"params_type");
                if(!Set.of("none","basic","margin").contains(params))throw new UiEvidence.Unavailable("actual layout params type unavailable");
                if(!params.equals("none")){integer(layout,"layout_width",true);integer(layout,"layout_height",true);}
                if(params.equals("margin"))for(String key:Set.of("margin_left","margin_top","margin_right","margin_bottom"))integer(layout,key,true);
                if(kind.endsWith("CheckboxPrimitive")&&(width!=83||height!=42)||kind.endsWith("RadioPrimitive")&&height!=70||kind.endsWith("ButtonPrimitive")&&height!=70||kind.endsWith("section.Section")&&width!=1364&&width!=705)throw new IOException("actual primitive/section measured dimensions differ from the current pixel contract");
                JsonObject row=new JsonObject();row.addProperty("id",id);row.addProperty("class",kind);row.addProperty("windowId",window);row.add("layout",layout.deepCopy());
                if(node.has("text_style")&&node.get("text_style").isJsonObject()){
                    JsonObject style=node.getAsJsonObject("text_style");if(kind.equals("android.widget.TextView"))for(String key:Set.of("text_size_px","text_scale_x","letter_spacing"))if(!style.has(key)||style.get(key).isJsonNull())throw new UiEvidence.Unavailable("actual component text metric missing: "+key);for(String key:Set.of("text_size_px","text_scale_x","letter_spacing"))if(style.has(key)&&!style.get(key).isJsonNull()){JsonElement metric=style.get(key);if(!metric.isJsonPrimitive()||!metric.getAsJsonPrimitive().isNumber()||!Double.isFinite(metric.getAsDouble())||Math.abs(metric.getAsDouble())>65536)throw new IOException("actual text metric malformed");}row.add("textStyle",style.deepCopy());
                }
                if(kind.equals("android.widget.TextView")&&(!row.has("textStyle")||row.getAsJsonObject("textStyle").get("text_size_px").getAsDouble()<=0||row.getAsJsonObject("textStyle").get("text_scale_x").getAsDouble()<=0))throw new UiEvidence.Unavailable("actual visible component text style unavailable");
                row.addProperty("canvasInkMetricsAvailable",false);rows.add(row);roles.add(kind);
            }
        }catch(RuntimeException malformed){throw new IOException("malformed actual Settings measurement frame",malformed);}
        if(!roles.containsAll(required))throw new UiEvidence.Unavailable("required checkbox/radio/section or action primitives are not observed with complete metrics");
        JsonObject result=new JsonObject();result.addProperty("caseNumber",number);result.addProperty("measurementBasis","actual exported View layout/text_style; current component pixel contracts");result.add("rows",rows);result.addProperty("measurementScope","complete_settings_application_window");result.addProperty("sourceSurfaceComplete",yes(source,"complete"));result.addProperty("sourceScope",text(source,"scope"));result.addProperty("viewMetricsVerified",true);result.addProperty("canvasTextPaddingVerified",false);result.addProperty("visualAcceptance",false);return result;
    }
    /** Caller retains the owned request receipt and rejects reused observation ids before this read. */
    public static JsonObject readObservation(JsonObject observation,String profile,String session,String boot,int number)throws IOException {
        JsonObject primary=observationSurface(observation,profile,session,boot);JsonObject result=read(primary,number);result.addProperty("observationId",text(observation,"observation_id"));result.addProperty("sourceInstance",text(observation,"source_instance"));result.addProperty("bootId",boot);result.addProperty("startedUptimeMs",Long.toString(uptime(observation,"started_uptime_ms")));result.addProperty("completedUptimeMs",Long.toString(uptime(observation,"completed_uptime_ms")));return result;
    }
    static JsonObject observationSurface(JsonObject observation,String profile,String session,String boot)throws IOException {
        try {
            if(profile.isBlank()||session.isBlank()||boot.isBlank()||!"voyemu.ui.v1".equals(text(observation,"format"))
                    ||!profile.equals(text(observation,"profile"))||!session.equals(text(observation,"session_id"))||!boot.equals(text(observation,"boot_id"))
                    ||text(observation,"observation_id").isBlank()||text(observation,"source_instance").isBlank()
                    ||!"android.uptime".equals(text(observation,"source_clock_domain")))throw new IOException("Settings measurement observation identity differs");
            long started=uptime(observation,"started_uptime_ms"),finished=uptime(observation,"completed_uptime_ms");
            if(finished<started||finished-started>30000)throw new IOException("Settings measurement source interval differs");
            JsonObject primary=null;JsonArray screens=observation.getAsJsonArray("screens");
            if(screens==null||screens.size()>2)throw new IOException("Settings measurement screen coverage differs");
            for(JsonElement value:screens){JsonObject screen=value.getAsJsonObject();if("primary".equals(text(screen,"screen"))){if(primary!=null)throw new IOException("ambiguous Settings measurement surface");primary=screen;}}
            if(primary==null)throw new UiEvidence.Unavailable("primary Settings measurement surface unavailable");return primary;
        }catch(RuntimeException malformed){throw new IOException("malformed Settings measurement observation",malformed);}
    }
    private static long uptime(JsonObject value,String key)throws IOException {
        JsonElement field=value.get(key);if(field==null||!field.isJsonPrimitive()||!field.getAsString().matches("[0-9]{1,18}"))throw new IOException("Settings measurement source clock unavailable");return Long.parseLong(field.getAsString());
    }
    /** A failed foreign wallpaper dump does not fabricate or invalidate the positively complete owner tree. */
    static JsonObject ownerSurface(JsonObject surface)throws IOException {
        try {
            if(!"all_attached".equals(text(surface,"scope"))||!Set.of("ok","partial").contains(text(surface,"status")))throw new UiEvidence.Unavailable("actual all_attached View bridge measurement required");
            JsonArray windows=surface.getAsJsonArray("windows"),nodes=surface.getAsJsonArray("nodes");
            if(windows==null||nodes==null||windows.size()>64||nodes.size()>12000)throw new IOException("Settings measurement hierarchy bound differs");
            JsonObject owner=null;Set<String> windowIds=new HashSet<>();
            for(JsonElement value:windows){JsonObject window=value.getAsJsonObject();String id=text(window,"id");if(id.isBlank()||!windowIds.add(id))throw new IOException("ambiguous measured window identity");
                if(OWNER.equals(text(window,"package"))&&"1".equals(text(window,"type"))){if(owner!=null)throw new IOException("ambiguous Settings application window");owner=window;}}
            if(owner==null||!yes(owner,"read_complete"))throw new UiEvidence.Unavailable("complete actual Settings application window unavailable");
            String windowId=text(owner,"id"),root=text(owner,"root_id");int count=integer(owner,"node_count",false);if(root.isBlank()||count<1)throw new IOException("measured window root or node count unavailable");
            JsonArray selected=new JsonArray();java.util.Map<String,JsonObject> indexed=new java.util.HashMap<>();
            for(JsonElement value:nodes){JsonObject node=value.getAsJsonObject();String nodeWindow=text(node,"window_id");
                if(OWNER.equals(text(node,"package"))&&!windowId.equals(nodeWindow))throw new IOException("Settings measurement owner coverage is incomplete");
                if(!windowId.equals(nodeWindow))continue;
                JsonElement bridge=node.get("bridge_version");String id=text(node,"id");
                if(!OWNER.equals(text(node,"package"))||!yes(node,"present")||bridge==null||!bridge.isJsonPrimitive()||!bridge.getAsJsonPrimitive().isNumber()||!"1".equals(bridge.getAsString())||id.isBlank()||indexed.putIfAbsent(id,node)!=null)throw new IOException("measured owner View bridge identity differs");selected.add(node.deepCopy());}
            if(count!=selected.size()||!indexed.containsKey(root)||!text(indexed.get(root),"parent").isEmpty())throw new IOException("measured owner tree is incomplete");
            for(JsonObject initial:indexed.values()){JsonObject node=initial;Set<String> ancestry=new HashSet<>();while(true){String id=text(node,"id");if(!ancestry.add(id)||ancestry.size()>128)throw new IOException("cyclic measured owner tree");String parent=text(node,"parent");if(parent.isEmpty()){if(!root.equals(id))throw new IOException("disconnected measured owner root");break;}node=indexed.get(parent);if(node==null)throw new IOException("measured owner ancestor unavailable");}}
            JsonObject result=new JsonObject();result.addProperty("complete",true);result.addProperty("status","ok");result.addProperty("scope","complete_settings_application_window");JsonArray ownedWindows=new JsonArray();ownedWindows.add(owner.deepCopy());result.add("windows",ownedWindows);result.add("nodes",selected);return result;
        }catch(RuntimeException malformed){throw new IOException("malformed Settings measurement owner tree",malformed);}
    }
    /** Select only the authenticated publication root's actual window; a dialog is never a panel. */
    static JsonObject rootSurface(JsonObject source,String identity,boolean exactWindowRoot)throws IOException {
        try {
            if(!"all_attached".equals(text(source,"scope"))||!Set.of("ok","partial").contains(text(source,"status"))||identity==null||!identity.matches("[A-Za-z_$][A-Za-z0-9_.$]*@[0-9a-f]{1,8}"))throw new IOException("actual publication root/window unavailable");
            JsonArray windows=source.getAsJsonArray("windows"),nodes=source.getAsJsonArray("nodes");if(windows==null||nodes==null||windows.size()>64||nodes.size()>12000)throw new IOException("publication window bounds differ");
            JsonObject root=null,window=null;Set<String> allIds=new HashSet<>();for(JsonElement item:nodes){JsonObject n=item.getAsJsonObject();String id=text(n,"id");if(id.isBlank()||!allIds.add(id))throw new IOException("ambiguous source View identity");if(id.equals(text(n,"window_id")+":"+identity)){if(root!=null)throw new IOException("publication root hash collides across windows");root=n;}}
            if(root==null||!OWNER.equals(text(root,"package")))throw new UiEvidence.Unavailable("published root not present in actual Settings window");String windowId=text(root,"window_id");Set<String> windowIds=new HashSet<>();for(JsonElement item:windows){JsonObject w=item.getAsJsonObject();if(text(w,"id").isBlank()||!windowIds.add(text(w,"id")))throw new IOException("ambiguous source window");if(windowId.equals(text(w,"id")))window=w;}
            if(window==null||!OWNER.equals(text(window,"package"))||!Set.of("1","2").contains(text(window,"type"))||!yes(window,"read_complete"))throw new UiEvidence.Unavailable("complete owning application/dialog window missing");
            String windowRoot=text(window,"root_id");if(exactWindowRoot&&!text(root,"id").equals(windowRoot))throw new IOException("dialog identity is not its independent window root");
            JsonArray selected=new JsonArray();java.util.Map<String,JsonObject> indexed=new java.util.HashMap<>();for(JsonElement item:nodes){JsonObject n=item.getAsJsonObject();if(!windowId.equals(text(n,"window_id")))continue;JsonElement bridge=n.get("bridge_version");if(!OWNER.equals(text(n,"package"))||!yes(n,"present")||bridge==null||!bridge.isJsonPrimitive()||!bridge.getAsJsonPrimitive().isNumber()||!"1".equals(bridge.getAsString()))throw new IOException("actual published window View bridge differs");selected.add(n.deepCopy());indexed.put(text(n,"id"),n);}
            if(integer(window,"node_count",false)!=selected.size()||!indexed.containsKey(windowRoot)||!text(indexed.get(windowRoot),"parent").isEmpty())throw new IOException("published window coverage incomplete");
            for(JsonObject first:indexed.values()){JsonObject n=first;Set<String> seen=new HashSet<>();while(true){String id=text(n,"id");if(!seen.add(id)||seen.size()>128)throw new IOException("published ancestry cyclic");String parent=text(n,"parent");if(parent.isEmpty()){if(!id.equals(windowRoot))throw new IOException("published root disconnected");break;}n=indexed.get(parent);if(n==null)throw new IOException("published ancestor missing");}}
            JsonObject result=new JsonObject();result.addProperty("scope","complete_published_settings_window");result.addProperty("complete",true);result.addProperty("status","ok");JsonArray one=new JsonArray();one.add(window.deepCopy());result.add("windows",one);result.add("nodes",selected);return result;
        }catch(RuntimeException malformed){throw new IOException("malformed publication window",malformed);}
    }
    private static boolean eligible(JsonObject node,java.util.Map<String,JsonObject> all,Set<String> roots)throws IOException {
        boolean found=false;String owner=text(node,"package"),window=text(node,"window_id");Set<String> ancestry=new HashSet<>();
        while(true){String id=text(node,"id");if(!ancestry.add(id)||ancestry.size()>128)throw new IOException("cyclic measured component ancestry");if(!OWNER.equals(text(node,"package"))||!owner.equals(text(node,"package"))||!window.equals(text(node,"window_id")))throw new IOException("measured component ancestry crossed owner/window");if(!booleanField(node,"present")||!booleanField(node,"visible"))return false;if(roots.contains(id))found=true;String parent=text(node,"parent");if(parent.isEmpty())return found;node=all.get(parent);if(node==null)throw new UiEvidence.Unavailable("complete measured component ancestry unavailable");}
    }
    private static int integer(JsonObject value,String key,boolean signed)throws IOException {JsonElement field=value.get(key);if(field==null||field.isJsonNull())throw new UiEvidence.Unavailable("actual metric unavailable: "+key);if(!field.isJsonPrimitive()||!field.getAsJsonPrimitive().isNumber()||!field.getAsString().matches(signed?"-?[0-9]{1,5}":"[0-9]{1,5}"))throw new IOException("actual integer metric malformed: "+key);int number=field.getAsInt();if(Math.abs(number)>65536)throw new IOException("metric bound exceeded");return number;}
    private static boolean booleanField(JsonObject node,String key)throws IOException {JsonElement value=node.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new UiEvidence.Unavailable("actual component state unavailable: "+key);return value.getAsBoolean();}
    private static boolean yes(JsonObject node,String key){return node.has(key)&&node.get(key).isJsonPrimitive()&&node.getAsJsonPrimitive(key).isBoolean()&&node.get(key).getAsBoolean();}
    private static String text(JsonObject node,String key){return node.has(key)&&node.get(key).isJsonPrimitive()?node.get(key).getAsString():"";}
    private SettingsMeasurements(){}
}
