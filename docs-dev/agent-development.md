# Agent development

**English** | [Русский](agent-development.ru.md)

An agent is an independent module for one feature. It runs inside a selected stock process, verifies an available Java contract, and only then installs its hooks.

## One feature per agent

An agent owns:

- one user-facing feature
- its target-process set
- its applicability
- its Java contracts
- its configuration and state
- installation and removal of its hooks
- restoration of recorded original state
- documentation and tests for that feature

One agent's failure must not stop other agents. An agent receives no configuration or state belonging to another feature.

## Status

Beta1 source and release contain exactly seven stable agents and the Settings-only ui-qinggan executor. Deferred features and their metadata are maintained outside the source repository.

## beta1 stable set

| ID | Section | Target process | Applicability |
| --- | --- | --- | --- |
| `settings` | Voboost | `com.qinggan.app.setting` | Free/Dreamer, 8155/NXP, Android 9/11, China/global |
| `font-cyrillic` | Interface | 42 exact processes | Free/Dreamer, 8155/NXP, Android 9/11, China |
| `keyboard-ru` | Interface | QGIME and its second process | Free/Dreamer, 8155/NXP, Android 9/11, China |
| `keyboard-en` | Interface | QGIME and its second process | Free/Dreamer, 8155/NXP, Android 9/11, China |
| `launcher-apps` | Interface | `com.qinggan.app.launcher` | Free/Dreamer, 8155/NXP, Android 9/11, China |
| `vehicle-drive-forced-ev` | Vehicle | `com.qinggan.systemservice` | Free/Dreamer, 8155, Android 9/11, hybrid, China/global |
| `vehicle-drive-pedestrian-warning` | Vehicle | `com.qinggan.systemservice` | Free/Dreamer, 8155/NXP, Android 9/11, EV/hybrid, China/global |

Applicability selects a candidate; it does not prove support. For example, `vehicle-drive-forced-ev` admits the Free/Dreamer family, but a specific process starts the feature only after the complete Java contract matches.

## Metadata

Every agent owns `<Name>Agent.json`. Example:

```json
{
    "id": "vehicle-drive-forced-ev",
    "category": "vehicle",
    "status": "stable",
    "order": 23,
    "class": "ru.voboost.agents.vehicle.drive.forcedev.VehicleDriveForcedEvAgent",
    "target": "com.qinggan.systemservice",
    "activation": {
        "type": "checkbox",
        "default": false
    },
    "title": {
        "en": "Forced EV mode",
        "ru": "Принудительный режим EV"
    },
    "description": {
        "en": "Keeps electric drive mode requested when the vehicle allows it.",
        "ru": "Удерживает запрос электрического режима движения, когда автомобиль его допускает."
    },
    "applicability": {
        "chip": [
            "8155"
        ],
        "vehicle": [
            "free",
            "dreamer"
        ],
        "android": [
            9,
            11
        ],
        "powertrain": [
            "hybrid"
        ],
        "market": [
            "china",
            "global"
        ]
    }
}
```

Metadata defines:

- stable ID
- category and Settings position
- status
- entry class
- exact process targets
- activation mode
- localized title and description
- applicability by chip, vehicle, Android, powertrain, and market
- required UI resources
- a separate Settings tab declaration when the agent provides one
- conflicts and typed options when present

Metadata carries no separate package, APK path, certificate, stock APK hash, or firmware list.

`<Name>Agent.json` is validated before compilation. The root accepts only `activation`, `applicability`, `category`, `class`, `description`, `disables`, `id`, `options`, `order`, `requiresArtifacts`, `settingsTab`, `status`, `target`, and `title`; unknown fields are forbidden. `id` is unique canonical kebab-case, `class` is the exact entry-class FQCN, `target` is a string or a nonempty array of exact processes, and localized strings contain `en` and `ru`. `order` defines sorting and a unique position for each displayed entry; the service `settings` agent uses a negative value, while other displayed agents may not share a position.

The beta1 source admits only stable metadata. Its catalog uses `checkbox` with a boolean `default` and the Settings service `config` with exact `key` and `value`.

The source metadata validator is in `voboost-agents/build.gradle.kts`. Generated-generation formats are defined by `contracts/agent-generation/agents.schema.json`, `agent-entry-abi.json`, and `README.md`. The build generates the catalog and Java registry only from validated metadata; a parallel manually maintained agent list is forbidden.

## Separate Settings tab

An agent that needs a separate tab in the vehicle settings declares it in signed metadata. In beta1, `logs` declares:

```json
{
    "settingsTab": {
        "tag": "logs",
        "order": 1,
        "menuLayout": "menu_logs",
        "fragmentLayout": "fragment_logs"
    }
}
```

`tag` is unique among selected agents and cannot equal `voboost`. `order` places the tab after Voboost. Layout names must be safe Android resource names and exist in every resource ABI declared by the agent. The build copies these fields into the signed catalog and validates the XML before DEX generation.

At startup, the agent registers a Fragment factory through `AgentContext.settingsTabs()`. The runtime binds registration to the agent ID and its accepted `settingsTab`; an arbitrary `ownerId`, different tag, or different layout is rejected. The `settings` agent consumes only a generic ordered registry snapshot and contains no special branch for a particular extension.

Registration does not start `settings` or change `voboost-startup`. Hidden mode preserves the extension selection but displays no tab. When Interface mode returns, active `settings` restores it from the registry. Agent stop must close the registration and release its content controller.

## Target process and package

`target` names the exact Android process. A release-catalog target contains only:

```json
{
    "process": "com.qinggan.systemservice",
    "boot": false
}
```

Package and process are not always equal. A private process may be named `com.qinggan.audiopolicy.service:fft` while its package is `com.qinggan.audiopolicy.service`. Package is therefore never derived from the process name. When package identity matters, the agent checks it inside its Java contract.

## Applicability

The five applicability axes have closed domains:

- chip: `8155`, `nxp`
- vehicle: `free`, `dreamer`
- android: `9`, `11`
- powertrain: `ev`, `hybrid`
- market: `china`, `global`

A known mismatch excludes the agent. An unknown value is not guessed: the selection remains unknown until a trusted fact becomes available or until a runtime contract handles that boundary explicitly.

## Java contracts

A contract describes process capabilities. It may check:

- current package and process
- class
- instance or static method
- complete argument and return descriptor
- modifiers
- field and field type
- constructor
- Android service
- provider and a bounded value read
- resource by package/type/name
- named static value
- trusted runtime fact
- `allOf`, `anyOf`, and `not` combinations

Declarative JSON is validated at build time and compiled into immutable typed Java definitions. Source JSON is not loaded on the device and cannot be replaced by a local override.

The root of `<Name>Contracts.json` contains `schemaVersion`, `feature`, and a nonempty `contracts` array. A variant defines `id`, `bindings`, a `match` tree, and `semantics`, `reads`, and `actions` when required by the feature. Match uses `allOf`, `anyOf`, `not`, `package`, `process`, `class`, `method`, `staticMethod`, `field`, `staticField`, `staticFieldValue`, `service`, `resource`, `providerValue`, and `runtimeFact`. A binding uses `class`, `constructor`, `method`, `staticMethod`, `field`, `staticField`, `staticConstant`, or `staticStringConstant`. Actions and reads refer to stable binding names instead of performing a second reflection lookup.

A class check uses the exact FQCN and selected class loader. A method check uses the owner, the name only when it is part of a stable API, the complete JVM descriptor, modifiers, and expected staticness. A field check uses the owner, a stable name or a unique structural binding, the descriptor, and modifiers. A resource is checked by package, type, and name; a numeric ID is allowed only as the result of resolving that name at runtime.

The compiler normalizes the DSL, rejects unknown fields, duplicate IDs and bindings, empty logical nodes, incompatible modifiers, and ambiguous structural bindings, then creates typed `AgentContractDefinition` instances and a semantic snapshot. The production build depends only on generated definitions. A DSL change requires simultaneous changes to the schema, compiler, generated-catalog tests, and OpenSpec.

Exactly one complete contract must match at startup:

- zero — `unsupported`
- one — the agent may continue
- more than one — `ambiguous`

`unsupported` and `ambiguous` install no hooks and change no stock state.

## Forbidden selectors

- stock APK SHA
- OEM signing certificate
- firmware name or version
- numeric resource ID as a cross-firmware selector
- one-character names produced only by decompilation
- method name without a complete descriptor
- arity in place of argument types

`font-cyrillic` is the single explicit exception: before applying a binary transformation, it verifies the exact size and SHA-256 of `semibold.ttf`. That digest selects only supported font bytes, not a vehicle, firmware, APK, or process.

A contract contains no binary fingerprints of OEM APKs, certificates, installation paths, firmware names, or local paths to research materials. A one-character or otherwise obfuscated name does not become a production dependency: only a stable public identifier or a unique structural binding is allowed, with zero-match and ambiguous-match both terminating safely without a hook.

## Naming and layout

Agent code lives below `ru/voboost/agents/<feature>` in package `ru.voboost.agents.<feature>`.

For ID `some-like`, every private file uses the complete `SomeLike` PascalCase prefix:

```text
SomeLikeAgent.java
SomeLikeAgent.json
SomeLikeAgent.md
SomeLikeAgent.ru.md
SomeLikeContracts.json
SomeLikeContract.java
```

Every private file in an agent directory starts with the same prefix. Shared files at the root of `ru.voboost.agents` start with `Agent` or `Hook`.

Names use the shortest unambiguous form. Words already implied by operation or scope are not repeated.

## Stubs

Stubs reproduce only stock API fragments needed by tests:

- exact FQCN
- method or field descriptor
- modifiers
- constructor
- capability-present and capability-absent variants

A stub is not a production dependency and does not prove that a contract is present in a vehicle. It provides a reproducible test for an already researched contract.

## State

An agent does not write stock-application SharedPreferences. The daemon owns persistent state under `/data/voboost/state/<agent-state-key>.json`.

Through state RPC, an agent may:

- write only its own key
- read only its own key
- receive a typed value
- acknowledge a mutation
- restore recorded original state after restart

`state/` contains no install, update, restore, or uninstall state.

`AgentState.set` sends `W|timestamp|source|key|value`, and `AgentState.get` sends `R|requestId|source|key`; the daemon replies with `R|requestId|status|value`. The daemon derives the file name from source and key, verifies ownership and bounds, and atomically writes a JSON scalar. `AgentState.setConfirmed` retries a bounded number of times and accepts success only after an exact readback of the same value. The current API has no separate state-deletion operation.

Before its first mutation, an agent records the original value and whether it was present. A repeated start does not overwrite the original value with the current modified value. `stop` removes callbacks and hooks, prevents new actions, waits a bounded time for callbacks already in progress, restores the recorded value, and confirms restoration by readback. If restoration cannot be confirmed, the data remains available for the next attempt and the agent reports `failed`.

## Lifecycle

```text
selected
→ contract check
→ hooks installed
→ waiting for observed readiness
→ active
→ stop requested
→ hooks and subscriptions removed
→ recorded original state restored
→ stopped
```

After an error, an agent first stops changing stock behavior and performs all available cleanup. It publishes `failed` only after that cleanup.

Installation is transactional: resolve the single contract, obtain bindings, read original state, prepare hooks, install them, perform the permitted action, wait for the ACK, perform an independent readback, and publish readiness. An error at any step rolls back only the completed steps in reverse order. A callback that arrives after `stop requested`, an epoch change, or generation closure is discarded by a fencing check.

## Diagnostics

An agent uses its exact ID as source and log tag. Every meaningful step emits a structured record with:

- predicate
- observed result
- expected value
- `MATCH`, `NO_MATCH`, `SKIPPED`, `PENDING`, or error reason
- hook installation and removal
- action and readback
- state save and restoration

Records travel to the daemon through the shared transport. Agents do not write logcat or files inside a stock process.

A record contains `agentId`, generation, process, PID/starttime, epoch, a `debug`, `info`, or `error` level, a stable event ID, result, and expected and actual values without secrets. Full stack traces, user data, and arbitrary `toString()` output from stock objects are not sent. The transport is documented in [Logs](logs.md).

## Documentation and OpenSpec

Every agent owns:

- `<Name>Agent.ru.md`
- `<Name>Agent.md`
- capability `openspec/specs/agent-<exact-id>/spec.md`
- named Gates for directly verified scenarios
- `Unverified` declarations for observations that require another environment

Agent documentation explains purpose, applicability, contract, actions, state, shutdown, evidence, and remaining limitations. Russian and English versions have the same structure and claims.

Runtime sources are in `voboost-agents/src/main/java/ru/voboost/agents`, feature code and metadata are in its subpackage, host tests are in `voboost-agents/src/test/java/ru/voboost/agents`, stubs are in `voboost-agents/stubs`, and capabilities are in `voboost-agents/openspec/specs`. Verifiable requirements receive named gates; researched behavior that has not been proven in the required environment remains `Unverified` and does not raise agent status.

Main verification:

```bash
./gradlew check buildRelease
npm run openspec:validate
npm run docs:check
```

## Next

- [Agent runtime](agents-runtime.md)
- [Storage and recovery](storage.md)
- [Security](security.md)
- [Verification](testing.md)
