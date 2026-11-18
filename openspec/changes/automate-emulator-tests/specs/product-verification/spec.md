## ADDED Requirements

### Requirement: Gradle orchestrates selected emulator tests

The product SHALL provide separate Gradle tasks for preparation, startup, test execution, and shutdown. The combined task SHALL select all implemented emulator tests, a group, or an individual method. Test execution SHALL consume the session supplied by startup and SHALL require preparation to have completed.

#### Scenario: A developer selects emulator coverage

- **WHEN** the combined task receives no filter, a group, or a method filter
- **THEN** it selects all implemented cases, that group, or that method respectively

#### Scenario: A developer runs the stages separately

- **WHEN** preparation and startup succeed for a configured machine
- **THEN** the test task uses that same session, and shutdown releases that session after confirmed cleanup

### Requirement: Release verification separates machine roles

The combined emulator run SHALL use the installer and agent roles from the test machine configuration. It SHALL execute independent lanes concurrently on distinct canonical machine paths and serialize them on one machine. Both lanes SHALL use the same verified read-only candidate mount.

#### Scenario: The two roles resolve to distinct machines

- **WHEN** both roles are selected and resolve to different machines
- **THEN** their test sessions can overlap and consume the same mounted candidate

#### Scenario: The two roles resolve to one machine

- **WHEN** both roles resolve to one machine
- **THEN** the installer lane finishes before the agent lane prepares the same machine

# Unverified: Live emulator lifecycle and scheduling

## Unverified

### Unverified: Live emulator lifecycle and scheduling

Requirements `Gradle orchestrates selected emulator tests` and `Release verification separates machine roles`, scenarios `A developer selects emulator coverage`, `A developer runs the stages separately`, `The two roles resolve to distinct machines`, and `The two roles resolve to one machine`. Local selection lists the six cases and filters by group and method. The two prepared A9 machines reached independent Android and QNX readiness while sharing one current candidate mount. Both test lanes still require successful runtime coverage. Installer preparation retained an unresolved uninstall, and the PW cases reached their runtime prerequisites but did not pass. A complete same-machine run and successful independent start/test/stop commands after UI recovery also remain open.
