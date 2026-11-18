# Troubleshooting

**English** | [Русский](installer-troubleshooting.ru.md)

## Overview

Do not bypass signature, descriptor, device-lock, or terminal-verification checks. First preserve the exact error text and safe log, then identify the layer: host, packaged GUI, CI/signing, ADB/device, or vehicle.

## Application installation problems

### Windows

For SmartScreen or a Microsoft-verified-app requirement, verify the official source and file signature; do not disable system protection for an unknown binary. If antivirus blocks the file, preserve its verdict and digest for investigation rather than adding a broad exception.

### macOS

Messages about an unidentified developer or damaged app usually point to signing, notarization, quarantine, or corrupted bytes. Download the release again, verify its digest, and use the package for the correct architecture. Diagnose an Apple Silicon crash with the packaged artifact and crash report; a local development build is not equivalent evidence.

## Package verification

The installer works offline with its embedded release. For a damaged download, obtain the complete package again from the intended channel and verify its published identity before use. Replacing embedded files does not repair closure verification. Network/TLS failures in Android OTA belong to the [application](application.md).

## Connection problems

`No device found` requires checking USB, debugging mode, and `adb devices`. For `unauthorized`, approve the prompt on the device; for `offline`, reconnect transport and wait for canonical state. With multiple devices, select the exact serial: the installer must not guess.

## Installation errors

`Root access not available`, `remount failed`, and `APK installation failed` belong to different stages and must not be hidden behind a generic success. Do not run internal device commands manually: preserve the report and use the supported retry under the same per-device lock. If an operation hangs, wait for its deadline; force-closing the host does not remove the need for recovery on the next start.

## After installation

If the application does not appear, check terminal verification and the installed package rather than only the launcher. A crash after launch requires logcat and the exact installed APK identity; do not attribute it to the installer without evidence.

## Getting help

Include the installer version, computer operating system and architecture, release identity, safe log, canonical error code, `adb devices` output without unnecessary personal data, and the failing step. Do not send tokens, private keys, cookies, license acceptances, or private research materials.

## Frequently asked questions

- Feature availability depends on the vehicle profile and agent runtime contract probes; the installer does not select firmware by version or binary fingerprint
- Root requirements come from the selected supported workflow; the installer must not obtain root covertly
- Warranty effects depend on jurisdiction and manufacturer terms; technical documentation does not provide legal assurance
- Removal is supported only through authenticated uninstall with a terminal absence check
- The installer processes only necessary technical data and must not send it without a declared channel
- USB debugging is required for the fixed ADB installation and verification workflow
- Each vehicle receives a separate operation with its own canonical device lock and evidence
