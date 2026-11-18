# Клиентский probe лаунчера

[English](launcher-client-probe.md) | **Русский**

[Отдельный Android probe](../../voboost-agents/stubs/launcher-client-probe) измеряет конфигурацию в уже работающей Activity. Он исключён из родительских Gradle settings, production source sets, DEX tasks, каталога и release-зависимостей. JUnit/Robolectric нужны host-тестам; runtime-библиотек у APK нет.

Из `voboost-agents`, с общими SDK/JDK/кешем:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/Users/vitaly/voboost/.toolchains/android-sdk \
ANDROID_SDK_ROOT=/Users/vitaly/voboost/.toolchains/android-sdk \
./gradlew -p stubs/launcher-client-probe --offline --no-daemon testDebugUnitTest assembleDebug
```

APK: `stubs/launcher-client-probe/build/outputs/apk/debug/launcher-client-probe-debug.apk`; пакет `ru.voboost.test.launcherclient`, launcher Activity `.ProbeActivity`, min/target API 28/30. Сборка не устанавливает APK.

Считывайте **текст** TextView с content description `launcher-client-report`. Это строки `key=value` со схемой `schema=launcher-client-v1`:

| Поля | Значение |
| --- | --- |
| `pid`, `instance` | Фактический PID и UUID Activity; оба сохраняются при проверке непрерывности |
| `resumeCount`, `configChangeCount`, `event` | Продвижение lifecycle и последнее событие; layout refresh не заменяет `event` |
| `densityDpi`, `orientation`, `locales`, `screenWidthDp`, `screenHeightDp` | Свежие Activity Resources; orientation — целое Android Configuration, locales — BCP47 tags через запятую |
| `callbackDensityDpi`, `callbackLocales` | Значения из последнего configuration callback; до первого callback — ноль и пустая строка |
| `displayId`, `displayWidthPx`, `displayHeightPx` | Фактический дисплей Activity |
| `windowBounds`, `windowBoundsSource` | Четыре целых через запятую и API30 `windowMetrics` либо API28 `visibleDisplayFrame`; семантика границ различается |
| `viewWidthPx`, `viewHeightPx` | Размеры TextView после layout |

Manifest обрабатывает density, orientation, размеры экрана, locale/layout direction и другие изменения конфигурации. Повторный launcher intent должен вывести существующий task вперёд; driver проверяет PID и UUID. Новый PID или UUID означает провал непрерывности. Считывайте свежий отчёт после callback либо resume/layout update.

Host-тесты моделируют изменения ресурсов на API28/API30, свежесть отчёта и сохранение Activity. Они не доказывают распространение конфигурации на устройстве. Runtime driver сравнивает одного работающего клиента до/после фактического изменения policy. См. [проверки](testing.ru.md).
