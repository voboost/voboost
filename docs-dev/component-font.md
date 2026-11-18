# Font Component

[Screen](component-screen.md) | [Tabs](component-tabs.md) | [Panel](component-panel.md) | [Section](component-section.md) | [Radio](component-radio.md) | [Checkbox](component-checkbox.md) | [Button](component-button.md) | [Buttons](component-buttons.md) | [Select](component-select.md) | [Dialog](component-dialog.md) | [Popup](component-popup.md) | [Toast](component-toast.md) | [Text](component-text.md) | [Hint](component-hint.md) | **Font** | [Theme](component-theme.md) | [Language](component-language.md)

**English** | [Русский](component-font.ru.md)

Provides the project typeface for all components.

## Architecture

Single source file (**[Font.java](../../voboost-components/src/main/java/ru/voboost/components/font/Font.java)**) plus three co-located `.ttf` assets — static utility, no Compose wrapper needed.

Font files are co-located (BEM structure):
```
font/
├── Font.java              # Loader with caching
├── Font.ttf               # Regular weight
├── Font_bold_ascii.ttf    # Bold for ASCII text
└── Font_bold_unicode.ttf  # Bold for non-ASCII text (Cyrillic, etc.)
```

## Usage

```java
// Regular font
Typeface regular = Font.getRegular(context);
paint.setTypeface(regular);

// Bold font (auto-selects ASCII vs Unicode variant)
Typeface bold = Font.getBold(context, "Hello");    // → Font_bold_ascii.ttf
Typeface boldRu = Font.getBold(context, "Привет"); // → Font_bold_unicode.ttf
```

## API

```java
// Returns regular typeface (never null)
// @throws RuntimeException if font file not found
static Typeface getRegular(Context context)

// Returns bold typeface appropriate for text content (never null)
// - ASCII text → Font_bold_ascii.ttf
// - Text with non-ASCII chars → Font_bold_unicode.ttf
// - null or empty string → Font_bold_ascii.ttf
// @param text the text to be rendered (used for font variant selection)
// @throws RuntimeException if font file not found
static Typeface getBold(Context context, String text)

// Clears all cached Typeface instances (for testing only)
static void clearCache()
```

## Key Behavior

- **Cached** — Typeface instances loaded once, reused
- **No fallback** — throws `RuntimeException` if font file not found, never returns `null`
- **Two bold variants** — ASCII-only text uses `Font_bold_ascii.ttf`, text with non-ASCII chars uses `Font_bold_unicode.ttf`
- **Asset loading** — fonts loaded from assets via `build.gradle.kts` config: `assets.srcDir("src/main/java/ru/voboost/components/font")`

## Thread Safety

All public methods in Font are **thread-safe**:

- `getRegular()` — synchronized, caches Typeface instance
- `getBold()` — synchronized, caches bold font variants (ASCII/Unicode)
- `clearCache()` — synchronized, resets all cached instances (regular, bold ASCII/Unicode)

The component uses static cached Typeface instances that are loaded once and reused.
All cache access is synchronized to prevent race conditions in multi-threaded environments.

In automotive UI systems, Font is typically called from the main UI thread only,
but the implementation ensures thread-safety for any usage pattern.
- **Null handling** — `getBold(context, null)` returns ASCII bold font (same as empty string)
- **Thread-safe** — all public methods are synchronized
