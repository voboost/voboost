# Компонент Select

[Screen](component-screen.ru.md) | [Tabs](component-tabs.ru.md) | [Panel](component-panel.ru.md) | [Section](component-section.ru.md) | [Radio](component-radio.ru.md) | [Checkbox](component-checkbox.ru.md) | [Button](component-button.ru.md) | [Buttons](component-buttons.ru.md) | **Select** | [Dialog](component-dialog.ru.md) | [Popup](component-popup.ru.md) | [Toast](component-toast.ru.md) | [Text](component-text.ru.md) | [Hint](component-hint.ru.md) | [Font](component-font.ru.md) | [Theme](component-theme.ru.md) | [Language](component-language.ru.md).

[English](component-select.md) | **Русский**

## Архитектура

- **[Select.java](../../voboost-components/src/main/java/ru/voboost/components/select/Select.java)** — Публичный контейнер (LinearLayout): текст заголовка/описания + вертикальная разметка; делегирует триггер/всплывающее окно в SelectPrimitive
- **[SelectPrimitive.java](../../voboost-components/src/main/java/ru/voboost/components/select/SelectPrimitive.java)** — Внутренний canvas-триггер (таблетка + текст + стрелка); владеет всплывающим окном и открывает его
- **[Select.compose.kt](../../voboost-components/src/main/java/ru/voboost/components/select/Select.compose.kt)** — Обёртка для Compose
- **[SelectWheel.java](../../voboost-components/src/main/java/ru/voboost/components/select/SelectWheel.java)** — 3D-цилиндрический выбор (преобразования Camera/Matrix)
- **[SelectPopup.java](../../voboost-components/src/main/java/ru/voboost/components/select/SelectPopup.java)** — Внутреннее: интеграция Popup + WheelView
- **[SelectOption.java](../../voboost-components/src/main/java/ru/voboost/components/select/SelectOption.java)** — Модель данных (значение + локализованная метка)

## Использование

### Java

```java
Select select = new Select(context);
select.setOptions(Arrays.asList(
    new SelectOption("auto", Map.of("en", "Automatic", "ru", "Авто")),
    new SelectOption("manual", Map.of("en", "Manual", "ru", "Ручной"))
));
select.setLanguage(Language.EN);
select.setTheme(Theme.FREE_DARK);
select.setSelectedValue("auto");
select.setOnValueChangeListener(newValue -> {
    // Обработка выбора
});
```

### Compose

```kotlin
var selected by remember { mutableStateOf("auto") }

Select(
    options = listOf(
        SelectOption("auto", mapOf("en" to "Automatic", "ru" to "Авто")),
        SelectOption("manual", mapOf("en" to "Manual", "ru" to "Ручной"))
    ),
    language = Language.EN,
    theme = Theme.FREE_DARK,
    value = selected,
    onValueChange = { selected = it }
)
```

Compose-обёртка устанавливает один запомненный нативный listener выбора и всегда обращается к последней лямбде `onValueChange` через `rememberUpdatedState`; блок update никогда не создаёт новый нативный listener при рекомпозиции. Необязательные локализованные тексты (`title`, `descriptionAbove`, `description`) и строки попапа (`confirmText`, `cancelText`) — управляемые значения: `null` сбрасывает соответствующее нативное состояние. `mode` (`Select.Mode.FLAT` или `Select.Mode.CURVED`, по умолчанию `CURVED`) выбирает стиль отрисовки колеса и применяется безусловно. `onViewCreated` — одноразовый хук создания, он не вызывается повторно при рекомпозиции.

### Kotlin (рекомендуется — DSL)

Расширения с receiver-lambda, определённые в `Section.kt`, оборачивают Java Builder, поэтому Kotlin-вызывающему коду не нужно писать `.create()`/`.build()`. Лямбда выполняется на `Select.Builder`, поэтому необязательные свойства задаются как вызовы методов Builder (`title(map)`, `margin(l, t, r, b)`, `onValueChange { }`).

```kotlin
section.addSelect(Theme.FREE_DARK, Language.EN, options, "auto") {
    title(titleMap)
    margin(0, 10, 0, 10)
    onValueChange { /* Обработка выбора */ }
}
```

## Публичный API

### Опции и значение

- `setOptions(List<SelectOption>)` — список опций (null обрабатывается как пустой)
- `setSelectedValue(String)` — выбранное значение (пустые строки игнорируются)
- `getSelectedValue()` — текущее значение
- `setOnValueChangeListener(OnValueChangeListener)` — обратный вызов изменения значения

### Текст (необязательно, локализованный `Map<String,String>`)

- `setTitle(Map<String,String>)` — заголовок над триггером
- `setDescriptionAbove(Map<String,String>)` — описание над триггером
- `setDescription(Map<String,String>)` — описание справа от триггера

Эти setters, соответствующие методы Builder, Map кнопок popup и метки `SelectOption` сохраняют снимки в порядке вставки. Разрешение использует точный язык, затем первое ненулевое сохранённое значение, затем `""`; SelectOption никогда не подставляет внутренний value.

### Режим колеса и кнопки всплывающего окна

- `setMode(Mode)` — `Mode.FLAT` (без кривизны, без затухания) или `Mode.CURVED` (по умолчанию; 3D-кривая + атмосферное затухание)
- `setConfirmText(Map<String,String>)` — текст кнопки подтверждения во всплывающем окне (null сбрасывает на значение по умолчанию)
- `setCancelText(Map<String,String>)` — текст кнопки отмены во всплывающем окне (null сбрасывает на значение по умолчанию)

### Раскладка

- `setMargin(int left, int top, int right, int bottom)` — внешние отступы (потребляются родительским Section)

## Builder API

`Select.create(context, theme, language, options, selectedValue)` возвращает `Select.Builder` для текучего построения. Это точка входа для Java; в Kotlin предпочтительнее DSL-расширение из раздела [Kotlin (рекомендуется — DSL)](#kotlin-рекомендуется--dsl), которое оборачивает этот Builder.

```java
Select select = Select.create(context, Theme.FREE_DARK, Language.EN, options, "auto")
    .title(Map.of("en", "Drive mode", "ru", "Режим"))
    .mode(Select.Mode.CURVED)
    .confirmText(Map.of("en", "Confirm", "ru", "Подтвердить"))
    .cancelText(Map.of("en", "Cancel", "ru", "Отмена"))
    .onValueChange(listener)
    .build();
```

Методы builder: `title`, `descriptionAbove`, `description`, `marginTop`, `marginBottom`, `marginLeft`, `marginRight`, `margin`, `onValueChange`, `mode`, `confirmText`, `cancelText`, `build`.

## Колесо (FLAT / CURVED)

`Select.setMode(Mode.FLAT|CURVED)` (по умолчанию CURVED). CURVED: изогнутое 3D-вращение + атмосферное затухание — выбранный элемент крупный и яркий, боковые элементы уходят в тень (3 чётких + 2 затухающих). FLAT: без кривизны, без затухания (соответствует продакшн-WheelDefault). Во всплывающем окне есть явные кнопки Confirm/Cancel: прокрутка только перемещает колесо; Confirm фиксирует значение. SelectWheel соответствует исходной реализации WheelView:

| Возможность | Реализация | Продакшн-использование |
|-------------|-----------|-----------|
| **3D-кривая** | Camera + Matrix: rotateX + translate Z | Только CURVED |
| **Прокрутка** | Scroller + VelocityTracker: fling, snap, инерция | Всегда |
| **Атмосферность** | Затухание alpha по расстоянию от центра | Только CURVED |
| **Циклический режим** | Бесконечная прокрутка с заворотом | **Отключено** (нециклический: первый→последний) |
| **Индикатор** | Горизонтальные линии сверху/снизу от выбранного | **Отключено** |
| **Шторка** | Полупрозрачная подсветка на выбранном | **Отключено** |
| **Стиль выбранного** | Крупный шрифт, жирный, отличный цвет | Всегда |

SelectWheel поддерживает циклическую прокрутку, линии-индикаторы и шторку-подсветку как возможности класса, но `SelectPopup.applyMode()` отключает все три в продакшне: колесо нециклическое, а выделение показывается размером и цветом (крупный шрифт, жирный, отличный цвет) без линий-индикаторов и шторки.

Открытое всплывающее окно владеет неизменяемым снимком labels/values. Изменение опций, языка, темы, режима или текста кнопок закрывает текущую сессию; повторное открытие использует новую конфигурацию. Confirm сначала останавливает и привязывает колесо к ближайшей позиции, поэтому не фиксирует устаревшую позицию; до внешнего callback он потребляет callback и снимок сессии, поэтому одна сессия не может подтвердиться дважды. Изменение языка также запускает повторное измерение триггера: локализованная подпись может иметь другую ширину.

Триггер открывает окно только по `ACTION_UP` внутри границ. Нажатие колеса вызывает click-обработку только по `ACTION_UP`; drag или `ACTION_CANCEL` вместо этого останавливают и привязывают колесо. Любое закрытие окна (включая закрытие снаружи и detach) очищает callback выбора и снимок сессии до того, как поздний Confirm сможет зафиксировать значение. Равные нормализованные опции/конфигурация/выбранное значение идемпотентны; фактическое изменение закрывает сессию. Detach колеса очищает состояние жеста и снимает запрет перехвата у родителя.

## Структура файлов

```
select/
├── Select.java             # Публичный контейнер (LinearLayout)
├── SelectPrimitive.java    # Внутренний canvas-триггер
├── Select.compose.kt               # Обёртка для Compose
├── SelectOption.java        # Модель данных
├── SelectWheel.java         # 3D WheelView
├── SelectPopup.java         # Внутреннее всплывающее окно
├── SelectTheme.java         # Цвета, размеры
├── Select_arrow_*.png       # Ассеты стрелки триггера (по одному на тему: free-light/dark, dreamer-light/dark)
├── Select.test/
│   ├── SelectTestUnit.java
│   └── SelectTestVisual.java
└── Select.screenshots/
```
