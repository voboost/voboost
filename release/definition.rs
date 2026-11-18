use anyhow::{Context, Result, bail, ensure};
use semver::Version;
use serde::Deserialize;
use std::fs;
use std::path::Path;

#[derive(Clone, Debug, Deserialize, Eq, PartialEq)]
#[serde(deny_unknown_fields)]
pub struct Definition {
    pub schema: u32,
    pub release: String,
    pub channel: String,
    #[serde(default)]
    pub base_release: Option<String>,
    pub repositories: Repositories,
    pub android: Android,
    pub compatibility: Compatibility,
    pub gates: Gates,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Android {
    pub version_code: u64,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq)]
#[serde(deny_unknown_fields)]
pub struct Repositories {
    pub app: String,
    pub agents: String,
    pub inject: String,
    pub installer: String,
    pub logo: String,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Compatibility {
    pub installer_protocol: u32,
    pub operation_protocol: u32,
    pub app_daemon_protocol: u32,
    pub agent_catalog_schema: u32,
    pub agent_entry_abi: u32,
    pub implant_abi: u32,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq)]
#[serde(deny_unknown_fields)]
pub struct Gates {
    pub host: Vec<String>,
    pub emulator: Vec<String>,
    pub device: Vec<String>,
    pub vehicle: Vec<String>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum Repository {
    App,
    Agents,
    Inject,
}

impl Repositories {
    pub fn commit(&self, repository: Repository) -> &str {
        match repository {
            Repository::App => &self.app,
            Repository::Agents => &self.agents,
            Repository::Inject => &self.inject,
        }
    }
}

impl Definition {
    pub fn load(path: &Path) -> Result<Self> {
        let source = fs::read_to_string(path)
            .with_context(|| format!("read release definition {}", path.display()))?;
        let definition: Self = toml::from_str(&source)
            .with_context(|| format!("parse release definition {}", path.display()))?;
        definition.validate(path)?;
        Ok(definition)
    }

    /// Load the editable authoring input (release/version.toml). The filename
    /// rule only applies to immutable resolved snapshots named <release>.toml;
    /// this entry point validates everything else and must only be used for
    /// the current authoring input, never for downstream compose/verify.
    #[cfg(test)]
    pub fn load_authoring(path: &Path) -> Result<Self> {
        let source = fs::read_to_string(path)
            .with_context(|| format!("read authoring release input {}", path.display()))?;
        let definition: Self = toml::from_str(&source)
            .with_context(|| format!("parse authoring release input {}", path.display()))?;
        definition.validate_no_filename()?;
        Ok(definition)
    }

    pub fn validate(&self, path: &Path) -> Result<()> {
        self.validate_no_filename()?;
        if let Some(name) = path.file_name().and_then(|value| value.to_str()) {
            ensure!(
                name == format!("{}.toml", self.release),
                "definition filename must equal <release>.toml"
            );
        }
        Ok(())
    }

    fn validate_no_filename(&self) -> Result<()> {
        ensure!(
            self.schema == 1,
            "unsupported definition schema {}",
            self.schema
        );
        let version = canonical_version(&self.release, "release")?;
        match self.channel.as_str() {
            "stable" => ensure!(
                version.pre.is_empty(),
                "stable release must not have a prerelease"
            ),
            "beta" => ensure!(
                !version.pre.is_empty() && version.pre.as_str().starts_with("beta"),
                "beta release must use a beta prerelease"
            ),
            value => bail!("unsupported channel {value:?}"),
        }

        if let Some(base) = &self.base_release {
            let base = canonical_version(base, "base_release")?;
            ensure!(base < version, "base_release must be older than release");
        }

        for (name, value) in [
            ("app", &self.repositories.app),
            ("agents", &self.repositories.agents),
            ("inject", &self.repositories.inject),
            ("installer", &self.repositories.installer),
            ("logo", &self.repositories.logo),
        ] {
            ensure!(
                is_full_commit(value),
                "repositories.{name} must be a full 40-character lowercase Git commit"
            );
        }

        ensure!(
            (1..=crate::manifest::MAX_APK_VERSION_CODE).contains(&self.android.version_code),
            "android.versionCode must be in 1..={}",
            crate::manifest::MAX_APK_VERSION_CODE
        );

        for (name, value) in [
            ("installerProtocol", self.compatibility.installer_protocol),
            ("operationProtocol", self.compatibility.operation_protocol),
            ("appDaemonProtocol", self.compatibility.app_daemon_protocol),
            (
                "agentCatalogSchema",
                self.compatibility.agent_catalog_schema,
            ),
            ("agentEntryAbi", self.compatibility.agent_entry_abi),
            ("implantAbi", self.compatibility.implant_abi),
        ] {
            ensure!(value > 0, "compatibility.{name} must be positive");
        }

        for (kind, gates) in [
            ("host", &self.gates.host),
            ("emulator", &self.gates.emulator),
            ("device", &self.gates.device),
            ("vehicle", &self.gates.vehicle),
        ] {
            ensure!(
                !gates.is_empty(),
                "gates.{kind} must name at least one required gate"
            );
            for gate in gates {
                ensure!(
                    !gate.trim().is_empty() && gate == gate.trim(),
                    "gates.{kind} contains an empty or non-canonical name"
                );
            }
        }
        Ok(())
    }

    pub fn validate_apk_version_code(&self, observed: u64) -> Result<()> {
        ensure!(
            observed == self.android.version_code,
            "APK versionCode {observed} differs from source definition android.versionCode {}",
            self.android.version_code
        );
        Ok(())
    }
}

fn canonical_version(value: &str, field: &str) -> Result<Version> {
    let parsed = Version::parse(value).with_context(|| format!("{field} must be strict SemVer"))?;
    ensure!(
        parsed.to_string() == value,
        "{field} must use canonical SemVer spelling"
    );
    Ok(parsed)
}

pub(crate) fn is_full_commit(value: &str) -> bool {
    value.len() == 40
        && value
            .bytes()
            .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::PathBuf;

    fn source(extra: &str) -> String {
        format!(
            r#"schema = 1
release = "1.0.0-beta1"
channel = "beta"
{extra}
[repositories]
app = "1111111111111111111111111111111111111111"
agents = "2222222222222222222222222222222222222222"
inject = "3333333333333333333333333333333333333333"
installer = "4444444444444444444444444444444444444444"
logo = "6666666666666666666666666666666666666666"

[android]
versionCode = 1000006

[compatibility]
installerProtocol = 1
operationProtocol = 1
appDaemonProtocol = 1
agentCatalogSchema = 1
agentEntryAbi = 1
implantAbi = 1

[gates]
host = ["component-checks"]
emulator = ["android-9-arm64"]
device = ["android-9-root"]
vehicle = ["free-09-6.0.3"]
"#
        )
    }

    #[test]
    fn accepts_full_commits_without_manual_artifact_mode() {
        let definition: Definition = toml::from_str(&source("")).unwrap();
        definition
            .validate(&PathBuf::from("1.0.0-beta1.toml"))
            .unwrap();
    }

    #[test]
    fn source_definition_excludes_retired_license_repository() {
        let valid = source("");
        let definition: Definition = toml::from_str(&valid).unwrap();
        definition
            .validate(&PathBuf::from("1.0.0-beta1.toml"))
            .unwrap();
        let retired = valid.replace(
            "[repositories]",
            "[repositories]\nlicense = \"5555555555555555555555555555555555555555\"",
        );
        let error = toml::from_str::<Definition>(&retired).unwrap_err();
        assert!(error.to_string().contains("unknown field `license`"));
    }

    #[test]
    fn rejects_manual_build_or_reuse_mode() {
        let error = toml::from_str::<Definition>(&source("mode = \"build\"\n")).unwrap_err();
        assert!(error.to_string().contains("unknown field `mode`"));
    }

    #[test]
    fn authoring_input_bypasses_only_the_filename_rule() {
        // Same bytes: valid under any filename as authoring input, valid as a
        // resolved snapshot only under <release>.toml.
        let directory = std::env::temp_dir().join("voboost-definition-authoring-test");
        fs::create_dir_all(&directory).unwrap();
        let authoring = directory.join("version.toml");
        fs::write(&authoring, source("")).unwrap();
        assert!(Definition::load_authoring(&authoring).is_ok());
        let resolved = directory.join("1.0.0-beta1.toml");
        fs::write(&resolved, source("")).unwrap();
        assert!(Definition::load(&resolved).is_ok());
        let misnamed = directory.join("1.0.0-beta2.toml");
        fs::write(&misnamed, source("")).unwrap();
        assert!(Definition::load(&misnamed).is_err());
        assert!(Definition::load_authoring(&misnamed).is_ok());
        let _ = fs::remove_dir_all(&directory);
    }

    #[test]
    fn rejects_short_commit() {
        let value = source("").replace(&"1".repeat(40), "deadbeef");
        let definition: Definition = toml::from_str(&value).unwrap();
        assert!(
            definition
                .validate(&PathBuf::from("1.0.0-beta1.toml"))
                .is_err()
        );
    }

    #[test]
    fn source_definition_conformance_rejects_every_bounded_field_class() {
        let path = PathBuf::from("1.0.0-beta1.toml");
        let definition: Definition = toml::from_str(&source("")).unwrap();
        definition.validate(&path).unwrap();

        let mut invalid = definition.clone();
        invalid.schema = 2;
        assert!(invalid.validate(&path).is_err());

        let mut invalid = definition.clone();
        invalid.channel = "stable".into();
        assert!(invalid.validate(&path).is_err());

        let mut invalid = definition.clone();
        invalid.base_release = Some(invalid.release.clone());
        assert!(invalid.validate(&path).is_err());

        assert!(
            definition
                .validate(&PathBuf::from("different.toml"))
                .is_err()
        );

        let mut invalid = definition.clone();
        invalid.compatibility.implant_abi = 0;
        assert!(invalid.validate(&path).is_err());

        let mut invalid = definition.clone();
        invalid.android.version_code = 0;
        assert!(invalid.validate(&path).is_err());

        definition.validate_apk_version_code(1_000_006).unwrap();
        assert!(definition.validate_apk_version_code(1_000_007).is_err());

        for kind in 0..4 {
            let mut invalid = definition.clone();
            match kind {
                0 => invalid.gates.host.clear(),
                1 => invalid.gates.emulator.clear(),
                2 => invalid.gates.device.clear(),
                3 => invalid.gates.vehicle.clear(),
                _ => unreachable!(),
            }
            assert!(invalid.validate(&path).is_err());
        }
    }
}
