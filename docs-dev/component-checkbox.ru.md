# Компонент Checkbox

[Screen](component-screen.ru.md) | [Tabs](component-tabs.ru.md) | [Panel](component-panel.ru.md) | [Section](component-section.ru.md) | [Radio](component-radio.ru.md) | **Checkbox** | [Button](component-button.ru.md) | [Buttons](component-buttons.ru.md) | [Select](component-select.ru.md) | [Dialog](component-dialog.ru.md) | [Popup](component-popup.ru.md) | [Toast](component-toast.ru.md) | [Text](component-text.ru.md) | [Hint](component-hint.ru.md) | [Font](component-font.ru.md) | [Theme](component-theme.ru.md) | [Language](component-language.ru.md).

[English](component-checkbox.md) | **Русский**

## Архитектура

- **[Checkbox.java](../../voboost-components/src/main/java/ru/voboost/components/checkbox/Checkbox.java)** — Java-контейнер (LinearLayout): layout, состояние; делегирует рендеринг на canvas, анимации и обработку касаний package-private [CheckboxPrimitive.java](../../voboost-components/src/main/java/ru/voboost/components/checkbox/CheckboxPrimitive.java)
- **[Checkbox.compose.kt](../../voboost-components/src/main/java/ru/voboost/components/checkbox/Checkbox.compose.kt)** — Kotlin-обёртка для Compose: лёгкая интеграция через AndroidView

## Использование

### Java

```java
Checkbox checkbox = new Checkbox(context);
checkbox.setTheme(Theme.FREE_DARK);
checkbox.setChecked(true);
checkbox.setOnCheckedChangeListener(isChecked -> {
    // Обработка изменения
});
parentLayout.addView(checkbox);
```

### Compose

```kotlin
var checked by remember { mutableStateOf(false) }

Checkbox(
    theme = Theme.FREE_DARK,
    checked = checked,
    onCheckedChange = { checked = it }
)
```

### Kotlin (рекомендуется — DSL)

Расширения с receiver-lambda, определённые в `Section.kt`, оборачивают Java Builder, поэтому Kotlin-вызывающему коду не нужно писать `.create()`/`.build()`. Лямбда выполняется на `Checkbox.Builder`, поэтому необязательные свойства задаются как вызовы методов Builder (`label(map)`, `margin(l, t, r, b)`, `onCheckedChange { }`).

```kotlin
section.addCheckbox(Theme.FREE_DARK, Language.EN, true) {
    label(labelMap)
    margin(0, 10, 0, 10)
    onCheckedChange { /* Обработка изменения */ }
}
```

Публичный Checkbox предоставляет один accessibility-узел переключения с локализованной подписью и текущими значениями checkable, checked и enabled. Приватный примитив отрисовки и дублирующая подпись исключены из обхода. Accessibility click использует существующий путь нажатия на всю строку и его callback; отключённый Checkbox не переключается. Это позволяет независимому UI-наблюдателю читать состояние без изменения layout, отрисовки или касаний.

## API

### Java Custom View

```java
void setTheme(Theme theme)
Theme getCurrentTheme()

void setChecked(boolean checked)        // мгновенно, без анимации
void setCheckedAnimated(boolean checked) // с анимацией
boolean isChecked()

void setEnabled(boolean enabled)
boolean isEnabled()

void setLabel(Map<String, String> label)         // локализованный текст метки
void setDescription(Map<String, String> desc)    // локализованное описание ниже
void setLabelBold(boolean bold)                   // жирная метка заголовок-checkbox

void setMargin(int left, int top, int right, int bottom)  // управляется родительским Section
int getMarginLeft() / getMarginTop() / getMarginRight() / getMarginBottom()
boolean isMarginSet()

void setOnCheckedChangeListener(OnCheckedChangeListener listener)

interface OnCheckedChangeListener {
    void onCheckedChange(boolean isChecked);
}
```

Setters и методы Builder для `label`/`description` сохраняют неизменяемые снимки в порядке вставки. Разрешение использует точный язык, затем первое ненулевое сохранённое значение, затем `""`; последующее изменение исходной Map не меняет метку.

### Builder API

`Checkbox.create(...)` возвращает `Builder` для текучего (fluent) построения. Это точка входа для Java; в Kotlin предпочтительнее DSL-расширение из раздела [Kotlin (рекомендуется — DSL)](#kotlin-рекомендуется--dsl), которое оборачивает этот Builder.

```java
Checkbox.create(context, theme, language, checked)
    .label(Map<String, String>)            // необязательная локализованная метка
    .description(Map<String, String>)      // необязательное локализованное описание
    .margin(left, top, right, bottom)      // или marginTop/Bottom/Left/Right
    .onCheckedChange(listener)             // необязательный callback изменения состояния
    .build();
```

Обязательные аргументы фабрики: `context`, `theme`, `language`, `checked` (boolean). Все цепочечные методы необязательны.

### Обёртка для Compose

```kotlin
@Composable
fun Checkbox(
    theme: Theme,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    label: Map<String, String>? = null,
    labelBold: Boolean = false,
    description: Map<String, String>? = null,
    language: Language? = null,
    onViewCreated: ((Checkbox) -> Unit)? = null,
)
```

Nullable-параметры — управляемые значения: non-null применяет значение, а последующий `null` сбрасывает соответствующее нативное состояние того же экземпляра `Checkbox`. `label`/`description = null` скрывают эти тексты; `language = null` вызывает `resetLanguage()` (текст приводится к языку «en»). `labelBold` применяется безусловно (по умолчанию `false`) и переключает метку в жирный стиль «title-checkbox». `onViewCreated` — одноразовый хук создания, он не вызывается повторно при рекомпозиции.

## Рендеринг

## Ввод

Вся строка checkbox является нативной областью нажатия и через `performClick()` переключает значение ровно один раз. Отменённый жест, отключённый checkbox или `UP` без отслеженного `DOWN` не меняют значение и не вызывают слушатель. `UP` за пределами строки очищает жест без переключения; программный `performClick()` также очищает отслеживаемый жест строки.

`CheckboxPrimitive.onDraw` отрисовывает 3 слоя (на основе bitmap, не вектор):
1. **Фон дорожки** — bitmap из `CheckboxPrimitiveBackground.getBackground(isDark)`, отцентрированный на дорожке 83x42, рисуется с `disabledAlpha` при отключении (цвет OFF встроен в ассет)
2. **Маска** — сплошной скруглённый прямоугольник, залитый цветом `trackOnEnd`; alpha модулируется прогрессом анимации (`animationProgress`, ограничен 0..1), рисуется только когда progress > 0. Рисуется внутри прямоугольника-вставки 82x40 (вставка 1px), радиус скругления = `MASK_HEIGHT_PX / 2`
3. **Ручка** — bitmap из `CheckboxPrimitiveKnob.getBitmap(isDark)`, отцентрированный на вычисляемом якоре, чья X-координата интерполируется от `KNOB_PADDING_PX` до `width - padding - diameter - 1` по `animationProgress`; X управляется `OvershootInterpolator` (tension 1.0). Alpha ручки умножается на `disabledAlpha` (и на `testMaskAlpha`, когда зафиксировано для тестов)

## Размеры

| Свойство | Значение |
|----------|-------|
| Ширина | 83px (TRACK_WIDTH_PX) |
| Высота | 42px (TRACK_HEIGHT_PX) |
| Радиус скругления | 21px |
| Размер ручки | 34px (светлая) / 40px (тёмная) |
| Padding ручки | 5px |
| Высота маски | 40px (MASK_HEIGHT_PX) |
| Длительность анимации | 400ms |

## Структура файлов

```
checkbox/
├── Checkbox.java            # Основная реализация
├── CheckboxPrimitive.java       # Рендеринг на canvas и касания (package-private)
├── CheckboxPrimitiveBackground.java # Битмап фона дорожки (package-private)
├── CheckboxPrimitiveKnob.java   # Битмап ручки (package-private)
├── Checkbox.compose.kt              # Обёртка для Compose
├── CheckboxTheme.java       # Цвета, размеры, схемы
├── Checkbox.test/
│   ├── CheckboxTestUnit.java
│   └── CheckboxTestVisual.java
└── Checkbox.screenshots/
```
