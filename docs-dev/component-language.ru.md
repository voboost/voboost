# Language

[Screen](component-screen.ru.md) | [Tabs](component-tabs.ru.md) | [Panel](component-panel.ru.md) | [Section](component-section.ru.md) | [Radio](component-radio.ru.md) | [Checkbox](component-checkbox.ru.md) | [Button](component-button.ru.md) | [Buttons](component-buttons.ru.md) | [Select](component-select.ru.md) | [Dialog](component-dialog.ru.md) | [Popup](component-popup.ru.md) | [Toast](component-toast.ru.md) | [Text](component-text.ru.md) | [Hint](component-hint.ru.md) | [Font](component-font.ru.md) | [Theme](component-theme.ru.md) | **Language**.

[English](component-language.md) | **Русский**

Общее перечисление (enum) языков для всех компонентов Voboost.

## Файл

**[Language.java](../../voboost-components/src/main/java/ru/voboost/components/i18n/Language.java)** — Java enum, используется напрямую как в Java, так и в Kotlin-коде.

**[ILocalizable.java](../../voboost-components/src/main/java/ru/voboost/components/i18n/ILocalizable.java)** — Интерфейс для переключения и распространения языка в контейнерных компонентах.

**[I18nUtils.java](../../voboost-components/src/main/java/ru/voboost/components/i18n/I18nUtils.java)** — Финальный (final) служебный класс со вспомогательными методами для поиска локализованных строк.

## Значения

| Enum         | Код    |
|---------------|--------|
| `Language.EN` | `"en"` |
| `Language.RU` | `"ru"` |

## API

```java
// Language.java
String getCode()                  // код ISO 639-1 (например, "en", "ru")
static Language fromCode(String)  // разбор строки, без учёта регистра, по умолчанию: EN
```

## Поведение разбора

Метод `fromCode` использует сопоставление **без учёта регистра** через `equalsIgnoreCase`:
- `"en"`, `"EN"`, `"En"` возвращают `Language.EN`
- `"ru"`, `"RU"`, `"Ru"` возвращают `Language.RU`
- Недопустимые значения (null, пустая строка, неизвестные) по умолчанию дают `Language.EN`

**Примеры:**.
```java
// Простой разбор — используется fallback на EN
Language lang = Language.fromCode(configValue);

// Прямая константа, когда значение известно
Language lang = Language.RU;
```

## I18nUtils

`I18nUtils` — это `final` служебный класс с приватным конструктором (выбрасывает `AssertionError`); его нельзя создать.

```java
// I18nUtils.java
static String getLocalizedString(
    Map<String, String> localizedStrings,  // код языка -> строка
    Language language,                      // текущий язык
    String defaultValue                     // возвращается, если не найдено
)
```

Поведение:
- Возвращает точное non-null значение для `language.getCode()`; точное `""` сохраняется
- Если код отсутствует или содержит null, возвращает первое non-null значение в порядке map
- Возвращает `defaultValue` для null/пустой map, null языка или map только с null-значениями

## Интерфейс ILocalizable

Компоненты, поддерживающие переключение языка, реализуют `ILocalizable`:

```java
public interface ILocalizable {
    void setLanguage(Language language);           // установить язык для компонента
    Language getCurrentLanguage();                 // текущий язык или null, если не задан
    void propagateLanguage(Language language);     // распространить на дочерние компоненты
}
```

**Шаблоны реализации:**.
- **Контейнерные компоненты** (Screen, Panel, Section, Tabs): берут snapshot непосредственных `ILocalizable`-детей и один раз вызывают `setLanguage()` каждого; глубиной владеют setter-ы детей
- **Листовые компоненты** (Button, Checkbox, Text, Radio, Select): Реализуют `propagateLanguage()` как пустой метод (Text) или делегируют примитиву через `primitive.setLanguage()` (Button, Checkbox, Radio, Select); Hint пробрасывает в собственный `setLanguage`

**Обработка null:**.
- Публичный `setLanguage(null)` следует проверенному контракту реализации; Text, Hint, Section и Tabs выбрасывают `IllegalArgumentException`
- Forwarding-метод `propagateLanguage` следует целевому setter и не создаёт отдельную nullable политику; остальное поведение контейнеров остаётся компонентным

## Архитектурное решение

Java enum (не Kotlin), потому что:
1. Используется напрямую в Java-коде View
2. Без проблем работает в Kotlin
3. Не требуется преобразование между слоями
