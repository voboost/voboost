# Screen Component

**Screen** | [Tabs](component-tabs.md) | [Panel](component-panel.md) | [Section](component-section.md) | [Radio](component-radio.md) | [Checkbox](component-checkbox.md) | [Button](component-button.md) | [Buttons](component-buttons.md) | [Select](component-select.md) | [Dialog](component-dialog.md) | [Popup](component-popup.md) | [Toast](component-toast.md) | [Text](component-text.md) | [Hint](component-hint.md) | [Font](component-font.md) | [Theme](component-theme.md) | [Language](component-language.md)

**English** | [Русский](component-screen.ru.md)

## Architecture

- **[Screen.java](../../voboost-components/src/main/java/ru/voboost/components/screen/Screen.java)** — Java ViewGroup: full-screen container, layout management
- **[Screen.compose.kt](../../voboost-components/src/main/java/ru/voboost/components/screen/Screen.compose.kt)** — Kotlin Compose wrapper
- **[ScreenTheme.java](../../voboost-components/src/main/java/ru/voboost/components/screen/ScreenTheme.java)** — Theme colors and dimension/animation constants
- **[ScreenView.java](../../voboost-components/src/main/java/ru/voboost/components/screen/ScreenView.java)** — ScrollView wrapper with rubber-band stretch; wraps Tabs and each Panel

Root container that manages Tabs (sidebar) and Panel (content area) layout.

## Usage

### Java

```java
Screen screen = new Screen(context);
screen.setTheme(Theme.FREE_LIGHT);
screen.setOffsetX(175);
screen.setOffsetY(50);
screen.setGapX(0);

// Install panels before tabs so the selected tab can activate content immediately
Panel[] panels = new Panel[] { panel1, panel2 };
screen.setPanels(panels);
Tabs tabs = new Tabs(context);
screen.setTabs(tabs);

// Screen lift
screen.setOnScreenLiftListener(state -> {
    // SCREEN_LOWERED (1) or SCREEN_RAISED (2)
});
```

### Kotlin

```kotlin
val screen = Screen(context).apply {
    setTheme(Theme.FREE_LIGHT)
    setPanels(arrayOf(panel1, panel2))
    setTabs(tabs)
}
```

### Compose

```kotlin
Screen(
    theme = Theme.FREE_LIGHT,
    language = Language.EN,
    tabs = listOf(
        TabItem("store", mapOf("en" to "Store", "ru" to "Магазин")),
        TabItem("settings", mapOf("en" to "Settings", "ru" to "Настройки"))
    ),
    panels = arrayOf(storePanel, settingsPanel),
    selectedTab = "store",
    onTabSelected = { /* handle tab change */ },
    offsetX = 175,
    offsetY = 50,
    gapX = 0,
    screenLiftState = 2,
    onScreenLift = { state -> /* handle */ }
)
```

## API

### Java

```java
// Theme & Language
void setTheme(Theme theme)                   // throws if null
Theme getCurrentTheme()
void setLanguage(Language language)          // throws if null
Language getCurrentLanguage()

// Layout
void setOffsetX(int offsetX)         // content X offset (px)
void setOffsetY(int offsetY)         // content Y offset (px)
void setGapX(int gapX)               // gap between Tabs and Panel (px)
int getOffsetX()
int getOffsetY()
int getGapX()
int getAvailableHeight()             // fixed content height (ScreenTheme.AVAILABLE_HEIGHT = 720)

// Components
void setTabs(Tabs tabs)
Tabs getTabs()
ScreenView getTabsScrollView()       // ScrollView wrapping the Tabs
void setPanels(Panel[] panels)       // null clears; entries must be non-null and identity-unique
Panel[] getPanels()                  // defensive copy
void setActivePanel(int index)       // invalid index clears logical and visible selection
Panel getActivePanel()
ScreenView getPanelWrapper(int index) // wrapper for a panel (null until first activation)

// Animations
void setAnimationsEnabled(boolean enabled) // disables panel/tab animations (e.g. pixel tests)
boolean isAnimationsEnabled()

// Screen lift
void onScreenLift(int state)         // SCREEN_LOWERED=1, SCREEN_RAISED=2
int getScreenLiftState()
void setOnScreenLiftListener(OnScreenLiftListener listener)

// Toast
void showToast(Toast toast)               // host a built Toast; Screen owns attach/show/removal
void showToast(String text, long duration) // convenience; delegates through showToast(Toast)
void dismissToast()
Toast getCurrentToast()

// Propagation
void propagateTheme(Theme theme)
void propagateLanguage(Language language)
```

### Compose Wrapper

```kotlin
@Composable
fun Screen(
    tabs: List<TabItem>,
    panels: Array<Panel>,
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    theme: Theme,
    language: Language,
    offsetX: Int = 175,
    offsetY: Int = 50,
    gapX: Int = 0,
    screenLiftState: Int = 2,
    onScreenLift: ((Int) -> Unit)? = null,
    onScreenReady: ((ru.voboost.components.screen.Screen) -> Unit)? = null
)
```

`tabs`, `panels`, `selectedTab`, `onTabSelected`, `theme` and `language` are required and have no defaults. `onScreenReady` exposes the underlying `Screen` view for imperative interop (e.g. calling `showToast`).
The Compose factory attaches one stable empty Tabs identity. Its first update applies Screen state,
installs panels, configures Tabs items/listener, and only then activates selection; each initial prop
is applied once. `onScreenReady` runs after that fully configured first update.

The wrapper installs panels before activating tabs. Recomposition applies content changes without
reinstalling equal arrays or listeners: current callbacks are delivered exactly once, and a nullable
`onScreenLift` is removed from the native view when it becomes `null`. Releasing the composition
detaches native listeners, tabs, and panels.

> **`offsetX` default differs by API:** the Compose wrapper defaults to `175`, while the Java `Screen` instance defaults to `ScreenTheme.DEFAULT_OFFSET_X = 145`. Pass `offsetX` explicitly if the value matters.

## Builder API

Fluent builder for constructing a fully-configured `Screen` in one call.

Entry points:

```java
static Screen.Builder create(Context context, Theme theme)
static Screen.Builder create(Context context, Theme theme, Language language) // preferred for the Kotlin DSL
```

`create(context, theme)` remains compatible. The preferred `create(context, theme, language)`
overload applies theme and language on `build()` and is the entry point the two-phase Kotlin DSL
uses. Required arguments reject null immediately.

Example:

```java
Screen screen = Screen.create(context, Theme.FREE_LIGHT, Language.EN)
        .offsets(175, 50, 0)
        .animationsEnabled(false)
        .tabs(tabs)
        .panels(new Panel[] { panel1, panel2 })
        .backgroundColorHex("#ffffff")
        .onScreenLift(listener)
        .build();
```

`offsets(x, y, gapX)` sets the content offsets and the Tabs/Panel gap together; all three must
be non-negative and a negative value throws before any of the three is applied. `build()`
applies offsets and the animation toggle before panels and tabs, sets panels before tabs so
`setActivePanel()` can find them, and returns a fully-configured, unattached `Screen`.

Builder methods (all return `Builder`):

| Method | Description |
|--------|-------------|
| `backgroundColor(int)` | Solid background color |
| `backgroundColorHex(String)` | Background color from hex string (e.g. `"#ffffff"`) |
| `offsets(int, int, int)` | Content offsets `offsetX`, `offsetY`, and `gapX` (all non-negative) |
| `animationsEnabled(boolean)` | Enables/disables panel transition animations |
| `tabs(Tabs)` | Tabs sidebar component |
| `panels(Panel[])` | Array of panels |
| `onScreenLift(OnScreenLiftListener)` | Screen lift listener |
| `build()` | Returns configured `Screen` (applies offsets/animations before panels/tabs) |

## Kotlin Screen DSL (Layer 2)

Kotlin View consumers get a two-phase `screen { }` aggregate DSL plus `panel`/`dialog`/`toast`
factories in the shared `ru.voboost.components.dsl` package. The DSL executes the receiver
lambda into a private specification, snapshots and validates the full aggregate, and only then
invokes the Java Builders and attaches Views. Consumer construction contains no
`.create()`/`.build()`; runtime calls such as `screen.setTheme()` remain valid.

```kotlin
import ru.voboost.components.dsl.screen
import ru.voboost.components.dsl.panel

val screen = screen(context, theme, language) {
    offsets(175, 50, 0)
    animationsEnabled(false)
    panels.forEach { panel(it) }
    tabs(items, selectedValue = "settings", onValueChange = { value -> /* ... */ }) {
        topPadding(50)
        animationsEnabled(false)
    }
    onScreenLift { state -> /* SCREEN_LOWERED / SCREEN_RAISED */ }
}
```

Validation runs before any View is attached or any listener installed and requires: exactly one
`tabs {}` (a second is an error, never replacement); non-empty, unique tab values; a
`selectedValue` that exists among them; panel count equals tab count; and Panels that are
identity-unique and unattached. On failure the offending value or index is identified and the
build aborts before any mutation. `ScreenDsl` does not expose a mutable native `screen`
property; Layer 2 selection is initial, uncontrolled native state.

## Layout Structure

Screen positions children:
1. **Tabs** — at `offsetX`, full height, inside a `ScreenView` ScrollView
2. **Panel** — after Tabs + `gapX`, at `offsetY` from top, inside a `ScreenView` wrapper

Theme and language are routed through Screen's logical `Tabs`, `Panel[]`, and live `Toast` fields,
not through wrapper views. Every explicit Screen setter calls each current logical child once.
New Tabs and only newly introduced Panel identities inherit current non-null state once; retained
panels receive no extra call on reorder, while a removed and later re-added panel is new again.

Calling `setPanels` repeatedly with the same instances is idempotent. Reordering preserves the active
panel by identity and moves its existing wrapper; removing that panel clears the selection. A valid
`setActivePanel` attaches exactly one visible wrapper, while an invalid index clears both
`getActivePanel()` and every attached panel wrapper. The supplied array and arrays returned by
`getPanels()` cannot mutate Screen's owned ordering. A null array clears the panels. A null entry
or the same `Panel` instance at multiple indexes is rejected with `IllegalArgumentException`
before the current hierarchy or selection changes. `setPanels` also preflights every parent before
any mutation: a Panel already attached to another parent is rejected atomically, while a Panel may
be reused when its parent is the wrapper already owned by this Screen.

`setTabs` performs the same preflight before removing the current Tabs. Tabs attached to a foreign
parent are rejected with `IllegalArgumentException`, leaving the current Tabs and wrapper intact;
reusing this Screen's current Tabs is allowed.

Screen observes tab indexes through `Tabs.addOnTabChangeListener`; it never replaces the consumer
listener configured through `Tabs.setOnTabChangeListener`. It resolves the current Tabs selection
instead of trusting a potentially stale reentrant event index. Detach removes the internal Tabs
observer; reattach restores it and synchronizes the current selection.

Panel-transition completion is session-owned: cancellation, a newer selection, or Screen detach
cannot let an older animator remove or translate the latest active wrapper.

## File Structure

```
screen/
├── Screen.java          # Core implementation (ViewGroup)
├── Screen.compose.kt            # Compose wrapper
├── ScreenTheme.java     # Theme colors, dimensions, animation constants
├── ScreenView.java      # Rubber-band ScrollView wrapping Tabs and Panels (internal helper; not part of the public Screen API)
├── Screen.test/         # Tests
└── Screen.screenshots/  # Reference images
```

## Limitations

- Screen must be measured before animations work correctly
- Panel transitions require valid Screen dimensions (width > 0, height > 0)
- Rapid panel switching (faster than 300ms) may cancel previous animations
- Toast messages are not persistent across configuration changes
- Detaching Screen terminally releases and removes its current Toast and clears `getCurrentToast()`;
  reattachment starts with no stale Toast child
- Maximum number of panels is limited by available memory

## Performance Considerations

- Panel transition animations run on the UI thread for 300ms
- Each panel is measured and laid out on every transition
- Theme and language propagation snapshots the current logical children; child setters own depth
- For large numbers of child components, consider lazy loading
- Toast views are added to the Screen view hierarchy and removed on dismiss or Screen detach

## Troubleshooting

### Panel transitions don't animate
- Ensure Screen has been measured before calling setActivePanel
- Check that panel dimensions are valid (width > 0, height > 0)
- Verify that Screen has valid offsetX and offsetY values

### Panels not visible
- Ensure setActivePanel was called with a valid index; an invalid index deliberately clears content
- Check that panels array was set via setPanels()
- Verify that theme has been set before setting panels

### Toast not showing
- Ensure theme has been set before showing toast
- Check that toast duration is valid (DURATION_SHORT or DURATION_LONG)
- Verify that Screen is attached to window

### Memory leaks
- Ensure tabs listeners are properly removed when replacing tabs
- Check that panel references are cleared when no longer needed
- Verify that toast views are removed after dismiss

## Thread Safety

The Screen component is **not thread-safe**. All methods must be called on the UI (main) thread.

### Main-thread methods

All mutable APIs and ordinary getters must be called on the UI thread, including:

- `setTheme(Theme)` - modifies view hierarchy
- `setLanguage(Language)` - modifies view hierarchy
- `setOffsetX(int)`, `setOffsetY(int)`, `setGapX(int)` - trigger layout
- `setTabs(Tabs)` - modifies view hierarchy
- `setPanels(Panel[])` - modifies internal state
- `setActivePanel(int)` - triggers animations and layout changes
- `onScreenLift(int)` - modifies state and triggers layout
- `setOnScreenLiftListener(OnScreenLiftListener)` - modifies listener reference
- `showToast(String, long)` and `showToast(Toast)` - modify view hierarchy
- `dismissToast()` - modifies view hierarchy
- `getCurrentTheme()`, `getCurrentLanguage()`, layout/state getters, `getPanels()`, `getTabs()`, and `getCurrentToast()` - read mutable UI state

### Animation thread-safety
Panel transition animations run on the UI thread using ObjectAnimator. Rapid calls to `setActivePanel()` are safe - the current animation will be cancelled before starting a new one.

## Compose Usage

Screen provides a modern Compose-friendly API:

```kotlin
@Composable
fun MyScreen() {
    var selectedTab by remember { mutableStateOf("settings") }

    Screen(
        tabs = listOf(
            TabItem("settings", mapOf("en" to "Settings")),
            TabItem("profile", mapOf("en" to "Profile"))
        ),
        panels = arrayOf(createSettingsPanel(), createProfilePanel()),
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        theme = Theme.FREE_LIGHT,
        language = Language.EN
    )
}
```

The Compose wrapper automatically:
- Creates Tabs component
- Installs panels before activating the Tabs selection and keeps native content aligned with `selectedTab`
- Applies changed panel lists and removes stale wrappers
- Delivers the latest callbacks and clears nullable listeners
- Cleans up listeners and child views when released

### Keyed Compose content API (Layer 3 — preferred)

The preferred keyed `Screen(...)` overload declares the whole tree through a non-composable
`ScreenContentScope` lambda instead of pre-built `Panel[]`. The implementation builds a fresh
immutable description tree on every recomposition, validates it before any native mutation, and
reconciles it against the native Screen so stable key+type nodes reuse their native instances
across reorder and recomposition:

```kotlin
@Composable
fun MyScreen() {
    var selected by remember { mutableStateOf("settings") }

    Screen(
        selectedPanelKey = selected,
        onSelectedPanelChange = { selected = it },
        theme = Theme.FREE_LIGHT,
        language = Language.EN,
    ) {
        panel(TabItem("settings", mapOf("en" to "Settings"))) {
            section("sound", mapOf("en" to "Sound")) {
                checkbox("mute", checked = false, onCheckedChange = {})
                radio(
                    key = "mode",
                    buttons = listOf(RadioButton("stereo", mapOf("en" to "Stereo"))),
                    value = "stereo",
                    onValueChange = {},
                )
            }
        }
        panel(TabItem("profile", mapOf("en" to "Profile"))) {
            section("account", mapOf("en" to "Account")) {
                button("signOut", "Sign out", onClick = {})
                nativeView(
                    key = "avatar",
                    factory = { ctx -> android.widget.ImageView(ctx) },
                    update = { /* apply latest state */ },
                    onRelease = { /* run exactly once when removed */ },
                )
            }
        }
    }
}
```

Identity and lifecycle:
- Panel identity is each `TabItem.value` (stable and non-empty); section, child, and row-button
  nodes are keyed by a stable `Any`.
- Stable key + stable node type reuses the same native instance across reorder and recomposition;
  stable key + changed type releases the old instance and creates the new one after validation.
- One stable native listener adapter per node reads the latest callback, so swapping a lambda on
  recomposition dispatches through the same retained adapter.
- Controlled values (`selectedPanelKey`, radio/select values, checkbox checked, nullable props)
  are authoritative on reconciliation: a native touch may update optimistically, but if caller
  state is unchanged the next recomposition restores the supplied value.
- `nativeView` is an escape hatch for application-owned Android Views only — not for constructing
  Voboost components and not a general Compose slot. Its `factory` runs once, `update` on every
  reconciliation, and `onRelease` exactly once.
- `horizontalRow` is deliberately narrow (the real two-weighted-button demo row) and is not a
  general Compose layout system.
- `onViewCreated` fires exactly once per newly created native Screen, after the first update
  installs initial properties/listeners/tree; it is not replayed on recomposition.
- Validation throws `IllegalArgumentException` with the parent path and offending key before any
  native View is created or mutated (unknown `selectedPanelKey`, duplicate/empty keys).

The array-based `Screen(...)` overload above remains available during the compatibility period.
