## Why

Voboost ships its Frida agents and a signed per-agent manifest as APK assets
(`assets/agents/`). The root daemon consumes these from the app-zone staging
directory, not from the APK directly: it cannot read the running APK's assets
from its own mount namespace and needs a single, well-known location with a
stable name. Until the agents are materialized to disk, the daemon has nothing
to inject.

Today nothing writes them there. The `copyAgents` build task embeds the agents
in the APK, but no runtime step extracts them, so on a fresh install (or after
the app APK is replaced) `staging/agents/` is empty and the daemon cannot apply
the plan. The extraction must also re-run after an OTA app update, because the
new APK may ship different agents.

## What Changes

- **`agents-extractor`** — an application capability that:
  - reads `assets/agents/agents-manifest.json` and computes a sha256 over its
    bytes as a content fingerprint (the manifest's `version` field is a schema
    version, not bumped on content edits, so it cannot gate re-extraction);
  - is **idempotent**: when the stored fingerprint matches and every agent
    `.js` file is present in `staging/agents/`, it does nothing — no copy, no
    marker recreation;
  - **self-heals**: a missing `.js` file triggers a full re-extract even when
    the fingerprint matches;
  - copies each agent `.js`, the manifest, and the optional detached
    `.sig` atomically (temp + fsync + rename);
  - creates the single-use `agents-update-ready` marker **last**, signalling
    the daemon that a complete, consistent agent set is staged;
  - persists the fingerprint under the app zone (not staging) so the
    `ApkStager.cleanStaging()` wipe does not reset it.

## Impact

- **`voboost` (this repo)**: new `ScriptExtractor` in the `ota` package; two
  call sites in `VoboostService` (after config load on startup, and after a
  successful OTA app-channel stage).
- **`voboost-inject` (sibling repo)**: will consume `staging/agents/` +
  `agents-update-ready` (Step 5, separate future change). Until then the marker
  is written but unread — harmless.
- **`voboost-script` (sibling repo)**: unaffected. The `.sig` is optional; the
  extractor copies it only if present (voboost-script does not yet sign the
  manifest). Daemon verification is Step 5's concern.
