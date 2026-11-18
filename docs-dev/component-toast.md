# Toast Component

[Screen](component-screen.md) | [Tabs](component-tabs.md) | [Panel](component-panel.md) | [Section](component-section.md) | [Radio](component-radio.md) | [Checkbox](component-checkbox.md) | [Button](component-button.md) | [Buttons](component-buttons.md) | [Select](component-select.md) | [Dialog](component-dialog.md) | [Popup](component-popup.md) | **Toast** | [Text](component-text.md) | [Hint](component-hint.md) | [Font](component-font.md) | [Theme](component-theme.md) | [Language](component-language.md)

**English** | [Русский](component-toast.ru.md)

## Architecture

- [Toast.java](../../voboost-components/src/main/java/ru/voboost/components/toast/Toast.java) is the self-contained custom `View`: canvas drawing, animation,
  timing, close-button input, lifecycle observation, and teardown.
- [Toast.compose.kt](../../voboost-components/src/main/java/ru/voboost/components/toast/Toast.compose.kt) is the Compose host. It attaches the native View through
  `AndroidView`, shows one session per `eventKey`, updates current properties and callbacks,
  and releases the session on replacement or disposal.
- [ToastTheme.java](../../voboost-components/src/main/java/ru/voboost/components/toast/ToastTheme.java) contains pixel dimensions, colors, and durations.

## Native usage

A standalone Toast must first be attached to a real `ViewGroup`; `show()` only controls the
already-hosted View's visibility and animation.

```java
FrameLayout host = findViewById(R.id.overlay_host);
Toast toast = new Toast(host.getContext());
toast.setTheme(Theme.FREE_DARK); // optional; FREE_DARK is the safe default
toast.setContent("Settings saved");
toast.setDuration(ToastTheme.DURATION_SHORT);
toast.setOnDismissListener(() -> host.removeView(toast));

FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
        ToastTheme.WIDTH,
        ToastTheme.HEIGHT,
        Gravity.TOP | Gravity.CENTER_HORIZONTAL);
params.topMargin = ToastTheme.TOP_OFFSET;
host.addView(toast, params);
toast.show();
```

### Java Builder (preferred for new Java code)

`Toast.create(...)` returns a `Builder`. Default duration is `ToastAnimation.DURATION_SHORT`
and the close button defaults to false. `build()` returns a hidden, unattached Toast and never
shows it. The consumer `onDismiss` callback is preserved when the Toast is hosted by Screen.

```java
Toast toast = Toast.create(context, Theme.FREE_DARK, "Settings saved")
        .duration(ToastTheme.DURATION_SHORT)
        .showCloseButton(false)
        .onDismiss(() -> { /* consumer callback */ })
        .build();
// Standalone: attach to your own host before show().
// Inside a Screen: screen.showToast(toast); owns attach, show, replacement, and removal.
```

`Screen.showToast(Toast)` is the host entry point for Screen-based layouts: Screen validates
no foreign parent, deterministically replaces the current toast, installs only its internal
observer (preserving the consumer callback), attaches/shows through Screen, removes the View
after dismissal, and clears ownership. `Screen.showToast(String, long)` delegates through the
same path.

### Kotlin (factory)

Use the `toast()` factory from `ToastFactory.kt` — no `.create()`/`.build()`:

```kotlin
val toast = toast(context, Theme.FREE_DARK, "Settings saved") {
    showCloseButton(true)
    onDismiss { /* consumer callback */ }
}
screen.showToast(toast)
```

## Compose usage

```kotlin
Toast(
    theme = Theme.FREE_DARK,
    text = "Settings saved",
    duration = ToastTheme.DURATION_SHORT,
    showCloseButton = false,
    eventKey = notificationId,
    onDismiss = { /* update caller state */ },
)
```

The composable is visible while its native session is active. Ordinary recomposition keeps
the same attached View and updates theme, text, duration, close-button state, and the current
`onDismiss` callback. The optional `onDismiss` callback always reaches the latest lambda through
a single remembered native listener; when it is `null` the native dismiss listener is physically
cleared and the same remembered listener is reinstalled when it becomes non-null again, so
`A -> B -> null -> A` never leaves a stale delivery path. `eventKey` is a lifecycle identity key
for the notification event, not a clearable property: change it to replace the session and
restart its lifetime. Leaving composition cancels the timer and animator, detaches the View,
removes the lifecycle observer, and clears the callback without delivering a dismissal event.

## API

```java
void setTheme(Theme theme)
Theme getCurrentTheme()
void setContent(String text)
String getContent()
void setDuration(long millis)
long getDuration()
void setShowCloseButton(boolean show)
boolean isShowCloseButton()
void setOnDismissListener(OnDismissListener listener)
boolean isShowing()
void show()
void dismiss()
```

```kotlin
@Composable
fun Toast(
    theme: Theme,
    modifier: Modifier = Modifier,
    text: String = "",
    duration: Long = ToastTheme.DURATION_SHORT,
    showCloseButton: Boolean = false,
    eventKey: Any? = Unit,
    onDismiss: (() -> Unit)? = null,
)
```

## Lifecycle and state

- Every constructor initializes `Theme.FREE_DARK`, so drawing before an explicit
  `setTheme` never produces uninitialized black-on-black paints.
- `show()` is idempotent while showing. `dismiss()` is idempotent while hidden or already
  dismissing, and the listener runs once for a completed session.
- An Activity context is resolved through nested `ContextWrapper` instances with cycle
  protection. If it implements `LifecycleOwner`, destruction dismisses the Toast and removes
  the observer. Non-Activity contexts are explicitly unowned.
- Dismissal and detach remove pending handler callbacks. Detach also cancels animations,
  resets hidden state, removes the observer, and clears the listener so no delayed work can
  mutate or retain a detached View.
- `show()` and `dismiss()` are main-thread operations.

## Duration and animation

`setDuration(0)` disables auto-dismiss. Positive values schedule a dismissal; negative
values throw `IllegalArgumentException`. Changing duration on a visible session replaces its
pending timer. Changing duration during an in-flight dismissal updates the next configured
duration but never schedules a callback for the ending session.

| Constant | Value |
|---|---:|
| `ToastTheme.DURATION_SHORT` | 3000ms |
| `ToastTheme.DURATION_LONG` | 5000ms |
| slide in/out | 400ms |
| fade in/out | 100ms |

Entrance translates from `-HEIGHT` to `0` and fades from 0 to 1. Exit reverses both. The
animation uses a `DecelerateInterpolator` with factor 3 and a hardware layer.

## Rendering

Toast draws a rounded theme background, centered bold text, and an optional close cross on
its own Canvas. Long text is ellipsized and reserves close-button space. The close button's
touch area is 50px by 50px.

| Dimension | Value |
|---|---:|
| Width | 1260px |
| Height | 100px |
| Corner radius | 20px |
| Text size | 36px |
| Close button | 50px |
| Close icon | 24px |
| Screen top offset | 10px |

Theme colors are defined in [ToastTheme.java](../../voboost-components/src/main/java/ru/voboost/components/toast/ToastTheme.java) for all four themes.

## Tests

- [ToastTestUnit.java](../../voboost-components/src/main/java/ru/voboost/components/toast/Toast.test/ToastTestUnit.java) covers native configuration, drawing
  defaults, timing, input, owner destruction, repeated sessions, and callback cleanup.
- [ToastComposeLifecycleTestUnit.kt](../../voboost-components/src/main/java/ru/voboost/components/toast/Toast.test/ToastComposeLifecycleTestUnit.kt) proves host
  attachment, visibility, live updates, callback replacement, keyed replacement, and disposal.
- [ToastTestVisual.java](../../voboost-components/src/main/java/ru/voboost/components/toast/Toast.test/ToastTestVisual.java) covers the visual variants.
