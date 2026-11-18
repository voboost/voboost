package ru.voboost.voyemu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

/** Fixed-field public CAN getter evidence. Setter history is never physical continuous-bus coverage. */
public final class VehicleCan {
    public static final String FEV="vehicle_state.IVI_SOC_MODESET", PW="vehicle_state.HUM_VSP_FUNCTION_SW";
    private VehicleCan() {}
    public record Snapshot(String boot,String session,String source,long pid,String started,String sampled,String revision,int fev,int pw,JsonObject receipt) {
        public Snapshot { receipt=receipt.deepCopy(); }
        @Override public JsonObject receipt(){return receipt.deepCopy();}
        public boolean selected(boolean forcedEv){return forcedEv?fev==5:pw==2;}
        public void requireNext(Snapshot prior) throws IOException {
            if(!boot.equals(prior.boot)||!session.equals(prior.session)||!source.equals(prior.source)||pid!=prior.pid||!started.equals(prior.started)
                || decimal(sampled).compareTo(decimal(prior.sampled))<=0 || decimal(revision).compareTo(decimal(prior.revision))<0)
                throw new IOException("CAN source lifetime changed or sampled observation did not advance");
        }
        public void requireNewBoot(Snapshot prior) throws IOException {
            if(boot.equals(prior.boot)||!session.equals(prior.session)||source.equals(prior.source)) throw new IOException("CAN cold reboot did not establish a new boot/source lifetime");
            // Revisions are scoped to each service lifetime; no cross-boot numeric ordering is inferred.
        }
    }
    private static String text(JsonObject o,String k) throws IOException {
        if(!o.has(k)||!o.get(k).isJsonPrimitive()||!o.getAsJsonPrimitive(k).isString())throw new IOException("CAN string missing: "+k);
        String s=o.get(k).getAsString();if(s.isEmpty()||s.length()>256)throw new IOException("CAN string bound: "+k);return s;
    }
    private static JsonObject object(JsonObject o,String k)throws IOException {
        if(!o.has(k)||!o.get(k).isJsonObject())throw new IOException("CAN object missing: "+k);return o.getAsJsonObject(k);
    }
    private static boolean bool(JsonObject o,String k)throws IOException {
        if(!o.has(k)||!o.get(k).isJsonPrimitive()||!o.getAsJsonPrimitive(k).isBoolean())throw new IOException("CAN boolean missing: "+k);return o.get(k).getAsBoolean();
    }
    private static BigInteger decimal(String s)throws IOException {
        if(!s.matches("0|[1-9][0-9]{0,18}")||new BigInteger(s).compareTo(BigInteger.valueOf(Long.MAX_VALUE))>0)throw new IOException("CAN canonical decimal outside signed64");return new BigInteger(s);
    }
    private static long number(JsonObject o,String k)throws IOException {
        if(!o.has(k)||!o.get(k).isJsonPrimitive()||!o.getAsJsonPrimitive(k).isNumber()||!o.get(k).getAsString().matches("-?(?:0|[1-9][0-9]{0,18})"))throw new IOException("CAN actual integer missing: "+k);
        try{return Long.parseLong(o.get(k).getAsString());}catch(NumberFormatException e){throw new IOException("CAN integer range",e);}
    }
    private static int raw(JsonObject o,String field,String basis,int low,int high)throws IOException {
        JsonObject value=object(o,field);long raw=number(value,"raw");
        if(!"observed".equals(text(value,"status"))||!basis.equals(text(value,"basis"))||raw<low||raw>high)throw new IOException("CAN exact getter value unavailable: "+field);return (int)raw;
    }
    public record PowerSnapshot(String boot,String session,String source,long pid,String started,String sampled,
                                String revision,int acc,JsonObject receipt) {
        public PowerSnapshot { receipt=receipt.deepCopy(); }
        @Override public JsonObject receipt(){return receipt.deepCopy();}
        public boolean featureGettersPending()throws IOException {
            JsonObject values=object(receipt,"values");
            return getterPending(values,FEV,0,5) | getterPending(values,PW,1,2);
        }
        private static boolean getterPending(JsonObject values,String key,int low,int high)throws IOException {
            JsonObject entry=object(values,key);
            if("unavailable".equals(text(entry,"status")) && number(entry,"raw")==-1
                    && "ICanBusService.getVehicleState".equals(text(entry,"basis")))return true;
            raw(values,key,"ICanBusService.getVehicleState",low,high);
            return false;
        }
        public void requireNext(PowerSnapshot prior)throws IOException {
            if(!boot.equals(prior.boot)||!session.equals(prior.session)||!source.equals(prior.source)
                ||pid!=prior.pid||!started.equals(prior.started)||decimal(sampled).compareTo(decimal(prior.sampled))<=0
                ||decimal(revision).compareTo(decimal(prior.revision))<0)
                throw new IOException("CAN power source lifetime changed or sampled observation did not advance");
        }
    }
    public static int driveMode(PowerSnapshot sample) throws IOException {
        if (sample.acc() == 0) throw new IOException("Outing requires confirmed ACC on");
        return raw(object(sample.receipt(), "values"), "vehicle_state.DRIVING_MODE_SET",
                "ICanBusService.getVehicleState", 1, 6);
    }

    public static Snapshot read(JsonObject o,String session,String boot)throws IOException {
        PowerSnapshot power=readPower(o,session,boot);
        JsonObject values=object(o,"values");
        int fev=raw(values,FEV,"ICanBusService.getVehicleState",0,5);
        int pw=raw(values,PW,"ICanBusService.getVehicleState",1,2);
        return new Snapshot(boot,session,power.source(),power.pid(),power.started(),power.sampled(),power.revision(),fev,pw,o);
    }
    public static PowerSnapshot readPower(JsonObject o,String session,String boot)throws IOException {
        try {
            if(number(o,"schema")!=1||!"android:qg.canbus".equals(text(o,"source"))||!"ready".equals(text(o,"status"))
                ||!session.equals(text(o,"session_id"))||!boot.equals(text(o,"boot_id"))||!boot.equals(text(o,"guest_boot_id"))||bool(o,"atomic_guest_state"))throw new IOException("CAN current ready session/boot scope unavailable");
            long pid=number(o,"pid");if(pid<1||pid>Integer.MAX_VALUE)throw new IOException("CAN PID range");
            String source=text(o,"source_instance"),started=text(o,"service_started_elapsed_ns"),revision=text(o,"revision");
            decimal(started);BigInteger latest=decimal(revision);JsonObject clock=object(o,"clock");String sampled=text(clock,"sampled_ns");
            if(!"android.elapsedRealtimeNanos".equals(text(clock,"domain"))||decimal(sampled).compareTo(decimal(started))<0)throw new IOException("CAN clock domain or service interval differs");
            JsonObject interval=object(o,"host_interval");
            if(!"host.steady".equals(text(interval,"domain"))||!("host.steady:"+session).equals(text(interval,"clock_id"))||decimal(text(interval,"end_ns")).compareTo(decimal(text(interval,"start_ns")))<0)throw new IOException("CAN host read interval differs");
            JsonObject transport=object(o,"transport");if(!bool(transport,"binder_alive")||!"registered".equals(text(transport,"registration"))||!"not_observed".equals(text(transport,"client_manager_cache")))throw new IOException("CAN binder registration unavailable");
            JsonObject history=object(o,"history"),cursor=object(history,"next_cursor");BigInteger oldest=decimal(text(history,"oldest_seq")),after=decimal(text(cursor,"after_seq"));
            if(!source.equals(text(cursor,"source_instance"))||after.compareTo(latest)>0||!decimal(text(history,"latest_seq")).equals(latest)||oldest.signum()==0||oldest.compareTo(latest.add(BigInteger.ONE))>0||!decimal(text(history,"dropped_count")).equals(oldest.subtract(BigInteger.ONE)))throw new IOException("CAN history/cursor inconsistent");
            bool(history,"has_more");if(!history.has("gap")||!history.get("gap").isJsonNull())throw new IOException("CAN history has a source/overflow gap");
            if(!o.has("events")||!o.get("events").isJsonArray()||o.getAsJsonArray("events").size()>32)throw new IOException("CAN event bounds");
            BigInteger previous=BigInteger.ZERO;for(JsonElement item:o.getAsJsonArray("events")) {
                JsonObject event=item.getAsJsonObject();BigInteger seq=decimal(text(event,"seq"));
                if(!source.equals(text(event,"source_instance"))||seq.compareTo(previous)<=0||seq.compareTo(oldest)<0||seq.compareTo(latest)>0)throw new IOException("CAN event source/sequence differs");
                decimal(text(event,"elapsed_ns"));decimal(text(event,"utc_ms"));text(event,"kind");previous=seq;
            }
            if(previous.signum()>0&&!previous.equals(after))throw new IOException("CAN cursor does not cover returned event batch");
            JsonObject values=object(o,"values");if(values.size()>32)throw new IOException("CAN values bound");
            int acc=raw(o,"acc","ICanBusService.getAccStatus",0,2);
            JsonObject coverage=object(o,"coverage");
            if(!"service_construction".equals(text(coverage,"begins"))||!"journal_replay".equals(text(coverage,"delivery"))||!bool(coverage,"vehicle_state_setter")||bool(coverage,"survives_process_death")||!"partial".equals(text(coverage,"other_feedback_paths")))throw new IOException("CAN public coverage contract differs; no full bus claim inferred");
            return new PowerSnapshot(boot,session,source,pid,started,sampled,revision,acc,o);
        }catch(IllegalStateException|UnsupportedOperationException e){throw new IOException("malformed CAN receipt",e);}
    }
    public static String continuousOffBlocker(Snapshot snapshot) {
        return "Public CAN source declares coverage.other_feedback_paths=partial and survives_process_death=false; sampled getters/setter journal cannot prove continuous CAN OFF across drawer transitions or a cold boot";
    }
}
