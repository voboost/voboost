package ru.voboost.tests.installer.cli;

import com.google.gson.*;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Set;

/** Validates one complete native kernel RX page and its continuity fence. */
public final class NativeCanJournal {
    private static final BigInteger MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    private static final Set<String> FIELDS = Set.of("schemaVersion", "producer", "bootId", "writerId", "receiverEpoch", "status", "earliestSeq", "latestSeq", "nextCursor", "dropped", "discarded", "unsupportedDatagrams", "errors", "hasMore", "gap", "sampledBootMs", "records");
    private static final Set<String> RECORD = Set.of("seq", "receiveBootMs", "frameHex");
    public record Page(String boot, String writer, BigInteger epoch, BigInteger latest, BigInteger dropped,
            BigInteger discarded, BigInteger errors, BigInteger sampled, boolean gap, JsonArray records) {}
    private NativeCanJournal() {}
    private static IOException bad(String message) { return new IOException("native CAN: " + message); }
    private static void shape(JsonObject o, Set<String> fields) throws IOException {
        if (!o.keySet().equals(fields)) throw bad("object fields differ");
    }
    private static String string(JsonObject o, String key) throws IOException {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw bad("expected string " + key);
        return e.getAsString();
    }
    private static BigInteger decimal(JsonObject o, String key) throws IOException {
        String s = string(o, key);
        if (!s.matches("0|[1-9][0-9]{0,19}")) throw bad("noncanonical decimal " + key);
        BigInteger n = new BigInteger(s);
        if (n.compareTo(MAX) > 0) throw bad("unsigned overflow " + key);
        return n;
    }
    private static boolean bool(JsonObject o, String key) throws IOException {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) throw bad("expected boolean " + key);
        return e.getAsBoolean();
    }
    static boolean bootstrap(JsonObject input, String boot) throws IOException {
        if (input == null) throw bad("missing bootstrap page");
        String status = string(input, "status");
        if (status.equals("active")) { read(input, BigInteger.ZERO, boot, true); return true; }
        if (!status.equals("draining") && !status.equals("unavailable")) throw bad("invalid bootstrap status");
        BigInteger epoch = decimal(input, "receiverEpoch");
        JsonObject validation = input.deepCopy();
        validation.addProperty("status", "active");
        if (epoch.signum() == 0) validation.addProperty("receiverEpoch", "1");
        if (!input.has("sampledBootMs")) throw bad("missing bootstrap timestamp");
        if (!input.get("sampledBootMs").isJsonNull()) decimal(input, "sampledBootMs");
        validation.addProperty("sampledBootMs", MAX.toString());
        read(validation, BigInteger.ZERO, boot, true);
        return false;
    }
    public static Page read(JsonObject o, BigInteger after, String expectedBoot, boolean allowInitialGap) throws IOException {
        if (o == null || after == null || after.signum() < 0 || after.compareTo(MAX) > 0) throw bad("invalid input");
        JsonObject journal = o.deepCopy();
        JsonElement initial = journal.remove("firstTargetFrames");
        shape(journal, FIELDS);
        if (initial != null) {
            if (!initial.isJsonArray() || initial.getAsJsonArray().size() > 3) throw bad("first target frames bound");
            Set<String> types = new java.util.HashSet<>();
            for (JsonElement item : initial.getAsJsonArray()) {
                if (!item.isJsonObject()) throw bad("first target frame shape");
                JsonObject frame = item.getAsJsonObject();
                shape(frame, Set.of("seq", "receiverEpoch", "receiveBootMs", "frameHex"));
                String raw = string(frame, "frameHex");
                if (!raw.matches("(6a|73|9a)08[0-9a-f]{16}") || !types.add(raw.substring(0, 2))
                        || decimal(frame, "seq").signum() == 0
                        || decimal(frame, "seq").compareTo(decimal(o, "latestSeq")) > 0
                        || decimal(frame, "receiverEpoch").signum() == 0
                        || decimal(frame, "receiverEpoch").compareTo(decimal(o, "receiverEpoch")) > 0)
                    throw bad("first target frame identity");
                decimal(frame, "receiveBootMs");
            }
        }
        JsonElement schema = o.get("schemaVersion");
        if (schema == null || !schema.isJsonPrimitive() || !schema.getAsJsonPrimitive().isNumber() || !schema.getAsString().equals("1")
                || !string(o, "producer").equals("actual_kernel_rx")) throw bad("schema or producer");
        String boot = string(o, "bootId"), writer = string(o, "writerId");
        if (!boot.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}") || !boot.equals(expectedBoot)
                || !writer.matches("[0-9a-f]{32}")) throw bad("boot or writer identity");
        BigInteger epoch = decimal(o, "receiverEpoch"), earliest = decimal(o, "earliestSeq"), latest = decimal(o, "latestSeq");
        BigInteger next = decimal(o, "nextCursor"), dropped = decimal(o, "dropped"), discarded = decimal(o, "discarded"), errors = decimal(o, "errors");
        if (!string(o, "status").equals("active") || epoch.signum() == 0 || bool(o, "hasMore")) throw bad("terminal EAGAIN unavailable");
        decimal(o, "unsupportedDatagrams");
        BigInteger sampled = decimal(o, "sampledBootMs");
        boolean gap = bool(o, "gap");
        boolean calculatedGap = after.compareTo(latest) > 0 || after.add(BigInteger.ONE).compareTo(earliest) < 0;
        if (earliest.signum() == 0 || earliest.compareTo(latest.add(BigInteger.ONE)) > 0 || !next.equals(latest)
                || gap != calculatedGap || gap && !allowInitialGap) throw bad("cursor or gap");
        JsonElement rs = o.get("records");
        if (rs == null || !rs.isJsonArray() || rs.getAsJsonArray().size() > 256) throw bad("records bound");
        BigInteger previous = null, time = null;
        for (JsonElement item : rs.getAsJsonArray()) {
            if (!item.isJsonObject()) throw bad("record shape");
            JsonObject record = item.getAsJsonObject(); shape(record, RECORD);
            BigInteger seq = decimal(record, "seq"), received = decimal(record, "receiveBootMs");
            String frame = string(record, "frameHex");
            if (!frame.matches("[0-9a-f]{4,514}") || frame.length() % 2 != 0
                    || frame.length() != (Integer.parseInt(frame.substring(2, 4), 16) + 2) * 2
                    || seq.compareTo(after) <= 0 || seq.compareTo(earliest) < 0 || seq.compareTo(latest) > 0
                    || previous != null && !seq.equals(previous.add(BigInteger.ONE))
                    || time != null && received.compareTo(time) < 0 || received.compareTo(sampled) > 0)
                throw bad("record sequence, time or frame");
            if (previous == null && !gap && !seq.equals(after.add(BigInteger.ONE))) throw bad("missing first record");
            previous = seq; time = received;
        }
        if (previous == null && after.compareTo(latest) < 0 && !gap || previous != null && !previous.equals(latest)) throw bad("incomplete page");
        return new Page(boot, writer, epoch, latest, dropped, discarded, errors, sampled, gap, rs.getAsJsonArray().deepCopy());
    }
}
