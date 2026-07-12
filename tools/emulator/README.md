# Emulator test harness

Local end-to-end testing of the full **app → daemon → stub-APK target → Frida
agent** cycle on a rooted arm64 Android emulator, with no physical device.

This harness is the `emulator-test-harness` capability of the
`local-emulator-testing` change. It boots a rooted AVD, provisions the root
daemon and its agents, installs the stub target processes and the app, then
drives the daemon via `inject.json` and asserts outcomes against
`inject-status.json` and the daemon logs. Silent on success; a non-zero exit
code for CI.

## Preconditions

- Android SDK at `ANDROID_HOME` (default `~/Library/Android/sdk`) with the
  emulator, platform-tools, and an arm64 API-28 system image.
- A rooted arm64 AVD named `free`
  (`system-images;android-28;default;arm64-v8a`), AOSP `default` (userdebug) so
  `adb root` works. Create once:
  ```
  avdmanager create avd -n free -k "system-images;android-28;default;arm64-v8a" -d pixel
  ```
- Daemon artifacts (the daemon is **not** rebuilt here):
  - arm64 daemon binary — built in `voboost-inject` via `make build-android`
    (auto-detected under `build-android/`), or set `DAEMON_BIN`.
  - agents — `voboost-script/build/*.js`, or set `AGENTS_DIR`.
  - signed daemon `manifest.json` + `manifest.sig` — set `MANIFEST`/`MANIFEST_SIG`,
    or the dev fixture under `voboost-inject/test/fixtures/` is used (its sha256
    must match the staged agents).
- App APK — `./gradlew assembleDebug` in this repo.
- (Optional, for full E2E) stub-APK targets — the `android-apk-port` change in
  `voboost-stubs`.

## Usage

```
# 1. Boot the AVD to a daemon-ready state (root + SELinux permissive + boot_completed).
tools/emulator/boot.sh

# 2a. Provision via raw adb (lean, CI-friendly): daemon + agents + manifest + app + stubs.
tools/emulator/provision.sh

# 2b. …or provision via the operator installer (also smoke-tests the installer).
tools/emulator/install.sh

# 3. Run the contract-driven scenarios (silent on success, exit code for CI).
tools/emulator/run-test.sh
tools/emulator/run-test.sh --list              # list scenario names
tools/emulator/run-test.sh --only kill-switch startup-gate
tools/emulator/run-test.sh --keep-logs         # collect daemon/app logs to a tmp dir
```

`provision.sh` defaults to `--manual-start` (run the daemon from a root shell —
fast, no reboot). Pass `--init-hook` to install `/system/etc/init/voboost.rc`
and start the `voboost-inject` service (realistic for boot/core-OTA scenarios).
`--no-stubs` / `--no-app` skip those installs.

## Configuration (environment)

| Variable | Default | Purpose |
|---|---|---|
| `AVD_NAME` | `free` | AVD to boot |
| `ADB_SERIAL` | _any_ | target a specific `emulator-XXXX` |
| `ANDROID_HOME` | `~/Library/Android/sdk` | SDK root |
| `VOBOOST_INJECT_DIR` | sibling `voboost-inject` | daemon source + artifacts |
| `VOBOOST_SCRIPT_DIR` | sibling `voboost-script` | agents |
| `VOBOOST_STUBS_DIR` | sibling `voboost-stubs` | stub APKs |
| `VOBOOST_INSTALL_DIR` | sibling `voboost-install` | installer binary |
| `DAEMON_BIN` | auto-detect / `make build-android` | arm64 daemon binary |
| `AGENTS_DIR` | `voboost-script/build` | agent files |
| `MANIFEST` / `MANIFEST_SIG` | dev fixture | signed daemon manifest |
| `ROOT_ZONE` / `APP_ZONE` | `/data/voboost` / `/data/user/0/ru.voboost` | on-device paths |

## Scenarios

See [`integration-tests.md`](integration-tests.md) (mirrored from
`voboost-inject`) for the daemon's full scenario set. `run-test.sh` wires the
daemon-only ones (`startup-gate`, `kill-switch`, `status-schema`,
`daemon-state`) and the target-required ones (`attach-launcher`,
`config-delivery`) — the latter are **skipped with a logged reason** when the
stub target process is absent, never silently dropped. Scenarios needing the
full stub+agent matrix (spawn-gate, js/native routing, resume, coexist-skip,
quarantine, boot-gate) are run manually after the stubs APK port.

## OTA server (host-side, for self-update E2E)

The OTA self-update flow serves `release-manifest.json` + APKs from the host.
On macOS, `python3 -m http.server 8888` binds to **IPv6-only** by default, but
the Android emulator reaches the host via `10.0.2.2` (IPv4) — this causes
"unexpected end of stream" / connection-refused failures. Always bind IPv4:

```
cd <ota-staging-dir>   # contains release-manifest.json + voboost-inject.apk
python3 -m http.server 8888 --bind 0.0.0.0
```

Verify the emulator can reach it before running OTA scenarios:

```
adb shell curl -s -o /dev/null -w "HTTP %{http_code}\n" http://10.0.2.2:8888/release-manifest.json
```

## Layout

```
tools/emulator/
├── lib/common.sh           # shared adb/log/readiness helpers + config
├── boot.sh                 # boot AVD, adb root, setenforce 0, wait for boot
├── provision.sh            # raw-adb provisioning: root zone, daemon, agents, manifest
├── install.sh              # installer path: drive voboost-install headless
├── run-test.sh             # contract-driven scenario driver (silent on success)
└── integration-tests.md    # daemon scenario reference
```
