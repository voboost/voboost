# Translation tooling

**English** | [Русский](translation-tooling.ru.md)

Translation generation, inventory selection, validation and build integration use Java and the existing agents Gradle build. Tools are in [`tools/java/ru/voboost/i18n`](../../voboost-agents/tools/java/ru/voboost/i18n), tests in [`tools/java-test/ru/voboost/i18n`](../../voboost-agents/tools/java-test/ru/voboost/i18n). Their Java 17 source sets use JDK XML/ZIP APIs and the existing `org.json` dependency; they do not enter Android DEX or the resource APK.

Run from `voboost-agents` with its SDK/JDK configuration:

```sh
./gradlew --offline testI18nResources
./gradlew --offline buildI18nResources
```

`buildI18nResources` runs when `i18n-ru` is selected. Explicit inputs declare A9/A11 profiles for `setting`, `launcher`, `vehiclesetting`, `settingnetwork`, `systemui`, `bluetoothphone` and `media`. Each profile under [`i18n/ru`](../../voboost-agents/src/i18n/ru) supplies `strings.xml`, `english.xml`, `AndroidManifest.xml` and `completion-ledger.json`. The build verifies exact resource inventory/counts, readback, compatibility contract, English key parity and a code-free mutable overlay targeting the ledger's package.

`build/i18n-resources/` contains unsigned `resource-i18n-ru-<profile>.apk`, paired JSON ledgers and `resource-artifacts.json`. Each RU APK contains only `values-ru`, including prepared English fallback for untranslated Russian entries. The full `english.xml` remains an authoring/fallback input and is not installed as a custom English localization. RU actual-English witnesses in the paired ledger refer to unchanged native English strings. Product closure signing supplies authenticity; generation does not sign or admit resources at runtime.

`buildI18nEnglishResources` uses bounded `english-native.xml` snapshots and `english-delta-ledger.json` to select only prepared corrections to effective native English text that still contains Han. Local aliases and default/English fallback are resolved; external references and unverified configuration-dependent candidates are excluded. Compilation/readback preserves already English string wording and English leaves of mixed text arrays and one/other plurals. The six nonempty profiles are Setting A11, Launcher A9/A11, SystemUI A9 and Media A9/A11 (102 resources). Empty profiles produce no dummy overlay. Distinct EN identities and exact typed native/post readback keys accompany each output. The combined schema2 index declares fourteen RU and six EN pairs. Native baseline verification runs after root has disabled its own EN overlays; cached corrections cannot count as native proof. Compatibility remains an actual owner-local Resources probe, without APK/firmware fingerprints.

The generator supports styled mixed XML, strings, generic/string arrays and plurals. It preserves mixed-text whitespace and namespace inheritance, disables external XML entities, and validates positional formats, array counts, plural quantities, reference closure and English fallback. Fallback requires explicit English input. A small admission subset cannot establish a complete owner; historical `src/i18n/ru/settings-a9.xml` is only such a probe.

Compatibility uses concrete methods, resource types, indexed format contracts and actual readback. APK/firmware digests, including ledger values, must not choose compatibility. Retained source-provenance records are historical evidence, not an allowlist.

QNX generation is separate; replace the manifest path with the actual input:

```sh
./gradlew --offline buildI18nQnxResources -PvoboostQnxLocaleInputs=path/to/manifest.json
```

The manifest names actual stock roots or QNX image inputs and bundle selections, as parsed by [KzbProductionBuild](../../voboost-agents/tools/java/ru/voboost/i18n/kzb/KzbProductionBuild.java). It writes pack/ledger pairs and `qnx-resource-artifacts.json` under `build/i18n-qnx-resources/`; it does not sign, stage or activate them.

A host build does not establish Android overlay admission, lookup in ordinary/skin contexts, cleanup, restart invalidation, early startup cleanup or QNX acknowledgement. Android ledgers currently retain `admission=unverified` and `runtimeApplied=false`. See the [agent's local documentation](../../voboost-agents/src/main/java/ru/voboost/agents/i18n/ru/I18nRuAgent.md), [agent development](agent-development.md) and [testing](testing.md).
