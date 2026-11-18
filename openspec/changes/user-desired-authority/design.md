# Design

## Authority and admission

Store values once in schema-2 `/data/voboost/agents.json`: `startup`, agent `enabled` and `options`; `userRevision` and `fieldRevisions` contain ordering metadata only. A field stamp contains owner revision, `{bootId,endpointEpoch,requestId}` and retained per-origin watermarks for the latest input boot. Bounds are 128 origins per field, 4096 stamped fields and 1 MiB for the document. Defaults initialize unstamped fields only. Compatibility is an effective projection based on exact capability probes, never a desired-state rewrite or firmware digest gate.

`SaveDesired{field,value,input}` performs one owner-serialized transaction independently of the product operation lease. The trusted catalog derives any mutually exclusive siblings; their values and stamps commit atomically with the primary field. File fsync, atomic rename and directory fsync precede a saved receipt. There is no capture reservation, pending inbox, second durable source or history migration. An unsupported old schema is refused rather than treated as empty defaults.

A live authenticated input endpoint supplies current-boot identity; the owner allocates revision. Request ordering is per affected field and endpoint. Concurrent not-yet-admitted inputs are ordered by owner admission, without promising physical click ordering. Exact retry returns authoritative current state without a new revision; conflicting identity is rejected and superseded identity never reasserts an old value. Readback resolves a lost reply without manufacturing a new input.

## Runtime and failure boundary

OFF revokes grants and cancels queued ON work before persistence. Jobs, dequeue, execution, reply and retry require current owner incarnation and field revision. Already executing OEM operations require separate drain/accounting and readback; no automatic compensation is inferred. Hooks can require controlled process cleanup; system_server termination is excluded. Saved and applied messages remain separate.

A storage error prevents a saved receipt. Volatile OFF fencing survives refresh within that owner, including directory-fsync uncertainty after rename. A restart can lose a physically unwritten input; readback of bytes is not proof of power-loss durability. Recovery cannot claim that input saved. Product activation, rollback and restore reconcile against current stamped desired rather than restoring old user fields. Terminal uninstall drains writers/executors before store deletion and prevents callbacks from recreating authority.

## Evidence

Colocated owner unit tests prove pure contract and fault branches. Product `tests/integration/daemon/` owns real owner IPC and crash/restart composition; `tests/emulator/daemon/` owns Settings/reboot scenarios, with shared primitives in `tests/common/`. Exact test names and commands become gates only after source inspection and execution. All new behavioral requirements are initially Unverified. Emulator execution remains paused; host checks do not establish OEM or vehicle behavior.
