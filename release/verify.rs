use crate::manifest::{
    ApkEvidence, ArtifactRole, MAX_APK_VERSION_CODE, Manifest, artifact_path, classify, identity,
    required_inventory, role_rank,
};
use anyhow::{Context, Result, bail, ensure};
use base64::Engine;
use ed25519_dalek::VerifyingKey;
use fluent_uri::Uri;
use semver::Version;
use serde::de::{DeserializeOwned, Deserializer, MapAccess, SeqAccess, Visitor};
use serde::{Deserialize, Serialize};
use serde_json::{Map, Number, Value};
use std::collections::{BTreeMap, BTreeSet, HashSet};
use std::fmt;
use std::fs;
use std::io::Read;
#[cfg(unix)]
use std::os::unix::fs::MetadataExt;
use std::path::Path;
use std::process::{Command, Stdio};
use std::time::SystemTime;

const ANDROID_BUILD_TOOLS: &str = "34.0.0";
const MAX_TOOL_OUTPUT: u64 = 1024 * 1024;
const APK_PRODUCT_ROOT_ASSET: &str = "assets/config/release-public.pem";
const ED25519_SPKI_PREFIX: &[u8; 12] = &[
    0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00,
];

#[derive(Clone, Debug, Eq, PartialEq)]
struct ApkStamp {
    size: u64,
    modified: SystemTime,
    #[cfg(unix)]
    device: u64,
    #[cfg(unix)]
    inode: u64,
    #[cfg(unix)]
    ctime: i64,
    #[cfg(unix)]
    ctime_nsec: i64,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Channel {
    pub schema_version: u32,
    pub channel: String,
    pub release_identity: String,
    pub manifest_url: String,
    pub manifest_size: u64,
    pub manifest_sha256: String,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct TrustRoots {
    pub schema_version: u32,
    pub release_identity: String,
    pub state: String,
    pub rotation: TrustRotation,
    pub roots: TrustRootSet,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct TrustRotation {
    pub mode: String,
    pub previous_document_sha256: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct TrustRootSet {
    pub android_certificate_sha256: Option<String>,
    pub agent_catalog_public_key_hex: Option<String>,
    pub product_public_key_hex: Option<String>,
    pub native_executable_public_key_hex: Option<String>,
    pub restore_public_key_hex: Option<String>,
    pub evidence_public_key_hex: Option<String>,
}

pub fn read_trust_roots(path: &Path) -> Result<TrustRoots> {
    let bytes = fs::read(path).with_context(|| format!("read trust roots {}", path.display()))?;
    let trust: TrustRoots = parse_canonical_json(&bytes)?;
    validate_trust_roots(&trust)?;
    Ok(trust)
}

pub fn validate_trust_roots(trust: &TrustRoots) -> Result<()> {
    ensure!(
        trust.schema_version == 1,
        "unsupported trust-root schemaVersion"
    );
    ensure!(
        trust.release_identity == "1.0.0-beta1",
        "trust roots releaseIdentity differs"
    );
    ensure!(
        trust.rotation.mode == "source-reviewed-no-runtime-rotation",
        "unsupported trust-root rotation mode"
    );
    if let Some(previous) = &trust.rotation.previous_document_sha256 {
        ensure!(
            crate::manifest::is_sha256(previous),
            "previous trust document digest is invalid"
        );
    }
    let roots = [
        trust.roots.agent_catalog_public_key_hex.as_ref(),
        trust.roots.product_public_key_hex.as_ref(),
        trust.roots.native_executable_public_key_hex.as_ref(),
        trust.roots.restore_public_key_hex.as_ref(),
        trust.roots.evidence_public_key_hex.as_ref(),
    ];
    match trust.state.as_str() {
        "unprovisioned" => {
            ensure!(
                trust.roots.android_certificate_sha256.is_none()
                    && roots.iter().all(|root| root.is_none()),
                "unprovisioned trust document must contain no placeholder authority"
            );
        }
        "provisioned" => {
            let certificate = trust
                .roots
                .android_certificate_sha256
                .as_ref()
                .context("provisioned Android certificate is missing")?;
            ensure!(
                crate::manifest::is_sha256(certificate),
                "Android certificate digest is invalid"
            );
            let values = roots
                .iter()
                .map(|root| root.context("provisioned Ed25519 root is missing"))
                .collect::<Result<Vec<_>>>()?;
            ensure!(
                values.iter().all(|root| crate::manifest::is_sha256(root)),
                "provisioned Ed25519 root is invalid"
            );
            ensure!(
                values.iter().collect::<HashSet<_>>().len() == values.len(),
                "release trust roles must use distinct public roots"
            );
        }
        _ => bail!("unknown trust-root state"),
    }
    Ok(())
}

pub fn read_manifest(path: &Path) -> Result<Manifest> {
    let bytes = fs::read(path).with_context(|| format!("read manifest {}", path.display()))?;
    let manifest: Manifest = parse_canonical_json(&bytes)?;
    validate_manifest(&manifest)?;
    Ok(manifest)
}

pub fn read_channel(path: &Path) -> Result<Channel> {
    let bytes = fs::read(path).with_context(|| format!("read channel {}", path.display()))?;
    let channel: Channel = parse_canonical_json(&bytes)?;
    validate_channel(&channel)?;
    Ok(channel)
}

pub fn inspect_apk(apk: &Path, android_sdk: &Path, source_commit: &str) -> Result<ApkEvidence> {
    let evidence = inspect_apk_identity(apk, android_sdk, source_commit)?;
    evidence.validate()?;
    Ok(evidence)
}
pub(crate) fn resource_xmltree(apk: &Path, android_sdk: &Path) -> Result<String> {
    bounded_output(
        Command::new(
            android_sdk
                .join("build-tools")
                .join(ANDROID_BUILD_TOOLS)
                .join("aapt2"),
        )
        .arg("dump")
        .arg("xmltree")
        .arg("--file")
        .arg("AndroidManifest.xml")
        .arg(apk),
        "resource aapt2 xmltree",
    )
}
pub(crate) fn resource_table(apk: &Path, android_sdk: &Path) -> Result<String> {
    bounded_output(
        Command::new(
            android_sdk
                .join("build-tools")
                .join(ANDROID_BUILD_TOOLS)
                .join("aapt2"),
        )
        .arg("dump")
        .arg("resources")
        .arg(apk),
        "resource aapt2 resource table",
    )
}
pub(crate) fn inspect_apk_identity(
    apk: &Path,
    android_sdk: &Path,
    source_commit: &str,
) -> Result<ApkEvidence> {
    let initial_stamp = apk_stamp(apk)?;
    let tools = android_sdk.join("build-tools").join(ANDROID_BUILD_TOOLS);
    let aapt2 = tools.join("aapt2");
    let apksigner = tools.join("apksigner");
    let properties = fs::read(tools.join("source.properties"))
        .context("read pinned Android build-tools source.properties")?;
    ensure!(
        properties.len() <= 64 * 1024,
        "Android build-tools properties exceed bound"
    );
    let properties =
        std::str::from_utf8(&properties).context("Android build-tools properties are not UTF-8")?;
    ensure!(
        properties
            .lines()
            .any(|line| line.trim() == "Pkg.Revision=34.0.0"
                || line.trim() == "Pkg.Revision = 34.0.0"),
        "Android build-tools revision is not 34.0.0"
    );
    ensure!(
        aapt2.is_file(),
        "pinned aapt2 is missing: {}",
        aapt2.display()
    );
    ensure!(
        apksigner.is_file(),
        "pinned apksigner is missing: {}",
        apksigner.display()
    );

    let badging = bounded_output(
        Command::new(&aapt2).arg("dump").arg("badging").arg(apk),
        "aapt2 dump badging",
    )?;
    let package_line = badging
        .lines()
        .find(|line| line.starts_with("package: "))
        .context("aapt2 output has no package row")?;
    ensure!(
        apk_stamp(apk)? == initial_stamp,
        "APK identity changed during aapt2 inspection"
    );
    let package_name = quoted_field(package_line, "name")?;
    let version_code = quoted_field(package_line, "versionCode")?
        .parse::<u64>()
        .context("APK versionCode is not an unsigned integer")?;

    let signer = bounded_output(
        Command::new(&apksigner)
            .arg("verify")
            .arg("--verbose")
            .arg("--print-certs")
            .arg(apk),
        "apksigner verify",
    )?;
    ensure!(
        apk_stamp(apk)? == initial_stamp,
        "APK identity changed during apksigner verification"
    );
    ensure!(
        signer.lines().any(|line| {
            line == "Verified using v2 scheme (APK Signature Scheme v2): true"
                || line == "Verified using v3 scheme (APK Signature Scheme v3): true"
                || line == "Verified using v3.1 scheme (APK Signature Scheme v3.1): true"
        }),
        "APK has no verified v2/v3 signer"
    );
    ensure!(
        signer.lines().any(|line| line == "Number of signers: 1"),
        "APK must have exactly one signer"
    );
    let certificate_sha256 = signer
        .lines()
        .find_map(|line| line.strip_prefix("Signer #1 certificate SHA-256 digest: "))
        .context("apksigner output has no signer certificate SHA-256")?
        .to_owned();
    ensure!(
        signer
            .lines()
            .filter(|line| line.starts_with("Signer #")
                && line.contains(" certificate SHA-256 digest: "))
            .count()
            == 1,
        "apksigner output contains multiple signer certificate digests"
    );

    let (size, sha256) = identity(apk)?;
    ensure!(
        apk_stamp(apk)? == initial_stamp,
        "APK identity changed during hashing"
    );
    let evidence = ApkEvidence {
        schema_version: 1,
        source_commit: source_commit.to_owned(),
        file: "voboost-app.apk".into(),
        size,
        sha256,
        package_name,
        version_code,
        certificate_sha256,
    };
    Ok(evidence)
}

fn canonical_ed25519_public_pem(raw: &[u8; 32]) -> String {
    let mut der = Vec::with_capacity(44);
    der.extend_from_slice(ED25519_SPKI_PREFIX);
    der.extend_from_slice(raw);
    format!(
        "-----BEGIN PUBLIC KEY-----\n{}\n-----END PUBLIC KEY-----\n",
        base64::engine::general_purpose::STANDARD.encode(der)
    )
}

pub fn write_ed25519_public_pem(raw_key: &Path, output: &Path) -> Result<()> {
    let raw: [u8; 32] = fs::read(raw_key)
        .with_context(|| format!("read {}", raw_key.display()))?
        .try_into()
        .map_err(|_| anyhow::anyhow!("public key must be raw 32-byte Ed25519"))?;
    VerifyingKey::from_bytes(&raw).context("public key is invalid Ed25519")?;
    fs::write(output, canonical_ed25519_public_pem(&raw))
        .with_context(|| format!("write {}", output.display()))
}

fn parse_ed25519_public_pem(bytes: &[u8]) -> Result<[u8; 32]> {
    ensure!(bytes.len() <= 4096, "APK product-root PEM exceeds bound");
    let text = std::str::from_utf8(bytes).context("APK product-root PEM is not UTF-8")?;
    let lines: Vec<_> = text.lines().collect();
    ensure!(
        lines.len() == 3
            && lines[0] == "-----BEGIN PUBLIC KEY-----"
            && lines[2] == "-----END PUBLIC KEY-----"
            && text.ends_with('\n'),
        "APK product-root PEM envelope is not canonical"
    );
    let der = base64::engine::general_purpose::STANDARD
        .decode(lines[1])
        .context("APK product-root PEM base64 is invalid")?;
    ensure!(
        der.len() == 44 && der[..12] == *ED25519_SPKI_PREFIX,
        "APK product-root key is not canonical Ed25519 SPKI"
    );
    let raw: [u8; 32] = der[12..].try_into().unwrap();
    VerifyingKey::from_bytes(&raw).context("APK product-root Ed25519 point is invalid")?;
    ensure!(
        canonical_ed25519_public_pem(&raw).as_bytes() == bytes,
        "APK product-root PEM spelling is not canonical"
    );
    Ok(raw)
}

fn apk_central_entry_count(bytes: &[u8], expected_name: &str) -> Result<usize> {
    const EOCD: &[u8; 4] = b"PK\x05\x06";
    const CENTRAL: &[u8; 4] = b"PK\x01\x02";
    ensure!(bytes.len() >= 22, "APK ZIP is too short");
    let search_start = bytes.len().saturating_sub(65_557);
    let eocd = (search_start..=bytes.len() - 22)
        .rev()
        .find(|offset| &bytes[*offset..*offset + 4] == EOCD)
        .context("APK ZIP end record is missing")?;
    let u16_at = |offset: usize| u16::from_le_bytes(bytes[offset..offset + 2].try_into().unwrap());
    let u32_at = |offset: usize| u32::from_le_bytes(bytes[offset..offset + 4].try_into().unwrap());
    ensure!(
        u16_at(eocd + 4) == 0 && u16_at(eocd + 6) == 0,
        "multi-disk APK ZIP is forbidden"
    );
    let entries = usize::from(u16_at(eocd + 10));
    ensure!(
        entries == usize::from(u16_at(eocd + 8)) && entries <= 4096,
        "APK central entry count is invalid"
    );
    let central_size = u32_at(eocd + 12) as usize;
    let central_offset = u32_at(eocd + 16) as usize;
    let comment = usize::from(u16_at(eocd + 20));
    ensure!(
        eocd + 22 + comment == bytes.len(),
        "APK ZIP trailing bytes differ"
    );
    let central_end = central_offset
        .checked_add(central_size)
        .context("APK central directory size overflow")?;
    ensure!(central_end == eocd, "APK central directory bounds differ");
    let mut cursor = central_offset;
    let mut count = 0;
    for _ in 0..entries {
        ensure!(cursor + 46 <= central_end, "APK central entry is truncated");
        ensure!(
            &bytes[cursor..cursor + 4] == CENTRAL,
            "APK central entry magic differs"
        );
        let name_len = usize::from(u16_at(cursor + 28));
        let extra_len = usize::from(u16_at(cursor + 30));
        let comment_len = usize::from(u16_at(cursor + 32));
        let end = cursor
            .checked_add(46 + name_len + extra_len + comment_len)
            .context("APK central entry size overflow")?;
        ensure!(end <= central_end, "APK central entry exceeds directory");
        if &bytes[cursor + 46..cursor + 46 + name_len] == expected_name.as_bytes() {
            count += 1;
        }
        cursor = end;
    }
    ensure!(
        cursor == central_end,
        "APK central directory has unparsed bytes"
    );
    Ok(count)
}

pub fn verify_apk_embedded_product_root(apk: &Path, expected_raw_key: &Path) -> Result<()> {
    let expected: [u8; 32] = fs::read(expected_raw_key)
        .with_context(|| format!("read {}", expected_raw_key.display()))?
        .try_into()
        .map_err(|_| anyhow::anyhow!("expected product root must be raw 32-byte Ed25519"))?;
    VerifyingKey::from_bytes(&expected).context("expected product root is invalid Ed25519")?;
    let apk_bytes = fs::read(apk).with_context(|| format!("read exact APK {}", apk.display()))?;
    ensure!(
        apk_bytes.len() <= 128 * 1024 * 1024,
        "APK exceeds product-root verification bound"
    );
    ensure!(
        apk_central_entry_count(&apk_bytes, APK_PRODUCT_ROOT_ASSET)? == 1,
        "APK must contain exactly one product-root asset"
    );
    let mut archive =
        zip::ZipArchive::new(std::io::Cursor::new(&apk_bytes)).context("parse APK ZIP")?;
    ensure!(archive.len() <= 4096, "APK entry count exceeds bound");
    let mut found = Vec::new();
    for index in 0..archive.len() {
        let mut entry = archive.by_index(index).context("read APK ZIP entry")?;
        if entry.name() != APK_PRODUCT_ROOT_ASSET {
            continue;
        }
        ensure!(!entry.is_dir(), "APK product-root asset is not a file");
        ensure!(
            entry.size() > 0 && entry.size() <= 4096 && entry.compressed_size() <= 4096,
            "APK product-root asset size exceeds bound"
        );
        let mut bytes = Vec::new();
        entry
            .by_ref()
            .take(4097)
            .read_to_end(&mut bytes)
            .context("read APK product-root asset")?;
        ensure!(
            bytes.len() <= 4096,
            "APK product-root asset expands past bound"
        );
        found.push(parse_ed25519_public_pem(&bytes)?);
    }
    ensure!(
        found.len() == 1,
        "APK product-root extraction count differs"
    );
    ensure!(
        found[0] == expected,
        "APK embedded product root differs from source-pinned root"
    );
    Ok(())
}

fn apk_stamp(path: &Path) -> Result<ApkStamp> {
    let metadata =
        fs::symlink_metadata(path).with_context(|| format!("stat exact APK {}", path.display()))?;
    ensure!(
        metadata.file_type().is_file(),
        "APK must be a regular non-symlink file"
    );
    ensure!(
        metadata.len() > 0 && metadata.len() <= 128 * 1024 * 1024,
        "APK size is outside the supported bound"
    );
    #[cfg(unix)]
    ensure!(
        metadata.nlink() == 1,
        "APK hard-link authority is ambiguous"
    );
    Ok(ApkStamp {
        size: metadata.len(),
        modified: metadata.modified().context("read APK modification time")?,
        #[cfg(unix)]
        device: metadata.dev(),
        #[cfg(unix)]
        inode: metadata.ino(),
        #[cfg(unix)]
        ctime: metadata.ctime(),
        #[cfg(unix)]
        ctime_nsec: metadata.ctime_nsec(),
    })
}

pub fn verify_apk_evidence(observed: &ApkEvidence, declared: &ApkEvidence) -> Result<()> {
    ensure!(
        observed == declared,
        "APK evidence differs from identity extracted from exact signed APK bytes"
    );
    Ok(())
}

fn quoted_field(line: &str, name: &str) -> Result<String> {
    let prefix = format!("{name}='");
    let start = line
        .find(&prefix)
        .with_context(|| format!("aapt2 package row has no {name}"))?
        + prefix.len();
    let tail = &line[start..];
    let end = tail
        .find('\'')
        .with_context(|| format!("aapt2 {name} value is unterminated"))?;
    ensure!(end > 0, "aapt2 {name} value is empty");
    Ok(tail[..end].to_owned())
}

fn bounded_output(command: &mut Command, name: &str) -> Result<String> {
    let mut child = command
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .with_context(|| format!("start {name}"))?;
    let stdout = child.stdout.take().context("capture tool stdout")?;
    let stderr = child.stderr.take().context("capture tool stderr")?;
    let stdout_thread = std::thread::spawn(move || bounded_read(stdout));
    let stderr_thread = std::thread::spawn(move || bounded_read(stderr));
    let status = child.wait().with_context(|| format!("wait for {name}"))?;
    let stdout = stdout_thread
        .join()
        .map_err(|_| anyhow::anyhow!("{name} stdout reader panicked"))??;
    let stderr = stderr_thread
        .join()
        .map_err(|_| anyhow::anyhow!("{name} stderr reader panicked"))??;
    ensure!(
        status.success(),
        "{name} failed: {}",
        String::from_utf8_lossy(&stderr)
    );
    String::from_utf8(stdout).with_context(|| format!("{name} output is not UTF-8"))
}

fn bounded_read<R: Read>(reader: R) -> Result<Vec<u8>> {
    let mut bytes = Vec::new();
    reader.take(MAX_TOOL_OUTPUT + 1).read_to_end(&mut bytes)?;
    ensure!(
        bytes.len() as u64 <= MAX_TOOL_OUTPUT,
        "Android tool output exceeds bound"
    );
    Ok(bytes)
}

pub fn parse_canonical_json<T>(bytes: &[u8]) -> Result<T>
where
    T: DeserializeOwned + Serialize,
{
    ensure!(
        bytes.ends_with(b"\n") && !bytes[..bytes.len() - 1].ends_with(b"\n"),
        "canonical JSON must end with exactly one LF"
    );
    let value = serde_json::from_slice::<UniqueValue>(bytes)
        .context("parse JSON and reject duplicate keys")?
        .0;
    let parsed: T = serde_json::from_value(value).context("decode strict JSON document")?;
    let mut canonical = serde_json::to_vec(&parsed)?;
    canonical.push(b'\n');
    ensure!(
        canonical == bytes,
        "JSON is not in canonical compact field order"
    );
    Ok(parsed)
}

pub fn validate_manifest(manifest: &Manifest) -> Result<()> {
    ensure!(
        manifest.schema_version == 1,
        "unsupported voboost.json schemaVersion {}",
        manifest.schema_version
    );
    canonical_semver(&manifest.release_identity)?;
    for (name, value) in [
        (
            "installerProtocol",
            manifest.compatibility.installer_protocol,
        ),
        (
            "operationProtocol",
            manifest.compatibility.operation_protocol,
        ),
        (
            "appDaemonProtocol",
            manifest.compatibility.app_daemon_protocol,
        ),
        (
            "agentCatalogSchema",
            manifest.compatibility.agent_catalog_schema,
        ),
        ("agentEntryAbi", manifest.compatibility.agent_entry_abi),
        ("implantAbi", manifest.compatibility.implant_abi),
    ] {
        ensure!(value > 0, "compatibility.{name} must be positive");
    }
    ensure!(
        manifest.compatibility.agent_catalog_schema == 2,
        "compatibility.agentCatalogSchema must be 2"
    );
    ensure!(
        !manifest.artifacts.is_empty(),
        "artifacts must not be empty"
    );

    let mut files = BTreeSet::new();
    let mut roles: BTreeMap<ArtifactRole, usize> = BTreeMap::new();
    let mut previous_key = None;
    for artifact in &manifest.artifacts {
        validate_basename(&artifact.file)?;
        ensure!(
            files.insert(artifact.file.as_str()),
            "duplicate artifact filename {}",
            artifact.file
        );
        *roles.entry(artifact.role).or_default() += 1;
        ensure!(
            artifact.size > 0,
            "artifact {} has zero size",
            artifact.file
        );
        ensure!(
            artifact.size <= maximum_size(artifact.role),
            "artifact {} exceeds its role size limit",
            artifact.file
        );
        ensure!(
            is_sha256(&artifact.sha256),
            "artifact {} has invalid sha256",
            artifact.file
        );

        let expected = classify(&artifact.file)?;
        ensure!(
            (artifact.role, artifact.activation, artifact.abi) == expected,
            "artifact {} metadata does not match its canonical filename",
            artifact.file
        );
        let key = (role_rank(artifact.role), artifact.file.as_str());
        if let Some(previous) = previous_key {
            ensure!(
                previous < key,
                "artifacts are not in canonical role/name order"
            );
        }
        previous_key = Some(key);

        if artifact.role == ArtifactRole::ResourceApk {
            let resource = artifact
                .resource
                .as_ref()
                .ok_or_else(|| anyhow::anyhow!("resource APK requires descriptor"))?;
            let valid_package = |v: &str| {
                v.len() <= 255
                    && v.split('.').count() >= 2
                    && v.split('.').all(|s| {
                        !s.is_empty()
                            && s.as_bytes()[0].is_ascii_alphabetic()
                            && s.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_')
                    })
            };
            let identity_matches = if let Some((target, overlay)) =
                crate::manifest::english_resource_packages(&artifact.file)
            {
                resource.owner == "i18n-en"
                    && resource.locale == "en"
                    && resource.target_package == target
                    && resource.overlay_package == overlay
            } else {
                artifact.file.starts_with("resource-i18n-ru-")
                    && resource.owner == "i18n-ru"
                    && resource.locale == "ru"
            };
            if !identity_matches
                || !valid_package(&resource.target_package)
                || !valid_package(&resource.overlay_package)
                || !resource
                    .overlay_package
                    .starts_with("ru.voboost.translation.")
                || resource.target_package == resource.overlay_package
                || resource.compatibility_schema != 1
                || artifact.package_name.as_deref() != Some(resource.overlay_package.as_str())
                || !artifact
                    .version_code
                    .is_some_and(|v| (1..=MAX_APK_VERSION_CODE).contains(&v))
                || !artifact
                    .certificate_sha256
                    .as_deref()
                    .is_some_and(is_sha256)
            {
                bail!("resource APK identity rejected");
            }
            let ledger = &resource.coverage_ledger;
            validate_basename(&ledger.file)?;
            if artifact.file.strip_suffix(".apk") != ledger.file.strip_suffix(".json")
                || ledger.size == 0
                || ledger.size > 1024 * 1024
                || !is_sha256(&ledger.sha256)
                || !manifest.artifacts.iter().any(|a| {
                    a.file == ledger.file
                        && a.role == ArtifactRole::ResourceLedger
                        && a.size == ledger.size
                        && a.sha256 == ledger.sha256
                })
            {
                bail!("resource ledger absent or identity differs");
            }
        } else if artifact.resource.is_some() {
            bail!("resource descriptor on foreign artifact role");
        }
        let has_apk_metadata = artifact.package_name.is_some()
            || artifact.version_code.is_some()
            || artifact.certificate_sha256.is_some();
        if artifact.role == ArtifactRole::App {
            ensure!(
                has_apk_metadata,
                "app artifact requires complete APK identity metadata"
            );
            ensure!(
                artifact.package_name.as_deref() == Some("ru.voboost"),
                "app packageName must be ru.voboost"
            );
            ensure!(
                artifact
                    .version_code
                    .is_some_and(|value| (1..=MAX_APK_VERSION_CODE).contains(&value)),
                "app versionCode must be in 1..={MAX_APK_VERSION_CODE}"
            );
            ensure!(
                artifact
                    .certificate_sha256
                    .as_deref()
                    .is_some_and(is_sha256),
                "app certificateSha256 must be lowercase SHA-256"
            );
        } else if artifact.role != ArtifactRole::ResourceApk {
            ensure!(
                !has_apk_metadata,
                "APK metadata is only allowed on role app"
            );
        }
    }

    let mut overlay_packages = BTreeSet::new();
    for resource in manifest
        .artifacts
        .iter()
        .filter_map(|a| a.resource.as_ref())
    {
        if !overlay_packages.insert(resource.overlay_package.as_str()) {
            bail!("duplicate resource overlay package");
        }
    }
    for ledger in manifest
        .artifacts
        .iter()
        .filter(|a| a.role == ArtifactRole::ResourceLedger)
    {
        if manifest
            .artifacts
            .iter()
            .filter_map(|a| a.resource.as_ref())
            .filter(|r| r.coverage_ledger.file == ledger.file)
            .count()
            != 1
        {
            bail!("resource ledger must have exactly one resource APK owner");
        }
    }
    for artifact in manifest.artifacts.iter().filter(|a| {
        matches!(
            a.role,
            ArtifactRole::QnxResourcePack | ArtifactRole::QnxResourceLedger
        )
    }) {
        let (suffix, other_suffix, role) = if artifact.role == ArtifactRole::QnxResourcePack {
            (".kzb", ".json", ArtifactRole::QnxResourceLedger)
        } else {
            (".json", ".kzb", ArtifactRole::QnxResourcePack)
        };
        let peer = format!(
            "{}{other_suffix}",
            artifact
                .file
                .strip_suffix(suffix)
                .context("QNX resource suffix")?
        );
        ensure!(
            manifest
                .artifacts
                .iter()
                .any(|a| a.file == peer && a.role == role),
            "QNX resource requires exact same-stem pack and ledger"
        );
    }
    for (role, count) in roles {
        if !matches!(
            role,
            ArtifactRole::Implant
                | ArtifactRole::Lsplant
                | ArtifactRole::AgentModule
                | ArtifactRole::QnxAgentModule
                | ArtifactRole::ResourceApk
                | ArtifactRole::ResourceLedger
                | ArtifactRole::QnxResourcePack
                | ArtifactRole::QnxResourceLedger
        ) {
            ensure!(count == 1, "singleton role {role:?} appears {count} times");
        }
    }
    for required in required_inventory() {
        ensure!(
            files.contains(required),
            "required artifact {required} is missing"
        );
    }
    ensure!(
        manifest
            .artifacts
            .iter()
            .any(|artifact| artifact.role == ArtifactRole::AgentModule),
        "at least one agent module DEX is required"
    );
    Ok(())
}

pub fn verify_closure(manifest: &Manifest, directory: &Path) -> Result<()> {
    validate_manifest(manifest)?;
    let expected: BTreeSet<String> = manifest
        .artifacts
        .iter()
        .map(|artifact| artifact.file.clone())
        .collect();
    verify_directory_inventory(directory, &expected)?;
    verify_artifact_identities(manifest, directory)
}

pub fn verify_product_closure(directory: &Path, public_key: &Path) -> Result<Manifest> {
    let manifest_path = directory.join("voboost.json");
    let signature_path = directory.join("voboost.json.sig");
    let manifest = read_manifest(&manifest_path)?;
    let expected: BTreeSet<String> = manifest
        .artifacts
        .iter()
        .map(|artifact| artifact.file.clone())
        .chain(["voboost.json".to_owned(), "voboost.json.sig".to_owned()])
        .collect();
    verify_directory_inventory(directory, &expected)?;
    crate::publish::verify_file(public_key, &manifest_path, &signature_path)
        .context("verify product manifest signature")?;
    verify_artifact_identities(&manifest, directory)?;
    if manifest
        .artifacts
        .iter()
        .any(|a| a.role == ArtifactRole::QnxResourcePack)
    {
        let raw_key: [u8; 32] = fs::read(public_key)?
            .try_into()
            .map_err(|_| anyhow::anyhow!("product key must be raw32"))?;
        let manifest_bytes = fs::read(&manifest_path)?;
        let signature = fs::read(&signature_path)?;
        let catalog = fs::read(directory.join("agents.json"))?;
        for artifact in manifest
            .artifacts
            .iter()
            .filter(|a| a.role == ArtifactRole::QnxResourcePack)
        {
            let ledger_file = artifact
                .file
                .strip_suffix(".kzb")
                .context("QNX pack suffix")?
                .to_owned()
                + ".json";
            voboost_contract_core::resource_pack::OwnedPack::verify(
                voboost_contract_core::resource_pack::Inputs {
                    manifest: &manifest_bytes,
                    signature: &signature,
                    public_key: &raw_key,
                    catalog: &catalog,
                    artifact_file: &artifact.file,
                    pack: &fs::read(directory.join(&artifact.file))?,
                    ledger: &fs::read(directory.join(ledger_file))?,
                },
            )
            .map_err(anyhow::Error::msg)?;
        }
    }
    Ok(manifest)
}

fn verify_directory_inventory(directory: &Path, expected: &BTreeSet<String>) -> Result<()> {
    let mut actual = BTreeSet::new();
    for entry in
        fs::read_dir(directory).with_context(|| format!("read closure {}", directory.display()))?
    {
        let entry = entry?;
        let file_type = entry.file_type()?;
        ensure!(
            file_type.is_file() && !file_type.is_symlink(),
            "closure contains a non-regular file: {}",
            entry.path().display()
        );
        let name = entry
            .file_name()
            .into_string()
            .map_err(|_| anyhow::anyhow!("closure filename is not UTF-8"))?;
        actual.insert(name);
    }
    ensure!(
        actual == *expected,
        "closure inventory differs from voboost.json: expected {expected:?}, found {actual:?}"
    );
    Ok(())
}

fn verify_artifact_identities(manifest: &Manifest, directory: &Path) -> Result<()> {
    for artifact in &manifest.artifacts {
        let (size, sha256) = identity(&artifact_path(directory, artifact))?;
        ensure!(
            size == artifact.size,
            "artifact {} size mismatch",
            artifact.file
        );
        ensure!(
            sha256 == artifact.sha256,
            "artifact {} SHA-256 mismatch",
            artifact.file
        );
    }
    Ok(())
}

pub fn validate_channel(channel: &Channel) -> Result<()> {
    ensure!(
        channel.schema_version == 1,
        "unsupported channel.json schemaVersion {}",
        channel.schema_version
    );
    let version = canonical_semver(&channel.release_identity)?;
    match channel.channel.as_str() {
        "stable" => ensure!(
            version.pre.is_empty(),
            "stable channel cannot point at a prerelease"
        ),
        "beta" => ensure!(
            !version.pre.is_empty() && version.pre.as_str().starts_with("beta"),
            "beta channel must point at a beta prerelease"
        ),
        value => bail!("unsupported channel {value:?}"),
    }
    ensure!(
        channel.manifest_size > 0 && channel.manifest_size <= 4 * 1024 * 1024,
        "manifestSize is outside the supported bound"
    );
    ensure!(
        is_sha256(&channel.manifest_sha256),
        "manifestSha256 must be 64 lowercase hexadecimal characters"
    );
    validate_https_url(&channel.manifest_url)?;
    Ok(())
}

pub fn canonical_channel_bytes(channel: &Channel) -> Result<Vec<u8>> {
    validate_channel(channel)?;
    let mut bytes = serde_json::to_vec(channel)?;
    bytes.push(b'\n');
    Ok(bytes)
}

fn validate_https_url(value: &str) -> Result<()> {
    ensure!(value.len() <= 2048, "manifestUrl is too long");
    let uri = Uri::parse(value).context("manifestUrl must be an RFC 3986 URI")?;
    ensure!(
        uri.scheme().as_str() == "https",
        "manifestUrl must use HTTPS"
    );
    let authority = uri
        .authority()
        .context("manifestUrl must have an authority")?;
    ensure!(
        !authority.host().is_empty()
            && authority.userinfo().is_none()
            && authority.port().is_none(),
        "manifestUrl authority is invalid"
    );
    ensure!(
        uri.path().as_str().starts_with('/') && uri.fragment().is_none(),
        "manifestUrl must have an absolute path and no fragment"
    );
    Ok(())
}

fn validate_basename(value: &str) -> Result<()> {
    ensure!(
        !value.is_empty() && value.len() <= 255 && value != "." && value != "..",
        "artifact file must be a non-special basename"
    );
    ensure!(
        !value.contains('/') && !value.contains('\\') && !value.contains('\0'),
        "artifact file must be a basename"
    );
    Ok(())
}

fn maximum_size(role: ArtifactRole) -> u64 {
    match role {
        ArtifactRole::App => 128 * 1024 * 1024,
        ArtifactRole::ResourceApk => 16 * 1024 * 1024,
        ArtifactRole::ResourceLedger | ArtifactRole::QnxResourceLedger => 1024 * 1024,
        ArtifactRole::QnxResourcePack => 16 * 1024 * 1024,
        ArtifactRole::AgentCatalog | ArtifactRole::RestoreAuthorization => 4 * 1024 * 1024,
        ArtifactRole::AgentCatalogSignature | ArtifactRole::RestoreSignature => 64,
        ArtifactRole::AgentCommon | ArtifactRole::AgentModule => 64 * 1024 * 1024,
        _ => 64 * 1024 * 1024,
    }
}

fn is_sha256(value: &str) -> bool {
    value.len() == 64
        && value
            .bytes()
            .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
}

fn canonical_semver(value: &str) -> Result<Version> {
    let version = Version::parse(value).context("releaseIdentity must be strict SemVer")?;
    ensure!(
        version.to_string() == value,
        "releaseIdentity must use canonical SemVer spelling"
    );
    Ok(version)
}

struct UniqueValue(Value);

impl<'de> Deserialize<'de> for UniqueValue {
    fn deserialize<D>(deserializer: D) -> std::result::Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        deserializer.deserialize_any(UniqueVisitor).map(Self)
    }
}

struct UniqueVisitor;

impl<'de> Visitor<'de> for UniqueVisitor {
    type Value = Value;

    fn expecting(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str("a JSON value without duplicate object keys")
    }

    fn visit_bool<E>(self, value: bool) -> std::result::Result<Self::Value, E> {
        Ok(Value::Bool(value))
    }
    fn visit_i64<E>(self, value: i64) -> std::result::Result<Self::Value, E> {
        Ok(Value::Number(value.into()))
    }
    fn visit_u64<E>(self, value: u64) -> std::result::Result<Self::Value, E> {
        Ok(Value::Number(value.into()))
    }
    fn visit_f64<E>(self, value: f64) -> std::result::Result<Self::Value, E>
    where
        E: serde::de::Error,
    {
        Number::from_f64(value)
            .map(Value::Number)
            .ok_or_else(|| E::custom("non-finite JSON number"))
    }
    fn visit_str<E>(self, value: &str) -> std::result::Result<Self::Value, E>
    where
        E: serde::de::Error,
    {
        Ok(Value::String(value.to_owned()))
    }
    fn visit_string<E>(self, value: String) -> std::result::Result<Self::Value, E> {
        Ok(Value::String(value))
    }
    fn visit_none<E>(self) -> std::result::Result<Self::Value, E> {
        Ok(Value::Null)
    }
    fn visit_unit<E>(self) -> std::result::Result<Self::Value, E> {
        Ok(Value::Null)
    }

    fn visit_seq<A>(self, mut sequence: A) -> std::result::Result<Self::Value, A::Error>
    where
        A: SeqAccess<'de>,
    {
        let mut values = Vec::new();
        while let Some(value) = sequence.next_element::<UniqueValue>()? {
            values.push(value.0);
        }
        Ok(Value::Array(values))
    }

    fn visit_map<A>(self, mut object: A) -> std::result::Result<Self::Value, A::Error>
    where
        A: MapAccess<'de>,
    {
        let mut keys = HashSet::new();
        let mut values = Map::new();
        while let Some(key) = object.next_key::<String>()? {
            if !keys.insert(key.clone()) {
                return Err(serde::de::Error::custom(format!(
                    "duplicate JSON key {key:?}"
                )));
            }
            let value = object.next_value::<UniqueValue>()?;
            values.insert(key, value.0);
        }
        Ok(Value::Object(values))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::manifest::{Artifact, RestoreDocument, classify, role_rank};
    use ed25519_dalek::{Signer, SigningKey};
    use std::io::Write;
    use std::path::PathBuf;
    #[cfg(feature = "android-integration")]
    use std::process::Command;

    #[cfg(feature = "android-integration")]
    fn run(command: &mut Command) {
        let output = command.output().unwrap();
        assert!(
            output.status.success(),
            "command failed: {}",
            String::from_utf8_lossy(&output.stderr)
        );
    }

    #[test]
    fn rejects_duplicate_keys_before_typed_parsing() {
        let source = b"{\"schemaVersion\":1,\"schemaVersion\":1}\n";
        assert!(
            parse_canonical_json::<Channel>(source)
                .unwrap_err()
                .to_string()
                .contains("duplicate")
        );
    }

    fn write_beta1_sized_product_closure(directory: &Path) -> (Manifest, PathBuf) {
        let fixture =
            Path::new(env!("CARGO_MANIFEST_DIR")).join("fixtures/voboost/valid/minimal.json");
        let mut manifest = read_manifest(&fixture).unwrap();
        for index in 0..50 {
            let file = format!("agent-extra-{index:02}.dex");
            let (role, activation, abi) = classify(&file).unwrap();
            manifest.artifacts.push(Artifact {
                role,
                file,
                size: 1,
                sha256: "0".repeat(64),
                activation,
                package_name: None,
                version_code: None,
                certificate_sha256: None,
                abi,
                resource: None,
            });
        }
        manifest
            .artifacts
            .sort_by_key(|artifact| (role_rank(artifact.role), artifact.file.clone()));
        assert_eq!(manifest.artifacts.len(), required_inventory().len() + 51);
        for (index, artifact) in manifest.artifacts.iter_mut().enumerate() {
            let length = if artifact.file.ends_with(".sig") {
                64
            } else {
                index + 1
            };
            fs::write(
                directory.join(&artifact.file),
                vec![(index + 1) as u8; length],
            )
            .unwrap();
            let (size, sha256) = identity(&directory.join(&artifact.file)).unwrap();
            artifact.size = size;
            artifact.sha256 = sha256;
        }
        validate_manifest(&manifest).unwrap();
        let mut bytes = serde_json::to_vec(&manifest).unwrap();
        bytes.push(b'\n');
        fs::write(directory.join("voboost.json"), &bytes).unwrap();
        let signing = SigningKey::from_bytes(&[73_u8; 32]);
        fs::write(
            directory.join("voboost.json.sig"),
            signing.sign(&bytes).to_bytes(),
        )
        .unwrap();
        let public = directory.parent().unwrap().join("product.pub");
        fs::write(&public, signing.verifying_key().to_bytes()).unwrap();
        (manifest, public)
    }

    #[test]
    fn product_closure_distinguishes_artifacts_from_the_signed_file_set() {
        let root = tempfile::tempdir().unwrap();
        let closure = root.path().join("closure");
        fs::create_dir(&closure).unwrap();
        let (manifest, public) = write_beta1_sized_product_closure(&closure);
        assert_eq!(
            fs::read_dir(&closure).unwrap().count(),
            manifest.artifacts.len() + 2
        );
        verify_product_closure(&closure, &public).unwrap();
        assert!(verify_closure(&manifest, &closure).is_err());

        let manifest_bytes = fs::read(closure.join("voboost.json")).unwrap();
        let signature_bytes = fs::read(closure.join("voboost.json.sig")).unwrap();
        fs::remove_file(closure.join("voboost.json")).unwrap();
        fs::remove_file(closure.join("voboost.json.sig")).unwrap();
        assert_eq!(
            fs::read_dir(&closure).unwrap().count(),
            manifest.artifacts.len()
        );
        verify_closure(&manifest, &closure).unwrap();
        assert!(verify_product_closure(&closure, &public).is_err());
        fs::write(closure.join("voboost.json"), manifest_bytes).unwrap();
        fs::write(closure.join("voboost.json.sig"), signature_bytes).unwrap();

        fs::write(closure.join("unexpected"), b"extra").unwrap();
        assert!(verify_product_closure(&closure, &public).is_err());
        fs::remove_file(closure.join("unexpected")).unwrap();

        let missing = closure.join(&manifest.artifacts[0].file);
        let missing_bytes = fs::read(&missing).unwrap();
        fs::remove_file(&missing).unwrap();
        assert!(verify_product_closure(&closure, &public).is_err());
        fs::write(&missing, missing_bytes).unwrap();

        #[cfg(unix)]
        {
            use std::os::unix::fs::symlink;
            fs::remove_file(&missing).unwrap();
            symlink(closure.join(&manifest.artifacts[1].file), &missing).unwrap();
            assert!(verify_product_closure(&closure, &public).is_err());
        }
    }

    fn write_apk_zip(path: &Path, entries: &[(&str, &[u8])]) {
        let file = fs::File::create(path).unwrap();
        let mut archive = zip::ZipWriter::new(file);
        let options = zip::write::SimpleFileOptions::default()
            .compression_method(zip::CompressionMethod::Deflated);
        for (name, bytes) in entries {
            archive.start_file(*name, options).unwrap();
            archive.write_all(bytes).unwrap();
        }
        archive.finish().unwrap();
    }

    #[test]
    fn apk_product_root_rejects_missing_duplicate_malformed_and_substituted_assets() {
        let root = tempfile::tempdir().unwrap();
        let expected = SigningKey::from_bytes(&[21_u8; 32])
            .verifying_key()
            .to_bytes();
        let foreign = SigningKey::from_bytes(&[22_u8; 32])
            .verifying_key()
            .to_bytes();
        let expected_path = root.path().join("product.pub");
        fs::write(&expected_path, expected).unwrap();
        let valid_pem = canonical_ed25519_public_pem(&expected);
        let foreign_pem = canonical_ed25519_public_pem(&foreign);

        let valid = root.path().join("valid.apk");
        write_apk_zip(&valid, &[(APK_PRODUCT_ROOT_ASSET, valid_pem.as_bytes())]);
        verify_apk_embedded_product_root(&valid, &expected_path).unwrap();

        let missing = root.path().join("missing.apk");
        write_apk_zip(
            &missing,
            &[("assets/config/other.pem", valid_pem.as_bytes())],
        );
        assert!(verify_apk_embedded_product_root(&missing, &expected_path).is_err());

        let duplicate = root.path().join("duplicate.apk");
        let alternate = "assets/config/release-public.peX";
        write_apk_zip(
            &duplicate,
            &[
                (APK_PRODUCT_ROOT_ASSET, valid_pem.as_bytes()),
                (alternate, valid_pem.as_bytes()),
            ],
        );
        let mut duplicate_bytes = fs::read(&duplicate).unwrap();
        let mut replacements = 0;
        for offset in 0..=duplicate_bytes.len() - alternate.len() {
            if &duplicate_bytes[offset..offset + alternate.len()] == alternate.as_bytes() {
                duplicate_bytes[offset..offset + alternate.len()]
                    .copy_from_slice(APK_PRODUCT_ROOT_ASSET.as_bytes());
                replacements += 1;
            }
        }
        assert_eq!(replacements, 2);
        fs::write(&duplicate, duplicate_bytes).unwrap();
        assert!(verify_apk_embedded_product_root(&duplicate, &expected_path).is_err());

        let malformed = root.path().join("malformed.apk");
        write_apk_zip(&malformed, &[(APK_PRODUCT_ROOT_ASSET, b"not a PEM\n")]);
        assert!(verify_apk_embedded_product_root(&malformed, &expected_path).is_err());

        let substituted = root.path().join("substituted.apk");
        write_apk_zip(
            &substituted,
            &[(APK_PRODUCT_ROOT_ASSET, foreign_pem.as_bytes())],
        );
        assert!(verify_apk_embedded_product_root(&substituted, &expected_path).is_err());
    }

    #[cfg(feature = "android-integration")]
    #[test]
    fn pinned_android_tools_extract_identity_from_exact_signed_apk() {
        let sdk = PathBuf::from(std::env::var("ANDROID_SDK_ROOT").expect("ANDROID_SDK_ROOT"));
        let root = tempfile::tempdir().unwrap();
        let manifest = root.path().join("AndroidManifest.xml");
        let unsigned = root.path().join("unsigned.apk");
        let signed = root.path().join("voboost-app.apk");
        let keystore = root.path().join("test.jks");
        let assets = root.path().join("assets");
        let expected_product_root = SigningKey::from_bytes(&[31_u8; 32])
            .verifying_key()
            .to_bytes();
        let foreign_product_root = SigningKey::from_bytes(&[32_u8; 32])
            .verifying_key()
            .to_bytes();
        let expected_product_root_path = root.path().join("product.pub");
        fs::create_dir_all(assets.join("config")).unwrap();
        fs::write(
            assets.join("config/release-public.pem"),
            canonical_ed25519_public_pem(&expected_product_root),
        )
        .unwrap();
        fs::write(&expected_product_root_path, expected_product_root).unwrap();
        fs::write(
            &manifest,
            r#"<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="ru.voboost" android:versionCode="1000007" android:versionName="1000007"><uses-sdk android:minSdkVersion="28" android:targetSdkVersion="28"/><application android:label="Voboost"/></manifest>"#,
        )
        .unwrap();
        let tools = sdk.join("build-tools").join(ANDROID_BUILD_TOOLS);
        run(Command::new(tools.join("aapt2"))
            .arg("link")
            .arg("-I")
            .arg(sdk.join("platforms/android-34/android.jar"))
            .arg("--manifest")
            .arg(&manifest)
            .arg("-A")
            .arg(&assets)
            .arg("-o")
            .arg(&unsigned));
        run(Command::new("keytool")
            .arg("-genkeypair")
            .arg("-keystore")
            .arg(&keystore)
            .arg("-storepass")
            .arg("changeit")
            .arg("-keypass")
            .arg("changeit")
            .arg("-alias")
            .arg("test")
            .arg("-keyalg")
            .arg("RSA")
            .arg("-keysize")
            .arg("2048")
            .arg("-validity")
            .arg("1")
            .arg("-dname")
            .arg("CN=Voboost Test,O=Voboost,C=RU")
            .arg("-noprompt"));
        run(Command::new(tools.join("apksigner"))
            .arg("sign")
            .arg("--ks")
            .arg(&keystore)
            .arg("--ks-pass")
            .arg("pass:changeit")
            .arg("--key-pass")
            .arg("pass:changeit")
            .arg("--v4-signing-enabled")
            .arg("false")
            .arg("--out")
            .arg(&signed)
            .arg(&unsigned));

        let observed = inspect_apk(&signed, &sdk, &"1".repeat(40)).unwrap();
        assert_eq!(observed.package_name, "ru.voboost");
        assert_eq!(observed.version_code, 1_000_007);
        assert!(is_sha256(&observed.certificate_sha256));
        verify_apk_embedded_product_root(&signed, &expected_product_root_path).unwrap();

        let mut substituted = observed.clone();
        substituted.version_code += 1;
        assert!(verify_apk_evidence(&observed, &substituted).is_err());
        substituted = observed.clone();
        substituted.certificate_sha256 = "0".repeat(64);
        assert!(verify_apk_evidence(&observed, &substituted).is_err());

        let foreign_pem = canonical_ed25519_public_pem(&foreign_product_root);
        for (name, asset) in [
            ("missing", None),
            ("malformed", Some(b"not a canonical PEM\n".as_slice())),
            ("substituted", Some(foreign_pem.as_bytes())),
        ] {
            let hostile_assets = root.path().join(format!("assets-{name}"));
            fs::create_dir_all(hostile_assets.join("config")).unwrap();
            if let Some(bytes) = asset {
                fs::write(hostile_assets.join("config/release-public.pem"), bytes).unwrap();
            }
            let hostile_unsigned = root.path().join(format!("unsigned-{name}.apk"));
            let hostile_signed = root.path().join(format!("signed-{name}.apk"));
            run(Command::new(tools.join("aapt2"))
                .arg("link")
                .arg("-I")
                .arg(sdk.join("platforms/android-34/android.jar"))
                .arg("--manifest")
                .arg(&manifest)
                .arg("-A")
                .arg(&hostile_assets)
                .arg("-o")
                .arg(&hostile_unsigned));
            run(Command::new(tools.join("apksigner"))
                .arg("sign")
                .arg("--ks")
                .arg(&keystore)
                .arg("--ks-pass")
                .arg("pass:changeit")
                .arg("--key-pass")
                .arg("pass:changeit")
                .arg("--v4-signing-enabled")
                .arg("false")
                .arg("--out")
                .arg(&hostile_signed)
                .arg(&hostile_unsigned));
            let hostile_identity = inspect_apk(&hostile_signed, &sdk, &"1".repeat(40)).unwrap();
            assert_eq!(
                hostile_identity.certificate_sha256,
                observed.certificate_sha256
            );
            assert!(
                verify_apk_embedded_product_root(&hostile_signed, &expected_product_root_path)
                    .is_err()
            );
        }
    }

    #[test]
    fn voboost_fixtures_match_expected_result() {
        let root = Path::new(env!("CARGO_MANIFEST_DIR")).join("fixtures");
        for (kind, accepted) in [("valid", true), ("invalid", false)] {
            for entry in fs::read_dir(root.join("voboost").join(kind)).unwrap() {
                let path = entry.unwrap().path();
                let result = read_manifest(&path);
                assert_eq!(result.is_ok(), accepted, "{}: {result:?}", path.display());
            }
        }
    }

    #[test]
    fn version_code_boundary_fixtures_match_schema_and_parser() {
        let root = Path::new(env!("CARGO_MANIFEST_DIR"));
        let schema: Value =
            serde_json::from_slice(&fs::read(root.join("voboost.schema.json")).unwrap()).unwrap();
        let validator = jsonschema::draft202012::options()
            .should_validate_formats(true)
            .build(&schema)
            .unwrap();
        for (path, accepted) in [
            ("fixtures/voboost/valid/version-code-max.json", true),
            ("fixtures/voboost/invalid/version-code-zero.json", false),
            ("fixtures/voboost/invalid/version-code-over-max.json", false),
        ] {
            let path = root.join(path);
            let bytes = fs::read(&path).unwrap();
            let instance: Value = serde_json::from_slice(&bytes).unwrap();
            assert_eq!(
                validator.is_valid(&instance),
                accepted,
                "schema: {}",
                path.display()
            );
            assert_eq!(
                read_manifest(&path).is_ok(),
                accepted,
                "parser: {}",
                path.display()
            );
        }
    }

    #[test]
    fn channel_fixtures_match_expected_result() {
        let root = Path::new(env!("CARGO_MANIFEST_DIR")).join("fixtures");
        for (kind, accepted) in [("valid", true), ("invalid", false)] {
            for entry in fs::read_dir(root.join("channel").join(kind)).unwrap() {
                let path = entry.unwrap().path();
                let result = read_channel(&path);
                assert_eq!(result.is_ok(), accepted, "{}: {result:?}", path.display());
            }
        }
    }

    #[test]
    fn checked_in_schemas_are_valid_json_and_name_the_wire_roots() {
        let root = Path::new(env!("CARGO_MANIFEST_DIR"));
        for (file, title) in [
            ("voboost.schema.json", "Voboost product manifest"),
            ("channel.schema.json", "Voboost OTA channel pointer"),
            ("restore.schema.json", "Voboost restore authorization"),
            (
                "local-delivery-evidence.schema.json",
                "Voboost local delivery evidence",
            ),
            (
                "ci-vehicle-evidence.schema.json",
                "Voboost CI vehicle evidence",
            ),
            ("trust-roots.schema.json", "Voboost release trust roots"),
        ] {
            let value: Value = serde_json::from_slice(&fs::read(root.join(file)).unwrap()).unwrap();
            assert_eq!(
                value["$schema"],
                "https://json-schema.org/draft/2020-12/schema"
            );
            assert_eq!(value["title"], title);
            assert_eq!(value["additionalProperties"], false);
        }
    }

    #[test]
    fn qnx_closure_contract_rejects_missing_and_misclassified_artifacts() {
        let root = Path::new(env!("CARGO_MANIFEST_DIR"));
        let manifest = read_manifest(&root.join("fixtures/voboost/valid/minimal.json")).unwrap();
        let schema: Value =
            serde_json::from_slice(&fs::read(root.join("voboost.schema.json")).unwrap()).unwrap();
        let validator = jsonschema::draft202012::options().build(&schema).unwrap();
        let valid = serde_json::to_value(&manifest).unwrap();
        assert!(validator.is_valid(&valid));
        let mut multiple = manifest.clone();
        let mut extra = multiple
            .artifacts
            .iter()
            .find(|row| row.role == ArtifactRole::QnxAgentModule)
            .unwrap()
            .clone();
        extra.file = "libagent-zulu-status.so".into();
        multiple.artifacts.push(extra);
        assert!(validate_manifest(&multiple).is_ok());
        assert!(validator.is_valid(&serde_json::to_value(multiple).unwrap()));
        for file in [
            "libagent-.so",
            "libagent-Zulu.so",
            "libagent-zulu--status.so",
            "libagent-../status.so",
        ] {
            assert!(classify(file).is_err());
        }
        let native_files = [
            "voboost-cluster-daemon",
            "voboost-cluster-startup.sh",
            "libvoboost-cluster-inject.so",
            "libvoboost-runtime-cluster.so",
            "libagent-font-cyrillic.so",
        ];
        for file in native_files {
            let index = manifest
                .artifacts
                .iter()
                .position(|row| row.file == file)
                .unwrap();
            let mut missing = manifest.clone();
            missing.artifacts.remove(index);
            assert!(validate_manifest(&missing).is_err(), "missing {file}");
            assert!(!validator.is_valid(&serde_json::to_value(missing).unwrap()));
            for field in ["role", "activation", "abi"] {
                let mut invalid = valid.clone();
                invalid["artifacts"][index][field] = match field {
                    "role" => Value::String("daemon".into()),
                    "activation" => Value::String("package-installer".into()),
                    _ => Value::String("arm64-v8a".into()),
                };
                let parsed: Manifest = serde_json::from_value(invalid.clone()).unwrap();
                assert!(validate_manifest(&parsed).is_err(), "{file}: {field}");
                assert!(!validator.is_valid(&invalid), "schema {file}: {field}");
            }
        }
        let mut old_catalog = manifest;
        old_catalog.compatibility.agent_catalog_schema = 1;
        assert!(validate_manifest(&old_catalog).is_err());
        assert!(!validator.is_valid(&serde_json::to_value(old_catalog).unwrap()));
        assert!(classify("voboost-cluster").is_err());
        assert!(classify("voboost-cluster-install").is_err());
        assert!(classify("voboost-cluster-restore").is_err());
        assert!(classify("voboost-cluster-uninstall").is_err());
    }

    #[test]
    fn draft_2020_12_schemas_match_the_shared_fixture_corpus() {
        let root = Path::new(env!("CARGO_MANIFEST_DIR"));
        for (schema_file, fixture_root, read) in [(
            "voboost.schema.json",
            "fixtures/voboost",
            read_manifest as fn(&Path) -> Result<Manifest>,
        )] {
            let schema: Value =
                serde_json::from_slice(&fs::read(root.join(schema_file)).unwrap()).unwrap();
            let validator = jsonschema::draft202012::options()
                .should_validate_formats(true)
                .build(&schema)
                .unwrap();
            for (kind, accepted) in [("valid", true), ("invalid", false)] {
                for entry in fs::read_dir(root.join(fixture_root).join(kind)).unwrap() {
                    let path = entry.unwrap().path();
                    let instance: Value =
                        serde_json::from_slice(&fs::read(&path).unwrap()).unwrap();
                    let schema_result = validator.is_valid(&instance);
                    let parser_result = read(&path).is_ok();
                    assert_eq!(
                        schema_result,
                        accepted,
                        "schema {}: {}",
                        schema_file,
                        path.display()
                    );
                    assert_eq!(
                        parser_result,
                        accepted,
                        "parser {}: {}",
                        schema_file,
                        path.display()
                    );
                }
            }
        }

        let schema: Value =
            serde_json::from_slice(&fs::read(root.join("channel.schema.json")).unwrap()).unwrap();
        let validator = jsonschema::draft202012::options()
            .should_validate_formats(true)
            .build(&schema)
            .unwrap();
        for (kind, accepted) in [("valid", true), ("invalid", false)] {
            for entry in fs::read_dir(root.join("fixtures/channel").join(kind)).unwrap() {
                let path = entry.unwrap().path();
                let instance: Value = serde_json::from_slice(&fs::read(&path).unwrap()).unwrap();
                assert_eq!(
                    validator.is_valid(&instance),
                    accepted,
                    "schema channel: {}",
                    path.display()
                );
                assert_eq!(
                    read_channel(&path).is_ok(),
                    accepted,
                    "parser channel: {}",
                    path.display()
                );
            }
        }

        let schema: Value =
            serde_json::from_slice(&fs::read(root.join("restore.schema.json")).unwrap()).unwrap();
        let validator = jsonschema::draft202012::options()
            .should_validate_formats(true)
            .build(&schema)
            .unwrap();
        for (file, accepted) in [("valid.json", true), ("invalid-bad-digest.json", false)] {
            let path = root.join("fixtures/restore").join(file);
            let bytes = fs::read(&path).unwrap();
            let instance: Value = serde_json::from_slice(&bytes).unwrap();
            let parser = parse_canonical_json::<RestoreDocument>(&bytes)
                .and_then(|document| document.validate_shape());
            assert_eq!(
                validator.is_valid(&instance),
                accepted,
                "schema restore: {}",
                path.display()
            );
            assert_eq!(
                parser.is_ok(),
                accepted,
                "parser restore: {}",
                path.display()
            );
        }

        for (schema_file, fixture_file) in [
            (
                "local-delivery-evidence.schema.json",
                "fixtures/evidence/local-valid.json",
            ),
            (
                "ci-vehicle-evidence.schema.json",
                "fixtures/evidence/ci-vehicle-valid.json",
            ),
        ] {
            let schema: Value =
                serde_json::from_slice(&fs::read(root.join(schema_file)).unwrap()).unwrap();
            let validator = jsonschema::draft202012::options()
                .should_validate_formats(true)
                .build(&schema)
                .unwrap();
            let instance: Value =
                serde_json::from_slice(&fs::read(root.join(fixture_file)).unwrap()).unwrap();
            assert!(
                validator.is_valid(&instance),
                "{fixture_file} must satisfy {schema_file}"
            );
            let mut extra = instance.clone();
            extra
                .as_object_mut()
                .unwrap()
                .insert("unexpected".into(), Value::Bool(true));
            assert!(
                !validator.is_valid(&extra),
                "{schema_file} must reject an expanded evidence root"
            );
            let mut foreign_producer = instance.clone();
            foreign_producer["producer"]["repository"] = Value::String("attacker/fork".into());
            assert!(
                !validator.is_valid(&foreign_producer),
                "{schema_file} must reject a foreign producer"
            );
            let mut failed_vehicle = instance.clone();
            failed_vehicle["vehicle"]["status"] = Value::String("failed".into());
            assert!(
                !validator.is_valid(&failed_vehicle),
                "{schema_file} must reject a failed vehicle receipt"
            );
        }

        let schema: Value =
            serde_json::from_slice(&fs::read(root.join("trust-roots.schema.json")).unwrap())
                .unwrap();
        let validator = jsonschema::draft202012::options()
            .should_validate_formats(true)
            .build(&schema)
            .unwrap();
        let path = root.join("1.0.0-beta1-trust.json");
        let instance: Value = serde_json::from_slice(&fs::read(&path).unwrap()).unwrap();
        assert!(validator.is_valid(&instance));
        assert!(read_trust_roots(&path).is_ok());
    }

    #[test]
    fn trust_roots_are_source_pinned_complete_and_role_separated() {
        let path = Path::new(env!("CARGO_MANIFEST_DIR")).join("1.0.0-beta1-trust.json");
        let mut trust = read_trust_roots(&path).unwrap();
        // The source document may legitimately be provisioned for release.
        validate_trust_roots(&trust).unwrap();
        trust.state = "unprovisioned".into();
        trust.roots.android_certificate_sha256 = None;
        trust.roots.agent_catalog_public_key_hex = None;
        trust.roots.product_public_key_hex = None;
        trust.roots.native_executable_public_key_hex = None;
        trust.roots.restore_public_key_hex = None;
        trust.roots.evidence_public_key_hex = None;
        validate_trust_roots(&trust).unwrap();
        trust.roots.product_public_key_hex = Some("1".repeat(64));
        assert!(validate_trust_roots(&trust).is_err());
        trust.state = "provisioned".into();
        trust.roots.android_certificate_sha256 = Some("a".repeat(64));
        trust.roots.agent_catalog_public_key_hex = Some("0".repeat(64));
        trust.roots.product_public_key_hex = Some("1".repeat(64));
        trust.roots.native_executable_public_key_hex = Some("2".repeat(64));
        trust.roots.restore_public_key_hex = Some("3".repeat(64));
        trust.roots.evidence_public_key_hex = Some("4".repeat(64));
        validate_trust_roots(&trust).unwrap();
        trust.roots.evidence_public_key_hex = trust.roots.product_public_key_hex.clone();
        assert!(validate_trust_roots(&trust).is_err());
        trust.roots.evidence_public_key_hex = None;
        assert!(validate_trust_roots(&trust).is_err());
    }

    #[test]
    fn semantic_invariants_beyond_json_schema_are_enforced() {
        let path =
            Path::new(env!("CARGO_MANIFEST_DIR")).join("fixtures/voboost/valid/minimal.json");
        let manifest = read_manifest(&path).unwrap();

        let mut reordered = manifest.clone();
        reordered.artifacts.swap(0, 1);
        assert!(validate_manifest(&reordered).is_err());

        let mut duplicate = manifest;
        let mut module = duplicate.artifacts.last().unwrap().clone();
        module.sha256 = "f".repeat(64);
        duplicate.artifacts.push(module);
        assert!(validate_manifest(&duplicate).is_err());
    }

    #[test]
    fn channel_authoring_is_canonical() {
        let channel = Channel {
            schema_version: 1,
            channel: "beta".into(),
            release_identity: "1.0.0-beta1".into(),
            manifest_url: "https://releases.voboost.ru/1.0.0-beta1/voboost.json".into(),
            manifest_size: 42,
            manifest_sha256: "a".repeat(64),
        };
        let bytes = canonical_channel_bytes(&channel).unwrap();
        assert_eq!(parse_canonical_json::<Channel>(&bytes).unwrap(), channel);
    }
}
