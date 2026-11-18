# Select Component

[Screen](component-screen.md) | [Tabs](component-tabs.md) | [Panel](component-panel.md) | [Section](component-section.md) | [Radio](component-radio.md) | [Checkbox](component-checkbox.md) | [Button](component-button.md) | [Buttons](component-buttons.md) | **Select** | [Dialog](component-dialog.md) | [Popup](component-popup.md) | [Toast](component-toast.md) | [Text](component-text.md) | [Hint](component-hint.md) | [Font](component-font.md) | [Theme](component-theme.md) | [Language](component-language.md)

**English** | [Русский](component-select.ru.md)

## Architecture

- **[Select.java](../../voboost-components/src/main/java/ru/voboost/components/select/Select.java)** — Public container (LinearLayout): title/description text + vertical layout; delegates trigger/popup to SelectPrimitive
- **[SelectPrimitive.java](../../voboost-components/src/main/java/ru/voboost/components/select/SelectPrimitive.java)** — Internal canvas trigger (pill + text + arrow); owns and opens the popup
- **[Select.compose.kt](../../voboost-components/src/main/java/ru/voboost/components/select/Select.compose.kt)** — Compose wrapper
- **[SelectWheel.java](../../voboost-components/src/main/java/ru/voboost/components/select/SelectWheel.java)** — 3D cylindrical picker (Camera/Matrix transforms)
- **[SelectPopup.java](../../voboost-components/src/main/java/ru/voboost/components/select/SelectPopup.java)** — Internal: Popup + WheelView integration
- **[SelectOption.java](../../voboost-components/src/main/java/ru/voboost/components/select/SelectOption.java)** — Data model (value + localized label)

## Usage

### Java

```java
Select select = new Select(context);
select.setOptions(Arrays.asList(
    new SelectOption("auto", Map.of("en", "Automatic", "ru", "Авто")),
    new SelectOption("manual", Map.of("en", "Manual", "ru", "Ручной"))
));
select.setLanguage(Language.EN);
select.setTheme(Theme.FREE_DARK);
select.setSelectedValue("auto");
select.setOnValueChangeListener(newValue -> {
    // Handle selection
});
```

### Compose

```kotlin
var selected by remember { mutableStateOf("auto") }

Select(
    options = listOf(
        SelectOption("auto", mapOf("en" to "Automatic", "ru" to "Авто")),
        SelectOption("manual", mapOf("en" to "Manual", "ru" to "Ручной"))
    ),
    language = Language.EN,
    theme = Theme.FREE_DARK,
    value = selected,
    onValueChange = { selected = it }
)
```

The Compose wrapper installs one remembered native value-change listener and always reaches the
latest `onValueChange` lambda through `rememberUpdatedState`; the update block never allocates a
new native listener per recomposition. Optional localized text props (`title`, `descriptionAbove`,
`description`) and popup strings (`confirmText`, `cancelText`) are controlled values: `null`
clears or resets the corresponding native state. `mode` (`Select.Mode.FLAT` or
`Select.Mode.CURVED`, default `CURVED`) selects the wheel rendering style and is applied
unconditionally. `onViewCreated` is an at-most-once creation hook — it is never replayed on
recomposition.

### Kotlin (recommended — DSL)

The receiver-lambda extensions defined in `Section.kt` wrap the Java Builder so Kotlin callers
never write `.create()`/`.build()`. The lambda runs against `Select.Builder`, so optional
properties are set as Builder method calls (`title(map)`, `margin(l, t, r, b)`,
`onValueChange { }`).

```kotlin
section.addSelect(Theme.FREE_DARK, Language.EN, options, "auto") {
    title(titleMap)
    margin(0, 10, 0, 10)
    onValueChange { /* Handle selection */ }
}
```

## Public API

### Options and value

- `setOptions(List<SelectOption>)` — option list (null treated as empty)
- `setSelectedValue(String)` — selected value (empty strings ignored)
- `getSelectedValue()` — current value
- `setOnValueChangeListener(OnValueChangeListener)` — value change callback

### Text (optional, localized `Map<String,String>`)

- `setTitle(Map<String,String>)` — title above the trigger
- `setDescriptionAbove(Map<String,String>)` — description above the trigger
- `setDescription(Map<String,String>)` — description to the right of the trigger

These setters, their Builder counterparts, popup button maps, and `SelectOption` labels capture
insertion-order snapshots. Resolution uses the exact language, then the first non-null captured
value, then `""`; SelectOption never falls back to its internal value.

### Wheel mode and popup buttons

- `setMode(Mode)` — `Mode.FLAT` (no curve, no fade) or `Mode.CURVED` (default; 3D curve + atmospheric fade)
- `setConfirmText(Map<String,String>)` — popup confirm-button text (null resets to default)
- `setCancelText(Map<String,String>)` — popup cancel-button text (null resets to default)

### Layout

- `setMargin(int left, int top, int right, int bottom)` — outer margins (consumed by parent Section)

## Builder API

`Select.create(context, theme, language, options, selectedValue)` returns a `Select.Builder`
for fluent construction. This is the Java entry point; in Kotlin prefer the DSL extension
shown in [Kotlin (recommended — DSL)](#kotlin-recommended--dsl), which wraps this Builder.

```java
Select select = Select.create(context, Theme.FREE_DARK, Language.EN, options, "auto")
    .title(Map.of("en", "Drive mode", "ru", "Режим"))
    .mode(Select.Mode.CURVED)
    .confirmText(Map.of("en", "Confirm", "ru", "Подтвердить"))
    .cancelText(Map.of("en", "Cancel", "ru", "Отмена"))
    .onValueChange(listener)
    .build();
```

Builder methods: `title`, `descriptionAbove`, `description`, `marginTop`, `marginBottom`, `marginLeft`, `marginRight`, `margin`, `onValueChange`, `mode`, `confirmText`, `cancelText`, `build`.

## Wheel (FLAT / CURVED)

`Select.setMode(Mode.FLAT|CURVED)` (default CURVED). CURVED: curved 3D rotation +
atmospheric fade — the selected item is large and bright, the side items recede
into shadow (3 clear + 2 faded). FLAT: no curve, no fade (matches the production
WheelDefault). The popup has explicit Confirm/Cancel buttons: scrolling only moves
the wheel; Confirm commits the value. SelectWheel matches the original WheelView
implementation:

| Feature | Implementation | Production use |
|---------|---------------|----------------|
| **3D curve** | Camera + Matrix: rotateX + translate Z | CURVED only |
| **Scrolling** | Scroller + VelocityTracker: fling, snap, inertia | Always |
| **Atmospheric** | Alpha fade by distance from center | CURVED only |
| **Cyclic mode** | Infinite scrolling with wrap-around | **Disabled** (non-cyclic: first→last) |
| **Indicator** | Horizontal lines above/below selected | **Disabled** |
| **Curtain** | Semi-transparent highlight on selected | **Disabled** |
| **Selected style** | Larger font, bold, distinct color | Always |

SelectWheel supports cyclic scrolling, indicator lines and a curtain highlight
as class capabilities, but `SelectPopup.applyMode()` disables all three in
production: the wheel is non-cyclic and selection is shown by size + color
(larger font, bold, distinct color) without indicator lines or a curtain.

An open popup owns an immutable labels/values snapshot. Updating options,
language, theme, mode, or popup button text dismisses that session; reopening
uses the new configuration. Confirm first stops and snaps a settling wheel, so
it cannot commit a stale position; it consumes the session callback and snapshot
before outward delivery, so the same session cannot confirm twice. Changing
language also remeasures the trigger because the localized label can have a
different width.

The trigger opens the popup only on an in-bounds `ACTION_UP`. Wheel taps invoke
click handling only on `ACTION_UP`; a drag or `ACTION_CANCEL` settles and snaps
the wheel instead. Every popup dismissal (including outside dismissal and
detach) clears its selection callback and session snapshot before a late Confirm
can commit. Equal normalized options/configuration/selected value are idempotent;
an actual change dismisses the session. Wheel detach clears gesture state and
releases parent-interception suppression.

## File Structure

```
select/
├── Select.java             # Public container (LinearLayout)
├── SelectPrimitive.java    # Internal canvas trigger
├── Select.compose.kt               # Compose wrapper
├── SelectOption.java        # Data model
├── SelectWheel.java         # 3D WheelView
├── SelectPopup.java         # Internal popup
├── SelectTheme.java         # Colors, dimensions
├── Select_arrow_*.png       # Trigger arrow assets (one per theme: free-light/dark, dreamer-light/dark)
├── Select.test/
│   ├── SelectTestUnit.java
│   └── SelectTestVisual.java
└── Select.screenshots/
```
