# agents-extractor Specification

## Purpose
TBD - created by archiving change agents-extractor. Update Purpose after archive.
## Requirements
### Requirement: Extract embedded agents from APK assets to staging
The application SHALL extract the Frida agents and the per-agent manifest
embedded in its own APK (`assets/agents/`) into the app-zone staging directory
on service start (after configuration is loaded) and after a successful
OTA app-channel stage. For each `agents[].file` listed in
`assets/agents/agents-manifest.json`, the extractor SHALL copy the asset to
`staging/agents/<basename>` where `<basename>` is the segment after the last
`/`. The extractor SHALL also copy `assets/agents/agents-manifest.json` to
`staging/agents-manifest.json`.

#### Scenario: Fresh install extracts all agents
- **WHEN** the service starts with an empty staging directory
- **THEN** every agent `.js` and the manifest is copied into `staging/agents/`
  and `staging/agents-manifest.json`

#### Scenario: OTA app update re-extracts agents
- **WHEN** an OTA app-channel stage completes successfully
- **THEN** the extractor runs again so any newly bundled agents are materialized

### Requirement: sha256-manifest content addressing gates extraction
The extractor SHALL compute a sha256 over the raw bytes of
`assets/agents/agents-manifest.json` as the content fingerprint. The manifest's
`version` field SHALL NOT be used as the extraction gate, because it is a
schema version that is not bumped on agent edits. The current fingerprint SHALL
be persisted under the app zone (not under staging).

#### Scenario: Unchanged manifest is not re-extracted
- **WHEN** the persisted fingerprint equals the current manifest hash and all
  agent files are present
- **THEN** no copy occurs and the update marker is not recreated

### Requirement: Idempotency with presence-based self-healing
The extractor SHALL treat the operation as a no-op only when BOTH the persisted
fingerprint equals the current manifest hash AND every agent `.js` listed in the
manifest exists in `staging/agents/`. When the fingerprint matches but any
listed `.js` is missing, the extractor SHALL re-extract the full set. A no-op
SHALL NOT recreate the update marker or copy any file.

#### Scenario: Missing staged agent is restored
- **WHEN** the persisted fingerprint matches but a staged `.js` has been deleted
- **THEN** the full agent set is re-extracted and the marker is recreated

#### Scenario: Idempotent restart does not re-signal
- **WHEN** the service restarts with a matching fingerprint and all files present
- **THEN** the update marker is not recreated

### Requirement: Optional detached manifest signature
The extractor SHALL copy `assets/agents/agents-manifest.json.sig` to
`staging/agents-manifest.json.sig` only when that asset exists. The absence of
the signature SHALL NOT be an error and SHALL NOT prevent extraction or marker
creation. The extractor SHALL NOT verify the signature — manifest verification
against a trust key is the daemon's responsibility.

#### Scenario: Signature asset absent
- **WHEN** `agents-manifest.json.sig` is not present in assets
- **THEN** the manifest and agents are copied and the marker is created; no
  signature file is staged

#### Scenario: Signature asset present
- **WHEN** `agents-manifest.json.sig` is present in assets
- **THEN** it is copied to `staging/agents-manifest.json.sig`

### Requirement: Atomic file writes
Each staged file (agent `.js`, manifest, signature) SHALL be written atomically:
a temporary file is created in the target directory, fsynced, and then renamed
over the final name. A crash SHALL NOT leave a partially written file visible
under the final name. This mirrors the APK staging write pattern.

#### Scenario: Partial write is never visible
- **WHEN** a write is interrupted before the rename
- **THEN** the target file is either absent or the previous complete version

### Requirement: Single-use agents-update-ready marker created last
The extractor SHALL create the `staging/agents-update-ready` marker as the LAST
step, after every agent, the manifest, and (if present) the signature have been
written and fsynced, and before persisting the fingerprint. Its presence SHALL
signal to the daemon that a complete, consistent agent set is staged. The marker
is single-use: the daemon consumes (removes) it after any apply attempt. The
marker SHALL be created only when extraction actually occurs, never on an
idempotent no-op.

#### Scenario: Marker signals a complete staged set
- **WHEN** extraction writes all files successfully
- **THEN** the `agents-update-ready` marker is created last

#### Scenario: Marker is not recreated on a no-op
- **WHEN** the extractor determines no change is needed
- **THEN** the marker is left as-is and not recreated

### Requirement: Extraction fingerprint persists under the app zone
The extractor SHALL persist the current manifest fingerprint at
`appZone/agents-extracted-version` (the app zone, not the staging directory),
written atomically. Because `ApkStager.cleanStaging()` deletes the staging
directory tree, a fingerprint stored under staging would be wiped on every OTA
stage, defeating idempotency.

#### Scenario: Fingerprint survives a staging wipe
- **WHEN** `cleanStaging()` removes the staging tree
- **THEN** the `appZone/agents-extracted-version` fingerprint is unaffected, so the
  next extraction correctly detects whether content changed

### Requirement: Non-throwing public API
The extractor's public extraction API SHALL NOT throw. Recoverable errors (a
missing asset, a read failure) SHALL be reported as a failure result rather than
an exception, so an extraction error does not crash the foreground service.

#### Scenario: Missing manifest asset is a failure, not a crash
- **WHEN** `assets/agents/agents-manifest.json` cannot be read
- **THEN** the extractor returns a failure result and the service continues

