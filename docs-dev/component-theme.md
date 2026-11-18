# Theme

[Screen](component-screen.md) | [Tabs](component-tabs.md) | [Panel](component-panel.md) | [Section](component-section.md) | [Radio](component-radio.md) | [Checkbox](component-checkbox.md) | [Button](component-button.md) | [Buttons](component-buttons.md) | [Select](component-select.md) | [Dialog](component-dialog.md) | [Popup](component-popup.md) | [Toast](component-toast.md) | [Text](component-text.md) | [Hint](component-hint.md) | [Font](component-font.md) | **Theme** | [Language](component-language.md)

**English** | [Русский](component-theme.ru.md)

Shared theme enum for all Voboost components.

## File

**[Theme.java](../../voboost-components/src/main/java/ru/voboost/components/theme/Theme.java)** — Java enum, used directly by both Java and Kotlin code.

**[IThemable.java](../../voboost-components/src/main/java/ru/voboost/components/theme/IThemable.java)** — Interface for theme propagation in container components.

## Values

| Enum                  | String value    |
|-----------------------|-----------------|
| `Theme.FREE_LIGHT`    | `"free-light"`  |
| `Theme.FREE_DARK`     | `"free-dark"`   |
| `Theme.DREAMER_LIGHT` | `"dreamer-light"`|
| `Theme.DREAMER_DARK`  | `"dreamer-dark"` |

## API

```java
// Instance methods
@NonNull String getValue()              // string representation for serialization
boolean isLight()                        // true for FREE_LIGHT, DREAMER_LIGHT
boolean isDark()                         // true for FREE_DARK, DREAMER_DARK
boolean isFree()                         // true for FREE_LIGHT, FREE_DARK
boolean isDreamer()                      // true for DREAMER_LIGHT, DREAMER_DARK

// Static methods
@NonNull static Theme fromValue(@Nullable String value)  // parse from string, default: FREE_DARK
static boolean isValidThemeValue(@Nullable String value) // strict validation
```

## Parsing Behavior

The `fromValue` method uses **case-insensitive** matching:
- `"free-light"`, `"FREE-LIGHT"`, `"Free-Light"` all return `Theme.FREE_LIGHT`
- Invalid values (null, empty, unknown) default to `Theme.FREE_DARK`
- For strict validation, check values with `isValidThemeValue()` first

**Examples:**
```java
// Simple parsing - uses FREE_DARK fallback
Theme theme = Theme.fromValue(configValue);

// Strict validation - handle invalid input explicitly
if (Theme.isValidThemeValue(userInput)) {
    theme = Theme.fromValue(userInput);
} else {
    theme = Theme.FREE_DARK; // Explicit fallback
    // Show error to user
}
```

## Thread Safety

`Theme` enum is immutable and thread-safe:
- All enum values are constants (singletons)
- `getValue()` returns immutable string
- `fromValue()` is stateless and can be called concurrently
- No synchronization needed

## Design Decision

Java enum (not Kotlin) because:
1. Used directly in Java View code
2. Works seamlessly in Kotlin
3. No conversion needed between layers

Consuming projects (like voboost-config) should use this shared `Theme` enum instead of defining their own, since voboost-components is a standalone library that can be used outside the voboost infrastructure.

## Best Practices

1. **Prefer enum constants** over `fromValue()` when possible
   ```java
   // Good
   theme = Theme.FREE_LIGHT;
   // Acceptable (for dynamic values)
   theme = Theme.fromValue(configValue);
   ```

2. **Validate external input** before parsing
   ```java
   if (Theme.isValidThemeValue(userInput)) {
       theme = Theme.fromValue(userInput);
   } else {
       theme = Theme.FREE_DARK; // Default fallback
   }
   ```

3. **Use helper methods** for type checking
   ```java
   if (theme.isLight()) {
       // Apply light-specific styling
   }
   ```

## IThemable Interface

Components that support theme switching implement `IThemable`:

```java
public interface IThemable {
    void setTheme(Theme theme);           // Set theme for this component
    Theme getCurrentTheme();              // Current theme, or null if unset
    void propagateTheme(Theme theme);      // Propagate to children
}
```

**Implementation patterns:**
- **Container components** (Screen, Panel, Section, Tabs): snapshot immediate `IThemable` children and call each `setTheme()` once; child setters own deeper propagation
- **Leaf components** (Button, Checkbox, Text, Radio, Select): Implement `propagateTheme()` as empty (Text, Toast) or delegate to primitive via `primitive.setTheme()` (Button, Checkbox, Radio, Select); Hint forwards to its own `setTheme`
- **Theme-aware non-IThemable** (Dialog, Popup): expose `setTheme(Theme)`/`getCurrentTheme()` but do NOT implement `IThemable`. They are overlay containers (not custom Views with children to propagate to), so implementing the interface is intentionally skipped.
