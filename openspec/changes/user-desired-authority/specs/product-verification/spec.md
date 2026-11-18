## ADDED Requirements

### Requirement: User desired has one durable owner

The daemon owner SHALL store user startup, enabled and option values exactly once in schema-2 `/data/voboost/agents.json`, with owner-allocated `userRevision` and per-field revision/input metadata. Only semantic `SaveDesired` SHALL change stamped user values after initialization. Product defaults SHALL initialize only unstamped fields. Missing catalog entries and incompatible options SHALL retain their desired values and stamps while remaining outside incompatible effective execution. An unsupported schema SHALL fail explicitly without falling back to empty defaults; schema-1 migration is outside this change.

#### Scenario: A selected field becomes unsupported

- **WHEN** a saved OFF or option loses catalog or executor compatibility
- **THEN** its desired value and stamp remain readable while incompatible execution is excluded

#### Scenario: An unsupported store is opened

- **WHEN** the owner reads an unsupported schema
- **THEN** it reports the incompatibility without replacing user state with defaults

# Unverified: Desired sole authority acceptance

### Requirement: Desired save is one independent atomic transaction

`SaveDesired{field,value,input}` SHALL validate typed fields and bounded values, derive mutually exclusive siblings from the trusted catalog, and commit their values and stamps atomically under one owner revision. Persistence SHALL require file fsync, atomic rename and directory fsync independently of the product operation lock, runtime injection and OEM readback. The implementation SHALL bound the document to 1 MiB, stamped fields to 4096 and origins per field to 128; limit failures SHALL reject the complete mutation without evicting ordering metadata. A second desired store, capture reservation or durable pending inbox SHALL NOT be introduced.

#### Scenario: OFF arrives during product installation

- **WHEN** installation holds the product operation lock and a valid OFF arrives
- **THEN** the owner durably saves OFF and returns its saved receipt without waiting for installation

#### Scenario: A derived sibling or metadata bound conflicts

- **WHEN** a mutation cannot atomically admit all derived sibling changes or exceeds a document, field or origin bound
- **THEN** no part of the desired mutation commits and retained ordering metadata is not evicted

# Unverified: Desired atomic persistence acceptance

### Requirement: Desired input ordering is field scoped

The owner SHALL accept new inputs only from authenticated live endpoints for the current boot and assign revisions at atomic admission. Request watermarks SHALL be scoped to the affected field and endpoint; another field SHALL NOT impose a global request floor. Exact identity/value replay SHALL return authoritative current state without a revision increment, a conflicting payload for an admitted identity SHALL be rejected, and a superseded input SHALL NOT reassert its value. Retained origin watermarks SHALL prevent an older endpoint retry from replacing a newer admitted choice. Callbacks and old-boot inputs SHALL NOT create new user mutations.

#### Scenario: Another field has a newer request

- **WHEN** a delayed valid input for one field arrives after another field commits a numerically higher request
- **THEN** the other field does not make the delayed input stale

#### Scenario: An admitted input is retried or conflicts

- **WHEN** an admitted identity is retried exactly, reused with a different payload, or replayed after another endpoint supersedes it
- **THEN** exact replay adds no revision, conflicting reuse fails, and the superseded value never replaces the current choice

#### Scenario: A closed or prior-boot endpoint submits input

- **WHEN** a closed endpoint, prior-boot input or runtime callback attempts a new user mutation
- **THEN** no new user authority is created

# Unverified: Desired field ordering acceptance

### Requirement: Saved and applied desired results are distinct

A saved receipt SHALL identify the durable owner revision, authoritative desired state and save disposition without claiming OEM application. Applied results SHALL identify the relevant user and field revision and result separately and SHALL NOT write desired. Readback after a lost reply or restart SHALL expose the last durable value and input stamp. UI reconciliation SHALL follow the latest authoritative desired revision; timeout and stale saved/applied replies SHALL NOT revert a later choice.

#### Scenario: A durable save loses its reply

- **WHEN** persistence succeeds but the input process loses the saved reply and restarts
- **THEN** authority readback recovers the saved value and stamp without claiming application or inventing a new input

#### Scenario: An older result follows a newer choice

- **WHEN** OFF is followed by a newer ON and an old OFF receipt or application result arrives late
- **THEN** the newer desired selection remains authoritative in storage and UI

# Unverified: Desired receipt and presentation acceptance

### Requirement: OFF immediately fences stale runtime authority

OFF admission SHALL revoke affected grants and cancel not-yet-started ON jobs before persistence. Admission, dequeue, execution, reply and retry SHALL validate current owner incarnation and field revision. Late success, retries and persisted armed/debt state SHALL NOT create ON authority without current compatible desired permission. Already executing OEM operations SHALL remain separately accounted as draining or unknown until observed; agent OFF SHALL NOT imply automatic restoration of persistent OEM values or termination of system_server.

#### Scenario: OFF supersedes queued or executing ON

- **WHEN** an ON job is queued or executing and a later OFF is admitted
- **THEN** queued ON is cancelled, stale work cannot obtain a new grant, and already executing work remains explicitly accounted without a false claim of physical cancellation

#### Scenario: Owner restart encounters old armed state

- **WHEN** a new owner incarnation encounters old jobs, callbacks or persisted armed/debt state
- **THEN** none can rearm execution without current desired permission and revision admission

# Unverified: Desired runtime revocation acceptance

### Requirement: Product lifecycle preserves current desired authority

Product activation, catalog switching, rollback and restore SHALL reconcile against the current owner desired state and SHALL NOT replace stamped user values with prepared or historical snapshots. Terminal uninstall SHALL stop and drain desired writers and executors before deleting their store; stale callbacks SHALL NOT recreate the store or product. Reinstallation SHALL require new explicit user action.

#### Scenario: Prepared installation predates OFF

- **WHEN** installation prepares ON at revision 7 and user OFF commits at revision 8 before publication, rollback or reboot recovery
- **THEN** revision 8 remains the authoritative desired value throughout those operations

#### Scenario: A callback outlives terminal uninstall

- **WHEN** a stale callback runs after terminal uninstall
- **THEN** it cannot recreate desired storage or installed product authority

# Unverified: Desired lifecycle preservation acceptance

### Requirement: Desired storage failures are reported honestly

File fsync, rename or directory fsync failure SHALL prevent a saved receipt. OFF SHALL remain a volatile fence in the current owner despite failed persistence and stale refresh. Directory fsync failure after rename SHALL be reported as durability unknown rather than proven safe by byte readback. Recovery after owner restart SHALL NOT promise preservation of physically unwritten input; runtime grants SHALL remain unavailable until recovered authority is checked.

#### Scenario: OFF persistence fails before or after rename

- **WHEN** OFF persistence fails at file fsync, rename or directory fsync
- **THEN** no saved receipt is emitted, current-owner OFF fencing remains, and post-rename durability uncertainty is visible

#### Scenario: Owner restarts after an uncertain write

- **WHEN** the owner restarts after an input could not be proven durable
- **THEN** recovery checks stored authority and reports the durability gap without claiming that unwritten input survived

# Unverified: Desired failure durability acceptance

## Unverified

### Unverified: Desired sole authority acceptance

Maps: Requirement `User desired has one durable owner`; Scenarios `A selected field becomes unsupported`, `An unsupported store is opened`.

Unavailable observation: Complete execution and asserted results of every listed branch against the authoritative owner and its real consumers.

Why unavailable: This delta is applied before runtime integration. No newly named behavioral test has been verified or executed for this contract; existing source or unrelated host tests cannot establish these observations. Emulator execution is paused.

Closure evidence: Inspect and execute exact named colocated owner unit tests for applicable pure and fault branches, product `tests/integration/daemon/` real IPC and lifecycle tests, and `tests/emulator/daemon/` Settings/restart/reboot tests after execution is authorized. Record each exact command and observation separately; host evidence does not establish OEM or vehicle behavior.

### Unverified: Desired atomic persistence acceptance

Maps: Requirement `Desired save is one independent atomic transaction`; Scenarios `OFF arrives during product installation`, `A derived sibling or metadata bound conflicts`.

Unavailable observation: Complete execution and asserted results of every listed branch against the authoritative owner and its real consumers.

Why unavailable: This delta is applied before runtime integration. No newly named behavioral test has been verified or executed for this contract; existing source or unrelated host tests cannot establish these observations. Emulator execution is paused.

Closure evidence: Inspect and execute exact named colocated owner unit tests for applicable pure and fault branches, product `tests/integration/daemon/` real IPC and lifecycle tests, and `tests/emulator/daemon/` Settings/restart/reboot tests after execution is authorized. Record each exact command and observation separately; host evidence does not establish OEM or vehicle behavior.

### Unverified: Desired field ordering acceptance

Maps: Requirement `Desired input ordering is field scoped`; Scenarios `Another field has a newer request`, `An admitted input is retried or conflicts`, `A closed or prior-boot endpoint submits input`.

Unavailable observation: Complete execution and asserted results of every listed branch against the authoritative owner and its real consumers.

Why unavailable: This delta is applied before runtime integration. No newly named behavioral test has been verified or executed for this contract; existing source or unrelated host tests cannot establish these observations. Emulator execution is paused.

Closure evidence: Inspect and execute exact named colocated owner unit tests for applicable pure and fault branches, product `tests/integration/daemon/` real IPC and lifecycle tests, and `tests/emulator/daemon/` Settings/restart/reboot tests after execution is authorized. Record each exact command and observation separately; host evidence does not establish OEM or vehicle behavior.

### Unverified: Desired receipt and presentation acceptance

Maps: Requirement `Saved and applied desired results are distinct`; Scenarios `A durable save loses its reply`, `An older result follows a newer choice`.

Unavailable observation: Complete execution and asserted results of every listed branch against the authoritative owner and its real consumers.

Why unavailable: This delta is applied before runtime integration. No newly named behavioral test has been verified or executed for this contract; existing source or unrelated host tests cannot establish these observations. Emulator execution is paused.

Closure evidence: Inspect and execute exact named colocated owner unit tests for applicable pure and fault branches, product `tests/integration/daemon/` real IPC and lifecycle tests, and `tests/emulator/daemon/` Settings/restart/reboot tests after execution is authorized. Record each exact command and observation separately; host evidence does not establish OEM or vehicle behavior.

### Unverified: Desired runtime revocation acceptance

Maps: Requirement `OFF immediately fences stale runtime authority`; Scenarios `OFF supersedes queued or executing ON`, `Owner restart encounters old armed state`.

Unavailable observation: Complete execution and asserted results of every listed branch against the authoritative owner and its real consumers.

Why unavailable: This delta is applied before runtime integration. No newly named behavioral test has been verified or executed for this contract; existing source or unrelated host tests cannot establish these observations. Emulator execution is paused.

Closure evidence: Inspect and execute exact named colocated owner unit tests for applicable pure and fault branches, product `tests/integration/daemon/` real IPC and lifecycle tests, and `tests/emulator/daemon/` Settings/restart/reboot tests after execution is authorized. Record each exact command and observation separately; host evidence does not establish OEM or vehicle behavior.

### Unverified: Desired lifecycle preservation acceptance

Maps: Requirement `Product lifecycle preserves current desired authority`; Scenarios `Prepared installation predates OFF`, `A callback outlives terminal uninstall`.

Unavailable observation: Complete execution and asserted results of every listed branch against the authoritative owner and its real consumers.

Why unavailable: This delta is applied before runtime integration. No newly named behavioral test has been verified or executed for this contract; existing source or unrelated host tests cannot establish these observations. Emulator execution is paused.

Closure evidence: Inspect and execute exact named colocated owner unit tests for applicable pure and fault branches, product `tests/integration/daemon/` real IPC and lifecycle tests, and `tests/emulator/daemon/` Settings/restart/reboot tests after execution is authorized. Record each exact command and observation separately; host evidence does not establish OEM or vehicle behavior.

### Unverified: Desired failure durability acceptance

Maps: Requirement `Desired storage failures are reported honestly`; Scenarios `OFF persistence fails before or after rename`, `Owner restarts after an uncertain write`.

Unavailable observation: Complete execution and asserted results of every listed branch against the authoritative owner and its real consumers.

Why unavailable: This delta is applied before runtime integration. No newly named behavioral test has been verified or executed for this contract; existing source or unrelated host tests cannot establish these observations. Emulator execution is paused.

Closure evidence: Inspect and execute exact named colocated owner unit tests for applicable pure and fault branches, product `tests/integration/daemon/` real IPC and lifecycle tests, and `tests/emulator/daemon/` Settings/restart/reboot tests after execution is authorized. Record each exact command and observation separately; host evidence does not establish OEM or vehicle behavior.
