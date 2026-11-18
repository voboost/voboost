# Section Component

[Screen](component-screen.md) | [Tabs](component-tabs.md) | [Panel](component-panel.md) | **Section** | [Radio](component-radio.md) | [Checkbox](component-checkbox.md) | [Button](component-button.md) | [Buttons](component-buttons.md) | [Select](component-select.md) | [Dialog](component-dialog.md) | [Popup](component-popup.md) | [Toast](component-toast.md) | [Text](component-text.md) | [Hint](component-hint.md) | [Font](component-font.md) | [Theme](component-theme.md) | [Language](component-language.md)

**English** | [Русский](component-section.ru.md)

## Architecture

- **[Section.java](../../voboost-components/src/main/java/ru/voboost/components/section/Section.java)** — Java ViewGroup: titled container with gradient header
- **[Section.compose.kt](../../voboost-components/src/main/java/ru/voboost/components/section/Section.compose.kt)** — Kotlin Compose wrapper
- **[Section.kt](../../voboost-components/src/main/java/ru/voboost/components/section/Section.kt)** — Kotlin DSL extensions (`addRadio`, `addButton`, ...)

Titled container with rounded corners for grouping related elements (e.g. Radio inside Section inside Panel).

## Usage

### Java

```java
Section section = new Section(context);
section.setTheme(Theme.FREE_DARK);
section.setLanguage(Language.EN);
section.setTitle(Map.of("en", "Language Selection", "ru", "Выбор языка"));

// Add child views
section.addView(radioComponent);
```

### Kotlin (recommended — DSL)

The receiver-lambda extensions defined in `Section.kt` (for children) and `Panel.kt` (for
sections) wrap the Java Builder so Kotlin callers never write `.create()`/`.build()`. Inside
the section receiver, child components are added with their own DSL overloads
(`addButton(...) { }`, `addRadio(...) { }`, ...).

```kotlin
panel.addSection(Theme.FREE_DARK, Language.EN, mapOf("en" to "Settings", "ru" to "Настройки")) {
    titleCheckbox(true)
    addRadio(Theme.FREE_DARK, Language.EN, buttons, "en") {
        title(mapOf("en" to "Language Selection", "ru" to "Выбор языка"))
    }
}
```

For standalone construction, use the `section()` factory from `SectionFactory.kt`:

```kotlin
val section = section(context, Theme.FREE_DARK, Language.EN, mapOf("en" to "Settings")) {
    titleCheckbox(true)
}
```

### Kotlin (View API)

```kotlin
val section = Section(context).apply {
    setTheme(Theme.FREE_DARK)
    setLanguage(Language.EN)
    setTitle(mapOf("en" to "Language Selection", "ru" to "Выбор языка"))
    addView(radioComponent)
}
```

### Compose

```kotlin
Section(
    title = mapOf("en" to "Settings", "ru" to "Настройки"),
    language = Language.EN,
    theme = Theme.FREE_LIGHT,
    content = { sectionView ->
        sectionView.addView(radioComponent)
    }
)
```

## Using Section in Compose

The Section component can be used in Jetpack Compose through the `Section` composable function or through extension functions.

### Option 1: Using the Section Composable Wrapper

```kotlin
import ru.voboost.components.section.Section
import ru.voboost.components.i18n.Language
import ru.voboost.components.theme.Theme

@Composable
fun MyScreen() {
    val theme = Theme.FREE_LIGHT
    val language = Language.EN

    Section(
    title = mapOf("en" to "Settings", "ru" to "Настройки"),
    language = language,
    theme = theme
) { section ->
        // Use extension functions to add children
        section.addRadio(
            buttons = listOf(
                RadioButton("en", mapOf("en" to "English")),
                RadioButton("ru", mapOf("en" to "Russian"))
            ),
            selectedValue = "en",
            theme = theme,
            language = language,
            onValueChange = { newValue -> /* handle change */ }
        )
    }
}
```

### Option 2: Using Panel.addSection() Extension (Recommended)

```kotlin
import ru.voboost.components.panel.Panel
import ru.voboost.components.panel.addSection
import ru.voboost.components.section.addRadio

@Composable
fun MyPanel() {
    val theme = Theme.FREE_LIGHT
    val language = Language.EN
    val context = LocalContext.current

    val panel = Panel(context).apply {
        setTheme(theme)
        setLanguage(language)
    }

    panel.addSection(
        title = mapOf("en" to "Settings"),
        theme = theme,
        language = language
    ) { section ->
        section.addRadio(
            buttons = listOf(
                RadioButton("en", mapOf("en" to "English")),
                RadioButton("ru", mapOf("en" to "Russian"))
            ),
            selectedValue = "en",
            theme = theme,
            language = language
        )
    }
}
```

### Option 3: Direct Java View Creation

For migration or special cases, you can create the Java View directly:

```kotlin
val section = ru.voboost.components.section.Section(context).apply {
    setTheme(Theme.FREE_LIGHT)
    setLanguage(Language.EN)
    setTitle(mapOf("en" to "Settings"))
}

// Add children using Java API
val radio = ru.voboost.components.radio.Radio(context).apply {
    setTheme(Theme.FREE_LIGHT)
    setLanguage(Language.EN)
    setButtons(listOf(...))
}
section.addView(radio)
```

### Integration with Panel

When using Section with Panel components, scrolling is provided by the surrounding Screen (via ScreenView), not by Panel itself. Panel is a LinearLayout that stacks sections; it does not wrap them in a ScrollView:

```kotlin
// Panel is a non-scrolling LinearLayout; Screen provides the ScreenView that scrolls
val panel = Panel(context).apply {
    setTheme(theme)
    setLanguage(language)
}

// Sections added to panel are scrolled by the surrounding ScreenView
panel.addSection(title = mapOf("en" to "Section 1", "ru" to "Раздел 1"), theme = theme, language = language) { ... }
panel.addSection(title = mapOf("en" to "Section 2", "ru" to "Раздел 2"), theme = theme, language = language) { ... }
panel.addSection(title = mapOf("en" to "Section 3", "ru" to "Раздел 3"), theme = theme, language = language) { ... }
```

### Extension Functions Reference

Section provides extension functions for common child components. Each has two Kotlin forms:
a param-based overload (every option as a named argument) and a **DSL overload** (required
params up front, then a receiver-lambda over the component's Builder for the rest —
recommended for Kotlin):

- `addRadio(...)` - Add a Radio component
- `addButton(...)` - Add a Button component
- `addCheckbox(...)` - Add a Checkbox component
- `addSelect(...)` - Add a Select component
- `addButtons(...)` - Add a Buttons component

Example:

```kotlin
Section(
    title = mapOf("en" to "My Section"),
    language = Language.EN,
    theme = Theme.FREE_LIGHT
) { section ->
    // Add Radio
    section.addRadio(
        buttons = listOf(...),
        selectedValue = "option1",
        theme = Theme.FREE_LIGHT,
        language = Language.EN
    )

    // Add Button
    section.addButton(
        text = "Click Me",
        style = ButtonStyle.PRIMARY,
        theme = Theme.FREE_LIGHT,
        language = Language.EN
    ) { /* handle click */ }

    // Add Checkbox
    section.addCheckbox(
        checked = true,
        theme = Theme.FREE_LIGHT,
        language = Language.EN,
        label = mapOf("en" to "Enable")
    )
}
```

### Theme and Language Propagation

Section automatically propagates theme and language to child components:

Each setter is applied once to a snapshot of immediate interface children. Their setters own
deeper propagation, while a plain `ViewGroup` stops traversal. Late-added content and the
title-checkbox inherit each current non-null state once without firing value callbacks.

```kotlin
Section(
    title = mapOf("en" to "Settings"),
    language = Language.EN,
    theme = Theme.FREE_LIGHT
) { section ->
    // Radio will automatically receive theme and language from Section
    // but you can also specify explicitly:
    section.addRadio(
        buttons = listOf(...),
        selectedValue = "en",
        theme = Theme.FREE_LIGHT,
        language = Language.EN
    )
}
```

### Title Checkbox (Collapsible Section)

When enabled, the title bar shows a `Checkbox` whose label is the section title. A user click animates between the exact expanded height and the title-only collapsed height in 200 ms. A newer toggle supersedes an unfinished transition. Children that were already `GONE` stay `GONE` and do not contribute to the expanded height; other child visibility values are restored after expansion.

`clearTitleCheckbox()`, inherited `removeView(titleCheckbox)`, and `removeAllViews()` all clear title-checkbox state and listeners. Detaching the Section cancels an active height animator, so an obsolete completion cannot mutate the detached view later.

Repeated programmatic application of the current checked state does not restart an active matching
transition. Applying the opposite state programmatically cancels that transition and snaps to the
new authoritative geometry. Compose always invokes the latest non-null
`onTitleCheckedChange`; recomposition to `null` and release clear the native callback.

```java
section.setTitle(Map.of("en", "Schedule charging"));
section.setTitleCheckbox(false, isChecked -> {
    // collapsed when isChecked == false
});
```

```kotlin
Section(
    title = mapOf("en" to "Schedule charging"),
    language = Language.EN,
    theme = Theme.FREE_DARK,
    titleChecked = false,
    onTitleCheckedChange = { /* handle toggle */ }
) { /* children — hidden while titleChecked == false */ }
```

### Info Icon and Popup

Set `infoText` (single block) or `infoBlocks` (multiple blocks with titles) to show a circular `(i)` icon after the title. When both Compose parameters are supplied, `infoText` takes priority. Tapping the icon opens a popup.

```java
// Single text block (no title)
section.setPopupText(Map.of("en", "Generally, the range extender is not started..."));

// Multiple blocks with titles
section.setPopupText(List.of(
    new PopupBlock(
        Map.of("en", "Pure EV"),
        Map.of("en", "Description of pure EV mode...")
    ),
    new PopupBlock(
        Map.of("en", "HEV"),
        Map.of("en", "Description of HEV mode...")
    )
));
```

```kotlin
// Single block
Section(
    title = mapOf("en" to "Power mode"),
    language = Language.EN,
    theme = Theme.FREE_DARK,
    infoText = mapOf("en" to "Generally, the range extender..."),
)

// Multiple blocks
Section(
    title = mapOf("en" to "Power mode"),
    language = Language.EN,
    theme = Theme.FREE_DARK,
    infoBlocks = listOf(
        SectionPopupBlock(
            title = mapOf("en" to "Pure EV"),
            text = mapOf("en" to "Description...")
        ),
        SectionPopupBlock(
            title = mapOf("en" to "HEV"),
            text = mapOf("en" to "Description...")
        )
    ),
)
```

## API

### Java

```java
// Title
void setTitle(Map<String, String> title)
Map<String, String> getTitle()
String getTitleText()                   // current language

// Title checkbox (collapsible)
void setTitleCheckbox(boolean checked)
void setTitleCheckbox(boolean checked, OnTitleCheckedChangeListener listener)
void clearTitleCheckbox()
boolean hasTitleCheckbox()
boolean isTitleChecked()
boolean isCollapsed()
void setOnTitleCheckedChangeListener(OnTitleCheckedChangeListener listener)

// Info popup (content blocks)
static class PopupBlock {
    PopupBlock(@Nullable Map<String, String> title, Map<String, String> text)
    @Nullable Map<String, String> getTitle()
    Map<String, String> getText()
}
void setPopupText(@Nullable List<PopupBlock> blocks)
void setPopupText(@Nullable Map<String, String> singleText)  // convenience
@Nullable List<PopupBlock> getPopupBlocks()
boolean hasPopupText()
void clearPopupText()
void showPopup()

// Theme and language
void setTheme(Theme theme)
Theme getCurrentTheme()
void setLanguage(Language language)
Language getCurrentLanguage()

// Propagation (to child components)
void propagateTheme(Theme theme)
void propagateLanguage(Language language)

// Generic ViewGroup addView/removeView/removeAllViews and typed child-add methods share one ordered child model
Checkbox addCheckbox(Checkbox checkbox)
Radio addRadio(Radio radio)
Button addButton(Button button)
Select addSelect(Select select)
Buttons addButtons(Buttons buttons)
List<View> getChildren()

// Section-level padding (px; overrides CONTENT_PADDING_TOP/BOTTOM)
void setPaddingTop(Integer top)
Integer getSectionPaddingTop()
void setPaddingBottom(Integer bottom)
Integer getSectionPaddingBottom()
void setPaddingLeft(Integer left)     // content padding in px; null restores the theme default
void setPaddingRight(Integer right)   // content padding in px; null restores the theme default

// Layout
void setIncludeBottomMargin(boolean include)   // false = omit BOTTOM_MARGIN (used by Panel)
boolean isIncludeBottomMargin()
void setCompactWidth(boolean compactWidth)      // true = COMPACT_SECTION_WIDTH (705px)
boolean isCompactWidth()

// Popup overlay (for visual tests to composite the popup on top of the screen)
@Nullable View getPopupOverlayView()
```

### Compose Wrapper

```kotlin
data class SectionPopupBlock(
    val title: Map<String, String>? = null,
    val text: Map<String, String>
)

@Composable
fun Section(
    title: Map<String, String>,
    language: Language,
    theme: Theme,
    titleChecked: Boolean? = null,
    onTitleCheckedChange: ((Boolean) -> Unit)? = null,
    infoText: Map<String, String>? = null,              // single block without title
    infoBlocks: List<SectionPopupBlock>? = null,        // multiple blocks
    content: ((Section) -> Unit)? = null
)
```

## Builder API

Fluent builder for constructing a fully-configured `Section` in one call. This is the Java
entry point; in Kotlin prefer the DSL extension `Panel.addSection(theme, language, title) { }`
defined in `Panel.kt`, which wraps this Builder.

Entry point:

```java
static Section.Builder create(Context context, Theme theme, Language language, Map<String, String> title)
```

Example:

```java
Section section = Section.create(context, Theme.FREE_DARK, Language.EN, titleMap)
        .paddingTop(40)
        .paddingBottom(2)
        .titleCheckbox(true, checked -> { /* handle toggle */ })
        .infoText(infoMap)
        .build();
```

Builder methods (all return `Builder`):

| Method | Description |
|--------|-------------|
| `paddingTop(Integer)` / `paddingBottom(Integer)` | Section-level top/bottom padding in px |
| `paddingLeft(Integer)` / `paddingRight(Integer)` | Left/right content padding in px; null restores the theme default |
| `titleCheckbox(boolean)` | Title-checkbox with initial checked state |
| `titleCheckbox(boolean, OnTitleCheckedChangeListener)` | Title-checkbox with checked state and listener |
| `infoText(Map<String,String>)` | Single info text block (no title) |
| `infoText(List<PopupBlock>)` | Multiple info blocks; the last info-content builder call wins |
| `infoBlock(@Nullable Map<String,String> title, Map<String,String> text)` | Append one content block to the info popup |
| `build()` | Returns configured `Section` |

The latest info-source call wins. Consecutive `infoBlock` calls append to the current block list;
calling `infoBlock` after `infoText(Map)` replaces that single-text source.

Section captures title, popup-block maps, and popup lists defensively in stable iteration order.
The title and every popup string resolve to the exact requested language, then the first non-null
captured value, then `""`; internal IDs are never rendered. The required Section title API still
rejects null/empty maps, while a fresh Section with no title reports `""`; a title set before the
language resolves to its first non-null value. Null block elements and null PopupBlock text are
rejected with `IllegalArgumentException` before current content changes. Changing popup theme,
language, title, or blocks while it is visible rebuilds its content immediately, so old localized
text is not retained.

## Implementation Details

Canvas-based rendering: gradient title bar with top-only rounded corners and a background fill. `BORDER_WIDTH = 0` (SectionTheme.java:84), so no border is drawn. No state persistence (state is managed externally). Propagates theme and language to children.

## File Structure

```
section/
├── Section.java                          # Core implementation (ViewGroup, canvas, builder)
├── Section.compose.kt                    # Compose wrapper
├── Section.kt                            # add* extension functions (DSL)
├── SectionTheme.java                     # Dimensions and theme colors
├── SectionPopup.java                     # Info popup (header + scrollable blocks)
├── SectionInfoIcon.png                   # (i) icon bitmap
├── Section_theme_free-light.png          # Close-icon asset (FREE_LIGHT)
├── Section_theme_free-dark.png           # Close-icon asset (FREE_DARK)
├── Section_theme_dreamer-light.png       # Close-icon asset (DREAMER_LIGHT)
├── Section_theme_dreamer-dark.png        # Close-icon asset (DREAMER_DARK)
├── Section.test/                         # Tests
└── Section.screenshots/                  # Reference images
```
