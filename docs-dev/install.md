# Initial installation

**English** | [Русский](install.ru.md)

The desktop installer is offline. It contains one signed product closure and does not choose, download, or build another release.

## Before host staging

`--install` first checks for a resumable installation of the same verified bundle. For a new clean installation it uses the shared [removal workflow](uninstall.md) to start or resume cleanup. The installer stages the new bundle in the same invocation only after it verifies the required Android and QNX checks. An unresolved object or missing evidence blocks staging and is reported with the reason. The standalone Linux CLI stops when QNX absence requires the desktop native transport. The `--update` path applies the signed bundle to an installed product and preserves user settings.

## Host staging

The installer verifies `voboost.json`, its signature, mandatory APK package/version/certificate identity, every artifact size/SHA-256, and the exact flat inventory. It creates one random transaction under `/data/voboost/install/<transaction-id>/`, writes transaction identity first, uploads flat files, and starts fixed `/data/voboost/voboost-install` without arguments only after the closure is complete.

The host never sends a shell fragment or arbitrary device path. After the device program admits the transaction, `operation.json` is the only observable authority and the desktop only reconnects and observes typed phases.

## Device sequence

1. Verify root, staging authority, product and agent signatures, compatibility, APK identity, and exact inventory.
2. Acquire `operation.lock`, create the WAL, and publish `operation.json` durably.
3. Save the stock state required for Restore and Uninstall.
4. Install permanent native programs, flat ABI libraries and SELinux policy; resolve and atomically save hardware facts in root-owned `vehicle.json` before publishing the init definition.
5. Install `voboost-app.apk` through PackageManager and observe package, certificate, `versionCode`, path, inode, size, and SHA-256 twice.
6. Compute agent applicability from the installed hardware facts and publish the initial immutable generation from external agent artifacts.
7. Start `voboost-daemon`, verify installed identity, generation, and required target status, then publish installed inventory and `voboost.json` last.
8. Remove transaction data and publish the terminal result.

The app does not extract agents after `BOOT_COMPLETED`. `agent-common.dex`, target `agent-*.dex`, `agents.json`, and its signature come from the installer closure and are admitted by the device-owned root operation.

## Interruption

Before `operation.json`, the host may resume only the same transaction and manifest digest. After `operation.json`, desktop restart, ADB reconnect, package replacement, and reboot continue the same device-owned operation. Another operation receives `busy`.

## Verification

The release gate repeats this sequence from the packaged installer and checks the exact stage inventory, operation resumption, PackageManager identity, SELinux Enforcing state, complete immutable agent generation, active manifest, and final terminal receipt.

## Related pages

- [Installer](installer.md)
- [Storage](storage.md)
- [Restore](restore.md)
- [Uninstall](uninstall.md)
- [Security](security.md)
