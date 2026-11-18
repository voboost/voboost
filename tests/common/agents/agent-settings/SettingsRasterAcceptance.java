package ru.voboost.voyemu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ru.voboost.tests.ui.SettingsCopySource;

/** Complete source-copy inventory joined to authenticated measurements and actual per-control SDK pixels. */
public final class SettingsRasterAcceptance {
    private record Expected(String role,String value,String caption,String viewClass,boolean checkbox) {
        String key(){return role+"\n"+value;}
    }
    public static final class Inventory {
        private final int number;private final String language;private final List<Expected> expected;
        private final JsonObject geometry,topology;private final Path source;private final String catalogIdentity;private final SettingsCopySource.Proof sourceAuthority;
        private Inventory(int number,String language,List<Expected> expected,JsonObject geometry,JsonObject topology,Path source,String catalogIdentity,SettingsCopySource.Proof sourceAuthority){
            this.number=number;this.language=language;this.expected=List.copyOf(expected);this.geometry=geometry.deepCopy();this.topology=topology.deepCopy();this.source=source;this.catalogIdentity=catalogIdentity;this.sourceAuthority=sourceAuthority;
        }
        public int caseNumber(){return number;}public String language(){return language;}
        public Set<String> keys(){Set<String> keys=new HashSet<>();for(var expected:expected)keys.add(expected.key());return Set.copyOf(keys);}
        public Path source(){return source;}
    }
    /** Measurements come from SettingsOemMeasurements, not resource XML or Accessibility guesses. */
    public record Sample(JsonObject capture,Path directory,JsonObject before,JsonObject after,String role,String value,String model) {
        public Sample {capture=capture.deepCopy();before=before.deepCopy();after=after.deepCopy();}
        @Override public JsonObject capture(){return capture.deepCopy();}@Override public JsonObject before(){return before.deepCopy();}@Override public JsonObject after(){return after.deepCopy();}
    }
    public static final class Result {
        private final JsonObject proof;
        private Result(JsonObject proof){this.proof=proof.deepCopy();}
        /** Only the complete visual/layout criterion. The caller still checks actions, locale programs and source admission. */
        public boolean accepted(){return proof.get("visualAcceptance").getAsBoolean();}
        public JsonObject proof(){return proof.deepCopy();}
    }
    private SettingsRasterAcceptance(){}
    private static IOException invalid(String reason){return new IOException("Settings raster acceptance unavailable: "+reason);}
    private static String text(JsonObject row,String key)throws IOException{
        var value=row.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()||value.getAsString().isBlank())throw invalid(key);return value.getAsString();
    }
    private static int integer(JsonObject row,String key)throws IOException{
        var value=row.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber()||!Double.isFinite(value.getAsDouble())||value.getAsDouble()!=Math.rint(value.getAsDouble())||Math.abs(value.getAsDouble())>65536)throw invalid(key);return value.getAsInt();
    }
    private static boolean yes(JsonObject row,String key){var value=row.get(key);return value!=null&&value.isJsonPrimitive()&&value.getAsJsonPrimitive().isBoolean()&&value.getAsBoolean();}
    private static String caption(JsonObject copy,String role,String value,String startup)throws IOException{
        if(role.equals("startup"))for(var item:copy.getAsJsonArray("startup")){var option=item.getAsJsonObject();if(value.equals(text(option,"value")))return text(option,"label");}
        if(role.equals("startup.title"))return text(copy,"startupTitle");
        if(role.equals("startup.description"))for(var item:copy.getAsJsonArray("startup")){var option=item.getAsJsonObject();if(startup.equals(text(option,"value")))return text(option,"description");}
        if(role.startsWith("checkbox."))for(var item:copy.getAsJsonArray("checkboxes")){var checkbox=item.getAsJsonObject();if(value.equals(text(checkbox,"id")))return text(checkbox,role.endsWith(".title")?"title":"description");}
        if(role.startsWith("section."))return switch(value){case"startup"->text(copy,"commonTitle");case"vehicle"->text(copy,"vehicleGroup");case"interface"->text(copy,"interfaceGroup");case"unstable"->text(copy,"unstableGroup");case"uninstall"->text(copy.getAsJsonObject("uninstall"),"group");default->throw invalid("unknown owning section "+value);};
        var removal=copy.getAsJsonObject("uninstall");
        return switch(role){case"uninstall.action"->text(removal,"buttonText");case"uninstall.status","dialog.message"->text(removal,"description");case"dialog.confirm"->text(removal,"confirmLabel");case"dialog.cancel"->text(removal,"cancelLabel");default->throw invalid("unhandled owning copy role "+role);};
    }
    public static Inventory inventory(SettingsCopySource.Proof source,int number,String language,String startup)throws IOException {
        if(source==null)throw invalid("fresh owning source-copy proof required");return inventory(source.copy(),source.evidence(),source.catalogIdentity(),source,number,language,startup);
    }
    static Inventory inventory(JsonObject source,Path sourceEvidence,String catalogIdentity,int number,String language,String startup)throws IOException {return inventory(source,sourceEvidence,catalogIdentity,null,number,language,startup);}
    private static Inventory inventory(JsonObject source,Path sourceEvidence,String catalogIdentity,SettingsCopySource.Proof sourceAuthority,int number,String language,String startup)throws IOException {
        try {
            if(catalogIdentity==null||catalogIdentity.isBlank()||catalogIdentity.length()>256)throw invalid("owning existing catalog identity required");
            if(!Set.of(20,21).contains(number)||!Set.of("en","ru","zh").contains(language)||!Set.of("interface","hidden","none").contains(startup)||sourceEvidence==null||!sourceEvidence.isAbsolute())throw invalid("closed case/locale/startup");
            JsonObject copy=source.getAsJsonObject("languages").getAsJsonObject(language),topology=copy.getAsJsonObject("layoutTopology");
            List<Expected> expected=new ArrayList<>();
            for(var item:topology.getAsJsonArray("panelTargets")) {
                JsonObject row=item.getAsJsonObject();String role=text(row,"role"),value=text(row,"value");
                if(number==21&&!role.startsWith("uninstall.")&&!role.equals("section.uninstall.title"))continue;
                expected.add(new Expected(role,value,caption(copy,role,value,startup),text(row,"class"),false));
            }
            if(number==20)for(var item:topology.getAsJsonArray("checkboxSwitches")) {
                String key=text(item.getAsJsonObject(),"key");expected.add(new Expected("checkbox."+key+".control",key,"","com.pateo.material.widgets.QGCheckBox",true));
            }
            if(number==21){
                for(var item:topology.getAsJsonArray("dialogTargets")){var row=item.getAsJsonObject();String role=text(row,"role"),value=text(row,"value");expected.add(new Expected(role,value,caption(copy,role,value,startup),text(row,"class"),false));}
                expected.add(new Expected("dialog.message","message",caption(copy,"dialog.message","message",startup),"",false));
            }
            if(expected.isEmpty()||expected.size()>256||new HashSet<>(expected.stream().map(Expected::key).toList()).size()!=expected.size())throw invalid("complete unique source role inventory");
            Set<String> roles=new HashSet<>(expected.stream().map(Expected::role).toList());
            if(number==20&&(!roles.contains("startup")||!roles.contains("startup.description")||expected.stream().noneMatch(Expected::checkbox))
                    ||number==21&&!roles.containsAll(Set.of("uninstall.action","uninstall.status","dialog.confirm","dialog.cancel","dialog.message")))throw invalid("mandatory owning roles absent");
            return new Inventory(number,language,expected,source.getAsJsonObject("geometry"),topology,sourceEvidence,catalogIdentity,sourceAuthority);
        }catch(RuntimeException error){throw new IOException("malformed owning Settings copy",error);}
    }
    private static JsonObject target(JsonObject measured,String role,String value)throws IOException {
        JsonObject found=null;for(var item:measured.getAsJsonArray("rows")){var row=item.getAsJsonObject();if(role.equals(text(row,"role"))&&value.equals(text(row,"value"))){if(found!=null)throw invalid("duplicate measurement role");found=row;}}
        if(found==null)throw invalid("required actual role absent");return found;
    }
    private static void scope(Inventory inventory,Sample sample,String profile,Map<String,String> identity)throws IOException {
        JsonObject before=sample.before();
        if(inventory.sourceAuthority!=null)inventory.sourceAuthority.requireCatalogIdentity(text(before,"catalogIdentity"));
        else if(!inventory.catalogIdentity.equals(text(before,"catalogIdentity")))throw invalid("actual catalog differs from owning source copy");
        if(integer(before,"caseNumber")!=inventory.number||!inventory.language.equals(text(before,"language"))||!profile.equals(text(before,"profile"))||!yes(before,"sourceRootObserved")||!yes(before,"viewMetricsVerified"))throw invalid("current source-bound native root required");
        for(String key:List.of("profile","sessionId","bootId","generationId","catalogIdentity","targetPid","targetStarttime","endpointEpoch","androidPid")){
            String value=before.has(key)?before.get(key).getAsString():"";if(value.isBlank())throw invalid("source identity "+key);
            String old=identity.putIfAbsent(key,value);if(old!=null&&!old.equals(value))throw invalid("mixed boot/endpoint/candidate program "+key);
        }
        boolean dialog=sample.role().startsWith("dialog.");
        if(!(dialog?"dialog":"panel").equals(text(before,"surface"))||!(dialog?"pre-click":"after-layout").equals(text(before,"phase")))throw invalid("separate dialog versus panel source surface");
    }
    private static String family(String role)throws IOException {
        if(role.equals("uninstall.action"))return "button";
        if(role.equals("startup"))return "radio";
        if(role.equals("dialog.confirm")||role.equals("dialog.cancel"))return "dialog-action";
        if(role.equals("dialog.message"))return "dialog-message";
        if(role.endsWith(".title"))return "heading";
        if(role.endsWith(".description")||role.endsWith(".status")||role.endsWith(".descriptionAbove"))return "body";
        throw invalid("unknown owning font family "+role);
    }
    private static void geometry(Inventory inventory,Expected expected,JsonObject row,SettingsRasterCapture.Frame frame)throws IOException {
        var source=inventory.geometry;
        if(expected.checkbox){if(frame.width()!=83||frame.height()!=integer(source,"CHECKBOX_HEIGHT")||!row.has("checked")||!row.get("checked").isJsonPrimitive()||!row.getAsJsonPrimitive("checked").isBoolean())throw invalid("actual checkbox state/geometry");return;}
        SettingsTextLayout text=SettingsTextLayout.read(row,expected.caption);
        if(expected.role.equals("startup")){
            JsonObject paint=row.getAsJsonObject("sourceCaptionMetrics");
            if(paint==null||frame.height()!=integer(source,"RADIO_HEIGHT")||text.style().size()!=integer(source,"RADIO_TEXT")||frame.width()!=Math.max(integer(source,"RADIO_ITEM_MIN_WIDTH"),integer(paint,"caption_px")+2*integer(source,"RADIO_ITEM_PADDING")))throw invalid("source adaptive radio geometry");
        }
        if(expected.role.startsWith("section.")&&expected.role.endsWith(".title")&&frame.height()!=integer(source,"SECTION_HEADER"))throw invalid("source section header geometry");
        if(expected.role.equals("uninstall.action")){
            JsonObject paint=row.getAsJsonObject("sourceCaptionMetrics");
            if(paint==null||frame.height()<integer(source,"BUTTON_HEIGHT")||integer(paint,"padding_left")!=integer(source,"BUTTON_HORIZONTAL_PADDING")||integer(paint,"padding_right")!=integer(source,"BUTTON_HORIZONTAL_PADDING"))throw invalid("real owning action ink padding");
            int inset=integer(source,"BUTTON_HORIZONTAL_PADDING");
            if(text.paddingLeft()!=inset||text.paddingRight()!=inset)throw invalid("actual native action padding differs");
            for(var line:text.lines())if(text.lineX(line)<inset||text.lineX(line)+(line.right()-line.left())>frame.width()-inset)throw invalid("wrapped action text cannot retain normative insets");
        }
    }
    private static JsonObject sectionGeometry(JsonObject measured,JsonObject body)throws IOException {
        if(!text(measured,"captureId").equals(text(body,"captureId")))throw invalid("section belongs to another source capture");
        JsonObject stable=new JsonObject();
        stable.addProperty("key",text(body,"key"));stable.addProperty("class",text(body,"class"));
        // top/bottom are sums of getTop() inside the published root, not screen coordinates.
        // Scrolling the root's ancestor moves rootBounds; it does not change this body geometry.
        for(String key:List.of("index","top","bottom","width","height","paddingTop","paddingBottom","firstChildTop","lastChildBottom"))
            stable.addProperty(key,integer(body,key));
        JsonObject actual=body.getAsJsonObject("actualLayout");if(actual==null)throw invalid("actual section View getters missing");
        if(integer(actual,"measured_width")!=integer(body,"width")||integer(actual,"measured_height")!=integer(body,"height")
                ||integer(actual,"padding_top")!=integer(body,"paddingTop")||integer(actual,"padding_bottom")!=integer(body,"paddingBottom"))
            throw invalid("section source geometry differs from actual View getters");
        JsonObject layout=new JsonObject();
        for(String key:List.of("measured_width","measured_height","min_width","min_height","padding_left","padding_right","padding_top","padding_bottom","scroll_x","scroll_y","layout_width","layout_height","margin_left","margin_top","margin_right","margin_bottom"))
            if(actual.has(key))layout.addProperty(key,integer(actual,key));
        if(actual.has("params_type"))layout.addProperty("params_type",text(actual,"params_type"));
        stable.add("actualLayout",layout);return stable;
    }
    private static void sections(Inventory inventory,List<Sample> samples)throws IOException {
        if(inventory.number!=20)return;
        JsonArray expected=inventory.topology.getAsJsonArray("sectionBodies");
        if(expected==null||expected.isEmpty())throw invalid("owning section inventory required");
        Set<String> declared=new HashSet<>();for(var item:expected)if(!declared.add(text(item.getAsJsonObject(),"key")))throw invalid("duplicate owning section");
        Map<String,JsonObject> bodies=new HashMap<>();
        for(var sample:samples)for(var measured:List.of(sample.before(),sample.after())){
            JsonArray observed=measured.getAsJsonArray("sectionBodies");
            if(observed==null||observed.size()!=expected.size())throw invalid("complete source section inventory required for each observation");
            Set<String> seen=new HashSet<>();
            for(var item:observed){
                JsonObject stable=sectionGeometry(measured,item.getAsJsonObject());String key=text(stable,"key");
                if(!seen.add(key)||!declared.contains(key))throw invalid("duplicate or foreign source section");
                JsonObject old=bodies.putIfAbsent(key,stable);
                if(old!=null&&!old.equals(stable))throw invalid("section geometry changed within locale program");
            }
        }
        Integer previous=null;
        for(var item:expected){var declaredSection=item.getAsJsonObject();var actual=bodies.get(text(declaredSection,"key"));
            if(actual==null||integer(actual,"index")!=integer(declaredSection,"index")||integer(actual,"paddingTop")!=integer(inventory.geometry,"SECTION_TOP")||integer(actual,"paddingBottom")!=integer(inventory.geometry,"SECTION_BOTTOM"))throw invalid("source section body padding/order");
            int top=integer(actual,"top"),bottom=integer(actual,"bottom");
            if(bottom-top!=integer(actual,"height")||integer(actual,"firstChildTop")!=integer(actual,"paddingTop")||integer(actual,"lastChildBottom")!=integer(actual,"height")-integer(actual,"paddingBottom"))throw invalid("source section content spacing");
            if(previous!=null&&top-previous!=integer(inventory.geometry,"SECTION_GAP")+integer(inventory.geometry,"SECTION_HEADER"))throw invalid("source section inter-group spacing");previous=bottom;
        }
    }
    /** All expected roles are mandatory; one passing crop cannot accept the complete numbered case. */
    public static Result verify(Inventory inventory,StockFontReference reference,GlyphRasterOracle.Calibration checkboxReference,List<Sample> samples)throws IOException {
        if(inventory==null||reference==null||samples==null||samples.isEmpty()||samples.size()>256)throw invalid("complete actual program and reviewed stock reference required");
        Map<String,Sample> indexed=new LinkedHashMap<>();for(var sample:samples)if(indexed.putIfAbsent(sample.role()+"\n"+sample.value(),sample)!=null)throw invalid("duplicate raster role");
        if(!indexed.keySet().equals(inventory.keys()))throw invalid("actual raster role inventory differs from owning source");
        JsonArray proofs=new JsonArray();Map<String,String> identities=new HashMap<>();String panelWindow=null,dialogWindow=null;
        for(var expected:inventory.expected){
            Sample sample=indexed.get(expected.key());scope(inventory,sample,reference.profile(),identities);
            JsonObject before=sample.before(),row=target(before,expected.role,expected.value);
            String nodeId=text(row,"nodeId");if(!nodeId.matches(".+:[A-Za-z_$][A-Za-z0-9_.$]*@[0-9a-f]{1,8}"))throw invalid("actual ViewDebug role identity codec");String actualClass=nodeId.substring(nodeId.lastIndexOf(':')+1,nodeId.lastIndexOf('@'));
            boolean actionSubclass=expected.role.equals("uninstall.action")&&expected.viewClass.equals("android.widget.Button")&&actualClass.equals("ru.voboost.ui.qinggan.Button$ActionButton");
            if(!expected.viewClass.isEmpty()&&!expected.viewClass.equals(actualClass)&&!actionSubclass)throw invalid("owning role View class differs");
            var frame=SettingsRasterCapture.read(sample.capture(),sample.directory(),before,sample.after(),expected.role,expected.value);
            geometry(inventory,expected,row,frame);JsonObject criterion;
            if(expected.checkbox)criterion=GlyphRasterOracle.checkbox(frame.image(),frame.x(),frame.y(),row.get("checked").getAsBoolean(),checkboxReference);
            else criterion=reference.verify(sample.model(),family(expected.role),frame,expected.caption);
            JsonObject proof=new JsonObject();proof.addProperty("role",expected.role);proof.addProperty("value",expected.value);proof.add("scope",frame.scope());proof.add("criterion",criterion);proof.addProperty("png",frame.png().toString());proof.addProperty("manifest",frame.manifest().toString());proofs.add(proof);
            String window=text(before,"windowId");
            if(expected.role.startsWith("dialog.")){if(dialogWindow!=null&&!dialogWindow.equals(window))throw invalid("dialog roles crossed windows");dialogWindow=window;}
            else {if(panelWindow!=null&&!panelWindow.equals(window))throw invalid("panel roles crossed windows");panelWindow=window;}
        }
        if(inventory.number==21&&(panelWindow==null||dialogWindow==null||panelWindow.equals(dialogWindow)))throw invalid("independent dialog window proof absent");
        sections(inventory,samples);
        JsonObject result=new JsonObject();result.addProperty("schema",1);result.addProperty("caseNumber",inventory.number);result.addProperty("language",inventory.language);result.addProperty("sourceCopy",inventory.source.toString());result.addProperty("catalogIdentity",inventory.catalogIdentity);result.addProperty("reference",reference.evidence().toString());result.addProperty("roleCount",proofs.size());result.addProperty("viewMetricsVerified",true);result.addProperty("canvasTextPaddingVerified",true);result.addProperty("visualAcceptance",true);result.addProperty("hardwareFidelityAccepted",false);result.addProperty("publicationApproval",false);result.add("roles",proofs);return new Result(result);
    }
    private static JsonObject compact(JsonObject measured,String role,String value)throws IOException {
        JsonObject copy=measured.deepCopy();JsonArray rows=new JsonArray();rows.add(target(measured,role,value).deepCopy());copy.add("rows",rows);return copy;
    }
    /** Retains exact executable inputs, deduplicating unrelated role rows; never creates a golden reference. */
    public static Result write(Path output,Inventory inventory,StockFontReference reference,GlyphRasterOracle.Calibration checkboxes,List<Sample> samples)throws IOException {
        Result result=verify(inventory,reference,checkboxes,samples);JsonObject record=new JsonObject();record.addProperty("schema",1);record.addProperty("kind","settings-raster-inputs");record.add("result",result.proof());JsonArray rows=new JsonArray();
        for(var sample:samples){JsonObject row=new JsonObject();row.addProperty("role",sample.role());row.addProperty("value",sample.value());row.addProperty("model",sample.model());row.addProperty("directory",sample.directory().toString());row.add("capture",sample.capture());row.add("before",compact(sample.before(),sample.role(),sample.value()));row.add("after",compact(sample.after(),sample.role(),sample.value()));rows.add(row);}record.add("samples",rows);ru.voboost.tests.RunFiles.write(output,record);return result;
    }
    /** Reopens original PNG/manifest and native metrics; a stored PASS flag is not an oracle. */
    public static Result readProof(Path input,Inventory inventory,StockFontReference reference,GlyphRasterOracle.Calibration checkboxes)throws IOException {
        try {
            JsonObject record=ru.voboost.tests.RunFiles.read(input);
            if(integer(record,"schema")!=1||!"settings-raster-inputs".equals(text(record,"kind")))throw invalid("retained executable raster proof");
            JsonArray rows=record.getAsJsonArray("samples");if(rows==null||rows.isEmpty()||rows.size()>256)throw invalid("bounded actual sample inventory");List<Sample> samples=new ArrayList<>();
            for(var item:rows){var row=item.getAsJsonObject();samples.add(new Sample(row.getAsJsonObject("capture"),Path.of(text(row,"directory")),row.getAsJsonObject("before"),row.getAsJsonObject("after"),text(row,"role"),text(row,"value"),text(row,"model")));}
            Result result=verify(inventory,reference,checkboxes,samples);
            if(!result.proof().equals(record.get("result")))throw invalid("retained verdict differs from recomputed actual oracle");return result;
        }catch(RuntimeException error){throw new IOException("malformed retained Settings raster proof",error);}
    }

}
