# Native runtime

**English** | [Русский](native.ru.md)

The native runtime lives in [`voboost-inject`](../../voboost-inject). The Cargo package `voboost-daemon` builds two resident programs: `voboost-daemon` owns runtime state, configuration and file logs; `voboost-inject` owns agent admission and injection. The split source tree still requires a new signed release and runtime acceptance.

## Source boundaries

The repository keeps `daemon/`, `injector/`, `implant/`, `install/`, `restore/`, `uninstall/`, `selinux/`, and `shared/`. Shared privileged wire/storage types and APK identity verification are flat modules in [`contract`](../../voboost-inject/contract). Java runtime source is owned separately by [`voboost-agents`](../../voboost-agents/src/main/java/ru/voboost/agents).

## Process admission and injection

`voboost-inject` admits a process only after stable PID/start time, exact `argv[0]`, UID, SELinux context, executable, and ABI checks. The C injector repeats identity after stopping the thread group, owns temporary ptrace state, restores registers/signals on every failure, and loads only the exact ABI implant.

The release uses flat versionless names `libimplant-arm64-v8a.so` and `libimplant-armeabi-v7a.so`. Each contains the statically linked LSPlant and dex_builder libraries and one static C++ runtime.

## Bootstrap and lifetime

`voboost-inject` opens verified generation files through retained descriptors and passes `agent-common.dex` plus only target-specific module DEX. The implant bounds the bootstrap envelope, materializes sealed memory-backed files, attaches to ART, prepares LSPlant, and starts `AgentEntry`.

A successful `dlopen` is not readiness. Java acknowledgement binds generation, process start time, target, and connection epoch. On stop, callbacks close before hooks and JNI resources are released. Loaded code may remain until the OEM process exits, but no active hook or transport owner may survive cleanup.

## Root authority

`voboost-daemon` owns persistent runtime configuration, agent state and file logs through a private authenticated protocol. `voboost-inject` owns applicability, generation publication and update admission. The app supplies untrusted candidate bytes; the installer supplies a packaged closure and observes typed operations. Neither can execute an arbitrary native path or select native roles.

## Native vehicle source

Agent-owned Rust codecs are colocated with their Java agents and declared through `native.source`. Gradle generates the selected native registry before the daemon build. The owner caches enabled selections after a durable config commit. Ordinary startup-mode changes apply on the next boot; the explicit hidden-interface recovery gesture is the exception. No agent config or log history is reread on each native poll. The owner creates a new write-only log session at startup without reading log history.

The owner reads CAN through bounded protocol21 netlink with kernel sender, group, framing and truncation checks. Disabled native parts drop their samples; a transport error clears samples and repeated identical waits do not repeat log events. Samples are receive observations, not user choices or proof of ECU freshness.

Before `CanBusService` starts, the owner can send an agent-encoded command through the early native ioctl backend, subject to its admission and input checks. Once the service is detected, the raw backend is retired for the daemon lifetime. Later commands use an authenticated semantic queue through inject and a thin Java transport that calls the OEM setter; Java does not own feature policy, and an unavailable endpoint does not trigger raw fallback. An accepted setter call is not proof of ECU application; fresh CAN receive observations are separate evidence.

This source implementation requires a new signed release and runtime acceptance. The new native CAN driver is absent from Voyemu; receive, NV and property access rights, SELinux runtime behavior and physical 3–5 second timing remain unaccepted.

Agent-owned decoders return named scalar fields. The daemon keeps samples by source and field, so Forced EV state and drive mode do not overwrite one another. Debug events identify the field and its OEM scalar value; unchanged values refresh receive time without repeating a change event.

The device installer resolves static hardware facts before publishing the init service. Normal daemon startup reads the existing trusted `vehicle.json` and does not open NVRAM. Native powertrain admission checks cached facts against current identity axes; missing, corrupt, contradictory or mismatched facts keep Forced EV unavailable. `--recompute-profile` is the explicit diagnostic refresh. Existing DEX selection consumes the trusted installed profile; native cache binding does not claim per-car identity or global copied-profile detection.

## Product manifest consumer

The closed native parser verifies `voboost.json`, its signature, compatibility, full inventory, APK identity, agent-catalog parity, and artifact size/SHA-256. Initial install, runtime injection, OTA activation, Restore, and active native status use this same product authority. Native host gates cover Cargo/C/C++ tests, ptrace rollback fixtures, contract consolidation, binary rename, role signatures, product-candidate activation, SELinux policy compilation, and Android cross-build. Physical device injection, SELinux Enforcing runtime, and vehicle behavior remain separate gates.

## Related pages

- [Architecture](architecture.md)
- [Agent runtime](agents-runtime.md)
- [Storage](storage.md)
- [Security](security.md)
- [Testing](testing.md)

## Component implementation and development

`voboost-inject` is the Android 9 and 11 system layer of Voboost. The repository contains the root daemon, native injector, in-process implant, system programs `voboost-install`, `voboost-restore`, `voboost-uninstall`, and `voboost-selinux`, and the SELinux policy.

The implementation follows the architecture and requirements in this documentation and the applied OpenSpec.

### Components

- `daemon/` builds two resident programs: `voboost-daemon` owns runtime configuration, state and file logs; `voboost-inject` verifies agent inputs, manages generations, selects eligible processes and starts injection
- `injector/` runs a bounded ptrace transaction, loads the implant, and restores the target process state after success, failure, or interruption
- `implant/` attaches to ART, loads DEX from supplied descriptors, initializes LSPlant, and serves agent runtime channels
- `install/` continues an authorized installation on the device and writes a verifiable operation result
- `restore/` accepts the current stock state after an OEM firmware update as the new baseline and reapplies permitted Voboost changes
- `uninstall/` stops Voboost, restores the baseline, removes Voboost-owned objects, and proves their absence
- `selinux/` contains the policy and system program for fixed policy and label changes
- `contract/` contains the flat `voboost-contract` crate for APK identity, operation, install/generation, canonical product-release and app/daemon status contracts; `shared/` contains implementation helpers that do not belong to that public contract
- `openspec/specs/` contains the applied native runtime and system-operation requirements
- `test/` and `tools/` contain host tests, contract checks, and release tooling

### Responsibility boundaries

Captured native-agent choices are ordered by the daemon, not by the injection client. The closed prepare request validates the declared source/key, boot and capture identity, persists pending before native admission and rejects conflicting or superseded actions. Finish requires the exact latest identity and the same canonical option value before clearing pending. Generic journal replacement cannot alter a registered captured-choice journal. Inject retains endpoint and catalog-option admission and sends the typed requests without local persistence or automatic replay after a lost reply. Host composition covers durable prepare/finish with a lost reply; it does not prove CAN revocation or vehicle application.

If journal I/O is uncertain, the daemon retains the latest unconfirmed capture and fences that source across unrelated configuration refreshes. This process-local record does not establish durability across daemon replacement. Owner recovery resumes its retained capture before an older WAL; an invalid conflicting duplicate cannot replace a known durable identity.

The private owner protocol authenticates root peer credentials, the exact executable path and trusted file metadata on every connection. Each process retains at most one successfully role-verified executable descriptor for daemon and inject. An unchanged descriptor identity reuses its Ed25519 result without rereading the binary; replacement or changes to inode, size, links, ownership, permissions, modification or change times require verification again. The cache pins the descriptor, rechecks the current process executable, exists only in memory and never stores firmware compatibility hashes. A cache hit authenticates the executable, not vehicle input authority or permission to send CAN commands.

Neither resident downloads updates or chooses user features. The Android app prepares an untrusted proposal, and `voboost-inject` verifies signatures, closed schemas, complete inventory and process eligibility before publishing a generation and injecting it. It requests persistent runtime changes through the authenticated owner protocol.

`voboost-inject` keeps one synchronous control loop and dispatches blocking injection/rebind jobs for all available distinct PIDs in catalog startup-priority order. Fresh targets and pending retries share that order; retries win equal-priority ties. Each PID has one complete bootstrap owner, and one bootstrap carries all applicable process modules. Only the control loop adopts owned result descriptors, reconciles current desired state and publishes status through the owner. Stop, uninstall and native update handoffs drain active jobs before releasing ownership; old-generation completions remain controlled under their original resident authority.

The injector does not choose a PID or Java contract, the implant does not read persistent Voboost files, and agents never receive root. Installation, restoration, removal, and SELinux changes run in separate system programs with closed workflows.

The [emulator diagnostic contract](../../voboost-inject/openspec/specs/emulator-diagnostics/spec.md) admits three bounded operator-owned windows only through the exact signed installed daemon on QEMU under Enforcing. A window holds the real mutation lock, drops only authorized fixed-key state replies, or loses one authenticated Settings uninstall transport before durable admission. It never substitutes stored state or runtime status. The packaged installer keeps its owner alive through a private stdin pipe; EOF, expiry and dead-owner identity fences end the effect. Host gates cover the closed protocol and production routing; actual JNI retry/cancel and Settings error copy require the same signed-kit emulator cycle.

Beta1 uses separate trust roles for the Android APK, product manifest, agent catalog, restore authorization, and native executables. `voboost.json`, `agents.json`, and `restore.json` have distinct authority. The native-executable role signs ELF files with a mandatory binding to `daemon`, `inject`, `install`, `restore`, `uninstall`, or `selinux`; the SELinux program is accepted only with `role=selinux`.

The device has no mutable trust store and no runtime key rotation. Public keys are embedded in verifier components, and a key change requires a coordinated release or computer-assisted installation.

`contract/release.rs` consumes the product-owned `voboost.json` schema and shared adversarial fixtures without importing product source. Initial installation uses that signed manifest, one flat `voboost-app.apk`/native/agent closure and a manifest-bound `staging.json`; it computes the first agent selection from daemon-owned vehicle facts and never consumes an app-authored selection receipt. Runtime injection, OTA activation, restore, and active native status use the same signed product authority. `contract/status.rs` defines the channel-independent manifest-digest-bound proposal, active identity, result, and descriptor handoff.

Install, restore, and uninstall request only the reboots required by the current verified phase. Uninstall has no predetermined reboot count: it completes after terminal cleanup and proven Voboost absence.

Native uninstall journals the original block-device read-only flag for each verified protected mount, clears it before the write window, and restores it after the RO remount. The init worker tolerates a bounded lease handoff from the triggering daemon; neither path relaxes SELinux Enforcing.

Remote calls keep transport outcome separate from the function result. A failed call fences further target calls and reports the failure stage and register-restoration outcome. Register restoration alone does not prove ART managed-stack continuation safety; the Android Launcher stack-walk crash remains under investigation.

Optional LSPlant stage diagnostics identify DEX/JNI, GC critical construction, SuspendAll construction and mutation boundaries. They use the ordinary record channel with trylock/drop behavior; missing markers remain unavailable evidence and do not identify an ART lock or prove that a stage was not reached.

For the exact managed bootstrap load, native loader/dlsym work retains the whole-group guard. The injector then disables execution clone tracing and detaches peers before ART initialization, retaining execution-TID restoration and cleanup ownership. Failed entry stops initialization; newly created untraced workers are excluded from managed cleanup ownership. Target scheduling and managed continuation still require runtime verification.

Remote diagnostics distinguish saved original PC/LR and their validity from callee stop PC/LR, with fixed native/managed-init/handoff phase labels. These fields are copied from already captured registers; they neither identify a lock owner nor prove a direct caller or ART continuation safety.

For non-return signal stops, bounded callee diagnostics run before original-context restoration. ARM64 reads at most sixteen saved FP/LR pairs inside the exact stopped execution stack, reporting only addresses and availability. Partial, invalid or unavailable chains never claim complete unwinding; unproven ARM32 frame layout remains unavailable. Signal delivery and restoration policy are unchanged.

The production hook bridge requires the per-hook LSPlant publication extension. It publishes the genuine reflected Java backup before target entry activation, outside SuspendAll; rejected publication aborts activation. The Java afterreturn setter is idempotent. Runtime OFF/ON acceptance remains a separate gate.

### Cluster owner epoch

The Android authority epoch for the QNX mailbox belongs to the resident `voboost-daemon`. Inject reads it through the authenticated private owner protocol and does not create or cache a substitute epoch. Restarting inject therefore preserves the QNX owner epoch; replacing the storage daemon creates a new one and invalidates observations and delivery leases from the previous owner. An unavailable owner rejects the operation rather than issuing a local fallback epoch. This identity is separate from the logging writer ID and does not require reading historical logs.

Mailbox state reads and commits use the private owner protocol. The owner validates the complete canonical mailbox schema and performs a byte-exact compare-and-swap on the fixed private `cluster/mailbox.json` path. Injection retains the cluster transaction lock while requesting the commit; the owner does not reacquire that lock. An unavailable owner or a conflicting document fails the operation without a local write or mutation replay.

Signed requests, receipts and uninstall reports are also written by the daemon. The protocol selects a fixed document role, never a caller-provided path. Before committing, the daemon verifies the paired signature; receipts and restoration reports must correlate with the retained pending request. Unsafe existing destination files are rejected.

Pairing import sends bounded protected material from the authenticated injector to the owner. The injector retains release-role separation checks; the owner validates the canonical provision, paired signing seed and credentials identity before writing fixed private files. It publishes provision last, resumes matching interrupted imports and performs no writes for an exact completed retry. A missing mailbox under an existing provision is an error, not permission to recreate authority. The courier transport still requires ownership transfer.

### Development

Requirements include the Rust version from `rust-toolchain.toml`, Node.js `24.13.0`, npm, Android NDK `29.0.14206865`, `cargo-ndk` 4.1.2, OpenSSL, and `uncrustify`. Set `NDK_HOME` to a complete shared upstream NDK installation outside project checkouts. Make forwards that path as `ANDROID_NDK_HOME` for `cargo-ndk`; it does not download an NDK. Product builds use the same path in the explicit `androidNdkHome` build input.

For the standard macOS SDK location:

```bash
export NDK_HOME="$HOME/Library/Android/sdk/ndk/29.0.14206865"
make check-ndk
```

Voboost and Voyemu can use this same installation. Removing or rebuilding either checkout must not remove the shared NDK. Retain upstream host helpers, including Python scripts used internally by `ndk-build`.

Prepare the checkout:

```bash
make init
make check
```

Run all host gates:

```bash
make lint
make test
make build
```

Build Android development artifacts with temporary keys for every native role:

```bash
npm ci
npm run dev:android
```

Check OpenSpec separately:

```bash
npm run check:openspec
```

`make build` builds the daemon host release profile and does not prove Android, ptrace, init, or SELinux Enforcing behavior. `npm run dev:android` builds Android development components but does not prove installation or operation on a device or vehicle.

After a canonical Android build, `tools/prepare-beta1-unsigned.sh <output-dir>` exports the exact ten flat unsigned native inputs consumed by product beta1 composition. It validates ABI, ELF hardening, debug/path absence and unsigned state, reads no signing secret and performs no publication.

### Documentation

- [Architecture](architecture.md)
- [Native runtime](native.md)
- [Installation](install.md)
- [Restoration](restore.md)
- [Full uninstall](uninstall.md)
- [Storage and recovery](storage.md)
- [Security and trust roles](security.md)
- [Testing and evidence boundaries](testing.md)
- [Contract registry](contracts.md)
- [Central OpenSpec](../openspec/specs)
- [Native component OpenSpec](../../voboost-inject/openspec/specs)

Native source preparation uses pinned build copies under `build/third-party-source/`. Live submodules remain unchanged; unexpected copy/source drift fails before compilation.

Initial provisioning uses adb root, adb remount and a verified setenforce 0 window. The installer restores Enforcing; post-reboot installation verifies Enforcing and rejects Permissive. This exception applies to initial provisioning and its pre-reboot retry. The desktop bootstrap and native SELinux helper inherit the stock shell execution label from private temporary creation, then retain the same inode at their fixed root-owned paths. Signature, byte and permission checks precede execution; product-specific labels are applied by the existing first-boot relabel service.

If the OEM root domain cannot read the loaded policy during initial installation, the signed helper reconstructs the stock boot source from matching firmware sidecars or fixed CIL snapshots through the stock compiler. Android 9 and 11 source selection is bounded and retains optional policy fragments; unsupported formats and unsafe inputs fail. Host fixtures cover source choice and child cleanup; actual installation remains a target gate.

Policy rollback includes the original init SHA selectors (one on Android 9, three on Android 11), including original absence. The committed schema-v2 policy metadata and terminal recovery retain these selectors until exact restoration is verified. A preferred ODM policy is rejected by the current vendor-only publication contract. Host checks do not establish initial stock-root publication privileges or target acceptance.

The owner logs `processStartedBootMs` and `endpointReadyBootMs` from Linux `CLOCK_BOOTTIME`, and `startupDurationMs` from its process-local monotonic clock, in `storage endpoint available` after store and native-configuration initialization. A missing or invalid boot clock is reported as `bootClockUnavailable`, never as zero. These timestamps measure owner readiness from kernel boot, not ECU application or the physical start of vehicle wake-up.
