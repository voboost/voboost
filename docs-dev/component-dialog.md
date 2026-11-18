# Dialog Component

[Screen](component-screen.md) | [Tabs](component-tabs.md) | [Panel](component-panel.md) | [Section](component-section.md) | [Radio](component-radio.md) | [Checkbox](component-checkbox.md) | [Button](component-button.md) | [Buttons](component-buttons.md) | [Select](component-select.md) | **Dialog** | [Popup](component-popup.md) | [Toast](component-toast.md) | [Text](component-text.md) | [Hint](component-hint.md) | [Font](component-font.md) | [Theme](component-theme.md) | [Language](component-language.md)

**English** | [Русский](component-dialog.ru.md)

## Architecture

- **[Dialog.java](../../voboost-components/src/main/java/ru/voboost/components/dialog/Dialog.java)** — Java: builds content layout, uses Popup as container
- **[Dialog.compose.kt](../../voboost-components/src/main/java/ru/voboost/components/dialog/Dialog.compose.kt)** — Kotlin Compose wrapper: lifecycle management

Dialog composes our [Popup](component-popup.md) for overlay and our [Button](component-button.md) for actions.

## Usage

### Java

```java
Dialog dialog = new Dialog(context);
dialog.setTheme(Theme.FREE_DARK);
dialog.setTitle("Reset Settings");
dialog.setMessage("Are you sure?");
dialog.setConfirmButton("Reset", () -> { resetSettings(); });
dialog.setCancelButton("Cancel", () -> {});
dialog.show();
```

### Java Builder (preferred for new Java code)

`Dialog.create(...)` returns a `Builder`. A button is absent until its
`confirmButton`/`cancelButton` method is called; confirm defaults to `ButtonStyle.PRIMARY`
and cancel to `ButtonStyle.SECONDARY`. `build()` never calls `show()`; the Builder may build
multiple independent Dialogs.

```java
Dialog dialog = Dialog.create(context, Theme.FREE_DARK)
        .title("Reset Settings")
        .message("Are you sure?")
        .confirmButton("Reset", () -> resetSettings())
        .cancelButton("Cancel", null)
        .build();
dialog.show();
```

### Kotlin (factory)

Use the `dialog()` factory from `DialogFactory.kt` — no `.create()`/`.build()`:

```kotlin
val dialog = dialog(context, Theme.FREE_DARK) {
    title("Reset Settings")
    message("Are you sure?")
    confirmButton("Reset") { resetSettings() }
    cancelButton("Cancel", null)
}
dialog.show()
```

### Compose

```kotlin
var showDialog by remember { mutableStateOf(false) }

if (showDialog) {
    Dialog(
        theme = Theme.FREE_DARK,
        title = "Reset Settings",
        message = "Are you sure?",
        confirmText = "Reset",
        cancelText = "Cancel",
        onConfirm = { resetSettings(); showDialog = false },
        onCancel = { showDialog = false },
        onDismiss = { showDialog = false }
    )
}
```

## API

### Java

```java
void setTheme(Theme theme)
Theme getCurrentTheme()
void setTitle(String title)
String getTitle()
void setMessage(String message)
String getMessage()
void setConfirmButton(String text, Runnable action)
void clearConfirmButton()
void setConfirmButtonStyle(ButtonStyle style)
void setCancelButton(String text, Runnable action)
void clearCancelButton()
void setCancelButtonStyle(ButtonStyle style)
void setOnDismissListener(Runnable onDismiss)
void show()
void dismiss()
boolean isShowing()
void rebuildContent()
```

### Compose

```kotlin
@Composable
fun Dialog(
    theme: Theme,
    title: String = "",
    message: String = "",
    confirmText: String? = null,
    cancelText: String? = null,
    onConfirm: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    onDialogCreated: ((Dialog) -> Unit)? = null
)
```

Optional callbacks (`onConfirm`, `onCancel`, `onDismiss`) always reach the latest lambda through
a single remembered native listener per slot. When an optional callback is `null` its native
slot is physically cleared and the same remembered listener is reinstalled when it becomes
non-null again, so `A -> B -> null -> A` never leaves a stale delivery path.

`onDialogCreated` is the one exception — it is a creation-lifecycle callback that runs at most
once per newly created native Dialog, after its initial properties and listener adapters are
installed and before the library-owned first `show()`. It is not replayed on recomposition, and
replacing the callback after creation is not retroactive. It is intended for tests and
exceptional imperative interop; Compose properties remain authoritative on later updates.
Disposal dismisses immediately and releases callback references.

## Content Updates

Dialog content can be updated before or after `show()`. While open, title, message, theme,
button styles, labels, and callbacks update immediately without dismissing or recreating the
Popup. `clearConfirmButton()` and `clearCancelButton()` remove nullable actions and release
their listeners. Detached old button views are made inert. When closed, setters invalidate
the cached content for the next `show()`; manual `rebuildContent()` is not normally needed.

```java
Dialog dialog = new Dialog(context);
dialog.setTheme(Theme.FREE_DARK);
dialog.setTitle("Step 1");
dialog.show();

// Later, update content
dialog.setTitle("Step 2");
// The visible dialog updates immediately; show() is not called again.
```

## Thread Safety

All Dialog methods must be called from the main UI thread. Calling methods from background threads may cause race conditions and undefined behavior.

```java
// Correct - called from UI thread
runOnUiThread(() -> {
    dialog.setTitle("Updated");
    dialog.show();
});

// Incorrect - may cause issues
new Thread(() -> {
    dialog.setTitle("Updated"); // Don't do this
    dialog.show(); // Don't do this
}).start();
```

## Animation Duration

Dialog uses Popup's 300ms enter and 150ms exit animations.

The Compose wrapper shows once on entering composition, updates the same native Dialog with
current properties and callbacks after recomposition, removes nullable buttons, and dismisses
immediately while clearing callback references when it leaves composition.
Localization is supplied through the string parameters; recomposing with translated title,
message, or button labels updates the open dialog in place.

## Validation

Dialog validates input parameters:
- `setTheme()` throws `IllegalArgumentException` if theme is null
- `setConfirmButton()` throws `IllegalArgumentException` if text is null or whitespace-only
- `setCancelButton()` throws `IllegalArgumentException` if text is null or whitespace-only
- `setTitle()` and `setMessage()` accept null (treated as empty)

```java
// Valid
dialog.setTitle("Title");
dialog.setTitle(null); // OK - no title shown
dialog.setMessage("Message");
dialog.setMessage(null); // OK - no message shown

// Invalid - throws IllegalArgumentException
dialog.setConfirmButton("", () -> {}); // Empty text
dialog.setConfirmButton("   ", () -> {}); // Whitespace-only text
dialog.setConfirmButton(null, () -> {}); // Null text
```

## File Structure

```
dialog/
├── Dialog.java            # Core implementation
├── Dialog.compose.kt              # Compose wrapper
├── DialogTheme.java       # Colors, dimensions
├── Dialog.test/
│   ├── DialogTestUnit.java
│   └── DialogTestVisual.java
└── Dialog.screenshots/
```
