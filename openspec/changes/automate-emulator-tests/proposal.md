# Automate emulator tests

## Why

Release testing needs repeatable Gradle entry points for machine preparation, session startup, and test execution. Installer and agent cases use different guest states and can share the same candidate when they run independently.

## What Changes

- Expose `emulatorPrepare`, `emulatorStart`, `emulatorTest`, `emulatorStop`, and `emulatorCheck`.
- Assign installer and agent roles to configured Voyemu machines. Run both roles concurrently on distinct machines and sequentially on one machine.
- Use one verified, read-only candidate mount during a combined run.

## Impact

- Root Gradle tasks, product test drivers, local machine configuration, and developer documentation.
