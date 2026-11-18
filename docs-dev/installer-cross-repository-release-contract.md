# Cross-repository release contract

**English** | [Русский](installer-cross-repository-release-contract.ru.md)

## Ownership

`voboost` owns the product version, source definition, protected signing, product composition, installer-package coordination, evidence, and publication. `voboost-app`, `voboost-agents`, `voboost-inject`, and `voboost-installer` verify and build their own source but do not publish independent component releases.

The source definition records the exact full commit of every component repository. A branch, short commit, floating tag, local path, or latest artifact cannot identify a release input.

## Component outputs

Each component exposes a non-interactive verification command and a product-neutral unsigned build adapter. The adapter returns only its exact named bytes and does not receive a private key, author product metadata, create a tag or release, upload an artifact, publish a channel, or emit a cross-repository event.

For beta1, `voboost-inject/tools/prepare-beta1-unsigned.sh` exports the ten flat native inputs. `voboost-app` exports one unsigned APK. `voboost-agents` exports `agents.json`, `agent-common.dex`, and every target-specific `agent-*.dex`. `voboost-installer` supplies the tested platform packaging implementation.

## Product composition and trust

Product release tooling signs the exact Android APK, agent catalog, native executable transcripts, restore authorization, and `voboost.json`. The resulting flat closure is verified as a closed set: no missing, extra, duplicate, renamed, nested, differently sized, or digest-mismatched member is accepted.

The long-lived product roots are the Android certificate, product manifest, agent catalog, native executable, and restore roots. Native OTA consumes `voboost.json` directly; there is no second native manifest or native-manifest trust root. The desktop installer embeds only the three roots it directly consumes: product manifest, restore and native executable. Agent-catalog authority remains embedded in the device binaries that verify the catalog.

Private keys exist only in the local ephemeral test build or the protected product signing job. Component repositories, ordinary CI, installer frontend, device storage, and packaged public-root fixtures contain no private key.

## Installer packages

The product pipeline packages the same signed closure into:

```text
voboost-installer-<release>.exe
voboost-installer-<release>-arm64.dmg
voboost-installer-<release>-x86_64.dmg
voboost-installer-<release>.tar.bz2
```

The installer does not rebuild, download, select, or replace product bytes. Windows adds only its ADB executable and two DLLs. macOS links direct ADB without an external ADB executable and ships separate `arm64` and `x86_64` DMGs. Linux adds no ADB; the tar.bz2 contains mode-`0755` `voboost-installer` and pinned readable licenses and notices.

## Evidence and publication

For macOS, the corresponding-source delivery accompanies the DMG as a separate artifact. The package constructor verifies that source delivery against the same signed product manifest before packaging, but the image and application bundle contain no `corresponding-source.tar.gz`, `corresponding-source.json`, or `BUILD-MODIFIED.md`.

Host checks prove source, schemas, deterministic composition, signatures, and package structure. Emulator, device, vehicle, protected CI, and publication remain separate evidence levels.

Beta1 follows this fixed order: local build with ephemeral keys, physical-vehicle verification, clean uninstall proof, approved commits and push, CI build, verification of the exact CI packages on the vehicle, and a separate publication decision. No GitHub Release, release asset, or OTA channel pointer is published before that decision.

## Secretless CI packaging adapter

After product composition, the central product workflow calls `node src/build/ci-package.ts <macos|windows|linux> <bundle-root> <public-inputs> <output>` in the exact installer checkout. The bundle contains `closure/` and `inputs/observation/` from the verified product build. `VOBOOST_PRODUCT_VERSION` must match both the closure and installer source.

The public-input directory contains exactly `product.pem`, `restore.pem`, `native-executable.pem`, `product.fixture.sig`, `restore.fixture.sig`, and `native-executable.fixture.sig`. Product signing creates the fixture signatures; packaging receives no private key. The signed UTF-8 fixture messages are `voboost-role-trust-fixture-v1:release-catalog`, `voboost-role-trust-fixture-v1:restore`, and `voboost-role-trust-fixture-v1:native-executable`, each followed by one LF.

Platform runners install locked npm dependencies and Rust 1.98.0 before invoking the adapter. macOS requires both Apple Rust targets; Linux requires the x86-64 GNU target, `bzip2`, and the tools used by `scripts/verify-linux-cli.sh`. Windows builds the desktop portable executable; Linux builds only the CLI; macOS builds a universal application input and separate `arm64` and `x86_64` DMGs. Each package reuses the signed closure and verifies its embedded authorities and packaged CLI. The adapter removes its owned staging after success and retains the shared Cargo cache. Failed staging is preserved for mount recovery and diagnosis. It does not publish packages or execute emulator tests.
