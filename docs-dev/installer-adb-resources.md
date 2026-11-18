# Installer ADB resources

**English** | [Русский](installer-adb-resources.ru.md)

ADB source resources live in [`src-tauri/resources/adb`](../../voboost-installer/src-tauri/resources/adb). Retain `ADB-LICENSE.txt`, the exact Android Platform Tools 35.0.1 (`r35.0.1-11580240`) redistribution notice, with the original binaries. Legal notices and source identities remain source inputs.

Current [resource staging](../../voboost-installer/src/build/lib-resources.ts) copies only the Windows trio `adb.exe`, `AdbWinApi.dll`, `AdbWinUsbApi.dll` into `resources-staged/win`. It writes `adb-resources-v1.txt` with the platform, exact inventory, sizes and digests; Rust `build.rs` re-verifies the manifest. Generated staging is owned by `src/build/build-resources.ts` and must not be edited manually. Windows runtime retains the verified executable/DLL identities through process creation. No UPX transformation is applied.

Normal macOS staging has no external ADB files: the application links the [direct ADB backend](installer-native-adb.md). Linux also stages no ADB; its backend uses only `/usr/bin/adb`, `/usr/local/bin/adb` or `/opt/android-platform-tools/adb`, with protected owner, path, file and executable checks. Mutable `PATH` does not select it. Install the distribution's `android-tools-adb`/`android-tools` package and required udev rules as appropriate.

## Retained macOS source provenance

The retained universal `mac/adb` is a separate source input with both arm64 and x86_64 slices, not the current normal macOS transport. Its pinned ad-hoc-signed identity is `e50256fbd434d08f510d467ec36f6bdce4224d3b8fd82fe0e8dd73f4da33edcc`. The original supplied input `92105d0c0f006a6fbdd8a91e82b791d23e4746491062b62d5ecc34abecf9086b` retained upstream `Identifier=adb`, `TeamIdentifier=EQHXZ8M8AV`, but its arm64 signature was invalid.

Explicit source preparation repaired signing, then Apple `strip -N` and deterministic ad-hoc signing reduced 13,594,432 bytes to 12,122,544. Loaded sections, dyld metadata, exports and both slices were retained; duplicate nlist symbols/strings were removed. Retained preparation evidence reports matching arm64 and translated x86_64 `version`/`help`, strict signatures and repeatable signing identity. This provenance does not establish physical USB acceptance or describe normal native-backend packaging.

Run from `voboost-installer` to check that retained input:

```sh
./scripts/verify-macos-adb-source.sh
```

## Intentional source update

Obtain the intended Android Platform Tools version and its notice, review source identities and the relevant tests, then update only the owned inputs. Keep Windows binaries together. Do not strip, merge, sign or repair an installed bundle at runtime. A source signing change belongs to explicit source preparation before recording its identity; ordinary staging copies verified Windows bytes. Re-run the platform source checks and packaging checks for affected targets. Release signing and physical transport acceptance are separate gates.

See [installer build](installer-build.md), [installer](installer.md) and the source [macOS provenance test](../../voboost-installer/tests/macos-adb-source.test.ts).
