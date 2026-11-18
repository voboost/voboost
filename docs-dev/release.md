# Product release

**English** | [Русский](release.ru.md)

Only the `voboost` product repository creates a Voboost Git tag, GitHub Release, or signed channel pointer. Component workflows build and verify inputs but do not publish user-facing component releases.

## Source definition

`release/<release-id>.toml` binds one canonical Product SemVer and channel to full commits of app, agents, inject, installer, license, and logo; the exact Android `versionCode`; positive compatibility values; required host/emulator/device/vehicle gates; and an optional older base release. It has no manual build/reuse mode. Beta1 pins `android.versionCode = 1000008`.

## Composition

1. Check out every full commit into an independent clean worktree.
2. Run the component-owned check and release-build entry points.
3. Extract package, version-code, and signer identity from the exact signed APK through pinned Android tools; compare any retained evidence output.
4. Compare every final component artifact with the verified base release.
5. Reuse equal bytes and reject changed bytes at an unchanged owner commit.
6. Author `restore.json` from release identity, exact source-definition digest, and the canonical restorable closure excluding the restore pair; verify its raw Ed25519 signature.
7. Verify the agent catalog and target DEX closure.
8. Author canonical `voboost.json` with mandatory APK package/version/certificate identity.
9. Sign exact manifest and product identity bytes last, then pass one immutable directory to installer packagers.

The local flat Rust crate implements definition validation, bounded pinned-tool APK inspection, compare-only evidence output, restore producer/verifier, manifest/channel/restore schema and semantic parsing, closure hashing, automatic byte decisions, local publication planning, and checksums. It performs no checkout, Git tag, upload, or publication. Restore signing runs only when an external raw private-key file is explicitly supplied; no production key is stored in the repository.

`release/build-local-beta1.sh` defines one local ephemeral build session: freeze source snapshots, validate and reuse one explicitly selected retained local authority, invoke the app and native adapters, build the macOS installer from the same roots, sign and reopen closure/package bytes, persist only public identities and complete hashes, then confirm unchanged trees after the output copy. It does not select an authority by glob or generate replacement keys. Output below source, temp, build/cache, or a symlink alias is rejected before the build. The product verifier distinguishes the exact 67-file artifact directory from the signed 69-file closure and never permits undeclared extras. The eight-argument `build-mac-dmg` interface verifies a separate corresponding-source directory containing `corresponding-source.tar.gz`, `corresponding-source.json`, and `BUILD-MODIFIED.md` before packaging. The local output retains these files in `voboost-corresponding-source-<release>` beside the DMG. The DMG contains no source archives or source trees; readable licenses and notices remain in the application.

## Protected publication

Secretless CI preparation produces unsigned immutable component inputs. Protected signing jobs apply Android, agents, native executable, restore, and product roles; a later secretless job re-downloads and verifies the signed closure. Three secretless post-closure jobs build the exact installer commit for Windows x86-64, Linux x86-64, and universal macOS, scan embedded roots with product-owned tooling, reopen every package, and aggregate the exact EXE/DMG/tar.bz2 set.

After trusted clean vehicle evidence for exact successful CI artifact IDs/digests/payload hashes, the protected publication workflow checks out the exact product commit, proves it clean, downloads release bytes outside the checkout, and creates or resumes the exact tag and draft through authenticated GitHub APIs. Existing assets are compared, missing assets are uploaded, and the full draft is downloaded again before publication. It never rebuilds, repackages, or re-signs release bytes.

The beta1 sequence is local ephemeral build, local vehicle gate, verifiable uninstall/clean state, commit planning, source commit/push, exact-SHA CI, exact CI artifact download, clean CI vehicle gate with another uninstall/clean-state receipt, and only then a separate publication decision. Source push is not a product release and is forbidden before the local vehicle sequence passes.

Trusted evidence ingestion binds signed user-machine receipts to snapshot Git trees or exact CI artifact identity plus the product commit, CI workflow blob, and source-definition digest. Publication requires that evidence. The channel pointer is updated only after immutable release assets are public and have been downloaded and verified again.

## Related pages

- [Build](build.md)
- [Updates](updates.md)
- [Security](security.md)
- [Testing](testing.md)
- [`release/` contract](../release/README.md)
