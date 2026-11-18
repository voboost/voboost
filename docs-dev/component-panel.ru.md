# Компонент Panel

[Screen](component-screen.ru.md) | [Tabs](component-tabs.ru.md) | **Panel** | [Section](component-section.ru.md) | [Radio](component-radio.ru.md) | [Checkbox](component-checkbox.ru.md) | [Button](component-button.ru.md) | [Buttons](component-buttons.ru.md) | [Select](component-select.ru.md) | [Dialog](component-dialog.ru.md) | [Popup](component-popup.ru.md) | [Toast](component-toast.ru.md) | [Text](component-text.ru.md) | [Hint](component-hint.ru.md) | [Font](component-font.ru.md) | [Theme](component-theme.ru.md) | [Language](component-language.ru.md).

[English](component-panel.md) | **Русский**

## Архитектура

- **[Panel.java](../../voboost-components/src/main/java/ru/voboost/components/panel/Panel.java)** — Java ViewGroup: вертикальный контейнер `LinearLayout` для компонентов Section
- **[Panel.compose.kt](../../voboost-components/src/main/java/ru/voboost/components/panel/Panel.compose.kt)** — Kotlin-обёртка для Compose
- **[Panel.kt](../../voboost-components/src/main/java/ru/voboost/components/panel/Panel.kt)** — Kotlin DSL-расширения (`addSection`)

Вся логика — в Java. Kotlin-обёртка только мостит к Compose.

## Назначение

Panel — **прозрачный контейнер**, предназначенный для:

- Вертикальной группировки одного или нескольких компонентов `Section`
- Плавных переходов между панелями в `Screen` через `Screen.setActivePanel()`
- Применения компактного режима (верхний отступ) и опционального фонового изображения для компактных раскладок
- Распространения темы и языка на дочерние компоненты

**Прокрутка:** Panel **не** обеспечивает прокрутку. Вертикальная прокрутка — ответственность окружающего `ScreenView` в `Screen`.

## Использование

### Java

```java
// Простой случай (одна Section)
Panel panel = new Panel(context);
panel.setTheme(Theme.FREE_LIGHT);

Section section = new Section(context);
section.setTitle(titleMap);
section.addView(radio);
panel.addView(section);

// Сложный случай (несколько Section)
Panel panel = new Panel(context);
for (int i = 0; i < sectionCount; i++) {
    Section section = new Section(context);
    section.setTitle(titleMap);
    section.addView(radio);
    panel.addView(section);
}
// Поместите Panel внутрь ScreenView; прокрутку обеспечивает ScreenView, а не Panel.
```

### Java Builder (рекомендуется для нового Java-кода)

`Panel.create(...)` возвращает одноразовый `Builder`. Section проверяются (не null, уникальны по ссылке, без чужого родителя) **до** любого прикрепления, поэтому неудачный `build()` не оставляет частично прикреплённого префикса. Переданный `List` снимается в снимок и никогда не изменяется. Построенный Panel не прикреплён; `Screen` владеет прикреплением.

```java
Panel panel = Panel.create(context, Theme.FREE_LIGHT, Language.EN)
        .compact(true)
        .image(backgroundBitmap)
        .sections(sections)   // снимок списка; либо addSection(...) для каждого ребёнка
        .build();
// panel сконфигурирован и не прикреплён; Screen забирает владение через setPanels(...).
```

Повторный `build()` на том же Builder бросает `IllegalStateException`.

### Kotlin (рекомендуется — DSL)

Расширение с receiver-lambda `addSection(theme, language, title) { }`, определённое в `Panel.kt`, оборачивает Java `Section.Builder`, поэтому Kotlin-вызывающему коду не нужно писать `.create()`/`.build()`. Лямбда выполняется на `Section.Builder`, поэтому необязательные свойства задаются как вызовы методов Builder (`titleCheckbox(true)`, `infoText(map)`). Внутри построенной Section дочерние компоненты добавляются DSL-перегрузками Section (`addButton(...) { }`, `addRadio(...) { }`, ...).

```kotlin
val panel = Panel(context).apply { setTheme(Theme.FREE_LIGHT) }

panel.addSection(Theme.FREE_DARK, Language.EN, mapOf("en" to "Settings")) {
    titleCheckbox(true)
    addRadio(Theme.FREE_DARK, Language.EN, buttons, "en") { /* ... */ }
}
```

Для самостоятельного создания используйте фабрику `panel()` из `PanelFactory.kt`:

```kotlin
val panel = panel(context, Theme.FREE_DARK, Language.EN) {
    compact(true)
    addSection(section)
}
```

### Kotlin (View API)

```kotlin
val panel = Panel(context).apply {
    setTheme(Theme.FREE_LIGHT)
    addView(section)
}
```

### Compose

```kotlin
Panel(theme = Theme.FREE_LIGHT, language = Language.EN)
```

## API

### Java Custom View

```java
// Тема
void setTheme(Theme theme)                 // выбрасывает исключение при null
Theme getCurrentTheme()

// Язык
void setLanguage(Language language)        // выбрасывает исключение при null
Language getCurrentLanguage()

// Распространение (на дочерние компоненты)
void propagateTheme(Theme theme)
void propagateLanguage(Language language)

// Фоновое изображение (компактный режим) — рисуется за прозрачными промежутками между Section
Bitmap getImage()
void setImage(Bitmap image)
int getImageWidth()                         // px, PanelTheme.IMAGE_WIDTH
int getImageMarginRight()                   // px, отрицательное = выходит за край

// Компактный режим — применяет COMPACT_TOP_MARGIN как верхний отступ, PADDING_BOTTOM как нижний
boolean isCompact()
void setCompact(boolean compact)
```

`addView`, `removeView`, `removeViewAt` переопределены и вызывают `updateChildMargins()` после вызова super; `removeAllViews` переопределён без него. `updateChildMargins()` устанавливает флаг `includeBottomMargin` у дочерних Section так, что только последняя Section опускает свой нижний отступ (Panel.java:82-119).

### Обёртка для Compose

```kotlin
@Composable
fun Panel(theme: Theme, language: Language = Language.EN)
```

Kotlin-расширения (Panel.kt):

```kotlin
// DSL (рекомендуется): построить Section через Section.Builder, применить config, добавить в Panel
fun Panel.addSection(
    theme: Theme,
    language: Language,
    title: Map<String, String>? = null,
    config: Section.Builder.() -> Unit = {}
): Section

// DSL: создать, настроить и добавить Section одним вызовом
fun Panel.addSection(
    title: Map<String, String>,
    theme: Theme,
    language: Language,
    content: Section.() -> Unit
): Section

// Добавить заранее настроенную Section
fun Panel.addSection(section: Section): Section
```

## Детали реализации

### Архитектура контейнера

- Panel наследует `LinearLayout` (ориентация `VERTICAL`) — Panel.java:19, 77
- Дочерние элементы добавляются непосредственно в Panel; промежуточного контейнера нет
- Panel не имеет визуального оформления — цвета фона и рамки `TRANSPARENT`
- Прокрутку обеспечивает окружающий `ScreenView` в `Screen`, а не Panel

### Переходы панелей

Panel предназначен для работы с `Screen.setActivePanel()` для анимированных переходов:

- Длительность перехода: 300 мс (определено в `ScreenTheme.PANEL_TRANSITION_DURATION`)
- Анимация использует `translationY` для плавного эффекта сдвига вверх/вниз
- Старая панель выезжает, пока новая въезжает, одновременно

### Распространение темы и языка

Panel один раз вызывает каждый setter на snapshot непосредственных детей, реализующих `IThemable` или `ILocalizable`. Более глубокое распространение принадлежит setter дочернего контейнера; обычный `ViewGroup` останавливает его. Поздно добавленный ребёнок сразу и ровно один раз получает каждое текущее ненулевое состояние Panel.

### Визуальное оформление

Panel намеренно не имеет визуального оформления:

- `CORNER_RADIUS`, `PADDING`, `ELEVATION`, `BORDER_WIDTH` равны 0
- Все цвета фона и рамки — `TRANSPARENT` (PanelTheme.java:66-99)
- Визуальное представление обрабатывается дочерними компонентами `Section`

Panel **определяет** ненулевые константы раскладки, используемые компактным режимом и фоновым изображением (см. Размеры).

## Размеры

Все размеры — в **пикселях** (требование автомобильной платформы). Константы в `PanelTheme`.

| Свойство | Значение |
|----------|-------|
| Ширина фонового изображения | 800px |
| Правый отступ фонового изображения | -30px (выходит за край) |
| Верхний отступ в компактном режиме | 50px (применяется как верхний padding в компактном режиме) |
| Нижний padding | 25px (место после последней section) |

Цвета определены в [PanelTheme.java](../../voboost-components/src/main/java/ru/voboost/components/panel/PanelTheme.java).

## Структура файлов

```
panel/
├── Panel.java           # Основная реализация
├── Panel.compose.kt     # Обёртка для Compose
├── Panel.kt             # addSection-расширения (DSL)
├── PanelTheme.java      # Цвета и размеры темы
├── Panel.test/          # Тесты
└── Panel.screenshots/   # Эталонные изображения
```
