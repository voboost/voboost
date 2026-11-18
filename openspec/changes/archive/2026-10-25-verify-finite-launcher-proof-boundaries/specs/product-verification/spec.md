## ADDED Requirements

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
