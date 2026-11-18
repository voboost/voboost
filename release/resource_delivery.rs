//! Producer-only resource inspection with existing pinned Android tools and ZIP reader.
use crate::manifest::{ApkEvidence, ResourceFile, ResourceMetadata, identity};
use anyhow::{Context, Result, ensure};
use serde_json::Value;
use sha2::{Digest, Sha256};
use std::{collections::BTreeSet, fs, io::Cursor, path::Path};
const ANDROID: &str = "http://schemas.android.com/apk/res/android:";
pub fn inspect(
    apk: &Path,
    ledger: &Path,
    sdk: &Path,
    source_commit: &str,
    expected_cert: &str,
) -> Result<(ApkEvidence, ResourceMetadata)> {
    let (size, digest) = identity(apk)?;
    ensure!(size > 0 && size <= 16 * 1024 * 1024, "resource APK bound");
    let (ledger_size, ledger_hash) = identity(ledger)?;
    ensure!(
        ledger_size > 0 && ledger_size <= 1024 * 1024,
        "resource ledger bound"
    );
    let ledger_bytes = fs::read(ledger)?;
    ensure!(
        hex::encode(Sha256::digest(&ledger_bytes)) == ledger_hash,
        "ledger changed during inspection"
    );
    let doc: Value = serde_json::from_slice(&ledger_bytes)?;
    let basename = |path: &Path| -> Result<String> {
        Ok(path.file_name().and_then(|name| name.to_str()).context("resource basename")?.into())
    };
    let (resource, ui_names) = validate_ledger(
        &doc,
        &basename(apk)?,
        ResourceFile { file: basename(ledger)?, size: ledger_size, sha256: ledger_hash.clone() },
    )?;
    let observed = crate::verify::inspect_apk_identity(apk, sdk, source_commit)?;
    ensure!(
        observed.certificate_sha256 == expected_cert
            && observed.package_name == resource.overlay_package
            && observed.sha256 == digest
            && observed.size == size,
        "resource APK signer/package/bytes differ"
    );
    let blob = fs::read(apk)?;
    let mut archive = zip::ZipArchive::new(Cursor::new(&blob))?;
    ensure!(
        !archive.is_empty() && archive.len() <= 1024,
        "resource ZIP entry count"
    );
    let mut entries = BTreeSet::new();
    let mut xml_count = 0;
    let mut arsc_count = 0;
    for i in 0..archive.len() {
        let e = archive.by_index(i)?;
        let n = e.name();
        ensure!(entries.insert(n.to_owned()), "duplicate resource ZIP entry");
        ensure!(
            !n.starts_with('/')
                && !n.contains('\\')
                && !n.split('/').any(|s| s == ".." || s == "."),
            "resource ZIP path"
        );
        if n == "AndroidManifest.xml" {
            xml_count += 1;
            ensure!(
                e.compression() == zip::CompressionMethod::Stored
                    && e.size() > 0
                    && e.size() <= 256 * 1024,
                "resource manifest must be bounded STORED"
            );
        } else if n == "resources.arsc" {
            arsc_count += 1;
        } else {
            ensure!(
                n.starts_with("res/") || n.starts_with("META-INF/"),
                "resource APK has code or unsupported entry"
            );
        }
    }
    ensure!(
        xml_count == 1 && arsc_count == 1,
        "resource ZIP manifest/resources missing"
    );
    let table = crate::verify::resource_table(apk, sdk)?;
    validate_resource_table(&table, &ui_names)?;
    let xml = crate::verify::resource_xmltree(apk, sdk)?;
    validate_xmltree(&xml, &resource.target_package)?;
    ensure!(
        identity(apk)? == (size, digest) && identity(ledger)? == (ledger_size, ledger_hash.clone()),
        "resource inputs changed"
    );
    Ok((observed, resource))
}

fn validate_ledger<'a>(
    doc: &'a Value,
    apk_file: &str,
    ledger: ResourceFile,
) -> Result<(ResourceMetadata, BTreeSet<&'a str>)> {
    let stem = apk_file.strip_suffix(".apk").context("resource APK suffix")?;
    ensure!(ledger.file.strip_suffix(".json") == Some(stem), "resource ledger basename differs");
    let english_packages = crate::manifest::english_resource_packages(apk_file);
    let (owner, locale, scope) = if english_packages.is_some() {
        ("i18n-en", "en", "english-delta")
    } else {
        ensure!(stem.starts_with("resource-i18n-ru-"), "resource APK owner unsupported");
        ("i18n-ru", "ru", "complete-ui-owner")
    };
    ensure!(doc["schema"] == 1 && doc["scope"] == scope, "resource ledger unsupported coverage");
    let target = doc["targetPackage"]
        .as_str()
        .context("ledger target missing")?;
    let package = doc["overlayPackage"]
        .as_str()
        .context("ledger package missing")?;
    if let Some((expected_target, expected_overlay)) = english_packages {
        ensure!((target, package) == (expected_target, expected_overlay), "English resource tuple differs");
    }
    let resources = doc["resources"]
        .as_array()
        .context("ledger resources missing")?;
    let preserved = doc["preservedResources"]
        .as_array()
        .context("ledger preserved resources missing")?;
    ensure!(
        !resources.is_empty() && resources.len() + preserved.len() <= 8192,
        "ledger resource count"
    );
    let mut names = BTreeSet::new();
    let mut ru = 0;
    let mut en = 0;
    for r in resources {
        let n = r["resource"].as_str().context("ledger resource missing")?;
        ensure!(names.insert(n), "duplicate ledger resource");
        match r["source"].as_str() {
            Some("ru") => ru += 1,
            Some("en") => en += 1,
            _ => anyhow::bail!("ledger source not RU or verified EN"),
        }
    }
    let ui_names = names.clone();
    if english_packages.is_some() {
        ensure!(ru == 0 && en <= 64, "English resource ledger must contain only bounded correction rows");
        let readback_keys_match = |value: &Value| {
            value.as_object().is_some_and(|map| {
                map.keys().map(String::as_str).eq(ui_names.iter().copied())
                    && map.values().all(Value::is_string)
            })
        };
        ensure!(
            readback_keys_match(&doc["nativeReadback"])
                && readback_keys_match(&doc["expectedReadback"])
                && doc["expectedEnglishReadback"] == doc["expectedReadback"],
            "English resource readback keys differ"
        );
        voboost_contract_core::resource_compatibility::validate(&doc["compatibility"], target, &ui_names)
            .map_err(anyhow::Error::msg)?;
    } else {
        validate_probe_contract(&doc["compatibility"], target, &ui_names)?;
    }
    for r in preserved {
        let n = r["resource"]
            .as_str()
            .context("preserved resource missing")?;
        ensure!(
            names.insert(n) && r["action"] == "preserve-oem",
            "preserved resource malformed"
        );
    }
    ensure!(
        doc["russianResources"].as_u64() == Some(ru)
            && doc["englishFallbackResources"].as_u64() == Some(en),
        "ledger counts differ"
    );
    Ok((
        ResourceMetadata {
            owner: owner.into(),
            locale: locale.into(),
            target_package: target.into(),
            compatibility_schema: 1,
            overlay_package: package.into(),
            coverage_ledger: ledger,
        },
        ui_names,
    ))
}

pub fn validate_resource_table(table: &str, expected: &BTreeSet<&str>) -> Result<()> {
    let mut actual = BTreeSet::new();
    for line in table.lines() {
        if let Some(row) = line.trim().strip_prefix("resource ") {
            let fields = row.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 2 && fields[0].starts_with("0x") && actual.insert(fields[1]),
                "ambiguous compiled resource table"
            );
        }
    }
    ensure!(
        &actual == expected,
        "compiled resource table differs from complete coverage ledger"
    );
    Ok(())
}

pub fn validate_xmltree(xml: &str, target: &str) -> Result<()> {
    let mut manifest = 0;
    let mut app = 0;
    let mut overlay = 0;
    let mut code = 0;
    let mut dynamic = 0;
    let mut target_count = 0;
    let mut current = "";
    for line in xml.lines() {
        let l = line.trim();
        if let Some(e) = l.strip_prefix("E: ") {
            let node = e.split(' ').next().unwrap_or("");
            let indent = line.len() - line.trim_start().len();
            ensure!(
                if node == "manifest" {
                    indent == 2
                } else {
                    indent == 6
                },
                "resource XML node nesting rejected"
            );
            current = node;
            match node {
                "manifest" => manifest += 1,
                "application" => app += 1,
                "overlay" => overlay += 1,
                "uses-sdk" => {}
                _ => anyhow::bail!("resource manifest contains component or permission"),
            }
        }
        if l.contains(":hasCode(") {
            ensure!(current == "application", "hasCode owner rejected");
            ensure!(
                l == format!("A: {ANDROID}hasCode(0x0101000c)=false"),
                "resource hasCode must be explicit false"
            );
            code += 1;
        }
        if l.contains(":isStatic(") {
            ensure!(current == "overlay", "isStatic owner rejected");
            ensure!(
                l == format!("A: {ANDROID}isStatic(0x0101055a)=false"),
                "resource overlay must be dynamic"
            );
            dynamic += 1;
        }
        if l.contains(":targetPackage(") {
            ensure!(current == "overlay", "targetPackage owner rejected");
            ensure!(
                l == format!(
                    "A: {ANDROID}targetPackage(0x01010021)=\"{target}\" (Raw: \"{target}\")"
                ),
                "resource target package mismatch"
            );
            target_count += 1;
        }
    }
    ensure!(
        (manifest, app, overlay, code, dynamic, target_count) == (1, 1, 1, 1, 1, 1),
        "resource manifest shape/attributes ambiguous"
    );
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn compiled_resource_coverage_is_exact() {
        let expected = BTreeSet::from(["string/title", "array/weeks"]);
        validate_resource_table(
            " resource 0x7f010000 string/title\n resource 0x7f020000 array/weeks\n",
            &expected,
        )
        .unwrap();
        for table in [
            "resource 0x7f010000 string/title\n",
            "resource 0x7f010000 string/title\nresource 0x7f010001 string/foreign\n",
            "resource 0x7f010000 string/title\nresource 0x7f010000 string/title\n",
        ] {
            assert!(validate_resource_table(table, &expected).is_err());
        }
    }
    const XML: &str = r#"N: android=http://schemas.android.com/apk/res/android (line=2)
  E: manifest (line=2)
    A: http://schemas.android.com/apk/res/android:versionCode(0x0101021b)=1
    A: http://schemas.android.com/apk/res/android:compileSdkVersion(0x01010572)=34
    A: http://schemas.android.com/apk/res/android:compileSdkVersionCodename(0x01010573)="14" (Raw: "14")
    A: package="ru.voboost.translation.setting" (Raw: "ru.voboost.translation.setting")
    A: platformBuildVersionCode=34
    A: platformBuildVersionName=14
      E: overlay (line=3)
        A: http://schemas.android.com/apk/res/android:priority(0x0101001c)=1
        A: http://schemas.android.com/apk/res/android:targetPackage(0x01010021)="com.qinggan.app.setting" (Raw: "com.qinggan.app.setting")
        A: http://schemas.android.com/apk/res/android:isStatic(0x0101055a)=false
      E: application (line=4)
        A: http://schemas.android.com/apk/res/android:hasCode(0x0101000c)=false
"#;
    #[test]
    fn owned_aapt2_xmltree_is_resource_only() {
        validate_xmltree(XML, "com.qinggan.app.setting").unwrap();
    }
    #[test]
    fn reject_executable_ambiguous_or_nested_manifests() {
        for bad in [
            XML.replace("hasCode(0x0101000c)=false", "hasCode(0x0101000c)=true"),
            XML.replace("      E: application", "          E: application"),
            XML.to_owned() + "      E: activity (line=5)\n",
            XML.replace("isStatic(0x0101055a)=false", "isStatic(0x0101055a)=true"),
            XML.replace("targetPackage(0x01010021)", "targetPackage(0x01010022)"),
        ] {
            assert!(validate_xmltree(&bad, "com.qinggan.app.setting").is_err());
        }
    }
}

/// A signed probe contract is data to check against resources, never an APK/build identity.
pub fn validate_probe_contract(
    value: &Value,
    target: &str,
    expected: &BTreeSet<&str>,
) -> Result<()> {
    let object = value
        .as_object()
        .context("resource compatibility probe object missing")?;
    ensure!(
        object.len() == 3 && value["schemaVersion"] == 1 && value["targetPackage"] == target,
        "resource compatibility schema/target invalid"
    );
    let rows = value["resources"]
        .as_array()
        .context("resource probes missing")?;
    ensure!(
        !rows.is_empty() && rows.len() <= 8192,
        "resource probe count bound"
    );
    let mut names = BTreeSet::new();
    for row in rows {
        let r = row.as_object().context("resource probe row invalid")?;
        ensure!(
            r.keys().all(|k| matches!(
                k.as_str(),
                "resource" | "type" | "format" | "formatted" | "items"
            )),
            "unknown resource probe member"
        );
        let name = row["resource"]
            .as_str()
            .context("resource probe name missing")?;
        ensure!(names.insert(name), "duplicate resource probe");
        let kind = row["type"]
            .as_str()
            .context("resource probe type missing")?;
        let suffix = name
            .strip_prefix(&format!("{kind}/"))
            .context("resource probe type/name differs")?;
        ensure!(
            !suffix.is_empty()
                && matches!(suffix.as_bytes()[0],b'a'..=b'z'|b'A'..=b'Z'|b'_')
                && suffix
                    .bytes()
                    .all(|b| b.is_ascii_alphanumeric() || matches!(b, b'_' | b'.')),
            "resource probe name invalid"
        );
        match kind {
            "string" => {
                ensure!(row.get("items").is_none(), "string probe has items");
                validate_format_probe(row)?;
            }
            "array" | "plurals" => {
                ensure!(
                    row.get("format").is_none() && row.get("formatted").is_none(),
                    "container format must belong to items"
                );
                let items = row["items"]
                    .as_array()
                    .context("resource probe items missing")?;
                ensure!(items.len() <= 4096, "resource item probe bound");
                let mut quantities = BTreeSet::new();
                for item in items {
                    let o = item.as_object().context("item probe invalid")?;
                    ensure!(
                        o.keys()
                            .all(|k| matches!(k.as_str(), "format" | "formatted")
                                || (kind == "plurals" && k == "quantity")),
                        "unknown item probe member"
                    );
                    if kind == "plurals" {
                        let q = item["quantity"]
                            .as_str()
                            .context("plural quantity missing")?;
                        ensure!(
                            matches!(q, "zero" | "one" | "two" | "few" | "many" | "other")
                                && quantities.insert(q),
                            "plural quantity invalid/duplicate"
                        );
                    }
                    validate_format_probe(item)?;
                }
                if kind == "plurals" {
                    ensure!(quantities.contains("other"), "plural probe needs other");
                }
            }
            _ => anyhow::bail!("unsupported resource probe type"),
        }
    }
    ensure!(
        &names == expected,
        "resource probes differ from coverage resource set"
    );
    Ok(())
}
fn validate_format_probe(row: &Value) -> Result<()> {
    ensure!(
        row.get("formatted").is_none() || row["formatted"] == false,
        "formatted override must be false"
    );
    let args = row["format"]
        .as_array()
        .context("resource format probe missing")?;
    ensure!(args.len() <= 256, "resource format probe count");
    let mut previous = None;
    for arg in args {
        let text = arg.as_str().context("resource format argument invalid")?;
        let (index, kind) = text
            .split_once(':')
            .context("resource format argument syntax")?;
        let number = index
            .parse::<u16>()
            .ok()
            .filter(|n| *n > 0)
            .context("resource format argument index")?;
        ensure!(
            number.to_string() == index
                && ((kind.len() == 1 && kind.as_bytes()[0].is_ascii_alphabetic())
                    || (kind.len() == 2
                        && matches!(kind.as_bytes()[0], b't' | b'T')
                        && kind.as_bytes()[1].is_ascii_alphabetic())),
            "resource format argument syntax"
        );
        let key = (number, kind);
        ensure!(
            previous.map(|p: (u16, &str)| p <= key).unwrap_or(true),
            "resource format probe not sorted"
        );
        previous = Some(key);
    }
    Ok(())
}

#[cfg(test)]
mod probe_format_order_tests {
    use super::*;
    #[test]
    fn format_order_uses_numeric_index_and_conversion() {
        assert!(validate_format_probe(&serde_json::json!({"format":["2:s","10:d"]})).is_ok());
        assert!(validate_format_probe(&serde_json::json!({"format":["10:d","2:s"]})).is_err());
        assert!(validate_format_probe(&serde_json::json!({"format":["2:d","2:s"]})).is_ok());
        assert!(validate_format_probe(&serde_json::json!({"format":["2:s","2:d"]})).is_err());
    }
    #[test]
    fn format_indices_are_canonical_decimal() {
        for v in ["02:s", "+2:s", "0:s", "65536:s"] {
            assert!(validate_format_probe(&serde_json::json!({"format":[v]})).is_err());
        }
    }
}

#[cfg(test)]
mod probe_contract_tests {
    use super::*;
    use serde_json::json;
    #[test]
    fn probe_contract_binds_concrete_resources_and_formats() {
        let expected = BTreeSet::from(["string/title", "array/items", "plurals/count"]);
        let value = json!({"schemaVersion":1,"targetPackage":"com.qinggan.app.setting","resources":[{"resource":"string/title","type":"string","format":["2:s","10:d"]},{"resource":"array/items","type":"array","items":[{"format":[]}]},{"resource":"plurals/count","type":"plurals","items":[{"quantity":"other","format":["1:d"]}]}]});
        validate_probe_contract(&value, "com.qinggan.app.setting", &expected).unwrap();
        for change in 0..6 {
            let mut v = value.clone();
            match change {
                0 => v["schemaVersion"] = json!(2),
                1 => v["targetPackage"] = json!("another.package"),
                2 => v["resources"][0]["type"] = json!("array"),
                3 => v["resources"][0]["format"] = json!(["10:d", "2:s"]),
                4 => v["resources"][0]["resource"] = json!("string/missing"),
                _ => v["resources"][2]["items"][0]["quantity"] = json!("unsupported"),
            };
            assert!(validate_probe_contract(&v, "com.qinggan.app.setting", &expected).is_err());
        }
    }
}
