package ru.voboost.tests.installer.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.GateStatus;
import ru.voboost.tests.installer.gui.GuiReport.Availability;
import ru.voboost.tests.installer.gui.GuiReport.CaseId;
import ru.voboost.tests.installer.gui.GuiReport.Fixture;
import ru.voboost.tests.installer.gui.GuiReport.Result;
import ru.voboost.tests.installer.gui.GuiReport.SkipCause;
import ru.voboost.tests.installer.gui.GuiReport.SharedControl;

class InstallerGuiReportTest {
    private static Result pass() { return new Result(GateStatus.PASS, "observed", "evidence", Fixture.VERIFIED); }
    private static Result fail() { return new Result(GateStatus.FAIL, "failed", "evidence", Fixture.VERIFIED); }
    private static GuiReport ready() { return new GuiReport(Map.of("session", pass())); }

    private static void executeOperation(GuiReport report, CaseId id) {
        report.beginOperation(id);
        report.recordControl(duringControl("LOG", id), pass());
        report.recordControl(duringControl("LOCK", id), pass());
        report.record(id, pass());
    }

    private static SharedControl duringControl(String prefix, CaseId id) {
        return SharedControl.valueOf(prefix + "_DURING_" + id.name());
    }

    private static void recordRemainingControls(GuiReport report) {
        for (SharedControl control : SharedControl.values()) {
            if (isDuringOperation(control) || report.controls().containsKey(control)) continue;
            report.recordControl(control, pass());
        }
    }

    private static boolean isDuringOperation(SharedControl control) {
        return control.name().startsWith("LOG_DURING_") || control.name().startsWith("LOCK_DURING_");
    }

    @Test void g11PassRejectsDuringOperationControlWithUnverifiedFixture() {
        GuiReport report = ready();
        report.beginOperation(CaseId.G02);
        report.recordControl(SharedControl.LOG_DURING_G02, pass());
        report.recordControl(SharedControl.LOCK_DURING_G02,
                new Result(GateStatus.NOT_APPLICABLE, "observed without fixture", null, Fixture.UNKNOWN));
        report.record(CaseId.G02, pass());
        executeOperation(report, CaseId.G06);
        recordRemainingControls(report);
        assertThrows(IllegalStateException.class, () -> report.record(CaseId.G11, pass()));
    }

    @Test void beginOperationStopsAfterFailure() {
        GuiReport report = ready();
        assertThrows(IllegalArgumentException.class, () -> report.beginOperation(CaseId.G01));
        assertThrows(NullPointerException.class, () -> report.beginOperation(null));
        report.record(CaseId.G01, fail());
        assertThrows(IllegalStateException.class, () -> report.beginOperation(CaseId.G02));
        assertThrows(IllegalStateException.class, () -> report.recordControl(SharedControl.RU, pass()));
    }

    @Test void blockedAndNotApplicableCasesRemainRecordableAfterStop() {
        GuiReport report = new GuiReport(Map.of("session",
                new Result(GateStatus.BLOCKED, "prerequisite blocked", null, Fixture.UNKNOWN)));
        report.record(CaseId.G01, new Result(GateStatus.BLOCKED, "prerequisite blocked", null, Fixture.UNKNOWN));
        report.record(CaseId.G02, new Result(GateStatus.NOT_APPLICABLE, "not applicable", null, Fixture.UNKNOWN));
        assertEquals(GateStatus.BLOCKED, report.cases().get(CaseId.G01).status());
        assertEquals(GateStatus.NOT_APPLICABLE, report.cases().get(CaseId.G02).status());
        assertThrows(IllegalArgumentException.class, () -> new Result(GateStatus.PASS, "observed", null, Fixture.VERIFIED));
        assertThrows(IllegalArgumentException.class, () -> new Result(GateStatus.FAIL, " ", null, Fixture.UNKNOWN));
    }

    @Test void casesAreUniqueAndInitiallyNotRun() {
        GuiReport report = ready();
        assertEquals(11, report.cases().size());
        for (CaseId id : CaseId.values()) assertEquals(GateStatus.NOT_RUN, report.cases().get(id).status());
        report.record(CaseId.G01, pass());
        assertThrows(IllegalStateException.class, () -> report.record(CaseId.G01, pass()));
        assertThrows(UnsupportedOperationException.class, () -> report.cases().clear());
    }

    @Test void prerequisiteAndFailureStopMutations() {
        GuiReport blocked = new GuiReport(Map.of("session", fail()));
        assertFalse(blocked.mutationAllowed());
        assertThrows(IllegalStateException.class, () -> blocked.record(CaseId.G01, pass()));
        GuiReport report = ready();
        assertTrue(report.mutationAllowed());
        report.record(CaseId.G01, fail());
        assertFalse(report.mutationAllowed());
        assertThrows(IllegalStateException.class, () -> report.record(CaseId.G02, pass()));
        report.record(CaseId.G02, GuiReport.skipped(SkipCause.PREVIOUS_FAILURE, "previous failure"));
        assertEquals(GateStatus.NOT_RUN, report.cases().get(CaseId.G02).status());
    }

    @Test void passRequiresVerifiedFixtureAndEvidence() {
        for (Fixture fixture : Fixture.values()) {
            if (fixture == Fixture.VERIFIED) continue;
            assertThrows(IllegalArgumentException.class, () -> new Result(GateStatus.PASS, "observed", "evidence", fixture));
        }
        assertThrows(IllegalArgumentException.class, () -> new Result(GateStatus.PASS, "observed", " ", Fixture.VERIFIED));
        assertEquals(GateStatus.BLOCKED, GuiReport.skipped(SkipCause.MISSING_FIXTURE, "missing fixture").status());
        assertEquals(GateStatus.BLOCKED, GuiReport.skipped(SkipCause.MISSING_PREREQUISITE, "missing prerequisite").status());
        assertEquals(GateStatus.NOT_RUN, GuiReport.skipped(SkipCause.NOT_SELECTED, "not selected").status());
    }

    @Test void availabilityParametersAreSeparateAndAllRequiredForG08() {
        GuiReport report = ready();
        report.recordAvailability(Availability.NO_DEVICE, pass());
        assertThrows(IllegalStateException.class, () -> report.record(CaseId.G08, pass()));
        assertThrows(IllegalStateException.class, () -> report.recordAvailability(Availability.NO_DEVICE, pass()));
        report.recordAvailability(Availability.UNAUTHORIZED, pass());
        report.recordAvailability(Availability.UNSUPPORTED, pass());
        report.record(CaseId.G08, pass());
        assertEquals(3, report.availability().size());
        assertEquals(GateStatus.PASS, report.cases().get(CaseId.G08).status());
    }

    @Test void parameterFailureAlsoStopsMutations() {
        GuiReport report = ready();
        report.recordAvailability(Availability.NO_DEVICE, fail());
        assertFalse(report.mutationAllowed());
        assertThrows(IllegalStateException.class, () -> report.recordAvailability(Availability.UNAUTHORIZED, pass()));
    }

    @Test void failedParameterCanBeReportedAsParentFailure() {
        GuiReport report = ready();
        report.recordAvailability(Availability.NO_DEVICE, fail());
        report.record(CaseId.G08, fail());
        assertEquals(GateStatus.FAIL, report.cases().get(CaseId.G08).status());
        assertFalse(report.mutationAllowed());
    }

    @Test void incompleteSharedControlsCannotPass() {
        GuiReport report = ready();
        assertThrows(IllegalStateException.class, () -> report.record(CaseId.G11, pass()));
    }

    @Test void sharedControlResultsAndFailureAreRecorded() {
        GuiReport report = ready();
        executeOperation(report, CaseId.G02);
        executeOperation(report, CaseId.G06);
        recordRemainingControls(report);
        report.record(CaseId.G11, pass());
        assertEquals(GateStatus.PASS, report.cases().get(CaseId.G11).status());

        GuiReport failed = ready();
        failed.recordControl(SharedControl.RU, fail());
        assertThrows(IllegalStateException.class, () -> failed.record(CaseId.G11, GuiReport.skipped(SkipCause.PREVIOUS_FAILURE, "failure")));
        failed.record(CaseId.G11, fail());
        assertFalse(failed.mutationAllowed());
        assertThrows(IllegalStateException.class, () -> failed.record(CaseId.G02, pass()));
    }

    @Test void g11PassRequiresG02AndG06Pass() {
        for (SkipCause cause : new SkipCause[] { SkipCause.MISSING_FIXTURE, SkipCause.NOT_SELECTED }) {
            GuiReport report = ready();
            report.beginOperation(CaseId.G02);
            report.recordControl(SharedControl.LOG_DURING_G02, pass());
            report.recordControl(SharedControl.LOCK_DURING_G02, pass());
            report.record(CaseId.G02, GuiReport.skipped(cause, "g02 not passed"));
            executeOperation(report, CaseId.G06);
            recordRemainingControls(report);
            assertEquals(cause == SkipCause.MISSING_FIXTURE ? GateStatus.BLOCKED : GateStatus.NOT_RUN,
                    report.cases().get(CaseId.G02).status());
            assertThrows(IllegalStateException.class, () -> report.record(CaseId.G11, pass()));
        }

        GuiReport notRun = ready();
        executeOperation(notRun, CaseId.G06);
        recordRemainingControls(notRun);
        assertThrows(IllegalStateException.class, () -> notRun.record(CaseId.G11, pass()));
    }

    @Test void g11PassStillBlockedAfterG02AndG06PassWhenControlsIncomplete() {
        GuiReport report = ready();
        executeOperation(report, CaseId.G02);
        executeOperation(report, CaseId.G06);
        for (SharedControl control : SharedControl.values()) {
            if (isDuringOperation(control) || control == SharedControl.COPY) continue;
            report.recordControl(control, pass());
        }
        assertEquals(12, report.controls().size());
        assertThrows(IllegalStateException.class, () -> report.record(CaseId.G11, pass()));
        report.recordControl(SharedControl.COPY, pass());
        report.record(CaseId.G11, pass());
        assertEquals(GateStatus.PASS, report.cases().get(CaseId.G11).status());
    }

    @Test void duringOperationControlRequiresActiveOperation() {
        GuiReport beforeBegin = ready();
        assertThrows(IllegalStateException.class, () -> beforeBegin.recordControl(SharedControl.LOG_DURING_G02, pass()));
        assertThrows(IllegalStateException.class, () -> beforeBegin.recordControl(SharedControl.LOCK_DURING_G06, pass()));

        GuiReport completed = ready();
        executeOperation(completed, CaseId.G02);
        assertThrows(IllegalStateException.class, () -> completed.recordControl(SharedControl.LOG_DURING_G02, pass()));

        GuiReport otherActive = ready();
        otherActive.beginOperation(CaseId.G02);
        assertThrows(IllegalStateException.class, () -> otherActive.recordControl(SharedControl.LOG_DURING_G06, pass()));
        assertThrows(IllegalStateException.class, () -> otherActive.beginOperation(CaseId.G06));
        otherActive.recordControl(SharedControl.LOG_DURING_G02, pass());
        otherActive.recordControl(SharedControl.LOCK_DURING_G02, pass());
        otherActive.record(CaseId.G02, pass());

        GuiReport otherAfterCompletion = ready();
        otherAfterCompletion.beginOperation(CaseId.G06);
        assertThrows(IllegalStateException.class, () -> otherAfterCompletion.recordControl(SharedControl.LOG_DURING_G02, pass()));
        otherAfterCompletion.record(CaseId.G06, pass());
    }

    @Test void g11PassRejectsOperationLeftActive() {
        GuiReport report = ready();
        executeOperation(report, CaseId.G02);
        report.beginOperation(CaseId.G06);
        recordRemainingControls(report);
        assertThrows(IllegalStateException.class, () -> report.record(CaseId.G11, pass()));
    }

    @Test void g11PassRejectsMissingDuringOperationControl() {
        GuiReport report = ready();
        report.beginOperation(CaseId.G02);
        report.recordControl(SharedControl.LOG_DURING_G02, pass());
        report.record(CaseId.G02, pass());
        executeOperation(report, CaseId.G06);
        for (SharedControl control : SharedControl.values()) {
            if (!isDuringOperation(control)) report.recordControl(control, pass());
        }
        assertEquals(12, report.controls().size());
        assertThrows(IllegalStateException.class, () -> report.record(CaseId.G11, pass()));
        assertThrows(IllegalStateException.class, () -> report.recordControl(SharedControl.LOCK_DURING_G02, pass()));
    }
}
