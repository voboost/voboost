# Continuous integration

**English** | [Русский](ci.ru.md)

Component CI verifies exact source without product private keys. Product CI owns cross-repository composition; source push, CI, publication decision, GitHub Release, and channel update remain distinct steps.

## Required source inputs

One source definition pins full commits for `voboost-app`, `voboost-agents`, `voboost-inject`, `voboost-installer`, and `voboost-logo`. The tagged `voboost` revision supplies product schemas, docs, release tooling, and the definition itself.

Each component exposes a non-interactive host check and final release-build entry point. Key-bearing jobs consume only verified request-bound inputs and protected-main tooling; pull-request source does not execute after secret admission.

## Product DAG

1. Build one local ephemeral beta1 set.
2. Run the local vehicle gate over those exact bytes.
3. Perform verifiable uninstall and record clean state.
4. Plan repository commits against the passed local bytes.
5. Commit and push source; do not call this a product release.
6. Run beta1 CI from the exact pushed SHAs and trusted local delivery evidence: secretless preparation, protected signing, then secretless final verification.
7. Download exact CI artifact IDs and verify their hashes.
8. Run a clean vehicle gate over the CI bytes, then verifiable uninstall and clean-state verification.
9. Make a separate publication decision; if accepted, publish the same CI bytes without rebuild or re-sign.

Source push is forbidden until step 3 passes. Stable promotion reuses verified bytes and never rebuilds them. A missing gate remains missing evidence; source push or component CI success does not imply product publication readiness.

## Evidence and publication

`beta1-evidence-ingest.yml` is the trusted producer for local and CI-vehicle evidence. Its public root is pinned in reviewed product source. It verifies a canonical user-machine payload and raw Ed25519 signature, full sorted artifact inventory, deterministic aggregate, chained receipts, and its own immutable producer identity. CI admission compares exact product/component commit trees with the snapshots that passed the local gates; CI evidence ingestion reconstructs the inventory from downloaded final artifacts.

Every workflow that checks generated agent documentation (`beta1-ci.yml`, `verify.yml`, and `sign-dev-vithar.yml`) resolves `agents` from the exact full commit in the source definition, checks it out into an isolated path with credentials disabled, and verifies clean HEAD before setting explicit release-mode root/commit inputs. The generator reads metadata, paired documentation, and OpenSpec only; it never executes candidate agent code. A sibling-directory default exists only for local development and cannot validate or author release evidence.

`beta1-ci.yml` builds the exact pushed SHAs. Read-only jobs produce immutable unsigned inputs. Protected jobs execute pinned product release tooling and require every derived signing identity to match the source-pinned role-separated trust document. A later secretless job re-downloads the final closure and independently verifies the APK certificate, agent catalog, role-bound native executables, restore authority, product signatures, consumer-embedded roots, manifest, and signed product/workflow/definition/trust identity.

After trusted clean-vehicle evidence, publication re-downloads admitted bytes outside a clean checkout and recomputes archive and payload hashes and closed inventory immediately before mutation. It resumes only a matching draft, never overwrites a mismatched asset, and publishes the signed channel pointer last.

## Related pages

- [Build](build.md)
- [Release](release.md)
- [Testing](testing.md)
- [Security](security.md)

Current code license authority is voboost/LICENSE. The old license source pin is retained as historical metadata only; the archive is not a build or CI input. Logo licensing remains separate.
