use anyhow::{Context, Result, ensure};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::fs;
use std::path::Path;

const RELEASE_IDENTITY: &str = "1.0.0-beta1";
const JOURNAL_SHA256: &str = "2222222222222222222222222222222222222222222222222222222222222222";
const CERTIFICATE_SHA256: &str = "3333333333333333333333333333333333333333333333333333333333333333";
const APK_SHA256: &str = "4444444444444444444444444444444444444444444444444444444444444444";
const MANIFEST_SHA256: &str = "5555555555555555555555555555555555555555555555555555555555555555";
const INSTALLED_MANIFEST_SHA256: &str =
    "8888888888888888888888888888888888888888888888888888888888888888";
const CATALOG_SHA256: &str = "6666666666666666666666666666666666666666666666666666666666666666";
const GENERATION_ID: &str = "7777777777777777777777777777777777777777777777777777777777777777";
const TRANSACTION_ID: &str = MANIFEST_SHA256;
const BOOT_ID: &str = "01234567-89ab-cdef-0123-456789abcdef";
const UNINSTALL_BOOT_ID: &str = "fedcba98-7654-3210-fedc-ba9876543210";
const SHA256SUMS: &str = "SHA256SUMS";

#[derive(Clone, Copy, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "lowercase")]
enum Operation {
    Install,
}

#[derive(Clone, Copy, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "kebab-case")]
enum Outcome {
    Complete,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct InstalledApk {
    certificate_sha256: String,
    package_name: String,
    path: String,
    sha256: String,
    size: u64,
    version_code: u64,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct OperationDocument {
    schema_version: u32,
    operation: Operation,
    transaction_id: String,
    journal: String,
    phase: String,
    outcome: Outcome,
    reboot_count: u32,
    boot_id: String,
    status_sequence: u64,
    journal_sha256: String,
    error: Option<OperationError>,
    installed_apk: Option<InstalledApk>,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(deny_unknown_fields)]
struct OperationError {
    code: String,
    message: String,
}

#[derive(Clone, Copy, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "lowercase")]
enum Health {
    Healthy,
    Unknown,
}

#[derive(Clone, Copy, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "lowercase")]
enum StartupMode {
    Interface,
    Hidden,
    None,
}

#[derive(Clone, Copy, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "lowercase")]
enum DaemonStatus {
    Running,
    Unknown,
}

#[derive(Clone, Copy, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "lowercase")]
enum RecoveryStatus {
    Managed,
    Unknown,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct LastOperation {
    operation: String,
    outcome: String,
    phase: String,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct InstalledInspection {
    schema_version: u32,
    installed: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    installed_version: Option<String>,
    health: Health,
    startup_mode: StartupMode,
    daemon_status: DaemonStatus,
    #[serde(skip_serializing_if = "Option::is_none")]
    active_generation: Option<String>,
    /// Content identity of the installed product: the SHA-256 of the exact
    /// verified active manifest bytes.
    #[serde(skip_serializing_if = "Option::is_none")]
    manifest_sha256: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    last_operation: Option<LastOperation>,
    recovery_status: RecoveryStatus,
    startup_control_available: bool,
    logs_available: bool,
    upgrade_compatible: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    diagnostic: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct StartupModeReceipt {
    schema_version: u32,
    operation: String,
    requested: StartupMode,
    confirmed: StartupMode,
    installed_version: String,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct StatusDocument {
    schema_version: u32,
    release_identity: String,
    manifest_sha256: String,
    generation_id: String,
    catalog_sha256: String,
    startup_mode: StartupMode,
    state: String,
    killed: bool,
    panic: bool,
    injections: Vec<StatusInjection>,
    /// Catalog executors of the active generation (id, processes, surfaces).
    #[serde(default)]
    executors: Vec<StatusExecutor>,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct StatusExecutor {
    id: String,
    processes: Vec<String>,
    surfaces: Vec<String>,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct StatusInjection {
    id: String,
    process: String,
    state: String,
    reason: String,
    details: serde_json::Value,
    #[serde(skip_serializing_if = "Option::is_none")]
    document_generation: Option<i64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    lifecycle_generation: Option<i64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    current_endpoint: Option<CurrentEndpoint>,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct CurrentEndpoint {
    target_pid: String,
    target_starttime: String,
    endpoint_epoch: String,
    generation_id: String,
    catalog_sha256: String,
    desired_document_generation: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    accepted_status: Option<AcceptedStatus>,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct AcceptedStatus {
    document_generation: String,
    receipt: AcceptanceReceipt,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct AcceptanceReceipt {
    sequence: String,
    daemon_pid: String,
    daemon_starttime: String,
    boot_id: String,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct AbsenceCheck {
    name: String,
    absent: bool,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct UninstallAbsence {
    schema_version: u32,
    boot_id: String,
    checks: Vec<AbsenceCheck>,
}

struct Fixture {
    name: &'static str,
    bytes: Vec<u8>,
}

fn canonical<T: Serialize>(value: &T) -> Result<Vec<u8>> {
    let mut bytes = serde_json::to_vec(value).context("serialize canonical wire fixture")?;
    bytes.push(b'\n');
    Ok(bytes)
}

fn fixtures() -> Result<Vec<Fixture>> {
    let operation = OperationDocument {
        schema_version: 1,
        operation: Operation::Install,
        transaction_id: TRANSACTION_ID.into(),
        journal: format!("recovery/{TRANSACTION_ID}/install-journal.json"),
        phase: "install-complete".into(),
        outcome: Outcome::Complete,
        reboot_count: 2,
        boot_id: BOOT_ID.into(),
        status_sequence: 36,
        journal_sha256: JOURNAL_SHA256.into(),
        error: None,
        installed_apk: Some(InstalledApk {
            certificate_sha256: CERTIFICATE_SHA256.into(),
            package_name: "ru.voboost".into(),
            path: "/data/app/ru.voboost-0123456789abcdef/base.apk".into(),
            sha256: APK_SHA256.into(),
            size: 12_345_678,
            version_code: 1,
        }),
    };
    let healthy = InstalledInspection {
        schema_version: 1,
        installed: true,
        installed_version: Some(RELEASE_IDENTITY.into()),
        health: Health::Healthy,
        startup_mode: StartupMode::Interface,
        daemon_status: DaemonStatus::Running,
        active_generation: Some(GENERATION_ID.into()),
        manifest_sha256: Some(INSTALLED_MANIFEST_SHA256.into()),
        last_operation: Some(LastOperation {
            operation: "install".into(),
            outcome: "complete".into(),
            phase: "install-complete".into(),
        }),
        recovery_status: RecoveryStatus::Managed,
        startup_control_available: true,
        logs_available: true,
        upgrade_compatible: true,
        diagnostic: None,
    };
    let unknown = InstalledInspection {
        schema_version: 1,
        installed: true,
        installed_version: None,
        health: Health::Unknown,
        startup_mode: StartupMode::None,
        daemon_status: DaemonStatus::Unknown,
        active_generation: None,
        manifest_sha256: None,
        last_operation: None,
        recovery_status: RecoveryStatus::Unknown,
        startup_control_available: false,
        logs_available: true,
        upgrade_compatible: false,
        diagnostic: Some("signed manifest is unavailable".into()),
    };
    let startup = StartupModeReceipt {
        schema_version: 1,
        operation: "startup-mode".into(),
        requested: StartupMode::Hidden,
        confirmed: StartupMode::Hidden,
        installed_version: RELEASE_IDENTITY.into(),
    };
    let status = |startup_mode, killed| StatusDocument {
        schema_version: 1,
        release_identity: RELEASE_IDENTITY.into(),
        manifest_sha256: MANIFEST_SHA256.into(),
        generation_id: GENERATION_ID.into(),
        catalog_sha256: CATALOG_SHA256.into(),
        startup_mode,
        state: "ready".into(),
        killed,
        panic: false,
        injections: vec![StatusInjection {
            id: "settings-menu".into(),
            process: "com.qinggan.app.setting".into(),
            state: "active".into(),
            reason: "ready".into(),
            details: serde_json::Value::Object(Default::default()),
            document_generation: Some(17),
            lifecycle_generation: Some(17),
            current_endpoint: None,
        }],
        executors: Vec::new(),
    };
    let absence = UninstallAbsence {
        schema_version: 1,
        boot_id: UNINSTALL_BOOT_ID.into(),
        checks: [
            "root",
            "app-data",
            "init-file",
            "package",
            "app-op",
            "mounts",
            "processes",
            "init-services",
            "localization-overlays",
            "policy",
        ]
        .into_iter()
        .map(|name| AbsenceCheck {
            name: name.into(),
            absent: true,
        })
        .collect(),
    };
    Ok(vec![
        Fixture {
            name: "operation-install-complete.json",
            bytes: canonical(&operation)?,
        },
        Fixture {
            name: "installed-healthy.json",
            bytes: canonical(&healthy)?,
        },
        Fixture {
            name: "installed-unknown-no-version.json",
            bytes: canonical(&unknown)?,
        },
        Fixture {
            name: "startup-mode-hidden.json",
            bytes: canonical(&startup)?,
        },
        Fixture {
            name: "status-interface.json",
            bytes: canonical(&status(StartupMode::Interface, false))?,
        },
        Fixture {
            name: "status-none.json",
            bytes: canonical(&status(StartupMode::None, true))?,
        },
        Fixture {
            name: "uninstall-absence.json",
            bytes: canonical(&absence)?,
        },
    ])
}

fn checksum_bytes(fixtures: &[Fixture]) -> Vec<u8> {
    let mut fixtures = fixtures.iter().collect::<Vec<_>>();
    fixtures.sort_by_key(|fixture| fixture.name);
    fixtures
        .into_iter()
        .map(|fixture| {
            format!(
                "{}  {}\n",
                hex::encode(Sha256::digest(&fixture.bytes)),
                fixture.name
            )
        })
        .collect::<String>()
        .into_bytes()
}

pub fn write_fixtures(directory: &Path) -> Result<()> {
    fs::create_dir_all(directory)
        .with_context(|| format!("create wire fixture directory {}", directory.display()))?;
    let fixtures = fixtures()?;
    for fixture in &fixtures {
        fs::write(directory.join(fixture.name), &fixture.bytes)
            .with_context(|| format!("write wire fixture {}", fixture.name))?;
    }
    fs::write(directory.join(SHA256SUMS), checksum_bytes(&fixtures))
        .with_context(|| format!("write wire fixture {SHA256SUMS}"))?;
    Ok(())
}

pub fn verify_fixtures(directory: &Path) -> Result<()> {
    let fixtures = fixtures()?;
    let mut actual_names = fs::read_dir(directory)
        .with_context(|| format!("read wire fixture directory {}", directory.display()))?
        .map(|entry| {
            entry
                .map(|value| value.file_name().to_string_lossy().into_owned())
                .context("read wire fixture entry")
        })
        .collect::<Result<Vec<_>>>()?;
    actual_names.sort();
    let mut expected_names = fixtures
        .iter()
        .map(|fixture| fixture.name.to_string())
        .chain([SHA256SUMS.to_string()])
        .collect::<Vec<_>>();
    expected_names.sort();
    ensure!(
        actual_names == expected_names,
        "wire fixture inventory differs"
    );
    for fixture in &fixtures {
        let actual = fs::read(directory.join(fixture.name))
            .with_context(|| format!("read wire fixture {}", fixture.name))?;
        ensure!(
            actual == fixture.bytes,
            "wire fixture {} differs",
            fixture.name
        );
    }
    let sums = fs::read(directory.join(SHA256SUMS))
        .with_context(|| format!("read wire fixture {SHA256SUMS}"))?;
    ensure!(
        sums == checksum_bytes(&fixtures),
        "wire fixture hashes differ"
    );
    Ok(())
}

#[cfg(test)]
mod tests {
    #[test]
    fn exact_inject_serializer_fixture_is_accepted_by_product_wire_consumer() {
        let root = std::env::var_os("VOBOOST_INJECT_CONTRACT_ROOT")
            .map(std::path::PathBuf::from)
            .unwrap_or_else(|| {
                std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("../../voboost-inject")
            });
        let fixture: serde_json::Value = serde_json::from_slice(
            &std::fs::read(root.join("daemon/tests/fixtures/current-endpoint146.json")).unwrap(),
        )
        .unwrap();
        for key in ["accepted", "pending", "reboundPending"] {
            let endpoint: super::CurrentEndpoint =
                serde_json::from_value(fixture[key].clone()).unwrap();
            assert_eq!(serde_json::to_value(endpoint).unwrap(), fixture[key]);
        }
        let mut wrong = fixture["accepted"].clone();
        wrong["targetStarttime"] = serde_json::json!(9007199254740993u64);
        assert!(serde_json::from_value::<super::CurrentEndpoint>(wrong).is_err());
    }

    use super::*;
    use crate::verify::parse_canonical_json;

    fn fixture<'a>(fixtures: &'a [Fixture], name: &str) -> &'a [u8] {
        &fixtures
            .iter()
            .find(|fixture| fixture.name == name)
            .unwrap()
            .bytes
    }

    #[test]
    fn producer_owned_wire_fixtures_match_checked_in_exact_bytes_and_hashes() {
        verify_fixtures(&Path::new(env!("CARGO_MANIFEST_DIR")).join("fixtures/wire")).unwrap();
    }

    #[test]
    fn wire_fixtures_are_strict_typed_canonical_schema_one_documents() {
        let fixtures = fixtures().unwrap();
        let operation = parse_canonical_json::<OperationDocument>(fixture(
            &fixtures,
            "operation-install-complete.json",
        ))
        .unwrap();
        assert_eq!(operation.transaction_id, MANIFEST_SHA256);
        assert_eq!(operation.status_sequence, 36);
        parse_canonical_json::<InstalledInspection>(fixture(&fixtures, "installed-healthy.json"))
            .unwrap();
        let unknown = parse_canonical_json::<InstalledInspection>(fixture(
            &fixtures,
            "installed-unknown-no-version.json",
        ))
        .unwrap();
        assert_eq!(unknown.installed_version, None);
        parse_canonical_json::<StartupModeReceipt>(fixture(&fixtures, "startup-mode-hidden.json"))
            .unwrap();
        let interface =
            parse_canonical_json::<StatusDocument>(fixture(&fixtures, "status-interface.json"))
                .unwrap();
        assert_eq!(interface.startup_mode, StartupMode::Interface);
        assert!(!interface.killed);
        let none =
            parse_canonical_json::<StatusDocument>(fixture(&fixtures, "status-none.json")).unwrap();
        assert_eq!(none.startup_mode, StartupMode::None);
        assert!(none.killed);
        let absence =
            parse_canonical_json::<UninstallAbsence>(fixture(&fixtures, "uninstall-absence.json"))
                .unwrap();
        assert!(absence.checks.iter().all(|check| check.absent));
    }

    #[test]
    fn wire_parser_rejects_unknown_duplicate_and_noncanonical_fields() {
        let fixtures = fixtures().unwrap();
        let healthy = fixture(&fixtures, "installed-healthy.json");
        let unknown = [b"{\"extra\":true,".as_slice(), &healthy[1..]].concat();
        assert!(parse_canonical_json::<InstalledInspection>(&unknown).is_err());
        let duplicate = [b"{\"schemaVersion\":1,".as_slice(), &healthy[1..]].concat();
        assert!(parse_canonical_json::<InstalledInspection>(&duplicate).is_err());
        let pretty: serde_json::Value = serde_json::from_slice(healthy).unwrap();
        let mut bytes = serde_json::to_vec_pretty(&pretty).unwrap();
        bytes.push(b'\n');
        assert!(parse_canonical_json::<InstalledInspection>(&bytes).is_err());
    }

    #[test]
    fn writer_creates_only_the_closed_wire_inventory() {
        let temporary = tempfile::tempdir().unwrap();
        write_fixtures(temporary.path()).unwrap();
        verify_fixtures(temporary.path()).unwrap();
        fs::write(temporary.path().join("extra.json"), b"{}\n").unwrap();
        assert!(verify_fixtures(temporary.path()).is_err());
    }
}
