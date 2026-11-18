# Kotlin component demo

**English** | [Русский](components-demo-kotlin.ru.md)

The non-Compose Activity uses Java Views and the `Section.addButton`, `addCheckbox`, `addRadio`, `addSelect`, `addButtons` DSL extensions. Explicit Theme/Language reach the same Java core; `setContentView` owns the screen.

Current implementation: [MainActivity.kt](../../voboost-components/src/demo-kotlin/java/ru/voboost/components/demo/kotlin/MainActivity.kt). Shared data and localization: [demo-shared](../../voboost-components/src/demo-shared). Exact Java/Kotlin/Compose API examples are in the [component guides](components.md).

Run from `voboost-components` with JDK 17 and the pinned Android SDK:

```sh
./gradlew :demo-kotlin:assembleRelease
./gradlew :demo-kotlin:testReleaseUnitTest
```

Authorized device installation is a separate `./gradlew :demo-kotlin:installRelease` action. Root tasks `demos`, `demosTest`, `demosInstall` cover all demos; debug variants are disabled.

Exercise language, theme, Free/Dreamer, control selection and screen lift using current demo content. DemoContent and DemoState define tabs/defaults. For configuration failures, first check explicit Theme/Language, localized maps and the actual component API.

Host tests and screenshot fixtures do not establish latency, FPS or vehicle behavior. Rendering changes require the [full visual check](components-visual-testing.md).
