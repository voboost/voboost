## ADDED Requirements

### Requirement: Root zone layout and permissions
Provisioning SHALL create the root zone at `/data/voboost`, owned by `root:root`
with mode `0700`, with no group- or world-writable bit on it or its parent chain.
The zone SHALL hold the daemon binary `voboost-inject`, the signed
`manifest.json` + `manifest.sig`, the `agents/` payloads, a `logs/` directory,
and a `run/` directory for the pidfile. The application SHALL NOT write here;
only the daemon and provisioning do.

#### Scenario: Fresh root zone is created
- **WHEN** provisioning runs on a device with no `/data/voboost`
- **THEN** it creates `/data/voboost` as `root:root` `0700` containing the daemon,
  the signed manifest, the agents, `logs/`, and `run/`

#### Scenario: Existing zone is not clobbered
- **WHEN** provisioning runs and the zone already exists
- **THEN** it refreshes the daemon, manifest, and agents but preserves runtime
  state (`run/`, `logs/`) unless an explicit reset is requested

### Requirement: Daemon launch via init hook
Provisioning SHALL install an init hook (`/system/etc/init/voboost.rc`) that
launches `/data/voboost/voboost-inject` as a root service at boot with
restart-on-exit, so a core OTA self-shutdown is recovered without a car reboot.
The daemon SHALL enforce single-instance semantics itself (pidfile + `flock`);
the hook SHALL NOT assume exclusivity. For fast injection tests, the harness MAY
start the daemon manually from a root shell instead of relying on the hook.

#### Scenario: Daemon starts at boot
- **WHEN** the device boots with the hook installed
- **THEN** the daemon starts as a root service and restarts if it exits

#### Scenario: Manual start for fast tests
- **WHEN** the harness starts the daemon from a root shell
- **THEN** the daemon runs and observes the app zone without a reboot

### Requirement: SELinux for the emulator
The emulator harness SHALL set SELinux to permissive (`setenforce 0`) so a
shell-started root daemon is not denied on `app_data_file` or root-zone access.
This is a test-only relaxation; a production device runs the daemon in a
dedicated init domain with a crafted SELinux policy, not in permissive mode.

#### Scenario: Emulator runs permissive
- **WHEN** the harness boots the emulator
- **THEN** it sets SELinux to permissive and the daemon can read the app zone and
  root zone without denial

### Requirement: Device readiness verification
Provisioning SHALL expose a verification step that reports readiness and exits
`0` only when all of the following hold: root access is available; the root zone
exists with correct ownership/mode; the daemon binary is present and executable;
the signed manifest and agents are present; the init hook is installed (or the
daemon is otherwise running); SELinux is in the expected mode. It SHALL exit
non-zero and name the failing check otherwise.

#### Scenario: Fully provisioned device verifies
- **WHEN** verification runs on a provisioned device
- **THEN** it exits `0`

#### Scenario: Missing component fails verification
- **WHEN** verification runs and the signed manifest is absent
- **THEN** it exits non-zero and names the missing manifest

### Requirement: Two equivalent provisioning paths
Provisioning SHALL be expressible both as a raw-adb script (lean, CI-friendly:
push artifacts, fix permissions, install hook or start manually) and as the
operator installer driven headless (`voboost-install --auto-install <apk> -s
<serial>`). Both paths SHALL install the stub-APK targets and the app, and both
SHALL leave the device in the same ready state. The harness SHALL be able to
drive either path against the same AVD.

#### Scenario: Raw-adb path provisions the device
- **WHEN** `provision.sh` runs against the emulator
- **THEN** the root zone, daemon, agents, stub-APKs, and app are installed and
  verification passes

#### Scenario: Installer path provisions and smoke-tests itself
- **WHEN** `install.sh` drives the headless installer
- **THEN** the device reaches the same ready state and the installer exits `0`
