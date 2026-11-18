//! Exercise the descriptor emitted by privileged programs against the desktop lifecycle consumer.
#[path = "../../../voboost-installer/src-tauri/src/operation_lifecycle.rs"]
#[allow(dead_code)]
mod consumer;
#[path = "../../../voboost-inject/contract/operation.rs"]
#[allow(dead_code)]
mod producer;

fn descriptor(
    operation: producer::Operation,
    phase: &str,
    outcome: &str,
    reboots: u32,
    sequence: u64,
) -> producer::OperationDescriptor {
    let mut value = producer::OperationDescriptor::new(
        operation,
        "a".repeat(64),
        format!(
            "recovery/{}/{}-journal.json",
            "a".repeat(64),
            operation.as_str()
        ),
        "01234567-89ab-cdef-0123-456789abcdef".into(),
    )
    .unwrap();
    if operation == producer::Operation::Update {
        value.journal = format!("recovery/{}/product-proposal.json", "a".repeat(64));
    }
    value.phase = phase.into();
    value.outcome = outcome.into();
    value.reboot_count = reboots;
    value.status_sequence = sequence;
    value
}

#[test]
fn real_operation_bytes_are_accepted_by_desktop_lifecycle() {
    let mut complete = descriptor(
        producer::Operation::Install,
        "install-complete",
        "complete",
        2,
        36,
    );
    complete.installed_apk = Some(
        serde_json::json!({"certificateSha256":"b".repeat(64),"packageName":"ru.voboost",
        "path":"/data/app/ru.voboost-test/base.apk","sha256":"c".repeat(64),"size":1234,"versionCode":1}),
    );
    for value in [
        complete,
        descriptor(
            producer::Operation::Update,
            "update-product-copying",
            "running",
            0,
            1,
        ),
        descriptor(
            producer::Operation::Install,
            "install-prepare",
            "running",
            0,
            1,
        ),
        descriptor(
            producer::Operation::Install,
            "install-reboot-first",
            "reboot-required",
            1,
            17,
        ),
        descriptor(
            producer::Operation::Restore,
            "restore-complete",
            "complete",
            2,
            33,
        ),
        descriptor(
            producer::Operation::Uninstall,
            "uninstall-absence-verify",
            "running",
            0,
            19,
        ),
    ] {
        let bytes = value.canonical_bytes().unwrap();
        let decoded = consumer::operation_report(&bytes).unwrap();
        assert_eq!(decoded.operation, value.operation.as_str());
        assert_eq!(decoded.transaction_id, value.transaction_id);
        assert_eq!(decoded.boot_id, value.boot_id);
        assert_eq!(decoded.status_sequence, value.status_sequence);
    }
}

#[test]
fn desktop_rejects_structurally_valid_but_inconsistent_operation_progress() {
    for value in [
        descriptor(
            producer::Operation::Restore,
            "restore-complete",
            "complete",
            0,
            33,
        ),
        descriptor(
            producer::Operation::Install,
            "install-prepare",
            "running",
            0,
            2,
        ),
        descriptor(
            producer::Operation::Restore,
            "restore-prepare",
            "complete",
            0,
            3,
        ),
    ] {
        let bytes = value.canonical_bytes().unwrap();
        assert!(consumer::operation_report(&bytes).is_err());
    }
}

#[test]
fn uninstall_never_publishes_a_managed_completion_in_place_of_external_absence() {
    let value = descriptor(
        producer::Operation::Uninstall,
        "uninstall-absence-verify",
        "complete",
        1,
        21,
    );
    assert!(value.canonical_bytes().is_err());
    let mut forged = serde_json::to_vec(&value).unwrap();
    forged.push(b'\n');
    assert!(consumer::operation_report(&forged).is_err());
}
