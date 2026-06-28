## Context

Voboost ships as a set of cooperating repos under `~/voboost/`:

- **voboost** (this repo, `ru.voboost`) — greenfield; currently calls
  `frida-inject` through `/data/local/tmp/su` and has a desktop injection path.
  Becomes: plan producer + status reader + OTA client + UI. No direct injection,
  no desktop injection.
- **voboost-inject** — the root daemon (Vala, arm64, API 28). Embeds frida-core,
  reads `inject.json` + a signed `manifest.json` from the app zone
  `/data/user/0/ru.voboost`, injects signed agents by **process name**, and
  writes back `inject-status.json`. Paths `ROOT_ZONE = /data/voboost` and
  `APP_ZONE = /data/user/0/ru.voboost` are hardcoded constants in the daemon.
  Consumed as a prebuilt arm64 binary; this repo does not edit it.
- **voboost-script** — builds the Frida agents (Rollup) and the signed daemon
  `manifest.json` mapping agent → process.
- **voboost-stubs** — host-Java target stubs. They cannot run on Android, so a
  full arm64 APK port is required (separate change) for emulator testing.
- **voboost-install** — the operator Tauri installer that, in production,
  provisions the root zone and the init hook over whitelisted ADB.

The daemon's contract is already implemented and is the source of truth; this
repo mirrors it. Measured facts that drive the design: the daemon is a single
resident root process launched by an init hook with restart-on-exit
(pidfile + `flock`); it treats the app zone as untrusted and validates every
plan against the verified manifest; an app-zone `staging/` directory plus a
single-use `update-ready` marker carry OTA material (owned jointly with the
`ota-client` change); a system OTA reverts `/system` (losing the hook) while
keeping `/data` and root.

## Goals / Non-Goals

**Goals:**

- Run the full app → daemon → stub → agent cycle locally on a developer machine
  and in CI, with no physical device.
- Mirror the daemon's `inject.json` / `inject-status.json` contract exactly so
  the app and daemon agree without coordination beyond the files.
- Make the daemon the only injector: delete `frida-inject`, the `su` path, the
  desktop injection path, the binary extractor, and the download task.
- Keep provisioning identical in shape for a real device (operator installer)
  and the emulator (raw adb), so the emulator is a faithful test target.

**Non-Goals:**

- Editing the daemon — it is consumed as an arm64 prebuilt.
- Porting the stubs — that is the `android-apk-port` change in `voboost-stubs`
  (tracked alongside, but a separate repo).
- The OTA client fetch/diff/download/staging-writer logic — that is the
  `ota-client` change; this change only fixes the app-zone paths and the contract
  surface both share.
- Boot/core-OTA realism that needs `-writable-system` + reboot — the harness
  supports a manually-started daemon for fast injection tests; init-hook boot
  realism is a documented future extension.

## Decisions

### D1. arm64-only, API 28

The target AVD `free` is `system-images;android-28;default;arm64-v8a`, matching
the daemon's cross file (`aarch64-linux-android28`) and the app's `minSdk = 28`.
No x86 path; the daemon is arm64-only and is not rebuilt for the emulator.

### D2. Two provisioning paths, one model

- `provision.sh` — lean raw-adb provisioning (root-zone push, permissions, init
  hook or manual start). The fast path; no Tauri build required.
- `install.sh` — drives the `voboost-install` headless CLI
  (`--auto-install <apk> -s <serial>`); both provisions the device and
  smoke-tests the installer itself.

Both install the stub-APK targets and the app, and both target AVD `free`. A
real device uses the same installer; the emulator simply picks the lean path for
CI speed.

### D3. Daemon consumed without edits

The daemon binary, its agents, and the signed `manifest.json` are produced by
the sibling repos and pushed into `/data/voboost` by provisioning. The app never
writes the root zone; it only writes its own app zone. A future optional
`voboost-inject` change could make `ROOT_ZONE`/`APP_ZONE` env-configurable for a
host contour, but that is not required here.

### D4. SELinux permissive in the emulator

A shell-started root daemon in a userdebug AOSP image can hit `app_data_file` /
root-zone denies. The harness sets `setenforce 0` for tests. A production device
runs the daemon in a dedicated init domain with a proper policy; permissive is a
test-only relaxation, documented as such.

### D5. The stub-APK is the target process

Each stub port is an arm64 APK whose `applicationId` equals a target process
name (e.g. `com.qinggan.app.launcher`), kept alive by a foreground service so
the daemon can attach/spawn-gate it. The daemon injects by process name, so the
stub is transparent to it.

### D6. App is plan producer + status reader only

`FeatureManager`/`config.yaml` produce `inject.json`; a status reader surfaces
`inject-status.json` to the UI. Enabling a feature flips a per-agent
`enabled` flag (and opaque `config`) in the plan — it no longer calls an
injector. The `FridaManager` abstraction is removed; there is no platform
injection interface left in the app.

### D7. Silent-on-success harness

`run-test.sh` asserts the documented integration scenarios against
`inject-status.json` and `/data/voboost/logs/*.log`, prints nothing on success,
and exits non-zero on any assertion failure — the contract CI needs.
