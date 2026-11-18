use crate::definition::{Compatibility, Definition, Repository, is_full_commit};
use anyhow::{Context, Result, bail, ensure};
use ed25519_dalek::{Signature, Signer, SigningKey, VerifyingKey};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::collections::{BTreeMap, BTreeSet};
use std::fs::{self, File};
use std::io::{BufReader, Read};
use std::path::{Path, PathBuf};

pub const MAX_APK_VERSION_CODE: u64 = 2_100_000_000;

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Manifest {
    pub schema_version: u32,
    pub release_identity: String,
    pub compatibility: CompatibilityManifest,
    pub artifacts: Vec<Artifact>,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct CompatibilityManifest {
    pub installer_protocol: u32,
    pub operation_protocol: u32,
    pub app_daemon_protocol: u32,
    pub agent_catalog_schema: u32,
    pub agent_entry_abi: u32,
    pub implant_abi: u32,
}

impl From<&Compatibility> for CompatibilityManifest {
    fn from(value: &Compatibility) -> Self {
        Self {
            installer_protocol: value.installer_protocol,
            operation_protocol: value.operation_protocol,
            app_daemon_protocol: value.app_daemon_protocol,
            agent_catalog_schema: value.agent_catalog_schema,
            agent_entry_abi: value.agent_entry_abi,
            implant_abi: value.implant_abi,
        }
    }
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Artifact {
    pub role: ArtifactRole,
    pub file: String,
    pub size: u64,
    pub sha256: String,
    pub activation: Activation,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub package_name: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub version_code: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub certificate_sha256: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub abi: Option<Abi>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub resource: Option<ResourceMetadata>,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct ResourceMetadata {
    pub owner: String,
    pub locale: String,
    pub target_package: String,
    pub compatibility_schema: u32,
    pub overlay_package: String,
    pub coverage_ledger: ResourceFile,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct ResourceFile {
    pub file: String,
    pub size: u64,
    pub sha256: String,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct ApkEvidence {
    pub schema_version: u32,
    pub source_commit: String,
    pub file: String,
    pub size: u64,
    pub sha256: String,
    pub package_name: String,
    pub version_code: u64,
    pub certificate_sha256: String,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct RestoreDocument {
    pub schema_version: u32,
    pub release_identity: String,
    pub source_definition_sha256: String,
    pub restorable_closure_sha256: String,
}

impl RestoreDocument {
    pub(crate) fn validate_shape(&self) -> Result<()> {
        ensure!(
            self.schema_version == 1,
            "unsupported restore schemaVersion"
        );
        let release = semver::Version::parse(&self.release_identity)
            .context("restore releaseIdentity must be strict SemVer")?;
        ensure!(
            release.to_string() == self.release_identity,
            "restore releaseIdentity must be canonical SemVer"
        );
        ensure!(
            is_sha256(&self.source_definition_sha256),
            "restore sourceDefinitionSha256 is invalid"
        );
        ensure!(
            is_sha256(&self.restorable_closure_sha256),
            "restore restorableClosureSha256 is invalid"
        );
        Ok(())
    }
}

impl ApkEvidence {
    pub fn load(path: &Path) -> Result<Self> {
        let bytes =
            fs::read(path).with_context(|| format!("read APK evidence {}", path.display()))?;
        let evidence: Self = crate::verify::parse_canonical_json(&bytes)?;
        evidence.validate()?;
        Ok(evidence)
    }

    pub fn validate(&self) -> Result<()> {
        ensure!(
            self.schema_version == 1,
            "unsupported APK evidence schemaVersion {}",
            self.schema_version
        );
        ensure!(
            is_full_commit(&self.source_commit),
            "APK evidence sourceCommit must be a full lowercase Git commit"
        );
        ensure!(
            self.file == "voboost-app.apk",
            "APK evidence file must be voboost-app.apk"
        );
        ensure!(
            self.size > 0 && self.size <= 128 * 1024 * 1024,
            "APK evidence size is outside the supported bound"
        );
        ensure!(is_sha256(&self.sha256), "APK evidence sha256 is invalid");
        ensure!(
            self.package_name == "ru.voboost",
            "APK evidence packageName must be ru.voboost"
        );
        ensure!(
            (1..=MAX_APK_VERSION_CODE).contains(&self.version_code),
            "APK evidence versionCode must be in 1..={MAX_APK_VERSION_CODE}"
        );
        ensure!(
            is_sha256(&self.certificate_sha256),
            "APK evidence certificateSha256 is invalid"
        );
        Ok(())
    }
}

#[derive(Clone, Copy, Debug, Deserialize, Eq, Ord, PartialEq, PartialOrd, Serialize)]
#[serde(rename_all = "kebab-case")]
pub enum ArtifactRole {
    App,
    Daemon,
    Inject,
    Install,
    Restore,
    Uninstall,
    Selinux,
    Init,
    RestoreAuthorization,
    RestoreSignature,
    Implant,
    Lsplant,
    AgentCatalog,
    AgentCatalogSignature,
    AgentCommon,
    AgentModule,
    QnxDaemon,
    QnxInit,
    QnxImplant,
    QnxRuntime,
    QnxAgentModule,
    ResourceApk,
    ResourceLedger,
    QnxResourcePack,
    QnxResourceLedger,
}

#[derive(Clone, Copy, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "kebab-case")]
pub enum Activation {
    PackageInstaller,
    NativeRuntime,
    Init,
    RestoreAuthority,
    AgentGeneration,
}

#[derive(Clone, Copy, Debug, Deserialize, Eq, PartialEq, Serialize)]
pub enum Abi {
    #[serde(rename = "arm64-v8a")]
    Arm64V8a,
    #[serde(rename = "armeabi-v7a")]
    ArmeabiV7a,
    #[serde(rename = "qnx-aarch64")]
    QnxAarch64,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ReuseDecision {
    New,
    Changed,
    Reused,
    Removed,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum ArtifactSource {
    Component(Repository),
    Product,
}

pub fn compose(
    definition: &Definition,
    definition_sha256: &str,
    artifact_directory: &Path,
    android_sdk: &Path,
    apk_evidence: Option<&ApkEvidence>,
    restore_public_key: &VerifyingKey,
    base: Option<(&Definition, &Manifest, &Path)>,
) -> Result<(Manifest, BTreeMap<String, ReuseDecision>)> {
    ensure!(
        is_sha256(definition_sha256),
        "source definition SHA-256 is invalid"
    );
    let observed_apk = crate::verify::inspect_apk(
        &artifact_directory.join("voboost-app.apk"),
        android_sdk,
        &definition.repositories.app,
    )?;
    definition.validate_apk_version_code(observed_apk.version_code)?;
    if let Some(declared) = apk_evidence {
        declared.validate()?;
        crate::verify::verify_apk_evidence(&observed_apk, declared)?;
    }
    let artifacts = collect_artifacts(artifact_directory, &observed_apk, android_sdk, true)?;
    verify_restore_pair(
        definition,
        definition_sha256,
        &artifacts,
        artifact_directory,
        restore_public_key,
    )?;

    let manifest = Manifest {
        schema_version: 1,
        release_identity: definition.release.clone(),
        compatibility: (&definition.compatibility).into(),
        artifacts,
    };
    crate::verify::validate_manifest(&manifest)?;

    compose_reuse(definition, base, manifest)
}

fn collect_artifacts(
    artifact_directory: &Path,
    observed_apk: &ApkEvidence,
    android_sdk: &Path,
    require_restore_pair: bool,
) -> Result<Vec<Artifact>> {
    let mut artifacts = Vec::new();
    for entry in fs::read_dir(artifact_directory)
        .with_context(|| format!("read artifact directory {}", artifact_directory.display()))?
    {
        let entry = entry?;
        let file_type = entry.file_type()?;
        ensure!(
            file_type.is_file() && !file_type.is_symlink(),
            "artifact input must contain regular files only: {}",
            entry.path().display()
        );
        let name = entry
            .file_name()
            .into_string()
            .map_err(|_| anyhow::anyhow!("artifact filename is not UTF-8"))?;
        ensure!(
            name != "voboost.json" && name != "voboost.json.sig",
            "artifact input must not contain authored manifest output {name}"
        );
        if !require_restore_pair && matches!(name.as_str(), "restore.json" | "restore.json.sig") {
            continue;
        }
        let (role, activation, abi) = classify(&name)?;
        let (size, sha256) = identity(&entry.path())?;
        let mut artifact = Artifact {
            role,
            file: name,
            size,
            sha256,
            activation,
            package_name: None,
            version_code: None,
            certificate_sha256: None,
            abi,
            resource: None,
        };
        if role == ArtifactRole::QnxResourcePack {
            let ledger = artifact_directory.join(
                artifact
                    .file
                    .strip_suffix(".kzb")
                    .context("QNX pack suffix")?
                    .to_owned()
                    + ".json",
            );
            ensure!(
                fs::metadata(entry.path())?.len() <= 16 * 1024 * 1024
                    && fs::metadata(&ledger)?.len() <= 1024 * 1024,
                "QNX resource bounds"
            );
            voboost_contract_core::resource_pack::inspect(
                &fs::read(entry.path())?,
                &fs::read(ledger)?,
            )
            .map_err(anyhow::Error::msg)?;
        }
        if role == ArtifactRole::ResourceApk {
            let ledger_name = artifact
                .file
                .strip_suffix(".apk")
                .context("resource APK suffix")?
                .to_owned()
                + ".json";
            let (observed, resource) = crate::resource_delivery::inspect(
                &entry.path(),
                &artifact_directory.join(ledger_name),
                android_sdk,
                &observed_apk.source_commit,
                &observed_apk.certificate_sha256,
            )?;
            ensure!(
                observed.size == artifact.size && observed.sha256 == artifact.sha256,
                "resource APK changed after inspection"
            );
            artifact.package_name = Some(observed.package_name);
            artifact.version_code = Some(observed.version_code);
            artifact.certificate_sha256 = Some(observed.certificate_sha256);
            artifact.resource = Some(resource);
        }
        if role == ArtifactRole::App {
            ensure!(
                artifact.file == observed_apk.file
                    && artifact.size == observed_apk.size
                    && artifact.sha256 == observed_apk.sha256,
                "APK bytes changed after Android identity inspection"
            );
            artifact.package_name = Some(observed_apk.package_name.clone());
            artifact.version_code = Some(observed_apk.version_code);
            artifact.certificate_sha256 = Some(observed_apk.certificate_sha256.clone());
        }
        artifacts.push(artifact);
    }
    artifacts.sort_by_key(|artifact| (role_rank(artifact.role), artifact.file.clone()));
    Ok(artifacts)
}

pub fn produce_restore_pair(
    definition: &Definition,
    definition_sha256: &str,
    artifact_directory: &Path,
    android_sdk: &Path,
    signing_key: &SigningKey,
) -> Result<RestoreDocument> {
    ensure!(
        is_sha256(definition_sha256),
        "source definition SHA-256 is invalid"
    );
    ensure!(
        !artifact_directory.join("restore.json").exists()
            && !artifact_directory.join("restore.json.sig").exists(),
        "restore pair already exists"
    );
    let observed_apk = crate::verify::inspect_apk(
        &artifact_directory.join("voboost-app.apk"),
        android_sdk,
        &definition.repositories.app,
    )?;
    definition.validate_apk_version_code(observed_apk.version_code)?;
    let artifacts = collect_artifacts(artifact_directory, &observed_apk, android_sdk, false)?;
    let document = RestoreDocument {
        schema_version: 1,
        release_identity: definition.release.clone(),
        source_definition_sha256: definition_sha256.to_owned(),
        restorable_closure_sha256: restorable_closure_sha256(&artifacts)?,
    };
    document.validate_shape()?;
    let bytes = canonical_restore_bytes(&document)?;
    let signature = signing_key.sign(&bytes);
    fs::write(artifact_directory.join("restore.json"), bytes)
        .context("write canonical restore.json")?;
    fs::write(
        artifact_directory.join("restore.json.sig"),
        signature.to_bytes(),
    )
    .context("write restore.json.sig")?;
    Ok(document)
}

pub fn verify_restore_pair(
    definition: &Definition,
    definition_sha256: &str,
    artifacts: &[Artifact],
    artifact_directory: &Path,
    verifying_key: &VerifyingKey,
) -> Result<RestoreDocument> {
    let app_version_code = artifacts
        .iter()
        .find(|artifact| artifact.role == ArtifactRole::App)
        .and_then(|artifact| artifact.version_code)
        .context("restore closure lacks APK versionCode")?;
    definition.validate_apk_version_code(app_version_code)?;
    let bytes = fs::read(artifact_directory.join("restore.json")).context("read restore.json")?;
    let document: RestoreDocument = crate::verify::parse_canonical_json(&bytes)?;
    document.validate_shape()?;
    ensure!(
        document.release_identity == definition.release,
        "restore releaseIdentity is stale"
    );
    ensure!(
        document.source_definition_sha256 == definition_sha256,
        "restore sourceDefinitionSha256 is stale"
    );
    ensure!(
        document.restorable_closure_sha256 == restorable_closure_sha256(artifacts)?,
        "restore restorableClosureSha256 is stale"
    );
    let signature_bytes =
        fs::read(artifact_directory.join("restore.json.sig")).context("read restore.json.sig")?;
    let signature = Signature::from_slice(&signature_bytes)
        .context("restore signature must be raw 64-byte Ed25519")?;
    verifying_key
        .verify_strict(&bytes, &signature)
        .context("restore role signature verification failed")?;
    Ok(document)
}

fn canonical_restore_bytes(document: &RestoreDocument) -> Result<Vec<u8>> {
    let mut bytes = serde_json::to_vec(document)?;
    bytes.push(b'\n');
    Ok(bytes)
}

fn restorable_closure_sha256(artifacts: &[Artifact]) -> Result<String> {
    let restorable: Vec<&Artifact> = artifacts
        .iter()
        .filter(|artifact| {
            !matches!(
                artifact.role,
                ArtifactRole::RestoreAuthorization | ArtifactRole::RestoreSignature
            )
        })
        .collect();
    ensure!(!restorable.is_empty(), "restorable closure is empty");
    Ok(hex::encode(Sha256::digest(serde_json::to_vec(
        &restorable,
    )?)))
}

pub fn load_signing_key(path: &Path) -> Result<SigningKey> {
    let bytes =
        fs::read(path).with_context(|| format!("read restore signing key {}", path.display()))?;
    let secret: [u8; 32] = bytes.try_into().map_err(|_| {
        anyhow::anyhow!("restore signing key must contain raw 32-byte Ed25519 secret")
    })?;
    Ok(SigningKey::from_bytes(&secret))
}

pub fn load_verifying_key(path: &Path) -> Result<VerifyingKey> {
    let bytes =
        fs::read(path).with_context(|| format!("read restore public key {}", path.display()))?;
    let public: [u8; 32] = bytes
        .try_into()
        .map_err(|_| anyhow::anyhow!("restore public key must contain raw 32-byte Ed25519 key"))?;
    VerifyingKey::from_bytes(&public).context("restore public key is invalid")
}

fn compose_reuse(
    definition: &Definition,
    base: Option<(&Definition, &Manifest, &Path)>,
    manifest: Manifest,
) -> Result<(Manifest, BTreeMap<String, ReuseDecision>)> {
    let mut decisions = BTreeMap::new();
    if let Some((base_definition, base_manifest, base_directory)) = base {
        ensure!(
            base_definition.release == base_manifest.release_identity,
            "base definition and base manifest release identities differ"
        );
        ensure!(
            definition.base_release.as_deref() == Some(base_manifest.release_identity.as_str()),
            "base manifest identity does not match definition.base_release"
        );
        crate::verify::verify_closure(base_manifest, base_directory)?;
        let base_artifacts: BTreeMap<&str, &Artifact> = base_manifest
            .artifacts
            .iter()
            .map(|artifact| (artifact.file.as_str(), artifact))
            .collect();
        for artifact in &manifest.artifacts {
            let Some(previous) = base_artifacts.get(artifact.file.as_str()) else {
                require_source_change(
                    definition,
                    base_definition,
                    artifact.role,
                    &artifact.file,
                    "appeared",
                )?;
                decisions.insert(artifact.file.clone(), ReuseDecision::New);
                continue;
            };
            if artifact.size == previous.size && artifact.sha256 == previous.sha256 {
                ensure!(
                    artifact == *previous,
                    "artifact {} has identical bytes but conflicting metadata",
                    artifact.file
                );
                ensure!(
                    artifact_source(artifact.role) != ArtifactSource::Product,
                    "product-derived authority {} must change for every release identity",
                    artifact.file
                );
                decisions.insert(artifact.file.clone(), ReuseDecision::Reused);
                continue;
            }
            require_source_change(
                definition,
                base_definition,
                artifact.role,
                &artifact.file,
                "changed bytes",
            )?;
            decisions.insert(artifact.file.clone(), ReuseDecision::Changed);
        }
        let current_files: BTreeSet<&str> = manifest
            .artifacts
            .iter()
            .map(|artifact| artifact.file.as_str())
            .collect();
        for previous in &base_manifest.artifacts {
            if current_files.contains(previous.file.as_str()) {
                continue;
            }
            require_source_change(
                definition,
                base_definition,
                previous.role,
                &previous.file,
                "disappeared",
            )?;
            decisions.insert(previous.file.clone(), ReuseDecision::Removed);
        }
    } else {
        ensure!(
            definition.base_release.is_none(),
            "base release inputs are required when base_release is set"
        );
        for artifact in &manifest.artifacts {
            decisions.insert(artifact.file.clone(), ReuseDecision::New);
        }
    }
    Ok((manifest, decisions))
}

fn require_source_change(
    definition: &Definition,
    base_definition: &Definition,
    role: ArtifactRole,
    file: &str,
    change: &str,
) -> Result<()> {
    if let ArtifactSource::Component(repository) = artifact_source(role) {
        ensure!(
            definition.repositories.commit(repository)
                != base_definition.repositories.commit(repository),
            "reproducibility failure: {file} {change} while its {repository:?} commit is unchanged"
        );
    }
    Ok(())
}

pub fn canonical_bytes(manifest: &Manifest) -> Result<Vec<u8>> {
    let mut bytes = serde_json::to_vec(manifest)?;
    bytes.push(b'\n');
    Ok(bytes)
}

pub fn write_canonical(path: &Path, manifest: &Manifest) -> Result<()> {
    fs::write(path, canonical_bytes(manifest)?)
        .with_context(|| format!("write manifest {}", path.display()))
}

pub fn identity(path: &Path) -> Result<(u64, String)> {
    let file = File::open(path).with_context(|| format!("open {}", path.display()))?;
    let metadata = file.metadata()?;
    ensure!(metadata.is_file(), "not a regular file: {}", path.display());
    let mut reader = BufReader::new(file);
    let mut hasher = Sha256::new();
    let mut buffer = [0_u8; 64 * 1024];
    let mut size = 0_u64;
    loop {
        let read = reader.read(&mut buffer)?;
        if read == 0 {
            break;
        }
        size = size
            .checked_add(read as u64)
            .context("artifact size overflow")?;
        hasher.update(&buffer[..read]);
    }
    ensure!(
        size == metadata.len(),
        "artifact changed while hashing: {}",
        path.display()
    );
    Ok((size, hex::encode(hasher.finalize())))
}

fn artifact_source(role: ArtifactRole) -> ArtifactSource {
    match role {
        ArtifactRole::App => ArtifactSource::Component(Repository::App),
        ArtifactRole::AgentCatalog
        | ArtifactRole::AgentCatalogSignature
        | ArtifactRole::AgentCommon
        | ArtifactRole::AgentModule
        | ArtifactRole::QnxRuntime
        | ArtifactRole::QnxAgentModule
        | ArtifactRole::ResourceApk
        | ArtifactRole::ResourceLedger
        | ArtifactRole::QnxResourcePack
        | ArtifactRole::QnxResourceLedger => ArtifactSource::Component(Repository::Agents),
        ArtifactRole::RestoreAuthorization | ArtifactRole::RestoreSignature => {
            ArtifactSource::Product
        }
        ArtifactRole::Daemon
        | ArtifactRole::Inject
        | ArtifactRole::Install
        | ArtifactRole::Restore
        | ArtifactRole::Uninstall
        | ArtifactRole::Selinux
        | ArtifactRole::Init
        | ArtifactRole::Implant
        | ArtifactRole::Lsplant
        | ArtifactRole::QnxDaemon
        | ArtifactRole::QnxInit
        | ArtifactRole::QnxImplant => ArtifactSource::Component(Repository::Inject),
    }
}

pub(crate) fn is_sha256(value: &str) -> bool {
    value.len() == 64
        && value
            .bytes()
            .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
}

pub fn required_inventory() -> BTreeSet<&'static str> {
    [
        "voboost-app.apk",
        "voboost-daemon",
        "voboost-inject",
        "voboost-install",
        "voboost-restore",
        "voboost-uninstall",
        "voboost-selinux",
        "voboost.rc",
        "restore.json",
        "restore.json.sig",
        "libimplant-arm64-v8a.so",
        "libimplant-armeabi-v7a.so",
        "agents.json",
        "agents.json.sig",
        "agent-common.dex",
        "voboost-cluster-daemon",
        "voboost-cluster-startup.sh",
        "libvoboost-cluster-inject.so",
        "libvoboost-runtime-cluster.so",
        "libagent-font-cyrillic.so",
    ]
    .into_iter()
    .collect()
}

pub(crate) fn english_resource_packages(apk_file: &str) -> Option<(&'static str, &'static str)> {
    Some(match apk_file.strip_prefix("resource-i18n-en-")?.strip_suffix(".apk")? {
        "setting-a11" => ("com.qinggan.app.setting", "ru.voboost.translation.setting.a11.en"),
        "launcher-a9" => ("com.qinggan.app.launcher", "ru.voboost.translation.launcher.en"),
        "launcher-a11" => ("com.qinggan.app.launcher", "ru.voboost.translation.launcher.a11.en"),
        "systemui-a9" => ("com.qinggan.systemui", "ru.voboost.translation.systemui.en"),
        "media-a9" => ("com.qinggan.media", "ru.voboost.translation.media.en"),
        "media-a11" => ("com.qinggan.media", "ru.voboost.translation.media.a11.en"),
        _ => return None,
    })
}

pub(crate) fn classify(name: &str) -> Result<(ArtifactRole, Activation, Option<Abi>)> {
    for (suffix, role) in [
        (".kzb", ArtifactRole::QnxResourcePack),
        (".json", ArtifactRole::QnxResourceLedger),
    ] {
        if name
            .strip_prefix("qnx-resource-i18n-ru-")
            .and_then(|s| s.strip_suffix(suffix))
            .is_some_and(|stem| {
                !stem.is_empty()
                    && stem.len() <= 96
                    && stem.split('-').all(|p| {
                        !p.is_empty()
                            && p.bytes()
                                .all(|b| b.is_ascii_lowercase() || b.is_ascii_digit())
                    })
            })
        {
            return Ok((role, Activation::AgentGeneration, Some(Abi::QnxAarch64)));
        }
    }
    for (suffix, role) in [
        (".apk", ArtifactRole::ResourceApk),
        (".json", ArtifactRole::ResourceLedger),
    ] {
        if let Some(stem) = name
            .strip_prefix("resource-i18n-ru-")
            .and_then(|s| s.strip_suffix(suffix))
            .or_else(|| {
                let stem = name.strip_prefix("resource-i18n-en-")?.strip_suffix(suffix)?;
                voboost_contract_core::release::EN_RESOURCE_PROFILES.contains(&stem).then_some(stem)
            })
            && !stem.is_empty()
            && stem.len() <= 128
            && stem.split('-').all(|s| {
                !s.is_empty()
                    && s.bytes()
                        .all(|b| b.is_ascii_lowercase() || b.is_ascii_digit())
            })
        {
            return Ok((role, Activation::AgentGeneration, None));
        }
    }
    let exact = match name {
        "voboost-cluster-daemon" => Some((
            ArtifactRole::QnxDaemon,
            Activation::NativeRuntime,
            Some(Abi::QnxAarch64),
        )),
        "voboost-cluster-startup.sh" => Some((ArtifactRole::QnxInit, Activation::Init, None)),
        "libvoboost-cluster-inject.so" => Some((
            ArtifactRole::QnxImplant,
            Activation::NativeRuntime,
            Some(Abi::QnxAarch64),
        )),
        "libvoboost-runtime-cluster.so" => Some((
            ArtifactRole::QnxRuntime,
            Activation::AgentGeneration,
            Some(Abi::QnxAarch64),
        )),
        "libagent-font-cyrillic.so" => Some((
            ArtifactRole::QnxAgentModule,
            Activation::AgentGeneration,
            Some(Abi::QnxAarch64),
        )),

        "voboost-app.apk" => Some((ArtifactRole::App, Activation::PackageInstaller, None)),
        "voboost-daemon" => Some((ArtifactRole::Daemon, Activation::NativeRuntime, None)),
        "voboost-inject" => Some((ArtifactRole::Inject, Activation::NativeRuntime, None)),
        "voboost-install" => Some((ArtifactRole::Install, Activation::NativeRuntime, None)),
        "voboost-restore" => Some((ArtifactRole::Restore, Activation::RestoreAuthority, None)),
        "voboost-uninstall" => Some((ArtifactRole::Uninstall, Activation::RestoreAuthority, None)),
        "voboost-selinux" => Some((ArtifactRole::Selinux, Activation::NativeRuntime, None)),
        "voboost.rc" => Some((ArtifactRole::Init, Activation::Init, None)),
        "restore.json" => Some((
            ArtifactRole::RestoreAuthorization,
            Activation::RestoreAuthority,
            None,
        )),
        "restore.json.sig" => Some((
            ArtifactRole::RestoreSignature,
            Activation::RestoreAuthority,
            None,
        )),
        "agents.json" => Some((
            ArtifactRole::AgentCatalog,
            Activation::AgentGeneration,
            None,
        )),
        "agents.json.sig" => Some((
            ArtifactRole::AgentCatalogSignature,
            Activation::AgentGeneration,
            None,
        )),
        "agent-common.dex" => Some((ArtifactRole::AgentCommon, Activation::AgentGeneration, None)),
        "libimplant-arm64-v8a.so" => Some((
            ArtifactRole::Implant,
            Activation::NativeRuntime,
            Some(Abi::Arm64V8a),
        )),
        "libimplant-armeabi-v7a.so" => Some((
            ArtifactRole::Implant,
            Activation::NativeRuntime,
            Some(Abi::ArmeabiV7a),
        )),
        "liblsplant-arm64-v8a.so" => Some((
            ArtifactRole::Lsplant,
            Activation::NativeRuntime,
            Some(Abi::Arm64V8a),
        )),
        "liblsplant-armeabi-v7a.so" => Some((
            ArtifactRole::Lsplant,
            Activation::NativeRuntime,
            Some(Abi::ArmeabiV7a),
        )),
        _ => None,
    };
    if let Some(value) = exact {
        return Ok(value);
    }
    if let Some(module) = name
        .strip_prefix("libagent-")
        .and_then(|value| value.strip_suffix(".so"))
        && !module.is_empty()
        && module.split('-').all(|part| {
            !part.is_empty()
                && part
                    .bytes()
                    .all(|byte| byte.is_ascii_lowercase() || byte.is_ascii_digit())
        })
    {
        return Ok((
            ArtifactRole::QnxAgentModule,
            Activation::AgentGeneration,
            Some(Abi::QnxAarch64),
        ));
    }
    if name.starts_with("agent-") && name.ends_with(".dex") {
        let module = &name["agent-".len()..name.len() - ".dex".len()];
        ensure!(
            !module.is_empty()
                && module.split('-').all(|segment| {
                    !segment.is_empty()
                        && segment
                            .bytes()
                            .all(|byte| byte.is_ascii_lowercase() || byte.is_ascii_digit())
                }),
            "agent module filename must use lowercase alphanumeric hyphen-separated segments"
        );
        return Ok((ArtifactRole::AgentModule, Activation::AgentGeneration, None));
    }
    bail!("unknown product artifact {name:?}")
}

pub(crate) fn role_rank(role: ArtifactRole) -> u8 {
    match role {
        ArtifactRole::App => 0,
        ArtifactRole::Daemon => 1,
        ArtifactRole::Inject => 2,
        ArtifactRole::Install => 3,
        ArtifactRole::Restore => 4,
        ArtifactRole::Uninstall => 5,
        ArtifactRole::Selinux => 6,
        ArtifactRole::Init => 7,
        ArtifactRole::RestoreAuthorization => 8,
        ArtifactRole::RestoreSignature => 9,
        ArtifactRole::Implant => 10,
        ArtifactRole::Lsplant => 11,
        ArtifactRole::AgentCatalog => 12,
        ArtifactRole::AgentCatalogSignature => 13,
        ArtifactRole::AgentCommon => 14,
        ArtifactRole::AgentModule => 15,
        ArtifactRole::QnxDaemon => 16,
        ArtifactRole::QnxInit => 17,
        ArtifactRole::QnxImplant => 18,
        ArtifactRole::QnxRuntime => 19,
        ArtifactRole::QnxAgentModule => 20,
        ArtifactRole::ResourceApk => 21,
        ArtifactRole::ResourceLedger => 22,
        ArtifactRole::QnxResourcePack => 23,
        ArtifactRole::QnxResourceLedger => 24,
    }
}

pub fn artifact_path(directory: &Path, artifact: &Artifact) -> PathBuf {
    directory.join(&artifact.file)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    use std::os::unix::fs::PermissionsExt;

    fn write_closure(directory: &Path) {
        fs::create_dir_all(directory).unwrap();
        for name in required_inventory()
            .into_iter()
            .chain(["agent-settings.dex"])
        {
            if matches!(name, "restore.json" | "restore.json.sig") {
                continue;
            }
            let bytes = if name.ends_with(".sig") {
                vec![7_u8; 64]
            } else {
                vec![7_u8]
            };
            fs::write(directory.join(name), bytes).unwrap();
        }
    }

    fn fake_sdk(directory: &Path, version_code: u64, certificate: &str) -> PathBuf {
        let tools = directory.join("build-tools/34.0.0");
        fs::create_dir_all(&tools).unwrap();
        fs::write(tools.join("source.properties"), "Pkg.Revision=34.0.0\n").unwrap();
        let aapt2 = tools.join("aapt2");
        let apksigner = tools.join("apksigner");
        fs::write(
            &aapt2,
            format!("#!/bin/sh\nprintf \"package: name='ru.voboost' versionCode='{version_code}' versionName='{version_code}'\\n\"\n"),
        )
        .unwrap();
        fs::write(
            &apksigner,
            format!("#!/bin/sh\nprintf \"Verified using v2 scheme (APK Signature Scheme v2): true\\nNumber of signers: 1\\nSigner #1 certificate SHA-256 digest: {certificate}\\n\"\n"),
        )
        .unwrap();
        for tool in [&aapt2, &apksigner] {
            let mut permissions = fs::metadata(tool).unwrap().permissions();
            permissions.set_mode(0o755);
            fs::set_permissions(tool, permissions).unwrap();
        }
        directory.to_path_buf()
    }

    fn copy_closure(source: &Path, target: &Path) {
        fs::create_dir_all(target).unwrap();
        for entry in fs::read_dir(source).unwrap() {
            let entry = entry.unwrap();
            fs::copy(entry.path(), target.join(entry.file_name())).unwrap();
        }
    }

    fn next_definition(base: &Definition) -> Definition {
        let mut next = base.clone();
        next.release = "1.0.0-beta2".into();
        next.base_release = Some(base.release.clone());
        next
    }

    fn signing_key() -> SigningKey {
        SigningKey::from_bytes(&[7_u8; 32])
    }

    fn prepare_restore(
        definition: &Definition,
        definition_sha256: &str,
        directory: &Path,
        sdk: &Path,
    ) {
        for name in ["restore.json", "restore.json.sig"] {
            let path = directory.join(name);
            if path.exists() {
                fs::remove_file(path).unwrap();
            }
        }
        produce_restore_pair(
            definition,
            definition_sha256,
            directory,
            sdk,
            &signing_key(),
        )
        .unwrap();
    }

    #[test]
    fn rejects_old_or_versioned_artifact_names() {
        for name in [
            "base.apk",
            "voboost-inject-1.0.0",
            "voboost-app-1.0.0.apk",
            "payload/voboost-daemon",
        ] {
            assert!(classify(name).is_err(), "accepted {name}");
        }
    }

    #[test]
    fn classifies_required_inject_runtime() {
        assert!(required_inventory().contains("voboost-inject"));
        assert_eq!(
            classify("voboost-inject").unwrap(),
            (ArtifactRole::Inject, Activation::NativeRuntime, None)
        );
    }

    #[test]
    fn classifies_target_agent_module() {
        assert_eq!(
            classify("agent-settings-voboost.dex").unwrap().0,
            ArtifactRole::AgentModule
        );
    }

    #[test]
    fn compose_requires_verified_apk_evidence() {
        let root = tempfile::tempdir().unwrap();
        let closure = root.path().join("closure");
        write_closure(&closure);
        let definition = Definition::load(
            &Path::new(env!("CARGO_MANIFEST_DIR")).join("fixtures/definition/1.0.0-beta1.toml"),
        )
        .unwrap();
        let definition_sha256 = "d".repeat(64);
        let sdk = fake_sdk(&root.path().join("sdk"), 1_000_001, &"a".repeat(64));
        prepare_restore(&definition, &definition_sha256, &closure, &sdk);
        let mut apk = crate::verify::inspect_apk(
            &closure.join("voboost-app.apk"),
            &sdk,
            &definition.repositories.app,
        )
        .unwrap();
        let key = signing_key().verifying_key();
        let (manifest, _) = compose(
            &definition,
            &definition_sha256,
            &closure,
            &sdk,
            Some(&apk),
            &key,
            None,
        )
        .unwrap();
        let app = manifest
            .artifacts
            .iter()
            .find(|artifact| artifact.role == ArtifactRole::App)
            .unwrap();
        assert_eq!(app.package_name.as_deref(), Some("ru.voboost"));
        assert_eq!(app.version_code, Some(1_000_001));
        assert_eq!(
            app.certificate_sha256.as_deref(),
            Some("a".repeat(64).as_str())
        );

        apk.version_code = 1_000_002;
        assert!(
            compose(
                &definition,
                &definition_sha256,
                &closure,
                &sdk,
                Some(&apk),
                &key,
                None,
            )
            .is_err()
        );
        apk = crate::verify::inspect_apk(
            &closure.join("voboost-app.apk"),
            &sdk,
            &definition.repositories.app,
        )
        .unwrap();
        apk.certificate_sha256 = "b".repeat(64);
        assert!(
            compose(
                &definition,
                &definition_sha256,
                &closure,
                &sdk,
                Some(&apk),
                &key,
                None,
            )
            .is_err()
        );
    }

    #[test]
    fn apk_evidence_fixture_requires_complete_identity() {
        let fixtures = Path::new(env!("CARGO_MANIFEST_DIR")).join("fixtures/apk");
        let mut evidence = ApkEvidence::load(&fixtures.join("valid.json")).unwrap();
        evidence.version_code = MAX_APK_VERSION_CODE;
        assert!(evidence.validate().is_ok());
        evidence.version_code = 0;
        assert!(evidence.validate().is_err());
        evidence.version_code = MAX_APK_VERSION_CODE + 1;
        assert!(evidence.validate().is_err());
        assert!(ApkEvidence::load(&fixtures.join("invalid-missing-certificate.json")).is_err());
    }

    #[test]
    fn restore_pair_rejects_garbage_stale_identity_stale_closure_and_signature_substitution() {
        let root = tempfile::tempdir().unwrap();
        let closure = root.path().join("closure");
        write_closure(&closure);
        let definition = Definition::load(
            &Path::new(env!("CARGO_MANIFEST_DIR")).join("fixtures/definition/1.0.0-beta1.toml"),
        )
        .unwrap();
        let definition_sha256 = "d".repeat(64);
        let sdk = fake_sdk(&root.path().join("sdk"), 1_000_001, &"a".repeat(64));
        let key = signing_key();

        prepare_restore(&definition, &definition_sha256, &closure, &sdk);
        assert!(
            compose(
                &definition,
                &definition_sha256,
                &closure,
                &sdk,
                None,
                &key.verifying_key(),
                None,
            )
            .is_ok()
        );

        fs::write(closure.join("restore.json"), b"{}\n").unwrap();
        assert!(
            compose(
                &definition,
                &definition_sha256,
                &closure,
                &sdk,
                None,
                &key.verifying_key(),
                None,
            )
            .is_err()
        );

        prepare_restore(&definition, &definition_sha256, &closure, &sdk);
        let mut next = next_definition(&definition);
        next.repositories.app = definition.repositories.app.clone();
        assert!(
            compose(
                &next,
                &"e".repeat(64),
                &closure,
                &sdk,
                None,
                &key.verifying_key(),
                None,
            )
            .is_err()
        );

        prepare_restore(&definition, &definition_sha256, &closure, &sdk);
        fs::write(closure.join("voboost-daemon"), [9_u8]).unwrap();
        assert!(
            compose(
                &definition,
                &definition_sha256,
                &closure,
                &sdk,
                None,
                &key.verifying_key(),
                None,
            )
            .is_err()
        );

        prepare_restore(&definition, &definition_sha256, &closure, &sdk);
        fs::write(closure.join("restore.json.sig"), [0_u8; 64]).unwrap();
        assert!(
            compose(
                &definition,
                &definition_sha256,
                &closure,
                &sdk,
                None,
                &key.verifying_key(),
                None,
            )
            .is_err()
        );
    }

    #[test]
    fn reuses_equal_component_bytes_and_rejects_changed_bytes_at_same_commit() {
        let root = tempfile::tempdir().unwrap();
        let base_directory = root.path().join("base");
        let next_directory = root.path().join("next");
        write_closure(&base_directory);
        copy_closure(&base_directory, &next_directory);
        let base_definition = Definition::load(
            &Path::new(env!("CARGO_MANIFEST_DIR")).join("fixtures/definition/1.0.0-beta1.toml"),
        )
        .unwrap();
        let base_sha = "d".repeat(64);
        let next_sha = "e".repeat(64);
        let sdk = fake_sdk(&root.path().join("sdk"), 1_000_001, &"a".repeat(64));
        let key = signing_key();
        prepare_restore(&base_definition, &base_sha, &base_directory, &sdk);
        let base_evidence = crate::verify::inspect_apk(
            &base_directory.join("voboost-app.apk"),
            &sdk,
            &base_definition.repositories.app,
        )
        .unwrap();
        let (base_manifest, _) = compose(
            &base_definition,
            &base_sha,
            &base_directory,
            &sdk,
            Some(&base_evidence),
            &key.verifying_key(),
            None,
        )
        .unwrap();

        let next_definition = next_definition(&base_definition);
        prepare_restore(&next_definition, &next_sha, &next_directory, &sdk);
        let next_evidence = crate::verify::inspect_apk(
            &next_directory.join("voboost-app.apk"),
            &sdk,
            &next_definition.repositories.app,
        )
        .unwrap();
        let (_, decisions) = compose(
            &next_definition,
            &next_sha,
            &next_directory,
            &sdk,
            Some(&next_evidence),
            &key.verifying_key(),
            Some((&base_definition, &base_manifest, &base_directory)),
        )
        .unwrap();
        assert!(
            decisions
                .iter()
                .filter(|(file, _)| !matches!(file.as_str(), "restore.json" | "restore.json.sig"))
                .all(|(_, decision)| *decision == ReuseDecision::Reused)
        );
        assert_eq!(decisions["restore.json"], ReuseDecision::Changed);
        assert_eq!(decisions["restore.json.sig"], ReuseDecision::Changed);

        fs::write(next_directory.join("voboost-app.apk"), [8_u8]).unwrap();
        prepare_restore(&next_definition, &next_sha, &next_directory, &sdk);
        let changed_evidence = crate::verify::inspect_apk(
            &next_directory.join("voboost-app.apk"),
            &sdk,
            &next_definition.repositories.app,
        )
        .unwrap();
        let error = compose(
            &next_definition,
            &next_sha,
            &next_directory,
            &sdk,
            Some(&changed_evidence),
            &key.verifying_key(),
            Some((&base_definition, &base_manifest, &base_directory)),
        )
        .unwrap_err();
        assert!(error.to_string().contains("reproducibility failure"));
    }

    #[test]
    fn classifies_app_agents_native_and_metadata_only_release_deltas() {
        let root = tempfile::tempdir().unwrap();
        let base_directory = root.path().join("base");
        write_closure(&base_directory);
        let base_definition = Definition::load(
            &Path::new(env!("CARGO_MANIFEST_DIR")).join("fixtures/definition/1.0.0-beta1.toml"),
        )
        .unwrap();
        let base_sha = "d".repeat(64);
        let next_sha = "e".repeat(64);
        let key = signing_key();
        let base_sdk = fake_sdk(&root.path().join("sdk-base"), 1_000_001, &"a".repeat(64));
        prepare_restore(&base_definition, &base_sha, &base_directory, &base_sdk);
        let base_evidence = crate::verify::inspect_apk(
            &base_directory.join("voboost-app.apk"),
            &base_sdk,
            &base_definition.repositories.app,
        )
        .unwrap();
        let (base_manifest, _) = compose(
            &base_definition,
            &base_sha,
            &base_directory,
            &base_sdk,
            Some(&base_evidence),
            &key.verifying_key(),
            None,
        )
        .unwrap();

        for kind in ["app", "agents", "native", "metadata"] {
            let next_directory = root.path().join(kind);
            copy_closure(&base_directory, &next_directory);
            let mut next = next_definition(&base_definition);
            let version_code = match kind {
                "app" => {
                    next.repositories.app = "a".repeat(40);
                    fs::write(next_directory.join("voboost-app.apk"), [8_u8]).unwrap();
                    1_000_002
                }
                "agents" => {
                    next.repositories.agents = "b".repeat(40);
                    fs::write(next_directory.join("agents.json"), [8_u8]).unwrap();
                    fs::write(next_directory.join("agents.json.sig"), vec![8_u8; 64]).unwrap();
                    fs::write(next_directory.join("agent-settings.dex"), [8_u8]).unwrap();
                    1_000_001
                }
                "native" => {
                    next.repositories.inject = "c".repeat(40);
                    fs::write(next_directory.join("voboost-daemon"), [8_u8]).unwrap();
                    1_000_001
                }
                "metadata" => 1_000_001,
                _ => unreachable!(),
            };
            next.android.version_code = version_code;
            let sdk = fake_sdk(
                &root.path().join(format!("sdk-{kind}")),
                version_code,
                &"a".repeat(64),
            );
            prepare_restore(&next, &next_sha, &next_directory, &sdk);
            let next_evidence = crate::verify::inspect_apk(
                &next_directory.join("voboost-app.apk"),
                &sdk,
                &next.repositories.app,
            )
            .unwrap();
            let (_, decisions) = compose(
                &next,
                &next_sha,
                &next_directory,
                &sdk,
                Some(&next_evidence),
                &key.verifying_key(),
                Some((&base_definition, &base_manifest, &base_directory)),
            )
            .unwrap();

            assert_eq!(decisions["restore.json"], ReuseDecision::Changed, "{kind}");
            assert_eq!(
                decisions["restore.json.sig"],
                ReuseDecision::Changed,
                "{kind}"
            );
            let changed: BTreeSet<&str> = decisions
                .iter()
                .filter_map(|(file, decision)| {
                    (*decision == ReuseDecision::Changed).then_some(file.as_str())
                })
                .collect();
            let expected: BTreeSet<&str> = match kind {
                "app" => ["restore.json", "restore.json.sig", "voboost-app.apk"]
                    .into_iter()
                    .collect(),
                "agents" => [
                    "agent-settings.dex",
                    "agents.json",
                    "agents.json.sig",
                    "restore.json",
                    "restore.json.sig",
                ]
                .into_iter()
                .collect(),
                "native" => ["restore.json", "restore.json.sig", "voboost-daemon"]
                    .into_iter()
                    .collect(),
                "metadata" => ["restore.json", "restore.json.sig"].into_iter().collect(),
                _ => unreachable!(),
            };
            assert_eq!(changed, expected, "{kind}");
        }
    }
}

#[cfg(test)]
mod qnx_resource_tests {
    use super::*;
    fn fixture() -> Manifest {
        let mut manifest: Manifest =
            serde_json::from_slice(include_bytes!("fixtures/voboost/valid/minimal.json")).unwrap();
        for (role, suffix) in [
            (ArtifactRole::QnxResourcePack, "kzb"),
            (ArtifactRole::QnxResourceLedger, "json"),
        ] {
            manifest.artifacts.push(Artifact {
                role,
                file: format!("qnx-resource-i18n-ru-a9-gauge.{suffix}"),
                size: 32,
                sha256: "a".repeat(64),
                activation: Activation::AgentGeneration,
                package_name: None,
                version_code: None,
                certificate_sha256: None,
                abi: Some(Abi::QnxAarch64),
                resource: None,
            });
        }
        manifest
    }
    #[test]
    fn qnx_resource_producer_schema_and_shared_parser_agree() {
        let manifest = fixture();
        crate::verify::validate_manifest(&manifest).unwrap();
        let schema: serde_json::Value =
            serde_json::from_str(include_str!("voboost.schema.json")).unwrap();
        let validator = jsonschema::validator_for(&schema).unwrap();
        validator
            .validate(&serde_json::to_value(&manifest).unwrap())
            .unwrap();
        voboost_contract_core::release::Manifest::parse(&canonical_bytes(&manifest).unwrap())
            .unwrap();
        for n in 0..4 {
            let mut invalid = fixture();
            let i = invalid.artifacts.len() - 1;
            match n {
                0 => {
                    invalid.artifacts.pop();
                }
                1 => invalid.artifacts[i].file = "qnx-resource-i18n-ru-a11-gauge.json".into(),
                2 => invalid.artifacts[i].size = 1024 * 1024 + 1,
                _ => invalid.artifacts[i].abi = None,
            };
            assert!(crate::verify::validate_manifest(&invalid).is_err());
            assert!(
                voboost_contract_core::release::Manifest::parse(
                    &canonical_bytes(&invalid).unwrap()
                )
                .is_err()
            );
        }
    }
    #[test]
    fn qnx_resources_are_agents_source_data_with_no_execution_activation() {
        for (suffix, role) in [
            ("kzb", ArtifactRole::QnxResourcePack),
            ("json", ArtifactRole::QnxResourceLedger),
        ] {
            assert_eq!(
                classify(&format!("qnx-resource-i18n-ru-a11-gauge.{suffix}")).unwrap(),
                (role, Activation::AgentGeneration, Some(Abi::QnxAarch64))
            );
            assert!(matches!(
                artifact_source(role),
                ArtifactSource::Component(Repository::Agents)
            ));
        }
    }
}
