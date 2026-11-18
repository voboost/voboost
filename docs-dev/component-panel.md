# Panel Component

[Screen](component-screen.md) | [Tabs](component-tabs.md) | **Panel** | [Section](component-section.md) | [Radio](component-radio.md) | [Checkbox](component-checkbox.md) | [Button](component-button.md) | [Buttons](component-buttons.md) | [Select](component-select.md) | [Dialog](component-dialog.md) | [Popup](component-popup.md) | [Toast](component-toast.md) | [Text](component-text.md) | [Hint](component-hint.md) | [Font](component-font.md) | [Theme](component-theme.md) | [Language](component-language.md)

**English** | [Русский](component-panel.ru.md)

## Architecture

- **[Panel.java](../../voboost-components/src/main/java/ru/voboost/components/panel/Panel.java)** — Java ViewGroup: vertical `LinearLayout` container for Section components
- **[Panel.compose.kt](../../voboost-components/src/main/java/ru/voboost/components/panel/Panel.compose.kt)** — Kotlin Compose wrapper
- **[Panel.kt](../../voboost-components/src/main/java/ru/voboost/components/panel/Panel.kt)** — Kotlin DSL extensions (`addSection`)

Java handles all logic. Kotlin wrapper only bridges to Compose.

## Purpose

Panel is a **transparent container** designed to:

- Group one or more `Section` components vertically
- Enable smooth panel transitions in `Screen` via `Screen.setActivePanel()`
- Apply compact mode (top padding) and optional background image for compact layouts
- Propagate theme and language to child components

**Scrolling:** Panel does **not** provide scrolling. Vertical scrolling is the responsibility of the surrounding `ScreenView` in `Screen`.

## Usage

### Java

```java
// Simple case (single Section)
Panel panel = new Panel(context);
panel.setTheme(Theme.FREE_LIGHT);

Section section = new Section(context);
section.setTitle(titleMap);
section.addView(radio);
panel.addView(section);

// Complex case (multiple Sections)
Panel panel = new Panel(context);
for (int i = 0; i < sectionCount; i++) {
    Section section = new Section(context);
    section.setTitle(titleMap);
    section.addView(radio);
    panel.addView(section);
}
// Place the Panel inside a ScreenView; scrolling is handled by ScreenView, not Panel.
```

### Java Builder (preferred for new Java code)

`Panel.create(...)` returns a single-use `Builder`. Sections are validated (non-null,
identity-unique, no foreign parent) **before** any attach, so a failed build never leaves a
partially attached prefix. The supplied `List` is snapshotted and never mutated. The built
Panel is unattached; `Screen` owns the attach.

```java
Panel panel = Panel.create(context, Theme.FREE_LIGHT, Language.EN)
        .compact(true)
        .image(backgroundBitmap)
        .sections(sections)   // snapshots the list; or use addSection(...) per child
        .build();
// panel is configured and unattached; Screen takes ownership via setPanels(...).
```

A second `build()` on the same Builder throws `IllegalStateException`.

### Kotlin (recommended — DSL)

The receiver-lambda extension `addSection(theme, language, title) { }` defined in `Panel.kt`
wraps the Java `Section.Builder` so Kotlin callers never write `.create()`/`.build()`. The
lambda runs against `Section.Builder`, so optional properties are set as Builder method calls
(`titleCheckbox(true)`, `infoText(map)`). Inside the built Section, add children with the
Section DSL overloads (`addButton(...) { }`, `addRadio(...) { }`, ...).

```kotlin
val panel = Panel(context).apply { setTheme(Theme.FREE_LIGHT) }

panel.addSection(Theme.FREE_DARK, Language.EN, mapOf("en" to "Settings")) {
    titleCheckbox(true)
    addRadio(Theme.FREE_DARK, Language.EN, buttons, "en") { /* ... */ }
}
```

For standalone construction, use the `panel()` factory from `PanelFactory.kt`:

```kotlin
val panel = panel(context, Theme.FREE_DARK, Language.EN) {
    compact(true)
    addSection(section)
}
```

### Kotlin (View API)

```kotlin
val panel = Panel(context).apply {
    setTheme(Theme.FREE_LIGHT)
    addView(section)
}
```

### Compose

```kotlin
Panel(theme = Theme.FREE_LIGHT, language = Language.EN)
```

## API

### Java Custom View

```java
// Theme
void setTheme(Theme theme)                 // throws if null
Theme getCurrentTheme()

// Language
void setLanguage(Language language)        // throws if null
Language getCurrentLanguage()

// Propagation (to child components)
void propagateTheme(Theme theme)
void propagateLanguage(Language language)

// Background image (compact mode) — drawn behind transparent gaps between Sections
Bitmap getImage()
void setImage(Bitmap image)
int getImageWidth()                         // px, PanelTheme.IMAGE_WIDTH
int getImageMarginRight()                   // px, negative = extends beyond edge

// Compact mode — applies COMPACT_TOP_MARGIN as top padding, PADDING_BOTTOM as bottom padding
boolean isCompact()
void setCompact(boolean compact)
```

`addView`, `removeView`, `removeViewAt` are overridden to call `updateChildMargins()` after the
super call; `removeAllViews` is overridden without it. `updateChildMargins()` sets the
`includeBottomMargin` flag on Section children so that only the last Section omits its bottom
margin (Panel.java:82-119).

### Compose Wrapper

```kotlin
@Composable
fun Panel(theme: Theme, language: Language = Language.EN)
```

Kotlin extensions (Panel.kt):

```kotlin
// DSL (recommended): build a Section via Section.Builder, apply config, add to Panel
fun Panel.addSection(
    theme: Theme,
    language: Language,
    title: Map<String, String>? = null,
    config: Section.Builder.() -> Unit = {}
): Section

// DSL: create, configure and add a Section in one call
fun Panel.addSection(
    title: Map<String, String>,
    theme: Theme,
    language: Language,
    content: Section.() -> Unit
): Section

// Add a pre-configured Section
fun Panel.addSection(section: Section): Section
```

## Implementation Details

### Container Architecture

- Panel extends `LinearLayout` (orientation `VERTICAL`) — Panel.java:19, 77
- Children are added directly to the Panel; there is no intermediate container
- Panel has no visual styling — background and border colors are `TRANSPARENT`
- Scrolling is provided by the surrounding `ScreenView` in `Screen`, not by Panel

### Panel Transitions

Panel is designed to work with `Screen.setActivePanel()` for animated transitions:

- Transition duration: 300ms (defined in `ScreenTheme.PANEL_TRANSITION_DURATION`)
- Animation uses `translationY` for smooth slide up/down effects
- Old panel slides out while new panel slides in simultaneously

### Theme and Language Propagation

Panel applies each setter once to a snapshot of its immediate children that implement `IThemable`
or `ILocalizable`. Each child setter owns deeper propagation; a plain `ViewGroup` wrapper stops it.
A child added after Panel state is set immediately inherits every current non-null state once.

### Visual Styling

Panel intentionally has no visual styling:

- `CORNER_RADIUS`, `PADDING`, `ELEVATION`, `BORDER_WIDTH` are 0
- All background and border colors are `TRANSPARENT` (PanelTheme.java:66-99)
- Visual presentation is handled by child `Section` components

Panel **does** define nonzero layout constants used by compact mode and the background image
(see Dimensions).

## Dimensions

All sizes in **pixels** (automotive requirement). Constants in `PanelTheme`.

| Property | Value |
|----------|-------|
| Background image width | 800px |
| Background image right margin | -30px (extends beyond edge) |
| Compact top margin | 50px (applied as top padding in compact mode) |
| Bottom padding | 25px (space after the last section) |

Colors defined in [PanelTheme.java](../../voboost-components/src/main/java/ru/voboost/components/panel/PanelTheme.java).

## File Structure

```
panel/
├── Panel.java           # Core implementation
├── Panel.compose.kt     # Compose wrapper
├── Panel.kt             # addSection extensions (DSL)
├── PanelTheme.java      # Theme colors and dimensions
├── Panel.test/          # Tests
└── Panel.screenshots/   # Reference images
```
