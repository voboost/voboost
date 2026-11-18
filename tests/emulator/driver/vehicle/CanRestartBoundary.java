package ru.voboost.voyemu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;

/** Verifies the history and fence returned by an owned cold restart. */
final class CanRestartBoundary {
    private CanRestartBoundary() {}
    static BigInteger decimal(JsonObject record,String key,boolean positive)throws IOException {
        JsonElement value=record==null?null:record.get(key);
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()||!value.getAsString().matches("0|[1-9][0-9]{0,19}"))throw new IOException("canonical CAN boundary counter required: "+key);
        BigInteger n=new BigInteger(value.getAsString());if(n.bitLength()>64||positive&&n.signum()==0)throw new IOException("CAN boundary counter out of range: "+key);return n;
    }
    private static final BigInteger NANOS_PER_MILLI = BigInteger.valueOf(1_000_000);

    static void expected(JsonObject can, String boot, String session) throws IOException {
        if (can == null || !can.keySet().equals(Set.of("anchor", "native_terminal_boottime_ms")))
            throw new IOException("exact CAN history anchor and native terminal time required");
        JsonObject anchor = PwSoundFixture.object(can, "anchor");
        history(anchor, boot);
        if (!"closed_ecu_boundary_v1".equals(PwSoundFixture.text(anchor, "capability"))
                || !session.equals(PwSoundFixture.text(anchor, "session_id"))
                || !boot.equals(PwSoundFixture.text(anchor, "guest_boot_id"))
                || PwSoundFixture.number(anchor, "owned_android_pid", Integer.MAX_VALUE) < 2)
            throw new IOException("CAN anchor differs from the owned Android source");
        drained(anchor);
        if (!List.of("none", "aborted").contains(PwSoundFixture.text(anchor, "stage"))
                || PwSoundFixture.number(anchor, "mode", 5) != 2
                || decimal(can, "native_terminal_boottime_ms", true).compareTo(bootMillis(anchor, "capture_boottime_ns")) <= 0)
            throw new IOException("native terminal must follow the live OFF anchor");
    }

    private static BigInteger bootMillis(JsonObject record, String key) throws IOException {
        return decimal(record, key, true).divide(NANOS_PER_MILLI);
    }

    private static void drained(JsonObject value) throws IOException {
        for (String key : List.of("raw_queued", "raw_inflight_seq", "semantic_queue", "loss_count"))
            if (decimal(value, key, false).signum() != 0) throw new IOException("CAN source not drained: " + key);
    }

    private static void history(JsonObject value, String boot) throws IOException {
        source(value, boot);
        PwSoundFixture.bool(value, "first_open", true);
        decimal(value, "commit_seq", true);
        if (!BigInteger.ONE.equals(decimal(value, "kernel_open_seq", true))
                || decimal(value, "first_commit_boottime_ns", true).compareTo(decimal(value, "capture_boottime_ns", true)) > 0
                || decimal(value, "loss_count", false).signum() != 0
                || List.of("expired", "quiescing").contains(PwSoundFixture.text(value, "stage")))
            throw new IOException("first-open lossless CAN history required");
    }

    static void source(JsonObject value,String boot)throws IOException {
        PwSoundFixture.bootId(boot);PwSoundFixture.bool(value,"valid",true);
        if(PwSoundFixture.number(value,"schema",1)!=1||!"closed_ecu_boundary".equals(PwSoundFixture.text(value,"producer"))||PwSoundFixture.number(value,"status",Integer.MAX_VALUE)!=0||!boot.equals(PwSoundFixture.text(value,"boot_uuid")))throw new IOException("actual CAN source Linux boot/record differs");
        if(PwSoundFixture.number(value,"mode",5)<1||PwSoundFixture.number(value,"first_mode",5)<1)throw new IOException("actual valid mode domain is 1..5");
        decimal(value,"session",true);decimal(value,"epoch",true);decimal(value,"capture_boottime_ns",true);
    }
    static void frozen(JsonObject value,JsonObject can,String boot)throws IOException {
        history(value,boot);PwSoundFixture.bool(value,"first_open",true);PwSoundFixture.bool(value,"all_writers_quiesced",true);
        for(String k:List.of("session","epoch","nonce"))if(!can.get(k).equals(value.get(k)))throw new IOException("CAN frozen source token differs");
        if(!"frozen".equals(PwSoundFixture.text(value,"stage"))||PwSoundFixture.number(value,"mode",5)!=2)throw new IOException("current frozen OFF boundary required");
        drained(value);
        decimal(value,"fence_seq",true);BigInteger fence=decimal(value,"fence_boottime_ns",true),now=decimal(value,"capture_boottime_ns",true);
        if(now.compareTo(fence)<0||now.compareTo(decimal(value,"expires_boottime_ns",true))>=0||decimal(value,"generated_rx_seq",false).compareTo(decimal(value,"fence_generated_rx_seq",false))<0||!value.get("commit_seq").equals(value.get("fence_seq")))throw new IOException("CAN frozen deadline/generated RX differs");
    }
    static void unchanged(JsonObject value,JsonObject prepared,JsonObject can,String boot)throws IOException {
        frozen(value,can,boot);frozen(prepared,can,boot);
        for(String key:List.of("session","epoch","nonce","kernel_open_seq","commit_seq","on_transition_count","first_commit_boottime_ns","fence_seq","fence_boottime_ns","expires_boottime_ns","raw_admission_seq","raw_queued","raw_inflight_seq","loss_count","fence_generated_rx_seq","semantic_queue","mode","first_mode"))if(!value.get(key).equals(prepared.get(key)))throw new IOException("CAN fence/counter changed: "+key);
        if(decimal(value,"generated_rx_seq",false).compareTo(decimal(prepared,"generated_rx_seq",false))<0||decimal(value,"capture_boottime_ns",true).compareTo(decimal(prepared,"capture_boottime_ns",true))<0)throw new IOException("CAN RX/time counter regressed");
    }
    static void restarted(JsonObject receipt, JsonObject expectedBoot, JsonObject can) throws IOException {
        String old = PwSoundFixture.text(expectedBoot, "android");
        expected(can, old, PwSoundFixture.text(receipt, "session_id"));
        JsonObject proof = PwSoundFixture.object(receipt, "can_boundary");
        JsonObject anchor = PwSoundFixture.object(can, "anchor");
        JsonObject before = PwSoundFixture.object(receipt, "before");
        if (!anchor.equals(proof.get("anchor")) || !can.get("native_terminal_boottime_ms").equals(proof.get("native_terminal_boottime_ms"))
                || PwSoundFixture.number(before, "android_pid", Integer.MAX_VALUE)
                    != PwSoundFixture.number(anchor, "owned_android_pid", Integer.MAX_VALUE))
            throw new IOException("restart receipt differs from the owned CAN history anchor");

        JsonObject prepared = PwSoundFixture.object(proof, "prepared");
        JsonObject qnx = PwSoundFixture.object(proof, "before_qnx_stop");
        JsonObject android = PwSoundFixture.object(proof, "before_android_stop");
        JsonObject token = new JsonObject();
        for (String key : List.of("session", "epoch")) token.add(key, anchor.get(key));
        token.addProperty("nonce", decimal(prepared, "nonce", true).toString());
        unchanged(qnx, prepared, token, old);
        unchanged(android, qnx, token, old);
        for (String key : List.of("kernel_open_seq", "first_mode", "first_commit_boottime_ns", "on_transition_count", "loss_count", "raw_admission_seq"))
            if (!anchor.has(key) || !anchor.get(key).equals(prepared.get(key)))
                throw new IOException("CAN history changed before the fence: " + key);
        for (String key : List.of("capture_boottime_ns", "commit_seq", "generated_rx_seq"))
            if (decimal(prepared, key, false).compareTo(decimal(anchor, key, false)) < 0)
                throw new IOException("CAN history regressed before the fence: " + key);
        if (decimal(can, "native_terminal_boottime_ms", true).compareTo(bootMillis(prepared, "fence_boottime_ns")) > 0)
            throw new IOException("native terminal is later than the kernel fence");

        String fresh = PwSoundFixture.text(PwSoundFixture.object(receipt, "after"), "android_boot_id");
        JsonObject after = PwSoundFixture.object(proof, "after");
        history(after, fresh);
        PwSoundFixture.bool(proof, "old_kernel_stopped", true);
        if (old.equals(fresh) || !"none".equals(PwSoundFixture.text(after, "stage"))
                || PwSoundFixture.number(after, "mode", 5) != 2 || PwSoundFixture.number(after, "first_mode", 5) != 2
                || decimal(after, "on_transition_count", false).signum() != 0)
            throw new IOException("new cold CAN first/global OFF history unverified");
    }
}
