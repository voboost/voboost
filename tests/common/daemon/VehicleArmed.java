package ru.voboost.voyemu;

import com.google.gson.JsonObject;
import java.io.IOException;

/** Fixed sampled durable vehicle intent only; never operation-time acknowledgement or continuous CAN feedback. */
public final class VehicleArmed {
    private VehicleArmed(){}
    public static boolean read(JsonObject observation,String boot)throws IOException {
        return readFixed(observation,boot,"state/vehicle-drive-forced-ev_forced-ev-armed");
    }
    public static boolean readPedestrian(JsonObject observation,String boot)throws IOException {
        return readFixed(observation,boot,"state/vehicle-drive-pedestrian-warning_pedestrian-warning-armed");
    }
    public static boolean read(ru.voboost.tests.agents.RuntimeObservation observation)throws IOException {
        return readNative(observation,"forcedEv");
    }
    public static boolean readPedestrian(ru.voboost.tests.agents.RuntimeObservation observation)throws IOException {
        return readNative(observation,"pedestrianWarning");
    }
    public static String pedestrianIntent(ru.voboost.tests.agents.RuntimeObservation observation)throws IOException {
        Boolean armed=readNative(observation,"pedestrianWarning",true);
        return armed==null?"observe":armed?"on":"off";
    }
    /** CAN is sampled after the tap/readback using Android elapsedRealtimeNanos (guest BOOTTIME). */
    public static void requireAfter(ru.voboost.tests.agents.RuntimeObservation current,
            VehicleCan.Snapshot afterInput)throws IOException {
        JsonObject publisher=current.runtime().getAsJsonObject("publisher");
        java.math.BigInteger floor=new java.math.BigInteger(afterInput.sampled()).divide(java.math.BigInteger.valueOf(1_000_000));
        if(!afterInput.boot().equals(publisher.get("bootId").getAsString())
                ||clock(publisher,"publishedAtBootMs").compareTo(floor)<=0)
            throw new UiEvidence.Unavailable("durable vehicle sample does not follow the post-input guest CAN sample");
    }
    private static boolean readNative(ru.voboost.tests.agents.RuntimeObservation observation,String key)throws IOException {
        return readNative(observation,key,false);
    }
    private static Boolean readNative(ru.voboost.tests.agents.RuntimeObservation observation,String key,boolean allowMissing)throws IOException {
        try {
            JsonObject runtime=observation.runtime(),publisher=runtime.getAsJsonObject("publisher");
            if(!runtime.has("persistentVehicleState")||!runtime.get("persistentVehicleState").isJsonObject())
                throw new UiEvidence.Unavailable("native durable vehicle state unavailable");
            JsonObject state=runtime.getAsJsonObject("persistentVehicleState");
            if(!state.keySet().equals(java.util.Set.of("bootId","observedAtBootMs","forcedEv","pedestrianWarning"))
                    ||!state.get("bootId").isJsonPrimitive()||!state.getAsJsonPrimitive("bootId").isString()
                    ||!publisher.get("bootId").equals(state.get("bootId")))throw new IOException("native durable state boot differs");
            java.math.BigInteger published=clock(publisher,"publishedAtBootMs"),sampled=clock(state,"observedAtBootMs");
            // The owner samples both files after creating this publication's BOOTTIME publisher stamp.
            if(sampled.compareTo(published)<0||sampled.subtract(published).compareTo(java.math.BigInteger.valueOf(5000))>0)
                throw new IOException("native durable state sample is outside its current publication");
            JsonObject flag=state.getAsJsonObject(key);String outcome=flag.get("outcome").getAsString();
            if(allowMissing&&"missing".equals(outcome)&&flag.keySet().equals(java.util.Set.of("outcome"))) return null;
            if(java.util.Set.of("missing","unavailable").contains(outcome)&&flag.keySet().equals(java.util.Set.of("outcome")))
                throw new UiEvidence.Unavailable("native durable vehicle intent is "+outcome);
            if(!"value".equals(outcome)||!flag.keySet().equals(java.util.Set.of("outcome","value"))
                    ||!flag.get("value").isJsonPrimitive()||!flag.getAsJsonPrimitive("value").isBoolean())
                throw new IOException("native durable vehicle boolean unavailable");
            return flag.get("value").getAsBoolean();
        }catch(IllegalStateException|NullPointerException malformed){throw new IOException("malformed native durable vehicle state",malformed);}
    }
    private static java.math.BigInteger clock(JsonObject value,String key)throws IOException {
        if(!value.has(key)||!value.get(key).isJsonPrimitive())throw new IOException("native durable state clock missing");
        String raw=value.get(key).getAsString();
        if(!raw.matches("0|[1-9][0-9]{0,19}")||new java.math.BigInteger(raw).bitLength()>64)
            throw new IOException("native durable state clock invalid");
        return new java.math.BigInteger(raw);
    }
    private static boolean readFixed(JsonObject observation,String boot,String key)throws IOException {
        try {
            if(!observation.has("schema")||!observation.get("schema").isJsonPrimitive()||!observation.getAsJsonPrimitive("schema").isNumber()||!"2".equals(observation.get("schema").getAsString())||!"voboost".equals(observation.get("source").getAsString())||!"ready".equals(observation.get("status").getAsString())||!boot.equals(observation.get("boot_id").getAsString()))throw new IOException("durable FEV observation scope differs");
            JsonObject data=observation.getAsJsonObject("data");if(!"present".equals(data.get("root").getAsString())||!"fixed-path-owner-mode-boot-fenced".equals(data.get("root_proof").getAsString()))throw new IOException("durable FEV root proof differs");
            JsonObject files=data.getAsJsonObject("files");
            if(!files.has(key)||!files.get(key).isJsonObject())throw new UiEvidence.Unavailable("official observer does not expose fixed durable FEV intent");
            JsonObject state=files.getAsJsonObject(key);
            if(!state.has("status")||!state.get("status").isJsonPrimitive()||!state.getAsJsonPrimitive("status").isString())throw new IOException("durable FEV status missing");
            String status=state.get("status").getAsString();
            if(java.util.Set.of("absent","unavailable").contains(status))throw new UiEvidence.Unavailable("fixed durable FEV intent is "+status);
            if(!"present".equals(status))throw new IOException("unknown durable FEV status");
            if(!state.has("current")||!state.get("current").isJsonPrimitive()||!state.getAsJsonPrimitive("current").isBoolean()||!state.get("current").getAsBoolean()||!"same-boot-retained-file".equals(state.get("proof").getAsString())||!state.has("revision")||!state.get("revision").isJsonPrimitive()||!state.getAsJsonPrimitive("revision").isString()||state.get("revision").getAsString().isBlank()||state.get("revision").getAsString().length()>256||!state.has("data")||!state.get("data").isJsonPrimitive()||!state.getAsJsonPrimitive("data").isBoolean())throw new IOException("actual same-boot durable FEV boolean unavailable");
            return state.get("data").getAsBoolean();
        }catch(IllegalStateException|NullPointerException malformed){throw new IOException("malformed durable FEV receipt",malformed);}
    }
}
