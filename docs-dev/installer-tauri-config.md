# Tauri configuration

**English** | [Русский](installer-tauri-config.ru.md)

## Source of truth

The canonical application configuration is `src-tauri/tauri.conf.json`, permissions are in `src-tauri/permissions/app.toml`, and capabilities are in `src-tauri/capabilities/default.json`. Do not edit generated schemas manually. Permission changes must be minimal and accompanied by a test of the command that uses them.

## GUI window lifecycle

The installer uses one main window with a fixed screen sequence. Closing the window during an operation initiates controlled cancellation but is not a terminal device result. A restart must reconstruct trusted state instead of resuming a UI phase from memory.

## Bundled resources

The bundle includes only resources named by the build pipeline: platform ADB, localized content, and verified release inputs. Staging verifies existence, type, size, and digest; unrelated working-tree files must not enter the package.

## TypeScript build tools

Build scripts live in `src/build`, use shared helpers, and run through `package.json` scripts. They must work from a clean checkout, use canonical paths, and not depend on globally installed packages.

## Development and release signing

Local development uses only explicitly permitted development roots. Release keys are available to separate protected environments and never enter the frontend or publisher. Successful local signing is not evidence of production signing.

## Rust toolchain

Rust dependencies are pinned by `Cargo.lock`; `cargo fmt`, clippy with warnings denied, and tests are mandatory. Platform-specific code must have honest tests or an `Unverified` entry and must not promise unsupported subprocess-tree semantics.
