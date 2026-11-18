# Protected Cluster pairing

**English** | [Русский](installer-cluster-pairing.ru.md)

Pairing is owned by the existing Rust implementation [cluster_pairing.rs](../../voboost-installer/src-tauri/src/cluster_pairing.rs). The obsolete independent host generator has been removed. Production bootstrap calls `cluster_pairing::prepare` and resumes retained protected material through `cluster_pairing::resume`; there is no separate preparation command.

The implementation creates independent per-device Android and QNX signing seeds, a random pair ID and authority epoch, an IPC secret, transport credentials and native configuration. It does not replace product signing authorities. macOS certificate operations use the system `/usr/bin/openssl`; no separately installed OpenSSL or Python runtime is needed.

Output directories have mode 0700 and private files mode 0600. Existing output is preserved; failed generation removes only its newly created output. Credentials must not enter logs or source control. Preparation alone does not prove delivery, startup persistence or physical operation.

Existing Rust tests cover wire/config consistency, independent keys, certificates and PKCS#12, file protection, transient material removal, preservation of existing output, invalid profiles and cleanup after a real crypto failure. Run through the configured installer Cargo environment:

```sh
cargo +1.98.0 test --manifest-path src-tauri/Cargo.toml --offline --lib cluster_pairing::tests
```

Keep `TMPDIR` inside the project and reuse the shared installer Cargo target. Crypto tests require macOS and remove their temporary credentials created beneath `src-tauri`.
