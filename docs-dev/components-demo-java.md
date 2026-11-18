# Java component demo

**English** | [Русский](components-demo-java.ru.md)

Java constructors and builders own Views, rendering and listeners. Shared content/state come from `demo-shared`; the Activity installs Screen through `setContentView`.

Current implementation: [MainActivity.java](../../voboost-components/src/demo-java/java/ru/voboost/components/demo/java/MainActivity.java). Shared data and localization: [demo-shared](../../voboost-components/src/demo-shared). Exact Java/Kotlin/Compose API examples are in the [component guides](components.md).

Run from `voboost-components` with JDK 17 and the pinned Android SDK:

```sh
./gradlew :demo-java:assembleRelease
./gradlew :demo-java:testReleaseUnitTest
```

Authorized device installation is a separate `./gradlew :demo-java:installRelease` action. Root tasks `demos`, `demosTest`, `demosInstall` cover all demos; debug variants are disabled.

Exercise language, theme, Free/Dreamer, control selection and screen lift using current demo content. DemoContent and DemoState define tabs/defaults. For configuration failures, first check explicit Theme/Language, localized maps and the actual component API.

Host tests and screenshot fixtures do not establish latency, FPS or vehicle behavior. Rendering changes require the [full visual check](components-visual-testing.md).
