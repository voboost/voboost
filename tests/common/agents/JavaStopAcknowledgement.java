package ru.voboost.tests.agents;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

/** Existing product S-document ACK, not a widened log capability or a new test receipt. */
public final class JavaStopAcknowledgement {
    public record Proof(
            String priorDocument,
            String stoppedDocument,
            String priorReceipt,
            String stoppedReceipt) {}

    private static final Set<String> BINDING = Set.of(
            "targetPid",
            "targetStarttime",
            "endpointEpoch",
            "generationId",
            "catalogSha256",
            "desiredDocumentGeneration",
            "acceptedStatus");

    private static void require(boolean value, String why) throws IOException {
        if (!value) {
            throw new IOException("Java stop acknowledgement: " + why);
        }
    }

    private static String text(JsonObject value, String key) throws IOException {
        require(
                value.has(key)
                        && value.get(key).isJsonPrimitive()
                        && value.getAsJsonPrimitive(key).isString(),
                key);
        return value.get(key).getAsString();
    }

    private static BigInteger number(String value, boolean positive, boolean signed)
            throws IOException {
        require(
                value.matches(positive ? "[1-9][0-9]{0,19}" : "0|[1-9][0-9]{0,19}"),
                "canonical receipt integer");
        BigInteger result = new BigInteger(value);
        require(result.bitLength() <= (signed ? 63 : 64), "receipt integer bound");
        return result;
    }

    private static JsonObject object(JsonObject value, String key) throws IOException {
        require(value.has(key) && value.get(key).isJsonObject(), key);
        return value.getAsJsonObject(key);
    }

    public static String prior(
            JsonObject binding,
            RootVehicleState.Authority authority,
            String publisher) throws IOException {
        require(binding != null && authority != null && publisher != null,
                "current authority required");
        require(
                authority.boot().matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"),
                "boot identity");
        require(
                number(authority.pid(), true, false)
                        .compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0,
                "target PID bound");
        number(authority.starttime(), true, false);
        number(authority.epoch(), true, false);
        require(
                binding.keySet().equals(BINDING),
                "closed current binding with accepted status required");
        for (var pair : Map.of(
                "targetPid", authority.pid(),
                "targetStarttime", authority.starttime(),
                "endpointEpoch", authority.epoch(),
                "generationId", authority.generation(),
                "catalogSha256", authority.catalog(),
                "desiredDocumentGeneration", authority.document()).entrySet()) {
            require(pair.getValue().equals(text(binding, pair.getKey())), "endpoint identity");
        }
        number(authority.document(), false, true);
        JsonObject accepted = object(binding, "acceptedStatus");
        require(
                accepted.keySet().equals(Set.of("documentGeneration", "receipt"))
                        && authority.document().equals(text(accepted, "documentGeneration")),
                "accepted current document");
        JsonObject receipt = object(accepted, "receipt");
        require(
                receipt.keySet().equals(Set.of(
                        "sequence", "daemonPid", "daemonStarttime", "bootId")),
                "closed S receipt");
        String[] owner = publisher.split(":", -1);
        require(
                owner.length == 3
                        && authority.boot().equals(owner[0])
                        && owner[0].equals(text(receipt, "bootId"))
                        && owner[1].equals(text(receipt, "daemonPid"))
                        && owner[2].equals(text(receipt, "daemonStarttime")),
                "same producer lifetime");
        require(
                number(owner[1], true, false)
                        .compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0,
                "producer PID bound");
        number(owner[2], true, false);
        String sequence = text(receipt, "sequence");
        number(sequence, true, false);
        return sequence;
    }

    public static Proof stopped(
            JsonObject before,
            JsonObject after,
            RootVehicleState.Authority authority,
            String publisher) throws IOException {
        String previous = prior(before, authority, publisher);
        String document = text(after, "desiredDocumentGeneration");
        require(
                number(document, false, true)
                        .compareTo(number(authority.document(), false, true)) > 0,
                "new stopped document required");
        var stopped = new RootVehicleState.Authority(
                authority.boot(),
                authority.generation(),
                authority.catalog(),
                authority.process(),
                authority.pid(),
                authority.starttime(),
                authority.epoch(),
                document);
        String sequence = prior(after, stopped, publisher);
        require(
                number(sequence, true, false).compareTo(number(previous, true, false)) > 0,
                "fresh accepted S receipt required");
        return new Proof(authority.document(), document, previous, sequence);
    }

    private JavaStopAcknowledgement() {}
}
