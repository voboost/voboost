## Why

<!-- Source plan: plans/2026-06-24/2026-06-24-21-20-emulator-test-stack.md -->

Voboost is moving from direct Frida injection — the app called `frida-inject`
over `/data/local/tmp/su`, and a desktop run injected into host JVM stubs — to a
**daemon-based architecture**: a resident root daemon (`voboost-inject`,
consumed as an arm64 binary without edits) is the sole injector, and the app
becomes only a plan producer, a status reader, an OTA client, and a UI. The
daemon, its signed agents, and the target processes it injects into cannot be
exercised end-to-end without a physical Voyah head unit, and the host-only
stubs cannot run on Android. This change establishes a **local emulator test
stack** so the full cycle — app → daemon → stub-APK target → Frida agent — can
be developed and validated on a developer machine and in CI.

The same change removes the legacy direct-injection and desktop paths
entirely: the app is greenfield and carries no backward-compatibility
obligation, so the `frida-inject` asset, the `su`-based Android manager, the
desktop manager/entry point, the binary extractor, and the `downloadFridaInject`
Gradle task are deleted rather than kept behind a flag.

## What Changes

- **`device-provisioning`** — establishes the on-device trust layout a root
  daemon needs: the `/data/voboost` root zone (`root:root`, `0700`) holding the
  daemon binary, the signed `manifest.json`, the agents, and logs; the init hook
  that launches the daemon at boot with restart-on-exit; and SELinux set to
  permissive for the emulator (a real device ships an init domain with a
  policy). Provisioning is performed by the operator installer in production and
  by raw-adb scripts in the emulator/CI.
- **`app-daemon-contract`** — defines the file contract between the app and the
  daemon, matching the implemented daemon: the app writes `inject.json`
  (`startup` gate, `disabled` kill-switch, per-agent `enabled` + opaque
  `config`) and reads `inject-status.json` (`state`, `killed`, `panic`,
  per-injection state). The app performs no injection itself; direct
  `frida-inject` and the desktop injection path are removed.
- **`emulator-test-harness`** — a `tools/emulator/` harness that boots a rooted
  arm64 AVD (`free`, API 28), provisions the root zone and daemon, installs the
  stub-APK targets and the app, drives the contract by writing `inject.json`,
  polls `inject-status.json` and daemon logs, and asserts the daemon's
  documented integration scenarios. Silent on success; an exit code for CI.

## Impact

- **New project** `voboost/voboost`: this change is the foundation of the
  daemon-based architecture; the OTA client change (`ota-client`) builds on the
  `staging/` paths and the app-zone layout introduced here.
- **`voboost-inject`** (sibling repo): consumed without edits. Its contract
  (`inject.json`/`inject-status.json`, root/app zones, init hook) is normative
  here — this change mirrors it, it does not redefine it.
- **`voboost-stubs`** (sibling repo): the host-only Java stubs are ported to
  arm64 Android APKs (separate `android-apk-port` change) so they can serve as
  the target processes the daemon injects into inside the emulator.
- **`voboost-install`** (sibling repo): the headless installer is driven by the
  harness as one of two provisioning paths and is itself smoke-tested.
- **Dependencies**: Android SDK + emulator, ADB, an arm64 API-28 system image,
  the assembled daemon + agents + signed manifest artifacts.
