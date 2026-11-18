# Tabs Component

[Screen](component-screen.md) | **Tabs** | [Panel](component-panel.md) | [Section](component-section.md) | [Radio](component-radio.md) | [Checkbox](component-checkbox.md) | [Button](component-button.md) | [Buttons](component-buttons.md) | [Select](component-select.md) | [Dialog](component-dialog.md) | [Popup](component-popup.md) | [Toast](component-toast.md) | [Text](component-text.md) | [Hint](component-hint.md) | [Font](component-font.md) | [Theme](component-theme.md) | [Language](component-language.md)

**English** | [Русский](component-tabs.ru.md)

## Architecture

- **[Tabs.java](../../voboost-components/src/main/java/ru/voboost/components/tabs/Tabs.java)** — Java Custom View: vertical sidebar, animated selection, canvas rendering
- **[Tabs.compose.kt](../../voboost-components/src/main/java/ru/voboost/components/tabs/Tabs.compose.kt)** — Kotlin Compose wrapper

Vertical navigation sidebar with sliding selection indicator.

## Usage

### Java

```java
Tabs tabs = new Tabs(context);
tabs.setTheme(Theme.FREE_LIGHT);
tabs.setLanguage(Language.EN);

tabs.setItems(Arrays.asList(
    new TabItem("store", Map.of("en", "Store", "ru", "Магазин")),
    new TabItem("settings", Map.of("en", "Settings", "ru", "Настройки"))
));
tabs.setSelectedValue("store");

tabs.setOnValueChangeListener(value -> {
    // Handle tab selection
});
```

### Kotlin

```kotlin
val tabs = Tabs(context).apply {
    setTheme(Theme.FREE_LIGHT)
    setLanguage(Language.EN)
    setItems(listOf(
        TabItem("store", mapOf("en" to "Store", "ru" to "Магазин")),
        TabItem("settings", mapOf("en" to "Settings", "ru" to "Настройки"))
    ))
    setSelectedValue("store")
    setOnValueChangeListener { value -> /* handle */ }
}
```

### Compose

```kotlin
var selectedTab by remember { mutableStateOf("store") }

Tabs(
    items = listOf(
        TabItem("store", mapOf("en" to "Store", "ru" to "Магазин")),
        TabItem("settings", mapOf("en" to "Settings", "ru" to "Настройки"))
    ),
    language = Language.EN,
    theme = Theme.FREE_LIGHT,
    value = selectedTab,
    onValueChange = { selectedTab = it }
)
```

## API

### Java

```java
// Configuration
void setItems(List<TabItem> items)
void setTheme(Theme theme)
void setLanguage(Language language)

// Value
void setSelectedValue(String value)
void setSelectedValue(String value, boolean triggerCallback)
String getSelectedValue()
int getSelectedIndex()                         // -1 if no match

// State
Theme getCurrentTheme()
Language getCurrentLanguage()
static int getSidebarWidth()                   // px; layout containers position content against this
int getPaddingBottom()                         // auto: 25 if any tab has more=true, else 5
int getTopPadding()
void setTopPadding(int topPadding)             // px; triggers relayout

// Animation
void setAnimationsEnabled(boolean enabled)     // default true; false = jump without sliding
boolean isAnimationsEnabled()

// Events
void setOnValueChangeListener(OnValueChangeListener listener)
void setOnTabChangeListener(OnTabChangeListener listener)
void addOnTabChangeListener(OnTabChangeListener listener)
void removeOnTabChangeListener(OnTabChangeListener listener)

interface OnValueChangeListener {
    void onValueChange(String value);
}

interface OnTabChangeListener {
    void onTabChanged(int newIndex);
}
```

`setOnTabChangeListener` owns the single consumer listener and replacing or clearing it does not
affect observers registered through `addOnTabChangeListener`. Additional observers are multicast;
adding the same listener instance twice is idempotent. Containers such as Screen use this channel
without taking ownership of the consumer callback. A validated selection is published before
listeners run, so a reentrant listener can make a newer selection without the outer dispatch
overwriting it. Each dispatch uses a listener snapshot; add/remove operations affect later events.

### TabItem

```java
TabItem(String value, Map<String, String> label)
TabItem(String value, Map<String, String> label, boolean enabled)
TabItem(String value, Map<String, String> label, boolean enabled, int marginTop)
TabItem(String value, Map<String, String> label, boolean enabled, int marginTop, boolean more)

String getValue()
Map<String, String> getLabel()
String getText(String langCode)
boolean isEnabled()
int getMarginTop()
boolean hasMore()
boolean isSelected()
```

`TabItem` captures an immutable label snapshot in the caller's iteration order. `getText` resolves
the exact language code, then the first non-null label, then `""`; `value` remains an internal
selection identifier and is never rendered as a localization fallback. `getLabel()` exposes the
immutable snapshot, so later caller-map mutations cannot change displayed text.

> Note: `isSelected()` returns `false` for any `TabItem` created with the plain `new TabItem(...)`
> constructors. Only a `TabItem` built through `Builder.build()` carries a real `selected` flag
> (stored on an anonymous subclass). Use `Tabs.Builder`, which reads `TabItem.isSelected()` to
> auto-select the matching tab on `build()`.

#### Builder

```java
TabItem item = TabItem.create("vehicle", labels)
    .enabled(true)
    .selected(false)
    .marginTop(30)
    .more(true)
    .build();
```

A tab with `more(true)` renders a trailing ">" indicator on the right side of the tab. The
indicator is drawn from 30x30 PNG assets, with a separate pair per theme brightness:
dark themes use `Tabs_theme_dark.png` / `Tabs_theme_dark.Tabs_pressed.png` (copied byte-for-byte
from the original Voyah `icon_menu_more_nor.png` / `icon_menu_more_pre.png`, gray+alpha);
light themes use `Tabs_theme_light.png` / `Tabs_theme_light.Tabs_pressed.png` (from the Voyah
`resc-defaulttheme-simple` light skin, RGBA with `#2d3442` matching the unselected text color).
When the user touches such a tab the indicator switches to the `*_pressed` variant — matches
the original `state_pressed` behavior on the trailing icon. PNGs are loaded lazily from
classpath alongside `Tabs.class`. The text and background of the tab itself do not change on
press — only the more-indicator does.

### Compose Wrapper

```kotlin
@Composable
fun Tabs(
    items: List<TabItem>,
    language: Language,
    theme: Theme,
    value: String,
    onValueChange: (String) -> Unit
)
```

### Builder API

Fluent builder for constructing a fully-configured `Tabs` in one call (Tabs.java:933-1001).
The builder defensively copies the supplied items list at the Builder boundary and rejects null
context/theme/language/items and null item elements there. It resolves the effective selected
value — an explicit `selectedValue(value)`, else the first `TabItem` whose `isSelected()` is
`true` — before constructing or mutating any Tabs state.

Entry point:

```java
static Tabs.Builder create(Context context, Theme theme, Language language, List<TabItem> items)
```

Example:

```java
Tabs tabs = Tabs.create(context, Theme.FREE_LIGHT, Language.EN, items)
        .selectedValue("settings")
        .topPadding(50)
        .animationsEnabled(false)
        .onValueChange(listener)
        .onTabChange(tabListener)
        .build();
```

`selectedValue(value)` is validated against the items at `build()`: an explicit value that
matches no item throws `IllegalArgumentException` before any Tabs View state is mutated.
`topPadding(px)` must be non-negative.

Builder methods (all return `Builder`):

| Method | Description |
|--------|-------------|
| `selectedValue(String)` | Value to auto-select (validated against items at `build()`) |
| `topPadding(int)` | Top padding in px (non-negative); overrides default |
| `animationsEnabled(boolean)` | Enables/disables the selection indicator animation |
| `onValueChange(OnValueChangeListener)` | Value-change callback |
| `onTabChange(OnTabChangeListener)` | Index-change callback |
| `build()` | Returns configured `Tabs`; resolves the effective selected value before mutation |

## Kotlin Tabs DSL (Layer 2)

Kotlin View consumers get a two-phase `tabs { }` entry point in the shared
`ru.voboost.components.dsl` package. Inputs are snapshotted and validated before any View is
attached or any listener installed.

```kotlin
import ru.voboost.components.dsl.tabs

val tabs = tabs(context, theme, language, items, selectedValue = "settings") {
    topPadding(50)
    animationsEnabled(false)
}
```

Validation requires non-empty items, unique item values, a `selectedValue` that exists among
them, and non-negative top padding; on failure the offending value or index is identified and
the build aborts before any mutation. `TabsDsl` does not expose a mutable native `tabs`
property.

## Implementation Details

Animated selection indicator using `ValueAnimator` (400ms) with `OvershootInterpolator` (tension
1.0). The sidebar, selection rectangle, and tab labels are drawn directly on the `Canvas` in
`onDraw`. Sizes are in pixels (automotive requirement); all dimensions live as `public static
final` constants in `TabsTheme` — there is no separate `TabsDimensions` class. Tabs does not
persist its selection across configuration changes; the caller is responsible for restoring state
(e.g. via the Compose `value` parameter or by calling `setSelectedValue`).

### Selection state

`selectedValue` always references an existing item value or is empty — it is never left as an
orphan. `setSelectedValue` with an unknown value (no matching item) clears the selection to `""`
and resets the indicator geometry (`animatedY`/`animatedYInitialized`) so a stale position cannot
paint a ghost indicator; this orphan clear does not fire `OnValueChangeListener`. `setItems` that
no longer contains the current value performs the same clear before layout, and an empty item list
leaves the selection `""`. With duplicate item values, the FIRST matching item owns the indicator
geometry. As defense-in-depth, `drawSelectionIndicator` also guards on `selectedIndex >= 0`, so no
draw path can use stale coordinates.

| Constant (TabsTheme) | Value |
|----------------------|-------|
| `SIDEBAR_WIDTH` | 340 |
| `TAB_ITEM_HEIGHT` | 100 |
| `MORE_SIZE` | 30 |
| `ANIMATION_DURATION` | 400 |

## File Structure

```
tabs/
├── Tabs.java                          # Core implementation
├── Tabs.compose.kt                            # Compose wrapper
├── TabItem.java                       # Data model
├── TabsTheme.java                     # Theme colors and dimensions
├── Tabs_theme_light.png               # Light more-indicator asset
├── Tabs_theme_light.Tabs_pressed.png  # Light more-indicator (pressed)
├── Tabs_theme_dark.png                # Dark more-indicator asset
├── Tabs_theme_dark.Tabs_pressed.png   # Dark more-indicator (pressed)
├── Tabs.test/
│   ├── TabsTestUnit.java              # Unit tests
│   └── TabsTestVisual.java            # Visual regression tests
└── Tabs.screenshots/                  # Reference images
```

## Error Handling

The Tabs component validates input and throws exceptions for invalid arguments:

- `setTheme(null)` → throws `IllegalArgumentException`
- `setLanguage(null)` → throws `IllegalArgumentException`
- `setItems(List<TabItem>)` with null elements → throws `IllegalArgumentException`
- `TabItem(null, label)` → throws `IllegalArgumentException`
- `TabItem(value, null)` → throws `IllegalArgumentException`

Disabled tabs are ignored when selected via touch or `setSelectedValue()`.

## Themes

All variants use a transparent sidebar background; only the selection indicator and text colors
differ.

| Enum | Selected BG | Selected Text | Unselected Text |
|------|-------------|---------------|-----------------|
| `Theme.FREE_LIGHT` | `#bfffffff` (translucent white) | `#4099f3` | `#2d3442` |
| `Theme.FREE_DARK` | `#23272f` | `#4099f3` | `#cacaca` |
| `Theme.DREAMER_LIGHT` | `#ffffff` | `#1a1a1a` | `#2d3442` |
| `Theme.DREAMER_DARK` | `#2a2a2a` | `#ffffff` | `#888888` |

Colors defined in [TabsTheme.java](../../voboost-components/src/main/java/ru/voboost/components/tabs/TabsTheme.java).

## Disabled Tabs

Tabs can be disabled to prevent user interaction:

```java
TabItem enabledTab = new TabItem("settings", labels, true);
TabItem disabledTab = new TabItem("admin", labels, false);

tabs.setItems(Arrays.asList(enabledTab, disabledTab));
```

Disabled tabs:
- Are displayed with gray text
- Do not respond to touch events
- Are ignored when selected via `setSelectedValue()`
- Do not trigger `OnValueChangeListener`
