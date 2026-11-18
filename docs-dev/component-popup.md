# Popup Component

[Screen](component-screen.md) | [Tabs](component-tabs.md) | [Panel](component-panel.md) | [Section](component-section.md) | [Radio](component-radio.md) | [Checkbox](component-checkbox.md) | [Button](component-button.md) | [Buttons](component-buttons.md) | [Select](component-select.md) | [Dialog](component-dialog.md) | **Popup** | [Toast](component-toast.md) | [Text](component-text.md) | [Hint](component-hint.md) | [Font](component-font.md) | [Theme](component-theme.md) | [Language](component-language.md)

**English** | [Русский](component-popup.ru.md)

## Architecture

- **[Popup.java](../../voboost-components/src/main/java/ru/voboost/components/popup/Popup.java)** — A subclass of `android.app.Dialog` providing the overlay container with animations
- **No Compose wrapper** — underlying infrastructure used by [Dialog](component-dialog.md) and [Select](component-select.md)

## Purpose

Popup is an **overlay container** providing:
- Semi-transparent overlay background
- Rounded content panel with theme colors
- Scale + fade animations (300ms enter, 150ms exit)
- Dismiss on touch outside (configurable)

Dialog and Select aggregate Popup as their container, injecting their own content.

## API

```java
Popup popup = new Popup(context);
popup.setTheme(Theme.FREE_DARK);
popup.getCurrentTheme();
popup.setPopupContentView(myView);
popup.setDismissOnTouchOutside(true);
popup.setAnimationDuration(300);   // -1 restores the default
popup.setOnDismissAction(() -> { /* cleanup */ });
popup.clearOnDismissAction();
popup.show();
popup.dismissWithAnimation();
popup.getOverlayViewForTest();     // test-only: full overlay view
```

Animation duration accepts `-1` or a positive millisecond value. Zero and values below
`-1` throw `IllegalArgumentException`.

### Lifecycle Management

Popup unwraps nested `ContextWrapper` instances with cycle protection. When the resolved
Activity is a `LifecycleOwner`, one observer is registered for the current show session;
re-show replaces it, and every dismiss path removes it exactly once. Destruction dismisses
the popup. A non-Activity context is supported as an explicitly unowned session and has no
automatic lifecycle dismissal.

`setOnDismissAction` is the unambiguous callback API. The legacy
`setOnDismissListener(Runnable)` overload remains for compatibility; use
`clearOnDismissAction()` instead of passing Java `null`, which is ambiguous with the
platform listener overload. The action runs once per dismissed show session.

### Best Practices

- Always call `dismissWithAnimation()` instead of `dismiss()` for consistent UX
- The popup manages its own lifecycle - don't keep references after showing
- Content view is added to the popup container - don't add it to other parent views

## Dimensions

| Property | Value |
|----------|-------|
| Panel width | 1300px |
| Corner radius | 20px |
| Padding | 0px |
| Enter / exit duration | 300ms / 150ms |
| Scale range | 0.8 → 1.0 |

## File Structure

```
popup/
├── Popup.java         # Core implementation
├── PopupTheme.java    # Colors, dimensions
├── Popup.test/
│   ├── PopupTestUnit.java
│   └── PopupTestVisual.java
└── Popup.screenshots/
```
