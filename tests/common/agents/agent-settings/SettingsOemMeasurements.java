package ru.voboost.voyemu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ru.voboost.tests.agents.RuntimeObservation;
import ru.voboost.tests.logging.InstallerLogs;

/** Authenticated current OEM roles joined to actual all_attached View getters, never class-name guesses. */
public final class SettingsOemMeasurements {
    private static final String OWNER="com.qinggan.app.setting";
    private SettingsOemMeasurements(){}
    private static IOException invalid(String reason){return new IOException("OEM Settings measurement unavailable: "+reason);}
    private static String text(JsonObject v,String key)throws IOException {JsonElement f=v.get(key);if(f==null||!f.isJsonPrimitive()||!f.getAsJsonPrimitive().isString()||f.getAsString().isBlank()||f.getAsString().length()>256)throw invalid("text "+key);return f.getAsString();}
    private static String scalar(JsonObject v,String key)throws IOException {JsonElement f=v.get(key);if(f==null||!f.isJsonPrimitive()||!f.getAsJsonPrimitive().isString()||!f.getAsString().matches("0|[1-9][0-9]{0,19}")||new BigInteger(f.getAsString()).bitLength()>64)throw invalid("decimal "+key);return f.getAsString();}
    private static JsonObject object(JsonObject v,String key)throws IOException {if(!v.has(key)||!v.get(key).isJsonObject())throw invalid("object "+key);return v.getAsJsonObject(key);}
    private static int number(JsonObject v,String key,boolean signed)throws IOException {JsonElement f=v.get(key);if(f==null||!f.isJsonPrimitive()||!f.getAsJsonPrimitive().isNumber()||!f.getAsString().matches(signed?"-?[0-9]{1,5}":"[0-9]{1,5}")||Math.abs(f.getAsInt())>65536)throw invalid("integer "+key);return f.getAsInt();}
    private static double metric(JsonObject v,String key)throws IOException {JsonElement f=v.get(key);if(f==null||!f.isJsonPrimitive()||!f.getAsJsonPrimitive().isNumber()||!Double.isFinite(f.getAsDouble())||Math.abs(f.getAsDouble())>65536)throw invalid("actual metric "+key);return f.getAsDouble();}
    private static void keys(JsonObject v,String... names)throws IOException {if(!v.keySet().equals(Set.of(names)))throw invalid("closed fields differ");}
    private static String key(JsonObject f)throws IOException{return text(f,"role")+"\n"+text(f,"value");}
    private static void owner(InstallerLogs.Diagnostic row,JsonObject endpoint,String boot,String[] publisher)throws IOException {
        if(!"ui-qinggan".equals(row.agent())||!"measured".equals(row.outcome()))throw invalid("diagnostic producer");
        JsonObject source=row.sourceFields();
        if(!OWNER.equals(text(source,"process"))||!boot.equals(text(source,"bootId"))||!publisher[1].equals(scalar(source,"daemonPid"))||!publisher[2].equals(scalar(source,"daemonStarttime")))throw invalid("diagnostic boot/process/writer");
        for(String field:List.of("targetPid","targetStarttime","endpointEpoch","generationId","catalogSha256","desiredDocumentGeneration"))if(!text(endpoint,field).equals(text(source,field)))throw invalid("diagnostic current endpoint "+field);
    }
    private static JsonObject unique(List<InstallerLogs.Diagnostic> rows,String event,String roleKey)throws IOException {
        JsonObject found=null;for(var row:rows)if(event.equals(row.event())&&roleKey.equals(key(row.fields()))){if(found!=null)throw invalid("duplicate role measurement");found=row.fields();}if(found==null)throw new UiEvidence.Unavailable("fresh OEM role measurement is not fully exported: "+event);return found;
    }
    private static JsonObject node(JsonObject surface,String identity)throws IOException {
        if(!identity.matches("[A-Za-z_$][A-Za-z0-9_.$]*@[0-9a-f]{1,8}"))throw invalid("ViewDebug identity codec");
        JsonObject found=null;for(JsonElement v:surface.getAsJsonArray("nodes")){JsonObject n=v.getAsJsonObject();if(text(n,"id").equals(text(n,"window_id")+":"+identity)){if(found!=null)throw invalid("ambiguous ViewDebug identity");found=n;}}if(found==null)throw invalid("actual View identity absent");return found;
    }
    private static void descendant(JsonObject surface,JsonObject child,JsonObject root)throws IOException {
        Map<String,JsonObject> nodes=new HashMap<>();for(JsonElement v:surface.getAsJsonArray("nodes")){JsonObject n=v.getAsJsonObject();nodes.put(text(n,"id"),n);}Set<String> seen=new HashSet<>();
        while(!text(child,"id").equals(text(root,"id"))){String id=text(child,"id");if(!seen.add(id)||seen.size()>128||!text(child,"window_id").equals(text(root,"window_id")))throw invalid("crossed role root");JsonElement p=child.get("parent");if(p==null||p.isJsonNull()||!p.isJsonPrimitive())throw invalid("role root ancestor missing");child=nodes.get(p.getAsString());if(child==null)throw invalid("role root ancestor absent");}
    }
    private static void origin(JsonObject view,JsonObject root,JsonObject identity,JsonObject measuredRoot)throws IOException {
        JsonObject bounds=object(view,"bounds");int x=number(bounds,"x",true),y=number(bounds,"y",true),expectedX=number(identity,"screenX",true),expectedY=number(identity,"screenY",true);
        if(measuredRoot!=null){JsonObject actualRoot=object(root,"bounds");x-=number(actualRoot,"x",true);y-=number(actualRoot,"y",true);expectedX-=number(measuredRoot,"screenX",true);expectedY-=number(measuredRoot,"screenY",true);}
        if(x!=expectedX||y!=expectedY)throw invalid("actual role origin differs inside publication root");
    }
    private static JsonObject boundView(JsonObject surface,JsonObject identity,String expectedRoot,Set<String> used,JsonObject rootMeasurement)throws IOException {
        keys(identity,"captureId","role","value","viewIdentity","rootIdentity","screenX","screenY");String id=text(identity,"viewIdentity");if(!used.add(id)||!expectedRoot.equals(text(identity,"rootIdentity")))throw invalid("colliding or foreign published role identity");JsonObject view=node(surface,id);descendant(surface,view,node(surface,expectedRoot));origin(view,node(surface,expectedRoot),identity,rootMeasurement);return view;
    }
    public static JsonObject read(InstallerLogs.Snapshot baseline,InstallerLogs.Snapshot current,RuntimeObservation runtime,JsonObject observation,String profile,String session,String boot,String language,int caseNumber)throws IOException {
        return readPublished(baseline,current,runtime,observation,profile,session,boot,language,caseNumber,false);
    }
    public static JsonObject readDialog(InstallerLogs.Snapshot baseline,InstallerLogs.Snapshot current,RuntimeObservation runtime,JsonObject observation,String profile,String session,String boot,String language)throws IOException {
        return readPublished(baseline,current,runtime,observation,profile,session,boot,language,21,true);
    }
    /** The message is joined inside the already authenticated exact dialog root, never by another window's text. */
    public static JsonObject dialogMessage(JsonObject measured,JsonObject observation,String expectedCaption)throws IOException {
        if (!"dialog".equals(text(measured,"surface")) || !text(measured,"observationId").equals(text(observation,"observation_id"))
                || expectedCaption==null || expectedCaption.isEmpty() || expectedCaption.length()>4096) throw invalid("exact current dialog observation required");
        JsonObject observed=SettingsMeasurements.observationSurface(observation,text(measured,"profile"),text(measured,"sessionId"),text(measured,"bootId"));
        JsonObject surface=SettingsMeasurements.rootSurface(observed,text(measured,"rootIdentity"),true);
        if (!text(surface.getAsJsonArray("windows").get(0).getAsJsonObject(),"id").equals(text(measured,"windowId"))) throw invalid("dialog window differs");
        JsonObject found=null;String wire=expectedCaption.replace("\n","\\n").replace("\r","\\r");
        for(JsonElement item:surface.getAsJsonArray("nodes")){
            JsonObject n=item.getAsJsonObject();if(n.has("text")&&n.get("text").isJsonPrimitive()&&wire.equals(n.get("text").getAsString())){
                if(found!=null)throw invalid("ambiguous owning dialog message");found=n;
            }
        }
        if(found==null)throw new UiEvidence.Unavailable("actual owning dialog message not observed");
        descendant(surface,found,node(surface,text(measured,"rootIdentity")));
        JsonObject out=measured.deepCopy(),row=new JsonObject();row.addProperty("kind","text");row.addProperty("role","dialog.message");row.addProperty("value","message");
        row.addProperty("nodeId",text(found,"id"));row.addProperty("actualText",wire);
        for(String[] pair:List.of(new String[]{"layout","layout"},new String[]{"text_style","textStyle"},new String[]{"text_layout","textLayout"},new String[]{"bounds","bounds"},new String[]{"clipped_bounds","clippedBounds"}))row.add(pair[1],object(found,pair[0]).deepCopy());
        SettingsTextLayout.read(row,expectedCaption);out.getAsJsonArray("rows").add(row);return out;
    }
    private static JsonObject readPublished(InstallerLogs.Snapshot baseline,InstallerLogs.Snapshot current,RuntimeObservation runtime,JsonObject observation,String profile,String session,String boot,String language,int caseNumber,boolean dialog)throws IOException {
        if(!Set.of(20,21).contains(caseNumber)||!Set.of("en","ru","zh").contains(language)||!boot.equals(current.boot())||!boot.equals(baseline.boot()))throw invalid("closed case/language/boot");
        current.after(baseline);if(!current.sequence().equals(current.watermark()))throw new UiEvidence.Unavailable("current OEM layout tail has not reached the writer watermark");
        try {
            JsonObject observedSurface=SettingsMeasurements.observationSurface(observation,profile,session,boot);
            String[] publisher=runtime.publisher().split(":",-1);if(publisher.length!=3||!publisher[0].equals(boot)||!runtime.desired("settings"))throw invalid("current Settings publisher");
            List<JsonObject> targets=runtime.targets("settings").stream().filter(r->OWNER.equals(r.get("process").getAsString())&&"active".equals(r.get("state").getAsString())).toList();if(targets.size()!=1)throw invalid("unique live Settings target");
            JsonObject endpoint=object(targets.get(0),"currentEndpoint"),publication=runtime.runtime();
            for(String field:List.of("targetPid","targetStarttime","endpointEpoch"))if(new BigInteger(scalar(endpoint,field)).signum()==0)throw invalid("positive endpoint "+field);
            if(!text(endpoint,"generationId").equals(text(publication,"generationId"))||!text(endpoint,"catalogSha256").equals(text(publication,"catalogSha256")))throw invalid("target publication differs");
            String revision=scalar(endpoint,"desiredDocumentGeneration");JsonObject accepted=object(endpoint,"acceptedStatus"),receipt=object(accepted,"receipt");
            if(!revision.equals(scalar(accepted,"documentGeneration"))||!boot.equals(text(receipt,"bootId"))||!publisher[1].equals(scalar(receipt,"daemonPid"))||!publisher[2].equals(scalar(receipt,"daemonStarttime"))||new BigInteger(scalar(receipt,"sequence")).signum()==0)throw invalid("current accepted source receipt");
            List<InstallerLogs.Diagnostic> fresh=current.after(baseline);InstallerLogs.Diagnostic capture=null;
            for(var row:fresh)if(row.agent().equals("ui-qinggan")&&row.event().equals("ui.layout.capture")&&language.equals(row.fields().has("effectiveLanguage")?row.fields().get("effectiveLanguage").getAsString():"")&&(dialog?"dialog":"panel").equals(row.fields().has("surface")?row.fields().get("surface").getAsString():"")&&(dialog?"pre-click":"after-layout").equals(row.fields().has("phase")?row.fields().get("phase").getAsString():"")){owner(row,endpoint,boot,publisher);capture=row;}
            if(capture==null)throw new UiEvidence.Unavailable("fresh current OEM layout capture missing");JsonObject header=capture.fields();
            keys(header,"captureId","desiredRevision","snapshotSha256","generationId","catalogSha256","effectiveLanguage","count","rootWidth","surface","phase","sections");
            if(!(dialog?"dialog":"panel").equals(text(header,"surface"))||!(dialog?"pre-click":"after-layout").equals(text(header,"phase"))||!revision.equals(scalar(header,"desiredRevision"))||!text(endpoint,"generationId").equals(text(header,"generationId"))||!text(endpoint,"catalogSha256").equals(text(header,"catalogSha256")))throw invalid("current capture publication differs");
            InstallerLogs.DaemonEvent queued=null;for(var row:current.daemonEvents())if(row.sequence().compareTo(capture.sequence())<0&&"runtime".equals(row.component())&&"desired-snapshot-queued".equals(row.message())&&OWNER.equals(row.fields().has("process")?row.fields().get("process").getAsString():""))if(queued==null||row.sequence().compareTo(queued.sequence())>0)queued=row;
            if(queued==null)throw invalid("authenticated queued publication before capture missing");JsonObject queuedFields=queued.fields();
            for(String field:List.of("generationId","catalogSha256","targetPid","targetStarttime","endpointEpoch"))if(!text(endpoint,field).equals(text(queuedFields,field)))throw invalid("queued publication differs "+field);
            if(!revision.equals(scalar(queuedFields,"documentGeneration"))||!text(header,"snapshotSha256").equals(text(queuedFields,"snapshotSha256")))throw invalid("queued snapshot/capture differs");
            for(var row:current.daemonEvents())if(row.sequence().compareTo(capture.sequence())>0&&"desired-snapshot-queued".equals(row.message())&&OWNER.equals(row.fields().has("process")?row.fields().get("process").getAsString():""))throw invalid("newer Settings publication after capture");
            BigInteger captureSequence=capture.sequence();String captureId=text(header,"captureId");List<InstallerLogs.Diagnostic> rows=fresh.stream().filter(r->r.fields().has("captureId")&&captureId.equals(r.fields().get("captureId").getAsString())&&r.sequence().compareTo(captureSequence)>0).toList();
            Map<String,JsonObject> metrics=new HashMap<>();for(var row:rows){owner(row,endpoint,boot,publisher);if(row.event().equals("ui.layout")){if(metrics.putIfAbsent(key(row.fields()),row.fields())!=null)throw invalid("duplicate role caption");}}
            if(metrics.size()<number(header,"count",false)||metrics.isEmpty())throw new UiEvidence.Unavailable("fresh current caption role inventory is not fully exported");if(metrics.size()!=number(header,"count",false))throw invalid("caption role inventory count differs");String rootIdentity=text(unique(rows,"ui.layout.identity",metrics.keySet().iterator().next()),"rootIdentity");JsonObject surface=SettingsMeasurements.rootSurface(observedSurface,rootIdentity,dialog),rootMeasurement=null;
            for(var row:rows)if(row.event().equals("ui.layout.root")){if(rootMeasurement!=null)throw invalid("duplicate publication root measurement");rootMeasurement=row.fields();keys(rootMeasurement,"captureId","rootIdentity","screenX","screenY");if(!rootIdentity.equals(text(rootMeasurement,"rootIdentity")))throw invalid("publication root measurement differs");}
            if(dialog){JsonObject selected=surface.getAsJsonArray("windows").get(0).getAsJsonObject();if(!"2".equals(selected.get("type").getAsString()))throw invalid("independent application dialog window required");boolean separate=false;for(JsonElement item:observedSurface.getAsJsonArray("windows")){JsonObject w=item.getAsJsonObject();if(OWNER.equals(w.has("package")&&!w.get("package").isJsonNull()?w.get("package").getAsString():"")&&w.has("type")&&!w.get("type").isJsonNull()&&"1".equals(w.get("type").getAsString())&&!text(w,"id").equals(text(selected,"id")))separate=true;}if(!separate)throw invalid("dialog lacks separate owning application window");}
            JsonArray resultRows=new JsonArray(),sectionBodies=new JsonArray();Set<String> identities=new HashSet<>();Set<String> roles=new HashSet<>();
            for(var entry:metrics.entrySet()){
                String roleKey=entry.getKey();JsonObject values=entry.getValue(),geometry=unique(rows,"ui.layout.geometry",roleKey),identity=unique(rows,"ui.layout.identity",roleKey);
                keys(identity,"captureId","role","value","viewIdentity","rootIdentity","screenX","screenY");String viewIdentity=text(identity,"viewIdentity");if(!identities.add(viewIdentity))throw invalid("colliding role View identities");if(!rootIdentity.equals(text(identity,"rootIdentity")))throw invalid("role crossed publication root");
                JsonObject view=node(surface,viewIdentity),root=node(surface,rootIdentity);descendant(surface,view,root);JsonObject layout=object(view,"layout"),style=object(view,"text_style"),bounds=object(view,"bounds");
                String kind=viewIdentity.substring(0,viewIdentity.indexOf('@'));if(!kind.equals(text(view,"class"))||!kind.equals(text(geometry,"class")))throw invalid("role class differs from actual object");
                origin(view,root,identity,rootMeasurement);
                if(number(layout,"measured_width",false)!=number(values,"width",false)||number(layout,"measured_height",false)!=number(geometry,"height",false)||metric(style,"text_size_px")!=metric(geometry,"textSize"))throw invalid("role View getters differ from source measurement");
                int width=number(values,"width",false),height=number(geometry,"height",false),left=number(values,"padding_left",false),right=number(values,"padding_right",false),available=number(geometry,"layoutWidth",false);
                if(width<1||height<1||available<1||available!=width-left-right||number(values,"ellipsis",false)!=0||number(values,"lines",false)<1||number(values,"lines",false)==1&&number(values,"caption_px",false)>available||number(values,"bottom",false)-number(values,"top",false)!=height)throw invalid("actual caption clipping/geometry differs");
                String role=text(values,"role");roles.add(role);JsonObject out=new JsonObject();out.addProperty("kind","text");out.addProperty("role",role);out.addProperty("value",text(values,"value"));out.addProperty("viewIdentity",viewIdentity);out.addProperty("nodeId",text(view,"id"));out.add("layout",layout.deepCopy());out.add("textStyle",style.deepCopy());out.add("bounds",bounds.deepCopy());out.add("clippedBounds",object(view,"clipped_bounds").deepCopy());if(view.has("text_layout"))out.add("textLayout",view.get("text_layout").deepCopy());if(view.has("text")&&view.get("text").isJsonPrimitive())out.add("actualText",view.get("text").deepCopy());out.add("sourceCaptionMetrics",values.deepCopy());out.add("sourceGeometry",geometry.deepCopy());out.addProperty("paintCaptionPx",number(values,"caption_px",false));resultRows.add(out);
            }
            if(caseNumber==20&&!dialog){
                Set<String> startupValues=new HashSet<>();for(JsonElement row:resultRows){JsonObject r=row.getAsJsonObject();if("startup".equals(text(r,"role")))startupValues.add(text(r,"value"));}if(!startupValues.equals(Set.of("interface","hidden","none")))throw invalid("current three-option startup radio inventory absent");
                var switches=rows.stream().filter(r->r.event().equals("ui.layout.checkbox")).toList();var bodies=rows.stream().filter(r->r.event().equals("ui.layout.section.body")).toList();
                if(switches.isEmpty()||bodies.size()<number(header,"sections",false))throw new UiEvidence.Unavailable("fresh OEM checkbox/section role inventory is not fully exported");
                if(bodies.size()!=number(header,"sections",false))throw invalid("section inventory differs");
                for(var row:switches){JsonObject f=row.fields();String role="checkbox."+text(f,"key")+".control";JsonObject identity=unique(rows,"ui.layout.identity",role+"\n"+text(f,"key"));JsonObject view=boundView(surface,identity,rootIdentity,identities,rootMeasurement);JsonObject layout=object(view,"layout");if(number(layout,"measured_width",false)!=number(f,"width",false)||number(layout,"measured_height",false)!=number(f,"height",false)||number(f,"bottom",false)-number(f,"top",false)!=number(f,"height",false))throw invalid("actual OEM checkbox geometry differs");JsonObject out=new JsonObject();out.addProperty("kind","checkbox");out.addProperty("role",role);out.addProperty("value",text(f,"key"));out.addProperty("nodeId",text(view,"id"));out.add("layout",layout.deepCopy());out.add("textStyle",new JsonObject());out.add("bounds",object(view,"bounds").deepCopy());out.add("clippedBounds",object(view,"clipped_bounds").deepCopy());if(view.has("checked")&&view.get("checked").isJsonPrimitive()&&view.getAsJsonPrimitive("checked").isBoolean())out.add("checked",view.get("checked").deepCopy());resultRows.add(out);}
                Set<String> bodyKeys=new HashSet<>();Set<Integer> indices=new HashSet<>();for(var row:bodies){JsonObject f=row.fields();String bodyKey=text(f,"key");if(!bodyKeys.add(bodyKey)||!indices.add(number(f,"index",false)))throw invalid("duplicate section body");JsonObject identity=unique(rows,"ui.layout.identity","section."+bodyKey+".body\n"+bodyKey);JsonObject view=boundView(surface,identity,rootIdentity,identities,rootMeasurement);JsonObject layout=object(view,"layout");if(!text(view,"class").equals(text(f,"class"))||number(layout,"measured_width",false)!=number(f,"width",false)||number(layout,"measured_height",false)!=number(f,"height",false)||number(layout,"padding_top",false)!=number(f,"paddingTop",false)||number(layout,"padding_bottom",false)!=number(f,"paddingBottom",false))throw invalid("actual OEM section body getters differ");JsonObject body=f.deepCopy();body.add("actualLayout",layout.deepCopy());sectionBodies.add(body);}
                for(int index=0;index<bodies.size();index++)if(!indices.contains(index))throw invalid("section index inventory gap");
            }
            if(dialog&&(!roles.equals(Set.of("dialog.confirm","dialog.cancel"))||metrics.size()!=2)||!dialog&&caseNumber==21&&!roles.contains("uninstall.action")||caseNumber==20&&!roles.contains("startup"))throw invalid("required current Settings role inventory absent");
            JsonObject result=new JsonObject();result.addProperty("caseNumber",caseNumber);result.addProperty("surface",dialog?"dialog":"panel");result.addProperty("phase",dialog?"pre-click":"after-layout");result.addProperty("rootIdentity",rootIdentity);result.addProperty("windowId",text(surface.getAsJsonArray("windows").get(0).getAsJsonObject(),"id"));result.addProperty("sourceRootObserved",rootMeasurement!=null);if(node(surface,rootIdentity).has("bounds"))result.add("rootBounds",node(surface,rootIdentity).get("bounds").deepCopy());result.addProperty("targetPid",text(endpoint,"targetPid"));result.addProperty("targetStarttime",text(endpoint,"targetStarttime"));result.addProperty("endpointEpoch",text(endpoint,"endpointEpoch"));result.addProperty("profile",profile);result.addProperty("sessionId",session);result.addProperty("bootId",boot);result.addProperty("observationId",text(observation,"observation_id"));result.addProperty("sourceInstance",text(observation,"source_instance"));result.addProperty("generationId",text(endpoint,"generationId"));result.addProperty("catalogIdentity",text(endpoint,"catalogSha256"));result.addProperty("documentGeneration",revision);if(observation.has("android_pid"))result.add("androidPid",observation.get("android_pid").deepCopy());if(observation.has("host_interval"))result.add("hostInterval",observation.get("host_interval").deepCopy());result.addProperty("captureId",captureId);result.addProperty("language",language);result.addProperty("measurementBasis","authenticated OEM role/Paint plus current actual ViewDumpBridge object");result.addProperty("viewMetricsVerified",true);result.addProperty("canvasTextPaddingVerified",false);result.addProperty("visualAcceptance",false);result.add("rows",resultRows);result.add("sectionBodies",sectionBodies);return result;
        }catch(RuntimeException malformed){throw new IOException("malformed current OEM layout observation",malformed);}
    }
}
