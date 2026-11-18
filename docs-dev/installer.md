# Offline installer

**English** | [Русский](installer.ru.md)

`voboost-installer` provides one Rust operation core to the Tauri GUI and CLI. It receives one immutable signed product closure from `voboost/release`; it contains no release selection, artifact download, channel switch, or component build.

## Startup routing

Before Welcome, the installer performs bounded device inspection. An active operation opens Current Operation. A healthy installed product opens management actions. A recoverable degraded product offers Restore and Uninstall. Unknown or unrecoverable state disables mutation and shows exact diagnostics. Only an absent product enters the installation flow.

Installed management offers startup mode, Restore, logs, Uninstall, and Upgrade only when the embedded installer release is newer and compatibility permits it. It does not manage individual agents or offer downgrade.

The startup selector saves intent for the next vehicle boot. Its canonical receipt confirms durable configuration, not immediate runtime activation. The current boot keeps its existing startup session; saving does not restart inject or wait for its status. The storage daemon remains resident even when the selected startup mode is `none`.

## Typed boundary

The WebView sends only a validated ADB serial, closed operation enum, or startup mode `interface`, `hidden`, or `none`. It sends no shell fragment, executable, device path, manifest path, or arbitrary argument. Device operations use fixed `voboost-daemon` endpoints and canonical receipts.

## Offline packages

| Platform | Package | Product/ADB storage |
| --- | --- | --- |
| Windows 10/11 x86-64 | `voboost-installer-<release>.exe` | product closure and official ADB trio embedded in one portable PE |
| macOS arm64+x86_64 | `voboost-installer-<release>.dmg` | separate arm64/x86_64 apps, linked direct ADB, flat closure once in Resources, CLI symlink |
| Linux x86-64 | `voboost-installer-<release>.tar.bz2` | one mode-`0755` CLI-only ELF entry with embedded closure; system ADB from fixed protected backend locations |

The macOS DMG contains no source archives or source trees. Packaging verifies a separate corresponding-source delivery (`corresponding-source.tar.gz`, `corresponding-source.json`, `BUILD-MODIFIED.md`) but does not export it beside the DMG; the local builder retains it in its work directory. Readable licenses remain in the application. See [modified builds](build-modified.md).

Windows GUI requires an already installed compatible WebView2 Runtime. The installer does not bundle, download, or install WebView2. CLI arguments are parsed before GUI initialization and remain usable without WebView2.

## Artifact source

The common core reads package bytes through one closed `ArtifactSource`: exact list, bounded streaming open, and declared identity from `voboost.json`. It rejects duplicate names, overlaps, out-of-bounds data, unsupported compression, undeclared trailing bytes, and any actual size/SHA-256 mismatch.

## Version handling

The installer resumes any active operation before Welcome, regardless of the version that started it. Restore and Uninstall use the installed machine authority. Upgrade uses the closure embedded in the running installer and appears only when that release is strictly newer and compatibility permits the transition. An older installer never offers downgrade. A schema it cannot interpret permits only bounded diagnostics, Retry, and logs when the fixed endpoint is available.

## Related pages

- [Install](install.md)
- [Updates](updates.md)
- [Storage](storage.md)
- [Security](security.md)
- [Release](release.md)

## Component implementation and development

`voboost-installer` is the fully offline graphical and command-line tool for installing Voboost, managing an installed release, restoring it after an OEM firmware update, exporting logs, and removing it completely over USB.


The GUI binds asynchronous results to the connected device and operation epoch. Disconnect, reset and remount invalidate older results, including a reconnect with the same serial. Management mode drafts remain within one device session; an untouched draft follows the latest confirmed mode.

### Running

The graphical interface opens when the program starts without arguments. The command line supports:

```text
voboost-installer --install [--device SERIAL]
voboost-installer --restore [--device SERIAL]
voboost-installer --logs DIRECTORY [--device SERIAL]
voboost-installer --uninstall [--device SERIAL]
```

The main packaged executable also accepts `voboost-installer --device SERIAL` without a headless action to open the GUI for that exact target. It uses the CLI device parser and retains the target across discovery, operations, child helpers and reconnect. An unavailable selected device does not fall back to another device. Without `--device`, existing default discovery remains unchanged.

Normal uninstall retains the committed pristine policy identity before teardown. After the device-owned reboot, stock init cache inputs may prove policy removal when the loaded-policy read is permission denied; all nine absence checks remain required, and this verification keeps SELinux Enforcing.

If the Cluster connection drops during removal, rerun `--uninstall` on the same device. The installer checks the retained transaction and its prepared finalizer before continuing. Completion requires a signed QNX absence receipt and the external Android absence checks.

If normal uninstall fails, its existing diagnostic reports the SELinux context, enforcement and fixed loaded/disk policy metadata, with one timed read's status and bounded error. Marker presence or absence is reported only after a successful complete read; this read-only information does not replace uninstall absence proof.

Installation does not display a mandatory license screen or ask for license acceptance. License texts and third-party notices remain available in the distribution.

All three builds also provide CLI `--status` and `--mode interface|hidden|none`; Windows and macOS expose the same controls on the graphical Management screen. On Windows and macOS, adding `--dry-run` to a headless operation validates its arguments without starting the GUI runtime, resolving ADB, contacting a vehicle, or writing logs; Install also validates the embedded signed product closure.

The installer inspects the connected vehicle before Welcome. It resumes an active operation immediately; an installed product opens Management with startup mode, Restore, logs, Uninstall, and Upgrade only when the embedded release is newer and compatible.

`voboost-installer-1.0.0-beta1.exe` is one portable Windows x86-64 executable. Its GUI requires Microsoft Edge WebView2 Runtime, while CLI commands work without WebView2. It extracts only `adb.exe`, `AdbWinApi.dll`, and `AdbWinUsbApi.dll` into a private temporary directory.

`voboost-installer-1.0.0-beta1-arm64.dmg` and `voboost-installer-1.0.0-beta1-x86_64.dmg` each contain the matching architecture of `Voboost Installer.app` and a root `voboost-installer` symlink to the same executable. macOS uses linked direct ADB without an external ADB executable. `voboost-installer-1.0.0-beta1.tar.bz2` contains the Linux x86-64 CLI executable with mode `0755` and pinned readable licenses and notices; Linux resolves system ADB from fixed protected locations.

Packaging verifies corresponding source as an internal build input; it does not export a source companion beside the DMG. `corresponding-source.tar.gz`, `corresponding-source.json`, and `BUILD-MODIFIED.md` are excluded from the image and application bundle.

Install and Restore perform only the reboots required by the current verified phase. Uninstall completes after an external observer verifies that Voboost is absent.


The CLI supports install, update, restore, uninstall, status, startup mode, logs, help and package verification on all supported platforms. `--update` applies the embedded verified release to an installed product and preserves its settings. The installed application can also initiate an update. Windows and macOS additionally support `--dry-run`, `--diagnostics` and `--emulator-check`. Windows provides `--licenses`. Record runtime acceptance separately for each tested platform.

`--observe --device SERIAL` reads fixed retained files without changing guest state. Native macOS and standalone Linux share one encoded-response and parser limit of 100 MiB (104,857,600 bytes), including HEX expansion and framing. Overflow returns `unavailable`; a truncated response is never accepted. The decoded runtime publication follows the resident owner’s 1 MiB limit; other fixed files retain their 32 KiB limits. Observe stderr and ordinary text commands keep their separate 64 KiB bounds. These limits do not establish runtime or vehicle readiness.

`--observe-native-can CURSOR --device SERIAL` is a closed diagnostic read on desktop and standalone Linux. CURSOR is a canonical unsigned 64-bit decimal. It executes only `/data/voboost/voboost-daemon --observe-native-can CURSOR` on the explicit device, with one bounded wait/read budget; it does not prepare root, install, reboot, change SELinux, or accept shell arguments. The daemon authenticates the resident owner and grants a fifteen-second diagnostic receive lease. The lease can keep the raw receiver open while agents are disabled, without enabling their policies or sending CAN commands.

The returned actual-kernel-RX page carries boot, writer and receiver epochs, contiguous raw sequence numbers, loss/error indicators and the last EAGAIN clock watermark. `active` means the receive queue was drained; unavailable, draining, a cursor gap, stale watermark or an epoch change cannot establish continuous coverage. Policy `discarded` counts are distinct from missing raw records. A cold kernel boundary needs its own closed-ECU proof and cannot be inferred from a new receiver's sampled state. Host CLI/parser tests validate this transport; a packaged runtime acceptance remains a separate required gate.

### Development

```text
npm ci
npm run typecheck
npm run lint:check
npm run format:check
npm test -- --run
npm run test:build
npm run build
./scripts/cargo-test-with-ephemeral-role-roots.sh
```

The Rust test helper supplies `VOBOOST_PRODUCT_VERSION` from the product-owned `installed-healthy.json` wire fixture and ignores any inherited release version. It generates disposable test role roots and restores the original `resources-staged` tree after success, failure or a signal. This fixture identity is only for host checks; production packaging still requires frozen version inputs from the product build chain.

Detailed contracts are maintained in [`voboost/docs-dev`](). User instructions are maintained in [`voboost/docs-user`](../docs-user).

The emulator acceptance adapter is `--emulator-check /absolute/request.json --device emulator-PORT`. It accepts only closed UI, reboot, diagnostic and approved fixture actions, verifies QEMU and Enforcing before root access, and never accepts shell or ADB arguments. The functional gate uses the packaged CLI for every device operation and Voyemu CLI for profile lifecycle; its results do not establish physical vehicle acceptance.

The closed `inspect`, `snapshot` and `screenshot` actions may observe an active installation through an already-rooted, Enforcing connection without provisioning root or taking its writer lock. `inspect` reports boot completion; the fixed `sleep` and `wake` keys exercise the emulator power transition. These observations do not establish physical vehicle ACC behavior.

The closed `fault-window` action exercises real startup mutation rejection, vehicle state reply loss or one authenticated uninstall transport failure on the same installed kit. It requires existing root, QEMU, Enforcing and exact boot, manifest, generation, catalog and target PID/starttime. Keep its stdin pipe open while ordinary packaged UI actions run; the first JSON receipt reports `armed`, and closing stdin produces a matching `closed` receipt after cleanup. The native owner expires after ninety seconds and a dead owner makes its marker inactive. These receipts identify the diagnostic window; acceptance still requires the real Java, daemon and visible UI observations. See the [native contract](../../voboost-inject/openspec/specs/emulator-diagnostics/spec.md).

Emergency CLI removal also restores the exact original init digest selectors before deleting recovery data. It reads only fixed root-owned backup authority, validates the complete Android 9/11 selector set, rechecks its bytes after stopping services and preserves recovery on drift or symlinks. An incomplete selector backup is an error, never a successful cleanup.

Initial installation uses adb root, adb remount and a verified setenforce 0 provisioning window. The installer restores Enforcing; post-reboot native installation verifies Enforcing and rejects Permissive. This exception applies to initial provisioning and its pre-reboot retry, not ongoing agent operation.

### License texts in release packages

The canonical English and Russian Voboost licenses and original bundled-component notices are readable files in `Voboost Installer.app/Contents/Resources` on macOS and beside the extracted Linux executable. In the macOS application menu or Windows Help menu, **Licenses…** opens a separate window with component groups, full terms and document search. It reads the packaged notices without accepting installation terms or contacting a vehicle. On Windows, `voboost-installer-1.0.0-beta1.exe --licenses C:\absolute\new-directory` verifies the embedded package and exports its exact license set into a new directory without using ADB or opening the GUI. The Android APK contains the shared product notices in `assets/licenses/`. Original third-party terms remain separate from the Voboost license. The GUI displays MIT separately from the owner’s installation authorization and risk notice.

Protected resource-file transactions capture the exact block-device identity and original read-only flag before durably publishing the remount journal. For ext4/f2fs, writable remounts use the shared protected-block path. Normal completion and watchdog recovery restore the filesystem to read-only before restoring its original block flag. Recovery compares the retained journal with the inherited transaction, target, mount topology and original flags; incomplete authority or restoration failure retains the journal and blocks further writes. The journal requires the current schema and explicit original flags; it does not infer protection from the device after an interrupted unlock.

An interrupted update is resumed by its retained transaction when its protected proposal identifies the same verified installation package, including `--install --force`. Resumption observes the existing operation instead of uploading the package again. A failed protected-system step publishes a failed operation with its reason while retaining the recovery journal; it must not remain indefinitely reported as running.
