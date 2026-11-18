# Button Component

[Screen](component-screen.md) | [Tabs](component-tabs.md) | [Panel](component-panel.md) | [Section](component-section.md) | [Radio](component-radio.md) | [Checkbox](component-checkbox.md) | **Button** | [Buttons](component-buttons.md) | [Select](component-select.md) | [Dialog](component-dialog.md) | [Popup](component-popup.md) | [Toast](component-toast.md) | [Text](component-text.md) | [Hint](component-hint.md) | [Font](component-font.md) | [Theme](component-theme.md) | [Language](component-language.md)

**English** | [Русский](component-button.ru.md)

## Architecture

- **[Button.java](../../voboost-components/src/main/java/ru/voboost/components/button/Button.java)** — Java container (LinearLayout): layout, state; delegates canvas rendering and touch to package-private [ButtonPrimitive.java](../../voboost-components/src/main/java/ru/voboost/components/button/ButtonPrimitive.java)
- **[Button.compose.kt](../../voboost-components/src/main/java/ru/voboost/components/button/Button.compose.kt)** — Kotlin Compose wrapper: lightweight AndroidView integration

Java handles all logic. Kotlin wrapper only bridges to Compose.

## Usage

### Java

```java
Button button = new Button(context);
button.setText("Confirm");
button.setStyle(ButtonStyle.PRIMARY);
button.setTheme(Theme.FREE_DARK);
button.setEnabled(true);
button.setOnClickListener(new View.OnClickListener() {
    @Override
    public void onClick(View v) {
        // Handle click
    }
});
parentLayout.addView(button);
```

### Kotlin (recommended — DSL)

The receiver-lambda extensions defined in `Section.kt` wrap the Java Builder so Kotlin callers
never write `.create()`/`.build()`. The lambda runs against `Button.Builder`, so optional
properties are set as Builder method calls (`description(map)`, `margin(l, t, r, b)`,
`onClick { }`).

```kotlin
section.addButton(Theme.FREE_DARK, Language.EN, "Confirm", ButtonStyle.PRIMARY) {
    description(descMap)
    margin(0, 10, 0, 10)
    onClick { /* Handle click */ }
}
```

For standalone construction, use the `button()` factory from `ButtonFactory.kt`:

```kotlin
val btn = button(context, Theme.FREE_DARK, Language.EN, "Confirm", ButtonStyle.PRIMARY) {
    description(descMap)
    margin(0, 10, 0, 10)
    onClick { /* Handle click */ }
}
```

### Kotlin (View API)

```kotlin
val button = Button(context).apply {
    setText("Confirm")
    setStyle(ButtonStyle.PRIMARY)
    setTheme(Theme.FREE_DARK)
    isEnabled = true
    setOnClickListener { /* Handle click */ }
}
parentLayout.addView(button)
```

### Compose

```kotlin
Button(
    text = "Confirm",
    style = ButtonStyle.PRIMARY,
    theme = Theme.FREE_DARK,
    enabled = true,
    onClick = { /* Handle click */ }
)
```

## API

### Java Custom View

```java
// Configuration
void setText(String text)
String getText()
void setBoldText(boolean bold)             // true = bold font
void setStyle(ButtonStyle style)           // ButtonStyle.PRIMARY, ButtonStyle.SECONDARY
ButtonStyle getStyle()
void setTheme(Theme theme)                 // throws if null
Theme getCurrentTheme()
void setLanguage(Language language)        // throws if null
Language getCurrentLanguage()

// Labels (localized: lang code -> text)
void setTitle(Map<String, String> title)            // above the button
void setDescriptionAbove(Map<String, String> desc)  // between title and button
void setDescription(Map<String, String> desc)       // to the right of the button

// Layout
void padding(int left, int top, int right, int bottom)   // px, overrides default horizontal padding
void setMargin(int left, int top, int right, int bottom)
int getMarginLeft(); int getMarginTop()
int getMarginRight(); int getMarginBottom()
boolean isMarginSet()

// State
void setEnabled(boolean enabled)
boolean isEnabled()

// Events — uses android.view.View.OnClickListener (no custom interface)
void setOnClickListener(android.view.View.OnClickListener listener)
```

Localized setters and their Builder counterparts capture immutable insertion-order snapshots.
Resolution uses the exact language, then the first non-null captured value, then `""`; later
mutation of the caller's map cannot change the rendered text.

### Compose Wrapper

```kotlin
@Composable
fun Button(
    text: String,
    title: Map<String, String>? = null,
    descriptionAbove: Map<String, String>? = null,
    boldText: Boolean = false,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    theme: Theme,
    enabled: Boolean = true,
    onClick: () -> Unit,
    description: Map<String, String>? = null,
    language: Language? = null,
    paddingTop: Int? = null,
    paddingBottom: Int? = null,
    paddingLeft: Int? = null,
    paddingRight: Int? = null,
    onViewCreated: ((Button) -> Unit)? = null
)
```

Nullable props are controlled values: a non-null value applies it and a later `null` clears or
resets the corresponding native state on the same `Button` instance. `title = null` and
`descriptionAbove = null` hide those texts; `description = null` hides the description; `language =
null` calls `resetLanguage()` (localized text falls back to "en"); when all four padding props are
`null` the wrapper calls `resetPadding()` so each side falls back to its default (`38px`
horizontal, `0` vertical) instead of writing zeros. `boldText` is applied unconditionally (`false`
is the default). `onViewCreated` is an at-most-once creation hook — it is never replayed on
recomposition.

## Builder API

Fluent builder for constructing a fully-configured `Button` in one call
(Button.java:340-480). This is the Java entry point; in Kotlin prefer the
[DSL extension](#kotlin-recommended--dsl) above, which wraps this Builder.

Entry point:

```java
static Button.Builder create(Context context, Theme theme, Language language, String text, ButtonStyle style)
```

Example:

```java
Button button = Button.create(context, Theme.FREE_DARK, Language.EN, "Confirm", ButtonStyle.PRIMARY)
        .title(titleMap)
        .descriptionAbove(descAboveMap)
        .description(descMap)
        .margin(0, 10, 0, 10)
        .padding(38, 0, 38, 0)
        .boldText(true)
        .onClick(listener)
        .build();
```

Builder methods (all return `Builder`):

| Method | Description |
|--------|-------------|
| `title(Map<String,String>)` | Optional title above the button |
| `descriptionAbove(Map<String,String>)` | Optional description between title and button |
| `description(Map<String,String>)` | Optional description to the right |
| `marginLeft(int)` / `marginRight(int)` / `marginTop(int)` / `marginBottom(int)` | Individual margins |
| `margin(int left, int top, int right, int bottom)` | All four margins |
| `padding(int left, int top, int right, int bottom)` | Padding in px (overrides default 38px) |
| `boldText(boolean)` | Bold button text |
| `onClick(View.OnClickListener)` | Click listener |
| `build()` | Returns configured `Button` |

## Styles

| Style | Description |
|-------|-------------|
| `ButtonStyle.PRIMARY` | Accent-colored (blue/brown), white text |
| `ButtonStyle.SECONDARY` | Neutral background (gray/white), theme-dependent text |

## Themes

| Enum | Primary BG | Secondary BG |
|------|-----------|-------------|
| `Theme.FREE_LIGHT` | Blue `#4099f3` | White `#ffffff` |
| `Theme.FREE_DARK` | Blue `#4099f3` | Dark gray `#373f4a` |
| `Theme.DREAMER_LIGHT` | Brown `#9c8069` | White `#ffffff` |
| `Theme.DREAMER_DARK` | Brown `#9c8069` | Dark gray `#40444a` |

Colors defined in [ButtonTheme.java](../../voboost-components/src/main/java/ru/voboost/components/button/ButtonTheme.java).

## States

- **Normal** — default appearance
- **Pressed** — instant color change on touch
- **Disabled** — PRIMARY uses 25% alpha background; SECONDARY uses a dedicated disabled color per theme

## Input

Only the pill primitive is a hit target; title and both description areas do not invoke the
listener. A primitive tap invokes the listener with the public `Button` as its `View`, and
`performClick()` uses the standard `View` listener and clickable behavior. Cancelled gestures and disabled buttons
do not invoke the listener.

## Dimensions

All sizes in **pixels** (automotive requirement). Constants in `ButtonDimensions`.

| Property | Value |
|----------|-------|
| Height | 70px |
| Corner radius | 35px (pill shape) |
| Text size | 28px |
| Horizontal padding | 38px |

Width is computed from text + padding (`onMeasure`); there is no minimum width.

## File Structure

```
button/
├── Button.java             # Core implementation
├── ButtonPrimitive.java    # Canvas/touch rendering (package-private)
├── Button.compose.kt               # Compose wrapper
├── ButtonStyle.java         # Style enum
├── ButtonTheme.java         # Colors, dimensions, schemes
├── Button.test/
│   ├── ButtonTestUnit.java  # Unit tests
│   └── ButtonTestVisual.java # Visual regression tests
└── Button.screenshots/     # Reference images
```
