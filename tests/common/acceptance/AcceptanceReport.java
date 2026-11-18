package ru.voboost.tests;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Required case/target outcomes; incomplete or focused reports never imply full acceptance. */
public final class AcceptanceReport {
    public enum Prerequisite { UNIT, CANDIDATE, ENVIRONMENT }
    public record Result(GateStatus status, String reason, List<Path> evidence) {
        public Result {
            Objects.requireNonNull(status); Objects.requireNonNull(reason);
            if (reason.isBlank()) throw new IllegalArgumentException("result reason required");
            evidence = List.copyOf(evidence);
            if ((status == GateStatus.PASS || status == GateStatus.NOT_APPLICABLE) && evidence.isEmpty())
                throw new IllegalArgumentException("positive and applicability results require evidence");
        }
    }
    private final Map<AcceptancePlan.Key, AcceptancePlan.Case> required = new LinkedHashMap<>();
    private final Map<AcceptancePlan.Key, Result> results = new LinkedHashMap<>();
    private final Map<AcceptancePlan.Key, InventoryProof.Verified> exclusions = new LinkedHashMap<>();
    private final boolean focused;
    private Result cleanup;
    private JsonObject identity;
    private final Map<Prerequisite, Result> prerequisites = new java.util.EnumMap<>(Prerequisite.class);

    public AcceptanceReport(AcceptancePlan plan) { this(plan.cases(), false); }

    public static AcceptanceReport focused(AcceptancePlan plan, AcceptancePlan.Layer layer) {
        return new AcceptanceReport(plan.layer(layer), true);
    }

    private AcceptanceReport(List<AcceptancePlan.Case> cases, boolean focused) {
        if (cases.isEmpty()) throw new IllegalArgumentException("required cases missing");
        this.focused = focused;
        for (AcceptancePlan.Case item : cases) {
            if (required.putIfAbsent(item.key(), item) != null) throw new IllegalArgumentException("duplicate requirement");
        }
    }

    public synchronized void identify(String runId, Path candidate, String candidateIdentity, String environment) throws IOException {
        if (identity != null || runId == null || !runId.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}") || candidateIdentity == null
                || candidateIdentity.isBlank() || environment == null || environment.isBlank()
                || !candidate.isAbsolute() || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("exact run, candidate and environment identity required once");
        identity = new JsonObject(); identity.addProperty("runId", runId);
        identity.addProperty("candidate", candidate.toString()); identity.addProperty("candidateIdentity", candidateIdentity);
        identity.addProperty("environment", environment);
    }
    public synchronized void prerequisite(Prerequisite key, Result result) throws IOException {
        if (prerequisites.containsKey(Objects.requireNonNull(key))) throw new IOException("prerequisite result already recorded");
        verifyEvidence(Objects.requireNonNull(result)); prerequisites.put(key, result);
    }

    public synchronized void record(AcceptancePlan.Key key, Result result) throws IOException {
        Objects.requireNonNull(result);
        if (result.status() == GateStatus.NOT_APPLICABLE)
            throw new IOException("unverified applicability exclusion is not admitted");
        AcceptancePlan.Case specification = required.get(key);
        if (specification == null || results.containsKey(key)) throw new IOException("unknown or already completed case");
        if (!specification.registered() && result.status() != GateStatus.BLOCKED)
            throw new IOException("unregistered candidate agent must remain blocked");
        verifyEvidence(result);
        results.put(key, result);
    }
    /** Admit only an exclusion whose retained current-run sources were re-evaluated. */
    public synchronized void recordVerifiedAbsence(InventoryProof.Verified proof) throws IOException {
        Objects.requireNonNull(proof);
        if (identity == null) throw new IOException("report identity required before exclusion");
        proof.requireScope(identity.get("runId").getAsString(), Path.of(identity.get("candidate").getAsString()));
        AcceptancePlan.Case specification = required.get(proof.key());
        if (specification == null || !specification.registered() || results.containsKey(proof.key()))
            throw new IOException("unknown, unregistered or already completed case");
        Result result = new Result(GateStatus.NOT_APPLICABLE,
            "Exact agent owner absent from the current verified inventory: " + proof.owner(), proof.evidence());
        verifyEvidence(result);
        results.put(proof.key(), result);
        exclusions.put(proof.key(), proof);
    }
    public synchronized void cleanup(Result result) throws IOException {
        if (cleanup != null) throw new IOException("cleanup result already recorded");
        verifyEvidence(Objects.requireNonNull(result)); cleanup = result;
    }
    private static void verifyEvidence(Result result) throws IOException {
        for (Path path : result.evidence()) {
            if (!path.isAbsolute() || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("retained evidence file required");
        }
    }
    public synchronized GateStatus verdict() {
        if (prerequisites.values().stream().anyMatch(item -> item.status() == GateStatus.FAIL)
                || results.values().stream().anyMatch(item -> item.status() == GateStatus.FAIL)
                || cleanup != null && cleanup.status() == GateStatus.FAIL) return GateStatus.FAIL;
        if (identity == null || !retainedEvidenceAvailable() || prerequisites.size() != Prerequisite.values().length
                || prerequisites.values().stream().anyMatch(item -> item.status() != GateStatus.PASS)
                || cleanup == null || cleanup.status() != GateStatus.PASS || results.size() != required.size()
                || results.values().stream().anyMatch(item -> item.status() != GateStatus.PASS && item.status() != GateStatus.NOT_APPLICABLE))
            return GateStatus.BLOCKED;
        return GateStatus.PASS;
    }
    private boolean retainedEvidenceAvailable() {
        Path candidate = Path.of(identity.get("candidate").getAsString());
        if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try {
            for (InventoryProof.Verified proof : exclusions.values()) {
                proof.requireScope(identity.get("runId").getAsString(), candidate);
                proof.revalidate();
            }
            for (Result result : prerequisites.values()) {
                verifyEvidence(result);
            }
            for (Result result : results.values()) {
                verifyEvidence(result);
            }
            if (cleanup != null) {
                verifyEvidence(cleanup);
            }
            return true;
        } catch (IOException unavailable) {
            return false;
        }
    }

    public synchronized JsonObject json() {
        JsonObject report = new JsonObject(); report.addProperty("schemaVersion", 1);
        report.addProperty("focused", focused); report.addProperty("verdict", verdict().name());
        report.addProperty("fullAcceptance", !focused && verdict() == GateStatus.PASS);
        if (identity != null) report.add("identity", identity.deepCopy());
        JsonObject conditions = new JsonObject();
        prerequisites.forEach((key, value) -> conditions.add(key.name(), result(value)));
        report.add("prerequisites", conditions);
        JsonArray cases = new JsonArray();
        required.forEach((key, specification) -> {
            Result result = results.get(key);
            InventoryProof.Verified exclusion = exclusions.get(key);
            if (exclusion != null) {
                try { exclusion.revalidate(); }
                catch (IOException invalid) { result = new Result(GateStatus.BLOCKED, "Retained applicability proof is no longer valid", List.of()); }
            }
            if (result == null) {
                boolean agentTarget = key.id().startsWith("agent/");
                result = new Result(specification.registered() && !agentTarget ? GateStatus.NOT_RUN : GateStatus.BLOCKED,
                    !specification.registered() ? "Candidate agent has no registered scenario"
                        : agentTarget ? "Target applicability or required effect is not established" : "Required case not executed", List.of());
            }
            JsonObject row = result(result); row.addProperty("id", key.id()); row.addProperty("target", key.target());
            row.addProperty("layer", specification.layer().name()); row.addProperty("observable", specification.observable()); cases.add(row);
        });
        report.add("cases", cases);
        if (cleanup != null) report.add("cleanup", result(cleanup));
        return report;
    }
    private static JsonObject result(Result value) {
        JsonObject result = new JsonObject(); result.addProperty("status", value.status().name()); result.addProperty("reason", value.reason());
        JsonArray evidence = new JsonArray(); value.evidence().forEach(path -> evidence.add(path.toString())); result.add("evidence", evidence);
        return result;
    }
}
