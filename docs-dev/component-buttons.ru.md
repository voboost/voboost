# Компонент Buttons

[Screen](component-screen.ru.md) | [Tabs](component-tabs.ru.md) | [Panel](component-panel.ru.md) | [Section](component-section.ru.md) | [Radio](component-radio.ru.md) | [Checkbox](component-checkbox.ru.md) | [Button](component-button.ru.md) | **Buttons** | [Select](component-select.ru.md) | [Dialog](component-dialog.ru.md) | [Popup](component-popup.ru.md) | [Toast](component-toast.ru.md) | [Text](component-text.ru.md) | [Hint](component-hint.ru.md) | [Font](component-font.ru.md) | [Theme](component-theme.ru.md) | [Language](component-language.ru.md).

[English](component-buttons.md) | **Русский**

## Архитектура

- **[Buttons.java](../../voboost-components/src/main/java/ru/voboost/components/buttons/Buttons.java)** — Java Custom View: `LinearLayout`, который размещает необязательный текст заголовка/описания плюс ряд дочерних view `Button` (рендеринг делегируется кнопкам)
- **[Buttons.compose.kt](../../voboost-components/src/main/java/ru/voboost/components/buttons/Buttons.compose.kt)** — Kotlin-обёртка для Compose: лёгкая интеграция через AndroidView

Вся логика — в Java. Kotlin-обёртка только мостит к Compose.

## Использование

### Java

```java
List<ButtonConfig> configs = Arrays.asList(
    new ButtonConfig("value1", "Вариант 1"),
    new ButtonConfig("value2", "Вариант 2")
);

Buttons buttons = new Buttons(context);
buttons.setButtons(configs);
buttons.setTheme(Theme.FREE_DARK);
buttons.setSelectedValue("value1");
buttons.setOnValueChangeListener(newValue -> {
    // Обработка изменения выбора
});
parentLayout.addView(buttons);
```

### Kotlin (рекомендуется — DSL)

Расширения с receiver-lambda, определённые в `Section.kt`, оборачивают Java Builder, поэтому Kotlin-вызывающему коду не нужно писать `.create()`/`.build()`. Лямбда выполняется на `Buttons.Builder`, поэтому необязательные свойства задаются как вызовы методов Builder (`title(map)`, `margin(l, t, r, b)`, `onValueChange { }`).

```kotlin
section.addButtons(Theme.FREE_DARK, Language.EN, configs, "value1") {
    title(titles)
    margin(0, 20, 0, 20)
    onValueChange { /* Обработка изменения выбора */ }
}
```

### Kotlin (View API)

```kotlin
val configs = listOf(
    ButtonConfig("value1", "Вариант 1"),
    ButtonConfig("value2", "Вариант 2")
)

val buttons = Buttons(context).apply {
    setButtons(configs)
    setTheme(Theme.FREE_DARK)
    setSelectedValue("value1")
    setOnValueChangeListener { newValue ->
        // Обработка изменения выбора
    }
}
parentLayout.addView(buttons)
```

### Compose

```kotlin
val configs = listOf(
    ButtonConfig("value1", "Вариант 1"),
    ButtonConfig("value2", "Вариант 2")
)

Buttons(
    buttons = configs,
    theme = Theme.FREE_DARK,
    selectedValue = "value1",
    onValueChange = { newValue ->
        // Обработка изменения выбора
    }
)
```

## API

### Java Custom View

```java
// Конфигурация
void setButtons(List<ButtonConfig> buttons)
void setSelectedValue(String value)
String getSelectedValue()

// Необязательный текст
void setTitle(Map<String, String> text)              // Локализованный заголовок надо всем
void setDescriptionAbove(Map<String, String> text)    // Локализованное описание под заголовком
void setRightText(Map<String, String> text)           // Локализованный текст справа
void setDescription(Map<String, String> text)         // Локализованное описание снизу

// Тема и язык
void setTheme(Theme theme)
Theme getCurrentTheme()
void setLanguage(Language language)
Language getCurrentLanguage()

// Отступы (управляются родительским Section)
void setMargin(int left, int top, int right, int bottom)
int getMarginLeft()
int getMarginTop()
int getMarginRight()
int getMarginBottom()
boolean isMarginSet()

// События
void setOnValueChangeListener(OnValueChangeListener listener)

interface OnValueChangeListener {
    void onValueChange(String newValue);
}
```

Локализованные setters и методы Builder сохраняют неизменяемые снимки в порядке вставки. Разрешение использует точный язык, затем первое ненулевое сохранённое значение, затем `""`; последующее изменение исходной Map не влияет на View.

### Builder API

Текучий (fluent) builder для построения полностью настроенной view `Buttons`. Это точка входа для Java; в Kotlin предпочтительнее DSL-расширение из раздела [Kotlin (рекомендуется — DSL)](#kotlin-рекомендуется--dsl), которое оборачивает этот Builder.

```java
Buttons buttons = Buttons.create(context, Theme.FREE_DARK, Language.EN, configs, "value1")
    .title(titles)
    .descriptionAbove(descAbove)
    .rightText(right)
    .description(desc)
    .margin(0, 20, 0, 20)
    .onValueChange(newValue -> { /* ... */ })
    .build();
```

```java
static Buttons.Builder create(Context, Theme, Language, List<ButtonConfig> buttons, String selectedValue)

// Методы builder (каждый возвращает Builder для цепочечного вызова)
Builder title(Map<String, String> title)
Builder descriptionAbove(Map<String, String> descriptionAbove)
Builder rightText(Map<String, String> rightText)
Builder description(Map<String, String> description)
Builder marginTop(int top)
Builder marginBottom(int bottom)
Builder marginLeft(int left)
Builder marginRight(int right)
Builder margin(int left, int top, int right, int bottom)
Builder onValueChange(OnValueChangeListener listener)
Buttons build()
```

### Обёртка для Compose

```kotlin
@Composable
fun Buttons(
    buttons: List<ButtonConfig>,
    theme: Theme,
    title: Map<String, String>? = null,
    descriptionAbove: Map<String, String>? = null,
    selectedValue: String? = null,
    onValueChange: ((String) -> Unit)? = null,
    rightText: Map<String, String>? = null,
    description: Map<String, String>? = null,
    language: Language? = null,
    onViewCreated: ((Buttons) -> Unit)? = null
)
```

Nullable-параметры — управляемые значения: non-null применяет значение, а последующий `null` сбрасывает соответствующее нативное состояние того же экземпляра `Buttons`. `title`/ `descriptionAbove = null` скрывают эти тексты; `selectedValue = null` очищает сохранённую выборку; `rightText`/`description = null` скрывают эти тексты; `language = null` вызывает `resetLanguage()` (текст приводится к языку «en»). Необязательный `onValueChange` физически сбрасывается при `null` и переустанавливает тот же запомненный нативный listener при возврате к non-null, поэтому переход `A -> B -> null -> A` не оставляет устаревший путь доставки. `onViewCreated` — одноразовый хук создания, он не вызывается повторно при рекомпозиции.

### ButtonConfig

```java
public class ButtonConfig {
    @NonNull
    private final String value;  // Уникальный идентификатор
    @NonNull
    private final String text;  // Отображаемый текст
    @NonNull
    private final ButtonStyle style;  // Только информационный — всегда рендерится как SECONDARY

    public ButtonConfig(@NonNull String value, @NonNull String text, @NonNull ButtonStyle style)
    public ButtonConfig(@NonNull String value, @NonNull String text)  // По умолчанию SECONDARY
}
```

Поле `style` **только информационное**. `Buttons` всегда отображает каждый дочерний элемент как `ButtonStyle.SECONDARY` и никогда не повышает кнопку до `PRIMARY`; `updateButtonStyles()` не обращается к `style`. Поле сохранено для стабильности API, чтобы вызывающий код мог выразить намерение, не влияя на отрисовку.

## Раскладка

```
┌───────┐ ┌─────────┐  Текст справа (необязательно)
│ Btn 1 │ │ Btn 2   │
└───────┘ └─────────┘
Текст описания снизу (необязательно, marginTop 14px)
```

## Стили

| Стиль | Описание |
|-------|-------------|
| `ButtonStyle.PRIMARY` | Акцентный фон (синий/коричневый), белый текст |
| `ButtonStyle.SECONDARY` | Нейтральный фон (серый), текст зависит от темы |

## Темы

| Enum | Основной фон | Дополнительный фон |
|------|-----------|-------------|
| `Theme.FREE_LIGHT` | Синий `#4099f3` | Белый `#ffffff` |
| `Theme.FREE_DARK` | Синий `#4099f3` | Тёмно-серый `#373f4a` |
| `Theme.DREAMER_LIGHT` | Коричневый `#9c8069` | Белый `#ffffff` |
| `Theme.DREAMER_DARK` | Коричневый `#9c8069` | Тёмно-серый `#40444a` |

Цвета определены в [ButtonsTheme.java](../../voboost-components/src/main/java/ru/voboost/components/buttons/ButtonsTheme.java).

## Выбор

- Все кнопки отображаются как `ButtonStyle.SECONDARY` — кнопки являются независимыми вторичными действиями, выбранного/primary-состояния нет
- `selectedValue` всё ещё хранится и сообщается через `getSelectedValue()`, но он не меняет визуальный стиль какой-либо кнопки
- Программный `setSelectedValue(String)` НЕ вызывает `OnValueChangeListener` — колбэк срабатывает только при касании пользователем дочерней кнопки, ровно один раз со значением этой кнопки

## Размеры

Все размеры — в **пикселях** (требование автомобильной платформы). Геометрия кнопки — в `ButtonTheme`; зазоры контейнера и размеры подписей — в `ButtonsDimensions`. Размер текста заголовка (32px) задаётся напрямую в `Buttons.init` и больше 24px для описания/текста справа.

| Свойство | Значение |
|----------|-------|
| Высота кнопки | 70px |
| Радиус скругления кнопки | 35px (форма таблетки) |
| Размер текста кнопки | 28px |
| Горизонтальный padding кнопки | 38px |
| Зазор между кнопками | 10px (каждый дочерний элемент имеет левый отступ 10px, включая первый) |
| Зазор от кнопок до текста | 20px |
| Зазор от кнопок до описания | 14px |
| Размер текста заголовка | 32px |
| Размер описания / текста справа | 24px |

## Производительность

Метод `rebuildButtons()` оптимизирует производительность за счёт переиспользования существующих экземпляров Button:
- При обновлении тем же количеством кнопок существующие view переиспользуются
- При увеличении количества кнопок новые view создаются только для дополнительных кнопок
- При уменьшении количества кнопок лишние view удаляются

Эта оптимизация снижает накладные расходы на создание view и повышает производительность при обновлении списков кнопок.

## Структура файлов

```
buttons/
├── Buttons.java             # Основная реализация
├── Buttons.compose.kt               # Обёртка для Compose
├── ButtonConfig.java         # Конфигурация кнопки
├── ButtonsTheme.java         # Цвета, размеры, схемы
├── Buttons.test/
│   ├── ButtonsTestUnit.java  # Unit-тесты
│   └── ButtonsTestVisual.java # Тесты визуальной регрессии
└── Buttons.screenshots/     # Эталонные изображения
```
