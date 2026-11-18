# Компонент Dialog

[Screen](component-screen.ru.md) | [Tabs](component-tabs.ru.md) | [Panel](component-panel.ru.md) | [Section](component-section.ru.md) | [Radio](component-radio.ru.md) | [Checkbox](component-checkbox.ru.md) | [Button](component-button.ru.md) | [Buttons](component-buttons.ru.md) | [Select](component-select.ru.md) | **Dialog** | [Popup](component-popup.ru.md) | [Toast](component-toast.ru.md) | [Text](component-text.ru.md) | [Hint](component-hint.ru.md) | [Font](component-font.ru.md) | [Theme](component-theme.ru.md) | [Language](component-language.ru.md).

[English](component-dialog.md) | **Русский**

## Архитектура

- **[Dialog.java](../../voboost-components/src/main/java/ru/voboost/components/dialog/Dialog.java)** — Java: строит макет содержимого, использует Popup как контейнер
- **[Dialog.compose.kt](../../voboost-components/src/main/java/ru/voboost/components/dialog/Dialog.compose.kt)** — Kotlin-обёртка для Compose: управление жизненным циклом

Dialog компонует наш [Popup](component-popup.md) для оверлея и наш [Button](component-button.md) для действий.

## Использование

### Java

```java
Dialog dialog = new Dialog(context);
dialog.setTheme(Theme.FREE_DARK);
dialog.setTitle("Сбросить настройки");
dialog.setMessage("Вы уверены?");
dialog.setConfirmButton("Сбросить", () -> { resetSettings(); });
dialog.setCancelButton("Отмена", () -> {});
dialog.show();
```

### Java Builder (рекомендуется для нового Java-кода)

`Dialog.create(...)` возвращает `Builder`. Кнопка отсутствует, пока не вызван `confirmButton`/`cancelButton`; confirm по умолчанию `ButtonStyle.PRIMARY`, cancel — `ButtonStyle.SECONDARY`. `build()` не вызывает `show()`; Builder может строить несколько независимых Dialog.

```java
Dialog dialog = Dialog.create(context, Theme.FREE_DARK)
        .title("Сбросить настройки")
        .message("Вы уверены?")
        .confirmButton("Сбросить", () -> resetSettings())
        .cancelButton("Отмена", null)
        .build();
dialog.show();
```

### Kotlin (фабрика)

Используйте фабрику `dialog()` из `DialogFactory.kt` — без `.create()`/`.build()`:

```kotlin
val dialog = dialog(context, Theme.FREE_DARK) {
    title("Сброс настроек")
    message("Вы уверены?")
    confirmButton("Сбросить") { resetSettings() }
    cancelButton("Отмена", null)
}
dialog.show()
```

### Compose

```kotlin
var showDialog by remember { mutableStateOf(false) }

if (showDialog) {
    Dialog(
        theme = Theme.FREE_DARK,
        title = "Сбросить настройки",
        message = "Вы уверены?",
        confirmText = "Сбросить",
        cancelText = "Отмена",
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

Необязательные callback-и (`onConfirm`, `onCancel`, `onDismiss`) всегда обращаются к последней лямбде через один запомненный нативный listener на слот. Когда необязательный callback равен `null`, его нативный слот физически сбрасывается, а тот же запомненный listener переустанавливается при возврате к non-null, поэтому переход `A -> B -> null -> A` не оставляет устаревший путь доставки.

`onDialogCreated` — единственное исключение: это callback жизненного цикла создания, который срабатывает не более одного раза для каждого заново созданного native Dialog, после установки начальных свойств и адаптеров слушателей и до библиотечного первого `show()`. Он не повторяется при рекомпозиции, а замена callback после создания не имеет обратной силы. Предназначен для тестов и исключительного императивного interop; Compose-свойства остаются авторитетными при последующих обновлениях. При.dispose диалог сразу закрывается и ссылки на callback освобождаются.

## Обновление содержимого

Содержимое Dialog можно обновлять до или после `show()`. В открытом диалоге заголовок, сообщение, тема, стили, подписи и callback-и кнопок обновляются сразу без закрытия и повторного создания Popup. `clearConfirmButton()` и `clearCancelButton()` удаляют nullable-действия и освобождают listeners; старые отсоединённые кнопки становятся неактивными. В закрытом диалоге сеттеры инвалидируют кэш для следующего `show()`; ручной `rebuildContent()` обычно не нужен.

```java
Dialog dialog = new Dialog(context);
dialog.setTheme(Theme.FREE_DARK);
dialog.setTitle("Шаг 1");
dialog.show();

// Позже обновляем содержимое
dialog.setTitle("Шаг 2");
// Открытый диалог обновляется сразу; повторный show() не нужен.
```

## Потокобезопасность

Все методы Dialog должны вызываться из главного потока UI. Вызов методов из фоновых потоков может привести к состояниям гонки и неопределённому поведению.

```java
// Правильно — вызов из потока UI
runOnUiThread(() -> {
    dialog.setTitle("Обновлено");
    dialog.show();
});

// Неправильно — может вызвать проблемы
new Thread(() -> {
    dialog.setTitle("Обновлено"); // Так делать нельзя
    dialog.show(); // Так делать нельзя
}).start();
```

## Длительность анимации

Dialog использует анимации Popup: 300мс для появления и 150мс для скрытия.

Compose-обёртка показывает Dialog один раз при входе в composition, обновляет тот же native Dialog актуальными параметрами и callback-ами, удаляет nullable-кнопки и при выходе из composition немедленно закрывает его и очищает ссылки на callback-и. Локализация передаётся строковыми параметрами: рекомпозиция с переведёнными заголовком, сообщением или подписями кнопок обновляет открытый диалог на месте.

## Валидация

Dialog проверяет входные параметры:
- `setTheme()` выбрасывает `IllegalArgumentException`, если тема равна null
- `setConfirmButton()` выбрасывает `IllegalArgumentException`, если текст равен null или состоит только из пробелов
- `setCancelButton()` выбрасывает `IllegalArgumentException`, если текст равен null или состоит только из пробелов
- `setTitle()` и `setMessage()` допускают null (рассматривается как пустое значение)

```java
// Допустимо
dialog.setTitle("Заголовок");
dialog.setTitle(null); // OK — заголовок не отображается
dialog.setMessage("Сообщение");
dialog.setMessage(null); // OK — сообщение не отображается

// Недопустимо — выбрасывает IllegalArgumentException
dialog.setConfirmButton("", () -> {}); // Пустой текст
dialog.setConfirmButton("   ", () -> {}); // Текст только из пробелов
dialog.setConfirmButton(null, () -> {}); // Текст null
```

## Структура файлов

```
dialog/
├── Dialog.java            # Основная реализация
├── Dialog.compose.kt              # Обёртка для Compose
├── DialogTheme.java       # Цвета, размеры
├── Dialog.test/
│   ├── DialogTestUnit.java
│   └── DialogTestVisual.java
└── Dialog.screenshots/
```
