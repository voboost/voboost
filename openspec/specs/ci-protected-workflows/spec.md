# Protected CI workflows

## Purpose

Define the beta1 cross-repository signing, evidence, and publication boundaries without retaining the superseded per-component publication flow.

## Requirements

### Requirement: Beta1 CI separates preparation, signing, verification, and packaging

The beta1 workflow SHALL admit exact full commits from the product source definition, prepare app, agent, native, and installer inputs without private keys, expose each private signing role only to its dedicated protected environment, and perform final closure verification and platform packaging without signing secrets. Windows, Linux, and macOS packages SHALL contain the same final signed product closure. The workflow SHALL NOT publish a tag, release, package, or channel pointer.

#### Scenario: Exact beta1 sources enter protected CI

- **WHEN** remote source execution is explicitly enabled and every dispatch commit equals the source definition
- **THEN** secretless preparation precedes role-separated signing, final verification, and three-platform packaging without publication authority

# Gate: node --test --test-name-pattern="beta1 CI separates prepared inputs, protected signing, and secretless final verification" tools/tests/beta1-product-workflow.test.ts

Gate contract: Maps: Requirement `Beta1 CI separates preparation, signing, verification, and packaging`; Scenario `Exact beta1 sources enter protected CI`. Preconditions: The beta1 workflow and source definition are available. Observable result: The named host test requires exact source admission, secretless component preparation, isolated signing roles, secretless final verification, Windows/macOS/Linux package construction, and an exact aggregate inventory without publication commands. Evidence boundary: Static workflow contract only; hosted runners, protected environments, secrets, package bytes, and a real CI run remain unverified.

### Requirement: Vehicle evidence ingestion is separate from build CI

Local and CI-vehicle evidence SHALL enter only through the manual evidence-ingestion workflow at the exact product commit on protected `main`. Ingestion SHALL verify the source-pinned evidence public root, canonical signed payload, producer identity, artifact set, source identities, receipt chain, and evidence kind before publishing the immutable evidence artifact. Beta1 build jobs SHALL NOT manufacture or claim vehicle evidence.

#### Scenario: Signed evidence is ingested

- **WHEN** the manual ingestion workflow receives a local or CI-vehicle payload
- **THEN** it accepts only the exact signed canonical evidence bound to the product workflow and source identities

# Gate: node --test --test-name-pattern="evidence ingestion verifies signed canonical payloads on exact product main" tools/tests/beta1-product-workflow.test.ts

Gate contract: Maps: Requirement `Vehicle evidence ingestion is separate from build CI`; Scenario `Signed evidence is ingested`. Preconditions: The ingestion workflow, source-pinned trust document, and evidence validator are available. Observable result: The named host test requires manual dispatch, protected-main identity, signature verification before producer wrapping, exact evidence kind, and immutable artifact output, and rejects build-created vehicle evidence. Evidence boundary: Static workflow policy only; user-machine signing, GitHub main protection, workflow execution, and physical vehicle evidence are not exercised.

### Requirement: Publication is a separate disabled-by-default decision

The beta1 publication workflow SHALL be manually dispatched only after an exact successful beta1 CI run and matching trusted CI-vehicle, uninstall, and clean-state evidence. It SHALL reuse the admitted CI bytes without rebuilding or resigning, keep write permission in the publication job only, and remain disabled unless the explicit publication decision is true. Channel mutation SHALL remain separately gated.

#### Scenario: Publication prerequisites are absent

- **WHEN** CI artifact identity, trusted clean vehicle evidence, or the explicit publication decision is absent
- **THEN** tag, release, asset, and channel mutation remain unreachable

# Gate: node --test --test-name-pattern="publication stays behind manual protected evidence gates and is disabled by default" tools/tests/beta1-product-workflow.test.ts

Gate contract: Maps: Requirement `Publication is a separate disabled-by-default decision`; Scenario `Publication prerequisites are absent`. Preconditions: The beta1 publication workflow is available with its default inputs. Observable result: The named host test requires exact CI/product/artifact/evidence admission, no rebuild or resign step, isolated write permission, explicit default-disabled publication, and a separate channel gate. Evidence boundary: Static fail-closed publication policy only; no Git tag, GitHub Release, asset upload, channel mutation, or vehicle operation occurs.

### Requirement: Product workflows contain no per-component publication pipeline

The product repository SHALL NOT build, sign, tag, publish, or emit terminal publication events for an independently versioned app, agent, or native component. Every component change SHALL enter a new complete product SemVer and the product-owned beta workflow. Superseded `dev-vithar` component coordinator and application publication workflows and their private-role tools SHALL be absent.

#### Scenario: Product workflow inventory is inspected

- **WHEN** repository layout verification enumerates every workflow and product tool
- **THEN** only ordinary product verification, beta1 build, evidence ingestion, and separately gated beta1 publication remain

# Gate: node --test --test-name-pattern="product repository contains no component publication workflow" tools/tests/product-repository-layout.test.ts

Gate contract: Maps: Requirement `Product workflows contain no per-component publication pipeline`; Scenario `Product workflow inventory is inspected`. Preconditions: The complete product workflow and tool inventory is available. Observable result: The named host test requires the four product workflows and rejects the old component coordinator, component signing workflow, APK channel authoring, app publication ledger, and installer-event allowlist. Evidence boundary: Repository layout only; it does not inspect sibling repository legacy workflows or execute GitHub.

### Requirement: Third-party actions are pinned

Every third-party action in retained beta1 product workflows SHALL use an immutable full commit rather than a mutable major tag.

#### Scenario: A retained workflow changes an action

- **WHEN** the complete retained workflow set is inspected
- **THEN** a mutable action reference is rejected

# Gate: node --test --test-name-pattern="all third-party actions in beta1 workflows are pinned to immutable commits" tools/tests/beta1-product-workflow.test.ts

Gate contract: Maps: Requirement `Third-party actions are pinned`; Scenario `A retained workflow changes an action`. Preconditions: All retained beta1 workflow files are available. Observable result: The named host test enumerates them and rejects a third-party action not pinned to a full immutable commit. Evidence boundary: Static workflow source only; upstream repository integrity and runner execution remain outside the gate.

### Requirement: Protected environment policy remains external evidence

Repository source SHALL NOT claim that GitHub environment reviewers, branch protection, secret inventory, hosted-runner isolation, or network policy are configured merely because workflow YAML names those controls.

#### Scenario: Repository workflow gates pass

- **WHEN** every host workflow-policy test succeeds without querying the GitHub control plane
- **THEN** operational protection remains unverified

# Unverified: Protected CI operational isolation

## Unverified

### Unverified: Protected CI operational isolation

Maps: Requirement `Protected environment policy remains external evidence` and Scenario `Repository workflow gates pass`.

Unavailable observation: Actual GitHub environment reviewers, branch and tag rules, secret inventory, token scope enforcement, hosted-runner administration, network isolation, and same-key concurrency behavior.

Why unavailable: Repository tests inspect source and execute local validators but cannot observe the GitHub control plane or remote runner environment.

Closure evidence: Query and retain protected-environment configuration, repository rules, secret-name inventory, token permission observations, and one controlled beta1 workflow execution without exposing secret values.
