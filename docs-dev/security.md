# Security model

**English** | [Русский](security.ru.md)

Voboost treats the network, release hosting, app-provided candidates, WebView input, OEM processes, and arbitrary device paths as untrusted. Trust is granted only by fixed embedded roots, exact signed bytes, closed schemas, and repeated verification at privilege boundaries.

## Signed identities

| Identity | Protects | Consumers |
| --- | --- | --- |
| Android APK signature | package `ru.voboost`, source-controlled `versionCode`, signer certificate | PackageManager, installer, daemon APK verifier |
| Product release role | exact `voboost.json` bytes and complete artifact identities | installer, app, daemon/system programs |
| Channel role | exact mutable `channel.json` pointer | app |
| Agent catalog role | exact `agents.json` semantics and DEX identities | release verifier, daemon, implant boundary |
| Restore role | exact product-derived `restore.json` authority | init-owned recovery programs |
| Native executable role | exact final bytes and runtime role of privileged executables | install/update/recovery verifiers |

Private keys exist only in protected signing environments. Trust roots are embedded in authorized consumers and are not loaded from a release, channel pointer, app storage, or `/data/voboost`.

## Verification layers

The app rejects a bad channel or product manifest before download. The daemon repeats product signature, schema, compatibility, inventory, size/SHA-256, catalog, and candidate-byte checks before root mutation. Less-privileged verification never grants authority to a more-privileged consumer.

Product authoring extracts APK identity from exact signed bytes through pinned `aapt2` and `apksigner`, requires `versionCode` in `1..=2100000000` and equal to source-definition `android.versionCode`, then binds observed package/version/certificate to the same file size/SHA-256 and app commit. An evidence JSON is not authority and cannot override fresh extraction.

Secretless final verification also parses the exact signed APK as a bounded ZIP, requires exactly one `assets/config/release-public.pem`, parses its canonical Ed25519 SPKI value, and compares the raw key with source-pinned `productPublicKeyHex`. APK signature validity does not excuse a missing, duplicate, malformed, or substituted embedded product root.

Product authoring also verifies canonical `restore.json` and its raw Ed25519 signature under the configured restore-role public key. The document binds Product SemVer, exact source-definition SHA-256, and a canonical digest of every restorable artifact row except the restore pair itself, avoiding a cyclic hash.

Agent applicability is computed only by the daemon from verified catalog semantics and root-owned vehicle facts. OEM target processes and agents receive no general root-filesystem access. Each agent is constrained by catalog ID, target, capability, state ownership, and typed pipes.

## Filesystem and process boundaries

All root paths are fixed or derived from validated transaction/generation identities. Symlinks, hard links, special files, unexpected children, wrong owner/mode, and identity changes during reads are rejected. Publication uses durable atomic replacement.

Process injection requires stable PID/start time, exact process name, UID, SELinux context, executable, and ABI. The injector repeats admission after stopping the thread group and restores all temporary ptrace state on failure.

## Evidence boundary

Host tests cover product parser/schema behavior, APK evidence binding, native contract and role-signature fixtures, process-admission negatives, and SELinux policy compilation. Cross-consumer product signatures, protected keys, packaged installer authenticity, physical device, SELinux Enforcing runtime, and vehicle behavior remain separate gates.

## Related pages

- [Architecture](architecture.md)
- [Contracts](contracts.md)
- [Storage](storage.md)
- [Release](release.md)
- [Testing](testing.md)

The post-reboot policy check compares access-vector rules and filename transitions as complete sets, so their serialization order does not affect equality. All other serialized policy data must match after those verified sets are aligned. Changed permissions, transition destinations, types or other policy content remain errors; the required-rule and context checks still run on the active policy.
