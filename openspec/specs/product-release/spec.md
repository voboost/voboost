# Product Release specification

## Purpose

Define the product-owned source definition, canonical `voboost.json`, signed `channel.json` pointer, exact-byte artifact reuse, and the boundary between local Rust authoring and protected publication.

## Requirements

### Requirement: A source definition binds one product release to exact sources and gates

`release/<release-id>.toml` SHALL use schema `1`, a canonical SemVer equal to its filename, channel `stable` or `beta`, an optional older `base_release`, positive compatibility values, non-empty host, emulator, device, and vehicle gate lists, and full 40-character lowercase Git commits for `app`, `agents`, `inject`, `installer`, and `logo`. Unknown fields, including the retired `license` repository key, SHALL be rejected. Per-artifact `mode = "build"` and `mode = "reuse"` fields SHALL NOT be accepted because final bytes determine reuse automatically.

#### Scenario: A complete beta source definition is read

- **WHEN** the CLI reads a schema-1 beta definition with a matching filename, five full commits, positive compatibility values, and all four gate classes
- **THEN** it accepts the definition without any per-artifact mode

#### Scenario: A bounded source-definition field is invalid

- **WHEN** schema, channel/version relation, base ordering, filename, compatibility, a gate class, or a repository commit violates its bound
- **THEN** the CLI rejects the definition before artifact composition

#### Scenario: A manual artifact mode is present

- **WHEN** a source definition contains `mode = "build"` or `mode = "reuse"`
- **THEN** strict TOML decoding rejects the unknown field

#### Scenario: A retired source repository is supplied

- **WHEN** a definition adds a license pin to the five valid active component pins
- **THEN** strict decoding rejects the retired key before composition

# Gate: cargo test --manifest-path release/Cargo.toml definition::tests::source_definition_conformance_rejects_every_bounded_field_class -- --exact

Gate contract: Maps: `A source definition binds one product release to exact sources and gates`, `A complete beta source definition is read`, and `A bounded source-definition field is invalid`. Preconditions: The release crate and schema-1 definition fixture are available. Observable result: The named host test accepts the complete fixture, then rejects changed schema, channel/version relation, base ordering, filename, compatibility, and each empty gate class. Evidence boundary: Host Rust validation only; repository checkout, component builds, signatures, and publication are not executed.

# Gate: cargo test --manifest-path release/Cargo.toml definition::tests::rejects_short_commit -- --exact

Gate contract: Maps: `A source definition binds one product release to exact sources and gates` and `A bounded source-definition field is invalid`. Preconditions: A valid source definition is available. Observable result: The named host test replaces one full commit with a short identity and observes rejection. Evidence boundary: Host source-identity syntax only; the commit is not fetched or proven to exist.

# Gate: cargo test --manifest-path release/Cargo.toml definition::tests::rejects_manual_build_or_reuse_mode -- --exact

Gate contract: Maps: `A source definition binds one product release to exact sources and gates` and `A manual artifact mode is present`. Preconditions: A valid source definition is available. Observable result: The named host test adds a manual mode and observes strict unknown-field rejection. Evidence boundary: Host TOML decoding only; component build selection is not executed.

# Gate: cargo test --manifest-path release/Cargo.toml definition::tests::source_definition_excludes_retired_license_repository -- --exact

Gate contract: Maps: `A source definition binds one product release to exact sources and gates`, `A complete beta source definition is read`, and `A retired source repository is supplied`. Preconditions: The release crate and five-component schema-1 fixture are available. Observable result: The named test accepts the valid active input and rejects the same input with the retired repository key. Evidence boundary: Host TOML parsing and validation only; no checkout, build, device or publication runs.


### Requirement: Artifact reuse is derived from final bytes

For every component artifact present in a base and candidate release, the compositor SHALL compare exact size and SHA-256. Equal final bytes SHALL retain the previous artifact identity. Different bytes SHALL be marked changed only when the owning component commit changed. An artifact that changes, appears, or disappears while its owning component commit is unchanged SHALL stop composition as a reproducibility failure. The compositor SHALL NOT silently replace unexpected bytes with base bytes. `restore.json` and `restore.json.sig` SHALL be product-derived authorities rather than inject-only outputs: both SHALL change for every new product release identity, including app-only, agents-only, native-only, and metadata-only releases.

#### Scenario: Candidate component bytes equal the base release

- **WHEN** every candidate component artifact has the same basename, size, and SHA-256 as the verified base closure while the product-derived recovery pair is regenerated
- **THEN** component decisions are `reuse-exact-bytes` and both recovery-authority decisions are `changed`

#### Scenario: Bytes change at an unchanged component commit

- **WHEN** one candidate artifact differs from the verified base artifact but its owning repository commit is unchanged
- **THEN** composition fails with a reproducibility error

#### Scenario: One release delta class changes

- **WHEN** the source definition and final bytes represent an app-only, agents-only, native-only, or metadata-only release
- **THEN** only changed component bytes and the product-derived recovery pair receive changed identities while every other component artifact is reused

# Gate: cargo test --manifest-path release/Cargo.toml manifest::tests::reuses_equal_component_bytes_and_rejects_changed_bytes_at_same_commit -- --exact

Gate contract: Maps: `Artifact reuse is derived from final bytes`, `Candidate component bytes equal the base release`, and `Bytes change at an unchanged component commit`. Preconditions: Temporary complete base and candidate closures and two bound source definitions are available. Observable result: The named host test observes reuse for every equal component file and changed product-derived recovery authority, then changes one APK byte without changing the app commit and observes a reproducibility failure. Evidence boundary: Host filesystem bytes and source-definition identities only; component build reproducibility and remote base-release download are not executed.

# Gate: cargo test --manifest-path release/Cargo.toml manifest::tests::classifies_app_agents_native_and_metadata_only_release_deltas -- --exact

Gate contract: Maps: `Artifact reuse is derived from final bytes` and `One release delta class changes`. Preconditions: One verified base closure, four isolated candidate closures, fixed Android-tool outputs, and an ephemeral restore-role key are available. Observable result: The named host test changes app, agent, native, and product metadata inputs independently and compares every resulting artifact decision with the exact expected set, including regenerated and verified `restore.json` and `restore.json.sig` in all four cases. Evidence boundary: Host authoring decisions only; no component build, protected key, or device recovery runs.

### Requirement: Product authoring extracts APK identity from exact signed bytes

Before authoring `voboost.json`, the compositor SHALL inspect the exact `voboost-app.apk` through `aapt2` and `apksigner` resolved only from pinned Android SDK `build-tools/34.0.0`. Tool output SHALL be bounded. The APK SHALL verify under v2 or v3 with exactly one signer, package name `ru.voboost`, `versionCode` equal to source-definition `android.versionCode` and within `1..=2100000000`, and one lowercase signer-certificate SHA-256. The compositor SHALL independently bind those observed values to exact APK size/SHA-256 and `repositories.app`. An optional APK evidence file SHALL be only a retained output: every field SHALL equal fresh extraction and SHALL NOT override it.

Secretless final verification SHALL independently parse the same exact signed APK as a bounded ZIP and require exactly one central-directory entry named `assets/config/release-public.pem`. It SHALL bound compressed and expanded size, require canonical Ed25519 SPKI PEM bytes, extract the raw 32-byte key, and compare it to source-pinned `productPublicKeyHex`. Missing, duplicate, malformed, or substituted assets SHALL fail even when the APK itself has a valid expected signer certificate.

#### Scenario: Signed APK identity is extracted

- **WHEN** pinned Android tools inspect a single-signer v2/v3 APK from the exact artifact path
- **THEN** the compositor records observed package, version-code, certificate, size, and SHA-256 identity in the app artifact row

#### Scenario: APK evidence attempts to substitute observed identity

- **WHEN** APK bytes remain unchanged but an evidence file changes `versionCode`, `certificateSha256`, source commit, size, or SHA-256
- **THEN** the compositor rejects the evidence before writing `voboost.json`

#### Scenario: APK version code reaches its first-schema boundary

- **WHEN** a manifest app row uses `versionCode=2100000000`, `versionCode=0`, or `versionCode=2100000001`
- **THEN** Draft 2020-12 and the Rust parser accept only `2100000000` and reject zero and the first value above the maximum

#### Scenario: APK embeds a different product trust root

- **WHEN** the exact signed APK has a valid expected Android signer but its product-root asset is missing, duplicated, malformed, or contains another Ed25519 key
- **THEN** secretless final verification rejects the APK before artifact export

# Gate: cargo test --manifest-path release/Cargo.toml manifest::tests::compose_requires_verified_apk_evidence -- --exact

Gate contract: Maps: `Product authoring extracts APK identity from exact signed bytes`, `Signed APK identity is extracted`, and `APK evidence attempts to substitute observed identity`. Preconditions: A complete source definition, flat closure, fixed pinned-tool outputs, and optional APK evidence are available. Observable result: The named host test authors an app row from observed identity, keeps APK bytes unchanged, substitutes version and certificate evidence independently, and observes rejection. Evidence boundary: Product-side command/output binding with deterministic tool fixtures; the real Android toolchain is exercised by the separate pinned-tools gate.

# Gate: cargo test --manifest-path release/Cargo.toml manifest::tests::apk_evidence_fixture_requires_complete_identity -- --exact

Gate contract: Maps: `Product authoring extracts APK identity from exact signed bytes` and `APK evidence attempts to substitute observed identity`. Preconditions: Accepted and missing-certificate evidence-output fixtures are available. Observable result: The named host test accepts the complete retained output shape and rejects the fixture without `certificateSha256`. Evidence boundary: Host evidence-output decoding only; authority still comes from fresh APK inspection.

# Gate: cargo test --manifest-path release/Cargo.toml --features android-integration verify::tests::pinned_android_tools_extract_identity_from_exact_signed_apk -- --exact

Gate contract: Maps: `Product authoring extracts APK identity from exact signed bytes`, `Signed APK identity is extracted`, `APK evidence attempts to substitute observed identity`, and `APK embeds a different product trust root`. Preconditions: `ANDROID_SDK_ROOT` contains Android platform 34 and build-tools `34.0.0`, and `keytool` is available. Observable result: The named test builds and signs temporary APKs with one exact asset, extracts package/version/certificate identity through pinned tools, accepts the matching embedded Ed25519 product root, and rejects missing, malformed, or substituted assets in separately signed APKs carrying the same valid certificate. Evidence boundary: Real host Android packaging/signing tooling and ephemeral roots only; protected production signer, reproducible application build, PackageManager, device, and vehicle are not exercised.

# Gate: cargo test --manifest-path release/Cargo.toml verify::tests::apk_product_root_rejects_missing_duplicate_malformed_and_substituted_assets -- --exact

Gate contract: Maps: `Product authoring extracts APK identity from exact signed bytes` and `APK embeds a different product trust root`. Preconditions: The bounded APK ZIP/SPKI parser and ephemeral Ed25519 public keys are available. Observable result: The named host test accepts exactly one canonical compressed product-root asset and rejects missing, duplicate central-directory names, malformed PEM/SPKI, and a valid foreign Ed25519 root. Evidence boundary: Synthetic ZIP structure and exact public-key parsing only; Android APK signature verification is covered by the separate ignored pinned-tool gate.

# Gate: cargo test --manifest-path release/Cargo.toml verify::tests::version_code_boundary_fixtures_match_schema_and_parser -- --exact

Gate contract: Maps: `Product authoring extracts APK identity from exact signed bytes` and `APK version code reaches its first-schema boundary`. Preconditions: The checked-in maximum, zero, and maximum-plus-one manifest fixtures are available. Observable result: The named host test runs the Draft 2020-12 schema and Rust parser over all three fixtures and observes the same accept/reject result. Evidence boundary: Cross-language product-manifest value contract on the host; Android PackageManager and other consumer implementations remain separate gates.

### Requirement: Restore authority is canonical, acyclic, and separately signed

`restore.json` SHALL contain only schema `1`, canonical Product SemVer `releaseIdentity`, SHA-256 of exact source-definition bytes, and SHA-256 of canonical restorable artifact rows. The restorable-closure digest SHALL exclude `restore.json` and `restore.json.sig`, preventing a cyclic hash, and SHALL include every other artifact identity and APK metadata. `restore.json.sig` SHALL be a raw 64-byte Ed25519 signature over exact canonical document bytes under the configured restore-role public key. The product compositor SHALL verify shape, release identity, source-definition digest, closure digest, and signature before authoring `voboost.json`. No production private key SHALL be stored in the repository.

#### Scenario: Product release creates restore authority

- **WHEN** protected authoring receives a complete restorable closure and restore-role signing key
- **THEN** it writes canonical `restore.json`, signs the exact bytes, and the compositor verifies the pair under the configured public key

#### Scenario: Recovery authority is malformed or stale

- **WHEN** the document is garbage, retains another release/source identity, describes earlier closure bytes, or carries a substituted signature
- **THEN** composition stops before `voboost.json` is written

# Gate: cargo test --manifest-path release/Cargo.toml manifest::tests::restore_pair_rejects_garbage_stale_identity_stale_closure_and_signature_substitution -- --exact

Gate contract: Maps: `Restore authority is canonical, acyclic, and separately signed`, `Product release creates restore authority`, and `Recovery authority is malformed or stale`. Preconditions: A complete temporary closure, exact source definition digest, pinned-tool fixture, and ephemeral Ed25519 restore-role key are available. Observable result: The named host test accepts the produced pair, then independently rejects garbage JSON, stale release/source identity, changed restorable bytes, and a substituted raw signature. Evidence boundary: Product host producer/verifier with an ephemeral key only; protected key custody and device recovery consumption are separate.

### Requirement: The product manifest is canonical and closed

`voboost.json` SHALL be schema `1`, canonical compact UTF-8 JSON with exactly one final LF, one strict product SemVer, positive compatibility values, and a non-empty artifact array in canonical role/name order. The app row SHALL contain `packageName`, `versionCode` in `1..=2100000000`, and `certificateSha256`. Duplicate keys, unknown fields, duplicate filenames, duplicate singleton roles, invalid SHA-256, zero or oversized files, non-basename paths, incomplete or misplaced APK metadata, unknown artifacts, missing required device artifacts, and a closure without an agent module SHALL be rejected. Artifact identity SHALL use exact file size and lowercase SHA-256.

`verify-manifest` SHALL retain its artifact-directory contract: the directory contains exactly the artifact rows named by `voboost.json`, without the manifest pair. `verify-product-closure` SHALL instead require exactly `voboost.json`, raw 64-byte `voboost.json.sig`, and every declared artifact; for beta1 this is 69 regular non-symlink files from 67 artifact rows. It SHALL reject extra, missing, directory, special, and symlink entries, verify the product signature over exact manifest bytes, and only then verify every artifact size and SHA-256.

#### Scenario: A canonical complete manifest is parsed

- **WHEN** the parser reads the accepted schema-1 conformance fixture
- **THEN** it accepts the manifest and preserves its canonical bytes

#### Scenario: A hostile or incomplete manifest is parsed

- **WHEN** a fixture contains a duplicate key, unknown field, path-bearing filename, or incomplete closure
- **THEN** the parser rejects the whole manifest before artifact use

#### Scenario: A signed product closure is verified

- **WHEN** a directory contains the canonical manifest, its valid product-role signature, and exactly all declared regular artifact files
- **THEN** the product-closure verifier accepts the signed artifact set with its two manifest authority files while the artifact-directory verifier continues to accept only the corresponding artifact files

# Gate: cargo test --manifest-path release/Cargo.toml verify::tests::voboost_fixtures_match_expected_result -- --exact

Gate contract: Maps: `The product manifest is canonical and closed`, `A canonical complete manifest is parsed`, and `A hostile or incomplete manifest is parsed`. Preconditions: The checked-in accepted and rejected `voboost.json` fixture corpus is available. Observable result: The named host test accepts every fixture under `valid` and rejects every fixture under `invalid`, including duplicate-key, unknown-field, path-bearing, and incomplete examples. Evidence boundary: Host Rust parsing and semantic validation only; Ed25519 verification and consumer-language conformance are separate.

# Gate: cargo test --manifest-path release/Cargo.toml verify::tests::product_closure_distinguishes_artifacts_from_the_signed_file_set -- --exact

Gate contract: Maps: `The product manifest is canonical and closed` and `A signed product closure is verified`. Preconditions: A generated complete manifest, matching files, and an ephemeral product-role Ed25519 key are available. Observable result: The named host test accepts the artifact directory only under `verify-manifest`, accepts the signed artifact set with two manifest authority files only under `verify-product-closure`, and rejects extra, missing, and symlink entries. Evidence boundary: Host temporary bytes and an ephemeral key only; protected signing and packaged installer execution are separate.

# Gate: cargo test --manifest-path release/Cargo.toml verify::tests::checked_in_schemas_are_valid_json_and_name_the_wire_roots -- --exact

Gate contract: Maps: `The product manifest is canonical and closed` and `A canonical complete manifest is parsed`. Preconditions: The five checked-in product, channel, restore, local-delivery-evidence, and CI-vehicle-evidence schema files are available. Observable result: The named host test parses all five as JSON and verifies their draft, title, and closed-root declaration. Evidence boundary: Schema document syntax and root metadata only; a third-party JSON Schema engine is not executed.

# Gate: cargo test --manifest-path release/Cargo.toml verify::tests::draft_2020_12_schemas_match_the_shared_fixture_corpus -- --exact

Gate contract: Maps: `The product manifest is canonical and closed`, `A canonical complete manifest is parsed`, and `A hostile or incomplete manifest is parsed`. Preconditions: The Draft 2020-12 schemas, production Rust parsers, and shared accepted/rejected fixture corpus are available. Observable result: The named host test compiles both schemas with format validation and requires the schema engine and production parser to return the expected result for every fixture. Evidence boundary: Host Draft 2020-12 and Rust semantic conformance; canonical raw-byte checks, actual artifact hashing, signatures, and other consumer languages remain additional gates documented in `release/SCHEMA-CONFORMANCE.md`.

# Gate: cargo test --manifest-path release/Cargo.toml verify::tests::semantic_invariants_beyond_json_schema_are_enforced -- --exact

Gate contract: Maps: `The product manifest is canonical and closed` and `A hostile or incomplete manifest is parsed`. Preconditions: One accepted typed product manifest is available. Observable result: The named host test changes canonical artifact order and adds a second row with the same filename but different metadata, then observes semantic rejection for both values. Evidence boundary: Product Rust semantic validation only; Draft 2020-12 cannot express sequence order or key uniqueness over unequal object rows, and other consumers must run the same semantic corpus.

### Requirement: Product release owns one exact cross-component wire corpus

`release/fixtures/wire` SHALL contain exactly `operation-install-complete.json`, `installed-healthy.json`, `installed-unknown-no-version.json`, `startup-mode-hidden.json`, `status-interface.json`, `status-none.json`, `uninstall-absence.json`, and `SHA256SUMS`. Product release tooling SHALL author every JSON file from strict typed schema-1 values in producer field order as compact UTF-8 with exactly one final LF, and SHALL author the filename-sorted lowercase SHA-256 inventory from those exact bytes. The complete Install operation SHALL use status sequence `36`, bind `transactionId` to the same manifest digest used by status, and preserve the shared native `InstalledApk` order `certificateSha256`, `packageName`, `path`, `sha256`, `size`, `versionCode`; the unknown inspection SHALL omit `installedVersion`; target status SHALL use flat `releaseIdentity` and `manifestSha256`, include one complete injection row with both generation counters, set `killed` false for `interface`, and set it true for intentional `none`; and the uninstall observation SHALL contain the exact ordered nine-check external absence predicate. Independently maintained component examples SHALL NOT replace this corpus.

Before local build planning or package composition, `local-beta1-ci.sh` SHALL require that both component mirrors contain exactly the same eight names and byte-identical contents, including `SHA256SUMS`, and SHALL record one corpus digest for each mirror. A missing mirror, extra entry, changed JSON byte, changed final LF or changed checksum inventory SHALL fail before repository or artifact evidence is accepted.

#### Scenario: The checked-in producer corpus is verified

- **WHEN** product verification reads the checked-in wire directory
- **THEN** every name, exact JSON byte sequence, typed field order, optional-field presence, semantic mode pair, and SHA-256 row equals the producer-owned output

#### Scenario: A wire document drifts independently

- **WHEN** a field is reordered, omitted when required, added, duplicated, pretty-printed, changed without advancing its hash, or accompanied by an extra fixture
- **THEN** strict typed canonical parsing or closed-corpus verification rejects it

# Gate: cargo test --manifest-path release/Cargo.toml wire::tests::producer_owned_wire_fixtures_match_checked_in_exact_bytes_and_hashes -- --exact

Gate contract: Maps: `Product release owns one exact cross-component wire corpus` and `The checked-in producer corpus is verified`. Preconditions: The Rust writer and checked-in `release/fixtures/wire` directory are available. Observable result: The named host test reconstructs all seven typed documents, requires byte equality for each checked-in file, recomputes every sorted `SHA256SUMS` row, and rejects inventory differences. Evidence boundary: Product host golden bytes only; component consumer execution and device behavior remain separate gates.

# Gate: cargo test --manifest-path release/Cargo.toml wire::tests::wire_fixtures_are_strict_typed_canonical_schema_one_documents -- --exact

Gate contract: Maps: `Product release owns one exact cross-component wire corpus` and `The checked-in producer corpus is verified`. Preconditions: The shared corpus and product canonical parser are available. Observable result: The named host test parses each fixture through its strict typed schema, verifies omitted unknown-version state, the `interface` and `none` kill-switch relation, and complete uninstall absence. Evidence boundary: Product host schema and selected cross-document invariants only; native or installer consumers are not executed.

# Gate: node --test --test-name-pattern="product wire corpus is byte-identical in every component mirror" tools/tests/beta1-product-workflow.test.ts

Gate contract: Maps: `Product release owns one exact cross-component wire corpus` and `The checked-in producer corpus is verified`. Preconditions: The product, inject and installer repositories are present in one workspace. Observable result: The test requires the exact eight-entry inventory and byte equality for every product and component mirror file, while the executed local planner records both mirror digests. Evidence boundary: Cross-repository host byte equality only; component production parsers and device behavior remain their own gates.

# Gate: cargo test --manifest-path release/Cargo.toml wire::tests::wire_parser_rejects_unknown_duplicate_and_noncanonical_fields -- --exact

Gate contract: Maps: `Product release owns one exact cross-component wire corpus` and `A wire document drifts independently`. Preconditions: One accepted installed-inspection fixture and the canonical duplicate-key parser are available. Observable result: The named host test independently adds an unknown field, duplicates `schemaVersion`, and pretty-prints the document and observes rejection. Evidence boundary: Product host hostile parsing only; exhaustive component-specific semantic failures remain component gates.

### Requirement: The product manifest has a detached release-role signature

Published `voboost.json.sig` SHALL contain one raw 64-byte Ed25519 signature over the exact `voboost.json` bytes. The trust root SHALL be embedded in each authorized consumer and SHALL NOT be loaded from the release, channel pointer, app-private storage, or `/data/voboost`. Signing SHALL occur only after final artifact bytes and manifest bytes are fixed.

#### Scenario: A signed product manifest reaches a consumer

- **WHEN** an installer, app, or privileged verifier receives `voboost.json`, its detached signature, and the complete artifact closure
- **THEN** it accepts the manifest only after exact signature, schema, inventory, size, and SHA-256 verification under its embedded release-role trust root

# Unverified: Cross-consumer product signature conformance

### Requirement: The channel pointer is bounded and uses HTTPS

`channel.json` SHALL contain only `schemaVersion`, `channel`, `releaseIdentity`, `manifestUrl`, `manifestSize`, and `manifestSha256`. It SHALL use canonical compact UTF-8 JSON with exactly one final LF, schema `1`, channel-compatible strict SemVer, an HTTPS URL without credentials or fragment, a bounded positive manifest size, and lowercase SHA-256. Duplicate keys, unknown fields, HTTP URLs, malformed channel/version combinations, and non-canonical bytes SHALL be rejected.

#### Scenario: A canonical beta pointer is parsed

- **WHEN** the parser reads the accepted beta channel fixture
- **THEN** it accepts the pointer with its exact immutable manifest identity

#### Scenario: A malformed or expanded pointer is parsed

- **WHEN** a pointer contains a duplicate key, unknown field, or HTTP manifest URL
- **THEN** the parser rejects the complete pointer

# Gate: cargo test --manifest-path release/Cargo.toml verify::tests::channel_fixtures_match_expected_result -- --exact

Gate contract: Maps: `The channel pointer is bounded and uses HTTPS`, `A canonical beta pointer is parsed`, and `A malformed or expanded pointer is parsed`. Preconditions: The checked-in accepted and rejected `channel.json` fixtures are available. Observable result: The named host test accepts the canonical beta fixture and rejects duplicate-key, unknown-field, and HTTP URL fixtures. Evidence boundary: Host Rust parsing and semantic validation only; redirect behavior, signature verification, network origin policy, and channel publication are separate.

# Gate: cargo test --manifest-path release/Cargo.toml verify::tests::channel_authoring_is_canonical -- --exact

Gate contract: Maps: `The channel pointer is bounded and uses HTTPS` and `A canonical beta pointer is parsed`. Preconditions: A valid bounded channel value is available. Observable result: The named host test authors canonical bytes and parses the exact result back to the same typed value. Evidence boundary: Host serialization only; no signature, network access, or remote pointer update occurs.

### Requirement: Channel publication is monotonic and follows immutable release publication

`channel.json.sig` SHALL contain one raw 64-byte Ed25519 signature over exact `channel.json` bytes. A protected product workflow SHALL update a channel pointer only after all immutable release assets have been uploaded, downloaded again, and reverified. The new release identity SHALL be strictly newer than the active identity for the same channel. Stable promotion SHALL reuse verified release bytes without rebuilding them.

#### Scenario: A protected workflow advances a channel

- **WHEN** a newer release has passed every required release-definition gate and remote draft re-download verification
- **THEN** the workflow publishes the immutable GitHub Release before atomically replacing the signed channel pointer

# Unverified: Protected release and channel publication

### Requirement: Beta1 CI separates preparation, protected signing, and secretless verification

The beta1 CI workflow SHALL run only by manual dispatch with the full pushed product commit and six full pushed component commits equal to `release/1.0.0-beta1.toml`. It SHALL default `enable_remote_sources` to false and fail before component checkout while source remotes are unavailable. Admission SHALL require trusted local delivery evidence for passed ephemeral build, vehicle, verifiable uninstall, and clean-state results and SHALL prove that every admitted pushed commit resolves to the exact product/component snapshot tree recorded after those gates. Its default permissions SHALL be only `actions: read` and `contents: read`, its concurrency group SHALL bind all seven commits, and a newer run SHALL NOT cancel an admitted build.

Every product workflow that runs generated agent-document checks SHALL resolve the full `agents` commit from the exact source definition, check out that commit into an isolated path with credentials disabled, and verify exact clean HEAD before supplying explicit absolute root, commit, and clean-checkout inputs in release mode. The generator SHALL read only agent metadata, paired documents, and OpenSpec; it SHALL NOT execute candidate agent code, build scripts, or binaries. Missing, dirty, relative-root, short-commit, and wrong-HEAD inputs SHALL fail. An implicit sibling root MAY be used only in development mode and SHALL NOT validate or author release evidence.

Secretless read-only jobs SHALL produce unsigned or otherwise prepared immutable inputs without production keys. A protected signing job SHALL check out and execute only the exact admitted product repository tooling and SHALL never check out or execute candidate component code after receiving a secret. Keys SHALL be materialized only as temporary files, removed by an exit trap, and their derived public identities SHALL equal one source-pinned trust document before signing. The final trust model SHALL contain the Android certificate and exactly five distinct agent-catalog, product/release-catalog, native-executable, restore, and evidence Ed25519 roots. Native OTA SHALL consume the signed product manifest directly; a legacy native-manifest root or build-only trust section is forbidden.

Before executing the secretless Android native build, CI SHALL materialize canonical PEM values for exactly the four roots required by current privileged binaries: agent-catalog, product/release-catalog, native-executable, and restore. All roots SHALL come from the same source-pinned document and SHALL be passed through the current exact environment names, including `VOBOOST_RESTORE_PUBLIC_KEY_PEM`; obsolete native-manifest/install-authorization input, component fallback, generated hidden root, and mutable variable are forbidden. Desktop installer builds SHALL receive only their directly consumed product/release-catalog, native-executable, and restore roots.

Separate protected environments SHALL apply the Android, agents, native, restore, and product release roles only to exact prepared bytes and SHALL NOT rebuild component source. A later secretless job SHALL locate and re-download the unique final signed-closure artifact from the same run; verify the Actions artifact digest, exact APK certificate, agent catalog signature, every role-bound native executable signature, restore signature, product signatures, consumer-embedded roots, manifest closure, and signed release identity; and export its artifact ID, digest, and canonical payload hash. The signed identity SHALL contain the product commit, exact `beta1-ci.yml` Git blob, exact source-definition SHA-256, and exact trust-document SHA-256.

After secretless final closure verification, three secretless installer jobs SHALL check out the exact pinned installer commit and consume the same closure plus separately downloaded protected role-fixture artifacts. Product-owned tooling SHALL verify each fixture under its source-pinned root and scan freshly built installer executables for exact embedded raw roots before packaging. Windows SHALL build one x86-64 portable EXE with the source-pinned ADB trio; macOS SHALL build one universal application from the same roots and one DMG using installer-pinned universal ADB. Linux SHALL build the standalone `voboost-installer-cli` for `x86_64-unknown-linux-gnu` with `--no-default-features --features cli`, verify ELF/DT_NEEDED/no-GUI/no-network-help properties, append the same exact signed 69-file closure to those verified ELF bytes, reopen the package and embedded roots, and create a bzip2-level-9 deterministic USTAR archive with exactly one regular mode-`0755` `voboost-installer` entry. Linux SHALL use system ADB from `PATH`; it SHALL NOT bundle ADB or a GUI runtime. Each produced platform package SHALL be reopened. A final read-only job SHALL require exactly the three public filenames, compute canonical inventory and hashes, and upload one `beta1-installer-packages` artifact. No package job receives a secret or publication authority.

#### Scenario: Remote sources are not explicitly enabled

- **WHEN** beta1 CI is dispatched with its default remote-source input
- **THEN** admission fails before any component checkout or build

#### Scenario: Exact beta1 sources are admitted

- **WHEN** local delivery evidence passed before source push, all six remote repositories exist, and dispatch inputs equal both the evidence and full commits in the source definition
- **THEN** read-only component preparation feeds protected role-specific signing, followed by secretless final verification without publication authority

# Gate: node --test --test-name-pattern="beta1 CI separates prepared inputs, protected signing, and secretless final verification" tools/tests/beta1-product-workflow.test.ts

Gate contract: Maps: `Beta1 CI separates preparation, protected signing, and secretless verification`, `Remote sources are not explicitly enabled`, and `Exact beta1 sources are admitted`. Preconditions: The beta1 workflow and exact source definition are available. Observable result: The named host test verifies Windows/macOS current-interface package jobs; the Linux dependency-minimal CLI build, ELF gate, exact closure append, reopen, single-entry tar.bz2, byte/mode/hash checks; executable embedded-root scans; protected fixture verification; and exact package aggregation. Evidence boundary: Static workflow contract only; hosted cross-platform builds and package artifacts are not executed.

# Gate: node --test --test-name-pattern="release docs require an explicit clean exact agents checkout" tools/tests/docs-target-structure.test.ts

Gate contract: Maps: `Beta1 CI separates preparation, protected signing, and secretless verification` and `Exact beta1 sources are admitted`. Preconditions: The release agents-input validator and source-shaped temporary directories are available. Observable result: The named host test accepts one explicit absolute clean exact-HEAD checkout and rejects absent, dirty, relative, short-commit, and wrong-HEAD variants. Evidence boundary: Host input policy with injected Git observations only; workflow gates separately verify actual checkout HEAD and status.

# Gate: node --test --test-name-pattern="every generated agent docs workflow consumer pins a clean exact metadata checkout" tools/tests/beta1-product-workflow.test.ts

Gate contract: Maps: `Beta1 CI separates preparation, protected signing, and secretless verification` and `Exact beta1 sources are admitted`. Preconditions: All checked-in product workflows are available. Observable result: The named host test enumerates every workflow containing `npm run check`, `target:docs:check`, or direct agent-status generation and requires exact agents checkout, disabled credentials, clean HEAD validation, explicit release inputs, and no candidate path execution. Evidence boundary: Static complete-consumer enumeration only; GitHub checkout and remote repositories are not executed.

# Gate: cargo test --manifest-path release/Cargo.toml publish::tests::native_signing_is_product_owned_role_bound_and_root_separated -- --exact

Gate contract: Maps: `Beta1 CI separates preparation, protected signing, and secretless verification` and `Exact beta1 sources are admitted`. Preconditions: Product-owned native signing tooling and ephemeral keys are available. Observable result: The named host test signs all five closed native executable roles, verifies each exact role, rejects a foreign root and role substitution, and confirms an embedded root without executing component code. Evidence boundary: Host fixture ELF bytes and ephemeral Ed25519 keys only; production keys, Android binaries, protected environments, and consumer execution are not used.

# Gate: cargo test --manifest-path release/Cargo.toml verify::tests::trust_roots_are_source_pinned_complete_and_role_separated -- --exact

Gate contract: Maps: `Beta1 CI separates preparation, protected signing, and secretless verification` and `Exact beta1 sources are admitted`. Preconditions: The source-pinned beta1 trust document and parser are available. Observable result: The named host test accepts the explicit unprovisioned state, accepts a complete distinct-role fixture, and rejects duplicated or missing authority. Evidence boundary: Source document semantics only; no production public or private root has been provisioned.

# Gate: node --test --test-name-pattern="native build dry run binds the exact Android certificate Make input" tools/tests/beta1-product-workflow.test.ts

Gate contract: Maps: `Beta1 CI separates preparation, protected signing, and secretless verification` and `Exact beta1 sources are admitted`. Preconditions: Current inject Makefile and local/CI orchestration source are available. Observable result: The named host test verifies that `build-uninstall` requires Make `ANDROID_CERT_SHA256`, maps it to Cargo `VOBOOST_ANDROID_CERT_SHA256`, and that local/CI Make invocations pass the exact ephemeral or source-pinned value. Evidence boundary: Source contract only; a separate read-only `make -n build-uninstall` smoke confirms expansion and a malformed value fails before build, while no Android binary is built by the named gate.

# Gate: cargo test --manifest-path release/Cargo.toml publish::tests::packaged_consumer_root_scan_rejects_missing_duplicate_and_substituted_keys -- --exact

Gate contract: Maps: `Beta1 CI separates preparation, protected signing, and secretless verification` and `Exact beta1 sources are admitted`. Preconditions: The product-owned bounded binary-root scanner and synthetic consumer bytes are available. Observable result: The named host test accepts one exact embedded root, rejects a foreign or missing count, accepts exactly two copies for a universal two-slice fixture, and rejects duplicate identity under a one-copy contract. Evidence boundary: Host byte scanning only; actual PE/ELF/Mach-O builds are exercised only by the unexecuted platform workflow jobs.

### Requirement: macOS delivery retains corresponding source outside the DMG

Product packaging SHALL retain `corresponding-source.tar.gz`, `corresponding-source.json`, and `BUILD-MODIFIED.md` as a separate companion to the macOS DMG. Both beta1 workflows SHALL upload these exact three files as `beta1-corresponding-source`, independently of the platform package artifact. The local beta1 constructor SHALL export the same three files in `voboost-corresponding-source-<release>` beside the DMG while preserving its exact bytes. The installer-owned DMG constructor SHALL verify the companion against the product manifest before packaging; the DMG SHALL exclude source archives and source trees while retaining readable licenses and notices in the application.

#### Scenario: CI prepares macOS source delivery

- **WHEN** either beta1 workflow creates the corresponding source from all seven pinned source roots
- **THEN** it passes that directory as a packaging verification input and uploads the three source companion files separately from the DMG

#### Scenario: The local constructor exports macOS delivery

- **WHEN** the local constructor exports its verified DMG and corresponding-source directory
- **THEN** output contains the exact three source files in a versioned companion directory beside the unchanged DMG

#### Scenario: A macOS package is reopened

- **WHEN** the completed DMG is reopened
- **THEN** it contains no source archive or source tree and the application retains its readable licenses and notices

# Gate: node --test --test-name-pattern="CI retains macOS corresponding source as a separate artifact with all seven pinned roots" tools/release/ci-build.test.ts

Gate contract: Maps: `macOS delivery retains corresponding source outside the DMG` and `CI prepares macOS source delivery`. Preconditions: Both checked-in beta1 workflows are available. Observable result: The named host test checks the separate exact source upload, all seven legacy macOS source pins, and the corresponding-source verification input to the DMG constructor. Evidence boundary: Static workflow contract only; hosted CI and final DMG bytes do not run.

# Gate: node --test --test-name-pattern="local macOS output exports the versioned source companion beside the DMG" tools/release/ci-build.test.ts

Gate contract: Maps: `macOS delivery retains corresponding source outside the DMG` and `The local constructor exports macOS delivery`. Preconditions: The actual constructor export block and fixture DMG/source files are available. Observable result: The export block copies exactly the three companion files into the versioned directory and preserves the fixture DMG bytes. Evidence boundary: Host export behavior only; component compilation and DMG construction do not run.

# Unverified: Source-free macOS package bytes

### Requirement: Vehicle evidence has a trusted producer and exact identity

Local and CI-vehicle evidence SHALL be accepted only from the manual `beta1-evidence-ingest.yml` workflow at the exact product commit on `main`. The evidence public root SHALL come only from the source-pinned trust document, never a mutable repository variable. Ingestion SHALL require canonical payload bytes and a valid raw Ed25519 user-machine signature before it adds a closed producer envelope. That envelope SHALL bind repository, workflow path, workflow commit/blob, ref, run ID/attempt, job, and artifact name.

Evidence SHALL contain one canonical, unique, sorted artifact-set document with every filename, owning component, byte size, and SHA-256 plus its deterministic canonical-document digest. Each build, vehicle, uninstall, and clean-state receipt SHALL contain that same artifact-set digest, a fixed sequence number, a distinct nonce, timestamp, previous-receipt digest, and a receipt digest over the canonical receipt body. Local evidence SHALL additionally bind the product commit/tree, product CI workflow blob, source-definition and trust-document SHA-256, and every component commit/tree. CI-vehicle evidence SHALL bind the same product identities, CI run/attempt, and exact final-closure and installer-package artifact IDs, Actions digests, and canonical payload hashes. Ingestion and publication SHALL reconstruct the CI artifact set from downloaded bytes and reject a mismatch.

#### Scenario: An evidence identity is substituted

- **WHEN** a payload or envelope substitutes its producer, workflow blob, product identity, component tree, CI run, artifact identity, passed receipt, or adds an unknown field
- **THEN** ingestion or downstream admission rejects the complete evidence document

# Gate: node --test --test-name-pattern="local evidence rejects forged producer, changed trees, failed receipts, and extra fields" tools/tests/beta1-evidence.test.ts

Gate contract: Maps: `Vehicle evidence has a trusted producer and exact identity` and `An evidence identity is substituted`. Preconditions: The canonical local evidence fixture and product validator are available. Observable result: The named host test accepts the exact fixture, then independently changes producer workflow/blob/job, product identity, component tree, artifact inventory/digest, receipt chain/status, and object shape and observes rejection. Evidence boundary: Host semantic validation only; Ed25519 signing, GitHub producer metadata, commit lookup, and vehicle execution are not performed.

# Gate: node --test --test-name-pattern="CI vehicle evidence rejects foreign producer, artifact, product, and clean-state substitutions" tools/tests/beta1-evidence.test.ts

Gate contract: Maps: `Vehicle evidence has a trusted producer and exact identity` and `An evidence identity is substituted`. Preconditions: The canonical CI-vehicle evidence fixture and product validator are available. Observable result: The named host test accepts the exact fixture, then independently changes trusted producer, CI run/attempt, final artifact ID/digest/payload, downloaded artifact inventory, product identity, and clean-state receipt chain and observes rejection. Evidence boundary: Host semantic validation only; GitHub artifact downloads, user-machine signature verification, uninstall, clean state, and vehicle execution are not performed.

### Requirement: Local beta1 planning records exact checkout and artifact state

The local CI simulator SHALL compare every component checkout HEAD with the source definition; record the product and component exact working-tree Git tree objects without changing their real indexes; record a SHA-256 of each working-tree status; and record exact size/SHA-256 or explicit absence for known app, agent, native, and installer artifacts. Its delivery sequence SHALL be: local ephemeral beta1 build; local vehicle gate over those bytes; verifiable uninstall and clean state; commit planning; commit and source push only when the resulting commits have the recorded trees; beta1 CI from exact pushed SHAs; exact CI artifact download/hash verification; clean CI vehicle gate with uninstall/clean-state receipt; and only then a separate publication decision. Source push SHALL be marked blocked until the local build, vehicle, uninstall, and clean-state evidence passes. Source push SHALL NOT be described as a product release.

#### Scenario: A local beta1 plan is requested

- **WHEN** the simulator receives the Voboost workspace root and an output path
- **THEN** it writes one plan bound to exact HEADs and snapshot trees, dirty-state digests, available artifact bytes, missing artifacts, all nine delivery stages, blocked source push, and blocked publication actions

# Gate: node --test --test-name-pattern="beta1 CI binds exact commits and records the local artifact plan" tools/tests/beta1-product-workflow.test.ts

Gate contract: Maps: `Local beta1 planning records exact checkout and artifact state` and `A local beta1 plan is requested`. Preconditions: The product and six local repositories exist at the commits in `release/1.0.0-beta1.toml`. Observable result: The named host test executes the simulator and verifies HEAD/tree/status repository rows, artifact presence-or-absence, the nine-stage order, source-push prerequisite, and three blocked publication rows. Evidence boundary: Read-only local planning only; no build, vehicle gate, uninstall, commit, push, CI, artifact download, or publication runs.

### Requirement: Local beta1 construction uses one durable test authority session

`release/init-local-test-authority.sh` SHALL create one new private local test authority outside source, temporary, and build/cache trees. It SHALL generate distinct agent-catalog, product/release-catalog, native-executable, and restore Ed25519 roles, one Android JKS with a 365-day local test certificate, and the three signed public role fixtures. The authority directory SHALL have mode `0700`, private key and JKS files SHALL have mode `0600`, and an existing or aliased path SHALL fail without overwrite. Production and CI signing custody remain unchanged.

`release/build-local-beta1.sh` SHALL require the separately completed component host gates, freeze product and component working-tree identities before construction, reserve a new durable sibling `<output>.work`, and require one explicit separately retained local test authority before artifact construction. It SHALL NOT select an authority by glob or generate replacement keys. Failure, signal, and success SHALL NOT automatically delete its work, partial output, or keys. Artifact construction SHALL call each component's release build entrypoint, not repeat a general host `check` or depend on an ambient documentation executable. Evidence authority SHALL NOT be generated during build: it belongs to the later vehicle-evidence operation. Component code SHALL receive only public roots; product-owned tooling SHALL sign APK, catalog, role-bound native executables, restore authority, and product manifest. The orchestrator SHALL independently verify APK signer and embedded product root, catalog/native/restore/product signatures, every consumed embedded root, exact reopened closure/package, and unchanged source snapshots before exporting output.

The final local step SHALL call the current installer-owned `scripts/build-macos-universal-app.sh` with the exact session public roots and protected-shape fixture signatures, then call the eight-argument `offline-package.ts build-mac-dmg` interface, including the separately verified corresponding-source input. The DMG constructor SHALL use the universal ADB pinned by the same installer source; no unrelated prebuilt application or ambient ADB argument is accepted. Before output the orchestrator SHALL reopen and reverify closure and package bytes and persist all local test public identities, Android certificate, source HEAD/tree/status rows, manifest/signature/closure/package hashes, and an honest declaration that private keys remain in the sibling work authority.

The requested output and its sibling work directory SHALL be absolute nonexistent paths whose basenames are bounded. Shared canonicalization SHALL resolve the nearest existing ancestor before any key generation or build and reject containment in any of the seven source repositories, system temporary roots, or build/cache paths, including through symlink ancestors and nonexistent suffixes. Repository tree/status snapshots SHALL be checked again after final output copy, and `output-identity.txt` SHALL be authored only after that final check. A retained partial output without this marker is not a complete build.

Local byte construction SHALL NOT imply vehicle readiness before the integrated emulator gateway passes with one complete signed closure. Evidence SHALL record `runtimeReadiness=blocked-emulator-gateway-gate` until that gate succeeds. After the Android 9 app-daemon transport gate has passed, local output SHALL use `ready-for-local-vehicle-gate` and CI output SHALL use `ready-for-ci-vehicle-gate`; each value authorizes only its named next gate and SHALL coexist with `vehicle=not-run` until a separate signed receipt exists. Source completion, a host-built package and emulator transport do not constitute vehicle evidence.

#### Scenario: A local output aliases source, temporary, or build state

- **WHEN** a requested nonexistent output is inside a source repository, system temporary tree, build/cache path, or reaches source through an existing symlink ancestor
- **THEN** canonical ancestor resolution rejects it before key creation or build and creates no output

#### Scenario: A local test authority is initialized

- **WHEN** the initializer receives one new durable authority path and local signing tools are available
- **THEN** it creates private role material and fixtures that a new verifier process accepts, while a repeated initialization fails without changing the authority

#### Scenario: An emulator build fails after reserving work

- **WHEN** a builder child fails after the durable sibling work and source snapshot exist
- **THEN** work, snapshot, and authority remain unchanged, and the same output attempt is refused

# Gate: node --test --test-name-pattern="durable local beta1 paths reject source temp cache and symlink outputs" tools/tests/beta1-product-workflow.test.ts

Gate contract: Maps: `Local beta1 construction uses one durable test authority session` and `A local output aliases source, temporary, or build state`. Preconditions: The seven local repositories and filesystem symlink support are available. Observable result: The named host test rejects temporary, source, build/cache, and symlink-aliased output locations before creation. Evidence boundary: Host path behavior only; key generation, component builds, packages, vehicle, source mutation, and publication do not run.

# Gate: node --test --test-name-pattern="durable local beta1 authority is private non-overwriting and verifies after reopen" tools/tests/beta1-product-workflow.test.ts

Gate contract: Maps: `Local beta1 construction uses one durable test authority session` and `A local test authority is initialized`. Preconditions: Local Cargo, OpenSSL, and keytool are available. Observable result: The named host test checks mode `0700`, every private mode `0600`, four 32-byte Ed25519 keys, all three role signatures in new processes, and unchanged private bytes after refused overwrite. Evidence boundary: Local test authority bytes only; production keys, component builds, packages, vehicle, and publication do not run.

# Gate: node --test --test-name-pattern="machine closure chain keeps durable output reservation and profile markers" tools/tests/beta1-product-workflow.test.ts

Gate contract: Maps: `Local beta1 construction uses one durable test authority session` and `An emulator build fails after reserving work`. Preconditions: The build-closure TS chain sources are present. Observable result: The named host test requires the single durable-output reservation entrypoint (reserveOutput single-owner semantics are covered by closure-primitives.test.ts) and the retired-marker rejection table kept by the chain. Evidence boundary: Chain contract only; component compilation, complete closure, AVD, vehicle, and publication do not run.

### Requirement: AVD beta1 uses one complete signed production-path closure

The machine-profile closure chain (`tools/release/build-closure.ts` with `tools/release/closure-steps.ts`) SHALL build current app, agents and production-path native inputs from source through one reserved durable output. The emulator builder is retired with the single machine-profile decision (a legacy `release/build-emulator-beta1.sh` no longer exists): the machine closure IS the production-path closure used for AVD runs. The chain SHALL reserve a new durable output, preserve incomplete work on failure, sign and verify the closed product inventory and all consumed embedded roots, and reject every retired emulator marker during the `verify-profile-markers` step. The closure SHALL contain the production init definition and no policy-skip branch.

The completed output SHALL contain the signed closure, inventory, source snapshot and completion marker. Its evidence SHALL contain an empty privileged-marker list, and set vehicle and publication proof to false on an AVD run because an emulator run is not vehicle acceptance.

#### Scenario: A complete production-path AVD closure is requested

- **WHEN** current sources, NDK/SDK and one complete persistent local test authority are supplied
- **THEN** the machine-profile closure chain produces one closed signed production product set rejecting every retired marker, or retains incomplete work for diagnosis

# Gate: node --test --test-name-pattern="AVD beta1 builder composes a closure with production SELinux behavior" tools/tests/beta1-product-workflow.test.ts

Gate contract: Maps: `AVD beta1 uses one complete signed production-path closure` and `A complete production-path AVD closure is requested`. Preconditions: The chain sources are present. Observable result: The test requires the retired-marker rejection table and the production-init contract in the closure chain. Evidence boundary: Static orchestration contract; source builds and AVD runtime are separate evidence.

### Requirement: A functional emulator cycle has closed action authority

The product runner SHALL preserve the original seventeen functional case IDs and add exactly ten required cases, recording all twenty-seven in canonical number order. Original cases 1–17 SHALL retain their relative execution order; additional observations SHALL execute before the destructive Settings uninstall, and the cross-cutting boot aggregate SHALL be finalized afterward. Android actions SHALL use only the verified packaged installer CLI and its closed emulator requests; machine observation SHALL use only the official Voyemu CLI. The required runner SHALL NOT invoke ADB directly, accept caller scripts, shell, coordinate presets or passed results, or perform fault cuts. Prepared runtime PID/port and immutable package, closure, DMG, machine manifest and Voyemu entry-point identities SHALL be revalidated before actions.

#### Scenario: A caller supplies a functional cycle config

- **WHEN** a config names canonical data inputs and a new evidence output
- **THEN** preflight rejects unknown control fields and foreign fixture bytes, preserves the exact twenty-seven case identities, and does not reinterpret schema-1 transaction receipts

#### Scenario: Runtime identity or immutable bytes change

- **WHEN** an admitted runtime PID/port or frozen input bytes change before a product action
- **THEN** the action is refused and the borrowed runtime is not stopped or replaced

#### Scenario: A semantic UI action uses an observation

- **WHEN** a target is ambiguous, foreign, stale, from another boot, or the input field/layout is not freshly proven
- **THEN** the runner does not send a guessed tap, coordinate preset or substitute text injection

#### Scenario: The prepared OEM check inventory is incomplete

- **WHEN** Voyemu verify omits, duplicates or adds IDs, changes prepared expectations, lacks actual fields, or reports failure
- **THEN** the report cannot authorize readiness; scalar equality is enforced only for source-defined equality checks

#### Scenario: An installer observation deadline expires

- **WHEN** a device transaction child remains unresolved at the observation deadline
- **THEN** the runner preserves its PID and retained lease, performs no interruption, and refuses subsequent mutations

#### Scenario: Normal or Settings removal is observed

- **WHEN** normal CLI removal or the pre-armed Settings observer returns
- **THEN** success requires the exact nine-check receipt without emergency fallback, and only the exact standalone armed marker permits the Settings action

#### Scenario: A required observation is unavailable

- **WHEN** current CAN, catalog, locale, font/order provenance, source-bound visual review, first-start cells or outage observations are not exposed
- **THEN** the corresponding subcheck records unavailable and overall success is forbidden; historical manual receipts are not executable runner inputs

#### Scenario: Guest boots are observed during an installer and functional actions

- **WHEN** the installer or a functional action reboots the guest
- **THEN** each boundary has independent new-boot, full prepared inventory, Enforcing and fresh UI coverage; missed, offline or crossed intermediate windows remain unavailable rather than inheriting the final boot result

# Gate: node --test --test-name-pattern="functional inventory preserves original IDs and executes extras before final Settings uninstall" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `A functional emulator cycle has closed action authority`; Scenario `A caller supplies a functional cycle config`. Preconditions: Product-owned plan and dispatcher are imported on the host. Observable result: Exact original IDs, twenty-seven unique cases, original relative order and late boot aggregation are checked. Evidence boundary: Host schedule authority only; Android actions do not run.

# Gate: node --test --test-name-pattern="functional config rejects caller scripts coordinates passed flags and foreign fixture bytes" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `A functional emulator cycle has closed action authority`; Scenario `A caller supplies a functional cycle config`. Preconditions: Temporary host inputs are available. Observable result: Closed config accepts data and rejects caller control fields and foreign fixture bytes. Evidence boundary: Host preflight only.

# Gate: node --test --test-name-pattern="prepared exact inventory rejects partial duplicate unexpected expected mismatch and missing actual" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `A functional emulator cycle has closed action authority`; Scenario `The prepared OEM check inventory is incomplete`. Preconditions: Source-derived prepared check inventory and captured host envelopes are available. Observable result: Partial, duplicate, unexpected, wrong expected, missing actual and invalid scalar observations are rejected; routing arrays are not equated to scalar display IDs. Evidence boundary: Host protocol validation only, not live OEM readiness.

# Gate: node --test --test-name-pattern="fresh semantic UI nodes reject stale foreign snapshots before sending a tap" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `A functional emulator cycle has closed action authority`; Scenario `A semantic UI action uses an observation`. Preconditions: Bounded hierarchy fixtures and an observed adapter are available. Observable result: Stale or copied nodes never reach the tap adapter. Evidence boundary: Host observation ownership only, not real keyboard/editor behavior.

# Gate: node --test --test-name-pattern="normal removal rejects emergency incomplete absence and unsuccessful process receipts" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `A functional emulator cycle has closed action authority`; Scenario `Normal or Settings removal is observed`. Preconditions: Captured receipt variants are available. Observable result: Emergency, incomplete and unsuccessful receipts fail normal absence parsing. Evidence boundary: Host receipt parser only.

# Gate: node --test --test-name-pattern="observer armed event accepts only an exact marker line amid ordinary diagnostics" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `A functional emulator cycle has closed action authority`; Scenario `Normal or Settings removal is observed`. Preconditions: Chunked diagnostic text is available. Observable result: Only the exact marker line is armed; prefixed or partial lookalikes are rejected. Evidence boundary: Host event recognition only.

# Gate: node --test --test-name-pattern="public dispatcher uses only packaged CLI and Voyemu and retains an unresolved transaction lease" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `A functional emulator cycle has closed action authority`; Scenario `An installer observation deadline expires`. Preconditions: Public dispatcher and a timeout-producing host process adapter are available. Observable result: Only installer/Voyemu invocations occur; timeout retains the child PID/lease and subsequent cases remain not-run. Evidence boundary: Host dispatcher behavior; the adapter does not prove a real device transaction.

# Gate: node --test --test-name-pattern="installer observation timeout leaves its owned host child alive until natural completion" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `A functional emulator cycle has closed action authority`; Scenario `An installer observation deadline expires`. Preconditions: A temporary owned host executable completes after the observation deadline. Observable result: Its PID remains alive at timeout and it writes its completion artifact naturally. Evidence boundary: Actual host child lifecycle only, not Android teardown.

# Gate: node --test --test-name-pattern="runtime replacement before product action prevents mutation and never stops borrowed machine" tools/tests/voyemu-gate.test.ts

Gate contract: Maps: Requirement `A functional emulator cycle has closed action authority`; Scenario `Runtime identity or immutable bytes change`. Preconditions: Host process replies change the runtime identity. Observable result: The public dispatcher rejects the action without starting/stopping the machine. Evidence boundary: Host lease admission only.

# Gate: node --test --test-name-pattern="changed immutable package bytes prevent the next product mutation" tools/tests/voyemu-gate.test.ts

Gate contract: Maps: Requirement `A functional emulator cycle has closed action authority`; Scenario `Runtime identity or immutable bytes change`. Preconditions: Temporary package bytes change after verification. Observable result: The next product mutation is refused. Evidence boundary: Host immutable-input enforcement only.

# Gate: node --test --test-name-pattern="functional schema rejects old phase receipts caller extras and unavailable overall pass" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `A functional emulator cycle has closed action authority`; Scenarios `A caller supplies a functional cycle config` and `A required observation is unavailable`. Preconditions: Schema-3 receipt fixtures are available. Observable result: Legacy receipts without preparation, extra-field, partial-inventory and unavailable overall-pass receipts are rejected. Evidence boundary: Host schema validation only.

# Gate: node --test --test-name-pattern="keyboard actuation requires exact empty focused editor and a live unique layout key" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `A functional emulator cycle has closed action authority`; Scenario `A semantic UI action uses an observation`. Preconditions: Host hierarchy fixtures expose editor focus, text and actual key labels. Observable result: Exact empty editor, focus, package, layout cardinality and key uniqueness are enforced before a key is selected. Evidence boundary: Host dispatcher/parser only; Android and real UI actions do not run.

# Gate: node --test --test-name-pattern="none process absence rejects permission denial malformed inventory and resident names" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `A functional emulator cycle has closed action authority`; Scenario `A required observation is unavailable`. Preconditions: Fixed inspect process section variants are available. Observable result: Permission denial and malformed inventories remain unavailable; resident Voboost names fail none absence. Evidence boundary: Host dispatcher/parser only; Android and real UI actions do not run.

# Gate: node --test --test-name-pattern="public dispatcher executes original install removal reinstall before a genuine missing-tab failure" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `A functional emulator cycle has closed action authority`; Scenario `A caller supplies a functional cycle config`. Preconditions: A host process adapter provides successful original installation/removal/reinstallation receipts and a missing tab hierarchy. Observable result: The public dispatcher executes cases1–4 in order, preserves unavailable intermediate boot coverage, then fails case5 without a fabricated tab pass. Evidence boundary: Host dispatcher/parser only; Android and real UI actions do not run.

# Unverified: Complete live functional cycle and intermediate boot coverage

### Requirement: Publication reuses exact CI bytes after clean vehicle evidence

The beta1 publication workflow SHALL be a separate manual, non-cancelling decision disabled by default. Admission SHALL verify a successful exact beta1 CI run; the product commit, CI workflow blob, source-definition and trust-document SHA-256; exact final-closure and installer-package artifact IDs, Actions digests, canonical extracted payload hashes, and reconstructed artifact inventory; and trusted signed user-machine evidence bound to those same identities after a clean CI vehicle gate, verifiable uninstall, and clean state. Publication SHALL prove the exact product checkout clean before downloading those admitted artifacts outside the checkout and SHALL NOT rebuild, repackage, or re-sign any file. In the same shell step immediately before its first mutation it SHALL recompute both admitted payload hashes and require the exact closed package inventory.

Publication SHALL use authenticated GitHub APIs to create an annotated tag targeting the exact product commit or verify an existing tag has that target, then create or resume one draft. Existing assets SHALL be byte-compared rather than overwritten, missing assets SHALL be uploaded, and the complete draft SHALL be re-downloaded and compared. An already public release SHALL be accepted as idempotently complete only when its exact tag, title, prerelease state, target, and complete asset bytes match; any difference SHALL fail without overwrite or deletion. A separately enabled channel operation SHALL accept an already-published exact signed channel as complete after an ambiguous result and SHALL reject absent or different bytes; new channel mutation remains unavailable until immutable hosting is configured. Source push alone SHALL NOT admit publication. Only the publication job MAY receive `contents: write`.

#### Scenario: Current publication prerequisites are absent

- **WHEN** a caller dispatches publication while the decision variable, exact CI artifact IDs/hashes, or matching clean vehicle/uninstall evidence are absent
- **THEN** admission fails or downstream mutation jobs remain skipped before tag, GitHub Release, or channel mutation

# Gate: node --test --test-name-pattern="publication stays behind manual protected evidence gates and is disabled by default" tools/tests/beta1-product-workflow.test.ts

Gate contract: Maps: `Publication reuses exact CI bytes after clean vehicle evidence` and `Current publication prerequisites are absent`. Preconditions: The publication workflow is available with default-disabled decision input and no enabling repository variable. Observable result: The named host test verifies exact CI/product/workflow/definition and artifact identities, trusted evidence binding to the same bytes, clean vehicle/uninstall state, clean checkout before outside-checkout downloads, absence of build/sign commands, write permission isolation, exact tag target, resumable draft behavior, compare-without-clobber assets, full draft re-download ordering, separate channel gate, and current explicit channel failure. Evidence boundary: Static fail-closed publication policy only; no source push, CI run, user-machine gate, Git tag, GitHub Release, or channel mutation occurs.

### Requirement: Android CI resolves explicit shared build inputs

Secretless Android preparation SHALL require a full `codestyle_sha` dispatch input, resolve its remote commit tree during admission, and check out that exact clean commit as sibling `voboost-codestyle` before executing app or agents Gradle. The concurrency group SHALL bind this build input. Agents acceptance SHALL additionally receive the admitted inject checkout and the hash of the freshly built host OEM driver; assumption-skipped driver tests SHALL NOT count as acceptance.

#### Scenario: Android preparation consumes shared Gradle and OEM driver inputs

- **WHEN** admitted app and agents preparation jobs run
- **THEN** both jobs verify the exact codestyle HEAD and tree in the owning sibling path and agents check receives explicit inject root and driver SHA-256

# Gate: node --test --test-name-pattern="Android CI resolves exact codestyle siblings and the OEM driver input" tools/tests/beta1-product-workflow.test.ts

Gate contract:
Maps: `Android CI resolves explicit shared build inputs`; `Android preparation consumes shared Gradle and OEM driver inputs`.
Preconditions: The current workflow and component build-input contracts are available.
Observable result: The named host test verifies required full-SHA admission, remote tree resolution, immutable credential-free sibling checkouts, clean identity checks, concurrency binding and explicit OEM driver inputs.
Evidence boundary: Static workflow contract only; GitHub jobs, remote repositories and Android artifacts are not executed by this gate. Codestyle remains a separate recorded build input rather than a newly pinned source-definition component.

### Requirement: Agent toggle readback waits for current Settings authority

After one necessary checkbox tap, the functional runner SHALL wait at most fifteen seconds for the same product, catalog and resident generation with a newer exact Settings document revision, active Settings, and the requested agent selection. Waiting SHALL use read-only resident observations and SHALL NOT replay the mutation or replace a failed UI capture with a guessed window. Once ready, the runner SHALL reacquire the visible checkbox through the owned panel and verify its value and retained Voboost tab; it SHALL NOT assume that process recreation preserves viewport coordinates.

#### Scenario: Settings is replaced after a checkbox mutation

- **WHEN** the first resident observation still belongs to the old revision or Settings is waiting
- **THEN** it cannot authorize UI readback, while a newer active revision with the requested selection can

#### Scenario: Root observation invalidates the prior UI node

- **WHEN** toggle preparation reads resident status after an earlier UI snapshot
- **THEN** it obtains a new UI snapshot and returns only its owned checkbox; the earlier node cannot reach the tap adapter

#### Scenario: Resident identity or exact revision is invalid

- **WHEN** a readback changes product identity or supplies an imprecise numeric revision
- **THEN** the runner rejects it rather than retrying that authority failure

# Gate: node --test --test-name-pattern="agent toggle readiness" tools/tests/functional-observations.test.ts

Gate contract: Maps: Requirement `Agent toggle readback waits for current Settings authority`; Scenarios `Settings is replaced after a checkbox mutation`, `Resident identity or exact revision is invalid`. Preconditions: The production readiness predicate receives exact nineteen-digit revisions, waiting/active/failed Settings, selection changes and foreign identity counterfactuals. Observable result: Stale, waiting, wrong-selection and malformed authority cannot promote readback; a new matching revision can. Evidence boundary: Host predicate only; actual process replacement and panel reacquisition require packaged target evidence.

# Gate: node --test --test-name-pattern="agent toggle input reacquires a fresh node after root resident observation" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Agent toggle readback waits for current Settings authority`; Scenario `Root observation invalidates the prior UI node`. Preconditions: The real InstallerUi observes a checkbox, then production toggle preparation reads a bounded resident receipt and reacquires the checkbox. Observable result: The old node is rejected without input and the returned node sends exactly one tap. Evidence boundary: Host UI epoch and adapter authority; target Activity restoration remains separate evidence.

### Requirement: Constructor rejects Android source counter disagreement before building

The closure constructor SHALL compare the app owner's single literal positive bounded `appVersionCode` declaration with the frozen product Android counter before release helpers or component builds. Missing, duplicate, dynamic, invalid or mismatched declarations SHALL fail rather than waste a build or compose inconsistent Android identity.

#### Scenario: Source and frozen counters differ

- **WHEN** the source declares a different counter from the frozen definition
- **THEN** preflight rejects with both counters while matching counters pass

#### Scenario: Source declarations are ambiguous or dynamic

- **WHEN** the declaration is missing, duplicate, dynamic or outside the supported range
- **THEN** preflight rejects

# Gate: node --test --test-name-pattern="Android source preflight admits matching counter and rejects the observed cross-owner mismatch" tools/release/android-version-preflight.test.ts

Gate contract: Maps: Requirement `Constructor rejects Android source counter disagreement before building`; Scenario `Source and frozen counters differ`. Preconditions: Production preflight receives literal app source and frozen counters. Observable result: Matching counters pass and the observed mismatch rejects with both values. Evidence boundary: Host source agreement only; successful APK construction and installed version remain separate evidence.

# Gate: node --test --test-name-pattern="Android preflight fails closed for ambiguous, dynamic and invalid source declarations" tools/release/android-version-preflight.test.ts

Gate contract: Maps: Requirement `Constructor rejects Android source counter disagreement before building`; Scenario `Source declarations are ambiguous or dynamic`. Preconditions: Production preflight receives every named invalid declaration class. Observable result: Missing, dynamic, nonpositive, oversized and duplicate declarations fail. Evidence boundary: Host source parsing only; constructor ordering is additionally inspected by the owning source-order regression and does not prove a completed build.

# Gate: node --test --test-name-pattern="Constructor checks source agreement before release helpers and component builds" tools/release/android-version-preflight.test.ts

Gate contract: Maps: Requirement `Constructor rejects Android source counter disagreement before building`; Scenarios `Source and frozen counters differ`, `Source declarations are ambiguous or dynamic`. Preconditions: The owning constructor source is read by its maintained order regression. Observable result: Source agreement preflight precedes release-helper and observation-helper invocation. Evidence boundary: Static maintenance ordering check only; direct parsing and rejection are proved by the separate named preflight gates, and no successful build is inferred.

### Requirement: Startup reports distinguish discovery and preparation from backend receipts

The functional runner SHALL bind daemon preparation diagnostics to the current boot, daemon lifetime, installed generation and attested target lifetime. It SHALL match the original successful dispatch timestamp exactly, preserve the first observation interval across attempts, and report preparation separately from backend receipts. Missing original diagnostics, failed preparation and a rebind alone SHALL NOT establish original startup timing. Owner-clock intervals SHALL NOT be presented as process birth, CAN execution latency or an invented latency SLA.

#### Scenario: Slow preparation is hidden by the legacy ready metric

- **WHEN** preparation takes time before the existing ready timestamp
- **THEN** the report exposes that preparation interval even when ready-to-dispatch is zero

#### Scenario: A later successful retry replaces a missing original event

- **WHEN** only a later dispatch event remains in the archive
- **THEN** original startup preparation remains unavailable

# Gate: node --test --test-name-pattern="slow preflight remains visible when legacy ready-to-dispatch is zero" tools/emulator/startup-preparation.test.ts

Gate contract: Maps: Requirement `Startup reports distinguish discovery and preparation from backend receipts`; Scenario `Slow preparation is hidden by the legacy ready metric`. Preconditions: A qualified owner-clock fixture has 100 milliseconds of preparation and zero legacy ready-to-dispatch. Observable result: Preparation and observation uncertainty remain explicit numeric intervals, with process birth marked not measured. Evidence boundary: Host diagnostic calculation only; native capture and actual vehicle timing require runtime evidence.

# Gate: node --test --test-name-pattern="missing first event cannot be repaired by substituting a later successful retry" tools/emulator/startup-preparation.test.ts

Gate contract: Maps: Requirement `Startup reports distinguish discovery and preparation from backend receipts`; Scenario `A later successful retry replaces a missing original event`. Preconditions: An attested first dispatch timestamp and only a different later successful event are supplied. Observable result: The result is unavailable rather than a substitute first-start timing. Evidence boundary: Host identity and attempt binding only.

### Requirement: Startup receipt acceptance includes independent current CAN getters

The beta1 startup callback SHALL require an owned native-session CAN getter sample for Forced EV enabled and Pedestrian Warning disabled after the qualified backend readback. The sample SHALL match the guest boot and session and use Android boot-clock time. This sampled current state SHALL NOT be interpreted as continuous state coverage or write attribution.

#### Scenario: Startup report is emitted after the dedicated boot

- **WHEN** the callback prepares retained intent and performs its dedicated fresh reboot
- **THEN** it requires first-dispatch preparation and a separate native CAN sample before writing its report

#### Scenario: CAN sample precedes confirmed application or belongs to another boot

- **WHEN** the independent sample has stale boot, session or Android clock identity
- **THEN** startup CAN evidence rejects it

# Gate: node --test --test-name-pattern="case27 actual callback binds first-dispatch preparation and independent CAN after fresh reboot" tools/emulator/startup-milestones.test.ts

Gate contract: Maps: Requirement `Startup receipt acceptance includes independent current CAN getters`; Scenario `Startup report is emitted after the dedicated boot`. Preconditions: The actual callback is executed with controlled action and observation adapters. Observable result: It prepares both intents, reboots once, binds preparation, requests the two native CAN getters and includes the independent result and action identity; unavailable preparation rejects. Evidence boundary: Host callback composition only; real CAN values are checked separately and runtime acceptance remains open.

# Gate: node --test --test-name-pattern="wrong boot, session and clock reject stale samples" tools/emulator/startup-can.test.ts

Gate contract: Maps: Requirement `Startup receipt acceptance includes independent current CAN getters`; Scenario `CAN sample precedes confirmed application or belongs to another boot`. Preconditions: Independent getter fixtures vary boot, session, clock domain and readback ordering. Observable result: Each stale or mixed-clock fixture is rejected. Evidence boundary: Host source qualification only; no physical vehicle action is inferred.

### Requirement: Beta1 closure includes QNX daemon and native font target

The product closure SHALL contain exactly one artifact for each of `voboost-cluster-daemon`, `voboost-cluster-startup.sh`, `libvoboost-cluster-inject.so`, `libvoboost-runtime-cluster.so`, and `libagent-font-cyrillic.so`. Native ELF artifacts SHALL carry ABI `qnx-aarch64` and their canonical `qnx-*` role; the startup script SHALL omit ABI. Compatibility `agentCatalogSchema` SHALL equal `2`. Additional native agent modules SHALL use repeatable role `qnx-agent-module` with the same ABI and filename grammar `libagent-<lowercase-alphanumeric-hyphen-id>.so`.

#### Scenario: Complete native closure is parsed

- **WHEN** the parser receives a complete product manifest with all canonical QNX artifacts, optionally including an additional correctly named native module
- **THEN** both Rust and Draft 2020-12 validation accept its artifact contract

#### Scenario: Native artifact is missing or misclassified

- **WHEN** a required native artifact is missing or its role, ABI, or activation differs from the canonical filename
- **THEN** both validators reject the manifest

# Gate: cargo test --manifest-path release/Cargo.toml verify::tests::qnx_closure_contract_rejects_missing_and_misclassified_artifacts -- --exact

Gate contract: Maps: `Beta1 closure includes QNX daemon and native font target`, `Complete native closure is parsed`, and `Native artifact is missing or misclassified`. Preconditions: The canonical complete fixture and Draft 2020-12 schema are available. Observable result: Both validators accept the complete closure and an additional native module, reject malformed native module names, and reject each of the five missing QNX artifacts, wrong role, activation, or ABI, and catalog compatibility 1. Evidence boundary: Host manifest conformance only; cross-build bytes, guest startup, font loader behavior, restoration, and vehicle readiness are separate gates.

## Unverified

### Unverified: Cross-consumer product signature conformance

Maps: `The product manifest has a detached release-role signature` and `A signed product manifest reaches a consumer`.

Unavailable observation: Exact raw Ed25519 verification, embedded trust-root equality, closed inventory parsing, and artifact verification across the installer, Android app, and privileged native consumer.

Why unavailable: This lane implements the product-side schema and host authoring verifier, while the three consumer implementations and their shared hostile fixture runs have not completed migration.

Closure evidence: Run one shared accepted/rejected signature and manifest corpus through the Rust installer, Kotlin app, and native contract parsers, then verify one exact signed closure through each packaged consumer.

### Unverified: Protected release and channel publication

Maps: `Channel publication is monotonic and follows immutable release publication` and `A protected workflow advances a channel`.

Unavailable observation: Protected signing identity access, Git tag ownership, draft asset upload, remote re-download, required emulator/device/vehicle gates, immutable release publication, and atomic signed channel replacement in a real workflow.

Why unavailable: The local Rust CLI intentionally performs no remote mutation, and protected CI integration is outside this repository-only host lane.

Closure evidence: Execute one protected prerelease workflow whose retained evidence identifies all source commits, final assets, signatures, re-downloaded hashes, required environment gates, published GitHub Release, and later monotonic channel-pointer update.

### Unverified: Complete live functional cycle and intermediate boot coverage

Maps: Requirement `A functional emulator cycle has closed action authority`; Scenarios `Guest boots are observed during an installer and functional actions`, `A semantic UI action uses an observation`, `Normal or Settings removal is observed`, and `A required observation is unavailable`.

Unavailable observation: Real keyboard/editor/Navi/launcher/CAN/mode/removal behavior, source-bound PNG review, and every intermediate installer boot.

Why unavailable: Host fixtures do not prove Android behavior. The packaged CLI currently exposes neither all authoritative CAN/catalog/locale/provenance observations nor concurrent intermediate screenshot access during an installer transaction. No live gate was run for this runner implementation.

Closure evidence: An exact-kit live run with independent per-boot receipts and the missing closed observables/review. Unavailable cells do not authorize overall success.

### Unverified: Source-free macOS package bytes

Maps: `macOS delivery retains corresponding source outside the DMG` and `A macOS package is reopened`.

Unavailable observation: Reopening one newly built DMG and checking that source archives and trees are absent while readable licenses and notices remain in the application.

Why unavailable: This product workflow/export verification lane does not build or mount a macOS package; packaging is owned by the installer repository.

Closure evidence: Build the current installer from the exact source inputs, reopen the resulting DMG, and verify its complete inventory and readable license identities.
