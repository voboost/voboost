# Building and distribution

**English** | [Русский](installer-build.ru.md)

## Tools and version sources

The interface is built with TypeScript and Vite, and the desktop shell with Tauri and Rust. Node.js, npm, Rust, Tauri, and dependency versions come from repository files and lockfiles. A release build does not update them automatically.

Every supported Rust command uses the release profile, including local Tauri startup, tests, Clippy, CI cross-target checks, and packaging. The repository does not create a separate `target/debug` tree. Vite may still run its frontend development server, but the desktop Rust binary remains a release build.

Prepare a clean checkout with `npm ci`. Run the main checks with:

```text
npm run typecheck
npm run lint:check
npm run format:check
npm test -- --run
npm run test:build
npm run build
npm run test:rust
npm run lint:rust
```

## Platform packages

- macOS uses separate `arm64` and `x86_64` DMGs with matching application executables, linked direct ADB, and one flat signed product closure in `Contents/Resources`
- Windows x86_64 uses one portable executable containing the closure and the exact ADB trio
- Linux x86_64 uses one CLI tar.bz2 with pinned readable licenses and notices without bundled ADB and preserves executable mode `0755`

Beta1 has no operating-system-trusted desktop publisher: macOS packages are ad-hoc signed, while Windows and Linux packages are unsigned. The interface must explain possible system warnings in advance and must not obscure the file's origin.

The local macOS package gate consumes already-built and already-signed product inputs; it does not build Android components or create product signatures:

```text
node src/build/offline-package.ts build-mac-dmg \
  <Voboost Installer.app> <flat-signed-closure> \
  <product-public-key.pem> <product-role.sig> <restore-role.sig> <native-executable-role.sig> \
  </absolute/output/voboost-installer-<releaseIdentity>.dmg> <corresponding-source-directory>
```

The command uses linked direct ADB and its pinned notice, without an external ADB executable. The optional final `arm64` or `x86_64` argument selects one application slice for a separate DMG. It requires both Mach-O slices in the application executable, exact `CFBundleDisplayName`/`CFBundleName` `Voboost Installer`, exact `CFBundleExecutable` `voboost-installer`, an exact signature/hash-verified closure, and fixtures binding the product, restore and native-executable roots embedded in both slices. It ad-hoc signs the final resource-bearing app, verifies the root CLI symlink resolves to that same executable, creates the DMG, attaches it read-only, repeats the complete package checks from the mounted image, and detaches it. Any missing or mismatched input fails without retaining the output.

Before staging, it verifies the separate corresponding-source delivery against the exact product manifest. That delivery accompanies the DMG as a separate artifact. The image root contains only `Voboost Installer.app` and the CLI symlink; `corresponding-source.tar.gz`, `corresponding-source.json`, and `BUILD-MODIFIED.md` are forbidden in the application input, signed closure, staging, and mounted image. The package constructor does not copy the source delivery into the DMG.

## Offline invariant

The desktop installer has no product download location, channel selector, release catalog, or local manifest import. Its GUI and CLI use only the signed `voboost.json` and exact flat closure packaged for that installer release. Network availability cannot change the selected release or any device artifact.

## Building without keys

The ordinary installer CI job checks source and package constructors without private keys, release coordination, artifact upload, or publication authority.

The product repository builds and signs the complete closure from exact component commits. Installer packaging receives those immutable signed bytes, verifies the product manifest and inventory, and does not rebuild or sign Android components. Private keys never enter the frontend or final package.

## Publication

Publication belongs to `voboost`, not this repository. Beta1 packages cannot be published until the local vehicle gate, clean uninstall proof, approved source push, CI build, exact CI-package vehicle gate, and a separate user decision have all completed.

## Image sources and runtime assets

Keep the seven original connection PNGs in `src/assets/images/` as editable source assets. The frontend imports only the reviewed WebP files from `src/content/index.ts`; unimported source PNGs are not copied into the Vite output. Five illustrations have light/dark WebP pairs flattened over the actual `webLightTheme`/`webDarkTheme` backgrounds, and the two genuine system captures retain separate theme variants. The unused legacy public PNG stays removed.

Lossless PNG recompression must preserve the canvas, all decoded RGBA samples including RGB under transparent pixels, and color, density, and orientation metadata. Reject an optimizer result that changes those contracts. Android fixture PNGs belong to test APKs in the agents repository and are not installer runtime assets. Keep their existing resource/Canvas consumer tests as a separate check. Compress ICNS PNG payloads without removing representations or changing non-PNG records; verify native `NSImage` decoding and pixels after reassembly. `tests/connection-illustrations.test.ts` checks retained PNG sources and WebP runtime selection; `tests/macos-icon.test.ts` checks the native icon representation and packaging contract.

## Evidence boundary

Local checks prove source and workflow structure. Actual CI configuration, signing, published bytes, packaged GUI, connected device, reboots, SELinux Enforcing, and vehicle behavior require separate evidence.
