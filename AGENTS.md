# Inheritance

- Follow [common Voboost rules](../voboost-codestyle/AGENTS.md).
- Use the [common Russian glossary](GLOSSARY.ru.md) for Russian prose.

# Ownership

- This repository owns product version/source definitions, release composition and verification, cross-component fixtures, product OpenSpec, and user/developer documentation.
- `release/` is a standalone Rust crate with `main.rs` beside `Cargo.toml`; schemas, source definitions and release scripts live there. Root `package.json` owns Node-based evidence and documentation tooling.
- Component production sources and builds belong to sibling repositories: [app](../voboost-app/AGENTS.md), [agents](../voboost-agents/AGENTS.md), [inject](../voboost-inject/AGENTS.md), and [installer](../voboost-installer/AGENTS.md). Read their rules when crossing those boundaries. The root Gradle project owns product orchestration and host test entry points; it has no `app/`, `agents/`, `reflect/`, or `ui/` modules.
- Start with [repository ownership](docs-dev/repository.ru.md), [contracts](docs-dev/contracts.ru.md), and [release tooling](release/README.md). Verify executable claims against the current implementation; historical plans are evidence, not instructions.

# Release inputs and artifacts

- Source definitions in `release/` own product SemVer, exact component commits, Android versionCode, compatibility and required gates. Do not introduce independent component release identities or manual build/reuse decisions.
- Compose from final artifact bytes and verified inputs. Source revision, built artifact identity and runtime evidence are separate claims; a hash or signature alone does not prove fresh construction or vehicle readiness.
- `release/fixtures/wire/` is the product-owned wire corpus. Use the documented generator and synchronization command for intentional changes; keep consumers byte-identical rather than editing mirrors independently.
- Keep private signing material out of source, reports and artifacts. Follow the explicit authority-directory contract in `release/README.md`; never create or replace signing authorities as an incidental verification step.
- Local release commands, component builds, device gates and remote publication are distinct operations. Do not infer commit, push, signing, device or publication authorization from a documentation or research task.

# Documentation and research

- User documentation lives in `docs-user/`, developer documentation in `docs-dev/`; keep paired English/Russian pages aligned. Apply the common documentation skill and link to owning sources instead of duplicating their architecture in these instructions.
- Behavioral changes use the existing OpenSpec change flow; see [contributing](docs-dev/contributing.ru.md). Validate requirements and their named gates without weakening criteria to match an implementation.
- Save product-specific plans and reports outside source repositories in timestamped `../plans/YYYY-MM-DD/YYYY-MM-DD-HH-mm-*.md`; component-specific reusable files belong in the owning project. Preserve one-off research code and results as Markdown examples. Do not use the workspace's `local-builds` as a research working directory or introduce research worktrees.
- Keep the shared architecture review as a short question/evidence index plus links to project reports. Record source-supported, host-reproduced and Android/vehicle-verified results separately, with remaining gates; do not put session history in AGENTS.md.

# Verification commands

- Use the Node/npm versions declared in `package.json`; pinned CI toolchain details live in `.github/workflows/verify.yml`.
- `npm run check`: product evidence validation, documentation checks, Node tests and strict OpenSpec validation.
- `npm run docs:check`: targeted documentation validation and structure tests.
- `npm run openspec:validate`: strict validation of all product OpenSpec artifacts.
- `cargo test --manifest-path release/Cargo.toml --locked`: release-tooling host suite. The separate Android-tools integration gate and its prerequisites are documented in `release/README.md`.
- `cargo run --manifest-path release/Cargo.toml -- check-definition release/1.0.0-beta1.toml`: validate the beta1 source definition.
- `cargo run --manifest-path release/Cargo.toml -- verify-wire-fixtures release/fixtures/wire`: verify the product wire corpus.
- Select checks appropriate to the change. These commands document entry points, not permission to run builds, mutate artifacts or contact devices during read-only work.

# Evidence

- Follow [testing and evidence](docs-dev/testing.ru.md): record exact source/artifact, environment, action and observed result. Host, emulator, device and vehicle gates are independent.
- A successful release-tooling test or composed closure does not establish Android/OEM behavior. Report unavailable prerequisites and unrun required gates explicitly.
