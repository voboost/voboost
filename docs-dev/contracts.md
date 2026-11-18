# Contract registry

**English** | [Русский](contracts.ru.md)

Each contract has one source owner and independent parsers at consumer boundaries. Production repositories exchange schemas, fixtures, final artifacts, and evidence; they do not import one another's source tree.

| Contract | Owner | Producer | Consumers |
| --- | --- | --- | --- |
| source definition | `voboost` | product release request | product compositor |
| `voboost.json` and signature | `voboost` | product compositor and release signer | installer, app, daemon/system programs |
| `channel.json` and signature | `voboost` | protected publication | app |
| APK evidence | `voboost` observation of final signed APK | pinned Android tooling | audit; compositor repeats extraction |
| `agents.json` and signature | `voboost-agents` | agent release build | product compositor, daemon, implant boundary |
| agent selection/generation | `voboost-inject` | daemon | implant and runtime status consumers |
| operation and installed status | `voboost-inject/contract` | device-owned system programs and daemon | installer, app where required |
| uninstall request | Settings-host and root daemon boundary | `SettingsUninstall` through the runtime executor | daemon/uninstaller |
| packaged artifact source | `voboost-installer` | platform packager | common installer core |

## Product manifest

[`voboost.schema.json`](../release/voboost.schema.json) defines the cross-language JSON value contract. [`SCHEMA-CONFORMANCE.md`](../release/SCHEMA-CONFORMANCE.md) separates Draft 2020-12 constraints from semantic raw-byte, duplicate-key, order, hashing, catalog-correlation, and signature checks.

The app artifact row always contains exact APK size/SHA-256, `packageName = "ru.voboost"`, `versionCode` in `1..=2100000000`, and signer `certificateSha256`. Agent DEX identities must exactly match the separately signed catalog.

[`restore.schema.json`](../release/restore.schema.json) defines an acyclic recovery document bound to release identity, exact source-definition digest, and canonical restorable closure digest excluding the restore pair. Its raw signature is verified under a separately configured restore-role key before product manifest authoring.

## Agent catalog

`agents.json` uses strict schema2 with one logical agent ID and target-scoped `platform`, `domain`, and `entryAbi`. Android targets use `android` / `android` / `android-java-v1`, require `entryClass`, and reference the same module DEX bytes. QNX targets use `qnx` / `cluster` / `voboost-agent3-v1`, omit `entryClass`, and reference native ELF bytes. Process, file, size, SHA-256, and boot policy remain target-specific. `clusterRuntime` identifies the native runtime when a QNX target exists; `core` identifies Android `agent-common.dex`. Schema1 is rejected; there is no migration.

The product closure carries one `voboost-cluster-daemon` multicall ELF, `voboost-cluster-startup.sh`, `libvoboost-cluster-inject.so`, `libvoboost-runtime-cluster.so`, and `libagent-font-cyrillic.so` under their canonical `qnx-*` roles. ELF rows use ABI `qnx-aarch64`; the startup script omits ABI. Product compatibility requires `agentCatalogSchema = 2`.

Root Plan remains the sole desired-state authority. Cluster delivery is a derived signed projection; courier delivery or module loading does not establish `applied`. Signed target observations and the current generation determine runtime status.

The daemon computes applicability from the catalog and `vehicle.json`; the app does not produce a selection receipt as authority.

## Operation and runtime transport

The flat [`voboost-inject/contract`](../../voboost-inject/contract) crate owns canonical operation, install, generation, and APK identity types shared by privileged binaries. Runtime pipe frames bind generation, process start time, endpoint epoch, source agent, bounded payload, and correlated request/ack identity.

## Conformance

The product Rust parser and Draft 2020-12 validator run one shared accepted/rejected corpus. Kotlin app, native contract, and installer parsers must copy the corpus as immutable fixtures or consume an exact release artifact; relative source dependencies are forbidden. A parser difference blocks cutover.

## Related pages

- [Architecture](architecture.md)
- [Security](security.md)
- [Storage](storage.md)
- [Testing](testing.md)

## Current endpoint status

The daemon owns each injection's optional `currentEndpoint`: PID/starttime, endpoint epoch, registered generation/catalog and desired document revision. Identity integers are canonical decimal strings. Optional `acceptedStatus` contains the revision of an actually accepted S and a root receipt identifying its sequence, daemon PID/starttime and boot. Omission means acknowledgement evidence is unavailable; desired state is not an acknowledgement. Replacing an endpoint, including with the same PID, invalidates its old S. A resident registration can retain its generation during passive cutover while the top-level active generation changes.

Root-attributed L metadata adds the same daemon incarnation and `desiredDocumentGeneration`. Correlation requires current accepted S before and after the fresh observation, independently obtained process/daemon authority, and matching L context. Historical queued logs and agent message text cannot supply a missing acknowledgement. The helper validates correlation; the caller remains responsible for fresh sampling order.

Resident status has a separate 1 MiB read limit. Oversized or truncated input fails closed; endpoint identity is not removed to fit. The beta1 budget covers the reviewed catalog's 52 finite targets with maximum details and receipts, and release checks compare the actual generated catalog. Boot payloads remain bounded at 16 KiB. Host parser and serializer tests establish these boundaries; they do not establish Android or vehicle behavior.

