# Canonical runtime Installer device selection

## Why

Product acceptance consumers still construct the retired --device-serial flag. The current user contract requires a single --device flag and excludes a legacy alias or separate server-port option.

## What changes

Update fixed Installer invocations, exact proof guards and current host fixtures together. Preserve existing selected-serial configuration and result schemas. No protocol or transport-port fields are added.

## Impact

Product host acceptance tooling, tests and product-verification contract. No emulator execution, component build, packaging or publication.
