# Компонент Tabs

[Screen](component-screen.ru.md) | **Tabs** | [Panel](component-panel.ru.md) | [Section](component-section.ru.md) | [Radio](component-radio.ru.md) | [Checkbox](component-checkbox.ru.md) | [Button](component-button.ru.md) | [Buttons](component-buttons.ru.md) | [Select](component-select.ru.md) | [Dialog](component-dialog.ru.md) | [Popup](component-popup.ru.md) | [Toast](component-toast.ru.md) | [Text](component-text.ru.md) | [Hint](component-hint.ru.md) | [Font](component-font.ru.md) | [Theme](component-theme.ru.md) | [Language](component-language.ru.md).

[English](component-tabs.md) | **Русский**

## Архитектура

- **[Tabs.java](../../voboost-components/src/main/java/ru/voboost/components/tabs/Tabs.java)** — Java Custom View: вертикальный сайдбар, анимированное выделение, рендеринг на canvas
- **[Tabs.compose.kt](../../voboost-components/src/main/java/ru/voboost/components/tabs/Tabs.compose.kt)** — Kotlin-обёртка для Compose

Вертикальный навигационный сайдбар со скользящим индикатором выделения.

## Использование

### Java

```java
Tabs tabs = new Tabs(context);
tabs.setTheme(Theme.FREE_LIGHT);
tabs.setLanguage(Language.EN);

tabs.setItems(Arrays.asList(
    new TabItem("store", Map.of("en", "Store", "ru", "Магазин")),
    new TabItem("settings", Map.of("en", "Settings", "ru", "Настройки"))
));
tabs.setSelectedValue("store");

tabs.setOnValueChangeListener(value -> {
    // Обработка выбора вкладки
});
```

### Kotlin

```kotlin
val tabs = Tabs(context).apply {
    setTheme(Theme.FREE_LIGHT)
    setLanguage(Language.EN)
    setItems(listOf(
        TabItem("store", mapOf("en" to "Store", "ru" to "Магазин")),
        TabItem("settings", mapOf("en" to "Settings", "ru" to "Настройки"))
    ))
    setSelectedValue("store")
    setOnValueChangeListener { value -> /* обработка */ }
}
```

### Compose

```kotlin
var selectedTab by remember { mutableStateOf("store") }

Tabs(
    items = listOf(
        TabItem("store", mapOf("en" to "Store", "ru" to "Магазин")),
        TabItem("settings", mapOf("en" to "Settings", "ru" to "Настройки"))
    ),
    language = Language.EN,
    theme = Theme.FREE_LIGHT,
    value = selectedTab,
    onValueChange = { selectedTab = it }
)
```

## API

### Java

```java
// Конфигурация
void setItems(List<TabItem> items)
void setTheme(Theme theme)
void setLanguage(Language language)

// Значение
void setSelectedValue(String value)
void setSelectedValue(String value, boolean triggerCallback)
String getSelectedValue()
int getSelectedIndex()                         // -1 если совпадений нет

// Состояние
Theme getCurrentTheme()
Language getCurrentLanguage()
static int getSidebarWidth()                   // px; контейнеры раскладки позиционируют контент относительно этого значения
int getPaddingBottom()                         // авто: 25 если хотя бы у одной вкладки more=true, иначе 5
int getTopPadding()
void setTopPadding(int topPadding)             // px; вызывает перераскладку

// Анимация
void setAnimationsEnabled(boolean enabled)     // по умолчанию true; false = без скольжения
boolean isAnimationsEnabled()

// События
void setOnValueChangeListener(OnValueChangeListener listener)
void setOnTabChangeListener(OnTabChangeListener listener)
void addOnTabChangeListener(OnTabChangeListener listener)
void removeOnTabChangeListener(OnTabChangeListener listener)

interface OnValueChangeListener {
    void onValueChange(String value);
}

interface OnTabChangeListener {
    void onTabChanged(int newIndex);
}
```

`setOnTabChangeListener` управляет единственным пользовательским слушателем; его замена или очистка не влияет на наблюдателей из `addOnTabChangeListener`. Дополнительные наблюдатели получают multicast-вызов, а повторное добавление того же экземпляра идемпотентно. Контейнеры, включая Screen, используют этот канал, не забирая пользовательский колбэк. Проверенный выбор публикуется до вызова слушателей, поэтому reentrant-слушатель может сделать более новый выбор без перезаписи внешним dispatch. Каждый dispatch использует снимок слушателей; добавление и удаление влияют на следующие события.

### TabItem

```java
TabItem(String value, Map<String, String> label)
TabItem(String value, Map<String, String> label, boolean enabled)
TabItem(String value, Map<String, String> label, boolean enabled, int marginTop)
TabItem(String value, Map<String, String> label, boolean enabled, int marginTop, boolean more)

String getValue()
Map<String, String> getLabel()
String getText(String langCode)
boolean isEnabled()
int getMarginTop()
boolean hasMore()
boolean isSelected()
```

`TabItem` сохраняет неизменяемый снимок labels в порядке итерации вызывающей стороны. `getText` использует точный код языка, затем первую non-null метку, затем `""`; `value` остаётся внутренним идентификатором выбора и никогда не отображается как localization fallback. `getLabel()` возвращает неизменяемый снимок, поэтому последующие изменения исходной map не меняют отображаемый текст.

> Примечание: `isSelected()` возвращает `false` для любого `TabItem`, созданного через обычные
> конструкторы `new TabItem(...)`. Реальный флаг `selected` несёт только `TabItem`, построенный
> через `Builder.build()` (хранится на анонимном подклассе). Используйте `Tabs.Builder`, который
> читает `TabItem.isSelected()`, чтобы автоматически выбрать соответствующую вкладку при `build()`.

#### Builder

```java
TabItem item = TabItem.create("vehicle", labels)
    .enabled(true)
    .selected(false)
    .marginTop(30)
    .more(true)
    .build();
```

Вкладка с `more(true)` выводит завершающий индикатор ">" с правой стороны вкладки. Индикатор рисуется из PNG-ассетов 30x30, с отдельной парой на каждую яркость темы: тёмные темы используют `Tabs_theme_dark.png` / `Tabs_theme_dark.Tabs_pressed.png` (скопированы побайтово из исходных Voyah `icon_menu_more_nor.png` / `icon_menu_more_pre.png`, серый+альфа); светлые темы используют `Tabs_theme_light.png` / `Tabs_theme_light.Tabs_pressed.png` (из светлого скина Voyah `resc-defaulttheme-simple`, RGBA со значением `#2d3442`, совпадающим с цветом невыделенного текста). При касании такой вкладки индикатор переключается на вариант `*_pressed` — повторяет исходное поведение `state_pressed` на завершающем значке. PNG загружаются лениво из classpath рядом с `Tabs.class`. Текст и фон самой вкладки при нажатии не меняются — меняется только индикатор.

### Обёртка для Compose

```kotlin
@Composable
fun Tabs(
    items: List<TabItem>,
    language: Language,
    theme: Theme,
    value: String,
    onValueChange: (String) -> Unit
)
```

### Builder API

Текучий (fluent) builder для построения полностью настроенных `Tabs` одним вызовом (Tabs.java:909-1003). Builder защитно копирует переданный список элементов на границе builder и отвергает null context/theme/language/items и null-элементы там же. Он разрешает действующее выбранное значение — явное `selectedValue(value)`, иначе первый `TabItem`, у которого `isSelected()` равен `true` — до конструирования или изменения любого состояния Tabs.

Точка входа:

```java
static Tabs.Builder create(Context context, Theme theme, Language language, List<TabItem> items)
```

Пример:

```java
Tabs tabs = Tabs.create(context, Theme.FREE_LIGHT, Language.EN, items)
        .selectedValue("settings")
        .topPadding(50)
        .animationsEnabled(false)
        .onValueChange(listener)
        .onTabChange(tabListener)
        .build();
```

`selectedValue(value)` проверяется по элементам при `build()`: явное значение, не совпадающее ни с одним элементом, выбрасывает `IllegalArgumentException` до мутации любого состояния View Tabs. `topPadding(px)` должен быть неотрицательным.

Методы builder (все возвращают `Builder`):

| Метод | Описание |
|--------|-------------|
| `selectedValue(String)` | Значение для автовыбора (проверяется по элементам при `build()`) |
| `topPadding(int)` | Верхний отступ в px (неотрицательный); переопределяет умолчание |
| `animationsEnabled(boolean)` | Включает/выключает анимацию индикатора выделения |
| `onValueChange(OnValueChangeListener)` | Колбэк изменения значения |
| `onTabChange(OnTabChangeListener)` | Колбэк изменения индекса |
| `build()` | Возвращает настроенные `Tabs`; разрешает действующее выбранное значение до мутации |

## Kotlin Tabs DSL (Слой 2)

Kotlin-потребители View получают двухфазную точку входа `tabs { }` в общем пакете `ru.voboost.components.dsl`. Входные данные снимаются в снимок и проверяются до прикрепления любого View или установки любого слушателя.

```kotlin
import ru.voboost.components.dsl.tabs

val tabs = tabs(context, theme, language, items, selectedValue = "settings") {
    topPadding(50)
    animationsEnabled(false)
}
```

Проверка требует непустых элементов, уникальных значений элементов, `selectedValue`, существующего среди них, и неотрицательного верхнего отступа; при ошибке идентифицируется проблемное значение или индекс, и сборка прерывается до любой мутации. `TabsDsl` не предоставляет изменяемое свойство native `tabs`.

## Детали реализации

Анимированный индикатор выделения на `ValueAnimator` (400 мс) с `OvershootInterpolator` (натяжение 1.0). Сайдбар, прямоугольник выделения и подписи вкладок рисуются напрямую на `Canvas` в `onDraw`. Размеры — в пикселях (требование автомобильной платформы); все размеры хранятся как `public static final` константы в `TabsTheme` — отдельного класса `TabsDimensions` нет. Tabs не сохраняет выбор при смене конфигурации; восстановление состояния лежит на вызывающей стороне (например, через параметр Compose `value` или вызов `setSelectedValue`).

### Состояние выбора

`selectedValue` всегда ссылается на существующее значение элемента или пуст — он не остаётся висячим (orphan). `setSelectedValue` с неизвестным значением (нет совпадающего элемента) очищает выбор в `""` и сбрасывает геометрию индикатора (`animatedY`/`animatedYInitialized`), поэтому устаревшая позиция не может нарисовать призрачный индикатор; эта очистка orphan не вызывает `OnValueChangeListener`. `setItems`, в котором больше нет текущего значения, выполняет такую же очистку перед layout, а пустой список элементов оставляет выбор `""`. При дублирующихся значениях элементов геометрией индикатора владеет ПЕРВЫЙ совпавший элемент. В качестве защиты в глубину `drawSelectionIndicator` дополнительно проверяет `selectedIndex >= 0`, поэтому ни один путь отрисовки не использует устаревшие координаты.

| Константа (TabsTheme) | Значение |
|----------------------|-------|
| `SIDEBAR_WIDTH` | 340 |
| `TAB_ITEM_HEIGHT` | 100 |
| `MORE_SIZE` | 30 |
| `ANIMATION_DURATION` | 400 |

## Структура файлов

```
tabs/
├── Tabs.java                          # Основная реализация
├── Tabs.compose.kt                            # Обёртка для Compose
├── TabItem.java                       # Модель данных
├── TabsTheme.java                     # Цвета тем и размеры
├── Tabs_theme_light.png               # Ассет индикатора (светлая тема)
├── Tabs_theme_light.Tabs_pressed.png  # Индикатор (светлая тема, нажатие)
├── Tabs_theme_dark.png                # Ассет индикатора (тёмная тема)
├── Tabs_theme_dark.Tabs_pressed.png   # Индикатор (тёмная тема, нажатие)
├── Tabs.test/
│   ├── TabsTestUnit.java              # Unit-тесты
│   └── TabsTestVisual.java            # Тесты визуальной регрессии
└── Tabs.screenshots/                  # Эталонные изображения
```

## Обработка ошибок

Компонент Tabs валидирует входные данные и выбрасывает исключения при некорректных аргументах:

- `setTheme(null)` → выбрасывает `IllegalArgumentException`
- `setLanguage(null)` → выбрасывает `IllegalArgumentException`
- `setItems(List<TabItem>)` с null-элементами → выбрасывает `IllegalArgumentException`
- `TabItem(null, label)` → выбрасывает `IllegalArgumentException`
- `TabItem(value, null)` → выбрасывает `IllegalArgumentException`

Отключённые вкладки игнорируются при выборе через касание или `setSelectedValue()`.

## Темы

Все варианты используют прозрачный фон сайдбара; различаются только цвет индикатора выделения и цвета текста.

| Enum | Фон выделения | Текст выделения | Текст невыбранный |
|------|-------------|---------------|-----------------|
| `Theme.FREE_LIGHT` | `#bfffffff` (полупрозрачный белый) | `#4099f3` | `#2d3442` |
| `Theme.FREE_DARK` | `#23272f` | `#4099f3` | `#cacaca` |
| `Theme.DREAMER_LIGHT` | `#ffffff` | `#1a1a1a` | `#2d3442` |
| `Theme.DREAMER_DARK` | `#2a2a2a` | `#ffffff` | `#888888` |

Цвета определены в [TabsTheme.java](../../voboost-components/src/main/java/ru/voboost/components/tabs/TabsTheme.java).

## Отключённые вкладки

Вкладки можно отключать, чтобы запретить пользовательское взаимодействие:

```java
TabItem enabledTab = new TabItem("settings", labels, true);
TabItem disabledTab = new TabItem("admin", labels, false);

tabs.setItems(Arrays.asList(enabledTab, disabledTab));
```

Отключённые вкладки:
- Отображаются серым текстом
- Не реагируют на касания
- Игнорируются при выборе через `setSelectedValue()`
- Не запускают `OnValueChangeListener`
