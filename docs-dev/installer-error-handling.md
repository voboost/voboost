# Error handling

**English** | [Русский](installer-error-handling.ru.md)

## Packaged release

The installer uses its embedded signed closure and does not download or select a release catalog. Signature, schema, inventory, size or integrity failure rejects it before device mutation. Show the error through existing diagnostics and retain bounded technical context without keys, tokens or unnecessary user paths.

## Device and release selection

Distinguish no device, `unauthorized`, `offline`, and multiple devices. Recheck the canonical serial and selected release before every change. A stale request response cannot replace a newer selection.

## Process execution

Every typed operation defines a time and output bound. Cancellation and timeout terminate the child process tree to the extent guaranteed by the platform and never turn partial stdout into success.

## Reboots and reconnection

Install and restore each expect two reboots. Uninstall has no fixed count: after every phase, the device decides whether another reboot is required for cleanup, while the computer verifies monotonic state and waits for reconnection.

Uninstall succeeds only after a terminal report and an absence check. If verification finds remnants, the operation continues or fails rather than reporting partial success.

## Restarting the installer

Restarting the GUI or CLI reconstructs trusted state from device data. It does not start another operation over an unfinished one or resume a UI phase from application memory alone.

## Optional features

A clipboard, log export, or optional diagnostics failure does not change operation state. Diagnostic data is copied or exported only through an explicit user action.

## Evidence boundary

A host test proves only the observed program branch. Actual ADB, reboots, SELinux Enforcing, device, and vehicle behavior require separate checks.
