## Contract

- [x] Record the accepted single-store, single-transaction design and bounded product delta.
- [x] Strictly validate the delta and apply it to current product requirements before runtime integration.

## Implementation and evidence

- [ ] Implement colocated owner schema, semantic command, ordering and durability tests with exact named evidence.
- [ ] Integrate input, owner, projection, product rollback, revocation and terminal uninstall paths without a second desired writer.
- [ ] Add and execute product L2 owner IPC, crash-cut, lease and stale-work scenarios using shared product primitives.
- [ ] Add product L3 Settings, restart and reboot scenarios; execute only after the user releases the emulator pause.
- [ ] Reconcile each requirement and scenario with executed named tests; keep missing boundaries explicitly Unverified.
- [ ] Complete independent review, documentation alignment and required checks, then archive and strictly revalidate.
