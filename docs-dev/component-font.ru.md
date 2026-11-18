# Компонент Font

[Screen](component-screen.ru.md) | [Tabs](component-tabs.ru.md) | [Panel](component-panel.ru.md) | [Section](component-section.ru.md) | [Radio](component-radio.ru.md) | [Checkbox](component-checkbox.ru.md) | [Button](component-button.ru.md) | [Buttons](component-buttons.ru.md) | [Select](component-select.ru.md) | [Dialog](component-dialog.ru.md) | [Popup](component-popup.ru.md) | [Toast](component-toast.ru.md) | [Text](component-text.ru.md) | [Hint](component-hint.ru.md) | **Font** | [Theme](component-theme.ru.md) | [Language](component-language.ru.md).

[English](component-font.md) | **Русский**

Предоставляет проектный шрифт для всех компонентов.

## Архитектура

Единственный исходный файл (**[Font.java](../../voboost-components/src/main/java/ru/voboost/components/font/Font.java)**) и три сопутствующих ассета `.ttf` — статическая утилита, обёртка для Compose не требуется.

Файлы шрифтов расположены рядом (BEM-структура):
```
font/
├── Font.java              # Загрузчик с кэшированием
├── Font.ttf               # Обычное начертание (Regular)
├── Font_bold_ascii.ttf    # Жирный для ASCII-текста
└── Font_bold_unicode.ttf  # Жирный для не-ASCII текста (кириллица и т. д.)
```

## Использование

```java
// Обычный шрифт
Typeface regular = Font.getRegular(context);
paint.setTypeface(regular);

// Жирный шрифт (автовыбор варианта ASCII или Unicode)
Typeface bold = Font.getBold(context, "Hello");    // → Font_bold_ascii.ttf
Typeface boldRu = Font.getBold(context, "Привет"); // → Font_bold_unicode.ttf
```

## API

```java
// Возвращает обычный шрифт (никогда не null)
// @throws RuntimeException, если файл шрифта не найден
static Typeface getRegular(Context context)

// Возвращает жирный шрифт, соответствующий содержимому текста (никогда не null)
// - ASCII-текст → Font_bold_ascii.ttf
// - Текст с не-ASCII символами → Font_bold_unicode.ttf
// - null или пустая строка → Font_bold_ascii.ttf
// @param text текст для отрисовки (используется для выбора варианта шрифта)
// @throws RuntimeException, если файл шрифта не найден
static Typeface getBold(Context context, String text)

// Очищает все закэшированные экземпляры Typeface (только для тестирования)
static void clearCache()
```

## Ключевое поведение

- **Кэширование** — экземпляры Typeface загружаются один раз и переиспользуются
- **Без фолбэка** — выбрасывает `RuntimeException`, если файл шрифта не найден, никогда не возвращает `null`
- **Два жирных варианта** — текст только из ASCII использует `Font_bold_ascii.ttf`, текст с не-ASCII символами использует `Font_bold_unicode.ttf`
- **Загрузка ассетов** — шрифты загружаются из ассетов через конфигурацию `build.gradle.kts`: `assets.srcDir("src/main/java/ru/voboost/components/font")`

## Потокобезопасность

Все публичные методы в Font **потокобезопасны**:

- `getRegular()` — синхронизирован, кэширует экземпляр Typeface
- `getBold()` — синхронизирован, кэширует варианты жирного шрифта (ASCII/Unicode)
- `clearCache()` — синхронизирован, сбрасывает все закэшированные экземпляры (regular, bold ASCII/Unicode)

Компонент использует статические закэшированные экземпляры Typeface, которые загружаются один раз и переиспользуются. Весь доступ к кэшу синхронизирован для предотвращения состояния гонки в многопоточных средах.

В автомобильных UI-системах Font обычно вызывается только из главного UI-потока, но реализация обеспечивает потокобезопасность для любого варианта использования.
- **Обработка null** — `getBold(context, null)` возвращает ASCII жирный шрифт (то же, что и пустая строка)
- **Потокобезопасный** — все публичные методы синхронизированы
