# Why

The license repository was retired by the project owner. CI and source construction already use the active component set, but the source definition still requires a pin to the archived repository.

# What Changes

Require exactly app, agents, inject, installer and logo component pins. Reject the retired license key and its unused build-directory override. Preserve all existing version, gate, active pin, signing and publication validation. Do not add a migration or alter runtime protocols.

# Impact

Product source definition, source-build configuration and focused tests only. Frozen older build inputs and DMG318 are not rewritten. Runtime acceptance remains separate.
