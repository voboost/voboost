## Context

The app bundles Frida agents and a per-agent manifest as APK assets under
`assets/agents/` (populated by the `copyAgents` build task from
`voboost-script/build`). The daemon reads agents from the app-zone staging
directory (`staging/agents/`), not the APK assets, because it needs stable
on-disk paths it can mmap/read and verify. An extraction step must materialize
the assets to disk.

This is **Step 4** of the OTA pipeline (per `docs/17-ota-architecture.md`
§7.1): APK installed/updated → app extracts agents to staging → daemon
observes the `agents-update-ready` marker → daemon applies. Step 4 covers only
the extraction and marker; the daemon's consume (Step 5) is a separate future
change.

The extraction is invoked twice:
- on service start (after config load) — covers a fresh install and the first
  boot after an `adb install`;
- after a successful OTA app-channel stage — covers an OTA self-update that
  delivered a new app APK with new agents.

## Goals / Non-Goals

**Goals:**

- Materialize all agent `.js`, the manifest, and (if present) the `.sig` from
  assets to `staging/`.
- Be idempotent: repeated calls with unchanged content are a no-op.
- Be self-healing: a deleted/corrupt staged `.js` is restored on the next call.
- Signal the daemon reliably: the marker is created last, after every file is
  fsynced, so its presence implies a complete staged set.
- Survive `cleanStaging()`: the content fingerprint lives in the app zone, not
  in staging (which `ApkStager` wipes).

**Non-Goals:**

- Verifying the manifest signature against a key (that is the daemon's job in
  Step 5).
- Deciding which agents to extract — all listed agents are extracted; the
  daemon gates per-agent via `inject.json`.
- Deleting stale agents removed from the manifest. Extraction overwrites by
  name; an agent removed from the manifest leaves its old `.js` behind until
  `cleanStaging()` runs. Acceptable: stale files are inert (no manifest entry)
  and `cleanStaging()` reclaims them.
- Consuming the marker — that is Step 5.

## Decisions

### D1. sha256-manifest content addressing, not an integer version

The agents-manifest `version` field is a **schema** version (`1`); it is not
bumped when agents are edited. It therefore cannot gate re-extraction. Instead
the extractor hashes the raw manifest **bytes** with sha256 and persists that
as the content fingerprint. Any manifest edit changes the hash → re-extract.
This needs no contract from `voboost-script` (no version-bump discipline) and
is robust to asset regeneration.

### D2. Hybrid idempotency: fingerprint + presence check

Two conditions are required for a no-op: (a) the stored fingerprint equals the
current manifest hash, and (b) **every** agent `.js` listed in the manifest
exists in `staging/agents/`. (a) alone misses the case where a `.js` was
deleted out-of-band (e.g. manual cleanup, partial disk failure) but the
manifest is unchanged. (b) is cheap (~16 `File.exists()` calls) and closes
that gap as self-healing. When either fails, the full set is re-extracted.

### D3. Marker created last, single-use

The `agents-update-ready` marker is written **after** every agent, the
manifest, and the `.sig` are fsynced, so its presence is a reliable signal that
a complete, consistent set is staged. It mirrors `voboost-inject-update-ready`
in `ApkStager`. It is single-use: the daemon (Step 5) consumes (deletes) it
after any apply attempt, so a successful apply does not loop. Recreating it
only on a content change (not on an idempotent no-op) means an idle restart
does not re-signal the daemon.

### D4. Fingerprint in app zone, not staging

`ApkStager.cleanStaging()` does `stagingDir.deleteRecursively()` + `mkdirs()`.
A fingerprint file in staging would be wiped by every OTA stage, defeating
idempotency and forcing a redundant re-extract. The fingerprint lives at
`appZone/agents-extracted-version`, which `cleanStaging()` never touches.

### D5. Optional `.sig`

`assets/agents/agents-manifest.json.sig` may be absent (`voboost-script` does
not yet sign the manifest). The extractor copies it only if present; absence is
not an error. The manifest is always copied. Step 5's daemon verification will
fail without a `.sig`, but producing one is `voboost-script`'s concern, not the
extractor's.

### D6. Atomic writes

Each file is written via temp + fsync + rename in the target directory (the
same pattern as `ApkStager.writeFileAtomically`), so a crash never leaves a
half-written file visible under the final name. The marker is atomic too, so it
never appears partially.
