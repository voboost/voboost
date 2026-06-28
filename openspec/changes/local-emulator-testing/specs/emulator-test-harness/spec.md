## ADDED Requirements

### Requirement: Rooted arm64 AVD boot
The harness SHALL boot a rooted arm64 API-28 AVD (`free`, AOSP `default`
userdebug) with `-no-snapshot`, obtain root via `adb root`, set SELinux
permissive, and wait for `boot_completed` before any provisioning step. It SHALL
fail fast (non-zero exit) if the emulator does not become ready within a timeout.

#### Scenario: Emulator boots and is rooted
- **WHEN** `boot.sh` runs
- **THEN** the AVD is running, `adb root` succeeded, SELinux is permissive, and
  `boot_completed` is observed

#### Scenario: Boot timeout fails the run
- **WHEN** the emulator does not reach `boot_completed` in time
- **THEN** the harness exits non-zero

### Requirement: Provisioning and deployment automation
The harness SHALL provision the emulator (root zone, daemon, agents, signed
manifest, init hook or manual start) and deploy the stub-APK targets and the app
APK, via either the raw-adb path or the headless installer path. It SHALL verify
device readiness before running tests.

#### Scenario: Harness provisions and installs
- **WHEN** the harness runs the provisioning step
- **THEN** the root zone, daemon, agents, stub-APKs, and app are deployed and the
  readiness check passes

### Requirement: Contract-driven test execution
The harness SHALL drive the daemon by writing `inject.json` (directly or via the
app), start or rely on the stub target processes, then poll
`inject-status.json` and `/data/voboost/logs/*.log` and assert the daemon's
documented integration scenarios. It SHALL be silent on success and exit non-zero
on any assertion failure, producing a CI-usable exit code.

#### Scenario: A passing scenario is silent
- **WHEN** a scenario's assertions all hold
- **THEN** the harness prints nothing and continues

#### Scenario: A failing scenario fails the run
- **WHEN** an `inject-status.json` field contradicts the expected outcome
- **THEN** the harness prints the mismatch and exits non-zero

### Requirement: Integration scenario coverage
The harness SHALL cover the daemon's documented scenarios: spawn-gate, attach,
js/native routing, resume after a gated failure, coexist-skip, quarantine,
kill-switch, startup-gate, boot-gate, and config-delivery. Each scenario SHALL
map to concrete assertions on `inject-status.json` (per-injection `state`,
`killed`, `panic`) and/or daemon logs.

#### Scenario: Kill-switch scenario
- **WHEN** the kill-switch is tripped
- **THEN** the harness asserts `inject-status.json` reports `killed: true` and
  injections stop

#### Scenario: Startup-gate scenario
- **WHEN** `inject.json` carries `"startup": "none"`
- **THEN** the harness asserts the daemon took no injection action

### Requirement: Log collection and diagnostics
The harness SHALL collect the daemon logs from `/data/voboost/logs/` and the app
logs on failure (or when artifact preservation is requested), so a failed run can
be diagnosed. It SHALL NOT leave the emulator running unless artifact
preservation is explicitly requested.

#### Scenario: Failure collects logs
- **WHEN** a scenario fails
- **THEN** the daemon and app logs are collected before the harness exits

#### Scenario: Cleanup on success
- **WHEN** the run completes successfully and preservation is not requested
- **THEN** the emulator is stopped and temporary files removed
