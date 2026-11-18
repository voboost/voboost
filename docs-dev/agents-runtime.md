# Agent runtime

**English** | [Русский](agents-runtime.ru.md)

Agent source and release production live in [`voboost-agents`](../../voboost-agents). Agents are independent artifacts of the complete Voboost release and are never embedded in `voboost-app.apk`.

## Release output

One exact agent commit builds:

- `agent-common.dex` with the shared runtime and `ru.voboost.reflect`
- one unique `agent-*.dex` for each logical module, shared by its cataloged target processes
- `agents.json` with logical agents and target-level `file`, `entryClass`, size, and SHA-256
- `agents.json.sig`, a raw 64-byte Ed25519 signature over exact catalog bytes

The current logical agents and targets are declared in the [release profile](../../voboost-agents/release-profile.json) and adjacent Agent.json metadata; [agent status](agents-status.md) records their source classification. Android translation resource APKs are separate artifacts produced by [translation tooling](translation-tooling.md), rather than DEX modules. These inventories do not establish runtime admission.

## Selection and generation

The daemon verifies the signed catalog, reads root-owned `vehicle.json`, evaluates applicability, dependencies, conflicts, and enabled intent, and computes the selected target closure. The app downloads changed bytes but does not compute or submit an authoritative selection.

An accepted set becomes one immutable generation containing the catalog pair, exact selection receipt, `agent-common.dex`, and each distinct module DEX in the signed catalog. Applicability affects only the derived `agents-selected.json` and the modules loaded into each process. `current` and `previous` change atomically. Incomplete or rejected input cannot become active.

## Process delivery

The daemon admits a process by stable PID/start time, exact `argv[0]`, UID, SELinux context, and catalog target. The injector loads the ABI-matched implant. The implant creates one common classloader and a child loader for each target module received through retained descriptors; it accepts no arbitrary device DEX path.

A logical module is built once; all of its process targets reference that same DEX. The generation stores and verifies the file once, while selection, loading, and status remain target-specific. Legacy generations with unique per-target files remain readable; an old daemon cannot accept the new shared-file catalog before being replaced.

## Runtime contract

`AgentEntry` verifies the generation and starts only entries assigned to the current process. Each agent resolves its structural Java contract before mutation. Unknown or ambiguous OEM capabilities fail closed.

Agent-to-daemon and daemon-to-agent pipes carry bounded typed state, status, logs, desired configuration, and acknowledgements. Each connection is bound to generation, process start time, and epoch. Stop closes new callback admission, drains bounded work, removes owned hooks and subscriptions, restores confirmed state, and reports cleanup.

Already loaded DEX is not unloaded. A running process retains its resident generation until natural exit or a user-approved reboot.

## Settings surface

Each OEM UI process has one host agent. Feature agents contribute through one process-local registry; they do not install independent menu hooks. The host discovers live OEM constructors, factories, containers, symbolic resources, theme, dimensions, measurement, and drawing. Missing or ambiguous structure produces an exact unsupported result instead of a partial or approximate surface.

## Sources and gates

Production sources are in [`src/main/java/ru/voboost/agents`](../../voboost-agents/src/main/java/ru/voboost/agents), reflection support is in [`ru/voboost/reflect`](../../voboost-agents/src/main/java/ru/voboost/reflect), and canonical contracts are in [`voboost-agents/openspec/specs`](../../voboost-agents/openspec/specs).

Host Gradle gates prove Java compilation, D8 closure, target placement, catalog generation/signature with the active build identity, stubs, and lifecycle fixtures. Physical injection, OEM UI, device, and vehicle behavior remain separate evidence.

## Related pages

- [Agent development](agent-development.md)
- [`settings` agent](agent-settings.md)
- [Native runtime](native.md)
- [Updates](updates.md)
- [Security](security.md)

## Component implementation and development

This repository owns the Voboost Java agent runtime, `agent-common.dex`, one `agent-<id>.dex` per logical agent or executor, the signed `agents.json` catalog, and OEM test fixtures.

Schema 2 retains process, file, size, SHA-256 and boot on every target and requires platform/domain/entryAbi. Android targets use android/android/android-java-v1 and entryClass; the QNX gauge target uses qnx/cluster/voboost-agent3-v1 and omits entryClass. The root clusterRuntime identifies the native runtime. Android targets of one logical module share its DEX file, size, SHA-256, and entry class while retaining individual process and boot policy; conflicting identity, cross-module reuse, and case-folded filename collisions are rejected.

Production sources are compiled from the root `src/` tree. A release generation contains the complete signed agent closure; applicability affects only `agents-selected.json` and the DEX files loaded into target processes. The repository has no embedded-app bundle, `ui-res`, or independently authored OEM layout profiles.

Run `./gradlew check` for host-level verification and `./gradlew buildRelease` to build the versionless component artifact set. Host checks remain distinct from emulator, device, vehicle, protected-signing, and publication gates.

The complete OEM matrix requires `runOemTestDriverBuild` with `-PoemTestDriverInjectProjectDir=/absolute/inject` and a project-local `-PoemTestDriverOut=/absolute/receipt.json`, then the actual receipt identity as `-PoemDriverSha` and absolute `-PoemInjectProjectDir` for `check`. Retain the Cargo target and environment across both steps.

The actual release set comes from current Agent.json metadata and the release profile; [agent status](agents-status.md) records it. Metadata classification does not establish runtime acceptance.

Settings mutation submission and pending-queue publication are serialized. ACK polling uses a protected snapshot without holding the queue monitor across transport or projection application; only the owning mutation may remove its entry and release its row token. Deadlines use monotonic time.

A failed submission, current mutation timeout or snapshot application failure adds an EN/RU/ZH unconfirmed-change message to the first Settings section. Confirmed selections remain authoritative; missing ACK does not imply that the change was not saved. A fresh submitted retry clears that row's message, and superseded terminals cannot restore or clear the latest attempt's error. Repeated input already awaiting its result remains a no-op without an error. The ordinary description is inserted or removed in place without replacing radio controls or reserving empty space in the normal panel.

Settings vertical spacing is generated from the verified `voboost-components` source snapshot in `tools/component-spacing/`. Section top/bottom, typed child margins and description gaps use one generated accessor; OEM internal row offsets are normalized before applying those defaults. Inter-section and trailing panel spacing are each applied once. The source snapshot adds no external repository, AAR, fonts or resources to the runtime closure.

Verify snapshot extraction with `./gradlew generateComponentSpacing verifyComponentSpacing`; intentional source-default updates are described in [spacing tooling](component-spacing.md). Live Settings acceptance remains separate from host fixture measurements.

Authored Qinggan action buttons measure translated content with `WRAP_CONTENT` and preserve OEM style and vertical padding, applying the verified components horizontal padding to both section and editor actions. Parent constraints cap their width; wrapped text can grow in height. Editor action pairs stay horizontal while their content fits and stack with the existing OEM gap when it does not. Generated spacing ownership is registered only for the declared accessor source in the DEX graph; unknown compiled classes remain rejected.

Voboost Settings supports English, Russian and Simplified Chinese. The canonical profile and mutation value is `zh`; display helpers accept `zh-CN` without changing the OEM system locale or vehicle facts. Legacy EN/RU catalog text falls back to English when Chinese is absent. Current agent captions, common uninstall dialog/status messages and validation copy include Chinese.

The startup hint explains the selected radio option. None starts no agents or interface and requires a computer with `voboost-installer` to restore startup. Hidden keeps agents running without the tab; several rapid taps on the TBOX version under System request its return. Interface keeps agents running with the tab visible immediately. A visible selection explains its mode and does not claim a persisted change; confirmed projections remain authoritative after pending or rejected mutations.

Hook installation emits six bounded `hook.install.frontier` info records: callback construction enter/complete, callback method lookup enter/complete, and native bridge enter/returned. Records contain only the target method identity and, after return, whether a backup exists; no invocation values are included. The last observed frontier narrows an unfinished startup stage without changing hook or startup behavior. Native bridge entry is not proof that LSPlant completed; correlate it with native stage markers.

ACK and ordinary desired updates share AgentProfile's canonical EN/RU/ZH domain through the outer AgentPlan parser. The production ACK integration regression exercises AgentEntry, the real runtime controller and Settings publisher, including Chinese retranslation without withdrawing the tab. Radio titles alone receive the source-derived Section horizontal inset; child controls keep their existing horizontal geometry.

The optional `AgentEntry.scheduleMainThreadSnapshot(String)` diagnostic is invoked by the verified native caller after successful init return. It schedules one DEBUG main-thread snapshot per successful runtime generation on an independent worker, never on the main Handler or canonical update thread. Output is capped at 24 class/method/line/native frame records plus process/PID/Java-thread/state stages, with no arguments, paths or arbitrary thread names. Task ownership is registered before execution. Stop/replacement uses atomic cancellation without Task.cancel interruption/logging or joining a possibly blocked VM stack call; late output is fenced and every record identifies its generation. Sampling is labelled `post-init-worker` / `may_precede_ptrace_release`: a hijack frame is not proof of the original OEM Binder caller until correlated after lease release.

Pre-init Launcher diagnostics schedule one independent delayed worker before executors/reconciliation. Successful init before the delay emits dropped; replacement/stop atomically fence late records. Font read frontier pairs identify raw-resource open, bounded reading and close without exposing bytes or paths or changing qualification/fallback. A missing terminal record is incomplete evidence.

The native hook bridge must publish the genuine reflected backup through `HookCallback.setBackup` before the target trampoline becomes callable. Java re-publication after return is idempotent; unhook retains the backup for already dispatched callbacks. Host scripted bridge tests verify callback stock semantics; native ordering and live-target acceptance remain separate evidence.

## Native applicability

The optional `native` metadata object requires `source` and permits only an additional `applicability` object. Without that override the native executor inherits all five logical axes: chip, vehicle, android, powertrain and market. An override contains a nonempty subset of those axes and nonempty, unique value lists drawn from the logical values. Unknown axes, widening, empty lists and wrong scalar types are rejected. The generated native registry contains the effective intersection in canonical axis order; unspecified axes inherit the logical values. Java target/catalog applicability is unchanged. These are semantic capability dimensions, not firmware identities or hash compatibility gates.

PW retains logical Java NXP applicability, and both PW and FEV retain logical Java Free/Dreamer applicability. The beta native backends narrow to Dongfeng/8155 Free on Android 9 and 11; native NXP and Dreamer execution remains unavailable until their own supported contracts and capability probes are established. `verifyNativeApplicabilityMetadata` tests narrowing, inheritance and rejection; `testNativeRegistry` checks actual generated ID/source/effective metadata. Current-property probe rejection and actual native effect/readback remain separate owning/runtime gates.

## Vehicle recovery observations

Early OEM settings and CAN-service discovery run on a bounded worker queue. Temporary missing inputs are retried after 250 ms; access or structural errors retain a diagnostic and retry after one second. The owner poll never sleeps or walks `/proc`. A failed readiness check before ioctl remains waiting and does not consume command retries or discard received feedback. Each later attempt rechecks current user intent, input revision and sample freshness. An ioctl with an unknown result is not blindly repeated. Process discovery skips known SELinux domains outside the runtime target set; an unreadable context remains eligible for the existing bounded discovery retry. APK scheduler recovery waits for Android boot completion and does not gate native vehicle agents.

The native owner distinguishes a short CAN frame, an unsupported kernel datagram, an empty receive queue and a transport failure. Unsupported messages are consumed without closing the socket or erasing the known power state; they never become vehicle feedback. The receive loop remains bounded and counts unsupported datagrams separately.

The current CanBusService executor publishes probed getAccStatus/getVehicleState readings through the authenticated existing command channel. The owner accepts only the registered process/endpoint, declared source/state, current boot and bounded observation time. These readings are current OEM API observations, not new CAN events. After a real receive gap they can restore the same power session without inventing an ignition cycle. Feedback sampled before an accepted command cannot confirm its result. User OFF remains authoritative while any recovery is pending.

Cluster injection retries pending UI dispatch in its existing worker without requiring an external observer. Only an OEM-dispatched callback may establish its UI owner before the first engine frame. A submitted task is not an applied agent. Temporary symbol absence is retried; successful resolutions are cached. Agent application and diagnostics do not require each other's completion.

The native RX diagnostic snapshot retains `firstTargetFrames` for `0x6a`, `0x73` and `0x9a` independently of its rolling journal. Each entry contains the original raw frame, receive boot time, sequence and receiver epoch. It records the first receipt by this daemon process, including frames discarded by working-state policy; it does not prove when the ECU first transmitted the frame or cover time before subscription. Reopening the receiver preserves these entries; restarting the daemon starts a new observation.
