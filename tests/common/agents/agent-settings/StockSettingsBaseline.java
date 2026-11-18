package ru.voboost.voyemu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Pre-install actual OEM candidate collection. Review and reference admission are separate actions. */
public final class StockSettingsBaseline {
    private static final String OWNER="com.qinggan.app.setting";
    public enum Viewport { SYSTEM_ACTIONS, LANGUAGE_RADIO }
    public enum Control {
        FACTORY_RESET(Viewport.SYSTEM_ACTIONS,"btn_factory_reset","恢复出厂设置"),
        LANGUAGE_CHINESE(Viewport.LANGUAGE_RADIO,"display_language_radio_chinese","中文（简体）");
        private final Viewport viewport;private final String symbol,caption;
        Control(Viewport viewport,String symbol,String caption){this.viewport=viewport;this.symbol=symbol;this.caption=caption;}
        public Viewport viewport(){return viewport;}public String resource(){return OWNER+":id/"+symbol;}public String caption(){return caption;}
        public String caption(String language){if(language.equals("zh"))return caption;if(language.equals("en"))return this==FACTORY_RESET?"Factory reset":"Chinese (Simplified)";throw new IllegalArgumentException("stock OEM EN/ZH required");}
        public String role(){return "stock."+name().toLowerCase(java.util.Locale.ROOT);}
    }
    /** The owning driver retains raw status/observe receipts and independently fences ready boot/Enforcing. */
    public record Authority(String profile,String session,String boot,int androidPid,String quickbootMode,JsonObject status,JsonObject rootAbsence,Path receipt){
        public Authority {status=status.deepCopy();rootAbsence=rootAbsence.deepCopy();}
        @Override public JsonObject status(){return status.deepCopy();}@Override public JsonObject rootAbsence(){return rootAbsence.deepCopy();}
    }
    public interface Port {
        Authority authority()throws Exception;
        /** Closed observed navigation only. System action is read-only; never tap the reset control. */
        Path open(Viewport viewport)throws Exception;
        JsonObject ui()throws Exception;
        /** A new absolute primary-only full SDK frame path under the API-owned build/tmp root. */
        Path captureDirectory(Viewport viewport)throws Exception;
        JsonObject capture(Path directory)throws Exception;
        Path save(String name,JsonObject evidence)throws Exception;
    }
    public record Candidate(Path evidence,JsonObject proof,List<SettingsRasterCapture.Frame> frames){
        public Candidate{proof=proof.deepCopy();frames=List.copyOf(frames);}@Override public JsonObject proof(){return proof.deepCopy();}
    }
    private StockSettingsBaseline(){}
    private static IOException invalid(String why){return new IOException("stock OEM reference unavailable: "+why);}
    private static String text(JsonObject o,String key)throws IOException{JsonElement v=o.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString()||v.getAsString().isBlank())throw invalid("text "+key);return v.getAsString();}
    private static JsonObject object(JsonObject o,String key)throws IOException{if(!o.has(key)||!o.get(key).isJsonObject())throw invalid("object "+key);return o.getAsJsonObject(key);}
    private static boolean yes(JsonObject o,String key){JsonElement v=o.get(key);return v!=null&&v.isJsonPrimitive()&&v.getAsJsonPrimitive().isBoolean()&&v.getAsBoolean();}
    static void authority(Authority a)throws IOException{
        try{
            if(a.profile()==null||a.profile().isBlank()||a.session()==null||!a.session().matches("[0-9a-f]{32}")||a.boot()==null||!a.boot().matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")||a.androidPid()<1||!Set.of("cold","sdk_cold_fallback").contains(a.quickbootMode())||a.receipt()==null||!a.receipt().isAbsolute())throw invalid("current cold owned machine authority");
            if(!"absent".equals(text(a.status(),"route")))throw invalid("management route is not absent");JsonObject root=a.rootAbsence();
            if(!root.has("schema")||!root.get("schema").isJsonPrimitive()||!root.getAsJsonPrimitive("schema").isNumber()||!"2".equals(root.get("schema").getAsString())||!"voboost".equals(text(root,"source"))||!"ready".equals(text(root,"status"))||!a.boot().equals(text(root,"boot_id"))||!"absent".equals(text(object(root,"data"),"root")))throw invalid("independent same-boot root absence");
        }catch(RuntimeException bad){throw new IOException("malformed stock absence authority",bad);}
    }
    static JsonObject measure(JsonObject ui,Authority a,Viewport viewport)throws IOException{return measure(ui,a,viewport,"zh");}
    static JsonObject measure(JsonObject ui,Authority a,Viewport viewport,String language)throws IOException{
        if(!Set.of("en","zh").contains(language))throw invalid("stock EN/ZH required");
        authority(a);try{
            JsonObject source=SettingsMeasurements.observationSurface(ui,a.profile(),a.session(),a.boot()),surface=SettingsMeasurements.ownerSurface(source);
            if(!ui.has("android_pid")||!ui.get("android_pid").isJsonPrimitive()||!ui.getAsJsonPrimitive("android_pid").isNumber()||ui.get("android_pid").getAsInt()!=a.androidPid()||!ui.has("host_interval"))throw invalid("actual owned UI process/clock");
            for(JsonElement e:surface.getAsJsonArray("nodes")){JsonObject n=e.getAsJsonObject();if(n.has("text")&&n.get("text").isJsonPrimitive()&&n.get("text").getAsString().matches("Voboost(?: .*)?"))throw invalid("Voboost contribution remains in supposedly stock tree");}
            if(viewport==Viewport.LANGUAGE_RADIO&&(!language.equals(SystemLanguagePicker.read(surface).language())||SystemLanguagePicker.read(surface).russian()!=null))throw invalid("original selected OEM locale radio required");
            JsonArray rows=new JsonArray();for(Control control:Control.values())if(control.viewport()==viewport){
                JsonObject found=null;for(JsonElement e:surface.getAsJsonArray("nodes")){JsonObject n=e.getAsJsonObject();if(n.has("resource_id")&&n.get("resource_id").isJsonPrimitive()&&n.getAsJsonPrimitive("resource_id").isString()&&control.resource().equals(n.get("resource_id").getAsString())){if(found!=null)throw invalid("ambiguous stock symbolic control");found=n;}}
                if(found==null)throw new UiEvidence.Unavailable("current stock symbolic control not visible: "+control.resource());
                if(!yes(found,"present")||!yes(found,"visible")||!control.caption(language).equals(text(found,"text")))throw invalid("current stock locale/visibility differs");
                JsonObject layout=object(found,"layout"),style=object(found,"text_style"),bounds=object(found,"bounds"),clipped=object(found,"clipped_bounds");
                if(!layout.has("measured_width")||!layout.has("measured_height"))throw invalid("actual stock View getters missing");for(String metric:List.of("text_size_px","text_scale_x","letter_spacing")){JsonElement v=style.get(metric);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber()||!Double.isFinite(v.getAsDouble())||Math.abs(v.getAsDouble())>65536||!metric.equals("letter_spacing")&&v.getAsDouble()<=0)throw invalid("actual stock text getter missing: "+metric);}
                JsonObject row=new JsonObject();row.addProperty("role",control.role());row.addProperty("value",control.caption(language));row.addProperty("nodeId",text(found,"id"));row.addProperty("resourceId",control.resource());row.add("layout",layout.deepCopy());row.add("textStyle",style.deepCopy());row.add("bounds",bounds.deepCopy());row.add("clippedBounds",clipped.deepCopy());row.addProperty("actualText",control.caption(language));if(found.has("text_layout"))row.add("textLayout",found.get("text_layout").deepCopy());rows.add(row);
            }
            JsonObject result=new JsonObject();result.addProperty("stockOem",true);result.addProperty("viewMetricsVerified",true);result.addProperty("profile",a.profile());result.addProperty("sessionId",a.session());result.addProperty("bootId",a.boot());result.addProperty("androidPid",a.androidPid());result.addProperty("sourceInstance",text(ui,"source_instance"));result.addProperty("observationId",text(ui,"observation_id"));result.add("hostInterval",object(ui,"host_interval").deepCopy());result.add("rows",rows);return result;
        }catch(RuntimeException bad){throw new IOException("malformed actual stock Settings observation",bad);}
    }
    /** Replays one reviewed source-owned stock control from portable original observations. */
    static JsonObject measureReference(JsonObject ui, Authority authority, String resourceId, String caption) throws IOException {
        authority(authority);
        if (resourceId == null || !resourceId.startsWith(OWNER + ":id/") || caption == null || caption.isEmpty())
            throw invalid("reviewed owning resource and caption required");
        JsonObject source = SettingsMeasurements.observationSurface(ui, authority.profile(), authority.session(), authority.boot());
        JsonObject surface = SettingsMeasurements.ownerSurface(source);
        if (!ui.has("android_pid") || !ui.get("android_pid").isJsonPrimitive()
                || !ui.getAsJsonPrimitive("android_pid").isNumber() || ui.get("android_pid").getAsInt() != authority.androidPid())
            throw invalid("reference Android PID");
        JsonObject found = null;
        for (JsonElement element : surface.getAsJsonArray("nodes")) {
            JsonObject node = element.getAsJsonObject();
            if (node.has("text") && node.get("text").isJsonPrimitive()
                    && node.get("text").getAsString().matches("Voboost(?: .*)?")) throw invalid("contribution remains");
            if (node.has("resource_id") && resourceId.equals(node.get("resource_id").getAsString())
                    && node.has("text") && node.get("text").isJsonPrimitive()
                    && caption.replace("\n", "\\n").replace("\r", "\\r").equals(node.get("text").getAsString())) {
                if (found != null) throw invalid("ambiguous reviewed resource");
                found = node;
            }
        }
        if (found == null || !yes(found, "present") || !yes(found, "visible")
                || !caption.replace("\n", "\\n").replace("\r", "\\r").equals(text(found, "text")))
            throw invalid("reviewed stock caption is absent or differs");
        JsonObject row = new JsonObject();
        row.addProperty("role", "stock.reference"); row.addProperty("value", caption);
        row.addProperty("nodeId", text(found, "id")); row.addProperty("resourceId", resourceId);
        row.addProperty("actualText", text(found, "text"));
        row.add("layout", object(found, "layout").deepCopy());
        row.add("textStyle", object(found, "text_style").deepCopy());
        row.add("textLayout", object(found, "text_layout").deepCopy());
        row.add("bounds", object(found, "bounds").deepCopy());
        row.add("clippedBounds", object(found, "clipped_bounds").deepCopy());
        SettingsTextLayout.read(row, caption);
        JsonObject result = new JsonObject();
        result.addProperty("stockOem", true); result.addProperty("viewMetricsVerified", true);
        result.addProperty("profile", authority.profile()); result.addProperty("sessionId", authority.session());
        result.addProperty("bootId", authority.boot()); result.addProperty("androidPid", authority.androidPid());
        result.addProperty("sourceInstance", text(ui, "source_instance"));
        result.addProperty("observationId", text(ui, "observation_id"));
        result.add("hostInterval", object(ui, "host_interval").deepCopy());
        JsonArray rows = new JsonArray(); rows.add(row); result.add("rows", rows);
        return result;
    }
    /** Returns reviewable actual pixels, never an automatically approved baseline. */
    public static Candidate collect(Port port,Viewport viewport)throws Exception{return collect(port,viewport,"zh");}
    public static Candidate collect(Port port,Viewport viewport,String language)throws Exception{
        if(!Set.of("en","zh").contains(language))throw invalid("stock EN/ZH required");
        Authority initial=port.authority();authority(initial);Path navigation=port.open(viewport);if(navigation==null||!navigation.isAbsolute())throw invalid("retained observed navigation receipt");
        Authority before=port.authority();same(initial,before);JsonObject beforeUi=port.ui(),measuredBefore=measure(beforeUi,before,viewport,language);
        Path directory=port.captureDirectory(viewport);JsonObject capture=port.capture(directory);JsonObject afterUi=port.ui();Authority after=port.authority();same(before,after);JsonObject measuredAfter=measure(afterUi,after,viewport,language);
        var frames=new java.util.ArrayList<SettingsRasterCapture.Frame>();JsonArray controls=new JsonArray();for(Control control:Control.values())if(control.viewport()==viewport){var frame=SettingsRasterCapture.readStock(capture,directory,measuredBefore,measuredAfter,control.role(),control.caption(language));frames.add(frame);controls.add(frame.scope());}
        JsonObject proof=new JsonObject();proof.addProperty("kind","emulator-stock-oem-before-install");proof.addProperty("viewport",viewport.name());proof.addProperty("language",language);proof.addProperty("profile",before.profile());proof.addProperty("sessionId",before.session());proof.addProperty("bootId",before.boot());proof.addProperty("androidPid",before.androidPid());proof.addProperty("coldMode",before.quickbootMode());proof.addProperty("authorityBefore",before.receipt().toString());proof.addProperty("authorityAfter",after.receipt().toString());proof.addProperty("navigation",navigation.toString());proof.addProperty("captureManifest",directory.resolve("manifest.json").toString());proof.addProperty("capturePng",directory.resolve("primary.png").toString());proof.add("uiBefore",beforeUi);proof.add("uiAfter",afterUi);proof.add("controls",controls);proof.addProperty("rootReviewRequired",true);proof.addProperty("referenceAccepted",false);proof.addProperty("hardwareFidelityAccepted",false);proof.addProperty("publicationApproval",false);
        Path file=port.save("stock-reference-"+language+"-"+viewport.name().toLowerCase(java.util.Locale.ROOT),proof);return new Candidate(file,proof,frames);
    }
    private static void same(Authority before,Authority after)throws IOException{authority(after);if(!before.profile().equals(after.profile())||!before.session().equals(after.session())||!before.boot().equals(after.boot())||before.androidPid()!=after.androidPid()||!before.quickbootMode().equals(after.quickbootMode()))throw invalid("stock baseline crossed its cold owned incarnation");}
}
