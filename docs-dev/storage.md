# Device storage

**English** | [Русский](storage.ru.md)

The target permanent root is `/data/voboost`. The Android app keeps downloads and resume state only in its private sandbox; OEM target processes do not read the product root.

## Permanent root

```text
/data/voboost/
├── root.json
├── operation.lock
├── operation.json
├── voboost.json
├── voboost.json.sig
├── voboost-daemon
├── voboost-install
├── voboost-restore
├── voboost-uninstall
├── voboost-selinux
├── voboost.rc
├── restore.json
├── restore.json.sig
├── libimplant-arm64-v8a.so
├── libimplant-armeabi-v7a.so
├── liblsplant-arm64-v8a.so
├── liblsplant-armeabi-v7a.so
├── vehicle.json
├── agents-enabled.json
├── status.json
├── generations/
├── state/
├── logs/
├── run/
├── install/
├── recovery/
└── uninstall/
```

Artifact basenames are flat and versionless. Directories exist only for transaction, generation, state, log, runtime, and recovery lifetimes.

## Ownership

Permanent files are root-owned with role-specific modes. The app does not write permanent files. A narrowly prepared incoming candidate is treated as untrusted until the daemon reopens it without following links, verifies ownership/type/link count/size/hash, and publishes root-owned bytes.

Every durable document uses bounded canonical parsing and safe publication through a temporary sibling, file `fsync`, atomic rename, and parent-directory `fsync`. Operation and generation publication share `operation.lock`; one active install/update/restore/uninstall excludes another root mutation.

## Generations

One generation contains `agents.json`, its signature, `agents-selected.json`, `agent-common.dex`, and every target DEX in the signed catalog. Applicability affects only `agents-selected.json` and process loading. `current` and `previous` are daemon-owned pointers. Candidate and quarantine directories cannot become active without complete verification.

## Recovery authority

`restore.json` is not a second product manifest and not an inject-only reusable artifact. It binds the release identity, exact source-definition SHA-256, and canonical digest of all restorable artifact rows except the restore pair; excluding itself and its signature removes the hash cycle. Its raw Ed25519 signature, fixed recovery programs, installed inventory, and init-owned services authorize one exact installed product state. If those authorities cannot be verified, the state is `unrecoverable`.

## Related pages

- [Install](install.md)
- [Updates](updates.md)
- [Restore](restore.md)
- [Uninstall](uninstall.md)
- [Security](security.md)
