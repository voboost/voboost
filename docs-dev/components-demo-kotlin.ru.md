# Kotlin demo компонентов

[English](components-demo-kotlin.md) | **Русский**

Activity без Compose использует Java View и DSL extensions `Section.addButton`, `addCheckbox`, `addRadio`, `addSelect`, `addButtons`. Явные Theme/Language передаются одному Java-ядру; экран устанавливается через `setContentView`.

Текущая реализация: [MainActivity.kt](../../voboost-components/src/demo-kotlin/java/ru/voboost/components/demo/kotlin/MainActivity.kt). Общие данные и локализация: [demo-shared](../../voboost-components/src/demo-shared). Примеры точного Java/Kotlin/Compose API находятся в [руководствах компонентов](components.ru.md).

Команды из `voboost-components` с JDK 17 и закреплённым Android SDK:

```sh
./gradlew :demo-kotlin:assembleRelease
./gradlew :demo-kotlin:testReleaseUnitTest
```

Установка на разрешённое устройство — отдельная `./gradlew :demo-kotlin:installRelease`. Root tasks `demos`, `demosTest`, `demosInstall` охватывают все demo; debug-варианты отключены.

Проверьте изменение языка, темы, Free/Dreamer, выбор controls и screen lift по текущему demo content. DemoContent и DemoState определяют вкладки и defaults. При ошибке сначала проверьте явные Theme/Language, локализованные maps и фактический API компонента.

Host-тесты и screenshot fixtures не доказывают время реакции, FPS или работу на автомобиле. Для изменений rendering выполните [полную визуальную проверку](components-visual-testing.ru.md).
