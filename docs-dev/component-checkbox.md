# Checkbox Component

[Screen](component-screen.md) | [Tabs](component-tabs.md) | [Panel](component-panel.md) | [Section](component-section.md) | [Radio](component-radio.md) | **Checkbox** | [Button](component-button.md) | [Buttons](component-buttons.md) | [Select](component-select.md) | [Dialog](component-dialog.md) | [Popup](component-popup.md) | [Toast](component-toast.md) | [Text](component-text.md) | [Hint](component-hint.md) | [Font](component-font.md) | [Theme](component-theme.md) | [Language](component-language.md)

**English** | [Русский](component-checkbox.ru.md)

## Architecture

- **[Checkbox.java](../../voboost-components/src/main/java/ru/voboost/components/checkbox/Checkbox.java)** — Java container (LinearLayout): layout, state; delegates canvas rendering, animations, and touch to package-private [CheckboxPrimitive.java](../../voboost-components/src/main/java/ru/voboost/components/checkbox/CheckboxPrimitive.java)
- **[Checkbox.compose.kt](../../voboost-components/src/main/java/ru/voboost/components/checkbox/Checkbox.compose.kt)** — Kotlin Compose wrapper: lightweight AndroidView integration

## Usage

### Java

```java
Checkbox checkbox = new Checkbox(context);
checkbox.setTheme(Theme.FREE_DARK);
checkbox.setChecked(true);
checkbox.setOnCheckedChangeListener(isChecked -> {
    // Handle change
});
parentLayout.addView(checkbox);
```

### Compose

```kotlin
var checked by remember { mutableStateOf(false) }

Checkbox(
    theme = Theme.FREE_DARK,
    checked = checked,
    onCheckedChange = { checked = it }
)
```

### Kotlin (recommended — DSL)

The receiver-lambda extensions defined in `Section.kt` wrap the Java Builder so Kotlin callers
never write `.create()`/`.build()`. The lambda runs against `Checkbox.Builder`, so optional
properties are set as Builder method calls (`label(map)`, `margin(l, t, r, b)`,
`onCheckedChange { }`).

```kotlin
section.addCheckbox(Theme.FREE_DARK, Language.EN, true) {
    label(labelMap)
    margin(0, 10, 0, 10)
    onCheckedChange { /* Handle change */ }
}
```

The public checkbox exposes one accessibility toggle node with its localized label and current checkable, checked and enabled state. Its private drawing primitive and duplicate label are excluded from traversal. Accessibility clicks use the existing full-row click and callback path; disabled clicks do not toggle. These semantics support an independent UI observer without changing layout, drawing or touch behavior.

## API

### Java Custom View

```java
void setTheme(Theme theme)
Theme getCurrentTheme()

void setChecked(boolean checked)        // instant, no animation
void setCheckedAnimated(boolean checked) // with animation
boolean isChecked()

void setEnabled(boolean enabled)
boolean isEnabled()

void setLabel(Map<String, String> label)         // localized label text
void setDescription(Map<String, String> desc)    // localized description below
void setLabelBold(boolean bold)                   // title-checkbox bold label

void setMargin(int left, int top, int right, int bottom)  // managed by parent Section
int getMarginLeft() / getMarginTop() / getMarginRight() / getMarginBottom()
boolean isMarginSet()

void setOnCheckedChangeListener(OnCheckedChangeListener listener)

interface OnCheckedChangeListener {
    void onCheckedChange(boolean isChecked);
}
```

`label` and `description` setters/Builder methods capture immutable insertion-order snapshots.
Resolution uses the exact language, then the first non-null captured value, then `""`; later
caller-map mutation cannot alter the label.

### Builder API

`Checkbox.create(...)` returns a `Builder` for fluent construction. This is the Java entry
point; in Kotlin prefer the DSL extension shown in
[Kotlin (recommended — DSL)](#kotlin-recommended--dsl), which wraps this Builder.

```java
Checkbox.create(context, theme, language, checked)
    .label(Map<String, String>)            // optional localized label
    .description(Map<String, String>)      // optional localized description
    .margin(left, top, right, bottom)      // or marginTop/Bottom/Left/Right
    .onCheckedChange(listener)             // optional checked-change callback
    .build();
```

Required factory args: `context`, `theme`, `language`, `checked` (boolean). All chained methods are optional.

### Compose Wrapper

```kotlin
@Composable
fun Checkbox(
    theme: Theme,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    label: Map<String, String>? = null,
    labelBold: Boolean = false,
    description: Map<String, String>? = null,
    language: Language? = null,
    onViewCreated: ((Checkbox) -> Unit)? = null,
)
```

Nullable props are controlled values: a non-null value applies it and a later `null` clears or
resets the corresponding native state on the same `Checkbox` instance. `label`/`description = null`
hide those text views; `language = null` calls `resetLanguage()` (localized text falls back to
"en"). `labelBold` is applied unconditionally (`false` is the default) and switches the label to the
bold title-checkbox style. `onViewCreated` is an at-most-once creation hook — it is never replayed
on recomposition.

## Rendering

## Input

The whole checkbox row is a native hit target and delegates through `performClick()` to toggle
exactly once. A cancelled gesture, a disabled checkbox, or an `UP` without a tracked `DOWN` does
not change the value or invoke the listener. An `UP` outside the row clears the gesture without a
toggle; a programmatic `performClick()` also clears a tracked row gesture.

`CheckboxPrimitive.onDraw` paints 3 layers (bitmap-based, not vector):
1. **Track background** — bitmap from `CheckboxPrimitiveBackground.getBackground(isDark)`, centered on the 83x42 track, drawn with `disabledAlpha` when disabled (OFF color baked into the asset)
2. **Mask** — solid rounded rect filled with `trackOnEnd` color; alpha is modulated by animation progress (`animationProgress`, clamped 0..1), only drawn when progress > 0. Drawn inside a 82x40 inset rect (1px inset), corner radius = `MASK_HEIGHT_PX / 2`
3. **Knob** — bitmap from `CheckboxPrimitiveKnob.getBitmap(isDark)`, centered on a computed anchor whose X interpolates from `KNOB_PADDING_PX` to `width - padding - diameter - 1` by `animationProgress`; X is driven by an `OvershootInterpolator` (tension 1.0). Knob alpha multiplied by `disabledAlpha` (and `testMaskAlpha` when frozen for tests)

## Dimensions

| Property | Value |
|----------|-------|
| Width | 83px (TRACK_WIDTH_PX) |
| Height | 42px (TRACK_HEIGHT_PX) |
| Corner radius | 21px |
| Knob size | 34px (light) / 40px (dark) |
| Knob padding | 5px |
| Mask height | 40px (MASK_HEIGHT_PX) |
| Animation duration | 400ms |

## File Structure

```
checkbox/
├── Checkbox.java            # Core implementation
├── CheckboxPrimitive.java       # Canvas/touch rendering (package-private)
├── CheckboxPrimitiveBackground.java # Track background bitmap (package-private)
├── CheckboxPrimitiveKnob.java   # Knob bitmap (package-private)
├── Checkbox.compose.kt              # Compose wrapper
├── CheckboxTheme.java       # Colors, dimensions, schemes
├── Checkbox.test/
│   ├── CheckboxTestUnit.java
│   └── CheckboxTestVisual.java
└── Checkbox.screenshots/
```
