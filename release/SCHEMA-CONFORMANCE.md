# Schema conformance

`voboost.schema.json`, `channel.schema.json`, `restore.schema.json`, `local-delivery-evidence.schema.json`, `ci-vehicle-evidence.schema.json`, and `trust-roots.schema.json` are Draft 2020-12 contracts. The Rust test compiles all six schemas. The trust parser enforces exactly the five final agent/product/native-executable/restore/evidence Ed25519 roots; every provisioned identity must be present and distinct. Native OTA consumes the product manifest directly, so no legacy native-manifest root remains.

The schemas enforce the JSON value contract that another language can implement directly:

- closed root and nested objects
- strict SemVer shape and channel/version relation
- HTTPS manifest URL shape and bounds
- positive compatibility numbers, sizes, and lowercase SHA-256
- complete required product inventory
- exact fixed filename, role, activation, and ABI relationships
- mandatory `packageName`, `versionCode` in `1..=2100000000`, and `certificateSha256` for `voboost-app.apk`
- signature size and role-specific bounds
- at least one target `agent-*.dex`
- closed local and CI vehicle evidence envelopes with full artifact rows, deterministic aggregate, chained receipt digests, timestamp, distinct nonce, immutable producer, source-tree, and artifact-identity shapes

Some requirements cannot be represented by Draft 2020-12 over an already parsed JSON value. Every consumer must implement and run these semantic checks against the shared corpus in addition to JSON Schema validation:

- reject duplicate object keys before normal JSON parsing
- require canonical compact UTF-8 bytes with one final LF and canonical field/artifact order
- require artifact filename uniqueness even when two rows differ in other fields
- hash actual artifact bytes and compare exact size/SHA-256 with the manifest
- compare every agent DEX identity with the separately signed `agents.json`
- verify raw Ed25519 signatures over exact document bytes
- apply product SemVer monotonicity and the compatibility transition rules

The product Rust parser currently covers canonical bytes, duplicate keys, unique filenames, order, bounds, closed inventory, and artifact hashing. Cross-language Kotlin, native Rust, and installer conformance, agent-catalog correlation, and release-role signature verification remain separate consumer gates; the product schema test does not claim them.

`fixtures/wire/` additionally fixes exact bytes for the shared operation, installed-inspection, startup-mode, status, and uninstall-absence documents. The Rust writer owns field order and optional-field omission through strict typed values, while `SHA256SUMS` pins the seven generated files for component equality gates. These fixtures do not prove that a component consumer has imported or accepted the corpus; each consumer repository must run its own equality and parsing gate.
