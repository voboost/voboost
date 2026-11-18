## ADDED Requirements

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

# Unverified: Complete catalog acceptance

### Requirement: Startup acceptance observes the selected next boot

Startup acceptance SHALL select the actual Settings radio, independently confirm the persisted mode without an immediate product stop, and verify the resulting owned boot. The none scenario SHALL prove fresh daemon and APK process absence and restore interface mode only through the packaged computer installer. Unknown mutation outcomes SHALL retain ownership without replay.

#### Scenario: None mode takes effect only after reboot

- **WHEN** the runner selects none through the current observed Settings radio
- **THEN** it confirms same-boot persistence and a still-live product before reboot, observes no Voboost processes after the new boot, and verifies packaged-installer recovery and the live Settings tab

#### Scenario: Hidden mode returns through the actual TBOX gesture

- **WHEN** the runner selects hidden through Settings and verifies its next owned boot
- **THEN** it verifies a complete Settings observation without the tab, independently checks enabled running targets, sends one fixed five-tap gesture to the observed stock TBOX row, and confirms live interface recovery without restarting the observed processes

# Unverified: Current startup hidden acceptance

# Unverified: Current startup none acceptance

## MODIFIED Requirements

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


## Unverified

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

### Unverified: Complete native state outage runtime

Maps: Requirement `State outage acceptance binds actual runtime controllers`, Scenarios `A diagnostic names a foreign or replaced controller`, `An incomplete export hides a retry`, `A complete asleep display sample has false readiness` and `PW is enabled after an explicit clean stop`.

Unavailable observation: One current-candidate run of all four native PW read/write recovery/cancellation programs and both FEV/PW disabled same-boot sleep/wake counterfactuals with normal state restoration.

Why unavailable: Source-shaped host fixtures exercise admission and ordering, but the current candidate has not executed the six actual programs on either prepared target.

Closure evidence: Retain exact native receipts, independent current writer/process/input/root observations, actual OEM/CAN warm-cycle states and normal restoration for every required program on the selected candidate.

