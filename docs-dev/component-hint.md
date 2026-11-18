# Hint Component

[Screen](component-screen.md) | [Tabs](component-tabs.md) | [Panel](component-panel.md) | [Section](component-section.md) | [Radio](component-radio.md) | [Checkbox](component-checkbox.md) | [Button](component-button.md) | [Buttons](component-buttons.md) | [Select](component-select.md) | [Dialog](component-dialog.md) | [Popup](component-popup.md) | [Toast](component-toast.md) | [Text](component-text.md) | **Hint** | [Font](component-font.md) | [Theme](component-theme.md) | [Language](component-language.md)

**English** | [Русский](component-hint.ru.md)

## Architecture

- **[Hint.java](../../voboost-components/src/main/java/ru/voboost/components/hint/Hint.java)** — Java Custom View: canvas text rendering with manual line wrapping

`Hint extends View` and implements `IThemable` + `ILocalizable`. All rendering happens in `onDraw` on a `Canvas`; there is no XML layout and no separate primitive class. `HintTheme` is a package-private constants holder (`final class HintTheme`) — only `Hint` accesses it directly.

No Compose wrapper (no `Hint.kt`). Use the Java Custom View directly from Compose via `AndroidView` if needed.

## Usage

### Java

```java
Hint hint = new Hint(context);
hint.setTheme(Theme.FREE_DARK);
hint.setLanguage(Language.EN);
hint.setText(Map.of(
    "en", "Stop all running applications to free memory.",
    "ru", "Остановить все запущенные приложения."
));
section.addView(hint);
```

### Kotlin

```kotlin
val hint = Hint(context).apply {
    setTheme(Theme.FREE_DARK)
    setLanguage(Language.EN)
    setText(mapOf(
        "en" to "Stop all running applications to free memory.",
        "ru" to "Остановить все запущенные приложения."
    ))
}
section.addView(hint)
```

## API

```java
// Constructors
Hint(Context context)
Hint(Context context, AttributeSet attrs)
Hint(Context context, AttributeSet attrs, int defStyleAttr)

// Text (localized: lang code -> text)
void setText(Map<String, String> textMap)   // keys are "en", "ru" — NOT Map<Language,String>

// Theme (IThemable) — null theme is allowed, uses default dark-text color
void setTheme(Theme theme)
void propagateTheme(Theme theme)            // delegates to setTheme

// Language (ILocalizable)
void setLanguage(Language language)
Language getCurrentLanguage()
void propagateLanguage(Language language)   // delegates to setLanguage
```

Notes:

- `setText` accepts a `Map<String, String>` keyed by **language code strings** (`"en"`, `"ru"`), not `Map<Language, String>`. The key is matched against `Language.getCode()`.
- There is no `getCurrentTheme()` and no `setMargin()` — `Hint` is a leaf text block that fills the width supplied by its parent and applies its own top/bottom padding internally.

## Themes

Text color is muted (50% alpha of primary text) and depends only on whether the theme is dark or light. Defined in [HintTheme.java](../../voboost-components/src/main/java/ru/voboost/components/hint/HintTheme.java).

| Theme group | Text color |
|-------------|------------|
| Dark themes (value ends with `-dark`: `FREE_DARK`, `DREAMER_DARK`) | `#80ffffff` (white at 50% alpha) |
| Light themes (`FREE_LIGHT`, `DREAMER_LIGHT`) and `null` | `#801a1e28` (dark at 50% alpha) |

Theme is set via `setTheme(Theme.FREE_DARK)` etc. `null` is allowed and is treated as light.

## Localization

External via `Map<String, String>` — no shared i18n system:

```java
hint.setText(Map.of("en", "Stop all apps", "ru", "Остановить все приложения"));
```

Language is set via `setLanguage(Language.EN)` / `setLanguage(Language.RU)`. Resolution (`resolveText`):

1. Look up `currentLanguage.getCode()` (e.g. `"en"`) in the map.
2. If the key is missing (or no language is set), use the first non-null value in the captured map
   iteration order; there is no implicit English preference.
3. If the map is empty or `null`, `displayText` becomes `""` and the component collapses to **0 height**.

`setText` takes a defensive iteration-ordered snapshot. `setText(null)` clears the map, resolved
text, cached lines and measured text contribution. `setLanguage(null)` throws
`IllegalArgumentException`.

## Implementation Details

### Rendering

Canvas-based, single paint (`textPaint`, anti-aliased):

1. `init()` sets text size (`TEXT_SIZE_PX`) and default color.
2. `setText` / `setLanguage` call `resolveText()`, which picks the localized string and loads `Font.getRegular(...)` as the typeface, then `invalidate()` + `requestLayout()`.
3. `onMeasure` builds the word-aware wrapped-line list and computes height from that exact list:
   - Empty text → height `0`.
   - Otherwise: `height = PADDING_TOP_PX + wrappedLines.size * lineHeight + PADDING_BOTTOM_PX`,
     where `lineHeight = TEXT_SIZE_PX * 1.3f`.
4. `onDraw` consumes the same cached list; it does not re-wrap independently.

Explicit `\n` boundaries, empty lines and the trailing final line are preserved. Every non-empty
paragraph uses `Paint.breakText` and backs up to whitespace when possible.

### Dimensions

All sizes in **pixels** (automotive requirement). Constants in `HintTheme`.

| Property | Value |
|----------|-------|
| Text size | 24px (`TEXT_SIZE_PX`) |
| Top padding | 8px (`PADDING_TOP_PX`) |
| Bottom padding | 8px (`PADDING_BOTTOM_PX`) |
| Line height | 31.2px (`TEXT_SIZE_PX * 1.3f`) |

Text size matches the description style used in Checkbox and Radio (24px).

## File Structure

```
hint/
├── Hint.java              # Core implementation (View + onDraw)
├── HintTheme.java         # Text size, padding, text-color resolver
└── Hint.test/
    ├── HintTestUnit.java   # Unit tests (init, text, theme, language, measure)
    └── HintTestVisual.java # Visual regression tests (Roborazzi, 1920x720)
```

## Testing

- **Unit**: `Hint.test/HintTestUnit.java` — initialization, default state, `setText` (null, empty, unicode), language switching + fallback, `setTheme` (all enums + null), and `onMeasure` invariants (single-line height formula, padding inclusion, empty-text collapse to 0, width-equals-spec, idempotent re-measure). Runs with Robolectric at SDK 33.
- **Visual**: `Hint.test/HintTestVisual.java` — Roborazzi screenshots for all theme/language combinations at `1920x720` landscape, including short text, wrapped (multi-line) text, empty map, and explicit `\n` multiline. Screenshots are written under `Hint.screenshots/`.

```bash
./gradlew test --tests "*HintTest*"   # All Hint tests
```
