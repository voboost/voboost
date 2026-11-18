# Radio Component

[Screen](component-screen.md) | [Tabs](component-tabs.md) | [Panel](component-panel.md) | [Section](component-section.md) | **Radio** | [Checkbox](component-checkbox.md) | [Button](component-button.md) | [Buttons](component-buttons.md) | [Select](component-select.md) | [Dialog](component-dialog.md) | [Popup](component-popup.md) | [Toast](component-toast.md) | [Text](component-text.md) | [Hint](component-hint.md) | [Font](component-font.md) | [Theme](component-theme.md) | [Language](component-language.md)

**English** | [Русский](component-radio.ru.md)

## Architecture

- **[Radio.java](../../voboost-components/src/main/java/ru/voboost/components/radio/Radio.java)** — Java container (LinearLayout): layout, state; delegates canvas rendering, animations, and touch to package-private [RadioPrimitive.java](../../voboost-components/src/main/java/ru/voboost/components/radio/RadioPrimitive.java)
- **[Radio.compose.kt](../../voboost-components/src/main/java/ru/voboost/components/radio/Radio.compose.kt)** — Kotlin Compose wrapper: lightweight AndroidView integration

Java handles all logic. Kotlin wrapper only bridges to Compose.

## Usage

### Java

```java
Radio radio = new Radio(context);

radio.setButtons(Arrays.asList(
    new RadioButton("en", Map.of("en", "English", "ru", "Английский")),
    new RadioButton("ru", Map.of("en", "Russian", "ru", "Русский"))
));
radio.setLanguage(Language.EN);
radio.setTheme(Theme.FREE_LIGHT);
radio.setSelectedValue("en");
radio.setOnValueChangeListener(newValue -> {
    // Handle selection change
});

parentLayout.addView(radio);
```

### Kotlin (recommended — DSL)

The receiver-lambda extensions defined in `Section.kt` wrap the Java Builder so Kotlin callers
never write `.create()`/`.build()`. The lambda runs against `Radio.Builder`, so optional
properties are set as Builder method calls (`title(map)`, `margin(l, t, r, b)`,
`onValueChange { }`).

```kotlin
section.addRadio(Theme.FREE_LIGHT, Language.EN, buttons, "en") {
    title(titleMap)
    margin(0, 10, 0, 10)
    onValueChange { /* Handle selection change */ }
}
```

For standalone construction, use the `radio()` factory from `RadioFactory.kt`:

```kotlin
val radio = radio(context, Theme.FREE_LIGHT, Language.EN, buttons, "en") {
    title(titleMap)
    margin(0, 10, 0, 10)
    onValueChange { /* Handle selection change */ }
}
```

### Kotlin (View API)

```kotlin
val radio = Radio(context).apply {
    setButtons(listOf(
        RadioButton("en", mapOf("en" to "English", "ru" to "Английский")),
        RadioButton("ru", mapOf("en" to "Russian", "ru" to "Русский"))
    ))
    setLanguage(Language.EN)
    setTheme(Theme.FREE_LIGHT)
    setSelectedValue("en")
    setOnValueChangeListener { newValue ->
        // Handle selection change
    }
}

parentLayout.addView(radio)
```

### Compose

```kotlin
var selectedValue by remember { mutableStateOf("en") }

Radio(
    buttons = listOf(
        RadioButton("en", mapOf("en" to "English", "ru" to "Английский")),
        RadioButton("ru", mapOf("en" to "Russian", "ru" to "Русский"))
    ),
    language = Language.EN,
    theme = Theme.FREE_LIGHT,
    value = selectedValue,
    onValueChange = { selectedValue = it }
)
```

## API

### Java Custom View

```java
// Configuration
void setButtons(List<RadioButton> buttons)
void setLanguage(Language language)    // Language.EN, Language.RU
void setTheme(Theme theme)            // Theme.FREE_LIGHT, Theme.FREE_DARK, etc.

// Text (optional, localized)
void setTitle(Map<String, String> title)
void setDescriptionAbove(Map<String, String> text)
void setDescription(Map<String, String> text)   // description below

// Value
void setSelectedValue(String value)
void setSelectedValue(String value, boolean isTriggerCallback)
String getSelectedValue()

// Layout
void setWidth(int widthPx)            // fixed total width (0 = auto by text)
void setMargin(int left, int top, int right, int bottom)
int getMarginLeft() / getMarginTop() / getMarginRight() / getMarginBottom()
boolean isMarginSet()

// State
Theme getCurrentTheme()
Language getCurrentLanguage()

// Events
void setOnValueChangeListener(OnValueChangeListener listener)
void setOptionHandler(String value, Consumer<Radio> handler)   // per-option UI handler

interface OnValueChangeListener {
    void onValueChange(String newValue);
}
```

### Builder

Fluent `create(...)` builder for required fields plus optional text, margins, width, listeners.
This is the Java entry point; in Kotlin prefer the DSL extension shown in
[Kotlin (recommended — DSL)](#kotlin-recommended--dsl), which wraps this Builder.

```java
Radio.create(context, theme, language, buttons, selectedValue)
    .title(Map<String, String>)
    .descriptionAbove(Map<String, String>)
    .description(Map<String, String>)
    .margin(int left, int top, int right, int bottom)   // or marginTop/Bottom/Left/Right
    .width(int widthPx)
    .onValueChange(OnValueChangeListener)
    .on(String value, Consumer<Radio> handler)          // per-option handler
    .build();
```

### Compose Wrapper

```kotlin
@Composable
fun Radio(
    buttons: List<RadioButton>,
    language: Language,
    theme: Theme,
    value: String,
    onValueChange: (String) -> Unit,
    title: Map<String, String>? = null,
    descriptionAbove: Map<String, String>? = null,
    descriptionBelow: Map<String, String>? = null,
    width: Int? = null,
    onViewCreated: ((Radio) -> Unit)? = null  // for testing
)
```

Nullable props are controlled values: a non-null value applies it and a later `null` hides the
corresponding text view on the same `Radio` instance (`title`, `descriptionAbove`,
`descriptionBelow`). `width` pins the radio items area to that pixel width; `null` resets it to
`0` so the width is auto-sized by the button text. The Java Builder's per-option
`on(value, Consumer<Radio>)` handlers are intentionally not exposed here — `onValueChange` already
covers selection; reach for the Java Builder directly for per-option UI side-effects.
`onViewCreated` is an at-most-once creation hook — it is never replayed on recomposition.

### RadioButton

```java
RadioButton(String value, Map<String, String> label)

String getText(String lang)       // localized text with fallback
String getValue()
Map<String, String> getLabel()
RadioButton copy(String value, Map<String, String> label)
```

## Themes

| Enum | Style |
|------|-------|
| `Theme.FREE_LIGHT` | Light + blue gradient |
| `Theme.FREE_DARK` | Dark + blue gradient |
| `Theme.DREAMER_LIGHT` | Light + brown gradient |
| `Theme.DREAMER_DARK` | Dark + brown gradient |

Theme colors defined in [RadioTheme.java](../../voboost-components/src/main/java/ru/voboost/components/radio/RadioTheme.java): `RadioColors`, `RadioColorSchemes`, `RadioDimensions`.

## Localization

External via `Map<String, String>` — no shared i18n system:

```java
new RadioButton("value", Map.of("en", "English", "ru", "Русский"));
```

Language is set via `setLanguage(Language.EN)` / `language = Language.EN`. Text setters, Builder
methods, and `RadioButton` capture insertion-order snapshots. Resolution uses the exact language,
then the first non-null captured label; caller-map and accessor-map mutation cannot alter it.

## Implementation Details

### Rendering

Canvas-based with 3 layers:
1. **Background** — rounded rect with theme color
2. **Selection** — drawn differently by theme:
   - **Free themes** — NinePatch slider (`RadioPrimitive.png`); falls back to parametric if the bitmap is unavailable
   - **Dreamer themes** — parametric rounded rect filled with a `LinearGradient`
3. **Text** — centered, color changes based on selection coverage

### Animation

`ValueAnimator` + `OvershootInterpolator`. Animates X position and width of selection background (400ms). Hardware layer disabled — `RadioPrimitive` calls `setLayerType(LAYER_TYPE_NONE, null)` (contrast: Button/Select use `LAYER_TYPE_HARDWARE`).

On touch selection, `OnValueChangeListener.onValueChange` is fired after a 300ms delay (`RadioDimensions.VALUE_CHANGE_DELAY`), during the animation — not synchronously on touch.

### Touch

Only primitive segments select values, using the child-local coordinates supplied by normal view
dispatch. Title and description areas do not select. A vertical scroll, cancelled gesture, or
disabled control does not select; every completed, cancelled, or disabled active gesture releases
parent-interception suppression. A valid tap animates to the touched item and triggers the callback
after the 300ms delay above.

### Selection state

Selection is normalized to `""` when its value is null, unknown, removed from the options, or
when options are empty. Duplicate values are supported; the first matching segment owns the
indicator. Empty options clear item and indicator geometry. Programmatic selection or option
changes cancel in-flight selection animation and invalidate delayed touch callbacks, so a prior
tap cannot report a value after a newer state update.

### Dimensions

All sizes in **pixels** (automotive requirement). Constants in `RadioDimensions`.

## File Structure

```
radio/
├── Radio.java              # Core implementation
├── RadioPrimitive.java     # Canvas/touch rendering (package-private)
├── Radio.compose.kt                # Compose wrapper
├── RadioButton.java        # Data model
├── RadioTheme.java         # Colors, dimensions, schemes
├── Radio.test/
│   ├── RadioTestUnit.java  # Unit tests
│   └── RadioTestVisual.java # Visual regression tests
└── Radio.screenshots/      # Reference images
```

## Testing

- **Unit**: `Radio.test/RadioTestUnit.java` — initialization, state, callbacks, edge cases, and touch
- **Visual**: `Radio.test/RadioTestVisual.java` — all theme/language combinations, animation frames
- **Screenshots**: `Radio.screenshots/` — reference images for visual regression

```bash
./gradlew test --tests "*RadioTest*"   # All Radio tests
./gradlew record                       # Record new screenshots
```

## Demo Applications

4 demos in [`src/demo-*/`](../../voboost-components/src/demo-java):
- **demo-java** — Pure Java
- **demo-kotlin** — Kotlin + Java Custom View
- **demo-compose** — Jetpack Compose
- **demo-pixel** — Pixel-perfect visual testing

The description above has no title gap when the title is absent or empty; adding a visible title restores the default title gap.
