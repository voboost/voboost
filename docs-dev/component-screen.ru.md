# Компонент Screen

**Screen** | [Tabs](component-tabs.ru.md) | [Panel](component-panel.ru.md) | [Section](component-section.ru.md) | [Radio](component-radio.ru.md) | [Checkbox](component-checkbox.ru.md) | [Button](component-button.ru.md) | [Buttons](component-buttons.ru.md) | [Select](component-select.ru.md) | [Dialog](component-dialog.ru.md) | [Popup](component-popup.ru.md) | [Toast](component-toast.ru.md) | [Text](component-text.ru.md) | [Hint](component-hint.ru.md) | [Font](component-font.ru.md) | [Theme](component-theme.ru.md) | [Language](component-language.ru.md).

[English](component-screen.md) | **Русский**

## Архитектура

- **[Screen.java](../../voboost-components/src/main/java/ru/voboost/components/screen/Screen.java)** — Java ViewGroup: полноэкранный контейнер, управление раскладкой
- **[Screen.compose.kt](../../voboost-components/src/main/java/ru/voboost/components/screen/Screen.compose.kt)** — Kotlin-обёртка для Compose
- **[ScreenTheme.java](../../voboost-components/src/main/java/ru/voboost/components/screen/ScreenTheme.java)** — цвета тем и константы размеров/анимаций
- **[ScreenView.java](../../voboost-components/src/main/java/ru/voboost/components/screen/ScreenView.java)** — обёртка ScrollView с резиновой прокруткой; оборачивает Tabs и каждый Panel

Корневой контейнер, управляющий раскладкой Tabs (боковая панель) и Panel (область содержимого).

## Использование

### Java

```java
Screen screen = new Screen(context);
screen.setTheme(Theme.FREE_LIGHT);
screen.setOffsetX(175);
screen.setOffsetY(50);
screen.setGapX(0);

// Установить панели до вкладок, чтобы выбранная вкладка сразу активировала контент
Panel[] panels = new Panel[] { panel1, panel2 };
screen.setPanels(panels);
Tabs tabs = new Tabs(context);
screen.setTabs(tabs);

// Подъём экрана
screen.setOnScreenLiftListener(state -> {
    // SCREEN_LOWERED (1) или SCREEN_RAISED (2)
});
```

### Kotlin

```kotlin
val screen = Screen(context).apply {
    setTheme(Theme.FREE_LIGHT)
    setPanels(arrayOf(panel1, panel2))
    setTabs(tabs)
}
```

### Compose

```kotlin
Screen(
    theme = Theme.FREE_LIGHT,
    language = Language.EN,
    tabs = listOf(
        TabItem("store", mapOf("en" to "Store", "ru" to "Магазин")),
        TabItem("settings", mapOf("en" to "Settings", "ru" to "Настройки"))
    ),
    panels = arrayOf(storePanel, settingsPanel),
    selectedTab = "store",
    onTabSelected = { /* обработать смену вкладки */ },
    offsetX = 175,
    offsetY = 50,
    gapX = 0,
    screenLiftState = 2,
    onScreenLift = { state -> /* обработать */ }
)
```

## API

### Java

```java
// Тема и язык
void setTheme(Theme theme)                   // выбрасывает исключение при null
Theme getCurrentTheme()
void setLanguage(Language language)          // выбрасывает исключение при null
Language getCurrentLanguage()

// Раскладка
void setOffsetX(int offsetX)         // X-смещение контента (px)
void setOffsetY(int offsetY)         // Y-смещение контента (px)
void setGapX(int gapX)               // зазор между Tabs и Panel (px)
int getOffsetX()
int getOffsetY()
int getGapX()
int getAvailableHeight()             // фиксированная высота контента (ScreenTheme.AVAILABLE_HEIGHT = 720)

// Компоненты
void setTabs(Tabs tabs)
Tabs getTabs()
ScreenView getTabsScrollView()       // ScrollView, оборачивающий Tabs
void setPanels(Panel[] panels)       // null очищает; элементы не-null и уникальны по identity
Panel[] getPanels()                  // защитная копия
void setActivePanel(int index)       // некорректный индекс очищает логический и видимый выбор
Panel getActivePanel()
ScreenView getPanelWrapper(int index) // обёртка панели (null до первой активации)

// Анимации
void setAnimationsEnabled(boolean enabled) // отключает анимации панели/вкладок (напр. в пиксельных тестах)
boolean isAnimationsEnabled()

// Подъём экрана
void onScreenLift(int state)         // SCREEN_LOWERED=1, SCREEN_RAISED=2
int getScreenLiftState()
void setOnScreenLiftListener(OnScreenLiftListener listener)

// Toast
void showToast(Toast toast)               // хостить построенный Toast; Screen владеет attach/show/removal
void showToast(String text, long duration) // удобство; делегирует через showToast(Toast)
void dismissToast()
Toast getCurrentToast()

// Распространение
void propagateTheme(Theme theme)
void propagateLanguage(Language language)
```

### Обёртка для Compose

```kotlin
@Composable
fun Screen(
    tabs: List<TabItem>,
    panels: Array<Panel>,
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    theme: Theme,
    language: Language,
    offsetX: Int = 175,
    offsetY: Int = 50,
    gapX: Int = 0,
    screenLiftState: Int = 2,
    onScreenLift: ((Int) -> Unit)? = null,
    onScreenReady: ((ru.voboost.components.screen.Screen) -> Unit)? = null
)
```

`tabs`, `panels`, `selectedTab`, `onTabSelected`, `theme` и `language` обязательны и не имеют значений по умолчанию. `onScreenReady` предоставляет доступ к нижележащему `Screen` для императивного взаимодействия (напр. вызов `showToast`). Compose factory прикрепляет один стабильный пустой экземпляр Tabs. Первый update применяет состояние Screen, устанавливает панели, настраивает items/listener Tabs и только затем активирует выбор; каждое начальное свойство применяется один раз. `onScreenReady` вызывается после этого полностью настроенного первого update.

Обёртка устанавливает панели до активации вкладок. Рекомпозиция применяет изменения контента без повторной установки равных массивов или слушателей: актуальные колбэки вызываются ровно один раз, а nullable-колбэк `onScreenLift` удаляется из native view при переходе в `null`. При освобождении композиции native-слушатели, вкладки и панели отсоединяются.

> **Значение `offsetX` по умолчанию зависит от API:** обёртка Compose по умолчанию использует `175`, тогда как экземпляр Java `Screen` по умолчанию использует `ScreenTheme.DEFAULT_OFFSET_X = 145`. Передавайте `offsetX` явно, если значение важно.

## Builder API

Текучий (fluent) builder для построения полностью настроенного `Screen` одним вызовом.

Точки входа:

```java
static Screen.Builder create(Context context, Theme theme)
static Screen.Builder create(Context context, Theme theme, Language language) // предпочтительно для Kotlin DSL
```

`create(context, theme)` остаётся совместимым. Предпочтительная перегрузка `create(context, theme, language)` применяет тему и язык при `build()` и является точкой входа для двухфазного Kotlin DSL. Обязательные аргументы немедленно отвергают null.

Пример:

```java
Screen screen = Screen.create(context, Theme.FREE_LIGHT, Language.EN)
        .offsets(175, 50, 0)
        .animationsEnabled(false)
        .tabs(tabs)
        .panels(new Panel[] { panel1, panel2 })
        .backgroundColorHex("#ffffff")
        .onScreenLift(listener)
        .build();
```

`offsets(x, y, gapX)` задаёт смещения содержимого и зазор между вкладками и панелью вместе; все три должны быть неотрицательными, и отрицательное значение выбрасывает исключение до того, как будет применено любое из трёх. `build()` применяет смещения и переключатель анимации перед панелями и вкладками, устанавливает панели перед вкладками, чтобы `setActivePanel()` их нашёл, и возвращает полностью настроенный, неприкреплённый `Screen`.

Методы builder (все возвращают `Builder`):

| Метод | Описание |
|--------|-------------|
| `backgroundColor(int)` | Сплошной цвет фона |
| `backgroundColorHex(String)` | Цвет фона из hex-строки (напр. `"#ffffff"`) |
| `offsets(int, int, int)` | Смещения содержимого `offsetX`, `offsetY` и `gapX` (все неотрицательные) |
| `animationsEnabled(boolean)` | Включает/выключает анимацию перехода панелей |
| `tabs(Tabs)` | Компонент боковых вкладок |
| `panels(Panel[])` | Массив панелей |
| `onScreenLift(OnScreenLiftListener)` | Слушатель подъёма экрана |
| `build()` | Возвращает настроенный `Screen` (применяет смещения/анимацию перед панелями/вкладками) |

## Kotlin Screen DSL (Слой 2)

Kotlin-потребители View получают двухфазный агрегатный DSL `screen { }` и фабрики `panel`/`dialog`/`toast` в общем пакете `ru.voboost.components.dsl`. DSL выполняет лямбду приёмника в приватную спецификацию, снимает снимок и проверяет весь агрегат, и только затем вызывает Java-билдеры и прикрепляет View. Потребительский код не содержит `.create()`/`.build()`; вызовы времени выполнения вроде `screen.setTheme()` остаются допустимыми.

```kotlin
import ru.voboost.components.dsl.screen
import ru.voboost.components.dsl.panel

val screen = screen(context, theme, language) {
    offsets(175, 50, 0)
    animationsEnabled(false)
    panels.forEach { panel(it) }
    tabs(items, selectedValue = "settings", onValueChange = { value -> /* ... */ }) {
        topPadding(50)
        animationsEnabled(false)
    }
    onScreenLift { state -> /* SCREEN_LOWERED / SCREEN_RAISED */ }
}
```

Проверка выполняется до прикрепления любого View или установки любого слушателя и требует: ровно одного объявления `tabs {}` (второе — ошибка, а не замена); непустых, уникальных значений вкладок; `selectedValue`, существующего среди них; числа панелей, равного числу вкладок; и панелей, уникальных по идентичности и неприкреплённых. При ошибке идентифицируется проблемное значение или индекс, и сборка прерывается до любой мутации. `ScreenDsl` не предоставляет изменяемое свойство native `screen`; выбор Слоя 2 — начальное, неконтролируемое native-состояние.

## Структура раскладки

Screen размещает дочерние элементы:
1. **Tabs** — на `offsetX`, во всю высоту, внутри `ScreenView` ScrollView
2. **Panel** — после Tabs + `gapX`, на `offsetY` от верха, внутри обёртки `ScreenView`

Тема и язык маршрутизируются через логические поля Screen: `Tabs`, `Panel[]` и текущий `Toast`, а не через view-обёртки. Каждый явный setter Screen один раз вызывает setter каждого текущего логического ребёнка. Новые Tabs и только новые identity Panel один раз получают текущее ненулевое состояние; перестановка retained-панелей не даёт лишних вызовов, а удалённая и затем добавленная снова панель считается новой.

Повторный `setPanels` с теми же экземплярами идемпотентен. При перестановке активная панель и её обёртка сохраняются по identity; удаление активной панели очищает выбор. Корректный `setActivePanel` прикрепляет ровно одну видимую обёртку, а некорректный индекс очищает и `getActivePanel()`, и все прикреплённые обёртки панелей. Переданный массив и массивы из `getPanels()` не могут изменить принадлежащий Screen порядок. Массив `null` очищает панели. Элемент `null` или один экземпляр `Panel` в нескольких позициях приводит к `IllegalArgumentException` до изменения текущей иерархии или выбора. `setPanels` также проверяет родителя каждой панели до любых изменений: панель с чужим родителем отклоняется атомарно, а панель можно повторно использовать, если её родитель — уже принадлежащая этому Screen обёртка.

`setTabs` выполняет такую же предварительную проверку до удаления текущих Tabs. Tabs с чужим родителем отклоняются с `IllegalArgumentException`, а текущие Tabs и их обёртка остаются на месте; повторная установка текущих Tabs этого Screen разрешена.

Screen наблюдает индексы вкладок через `Tabs.addOnTabChangeListener` и не заменяет пользовательский слушатель из `Tabs.setOnTabChangeListener`. Screen читает текущий выбор Tabs, а не доверяет потенциально устаревшему индексу reentrant-события. При отсоединении внутренний наблюдатель удаляется; при повторном присоединении он восстанавливается и выбор синхронизируется.

Завершение перехода принадлежит конкретной сессии: отмена, новый выбор или отсоединение Screen не позволяют старому animator удалить или сдвинуть последнюю активную обёртку.

## Структура файлов

```
screen/
├── Screen.java          # Основная реализация (ViewGroup)
├── Screen.compose.kt            # Обёртка для Compose
├── ScreenTheme.java     # Цвета тем, размеры, константы анимаций
├── ScreenView.java      # ScrollView с резиновой прокруткой, обёртка Tabs и Panels (внутренний helper; не часть публичного API Screen)
├── Screen.test/         # Тесты
└── Screen.screenshots/  # Эталонные изображения
```

## Ограничения

- Screen должен быть измерён до того, как анимации начнут корректно работать
- Переходы между панелями требуют корректных размеров Screen (ширина > 0, высота > 0)
- Быстрое переключение панелей (быстрее 300 мс) может отменять предыдущие анимации
- Сообщения Toast не сохраняются при изменении конфигурации
- При отсоединении Screen окончательно освобождает и удаляет текущий Toast и очищает `getCurrentToast()`; после повторного присоединения старого Toast-потомка нет
- Максимальное количество панелей ограничено доступной памятью

## Производительность

- Анимации перехода между панелями выполняются в UI-потоке в течение 300 мс
- Каждая панель измеряется и размещается при каждом переходе
- Распространение темы и языка использует snapshot логических детей; глубиной владеют их setter-ы
- При большом количестве дочерних компонентов рассмотрите ленивую загрузку
- Toast-представления добавляются в иерархию Screen и удаляются при закрытии или отсоединении Screen

## Устранение неполадок

### Переходы между панелями не анимируются
- Убедитесь, что Screen был измерён перед вызовом setActivePanel
- Проверьте, что размеры панели корректны (ширина > 0, высота > 0)
- Убедитесь, что Screen имеет корректные значения offsetX и offsetY

### Панели не видны
- Убедитесь, что setActivePanel вызван с корректным индексом; некорректный индекс намеренно очищает контент
- Проверьте, что массив панелей установлен через setPanels()
- Убедитесь, что тема установлена перед установкой панелей

### Toast не отображается
- Убедитесь, что тема установлена перед показом toast
- Проверьте, что длительность toast корректна (DURATION_SHORT или DURATION_LONG)
- Убедитесь, что Screen прикреплён к окну

### Утечки памяти
- Убедитесь, что слушатели вкладок корректно удаляются при замене вкладок
- Проверьте, что ссылки на панели очищаются, когда они больше не нужны
- Убедитесь, что toast-представления удаляются после закрытия

## Потокобезопасность

Компонент Screen **не является потокобезопасным**. Все методы должны вызываться в UI-потоке (основном потоке).

### Методы главного потока

Все изменяемые API и обычные getters должны вызываться в UI-потоке, включая:

- `setTheme(Theme)` - изменяет иерархию представлений
- `setLanguage(Language)` - изменяет иерархию представлений
- `setOffsetX(int)`, `setOffsetY(int)`, `setGapX(int)` - запускают раскладку
- `setTabs(Tabs)` - изменяет иерархию представлений
- `setPanels(Panel[])` - изменяет внутреннее состояние
- `setActivePanel(int)` - запускает анимации и изменения раскладки
- `onScreenLift(int)` - изменяет состояние и запускает раскладку
- `setOnScreenLiftListener(OnScreenLiftListener)` - изменяет ссылку на слушатель
- `showToast(String, long)` и `showToast(Toast)` - изменяют иерархию представлений
- `dismissToast()` - изменяет иерархию представлений
- `getCurrentTheme()`, `getCurrentLanguage()`, getters раскладки/состояния, `getPanels()`, `getTabs()` и `getCurrentToast()` - читают изменяемое UI-состояние

### Потокобезопасность анимаций
Анимации перехода между панелями выполняются в UI-потоке с использованием ObjectAnimator. Быстрые вызовы `setActivePanel()` безопасны — текущая анимация будет отменена перед запуском новой.

## Использование с Compose

Screen предоставляет современный API, удобный для Compose:

```kotlin
@Composable
fun MyScreen() {
    var selectedTab by remember { mutableStateOf("settings") }

    Screen(
        tabs = listOf(
            TabItem("settings", mapOf("en" to "Settings")),
            TabItem("profile", mapOf("en" to "Profile"))
        ),
        panels = arrayOf(createSettingsPanel(), createProfilePanel()),
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        theme = Theme.FREE_LIGHT,
        language = Language.EN
    )
}
```

Обёртка Compose автоматически:
- Создаёт компонент Tabs
- Устанавливает панели до активации выбора Tabs и синхронизирует native-контент с `selectedTab`
- Применяет изменённые списки панелей и удаляет устаревшие обёртки
- Доставляет актуальные колбэки и очищает nullable-слушатели
- Очищает слушатели и дочерние view при освобождении

### Keyed Compose content API (Слой 3 — предпочтительный)

Предпочтительная перегрузка `Screen(...)` объявляет всё дерево через некомпозируемую лямбду `ScreenContentScope` вместо заранее построенного `Panel[]`. Реализация на каждую рекомпозицию строит свежее неизменяемое дерево-описание, валидирует его до любых native-мутаций и синхронизирует с native Screen так, что узлы со стабильными key+type переиспользуют свои native-экземпляры при переупорядочивании и рекомпозиции:

```kotlin
@Composable
fun MyScreen() {
    var selected by remember { mutableStateOf("settings") }

    Screen(
        selectedPanelKey = selected,
        onSelectedPanelChange = { selected = it },
        theme = Theme.FREE_LIGHT,
        language = Language.EN,
    ) {
        panel(TabItem("settings", mapOf("en" to "Settings"))) {
            section("sound", mapOf("en" to "Sound")) {
                checkbox("mute", checked = false, onCheckedChange = {})
                radio(
                    key = "mode",
                    buttons = listOf(RadioButton("stereo", mapOf("en" to "Stereo"))),
                    value = "stereo",
                    onValueChange = {},
                )
            }
        }
        panel(TabItem("profile", mapOf("en" to "Profile"))) {
            section("account", mapOf("en" to "Account")) {
                button("signOut", "Sign out", onClick = {})
                nativeView(
                    key = "avatar",
                    factory = { ctx -> android.widget.ImageView(ctx) },
                    update = { /* применить актуальное состояние */ },
                    onRelease = { /* ровно один раз при удалении */ },
                )
            }
        }
    }
}
```

Идентичность и жизненный цикл:
- Идентичность панели — это `TabItem.value` (стабильный и непустой); узлы section, child и row-button ключуются стабильным `Any`
- Стабильный key + стабильный тип узла переиспользует тот же native-экземпляр при переупорядочивании и рекомпозиции; стабильный key + изменённый тип освобождает старый экземпляр и создаёт новый после валидации
- Один стабильный native-адаптер слушателя на узел читает актуальный колбэк, поэтому замена лямбды при рекомпозиции диспетчеризуется через тот же сохранённый адаптер
- Контролируемые значения (`selectedPanelKey`, значения radio/select, checked у checkbox, nullable-свойства) авторитетны при синхронизации: native-касание может обновить состояние оптимистично, но если состояние вызывающей стороны не изменилось, следующая рекомпозиция восстанавливает переданное значение
- `nativeView` — это лазейка только для application-owned Android View, а не для построения компонентов Voboost и не общий Compose-слот. Его `factory` выполняется один раз, `update` — на каждой синхронизации, `onRelease` — ровно один раз
- `horizontalRow` намеренно узок (реальный demo-ряд из двух взвешенных кнопок) и не является общей Compose-системой раскладки
- `onViewCreated` срабатывает ровно один раз для каждого заново созданного native Screen, после того как первый update установил начальные свойства/слушатели/дерево; при рекомпозиции не повторяется
- Валидация бросает `IllegalArgumentException` с путём к родителю и проблемным ключом до создания или мутации любого native View (неизвестный `selectedPanelKey`, дублирующие/пустые ключи)

Перегрузка `Screen(...)` на основе массива выше остаётся доступной в течение периода совместимости.
