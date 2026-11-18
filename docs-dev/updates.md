# Product updates

**English** | [Русский](updates.ru.md)

Every published change creates a new Voboost Product SemVer and one complete signed `voboost.json`. There are no independently published app, agent, or native versions.

## Discovery and download

The Android app reads a bounded signed `channel.json`, rejects rollback, and downloads the immutable `voboost.json` identified by exact URL, size, SHA-256, and signature. It compares the complete artifact rows with the daemon's active manifest identity and downloads every changed artifact. It does not compute agent applicability or native update roles.

JobScheduler owns the periodic check and continuation. The app sends a descriptor-bound proposal over `voboost.ota.v1`; the daemon authenticates the peer, receives the manifest, signature, and artifacts through `SCM_RIGHTS`, and reconstructs the complete candidate without trusting app-selected paths or roles.

## Candidate verification

The app writes changed bytes into app-private storage and publishes readiness last. A proposal contains no shell command, applicability result, or arbitrary root path.

`voboost-daemon` independently verifies the release signature, schema, compatibility, exact closed inventory, all received bytes, and every reused installed byte. It computes applicability from `agents.json` and root-owned `vehicle.json`, then creates one full candidate state.

## Activation by changed role

| Changed bytes | Activation |
| --- | --- |
| Product metadata and product-derived recovery authority only | verify the installed closure and publish the new manifest without reboot |
| Agent catalog or target DEX | create a new immutable generation |
| `agent-common.dex` | rebuild the complete applicable generation |
| `voboost-app.apk` | PackageInstaller replacement and transaction resume |
| `voboost-daemon`, native libraries, init, or SELinux | root update operation and post-reboot verification |
| Mixed roles | one product transaction with compatibility-defined order |

`restore.json` and `restore.json.sig` are regenerated for every Product SemVer because they authorize recovery of the complete installed product state. They are not reused solely because the `voboost-inject` commit is unchanged.

## Commit and rollback

The installed `voboost.json` becomes active last, after every changed role has been activated and reverified. Until then, the previous release remains installed authority. A failure uses the previous generation, previous native bytes, and the same transaction WAL; it does not create a second operation.

Already loaded DEX remains in an OEM process. If an affected required process still uses the old generation, the update becomes `reboot-required` and waits for user confirmation. Voboost does not kill a target process or reboot automatically.

## Release verification

Every release runs schema/parser, APK evidence, artifact-decision, PackageInstaller continuation, native activation, rollback, emulator, device, and declared vehicle gates over the same immutable candidate. Results remain scoped to the named artifact and environment.

## Related pages

- [Application](application.md)
- [Architecture](architecture.md)
- [Storage](storage.md)
- [Security](security.md)
- [Release](release.md)
