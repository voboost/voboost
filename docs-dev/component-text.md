# Text Component

[Screen](component-screen.md) | [Tabs](component-tabs.md) | [Panel](component-panel.md) | [Section](component-section.md) | [Radio](component-radio.md) | [Checkbox](component-checkbox.md) | [Button](component-button.md) | [Buttons](component-buttons.md) | [Select](component-select.md) | [Dialog](component-dialog.md) | [Popup](component-popup.md) | [Toast](component-toast.md) | **Text** | [Hint](component-hint.md) | [Font](component-font.md) | [Theme](component-theme.md) | [Language](component-language.md)

**English** | [Русский](component-text.ru.md)

## Architecture

- **[Text.java](../../voboost-components/src/main/java/ru/voboost/components/text/Text.java)** — Extended platform `TextView`: native text rendering, robust sizing, and theming integration
- **[Text.compose.kt](../../voboost-components/src/main/java/ru/voboost/components/text/Text.compose.kt)** — Kotlin Compose wrapper + `TextData` model

Java handles all logic. Kotlin wrapper bridges to Compose. `Text` implements `IThemable` (`propagateTheme(Theme)`, [Text.java:99](../../voboost-components/src/main/java/ru/voboost/components/text/Text.java)) and `ILocalizable` (`propagateLanguage(Language)`, [Text.java:122](../../voboost-components/src/main/java/ru/voboost/components/text/Text.java)) — leaf methods with no children to propagate to.

## Usage

### Java

```java
// Static text
Text text = new Text(context);
text.setText("Hello World");
text.setTheme(Theme.FREE_LIGHT);
text.setRole(TextRole.CONTROL);
parentLayout.addView(text);

// Localized text
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
// Simple string
Text(
    text = "Hello World",
    theme = Theme.FREE_LIGHT,
    role = TextRole.CONTROL
)

// Localized
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
// Text content
void setText(Map<Language, String> localizedText)  // OWN setter: localized map
void setText(String text)                          // inherited from TextView (plain string)
String getText()

// Theme and role
void setTheme(Theme theme)            // throws IllegalArgumentException if null
Theme getTheme()
void setRole(TextRole role)           // throws IllegalArgumentException if null; TextRole.CONTROL, TextRole.TITLE
TextRole getRole()

// Localization
void setLanguage(Language language)  // throws IllegalArgumentException if null
Language getLanguage()
Language getCurrentLanguage()        // ILocalizable override (same value as getLanguage())

// Interface hooks (leaf component — no propagation)
void propagateTheme(Theme theme)     // IThemable: no-op
void propagateLanguage(Language lang) // ILocalizable: no-op
```

### Compose Wrapper

```kotlin
// Localized version
@Composable
fun Text(
    text: TextData,
    language: Language,
    theme: Theme,
    role: TextRole = TextRole.CONTROL,
    onViewCreated: ((Text) -> Unit)? = null
)

// Simple string version
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

`TextData` validates its arguments and throws `IllegalArgumentException` if `value` is blank/empty,
if `label` is empty, or if a Java caller supplies a null label value. It stores an immutable
iteration-ordered snapshot of `label`.

Localized resolution always assigns a result: exact language first, otherwise the first non-null
value in captured iteration order, otherwise `""`. Null or empty localized input clears rendered
text instead of retaining an earlier value. A plain `setText(String)` exits localized mode, so later
language changes do not overwrite it; calling the localized setter enters localized mode again.
The internal `TextData.value` is never displayed as a localization fallback.

### TextRole

```java
enum TextRole {
    CONTROL,  // UI controls — determines size, weight, and color
    TITLE;    // Section headers — determines size, weight, and color

    int getSizePx()
    int getWeight()
}
```

| Role | Size (px) | Weight | Use |
|------|-----------|--------|-----|
| `TextRole.CONTROL` | 24 | 500 (medium) | Radio buttons, tabs, other UI controls |
| `TextRole.TITLE` | 32 | 600 (semi-bold) | Section headers, prominent text |

Weight >= 600 selects a bold typeface (`Font.getBold`); otherwise a regular typeface (`Font.getRegular`). Role determines both visual appearance (size + weight) and color. Colors are defined per role x theme in [TextTheme.java](../../voboost-components/src/main/java/ru/voboost/components/text/TextTheme.java).

## Themes

| Enum | Style |
|-----------------------|------------------------|
| `Theme.FREE_LIGHT` | Dark text on light bg |
| `Theme.FREE_DARK` | Light text on dark bg |
| `Theme.DREAMER_LIGHT` | Dark text on light bg |
| `Theme.DREAMER_DARK` | Light text on dark bg |

## Rendering

Native Android text rendering via the platform `TextView`. Avoids custom Canvas clipping bugs. Fonts are loaded and applied natively via `Font.getRegular(context)` and `Font.getBold(context, text)` depending on the role.

## File Structure

```
text/
├── Text.java              # Core implementation
├── Text.compose.kt                # Compose wrapper + TextData
├── TextRole.java          # CONTROL / TITLE enum
├── TextTheme.java         # Theme colors per role
└── Text.test/             # Tests
```

## Testing

```bash
./gradlew test --tests "*TextTest*"
```
