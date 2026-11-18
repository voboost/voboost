# Compose component demo

**English** | [Русский](components-demo-compose.ru.md)

The Activity uses `setContent` and `VoboostDemoApp`; `DemoViewModel` supplies state and `AndroidView` wrappers delegate to Java Views. Keep stable identities and lifecycle ownership when updating child collections.

Current implementation: [MainActivity.kt](../../voboost-components/src/demo-compose/java/ru/voboost/components/demo/compose/MainActivity.kt). Shared data and localization: [demo-shared](../../voboost-components/src/demo-shared). Exact Java/Kotlin/Compose API examples are in the [component guides](components.md).

Run from `voboost-components` with JDK 17 and the pinned Android SDK:

```sh
./gradlew :demo-compose:assembleRelease
./gradlew :demo-compose:testReleaseUnitTest
```

Authorized device installation is a separate `./gradlew :demo-compose:installRelease` action. Root tasks `demos`, `demosTest`, `demosInstall` cover all demos; debug variants are disabled.

Exercise language, theme, Free/Dreamer, control selection and screen lift using current demo content. DemoContent and DemoState define tabs/defaults. For configuration failures, first check explicit Theme/Language, localized maps and the actual component API.

Host tests and screenshot fixtures do not establish latency, FPS or vehicle behavior. Rendering changes require the [full visual check](components-visual-testing.md).
