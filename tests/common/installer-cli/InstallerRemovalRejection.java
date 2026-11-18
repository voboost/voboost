package ru.voboost.tests.installer.cli;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import ru.voboost.tests.RunFiles;

/** Recognizes one audited host rejection that occurs before removal admission. */
public final class InstallerRemovalRejection {
    private static final String REASON = "Emulator acceptance failed: Cluster provisioning is missing; signed removal cannot be verified";
    private static final Set<String> FIELDS = Set.of("schemaVersion", "kind", "manifest", "device", "bootId", "diagnostic", "functionalAcceptance");

    public static boolean reported(String stderr) throws IOException {
        boolean found = false;
        for (String line : stderr.lines().filter(value -> !value.isBlank()).toList()) {
            JsonObject row = RunFiles.parse(line);
            if ("error".equals(text(row, "level"))) {
                if (found || !REASON.equals(text(row, "message"))) return false;
                JsonObject source = row.getAsJsonObject("source");
                if (source == null || !"installer".equals(text(source, "kind"))
                        || !"desktop".equals(text(source, "id"))) return false;
                found = true;
            }
        }
        return found;
    }

    public static JsonObject prove(JsonObject input, JsonObject nativeReport, String boot,
            String stdout, String stderr, JsonObject timing) throws IOException {
        var command = input.getAsJsonObject("process").getAsJsonArray("command");
        if (!"failed-install-cleanup".equals(text(input, "phase")) || command == null || command.size() != 7
                || !"--candidate".equals(command.get(5).getAsString())
                || !java.nio.file.Path.of(command.get(6).getAsString()).isAbsolute()
                || !"--emulator-check".equals(command.get(1).getAsString())
                || !"--device".equals(command.get(3).getAsString())
                || !text(input, "device").equals(command.get(4).getAsString())
                || !stdout.isEmpty() || !reported(stderr)
                || !"1".equals(timing.get("schemaVersion").toString())
                || !"1".equals(timing.get("exit").toString())
                || !"exited".equals(text(timing, "status"))
                || !"launch-through-exit-observation".equals(text(timing, "scope"))
                || !Instant.parse(text(timing, "exitedAt")).isAfter(Instant.parse(text(timing, "startedAt")))
                || !boot.equals(text(input, "priorBoot")) || !boot.equals(text(nativeReport, "bootId"))
                || !"install".equals(text(nativeReport, "operation"))
                || !"install-complete".equals(text(nativeReport, "phase"))
                || !"complete".equals(text(nativeReport, "outcome"))
                || !text(input, "manifest").equals(text(nativeReport, "transactionId"))) {
            throw new IOException("removal was not proven rejected before admission");
        }
        JsonObject proof = new JsonObject();
        proof.addProperty("schemaVersion", 1);
        proof.addProperty("kind", "installer-uninstall-rejected");
        proof.addProperty("functionalAcceptance", false);
        proof.addProperty("bootId", boot);
        proof.addProperty("diagnostic", REASON);
        for (String key : Set.of("device", "manifest")) proof.add(key, input.get(key).deepCopy());
        require(proof, input);
        return proof;
    }

    public static void require(JsonObject proof, JsonObject input) throws IOException {
        if (!proof.keySet().equals(FIELDS) || !"1".equals(proof.get("schemaVersion").toString())
                || !"installer-uninstall-rejected".equals(text(proof, "kind"))
                || !"false".equals(proof.get("functionalAcceptance").toString())
                || !REASON.equals(text(proof, "diagnostic"))
                || !InstallerDriver.emulator(text(proof, "device"))
                || !text(proof, "manifest").matches("[0-9a-f]{64}")
                || !text(input, "priorBoot").equals(text(proof, "bootId")))
            throw new IOException("removal rejection receipt differs");
        for (String key : Set.of("device", "manifest"))
            if (!text(input, key).equals(text(proof, key))) throw new IOException("removal rejection identity differs");
    }

    private static String text(JsonObject value, String key) throws IOException {
        var field = value.get(key);
        if (field == null || !field.isJsonPrimitive() || !field.getAsJsonPrimitive().isString())
            throw new IOException("removal rejection string missing: " + key);
        return field.getAsString();
    }
    private InstallerRemovalRejection() {}
}
