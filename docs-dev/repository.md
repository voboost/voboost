# Repositories and sources

**English** | [Русский](repository.ru.md)

Voboost uses separate repositories with one owner for each source boundary. A product release definition in `voboost/release` pins full commits; no component imports another repository through a relative production-source dependency.

| Repository | Source ownership |
| --- | --- |
| [`voboost`](..) | Product SemVer, `voboost.json`, `channel.json`, release composition, `docs-user`, `docs-dev`, plans |
| [`voboost-app`](../../voboost-app) | Headless Android OTA courier and APK self-update |
| [`voboost-agents`](../../voboost-agents) | Java agent runtime, DEX/catalog generation, OEM stubs |
| [`voboost-inject`](../../voboost-inject) | Root daemon, injector, implant, install/restore/uninstall, SELinux, shared contract crate |
| [`voboost-installer`](../../voboost-installer) | Offline Windows/macOS/Linux installer UX and ADB transport |
| `voboost-site` | Four documentation targets from one tagged product checkout |
| `voboost-license` | Historical license archive; not a release input |
| `voboost-logo` | Brand source assets |
| `voboost-codestyle` | Shared writing and source-format rules |

## Target layouts

`voboost-app` has root `src/` and one Android application. It has no `app/`, agent project, or Node package.

`voboost-agents` has root `src/main/java/ru/voboost/agents` and `ru/voboost/reflect`, plus Android fixture subprojects under `stubs/`. It has no top-level `agents/`, `reflect/`, `ui/`, `ui-res/`, Node package, or resource-APK production pipeline.

`voboost-inject` keeps `daemon/`, `injector/`, `implant/`, system-program directories, `selinux/`, and `shared/`. Cross-binary contracts are flat modules in [`contract`](../../voboost-inject/contract); the daemon package and binary are `voboost-daemon`.

`voboost` contains product documentation, release definitions and tooling, global schemas, product OpenSpec, and plans. Android, agent, installer, and native production sources remain in their owning repositories.

## Source-link policy

Developer pages link to a concrete file or an existing source directory in the sibling checkout. A directory link is valid without an artificial `index.md`. Site publication converts repository-relative source links to immutable repository URLs from the exact product tag; it does not copy source directories into documentation output.

## Build boundary

Each production repository exposes its own `check` and `build-release` equivalent. Product release tooling invokes documented entry points at exact commits, consumes only final artifacts and evidence, and never reproduces Gradle, D8, NDK, Cargo, or installer-internal build steps.

## Related pages

- [Architecture](architecture.md)
- [Build](build.md)
- [Contracts](contracts.md)
- [Release](release.md)
