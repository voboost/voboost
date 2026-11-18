package ru.voboost.tests.installer.cli;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
import java.util.Set;

/** Typed emulator install protocol; verification of the actual boot belongs to the scenario. */
public final class InstallerAcceptance {
    private static final String PREFIX = "install-acceptance-armed ";
    private static final Set<String> ARM = Set.of("schemaVersion", "transactionId", "manifestSha256", "attemptToken");
    private static final Set<String> RECEIPT = Set.of("schemaVersion", "transactionId", "manifestSha256", "attemptToken",
        "bootId", "rebootCount", "decision", "state", "expiresMonotonicMs");
    private static final BigInteger UINT64 = new BigInteger("18446744073709551615");
    private final String manifest;
    private final String beforeBoot;
    private JsonObject armed;
    private String boot;
    private String expiry;
    private String decision;
    private boolean consumed;

    public InstallerAcceptance(String manifest, String beforeBoot) {
        if (!digest(manifest) || !uuid(beforeBoot)) throw new IllegalArgumentException("candidate and initial boot required");
        this.manifest = manifest;
        this.beforeBoot = beforeBoot;
    }

    public boolean observeArm(String line) throws IOException {
        if (!line.startsWith(PREFIX)) return false;
        if (armed != null) throw new IOException("install acceptance arm repeated");
        JsonObject value = object(line.substring(PREFIX.length()));
        if (!value.keySet().equals(ARM) || !one(value, "schemaVersion")
                || !manifest.equals(text(value, "transactionId")) || !manifest.equals(text(value, "manifestSha256"))
                || !digest(text(value, "attemptToken"))) throw new IOException("install acceptance arm identity differs");
        armed = value;
        return true;
    }

    /** Call only after independently observing the current intermediate boot. Does not authorize acceptance. */
    public JsonObject statusRequest(String currentBoot, int rebootCount) throws IOException {
        if (armed == null || boot != null || !uuid(currentBoot) || currentBoot.equals(beforeBoot) || rebootCount != 1)
            throw new IOException("install acceptance intermediate boot differs");
        boot = currentBoot;
        return request("status");
    }

    public void observeWaiting(String body) throws IOException {
        if (boot == null || expiry != null || decision != null) throw new IOException("install acceptance status out of order");
        JsonObject receipt = receipt(body, "status", "waiting");
        expiry = text(receipt, "expiresMonotonicMs");
    }

    /** The scenario explicitly chooses accept only after its boot verification; failure chooses reject. */
    public JsonObject decisionRequest(boolean verifiedBoot) throws IOException {
        if (expiry == null || decision != null) throw new IOException("install acceptance decision lacks waiting receipt");
        decision = verifiedBoot ? "accept" : "reject";
        return request(decision);
    }

    public void observeDecision(String body) throws IOException {
        if (decision == null || consumed) throw new IOException("install acceptance decision out of order");
        JsonObject value = receipt(body, decision, decision.equals("accept") ? "accepted" : "rejected");
        if (!expiry.equals(text(value, "expiresMonotonicMs"))) throw new IOException("install acceptance lease changed");
        consumed = true;
    }

    public boolean accepted() { return consumed && "accept".equals(decision); }

    private JsonObject request(String choice) {
        JsonObject value = armed.deepCopy();
        value.remove("schemaVersion");
        value.addProperty("action", "install-acceptance");
        value.addProperty("bootId", boot);
        value.addProperty("rebootCount", 1);
        value.addProperty("decision", choice);
        return value;
    }

    private JsonObject receipt(String body, String expectedDecision, String state) throws IOException {
        JsonObject value = object(body);
        String expires = text(value, "expiresMonotonicMs");
        if (!value.keySet().equals(RECEIPT) || !one(value, "schemaVersion") || !one(value, "rebootCount")
                || !manifest.equals(text(value, "transactionId")) || !manifest.equals(text(value, "manifestSha256"))
                || !text(armed, "attemptToken").equals(text(value, "attemptToken")) || !boot.equals(text(value, "bootId"))
                || !expectedDecision.equals(text(value, "decision")) || !state.equals(text(value, "state"))
                || !expires.matches("[1-9][0-9]{0,19}") || new BigInteger(expires).compareTo(UINT64) > 0)
            throw new IOException("install acceptance receipt identity or state differs");
        return value;
    }
    private static boolean digest(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static boolean uuid(String value) { return value != null && value.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"); }
    private static String text(JsonObject value, String name) {
        return value.has(name) && value.get(name).isJsonPrimitive() && value.getAsJsonPrimitive(name).isString()
            ? value.get(name).getAsString() : "";
    }
    private static boolean one(JsonObject value, String name) {
        return value.has(name) && value.get(name).isJsonPrimitive() && value.getAsJsonPrimitive(name).isNumber()
            && "1".equals(value.get(name).getAsString());
    }
    private static JsonObject object(String input) throws IOException {
        if (input.length() > 4096) throw new IOException("install acceptance receipt exceeds bound");
        try (JsonReader reader = new JsonReader(new StringReader(input))) {
            reader.setLenient(false);
            JsonObject value = new JsonObject();
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (value.has(name)) throw new IOException("duplicate install acceptance field");
                if (reader.peek() == JsonToken.STRING) value.addProperty(name, reader.nextString());
                else if (reader.peek() == JsonToken.NUMBER) {
                    String number = reader.nextString();
                    if (!number.matches("[0-9]{1,20}")) throw new IOException("invalid install acceptance number");
                    value.add(name, new JsonPrimitive(new BigInteger(number)));
                } else throw new IOException("invalid install acceptance field type");
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("trailing install acceptance input");
            return value;
        } catch (IllegalStateException | NumberFormatException failure) {
            throw new IOException("malformed install acceptance input", failure);
        }
    }
}
