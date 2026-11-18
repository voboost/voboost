# Architecture

**English** | [Русский](architecture.ru.md)

Voboost is assembled from independent source repositories and released only as one product. The root `voboost` repository owns Product SemVer, `voboost.json`, `channel.json`, release definitions, publication coordination, and the user/developer documentation.

## Runtime responsibilities

| Component | Owns | Does not own |
| --- | --- | --- |
| `voboost-app` | unprivileged OTA download, app-private candidate storage, APK self-update, uninstall confirmation | agent applicability, root mutation, publication |
| `voboost-agents` | agent runtime, signed `agents.json`, common and target DEX, OEM stubs | OTA network, installer, root filesystem |
| `voboost-inject` | `voboost-daemon`, injector, implant, system operations, root state, selection, generations | HTTP/TLS, desktop UI, release choice |
| `voboost-installer` | offline host UX, ADB transport, packaged closure, operation observation | component builds, artifact download, publication |
| `voboost` | product identity, global schemas, exact source definition, release composition, documentation | Android, agent, or native production source |

The `voboost-inject` repository builds two resident executables: `voboost-daemon` owns persistent state, configuration, and file logs; `voboost-inject` verifies agent inputs, selects processes, and manages injection. Android init starts inject after the owner service and stops it when the owner stops or restarts.

User choices belong to the daemon. It stores them once in `/data/voboost/agents.json` (schema 2), separately from the signed agent catalog, with a durable `userRevision` and per-field action metadata. Installation, generation activation, rollback and retries preserve these choices. Product defaults fill fields the user has not chosen.

A Saved receipt confirms durable storage; application to Android or CAN is a separate result. A failed or delayed application does not reverse the checkbox. Ordinary startup-mode changes take effect on the next boot; the explicit hidden-interface recovery gesture can restore the interface in the current boot.

## Product closure

Every publication has one Product SemVer and one signed `voboost.json`. The manifest contains the complete versionless device-artifact inventory, compatibility values, exact size/SHA-256, and mandatory package/version/signer identity for `voboost-app.apk`.

Agent artifacts remain outside the APK. `voboost-agents` produces `agents.json`, `agents.json.sig`, `agent-common.dex`, and one `agent-*.dex` per logical module, shared by its target processes. `voboost-inject` verifies the catalog and computes applicability from root-owned `vehicle.json`; the app and installer never duplicate that selection.

`restore.json` and `restore.json.sig` are product-derived recovery authority. The document binds release identity, exact source-definition digest, and a canonical digest of all restorable artifact rows excluding the restore pair, so no cyclic hash exists. The pair changes with every product release identity.

## Install and runtime flow

1. The offline installer verifies one packaged signed product closure and stages flat artifacts.
2. The fixed device installer verifies the same closure and creates the initial root-owned generation.
3. Android init starts `/data/voboost/voboost-daemon` in its dedicated SELinux domain.
4. `voboost-inject` verifies installed product state and agent inputs, determines applicability from vehicle facts, and checks target process identity; it requests persistent state through the authenticated owner protocol.
5. The injector loads the exact ABI implant; the implant receives `agent-common.dex` and only target-specific DEX through retained descriptors.
6. Host agents own shared OEM surfaces, while feature agents contribute through the process-local registry.

## OTA flow

The app discovers a release through signed `channel.json`, downloads the immutable signed `voboost.json`, and downloads all changed artifacts. It submits bytes but no applicability result or arbitrary path. `voboost-inject` reconstructs the full candidate, verifies every identity, creates a generation or native transaction as required, and makes the new installed `voboost.json` active last.

Target processes keep already loaded DEX until a natural process restart or user-approved reboot. Voboost does not kill OEM processes automatically.

## Verification boundary

Host gates cover source definitions, schemas, manifest parsing, pinned-tool APK identity extraction, acyclic restore production and verification, and artifact reuse. Product signatures across consumers, cross-language parsing, offline packages, emulator, device, SELinux Enforcing, and vehicle behavior retain independent gates.

## Related pages

- [Repositories](repository.md)
- [Application](application.md)
- [Agent runtime](agents-runtime.md)
- [Native runtime](native.md)
- [Updates](updates.md)
- [Release](release.md)
