package ru.voboost.tests.agents;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

/** Per-owner locale evidence from the existing authenticated official native publication. */
public final class I18nObservation {
    public static final class Pending extends IOException {public Pending(String reason){super(reason);}}
    public static final Set<String> OWNERS=Set.of("gauge","point","light");
    public record Process(String pid,String birth,String endpoint,String lifecycle) {}
    private final RuntimeObservation runtime;
    private final JsonObject cluster,agent;
    private final Map<String,Process> processes;
    private I18nObservation(RuntimeObservation runtime,JsonObject cluster,JsonObject agent,Map<String,Process> processes) {
        this.runtime=runtime;this.cluster=cluster.deepCopy();this.agent=agent.deepCopy();this.processes=Map.copyOf(processes);
    }
    public static I18nObservation read(RuntimeObservation runtime)throws IOException {
        try {
            JsonObject publication=runtime.runtime();
            if(publication.has("clusterUnavailable")||!publication.has("cluster")||!publication.get("cluster").isJsonObject())throw new Pending("current native locale observation unavailable");
            JsonObject cluster=publication.getAsJsonObject("cluster");
            if(!"1".equals(number(cluster,"schemaVersion",false).toString())||!"cluster".equals(text(cluster,"domain"))||!"idle".equals(text(cluster,"deliveryState")))throw new IOException("native locale schema/domain/delivery differs");
            for(String key:Set.of("bootId","daemonEpoch","generationId","catalogSha256","desiredSha256"))text(cluster,key);
            for(String key:Set.of("snapshotRevision","observationRevision","desiredRevision"))number(cluster,key,false);
            JsonObject selected=null;
            for(var value:cluster.getAsJsonArray("agents")) {JsonObject row=value.getAsJsonObject();if("i18n-ru".equals(text(row,"id"))){if(selected!=null)throw new IOException("duplicate native Russian agent");selected=row;}}
            if(selected==null||!selected.has("enabled")||!selected.get("enabled").isJsonPrimitive()||!selected.getAsJsonPrimitive("enabled").isBoolean()||selected.get("enabled").getAsBoolean()!=runtime.desired("i18n-ru"))throw new IOException("native Russian desired state unavailable or differs");
            Map<String,Process> processes=new java.util.HashMap<>();
            for(var value:selected.getAsJsonArray("targets")) {
                JsonObject target=value.getAsJsonObject();String owner=text(target,"targetProcess");
                if(!"i18n-ru".equals(text(target,"id"))||!OWNERS.contains(owner)||processes.containsKey(owner))throw new IOException("native Russian owner set differs");
                JsonObject process=target.getAsJsonObject("process");if(number(process,"pid",false).compareTo(BigInteger.valueOf(Integer.MAX_VALUE))>0)throw new IOException("native PID exceeds process identity bound");number(process,"lifecycleGeneration",false);
                // These are existing protocol lifetime identities, never firmware/file compatibility gates.
                processes.put(owner,new Process(number(process,"pid",false).toString(),text(process,"birthIdentity"),text(process,"endpointEpoch"),number(process,"lifecycleGeneration",false).toString()));
                text(target,"state");text(target,"reason");
            }
            if(!processes.keySet().equals(OWNERS))throw new IOException("incomplete native Russian per-owner evidence");
            return new I18nObservation(runtime,cluster,selected,processes);
        }catch(RuntimeException malformed){throw new IOException("malformed native Russian owner evidence",malformed);}
    }
    public void requireLocale(String locale,I18nObservation before)throws IOException {
        String reason=switch(locale){case "en"->"confirmed English locale and selected dictionary";case "zh"->"confirmed Chinese locale and selected dictionary";case "ru"->"confirmed Russian locale and selected dictionary";default->throw new IOException("unsupported locale evidence");};
        if(before!=null){requireSameScope(before);requireAdvance(before);if(!processes.equals(before.processes))throw new IOException("locale apply replaced native owner lifetimes");}
        if(!runtime.desired("i18n-ru")||!"applied".equals(text(agent,"state")))throw new Pending("Russian desired/applied state unavailable");
        for(var value:agent.getAsJsonArray("targets")){JsonObject target=value.getAsJsonObject();if(!"applied".equals(text(target,"state"))||!reason.equals(text(target,"reason")))throw new Pending("native owner selected dictionary locale not confirmed");}
    }
    public void requireDisabledRestart(I18nObservation before)throws IOException {
        requireSameScope(before);requireAdvance(before);
        if(runtime.desired("i18n-ru")||number(cluster,"desiredRevision",false).compareTo(number(before.cluster,"desiredRevision",false))<=0)throw new Pending("native disable intent did not advance");
        for(var value:agent.getAsJsonArray("targets")) {
            JsonObject target=value.getAsJsonObject();String owner=text(target,"targetProcess");Process prior=before.processes.get(owner),current=processes.get(owner);
            if(!"disabled".equals(text(target,"state"))||prior.pid.equals(current.pid)||prior.birth.equals(current.birth)||prior.endpoint.equals(current.endpoint))throw new Pending("disabled native owner did not independently restart");
        }
    }
    public void requireRestored(I18nObservation before)throws IOException {
        if(androidBoot().equals(before.androidBoot())||text(cluster,"bootId").equals(text(before.cluster,"bootId"))||text(cluster,"daemonEpoch").equals(text(before.cluster,"daemonEpoch")))throw new IOException("restoration needs changed Android publisher and native boot/daemon");
        for(String key:Set.of("generationId","catalogSha256","desiredSha256","desiredRevision"))if(!cluster.get(key).equals(before.cluster.get(key)))throw new IOException("restored native authority differs");
        requireLocale("ru",null);
        for(String owner:OWNERS){Process prior=before.processes.get(owner),current=processes.get(owner);if(prior.birth.equals(current.birth)||prior.endpoint.equals(current.endpoint))throw new IOException("restored native owner identity was retained");}
    }
    public void requireSameScope(I18nObservation before)throws IOException {
        if(!runtime.publisher().equals(before.runtime.publisher()))throw new IOException("native locale Android publisher changed");
        for(String key:Set.of("bootId","daemonEpoch","generationId","catalogSha256"))if(!cluster.get(key).equals(before.cluster.get(key)))throw new IOException("native locale boot/owner/generation changed");
    }
    public void requireCurrentWindow(I18nObservation before)throws IOException {
        requireSameScope(before);if(!processes.equals(before.processes))throw new IOException("native locale owner lifetimes changed during admission window");
        for(String key:Set.of("snapshotRevision","observationRevision","desiredRevision"))if(number(cluster,key,false).compareTo(number(before.cluster,key,false))<0)throw new IOException("native locale admission window regressed");
    }
    public void requireAdvance(I18nObservation before)throws IOException {
        for(String key:Set.of("snapshotRevision","observationRevision"))if(number(cluster,key,false).compareTo(number(before.cluster,key,false))<=0)throw new Pending("native locale response did not independently advance");
        if(number(cluster,"desiredRevision",false).compareTo(number(before.cluster,"desiredRevision",false))<0)throw new IOException("native locale desired revision regressed");
    }
    private String androidBoot(){return runtime.publisher().substring(0,runtime.publisher().indexOf(':'));}
    public JsonObject evidence(){JsonObject result=new JsonObject();result.addProperty("publisher",runtime.publisher());result.addProperty("configRevision",runtime.revision());result.add("snapshot",cluster.deepCopy());return result;}
    private static String text(JsonObject value,String key)throws IOException {var field=value.get(key);if(field==null||!field.isJsonPrimitive()||!field.getAsJsonPrimitive().isString()||field.getAsString().isBlank()||field.getAsString().length()>256)throw new IOException("bounded native field missing: "+key);return field.getAsString();}
    private static BigInteger number(JsonObject value,String key,boolean zero)throws IOException {var field=value.get(key);if(field==null||!field.isJsonPrimitive()||!field.getAsJsonPrimitive().isNumber()||!field.getAsString().matches("0|[1-9][0-9]{0,19}"))throw new IOException("exact native integer missing: "+key);BigInteger number=new BigInteger(field.getAsString());if(number.bitLength()>64||!zero&&number.signum()==0)throw new IOException("native integer out of bounds: "+key);return number;}
}
