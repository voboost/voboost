# Voboost

## Project Overview

Voboost is an Android application (`ru.voboost`) for Voyah vehicles (Free,
Dreamer) that customizes the system through signed Frida agents injected by a
resident root daemon. This app is the **user space** half of a multi-repo
system; it does not inject anything itself.

The app is a **plan producer, a status reader, an OTA client, and a UI**:

- It writes `inject.json` (the daemon's plan: a `startup` gate, a `disabled`
  kill-switch, and per-agent `enabled` + opaque `config`) into its Android app
  zone `/data/user/0/ru.voboost`.
- It reads `inject-status.json` (daemon state, per-injection state, `killed`,
  `panic`) and surfaces it in the UI.
- It fetches, verifies, diffs, downloads, and stages OTA updates (agents and the
  daemon binary) into `staging/`, then signals the daemon via a single-use
  `update-ready` marker.
- It ships the daemon binary, the agents, and the signed manifests as APK assets
  that the operator installer or the test harness provisions into the root zone.

The injector — `voboost-inject`, a single resident root process launched by an
init hook — lives in a sibling repo and is consumed here as a prebuilt arm64
binary. Direct Frida injection (`frida-inject` over `su`) and the desktop
injection path have been removed.

## Repo topology

- **voboost** (this repo) — app: plan producer, status reader, OTA client, UI,
  APK carrier. Greenfield; spec-driven via `openspec/`.
- **voboost-inject** — the root daemon (Vala, arm64, API 28). Sole injector.
  Contract source of truth (`inject.json`/`inject-status.json`, root zone
  `/data/voboost`, app zone `/data/user/0/ru.voboost`, init hook, apply/rollback).
- **voboost-script** — builds the Frida agents and the unsigned daemon manifest.
- **voboost-stubs** — target-process stubs, ported to arm64 APKs whose
  `applicationId` matches a target process name.
- **voboost-install** — operator installer; provisions the root zone and init
  hook over whitelisted ADB; has a headless CLI used by the test harness.

## OpenSpec

Spec-driven; the truth is `openspec/`, no code without an applied change, and
invariants live in specs. The layout mirrors `../voboost-inject/openspec`:
`changes/<change>/{.openspec.yaml,proposal.md,design.md,tasks.md,specs/<capability>/spec.md}`,
with specs written as `## ADDED/MODIFIED/REMOVED Requirements` using SHALL and
WHEN/THEN scenarios. Validate before commit:

```bash
npx @fission-ai/openspec validate <change> --strict   # e.g. local-emulator-testing
npx @fission-ai/openspec validate --all --strict
```

Active changes:

- `local-emulator-testing` — the daemon-contract migration (plan producer,
  status reader, removal of direct injection/desktop), device provisioning, and
  the emulator test harness.
- `ota-client` — the application-side OTA client (verify, whole-file incremental
  diff, content-addressed staging, channel/manifest producer).
