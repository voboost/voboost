## ADDED Requirements

### Requirement: Signed release manifest lists whole APKs
The OTA client SHALL verify a fetched release manifest's detached ed25519
signature (`release-manifest.json` + `.sig`) against the committed
`release-public.pem` before trusting any of its contents. Each entry SHALL
describe a whole APK: `path` (APK file name), `channel ∈ {app, core}`, `sha256`,
`size`, and `version`. An entry missing any field, or whose `channel` is not in
`{app, core}`, SHALL be rejected even if the signature is otherwise valid. A
manifest that fails signature or structural validation SHALL NOT be persisted as
the current manifest.

#### Scenario: Valid signature is trusted
- **WHEN** the client fetches a release manifest whose signature verifies against
  the committed public key
- **THEN** it trusts the APK list and proceeds to version comparison

#### Scenario: Invalid signature is rejected and not persisted
- **WHEN** the signature is missing or fails verification
- **THEN** the client rejects the manifest, performs no update, and keeps the last
  good manifest

#### Scenario: Entry with invalid channel is rejected
- **WHEN** an entry's channel is `agents`
- **THEN** the entire manifest is rejected, even with a valid signature

### Requirement: Release-manifest size and entry bounds
The parser SHALL enforce a maximum manifest byte size (1 MiB) and a maximum entry
count (4096); a manifest exceeding either SHALL be rejected.

#### Scenario: Oversized manifest rejected
- **WHEN** a signed manifest exceeds the byte-size or entry-count cap
- **THEN** the client rejects it and performs no update

### Requirement: Version-gated whole-APK download
The client SHALL compare the installed app version (its own `versionName`) and
the installed daemon version (from `inject-status.json`) against the manifest.
It SHALL download an APK only when the manifest version for its channel is
newer (semver). Each APK SHALL be downloaded whole; there is no per-file or
per-resource diffing.

#### Scenario: Newer app APK is downloaded
- **WHEN** the manifest's `app` APK version is newer than the installed app
- **THEN** the client downloads that APK whole

#### Scenario: Up-to-date channel is skipped
- **WHEN** the manifest's `core` APK version is not newer than the installed daemon
- **THEN** the client does not download the core APK

### Requirement: Download integrity with size pre-check
For each downloaded APK the client SHALL compare the received byte count to the
manifest `size` before hashing, rejecting a download whose size disagrees, and
SHALL then verify the `sha256`. An APK that fails either check SHALL be discarded
and not staged.

#### Scenario: Size mismatch rejects before hashing
- **WHEN** a downloaded APK's byte count differs from the manifest size
- **THEN** the client discards it without hashing and does not stage it

### Requirement: Apply app channel via installer
For an `app`-channel APK the client SHALL stage the verified APK into the app-zone
`staging/` and then invoke the system installer (PackageInstaller / install
intent, or `voboost-install`) to replace the running app. There is no apply
marker for the app channel.

#### Scenario: App APK is installed
- **WHEN** a newer app APK is downloaded and verified
- **THEN** the client stages it and issues an install intent to replace the app

### Requirement: Apply core channel by staging and signalling the daemon
For a `core`-channel APK the client SHALL stage the verified daemon APK into the
app-zone `staging/` and SHALL create a single-use marker (e.g.
`core-update-ready`) as the last step, signalling voboost-inject to self-update.
The client SHALL NOT install the daemon itself — the daemon verifies, replaces,
and restarts itself. The marker is single-use: the daemon consumes (removes) it
after any apply attempt.

#### Scenario: Daemon APK is staged and signalled
- **WHEN** a newer core APK is downloaded and verified
- **THEN** the client stages it and creates the single-use marker last; it does
  not install the daemon

#### Scenario: Marker is single-use
- **WHEN** the daemon applies (or rejects) a staged core update
- **THEN** it consumes the marker, so a successful self-update does not loop

### Requirement: Channel producer roles
The `app` channel SHALL be the voboost client APK; the `core` channel SHALL be
the voboost-inject daemon APK. The client SHALL NOT produce or sign the daemon's
per-agent `manifest.json` — that is build-time, inside the daemon APK.

#### Scenario: Client does not generate the daemon manifest
- **WHEN** the client stages a core APK
- **THEN** it stages the APK as-is; it does not synthesize a per-agent manifest
