# Компонент Toast

[Screen](component-screen.ru.md) | [Tabs](component-tabs.ru.md) | [Panel](component-panel.ru.md) | [Section](component-section.ru.md) | [Radio](component-radio.ru.md) | [Checkbox](component-checkbox.ru.md) | [Button](component-button.ru.md) | [Buttons](component-buttons.ru.md) | [Select](component-select.ru.md) | [Dialog](component-dialog.ru.md) | [Popup](component-popup.ru.md) | **Toast** | [Text](component-text.ru.md) | [Hint](component-hint.ru.md) | [Font](component-font.ru.md) | [Theme](component-theme.ru.md) | [Language](component-language.ru.md).

[English](component-toast.md) | **Русский**

## Архитектура

- [Toast.java](../../voboost-components/src/main/java/ru/voboost/components/toast/Toast.java) — самостоятельный custom `View`: Canvas, анимация, таймер, обработка close-кнопки, lifecycle и очистка
- [Toast.compose.kt](../../voboost-components/src/main/java/ru/voboost/components/toast/Toast.compose.kt) — Compose-host. Он прикрепляет native View через `AndroidView`, показывает одну сессию на `eventKey`, обновляет актуальные параметры и callback-и и освобождает сессию при замене или выходе из composition
- [ToastTheme.java](../../voboost-components/src/main/java/ru/voboost/components/toast/ToastTheme.java) содержит размеры в пикселях, цвета и длительности

## Native-использование

Standalone Toast нужно сначала добавить в настоящий `ViewGroup`: `show()` управляет только видимостью и анимацией уже размещённого View.

```java
FrameLayout host = findViewById(R.id.overlay_host);
Toast toast = new Toast(host.getContext());
toast.setTheme(Theme.FREE_DARK); // необязательно: безопасный default — FREE_DARK
toast.setContent("Настройки сохранены");
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

### Java Builder (рекомендуется для нового Java-кода)

`Toast.create(...)` возвращает `Builder`. Длительность по умолчанию `ToastAnimation.DURATION_SHORT`, кнопка закрытия по умолчанию выключена. `build()` возвращает скрытый, неприкреплённый Toast и никогда не показывает его. Потребительский колбэк `onDismiss` сохраняется, когда Toast хостится Screen.

```java
Toast toast = Toast.create(context, Theme.FREE_DARK, "Настройки сохранены")
        .duration(ToastTheme.DURATION_SHORT)
        .showCloseButton(false)
        .onDismiss(() -> { /* потребительский колбэк */ })
        .build();
// Самостоятельно: прикрепите к своему хосту перед show().
// Внутри Screen: screen.showToast(toast); владеет прикреплением, показом, заменой и удалением.
```

`Screen.showToast(Toast)` — host-точка входа для Screen-layout: Screen проверяет отсутствие чужого родителя, детерминированно заменяет текущий toast, устанавливает только свой внутренний наблюдатель (сохраняя потребительский колбэк), прикрепляет/показывает через Screen, удаляет View после закрытия и очищает владение. `Screen.showToast(String, long)` делегирует по тому же пути.

### Kotlin (фабрика)

Используйте фабрику `toast()` из `ToastFactory.kt` — без `.create()`/`.build()`:

```kotlin
val toast = toast(context, Theme.FREE_DARK, "Настройки сохранены") {
    showCloseButton(true)
    onDismiss { /* потребительский колбэк */ }
}
screen.showToast(toast)
```

```kotlin
Toast(
    theme = Theme.FREE_DARK,
    text = "Настройки сохранены",
    duration = ToastTheme.DURATION_SHORT,
    showCloseButton = false,
    eventKey = notificationId,
    onDismiss = { /* обновить состояние вызывающей стороны */ },
)
```

Composable видим, пока активна его native-сессия. Обычная рекомпозиция сохраняет тот же прикреплённый View и обновляет тему, текст, длительность, close-кнопку и актуальный `onDismiss`. Необязательный callback `onDismiss` всегда обращается к последней лямбде через один запомненный нативный listener; когда он равен `null`, нативный listener физически сбрасывается, а тот же запомненный listener переустанавливается при возврате к non-null, поэтому переход `A -> B -> null -> A` не оставляет устаревший путь доставки. `eventKey` — ключ-идентификатор события жизненного цикла, а не очищаемое свойство: измените его, чтобы заменить сессию и перезапустить её время жизни. Выход из composition отменяет таймер и animator, отсоединяет View, удаляет lifecycle observer и очищает callback без события dismiss.

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

## Lifecycle и состояние

- Все конструкторы устанавливают `Theme.FREE_DARK`, поэтому до явного `setTheme` paints не остаются в неинициализированном black-on-black состоянии
- `show()` идемпотентен, пока Toast показан. `dismiss()` идемпотентен для скрытого или уже закрывающегося Toast; listener вызывается один раз на завершённую сессию
- Activity разворачивается через вложенные `ContextWrapper` с защитой от циклов. Если она реализует `LifecycleOwner`, уничтожение закрывает Toast и удаляет observer. Контексты без Activity считаются явно непривязанными
- Закрытие и detach удаляют отложенные callbacks. Detach также отменяет анимацию, сбрасывает скрытое состояние, удаляет observer и очищает listener: отложенная работа не изменит и не удержит отсоединённый View
- `show()` и `dismiss()` вызываются в main thread

## Длительность и анимация

`setDuration(0)` отключает auto-dismiss. Положительное значение планирует закрытие, отрицательное вызывает `IllegalArgumentException`. Изменение длительности в видимой сессии заменяет её таймер. Изменение длительности во время выполняющегося dismiss обновляет настройку для следующей сессии, но не планирует callback для завершающейся сессии.

| Константа | Значение |
|---|---:|
| `ToastTheme.DURATION_SHORT` | 3000мс |
| `ToastTheme.DURATION_LONG` | 5000мс |
| slide in/out | 400мс |
| fade in/out | 100мс |

Появление перемещает Toast от `-HEIGHT` к `0` и меняет alpha от 0 до 1; скрытие выполняет обратное. Используются `DecelerateInterpolator` с factor 3 и hardware layer.

## Отрисовка

Toast рисует на собственном Canvas скруглённый фон темы, центрированный bold-текст и необязательный крестик закрытия. Длинный текст сокращается с ellipsis и резервирует место под close-кнопку. Её touch area — 50px на 50px.

| Размер | Значение |
|---|---:|
| Ширина | 1260px |
| Высота | 100px |
| Радиус | 20px |
| Размер текста | 36px |
| Close-кнопка | 50px |
| Close-иконка | 24px |
| Верхний offset в Screen | 10px |

Цвета всех четырёх тем определены в [ToastTheme.java](../../voboost-components/src/main/java/ru/voboost/components/toast/ToastTheme.java).

## Тесты

- [ToastTestUnit.java](../../voboost-components/src/main/java/ru/voboost/components/toast/Toast.test/ToastTestUnit.java) проверяет native-конфигурацию, default rendering, таймер, input, уничтожение owner, повторные сессии и очистку callback-ов
- [ToastComposeLifecycleTestUnit.kt](../../voboost-components/src/main/java/ru/voboost/components/toast/Toast.test/ToastComposeLifecycleTestUnit.kt) доказывает прикрепление к host, видимость, live updates, замену callback-а, keyed replacement и disposal
- [ToastTestVisual.java](../../voboost-components/src/main/java/ru/voboost/components/toast/Toast.test/ToastTestVisual.java) покрывает визуальные варианты
