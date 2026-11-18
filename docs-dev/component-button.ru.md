# Компонент Button

[Screen](component-screen.ru.md) | [Tabs](component-tabs.ru.md) | [Panel](component-panel.ru.md) | [Section](component-section.ru.md) | [Radio](component-radio.ru.md) | [Checkbox](component-checkbox.ru.md) | **Button** | [Buttons](component-buttons.ru.md) | [Select](component-select.ru.md) | [Dialog](component-dialog.ru.md) | [Popup](component-popup.ru.md) | [Toast](component-toast.ru.md) | [Text](component-text.ru.md) | [Hint](component-hint.ru.md) | [Font](component-font.ru.md) | [Theme](component-theme.ru.md) | [Language](component-language.ru.md).

[English](component-button.md) | **Русский**

## Архитектура

- **[Button.java](../../voboost-components/src/main/java/ru/voboost/components/button/Button.java)** — Java-контейнер (LinearLayout): layout, состояние; делегирует рендеринг на canvas и обработку касаний package-private [ButtonPrimitive.java](../../voboost-components/src/main/java/ru/voboost/components/button/ButtonPrimitive.java)
- **[Button.compose.kt](../../voboost-components/src/main/java/ru/voboost/components/button/Button.compose.kt)** — Kotlin-обёртка для Compose: лёгкая интеграция через AndroidView

Вся логика — в Java. Kotlin-обёртка только мостит к Compose.

## Использование

### Java

```java
Button button = new Button(context);
button.setText("Подтвердить");
button.setStyle(ButtonStyle.PRIMARY);
button.setTheme(Theme.FREE_DARK);
button.setEnabled(true);
button.setOnClickListener(new View.OnClickListener() {
    @Override
    public void onClick(View v) {
        // Обработка нажатия
    }
});
parentLayout.addView(button);
```

### Kotlin (рекомендуется — DSL)

Расширения с receiver-lambda, определённые в `Section.kt`, оборачивают Java Builder, поэтому Kotlin-вызывающему коду не нужно писать `.create()`/`.build()`. Лямбда выполняется на `Button.Builder`, поэтому необязательные свойства задаются как вызовы методов Builder (`description(map)`, `margin(l, t, r, b)`, `onClick { }`).

```kotlin
section.addButton(Theme.FREE_DARK, Language.EN, "Подтвердить", ButtonStyle.PRIMARY) {
    description(descMap)
    margin(0, 10, 0, 10)
    onClick { /* Обработка нажатия */ }
}
```

Для самостоятельного создания используйте фабрику `button()` из `ButtonFactory.kt`:

```kotlin
val btn = button(context, Theme.FREE_DARK, Language.EN, "Подтвердить", ButtonStyle.PRIMARY) {
    description(descMap)
    margin(0, 10, 0, 10)
    onClick { /* Обработка нажатия */ }
}
```

### Kotlin (View API)

```kotlin
val button = Button(context).apply {
    setText("Подтвердить")
    setStyle(ButtonStyle.PRIMARY)
    setTheme(Theme.FREE_DARK)
    isEnabled = true
    setOnClickListener { /* Обработка нажатия */ }
}
parentLayout.addView(button)
```

### Compose

```kotlin
Button(
    text = "Подтвердить",
    style = ButtonStyle.PRIMARY,
    theme = Theme.FREE_DARK,
    enabled = true,
    onClick = { /* Обработка нажатия */ }
)
```

## API

### Java Custom View

```java
// Конфигурация
void setText(String text)
String getText()
void setBoldText(boolean bold)             // true = жирный шрифт
void setStyle(ButtonStyle style)           // ButtonStyle.PRIMARY, ButtonStyle.SECONDARY
ButtonStyle getStyle()
void setTheme(Theme theme)                 // выбрасывает исключение при null
Theme getCurrentTheme()
void setLanguage(Language language)        // выбрасывает исключение при null
Language getCurrentLanguage()

// Метки (локализованные: код языка -> текст)
void setTitle(Map<String, String> title)            // над кнопкой
void setDescriptionAbove(Map<String, String> desc)  // между заголовком и кнопкой
void setDescription(Map<String, String> desc)       // справа от кнопки

// Раскладка
void padding(int left, int top, int right, int bottom)   // px, переопределяет горизонтальный padding по умолчанию
void setMargin(int left, int top, int right, int bottom)
int getMarginLeft(); int getMarginTop()
int getMarginRight(); int getMarginBottom()
boolean isMarginSet()

// Состояние
void setEnabled(boolean enabled)
boolean isEnabled()

// События — используется android.view.View.OnClickListener (без собственного интерфейса)
void setOnClickListener(android.view.View.OnClickListener listener)
```

Локализованные setters и соответствующие методы Builder сохраняют неизменяемый снимок в порядке вставки. Разрешение использует точный язык, затем первое ненулевое сохранённое значение, затем `""`; последующее изменение исходной Map не меняет отрисованный текст.

### Обёртка для Compose

```kotlin
@Composable
fun Button(
    text: String,
    title: Map<String, String>? = null,
    descriptionAbove: Map<String, String>? = null,
    boldText: Boolean = false,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    theme: Theme,
    enabled: Boolean = true,
    onClick: () -> Unit,
    description: Map<String, String>? = null,
    language: Language? = null,
    paddingTop: Int? = null,
    paddingBottom: Int? = null,
    paddingLeft: Int? = null,
    paddingRight: Int? = null,
    onViewCreated: ((Button) -> Unit)? = null
)
```

Nullable-параметры — управляемые значения: non-null применяет значение, а последующий `null` сбрасывает соответствующее нативное состояние того же экземпляра `Button`. `title = null` и `descriptionAbove = null` скрывают соответствующие тексты; `description = null` скрывает описание; `language = null` вызывает `resetLanguage()` (текст приводится к языку «en»); когда все четыре параметра padding равны `null`, вызывается `resetPadding()`, и каждая сторона возвращается к значению по умолчанию (`38px` по горизонтали, `0` по вертикали) вместо записи нулей. `boldText` применяется безусловно (по умолчанию `false`). `onViewCreated` — одноразовый хук создания, он не вызывается повторно при рекомпозиции.

## Builder API

Текучий (fluent) builder для построения полностью настроенной `Button` одним вызовом (Button.java:340-480). Это точка входа для Java; в Kotlin предпочтительнее [DSL-расширение](#kotlin-рекомендуется--dsl) выше, которое оборачивает этот Builder.

Точка входа:

```java
static Button.Builder create(Context context, Theme theme, Language language, String text, ButtonStyle style)
```

Пример:

```java
Button button = Button.create(context, Theme.FREE_DARK, Language.EN, "Подтвердить", ButtonStyle.PRIMARY)
        .title(titleMap)
        .descriptionAbove(descAboveMap)
        .description(descMap)
        .margin(0, 10, 0, 10)
        .padding(38, 0, 38, 0)
        .boldText(true)
        .onClick(listener)
        .build();
```

Методы builder (все возвращают `Builder`):

| Метод | Описание |
|--------|-------------|
| `title(Map<String,String>)` | Необязательный заголовок над кнопкой |
| `descriptionAbove(Map<String,String>)` | Необязательное описание между заголовком и кнопкой |
| `description(Map<String,String>)` | Необязательное описание справа |
| `marginLeft(int)` / `marginRight(int)` / `marginTop(int)` / `marginBottom(int)` | Индивидуальные отступы |
| `margin(int left, int top, int right, int bottom)` | Все четыре отступа |
| `padding(int left, int top, int right, int bottom)` | Padding в px (переопределяет 38px по умолчанию) |
| `boldText(boolean)` | Жирный текст кнопки |
| `onClick(View.OnClickListener)` | Слушатель нажатий |
| `build()` | Возвращает настроенную `Button` |

## Стили

| Стиль | Описание |
|-------|-------------|
| `ButtonStyle.PRIMARY` | Акцентный фон (синий/коричневый), белый текст |
| `ButtonStyle.SECONDARY` | Нейтральный фон (серый/белый), текст зависит от темы |

## Темы

| Enum | Основной фон | Дополнительный фон |
|------|-----------|-------------|
| `Theme.FREE_LIGHT` | Синий `#4099f3` | Белый `#ffffff` |
| `Theme.FREE_DARK` | Синий `#4099f3` | Тёмно-серый `#373f4a` |
| `Theme.DREAMER_LIGHT` | Коричневый `#9c8069` | Белый `#ffffff` |
| `Theme.DREAMER_DARK` | Коричневый `#9c8069` | Тёмно-серый `#40444a` |

Цвета определены в [ButtonTheme.java](../../voboost-components/src/main/java/ru/voboost/components/button/ButtonTheme.java).

## Состояния

- **Обычное** — внешний вид по умолчанию
- **Нажатое** — мгновенная смена цвета при касании
- **Отключённое** — пониженная непрозрачность (фон с alpha 25%)

## Ввод

Областью нажатия является только примитив в форме таблетки; заголовок и оба описания не вызывают слушатель. Нажатие на примитив передаёт в слушатель публичный `Button` как `View`, а `performClick()` использует стандартные listener и clickable-поведение `View`. Отменённый жест и отключённая кнопка слушатель не вызывают.

## Размеры

Все размеры — в **пикселях** (требование автомобильной платформы). Константы в `ButtonDimensions`.

| Свойство | Значение |
|----------|-------|
| Высота | 70px |
| Радиус скругления | 35px (форма таблетки) |
| Размер текста | 28px |
| Горизонтальный padding | 38px |

Ширина вычисляется из текста + padding (`onMeasure`); минимальной ширины нет.

## Структура файлов

```
button/
├── Button.java             # Основная реализация
├── ButtonPrimitive.java    # Рендеринг на canvas и касания (package-private)
├── Button.compose.kt               # Обёртка для Compose
├── ButtonStyle.java         # Enum стилей
├── ButtonTheme.java         # Цвета, размеры, схемы
├── Button.test/
│   ├── ButtonTestUnit.java  # Unit-тесты
│   └── ButtonTestVisual.java # Тесты визуальной регрессии
└── Button.screenshots/     # Эталонные изображения
```
