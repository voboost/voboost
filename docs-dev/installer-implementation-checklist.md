# Implementation checklist

**English** | [Русский](installer-implementation-checklist.ru.md)

## Contract first

- Change the permanent specification or active delta before changing behavior
- Give every Scenario an exact named Gate or a complete `Unverified` entry with Maps, reason, and closure method
- Do not present a build, lint, suite, or source scan as a behavioral Gate

## Security and data flow

- Verify signatures, canonical bytes, size, SHA-256, role, and closed inventory before use
- Do not accept commands, device paths, phases, or privileged-program arguments from the GUI
- Hold the per-device lock throughout the operation, publish temporary files atomically, and recover fail-closed
- Do not write secrets, tokens, license content, or unnecessary personal data to logs

## Frontend

- Make loading, error, empty, and cancellation states explicit
- Provide EN and RU variants for all user-facing strings and preserve placeholders
- Do not report success before terminal verification

## Tests and documentation

- Add focused regression tests for normal and hostile paths
- Keep EN and RU documentation, glossaries, OpenSpec, and actual commands in sync
- Do not edit architecture documentation or plans mechanically to satisfy parity

## Local gates

Run formatting, lint, TypeScript, Vitest, Rust fmt/clippy/tests, build, strict OpenSpec, documentation/parity/forbidden checks, and `git diff --check`. Record exact results and the boundaries they do not prove.

## Delivery evidence

Record source/host, CI/signing, published bytes, packaged GUI, device/reboot/SELinux, and vehicle evidence separately. Evidence from one layer does not replace another.
