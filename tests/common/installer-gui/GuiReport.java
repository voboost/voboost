package ru.voboost.tests.installer.gui;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import ru.voboost.tests.GateStatus;

/** Host-side evidence contract; it does not execute GUI operations. */
public final class GuiReport {
    public enum CaseId { G01, G02, G03, G04, G05, G06, G07, G08, G09, G10, G11 }
    public enum Availability { NO_DEVICE, UNAUTHORIZED, UNSUPPORTED }
    public enum SharedControl { RU, EN, BOTH_THEMES, DEFAULT_SIZE, MINIMUM_SIZE, LOG_OFF, LOG_ON, COPY, LICENSE_MENU, LOG_DURING_G02, LOG_DURING_G06, LOCK_DURING_G02, LOCK_DURING_G06 }
    public enum Fixture { VERIFIED, UNAVAILABLE, UNKNOWN }
    public enum SkipCause { MISSING_PREREQUISITE, MISSING_FIXTURE, NOT_SELECTED, PREVIOUS_FAILURE }

    public record Result(GateStatus status, String detail, String evidence, Fixture fixture) {
        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(fixture, "fixture");
            if (detail == null || detail.isBlank()) throw new IllegalArgumentException("detail is required");
            if (status == GateStatus.PASS && (fixture != Fixture.VERIFIED || evidence == null || evidence.isBlank())) {
                throw new IllegalArgumentException("PASS requires verified fixture and evidence");
            }
        }
    }

    private final Map<String, Result> prerequisites = new LinkedHashMap<>();
    private final Map<CaseId, Result> cases = new EnumMap<>(CaseId.class);
    private final Map<Availability, Result> availability = new EnumMap<>(Availability.class);
    private final EnumSet<CaseId> recorded = EnumSet.noneOf(CaseId.class);
    private final Map<SharedControl, Result> controls = new EnumMap<>(SharedControl.class);
    private final EnumSet<SharedControl> controlsRecordedDuringOperation = EnumSet.noneOf(SharedControl.class);
    private CaseId activeOperation;
    private boolean stopped;

    public GuiReport(Map<String, Result> prerequisites) {
        Objects.requireNonNull(prerequisites, "prerequisites");
        if (prerequisites.isEmpty()) throw new IllegalArgumentException("prerequisites are required");
        prerequisites.forEach((name, result) -> {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("prerequisite name is required");
            this.prerequisites.put(name, Objects.requireNonNull(result, "prerequisite result"));
        });
        stopped = this.prerequisites.values().stream().anyMatch(result -> result.status() != GateStatus.PASS);
        for (CaseId id : CaseId.values()) cases.put(id, notRun());
    }

    public static Result skipped(SkipCause cause, String reason) {
        Objects.requireNonNull(cause, "cause");
        GateStatus status = switch (cause) {
            case MISSING_PREREQUISITE, MISSING_FIXTURE -> GateStatus.BLOCKED;
            case NOT_SELECTED, PREVIOUS_FAILURE -> GateStatus.NOT_RUN;
        };
        return new Result(status, reason, null, Fixture.UNKNOWN);
    }

    private static Result notRun() {
        return new Result(GateStatus.NOT_RUN, "Not recorded", null, Fixture.UNKNOWN);
    }

    public boolean mutationAllowed() { return !stopped; }

    /** Declares that a G02 or G06 route has started. This is structural bookkeeping, not UI or backend proof. */
    public void beginOperation(CaseId id) {
        Objects.requireNonNull(id, "id");
        if (id != CaseId.G02 && id != CaseId.G06) throw new IllegalArgumentException("only G02 and G06 operations can be begun");
        if (stopped) throw new IllegalStateException("execution stopped");
        if (recorded.contains(id)) throw new IllegalStateException("operation already completed");
        if (activeOperation != null) throw new IllegalStateException("another operation is active");
        activeOperation = id;
    }

    public void record(CaseId id, Result result) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(result, "result");
        if (recorded.contains(id)) throw new IllegalStateException("case already recorded");
        if (stopped && (result.status() == GateStatus.PASS || result.status() == GateStatus.FAIL) &&
                !(result.status() == GateStatus.FAIL && ((id == CaseId.G08 && hasFailure(availability)) || (id == CaseId.G11 && hasFailure(controls))))) {
            throw new IllegalStateException("execution stopped");
        }
        if (((id == CaseId.G08 && hasFailure(availability)) || (id == CaseId.G11 && hasFailure(controls))) && result.status() != GateStatus.FAIL) throw new IllegalStateException("failed parameter requires failed case");
        if (id == CaseId.G08 && result.status() == GateStatus.PASS &&
                (availability.size() != Availability.values().length || availability.values().stream().anyMatch(item -> item.status() != GateStatus.PASS))) {
            throw new IllegalStateException("G08 requires all availability parameters to pass");
        }
        if (id == CaseId.G11 && result.status() == GateStatus.PASS && (!operationsCompleted(CaseId.G02, CaseId.G06) ||
                controls.size() != SharedControl.values().length ||
                controls.values().stream().anyMatch(item -> item.status() != GateStatus.PASS) ||
                !duringOperationEvidenceVerified() || activeOperation != null)) {
            throw new IllegalStateException("G11 requires G02 and G06 to pass, every shared control parameter to pass with verified evidence, every during-operation control to be recorded while its operation was active, and no active operation");
        }
        cases.put(id, result);
        recorded.add(id);
        if (activeOperation == id) activeOperation = null;
        if (result.status() == GateStatus.FAIL) stopped = true;
    }

    public void recordAvailability(Availability parameter, Result result) {
        Objects.requireNonNull(parameter, "parameter");
        Objects.requireNonNull(result, "result");
        if (recorded.contains(CaseId.G08)) throw new IllegalStateException("G08 already recorded");
        if (availability.containsKey(parameter)) throw new IllegalStateException("parameter already recorded");
        if (stopped && (result.status() == GateStatus.PASS || result.status() == GateStatus.FAIL)) throw new IllegalStateException("execution stopped");
        availability.put(parameter, result);
        if (result.status() == GateStatus.FAIL) stopped = true;
    }

    private static boolean hasFailure(Map<?, Result> results) {
        return results.values().stream().anyMatch(item -> item.status() == GateStatus.FAIL);
    }

    public void recordControl(SharedControl parameter, Result result) {
        Objects.requireNonNull(parameter, "parameter");
        Objects.requireNonNull(result, "result");
        if (recorded.contains(CaseId.G11)) throw new IllegalStateException("G11 already recorded");
        if (controls.containsKey(parameter)) throw new IllegalStateException("parameter already recorded");
        if (stopped && (result.status() == GateStatus.PASS || result.status() == GateStatus.FAIL)) throw new IllegalStateException("execution stopped");
        if (isDuringOperation(parameter)) {
            CaseId owner = parameter.name().endsWith("_G02") ? CaseId.G02 : CaseId.G06;
            if (activeOperation != owner) {
                throw new IllegalStateException("during-operation control must be recorded while its operation is active");
            }
            controlsRecordedDuringOperation.add(parameter);
        }
        controls.put(parameter, result);
        if (result.status() == GateStatus.FAIL) stopped = true;
    }

    private static boolean isDuringOperation(SharedControl parameter) {
        return parameter.name().startsWith("LOG_DURING_") || parameter.name().startsWith("LOCK_DURING_");
    }

    private boolean operationsCompleted(CaseId... ids) {
        for (CaseId id : ids) {
            Result result = cases.get(id);
            if (!recorded.contains(id) || result == null || result.status() != GateStatus.PASS) return false;
        }
        return true;
    }

    private boolean duringOperationEvidenceVerified() {
        for (SharedControl parameter : SharedControl.values()) {
            if (!(parameter.name().startsWith("LOG_DURING_") || parameter.name().startsWith("LOCK_DURING_"))) continue;
            if (!controlsRecordedDuringOperation.contains(parameter)) return false;
            if (controls.get(parameter).fixture() != Fixture.VERIFIED) return false;
        }
        return true;
    }

    public Map<SharedControl, Result> controls() { return Collections.unmodifiableMap(controls); }
    public Map<String, Result> prerequisites() { return Collections.unmodifiableMap(prerequisites); }
    public Map<CaseId, Result> cases() { return Collections.unmodifiableMap(cases); }
    public Map<Availability, Result> availability() { return Collections.unmodifiableMap(availability); }
}
