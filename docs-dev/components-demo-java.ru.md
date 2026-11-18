# Java demo компонентов

[English](components-demo-java.md) | **Русский**

Java constructors и builders создают View, rendering и listeners. Общие content/state поступают из `demo-shared`; Activity устанавливает Screen через `setContentView`.

Текущая реализация: [MainActivity.java](../../voboost-components/src/demo-java/java/ru/voboost/components/demo/java/MainActivity.java). Общие данные и локализация: [demo-shared](../../voboost-components/src/demo-shared). Примеры точного Java/Kotlin/Compose API находятся в [руководствах компонентов](components.ru.md).

Команды из `voboost-components` с JDK 17 и закреплённым Android SDK:

```sh
./gradlew :demo-java:assembleRelease
./gradlew :demo-java:testReleaseUnitTest
```

Установка на разрешённое устройство — отдельная `./gradlew :demo-java:installRelease`. Root tasks `demos`, `demosTest`, `demosInstall` охватывают все demo; debug-варианты отключены.

Проверьте изменение языка, темы, Free/Dreamer, выбор controls и screen lift по текущему demo content. DemoContent и DemoState определяют вкладки и defaults. При ошибке сначала проверьте явные Theme/Language, локализованные maps и фактический API компонента.

Host-тесты и screenshot fixtures не доказывают время реакции, FPS или работу на автомобиле. Для изменений rendering выполните [полную визуальную проверку](components-visual-testing.ru.md).
