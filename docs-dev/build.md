# Builds

**English** | [Русский](build.ru.md)

Each production repository owns its toolchain, lockfiles, canonical checks, and final release build. `voboost/release` pins exact commits and invokes documented entry points; it does not reproduce component build steps.

## Component entry points

| Repository | Host check | Release output |
| --- | --- | --- |
| `voboost-app` | root Gradle `check` | `voboost-app.apk` from `buildRelease` |
| `voboost-agents` | root Gradle `check` | `agents.json`, signature, `agent-common.dex`, target `agent-*.dex` from `buildRelease` |
| `voboost-inject` | Cargo/Make checks | `voboost-daemon`, system programs, flat ABI libraries, init and SELinux inputs |
| `voboost-installer` | frontend and Rust checks | platform-specific package from one verified product closure |
| `voboost` | Cargo release tests and independent docs checks | canonical schemas, manifest, channel document, checksums and publication evidence |

`voboost-app` and `voboost-agents` contain no Node package. OpenSpec is supplied by the pinned CI environment. Documentation formatting is supplied by `voboost-codestyle`; site rendering belongs to `voboost-site`.

## Final bytes

The product compositor receives final component bytes, agent catalog evidence, and the source definition. It independently runs pinned Android tools over the exact signed APK, requires `versionCode` to equal the source-pinned `android.versionCode`, and hashes the same file; any retained APK evidence is only a compare-only audit output. Equal component bytes are reused; unexpected different bytes at the same owning commit stop the release.

`restore.json` binds Product SemVer, exact source-definition digest, and the canonical restorable-closure digest excluding the restore pair. Its raw Ed25519 signature is verified before manifest composition. Installer packages are rebuilt because they contain the new manifest, release identity, and license binding.

## Reproducibility boundary

A component host build proves only the named build. The app adapter performs two clean minified builds at the same commit and toolchain identity and requires byte-identical unsigned APKs. Protected signing then creates one exact signed APK; pinned Android tools extract and bind its package, `versionCode`, signer certificate, size, and SHA-256 before `voboost.json` is authored. No release byte is rebuilt or re-signed after the vehicle gate.

## Related pages

- [Repositories](repository.md)
- [Release](release.md)
- [Testing](testing.md)
- [Security](security.md)
