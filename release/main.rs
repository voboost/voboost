mod definition;
mod manifest;
mod publish;
mod resource_delivery;
mod verify;
mod wire;

use anyhow::{Context, Result, bail, ensure};
use definition::Definition;
use manifest::{
    ApkEvidence, ReuseDecision, compose, identity, load_signing_key, load_verifying_key,
    produce_restore_pair, write_canonical,
};
use std::env;
use std::fs;
use std::path::{Path, PathBuf};
use verify::{
    Channel, canonical_channel_bytes, read_channel, read_manifest, read_trust_roots,
    verify_closure, verify_product_closure,
};

fn main() {
    if let Err(error) = run(env::args().skip(1).collect()) {
        eprintln!("error: {error:#}");
        std::process::exit(1);
    }
}

fn run(arguments: Vec<String>) -> Result<()> {
    let Some(command) = arguments.first().map(String::as_str) else {
        print_help();
        return Ok(());
    };
    match command {
        "check-definition" => {
            exact_arity(&arguments, 2)?;
            let definition = Definition::load(Path::new(&arguments[1]))?;
            println!("{}: valid source definition", definition.release);
        }
        "compose" => compose_command(&arguments)?,
        "inspect-apk" => inspect_apk_command(&arguments)?,
        "produce-restore" => produce_restore_command(&arguments)?,
        "verify-restore" => {
            exact_arity(&arguments, 5)?;
            let definition_path = Path::new(&arguments[1]);
            let definition = Definition::load(definition_path)?;
            let definition_sha256 = identity(definition_path)?.1;
            let manifest = read_manifest(Path::new(&arguments[2]))?;
            let public = load_verifying_key(Path::new(&arguments[4]))?;
            manifest::verify_restore_pair(
                &definition,
                &definition_sha256,
                &manifest.artifacts,
                Path::new(&arguments[3]),
                &public,
            )?;
        }
        "verify-manifest" => {
            exact_arity(&arguments, 3)?;
            let manifest = read_manifest(Path::new(&arguments[1]))?;
            verify_closure(&manifest, Path::new(&arguments[2]))?;
            println!("{}: exact closure verified", manifest.release_identity);
        }
        "verify-product-closure" => {
            exact_arity(&arguments, 3)?;
            let manifest =
                verify_product_closure(Path::new(&arguments[1]), Path::new(&arguments[2]))?;
            println!(
                "{}: exact signed product closure verified",
                manifest.release_identity
            );
        }
        "write-channel" => write_channel_command(&arguments)?,
        "verify-channel" => {
            exact_arity(&arguments, 2)?;
            let channel = read_channel(Path::new(&arguments[1]))?;
            println!(
                "{} {}: channel document verified",
                channel.channel, channel.release_identity
            );
        }
        "publication-plan" => {
            exact_arity(&arguments, 3)?;
            let manifest = read_manifest(Path::new(&arguments[1]))?;
            print!(
                "{}",
                publish::publication_plan(&manifest, Path::new(&arguments[2]))?
            );
        }
        "derive-public-key" => {
            exact_arity(&arguments, 3)?;
            publish::derive_public_key(Path::new(&arguments[1]), Path::new(&arguments[2]))?;
        }
        "sign-file" => {
            exact_arity(&arguments, 4)?;
            publish::sign_file(
                Path::new(&arguments[1]),
                Path::new(&arguments[2]),
                Path::new(&arguments[3]),
            )?;
        }
        "verify-file" => {
            exact_arity(&arguments, 4)?;
            publish::verify_file(
                Path::new(&arguments[1]),
                Path::new(&arguments[2]),
                Path::new(&arguments[3]),
            )?;
        }
        "sign-native-executable" => {
            exact_arity(&arguments, 5)?;
            publish::sign_native_executable(
                &arguments[1],
                Path::new(&arguments[2]),
                Path::new(&arguments[3]),
                Path::new(&arguments[4]),
            )?;
        }
        "verify-native-executable" => {
            exact_arity(&arguments, 4)?;
            publish::verify_native_executable(
                &arguments[1],
                Path::new(&arguments[2]),
                Path::new(&arguments[3]),
            )?;
        }
        "verify-embedded-root" => {
            exact_arity(&arguments, 3)?;
            publish::verify_embedded_root(Path::new(&arguments[1]), Path::new(&arguments[2]))?;
        }
        "verify-apk-product-root" => {
            exact_arity(&arguments, 3)?;
            verify::verify_apk_embedded_product_root(
                Path::new(&arguments[1]),
                Path::new(&arguments[2]),
            )?;
        }
        "verify-binary-embedded-root" => {
            exact_arity(&arguments, 4)?;
            let count = arguments[3]
                .parse()
                .context("embedded root count must be an integer")?;
            publish::verify_binary_embedded_root(
                Path::new(&arguments[1]),
                Path::new(&arguments[2]),
                count,
            )?;
        }
        "write-public-key-pem" => {
            exact_arity(&arguments, 3)?;
            verify::write_ed25519_public_pem(Path::new(&arguments[1]), Path::new(&arguments[2]))?;
        }
        "check-trust-roots" => {
            exact_arity(&arguments, 2)?;
            let trust = read_trust_roots(Path::new(&arguments[1]))?;
            println!("{}: {} trust roots", trust.release_identity, trust.state);
        }
        "write-sha256sums" => {
            exact_arity(&arguments, 4)?;
            let manifest = read_manifest(Path::new(&arguments[1]))?;
            publish::write_sha256_sums(
                &manifest,
                Path::new(&arguments[2]),
                Path::new(&arguments[3]),
            )?;
        }
        "write-wire-fixtures" => {
            exact_arity(&arguments, 2)?;
            wire::write_fixtures(Path::new(&arguments[1]))?;
        }
        "verify-wire-fixtures" => {
            exact_arity(&arguments, 2)?;
            wire::verify_fixtures(Path::new(&arguments[1]))?;
            println!("wire fixture corpus verified");
        }
        "help" | "--help" | "-h" => print_help(),
        value => bail!("unknown command {value:?}; run voboost-release help"),
    }
    Ok(())
}

fn compose_command(arguments: &[String]) -> Result<()> {
    ensure!(
        arguments.len() == 7 || arguments.len() == 10,
        "usage: compose <definition> <artifact-directory> <android-sdk> <apk-evidence-or-dash> <restore-public-key> <output> [<base-definition> <base-manifest> <base-artifact-directory>]"
    );
    let definition_path = Path::new(&arguments[1]);
    let definition = Definition::load(definition_path)?;
    let definition_sha256 = identity(definition_path)?.1;
    let artifact_directory = Path::new(&arguments[2]);
    let android_sdk = Path::new(&arguments[3]);
    let evidence = (arguments[4] != "-")
        .then(|| ApkEvidence::load(Path::new(&arguments[4])))
        .transpose()?;
    let restore_public_key = load_verifying_key(Path::new(&arguments[5]))?;
    let output = Path::new(&arguments[6]);
    let base_definition;
    let base_manifest;
    let base = if arguments.len() == 10 {
        base_definition = Definition::load(Path::new(&arguments[7]))?;
        base_manifest = read_manifest(Path::new(&arguments[8]))?;
        Some((&base_definition, &base_manifest, Path::new(&arguments[9])))
    } else {
        None
    };
    let (manifest, decisions) = compose(
        &definition,
        &definition_sha256,
        artifact_directory,
        android_sdk,
        evidence.as_ref(),
        &restore_public_key,
        base,
    )?;
    write_canonical(output, &manifest)?;
    for (file, decision) in decisions {
        let label = match decision {
            ReuseDecision::New => "new",
            ReuseDecision::Changed => "changed",
            ReuseDecision::Reused => "reuse-exact-bytes",
            ReuseDecision::Removed => "removed",
        };
        println!("{label}\t{file}");
    }
    Ok(())
}

fn inspect_apk_command(arguments: &[String]) -> Result<()> {
    exact_arity(arguments, 5)?;
    let definition = Definition::load(Path::new(&arguments[1]))?;
    let evidence = verify::inspect_apk(
        &Path::new(&arguments[2]).join("voboost-app.apk"),
        Path::new(&arguments[3]),
        &definition.repositories.app,
    )?;
    definition.validate_apk_version_code(evidence.version_code)?;
    let mut bytes = serde_json::to_vec(&evidence)?;
    bytes.push(b'\n');
    fs::write(&arguments[4], bytes)
        .with_context(|| format!("write APK evidence {}", arguments[4]))?;
    Ok(())
}

fn produce_restore_command(arguments: &[String]) -> Result<()> {
    exact_arity(arguments, 5)?;
    let definition_path = Path::new(&arguments[1]);
    let definition = Definition::load(definition_path)?;
    let definition_sha256 = identity(definition_path)?.1;
    let signing_key = load_signing_key(Path::new(&arguments[4]))?;
    produce_restore_pair(
        &definition,
        &definition_sha256,
        Path::new(&arguments[2]),
        Path::new(&arguments[3]),
        &signing_key,
    )?;
    Ok(())
}

fn write_channel_command(arguments: &[String]) -> Result<()> {
    exact_arity(arguments, 5)?;
    let manifest_path = PathBuf::from(&arguments[1]);
    let manifest = read_manifest(&manifest_path)?;
    let (manifest_size, manifest_sha256) = identity(&manifest_path)?;
    let channel = Channel {
        schema_version: 1,
        channel: arguments[2].clone(),
        release_identity: manifest.release_identity,
        manifest_url: arguments[3].clone(),
        manifest_size,
        manifest_sha256,
    };
    fs::write(&arguments[4], canonical_channel_bytes(&channel)?)
        .with_context(|| format!("write channel document {}", arguments[4]))?;
    Ok(())
}

fn exact_arity(arguments: &[String], expected: usize) -> Result<()> {
    ensure!(
        arguments.len() == expected,
        "wrong number of arguments for {}",
        arguments.first().map(String::as_str).unwrap_or("command")
    );
    Ok(())
}

fn print_help() {
    println!(
        "voboost-release: local product release authoring and verification\n\n\
check-definition <definition>\n\
inspect-apk <definition> <artifact-directory> <android-sdk> <output-evidence>\n\
produce-restore <definition> <artifact-directory> <android-sdk> <restore-private-key>\n\
verify-restore <definition> <manifest> <artifact-directory> <restore-public-key>\n\
compose <definition> <artifact-directory> <android-sdk> <apk-evidence-or-dash> <restore-public-key> <output> [<base-definition> <base-manifest> <base-artifact-directory>]\n\
verify-manifest <manifest> <artifact-directory>\n\
verify-product-closure <closure-directory> <raw-product-public-key>\n\
write-channel <manifest> <channel> <manifest-url> <output>\n\
verify-channel <channel-document>\n\
publication-plan <manifest> <artifact-directory>\n\
write-sha256sums <manifest> <artifact-directory> <output>\n\n\
write-wire-fixtures <output-directory>\n\
verify-wire-fixtures <fixture-directory>\n\n\
derive-public-key <raw-private-key> <raw-public-key>\n\
sign-file <raw-private-key> <input> <raw-signature>\n\
verify-file <raw-public-key> <input> <raw-signature>\n\n\
sign-native-executable <daemon|inject|install|restore|uninstall|selinux> <raw-private-key> <unsigned-elf> <signed-elf>\n\
verify-native-executable <daemon|inject|install|restore|uninstall|selinux> <raw-public-key> <signed-elf>\n\
verify-embedded-root <raw-public-key> <signed-native-elf>\n\
verify-apk-product-root <signed-apk> <raw-product-public-key>\n\
verify-binary-embedded-root <raw-public-key> <consumer-binary> <expected-count>\n\
write-public-key-pem <raw-public-key> <canonical-spki-pem>\n\
check-trust-roots <trust-roots.json>\n\n\
This CLI performs bounded caller-requested local signing but no checkout, component build, upload, Git tag, GitHub Release, or channel publication by itself."
    );
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn help_and_unknown_command_are_bounded() {
        run(vec!["help".into()]).unwrap();
        assert!(run(vec!["publish".into()]).is_err());
    }
}

#[cfg(test)]
mod resource_release_tests;
