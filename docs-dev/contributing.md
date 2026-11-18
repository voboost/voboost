# Contributing a change

**English** | [Русский](contributing.ru.md)

A change must preserve user-visible behavior, trust boundaries, and complete removal. Start with the user documentation and the applied OpenSpec, not with a convenient place in the code.

## Choosing a task

For an agent, open [agent status](agents-status.md): it lists status, processes, applicability, OpenSpec Unverified items, and the next contribution.

beta1 consists of 7 stable agents, the unstable `logs` agent, and 35 planned agents. Changing a status requires changes to metadata, the agent catalog, Settings projection, documentation, OpenSpec, and composition checks.

For a system component, first identify its owner:

- product schemas, release definitions, publication, and docs — `voboost`
- application — `voboost-app`
- agents, reflection runtime, and OEM stubs — `voboost-agents`
- daemon, native code, and system operations — `voboost-inject`
- GUI/CLI/packaging — `voboost-installer`

## Before making a change

1. Read the feature's user page
2. Read the component's developer page
3. Read the complete OpenSpec requirement
4. Find the source description and its generator
5. Check uncommitted changes and do not disturb someone else's work
6. Reproduce the defect or define the new observable behavior

## OpenSpec

A behavioral change starts with one scoped change:

1. `proposal` — why and what changes
2. `design` — decisions and boundaries
3. `tasks` — verifiable steps
4. `delta spec` — added, changed, and removed requirements
5. strict validation
6. implementation
7. named Gates
8. application and archival
9. repeated strict validation

Only a rebaseline of the current state may proceed without the change flow, and only by an explicit decision of the project owner.

## New agent

Create one package:

```text
ru/voboost/agents/<feature>/
├── <Name>Agent.java
├── <Name>Agent.json
├── <Name>Agent.md
├── <Name>Agent.ru.md
├── <Name>Contracts.json
└── <Name>*.java
```

Rules:

- ID — canonical kebab-case
- Java package — `ru.voboost.agents.<feature>`
- every private file uses the full `<Name>` prefix
- name — the shortest unambiguous form
- metadata JSON — four-space indentation
- a planned agent uses `status: planned` and disabled activation
- production agent code — Java 8 without Kotlin, AndroidX, or lambdas
- shared environment classes appear only when a shared responsibility has been demonstrated

## Agent contract

1. Describe stable packages, processes, classes, members, and descriptors
2. Do not use the APK SHA, certificate, firmware version, or names found only after decompilation
3. Add positive and negative capability variants
4. Compile the JSON into immutable typed definitions
5. Add a debug record for every verification step
6. Require exactly one complete match

If a method or field may have been renamed by obfuscation, use descriptor/modifiers/unique structural binding or leave the contract Unverified. Do not guess the name.

Do not add OEM APKs, decompiled sources, their binary fingerprints, or local paths to the repository. Record research results as a minimal declarative contract, a stub variant, positive and negative tests, and an honest `Unverified` boundary.

## Action and confirmation

An agent must separately describe:

- what it reads before the change
- what it preserves as the original state
- one permitted action
- ACK
- readback
- retry
- stop
- restoration
- behavior after a partial failure

A successfully installed hook does not yet mean that the feature works successfully.

## Stubs

A stub preserves exact FQCNs, descriptors, and modifiers. Add only the method or field required by a test. A stub contains no simplified helper code and is never linked into a production build.

Create separate capability variants for different contracts instead of one overly broad stub.

## State

An agent uses the daemon state RPC and only its own keys. It must not write SharedPreferences or files belonging to the stock application.

If the original value was not saved, uninstall must not invent it.

## Documentation

Update the Russian document first:

- purpose
- user-visible result
- applicability
- target process
- contract
- action and readback
- state
- stop and removal
- diagnostics
- evidence and Unverified items

After the Russian text is approved, synchronize the English version. Exact identifiers are not translated.

A user-visible change also updates the user pages, roadmap, and changelog. Every release gets its own tagline.

## Checks

At minimum, an agent requires:

- metadata and schema
- contract compiler
- positive match
- zero match
- ambiguous match
- hook/action
- ACK/readback
- stop/restore
- invalid input
- stub variants
- docs/OpenSpec
- full `./gradlew check`

Host tests do not replace checks on a device and in a vehicle. Preserve the required Unverified items.

Commands from the agent repository root:

```bash
./gradlew check
./gradlew buildRelease
```

Agent code and runtime are in `voboost-agents/src/main/java/ru/voboost/agents`, host tests are in `voboost-agents/src/test/java/ru/voboost/agents`, stubs are in `voboost-agents/stubs`, and capabilities are in `voboost-agents/openspec/specs`. Native injection details belong in [native runtime](native.md), not in an agent document.

## Naming

The shortest unambiguous name is preferable to a detailed retelling of context:

```text
install-app
```

Add a qualifier only to distinguish adjacent concepts.

## Commit

One commit contains one complete change:

- OpenSpec
- code
- tests
- generated files, when stored in Git
- RU/EN documentation
- changelog for a user-visible change

Format:

```text
<type>(<scope>): <summary>
```

Do not include unrelated formatting changes, local artifacts, private keys, absolute paths, raw research material, or old verification reports.

## Next

- [Agent status](agents-status.md)
- [Agent development](agent-development.md)
- [Testing](testing.md)
- [Local build](build.md)
- [Release](release.md)
