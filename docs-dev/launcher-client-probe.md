# Launcher client probe

**English** | [Русский](launcher-client-probe.ru.md)

The [standalone Android probe](../../voboost-agents/stubs/launcher-client-probe) measures configuration in an already-running Activity. It is excluded from parent Gradle settings, production source sets, DEX tasks, catalog and release dependencies. JUnit/Robolectric are host-test dependencies; the APK has no runtime libraries.

From `voboost-agents`, using the shared SDK/JDK/cache:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/Users/vitaly/voboost/.toolchains/android-sdk \
ANDROID_SDK_ROOT=/Users/vitaly/voboost/.toolchains/android-sdk \
./gradlew -p stubs/launcher-client-probe --offline --no-daemon testDebugUnitTest assembleDebug
```

APK: `stubs/launcher-client-probe/build/outputs/apk/debug/launcher-client-probe-debug.apk`; package `ru.voboost.test.launcherclient`, launcher Activity `.ProbeActivity`, min/target API 28/30. Building does not install it.

Read the **text** of the TextView with content description `launcher-client-report`. It contains newline-delimited `key=value` records with `schema=launcher-client-v1`:

| Fields | Meaning |
| --- | --- |
| `pid`, `instance` | Actual PID and Activity UUID; both stay unchanged across continuity checks |
| `resumeCount`, `configChangeCount`, `event` | Lifecycle progress and last lifecycle event; layout refresh does not replace `event` |
| `densityDpi`, `orientation`, `locales`, `screenWidthDp`, `screenHeightDp` | Fresh Activity Resources; orientation is an Android Configuration integer, locales are comma-delimited BCP47 tags |
| `callbackDensityDpi`, `callbackLocales` | Values from the latest configuration callback; zero/empty before the first callback |
| `displayId`, `displayWidthPx`, `displayHeightPx` | Actual Activity display |
| `windowBounds`, `windowBoundsSource` | Four comma-delimited integers and API30 `windowMetrics` or API28 `visibleDisplayFrame`; their bounds have different semantics |
| `viewWidthPx`, `viewHeightPx` | Report view size after layout |

The manifest handles density, orientation, screen size, locale/layout direction and other configuration changes. Repeated launcher intents are expected to bring the existing task forward; the driver must verify PID and UUID. A new PID or UUID fails continuity. Read a fresh report after callback or resume/layout update.

Host tests simulate API28/API30 resource changes, report freshness and Activity continuity. They do not establish device configuration propagation. A runtime driver compares the same running client before/after actual policy changes. See [testing](testing.md).
