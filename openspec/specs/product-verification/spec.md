# Product Verification specification

## Purpose

Define product-repository verification and keep host, emulator, device, vehicle, CI, and publication evidence distinct.

## Requirements

### Requirement: Native emulator evidence retains its distinct verification contract

Native-session mode SHALL invoke only the approved installed native executable through `test --root --request` with the root-owned exclusive session. Its schema-4 `verificationContract` SHALL explicitly exclude the legacy prepared-check inventory, scene oracle, process inventory and forced host cold boot. Schema-3 evidence SHALL NOT claim this native contract. All twenty-seven business cases and Installer CLI installation, removal, independent root/Enforcing Inspect and gesture authority SHALL remain unchanged. Native-session primary semantic snapshots SHALL use `output.tree` with retained raw JSON and validated session, runtime PID, actual guest boot, observation interval, complete primary scope, window metadata, parent linkage, raw/clipped geometry and password redaction. Accessibility window types SHALL NOT be interpreted as WindowManager layout types. Secondary observations SHALL retain the existing verified Installer backend; unsupported secondary, stale, timeout, protocol and partial primary responses SHALL NOT become empty trees or silent fallback. FEV/PW cold guest reboot and warm sleep/wake SHALL retain their existing device proof independently of the initial native Quick Boot mode.

Native captures SHALL retain all three original PNGs and the `parallel_fenced` manifest, verify operation/request/session/PID/guest boot, original Android displays 0 and 1, 1920×720 decoded dimensions, every SHA and bounded skew, and bind the chosen named image to those same bytes. A black cluster placeholder SHALL be accepted only with explicit cluster-off, derived-placeholder and unverified-QNX-frame authority; Android black-frame rejection SHALL remain enabled. Bootstrap home readiness SHALL NOT establish the current visible scene. Current selected theme and actual PNG review SHALL establish Classic metal versus Speedy Dash color.

A strictly correlated `TREE_REJECTED`/`not_started`/`retryable:false` primary read SHALL remain a failed retained action. A bounded UI observation condition MAY request up to three fresh read-only observations within one five-second monotonic admission budget, with fresh request IDs and runtime/boot guards before subsequent reads; it SHALL NOT replay input or the rejected operation, reclassify retryability, use empty trees or fall back to CLI. Stale, protocol, timeout, unknown transport and epoch failures SHALL fail closed. The exact current CLI exit-4 reconnect-only `TREE_STALE: session or boot changed while reconnecting observer` with strictly correlated schema/request/operation and `not_started`/`retryable:false` MAY authorize one NEW read only through a single-use capability armed by closed completed Enforcing OLD-to-NEW boot proofs and unchanged owned session/runtime PID. Ordinary probes SHALL NOT arm this capability. The first read in that NEW epoch SHALL consume it even on success; ownership and NEW boot SHALL be independently checked before and after recovery, and the original five-second admission deadline SHALL remain in force. A second stale response or any changed session/PID/boot SHALL fail closed. The failed operation SHALL remain failed; no operation ID or input SHALL be replayed.

Private ownership tokens SHALL remain outside report artifacts. Public requests SHALL retain only the session ID and operation identity. The root-approved host-source overlay SHALL bind exact constructor source owners, changed host-file hashes and immutable payload; host adapter changes SHALL NOT be described as changes already compiled into that payload.

#### Scenario: Native transport retains the complete case inventory

- **WHEN** native-session mode is selected
- **THEN** all twenty-seven cases retain their order and legacy CLI dispatch is rejected before execution

#### Scenario: A foreign session reuses matching frame metadata

- **WHEN** a retained native bundle has matching PID and boot but belongs to another exclusive session
- **THEN** evidence admission rejects the bundle

#### Scenario: Primary semantic observations preserve actual guest authority

- **WHEN** a native primary tree is mapped for selection and guarded CLI gestures
- **THEN** observed nodes preserve their parent and window authority, clipped visible geometry, raw gesture geometry and checked states without fabricating installer receipts

#### Scenario: A UI transition temporarily prevents a verified tree

- **WHEN** the strict native read reports the retained post-cancel unavailable outcome
- **THEN** the UI condition requests only bounded fresh observations of the same runtime and guest boot, preserves the failed operation and never repeats input

# Gate: node --test --test-name-pattern="native transport preserves exact full27 and rejects legacy dispatch before execution" tools/tests/native-gate-wiring.test.ts

Gate contract: Maps: Requirement `Native emulator evidence retains its distinct verification contract`; Scenario `Native transport retains the complete case inventory`. Preconditions: Product host sources and native transport fixtures are available. Observable result: The original full27 order remains intact and native dispatch rejects the legacy CLI. Evidence boundary: Host contract only; actual runtime and visual acceptance remain required.

# Gate: node --test --test-name-pattern="retained native bundle rejects foreign session even with identical PID, boot and internally matching response" tools/tests/native-gate-wiring.test.ts

Gate contract: Maps: Requirement `Native emulator evidence retains its distinct verification contract`; Scenario `A foreign session reuses matching frame metadata`. Preconditions: Retained native bundle host fixtures and product validators are available. Observable result: Foreign session evidence fails despite matching PID, boot and self-consistent response. Evidence boundary: Host rejection only; no guest frames are produced by this test.


# Gate: node --test --test-name-pattern="native tree maps observed parent IDs, clipped geometry, Unicode and radio states" tools/emulator/native-tree.test.ts

Gate contract: Maps: Requirement `Native emulator evidence retains its distinct verification contract`; Scenario `Primary semantic observations preserve actual guest authority`. Preconditions: Product host sources and native API fixtures are available. Observable result: Native mapping preserves state, geometry and window metadata without inventing missing focusable or ordinal fields. Evidence boundary: Host admission only; actual native observer latency and guarded CLI input require guest verification.

# Gate: node --test --test-name-pattern="actual sequential native and CLI observations preserve visible semantics without equating local window IDs" tools/emulator/native-tree.test.ts

Gate contract: Maps: Requirement `Native emulator evidence retains its distinct verification contract`; Scenario `Primary semantic observations preserve actual guest authority`. Preconditions: The retained sequential native primary response and CLI XML from the same unchanged Settings scene are available. Observable result: Both production parsers preserve all 116 node semantics and parent/window metadata, including 61 visible nodes, without equating observation-local window IDs. Evidence boundary: Fixture host admission only; native-derived guarded input, guest boot fencing and full runtime acceptance remain independent.

# Gate: node --test --test-name-pattern="fresh native read requests retain the failed raw operation and use new request IDs" tools/emulator/native-tree.test.ts

Gate contract: Maps: Requirement `Native emulator evidence retains its distinct verification contract`; Scenario `A UI transition temporarily prevents a verified tree`. Preconditions: The actual post-cancel rejection fixture and native session host adapter are available. Observable result: The original operation remains failed with retryable false, the next read uses a new request ID, and only output.tree operations are dispatched. Evidence boundary: Host condition admission only; actual guest transitions and full runtime acceptance remain independent.

### Requirement: The product repository contains no component implementation

The product repository SHALL own system documentation, release definitions, product schemas, release composition and verification, evidence admission, plans, and product workflows. It SHALL NOT contain an Android Gradle project, app or agent production source, OEM stubs, injected UI implementation, native implementation, copied component OpenSpec capabilities, or built component artifacts. App, agent, native, and installer implementation and tests SHALL remain in their respective repositories.

#### Scenario: The product source tree is verified

- **WHEN** repository layout verification enumerates product-owned and component-owned paths
- **THEN** every forbidden component source root and build-system entry is absent while the product release and documentation roots remain present

# Gate: node --test --test-name-pattern="product repository contains no component production source" tools/tests/product-repository-layout.test.ts

Gate contract: Maps: Requirement `The product repository contains no component implementation`; Scenario `The product source tree is verified`. Preconditions: A product checkout with the current repository layout is available. Observable result: The named host test rejects Android, app, agent, reflection, UI, resource-APK, stub, copied component-contract, and Gradle roots while requiring product release, documentation, plans, and OpenSpec roots. Evidence boundary: Repository layout only; sibling repository completeness and packaged artifact behavior are separate gates.

### Requirement: Ordinary verification executes only product-owned checks

The ordinary product workflow SHALL verify the Rust release tool, Android-tools integration used by product verification, target user and developer documentation against one exact clean source-pinned agents metadata checkout, the complete product tool-test inventory, and strict OpenSpec. It SHALL NOT build an APK, agent DEX, native executable, OEM stub, resource APK, desktop installer, or release package. Component builds SHALL run only in their own repository gates or the explicit cross-repository beta1 workflow.

#### Scenario: Ordinary product verification runs

- **WHEN** the product `verify.yml` workflow reaches repository verification
- **THEN** it runs every product-owned check and contains no component build or artifact-upload step

# Gate: node --test --test-name-pattern="ordinary CI verifies product sources without rebuilding components" tools/tests/ci-workflow.test.ts

Gate contract: Maps: Requirement `Ordinary verification executes only product-owned checks`; Scenario `Ordinary product verification runs`. Preconditions: The product workflow and package command definitions are available. Observable result: The named host test requires release Rust tests, the pinned Android-tools integration test, target docs, the complete product tool suite and strict OpenSpec, while rejecting Gradle, app, agent, stub and component artifact commands. Evidence boundary: Static workflow and command-policy verification only; a remote GitHub run and sibling component gates are not executed.

### Requirement: The product check command reaches every retained product tool test

While product documentation and evidence tools still use Node, `npm run check` SHALL execute OpenSpec evidence validation, target documentation validation, and the complete flat `tools/tests/*.test.ts` inventory. It SHALL NOT select a historical test subset or read removed local app, agent, UI, stub, or legacy documentation roots.

#### Scenario: A retained product test is added

- **WHEN** the ordinary product check command is inspected
- **THEN** its test glob includes the new retained test without another workflow edit

# Gate: node --test --test-name-pattern="ordinary CI executes the complete product tool suite" tools/tests/ci-workflow.test.ts

Gate contract: Maps: Requirement `The product check command reaches every retained product tool test`; Scenario `A retained product test is added`. Preconditions: The package manifest and ordinary workflow are available. Observable result: The named host test requires one complete flat test glob after OpenSpec and target-doc checks and rejects local component and old-doc paths. Evidence boundary: Static command reachability only; individual test semantics are established by their own gates.

### Requirement: Product Node tooling is transitional

The final product repository SHALL implement release, evidence, and documentation verification without a Node package manifest, lockfile, or `node_modules`. Migration SHALL preserve canonical JSON bytes, exact validation failures, generated agent-status bytes, documentation link checks, and every product release/evidence gate before deleting the Node entrypoints.

#### Scenario: Node tooling removal is evaluated

- **WHEN** the replacement implementation and its conformance corpus are incomplete
- **THEN** the package files remain explicitly transitional instead of being deleted with an unverified behavior change

# Unverified: Node-free product tooling

### Requirement: Environment evidence retains its exact boundary

Host, emulator, device, vehicle, CI, and publication evidence SHALL be recorded as distinct types and SHALL authorize only the next gate named by that evidence. A host package or emulator run SHALL NOT satisfy physical OEM SELinux, init, UI, device, vehicle, CI, or publication requirements.

#### Scenario: Host and emulator evidence exist without a vehicle run

- **WHEN** the local product closure, installer package, and emulator transport gates pass
- **THEN** the product may be marked ready for the local vehicle gate while vehicle, CI, and publication remain not run

# Gate: node --test --test-name-pattern="local evidence rejects forged producer, changed trees, failed receipts, and extra fields" tools/tests/beta1-evidence.test.ts

Gate contract: Maps: Requirement `Environment evidence retains its exact boundary`; Scenario `Host and emulator evidence exist without a vehicle run`. Preconditions: The canonical local evidence fixture and validator are available. Observable result: The named host test accepts only the exact host/local evidence type and rejects changed producer, source tree, artifact set, failed receipt, or extra fields rather than promoting it to another environment. Evidence boundary: Host semantic validation only; it does not execute an emulator, device, vehicle, GitHub workflow, or publication.

### Requirement: Emulator runtime evidence is fail-closed and non-production

The maintained functional emulator runner SHALL accept only `workspaceRoot`, `voyemuRoot`, `voyemuProfile`, `closure`, staged package-root `package`, immutable `dmg`, root-built first-party `fixture` or explicit null, new `output`, and optional typed `nativeSession` containing only the installed executable and private exclusive-start receipt path. It SHALL validate the exact app and CLI-symlink root, run package self-verification, and require inventory equality between the closure and app Resources excluding only ADB and its license. Product code SHALL author every invocation and semantic UI action. Caller commands, scripts, selectors, coordinate presets and pass attestations SHALL be rejected. Schema-3 legacy and schema-4 native-session evidence SHALL retain exactly twenty-seven case IDs, all original cases 1–17 with cases 13, 14, and 12 immediately after case 5 and before locale cases 6 and 7, extensions before final Settings uninstall, and the every-boot aggregate afterward. Each required case and independent subcheck SHALL pass for overall pass. A missing observation SHALL remain unavailable; a screenshot, host regression, old log or healthy status SHALL NOT independently satisfy a runtime or visual review requirement. Production and vehicle proof SHALL remain false and publication forbidden.

#### Scenario: The complete lifecycle is driven through fixed interfaces

- **WHEN** the functional inventory and closed config are verified
- **THEN** the exact original and extended case IDs and order are retained while caller-authored commands and verdicts are rejected

# Gate: node --test --test-name-pattern="functional inventory preserves all IDs and executes critical vehicle and launcher cases before locales" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Emulator runtime evidence is fail-closed and non-production`; Scenario `The complete lifecycle is driven through fixed interfaces`. Preconditions: Pinned host Node and the functional inventory are available. Observable result: Twenty-seven unique case IDs preserve the complete original scope, execute cases 13, 14, and 12 immediately after case 5, and execute extensions before final Settings removal. Evidence boundary: Host inventory semantics only; the complete runtime cycle, PNG review, device, vehicle, CI and publication remain separate gates.

# Gate: node --test --test-name-pattern="functional config rejects caller scripts coordinates passed flags and foreign fixture bytes" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Emulator runtime evidence is fail-closed and non-production`; Scenario `The complete lifecycle is driven through fixed interfaces`. Preconditions: A staged host package fixture is available. Observable result: Unknown scripts, selectors, coordinates, pass fields and foreign fixture bytes are rejected. Evidence boundary: Host config admission only; no Android actions are executed.

# Gate: node --test --test-name-pattern="functional schema rejects old phase receipts caller extras and unavailable overall pass" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Emulator runtime evidence is fail-closed and non-production`; Scenario `The complete lifecycle is driven through fixed interfaces`. Preconditions: Schema-3 and hostile host evidence fixtures are available. Observable result: Legacy phase receipts, schema-2 receipts without preparation, extra fields and unavailable overall-pass evidence fail admission. Evidence boundary: Host evidence schema only; no runtime or visual observations are manufactured.

### Requirement: Full functional acceptance without images remains a separate result

The opt-in `release/emulator-full-semantic.ts` entrypoint SHALL run the complete original twenty-seven-case program in its existing order, including preparation, normal CLI installation and removal, destructive Settings removal, owned-fixture cleanup, host gates, fault branches and independent guest-boot verification. It SHALL NOT replace the ordinary visual runner or admit an installed-kit subset as a complete functional run. Image dispatch SHALL be rejected; only the exact visual-only inventory SHALL remain unavailable. Every functional check in `FULL_REQUIRED`, exactly one `full-case-program-completed` marker per case, complete preparation and cleanup, passed source admission and independently verified semantic boots SHALL be required. Missing, failed, duplicate or unavailable functional checks, unknown unavailable checks, an active operation or mixed regression scope SHALL prevent semantic acceptance.

Destructive admission SHALL require a native session and exactly six permit fields: `schemaVersion`, `operation`, `packageSha256`, `dmgSha256`, `sessionReceiptSha256`, and `sourceReceiptSha256`. Schema version SHALL be 1, operation SHALL be `full27-install-uninstall`, and all four digests SHALL match the selected package inventory, immutable DMG, exclusive session receipt and constructor source-build receipt. Internal source admission SHALL recheck all eight constructor owners before and after execution. The root-owned external source fence SHALL separately bind all nine source owners, including components; the internal eight-owner receipt SHALL NOT be described as nine-owner verification.

The summary SHALL expose `semanticFullAcceptance` separately while retaining `passed`, `fullAcceptance`, and `visualAcceptance` as false and publication forbidden. Images, animation and visible appearance SHALL remain pending. A successful host fixture or semantic runtime summary SHALL NOT establish visual, vehicle, CI or publication acceptance.

#### Scenario: All twenty-seven functional programs have complete proof

- **WHEN** every required functional check, boot, source, preparation and cleanup observation passes without image capture
- **THEN** only semantic full acceptance may become true while full and visual acceptance remain false

#### Scenario: Functional proof is missing or incorrectly relabeled visual

- **WHEN** required proof is missing, duplicated, failed or unavailable, or an unknown unavailable check is supplied
- **THEN** semantic full acceptance remains false

#### Scenario: Destructive scope or authority differs

- **WHEN** permit authority differs, a regression subset is mixed with full mode, or current source or boot proof is incomplete
- **THEN** full functional admission or acceptance fails without promoting partial evidence

# Gate: node --test --test-name-pattern="exact 27 functional programs accept only semantic scope" tools/tests/full-semantic.test.ts

Gate contract: Maps: Requirement `Full functional acceptance without images remains a separate result`; Scenario `All twenty-seven functional programs have complete proof`. Preconditions: Current product host sources and full functional fixtures are available. Observable result: The complete ledger produces only semantic acceptance and keeps all broader acceptance flags false. Evidence boundary: Host fixtures or static wiring only; no emulator execution, external nine-owner fence, visual review, vehicle, CI or publication is established.

# Gate: node --test --test-name-pattern="unknown unavailable checks cannot be relabelled visual" tools/tests/full-semantic.test.ts

Gate contract: Maps: Requirement `Full functional acceptance without images remains a separate result`; Scenario `Functional proof is missing or incorrectly relabeled visual`. Preconditions: Current product host sources and full functional fixtures are available. Observable result: Unknown unavailable checks remain blockers rather than visual exemptions. Evidence boundary: Host fixtures or static wiring only; no emulator execution, external nine-owner fence, visual review, vehicle, CI or publication is established.

# Gate: node --test --test-name-pattern="missing or mixed scope, boot, source, active writer and incomplete absence deny acceptance" tools/tests/full-semantic.test.ts

Gate contract: Maps: Requirement `Full functional acceptance without images remains a separate result`; Scenario `Destructive scope or authority differs`. Preconditions: Current product host sources and full functional fixtures are available. Observable result: Missing or mixed scope, source, boot, cleanup, preparation and active-operation fixtures deny semantic acceptance. Evidence boundary: Host fixtures or static wiring only; no emulator execution, external nine-owner fence, visual review, vehicle, CI or publication is established.

# Gate: node --test --test-name-pattern="full mode preserves original destructive cycle, faults, host gate and boot/source proofs" tools/tests/full-semantic.test.ts

Gate contract: Maps: Requirement `Full functional acceptance without images remains a separate result`; Scenario `Destructive scope or authority differs`. Preconditions: Current product host sources and full functional fixtures are available. Observable result: Static wiring retains the destructive original cycle, six-field permit digest checks, host gates, source checks and image-dispatch rejection. Evidence boundary: Host fixtures or static wiring only; no emulator execution, external nine-owner fence, visual review, vehicle, CI or publication is established.

# Gate: node --test --test-name-pattern="deferred scope allows exactly full27 or installed regression, never both" tools/tests/full-semantic-schema.test.ts

Gate contract: Maps: Requirement `Full functional acceptance without images remains a separate result`; Scenario `Destructive scope or authority differs`. Preconditions: Current product host sources and full functional fixtures are available. Observable result: The evidence schema rejects mixed full and subset scope and an overall pass in deferred mode. Evidence boundary: Host fixtures or static wiring only; no emulator execution, external nine-owner fence, visual review, vehicle, CI or publication is established.

# Unverified: Complete no-images functional runtime acceptance

### Requirement: Preparation and normal removal retain separate lifecycle evidence

Before numbered cases, the runner SHALL obtain a complete nine-check absence receipt through the packaged CLI and record preparation separately with its normal, already-absent or emergency classification, exact action receipt, package, runtime and boot. Emergency cleanup SHALL satisfy only this precondition and SHALL NOT satisfy normal CLI or Settings removal. Case 1 SHALL require a fresh absent status on the prepared boot before installation and SHALL NOT perform removal. Case 2 SHALL require a strict normal absence receipt and a new independently verified boot. Case 3 SHALL reference that exact case-2 action receipt and require fresh absent status, Inspect, full prepared-profile verification and UI capture on that same boot without another removal. Initial seeding observations SHALL reference the real preparation absence proof separately from OEM/CAN values and present false root readback; missing proof SHALL remain unavailable.

#### Scenario: Preparation observes a terminal older installation

- **WHEN** initial Inspect contains one fully formed terminal successful installation journal from an older transaction, or a failed installation journal from an older transaction and a previous boot
- **THEN** the runner may observe that historical journal solely before packaged CLI cleanup, without using it as current installation or reboot evidence; a failed journal requires the contract error object and null installed APK; malformed, live, reboot-required, same-boot failed, duplicate or conflicting journals fail closed, and subsequent observations retain strict current transaction admission

#### Scenario: Initial cleanup uses emergency recovery

- **WHEN** the packaged CLI reports complete emergency absence during preparation
- **THEN** preparation may pass with its recorded classification while that receipt is rejected for normal CLI and Settings removal

#### Scenario: Absence is verified after normal removal

- **WHEN** case 2 has a normal absence receipt and verified new boot
- **THEN** case 3 reuses the exact receipt, rejects a changed boot or non-absent status, and performs no second removal

#### Scenario: Preparation does not prove every absence predicate

- **WHEN** preparation supplies an incomplete absence receipt
- **THEN** preparation fails before case 1 and no install is attempted

# Gate: node --test --test-name-pattern="preparation accepts retained completed older installation only before normal CLI cleanup" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Preparation and normal removal retain separate lifecycle evidence`; Scenario `Preparation observes a terminal older installation`. Preconditions: Minimal retained cycle65 terminal operation and a staged host package adapter are available. Observable result: Initial preparation admits the old completed operation, performs packaged CLI cleanup, and case 1 installs only after absence proof. Evidence boundary: Host dispatcher only; no Android operation is executed.

# Gate: node --test --test-name-pattern="preparation cleans failed previous-boot install through packaged CLI without borrowing reboot evidence" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Preparation and normal removal retain separate lifecycle evidence`; Scenario `Preparation observes a terminal older installation`. Preconditions: The operation-only failed cycle108 journal retained by cycle109 and a staged host package adapter are available. Observable result: The previous-boot terminal failed journal admits packaged CLI preparation cleanup and never supplies current reboot count; malformed errors, nonterminal outcomes, same-boot failure and retained history after cleanup reject before installation. Evidence boundary: Host parser and dispatcher only; packaged cleanup may classify emergency recovery, which does not prove normal CLI removal or any runtime acceptance.

# Gate: node --test --test-name-pattern="historical preparation rejects live failed malformed duplicate and later retained install operations" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Preparation and normal removal retain separate lifecycle evidence`; Scenario `Preparation observes a terminal older installation`. Preconditions: Retained operation mutations and host lifecycle adapters are available. Observable result: Live, failed, malformed identity, invalid completion, extra fields and duplicate operations fail closed; retained history after cleanup fails before install; current transaction and boot admission remain strict. Evidence boundary: Host parser and dispatcher only; historical completion does not attest runtime readiness.

# Gate: node --test --test-name-pattern="preparation admits older completed installation on the current boot but rejects concurrent foreign uninstall" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Preparation and normal removal retain separate lifecycle evidence`; Scenario `Preparation observes a terminal older installation`. Preconditions: Completed and live older operations on the current boot and a conflicting foreign uninstall are supplied to host adapters. Observable result: Completed history permits CLI cleanup, live history blocks cleanup, and conflicting operation documents are rejected. Evidence boundary: Host parser and dispatcher only; no guest operations are executed.

# Gate: node --test --test-name-pattern="preparation emergency never satisfies normal CLI or Settings removal" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Preparation and normal removal retain separate lifecycle evidence`; Scenario `Initial cleanup uses emergency recovery`. Preconditions: A staged host package and emergency process-adapter receipts are available. Observable result: Complete emergency preparation retains package/runtime authority, case 1 can install, and emergency normal CLI or Settings receipts fail rather than borrowing preparation. Evidence boundary: Host lifecycle and receipt rejection only; no Android policy recovery is executed.

# Gate: node --test --test-name-pattern="public dispatcher executes original install removal reinstall before a genuine missing-tab failure" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Preparation and normal removal retain separate lifecycle evidence`; Scenario `Absence is verified after normal removal`. Preconditions: A staged host package and successful preparation/install/removal adapter are available. Observable result: Original cases 1–4 pass, cases 1 and 3 perform no removal, and case 3 references the exact case-2 receipt before a real missing-tab observation fails. Evidence boundary: Host dispatcher semantics only; Android and visual readiness remain unverified.

# Gate: node --test --test-name-pattern="case three reuses normal removal proof only with fresh absent status and the same boot" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Preparation and normal removal retain separate lifecycle evidence`; Scenario `Absence is verified after normal removal`. Preconditions: Host adapters change the status or boot after a valid case-2 proof. Observable result: Case 3 rejects either change without another removal or reinstall. Evidence boundary: Host freshness semantics only; no guest action is executed.

# Gate: node --test --test-name-pattern="preparation retains complete already-absent proof and rejects incomplete cleanup before case one" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Preparation and normal removal retain separate lifecycle evidence`; Scenario `Preparation does not prove every absence predicate`. Preconditions: Complete already-absent and incomplete preparation fixtures are available. Observable result: Complete preparation retains boot and artifacts; incomplete preparation fails with all numbered cases not run and no install. Evidence boundary: Host preparation admission only; runtime seeding remains part of the unverified extended cycle.

# Unverified: Complete extended functional runtime and visual review

### Requirement: Post-install readiness observes owned asynchronous publication

After terminal installation and boot verification, an unhealthy selected-injection CLI status SHALL authorize only bounded read-only current resident observations. Readiness SHALL require unchanged runtime lease, boot, installed generation, release, manifest, catalog and startup identity, ready daemon state, and injection entries that are healthy or explicitly waiting for owned worker or native initialization publication. Malformed observations, hard faults, foreign states, authority changes, transport failures and exhausted bounds SHALL fail closed. Completion SHALL require a fresh healthy CLI status; pending state SHALL NOT become a pass.

#### Scenario: Owned publication becomes healthy

- **WHEN** current owned asynchronous publication is pending after installation and subsequently becomes healthy under the same authority
- **THEN** the dispatcher records read-only resident-status observations and requires a fresh healthy CLI status before installation passes

#### Scenario: Publication cannot establish readiness

- **WHEN** publication remains pending through the bound or a hard fault, generation change or boot change is observed
- **THEN** installation fails without another install or repair mutation

# Gate: node --test --test-name-pattern="post-install readiness dispatcher waits only for current owned publication and requires fresh healthy CLI" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Post-install readiness observes owned asynchronous publication`; Scenario `Owned publication becomes healthy`. Preconditions: A staged host package and public dispatcher adapter expose pending then active resident publication. Observable result: Case 1 passes only after two recorded read-only resident-status reads followed by healthy CLI status. Evidence boundary: Host dispatcher semantics only; Android publication remains a runtime gate.

# Gate: node --test --test-name-pattern="post-install readiness dispatcher bounds pending and rejects hard fault generation and boot changes" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Post-install readiness observes owned asynchronous publication`; Scenario `Publication cannot establish readiness`. Preconditions: Public dispatcher adapters expose indefinitely pending, failed, changed-generation or changed-boot observations. Observable result: Case 1 fails with bounded reads and one install invocation. Evidence boundary: Host dispatcher rejection semantics only; no device actions execute.

# Gate: node --test --test-name-pattern="post-install readiness dispatcher preserves healthy coexist beside owned pending publication" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Post-install readiness observes owned asynchronous publication`; Scenario `Owned publication becomes healthy`. Preconditions: A public dispatcher adapter exposes healthy skipped-coexist foreign-footprint beside owned pending publication. Observable result: Case 1 preserves the healthy coexist tuple and passes only after fresh healthy CLI status. Evidence boundary: Host dispatcher semantics only; no foreign agent or Android runtime executes.

### Requirement: Every guest boot has independent prepared-profile evidence

The functional runner SHALL borrow an already prepared and running Voyemu PID/port lease and invoke only the selected official runtime API plus the packaged installer for Android actions. Legacy runtime verification uses status/verify; native-session verification uses owned-session status and Android bootstrap readiness, without claiming equivalence to the legacy prepared-check inventory. It SHALL NOT start, stop, rebuild or replace the machine. Every new guest boot, including intermediate install boots, SHALL have pre/post boot identity, boot completion, the explicit backend verification contract, Enforcing, screenshot and hierarchy within that same boot. Legacy mode requires the complete exact prepared-check inventory; native mode requires current session/PID/serial, Android delivery and readiness, fenced original capture, and the unchanged case-specific device and application assertions. Crossed, offline, missed or incomplete intermediate windows SHALL remain unavailable. Read-only boot-observation/inspect/snapshot/screenshot during an active writer SHALL NOT interrupt it or permit mutations. Observation timeout SHALL retain the unresolved child PID and lease and forbid further mutations. Final Settings uninstall SHALL have its independent observer armed before the UI confirmation and SHALL require normal nine-check absence and a new independently verified boot; emergency or host removal SHALL NOT substitute for that action.

#### Scenario: Boot UI capture uses a recorded stock screen

- **WHEN** a new boot has independently passed completion, Enforcing and exact prepared-profile verification and the installer writer is terminal
- **THEN** the runner records fixed stock Settings navigation before paired PNG and hierarchy capture and retains the same-boot authority checks

#### Scenario: Preparation restores ordinary primary UI focus

- **WHEN** absence preparation is complete and no installer writer owns the device
- **THEN** the runner records ordinary Home input followed by stock Settings navigation before PNG and hierarchy capture; a failed Home input stops preparation before installation and same-boot checks remain mandatory

#### Scenario: A functional installer observation deadline expires

- **WHEN** a packaged installer child exceeds its observation deadline
- **THEN** the child remains alive, its lease is retained and subsequent mutations are forbidden

#### Scenario: An intermediate install boot is not independently captured

- **WHEN** a durable reboot count identifies a boundary without complete same-boot verification and UI capture
- **THEN** the boundary remains unavailable and prevents overall pass

# Gate: node --test --test-name-pattern="preparation foregrounds Home then Settings before its UI evidence and stops on failed Home" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Every guest boot has independent prepared-profile evidence`; Scenario `Preparation restores ordinary primary UI focus`. Preconditions: The real dispatcher runs with an application-root fixture unavailable before ordinary Home input. Observable result: Preparation records Home, Settings, PNG and hierarchy in order; a failed Home stops before any install invocation. Evidence boundary: Host orchestration only; live UI readiness requires the packaged CLI and unchanged guest lease.

# Gate: node --test --test-name-pattern="boot UI pairs follow recorded stock Settings navigation and preserve boot authority" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Every guest boot has independent prepared-profile evidence`; Scenario `Boot UI capture uses a recorded stock screen`. Preconditions: The public dispatcher executes the host staged-kit lifecycle with exact profile and CLI fixtures. Observable result: Each passed post-writer boot records stock Settings navigation before its PNG and hierarchy in the same action scope, with unchanged boot receipt binding. Evidence boundary: Host routing and authority binding only; stock screen accessibility and guest behavior require the live immutable kit.

# Gate: node --test --test-name-pattern="public dispatcher uses only packaged CLI and Voyemu and retains an unresolved transaction lease" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Every guest boot has independent prepared-profile evidence`; Scenarios `A functional installer observation deadline expires`, `An intermediate install boot is not independently captured`. Preconditions: Host process adapters, staged package and prepared-profile fixtures are available. Observable result: The dispatcher retains a timed-out child lease, prevents later mutations and fails the incomplete lifecycle. Evidence boundary: Host routing and rejection semantics; actual intermediate windows and final Settings removal require a fresh runtime cycle.

# Gate: node --test --test-name-pattern="installer observation timeout leaves its owned host child alive until natural completion" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Every guest boot has independent prepared-profile evidence`; Scenario `A functional installer observation deadline expires`. Preconditions: A real host child fixture is available. Observable result: The production observation deadline returns without terminating the child, which completes naturally. Evidence boundary: Host process lifetime only; no guest operation is interrupted or executed.

# Gate: node --test --test-name-pattern="intermediate boot observation retries expected offline and Permissive before exact completed boundary" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Every guest boot has independent prepared-profile evidence`; Scenario `An intermediate install boot is not independently captured`. Preconditions: A host fake transition emits valid pending Permissive/offline observations followed by old, incomplete and exact new boot states. Observable result: Closed reads retry only the expected transition until a new completed boot with exact durable count is observed. Evidence boundary: Host transition semantics only; the full prepared inventory still requires a real same-boot runtime check.

# Gate: node --test --test-name-pattern="intermediate boot observation never borrows a later boot or retries foreign identity" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Every guest boot has independent prepared-profile evidence`; Scenario `An intermediate install boot is not independently captured`. Preconditions: Host transitions expose a later count, a changed runtime identity or an exhausted pending window. Observable result: Later boot substitution and foreign identity fail immediately, while exhausted expected reads remain unavailable. Evidence boundary: Host rejection semantics only; no runtime window is inferred or repaired.

### Requirement: Routine boot reads do not depend on optional forensic completion

Routine boot identity and intermediate boot pre/post reads SHALL dispatch the closed packaged `boot-observation` action and validate its bounded mandatory body. They SHALL retain the exact transaction, durable reboot count, new independently read UUID and missed-window rejection. A raw empty or `0` boot-completed property SHALL remain incomplete; only `1` with Enforcing and the exact expected count SHALL satisfy completion. Full Inspect SHALL remain the source for process, status, injection, crash and vehicle authority consumers. Mandatory read failure SHALL NOT be replaced with caller boot data, healthy status, screenshot or a prepared-profile count; full official inventory verification and same-boot capture SHALL remain separate required evidence.

#### Scenario: Full optional inspection cannot complete

- **WHEN** a closed full Inspect times out on optional forensic work
- **THEN** the production mandatory capture dispatches its own closed boot read and uses only that read's independently observed identity

#### Scenario: A mandatory operation has foreign or malformed identity

- **WHEN** mandatory data contains unknown fields, malformed transaction or journal digests, invalid counts or a mismatched boot
- **THEN** malformed data is rejected and a mismatched boot cannot supply a current durable reboot count

#### Scenario: Empty completion precedes the expected intermediate boot completion

- **WHEN** the expected durable count and independently read UUID are observed with empty completion
- **THEN** they remain pending until completed Enforcing on that boot, and a later boot count cannot substitute

# Gate: node --test --test-name-pattern="production mandatory boot capture survives closed full Inspect timeout without borrowing its proof" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Routine boot reads do not depend on optional forensic completion`; Scenario `Full optional inspection cannot complete`. Preconditions: Actual InstallerUi production dispatch uses a host process adapter that times out full Inspect and returns the mandatory capture. Observable result: Old full read fails, the closed mandatory request succeeds within its separate observation budget and rejects caller authority fields. Evidence boundary: Host dispatch dependency only; the exact optional Android hang remains unidentified and runtime verification remains required.

# Gate: node --test --test-name-pattern="mandatory boot capture rejects unknown operation fields transaction digests counts and wrong-boot substitution" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Routine boot reads do not depend on optional forensic completion`; Scenario `A mandatory operation has foreign or malformed identity`. Preconditions: Mandatory parser and durable-operation selector receive valid and altered host fixtures. Observable result: Invalid fields, digests and counts fail; foreign transaction fails and wrong-boot operation supplies no count. Evidence boundary: Host parser and selection only; missed intermediate windows remain governed by the independent boot evidence requirement.

# Gate: node --test --test-name-pattern="production mandatory capture retries exact transient Permissive before completed Enforcing" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Routine boot reads do not depend on optional forensic completion`; Scenario `A mandatory operation has foreign or malformed identity`. Preconditions: Production mandatory capture receives canonical Permissive, Enforcing and malformed mode/operation fixtures. Observable result: Exact transient Permissive retries to Enforcing; invalid mode or operation fails without being reclassified as pending. Evidence boundary: Host transition parsing; pre-capture QEMU/UID/Enforcing guards remain unchanged.

# Gate: node --test --test-name-pattern="mandatory capture failure remains explicit without full Inspect or healthy fallback" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Routine boot reads do not depend on optional forensic completion`; Scenario `Full optional inspection cannot complete`. Preconditions: Actual InstallerUi dispatch receives mandatory command error and timeout fixtures. Observable result: Both failures remain explicit and dispatch no fallback read or healthy-status substitution. Evidence boundary: Host dispatch only; retained child ownership remains covered by the independent timeout gate.

# Gate: node --test --test-name-pattern="unset boot completion remains pending with exact UUID and durable count before completion" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Routine boot reads do not depend on optional forensic completion`; Scenarios `Empty completion precedes the expected intermediate boot completion` and `A mandatory operation has foreign or malformed identity`. Preconditions: The production mandatory capture, parser and intermediate poll receive empty then completed Enforcing reads with the same UUID and exact durable count, or a later boot count. Observable result: Empty remains false with its real UUID and count; completion requires the next completed same-boot read, while a missed first boot remains unavailable and malformed fields fail. Evidence boundary: Host capture and transition semantics only; full inventory verification, UI captures and actual Android windows remain separate runtime gates.

### Requirement: Functional observations retain their production source and freshness

The runner SHALL parse only the bounded ordinary installer tar.xz log archive containing exactly the three unique regular USTAR members `manifest.json`, `records.jsonl` and `context.json`, and SHALL bind its verified-stream envelope and daemon context to the selected serial, exact release and manifest. XZ integrity, USTAR checksums, decoded size bounds and zero-only end padding SHALL reject truncated, corrupted, extra or unconsumed compressed or decoded content. The host verification tool SHALL use an installed XZ decoder with bounded output and timeout; this tool dependency SHALL NOT enter the production installer. Agent diagnostics SHALL retain actual source attribution. Sequence freshness SHALL be evaluated only between independently observed same-boot endpoints and SHALL reject duplicates, regression and crossed boots. Vehicle root readback SHALL distinguish a JSON boolean, absent and unavailable. Cold vehicle checks SHALL compare OEM defaults with agents disabled and preserved OEM values with saved root intent; warm checks SHALL require actual callback OFF/ON generation plus ordered authoritative reset and restore readbacks. Shared OEM switch resources SHALL be scoped to the observed feature parent. Catalog checks SHALL use actual UID, flags, classification and full fresh scan boundaries, including positive fixture classification.

The owning Settings copy producer SHALL execute fixed offline host tests against source snapshots matching the delivered source-build receipt, retain actual JUnit reports, and bind generated public-API copy to the exact main source inventory, source digest, signed catalog and every delivered agent DEX. Runtime captions SHALL use that producer output rather than a second translation table. Effective Voboost language and unchanged OEM process/resource locale SHALL come from fresh `ui-qinggan` composition records. Bounded layout measurements SHALL retain actual wire roles, source geometry and snapshot identity; measurements SHALL NOT substitute for PNG review. Host startup, retry/cancel and listener tests SHALL remain explicitly host evidence. The primary profile SHALL retain its normal initial vehicle defaults; other initial source-policy inputs SHALL NOT be claimed as emulator seeding.

#### Scenario: Historical or foreign observations are supplied

- **WHEN** a log window crosses a boot, a source or delivered DEX differs, an unavailable state is presented as false, or a shared switch belongs to another feature
- **THEN** the observation is rejected instead of becoming current runtime proof

#### Scenario: Source-bound measurements exist without visual review

- **WHEN** all bounded layout targets and their source-derived dimensions are observed but no actual PNG review is available
- **THEN** the measurement subcheck may pass while the visual subcheck remains unavailable

# Gate: node --test --test-name-pattern="fresh diagnostics reject crossed boots sequence reset duplicate and historical readback" tools/tests/functional-observations.test.ts

Gate contract: Maps: Requirement `Functional observations retain their production source and freshness`; Scenario `Historical or foreign observations are supplied`. Preconditions: Accepted and hostile same-boot log fixtures are available. Observable result: Crossed boots, sequence reset and duplicate records fail, and historical readback is excluded. Evidence boundary: Host freshness semantics only; no Android execution is performed.

# Gate: node --test --test-name-pattern="Settings copy binds exact source inventory source digest signed metadata and delivered DEX" tools/tests/settings-copy.test.ts

Gate contract: Maps: Requirement `Functional observations retain their production source and freshness`; Scenario `Historical or foreign observations are supplied`. Preconditions: Accepted and hostile source/catalog/DEX fixtures are available. Observable result: Changed source inventory or digest, metadata, delivered DEX, captions and control topology fail. Evidence boundary: Host copy admission only; the real owning Gradle producer and runtime captions remain separate executions.

# Gate: node --test --test-name-pattern="actual bounded layout binds all eight wire roles and source radio widths without substituting for PNG review" tools/tests/functional-observations.test.ts

Gate contract: Maps: Requirement `Functional observations retain their production source and freshness`; Scenario `Source-bound measurements exist without visual review`. Preconditions: Bounded layout and component-source fixtures are available. Observable result: All eight exact wire roles and actual source-derived widths are required, while the measurement parser has no visual pass result. Evidence boundary: Host measured-record semantics only; actual PNG inspection remains unavailable until performed.

# Gate: node --test --test-name-pattern="valid XZ integrity does not admit trailing or concatenated archive content" tools/tests/installer-logs.test.ts

Gate contract: Maps: Requirement `Functional observations retain their production source and freshness`; Scenario `Historical or foreign observations are supplied`. Preconditions: A valid XZ archive with trailing garbage or a concatenated stream containing hidden archive content is available. Observable result: All trailing or concatenated content is rejected despite a valid first decoded archive. Evidence boundary: Host XZ/TAR integrity only; freshness and runtime provenance require separate observations.

# Gate: node --test --test-name-pattern="ordinary log tar.xz binds exact package/device and exposes actual agent diagnostic fields" tools/tests/installer-logs.test.ts

Gate contract: Maps: Requirement `Functional observations retain their production source and freshness`; Scenario `Historical or foreign observations are supplied`. Preconditions: An exact-identity synthetic tar.xz archive carries authenticated agent diagnostics. Observable result: The consumer accepts the exact archive and retains its source and diagnostic fields. Evidence boundary: Host archive admission only; runtime freshness and provenance require independent observations.

# Gate: node --test --test-name-pattern="truncated corrupt duplicate traversal nonregular and hidden USTAR entries are rejected" tools/tests/installer-logs.test.ts

Gate contract: Maps: Requirement `Functional observations retain their production source and freshness`; Scenario `Historical or foreign observations are supplied`. Preconditions: Truncated and corrupt XZ, invalid USTAR members, duplicate or unexpected names, nonzero padding and oversized payload fixtures are available. Observable result: The consumer rejects each invalid archive, including bounded decoder expansion. Evidence boundary: Host XZ/TAR validation only; native runtime observations remain separate.

# Unverified: Complete extended functional runtime and visual review

### Requirement: Root vehicle observations retain actual operation authority

The consumer SHALL accept only canonical daemon `agent-state/vehicle-state-observed` records for the fixed forced-EV and pedestrian-warning armed keys, with string fields and strict value/missing/unavailable outcomes. Read records SHALL retain their exact request identity; write-readback records SHALL represent the producer's actual authorized durable write and secure reread. Observation admission SHALL require the current boot, resident generation, catalog, process, target PID and registered start time, and SHALL compare the exact desired revision where available. A later unavailable, conflicting target or conflicting state operation SHALL NOT be hidden by an earlier boolean. Historical operation records SHALL NOT become fresh on-demand queries. A read baseline may support a same-boot restore only with a complete retained no-conflicting-operation window and an independent fresh CAN restore observation. Missing authority SHALL remain unavailable; pedestrian-warning armed true means the warning is disabled.

#### Scenario: A frame or historical state is presented as authority

- **WHEN** attribution, fixed key, typed boolean, resident identity, target lifetime, boot or exact revision is foreign, malformed or stale
- **THEN** the consumer rejects malformed records or retains unavailable without interpreting absence as false

#### Scenario: A valid root baseline has an incomplete or conflicting window

- **WHEN** the retained read is missing, sequence coverage has a gap, or a newer operation changes state or target lifetime
- **THEN** the carried baseline remains unavailable

# Gate: node --test --test-name-pattern="canonical root vehicle receipt rejects forged frame identity malformed booleans and operation failures" tools/tests/functional-observations.test.ts

Gate contract: Maps: Requirement `Root vehicle observations retain actual operation authority`; Scenario `A frame or historical state is presented as authority`. Preconditions: Fixed producer-contract host records and hostile identity/outcome variants are available. Observable result: Only canonical daemon records, exact fixed keys, strict string booleans and legal operation fields parse. Evidence boundary: Host parser conformance only; no guest operation is performed.

# Gate: node --test --test-name-pattern="root vehicle state requires current resident target boot revision and fresh actual operation" tools/tests/functional-observations.test.ts

Gate contract: Maps: Requirement `Root vehicle observations retain actual operation authority`; Scenario `A frame or historical state is presented as authority`. Preconditions: Same-boot resident and current-target host authorities are available. Observable result: Stale operation, wrong generation/catalog/process/PID/revision and missing target lifetime remain unavailable; a newer unavailable event wins. Evidence boundary: Host operation admission only; actual CAN and root operations require runtime receipts.

# Gate: node --test --test-name-pattern="carried root read baseline requires complete unchanged same-boot operation window" tools/tests/functional-observations.test.ts

Gate contract: Maps: Requirement `Root vehicle observations retain actual operation authority`; Scenario `A valid root baseline has an incomplete or conflicting window`. Preconditions: Complete and gapped same-boot host windows are available. Observable result: Missing root baseline, gaps, target replacement and later unavailable operations cannot be carried as a boolean. Evidence boundary: Host retained-window semantics only; fresh runtime CAN restoration remains independent.

# Unverified: Complete extended functional runtime and visual review

### Requirement: Install preparation retains real typed operation receipts

The desktop packaged CLI consumer SHALL retain only canonical stderr DEBUG `Install preparation receipt: ` schema-1 records with exactly the producer's 22 fields, selected serial, process-local ordered sequence, actual attempt, exit code, output digests and bounded UTF-8 error. Installation readiness SHALL require the successful ordered Wait/Root/Wait/Remount/RelaxFirstPhase/VerifyWriteAccess operations from the same install action and exact manifest transaction. The source-bound VerifyWriteAccess success proves UID 0 and its actual private `/system/etc/init` write probe; RelaxFirstPhase success proves Permissive through its existing checked script. No new root permission SHALL be inferred or requested. Subsequent EnforceSelinux success and independently captured Enforcing guest boots SHALL remain separate evidence. Null initial boot and metadata copied from a previously accepted journal SHALL NOT be interpreted as a fresh boot observation or independent raw device report. Failed or missing preparation SHALL remain explicit; separate CLI-only builds without this desktop producer SHALL NOT be described as emitting these receipts.

#### Scenario: A preparation marker is forged, failed or incomplete

- **WHEN** serial, schema, manifest, sequence, typed output, bounded error or accepted report fields differ
- **THEN** malformed input is rejected and actual failures remain failures

#### Scenario: Later metadata is offered without first-phase operations

- **WHEN** an Enforcing or previous-journal receipt exists without the complete successful first-phase order
- **THEN** readiness is unavailable and no fresh initial boot or raw output is inferred

# Gate: node --test --test-name-pattern="installer preparation accepts only exact typed same-action serial receipts and preserves real failures" tools/tests/install-preparation.test.ts

Gate contract: Maps: Requirement `Install preparation retains real typed operation receipts`; Scenario `A preparation marker is forged, failed or incomplete`. Preconditions: Producer-contract DEBUG host transcripts and hostile field/error/identity variants are available. Observable result: Exact schema, selected serial, ordered sequence and real failure output parse; forged success and unknown fields fail. Evidence boundary: Host parser only; no Android preparation is performed.

# Gate: node --test --test-name-pattern="installer preparation ordered readiness and previous journal correlation never infer fresh boot or raw output" tools/tests/install-preparation.test.ts

Gate contract: Maps: Requirement `Install preparation retains real typed operation receipts`; Scenario `Later metadata is offered without first-phase operations`. Preconditions: Ordered first-phase and accepted previous-journal host records are available. Observable result: Only complete successful operation order establishes readiness; terminal metadata alone does not, and initial null boot remains null. Evidence boundary: Host receipt semantics only; source-bound actual operations and Enforcing boots require a fresh packaged desktop runtime.

# Unverified: Complete extended functional runtime and visual review

# Gate: ./gradlew test --tests ru.voboost.tests.installer.cli.InstallPreparationTest --offline

Gate contract: Maps: Requirement `Install preparation retains real typed operation receipts`; Scenario `A preparation marker is forged, failed or incomplete`. Preconditions: Canonical DEBUG stderr receipts from the existing production schema and hostile serial, transaction, sequence, boot and output fixtures are available. Observable result: The actual streaming InstallMonitor captures only bounded canonical stderr, rejects stdout/INFO markers, and the preparation validator admits ordered typed operations and independently observed journal boots. Evidence boundary: Root Java host receipt validation and install source wiring only; no device preparation, native applied receipt or runtime acceptance is inferred.

### Requirement: Functional fixtures retain ownership and cleanup obligations

The runner SHALL remove only its approved `ru.voboost.tests.launcherfixture` fixture, after case-12 and case-18 observations have been retained and before final Settings uninstall. Ownership SHALL require the selected-serial original absence receipt and actual successful install receipt for the fixed first-party package and successfully admitted binary manifest. Preexisting or unproven applications SHALL remain untouched. Cleanup SHALL retain its closed RemoveFixture action, fresh fixture absence and observed OEM-grid preservation. A failed cycle with owned or uncertain installed fixture SHALL record its unresolved obligation rather than perform unproven recovery or silently claim a clean state.

#### Scenario: A fixture existed before the cycle

- **WHEN** original absence, exact approved identity or actual install success is missing
- **THEN** the runner does not authorize removal from that cycle

# Gate: node --test --test-name-pattern="owned fixture cleanup requires original absence exact approved identity and real install success" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Functional fixtures retain ownership and cleanup obligations`; Scenario `A fixture existed before the cycle`. Preconditions: Original-absence and install-output host variants are available. Observable result: Preexisting, failed or foreign-package/bytes variants cannot establish owned cleanup. Evidence boundary: Host ownership predicate only; actual RemoveFixture, fresh absence and OEM grid require runtime actions.

# Unverified: Complete extended functional runtime and visual review

### Requirement: Startup cases observe actual radio selections and current copy

Hidden and none functional cases SHALL select their real Settings startup radios and confirm both the current root mode and fresh selected UI before reboot. None SHALL require the actual post-reboot process absence and return only through the packaged computer installer. The description program SHALL select and observe interface, hidden and none in all three source-bound locales, with a packaged computer return after every none selection. An old retained profile SHALL NOT satisfy the selected description. Current host copy and rejected-mutation tests SHALL remain distinct from actual selected descriptions and observed rejected ACKs.

#### Scenario: None description is selected in a live Settings host

- **WHEN** the nine-description program reaches none in each locale
- **THEN** it observes the actual selected current description and returns through the computer before the next locale

# Gate: node --test --test-name-pattern="startup profile program observes nine actual selected descriptions and returns from none only by computer" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Startup cases observe actual radio selections and current copy`; Scenario `None description is selected in a live Settings host`. Preconditions: Host adapters observe each fixed language/radio/description/computer step. Observable result: The production program executes all nine ordered description observations with computer-only none returns, and stops subsequent mutations after a failed observation. Evidence boundary: Host program ordering only; real radios, root ACK, preserved live host and copy require the new source-bound runtime kit.

# Gate: node --test --test-name-pattern="startup reboot program selects and observes real hidden and none radios before guest reboot" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Startup cases observe actual radio selections and current copy`; Scenario `None description is selected in a live Settings host`. Preconditions: Host adapters record the actual hidden and none program's radio, selected-description and guest-reboot steps. Observable result: Both modes select and observe before reboot, and unconfirmed descriptions prevent reboot. Evidence boundary: Host production program ordering only; actual target radios and none process absence remain runtime checks.

# Unverified: Complete extended functional runtime and visual review

### Requirement: Voyemu owns the emulator lifecycle

The runner SHALL invoke only the selected local Voyemu CLI's `status`, `start`, `verify`, and `stop` commands with explicit root/profile and JSON output. It SHALL reject malformed, failed or foreign-profile envelopes, an unprepared machine, a changed runtime PID/port, and missing or failed verification checks. The checks SHALL explicitly prove the Enforcing receipt. The installer serial SHALL be derived only from the accepted console port, and the runner SHALL revalidate that runtime identity before each product phase. The runner SHALL NOT launch SDK emulator binaries, issue ADB emulator kill, rebuild/fetch/deps, or wipe userdata. A machine already running before admission SHALL retain its exact runtime identity and remain running after the check. A machine admitted from a stopped state SHALL be released through Voyemu stop only after its recorded runtime identity is revalidated, and final status SHALL prove stopped.

#### Scenario: The runtime was already running

- **WHEN** the initial Voyemu status and start replies identify the same running PID and port
- **THEN** the runner uses only the corresponding serial and releases its use without stopping that machine

#### Scenario: The runtime was stopped before the check

- **WHEN** the prepared profile was stopped and Voyemu admits its running PID and port
- **THEN** a later product failure still invokes Voyemu stop and requires a stopped final status

#### Scenario: The runtime identity changes before a product phase

- **WHEN** Voyemu status reports another PID or port after setup but before a product phase
- **THEN** that product operation is not invoked and cleanup does not stop the replacement machine

# Gate: node --test --test-name-pattern="Voyemu-owned runtime is stopped and pre-existing runtime is retained" tools/tests/voyemu-gate.test.ts

Gate contract: Maps: Requirement `Voyemu owns the emulator lifecycle`; Scenarios `The runtime was already running`, `The runtime was stopped before the check`. Preconditions: Independent CLI fixtures return exact Voyemu JSON envelopes and a product child returns a primary failure. Observable result: Only the receipt-bound serial is used; owned runtime is stopped and final status is checked; borrowed runtime retains its PID/port without a stop call. Evidence boundary: Host routing and ownership semantics; actual Voyemu start/verify/stop and Android behavior require a live kit run.

# Gate: node --test --test-name-pattern="runtime replacement before a product phase blocks that operation" tools/tests/voyemu-gate.test.ts

Gate contract: Maps: Requirement `Voyemu owns the emulator lifecycle`; Scenario `The runtime identity changes before a product phase`. Preconditions: A borrowed runtime passes initial admission and its later status reports a replacement PID. Observable result: The pending installer operation and Voyemu stop are both absent from the invocation trace; evidence fails. Evidence boundary: Host routing and identity checks; a real Android runtime replacement is not performed.

### Requirement: Machine validation preserves human CLI progress

Installer children of the machine validation runner SHALL receive `VOBOOST_LOG=error` through a fixed child environment. The runner SHALL NOT change its parent's log setting or the installer's human default. Error output SHALL remain a failing product result.

#### Scenario: Human debug output is configured in the parent

- **WHEN** the parent has `VOBOOST_LOG=debug` and the runner starts a product child
- **THEN** the child observes `error` while the parent's setting remains `debug`

# Gate: node --test --test-name-pattern="installer children use machine output mode without changing parent progress" tools/tests/voyemu-gate.test.ts

Gate contract: Maps: Requirement `Machine validation preserves human CLI progress`; Scenario `Human debug output is configured in the parent`. Preconditions: A real Node child and a parent debug environment are available. Observable result: The real production spawn adapter passes the fixed error threshold to the child and preserves the parent's value. Evidence boundary: Host child environment; this does not execute an Android operation or prove a vehicle console replay.

### Requirement: Window metadata preserves the Android 9 integer contract

The API28 multiwindow parser SHALL preserve canonical signed 32-bit window layers and window types `-1`, `1`, `2`, `3`, `4`, and `5`. Unclassified type `-1` windows SHALL retain their actual nodes without becoming generic application or tap targets. Other type values, noncanonical integer spellings and signed layer overflow SHALL be rejected.

#### Scenario: A legitimate unclassified window or signed layer is observed

- **WHEN** a bounded multiwindow capture contains a signed 32-bit layer or an unclassified window
- **THEN** parsing retains the metadata and nodes while application selection excludes the unclassified window

#### Scenario: Window metadata is outside the canonical contract

- **WHEN** a layer overflows signed 32-bit range or a type or integer spelling is unsupported
- **THEN** the capture is rejected before any target selection

# Gate: node --test --test-name-pattern="window metadata preserves signed layers and unclassified windows without target authority" tools/tests/emulator-window-metadata.test.ts

Gate contract: Maps: Requirement `Window metadata preserves the Android 9 integer contract`; Scenarios `A legitimate unclassified window or signed layer is observed`, `Window metadata is outside the canonical contract`. Preconditions: Synthetic variants of the retained host serializer fixture are available. Observable result: The actual parser preserves signed layer endpoints and unclassified nodes, the actual focused-application selector and tap path reject those nodes before dispatch, and malformed integer/type variants fail. Evidence boundary: Host parser and selector behavior only; actual Android descriptor values and full runtime captures require the live kit.

### Requirement: Hidden clipped rectangles retain data without action authority

API28 window capture parsing SHALL preserve raw negative-extent rectangles only for nodes explicitly marked invisible. Such nodes SHALL remain excluded from selectors and tap dispatch. Visible nodes and window rectangles with negative extents SHALL be rejected; existing numeric, XML and capture authority bounds SHALL remain unchanged.

#### Scenario: A hidden node is clipped outside its viewport

- **WHEN** a captured node is explicitly invisible and its clipped bottom or right precedes its top or left
- **THEN** the parser retains its actual coordinates while selection and tap remain unavailable

#### Scenario: Visible geometry has reversed extents

- **WHEN** a visible node or a window contains a negative-extent rectangle
- **THEN** the parser rejects the capture rather than inventing positive geometry

# Gate: node --test --test-name-pattern="hidden clipped node rectangles retain raw data without target authority" tools/tests/emulator-window-metadata.test.ts

Gate contract: Maps: Requirement `Hidden clipped rectangles retain data without action authority`; Scenarios `A hidden node is clipped outside its viewport`, `Visible geometry has reversed extents`. Preconditions: Host serializer variants use the recorded cycle78 clipped rectangle shapes with explicit visibility. Observable result: The actual parser retains hidden raw bounds, rejects visible/window reversed variants, and InstallerUi refuses an observed hidden tap without another child dispatch. Evidence boundary: Host parser and actuation guard only; full guest capture and real UI still require the immutable kit.

### Requirement: Closed capture transport remains a bounded read-only retry

The emulator runner SHALL recognize the packaged capture wrapper around an actual `error: closed` as a transport transition only after a nonzero terminal result without timeout, signal or overflow. It SHALL NOT classify malformed boot observations, permission failures, changed leases or unresolved processes as transport loss, replay a mutation, or promote a retry to boot acceptance.

#### Scenario: A boot capture loses its transport

- **WHEN** the packaged read-only boot capture exits with the observed closed-transport wrapper
- **THEN** the existing bounded observer may read again while preserving all independent boot identity, completion, inventory, Enforcing and UI requirements

# Gate: node --test --test-name-pattern="actual closed boot capture retries only terminal bounded transport loss" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Closed capture transport remains a bounded read-only retry`; Scenario `A boot capture loses its transport`. Preconditions: The retained actual cycle79 capture stderr and product classifier are available. Observable result: The named host regression accepts the exact terminal closed-capture envelope and rejects success, unresolved results, timeout, signal, either overflow, malformed boot data, permission denial and lease change. Evidence boundary: Host transport classification only; a fresh emulator run must independently prove every reboot and all lifecycle cases.


### Requirement: Root observation waiting is confined to the actual installer lifetime

The runner SHALL recognize only the exact packaged BootObservation guard-root failure while its actual normal install invocation remains active, with unchanged runtime ownership. That result SHALL authorize bounded read-only observation waiting, never successful boot fields, root provisioning, mutation replay or a changed acceptance criterion. Other requests, inactive installation, identity or malformed failures, timeout, signal, overflow, extra diagnostics and successful JSON SHALL remain rejected. The active lifetime SHALL clear on every install completion or failure.

#### Scenario: The active writer has not yet restored root observation

- **WHEN** its fixed boot read returns the exact remote73 empty-stdout root-guard failure under the same VM lease
- **THEN** only the existing observer read is retried, and acceptance still requires a genuine new completed boot with exact reboot count and full independent verification

#### Scenario: A root failure occurs outside the authorized observation scope

- **WHEN** the writer is inactive or the request or failure does not match the closed contract
- **THEN** no pending observation or install replay is authorized

# Gate: node --test --test-name-pattern="public dispatcher retries root observation only during its active install without replay" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Root observation waiting is confined to the actual installer lifetime`; Scenarios `The active writer has not yet restored root observation`, `A root failure occurs outside the authorized observation scope`. Preconditions: The actual product dispatcher executes controlled packaged-process responses and an active install progress callback. Observable result: An exact guard-root read followed by genuine boot data reaches independent boot verification without repeating install; the same read before an active install fails preparation without starting one. Evidence boundary: Host orchestration only; a fresh packaged emulator campaign must independently satisfy all boot and lifecycle observations.

# Gate: node --test --test-name-pattern="root observation pending requires exact failure and active installer scope" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Root observation waiting is confined to the actual installer lifetime`; Scenario `A root failure occurs outside the authorized observation scope`. Preconditions: The exact fixed diagnostic and public failure envelope are available. Observable result: The classifier rejects inactive scope, Inspect, success/unresolved processes, timeout, signal, either overflow, extra diagnostic bytes, identity/exit changes and successful JSON. Evidence boundary: Host closed-envelope classification, not proof of an actual Android root transition.

### Requirement: Post-writer boot navigation follows actual invocation completion

A retained installer boot kind SHALL NOT prohibit fixed stock Settings navigation once the actual installer invocation has terminated. The runner SHALL keep UI mutations forbidden while that invocation remains active, and SHALL retain same-boot prepared inventory, Enforcing, UUID and paired capture requirements in both cases.

#### Scenario: An intermediate boot observer outlives its installer writer

- **WHEN** the writer has completed before the retained boot reaches UI capture
- **THEN** recorded stock Settings navigation precedes both captures and its monotonic start follows the recorded installer completion

# Gate: node --test --test-name-pattern="intermediate boot verification and captures precede exact acceptance consumption" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Post-writer boot navigation follows actual invocation completion`; Scenario `An intermediate boot observer outlives its installer writer`. Preconditions: The actual dispatcher processes a completed install callback while retaining an intermediate boot row. Observable result: Independent boot verification succeeds only with navigation after recorded writer completion and before paired PNG/hierarchy capture. Evidence boundary: Host invocation lifetime and ordering; all actual guest observations still require the immutable package.

### Requirement: Resident readiness uses narrow same-boot observation

The post-install readiness loop SHALL use only the closed resident-status capture rather than forensic Inspect. It SHALL require completed and Enforcing pre/post observations with matching canonical boot UUID, the fixed resident file section and bounded body, while retaining current resident authority validation, the ten-second and twenty-read bounds and fresh final healthy CLI confirmation. A cross-boot or incomplete capture SHALL fail closed. The entire captured resident JSON SHALL be consumed; a valid prefix followed by another document or trailing data SHALL NOT authorize readiness or further pending reads.

#### Scenario: Readiness is checked after an unhealthy selected injection status

- **WHEN** the dispatcher observes owned pending publication followed by readiness
- **THEN** only recorded resident-status reads occur between the initial degraded and final healthy CLI results, with no forensic Inspect or mutation

# Gate: node --test --test-name-pattern="post-install readiness uses narrow status observation without forensic Inspect" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Resident readiness uses narrow same-boot observation`; Scenario `Readiness is checked after an unhealthy selected injection status`. Preconditions: The actual dispatcher executes pending-to-ready narrow observation responses. Observable result: Case 1 passes with only two resident-status reads in the exact degraded-to-healthy interval and no forensic Inspect there. Evidence boundary: Host orchestration; installer file guards and actual ten-second runtime completion need their owning gates and fresh kit.

### Requirement: Source drift rejects emulator admission before device operations

The full emulator cycle SHALL reject an owning agents source snapshot that differs from the constructed closure receipt before installation, removal or UI operations. Editing specifications or verification tasks after construction SHALL require fresh construction rather than reusing the old receipt or weakening its comparison.

#### Scenario: A specification changes after construction

- **WHEN** the owning repository specification changes after its source snapshot is recorded in the closure receipt
- **THEN** the cycle fails admission after package verification without any device operation

# Gate: node --test --test-name-pattern="editing a specification after closure construction rejects the full cycle before device operations" tools/tests/emulator-source-admission.test.ts

Gate contract:
Maps: Requirement `Source drift rejects emulator admission before device operations`; Scenario `A specification changes after construction`.
Preconditions: The named test creates a real isolated Git repository, records its source snapshot, then edits a specification.
Observable result: Production runGate rejects the mismatch and its adapter receives only package verification, with no install, uninstall or UI request.
Evidence boundary: Host orchestration and real source hashing; no Android behavior is claimed.

### Requirement: The OEM launcher dock has closed semantic actuation

The driver SHALL retain the generic prohibition on tapping unclassified windows. Its closed launcher dock operation SHALL use a fresh capture and require exactly one enabled visible OEM RadioButton with the All Apps resource identity, an owned default-display dock root, and a unique owned primary Launcher application window in the same frame. For the explicitly opted-in native launcher pointer path, complete native default-display window references, a visible enabled owned primary root and the actual target from fresh observed membership SHALL supply authority without requiring global accessibility focus on that primary window. Another active or input-focused primary window, missing or ambiguous ownership, or known target occlusion SHALL prevent dispatch. The native request SHALL retain the actual boot, source instance, revision, owning window and target geometry fence; actual focus fields SHALL NOT be fabricated. The dock and application SHALL have consistent capture identity, layer order and geometric containment. After dock or home-swipe dispatch, observed native grid and actual named item identities and order SHALL establish route effect; accepted input alone SHALL NOT establish success. Legacy launcher, Settings and keyboard focus contracts SHALL remain unchanged. Missing, foreign, duplicated or inconsistent authority SHALL prevent dispatch.

#### Scenario: The recorded vendor dock is opened

- **WHEN** the captured primary Launcher includes the actual vendor dock reported with type `-1`
- **THEN** the closed operation opens its observed All Apps control while generic tap still rejects the same unclassified node

#### Scenario: Dock authority is incomplete or inconsistent

- **WHEN** a dock is duplicated, lacks its required primary authority, has a foreign active or input-focused primary window under native pointer admission, changes frame identity or control class, or escapes its owning bounds
- **THEN** the closed selector rejects it

# Gate: node --test --test-name-pattern="generic unknown-window tap remains rejected while the closed dock operation uses a fresh capture" tools/emulator/launcher-dock.test.ts

Gate contract:
Maps: Requirement `The OEM launcher dock has closed semantic actuation`; Scenario `The recorded vendor dock is opened`.
Preconditions: The retained cycle90 dock capture is passed through production InstallerUi and an adapter records every dispatch.
Observable result: Generic tap is rejected; the closed operation obtains a new capture and dispatches one tap at the observed dock center.
Evidence boundary: Host protocol and actuation guard. A separate CLI90 Voyemu observation opened the real grid; full-cycle acceptance remains required.

# Gate: node --test --test-name-pattern="recorded OEM dock is certified against its focused primary launcher in the same capture" tools/emulator/launcher-dock.test.ts

Gate contract:
Maps: Requirement `The OEM launcher dock has closed semantic actuation`; Scenario `Dock authority is incomplete or inconsistent`.
Preconditions: Counterfactuals alter the recorded capture authority while retaining the actual OEM resource identity.
Observable result: Every counterfactual is rejected and the unchanged capture retains its observed dock bounds.
Evidence boundary: Host selector behavior; these counterfactuals are not emulator runtime observations. Native pointer admission does not satisfy the optional deferred producer's separate hasWindowFocus prerequisite or establish its readiness.

### Requirement: Resident inspection parsing isolates the status document

Inspection parsing SHALL read the bounded fixed resident-status document rather than treating every diagnostic line beginning with a brace as JSON. Unrelated policy metadata SHALL NOT substitute for status. Missing, duplicated, truncated or incompatible resident documents SHALL be rejected, and document revision integers SHALL retain their exact source value.

#### Scenario: Other diagnostic files contain formatted JSON

- **WHEN** the real inspection shape contains the resident status followed by unrelated formatted policy JSON
- **THEN** only resident injections are selected, large revisions remain exact, and malformed or duplicated resident status is rejected

# Gate: node --test --test-name-pattern="inspection selects the resident file and ignores other pretty-printed JSON" tools/emulator/inspection-resident.test.ts

Gate contract:
Maps: Requirement `Resident inspection parsing isolates the status document`; Scenario `Other diagnostic files contain formatted JSON`.
Preconditions: The named test supplies the fixed diagnostic section boundaries, unrelated formatted metadata and a revision above the safe floating-point integer range.
Observable result: The exact revision string is preserved, unrelated JSON is ignored and every invalid status variant fails.
Evidence boundary: Host diagnostic parser, not device health.

### Requirement: Runtime visual review is bound to independently observed captures

The runner SHALL accept visual review only through an explicitly supplied session-only Ed25519 public key and a signed assessment of its exact fresh review request. This key SHALL NOT become a production trust root. The request SHALL bind invocation, challenge, immutable input hashes, runtime, serial, ordered action prefix, source artifacts and every reviewed PNG and hierarchy to its action, boot and locale. Every required visual criterion SHALL retain expected and actual observations and a verdict. Missing authority or assessment SHALL remain unavailable; foreign authority, replay, altered bytes and incomplete or duplicate assessments SHALL be rejected. Review SHALL update only its named visual subchecks, preserve every independent runtime failure or unavailable prerequisite, and repeat immutable, runtime and boot checks after asynchronous review before continuing device actions.

#### Scenario: A complete authenticated visual assessment is returned

- **WHEN** the reviewer inspects the actual captures and returns a complete signed assessment for the exact request
- **THEN** only the corresponding visual subchecks may pass and their request and signed receipt remain artifacts

#### Scenario: Review authority or its evidence is missing or inconsistent

- **WHEN** authority or a receipt is absent, or the receipt has foreign authority, replayed identity, altered artifacts or incomplete coverage
- **THEN** absence remains unavailable and inconsistent evidence is rejected without promoting runtime results

#### Scenario: Independent runtime evidence changes during review

- **WHEN** asynchronous review returns after an independent failure or unavailable prerequisite exists
- **THEN** that independent result remains unchanged and overall acceptance remains blocked

# Gate: node --test --test-name-pattern="visual review consumes only signed complete observations for its exact capture request" tools/emulator/visual-review.test.ts

Gate contract: Maps: Requirement `Runtime visual review is bound to independently observed captures`; Scenario `A complete authenticated visual assessment is returned`. Preconditions: Real host PNG/hierarchy files, recorded successful capture actions and an isolated session key are available. Observable result: The actual verifier accepts exact signed criterion observations and retains the bound capture request. Evidence boundary: Host review protocol only; the fixture never claims an actual Android visual inspection.

# Gate: node --test --test-name-pattern="visual review rejects replay tampering incomplete coverage and foreign authority" tools/emulator/visual-review.test.ts

Gate contract: Maps: Requirement `Runtime visual review is bound to independently observed captures`; Scenario `Review authority or its evidence is missing or inconsistent`. Preconditions: A valid signed host assessment is independently changed in identity, capture bytes, criteria and authority. Observable result: Every inconsistent variant fails, while absent authority or receipt remains unavailable. Evidence boundary: Host authentication and artifact integrity only.

# Gate: node --test --test-name-pattern="asynchronous visual assessment cannot promote independent runtime evidence" tools/emulator/visual-review.test.ts

Gate contract: Maps: Requirement `Runtime visual review is bound to independently observed captures`; Scenario `Independent runtime evidence changes during review`. Preconditions: The actual asynchronous review consumer receives a valid signed assessment while independent case evidence contains a failure or unavailable prerequisite. Observable result: Only its visual subcheck changes and independent evidence remains blocking. Evidence boundary: Host aggregation and asynchronous review only; actual immutable/runtime/boot readbacks require the full emulator run.

### Requirement: Action coverage retains the actual final uninstall

Action-button coverage SHALL require every actual source-bound panel button and dialog cancel in all three locales, plus the genuine final-language confirm and normal uninstall receipt in case 17. A pre-uninstall visual assessment SHALL NOT satisfy final confirm coverage. Error and retry status branches SHALL retain their separate runtime requirements.

#### Scenario: Final confirm has not completed

- **WHEN** panel buttons and dialog cancel have been observed but case 17 lacks its successful normal uninstall
- **THEN** all-action coverage remains unavailable

#### Scenario: Final confirm completes normal removal

- **WHEN** case 17 completes its observed confirm, strict normal uninstall and independent boot verification
- **THEN** those actual actions may complete action coverage without repeating uninstall solely for each locale

# Gate: node --test --test-name-pattern="action coverage requires three locale cancel captures and the genuine final removal" tools/emulator/visual-review.test.ts

Gate contract: Maps: Requirement `Action coverage retains the actual final uninstall`; Scenarios `Final confirm has not completed`, `Final confirm completes normal removal`. Preconditions: Source topology and host case/action receipts are supplied to the production coverage consumer. Observable result: Missing, failed or incomplete final removal remains unavailable; complete three-locale cancel evidence and genuine final confirm/removal complete only action coverage. Evidence boundary: Host aggregation only; real normal removal remains independently required in case 17.

### Requirement: Functional fault observations retain their bounded owning window

The runner SHALL request only the packaged installer's typed startup mutation lock, fixed vehicle-state reply loss or pre-durable uninstall transport loss window. The request and streamed armed/closed receipts SHALL bind the current boot, exact manifest, resident generation, catalog and target PID/start time. The runner SHALL keep stdin open only while its ordinary UI observations require the window, close it in every completion or failure branch, require matching cleanup and reject expiry, foreign identity, extra fields or unresolved children. Armed receipts SHALL NOT prove any effect. Runtime outcomes SHALL additionally require fresh canonical root fault-effect records for that token and real matching agent retry/cancel or actual source-bound UI failure observations. Missing production observations SHALL remain unavailable and SHALL NOT be promoted by host fixtures.

#### Scenario: A typed window surrounds an ordinary observation

- **WHEN** the same packaged CLI arms its exact bounded window and the ordinary UI observation completes or fails
- **THEN** stdin is closed, matching cleanup is retained and the observation's original failure remains explicit

#### Scenario: Window identity or effect evidence differs

- **WHEN** authority, token, expiry, fields, target lifetime or canonical root effect attribution differs
- **THEN** the receipt or effect is rejected before any runtime result may pass

# Gate: node --test --test-name-pattern="fault window closes the real host child on observation completion and failure" tools/emulator/fault-window.test.ts

Gate contract: Maps: Requirement `Functional fault observations retain their bounded owning window`; Scenario `A typed window surrounds an ordinary observation`. Preconditions: A bounded executable host child emits typed armed and EOF-cleanup receipts through the real spawn adapter. Observable result: The actual consumer runs the observation only after arming, closes stdin on success and failure and retains the original failure. Evidence boundary: Host child lifecycle only; no Android fault or readiness is manufactured.

# Gate: node --test --test-name-pattern="fault window rejects foreign expiry replay and canonical effect substitution" tools/emulator/fault-window.test.ts

Gate contract: Maps: Requirement `Functional fault observations retain their bounded owning window`; Scenario `Window identity or effect evidence differs`. Preconditions: Exact producer-contract host receipts and canonical effect records are available with independent hostile mutations. Observable result: Foreign identities, unknown fields, expired or mismatched cleanup and substituted root effects fail admission. Evidence boundary: Host receipt and effect parser only; actual UI errors and retry/cancel require the full immutable emulator run.

### Requirement: Stock icon provenance covers the complete actual OEM inventories

The stock-icon consumer SHALL require both actual launcher screen inventories, every unregistered stock token and its original resource identity, and an actual completed original bind for every inventory item. Fresh observations SHALL bind the current boot, registered target PID/start time, resident code generation and catalog through canonical root queued-snapshot evidence. Before and original-boundary bean and target values SHALL remain equal. After the actual OEM original, the stock bean tag and requested resource SHALL bind the actual theme-returned native Drawable to the holder background without a managed wrapper. Missing inventory items, theme returns, original completions or target authority SHALL remain unavailable. Retained labels, PNG review, source fixtures and a positive sampled subset SHALL NOT prove complete stock ownership.

#### Scenario: Every original stock bind is independently observed

- **WHEN** both complete actual stock inventories and every corresponding original resource-to-Drawable-to-View bind have fresh registered authority
- **THEN** only stock bind provenance may pass while PNG appearance and all other independent observations retain their own results

#### Scenario: A stock bind or its authority is partial or substituted

- **WHEN** an inventory item, screen, original boundary, theme return, bean tag, source resource or registered identity is missing or differs
- **THEN** complete stock ownership cannot pass

# Gate: node --test --test-name-pattern="stock bind provenance requires complete actual inventories and native resource bindings" tools/emulator/launcher-stock.test.ts

Gate contract: Maps: Requirement `Stock icon provenance covers the complete actual OEM inventories`; Scenarios `Every original stock bind is independently observed`, `A stock bind or its authority is partial or substituted`. Preconditions: Exact producer-contract host records supply two inventories, all original boundaries, native theme returns and independent root registration, plus hostile substitutions. Observable result: Only exact full inventory/resource/background coverage admits provenance; partial screens or items and substituted authority fail. Evidence boundary: Host provenance admission only; actual OEM callbacks, stock appearance and full emulator readiness remain independent runtime gates.

### Requirement: Emulator gestures retain live foreground authority

The product gate SHALL make Settings opening idempotent only after observing the actual focused active Settings application window and its selected owned Voboost tab. A confirmed locale mutation SHALL retain a complete translated panel traversal without an additional open or tab tap. Foreground drift during panel capture SHALL fail with a diagnostic capture and SHALL NOT automatically restore Settings. A separate case-20 diagnostic SHALL repeat selected-tab gestures with before and after captures. Every coordinate gesture SHALL carry the actual focused primary window and exact target window, package, bounds, resource and text for fresh validation inside the installer helper immediately before input.

#### Scenario: An already selected owned Settings tab is observed

- **WHEN** an actual snapshot identifies the unique focused active Settings window and selected Voboost tab
- **THEN** opening does not send another open-settings request or selected-tab tap

#### Scenario: Foreground changes to Music

- **WHEN** a panel traversal or repeated selected-tab gesture observes Music instead of Settings
- **THEN** the gate records a foreground failure capture and stops without recovery or acceptance

# Unverified: Cycle107 live gesture authority

### Requirement: Intermediate install acceptance consumes complete independent boot proof

The emulator gate SHALL explicitly prearm acceptance before install reboot one and bind the typed armed receipt to the frozen manifest, transaction and attempt token. It SHALL send acceptance only after the existing first-boot check passes complete prepared verification, Enforcing, boot identity fences and fresh UI captures. It SHALL require a correlated waiting status and accepted consumption receipt on the same boot and unchanged deadline. A failed or unavailable boot proof SHALL never authorize acceptance. Later reboot checks and the every-boot aggregate SHALL remain mandatory.

#### Scenario: Complete first-boot proof precedes consumption

- **WHEN** the prearmed install reaches its first completed boot
- **THEN** full independent boot verification and UI capture finish before exact status and acceptance requests while the install child remains active

# Gate: node --test --test-name-pattern="intermediate boot verification and captures precede exact acceptance consumption" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Intermediate install acceptance consumes complete independent boot proof`; Scenario `Complete first-boot proof precedes consumption`. Preconditions: Host lifecycle adapter supplies the closed prearmed identity and waits for acceptance. Observable result: Every first-boot proof action finishes before status and acceptance, whose boot equals the verified boot, and the install child remains active. Evidence boundary: Host protocol ordering only; native socket, emulator verification and physical runtime remain unverified.

# Unverified: Cycle107 live install acceptance


### Requirement: Boot readiness and UI capture have independent finite budgets

The gate SHALL cap readiness to the existing 600-second official Voyemu verification contract, including ownership and boot fences. Only a complete prepared verification and unchanged completed boot SHALL start the separate 180-second UI capture budget. Every invocation SHALL be capped to its phase remaining time. A timed-out verify SHALL remain unavailable before any subsequent observation can mask its cause. The native prearmed first-boot acceptance lifetime SHALL be 900 seconds: 600 for readiness, 180 for capture and 120 for discovery and correlated decision overhead. This is an upper bound, never a pause. The native actor SHALL retain its original guest-monotonic absolute expiry across restart and status, reject decisions at or beyond expiry, and never rearm or extend the lifetime. The host SHALL require positive native expiry and exact unchanged expiry in correlated waiting and accepted receipts; host elapsed time SHALL never substitute for guest monotonic time.

#### Scenario: Slow complete verification still permits fresh capture

- **WHEN** exact prepared verification takes 130 seconds and its same-boot post-fence passes
- **THEN** fresh capture receives its own bounded phase and acceptance still requires all existing native correlation and expiry checks

#### Scenario: Verification timeout cannot become capture proof

- **WHEN** the readiness verify process times out
- **THEN** no post-verification observation or screenshot runs and the timeout remains an unavailable readiness failure

#### Scenario: Two slow captures remain bounded

- **WHEN** the first capture takes 75.997 seconds and returns the structured warmup deadline
- **THEN** one second capture of the same duration fits the independent 180-second phase while retaining the same boot fences

#### Scenario: Slow retry rejects a foreign boot

- **WHEN** the boot changes before the second slow capture
- **THEN** acceptance fails before a second screenshot

#### Scenario: Exhausted capture cannot retry

- **WHEN** the first warmup failure exhausts the capture phase
- **THEN** the failure is retained and no retry runs

# Gate: node --test --test-name-pattern="130 second real readiness leaves a fresh independent UI budget" tools/tests/boot-phase-budget.test.ts

Gate contract: Maps: Requirement `Boot readiness and UI capture have independent finite budgets`; Scenario `Slow complete verification still permits fresh capture`. Preconditions: Fake monotonic clock executes the actual production boot-check source. Observable result: Verification lasting 130 seconds precedes a separate finite capture phase on the same boot. Evidence boundary: Host control flow only; packaged native expiry and real emulator timing remain unverified.

# Gate: node --test --test-name-pattern="readiness timeout is retained without a masking post verify observation or screenshot" tools/tests/boot-phase-budget.test.ts

Gate contract: Maps: Requirement `Boot readiness and UI capture have independent finite budgets`; Scenario `Verification timeout cannot become capture proof`. Preconditions: Fake monotonic clock executes the actual production boot-check source with a timed-out verification. Observable result: Readiness timeout is retained without a subsequent probe or capture masking it. Evidence boundary: Host timeout control flow only; runtime timing remains unverified.

# Gate: node --test --test-name-pattern="two observed slow first boot captures fit the independent phase" tools/tests/boot-phase-budget.test.ts

Gate contract: Maps: Requirement `Boot readiness and UI capture have independent finite budgets`; Scenario `Two slow captures remain bounded`. Preconditions: Fake monotonic clock executes actual production bootCheck and captureFirstBoot with observed slow attempt durations. Observable result: Same-boot captures fit only the finite phase while foreign boot and exhaustion reject acceptance. Evidence boundary: Host control flow only; packaged emulator runtime remains unverified.

# Gate: node --test --test-name-pattern="foreign boot on slow capture retry remains rejected" tools/tests/boot-phase-budget.test.ts

Gate contract: Maps: Requirement `Boot readiness and UI capture have independent finite budgets`; Scenario `Slow retry rejects a foreign boot`. Preconditions: Fake monotonic clock executes actual production bootCheck and captureFirstBoot with observed slow attempt durations. Observable result: Same-boot captures fit only the finite phase while foreign boot and exhaustion reject acceptance. Evidence boundary: Host control flow only; packaged emulator runtime remains unverified.

# Gate: node --test --test-name-pattern="exhausted phase never permits the warm retry" tools/tests/boot-phase-budget.test.ts

Gate contract: Maps: Requirement `Boot readiness and UI capture have independent finite budgets`; Scenario `Exhausted capture cannot retry`. Preconditions: Fake monotonic clock executes actual production bootCheck and captureFirstBoot with observed slow attempt durations. Observable result: Same-boot captures fit only the finite phase while foreign boot and exhaustion reject acceptance. Evidence boundary: Host control flow only; packaged emulator runtime remains unverified.

# Unverified: Independent boot budget runtime acceptance

### Requirement: First-boot UI warming retries preserve independent acceptance proof

The emulator gate SHALL allow at most one immediate read-only first-install-boot recapture after a structured empty-XML Accessibility metadata or nodes deadline. It SHALL retain failed actions and attempt timings, independently recheck unchanged lease, exact boot, completion and Enforcing before each attempt and after complete fresh PNG and hierarchy capture, and retain exact prepared verification as a prerequisite. It SHALL bound capture operations within 180 seconds beginning only after complete prepared verification and the same-boot post-verification fence and retry only with 45 seconds remaining. It SHALL never navigate or relaunch for recovery. The exact two standard CLI USB connection INFO lines with canonical timestamps and the selected serial SHALL not be treated as XML payload. Extra, duplicate, malformed or foreign-serial output SHALL remain ineligible for retry. Unsupported failures, changed boot or owner, and exhausted attempts SHALL reject acceptance.

#### Scenario: Transient deadline completes on the same boot

- **WHEN** the first attempt reaches a known metadata deadline and a bounded second attempt obtains complete PNG and hierarchy on the same verified boot
- **THEN** acceptance follows fresh successful proof while failed attempt artifacts remain retained

#### Scenario: Retry cannot establish first-boot proof

- **WHEN** deadlines persist, the boot or owner changes, the budget expires or a failure is unsupported
- **THEN** the gate refuses acceptance

# Gate: node --test --test-name-pattern="first-boot UI warming" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `First-boot UI warming retries preserve independent acceptance proof`; Scenarios `Transient deadline completes on the same boot` and `Retry cannot establish first-boot proof`. Preconditions: Host lifecycle adapter supplies prepared verification and exact acceptance identities. Observable result: One deadline permits fresh same-boot PNG/tree capture; persistent deadlines, changed boot, foreign ownership, unsupported errors and exhausted retry budget never accept. Evidence boundary: Host control flow only; actual Accessibility readiness and native expiry remain unverified.

# Gate: node --test --test-name-pattern="first boot deadline retries only the exact selected USB connection log envelope" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `First-boot UI warming retries preserve independent acceptance proof`; Scenarios `Transient deadline completes on the same boot` and `Retry cannot establish first-boot proof`. Preconditions: Production classifier receives the exact two standard CLI USB connection INFO lines with a structured empty-XML traversal deadline and selected device serial. Observable result: One guarded retry is admitted only for the selected serial; extra, malformed, foreign or mutating output and process, transport or permission failures remain rejected. Evidence boundary: Host classification and retry control flow only; actual warmed first-boot capture remains unverified.

# Unverified: First-boot UI warming runtime


### Requirement: Intermediate installer monitor lifecycle

The intermediate boot monitor SHALL share the lifetime of its installer command. Intermediate `installer-*` boot checks SHALL remain read-only regardless of whether the child has exited, and SHALL never navigate Home or Settings. Terminal child failure SHALL stop new acceptance work and the dispatcher SHALL join its in-flight monitor before returning or finalizing diagnostics. The original installer failure SHALL remain the reported installation cause; a failed command SHALL never authorize acceptance or another reboot.

#### Scenario: Installer fails while boot verification is in flight

- **WHEN** the installer exits unsuccessfully while the intermediate boot monitor is verifying the same boot
- **THEN** no Home/Settings navigation or acceptance is dispatched by that monitor
- **AND** the monitor is joined before the dispatcher returns
- **AND** the native installer error remains the case failure

# Gate: node --test --test-name-pattern="dispatcher joins failed installer monitor" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Intermediate installer monitor lifecycle`; Scenario `Installer fails while boot verification is in flight`. Preconditions: Host public dispatcher adapter completes the child with native failure during intermediate verification. Observable result: No navigation, acceptance or second reboot, monitor joined before return, original native error retained. Evidence boundary: Host lifecycle regression only; successful emulator installation remains unverified.


### Requirement: Intermediate acceptance preserves the CLI and native wire boundaries

The gate SHALL send only the install-acceptance action and its six declared CLI fields. Native schemaVersion SHALL remain in armed identities and correlated receipts; the installer SHALL add it when producing the native frame. Unknown CLI fields SHALL remain rejected.

#### Scenario: Native schema does not leak into the CLI action

- **WHEN** the gate sends status, accept or reject for a verified intermediate boot
- **THEN** its explicit CLI action omits schemaVersion while the correlated native receipt retains schemaVersion 1

# Gate: node --test --test-name-pattern="install acceptance CLI action matches actual serde fixture without leaking native schema" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Intermediate acceptance preserves the CLI and native wire boundaries`; Scenario `Native schema does not leak into the CLI action`. Preconditions: The production action builder and retained CLI fixture are available. Observable result: All three decisions use exactly the closed CLI fields without leaking native schema; the installer has a matching local fixture consumed by its actual Rust Request deserializer and rejects the leaked field. Evidence boundary: Host serialization and actual serde boundary only; runtime acceptance requires the next packaged emulator cycle.

### Requirement: Intermediate boot waits for the required second screen

The gate SHALL retry only a structured official verify exit6 with the exact 220 prepared checks, whose only failures are the three absent required second-screen routing windows, while the second launcher still has its display1 splash window. It SHALL retain every failed snapshot, perform no recovery navigation, preserve the same lease, bootId, Enforcing and installer reboot count before and after each attempt, and remain within the finite 600-second readiness budget; successful verification and the same-boot fence SHALL begin a separate 180-second capture budget with 45 seconds reserved for a capture retry. Wrong displays, other failed checks, malformed or partial inventories and exhausted readiness SHALL fail. Acceptance SHALL require a subsequent full 220-check pass and fresh PNG and hierarchy proof on that same boot.

#### Scenario: Second launcher finishes starting

- **WHEN** the exact three windows are absent at first and the same boot later passes the complete prepared inventory
- **THEN** the gate retains both attempts and captures fresh UI before acceptance

#### Scenario: Second screen cannot establish readiness

- **WHEN** required windows remain absent, a window is on the wrong display, another check fails or the boot changes
- **THEN** the gate refuses acceptance

# Gate: node --test --test-name-pattern="second-screen" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Intermediate boot waits for the required second screen`; Scenarios `Second launcher finishes starting` and `Second screen cannot establish readiness`. Preconditions: Reduced actual-shaped cycle112 action140/action151 snapshots and the public dispatcher host adapter. Observable result: A transient exact missing-window result requires a later full pass and capture; persistent absence, wrong display and changed boot never accept. Evidence boundary: Host lifecycle only; fresh immutable emulator retry behavior remains unverified.


# Unverified: Second-screen readiness runtime

### Requirement: Cache recovery ACK follows complete boot proof

The gate SHALL request the CLI recovery hold and accept only after the existing full prepared-inventory verification, Enforcing and fresh UI evidence, repeated ownership fence and current completed boot match the held identity. Partial verification, stale lease and foreign or incomplete boot SHALL produce reject. Intermediate and pristine boots SHALL remain separate ledger entries.

#### Scenario: Complete proof precedes acceptance

- **WHEN** a waiting event is valid
- **THEN** ownership, full boot proof, ownership and current boot checks finish in order before accept

#### Scenario: Proof becomes stale

- **WHEN** lease, verification or current boot fails
- **THEN** the consumer emits reject

# Gate: node --test --test-name-pattern="recovery ACK waits for complete verify and repeated lease/boot fences" tools/tests/cache-recovery-acceptance.test.ts

Gate contract: Maps: Requirement `Cache recovery ACK follows complete boot proof`; Scenarios `Complete proof precedes acceptance` and `Proof becomes stale`. Preconditions: The production recoveryBootAck consumer receives a validated waiting event and controlled ownership, verify and current-boot callbacks. Observable result: Acceptance follows full verify and repeated ownership/current-boot checks; stale lease, partial verify, foreign boot and incomplete boot produce reject. Evidence boundary: Host consumer ordering only; real emulator intermediate-boot acceptance remains unverified.

### Unverified: Real cache recovery intermediate boot

Maps: Requirement `Cache recovery ACK follows complete boot proof`. Unavailable observation: Real immutable CASE26, all220 checks and UI capture before ACK, retained authority on rejection and separate final pristine boot. Reason: No runtime work in this isolated stage. Closure: Authorized immutable emulator cycle and visual review of actual PNGs.


### Requirement: Persistent vehicle diagnostics preserve closed authority and freshness

Functional observations SHALL consume the daemon's closed persistent vehicle projection only with independent current boot and resident generation/catalog authority. The consumer SHALL preserve actual true and false, missing and unavailable without using cached agent details as persistence evidence. It SHALL reject stale observations older than 5000 boot milliseconds, future observations, foreign boot/generation/catalog, malformed boolean triads and unknown fields. Closed status consumers, including post-install readiness, SHALL admit only the typed optional `persistentVehicleState`, `lastUnsafeNativeFailure`, and `startupMilestones` additions without admitting arbitrary fields.

#### Scenario: Private shell access is unavailable

- **WHEN** the private fixed-key probes are unavailable but a current trusted daemon snapshot contains true and false
- **THEN** the production consumer retains the exact persistent readbacks from that projection

#### Scenario: Snapshot authority or triads differ

- **WHEN** snapshot boot, generation, catalog, freshness or triad shape differs
- **THEN** the consumer rejects the projection and does not infer false from absence or unavailability

#### Scenario: Typed diagnostics coexist with post-install readiness

- **WHEN** canonical resident status includes either typed optional diagnostic
- **THEN** the closed post-install validator accepts its shape while rejecting unknown root or nested diagnostic fields

# Gate: node --test --test-name-pattern="secure persistent projection survives unavailable private shell probes" tools/emulator/persistent-vehicle-state.test.ts

Gate contract: Maps: Requirement `Persistent vehicle diagnostics preserve closed authority and freshness`; Scenario `Private shell access is unavailable`. Preconditions: Independent resident identity and current closed projection, with unavailable private lines. Observable result: The production consumer returns present true and false without consulting agent details. Evidence boundary: Host consumer branch; Android root-read origin is covered separately by the composed native gate.

# Gate: node --test --test-name-pattern="persistent projection rejects stale, future, foreign boot and generation" tools/emulator/persistent-vehicle-state.test.ts

Gate contract: Maps: Requirement `Persistent vehicle diagnostics preserve closed authority and freshness`; Scenario `Snapshot authority or triads differ`. Preconditions: Host fixtures vary boot, resident generation/catalog, negative and over-5000-ms age, and malformed or nonvalue triads. Observable result: Each invalid projection is rejected and valid missing/unavailable remain distinct. Evidence boundary: Host acceptance decisions; the 5000-ms runtime scheduling envelope is not yet demonstrated.

# Gate: node --test --test-name-pattern="persistent projection preserves triads and rejects corrupt boolean shapes" tools/emulator/persistent-vehicle-state.test.ts

Gate contract: Maps: Requirement `Persistent vehicle diagnostics preserve closed authority and freshness`; Scenario `Snapshot authority or triads differ`. Preconditions: Missing, unavailable, nonboolean values and malformed triad fields enter the production consumer. Observable result: Exact nonvalue outcomes survive and every malformed triad is rejected. Evidence boundary: Host parser and consumer, not Android persistence.

# Gate: node --test --test-name-pattern="post-install dispatcher admits typed persistent and native diagnostics while rejecting unknown or malformed additions" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Persistent vehicle diagnostics preserve closed authority and freshness`; Scenario `Typed diagnostics coexist with post-install readiness`. Preconditions: The production dispatcher receives actual mocked CLI/readiness responses carrying both typed optional diagnostics, then unknown and malformed additions. Observable result: Typed diagnostics reach healthy readiness and hostile additions fail the same dispatcher. Evidence boundary: Host orchestration with mocked external child receipts, not a running guest.

# Gate: node --test --test-name-pattern="post-install closed status admits typed optional diagnostics and rejects unknown fields" tools/emulator/persistent-vehicle-state.test.ts

Gate contract: Maps: Requirement `Persistent vehicle diagnostics preserve closed authority and freshness`; Scenario `Typed diagnostics coexist with post-install readiness`. Preconditions: Canonical-shaped status includes both typed optional diagnostics and hostile unknown fields. Observable result: The same closed validator used by post-install readiness accepts only typed additions and rejects unknown root and nested fields. Evidence boundary: Host validator; a complete post-install emulator run remains independent evidence.

# Unverified: Persistent diagnostic runtime acceptance window


### Requirement: Launcher observations preserve both display and complete visible-page evidence

Case12 SHALL retain independently targeted primary and secondary PNG captures and the secondary activity window receipt; a default-display Accessibility tree SHALL NOT be described as a secondary hierarchy. Its order comparison SHALL compare every observed visible-page label and reject missing, added, duplicate or reordered suffix entries even when the fixture remains first. Its disk-reuse comparison SHALL require current-lifetime canonical ready-before-first-draw records for both screens, exact retained cache/source/style identities and a distinct boot with unchanged signed catalog. A cross-boot comparison SHALL retain and revalidate an independent native-bracketed `beforeWitness`, with exact retained pairs, distinct boot, unchanged signed catalog and exact baseline cursor; it SHALL NOT require the old cursor to appear in rotated current-boot logs. Recursive witnesses, replaced or tampered pairs, missing native lifetime brackets and a wrong cursor SHALL fail admission. Later successful disk binds SHALL NOT erase an initial rendered draw. These observations SHALL NOT substitute for visual icon review or prove unobserved pages.

Case12 visual acceptance SHALL use actual Cloud Space/HiCar references in the paired captures. Classic left metal icons SHALL match the native frame at its original size and shape, including rim and upper illumination, without scaling the frame. Speedy Dash full-color icons on both screens and Classic right full-color icons SHALL match the common native Cloud Space/HiCar visible size and corner radius. Transparent padding and near-transparent halos SHALL NOT enlarge the reference. Music SHALL NOT determine either geometry. Missing actual references or the third-party fixture SHALL fail visual assessment. Matching actual selected-theme snapshots SHALL bracket both captures; source assets, cache receipts and historical measurements SHALL NOT replace this visual proof.

#### Scenario: Closed display selection is serialized

- **WHEN** the driver serializes secondary navigation and a targeted screenshot
- **THEN** only fixed typed display selections and zero-argument navigation are admitted

#### Scenario: The first item survives but another visible slot changes

- **WHEN** a later observation keeps the fixture first but changes the visible suffix
- **THEN** the order comparison rejects reordered, missing, added or duplicated items

#### Scenario: A later disk bind follows an initial rendered draw

- **WHEN** a current-lifetime observation first renders an icon and later loads it from disk
- **THEN** disk-reuse admission rejects rather than hiding the initial draw

# Gate: node --test --test-name-pattern="secondary launcher requests are closed and screenshots use a fixed display enum" tools/emulator/installer-ui.test.ts

Gate contract: Maps: Requirement `Launcher observations preserve both display and complete visible-page evidence`; Scenario `Closed display selection is serialized`. Preconditions: Production request validator receives typed display and hostile caller fixtures. Observable result: Fixed primary and secondary screenshot selections pass; arbitrary displays and caller component fields fail. Evidence boundary: Host serialization only; actual paired PNGs and window authority require the packaged emulator run.

# Gate: node --test --test-name-pattern="retaining Voboost Fixture first cannot hide reordered, lost, added or duplicated suffix" tools/emulator/launcher-order.test.ts

Gate contract: Maps: Requirement `Launcher observations preserve both display and complete visible-page evidence`; Scenario `The first item survives but another visible slot changes`. Preconditions: Production comparison receives synthetic complete visible-page labels. Observable result: Every altered suffix fails despite unchanged first position. Evidence boundary: Host comparison only; no unseen page or actual reboot is claimed.

# Gate: node --test --test-name-pattern="an initial rendered first draw cannot be hidden by a later successful disk bind" tools/emulator/launcher-cache.test.ts

Gate contract: Maps: Requirement `Launcher observations preserve both display and complete visible-page evidence`; Scenario `A later disk bind follows an initial rendered draw`. Preconditions: Production canonical record consumer receives signed-authority-shaped host fixtures containing initial render and later disk records. Observable result: Later disk success cannot admit the initial rendered draw as disk reuse. Evidence boundary: Host evidence admission only; actual disk readiness and guest first draw require fresh runtime records.

# Gate: node --test --test-name-pattern="current lifetime ready and first draw prove both retained disk keys across boot" tools/emulator/launcher-cache.test.ts

Gate contract: Maps: Requirement `Launcher observations preserve both display and complete visible-page evidence`; Scenario `A later disk bind follows an initial rendered draw`. Preconditions: Production admission receives canonical two-screen ready/draw pairs and retained cache identities on distinct boots. Observable result: The valid disk fixture passes, independently of the initial-render rejection gate. Evidence boundary: Host admission positive branch; actual disk bytes and rendered pixels remain runtime observations.

# Gate: node --test --test-name-pattern="same boot and signed catalog changes cannot prove reboot reuse" tools/emulator/launcher-cache.test.ts

Gate contract: Maps: Requirement `Launcher observations preserve both display and complete visible-page evidence`; Scenario `A later disk bind follows an initial rendered draw`. Preconditions: Valid host record pairs have their boot or signed catalog authority changed. Observable result: Same-boot and catalog-changing evidence cannot pass reboot reuse. Evidence boundary: Host authority rejection, not real reboot evidence.

# Gate: node --test --test-name-pattern="cross boot retained authenticated witness admits rotation without copying old cursor into current logs" tools/emulator/launcher-cache.test.ts

Gate contract: Maps: Requirement `Launcher observations preserve both display and complete visible-page evidence`; Scenario `A later disk bind follows an initial rendered draw`. Preconditions: Source-owned canonical cache fixtures include independent native lifetime brackets across distinct boots. Observable result: Independently bracketed before witness admits current log rotation without importing the old cursor. Evidence boundary: Host evidence admission only; actual cache reuse and same-boot capture require fresh packaged runtime evidence.

# Gate: node --test --test-name-pattern="cross boot witness rejects replacement, tampering, missing native bracket and wrong cursor" tools/emulator/launcher-cache.test.ts

Gate contract: Maps: Requirement `Launcher observations preserve both display and complete visible-page evidence`; Scenario `A later disk bind follows an initial rendered draw`. Preconditions: Source-owned canonical cache fixtures include independent native lifetime brackets across distinct boots. Observable result: Changed witness pairs, missing native bracket and wrong cursor fail. Evidence boundary: Host evidence admission only; actual cache reuse and same-boot capture require fresh packaged runtime evidence.

### Requirement: Agent disable acceptance binds source policy and independent process evidence

Case8 SHALL select exactly the six signed checkbox agents: font-cyrillic, keyboard-ru, keyboard-en, launcher-apps, vehicle-drive-forced-ev and vehicle-drive-pedestrian-warning. Settings config and executor SHALL remain outside that selection. Stop policy SHALL be admitted only from exact owning entry classes and Agent, AgentAsync and AgentCacheQuiescence source snapshots bound through snapshotRepository to the installed catalog source-build receipt. Default RESTART_PROCESS SHALL apply except the two vehicle IN_PLACE overrides; this check SHALL NOT create a production capability.

Before and after each disable, independent full OEM process inventory SHALL bind target observations to the same boot. An active restart target SHALL require a different replacement PID and no disabled-agent row; remaining enabled agents in that process and re-enabled live targets SHALL regain active readiness. Exact complete absence plus a waiting row SHALL record not applicable because no injection occurred, without launching dormant targets. Partial inventory, live inactive targets and an unreturned replacement SHALL remain unavailable. Keyboard conflict SHALL also check the displaced keyboard restart and selected keyboard activity.

Case25 SHALL retain `runtime-disable-cancellation-fev` and `runtime-disable-cancellation-pw`: preserve OEM state, disable without PID change, observe actual same-boot sleep/wake OEM reset to the stock value without disabled-agent restoration, then re-enable and restore the saved choice. Host tests and historical supplemental results SHALL NOT satisfy these runtime subchecks.

#### Scenario: A checkbox agent is disabled and re-enabled

- **WHEN** complete observations accompany a source-admitted checkbox policy
- **THEN** restart requires independent PID change, while complete absent waiting targets are explicitly not applicable and partial absence remains unavailable


# Gate: node --test --test-name-pattern="restart requires independent PID change" tools/emulator/agent-disable.test.ts

Gate contract: Maps: Requirement `Agent disable acceptance binds source policy and independent process evidence`; Scenario `A checkbox agent is disabled and re-enabled`. Preconditions: Production host validators and controlled fixtures are available. Observable result: Independent PID change admits restart. Evidence boundary: Host contract verification only; packaged emulator execution, rendered pixels, actual process restart and vehicle behavior require fresh runtime evidence.

# Gate: node --test --test-name-pattern="complete absent+waiting means no injection to undo" tools/emulator/agent-disable.test.ts

Gate contract: Maps: Requirement `Agent disable acceptance binds source policy and independent process evidence`; Scenario `A checkbox agent is disabled and re-enabled`. Preconditions: Production host validators and controlled fixtures are available. Observable result: Complete absence and waiting produce explicit not-applicable outcome. Evidence boundary: Host contract verification only; packaged emulator execution, rendered pixels, actual process restart and vehicle behavior require fresh runtime evidence.

# Gate: node --test --test-name-pattern="partial absence remains unavailable" tools/emulator/agent-disable.test.ts

Gate contract: Maps: Requirement `Agent disable acceptance binds source policy and independent process evidence`; Scenario `A checkbox agent is disabled and re-enabled`. Preconditions: Production host validators and controlled fixtures are available. Observable result: Partial inventory cannot prove absence. Evidence boundary: Host contract verification only; packaged emulator execution, rendered pixels, actual process restart and vehicle behavior require fresh runtime evidence.

# Gate: node --test --test-name-pattern="in-place verifies retained PID but explicitly requires feature proof" tools/emulator/agent-disable.test.ts

Gate contract: Maps: Requirement `Agent disable acceptance binds source policy and independent process evidence`; Scenario `A checkbox agent is disabled and re-enabled`. Preconditions: Production host validators and controlled fixtures are available. Observable result: Retained PID preserves the separate cancellation requirement. Evidence boundary: Host contract verification only; packaged emulator execution, rendered pixels, actual process restart and vehicle behavior require fresh runtime evidence.

# Unverified: Fresh disable and cancellation runtime acceptance

### Requirement: Product secondary capture retains the packaged installer boundary

In legacy mode, the product runner SHALL send secondary-only typed official Voyemu capture context through the packaged installer CLI. Native-session mode SHALL use the installed native capture API directly and retain its fenced three-frame bundle under the native verification contract. Primary capture SHALL omit that context. Host helper hashes SHALL retain input integrity only; explicit operator approval of the official source helper remains a prerequisite. API28 default-display Accessibility hierarchy SHALL NOT become secondary hierarchy evidence. Fresh same-boot native window proof, paired PNG capture and visual review SHALL remain independent required runtime observations.

#### Scenario: Typed official capture context reaches the packaged screenshot action

- **WHEN** the executor captures secondary and primary through the packaged CLI adapter
- **THEN** only the secondary request retains the closed context and primary omits it


# Gate: node --test --test-name-pattern="official Voyemu screenshot context is secondary-only and closed" tools/emulator/voyemu-capture.test.ts

Gate contract: Maps: Requirement `Product secondary capture retains the packaged installer boundary`; Scenario `Typed official capture context reaches the packaged screenshot action`. Preconditions: Production host validators and controlled fixtures are available. Observable result: Secondary context passes and caller extras or primary context fail. Evidence boundary: Host contract verification only; packaged emulator execution, rendered pixels, actual process restart and vehicle behavior require fresh runtime evidence.

# Gate: node --test --test-name-pattern="actual screenshot executor delegates through packaged CLI and retains typed context" tools/emulator/voyemu-capture.test.ts

Gate contract: Maps: Requirement `Product secondary capture retains the packaged installer boundary`; Scenario `Typed official capture context reaches the packaged screenshot action`. Preconditions: Production host validators and controlled fixtures are available. Observable result: Actual executor serializes secondary context and omits it for primary. Evidence boundary: Host contract verification only; packaged emulator execution, rendered pixels, actual process restart and vehicle behavior require fresh runtime evidence.

# Unverified: Packaged authenticated secondary capture acceptance

### Requirement: Vehicle timing acceptance binds actual reads to current lifetime

Selected case13 and case14 cold and warm restoration checks SHALL consume actual vehicle semantic reads and owner completion only after the current-boot root vehicle receipt for the exact PID, starttime, endpoint, generation and catalog. Warm checks SHALL exclude records at or before the baseline sequence cutoff. Missing, negative, foreign-domain or regressing clock samples SHALL be unavailable timing evidence. Timing SHALL describe boot-to-matched-observation, without a guessed SLA, mutation causality, assumed guest `CLK_TCK` or process-start conversion. Settings ordering requires a separately boot-fenced same-clock observation.

#### Scenario: A Dreamer restoration has only one matching semantic

- **WHEN** baseline or incomplete Dreamer semantic evidence is supplied
- **THEN** baseline and restored observations remain distinct and Dreamer requires both semantics

#### Scenario: Cold restore reports actual boot-relative observation

- **WHEN** current-lifetime actual read and owner completion records satisfy the cold restore consumer
- **THEN** output describes boot-to-matched-observation without process latency or mutation causality

#### Scenario: Warm restore excludes preceding completion

- **WHEN** only baseline state or completion at or before the warm baseline sequence cutoff is present
- **THEN** the consumer does not accept that evidence as the selected warm restoration

# Gate: node --test --test-name-pattern='^baseline match is separate from verified restore and Dreamer requires both semantics$' tools/emulator/vehicle-timing.test.ts

Gate contract: Maps: Requirement `Vehicle timing acceptance binds actual reads to current lifetime`; Scenario `A Dreamer restoration has only one matching semantic`. Preconditions: The production timing consumer receives boot-bound current-lifetime fixtures. Observable result: Baseline and restore remain distinct; Dreamer requires both forced-ev and drive-electric matches. Evidence boundary: Host consumer policy only; final129 selected cold/warm runtime and Settings ordering remain pending.

# Gate: node --test --test-name-pattern='^actual read observation measures boot time and does not invent process latency or causality$' tools/emulator/vehicle-timing.test.ts

Gate contract: Maps: Requirement `Vehicle timing acceptance binds actual reads to current lifetime`; Scenario `Cold restore reports actual boot-relative observation`. Preconditions: The production consumer receives current-boot lifetime fixtures including baseline and warm-cutoff branches. Observable result: Actual reads report boot-relative matched observation without invented process latency or causality. Evidence boundary: Host consumer fixtures only; selected immutable case13/14 cold/warm runtime remains pending.

# Gate: node --test --test-name-pattern='^PW baseline alone does not satisfy accepted completion and warm sequence cutoff excludes old success$' tools/emulator/vehicle-timing.test.ts

Gate contract: Maps: Requirement `Vehicle timing acceptance binds actual reads to current lifetime`; Scenario `Warm restore excludes preceding completion`. Preconditions: The production consumer receives current-boot lifetime fixtures including baseline and warm-cutoff branches. Observable result: Baseline-only evidence and prior warm-cutoff completion do not satisfy accepted restoration. Evidence boundary: Host consumer fixtures only; selected immutable case13/14 cold/warm runtime remains pending.

# Unverified: Current timing runtime acceptance

### Requirement: Read-only functional snapshots retry within unchanged authority

Outside an existing first-boot capture phase, the host emulator gate SHALL retry a read-only snapshot at most once after the structured empty-XML Accessibility deadline, including the bounded elapsed traversal report. It SHALL retain each failed action and its request/output artifacts, preserve the selected serial, exact original boot, Enforcing state and runtime lease through independent guards, and cap all capture and guard subprocesses to one 180-second phase. A retry SHALL require more than 45 seconds remaining. Mutating actions, unsupported failures, changed authority and exhausted attempts SHALL never be retried by this mechanism. The existing first-boot retry SHALL remain independent without nested attempts; native helper limits SHALL remain unchanged.

#### Scenario: A retained traversal deadline permits one read-only retry

- **WHEN** a selected-serial snapshot reaches the known empty traversal deadline and the next bounded observation completes under unchanged authority
- **THEN** the host returns the successful second observation and retains the failed action

#### Scenario: Snapshot authority or remaining budget changes

- **WHEN** a guard rejects the original authority or consumes the phase budget
- **THEN** no further observation is invoked and the snapshot fails

#### Scenario: Unsupported and persistent failures remain terminal

- **WHEN** the failure is unsupported or the second observation reaches the same deadline
- **THEN** the host returns the terminal failure without another attempt

#### Scenario: Mutation is never repeated as snapshot recovery

- **WHEN** a mutation returns the same helper deadline
- **THEN** snapshot recovery invokes it exactly once

# Gate: node --test --test-name-pattern="retained elapsed helper deadline is recognized only for the selected serial" tools/tests/snapshot-readback.test.ts

Gate contract: Maps: Requirement `Read-only functional snapshots retry within unchanged authority`; Scenario `A retained traversal deadline permits one read-only retry`. Preconditions: Exact retained launcher132 process output and matching or foreign serial. Observable result: Only the matching serial classifies the known elapsed deadline. Evidence boundary: Host production control flow and classifier only; actual guest scheduling and authority observations require runtime acceptance.

# Gate: node --test --test-name-pattern="retained launcher failure permits one fenced readonly retry" tools/tests/snapshot-readback.test.ts

Gate contract: Maps: Requirement `Read-only functional snapshots retry within unchanged authority`; Scenario `A retained traversal deadline permits one read-only retry`. Preconditions: Retained failure followed by a successful observation. Observable result: Exactly two reads and before/after guards with decreasing remaining budgets. Evidence boundary: Host production control flow and classifier only; actual guest scheduling and authority observations require runtime acceptance.

# Gate: node --test --test-name-pattern="foreign boot owner or non-Enforcing prevents a second observation" tools/tests/snapshot-readback.test.ts

Gate contract: Maps: Requirement `Read-only functional snapshots retry within unchanged authority`; Scenario `Snapshot authority or remaining budget changes`. Preconditions: Guard rejects after a retained deadline. Observable result: No second read is invoked. Evidence boundary: Host production control flow and classifier only; actual guest scheduling and authority observations require runtime acceptance.

# Gate: node --test --test-name-pattern="expired guard budget rejects before issuing another snapshot" tools/tests/snapshot-readback.test.ts

Gate contract: Maps: Requirement `Read-only functional snapshots retry within unchanged authority`; Scenario `Snapshot authority or remaining budget changes`. Preconditions: Guard consumes the full phase. Observable result: No snapshot is invoked. Evidence boundary: Host production control flow and classifier only; actual guest scheduling and authority observations require runtime acceptance.

# Gate: node --test --test-name-pattern="successful response is rejected if the final fence changes" tools/tests/snapshot-readback.test.ts

Gate contract: Maps: Requirement `Read-only functional snapshots retry within unchanged authority`; Scenario `Snapshot authority or remaining budget changes`. Preconditions: Final guard rejects after a successful read. Observable result: The result is rejected. Evidence boundary: Host production control flow and classifier only; actual guest scheduling and authority observations require runtime acceptance.

# Gate: node --test --test-name-pattern="a second deadline remains terminal without a third observation" tools/tests/snapshot-readback.test.ts

Gate contract: Maps: Requirement `Read-only functional snapshots retry within unchanged authority`; Scenario `Unsupported and persistent failures remain terminal`. Preconditions: Both reads return the retained deadline. Observable result: Exactly two reads return terminal failure. Evidence boundary: Host production control flow and classifier only; actual guest scheduling and authority observations require runtime acceptance.

# Gate: node --test --test-name-pattern="unknown failures are returned once and cannot trigger retry" tools/tests/snapshot-readback.test.ts

Gate contract: Maps: Requirement `Read-only functional snapshots retry within unchanged authority`; Scenario `Unsupported and persistent failures remain terminal`. Preconditions: Unknown, timed-out or permission failures. Observable result: Exactly one read returns the failure. Evidence boundary: Host production control flow and classifier only; actual guest scheduling and authority observations require runtime acceptance.

# Gate: node --test --test-name-pattern="mutating input is invoked once even with the same helper deadline" tools/tests/snapshot-readback.test.ts

Gate contract: Maps: Requirement `Read-only functional snapshots retry within unchanged authority`; Scenario `Mutation is never repeated as snapshot recovery`. Preconditions: Tap, swipe, drag, text and reboot return the retained deadline. Observable result: Each action is invoked exactly once. Evidence boundary: Host production control flow and classifier only; actual guest scheduling and authority observations require runtime acceptance.

# Unverified: Functional snapshot retry runtime



### Requirement: Vehicle observations correlate current accepted status

Strict vehicle observation helpers SHALL require current accepted S before and after a fresh CAN observation, independently supplied target process and daemon incarnation, unchanged endpoint binding and desired revision, and nondecreasing accepted receipt sequence. Root L metadata SHALL match the accepted endpoint, daemon incarnation and desired revision. Missing current acknowledgement or root metadata SHALL yield unavailable evidence; historical queued records SHALL NOT replace it.

#### Scenario: A fresh CAN observation belongs to the accepted endpoint

- **WHEN** both current accepted S observations and independently supplied authority match the fresh root-attributed L record
- **THEN** the helper returns the observation only for that binding

#### Scenario: Current status or routed metadata belongs to another endpoint lifetime

- **WHEN** endpoint, daemon incarnation or revision differs, or current accepted status is absent
- **THEN** the helper rejects the observation as unavailable

# Gate: node --test --test-name-pattern='^fresh CAN binds actual producer L metadata to both accepted S observations$' tools/emulator/current-endpoint.test.ts

Gate contract: Maps: Requirement `Vehicle observations correlate current accepted status`; Scenarios `A fresh CAN observation belongs to the accepted endpoint`, `Current status or routed metadata belongs to another endpoint lifetime`.
Preconditions: `VOBOOST_INJECT_CONTRACT_ROOT` points to the pinned producer fixture tree; independently supplied authority and fresh baseline/current log fixtures exercise the production helper.
Observable result: Matching producer metadata is accepted and missing, stale or mismatched metadata is unavailable.
Evidence boundary: Host correlation helper only; caller sampling order, real CAN changes, restart persistence and device application require emulator or vehicle evidence.

### Requirement: Startup vehicle receipts retain exact current authority

The optional `startupMilestones` resident projection SHALL use the closed daemon-monotonic clock shape and a separate Android boot timestamp. Its required nullable `clockAnchor` key SHALL retain a trusted Linux boot-elapsed sample bracketed by daemon-owner monotonic observations, with explicit 10ms precision, or null when unavailable. The anchor SHALL describe owner initialization, not process execution; consumers SHALL keep it separate from daemon receipt intervals and SHALL NOT subtract different clocks to synthesize an exact process start. A null anchor SHALL leave startup evidence unavailable. Its maximum serialized diagnostic size SHALL remain 64KiB within the unchanged 1MiB resident transport. Integer endpoint identities and document/lifecycle revisions SHALL remain canonical decimal strings. Unknown fields, unsafe clocks, duplicate target/member/semantic identities, contradictory application semantics and inconsistent dispatch/adoption order SHALL be rejected. Overflow SHALL retain expected inventory counts with empty diagnostic rows and SHALL remain unavailable rather than successful functional evidence.

A startup proof SHALL require at least one current backend application receipt for every enabled signed-catalog member whose `startupPriority` is `vehicle`. Every actually admitted-ready recorded target SHALL retain dispatch and adoption. Absent catalog target processes SHALL NOT become an artificial startup barrier. Accepted endpoint PID/starttime/epoch, current document/lifecycle and daemon/boot identities SHALL agree. Same-batch dispatch SHALL respect vehicle-first priority without requiring application completion before other processes begin. Numeric intervals SHALL be labeled daemon receipt intervals, with no fabricated latency SLA or claim of backend execution duration.

Case 27 SHALL preserve its existing preparation, Enforcing and lifecycle assertions and the unchanged twenty-seven-case inventory. It SHALL prepare stable current beta1 vehicle intent through existing public controls, perform a fresh Installer-owned reboot, and observe current startup receipts within the existing boot readiness budget. Repeated observations SHALL preserve the same boot, installed identity and daemon incarnation. Missing evidence SHALL remain unavailable; no screenshot, custom adb command or synthesized receipt SHALL substitute for authority.

#### Scenario: Optional startup status is malformed or stale

- **WHEN** a startup projection carries unknown fields, mixed clocks, stale endpoint/document/lifecycle identity or overflow
- **THEN** the consumer rejects malformed or foreign authority and preserves missing or overflow diagnostics as unavailable

#### Scenario: Every enabled vehicle member has current backend evidence

- **WHEN** a generic enabled vehicle member has a verified current backend receipt but another catalog target is absent
- **THEN** the consumer accepts that member without inventing a requirement for the absent process and retains false desired state distinctly

#### Scenario: Startup case retains its own fresh boot

- **WHEN** case27 executes its startup receipt assertion
- **THEN** stable vehicle intent is confirmed before one Installer-owned reboot and the resulting report retains receipt intervals separately from backend execution timing

#### Scenario: Native startup bytes cross the host parser boundary

- **WHEN** the owning native maximum status fixture is read by the product consumer
- **THEN** the exact serialized optional shape and total byte budget pass the strict unchanged resident reader

# Gate: node --test --test-name-pattern="owner initialization anchor is separately retained and unavailable never becomes synthetic process start" tools/emulator/startup-milestones.test.ts

Gate contract: Maps: Requirement `Startup vehicle receipts retain exact current authority`; Scenario `Optional startup status is malformed or stale`. Preconditions: Host fixtures vary trusted owner initialization anchor, its clock domain, safe integer bracket and 10ms precision. Observable result: Null stays unavailable, malformed anchors reject, and a late valid anchor remains separate diagnostic data without an invented process-start timestamp. Evidence boundary: Host shape and report semantics only; actual daemon process execution and early vehicle timing remain unverified.

# Gate: node --test --test-name-pattern="stale endpoint document lifecycle and daemon identity rejected" tools/emulator/startup-milestones.test.ts

Gate contract: Maps: Requirement `Startup vehicle receipts retain exact current authority`; Scenario `Optional startup status is malformed or stale`. Preconditions: Current resident, endpoint and startup fixtures vary their document, lifecycle, boot and daemon incarnation. Observable result: Foreign authority is rejected. Evidence boundary: Host rejection only; real boot application remains unverified.

# Gate: node --test --test-name-pattern="missing and overflow diagnostics stay unavailable" tools/emulator/startup-milestones.test.ts

Gate contract: Maps: Requirement `Startup vehicle receipts retain exact current authority`; Scenario `Optional startup status is malformed or stale`. Preconditions: Optional diagnostics are missing or the bounded producer reports overflow. Observable result: Missing and overflow stay unavailable and nonempty overflow tables are rejected. Evidence boundary: Host parser only; no native runtime overflow is injected.

# Gate: node --test --test-name-pattern="generic backend receipt accepts false intent without requiring absent intent-only target" tools/emulator/startup-milestones.test.ts

Gate contract: Maps: Requirement `Startup vehicle receipts retain exact current authority`; Scenario `Every enabled vehicle member has current backend evidence`. Preconditions: A generic future member has one current backend confirmation and an absent intent-only target. Observable result: The consumer preserves false intent and computes numeric receipt intervals. Evidence boundary: Synthetic host authority fixture, not CAN or physical vehicle proof.

# Gate: node --test --test-name-pattern="case27 actual callback arms before one installer-owned fresh reboot and writes receipt-only evidence" tools/emulator/startup-milestones.test.ts

Gate contract: Maps: Requirement `Startup vehicle receipts retain exact current authority`; Scenario `Startup case retains its own fresh boot`. Preconditions: The actual staged case27 callback executes against mock public-control, observation and reboot adapters. Observable result: Current intent preparation precedes exactly one reboot and missing receipts fail acceptance. Evidence boundary: Executed production callback with mock adapters; no emulator reboot or early vehicle timing is proven.

# Gate: node --test --test-name-pattern="owning native startup maximum serializer bytes roundtrip through unchanged bounded strict host status reader" tools/emulator/startup-milestones.test.ts

Gate contract: Maps: Requirement `Startup vehicle receipts retain exact current authority`; Scenario `Native startup bytes cross the host parser boundary`. Preconditions: The native owning golden fixture is available from the injection contract root. Observable result: Actual Status serializer bytes with 52 injection rows and typed startup diagnostics fit the unchanged 1MiB transport and strict optional 64KiB shape. Evidence boundary: Cross-language serializer and parser compatibility; fixture application semantics are synthetic and do not establish actual CAN behavior.

# Unverified: Current startup receipt runtime acceptance


### Requirement: Deferred launcher host boundary consumes direct semantic page observations

The product host first-page dispatcher SHALL select `launcher-page-observe` in deferred image mode and retain the PNG indicator branch when images are authorized. Each semantic boundary SHALL generate two separate lower-case 64-hex request IDs from `randomBytes(32)` and consume closed root receipts. The host consumer SHALL require direct `firstPosition`, `pageCount`, `offsetX`, `itemCount`, `outerCurrentItem`, idle scroll and noncomputing layout metrics; only `firstPosition=0`, `offsetX=0`, positive `pageCount=ceil(itemCount/12)` and `outerCurrentItem=1` establish the first app page. Missing metadata SHALL NOT fall back to images, Accessibility actions or the outer pager index. A valid native unavailable receipt SHALL remain unavailable, while a partial horizontal offset SHALL NOT establish the first page.

The host consumer SHALL correlate both root receipts across boot, daemon PID/starttime, launcher PID/starttime, endpoint epoch, generation, catalog and document generation, and across native window token and observation. Independent boot observations SHALL supply completed boot, current OEM PID and resident generation/catalog without requiring any feature injection to be enabled. Before, after and fresh Accessibility hierarchies SHALL retain the existing `launcherCaptureContinuity` checks and exact visible labels and bounds. Native `AllAppBarView` and its fixed `PagerGridLayoutManager` SHALL remain distinct from the captured Accessibility `androidx.recyclerview.widget.RecyclerView` class. The semantic query SHALL preserve fresh gesture authority after its read-only observations.

#### Scenario: Deferred dispatch avoids the null PNG path

- **WHEN** the actual production first-page callback runs with deferred images and matching paired root receipts
- **THEN** it makes two semantic queries, returns the first-page witness and preserves the captured item labels, bounds and fresh grid authority without a screenshot

#### Scenario: Authorized visual dispatch retains PNG observation

- **WHEN** the actual production first-page callback runs with images authorized
- **THEN** it uses the PNG indicator branch without a semantic page query

#### Scenario: A later app page shares the outer pager index

- **WHEN** direct first position is twelve while the outer pager index remains one
- **THEN** the host callback rejects the first-page claim and bounded normalization cannot accept a stalled viewport

#### Scenario: Every feature is disabled

- **WHEN** independent resident status contains no feature injections and paired root receipts match boot, OEM PID and resident generation/catalog
- **THEN** the host lifetime and hierarchy consumers admit the semantic witness

#### Scenario: Metadata or lifetime is stale

- **WHEN** direct metrics, required fields, idle layout, primary window, native lifetime, independent process identity, labels or bounds differ
- **THEN** the host consumer rejects the semantic witness

#### Scenario: Native observation is unavailable or partially offset

- **WHEN** a valid native unavailable receipt arrives or first position zero has a nonzero horizontal offset
- **THEN** the host preserves domain unavailable or returns a false first-page witness without image fallback

#### Scenario: The request contains authority overrides

- **WHEN** the semantic request lacks its exact lower-case 64-hex nonce or contains another field
- **THEN** the host request admission rejects it

# Gate: node --test --test-name-pattern="actual noimages launcherBeginning dispatcher queries semantics without reading a null PNG" tools/tests/launcher-semantic-boundary.test.ts

Gate contract: Maps: Requirement `Deferred launcher host boundary consumes direct semantic page observations`; Scenario `Deferred dispatch avoids the null PNG path`. Preconditions: Actual production callback with parsed captured OEM hierarchy and paired synthetic root receipts. Observable result: Two semantic queries, no screenshot, preserved labels/bounds and fresh grid gesture authority. Evidence boundary: Host dispatcher and consumer only; synthetic root receipts do not establish the Android producer or root transport.

# Gate: node --test --test-name-pattern="authorized visual dispatcher preserves PNG first-page observation" tools/tests/launcher-semantic-boundary.test.ts

Gate contract: Maps: Requirement `Deferred launcher host boundary consumes direct semantic page observations`; Scenario `Authorized visual dispatch retains PNG observation`. Preconditions: Actual production callback with authorized image branch. Observable result: One PNG capture and no semantic queries. Evidence boundary: Host dispatcher and consumer only; synthetic root receipts do not establish the Android producer or root transport.

# Gate: node --test --test-name-pattern="same outer item on later app page cannot establish first page" tools/tests/launcher-semantic-boundary.test.ts

Gate contract: Maps: Requirement `Deferred launcher host boundary consumes direct semantic page observations`; Scenario `A later app page shares the outer pager index`. Preconditions: Direct later-page metrics with outer item one and a repeated viewport. Observable result: False first-page result and bounded normalization rejection. Evidence boundary: Host dispatcher and consumer only; synthetic root receipts do not establish the Android producer or root transport.

# Gate: node --test --test-name-pattern="semantic witness remains available with every feature disabled" tools/tests/launcher-semantic-boundary.test.ts

Gate contract: Maps: Requirement `Deferred launcher host boundary consumes direct semantic page observations`; Scenario `Every feature is disabled`. Preconditions: Independent resident status with an empty injection inventory. Observable result: Matching paired root lifetime and hierarchy accepted. Evidence boundary: Host dispatcher and consumer only; synthetic root receipts do not establish the Android producer or root transport.

# Gate: node --test --test-name-pattern="semantic receipt rejects missing direct metrics busy layout wrong primary and missing fields" tools/tests/launcher-semantic-boundary.test.ts

Gate contract: Maps: Requirement `Deferred launcher host boundary consumes direct semantic page observations`; Scenario `Metadata or lifetime is stale`. Preconditions: Malformed or incomplete synthetic native receipts. Observable result: Missing metrics, busy layout, wrong window, duplicate keys and incomplete receipts rejected. Evidence boundary: Host dispatcher and consumer only; synthetic root receipts do not establish the Android producer or root transport.

# Gate: node --test --test-name-pattern="semantic receipt fences stale boot PID starttime endpoint generation document and native token" tools/tests/launcher-semantic-boundary.test.ts

Gate contract: Maps: Requirement `Deferred launcher host boundary consumes direct semantic page observations`; Scenario `Metadata or lifetime is stale`. Preconditions: Paired synthetic root receipts and independent inspect bodies. Observable result: Changed native lifetime or independent boot/process/catalog rejected. Evidence boundary: Host dispatcher and consumer only; synthetic root receipts do not establish the Android producer or root transport.

# Gate: node --test --test-name-pattern="semantic hierarchy preserves exact visible labels bounds and window authority" tools/tests/launcher-semantic-boundary.test.ts

Gate contract: Maps: Requirement `Deferred launcher host boundary consumes direct semantic page observations`; Scenario `Metadata or lifetime is stale`. Preconditions: Parsed captured OEM hierarchy with one altered property. Observable result: Changed labels, bounds, window, class or item inventory rejected. Evidence boundary: Host dispatcher and consumer only; synthetic root receipts do not establish the Android producer or root transport.

# Gate: node --test --test-name-pattern="launcher page request admits only a fresh lower-case 64hex nonce" tools/tests/launcher-semantic-boundary.test.ts

Gate contract: Maps: Requirement `Deferred launcher host boundary consumes direct semantic page observations`; Scenario `The request contains authority overrides`. Preconditions: Closed host request validator. Observable result: Only exact action and nonce fields admitted. Evidence boundary: Host dispatcher and consumer only; synthetic root receipts do not establish the Android producer or root transport.

# Gate: node --test --test-name-pattern="valid native unavailable remains unavailable and partial offset cannot establish first page" tools/tests/launcher-semantic-boundary.test.ts

Gate contract: Maps: Requirement `Deferred launcher host boundary consumes direct semantic page observations`; Scenario `Native observation is unavailable or partially offset`. Preconditions: Actual deferred callback with a valid unavailable root receipt or nonzero horizontal offset. Observable result: Domain unavailable is preserved; partial offset produces false and no screenshot. Evidence boundary: Host dispatcher and consumer only; Android producer behavior requires runtime acceptance.

### Requirement: Case 12 observes stock before enabling the launcher feature

Case 12 SHALL retain the observed initial signed launcher checkbox state. Before fixture-absent stock grid capture, an enabled launcher agent SHALL be disabled through its ordinary Settings control under the owning source-admitted stop policy. A required restart SHALL be confirmed through independent same-boot replacement process evidence and omitted launcher injection. The gate SHALL independently observe a unique unchecked checkbox and no selected launcher injection before accepting the stock capture, and SHALL only then enable the launcher for fixture installation. The preinstall case 1 baseline SHALL remain separate. A missing checkbox, retained injection or unconfirmed required restart SHALL prevent stock acceptance.

#### Scenario: Disabled original state permits stock capture before enable

- **WHEN** the actual case 12 prefix observes the launcher checkbox disabled and no selected runtime injection
- **THEN** fixture absence and the OEM grid capture precede the feature enable

#### Scenario: Enabled initial state is safely disabled before comparison

- **WHEN** the initial launcher checkbox is checked
- **THEN** the gate records that state and confirms the source-admitted disable and required process replacement before accepting its stock baseline
- **AND** it independently confirms the unchecked checkbox and omitted launcher injection before stock capture

#### Scenario: Original state cannot establish a disabled launcher

- **WHEN** the launcher checkbox is missing, the required stop proof is unavailable, or the runtime still selects its injection
- **THEN** the case reports unavailable before stock capture or feature enable

#### Scenario: A short checkbox is skipped by a large scroll

- **WHEN** the signed checkbox title is present but its checkbox has fewer than 40 visible pixels
- **THEN** the gate uses bounded short navigation toward the viewport center and a fresh focused observation
- **AND** it never lowers the checkbox admission threshold or taps stale coordinates

# Gate: node --test --test-name-pattern="actual case12 captures stock after source-admitted disable and rejects unavailable preparation" tools/tests/launcher-semantic-boundary.test.ts

Gate contract: Maps: Requirement `Case 12 observes stock before enabling the launcher feature`; Scenarios `Disabled original state permits stock capture before enable`, `Enabled initial state is safely disabled before comparison` and `Original state cannot establish a disabled launcher`. Preconditions: Actual source-extracted case 12 prefix and checkbox preflight with disabled, enabled, missing, failed-stop and runtime-selected fixtures. Observable result: Disabled state captures stock before enable; enabled state requires confirmed disable before capture; missing or unconfirmed preparation rejects before capture. Evidence boundary: Host executable control flow only; actual Android stop and stock state remain runtime acceptance.

# Gate: node --test --test-name-pattern="actual production seek recovers overshoot with bounded 25 percent swipes" tools/tests/launcher-checkbox158.test.ts

Gate contract: Maps: Requirement `Case 12 observes stock before enabling the launcher feature`; Scenario `A short checkbox is skipped by a large scroll`. Preconditions: Actual production navigation and captured clipped checkbox XML from full158. Observable result: Short downward then reverse upward navigation recovers a fully visible control while the 40-pixel threshold stays unchanged. Evidence boundary: Host executable navigation using actual historical XML; fresh guest execution remains runtime acceptance.

### Requirement: Semantic launcher runtime acceptance remains separate

The product SHALL keep fresh immutable runtime acceptance of the common bootstrap producer, root query transport and first-page normalization across drag and reboot separate from host receipt fixtures. Host receipt fixtures do not establish the actual Android page or physical display.

#### Scenario: Runtime launcher drag and reboot acceptance is pending

- **WHEN** only host dispatcher and consumer tests have run
- **THEN** actual no-images case 12, drag readback, reboot retention, all-disabled producer behavior and physical visual acceptance remain unverified

# Unverified: Semantic launcher page runtime acceptance


### Requirement: Fixture publication and secondary navigation retain actual evidence

The host gate SHALL capture immutable native-bracketed canonical logs immediately after fixture installation and before route or drag work. It SHALL complete first publication only from actual first emitted, first bind and prepared draw receipts on both screens tied to the original preinstall cursor and ownership receipt. Same-boot exports SHALL join only through identical overlapping canonical records with unchanged native boot and Launcher lifetime; conflicting, disjoint or regressing exports SHALL fail. A retained authenticated cross-boot beforeWitness SHALL remain tied to its exact old cursor while current-boot first-draw selection SHALL admit both persistent and reset numeric sequences without applying the old cursor as a current-store floor.

#### Scenario: Publication survives overlapping export rotation

- **WHEN** a later same-boot export omits early publication records but overlaps an independently bracketed retained export
- **THEN** the host retains actual original records without renumbering and rejects conflicts, gaps or changed lifetime

#### Scenario: Reboot resets canonical sequence

- **WHEN** a new boot starts with lower sequences and a valid independently bracketed beforeWitness exists
- **THEN** the current boot and authenticated endpoint select the genuine first draw on both screens, while an initial render still rejects disk reuse

# Gate: node --test --test-name-pattern="same boot overlapping canonical exports retain publication without inventing continuity" tools/emulator/launcher-cache.test.ts

Gate contract: Maps: Requirement `Fixture publication and secondary navigation retain actual evidence`; Scenario `Publication survives overlapping export rotation`. Preconditions: Host canonical-shaped overlapping, disjoint, conflicting and lifetime-changing exports. Observable result: Only exact monotonic same-lifetime overlap joins real records. Evidence boundary: Host archive admission, not packaged runtime publication.

# Gate: node --test --test-name-pattern="cross boot reset cursor selects current lifetime first draw without an old numeric floor" tools/emulator/launcher-cache.test.ts

Gate contract: Maps: Requirement `Fixture publication and secondary navigation retain actual evidence`; Scenario `Reboot resets canonical sequence`. Preconditions: Authenticated native-bracketed two-boot host fixtures with reset current sequences. Observable result: Genuine two-screen disk first draws pass and an initial render fails. Evidence boundary: Host evidence admission; actual reboot cache pixels require runtime evidence.

### Requirement: Secondary physical page navigation states its authority

The host fixture search SHALL traverse independently ordered secondary pages with at most eleven supported owned-profile physical swipes and SHALL accept only actual authenticated two-screen cache receipts. Physical swipe receipts SHALL require secondary SDK display1 completion and pointer release bracketed by unchanged independently observed boot and Launcher lifetime. The gate SHALL explicitly record the absence of a semantic target/window pre-dispatch fence and SHALL NOT represent a partial secondary tree, physical navigation or primary hierarchy as secondary resource authority.

#### Scenario: The right fixture is absent from the first page

- **WHEN** primary and secondary catalogs use independent page order
- **THEN** opening the right first page alone cannot finish search, a later genuine witness completes it, and absence stops after the bounded traversal

#### Scenario: Physical swipe lacks completion or crosses lifetime

- **WHEN** SDK display/completion/release differs or independent boot/Launcher identity changes
- **THEN** the physical navigation fails and never claims a semantic target fence

# Gate: node --test --test-name-pattern="secondary fixture search follows independent pages and never accepts opening the first page" tools/emulator/launcher-fixture.test.ts

Gate contract: Maps: Requirement `Secondary physical page navigation states its authority`; Scenario `The right fixture is absent from the first page`. Preconditions: Host page callbacks with distinct primary/right order and absent fixture. Observable result: Search needs later witness and terminates after eleven swipes when absent. Evidence boundary: Host orchestration using mocked reads; the production cache validator and actual screen1 receipts remain independent requirements.

# Gate: node --test --test-name-pattern="secondary physical swipe preserves honest display completion and lifetime authority" tools/emulator/launcher-fixture.test.ts

Gate contract: Maps: Requirement `Secondary physical page navigation states its authority`; Scenario `Physical swipe lacks completion or crosses lifetime`. Preconditions: Host SDK-shaped completion and independently observed lifetime callbacks. Observable result: Display/completion/release/lifetime changes fail and valid navigation explicitly records targetWindowPreDispatchFenced false. Evidence boundary: Host receipt consumer, not an actual secondary dispatch.

### Requirement: Drawer mode preparation and input have separate bounded budgets

Initial mode-card geometry preparation SHALL retain its bounded settling deadline. The tap/reacquisition operation SHALL start a separate ten-second monotonic deadline after initial stable geometry and observer identity are available. Exactly correlated missing-window not_started input MAY trigger one new observation and one retry only with unchanged mode caption, boot and observer identity and fresh valid geometry. Deadline expiry, stale observation, semantic drift and target authority failure SHALL remain distinct failures. Accepted, ambiguous, uncorrelated or second-failed input SHALL NOT replay.

#### Scenario: Initial preparation and reacquisition each take 6.2 seconds

- **WHEN** bounded initial preparation completes and one correlated non-dispatch is followed by unchanged semantic reacquisition in6.2seconds
- **THEN** preparation does not consume the ten-second operation budget and exactly one retry can complete

#### Scenario: Reacquisition expires or identity changes

- **WHEN** reacquisition crosses the operation deadline or changes caption, boot or observer identity
- **THEN** no retry is sent and expiry is distinguished from semantic drift

#### Scenario: Initial input is accepted

- **WHEN** the initial mode-card tap succeeds
- **THEN** the gate returns without reacquisition or replay

# Gate: node --test --test-name-pattern="drawer mode operation excludes delayed preparation and admits one 6.2 second reacquisition" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Drawer mode preparation and input have separate bounded budgets`; Scenarios `Initial preparation and reacquisition each take 6.2 seconds`, `Reacquisition expires or identity changes`, `Initial input is accepted`. Preconditions: Executed production cycle body and input helper with a deterministic clock and correlated native-shaped non-dispatch. Observable result: Delayed preparation plus6.2second reacquisition retries once, true expiry stops with a deadline error, accepted input never reacquires and an expired initial budget never dispatches. Evidence boundary: Host scheduling with mocked native input; actual Save CAN and intent require fresh runtime evidence.

# Gate: node --test --test-name-pattern="drawer mode card reacquires only unchanged semantic mode after correlated non-dispatch" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Drawer mode preparation and input have separate bounded budgets`; Scenario `Reacquisition expires or identity changes`. Preconditions: Production helper receives unchanged/changed mode and native-shaped correlated, ambiguous and second-stale failures. Observable result: Only unchanged semantic fresh geometry after exact non-dispatch retries once. Evidence boundary: Host replay admission.

# Gate: node --test --test-name-pattern="actual drawer mode cycle checks boot before replay without invalidating UI observation" tools/tests/emulator-gate.test.ts

Gate contract: Maps: Requirement `Drawer mode preparation and input have separate bounded budgets`; Scenario `Reacquisition expires or identity changes`. Preconditions: Production cycle body with fresh observer/boot callbacks and changed boot/source/observer branches. Observable result: Identity changes reject before retry. Evidence boundary: Host orchestration, not actual CAN transitions.

### Requirement: Finite launcher acceptance preserves independent proof boundaries

The finite launcher driver SHALL reject a user-section bulk visibility mutation that changes effective original-application visibility, including absent default-true entries. It SHALL restore the user header after a rejected mutation without writing original-application settings. Before arming an external-reboot checkpoint, it SHALL dispatch an observed-target-fenced move of the user fixture from a non-first slot to the first slot, then require fresh complete native page receipts on the same launcher lifetime proving the changed full order. Reboot acceptance SHALL compare that changed full order on a distinct boot. Accepted but ignored gestures SHALL NOT establish movement. A checkpoint without a proved move SHALL NOT establish complete launcher acceptance. Conditional Russian-language propagation SHALL remain a separate result: without current enabled translation authority, a checked system Russian selection, and actual same-client resource and callback locale readback, the driver SHALL report language `NOT_RUN` and broad completion `PARTIAL`, while preserving independent visibility, density, display and order results.

#### Scenario: User bulk visibility corrupts an original entry

- **WHEN** an authoritative user-header mutation changes an original entry even transiently
- **THEN** the driver rejects it and restores the user header

#### Scenario: Accepted movement has no observed effect

- **WHEN** the gesture is accepted but the complete native order remains unchanged
- **THEN** the driver rejects it without arming a reboot checkpoint

#### Scenario: A changed order is retained across reboot

- **WHEN** actual movement changes the complete inventory before an external reboot
- **THEN** the checkpoint retains that changed order and rejects the original order after reboot

#### Scenario: Russian propagation has no independent authority

- **WHEN** density and display observations complete without a proved Russian-language dependency
- **THEN** language remains `NOT_RUN` and reboot completion remains `PARTIAL`

# Gate: node --test --test-name-pattern="user section cannot change original visibility even transiently" tools/emulator/launcher-runtime.test.ts

Gate contract: Maps: Requirement `Finite launcher acceptance preserves independent proof boundaries`; Scenario `User bulk visibility corrupts an original entry`. Preconditions: Official-observer-shaped fixtures change an original visibility entry during a user header action. Observable result: Rejection and user-header restoration. Evidence boundary: Host driver admission, not OEM UI behavior.

# Gate: node --test --test-name-pattern="accepted but ignored drag cannot checkpoint unchanged order" tools/emulator/launcher-runtime.test.ts

Gate contract: Maps: Requirement `Finite launcher acceptance preserves independent proof boundaries`; Scenario `Accepted movement has no observed effect`. Preconditions: A fenced gesture is accepted without changing native page receipts. Observable result: No checkpoint and a failed order comparison. Evidence boundary: Host driver admission, not actual dragging.

# Gate: node --test --test-name-pattern="checkpoint retains changed full order and rejects original order after reboot" tools/emulator/launcher-runtime.test.ts

Gate contract: Maps: Requirement `Finite launcher acceptance preserves independent proof boundaries`; Scenario `A changed order is retained across reboot`. Preconditions: Complete fixture page receipts expose the changed full order. Observable result: The checkpoint contains the changed inventory and original-order reboot evidence is rejected. Evidence boundary: Host evidence comparison, not a real reboot.

# Gate: node --test --test-name-pattern="completed non-language phases plus changed external reboot order remain PARTIAL" tools/emulator/launcher-runtime.test.ts

Gate contract: Maps: Requirement `Finite launcher acceptance preserves independent proof boundaries`; Scenario `Russian propagation has no independent authority`. Preconditions: Density, display, visibility, section and changed-order fixture checks complete with no independent Russian authority. Observable result: Distinct-boot resume remains partial with language unavailable. Evidence boundary: Host result classification; actual conditional language propagation remains unverified.

### Requirement: Installer consumers select devices with one canonical runtime flag

Product acceptance consumers SHALL pass the currently selected serial to Installer observation, log collection and acceptance requests through the sole --device runtime flag. The product CLI contract SHALL NOT retain --device-serial as an alias or add a separate ADB server port option. Existing configuration, owned-session identity and Installer response schemas SHALL remain authoritative for the selected serial; host consumers SHALL NOT replace a selected serial with a fallback.

Exact argument proof guards SHALL admit the canonical invocation and reject a legacy flag, changed serial or additional target arguments. Native USB consumers SHALL use the same canonical device flag without an ADB server port argument.

#### Scenario: Acceptance consumers forward the current device

- **WHEN** an acceptance consumer selects either of two emulator serials or a native USB serial
- **THEN** the host constructs the exact --device invocation and preserves that serial across the child call and result identity check

#### Scenario: Retained argument proof differs

- **WHEN** retained evidence or a fault-window invocation contains --device-serial, another serial or extra target arguments
- **THEN** the exact proof guard rejects that input instead of admitting it as current canonical evidence

# Gate: node --test --test-name-pattern="runtime drivers forward the selected device with only the canonical flag" tools/emulator/installer-device.test.ts

Gate contract: Maps: Requirement `Installer consumers select devices with one canonical runtime flag`; Scenario `Acceptance consumers forward the current device`. Preconditions: Four real product runtime driver entrypoints run against a host process adapter and owned-session fixtures. Observable result: Every driver passes each selected emulator serial through exact --observe --device argv without a server-port option. Evidence boundary: Host invocation construction only; actual Installer transport and emulator device I/O remain separate.

# Gate: node --test --test-name-pattern="Installer UI preserves canonical device identity for emulator and native USB" tools/emulator/installer-device.test.ts

Gate contract: Maps: Requirement `Installer consumers select devices with one canonical runtime flag`; Scenario `Acceptance consumers forward the current device`. Preconditions: InstallerUi and a host child adapter are available. Observable result: Exact acceptance argv uses --device for two emulator serials and a USB serial, and a foreign result serial fails. Evidence boundary: Host invocation and receipt admission only; physical USB and diagnostic transport remain separate.

# Gate: node --test --test-name-pattern="provider forwards only the canonical runtime device" tools/emulator/provider-voboost.test.ts

Gate contract: Maps: Requirement `Installer consumers select devices with one canonical runtime flag`; Scenario `Acceptance consumers forward the current device`. Preconditions: Schema2 requests and a host child adapter are available. Observable result: Two explicit emulator serials and one USB serial each reach exact --observe --device argv without protocol extensions. Evidence boundary: Product consumer host contract only; no external producer or guest is run.

# Gate: node --test --test-name-pattern="i18n log proof rejects legacy and foreign device arguments" tools/tests/i18n-system-picker.test.ts

Gate contract: Maps: Requirement `Installer consumers select devices with one canonical runtime flag`; Scenario `Retained argument proof differs`. Preconditions: Complete raw i18n host fixtures contain canonical log invocation transcripts. Observable result: Canonical evidence admits while legacy flags, changed serials and extra port arguments fail. Evidence boundary: Host evidence admission only; Android and QNX acceptance remain separate.

# Gate: node --test --test-name-pattern="fault window rejects legacy and extra device arguments before dispatch" tools/emulator/fault-window.test.ts

Gate contract: Maps: Requirement `Installer consumers select devices with one canonical runtime flag`; Scenario `Retained argument proof differs`. Preconditions: Valid typed fault authority and hostile invocation fixtures are available. Observable result: The legacy flag, mismatched serial and extra target arguments fail before any child dispatch. Evidence boundary: Host argument admission only; guest fault behavior remains separate.

### Requirement: One product runner owns the prepared machine

The product runner SHALL obtain an exclusive lease for the canonical prepared machine before session mutation and SHALL preserve nonterminal product operation records across process failure. An unknown product operation SHALL prevent automatic session shutdown and later mutations until its outcome is reconciled.

#### Scenario: Product mutation outcome is unknown

- **WHEN** a dispatched product operation has no verified terminal outcome
- **THEN** the runner preserves the operation record and refuses session shutdown and further mutations

#### Scenario: Another runner selects the same machine

- **WHEN** the canonical machine already has a live lease or an unreconciled durable owner
- **THEN** another run is rejected before session start

# Unverified: Complete runner ownership acceptance

### Requirement: Full acceptance accounts for the candidate catalog

A full product verdict SHALL include every required integration and functional case and every applicable stable agent target from the candidate catalog. Missing required execution or evidence SHALL block a full PASS. Focused and semantic-only evidence SHALL NOT be promoted to full acceptance.

#### Scenario: A stable agent has no scenario

- **WHEN** the candidate includes a stable agent without a registered required scenario
- **THEN** the product report identifies that agent as blocked and does not return full PASS

#### Scenario: A native GUI subset lacks complete acceptance

- **WHEN** only the native G01, G02 and G06 subset has evidence and other required GUI cases remain unexecuted
- **THEN** the product report keeps all eight missing GUI cases visible and blocks full PASS

# Gate: ./gradlew test --tests ru.voboost.tests.AcceptancePlanTest.nativeGuiSubsetCannotEstablishFullAcceptance

Gate contract: Maps: Requirement `Full acceptance accounts for the candidate catalog`; Scenario `A native GUI subset lacks complete acceptance`. Preconditions: Controlled host admission fixtures include all eleven required GUI cases and evidence for the three-case subset. Observable result: Eight missing GUI results remain visible and full acceptance stays blocked. Evidence boundary: Host evidence admission only; no native application or device is executed.

# Unverified: Current packaged native GUI acceptance

Maps: Requirement `Full acceptance accounts for the candidate catalog`; Scenario `A native GUI subset lacks complete acceptance`. Unavailable observation: Fresh packaged G01/G02/G06 native input and independent readback, exact Java/native application start-time equality, background launch behavior, complete G03–G05/G07–G11 recipes and verified owned cleanup. Why unavailable: The current packet compiles test-only native tools and runners and runs host protocol/ownership tests; the integrated native split and new candidate have not been executed. Closure evidence: Complete the missing recipes and execute the approved immutable candidate on one coordinator-owned guest, retaining actual native app/control, device, operation and cleanup evidence for all eleven cases without replay of unknown outcomes.

# Unverified: Complete catalog acceptance

### Requirement: Startup acceptance observes the selected next boot

Startup acceptance SHALL select the actual Settings radio, independently confirm the persisted mode without an immediate product stop, and verify the resulting owned boot. The none scenario SHALL prove fresh inject, ART runtime, APK and functional executor absence while independently binding the permitted resident owner to its exact installed executable and restore interface mode only through the packaged computer installer. Unknown mutation outcomes SHALL retain ownership without replay.

#### Scenario: None mode takes effect only after reboot

- **WHEN** the runner selects none through the current observed Settings radio
- **THEN** it confirms same-boot persistence and a still-live product before reboot, observes no inject, ART runtime, APK or functional executor after the new boot and separately verifies the exact installed resident owner, and verifies packaged-installer recovery and the live Settings tab

#### Scenario: Hidden mode returns through the actual TBOX gesture

- **WHEN** the runner selects hidden through Settings and verifies its next owned boot
- **THEN** it verifies a complete Settings observation without the tab, independently checks enabled running targets, sends one fixed five-tap gesture to the observed stock TBOX row, and confirms live interface recovery without restarting the observed processes

# Unverified: Current startup hidden acceptance

# Unverified: Current startup none acceptance

### Requirement: Launcher fixture is first-party test source

Case12 SHALL use only the minimal root-owned `tests/emulator/agents/agent-launcher/app` application, package `ru.voboost.tests.launcherfixture`, launcher activity `.MainActivity` and label `Voboost Fixture`. Existing offline Java17 and Android34 tools SHALL build and verify it under `build/test-fixtures/launcher`; production composition SHALL NOT contain the fixture. No external APK or application digest SHALL authorize compatibility or installation. The closed installer action SHALL validate the bounded stored binary manifest and matching ZIP local/central directory, reject foreign packages, additional components, permissions, duplicate entries, unsupported encodings and incomplete input, and freeze admitted bytes before install. Install SHALL refuse replacement. Original absence, successful install, observed complete order, one owned drag, a new boot, retained complete order and owned uninstall SHALL remain required. Semantic persistence and mandatory visual acceptance SHALL retain separate results.

#### Scenario: A same-name external APK is offered

- **WHEN** its package, manifest components, ZIP directory or manifest encoding differs from the fixed fixture contract
- **THEN** admission fails before ADB installation, regardless of its filename or embedded package-like strings

# Gate: ./gradlew buildLauncherFixture test --tests ru.voboost.tests.installer.cli.InstallerDriverTest --offline

Gate contract: Maps: Requirement `Launcher fixture is first-party test source`; Scenario `A same-name external APK is offered`. Preconditions: Existing offline JDK17/Android34 tools and ephemeral host role roots are available. Observable result: The root builds its own launcher APK; the owning parser rejects foreign/additional-component, alternate-directory, compressed/streaming and truncated manifests while accepting its fixed semantic manifest. Evidence boundary: Host source/build/admission only; actual installation, drag, reboot persistence, cleanup and visual acceptance require the coordinated candidate runtime.


### Requirement: Numbered input and vehicle subchecks preserve capability boundaries

The shared root runner SHALL execute numbered9–14 through its retained device owner and publish each terminal subcheck before starting the next scenario. Real input SHALL require observed committed text and UTF-16 selection, actual keys and exact per-key readback, with no replay after unknown input. Missing API28 editor metadata or reviewed canvas authority SHALL remain BLOCKED before key dispatch. The PW counterfactual SHALL require actual OEM/CAN agreement, disabled-agent reset and enabled-agent retention across two distinct boots. Partial CAN getter/setter history SHALL NOT prove continuous physical OFF. The existing producer wire identity SHALL bind voboost-inject; any independently observed owner process SHALL retain its separate identity.

#### Scenario: A later scenario fails after a real semantic observation

- **WHEN** an earlier numbered subcheck retained its verified input, persistence or explicit capability blocker
- **THEN** its current-run evidence remains published, while mandatory visual acceptance and the whole candidate remain independently incomplete

#### Scenario: A sampled CAN getter is offered as continuous OFF proof

- **WHEN** the public observer declares other feedback paths partial and no survival across process death
- **THEN** case13 records the specific missing capability before FEV mutation and cannot claim the mandatory drawer/continuous-OFF program passed

# Gate: ./gradlew test --tests ru.voboost.tests.installer.cli.SourceCaseEvidenceTest --offline

Gate contract: Maps: Requirement `Numbered input and vehicle subchecks preserve capability boundaries`; Scenario `A later scenario fails after a real semantic observation`. Preconditions: Numbered terminal proof fixtures and partial semantic results exist. Observable result: The closed publisher preserves numbered current-run semantic evidence and keeps incomplete visual/full functional outcomes blocked. Evidence boundary: Host result publication only; no runtime observation is synthesized.

# Gate: ./gradlew test --tests ru.voboost.voyemu.VehicleCanTest --offline

Gate contract: Maps: Requirement `Numbered input and vehicle subchecks preserve capability boundaries`; Scenario `A sampled CAN getter is offered as continuous OFF proof`. Preconditions: Real-producer-shaped host observations and deterministic owning program ports are available. Observable result: Fixed CAN/window/checkbox identity is required; two changed boots establish the counterfactual; unknown input never replays; partial coverage remains explicit; the actual read-only process script binds inject and cannot substitute the owner. Evidence boundary: Host behavior and runner compilation only; current-candidate runtime, font provenance, visual review and full FEV drawer coverage remain unverified.

### Requirement: Forced EV source recipe is whole behind independent admission

The case13 program SHALL include disabled-agent cold and warm baselines, actual drawer arming, Hybrid/Save/Electric transitions, enabled warm and cold restoration, explicit OFF, repeated mode transitions and OFF retention through warm/cold cycles. Each accepted effect SHALL have independent actual drawer, CAN and available durable-intent readback. A matching observation after the effect deadline SHALL NOT pass. Continuous coverage SHALL begin before explicit OFF and span every later transition. Admission SHALL precede every mutation; sampled getters, setter-only replay or a missing native source SHALL keep it BLOCKED. Missing durable scalar values SHALL remain unknown rather than false. The recipe SHALL retain single owned inputs and lookup-only unknown outcomes, including its observed opening/closing gestures, exact OEM consent, power keys and cold reboot. A future official evidence adapter SHALL supply native coverage without replacing the scenario sequence.

#### Scenario: Current public CAN has only partial feedback coverage

- **WHEN** the current official source does not expose a complete native/continuous proof contract
- **THEN** the whole source program remains wired but dispatches no mutations; host behavior tests cannot turn the blocker into runtime acceptance

#### Scenario: An accepted mode input loses its reply or effect proof

- **WHEN** its outcome is unknown or independent effect readback expires
- **THEN** no second mode click, checkbox rollback or alternative coordinate action is issued

# Gate: ./gradlew test --tests ru.voboost.voyemu.AgentForcedEvProgramTest --offline

Gate contract: Maps: Requirement `Forced EV source recipe is whole behind independent admission`; Scenario `Current public CAN has only partial feedback coverage`; Scenario `An accepted mode input loses its reply or effect proof`. Preconditions: Real-producer-shaped host drawer/scalar/CAN observations and controlled program ports are available. Observable result: All16 stages and warm/cold distinctions are exercised; no mutation precedes admission; geometry, owner, scalar types and deadlines are checked; unknown operations can only be looked up. Evidence boundary: Host source behavior and compilation only; official native continuous coverage and current-candidate runtime remain unverified.

### Requirement: Startup profile rows retain actual semantic and admission outcomes

Case23 SHALL execute the three selected startup modes in every independently admitted system locale, with an actual changed interface selection, exact selected-description/root readback, computer-installer return from none, real uninstall-dialog cancellation and rejected-radio persistence readback. The original ready boot SHALL fence every action. Each completed row SHALL be retained before the next action. A source admission adapter that has not been implemented SHALL remain an explicit implementation gap rather than evidence of unsupported firmware or a passing locale. Native draw/visual criteria SHALL remain independent, and failed or unknown input SHALL NOT be replayed or cause agent-checkbox rollback.

#### Scenario: Russian all-owner admission is not implemented in the Java route

- **WHEN** EN/ZH semantic rows are available but the Russian picker/all-owner admission adapter remains missing
- **THEN** those rows remain retained, sourceComplete is false, and the full case remains BLOCKED without pretending that the firmware lacks Russian capability

#### Scenario: A later profile fails after an earlier profile passed

- **WHEN** an accepted earlier locale/mode has actual UI and root evidence
- **THEN** its retained row remains available and the later action is not replayed

# Gate: ./gradlew test --tests ru.voboost.voyemu.StartupDescriptionsTest --offline

Gate contract: Maps: Requirement `Startup profile rows retain actual semantic and admission outcomes`; Scenario `Russian all-owner admission is not implemented in the Java route`; Scenario `A later profile fails after an earlier profile passed`. Preconditions: Controlled host program ports and actual-public-shape control/dialog fixtures are available. Observable result: All nine program profiles, computer-only returns, cancel/reject callbacks, explicit Russian admission gaps and durable earlier rows are exercised without replay. Evidence boundary: Host source behavior and compilation only; new schema2/session log consumption, Russian all-owner admission, native drawing and current packaged runtime remain unverified.


### Requirement: First seeding uses two clean owned lifecycles

Case24 SHALL run both initial OEM baselines through sequential normal uninstall/managed-root and relevant-key absence, independent pre-install OEM readback, and installation of the same candidate on one retained owned machine. The current writer first ABSENT read, matching commit and independent present boolean root readback SHALL remain distinct evidence. An authenticated early T2 no-state-seeding/current-writer floor SHALL precede the first reads; a post-export watermark SHALL NOT substitute. The runner SHALL admit normal cleanup, OEM controls, install and restoration before any mutation. Unknown outcomes SHALL stop without replay or finally-based cleanup. Missing durable coordinator phases or early first-read evidence SHALL remain BLOCKED; all-agent enablement or final false values SHALL NOT prove initial seeding.

#### Scenario: Initial seeding prerequisites are missing

- **WHEN** admission cannot prove the clean lifecycle, early T2 first-read capture or restoration capability
- **THEN** no cleanup, OEM selection or installation is dispatched
- **AND** host policy results remain separate from functional acceptance

#### Scenario: The coordinator records unavailable early capture

- **WHEN** the retained coordinator reaches case24 without authenticated early T2 capture
- **THEN** it records scoped BLOCKED before all-agent enablement and dispatches no clean-cycle mutation
- **AND** seed-first, seed-second and seed-restore remain distinct normal install/uninstall phases, rejecting emergency absence
- **AND** independent cases continue after this unmutated BLOCKED result
- **AND** an unknown dispatched mutation stops without replay and retains its owner

# Gate: ./gradlew test --tests ru.voboost.tests.agents.InitialSeedingProgramTest --offline

Gate contract: Maps: Requirement `First seeding uses two clean owned lifecycles`; Scenario `Initial seeding prerequisites are missing`. Preconditions: A retained-authority host policy port and missing-capability, changed-source and unknown-action fixtures are available. Observable result: Two admitted clean cycles preserve ordering and restore after both; unavailable admission dispatches nothing and unknown install cannot replay or trigger cleanup. Evidence boundary: Host policy orchestration only; the actual durable coordinator adapter and early T2 first-read evidence remain unavailable.

# Gate: ./gradlew test --tests ru.voboost.tests.installer.cli.InitialSeedingAdmissionTest --offline

Gate contract: Maps: Requirement `First seeding uses two clean owned lifecycles`; Scenario `The coordinator records unavailable early capture`. Preconditions: A retained scope and the current missing early install capture contract are available. Observable result: Admission produces BLOCKED without mutation authority and invokes the independent case exactly once; unknown dispatch exceptions and mutated blocked receipts cannot continue; missing UI option restoration is rejected before cleanup. Evidence boundary: Host admission/control flow and source wiring of normal removal/OEM/root/config restoration only; early first-read capture, the guarded install transition and runtime clean lifecycles remain unavailable.

### Requirement: State outage acceptance binds actual runtime controllers

Case25 SHALL prove the current native root state owner and its independently observed PID/starttime, authenticated writer, current boot/generation/catalog and actual canbus input endpoint before state mutation. Injection metadata, codec availability and passive RX alone SHALL NOT establish controller ownership. The closed native read-unavailable and write-unavailable windows SHALL bind the owner, input target and token. Each window SHALL prove at least two actual failed operations and the corresponding bounded retry or Waiting(State) progression. Producer, target, writer or lease replacement SHALL NOT count as recovery. Cancellation SHALL prove the stopped lease and a separate quiet interval of at least one second with unchanged operation/progress counters; unavailable or incomplete receipts SHALL NOT prove silence. Recovery SHALL require controlled release, a current-lease successful state read, EOF cleanup and independent fixed root-file readback. Unknown actions SHALL stop without replay or checkbox rollback. Original desired configuration, root intent and OEM state SHALL be independently restored after confirmed completion.

Whole-case acceptance SHALL include exactly the four PW fault programs (read recovery, read cancellation, write recovery and write cancellation) and both runtime-disable-cancellation-fev and runtime-disable-cancellation-pw programs. Each disable program SHALL observe actual same-boot sleep/wake, stock OEM reset while disabled, unchanged input process lifetime and the distinct FEV retained-intent restoration or PW clean-stop adoption followed by explicit rearm. Android display power SHALL remain separate from CAN ACC. Component host tests SHALL remain separate from the six actual runtime results.

#### Scenario: A diagnostic names a foreign or replaced controller

- **WHEN** token, native owner, input endpoint, request, writer or target lifetime differs
- **THEN** the reader rejects the proof and no matching payload is admitted

#### Scenario: An incomplete export hides a retry

- **WHEN** native snapshots omit the stopped lease or do not cover the required quiet interval
- **THEN** a quiet cancellation assertion remains unavailable even if the available snapshots contain no later retry

#### Scenario: A complete asleep display sample has false readiness

- **WHEN** public system.read reports complete Asleep/OFF with display_ready=false
- **THEN** the Android sleep edge is accepted while independent CAN ACC remains required

#### Scenario: PW is enabled after an explicit clean stop

- **WHEN** the disabled warm reset established the current OEM sound setting
- **THEN** PW adopts that setting and requires explicit rearm instead of restoring stale intent

# Gate: ./gradlew test --tests ru.voboost.tests.installer.cli.NativeStateOutageAdmissionTest.actual_closed_receipts_prove_retry_cancel_recovery_and_no_replay --offline

Gate contract: Maps: Requirement `State outage acceptance binds actual runtime controllers`; Scenarios `A diagnostic names a foreign or replaced controller` and `An incomplete export hides a retry`. Preconditions: Closed source-shaped native owner stream receipts and independent writer/process/endpoint/root fixtures exist. Observable result: Actual failure/progress counters, stopped-lease quiet interval, release, new-lease recovery and EOF cleanup are admitted only for the exact current scope. Evidence boundary: Host causal admission only; no guest fault is dispatched.

# Gate: ./gradlew test --tests ru.voboost.tests.installer.cli.NativeStateOutageAdmissionTest.rejects_wrong_owner_stale_generation_target_timeout_unknown_and_replay --offline

Gate contract: Maps: Requirement `State outage acceptance binds actual runtime controllers`; Scenario `A diagnostic names a foreign or replaced controller`. Preconditions: Native owner stream fixtures with wrong identities, stale leases, timeouts and replay attempts exist. Observable result: Foreign or unresolved authorities cannot establish recovery or dispatch another input. Evidence boundary: Host receipt admission; the actual current owner remains a runtime observation.

# Unverified: Complete native state outage runtime

# Gate: ./gradlew test --tests ru.voboost.voyemu.VehiclePowerTest --offline

Gate contract: Maps: Requirement `State outage acceptance binds actual runtime controllers`; Scenario `A complete asleep display sample has false readiness`. Preconditions: Source-shaped complete asleep/awake and malformed public power projections exist. Observable result: Asleep/OFF/false and Awake/ON/true are distinguished; null/missing/coerced readiness cannot become false. Evidence boundary: Host Android display consumer only; CAN ACC remains independently observed at runtime.

# Gate: ./gradlew test --tests ru.voboost.voyemu.VehicleDisableCancellationTest.pedestrianAdoptsCurrentOemAfterCleanStopThenRequiresExplicitRearm --offline

Gate contract: Maps: Requirement `State outage acceptance binds actual runtime controllers`; Scenario `PW is enabled after an explicit clean stop`. Preconditions: Counterfactual host ports model independently observed warm reset, reenable and explicit rearm. Observable result: FEV restores while PW adopts then rearms; unknown power outcome cannot replay or reenable a checkbox. Evidence boundary: Host program ordering only; actual OEM/CAN/source-bound candidate evidence remains separate.


# Gate: ./gradlew test --tests ru.voboost.voyemu.VehicleDisableCancellationTest.forcedEvRestoresRetainedIntentAfterDisabledWarmReset --offline

Gate contract: Maps: Requirement `State outage acceptance binds actual runtime controllers`; Scenario `PW is enabled after an explicit clean stop`. Preconditions: A counterfactual FEV host port exposes the disabled warm reset and retained intent. Observable result: Reenable restores the retained FEV choice, separately from PW adoption. Evidence boundary: Host ordering only; no runtime OEM effect is inferred.


### Requirement: Log integration uses the current resident writer

I05 SHALL compose the current daemon owner writer, actual rotation and production export with the actual desktop parser and archive consumer. The root runner SHALL select binary voboost-daemon and exact existing owning tests, require exactly one selected terminal pass with the exact owning name per invocation. An actual Cargo inventory SHALL be retained when binding or changing the selected producer; a repeated listing build SHALL NOT be required for every regular invocation. A legacy inject writer, zero-selected filter, ignored-but-unexecuted test or a different test with a successful summary SHALL NOT satisfy this gate. An 8 MiB bounded host retention budget SHALL NOT be described as physical 128 MiB pressure or authenticated runtime IPC.

#### Scenario: Wrong binary or test path is selected

- **WHEN** the root mapping points at the legacy inject writer or a foreign test path
- **THEN** the mapping regression or exact terminal evidence rejects the invocation
- **AND** no I05 PASS is published for an empty or unexecuted selection

#### Scenario: Current owner bytes reach the desktop archive

- **WHEN** the real owner producer rotates actual written bytes and exports its retained records
- **THEN** the actual desktop parser and archive preserve exact schema2 records and context
- **AND** unavailable authenticated current session remains explicit
- **AND** exact production owner_client/state wrapper tests use real framed Store/storage, cover Missing/false/Error, commit and lost acknowledgement without resend, configuration conflict/byte preservation, and writer/batch identity
- **AND** only host connection and peer admission are substituted; live peer authentication and general runtime readiness remain separate gates

#### Scenario: Captured choice loses its acknowledgement

- **WHEN** the closed I05 owner composition inventory is selected
- **THEN** the exact captured_choice_without_ack_keeps_owner_wal_and_never_reconnects test is required in binary voboost-daemon
- **AND** host mapping alone does not prove the owner WAL behavior

#### Scenario: The generated native registry is unavailable

- **WHEN** I05 has no canonical nonempty regular generated native registry
- **THEN** it reports an explicit BLOCKED prerequisite and the existing generation command before owning Cargo execution
- **AND** unrelated delivery and CLI checks do not require that registry

# Gate: ./gradlew test --tests ru.voboost.tests.logging.LogPipelineSelectionTest --offline

Gate contract: Maps: Requirement `Log integration uses the current resident writer`; Scenario `Wrong binary or test path is selected`. Preconditions: The closed owner/consumer command mappings and actual-format Cargo output fixtures are available. Observable result: Owner binary, exact ignored producer, framed Store selection and consumer agree; empty, foreign, ignored and ambiguous terminal results are rejected. Evidence boundary: Root mapping and evidence parser behavior only; no native test execution or runtime is implied.

# Gate: ./gradlew integrationTest --tests ru.voboost.tests.logging.DaemonLogPipelineTest -PtrustScope=local-input -Pcargo=/absolute/trusted/cargo -Prustc=/absolute/trusted/rustc --offline

Gate contract: Maps: Requirement `Log integration uses the current resident writer`; Scenario `Current owner bytes reach the desktop archive`. Preconditions: Existing trusted Rust/JDK/SDK tools, shared caches, current ignored owner producer and desktop consumer are available. Observable result: The bound owning names are retained in one-time actual inventory; framed Store and producer and consumer execute once with exact terminal counts; real owner rotation/export bytes survive desktop parsing and archive publication. Evidence boundary: Host writer/export/consumer composition with 8 MiB retention budget only; current session authentication is unavailable and authenticated production peer/runtime and physical pressure remain unverified.


# Gate: ./gradlew test --tests ru.voboost.tests.logging.LogPipelineSelectionTest.capturedChoiceWithoutAcknowledgementIsRequiredByTheOwnerComposition --offline

Gate contract: Maps: Requirement `Log integration uses the current resident writer`; Scenario `Captured choice loses its acknowledgement`. Preconditions: Closed current owner composition inventory is available. Observable result: The exact captured-choice owning test selects the resident daemon with exact non-ignored execution. Evidence boundary: Java command selection only; actual owner WAL composition execution remains required.

# Gate: ./gradlew test --tests ru.voboost.tests.logging.LogPipelineSelectionTest.nativeRegistryRequiresTheActualNonemptyGeneratedFile --offline

Gate contract: Maps: Requirement `Log integration uses the current resident writer`; Scenario `The generated native registry is unavailable`. Preconditions: Local generated-file fixtures are available. Observable result: Missing, empty and symbolic-link registry inputs fail with an explicit prerequisite, while the canonical regular nonempty file is admitted. Evidence boundary: Host file prerequisite only; no registry generation or Cargo execution occurs.

### Requirement: Installation completion retains independent terminal and content proof

The root install monitor SHALL require the exact current CLI terminal transaction receipt for the requested manifest, successful process exit, accepted install authority and two independently verified changed boots. Duplicate or foreign terminal receipts SHALL fail. A canonical or plain terminal Install failed diagnostic SHALL remain failure even if the process exits zero. The independent status consumer SHALL require management/healthy/running, the requested version, same content relation and no diagnostic before requesting its separate boot-fenced runtime observation. Native install-complete or two boots SHALL NOT substitute for installed content identity or health. An explicit retained-owner recovery MAY reconcile a terminal native install with a source-bound desktop identity failure only as failed-reconciled/FAILED, never verified. Until a strict normal uninstall receipt proves a changed boot and all nine absence checks, the owner SHALL remain retained and other mutations or session shutdown SHALL be rejected. Normal lifecycle resume SHALL NOT replay an installation from failed history. An uninstall transaction SHALL NOT be guessed from the install manifest.

#### Scenario: Late identity failure follows two accepted boots

- **WHEN** the CLI reports unavailable installed content identity after both accepted reboots
- **THEN** terminal completion fails and retains the diagnostic even if a receipt or zero exit is also presented
- **AND** no install PASS or replay authority is inferred

#### Scenario: Healthy version lacks content identity

- **WHEN** status appears healthy and running at the requested version but content relation is missing, unavailable or different
- **THEN** the actual root status predicate rejects health
- **AND** the separate current runtime observation remains required after a valid status

# Gate: ./gradlew test --tests ru.voboost.tests.installer.cli.InstallerCliInstallTerminalTest --offline

Gate contract: Maps: Requirement `Installation completion retains independent terminal and content proof`; Scenario `Late identity failure follows two accepted boots`. Preconditions: Current CLI terminal strings and bounded host subprocess transcripts are available. Observable result: Two boots without an exact receipt, foreign/duplicate receipts and late canonical/plain identity failures cannot complete; independent stream draining remains supported. Evidence boundary: Host monitor/subprocess composition only; no guest install or peer authentication is performed.

# Gate: ./gradlew test --tests ru.voboost.tests.installer.cli.InstallerHealthTest --offline

Gate contract: Maps: Requirement `Installation completion retains independent terminal and content proof`; Scenario `Healthy version lacks content identity`. Preconditions: Current root health predicate and typed status variants are available. Observable result: Missing, coerced or different content relation and non-null diagnostics fail despite healthy/version claims. Evidence boundary: Actual root consumer predicate only; production identity transport, peer authentication and guest runtime remain separate gates.


### Requirement: Failed fixture retirement preserves failed acceptance

An explicit retirement registration SHALL import only an existing same-machine/session stop from the retained API journal and correlated lookup with verified cleanup, closed supervisor endpoint and released lock. It SHALL NOT send another stop or any start/reset/product mutation. The durable disposition SHALL remain retired-failed with FAILED acceptance and baseline-required admission. Existing failed-install and unknown-uninstall operation records SHALL remain unchanged. Generic open, resume and product mutation on the old machine SHALL stay blocked; this packet SHALL NOT admit a new baseline or substitute retirement for normal removal or candidate acceptance.

#### Scenario: An operator already stopped the failed fixture

- **WHEN** the existing same-scope stop has actual correlated cleanup proof
- **THEN** registration preserves the product operation bytes and publishes only FAILED retirement with baseline required
- **AND** repeated registration does not resend stop or permit generic product reuse

#### Scenario: Stop proof is absent, unknown or foreign

- **WHEN** no verified matching retained stop can be reconstructed
- **THEN** retirement registration fails without changing the disposition or sending another mutation

# Gate: ./gradlew test --tests ru.voboost.voyemu.FailedFixtureRetirementTest.existingStopRetiresFailureWithoutChangingOperationsOrAllowingOldBaseline --offline

Gate contract: Maps: Requirement `Failed fixture retirement preserves failed acceptance`; Scenario `An operator already stopped the failed fixture`. Preconditions: The host transport fixture retains a failed install, unknown uninstall and a successful operator stop journal. Observable result: Import preserves both operation byte streams, stores FAILED/baseline-required retirement, blocks generic reuse and retirement-handle mutation, and repeated import sends no start or stop. Evidence boundary: Actual root journal/lookup orchestration with a host transport fixture only; no real VM, baseline or product acceptance is established.

# Gate: ./gradlew test --tests ru.voboost.voyemu.FailedFixtureRetirementTest.liveSessionCannotBeRetiredByImportAndDoesNotTriggerStop --offline

Gate contract: Maps: Requirement `Failed fixture retirement preserves failed acceptance`; Scenario `Stop proof is absent, unknown or foreign`. Preconditions: The failed host fixture has no prior stop. Observable result: Import refuses the live session without writing retirement or dispatching stop. Evidence boundary: Host orchestration only; no real guest is contacted or stopped.

# Gate: ./gradlew test --tests ru.voboost.voyemu.FailedFixtureRetirementTest.foreignLookupCannotManufactureRetirementOrReplayStop --offline

Gate contract: Maps: Requirement `Failed fixture retirement preserves failed acceptance`; Scenario `Stop proof is absent, unknown or foreign`. Preconditions: A retained host stop is followed by a lookup with foreign request correlation. Observable result: Reconstruction rejects the lookup, writes no retirement and does not replay stop. Evidence boundary: Host correlation behavior only; no real API or VM is executed.

# Gate: ./gradlew test --tests ru.voboost.voyemu.FailedFixtureRetirementTest.unverifiedCleanupCannotBeImportedOrCauseASecondStop --offline

Gate contract: Maps: Requirement `Failed fixture retirement preserves failed acceptance`; Scenario `Stop proof is absent, unknown or foreign`. Preconditions: The host lookup returns cleanup verified=false for the retained stop. Observable result: Reconstruction refuses unverified cleanup, writes no retirement and sends no second stop. Evidence boundary: Actual cleanup parser and host orchestration only; real resource release remains outside this test.

# Gate: ./gradlew test --tests ru.voboost.voyemu.FailedFixtureRetirementTest.unknownStopLookupNeverDispatchesAnotherStopOrRetiresTheFixture --offline

Gate contract: Maps: Requirement `Failed fixture retirement preserves failed acceptance`; Scenario `Stop proof is absent, unknown or foreign`. Preconditions: The host stop lookup remains unavailable throughout the existing bounded lookup budget. Observable result: Reconstruction fails with no retirement record and no replay of the stop mutation. Evidence boundary: Host timeout/lookup policy only; no real API or VM is executed.

### Requirement: Final acceptance retains its evidence files

The final acceptance verdict SHALL recheck the candidate and registered prerequisite, case and cleanup evidence as regular files without following the final symbolic link. Missing retained files SHALL block a positive final verdict. An observed FAIL SHALL retain precedence. This availability check SHALL NOT establish immutable contents or runtime acceptance.

#### Scenario: A registered proof disappears before the verdict

- **GIVEN** the candidate, prerequisites, cases and cleanup have produced a positive host report
- **WHEN** the candidate or one of the registered evidence files is removed
- **THEN** the final verdict is BLOCKED and fullAcceptance is false

# Gate: ./gradlew test --tests ru.voboost.tests.AcceptancePlanTest.removedRetainedProofPreventsFinalAcceptance

Gate contract: Maps: Requirement `Final acceptance retains its evidence files`; Scenario `A registered proof disappears before the verdict`. Preconditions: Product host sources and local file fixtures are available. Observable result: Removing each candidate, prerequisite, case or cleanup file after a positive report blocks final acceptance. Evidence boundary: Host file availability only; no guest, immutable-content or runtime acceptance proof.

### Requirement: Transaction recovery selects its actual owning executable

I03 SHALL retain the closed seven-case recovery inventory. Generation recovery SHALL select package `voboost-daemon` and binary `voboost-inject`. Each serial suite SHALL require exactly its expected unique successful test results and one matching terminal summary with no failed or ignored tests. A known test prefix MAY contain strict JSON-object diagnostics before a separate exact terminal ok. The existing bounded JSON parser SHALL reject scalar, malformed, duplicate-field and trailing-content diagnostics; diagnostics SHALL NOT replace the terminal outcome. Empty filters, unexpected rows and contradictory summaries SHALL NOT establish transaction recovery acceptance. Host selection/parser tests SHALL NOT substitute for execution of the owning transaction drivers.

I03 SHALL complete the existing owning CAN TX ioctl1/PID metadata policy test before recovery cases and before its PASS receipt. A failed or incomplete process result SHALL stop publication. This is current source-policy composition only; native RX/netlink, NV/property and full native Enforcing readiness SHALL remain outside this narrow proof.

#### Scenario: The split generation recovery selects the injection executable

- **WHEN** the closed I03 recovery commands are constructed
- **THEN** all seven expected cases remain selected and generation recovery uses package voboost-daemon with binary voboost-inject

#### Scenario: A failed required case borrows a passing summary

- **WHEN** a failed required test row appears beside an unrelated passing terminal summary
- **THEN** I03 rejects the output

#### Scenario: A terminal result is empty, ignored or contradictory

- **WHEN** a selected suite emits no tests, ignored results, duplicate rows, unexpected rows or inconsistent summaries
- **THEN** I03 rejects the output

#### Scenario: Production diagnostics interrupt the serial test prefix

- **WHEN** known serial recovery tests emit valid JSON-object diagnostics after their prefixes and then exact terminal ok results
- **THEN** all expected test results and the unique matching summary are accepted

#### Scenario: Diagnostics cannot manufacture a missing test outcome

- **WHEN** an interrupted serial test lacks its terminal ok or emits failed, ignored or invalid diagnostics
- **THEN** I03 rejects it even beside a positive aggregate summary

#### Scenario: Owner policy reports a CAN permission denial

- **WHEN** the owning process returns a captured CAN denial or incomplete success result
- **THEN** the root prerequisite rejects it before any PASS receipt

#### Scenario: The current owner policy completes its narrow checks

- **WHEN** the existing generator, normal C policy and baseline roundtrip controls complete successfully
- **THEN** the standalone root entry retains their evidence without claiming I03 or native readiness

# Gate: ./gradlew test --tests ru.voboost.tests.TransactionRecoverySelectionTest.exactOwningSelectionRetainsAllSevenRecoveryCases

Gate contract: Maps: Requirement `Transaction recovery selects its actual owning executable`; Scenario `The split generation recovery selects the injection executable`. Preconditions: Product host sources and closed I03 inventory are available. Observable result: The command builder selects the exact package/binary and all seven cases. Evidence boundary: Host selection only; Cargo recovery execution remains separate.

# Gate: ./gradlew test --tests ru.voboost.tests.TransactionRecoverySelectionTest.failedRequiredCaseCannotBorrowAnUnrelatedPassingSummary

Gate contract: Maps: Requirement `Transaction recovery selects its actual owning executable`; Scenario `A failed required case borrows a passing summary`. Preconditions: Retained-terminal host fixtures are available. Observable result: A failed required row cannot borrow an unrelated positive summary. Evidence boundary: Host terminal admission only; no production transaction is executed.

# Gate: ./gradlew test --tests ru.voboost.tests.TransactionRecoverySelectionTest.emptyIgnoredDuplicateAndContradictoryTerminalsAreRejected

Gate contract: Maps: Requirement `Transaction recovery selects its actual owning executable`; Scenario `A terminal result is empty, ignored or contradictory`. Preconditions: Closed I03 terminal fixtures are available. Observable result: Empty filters, ignored or failed tests, duplicate or unexpected rows, and inconsistent summaries are rejected for every selected suite. Evidence boundary: Host parser rejection only; owning Cargo execution remains separate.

# Gate: ./gradlew test --tests ru.voboost.tests.TransactionRecoverySelectionTest.retainedSerialOutputAllowsDiagnosticsBeforeTheTerminalOk

Gate contract: Maps: Requirement `Transaction recovery selects its actual owning executable`; Scenario `Production diagnostics interrupt the serial test prefix`. Preconditions: A reduced retained production install stdout fixture is available. Observable result: Valid serial JSON diagnostics preserve the required exact successful terminal outcomes. Evidence boundary: Host parser fixture only; actual owning Cargo execution remains separate.

# Gate: ./gradlew test --tests ru.voboost.tests.TransactionRecoverySelectionTest.serialDiagnosticsCannotReplaceTheTerminalResult

Gate contract: Maps: Requirement `Transaction recovery selects its actual owning executable`; Scenario `Diagnostics cannot manufacture a missing test outcome`. Preconditions: Interrupted serial output fixtures are available. Observable result: Missing, failed and ignored outcomes and invalid diagnostics cannot borrow a positive summary. Evidence boundary: Host rejection only; diagnostics confer no log or runtime authority.

# Gate: ./gradlew test --tests ru.voboost.tests.CanTxPolicyGateTest.capturedCanDenialStopsBeforeAnyPassReceipt --offline

Gate contract: Maps: Requirement `Transaction recovery selects its actual owning executable`; Scenario `Owner policy reports a CAN permission denial`. Preconditions: A retained owning-process failure receipt is available to the Java transport seam. Observable result: CAN denial propagates and no success publication is reached. Evidence boundary: Process-receipt admission only; the product C permission logic is not mocked or rerun by this unit.

# Gate: ./gradlew testCanTxPolicy --tests ru.voboost.tests.delivery.CanTxPolicyTest.requiresActualOwnerCanTxAndPidPolicyGate --offline

Gate contract: Maps: Requirement `Transaction recovery selects its actual owning executable`; Scenario `The current owner policy completes its narrow checks`. Preconditions: Current inject owner sources, existing local C toolchain, libsepol inputs and retained OEM policy fixture are available. Observable result: The actual owner generator and C gate complete normal/baseline roundtrips and retain a full log. Evidence boundary: CAN TX ioctl1/PID metadata and unchanged rights only; no RX/netlink, NV/property, packaged policy binary or full native Enforcing readiness proof.

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

### Unverified: Complete extended functional runtime and visual review

Maps: Requirements `Emulator runtime evidence is fail-closed and non-production`, `Preparation and normal removal retain separate lifecycle evidence`, `Every guest boot has independent prepared-profile evidence`, `Functional observations retain their production source and freshness`, `Root vehicle observations retain actual operation authority`, `Install preparation retains real typed operation receipts`, `Functional fixtures retain ownership and cleanup obligations` and `Startup cases observe actual radio selections and current copy`.

Unavailable observation: Fresh complete twenty-seven-case runtime receipts for the exact new kit, PNG typography/spacing/clipping reviews bound to their actual captures and sources, full OEM icon ownership, all selected startup descriptions and rejected/error branches, actual fixed-key root operations and complete carried restore windows, lawful owned-fixture removal, an actual temporary state-outage retry/cancel window, and exact runtime installation authority preparation.

Why unavailable: The runner now executes ordinary OEM cold/warm actions, parses source-bound copy and fresh logs, and records independent host and runtime subchecks, but source changes and host fixtures do not execute those remaining runtime or visual observations. There is no caller-supplied visual pass flag or diagnostic fault command. The ordinary primary profile proves its actual default-input seeding separately from host coverage of other initial inputs.

Closure evidence: Run the full original and extended case order on one immutable kit and unchanged prepared-profile lease, retain every independent same-boot verification, inspect the actual PNGs against their source and measured layout, and acquire each named remaining production branch through its lawful interface. Missing prerequisites or observations remain unavailable; host coverage is never relabeled as emulator or vehicle proof.

### Unverified: Node-free product tooling

Maps: Requirement `Product Node tooling is transitional` and Scenario `Node tooling removal is evaluated`.

Unavailable observation: Byte-equivalent Rust or dependency-free replacements for the current product evidence validator, target-document validator, generated agent-status tool, Russian formatter, and their complete hostile test corpus.

Why unavailable: The current retained tools and tests still execute through the pinned Node package.

Closure evidence: Run one shared accepted and hostile corpus through the old and replacement tools, require byte-identical generated output and equivalent rejection classes, switch every workflow consumer, then remove `package.json`, `package-lock.json`, `node_modules`, and the superseded TypeScript entrypoints.


### Unverified: Cycle107 live gesture authority

Maps: Requirement `Emulator gestures retain live foreground authority`, Scenarios `An already selected owned Settings tab is observed` and `Foreground changes to Music`. Unavailable observation: Actual helper input, selected-tab repetition and full translated panel observations. Why unavailable: The emulator is stopped and this host-only change does not authorize a device run. Closure evidence: Next authorized immutable emulator kit run with complete panel captures and actual gesture failure captures; host selectors do not establish OEM behavior or the cause of the cycle106 Music transition.

### Unverified: Cycle107 live install acceptance

Maps: Requirement `Intermediate install acceptance consumes complete independent boot proof`, Scenario `Complete first-boot proof precedes consumption`. Unavailable observation: Actual native barrier consumption, monotonic expiry and all prepared checks under Enforcing. Why unavailable: The emulator is stopped and host ordering does not establish real boot or socket behavior. Closure evidence: Next authorized immutable emulator kit run retaining native consumption and complete first-boot verification receipts.

### Unverified: First-boot UI warming runtime

Maps: Requirement `First-boot UI warming retries preserve independent acceptance proof`, Scenarios `Transient deadline completes on the same boot` and `Retry cannot establish first-boot proof`. Unavailable observation: Actual complete same-boot Accessibility tree and PNG after a transient deadline within native acceptance lifetime. Why unavailable: Host mocks do not establish emulator scheduling and this change performs no device run. Closure evidence: Next authorized immutable full runtime cycle retaining exact prepared checks, captures, failed attempts, monotonic timings and acceptance consumption.

### Unverified: Second-screen readiness runtime

Maps: Requirement `Intermediate boot waits for the required second screen`; Scenarios `Second launcher finishes starting` and `Second screen cannot establish readiness`. Unavailable observation: A fresh packaged emulator cycle using the bounded retry and capturing complete same-boot UI. Why unavailable: This change has only host fixture evidence. Closure evidence: Run the immutable kit in Voyemu, retain all attempts and all220-pass result before fresh UI capture and acceptance.

### Unverified: Persistent diagnostic runtime acceptance window

Maps: Requirement `Persistent vehicle diagnostics preserve closed authority and freshness`; all scenarios. Unavailable observation: The 5000-ms freshness bound during actual slow poll/injection windows and successful post-reboot ordinary diagnostics plus complete post-install readiness. Why unavailable: The current stage has only host evidence and performs no guest run. Closure evidence: Raw same-boot diagnostic timings, exact public snapshots, complete post-install readiness and functional OEM readback on one immutable kit. The bound remains unchanged until that evidence exists.


### Unverified: Fresh disable and cancellation runtime acceptance

Maps: Requirement `Agent disable acceptance binds source policy and independent process evidence`; Scenario `A checkbox agent is disabled and re-enabled`. Unavailable observation: Fresh packaged case8 target restart/re-enable and case25 same-boot FEV/PW sleep/wake cancellation observations. Why unavailable: This documentation stage performs host verification without VM or device actions. Closure evidence: Run the exact packaged source/artifact against the approved profile and retain independent process, injection and OEM state evidence; historical supplemental results cannot replace it.

### Unverified: Packaged authenticated secondary capture acceptance

Maps: Requirement `Product secondary capture retains the packaged installer boundary`; Scenario `Typed official capture context reaches the packaged screenshot action`. Unavailable observation: Actual authenticated loopback gRPC secondary capture through the rebuilt packaged installer, same-boot before/after native proof and fresh paired-screen visual review. Why unavailable: Official helper source and host tests do not execute emulator gRPC. Closure evidence: Run the approved official runtime with authenticated controller enabled and retain the packaged capture and visual evidence.


### Unverified: Current timing runtime acceptance

Maps: Requirement `Vehicle timing acceptance binds actual reads to current lifetime` and every Scenario under that requirement. Unavailable observation: Fresh final129 packaged runtime acceptance beyond the named host fixtures. Why unavailable: This documentation stage does not run Android, OEM, device or vehicle workflows; a named fixture proves only its stated boundary. Closure evidence: Execute the owning complete host branches and selected immutable final129 runtime workflow, retaining boot and lifetime identity, actual read/completion records and explicit remaining Settings and guest clock limitations.

### Unverified: Independent boot budget runtime acceptance

Maps: Requirement `Boot readiness and UI capture have independent finite budgets`; Scenarios `Slow complete verification still permits fresh capture` and `Verification timeout cannot become capture proof`. Unavailable observation: Actual same-boot prepared verification, PNG and hierarchy capture followed by native consumption within the original absolute expiry. Why unavailable: Host fixtures do not establish emulator scheduling or native socket consumption. Closure evidence: A fresh immutable packaged emulator run retaining phase timings, complete prepared checks, UI artifacts and correlated native waiting and accepted receipts.

### Unverified: Functional snapshot retry runtime

Maps: Requirement `Read-only functional snapshots retry within unchanged authority`; all scenarios. Unavailable observation: Fresh packaged functional launcher snapshots after a retained deadline with original-boot, Enforcing and lease guards, failed action artifacts, final parsed hierarchy and complete launcher cases. Why unavailable: This isolated host stage does not run devices. Closure evidence: Run the reviewed host gate against the exact installed kit and retain its separate host-source provenance, all attempts and completed functional case reports.


### Unverified: Complete no-images functional runtime acceptance

Maps: Requirement `Full functional acceptance without images remains a separate result`; all scenarios. Unavailable observation: A fresh immutable full twenty-seven-case no-images run with all required functional checks, complete preparation and cleanup, independent semantic boots, final internal eight-owner source admission and external root-owned nine-owner source fence. Why unavailable: Host fixtures and static wiring do not run the emulator. Closure evidence: Execute the exact approved native-session kit with its six-field destructive permit, retain every functional ledger entry and authority receipt, and require `semanticFullAcceptance=true` while `passed=false`, `fullAcceptance=false`, and `visualAcceptance=false`; visual acceptance remains pending independently.


### Unverified: Current startup receipt runtime acceptance

Maps: Requirement `Startup vehicle receipts retain exact current authority`; all scenarios. Unavailable observation: Fresh immutable packaged case27 startup receipts for every enabled vehicle member, the trusted Linux boot-elapsed owner initialization anchor alongside raw daemon starttime, actual same-batch dispatch/adoption order and stable current endpoint/document/lifecycle identity following the dedicated Installer-owned reboot. Why unavailable: Host callback tests and native serializer fixtures use controlled synthetic semantics and do not execute the actual vehicle backend in the emulator or physical car. Closure evidence: Integrate reviewed native, agent and product sources, run their owning gates, build an immutable package and complete the original twenty-seven-case emulator program with current typed startup receipts and explicit numeric receipt intervals. No backend execution SLA or physical early-driving timing is inferred from that report.

### Unverified: Semantic launcher page runtime acceptance

Maps: Requirement `Semantic launcher runtime acceptance remains separate`; Scenario `Runtime launcher drag and reboot acceptance is pending`. Unavailable observation: A fresh immutable runtime query through the installer and root endpoint, exact native primary launcher metrics and captured hierarchy, all-disabled common bootstrap, case 12 drag readback and reboot retention. Why unavailable: This staged source task runs host suites without Android builds, emulator or device access. Closure evidence: Run the approved immutable kit on the named OEM image, retain separately nonced root receipts, independent boot/process/catalog observations and before/after/fresh hierarchies through case 12; complete physical visual acceptance independently.

### Unverified: Complete runner ownership acceptance

Maps: Requirement `One product runner owns the prepared machine`, Scenarios `Product mutation outcome is unknown` and `Another runner selects the same machine`.

Unavailable observation: Complete real-session continuation after an interrupted candidate operation.

Why unavailable: Host lease and unknown-operation regressions pass; they do not execute the current candidate lifecycle in Voyemu.

Closure evidence: Execute the candidate lifecycle and continuation with retained ownership, no replay and verified cleanup.

### Unverified: Complete catalog acceptance

Maps: Requirement `Full acceptance accounts for the candidate catalog`, Scenario `A stable agent has no scenario`.

Unavailable observation: Candidate-specific execution of every required functional and agent target scenario.

Why unavailable: Registry and negative-verdict host tests pass, but required runtime executors are not complete.

Closure evidence: Retain a complete current-candidate report with all required coverage and verified cleanup.

### Unverified: Current startup none acceptance

Maps: Requirement `Startup acceptance observes the selected next boot`, Scenario `None mode takes effect only after reboot`.

Unavailable observation: The current candidate's actual radio mutation, deferred stop, new-boot process absence and computer-only recovery.

Why unavailable: Implementation and host protocol regressions do not execute OEM input or the rebuilt package.

Closure evidence: A current-candidate owned emulator run retaining fresh boot, process, persisted mode, live publisher and Settings observations plus verified cleanup.


### Unverified: Current startup hidden acceptance

Maps: Requirement `Startup acceptance observes the selected next boot`, Scenario `Hidden mode returns through the actual TBOX gesture`.

Unavailable observation: Current-candidate hidden startup, enabled running target states and live TBOX restoration.

Why unavailable: Host protocol tests do not execute the native UI input or the prepared Android and QNX targets.

Closure evidence: A current-candidate owned run with complete tab absence, current target and process identities, one durably reserved five-tap request and actual same-boot live recovery followed by verified cleanup.



### Unverified: Current packaged native GUI acceptance

Maps: Requirement `Full acceptance accounts for the candidate catalog`; Scenario `A native GUI subset lacks complete acceptance`.

Unavailable observation: Current packaged application launch, accessibility permissions, real window identity and GUI/device effects.

Why unavailable: Host bridge and runner tests do not launch the application or prove native accessibility permission, backend effects or runtime ownership.

Closure evidence: A coordinated signed candidate run on one retained owned machine, with verified application identity, scoped native windows/actions, independent backend readback and terminal cleanup without replay.

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


### Unverified: Complete native state outage runtime

Maps: Requirement `State outage acceptance binds actual runtime controllers`, Scenarios `A diagnostic names a foreign or replaced controller`, `An incomplete export hides a retry`, `A complete asleep display sample has false readiness` and `PW is enabled after an explicit clean stop`.

Unavailable observation: One current-candidate run of all four native PW read/write recovery/cancellation programs and both FEV/PW disabled same-boot sleep/wake counterfactuals with normal state restoration.

Why unavailable: Host fixtures exercise receipt admission and program ordering; the selected candidate has not executed the six actual programs on either prepared target.

Closure evidence: Retain exact native receipts, current writer/process/input/root observations, actual OEM/CAN warm-cycle states and normal restoration for all six required programs.

