# Voboost

Android application (`ru.voboost`) for Voyah vehicles (Free, Dreamer) that customizes the
system through signed Frida agents injected by a resident root daemon.

This app is the **user-space** half of a multi-repo system. It does **not** inject anything
itself. It is a **plan producer, a status reader, an OTA client, and a UI**:

- writes `inject.json` (the daemon's plan) into its Android app zone;
- reads `inject-status.json` (daemon state) and surfaces it in the UI;
- fetches, verifies, diffs, downloads, and stages whole-APK OTA updates for itself
  (the app) and for the daemon (the core);
- ships the daemon binary, agents, and signed manifests as APK assets.

The injector — `voboost-inject`, a single resident root process launched by an init hook —
lives in a sibling repo and is consumed here as a prebuilt arm64 binary. Direct Frida
injection (`frida-inject` over `su`) and the former desktop injection path have been removed.

## Architecture

- **[`VoboostService`](src/main/java/ru/voboost/VoboostService.kt)** — the foreground service
  that runs the app. Started on boot by [`BootReceiver`](src/main/java/ru/voboost/BootReceiver.kt);
  keeps the process alive, produces the plan, reads status, and runs the periodic OTA check.
  Running as a foreground service (with a persistent notification) prevents the system from
  killing it.
- **[`Main`](src/main/java/ru/voboost/Main.kt)** — orchestrator: produces the plan and reads status.
- **[`PlanProducer`](src/main/java/ru/voboost/PlanProducer.kt)** — turns `config.yaml` + active
  features into the daemon's `inject.json` (`version`, `startup` gate, `disabled` kill-switch,
  per-agent `enabled` + opaque `config`). Atomic write; plan ≤ 1 MiB, per-agent config ≤ 64 KiB.
- **[`StatusReader`](src/main/java/ru/voboost/StatusReader.kt)** — parses `inject-status.json`
  (daemon `state`, per-injection state, `killed`, `panic`); tolerant of in-flight atomic writes.
- **[`ota/`](src/main/java/ru/voboost/ota/)** — APK-level OTA client: ed25519 release-manifest
  verify, whole-APK download (size + sha256), per-channel apply. The **app** APK is staged and
  handed to the system installer; the **core** (daemon) APK is staged into the app-zone
  `staging/voboost-inject.apk` plus a single-use `core-update-ready` marker — the daemon
  self-updates from it (the app never installs the daemon).
- **[`FeatureManager`](src/main/java/ru/voboost/FeatureManager.kt)** — maps config → active
  agents (each `FeatureFrida*` declares its agent id + target process + plan entry).
- **[`Paths`](src/main/java/ru/voboost/PathsAndroid.kt)** — resolves the app-zone
  `/data/user/0/ru.voboost` (`inject.json`, `inject-status.json`, `staging/`). The app never
  writes the root zone `/data/voboost`.

### App ↔ daemon contract

The app writes `/data/user/0/ru.voboost/inject.json`; the daemon writes
`/data/user/0/ru.voboost/inject-status.json`. The contract mirrors the implemented
`voboost-inject` daemon (see `openspec/changes/local-emulator-testing/specs/app-daemon-contract/`).

## Building

The project ships a **single release variant** (the debug variant is disabled). `./gradlew build`
produces `voboost.apk`; `-Pdebuggable=true` flips the release variant's `isDebuggable` for rare
deep debugging without reintroducing a debug build type.

```bash
./gradlew build                 # build the app APK (voboost.apk) + run unit tests
./gradlew testUnit              # unit tests only (alias for testReleaseUnitTest)
./gradlew assembleRelease       # assemble the APK without tests
```

Release signing reads `KEYSTORE_PASSWORD` from `local.properties`; the keystore
(`voboost-release.jks`) is gitignored. The APK carries the daemon binary, agents, and signed
manifests as assets; they are provisioned into `/data/voboost` by the operator installer
(`voboost-install`) or the test harness — not by the app.

## Local emulator testing

The full app → daemon → stub-APK → agent cycle is exercised on a rooted arm64 API-28 AVD
without a physical device. See [`tools/emulator/README.md`](tools/emulator/README.md).

```bash
tools/emulator/boot.sh                              # boot AVD 'free', root, SELinux permissive
tools/emulator/provision.sh                         # raw-adb: daemon + agents + manifest + stubs + app
tools/emulator/run-test.sh                          # contract scenarios (silent on success)
./gradlew emulatorTest                              # gradle entry point for the above
./gradlew openspecValidate                          # validate openspec changes (strict)
```

The stub target processes (arm64 APKs whose `applicationId` is a `com.qinggan.*` process
name) are built in the `voboost-stubs` repo (`android-apk-port`).

## Configuration

Managed by the [`voboost-config`](../voboost-config) library; the live file lives at
`/data/data/ru.voboost/files/config.yaml` (the default is shipped as
[`src/main/assets/config.yaml`](src/main/assets/config.yaml) and copied on first launch).
Key settings:

- `settings-startup`: `off` | `hidden` | `interface` — maps to the daemon `startup` gate
  (`off` → `startup:"none"`, the daemon takes no action).
- `vehicle-fuel-mode`: `electric-forced` forces EV mode (Free only).
- `interface-widget-weather`, `interface-keyboard`, `vehicle-pedestrian-warning`, … — each
  toggles a daemon agent via the plan.

## Logging

Centralized [`Logger`](src/main/java/ru/voboost/Logger.kt); app-side logs at
`/data/data/ru.voboost/logs/voboost-YYYY-MM-DD.log` (daily rotation, 7-day retention).

## OpenSpec

Spec-driven; truth is `openspec/`. Active changes:

- `local-emulator-testing` — daemon-contract migration, device provisioning, emulator harness.
- `ota-client` — application-side APK-level OTA client.

```bash
npx @fission-ai/openspec validate --all --strict
```

## Project structure

```
src/main/java/ru/voboost/
├── VoboostService.kt        # foreground service (boot, plan, status, OTA)
├── Main.kt                  # orchestrator
├── PlanProducer.kt          # config.yaml + features -> inject.json
├── StatusReader.kt         # inject-status.json -> UI state
├── BootReceiver.kt          # ACTION_BOOT_COMPLETED -> VoboostService
├── FeatureManager.kt        # config -> active agents
├── PathsAndroid.kt          # app-zone path resolution (Paths interface)
├── Logger.kt                # centralized logging
├── ota/                     # APK-level OTA client (verify, download, stage)
├── feature/                # FeatureFrida* (declare agent id + plan entry)
└── ui/                      # ConfigState, StatusState, panels, components
src/main/assets/
├── config.yaml              # default configuration (copied on first launch)
└── config/release-public.pem # ed25519 public key for OTA manifest verify
tools/emulator/             # boot/provision/run-test harness
.github/workflows/           # emulator E2E CI
openspec/                    # specs (local-emulator-testing, ota-client)
```

## Requirements

- Android API 28+ (arm64-v8a); target AVD `free` (`system-images;android-28;default;arm64-v8a`).
- Sibling repos: `voboost-inject` (daemon), `voboost-script` (agents), `voboost-stubs`
  (target APKs), `voboost-install` (provisioning), `voboost-config` (config).

## Related projects

- **[voboost-inject](../voboost-inject)** — the root daemon (sole injector); contract source of truth.
- **[voboost-script](../voboost-script)** — Frida agents + daemon manifest.
- **[voboost-stubs](../voboost-stubs)** — target-process APKs for emulator testing.
- **[voboost-install](../voboost-install)** — operator installer / headless provisioning.
- **[voboost-config](../voboost-config)** — configuration management library.
- **[voboost-codestyle](../voboost-codestyle)** — code style and linting rules.

## License

GPL-3.0
