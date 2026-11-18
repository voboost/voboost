# Rebuild and install a modified Voboost

**English** | [Русский](build-modified.ru.md)

Build from the product and component repositories checked out at the coordinated source revisions. Packaging does not create or require source archives. Keep third-party licenses, notices and repository-owned patches with their source owners.

The Android courier APK carries its MIT documents and Android/JVM dependency notices. Native and Rust notices belong to the installer package and remain available through its Licenses menu; they are not copied into the courier APK. Source notice files and their provenance are retained.

macOS packaging uses ULMO with `udif-chunk-size=8192`, selected by comparing the same prepared volume with UDZO at `zlib-level=9`. Ordinary builds create one compressed image. The image key controls chunk size, not an LZMA preset; no undocumented LZMA level is assumed. Frontend assets remain uncompressed inside the executable so the DMG can compress them together.

Android implant statically links the pinned LSPlant and dex_builder libraries, with one static C++ runtime per ABI. LSPlant initialization and hook calls are direct; a separate LSPlant shared library is not shipped. The linker map in `voboost-inject/implant/implant.map` preserves loader entrypoints and the exception/RTTI/allocation ABI. `make check-prebuilt` verifies exports and dependencies for both Android ABIs. Map changes trigger relinking; do not reuse older prebuilt libraries after changing this contract.

## Prepare the workspace

Check out the product and component repositories at coordinated revisions in one workspace. Initialize their required submodules and keep the repository-owned patches. Packaging uses this tree directly; no source archive is generated or required.

Use Node 24.13.0, npm 11.6.2, Rust 1.98.0, JDK 17, Android platform 34, build-tools 34.0.0 and NDK 29.0.14206865. The macOS build requires Xcode command-line tools, CMake, Ninja, `cargo-ndk` 4.1.2, OpenSSL, `keytool`, `jq` and the normal macOS archive utilities. Install the Rust targets `aarch64-linux-android`, `aarch64-apple-darwin` and `x86_64-apple-darwin`. Install pinned npm dependencies with `npm ci --ignore-scripts` in `voboost`, `voboost-inject` and `voboost-installer`; Gradle wrappers resolve their pinned dependencies. Set `ANDROID_SDK_ROOT` and `ANDROID_NDK_HOME` to the installed SDK and NDK. Fetch the release-tooling Cargo dependencies before invoking the scripts, which use offline Cargo.

Modify the required source. For native third-party changes, retain the pinned upstream base and update the repository-owned patch and its existing source contract in `voboost-inject/tools/prepare-native-third-party-source.ts` or `prepare-libsepol-source.ts`. Keep signature verification and the coordinated signing roles. Commit component changes locally, then freeze their new commits in both product definitions:

```sh
node voboost/tools/release/prepare-source-pins.ts "$PWD" --write
git -C voboost add release/version.toml release/1.0.0-beta1.toml
git -C voboost commit -m 'Use local modified source pins'
```

## Build with your own authority

Run from the workspace root. The authority and output paths must be new absolute paths outside the source repositories and temporary, build or cache trees. The following sibling directories satisfy that layout:

```sh
cargo fetch --manifest-path voboost/release/Cargo.toml --locked
voboost/release/init-local-test-authority.sh "$PWD/local-authority"
voboost/release/build-local-beta1.sh "$PWD" "$PWD/local-authority" "$PWD/local-modified-build"
```

The builder signs the coordinated APK, agent catalog, product catalog, restore authorization and native roles with your authority, embeds matching public roots, verifies the closure and builds a macOS DMG. Keep the authority private and retain it for later builds. Production private keys are neither supplied nor required. The final output includes the DMG, signed closure, source and artifact evidence, and public roots. The work directory is retained as `local-modified-build.work`.

## Install the complete modified build

Use an owner-authorized supported device with the documented USB debugging and root prerequisites. If another APK certificate signs the existing installation, uninstall it with its own installer first. A change of signing roots requires the complete coordinated build; replacing one signed helper does not satisfy verification.

Mount the generated DMG and replace the volume path and serial below with the actual values:

```sh
/Volumes/Voboost/voboost-installer --install --device SERIAL
/Volumes/Voboost/voboost-installer --status --device SERIAL
```

A successful host rebuild does not establish device, emulator or vehicle acceptance. Run the applicable acceptance gates against the exact modified package before drawing those conclusions.
