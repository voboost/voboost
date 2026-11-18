package ru.voboost.tests;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

/** Current-run scenario receipts consumed by the product verdict. */
public final class CaseEvidence {
    private CaseEvidence() {}
    public static void record(AcceptancePlan.Key key, AcceptanceReport.Result result) throws IOException {
        String runId = System.getProperty("checkRunId");
        if (runId == null) return; // Focused diagnostics do not publish product acceptance.
        UUID.fromString(runId);
        Path work = Path.of(System.getProperty("testWorkDir"));
        if (!work.isAbsolute()) throw new IOException("absolute case evidence directory required");
        Path directory = RunFiles.directory(work.resolve("cases"));
        JsonObject value = new JsonObject(); value.addProperty("schemaVersion", 1); value.addProperty("runId", runId);
        value.addProperty("id", key.id()); value.addProperty("target", key.target()); value.addProperty("status", result.status().name());
        value.addProperty("reason", result.reason()); JsonArray evidence = new JsonArray();
        for (Path file : result.evidence()) {
            if (!file.isAbsolute() || !java.nio.file.Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                throw new IOException("retained case evidence file required");
            evidence.add(file.toString());
        }
        value.add("evidence", evidence); RunFiles.write(directory.resolve(UUID.randomUUID() + ".json"), value);
    }
}
