# Voboost Components

Библиотека компонентов интерфейса для автомобилей Voyah Free и Voyah Dreamer на базе Android 9 и 11. Не подходит для Voyah Passion и Courage и для автомобилей на базе HarmonyOS. Компоненты пиксельно точно повторяют нативный интерфейс настроек автомобиля и доступны в трёх слоях: ядро на Java, фабрика на Kotlin и обёртка Compose.

[English](components.md) | **Русский**

## Зачем

Нативный интерфейс настроек автомобиля фиксирован: разрешение 1920x720, Android 9 или 11. Библиотека повторяет его точно, чтобы приложения выглядели «родными» в системе. Адаптивный дизайн, доступность и сохранение состояния не предусмотрены — экран фиксирован, парк автомобилей известен.

## Архитектура

Одно ядро на Java, три слоя доступа:

```
Слой 3 — Compose     Component.compose.kt   @Composable fun Component(...)
Слой 2 — Kotlin      ComponentFactory.kt     fun component(...) { ... }
Слой 1 — Java        Component.java          Component.create(...).build()
```

- **Слой 1 (ядро на Java + строитель)**: отрисовка, обработка касаний, анимация, тема, локализация. Публичный API для приложений на Java
- **Слой 2 (фабрика на Kotlin)**: оборачивает строитель, чтобы приложения на Kotlin не вызывали `.create()` и `.build()`. Живёт внутри каждого компонента в файле `ComponentFactory.kt`
- **Слой 3 (обёртка Compose)**: `@Composable` + `AndroidView` для реактивных приложений на Compose. Живёт в файле `Component.compose.kt`

Все слои работают с одним ядром на Java и не дублируют его. Каждый компонент самодостаточен: нет общей базовой реализации или глобального хранилища темы/языка. Тонкие общие контракты и утилиты без состояния (`IThemable`, `ILocalizable`, `Theme`, `Language`, `Font`, `I18nUtils`) обеспечивают единое поведение компонентов.

Обоснование ключевых решений — в [docs/architecture-decisions.md](components-architecture-decisions.md).

## Компоненты

| Компонент | Описание | Документация |
|---|---|---|
| Screen | Корневой контейнер с эффектом lift и согласованием панелей | [Screen.ru.md](component-screen.ru.md) |
| Panel | Контейнер содержимого внутри экрана | [Panel.ru.md](component-panel.ru.md) |
| Tabs | Навигация по вкладкам с анимацией индикатора выбора | [Tabs.ru.md](component-tabs.ru.md) |
| Section | Сворачиваемая секция с заголовком и всплывающим окном | [Section.ru.md](component-section.ru.md) |
| Radio | Группа переключателей с горизонтальным выбором | [Radio.ru.md](component-radio.ru.md) |
| Button | Кнопка с жирным текстом, отступами и описанием | [Button.ru.md](component-button.ru.md) |
| Buttons | Группа кнопок с заголовком и настройкой каждой | [Buttons.ru.md](component-buttons.ru.md) |
| Checkbox | Флажок с меткой и анимированным переключателем | [Checkbox.ru.md](component-checkbox.ru.md) |
| Select | Колёсный выбор со всплывающим окном | [Select.ru.md](component-select.ru.md) |
| Text | Текст с выбором веса и цвета по роли | [Text.ru.md](component-text.ru.md) |
| Hint | Текстовая подсказка | [Hint.ru.md](component-hint.ru.md) |
| Dialog | Модальное окно с заголовком, сообщением и кнопками | [Dialog.ru.md](component-dialog.ru.md) |
| Popup | Информационное окно с заголовком и текстом | [Popup.ru.md](component-popup.ru.md) |
| Toast | Уведомление с длительностью и кнопкой закрытия | [Toast.ru.md](component-toast.ru.md) |
| Font | Загрузчик шрифтов: обычный, жирный ASCII, жирный Unicode | [Font.ru.md](component-font.ru.md) |
| Theme | Перечисление тем: Free и Dreamer, светлая и тёмная | [Theme.ru.md](component-theme.ru.md) |
| Language | Перечисление языков (EN, RU) и утилиты локализации | [Language.ru.md](component-language.ru.md) |

## Быстрый старт

### Требования

- JDK 17 для сборки (источник и байт-код — Java 11)
- Android SDK API 28+ (compile SDK 34)
- Gradle 8.2 (через `./gradlew`)

### Сборка

```bash
./gradlew build          # Собрать, проверить и протестировать (только release)
./gradlew format         # Исправить стиль автоматически (ktlint + Spotless)
./gradlew validate       # Тесты + ktlint + проверка Spotless
```

### Подключение

```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":voboost-components"))
}
```

Слой 1 (Java):
```java
Button.create(context, theme, language, "OK", ButtonStyle.SECONDARY)
        .boldText(true)
        .build();
```

Слой 2 (фабрика на Kotlin):
```kotlin
val panel = panel(context, Theme.FREE_DARK, Language.EN) {
    compact(true)
    addSection(section)
}
```

Слой 3 (Compose):
```kotlin
Button(text = "OK", theme = Theme.FREE_LIGHT, language = Language.EN, boldText = true)
```

## Демо

| Демо | Язык | Зачем нужно |
|---|---|---|
| [demo-java](../../voboost-components/src/demo-java) | Java | Показывает строитель (слой 1) для приложений на Java |
| [demo-kotlin](../../voboost-components/src/demo-kotlin) | Kotlin | Показывает слои 1 и 2 из Kotlin без Compose |
| [demo-compose](../../voboost-components/src/demo-compose) | Kotlin | Показывает обёртки Compose (слой 3) и согласоватор |
| [demo-pixel](../../voboost-components/src/demo-pixel) | Java | Пиксельные тесты: сравнение со скриншотами с оборудования |
| [demo-java-cunba](../../voboost-components/src/demo-java-cunba) | Java | CunBA 3 — поток активации на Java |
| [demo-kotlin-cunba](../../voboost-components/src/demo-kotlin-cunba) | Kotlin | CunBA 3 — поток активации на Kotlin |
| [demo-shared](../../voboost-components/src/demo-shared) | Java | Общий контент, состояние и вспомогательные функции для всех демо |

```bash
./gradlew demos           # Собрать все демо
./gradlew demosTest       # Протестировать все демо
./gradlew demosInstall    # Установить все демо на устройство
./gradlew demoJavaStart   # Запустить демо на Java на устройстве
```

## Тестирование

Тесты расположены рядом с компонентами (структура BEM): `Component.test/ComponentTestUnit.java` — логика, `ComponentTestVisual.java` — визуальная регрессия через Roborazzi.

```bash
./gradlew testUnit                        # Все unit-тесты
./gradlew testVisual                       # Все визуальные тесты
./gradlew testUnit --tests "*RadioTest*"   # Один компонент
./gradlew :demo-pixel:demoPixelVerify                 # Пиксельная регрессия против оборудования
```

Пиксельные тесты сравнивают с эталонами `_1original.png` — снимками с реального оборудования. Тест падает при регрессии: процент совпадения упал ниже записанного baseline. Эталоны `_1original.png` не изменять.

## Как контрибьютить

1. Прочитать [AGENTS.md](../../voboost-components/AGENTS.md) — правила и ограничения проекта
2. Каждый компонент самодостаточен: ядро на Java, фабрика на Kotlin, обёртка Compose и тесты находятся в одном каталоге исходников; usage guides — в product `docs-dev`
3. Все размеры — в пикселях (не dp), все hex-коды цветов — в нижнем регистре
4. После любого визуального изменения запустить `./gradlew :demo-pixel:demoPixelVerify`
5. Перед передачей в репозиторий запустить `./gradlew format`
6. Зафиксированные решения — в [docs/architecture-decisions.md](components-architecture-decisions.md), не пересматривать

## Версии сборки

| Компонент | Версия | Обоснование |
|---|---|---|
| JDK для сборки | 17 | Требуется для Gradle и AGP |
| Java (источник) | 11 | Байт-код для Android 9 и 11 |
| AGP | 8.2.2 | Зафиксированная версия плагина |
| Gradle | 8.2 | Зафиксированная версия wrapper |
| Kotlin | 2.0.21 | Плагин компилятора Compose |
| Compile SDK | 34 | Требование AndroidX |
| Min SDK | 28 | Android 9 (парк автомобилей) |
| Target SDK | 28 | Базовый уровень Android 9 |

Только release-сборка — вариант `debug` отключён. `./gradlew build` собирает один release-вариант для библиотеки и каждого демо.

## Лицензия

Apache License 2.0. См. [LICENSE](../../voboost-components/LICENSE).
