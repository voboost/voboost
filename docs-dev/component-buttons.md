# Buttons Component

[Screen](component-screen.md) | [Tabs](component-tabs.md) | [Panel](component-panel.md) | [Section](component-section.md) | [Radio](component-radio.md) | [Checkbox](component-checkbox.md) | [Button](component-button.md) | **Buttons** | [Select](component-select.md) | [Dialog](component-dialog.md) | [Popup](component-popup.md) | [Toast](component-toast.md) | [Text](component-text.md) | [Hint](component-hint.md) | [Font](component-font.md) | [Theme](component-theme.md) | [Language](component-language.md)

**English** | [Русский](component-buttons.ru.md)

## Architecture

- **[Buttons.java](../../voboost-components/src/main/java/ru/voboost/components/buttons/Buttons.java)** — Java Custom View: `LinearLayout` that lays out optional title/description text plus a row of child `Button` views (rendering is delegated to the buttons)
- **[Buttons.compose.kt](../../voboost-components/src/main/java/ru/voboost/components/buttons/Buttons.compose.kt)** — Kotlin Compose wrapper: lightweight AndroidView integration

Java handles all logic. Kotlin wrapper only bridges to Compose.

## Usage

### Java

```java
List<ButtonConfig> configs = Arrays.asList(
    new ButtonConfig("value1", "Option 1"),
    new ButtonConfig("value2", "Option 2")
);

Buttons buttons = new Buttons(context);
buttons.setButtons(configs);
buttons.setTheme(Theme.FREE_DARK);
buttons.setSelectedValue("value1");
buttons.setOnValueChangeListener(newValue -> {
    // Handle selection change
});
parentLayout.addView(buttons);
```

### Kotlin (recommended — DSL)

The receiver-lambda extensions defined in `Section.kt` wrap the Java Builder so Kotlin callers
never write `.create()`/`.build()`. The lambda runs against `Buttons.Builder`, so optional
properties are set as Builder method calls (`title(map)`, `margin(l, t, r, b)`,
`onValueChange { }`).

```kotlin
section.addButtons(Theme.FREE_DARK, Language.EN, configs, "value1") {
    title(titles)
    margin(0, 20, 0, 20)
    onValueChange { /* Handle selection change */ }
}
```

### Kotlin (View API)

```kotlin
val configs = listOf(
    ButtonConfig("value1", "Option 1"),
    ButtonConfig("value2", "Option 2")
)

val buttons = Buttons(context).apply {
    setButtons(configs)
    setTheme(Theme.FREE_DARK)
    setSelectedValue("value1")
    setOnValueChangeListener { newValue ->
        // Handle selection change
    }
}
parentLayout.addView(buttons)
```

### Compose

```kotlin
val configs = listOf(
    ButtonConfig("value1", "Option 1"),
    ButtonConfig("value2", "Option 2")
)

Buttons(
    buttons = configs,
    theme = Theme.FREE_DARK,
    selectedValue = "value1",
    onValueChange = { newValue ->
        // Handle selection change
    }
)
```

## API

### Java Custom View

```java
// Configuration
void setButtons(List<ButtonConfig> buttons)
void setSelectedValue(String value)
String getSelectedValue()

// Optional text
void setTitle(Map<String, String> text)              // Localized title above everything
void setDescriptionAbove(Map<String, String> text)    // Localized description below the title
void setRightText(Map<String, String> text)           // Localized text to the right
void setDescription(Map<String, String> text)         // Localized description below

// Theme & Language
void setTheme(Theme theme)
Theme getCurrentTheme()
void setLanguage(Language language)
Language getCurrentLanguage()

// Margin (managed by parent Section)
void setMargin(int left, int top, int right, int bottom)
int getMarginLeft()
int getMarginTop()
int getMarginRight()
int getMarginBottom()
boolean isMarginSet()

// Events
void setOnValueChangeListener(OnValueChangeListener listener)

interface OnValueChangeListener {
    void onValueChange(String newValue);
}
```

Localized setters and Builder methods capture immutable insertion-order snapshots. Resolution uses
the exact language, then the first non-null captured value, then `""`; later caller-map mutation
does not affect the view.

### Builder API

Fluent builder for constructing a fully-configured `Buttons` view. This is the Java entry
point; in Kotlin prefer the DSL extension shown in
[Kotlin (recommended — DSL)](#kotlin-recommended--dsl), which wraps this Builder.

```java
Buttons buttons = Buttons.create(context, Theme.FREE_DARK, Language.EN, configs, "value1")
    .title(titles)
    .descriptionAbove(descAbove)
    .rightText(right)
    .description(desc)
    .margin(0, 20, 0, 20)
    .onValueChange(newValue -> { /* ... */ })
    .build();
```

```java
static Buttons.Builder create(Context, Theme, Language, List<ButtonConfig> buttons, String selectedValue)

// Builder methods (each returns the Builder for chaining)
Builder title(Map<String, String> title)
Builder descriptionAbove(Map<String, String> descriptionAbove)
Builder rightText(Map<String, String> rightText)
Builder description(Map<String, String> description)
Builder marginTop(int top)
Builder marginBottom(int bottom)
Builder marginLeft(int left)
Builder marginRight(int right)
Builder margin(int left, int top, int right, int bottom)
Builder onValueChange(OnValueChangeListener listener)
Buttons build()
```

### Compose Wrapper

```kotlin
@Composable
fun Buttons(
    buttons: List<ButtonConfig>,
    theme: Theme,
    title: Map<String, String>? = null,
    descriptionAbove: Map<String, String>? = null,
    selectedValue: String? = null,
    onValueChange: ((String) -> Unit)? = null,
    rightText: Map<String, String>? = null,
    description: Map<String, String>? = null,
    language: Language? = null,
    onViewCreated: ((Buttons) -> Unit)? = null
)
```

Nullable props are controlled values: a non-null value applies it and a later `null` clears or
resets the corresponding native state on the same `Buttons` instance. `title`/`descriptionAbove =
null` hide those text views; `selectedValue = null` clears the stored selection; `rightText`/
`description = null` hide those text views; `language = null` calls `resetLanguage()` (localized
text falls back to "en"). The optional `onValueChange` is physically cleared when `null` and the
same remembered native listener is reinstalled when it becomes non-null again, so `A -> B -> null
-> A` never leaves a stale delivery path. `onViewCreated` is an at-most-once creation hook — it is
never replayed on recomposition.

### ButtonConfig

```java
public class ButtonConfig {
    @NonNull
    private final String value;  // Unique identifier
    @NonNull
    private final String text;  // Display text
    @NonNull
    private final ButtonStyle style;  // Informational only — always rendered as SECONDARY

    public ButtonConfig(@NonNull String value, @NonNull String text, @NonNull ButtonStyle style)
    public ButtonConfig(@NonNull String value, @NonNull String text)  // Defaults to SECONDARY
}
```

The `style` field is **informational only**. `Buttons` always renders every child as
`ButtonStyle.SECONDARY` and never promotes a button to `PRIMARY`; `updateButtonStyles()` does
not consult `style`. The field is retained for API stability so callers can express intent
without affecting rendering.

## Layout

```
┌───────┐ ┌─────────┐  Right text (optional)
│ Btn 1 │ │ Btn 2   │
└───────┘ └─────────┘
Description text below (optional, marginTop 14px)
```

## Styles

| Style | Description |
|-------|-------------|
| `ButtonStyle.PRIMARY` | Accent-colored (blue/brown), white text |
| `ButtonStyle.SECONDARY` | Neutral background (gray), theme text |

## Themes

| Enum | Primary BG | Secondary BG |
|------|-----------|-------------|
| `Theme.FREE_LIGHT` | Blue `#4099f3` | White `#ffffff` |
| `Theme.FREE_DARK` | Blue `#4099f3` | Dark gray `#373f4a` |
| `Theme.DREAMER_LIGHT` | Brown `#9c8069` | White `#ffffff` |
| `Theme.DREAMER_DARK` | Brown `#9c8069` | Dark gray `#40444a` |

Colors defined in [ButtonsTheme.java](../../voboost-components/src/main/java/ru/voboost/components/buttons/ButtonsTheme.java).

## Selection

- All buttons are rendered as `ButtonStyle.SECONDARY` — buttons are independent secondary actions, there is no selected/primary state
- `selectedValue` is still stored and reported via `getSelectedValue()`, but it does not change any button's visual style
- Programmatic `setSelectedValue(String)` does NOT fire `OnValueChangeListener` — only a user tap on a child Button fires the callback, exactly once with that button's value

## Dimensions

All sizes in **pixels** (automotive requirement). Button geometry lives in `ButtonTheme`;
container gaps and label sizes live in `ButtonsDimensions`. The title text size (32px) is set
directly in `Buttons.init`, larger than the 24px description/right-text.

| Property | Value |
|----------|-------|
| Button height | 70px |
| Button corner radius | 35px (pill shape) |
| Button text size | 28px |
| Button horizontal padding | 38px |
| Button gap | 10px (each child carries a 10px left margin, including the first) |
| Buttons to text gap | 20px |
| Buttons to description gap | 14px |
| Title text size | 32px |
| Description / right-text size | 24px |

## Performance

The `rebuildButtons()` method optimizes performance by reusing existing Button instances:
- When updating with the same number of buttons, existing views are reused
- When increasing the number of buttons, new views are created only for the additional buttons
- When decreasing the number of buttons, excess views are removed

This optimization reduces view creation overhead and improves performance when updating button lists.

## File Structure

```
buttons/
├── Buttons.java             # Core implementation
├── Buttons.compose.kt               # Compose wrapper
├── ButtonConfig.java         # Button configuration
├── ButtonsTheme.java         # Colors, dimensions, schemes
├── Buttons.test/
│   ├── ButtonsTestUnit.java  # Unit tests
│   └── ButtonsTestVisual.java # Visual regression tests
└── Buttons.screenshots/     # Reference images
```
