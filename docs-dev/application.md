# Android application

**English** | [Русский](application.ru.md)

The Android application lives in the sibling [`voboost-app`](../../voboost-app) repository. It builds one headless `voboost-app.apk` from root `src/`; it does not contain agent source, `agents.json`, agent DEX, or OEM UI resources.

## Boundary

The application is the only network courier for OTA. It may read a signed `channel.json`, download and preliminarily verify one immutable `voboost.json`, stream changed artifacts into app-private candidate storage, update its own APK through PackageInstaller, and submit a narrow candidate proposal to the daemon.

The application does not choose applicable agents, publish a generation, write permanent root-owned files, interpret native update roles, or activate a release. `voboost-daemon` independently verifies the complete manifest, compatibility, artifact bytes, agent catalog, vehicle facts, and selected generation before root mutation.

## Scheduling and transport

[`BootReceiver`](../../voboost-app/src/main/java/ru/voboost/BootReceiver.kt) and [`PackageReplacedReceiver`](../../voboost-app/src/main/java/ru/voboost/PackageReplacedReceiver.kt) ask [`OtaScheduler`](../../voboost-app/src/main/java/ru/voboost/OtaScheduler.kt) to schedule one persisted JobScheduler job. [`OtaJobService`](../../voboost-app/src/main/java/ru/voboost/OtaJobService.kt) owns the bounded check, resumable download, descriptor proposal, and retry decision.

The app and daemon communicate through the abstract stream socket `voboost.ota.v1`. The app passes the manifest, signature, and changed artifacts as retained file descriptors with exact length framing. The daemon authenticates the peer and re-verifies every authority before accepting a proposal.

The OTA socket checks the kernel-supplied UID and PID, requires an exclusive ordinary application UID for `ru.voboost` in `packages.list`, and verifies the installed APK against the active or pending signed product. It does not read the process name from `/proc`. Log transport retains its separate peer checks.

## OTA sequence

1. JobScheduler starts a bounded check when the network constraint is satisfied.
2. The app verifies exact signed `channel.json` bytes and rejects rollback.
3. The app downloads and verifies the referenced `voboost.json` size, SHA-256, and signature.
4. The app asks the daemon only for the active manifest identity; it does not ask for applicability.
5. The app downloads every artifact whose filename, role, size, or SHA-256 changed.
6. It publishes a complete app-private candidate with readiness written last.
7. It submits one descriptor-bound request; the daemon reconstructs and verifies the complete candidate from changed and installed bytes.
8. PackageInstaller replaces `voboost-app.apk` only when its SHA-256 changed, and the app resumes the same transaction after replacement or reboot.

## APK identity

Product authoring opens the exact final APK with `aapt2` and `apksigner` from pinned Android build-tools `34.0.0`, requires one verified v2/v3 signer, extracts package/version/certificate identity, and hashes the same file. Product schema 1 accepts `versionCode` only in `1..=2100000000`, and beta1 requires the observed value to equal `android.versionCode` in the selected source definition. A retained APK evidence document is output for audit only; the compositor freshly extracts every field and rejects any substitution before writing the app row of `voboost.json`.

## Storage and uninstall

Private downloads, PackageInstaller sharing, bounded log buffering, and resume state stay in the Android sandbox. The app has no writable root subtree and never writes `/data/voboost` directly.

The headless APK has no Activity or uninstall confirmation entry point. The injected Settings interface owns the user confirmation; the root daemon owns request admission, uninstall execution, and final absence verification.

## Verification

Release checks cover host tests, Android lint, minified APK inventory, exact package/version/signer inspection, download and resume behavior, socket framing, peer authentication, PackageInstaller continuation, and the applicable emulator, device, and vehicle gates from the product source definition.

Canonical app contracts are in [`voboost-app/openspec/specs`](../../voboost-app/openspec/specs). Product manifest and publication contracts are in [`product-release`](../openspec/specs/product-release/spec.md).

## Related pages

- [Architecture](architecture.md)
- [Updates](updates.md)
- [Contracts](contracts.md)
- [Storage](storage.md)
- [Security](security.md)

## Component implementation and development

`voboost-app` builds the headless, unprivileged Android OTA courier for Voboost.

The app verifies signed `channel.json` and `voboost.json`, downloads every changed artifact into one resumable app-private transaction, and submits an opaque typed candidate. It opens the descriptor-bound PackageInstaller provider only for the daemon's exact `install-apk` continuation status; `pending` and `accepted` never reopen it. It does not compute agent applicability or native activation order. Agent selection, injection, root mutation, Product SemVer, and publication remain owned by sibling repositories.

`JobScheduler` owns periodic checks and continuation. The app talks to `voboost-daemon` through the abstract stream socket `voboost.ota.v1`, passes the manifest, signature, and changed artifacts as retained file descriptors in canonical order, and resumes the same transaction after package replacement or reboot. The daemon authenticates the peer and independently verifies every authority before root mutation.

Beta1 CI prepares its immutable signing input with `VOBOOST_EMBEDDED_PRODUCT_PUBLIC_KEY_PEM=<source-pinned-product.pem> scripts/prepare-beta1-unsigned.sh <output-apk>`. The adapter validates an exact Ed25519 SPKI, packages that root without mutating source, verifies the packaged raw root, performs two clean minified builds, requires byte-identical unsigned APKs, rejects stale output, signing inputs, and debug mode, and never receives a keystore.


The APK courier verifies the bytes while copying and rechecks the retained read descriptor before issuing the installer grant. App logging connects before setting the read timeout, authenticates the root peer and bounds the complete exchange. OTA URL construction preserves signed path and query escapes. Uninstall remains entirely on the root side.

### Build

Use JDK 17 and the Android SDK/NDK versions pinned by Gradle.

```bash
./gradlew testUnit
./gradlew check
./gradlew buildRelease
```

After protected signing in a committed checkout, `./gradlew apkEvidence -PvoboostSourceCommit=<exact-head>` writes canonical `voboost-app-evidence.json` beside the APK. This file is non-authoritative input: product release tooling must independently inspect the same APK package, version code, signer certificate, size, and SHA-256 before authoring `voboost.json`.

Protected packaging requires an external signing keystore and an absolute HTTPS `VOBOOST_CHANNEL_URL` ending in `/channel.json`. Signing material and `local.properties` are never repository inputs.

Cluster delivery uses an independent persisted JobScheduler job and a platform-permission-protected wake receiver. It relays only root-leased opaque signed bytes and observations over paired mutual TLS; root and QNX retain signature, generation and replay admission. Host tests do not establish Android descriptor interoperability or QNX runtime readiness.

Cluster delivery exports verified read-only copies through the authenticated OTA socket. Copies receive the dedicated `voboost_cluster_lease_file` label and are unlinked before descriptor transfer. The Android courier may use received daemon descriptors and read that type; it cannot open or write those copies or read the original data and generation files. The existing signed payload and lease checks remain in force.
