# Компонент Section

[Screen](component-screen.ru.md) | [Tabs](component-tabs.ru.md) | [Panel](component-panel.ru.md) | **Section** | [Radio](component-radio.ru.md) | [Checkbox](component-checkbox.ru.md) | [Button](component-button.ru.md) | [Buttons](component-buttons.ru.md) | [Select](component-select.ru.md) | [Dialog](component-dialog.ru.md) | [Popup](component-popup.ru.md) | [Toast](component-toast.ru.md) | [Text](component-text.ru.md) | [Hint](component-hint.ru.md) | [Font](component-font.ru.md) | [Theme](component-theme.ru.md) | [Language](component-language.ru.md).

[English](component-section.md) | **Русский**

## Архитектура

- **[Section.java](../../voboost-components/src/main/java/ru/voboost/components/section/Section.java)** — Java ViewGroup: контейнер с заголовком и градиентной шапкой
- **[Section.compose.kt](../../voboost-components/src/main/java/ru/voboost/components/section/Section.compose.kt)** — Kotlin-обёртка для Compose
- **[Section.kt](../../voboost-components/src/main/java/ru/voboost/components/section/Section.kt)** — Kotlin DSL-расширения (`addRadio`, `addButton`, ...)

Контейнер с заголовком и скруглёнными углами для группировки связанных элементов (например, Radio внутри Section внутри Panel).

## Использование

### Java

```java
Section section = new Section(context);
section.setTheme(Theme.FREE_DARK);
section.setLanguage(Language.EN);
section.setTitle(Map.of("en", "Language Selection", "ru", "Выбор языка"));

// Добавление дочерних View
section.addView(radioComponent);
```

### Kotlin (рекомендуется — DSL)

Расширения с receiver-lambda, определённые в `Section.kt` (для дочерних компонентов) и `Panel.kt` (для секций), оборачивают Java Builder, поэтому Kotlin-вызывающему коду не нужно писать `.create()`/`.build()`. Внутри receiver-секции дочерние компоненты добавляются их собственными DSL-перегрузками (`addButton(...) { }`, `addRadio(...) { }`, ...).

```kotlin
panel.addSection(Theme.FREE_DARK, Language.EN, mapOf("en" to "Settings", "ru" to "Настройки")) {
    titleCheckbox(true)
    addRadio(Theme.FREE_DARK, Language.EN, buttons, "en") {
        title(mapOf("en" to "Language Selection", "ru" to "Выбор языка"))
    }
}
```

Для самостоятельного создания используйте фабрику `section()` из `SectionFactory.kt`:

```kotlin
val section = section(context, Theme.FREE_DARK, Language.EN, mapOf("en" to "Settings")) {
    titleCheckbox(true)
}
```

### Kotlin (View API)

```kotlin
val section = Section(context).apply {
    setTheme(Theme.FREE_DARK)
    setLanguage(Language.EN)
    setTitle(mapOf("en" to "Language Selection", "ru" to "Выбор языка"))
    addView(radioComponent)
}
```

### Compose

```kotlin
Section(
    title = mapOf("en" to "Settings", "ru" to "Настройки"),
    language = Language.EN,
    theme = Theme.FREE_LIGHT,
    content = { sectionView ->
        sectionView.addView(radioComponent)
    }
)
```

## Использование Section в Compose

Компонент Section можно использовать в Jetpack Compose через composable-функцию `Section` или через функции-расширения.

### Вариант 1: использование composable-обёртки Section

```kotlin
import ru.voboost.components.section.Section
import ru.voboost.components.i18n.Language
import ru.voboost.components.theme.Theme

@Composable
fun MyScreen() {
    val theme = Theme.FREE_LIGHT
    val language = Language.EN

    Section(
        title = mapOf("en" to "Settings", "ru" to "Настройки"),
        language = language,
        theme = theme
    ) { section ->
        // Добавление дочерних элементов через функции-расширения
        section.addRadio(
            buttons = listOf(
                RadioButton("en", mapOf("en" to "English")),
                RadioButton("ru", mapOf("en" to "Russian"))
            ),
            selectedValue = "en",
            theme = theme,
            language = language,
            onValueChange = { newValue -> /* обработка изменения */ }
        )
    }
}
```

### Вариант 2: через функцию-расширение Panel.addSection() (рекомендуется)

```kotlin
import ru.voboost.components.panel.Panel
import ru.voboost.components.panel.addSection
import ru.voboost.components.section.addRadio

@Composable
fun MyPanel() {
    val theme = Theme.FREE_LIGHT
    val language = Language.EN
    val context = LocalContext.current

    val panel = Panel(context).apply {
        setTheme(theme)
        setLanguage(language)
    }

    panel.addSection(
        title = mapOf("en" to "Settings"),
        theme = theme,
        language = language
    ) { section ->
        section.addRadio(
            buttons = listOf(
                RadioButton("en", mapOf("en" to "English")),
                RadioButton("ru", mapOf("en" to "Russian"))
            ),
            selectedValue = "en",
            theme = theme,
            language = language
        )
    }
}
```

### Вариант 3: прямое создание Java View

Для миграции или особых случаев можно создать Java View напрямую:

```kotlin
val section = ru.voboost.components.section.Section(context).apply {
    setTheme(Theme.FREE_LIGHT)
    setLanguage(Language.EN)
    setTitle(mapOf("en" to "Settings"))
}

// Добавление дочерних элементов через Java API
val radio = ru.voboost.components.radio.Radio(context).apply {
    setTheme(Theme.FREE_LIGHT)
    setLanguage(Language.EN)
    setButtons(listOf(...))
}
section.addView(radio)
```

### Интеграция с Panel

При использовании Section совместно с компонентами Panel прокрутку обеспечивает окружающий Screen (через ScreenView), а не сам Panel. Panel — это LinearLayout, который размещает секции друг под другом; он не оборачивает их в ScrollView:

```kotlin
// Panel — не прокручиваемый LinearLayout; прокрутку обеспечивает Screen через ScreenView
val panel = Panel(context).apply {
    setTheme(theme)
    setLanguage(language)
}

// Секции, добавленные в panel, прокручиваются окружающим ScreenView
panel.addSection(title = mapOf("en" to "Section 1", "ru" to "Раздел 1"), theme = theme, language = language) { ... }
panel.addSection(title = mapOf("en" to "Section 2", "ru" to "Раздел 2"), theme = theme, language = language) { ... }
panel.addSection(title = mapOf("en" to "Section 3", "ru" to "Раздел 3"), theme = theme, language = language) { ... }
```

### Справочник функций-расширений

Section предоставляет функции-расширения для типичных дочерних компонентов. У каждой есть две Kotlin-формы: перегрузка с параметрами (каждая опция — именованный аргумент) и **DSL-перегрузка** (обязательные параметры сначала, затем receiver-lambda над Builder компонента для остальных — рекомендуется для Kotlin):

- `addRadio(...)` — добавить компонент Radio
- `addButton(...)` — добавить компонент Button
- `addCheckbox(...)` — добавить компонент Checkbox
- `addSelect(...)` — добавить компонент Select
- `addButtons(...)` — добавить компонент Buttons

Пример:

```kotlin
Section(
    title = mapOf("en" to "My Section"),
    language = Language.EN,
    theme = Theme.FREE_LIGHT
) { section ->
    // Добавить Radio
    section.addRadio(
        buttons = listOf(...),
        selectedValue = "option1",
        theme = Theme.FREE_LIGHT,
        language = Language.EN
    )

    // Добавить Button
    section.addButton(
        text = "Click Me",
        style = ButtonStyle.PRIMARY,
        theme = Theme.FREE_LIGHT,
        language = Language.EN
    ) { /* обработка нажатия */ }

    // Добавить Checkbox
    section.addCheckbox(
        checked = true,
        theme = Theme.FREE_LIGHT,
        language = Language.EN,
        label = mapOf("en" to "Enable")
    )
}
```

### Распространение темы и языка

Section автоматически распространяет тему и язык на дочерние компоненты:

Каждый setter один раз применяется к snapshot непосредственных interface-детей. Их setter сам отвечает за более глубокое распространение, а обычный `ViewGroup` останавливает обход. Поздно добавленный контент и checkbox заголовка получают каждое текущее ненулевое состояние ровно один раз и без вызова value-callback.

```kotlin
Section(
    title = mapOf("en" to "Settings"),
    language = Language.EN,
    theme = Theme.FREE_LIGHT
) { section ->
    // Radio автоматически получит тему и язык от Section,
    // но их можно задать и явно:
    section.addRadio(
        buttons = listOf(...),
        selectedValue = "en",
        theme = Theme.FREE_LIGHT,
        language = Language.EN
    )
}
```

### Чекбокс в заголовке (сворачиваемая секция)

Если включено, в полосе заголовка показывается `Checkbox`, меткой которого служит заголовок секции. Нажатие пользователя за 200 мс анимирует переход между точной развёрнутой высотой и высотой одной полосы заголовка. Более новое переключение отменяет незавершённый переход. Дочерние элементы, которые уже были `GONE`, остаются `GONE` и не входят в развёрнутую высоту; остальные значения видимости восстанавливаются после разворачивания.

`clearTitleCheckbox()`, унаследованный `removeView(titleCheckbox)` и `removeAllViews()` одинаково очищают состояние и слушатели чекбокса заголовка. Отсоединение Section отменяет активный аниматор высоты, поэтому устаревшее завершение не может позднее изменить отсоединённый View.

Повторная программная установка текущего checked-состояния не перезапускает совпадающий активный переход. Программная установка противоположного состояния отменяет переход и сразу применяет новую авторитетную геометрию. Compose всегда вызывает последний non-null `onTitleCheckedChange`, а рекомпозиция в `null` и release очищают native callback.

```java
section.setTitle(Map.of("en", "Schedule charging"));
section.setTitleCheckbox(false, isChecked -> {
    // свёрнуто, когда isChecked == false
});
```

```kotlin
Section(
    title = mapOf("en" to "Schedule charging"),
    language = Language.EN,
    theme = Theme.FREE_DARK,
    titleChecked = false,
    onTitleCheckedChange = { /* обработка переключения */ }
) { /* дочерние элементы — скрыты, пока titleChecked == false */ }
```

### Информационная иконка и всплывающее окно

Задайте `infoText` (один блок) или `infoBlocks` (несколько блоков с заголовками), чтобы после заголовка показывать круглую иконку `(i)`. Если в Compose переданы оба параметра, приоритет у `infoText`. Нажатие на иконку открывает всплывающее окно.

```java
// Один текстовый блок (без заголовка)
section.setPopupText(Map.of("en", "Generally, the range extender is not started..."));

// Несколько блоков с заголовками
section.setPopupText(List.of(
    new PopupBlock(
        Map.of("en", "Pure EV"),
        Map.of("en", "Description of pure EV mode...")
    ),
    new PopupBlock(
        Map.of("en", "HEV"),
        Map.of("en", "Description of HEV mode...")
    )
));
```

```kotlin
// Один блок
Section(
    title = mapOf("en" to "Power mode"),
    language = Language.EN,
    theme = Theme.FREE_DARK,
    infoText = mapOf("en" to "Generally, the range extender..."),
)

// Несколько блоков
Section(
    title = mapOf("en" to "Power mode"),
    language = Language.EN,
    theme = Theme.FREE_DARK,
    infoBlocks = listOf(
        SectionPopupBlock(
            title = mapOf("en" to "Pure EV"),
            text = mapOf("en" to "Description...")
        ),
        SectionPopupBlock(
            title = mapOf("en" to "HEV"),
            text = mapOf("en" to "Description...")
        )
    ),
)
```

## API

### Java

```java
// Заголовок
void setTitle(Map<String, String> title)
Map<String, String> getTitle()
String getTitleText()                   // для текущего языка

// Чекбокс в заголовке (сворачиваемый режим)
void setTitleCheckbox(boolean checked)
void setTitleCheckbox(boolean checked, OnTitleCheckedChangeListener listener)
void clearTitleCheckbox()
boolean hasTitleCheckbox()
boolean isTitleChecked()
boolean isCollapsed()
void setOnTitleCheckedChangeListener(OnTitleCheckedChangeListener listener)

// Информационное всплывающее окно (блоки содержимого)
static class PopupBlock {
    PopupBlock(@Nullable Map<String, String> title, Map<String, String> text)
    @Nullable Map<String, String> getTitle()
    Map<String, String> getText()
}
void setPopupText(@Nullable List<PopupBlock> blocks)
void setPopupText(@Nullable Map<String, String> singleText)  // вспомогательный метод
@Nullable List<PopupBlock> getPopupBlocks()
boolean hasPopupText()
void clearPopupText()
void showPopup()

// Тема и язык
void setTheme(Theme theme)
Theme getCurrentTheme()
void setLanguage(Language language)
Language getCurrentLanguage()

// Распространение (на дочерние компоненты)
void propagateTheme(Theme theme)
void propagateLanguage(Language language)

// Обобщённые addView/removeView/removeAllViews ViewGroup и типизированные методы используют одну упорядоченную модель дочерних элементов
Checkbox addCheckbox(Checkbox checkbox)
Radio addRadio(Radio radio)
Button addButton(Button button)
Select addSelect(Select select)
Buttons addButtons(Buttons buttons)
List<View> getChildren()

// Отступы на уровне секции (px; переопределяют CONTENT_PADDING_TOP/BOTTOM)
void setPaddingTop(Integer top)
Integer getSectionPaddingTop()
void setPaddingBottom(Integer bottom)
Integer getSectionPaddingBottom()
void setPaddingLeft(Integer left)     // отступ контента в px; null возвращает значение темы
void setPaddingRight(Integer right)   // отступ контента в px; null возвращает значение темы

// Раскладка
void setIncludeBottomMargin(boolean include)   // false = не учитывать BOTTOM_MARGIN (используется Panel)
boolean isIncludeBottomMargin()
void setCompactWidth(boolean compactWidth)      // true = COMPACT_SECTION_WIDTH (705px)
boolean isCompactWidth()

// Оверлей всплывающего окна (для визуальных тестов: композитит popup поверх экрана)
@Nullable View getPopupOverlayView()
```

### Обёртка для Compose

```kotlin
data class SectionPopupBlock(
    val title: Map<String, String>? = null,
    val text: Map<String, String>
)

@Composable
fun Section(
    title: Map<String, String>,
    language: Language,
    theme: Theme,
    titleChecked: Boolean? = null,
    onTitleCheckedChange: ((Boolean) -> Unit)? = null,
    infoText: Map<String, String>? = null,              // один блок без заголовка
    infoBlocks: List<SectionPopupBlock>? = null,        // несколько блоков
    content: ((Section) -> Unit)? = null
)
```

## Builder API

Текучий (fluent) builder для построения полностью настроенной `Section` одним вызовом. Это точка входа для Java; в Kotlin предпочтительнее DSL-расширение `Panel.addSection(theme, language, title) { }`, определённое в `Panel.kt`, которое оборачивает этот Builder.

Точка входа:

```java
static Section.Builder create(Context context, Theme theme, Language language, Map<String, String> title)
```

Пример:

```java
Section section = Section.create(context, Theme.FREE_DARK, Language.EN, titleMap)
        .paddingTop(40)
        .paddingBottom(2)
        .titleCheckbox(true, checked -> { /* обработка переключения */ })
        .infoText(infoMap)
        .build();
```

Методы builder (все возвращают `Builder`):

| Метод | Описание |
|--------|-------------|
| `paddingTop(Integer)` / `paddingBottom(Integer)` | Верхний/нижний отступ на уровне секции в px |
| `paddingLeft(Integer)` / `paddingRight(Integer)` | Левый/правый отступ контента в px; null возвращает значение темы |
| `titleCheckbox(boolean)` | Чекбокс-в-заголовке с начальным состоянием |
| `titleCheckbox(boolean, OnTitleCheckedChangeListener)` | Чекбокс-в-заголовке с состоянием и слушателем |
| `infoText(Map<String,String>)` | Один блок информации (без заголовка) |
| `infoText(List<PopupBlock>)` | Несколько блоков; применяется последний вызов builder для информационного содержимого |
| `infoBlock(@Nullable Map<String,String> title, Map<String,String> text)` | Добавить один блок во всплывающее окно |
| `build()` | Возвращает настроенную `Section` |

Применяется последний вызов источника информации. Последовательные вызовы `infoBlock` добавляют блоки в текущий список; `infoBlock` после `infoText(Map)` заменяет источник с одиночным текстом.

Section сохраняет защитные снимки title, map каждого popup-блока и списка блоков с исходным порядком итерации. Заголовок и каждая строка popup разрешаются по точному языку, затем по первому non-null сохранённому значению, затем в `""`; внутренние ID не отображаются. Обязательный API заголовка Section по-прежнему отклоняет null/пустую map, а новая Section без заголовка возвращает `""`; заголовок, заданный до языка, разрешается в первое non-null значение. Null-элементы списка блоков и null-текст PopupBlock отклоняются с `IllegalArgumentException` до изменения текущего содержимого. Изменение темы, языка, заголовка или блоков видимого popup немедленно перестраивает содержимое и не оставляет старый локализованный текст.

## Детали реализации

Рендеринг на canvas: полоса заголовка с градиентом, скруглёнными только сверху углами и заливкой фона. `BORDER_WIDTH = 0` (SectionTheme.java:84), поэтому рамка не рисуется. Без сохранения состояния (состояние управляется внешне). Распространяет тему и язык на дочерние элементы.

## Структура файлов

```
section/
├── Section.java                          # Основная реализация (ViewGroup, canvas, builder)
├── Section.compose.kt                    # Обёртка для Compose
├── Section.kt                            # add*-функции-расширения (DSL)
├── SectionTheme.java                     # Размеры и цвета темы
├── SectionPopup.java                     # Информационное всплывающее окно (шапка + прокручиваемые блоки)
├── SectionInfoIcon.png                   # Битовая иконка (i)
├── Section_theme_free-light.png          # Иконка закрытия (FREE_LIGHT)
├── Section_theme_free-dark.png           # Иконка закрытия (FREE_DARK)
├── Section_theme_dreamer-light.png       # Иконка закрытия (DREAMER_LIGHT)
├── Section_theme_dreamer-dark.png        # Иконка закрытия (DREAMER_DARK)
├── Section.test/                         # Тесты
└── Section.screenshots/                  # Эталонные изображения
```
