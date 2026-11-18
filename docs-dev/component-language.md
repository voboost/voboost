# Language

[Screen](component-screen.md) | [Tabs](component-tabs.md) | [Panel](component-panel.md) | [Section](component-section.md) | [Radio](component-radio.md) | [Checkbox](component-checkbox.md) | [Button](component-button.md) | [Buttons](component-buttons.md) | [Select](component-select.md) | [Dialog](component-dialog.md) | [Popup](component-popup.md) | [Toast](component-toast.md) | [Text](component-text.md) | [Hint](component-hint.md) | [Font](component-font.md) | [Theme](component-theme.md) | **Language**

**English** | [Русский](component-language.ru.md)

Shared language enum for all Voboost components.

## File

**[Language.java](../../voboost-components/src/main/java/ru/voboost/components/i18n/Language.java)** — Java enum, used directly by both Java and Kotlin code.

**[ILocalizable.java](../../voboost-components/src/main/java/ru/voboost/components/i18n/ILocalizable.java)** — Interface for language switching and propagation in container components.

**[I18nUtils.java](../../voboost-components/src/main/java/ru/voboost/components/i18n/I18nUtils.java)** — Final utility class with helpers for localized string lookups.

## Values

| Enum          | Code   |
|---------------|--------|
| `Language.EN` | `"en"` |
| `Language.RU` | `"ru"` |

## API

```java
// Language.java
String getCode()                  // ISO 639-1 code (e.g., "en", "ru")
static Language fromCode(String)  // parse from string, case-insensitive, default: EN
```

## Parsing Behavior

The `fromCode` method uses **case-insensitive** matching via `equalsIgnoreCase`:
- `"en"`, `"EN"`, `"En"` all return `Language.EN`
- `"ru"`, `"RU"`, `"Ru"` all return `Language.RU`
- Invalid values (null, empty, unknown) default to `Language.EN`

**Examples:**
```java
// Simple parsing - uses EN fallback
Language lang = Language.fromCode(configValue);

// Direct constant when value is known
Language lang = Language.RU;
```

## I18nUtils

`I18nUtils` is a `final` utility class with a private constructor (throws `AssertionError`); it cannot be instantiated.

```java
// I18nUtils.java
static String getLocalizedString(
    Map<String, String> localizedStrings,  // language code -> string
    Language language,                      // current language
    String defaultValue                     // returned when not found
)
```

Behavior:
- Returns the exact non-null value for `language.getCode()`; exact `""` remains exact
- If the code is absent or maps to null, returns the first non-null value in map iteration order
- Returns `defaultValue` for a null/empty map, null language, or an all-null map

## ILocalizable Interface

Components that support language switching implement `ILocalizable`:

```java
public interface ILocalizable {
    void setLanguage(Language language);           // set language for this component
    Language getCurrentLanguage();                 // current language, or null if unset
    void propagateLanguage(Language language);     // propagate to child components
}
```

**Implementation patterns:**
- **Container components** (Screen, Panel, Section, Tabs): snapshot immediate `ILocalizable` children and call each `setLanguage()` once; child setters own deeper propagation
- **Leaf components** (Button, Checkbox, Text, Radio, Select): Implement `propagateLanguage()` as empty (Text) or delegate to primitive via `primitive.setLanguage()` (Button, Checkbox, Radio, Select); Hint forwards to its own `setLanguage`

**Null handling:**
- Public `setLanguage(null)` follows each implementer's validated contract; Text, Hint, Section,
  and Tabs reject it with `IllegalArgumentException`
- A forwarding `propagateLanguage` follows its target setter and does not invent a different
  nullable policy; other container behavior remains component-specific

## Design Decision

Java enum (not Kotlin) because:
1. Used directly in Java View code
2. Works seamlessly in Kotlin
3. No conversion needed between layers
