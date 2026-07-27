# app-daemon-contract Specification

## Purpose
TBD - created by archiving change local-emulator-testing. Update Purpose after archive.
## Requirements
### Requirement: App zone is the only writable surface
The application SHALL confine all of its writes to its Android app zone
`/data/user/0/ru.voboost` (equivalent to `/data/data/ru.voboost`, one inode).
The application SHALL NOT write the root zone `/data/voboost` under any
circumstance; root-zone provisioning is the responsibility of the operator
installer or the test harness. `PathsAndroid` SHALL resolve `inject.json`,
`inject-status.json`, and the `staging/` directory under the app zone.

#### Scenario: App resolves contract paths under the app zone
- **WHEN** the application resolves the plan, status, and staging paths
- **THEN** each resolves under `/data/user/0/ru.voboost/`

#### Scenario: App never writes the root zone
- **WHEN** the application stages an OTA or writes a plan
- **THEN** no path under `/data/voboost/` is opened for writing by the app

### Requirement: inject.json plan producer
The application SHALL produce `inject.json` in the app zone as the daemon's
input plan. The plan SHALL be a JSON object with: an integer `version`; a
`startup` string gate where the value `"none"` (case-insensitive) means the
application requests no injection; a boolean `disabled` kill-switch; and an
`agents` array whose entries each carry a string `id`, a boolean `enabled`, and
an opaque `config` object forwarded uninterpreted to the agent. The producer
SHALL write the plan atomically (write a temporary file then rename). The whole
plan SHALL NOT exceed 1 MiB and each per-agent `config` SHALL NOT exceed 64 KiB;
a plan exceeding either bound SHALL NOT be written.

The plan SHALL be reproduced whenever the on-device configuration changes while
the service is running: the service SHALL watch the configuration file and,
on a change, re-run plan production against the new configuration (the producer
is idempotent — it re-evaluates every feature and rewrites the whole plan). This
SHALL NOT require a service restart. The `off` startup mode (which stops the
service before any features start) SHALL NOT start the watcher.

#### Scenario: Feature toggle writes an enabled agent
- **WHEN** the user enables a feature mapped to agent `wm-viewport`
- **THEN** the producer writes `inject.json` with an `agents` entry
  `{ "id": "wm-viewport", "enabled": true, "config": {...} }` via temp + rename

#### Scenario: Startup gate
- **WHEN** the application requests no injection
- **THEN** it writes `"startup": "none"` and the daemon takes no action

#### Scenario: Kill-switch
- **WHEN** the application sets the global kill-switch
- **THEN** it writes `"disabled": true`

#### Scenario: Oversized plan is rejected
- **WHEN** the assembled plan would exceed 1 MiB, or a per-agent config would
  exceed 64 KiB
- **THEN** the producer does not write the plan and logs the rejection

#### Scenario: Plan is reproduced on a mid-run config change
- **WHEN** the service is running (hidden or interface mode) and the on-device
  configuration file changes
- **THEN** the producer rewrites `inject.json` from the new configuration
  without a service restart

#### Scenario: Watcher is not started in off mode
- **WHEN** the service starts in the `off` startup mode
- **THEN** no configuration watcher is started (the service stops before any
  features run)

### Requirement: inject-status.json reader
The application SHALL read `inject-status.json` from the app zone to observe the
daemon. The status is a JSON object with: a `daemon` version string; an integer
`manifest` version; a `state` of `"ready"` or `"degraded"`; boolean `killed` and
`panic` flags; and an `injections` array whose entries each carry an `id`, a
target `process` name, and a per-injection `state` of `"active"`, `"failed"`,
`"skipped-coexist"`, `"waiting"`, or `"quarantined"`. The reader SHALL tolerate
an in-flight partial write (the daemon writes atomically via temp + rename) and
SHALL surface the status to the UI without performing injection.

#### Scenario: Reader surfaces a ready daemon
- **WHEN** the reader parses `inject-status.json` with `state: "ready"` and one
  `active` injection
- **THEN** the UI shows the daemon ready and that injection active

#### Scenario: Reader tolerates a partial write
- **WHEN** the reader observes `inject-status.json` mid-rename
- **THEN** it retries or reports the last known good status without crashing

### Requirement: Daemon-only injection; direct paths removed
The application SHALL NOT perform injection itself. The daemon is the sole
injector: it attaches to target processes via ptrace and loads the Java/DEX
agent runtime. There SHALL be no injection binary asset bundled in the app, no
`/data/local/tmp/su` invocation, no desktop injection manager or entry point,
no binary extractor, and no injection-asset download Gradle task. Enabling a
feature SHALL only mutate the `inject.json` plan; the daemon is the sole
injector.

#### Scenario: Enabling a feature does not inject
- **WHEN** a feature is enabled
- **THEN** only `inject.json` changes; no process is spawned or injected by the app

#### Scenario: Legacy injection symbols are absent
- **WHEN** the source tree and Gradle build are searched
- **THEN** none of an injection binary, `/data/local/tmp/su`, an injection
  manager, a desktop entry point, a binary extractor, or an injection-asset
  download Gradle task appear

