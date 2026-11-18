# Компонент Text

[Screen](component-screen.ru.md) | [Tabs](component-tabs.ru.md) | [Panel](component-panel.ru.md) | [Section](component-section.ru.md) | [Radio](component-radio.ru.md) | [Checkbox](component-checkbox.ru.md) | [Button](component-button.ru.md) | [Buttons](component-buttons.ru.md) | [Select](component-select.ru.md) | [Dialog](component-dialog.ru.md) | [Popup](component-popup.ru.md) | [Toast](component-toast.ru.md) | **Text** | [Hint](component-hint.ru.md) | [Font](component-font.ru.md) | [Theme](component-theme.ru.md) | [Language](component-language.ru.md).

[English](component-text.md) | **Русский**

## Архитектура

- **[Text.java](../../voboost-components/src/main/java/ru/voboost/components/text/Text.java)** — Расширяет платформенный `TextView`: нативный рендеринг текста, надёжное масштабирование и интеграция с темами
- **[Text.compose.kt](../../voboost-components/src/main/java/ru/voboost/components/text/Text.compose.kt)** — Kotlin-обёртка для Compose + модель `TextData`

Вся логика — в Java. Kotlin-обёртка только мостит к Compose. `Text` реализует `IThemable` (`propagateTheme(Theme)`, [Text.java:99](../../voboost-components/src/main/java/ru/voboost/components/text/Text.java)) и `ILocalizable` (`propagateLanguage(Language)`, [Text.java:122](../../voboost-components/src/main/java/ru/voboost/components/text/Text.java)) — листовые методы без дочерних элементов для пропагации.

## Использование

### Java

```java
// Статический текст
Text text = new Text(context);
text.setText("Hello World");
text.setTheme(Theme.FREE_LIGHT);
text.setRole(TextRole.CONTROL);
parentLayout.addView(text);

// Локализованный текст
Text localized = new Text(context);
localized.setText(Map.of(Language.EN, "Settings", Language.RU, "Настройки"));
localized.setLanguage(Language.EN);
localized.setTheme(Theme.FREE_LIGHT);
localized.setRole(TextRole.TITLE);
```

### Kotlin

```kotlin
val text = Text(context).apply {
    setText(mapOf(Language.EN to "Settings", Language.RU to "Настройки"))
    setLanguage(Language.EN)
    setTheme(Theme.FREE_LIGHT)
    setRole(TextRole.TITLE)
}
parentLayout.addView(text)
```

### Compose

```kotlin
// Простая строка
Text(
    text = "Hello World",
    theme = Theme.FREE_LIGHT,
    role = TextRole.CONTROL
)

// Локализованный
Text(
    text = TextData("settings", mapOf(Language.EN to "Settings", Language.RU to "Настройки")),
    language = Language.EN,
    theme = Theme.FREE_LIGHT,
    role = TextRole.TITLE
)
```

## API

### Java Custom View

```java
// Содержимое текста
void setText(Map<Language, String> localizedText)  // СОБСТВЕННЫЙ сеттер: локализованная карта
void setText(String text)                          // унаследован от TextView (обычная строка)
String getText()

// Тема и роль
void setTheme(Theme theme)            // выбрасывает IllegalArgumentException при null
Theme getTheme()
void setRole(TextRole role)           // выбрасывает IllegalArgumentException при null; TextRole.CONTROL, TextRole.TITLE
TextRole getRole()

// Локализация
void setLanguage(Language language)  // выбрасывает IllegalArgumentException при null
Language getLanguage()
Language getCurrentLanguage()        // переопределение ILocalizable (то же значение, что и getLanguage())

// Хуки интерфейсов (листовой компонент — без пропагации)
void propagateTheme(Theme theme)     // IThemable: no-op
void propagateLanguage(Language lang) // ILocalizable: no-op
```

### Обёртка для Compose

```kotlin
// Локализованная версия
@Composable
fun Text(
    text: TextData,
    language: Language,
    theme: Theme,
    role: TextRole = TextRole.CONTROL,
    onViewCreated: ((Text) -> Unit)? = null
)

// Версия с простой строкой
@Composable
fun Text(
    text: String,
    theme: Theme,
    role: TextRole = TextRole.CONTROL,
    onViewCreated: ((Text) -> Unit)? = null
)
```

### TextData (Kotlin)

```kotlin
class TextData(
    val value: String,
    val label: Map<Language, String>
) {
    fun getText(language: Language): String
}
```

`TextData` проверяет аргументы и выбрасывает `IllegalArgumentException`, если `value` пустой/blank, если `label` пустая или если Java-клиент передал null-значение метки. Метки сохраняются как неизменяемый снимок с исходным порядком итерации.

Локализованный resolver всегда присваивает результат: сначала точный язык, иначе первое non-null значение в сохранённом порядке, иначе `""`. Null или пустая локализованная map очищает текст и не оставляет прежнее значение. Обычный `setText(String)` выходит из локализованного режима, поэтому последующая смена языка его не перезаписывает; локализованный setter включает режим снова. Внутренний `TextData.value` никогда не отображается как localization fallback.

### TextRole

```java
enum TextRole {
    CONTROL,  // UI-контролы — определяет размер, вес и цвет
    TITLE;    // Заголовки секций — определяет размер, вес и цвет

    int getSizePx()
    int getWeight()
}
```

| Роль | Размер (px) | Вес | Назначение |
|------|-----------|--------|-----|
| `TextRole.CONTROL` | 24 | 500 (medium) | Радиокнопки, вкладки, другие UI-контролы |
| `TextRole.TITLE` | 32 | 600 (semi-bold) | Заголовки секций, заметный текст |

Вес >= 600 выбирает жирный шрифт (`Font.getBold`); в противном случае — обычный шрифт (`Font.getRegular`). Роль определяет как внешний вид (размер + вес), так и цвет. Цвета заданы для каждой пары роль x тема в [TextTheme.java](../../voboost-components/src/main/java/ru/voboost/components/text/TextTheme.java).

## Темы

| Enum | Стиль |
|-----------------------|------------------------|
| `Theme.FREE_LIGHT` | Тёмный текст на светлом фоне |
| `Theme.FREE_DARK` | Светлый текст на тёмном фоне |
| `Theme.DREAMER_LIGHT` | Тёмный текст на светлом фоне |
| `Theme.DREAMER_DARK` | Светлый текст на тёмном фоне |

## Рендеринг

Нативный рендеринг текста Android через платформенный `TextView`. Избегает багов с обрезкой на кастомном Canvas. Шрифты загружаются и применяются нативно через `Font.getRegular(context)` и `Font.getBold(context, text)` в зависимости от роли.

## Структура файлов

```
text/
├── Text.java              # Основная реализация
├── Text.compose.kt                # Обёртка для Compose + TextData
├── TextRole.java          # Enum CONTROL / TITLE
├── TextTheme.java         # Цвета темы для каждой роли
└── Text.test/             # Тесты
```

## Тестирование

```bash
./gradlew test --tests "*TextTest*"
```
