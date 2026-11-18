use crate::manifest::{Manifest, identity};
use crate::verify::verify_closure;
use anyhow::{Context, Result, ensure};
use ed25519_dalek::{Signature, Signer, SigningKey, VerifyingKey};
use std::fs;
use std::path::Path;

const NATIVE_MAGIC: &[u8; 8] = b"VOBOOST\0";
const NATIVE_DOMAIN: &[u8] = b"voboost-native-executable-v1\0";

fn native_role(value: &str) -> Result<&str> {
    ensure!(
        matches!(
            value,
            "daemon" | "inject" | "install" | "restore" | "uninstall" | "selinux"
        ),
        "native executable role is not in the closed role table"
    );
    Ok(value)
}

fn native_transcript(role: &str, elf: &[u8]) -> Result<Vec<u8>> {
    let role = native_role(role)?;
    ensure!(
        elf.starts_with(b"\x7fELF"),
        "native input is not an ELF file"
    );
    ensure!(
        elf.len() <= 64 * 1024 * 1024,
        "native ELF exceeds release bound"
    );
    ensure!(
        elf.len() < 80 || &elf[elf.len() - 80..elf.len() - 72] != NATIVE_MAGIC,
        "native input must be an unsigned ELF prefix"
    );
    let mut transcript = Vec::with_capacity(NATIVE_DOMAIN.len() + role.len() + 9 + elf.len());
    transcript.extend_from_slice(NATIVE_DOMAIN);
    transcript.extend_from_slice(role.as_bytes());
    transcript.push(0);
    transcript.extend_from_slice(&(elf.len() as u64).to_le_bytes());
    transcript.extend_from_slice(elf);
    Ok(transcript)
}

fn parse_signed_native(bytes: &[u8]) -> Result<(&[u8], Signature)> {
    ensure!(bytes.len() >= 84, "signed native executable is too short");
    let trailer = &bytes[bytes.len() - 80..];
    ensure!(
        &trailer[..8] == NATIVE_MAGIC,
        "native signature magic differs"
    );
    let offset = u64::from_le_bytes(trailer[8..16].try_into().unwrap()) as usize;
    ensure!(
        offset == bytes.len() - 80,
        "native signature offset differs"
    );
    ensure!(
        bytes[..offset].starts_with(b"\x7fELF"),
        "signed native prefix is not ELF"
    );
    let signature = Signature::from_slice(&trailer[16..])
        .context("native signature must be raw 64-byte Ed25519")?;
    Ok((&bytes[..offset], signature))
}

pub fn sign_native_executable(
    role: &str,
    private_key: &Path,
    input: &Path,
    output: &Path,
) -> Result<()> {
    let signing = signing_key(private_key)?;
    let elf = fs::read(input).with_context(|| format!("read {}", input.display()))?;
    let transcript = native_transcript(role, &elf)?;
    let signature = signing.sign(&transcript);
    let elf_len = elf.len() as u64;
    let mut signed = elf;
    signed.extend_from_slice(NATIVE_MAGIC);
    signed.extend_from_slice(&elf_len.to_le_bytes());
    signed.extend_from_slice(&signature.to_bytes());
    fs::write(output, signed).with_context(|| format!("write {}", output.display()))
}

pub fn verify_native_executable(role: &str, public_key: &Path, input: &Path) -> Result<()> {
    let public: [u8; 32] = fs::read(public_key)?
        .try_into()
        .map_err(|_| anyhow::anyhow!("public key must be raw 32-byte Ed25519"))?;
    let verifying = VerifyingKey::from_bytes(&public).context("invalid Ed25519 public key")?;
    let bytes = fs::read(input).with_context(|| format!("read {}", input.display()))?;
    let (elf, signature) = parse_signed_native(&bytes)?;
    verifying
        .verify_strict(&native_transcript(role, elf)?, &signature)
        .context("native executable Ed25519 verification failed")
}

pub fn verify_embedded_root(public_key: &Path, input: &Path) -> Result<()> {
    let root: [u8; 32] = fs::read(public_key)?
        .try_into()
        .map_err(|_| anyhow::anyhow!("embedded root must be raw 32-byte Ed25519"))?;
    let bytes = fs::read(input).with_context(|| format!("read {}", input.display()))?;
    let (elf, _) = parse_signed_native(&bytes)?;
    let count = elf
        .windows(root.len())
        .filter(|window| *window == root)
        .count();
    ensure!(
        count == 1,
        "consumer must embed the exact root exactly once"
    );
    Ok(())
}

pub fn verify_binary_embedded_root(
    public_key: &Path,
    input: &Path,
    expected_count: usize,
) -> Result<()> {
    ensure!(
        (1..=4).contains(&expected_count),
        "embedded root count is outside bound"
    );
    let root: [u8; 32] = fs::read(public_key)?
        .try_into()
        .map_err(|_| anyhow::anyhow!("embedded root must be raw 32-byte Ed25519"))?;
    let metadata = fs::symlink_metadata(input)?;
    ensure!(
        metadata.is_file() && metadata.len() <= 512 * 1024 * 1024,
        "consumer binary is unsafe or oversized"
    );
    let bytes = fs::read(input).with_context(|| format!("read {}", input.display()))?;
    let count = bytes
        .windows(root.len())
        .filter(|window| *window == root)
        .count();
    ensure!(
        count == expected_count,
        "consumer embedded root count differs"
    );
    Ok(())
}

pub fn derive_public_key(private_key: &Path, output: &Path) -> Result<()> {
    let signing = signing_key(private_key)?;
    fs::write(output, signing.verifying_key().to_bytes())
        .with_context(|| format!("write {}", output.display()))
}

pub fn sign_file(private_key: &Path, input: &Path, output: &Path) -> Result<()> {
    let signing = signing_key(private_key)?;
    let bytes = fs::read(input).with_context(|| format!("read {}", input.display()))?;
    fs::write(output, signing.sign(&bytes).to_bytes())
        .with_context(|| format!("write {}", output.display()))
}

pub fn verify_file(public_key: &Path, input: &Path, signature: &Path) -> Result<()> {
    let public: [u8; 32] = fs::read(public_key)?
        .try_into()
        .map_err(|_| anyhow::anyhow!("public key must be raw 32-byte Ed25519"))?;
    let verifying = VerifyingKey::from_bytes(&public).context("invalid Ed25519 public key")?;
    let bytes = fs::read(input).with_context(|| format!("read {}", input.display()))?;
    let signature = Signature::from_slice(&fs::read(signature)?)
        .context("signature must be raw 64-byte Ed25519")?;
    verifying
        .verify_strict(&bytes, &signature)
        .context("Ed25519 verification failed")
}

fn signing_key(path: &Path) -> Result<SigningKey> {
    let secret: [u8; 32] = fs::read(path)?
        .try_into()
        .map_err(|_| anyhow::anyhow!("private key must be raw 32-byte Ed25519"))?;
    Ok(SigningKey::from_bytes(&secret))
}

/// Produces a deterministic, local-only publication plan after closure verification.
/// Network upload and GitHub Release mutation deliberately remain outside this lane.
pub fn publication_plan(manifest: &Manifest, closure: &Path) -> Result<String> {
    verify_closure(manifest, closure)?;
    let mut lines = vec![
        format!("release={}", manifest.release_identity),
        "state=verified-local-closure".to_owned(),
        "remoteMutation=not-performed".to_owned(),
        "assets:".to_owned(),
    ];
    for artifact in &manifest.artifacts {
        lines.push(format!(
            "{}  {}  {}",
            artifact.sha256, artifact.size, artifact.file
        ));
    }
    Ok(format!("{}\n", lines.join("\n")))
}

pub fn sha256_sums(manifest: &Manifest, closure: &Path) -> Result<String> {
    verify_closure(manifest, closure)?;
    let mut lines = Vec::new();
    for artifact in &manifest.artifacts {
        let path = closure.join(&artifact.file);
        let (size, sha256) = identity(&path)?;
        ensure!(
            size == artifact.size,
            "size changed after closure verification for {}",
            artifact.file
        );
        lines.push(format!("{sha256}  {}", artifact.file));
    }
    Ok(format!("{}\n", lines.join("\n")))
}

pub fn write_sha256_sums(manifest: &Manifest, closure: &Path, output: &Path) -> Result<()> {
    fs::write(output, sha256_sums(manifest, closure)?)
        .with_context(|| format!("write {}", output.display()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ephemeral_file_signing_is_role_separated_and_rejects_substitution() {
        let root = tempfile::tempdir().unwrap();
        let private = root.path().join("private");
        let public = root.path().join("public");
        let input = root.path().join("input");
        let signature = root.path().join("signature");
        fs::write(&private, [9_u8; 32]).unwrap();
        fs::write(&input, b"exact bytes").unwrap();
        derive_public_key(&private, &public).unwrap();
        sign_file(&private, &input, &signature).unwrap();
        verify_file(&public, &input, &signature).unwrap();
        fs::write(&input, b"substituted bytes").unwrap();
        assert!(verify_file(&public, &input, &signature).is_err());
    }

    #[test]
    fn native_signing_is_product_owned_role_bound_and_root_separated() {
        let root = tempfile::tempdir().unwrap();
        let private = root.path().join("private");
        let public = root.path().join("public");
        let foreign_private = root.path().join("foreign-private");
        let foreign_public = root.path().join("foreign-public");
        fs::write(&private, [7_u8; 32]).unwrap();
        fs::write(&foreign_private, [8_u8; 32]).unwrap();
        derive_public_key(&private, &public).unwrap();
        derive_public_key(&foreign_private, &foreign_public).unwrap();
        let input = root.path().join("input");
        fs::write(
            &input,
            [b"\x7fELF".as_slice(), fs::read(&public).unwrap().as_slice()].concat(),
        )
        .unwrap();
        let roles = ["daemon", "inject", "install", "restore", "uninstall", "selinux"];
        for role in roles {
            let signed = root.path().join(role);
            sign_native_executable(role, &private, &input, &signed).unwrap();
            verify_native_executable(role, &public, &signed).unwrap();
            verify_embedded_root(&public, &signed).unwrap();
            assert!(verify_native_executable(role, &foreign_public, &signed).is_err());
            for other in roles.into_iter().filter(|other| *other != role) {
                assert!(verify_native_executable(other, &public, &signed).is_err());
            }
        }
        assert!(
            sign_native_executable("candidate", &private, &input, &root.path().join("bad"))
                .is_err()
        );
    }

    #[test]
    fn packaged_consumer_root_scan_rejects_missing_duplicate_and_substituted_keys() {
        let root = tempfile::tempdir().unwrap();
        let key = root.path().join("key");
        let foreign = root.path().join("foreign");
        let binary = root.path().join("binary");
        let expected = std::array::from_fn::<_, 32, _>(|index| index as u8);
        let substituted = std::array::from_fn::<_, 32, _>(|index| (31 - index) as u8);
        fs::write(&key, expected).unwrap();
        fs::write(&foreign, substituted).unwrap();
        fs::write(
            &binary,
            [
                b"prefix".as_slice(),
                expected.as_slice(),
                b"suffix".as_slice(),
            ]
            .concat(),
        )
        .unwrap();
        verify_binary_embedded_root(&key, &binary, 1).unwrap();
        assert!(verify_binary_embedded_root(&foreign, &binary, 1).is_err());
        assert!(verify_binary_embedded_root(&key, &binary, 2).is_err());
        fs::write(&binary, [expected.as_slice(), expected.as_slice()].concat()).unwrap();
        verify_binary_embedded_root(&key, &binary, 2).unwrap();
        assert!(verify_binary_embedded_root(&key, &binary, 1).is_err());
    }
}
