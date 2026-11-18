# Full beta1 functional gate without images

This optional runner executes the original 27 full case programs in their original order, including preparation removal, two CLI installations, normal CLI removal, genuine Settings removal, state outage and error-copy fault cases, and every intermediate boot observation. Owning host checks run once before runtime admission. Installed-kit subset receipts cannot replace this run.

Use the same absolute config accepted by `emulator-gate.ts`, an exclusively owned native Voyemu session, and a separate coordinator-approved permit file. The permit is deliberately separate from a regression selector: this run installs and removes software and may delete its configuration.

```sh
node --experimental-strip-types release/emulator-full-semantic.ts /absolute/config.json /absolute/full-run-permit.json
```

The permit contains exactly `schemaVersion: 1`, `operation: "full27-install-uninstall"`, `packageSha256`, `dmgSha256`, `sessionReceiptSha256`, and `sourceReceiptSha256`. Package identity uses the existing gate inventory algorithm (the complete app inventory and top-level CLI symlink); the other values are SHA-256 of the exact DMG, native ownership receipt, and constructor `receipt.json`. Preserve the private native ownership receipt; never publish its contents. The coordinator must additionally retain its full source fence, including auxiliary components absent from the eight-owner constructor receipt.

A caller cannot select a case subset or inject a visual reviewer. The runner refuses image dispatch at both native and installer adapters. All image-only checks and visual boot states remain `unavailable`; no synthetic images, visual passes or assumptions about appearance are emitted. Functional boot acceptance still requires the existing independent readiness, boot identity, Enforcing and fresh hierarchy proofs before an installer ACK.

`emulator-runtime-evidence.json` retains the complete ledger. `full-semantic-summary.json` has `semanticFullAcceptance: true` only when all 27 case programs completed, every mandatory named functional check passed exactly once, all additional functional checks passed, all independent boot proofs passed, fixture cleanup completed, no writer remains, and exact constructor sources still match. Source admission runs before device access and again at termination. A missing, failed or unavailable functional observation is a failed semantic acceptance.

Exit 0 means full functional acceptance only. `passed`, `fullAcceptance` and `visualAcceptance` remain false and visual acceptance remains pending. Normal visual `emulator-gate.ts` behavior is unchanged. CI can invoke this runner as an optional functional job on an explicitly owned emulator; it must consume `semanticFullAcceptance`, and must not publish this receipt as visual or real-vehicle release acceptance.
