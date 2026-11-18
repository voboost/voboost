# Developer documentation

**English** | [Русский](index.ru.md)

This documentation describes the assembled Voboost architecture, its repository ownership, runtime contracts, release process, and verification boundaries.

Before reading the technical pages, read the [Voboost overview](../docs-user/index.md), [usage guide](../docs-user/usage.md), [updates](../docs-user/updates.md), [restore guide](../docs-user/restore.md), and [uninstall guide](../docs-user/uninstall.md). Those pages define observable behavior that the implementation must not change without making the change clear to users.

## Getting started

1. [Architecture](architecture.md) — components, ownership, and main flows
2. [Repositories and sources](repository.md) — product repository boundaries and the change path
3. [Contracts](contracts.md) — schemas, wire protocols, versions, and limits
4. [Application](application.md) — `ru.voboost`, APK OTA, staging, and uninstall requests
5. [Native runtime](native.md) — daemon, injector, implant, LSPlant, ABI, and cleanup
6. [Agent runtime](agents-runtime.md) — catalog, selection, generation, bootstrap, and runtime state
7. [Security](security.md) — keys, signed roles, process admission, SELinux, and file checks
8. [Glossary](../GLOSSARY.md) — shared user and engineering terminology

## Working on a component

| Component | Primary page | Related contracts | Required gates |
| --- | --- | --- | --- |
| Android application | [Application](application.md) | [Contracts](contracts.md), [Storage](storage.md), [Logs](logs.md) | [Testing](testing.md) |
| Agent | [Agent development](agent-development.md) | [Agent runtime](agents-runtime.md), [Agent status](agents-status.md) | metadata, contract, lifecycle, and cleanup gates |
| Voboost tab | [`settings` agent](agent-settings.md) | [Contracts](contracts.md), [Uninstall](uninstall.md) | projection, resource ABI, UI lifecycle, and uninstall-request gates |
| Native runtime | [Native runtime](native.md) | [Contracts](contracts.md), [Security](security.md) | admission, `ptrace` rollback, ABI, FD/JNI lifetime, and cleanup gates |
| System operation | [Install](install.md), [Updates](updates.md), [Restore](restore.md), [Uninstall](uninstall.md) | [Storage](storage.md), [Security](security.md) | crash-cut, idempotency, rollback, and terminal-result gates |
| Installer | [`voboost-installer`](installer.md) | [Contracts](contracts.md), [Release](release.md) | GUI/CLI parity, fixed endpoint, reconnect, and artifact-verification gates |
| Release and CI | [Release](release.md), [CI](ci.md) | [Build](build.md), [Security](security.md) | readiness, signing, provenance, promotion, and no-rebuild gates |

## Adding or changing an agent

1. Define the user-visible behavior in OpenSpec and add a gate
2. Describe metadata, applicability, target processes, contracts, and artifacts as specified in the [agent development guide](agent-development.md)
3. Implement the agent, lifecycle, and cleanup so CI uses only committed contracts, stubs, and tests
4. Regenerate [agent status](agents-status.md) from metadata and applied OpenSpec
5. Run the host, emulator, device, and vehicle gates without treating one level as proof of another

The current source inventory is recorded in [agent status](agents-status.md); metadata classification does not establish runtime acceptance. See [beta0 state](beta0-state.md) for the historical snapshot limitations.

## Changing a wire contract

1. Find the owner in the [contract registry](contracts.md)
2. Change the schema or frame definition owned by that component
3. Apply an OpenSpec change that defines compatibility requirements and negative cases
4. Update the producer and every consumer
5. Add canonical vectors, malformed vectors, size-bound gates, and version-skew gates
6. Rebuild artifacts without manually copying generated output between repositories

## Sources of truth

| Question | Source of truth |
| --- | --- |
| Observable user behavior | pages in `docs-user` |
| Mandatory contract | applied OpenSpec |
| Format fields and limits | the owner's schema and typed parser |
| Executable behavior | sources and generators in the owning component repository |
| Evidence | a named gate with an exact environment and artifact |
| Current agent set | metadata and applied OpenSpec published in `agents-status` |

## Main commands

```text
(cd ../voboost-app && ./gradlew check)
(cd ../voboost-agents && ./gradlew check)

(cd ../voboost-inject && make lint && make test)

(cd ../voboost-installer && npm test -- --run)

cargo test --manifest-path release/Cargo.toml
node tools/check-target-docs.ts
```

Each command proves only the level it names. See [Testing](testing.md) for the complete list of environments and evidence boundaries.

## Shared component guides

General guides, procedures and examples live here. Each agent keeps its own documents beside Agent.json and code; use [agent status](agents-status.md) to find them. Tool rules, OpenSpec, licenses, provenance and fixture evidence remain with their owners.

- [Voboost Components](components.md)
- [Component architecture decisions](components-architecture-decisions.md)
- [Component visual testing](components-visual-testing.md)
- [Java component demo](components-demo-java.md)
- [Kotlin component demo](components-demo-kotlin.md)
- [Compose component demo](components-demo-compose.md)
- [Component pixel demo](components-demo-pixel.md)
- [Translation tooling](translation-tooling.md)
- [Component spacing inputs](component-spacing.md)
- [Launcher client probe](launcher-client-probe.md)
- [Native font runtime](cluster-font-runtime.md)
- [Cluster PW visual observer and mask](cluster-pw-visual-observer.md)
- [Native dependencies](native-dependencies.md)
- [QNX link inputs](cluster-link-inputs.md)
- [Installer themes and styling](installer-themes.md)
- [Internationalization (i18n)](installer-i18n.md)
- [Building and distribution](installer-build.md)
- [Implementation checklist](installer-implementation-checklist.md)
- [Error handling](installer-error-handling.md)
- [Tauri configuration](installer-tauri-config.md)
- [Troubleshooting](installer-troubleshooting.md)
- [Cross-repository release contract](installer-cross-repository-release-contract.md)
- [Native GUI acceptance](installer-gui-acceptance.md)
- [Minimal beta1 GUI acceptance](installer-gui-test-cases.md)
- [Direct macOS ADB backend](installer-native-adb.md)
- [Installer ADB resources](installer-adb-resources.md)
- [Protected Cluster pairing](installer-cluster-pairing.md)
- [Rebuild and install a modified Voboost](build-modified.md)
- [Voboost Code Style](codestyle-integration.md)

- [Voyemu dispatch reservation journal](voyemu-journal.md) — isolated host component and its safety limits.
