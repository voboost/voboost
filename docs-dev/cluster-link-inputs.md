# QNX link inputs

**English** | [Русский](cluster-link-inputs.ru.md)

The QNX product builder uses the audited [`qnx-link-interface.json`](../../voboost-inject/cluster/tools/qnx-link-interface.json) inventory. It records strong undefined symbols, ordered `DT_NEEDED`, provider and symbol-type metadata for `voboost-cluster-daemon` and `libvoboost-cluster-inject.so`, plus TLS dynamic lookups. The current Java implementation is [`QnxLinkInterfaces`](../../voboost-agents/tools/java/ru/voboost/qnx/QnxLinkInterfaces.java) and [`QnxProductBuild`](../../voboost-agents/tools/java/ru/voboost/qnx/QnxProductBuild.java), invoked through the existing agents Gradle build.

## Build inputs and checks

Default `audited-metadata` mode generates interface libraries from the inventory; CI does not need original QNX library bytes. These temporary linker inputs never enter the product or replace executable components in tests. The builder removes its owned work directory. Runtime resolves original firmware libraries.

The implant exports only the exact OEM interposers and named SDK capability entries declared by [`QnxProductExports`](../../voboost-agents/tools/java/ru/voboost/qnx/QnxProductExports.java). Its generated linker version script localizes all other symbols, including Rust implementation names and the daemon entry. The build checks the complete defined dynamic export set, function type, global binding and default visibility. Section garbage collection can then remove server code from the implant.

The implant retains the QNX target default Rust unwind tables and the existing C++ exception containment. The standalone daemon uses a second Rust archive build with `-C force-unwind-tables=no`: its launcher is C and its `no_std` Rust panic/allocation handlers abort. The builder links the implant before rebuilding the archive for the daemon, reusing the same Cargo target directory. `build-evidence.json` records the separate unwind settings and exact implant exports. This does not disable C++ exception support in injected code.

The inventory identifies `libc.so.4`, `libsocket.so.3`, `libssl1_1.so.2.1`, `libcrypto1_1.so.2.1` and the audited GNU interface `libstdc++.so.6`. Unknown imports fail linking. Verification rejects changed strong imports, new weak imports, COPY relocations, dependency order, providers/types or TLS lookup lists. Product imports use complete LLVM dynamic symbol tables; GNU hash alone cannot enumerate undefined symbols. This checks the link contract, not C signatures or QNX execution; ABI declarations stay in source.

Local `verified-originals` mode requires all four paths: `--qnx-libc`, `--qnx-socket`, `--qnx-ssl`, `--qnx-crypto`. The builder checks the existing recorded input identities, SONAME and admitted symbol metadata, then generates the same interfaces as metadata mode. Original default symbol-version aliases remain provenance and generated inputs retain the unversioned recipe. Sectionless-original fallback reads bounded ELF program-header/GNU-hash metadata after checking recorded identity. The GNU record is carried separately without a local original-byte input. These are build provenance checks, not firmware admission policy.

## Commands

Run from `voboost-agents` with NDK `29.0.14206865` and its Gradle/Java prerequisites. The builder invokes Rust `1.98.0` for `aarch64-unknown-nto-qnx700`, rebuilding `core` and `alloc`. Set `VOBOOST_AGENT_CATALOG_PUBLIC_KEY_PEM`, `VOBOOST_RELEASE_CATALOG_PUBLIC_KEY_PEM`, `VOBOOST_NATIVE_EXECUTABLE_PUBLIC_KEY_PEM` and `VOBOOST_RESTORE_PUBLIC_KEY_PEM` to explicit distinct public role roots.

```sh
./gradlew verifyQnxLinkInterfaces --offline --no-daemon --console=plain
./gradlew runQnxProductBuild --offline --no-daemon --console=plain \
  '-PqnxProductArguments=["--product","--inject","/absolute/path/to/voboost-inject","--ndk","/absolute/path/to/ndk/29.0.14206865","--output","/absolute/path/to/voboost-inject/build/cluster/product"]'
```

Replace the example paths. For original verification, append all four original-library flag/path pairs inside the JSON array. The [product closure builder](../tools/release/build-closure.ts) invokes the same task and retains `build-evidence.json`. It distinguishes `audited-metadata` and `verified-originals`, records `qnxExecuted=false` and `runtimeExecuted=false`, and keeps `native-startup-tls-ui` and `native-removal-final-absence` unverified. A host build does not establish device or vehicle acceptance.

`verifyQnxLinkInterfaces` runs Java inventory/import and load-layout regressions. Layout tests use an LLVM-built fixture when NDK tools are available, otherwise a synthetic fixture; a test count alone does not identify which ran.

## Inventory changes

Review a new import against original provider/type metadata before admitting it to a local candidate. Build fresh unsigned products, inspect import tables and review the refreshed candidate before replacing the checked-in inventory. The ordinary build does not bypass final import verification.

[`QnxLinkAudit`](../../voboost-agents/tools/java/ru/voboost/qnx/QnxLinkAudit.java) implements re-audit with `--inject`, `--llvm`, `--products`, `--evidence` and `--output`. Evidence must retain all four verified original paths; inventory alone is not build evidence. The audit carries the GNU record forward and refuses an existing output. No dedicated Gradle audit task is registered; former Python audit/test commands are obsolete.

[Third-party terms](../../voboost-inject/THIRD_PARTY.md) retain OEM and upstream rights. MIT grants no permission to redistribute original OEM libraries. See [native dependencies](native-dependencies.md), [native runtime](native.md) and [testing](testing.md).
