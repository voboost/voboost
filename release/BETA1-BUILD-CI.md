# Beta1 builds with GitHub Actions

`.github/workflows/beta1-build.yml` builds fresh machine artifacts from source: a universal macOS DMG, portable Windows EXE and Linux x86-64 CLI tar.bz2. It does not publish a GitHub Release or run an emulator. Vehicle acceptance remains separate.

## Commit preparation

Commit the components, logo and codestyle first. The archived license repository is not a build input and has no source pin. From the product repository run:

```sh
node tools/release/prepare-source-pins.ts /absolute/path/to/workspace --write
```

This updates both `release/version.toml` and `release/1.0.0-beta1.toml` to exact dependency HEADs. Retain the printed codestyle SHA for the separate workflow input. Commit the product last. All seven source commits (product, four components, logo and codestyle) must be available on GitHub before dispatch. Preparing local commits does not authorize push or dispatch. Dispatch is restricted to `main`; component checkouts use the source definition, never branch tips.

## GitHub settings

Create the protected `beta1-build` environment, restrict it to `main`, and require approval by a trusted maintainer. Import the new CI-only authority as base64 secrets without line breaks:

| Secret | Contents |
| --- | --- |
| `VOBOOST_BETA1_AGENTS_SIGNING_KEY` | Raw 32-byte agent catalog Ed25519 seed |
| `VOBOOST_BETA1_PRODUCT_SIGNING_KEY` | Raw 32-byte product seed |
| `VOBOOST_BETA1_NATIVE_EXECUTABLE_SIGNING_KEY` | Raw 32-byte native executable seed |
| `VOBOOST_BETA1_RESTORE_SIGNING_KEY` | Raw 32-byte restore seed |
| `VOBOOST_BETA1_ANDROID_KEYSTORE_BASE64` | New CI-only `app.jks` bytes |

The four Ed25519 keys must differ. `release/beta1-build-public-roots.json` pins their public keys and the Android certificate SHA-256. Before writing Ed25519 seeds, the helper compares all four derived roots and the actual JKS certificate against these pins; a mismatch stops the build and removes its temporary directory. The builder authority format requires Android alias `voboost` and store/key password `changeit`; this build entrypoint currently does not support another password. Keys are created separately for CI; their private local copies are deleted after transfer to GitHub. The workflow does not generate keys. Private material is excluded from source and artifacts and removed by an `always()` cleanup step.

Public repositories use the default GitHub token. For private repositories or submodules, add repository secret `VOBOOST_SOURCE_READ_TOKEN` with Contents read access limited to the required repositories and submodules. Packaging jobs outside the environment also need this token. Enable GitHub Actions and the pinned Actions used by the workflow. Workflow permissions are limited to `contents: read`; no publication permission is needed.

## Current setup and migration from development builds

On 20 October 2026, `voboost/voboost → beta1-build` was configured for `main` with required approval by `vithar` and five new CI-only secrets. `beta1-build-public-roots.json` records the public roots and Android certificate fingerprint. Temporary private files and discovered local copies of the retired release authority were removed. Do not recreate or import the previous development keys.

DMG242 remains a verified package signed with the previous development authority. The new CI authority also changes the Android certificate and product trust roots: fully uninstall the development version using its previous installer and wait for verified cleanup before installing the first CI package. An OTA migration between these authorities is not claimed.

## Dispatch after commit and push

Open Actions → **Beta1 build artifacts** → Run workflow, select `main`, and supply the full codestyle SHA. Approve the environment if configured. Preparing this workflow does not dispatch it.

The signing job invokes `tools/release/build-closure.ts` in `exact-source` mode, verifies signatures, embedded public roots, versions and unchanged sources. Separate macOS, Windows and Linux jobs receive the closure, public roots and signed role fixtures without private keys. `voboost-installer/src/build/ci-package.ts` owns packaging and reopening final files.

Download `beta1-build-macos`, `beta1-build-windows` and `beta1-build-linux`. Corresponding source is verified as an internal build input; no source companion is exported beside the installer. The macOS DMG contains no source archives or source trees; readable licenses and notices remain in the application. Diagnostic artifact `beta1-build-inputs` contains the closure, source SHAs, inventories, APK evidence and toolchain metadata. Retention is 14 days. The first actual run after push and secret configuration must confirm CI execution; local workflow checks cannot establish that result.

## Separate publication

The separate publication path uses `beta1-ci.yml`, `beta1-evidence-ingest.yml`, `beta1-publication.yml` and `1.0.0-beta1-trust.json`. Their production trust and acceptance requirements still apply to their publication process. The build-only workflow uses the separate CI-only authority and does not turn unprovisioned production trust into permission to publish.
