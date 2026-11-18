# Restore after stock firmware

**English** | [Русский](restore.ru.md)

The `restore` operation accepts the current stock firmware as the new original state, rebuilds Voboost system integration, and verifies it after two reboots. It does not restore policy or system files from the previous firmware.

## Owners, inputs, and startup

`voboost-installer` verifies the signed restore kit, uploads it to `/data/voboost/install/<transactionId>`, and starts the fixed endpoint once:

```text
/data/voboost/voboost-restore
```

`voboost-restore` owns the journal, mutations, rollback, and cleanup. Android init starts continuation after reboots. For policy transformation, `voboost-restore` executes installed `voboost-selinux` with no arguments. `voboost-selinux` accepts only a committed `restore` operation at phase `restore-policy-backup`, verifies the correlated journal, derives fixed paths itself, accepts no policy pathname, and does not decide operation progress. The daemon performs final runtime verification.

Inputs are a signed closed-schema `restore.json`, ARM64 system programs, implant and LSPlant libraries for ARM64 and ARM32 processes, `voboost.rc`, the installed `ru.voboost` package, the saved persistent inventory, and the actual bytes of the current stock policy. `restore.json` binds release identity, exact source-definition SHA-256, and the canonical restorable-closure digest excluding the restore pair; its raw Ed25519 signature is verified before composition and recovery. Arbitrary paths, commands, and additional files are rejected.

## Admission

Before changing the system, restore verifies UID 0, the fixed `root:root 0700` executable, the `restore` signature role, `operation.lock`, the absence of another operation, the exact input set, owner, mode, label, link count, sizes, and SHA-256. The APK is observed twice by package name, user 0, `versionCode`, certificate, path, inode, and size.

The current stock policy is verified at a fixed path by regular inode, owner, mode, label, format, and an embedded size limit. It need not match the saved policy from the previous firmware. If the APK is absent or its Voboost ownership cannot be proven, restore ends as `failed` without system changes and requires a normal installation.

## States

After acquiring the lock, the program creates `recovery/<transactionId>/restore-journal.json` and a root `operation.json` with `operation=restore`, `phase=restore-prepare`, `outcome=running`, and `rebootCount=0`.

```text
restore-prepare
→ restore-firmware-verify
→ restore-policy-backup
→ restore-policy-apply
→ restore-reboot-first
→ restore-runtime-apply
→ restore-reboot-second
→ restore-runtime-verify
→ restore-inventory-commit
→ restore-cleanup
→ restore-complete
```

## New original state

Before the first mutation, the journal saves the current stock policy bytes, owner, mode, label, inode, size, and SHA-256. Those bytes are used to build policy with the minimal Voboost rules. Old stock bytes are never published over new firmware.

After verification, the compiled policy is published atomically. Before the first reboot, the operation also restores `voboost.rc` from the verified persistent inventory and revalidates its bytes and metadata. The next boot therefore already contains the continuation, relabel, and daemon init services. `operation.json` receives `phase=restore-reboot-first`, `outcome=reboot-required`, `rebootCount=1`, and the boot ID before reboot is requested.

After boot, Android init starts `voboost-selinux --relabel` and restore continuation. Continuation proves a changed boot ID, SELinux Enforcing, and application of the expected policy, then restores the daemon, descriptors, implant, and LSPlant for both ABIs. An unchanged object is verified but not rewritten.

Before the second reboot, the complete set is verified on disk, and `operation.json` receives `phase=restore-reboot-second`, `outcome=reboot-required`, and `rebootCount=2`.

## Verification and commit

After the second boot-ID change, restore verifies SELinux Enforcing, the init service, daemon `/proc/self/exe`, both ABIs, descriptors, the package, AppOp, and the active agent generation. A mixed set or a process running from an old inode is rejected.

Only after that verification does the persistent inventory replace the previous original policy with the new stock policy and record the new installed system set. During `restore-cleanup`, the owner removes inputs, rollback bytes, and temporary bodies, then publishes `phase=restore-complete`, `outcome=complete`, and `rebootCount=2`, removes the operation journal and empty transaction directory last, and retains `operation.json` until the next operation.

## Idempotency, crash cuts, and rollback

A repeat with the same `transactionId` resumes the journal. Another identifier cannot acquire the lock or replace `operation.json`. Every intent, original boundary, and expected result is synchronized before an external effect.

After a crash cut, an object is compared with the original and expected identities. The expected result is recorded without repeating the step, original state permits the step to be retried, and a third state stops restore without overwriting it.

Each reversible external step returns only to a proven boundary of the current firmware: an interrupted atomic publication retains the previous inode, and the policy watchdog restores its confirmed bytes. Restore never guesses at reinstating the old firmware or undoing a proven boot. A failure publishes `failed`, retains the same journal, and permits only explicit Retry of the same transaction; a third object state is not overwritten. A reboot required by local compensation is counted separately from the two successful-restore reboots.

After commit, inputs, temporary files, the previous transaction inventory, and the recovery directory are removed. Failure while removing inputs, rollback bytes, or temporary bodies remains a retryable phase and prevents `restore-complete`; failure while subsequently removing the operation journal retains the terminal document and blocks a new operation until the exact residue is removed.

## Implementation sources and gates

- authorization and kit: `voboost-inject/tools/test-restore-authorization.ts` and the `voboost-inject/restore` crate
- journal and continuation: `voboost-inject/restore/src/main.rs`
- OpenSpec: `voboost-inject/openspec/specs/install-recovery/spec.md`, `selinux-domain-policy/spec.md`, `selinux-root-zone/spec.md`, and `global-operation-coordination/spec.md`
- related APK contract: `voboost-app/openspec/specs/app-apk-install/spec.md`
- recovery gate: crash cuts before and after every write, `rename`, fsync, and both reboots
- release gate: the new stock policy becomes the sole baseline, and two boot-ID changes, SELinux Enforcing, and the complete runtime for both ABIs are proven

## Next

- [Operation storage](storage.md)
- [Installation](install.md)
- [Uninstall](uninstall.md)
- [Security](security.md)

## Installer observation

The installer pins the selected device and operation identity. After reconnecting it verifies the same device, operation type, transaction and release kit, with monotonic state/reboot counters. The host passes no arbitrary paths, commands or phase names. Restarting GUI/CLI resumes the same operation from device state; launching the program or receiving a terminal report without independent result checks does not establish restoration.
