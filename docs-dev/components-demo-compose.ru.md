# Compose demo компонентов

[English](components-demo-compose.md) | **Русский**

Activity использует `setContent` и `VoboostDemoApp`; `DemoViewModel` предоставляет состояние, `AndroidView` wrappers делегируют Java View. При обновлении коллекций сохраняйте стабильные identity и lifecycle ownership.

Текущая реализация: [MainActivity.kt](../../voboost-components/src/demo-compose/java/ru/voboost/components/demo/compose/MainActivity.kt). Общие данные и локализация: [demo-shared](../../voboost-components/src/demo-shared). Примеры точного Java/Kotlin/Compose API находятся в [руководствах компонентов](components.ru.md).

Команды из `voboost-components` с JDK 17 и закреплённым Android SDK:

```sh
./gradlew :demo-compose:assembleRelease
./gradlew :demo-compose:testReleaseUnitTest
```

Установка на разрешённое устройство — отдельная `./gradlew :demo-compose:installRelease`. Root tasks `demos`, `demosTest`, `demosInstall` охватывают все demo; debug-варианты отключены.

Проверьте изменение языка, темы, Free/Dreamer, выбор controls и screen lift по текущему demo content. DemoContent и DemoState определяют вкладки и defaults. При ошибке сначала проверьте явные Theme/Language, локализованные maps и фактический API компонента.

Host-тесты и screenshot fixtures не доказывают время реакции, FPS или работу на автомобиле. Для изменений rendering выполните [полную визуальную проверку](components-visual-testing.ru.md).
