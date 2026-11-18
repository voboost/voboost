# Voboost product

## Project overview

This repository owns the complete Voboost product release, not an Android component.

It contains:

- user and developer documentation for the complete system;
- one Product SemVer source definition with exact component commits;
- canonical `voboost.json` and channel schemas;
- release composition, signing-role separation, verification, evidence admission, and publication policy;
- cross-repository beta workflows.

Production implementation remains in the sibling repositories:

- `voboost-app` owns the unprivileged Android OTA courier;
- `voboost-agents` owns agent Java, DEX, catalog, reflection support, and OEM stubs;
- `voboost-inject` owns privileged native installation, daemon, injection, update, restore, and uninstall;
- `voboost-installer` owns offline desktop and Linux packages.

Every product change receives one new Product SemVer and one complete signed `voboost.json`. Unchanged artifact bytes retain their SHA-256 and are not transferred again. An app, agent, or native change is never published as an independently versioned product update.

## OpenSpec workflow

Permanent truth lives under `openspec/specs`. Before the first release, verified baseline architecture is written directly to the permanent specifications rather than represented as a historical change.

Validation:

```bash
npm ci --ignore-scripts
npm run check
```

Host, emulator, device, vehicle, CI, and publication evidence are distinct. A result at one boundary never establishes another boundary.
