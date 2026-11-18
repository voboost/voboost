## MODIFIED Requirements

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

