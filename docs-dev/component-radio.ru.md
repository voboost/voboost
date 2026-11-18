# Компонент Radio

[Screen](component-screen.ru.md) | [Tabs](component-tabs.ru.md) | [Panel](component-panel.ru.md) | [Section](component-section.ru.md) | **Radio** | [Checkbox](component-checkbox.ru.md) | [Button](component-button.ru.md) | [Buttons](component-buttons.ru.md) | [Select](component-select.ru.md) | [Dialog](component-dialog.ru.md) | [Popup](component-popup.ru.md) | [Toast](component-toast.ru.md) | [Text](component-text.ru.md) | [Hint](component-hint.ru.md) | [Font](component-font.ru.md) | [Theme](component-theme.ru.md) | [Language](component-language.ru.md).

[English](component-radio.md) | **Русский**

## Архитектура

- **[Radio.java](../../voboost-components/src/main/java/ru/voboost/components/radio/Radio.java)** — Java-контейнер (LinearLayout): layout, состояние; делегирует рендеринг на canvas, анимации и обработку касаний package-private [RadioPrimitive.java](../../voboost-components/src/main/java/ru/voboost/components/radio/RadioPrimitive.java)
- **[Radio.compose.kt](../../voboost-components/src/main/java/ru/voboost/components/radio/Radio.compose.kt)** — Kotlin-обёртка для Compose: лёгкая интеграция через AndroidView

Вся логика — в Java. Kotlin-обёртка только мостит к Compose.

## Использование

### Java

```java
Radio radio = new Radio(context);

radio.setButtons(Arrays.asList(
    new RadioButton("en", Map.of("en", "English", "ru", "Английский")),
    new RadioButton("ru", Map.of("en", "Russian", "ru", "Русский"))
));
radio.setLanguage(Language.EN);
radio.setTheme(Theme.FREE_LIGHT);
radio.setSelectedValue("en");
radio.setOnValueChangeListener(newValue -> {
    // Обработка изменения выбора
});

parentLayout.addView(radio);
```

### Kotlin (рекомендуется — DSL)

Расширения с receiver-lambda, определённые в `Section.kt`, оборачивают Java Builder, поэтому Kotlin-вызывающему коду не нужно писать `.create()`/`.build()`. Лямбда выполняется на `Radio.Builder`, поэтому необязательные свойства задаются как вызовы методов Builder (`title(map)`, `margin(l, t, r, b)`, `onValueChange { }`).

```kotlin
section.addRadio(Theme.FREE_LIGHT, Language.EN, buttons, "en") {
    title(titleMap)
    margin(0, 10, 0, 10)
    onValueChange { /* Обработка изменения выбора */ }
}
```

Для самостоятельного создания используйте фабрику `radio()` из `RadioFactory.kt`:

```kotlin
val radio = radio(context, Theme.FREE_LIGHT, Language.EN, buttons, "en") {
    title(titleMap)
    margin(0, 10, 0, 10)
    onValueChange { /* Обработка изменения выбора */ }
}
```

### Kotlin (View API)

```kotlin
val radio = Radio(context).apply {
    setButtons(listOf(
        RadioButton("en", mapOf("en" to "English", "ru" to "Английский")),
        RadioButton("ru", mapOf("en" to "Russian", "ru" to "Русский"))
    ))
    setLanguage(Language.EN)
    setTheme(Theme.FREE_LIGHT)
    setSelectedValue("en")
    setOnValueChangeListener { newValue ->
        // Обработка изменения выбора
    }
}

parentLayout.addView(radio)
```

### Compose

```kotlin
var selectedValue by remember { mutableStateOf("en") }

Radio(
    buttons = listOf(
        RadioButton("en", mapOf("en" to "English", "ru" to "Английский")),
        RadioButton("ru", mapOf("en" to "Russian", "ru" to "Русский"))
    ),
    language = Language.EN,
    theme = Theme.FREE_LIGHT,
    value = selectedValue,
    onValueChange = { selectedValue = it }
)
```

## API

### Java Custom View

```java
// Конфигурация
void setButtons(List<RadioButton> buttons)
void setLanguage(Language language)    // Language.EN, Language.RU
void setTheme(Theme theme)            // Theme.FREE_LIGHT, Theme.FREE_DARK и т.д.

// Текст (необязательно, локализованный)
void setTitle(Map<String, String> title)
void setDescriptionAbove(Map<String, String> text)
void setDescription(Map<String, String> text)   // описание снизу

// Значение
void setSelectedValue(String value)
void setSelectedValue(String value, boolean isTriggerCallback)
String getSelectedValue()

// Раскладка
void setWidth(int widthPx)            // фиксированная общая ширина (0 = авто по тексту)
void setMargin(int left, int top, int right, int bottom)
int getMarginLeft() / getMarginTop() / getMarginRight() / getMarginBottom()
boolean isMarginSet()

// Состояние
Theme getCurrentTheme()
Language getCurrentLanguage()

// События
void setOnValueChangeListener(OnValueChangeListener listener)
void setOptionHandler(String value, Consumer<Radio> handler)   // обработчик UI для отдельного пункта

interface OnValueChangeListener {
    void onValueChange(String newValue);
}
```

### Builder

Текучий (fluent) `create(...)` builder для обязательных полей плюс необязательные текст, отступы, ширина, слушатели. Это точка входа для Java; в Kotlin предпочтительнее DSL-расширение из раздела [Kotlin (рекомендуется — DSL)](#kotlin-рекомендуется--dsl), которое оборачивает этот Builder.

```java
Radio.create(context, theme, language, buttons, selectedValue)
    .title(Map<String, String>)
    .descriptionAbove(Map<String, String>)
    .description(Map<String, String>)
    .margin(int left, int top, int right, int bottom)   // или marginTop/Bottom/Left/Right
    .width(int widthPx)
    .onValueChange(OnValueChangeListener)
    .on(String value, Consumer<Radio> handler)          // обработчик для отдельного пункта
    .build();
```

### Обёртка для Compose

```kotlin
@Composable
fun Radio(
    buttons: List<RadioButton>,
    language: Language,
    theme: Theme,
    value: String,
    onValueChange: (String) -> Unit,
    title: Map<String, String>? = null,
    descriptionAbove: Map<String, String>? = null,
    descriptionBelow: Map<String, String>? = null,
    width: Int? = null,
    onViewCreated: ((Radio) -> Unit)? = null  // для тестирования
)
```

Nullable-параметры — управляемые значения: non-null применяет значение, а последующий `null` скрывает соответствующий текст того же экземпляра `Radio` (`title`, `descriptionAbove`, `descriptionBelow`). `width` фиксирует ширину области элементов в пикселях; `null` сбрасывает её в `0`, тогда ширина подбирается по тексту кнопок. Per-option обработчики `on(value, Consumer<Radio>)` из Java Builder намеренно не экспонируются — `onValueChange` уже покрывает выбор; для побочных UI-эффектов на конкретной опции используйте Java Builder напрямую. `onViewCreated` — одноразовый хук создания, он не вызывается повторно при рекомпозиции.

### RadioButton

```java
RadioButton(String value, Map<String, String> label)

String getText(String lang)       // локализованный текст с fallback
String getValue()
Map<String, String> getLabel()
RadioButton copy(String value, Map<String, String> label)
```

## Темы

| Enum | Стиль |
|------|-------|
| `Theme.FREE_LIGHT` | Светлая + синий градиент |
| `Theme.FREE_DARK` | Тёмная + синий градиент |
| `Theme.DREAMER_LIGHT` | Светлая + коричневый градиент |
| `Theme.DREAMER_DARK` | Тёмная + коричневый градиент |

Цвета темы определены в [RadioTheme.java](../../voboost-components/src/main/java/ru/voboost/components/radio/RadioTheme.java): `RadioColors`, `RadioColorSchemes`, `RadioDimensions`.

## Локализация

Внешняя через `Map<String, String>` — нет общей системы i18n:

```java
new RadioButton("value", Map.of("en", "English", "ru", "Русский"));
```

Язык задаётся через `setLanguage(Language.EN)` / `language = Language.EN`. Текстовые setters, методы Builder и `RadioButton` сохраняют снимки в порядке вставки. Разрешение использует точный язык, затем первую ненулевую сохранённую метку; изменения исходной или возвращённой Map на неё не влияют.

## Детали реализации

### Рендеринг

На базе canvas с 3 слоями:
1. **Фон** — скруглённый прямоугольник с цветом темы
2. **Выделение** — отрисовывается по-разному в зависимости от темы:
   - **Free темы** — NinePatch-слайдер (`RadioPrimitive.png`); откатывается к параметрическому, если битмап недоступен
   - **Dreamer темы** — параметрический скруглённый прямоугольник, залитый `LinearGradient`
3. **Текст** — по центру, цвет меняется в зависимости от покрытия выделением

### Анимация

`ValueAnimator` + `OvershootInterpolator`. Анимирует X-позицию и ширину фона выделения (400 мс). Аппаратный слой отключён — `RadioPrimitive` вызывает `setLayerType(LAYER_TYPE_NONE, null)` (для сравнения: Button/Select используют `LAYER_TYPE_HARDWARE`).

При выборе касанием `OnValueChangeListener.onValueChange` срабатывает с задержкой 300 мс (`RadioDimensions.VALUE_CHANGE_DELAY`) во время анимации — не синхронно в момент касания.

### Касание

Значение выбирают только сегменты примитива, использующие локальные координаты дочернего view из обычного dispatch. Заголовок и описания значение не выбирают. Вертикальная прокрутка, отменённый жест и отключённый контрол не выбирают значение; завершённый, отменённый или прерванный отключением активный жест снимает запрет перехвата у родителя. Корректный tap анимирует переход к затронутому пункту и вызывает callback после указанной выше задержки 300 мс.

### Состояние выбора

Выбор нормализуется к `""`, если значение равно null, неизвестно, удалено из списка или список пуст. Повторяющиеся значения поддерживаются; геометрией индикатора владеет первый совпадающий сегмент. Пустой список очищает геометрию пунктов и индикатора. Программный выбор или изменение списка отменяет текущую анимацию выбора и делает отложенный callback недействительным, поэтому старый tap не может сообщить значение после более нового обновления состояния.

### Размеры

Все размеры — в **пикселях** (требование автомобильной платформы). Константы в `RadioDimensions`.

## Структура файлов

```
radio/
├── Radio.java              # Основная реализация
├── RadioPrimitive.java     # Рендеринг на canvas и касания (package-private)
├── Radio.compose.kt                # Обёртка для Compose
├── RadioButton.java        # Модель данных
├── RadioTheme.java         # Цвета, размеры, схемы
├── Radio.test/
│   ├── RadioTestUnit.java  # Unit-тесты
│   └── RadioTestVisual.java # Тесты визуальной регрессии
└── Radio.screenshots/      # Эталонные изображения
```

## Тестирование

- **Unit**: `Radio.test/RadioTestUnit.java` — инициализация, состояние, callback, краевые случаи и касание
- **Визуальные**: `Radio.test/RadioTestVisual.java` — все комбинации тема/язык, кадры анимации
- **Скриншоты**: `Radio.screenshots/` — эталонные изображения для визуальной регрессии

```bash
./gradlew test --tests "*RadioTest*"   # Все тесты Radio
./gradlew record                       # Записать новые скриншоты
```

## Демонстрационные приложения

4 демо в [`src/demo-*/`](../../voboost-components/src/demo-java):
- **demo-java** — Чистый Java
- **demo-kotlin** — Kotlin + Java Custom View
- **demo-compose** — Jetpack Compose
- **demo-pixel** — Пиксельно-точное визуальное тестирование

Описание над переключателем не имеет отступа от заголовка, если заголовок отсутствует или пуст; при появлении заголовка восстанавливается стандартный отступ.
