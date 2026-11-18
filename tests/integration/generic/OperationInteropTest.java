package ru.voboost.tests.wire;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.voboost.tests.AcceptancePlan;
import ru.voboost.tests.AcceptanceReport;
import ru.voboost.tests.CaseEvidence;
import ru.voboost.tests.GateStatus;
import ru.voboost.tests.RustBoundary;

class OperationInteropTest {
    @Test void realProducerBytesReachTheActualDesktopConsumers() throws Exception {
        Path operations = RustBoundary.verify("operation_interop", List.of(
            "real_operation_bytes_are_accepted_by_desktop_lifecycle",
            "desktop_rejects_structurally_valid_but_inconsistent_operation_progress",
            "uninstall_never_publishes_a_managed_completion_in_place_of_external_absence"));
        Path logs = RustBoundary.verify("log_interop", List.of(
            "daemon_stream_is_consumed_without_changing_records_or_device_context",
            "consumer_rejects_real_stream_corruption_truncation_and_trailing_bytes",
            "malformed_record_and_context_are_rejected_even_with_a_valid_stream_digest"));
        CaseEvidence.record(new AcceptancePlan.Key("I02", "product"), new AcceptanceReport.Result(GateStatus.PASS,
            "Actual operation producer bytes enter the desktop decoder; actual log wire rejects corrupted, truncated and malformed input",
            List.of(operations, logs)));
    }
}
