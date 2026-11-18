# Complete removal

**English** | [Русский](uninstall.ru.md)

The `uninstall` operation restores the proven original state and removes every Voboost object. It has no fixed reboot count: each reboot occurs only when the current phase proves it necessary, and only complete absence verification proves success.

## Owners and entry points

From the vehicle, the `settings` agent starts an explicit Intent for `UninstallActivity`. The application verifies the protected action and one-shot token, displays one dialog, accepts confirmation only from an active window, and publishes a one-shot request in a private directory. The agent gains no root access and removes no files.

On the normal desktop path, the GUI asks for confirmation. The installer invokes the installed management command.

```text
/data/voboost/voboost-inject --request-desktop-uninstall
```

Both entry points publish a durable request on Android. The installed software uses the installation inventory and original files recorded on Android and QNX. If QNX is provisioned, Android runs the installed version's Cluster removal helper. When signed completion is unavailable but a complete recovery snapshot can be retained, independent Android cleanup proceeds with that snapshot preserved. Init resumes the Android transaction across reboots. The desktop observes progress and independently checks Android absence after the final boot. Disconnecting the desktop after admission leaves the request and journals on the device.

The desktop selects this route via the installed program's `--uninstall-protocol` response. It uses the existing recovery path for older installations and when the installed program is missing or cannot execute. Recovery relies on device probes and retained evidence for each object.

An explicit fresh installation also supports a vehicle already removed through Settings while the computer was disconnected. It first proves complete Android absence and the stock QNX surfaces. If old host credentials remain, their exact private directory is preserved under its original basename followed by `-unconfirmed-<boot-id>-<nonce>`. Each preservation selects a new random nonce, so an earlier archive from the same boot remains intact. The active slot is then available for a fresh pairing. This preserves recovery material without claiming a signed removal result for the old pairing. An installed product, unknown surface, changed directory, or collision at the newly selected preservation destination blocks this path.

## Unavailable QNX and partial outcome

Recovery inputs remain inside `/data/voboost/recovery/<transactionId>`: private pairing material, the original signed request and recovery components. They are removed last. An unavailable QNX does not prove its absence and does not authorize discarding these inputs.

After Android cleanup and a new boot, the installer verifies all ordinary Android surfaces except the retained root and separately verifies the allowed inert recovery contents. Successful checks yield `partial`, containing `android-clean` and the pending `cluster` domain. The installer preserves pairing and refuses a fresh installation on that result. Full completion still requires complete absence, including recovery.

Once the ordinary `voboost-inject` has been removed, a closed ADB operation reads public authority from the single protected transaction directory. The installer verifies the original signed request against the Android key in retained desktop pairing and checks the QNX pairing identity. A stored `completed` value is not a receipt and never authorizes key retirement. This reader starts no agents and installs no additional program on the device.

Provisional and final receipt operations also use the protected recovery directory when inject is absent. The backend verifies each signed QNX receipt before sending it. A bounded private upload is synchronized before exclusive publication; an identical retry preserves the existing file, while conflicting contents are rejected. Final receipts are preferred after an interrupted acceptance.

After QNX finalization, the installer authenticates the original request, final QNX receipt, native uninstall report and new Android absence observation together. A closed completion proof rechecks those exact records and the current boot before admitting the existing emergency cleanup. Ordinary recovery still refuses pending QNX data. The native report and recovery survive ordinary cleanup failures; recovery is removed after the other product objects.

Offline continuation requires the retained protected desktop pairing. Missing pairing does not authorize discarding the device snapshot or accepting an unsigned absence result.

## Preparation and ownership transfer

`voboost-inject` first attempts signed Cluster removal while keeping its courier available. A pending request permits Android cleanup only after its private recovery inputs have been retained. It then freezes generation activation, stops injection jobs, clears pending retries, and drains Launcher cache writers. This preparation does not remove every in-process hook or stop every injected agent. The terminal reboot removes the injected code; subsequent absence checks verify the result.

After preparation, `voboost-inject` creates `recovery/<transactionId>/uninstall-journal.json`, publishes the root `operation.json`, and starts the fixed no-argument `/data/voboost/voboost-uninstall`. The program accepts ownership only from an allowed parent, with UID 0, a fixed `root:root 0700` executable, an `uninstall` role signature, and consistent journal and `operation.json`.

## Admission for destructive steps

Before the first deletion, the operation verifies the persistent inventory, current and original policy, `voboost.rc`, system programs and libraries, user-0 package, AppOp, and prepared operation authority. For every object, the journal stores a fixed path or strictly defined discovery method, owner, mode, label, inode, link count, size, SHA-256, and original and installed bytes.

An object is not changed when Voboost ownership is unproven, its path or inode changed, a link is present, a bound is exceeded, original data is missing, or current bytes do not match the installed boundary.

The APK is observed twice with the same `ru.voboost` package name, user 0, `versionCode`, v2/v3 certificate, dynamic path, inode, and size. A change between observations stops the operation before system cleanup.

## States and conditional reboots

```text
uninstall-prepare
→ uninstall-agents-stop
→ uninstall-policy-restore-prepare
→ uninstall-app-remove
→ uninstall-runtime-remove
→ uninstall-terminal-cleanup
→ device-owned absence predicate
→ remove `/data/voboost`
→ external absence verification when an installer is connected
```

The phase is not renamed for a reboot. When the journal identifies a specific object, an observation unavailable in the current boot, and a post-boot verification predicate, `operation.json` retains the current phase and receives `outcome=reboot-required`. After the boot ID changes, the operation returns to the incomplete phase instead of incrementing a predetermined phase counter.

For one-based phase N, `running`, `reboot-required`, and `failed` use sequences `3N-2`, `3N-1`, and `3N`. Before removing the root, the watchdog publishes `uninstall-absence-verify` with `reboot-required` and a proven reason. A failure retains a short safe message and the same journal for explicit Retry; while the root exists, the installer observes all progress through `operation.json`.

A reboot is permitted, for example, when a current process retains an inode being removed, policy must be checked after boot, or init still observes a removed service. It is not performed for convenience, to wait for time to pass, or to repeat an already proven step.

## Restoration and removal

The operation restores the current firmware's stock policy, the original AppOp, proven original bytes of changed system objects, and saved agent state. Publication is atomic and restores owner, mode, and label.

After restoration, PackageManager removes the APK. Only then are verified runtime objects, generations, agent state, logs, and transaction data removed. The running uninstaller hands terminal cleanup to a disabled init service with a fixed endpoint and exact journal so that the service can remove the uninstaller executable, `voboost.rc`, and the `/data/voboost` root itself.

Terminal cleanup accepts no paths from arguments or the journal, creates no new log, and writes no diagnostics outside the operation. Until the root is removed, `operation.json` and the journal expose state. After root removal, only external verification can provide a result.

## Absence verification and result

`voboost-installer` or another trusted external observer verifies the absence of:

- the user-0 `ru.voboost` package and its AppOp
- application directories `/data/user/0/ru.voboost`, `/data/user_de/0/ru.voboost`, and the `/data/data/ru.voboost` alias
- `/data/voboost` and every known mount or bind-mount trace of that root
- `/system/etc/init/voboost.rc` and Voboost init services
- processes whose executable, mapped libraries, or open files belong to Voboost
- Voboost policy types, transitions, and permissions in the active policy
- system programs, the implant, and LSPlant at fixed destinations

If verification requires a new boot, an init-owned watchdog or successor records the reason, predicate SHA-256, and current boot ID, then requests the reboot itself without a computer. After boot, the device repeats the predicate. The same reason and predicate after a changed boot ID means no progress and ends cleanup with an error without another reboot.

The result must also cover known Android localization overlays and their PackageManager and OverlayManager registrations. For QNX on an applicable profile, verify restoration of the original `boot_cfg.xml` state and absence of Voboost startup entries, runtime components and pairing data through trusted observations. If a required observation or the original state is unavailable, report the affected object and reason as a blocker. An inapplicable check requires positive platform evidence. Repeating removal on a clean system still requires current evidence.

The device does not retain `uninstall-complete` because the root has been removed. When removal starts from a computer, the installer produces a local result bound to the `transactionId`, last boot ID, and complete absence-check set. Removal from vehicle Settings follows the same device-owned path and does not require a running installer.

## Idempotency, crash cuts, and cleanup

Before removing each object, the journal persists the intent and boundaries. After interruption, the program observes the object first: absence counts as success, the installed boundary permits deletion to be retried, the original boundary proves restoration, and a third state blocks the step.

Removing one object never permits another to be skipped. An error does not move an already safely restored object back to the Voboost state. While the root exists, another run with the same `transactionId` resumes the journal; after root removal, only absence verification is allowed.

A foreign object is never removed to force completion. `complete` is not inferred from one missing marker file, process exit, or PackageManager success.

## Implementation sources and gates

- application request: `voboost-app/src/main/java/ru/voboost/UninstallActivity.kt` and `UninstallRequestPublisher.kt`
- daemon: `voboost-inject/daemon/src/uninstall.rs`
- installer observation: `voboost-installer/src-tauri/src/commands/operations.rs`
- OpenSpec: `voboost-app/openspec/specs/app-uninstall/spec.md`, `voboost-inject/openspec/specs/uninstall-control/spec.md`, `uninstall-recovery/spec.md`, and `uninstall-completion/spec.md`
- confirmation gate: `voboost-app/src/test/java/ru/voboost/UninstallConfirmationRequestPolicyTest.kt`, `UninstallReplayGuardTest.kt`, and `UninstallRequestPublisherTest.kt`
- settings-agent gate: `voboost-agents/src/test/java/ru/voboost/agents/settings/SettingsUninstallRequestGateTest.java` and `SettingsUninstallResultFenceTest.java`
- installer gate: `voboost-installer/tests/uninstall-content.test.tsx` and `src-tauri/tests/fixtures/daemon-uninstall-golden.txt`
- release gate: crash cuts for every destructive step, a conditional reboot only with new evidence, and complete absence verification after the last boot

Cluster cleanup progress precedes Android uninstall admission. Its retained status does not claim an Android operation or require an uninstall WAL that has not yet been created. Local receipt-lock contention is retried with a bounded backoff; remote teardown is not replayed by this retry policy. A terminal preparation failure is published as `prepare-failed`, distinct from `prepare-rolled-back`, and keeps the request's original boot and nonce for desktop correlation. Only a new validated user publication may restart the failed worker in the resident process. A CLI-local in-memory reset cannot do so.

## Next

- [Operation storage](storage.md)
- [Security](security.md)
- [Testing](testing.md)
