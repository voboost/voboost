## Why

<!-- Rewritten 2026-06-27 for APK-level OTA (whole-APK updates of voboost and
     voboost-inject), replacing the earlier incremental file-level design.
     Source plan: plans/2026-06-24/2026-06-24-21-20-emulator-test-stack.md -->

Voboost ships as **two APKs**, each updated as a whole — never as detached
"resources":

- **`voboost`** — the client app: UI, settings, translations, and the Frida
  agent scripts. One APK; a new release is a new APK installed over the old one.
- **`voboost-inject`** — the root daemon. A separate APK, released less often,
  installed by `adb install` or **self-updated**: the voboost app stages a new
  daemon APK, and voboost-inject verifies and replaces itself (that mechanism is
  owned by the voboost-inject repo).

There is intentionally **no partial/incremental update of agents or resources**
— that model ("resources") causes the download/refresh problems seen in other
tools (e.g. VoyahTweaks). Every update is a complete, self-consistent APK.

The application owns the **OTA client**: fetch a signed release manifest that
lists the current APK versions, compare against what is installed, download the
newer APK(s) whole, verify them, and apply — install for the app, stage-and-
signal for the daemon.

## What Changes

- **`ota-client`** — an application capability that:
  - verifies a signed OTA release manifest (ed25519, detached) against the
    committed `release-public.pem` before trusting its APK list;
  - compares the installed app version and the installed daemon version against
    the manifest and determines which APK(s) are newer;
  - downloads each newer APK **whole**, with a size pre-check and sha256 verify;
  - **applies per channel**: the `app` APK is staged and handed to the installer
    (PackageInstaller / `voboost-install`) to replace the running app; the
    `core` APK is staged into the app zone and a marker signals voboost-inject
    to self-update (the actual replace is the daemon's responsibility);
  - enforces manifest size/entry bounds and never persists a manifest that fails
    verification.

## Impact

- **New project** `voboost/voboost`: the OTA client, replacing the earlier
  file-level incremental/staging design.
- **`voboost-inject`** (sibling repo): owns the `core` self-update — verify a
  staged APK against the daemon's embedded trust, replace itself, restart via
  init. The voboost client only **stages and signals**, never installs the
  daemon.
- **`voboost-install`** (sibling repo): remains the operator installer; may also
  perform the `app` APK install in the operator path.
- **Dependencies**: ed25519 verification (BouncyCastle, already added for API 28
  which lacks EdDSA), an HTTP client (OkHttp, present), SHA-256.
