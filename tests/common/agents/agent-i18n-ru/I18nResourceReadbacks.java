package ru.voboost.tests.agents;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Seven individually fenced owner resource probes; selected rendered content is separate. */
public final class I18nResourceReadbacks {
    static final Map<String,List<String>> PROFILES=Map.of(
        "com.qinggan.app.setting",List.of("setting-a9","setting-a11"),
        "com.qinggan.app.launcher",List.of("launcher-a9","launcher-a11"),
        "com.qinggan.app.vehiclesetting",List.of("vehiclesetting-a9","vehiclesetting-a11"),
        "com.qinggan.app.settingnewwork",List.of("settingnetwork-a9","settingnetwork-a11"),
        "com.qinggan.systemui",List.of("systemui-a9","systemui-a11"),
        "com.qinggan.bluetoothphone",List.of("bluetoothphone-a9","bluetoothphone-a11"),
        "com.qinggan.media",List.of("media-a9","media-a11"));
    public record TargetReadback(String owner,String profile,String probeRevision,String targetPid,
            String targetStarttime,String endpointEpoch,String generationId,String catalogIdentity,
            String desiredDocumentGeneration) {}
    public record Proof(String kind,int size,Map<String,TargetReadback> targets) {
        public Proof { targets=Map.copyOf(targets); if(size!=targets.size()) throw new IllegalArgumentException("target count differs"); }
        public TargetReadback requireTarget(String owner)throws IOException {
            TargetReadback result=targets.get(owner);
            if(result==null) throw new IOException("resource readback missing exact target: "+owner);
            return result;
        }
    }
    /** Two ordered post-input observations of the same authenticated publisher. */
    public static final class PublicationFence {
        private final String publisher,generation,catalog;
        private final java.math.BigInteger floor;
        private PublicationFence(String publisher,String generation,String catalog,java.math.BigInteger floor){this.publisher=publisher;this.generation=generation;this.catalog=catalog;this.floor=floor;}
    }
    public static PublicationFence afterPublication(RuntimeObservation first,RuntimeObservation later)throws IOException {
        JsonObject before=first.runtime(),after=later.runtime();
        if(!first.publisher().equals(later.publisher())||!text(before,"generationId").equals(text(after,"generationId"))||!text(before,"catalogSha256").equals(text(after,"catalogSha256")))throw new IOException("publication fence changed owner or generation");
        java.math.BigInteger previous=publicationClock(before),current=publicationClock(after);
        if(current.compareTo(previous)<=0)throw new IOException("a later actual publisher BOOTTIME is required");
        return new PublicationFence(later.publisher(),text(after,"generationId"),text(after,"catalogSha256"),current);
    }
    public static Proof readCurrentAfter(RuntimeObservation runtime,String locale,PublicationFence fence)throws IOException {
        if(fence==null)throw new IOException("actual post-input publication fence required");
        return read(runtime,"i18nCurrentReadbacks",kind(locale),25000,fence);
    }
    public static Proof read(RuntimeObservation runtime)throws IOException {
        return read(runtime,"i18nResourceReadbacks","forced-ru-resource-readback",25000,null);
    }
    /** Actual owner Resources in a fresh root-issued current probe; visible rendering is separate. */
    public static Proof readCurrent(RuntimeObservation runtime)throws IOException {
        return readCurrent(runtime,"ru");
    }
    public static Proof readCurrent(RuntimeObservation runtime,String locale)throws IOException {
        return readCurrent(runtime,locale,Long.MAX_VALUE);
    }
    /** elapsedMs is measured on the host from before dispatch to after runtime capture. */
    public static Proof readCurrent(RuntimeObservation runtime,String locale,long elapsedMs)throws IOException {
        if(elapsedMs<5000)throw new IOException("owner proof cannot yet be fenced after locale dispatch");
        long maxAge=Math.min(25000,elapsedMs-5000);
        return read(runtime,"i18nCurrentReadbacks",kind(locale),maxAge,null);
    }
    private static String kind(String locale)throws IOException {
        return switch(locale) {
            case "ru" -> "current-owner-resource-readback";
            case "en" -> "current-en-owner-resource-readback";
            case "zh" -> "current-zh-owner-resource-readback";
            default -> throw new IOException("closed EN/RU/ZH locale required");
        };
    }
    private static Proof read(RuntimeObservation runtime,String field,String kind,long maxAge,PublicationFence fence)throws IOException {
        try {
            if (!runtime.desired("i18n-ru")) throw new IOException("Russian contribution is not desired");
            JsonObject publication=runtime.runtime();JsonObject report=publication.getAsJsonObject(field);
            java.math.BigInteger published=null;
            if(fence!=null){
                if(!runtime.publisher().equals(fence.publisher)||!text(publication,"generationId").equals(fence.generation)||!text(publication,"catalogSha256").equals(fence.catalog))throw new IOException("current resource proof crossed publication fence");
                published=publicationClock(publication);
            }
            if(report==null||!report.keySet().equals(Set.of("schema","kind","owners"))||!number(report,"schema","1")||!kind.equals(text(report,"kind"))||!report.get("owners").isJsonArray())throw new IOException("requested locale resource readbacks unavailable");
            var owners=report.getAsJsonArray("owners");if(owners.size()!=PROFILES.size())throw new IOException("seven resource owners required");
            Set<String> seen=new HashSet<>();
            Map<String,TargetReadback> targetProofs=new java.util.HashMap<>();
            for(JsonElement item:owners) {
                JsonObject row=item.getAsJsonObject();if(!row.keySet().equals(Set.of("owner","profile","probeRevision","ageMs","binding")))throw new IOException("resource readback fields differ");
                String owner=text(row,"owner"),profile=text(row,"profile"),revision=text(row,"probeRevision");
                if(!seen.add(owner)||!PROFILES.containsKey(owner)||!PROFILES.get(owner).contains(profile)||!positive(revision)||!age(row,maxAge))throw new IOException("resource readback owner/profile/revision/freshness differs");
                if(fence!=null&&published.subtract(new java.math.BigInteger(row.get("ageMs").getAsString())).compareTo(fence.floor)<=0)throw new IOException("owner resource observation did not follow post-input guest fence");
                JsonObject binding=row.getAsJsonObject("binding");if(binding==null||!text(binding,"generationId").equals(text(publication,"generationId"))||!text(binding,"catalogSha256").equals(text(publication,"catalogSha256")))throw new IOException("resource generation/catalog differs");
                for(String key:List.of("targetPid","targetStarttime","endpointEpoch"))if(!positive(text(binding,key)))throw new IOException("resource endpoint identity unavailable");
                if(!decimal(text(binding,"desiredDocumentGeneration")))throw new IOException("resource document identity unavailable");
                JsonObject target=null;for(JsonObject candidate:runtime.targets("i18n-ru"))if(owner.equals(text(candidate,"process")))target=candidate;
                if(target==null||!"active".equals(text(target,"state"))||!target.has("currentEndpoint")||!target.get("currentEndpoint").isJsonObject())throw new IOException("resource owner endpoint no longer current");
                JsonObject endpoint=target.getAsJsonObject("currentEndpoint");
                for(String key:List.of("targetPid","targetStarttime","endpointEpoch","generationId","catalogSha256","desiredDocumentGeneration"))
                    if(!text(binding,key).equals(text(endpoint,key)))throw new IOException("resource owner endpoint changed: "+key);
                targetProofs.put(owner,new TargetReadback(owner,profile,revision,text(binding,"targetPid"),
                    text(binding,"targetStarttime"),text(binding,"endpointEpoch"),text(binding,"generationId"),
                    text(binding,"catalogSha256"),text(binding,"desiredDocumentGeneration")));
            }
            return new Proof(kind,owners.size(),targetProofs);
        }catch(RuntimeException malformed){throw new IOException("malformed resource readback publication",malformed);}
    }
    private static java.math.BigInteger publicationClock(JsonObject publication)throws IOException {
        JsonObject owner=publication.has("publisher")&&publication.get("publisher").isJsonObject()?publication.getAsJsonObject("publisher"):null;
        String time=text(owner,"publishedAtBootMs");if(!positive(time))throw new IOException("authenticated publisher BOOTTIME unavailable");
        return new java.math.BigInteger(time);
    }
    private static String text(JsonObject value,String key){return value!=null&&value.has(key)&&value.get(key).isJsonPrimitive()&&value.getAsJsonPrimitive(key).isString()?value.get(key).getAsString():"";}
    private static boolean number(JsonObject value,String key,String expected){return value.has(key)&&value.get(key).isJsonPrimitive()&&value.getAsJsonPrimitive(key).isNumber()&&expected.equals(value.get(key).getAsString());}
    private static boolean decimal(String value){if(!value.matches("0|[1-9][0-9]{0,19}"))return false;return new java.math.BigInteger(value).bitLength()<=64;}
    private static boolean positive(String value){return decimal(value)&&!value.equals("0");}
    private static boolean age(JsonObject row,long maxAge){JsonElement v=row.get("ageMs");return v!=null&&v.isJsonPrimitive()&&v.getAsJsonPrimitive().isNumber()&&decimal(v.getAsString())&&new java.math.BigInteger(v.getAsString()).compareTo(java.math.BigInteger.valueOf(maxAge))<=0;}
    private I18nResourceReadbacks(){}
}
