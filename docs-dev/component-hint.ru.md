# Компонент Hint

[Screen](component-screen.ru.md) | [Tabs](component-tabs.ru.md) | [Panel](component-panel.ru.md) | [Section](component-section.ru.md) | [Radio](component-radio.ru.md) | [Checkbox](component-checkbox.ru.md) | [Button](component-button.ru.md) | [Buttons](component-buttons.ru.md) | [Select](component-select.ru.md) | [Dialog](component-dialog.ru.md) | [Popup](component-popup.ru.md) | [Toast](component-toast.ru.md) | [Text](component-text.ru.md) | **Hint** | [Font](component-font.ru.md) | [Theme](component-theme.ru.md) | [Language](component-language.ru.md).

[English](component-hint.md) | **Русский**

## Архитектура

- **[Hint.java](../../voboost-components/src/main/java/ru/voboost/components/hint/Hint.java)** — Java Custom View: рендеринг текста на canvas с ручным переносом строк

`Hint extends View` и реализует `IThemable` + `ILocalizable`. Весь рендеринг выполняется в `onDraw` на `Canvas`; XML-разметки и отдельного класса-примитива нет. `HintTheme` — хранилище констант на уровне пакета (`final class HintTheme`), доступ к нему имеет только `Hint`.

Обёртки для Compose нет (нет `Hint.kt`). При необходимости используйте Java Custom View напрямую из Compose через `AndroidView`.

## Использование

### Java

```java
Hint hint = new Hint(context);
hint.setTheme(Theme.FREE_DARK);
hint.setLanguage(Language.EN);
hint.setText(Map.of(
    "en", "Stop all running applications to free memory.",
    "ru", "Остановить все запущенные приложения."
));
section.addView(hint);
```

### Kotlin

```kotlin
val hint = Hint(context).apply {
    setTheme(Theme.FREE_DARK)
    setLanguage(Language.EN)
    setText(mapOf(
        "en" to "Stop all running applications to free memory.",
        "ru" to "Остановить все запущенные приложения."
    ))
}
section.addView(hint)
```

## API

```java
// Конструкторы
Hint(Context context)
Hint(Context context, AttributeSet attrs)
Hint(Context context, AttributeSet attrs, int defStyleAttr)

// Текст (локализованный: код языка -> текст)
void setText(Map<String, String> textMap)   // ключи — "en", "ru" — НЕ Map<Language,String>

// Тема (IThemable) — null-тема допустима, используется тёмный цвет текста по умолчанию
void setTheme(Theme theme)
void propagateTheme(Theme theme)            // делегирует в setTheme

// Язык (ILocalizable)
void setLanguage(Language language)
Language getCurrentLanguage()
void propagateLanguage(Language language)   // делегирует в setLanguage
```

Примечания:

- `setText` принимает `Map<String, String>` с ключами в виде **строковых кодов языка** (`"en"`, `"ru"`), а не `Map<Language, String>`. Ключ сопоставляется с `Language.getCode()`
- Здесь нет `getCurrentTheme()` и нет `setMargin()` — `Hint` это листовой текстовый блок, который заполняет ширину, заданную родителем, и применяет собственные отступы сверху и снизу

## Темы

Цвет текста приглушённый (50% alpha от основного текста) и зависит только от того, тёмная тема или светлая. Определено в [HintTheme.java](../../voboost-components/src/main/java/ru/voboost/components/hint/HintTheme.java).

| Группа тем | Цвет текста |
|------------|-------------|
| Тёмные темы (значение заканчивается на `-dark`: `FREE_DARK`, `DREAMER_DARK`) | `#80ffffff` (белый 50% alpha) |
| Светлые темы (`FREE_LIGHT`, `DREAMER_LIGHT`) и `null` | `#801a1e28` (тёмный 50% alpha) |

Тема задаётся через `setTheme(Theme.FREE_DARK)` и т.д. `null` допустим и трактуется как светлая.

## Локализация

Внешняя, через `Map<String, String>` — нет общей системы i18n:

```java
hint.setText(Map.of("en", "Stop all apps", "ru", "Остановить все приложения"));
```

Язык задаётся через `setLanguage(Language.EN)` / `setLanguage(Language.RU)`. Разрешение (`resolveText`):

1. Поиск `currentLanguage.getCode()` (например `"en"`) в map
2. Если ключ отсутствует (или язык ещё не задан), используется первое non-null значение в сохранённом порядке итерации map; неявного приоритета английского нет
3. Если map пустая или `null`, `displayText` становится `""`, и компонент схлопывается до **0 высоты**

`setText` сохраняет защитный снимок с исходным порядком. `setText(null)` очищает map, resolved text, кэш строк и вклад текста в высоту. `setLanguage(null)` выбрасывает `IllegalArgumentException`.

## Детали реализации

### Рендеринг

На canvas, с единственным paint (`textPaint`, anti-aliased):

1. `init()` задаёт размер текста (`TEXT_SIZE_PX`) и цвет по умолчанию
2. `setText` / `setLanguage` вызывают `resolveText()`, который выбирает локализованную строку и подгружает `Font.getRegular(...)` как typeface, затем `invalidate()` + `requestLayout()`
3. `onMeasure` строит word-aware список перенесённых строк и вычисляет высоту из этого же списка:
   - Пустой текст → высота `0`
   - Иначе: `height = PADDING_TOP_PX + wrappedLines.size * lineHeight + PADDING_BOTTOM_PX`, где `lineHeight = TEXT_SIZE_PX * 1.3f`
4. `onDraw` использует тот же кэшированный список и не переносит текст повторно

Явные границы `\n`, пустые строки и последняя завершающая строка сохраняются. Каждый непустой абзац переносится через `Paint.breakText` с откатом к пробелу, когда это возможно.

### Размеры

Все размеры — в **пикселях** (требование автомобильной платформы). Константы в `HintTheme`.

| Свойство | Значение |
|----------|-------|
| Размер текста | 24px (`TEXT_SIZE_PX`) |
| Отступ сверху | 8px (`PADDING_TOP_PX`) |
| Отступ снизу | 8px (`PADDING_BOTTOM_PX`) |
| Высота строки | 31.2px (`TEXT_SIZE_PX * 1.3f`) |

Размер текста совпадает со стилем описания в Checkbox и Radio (24px).

## Структура файлов

```
hint/
├── Hint.java              # Основная реализация (View + onDraw)
├── HintTheme.java         # Размер текста, отступы, определитель цвета текста
└── Hint.test/
    ├── HintTestUnit.java   # Unit-тесты (init, текст, тема, язык, measure)
    └── HintTestVisual.java # Тесты визуальной регрессии (Roborazzi, 1920x720)
```

## Тестирование

- **Unit**: `Hint.test/HintTestUnit.java` — инициализация, состояние по умолчанию, `setText` (null, пустая, unicode), переключение языка + откат, `setTheme` (все enum + null) и инварианты `onMeasure` (формула высоты для одной строки, учёт отступов, схлопывание пустого текста в 0, ширина равна spec, идемпотентность повторного measure). Запускается на Robolectric, SDK 33
- **Visual**: `Hint.test/HintTestVisual.java` — скриншоты Roborazzi для всех комбинаций тема/язык в `1920x720` landscape, включая короткий текст, обёрнутый (многострочный) текст, пустую map и явный многострочный текст через `\n`. Скриншоты сохраняются в `Hint.screenshots/`

```bash
./gradlew test --tests "*HintTest*"   # Все тесты Hint
```
