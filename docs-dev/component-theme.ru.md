# Theme

[Screen](component-screen.ru.md) | [Tabs](component-tabs.ru.md) | [Panel](component-panel.ru.md) | [Section](component-section.ru.md) | [Radio](component-radio.ru.md) | [Checkbox](component-checkbox.ru.md) | [Button](component-button.ru.md) | [Buttons](component-buttons.ru.md) | [Select](component-select.ru.md) | [Dialog](component-dialog.ru.md) | [Popup](component-popup.ru.md) | [Toast](component-toast.ru.md) | [Text](component-text.ru.md) | [Hint](component-hint.ru.md) | [Font](component-font.ru.md) | **Theme** | [Language](component-language.ru.md).

[English](component-theme.md) | **Русский**

Общее перечисление (enum) тем для всех компонентов Voboost.

## Файл

**[Theme.java](../../voboost-components/src/main/java/ru/voboost/components/theme/Theme.java)** — Java enum, используется напрямую как в Java-, так и в Kotlin-коде.

**[IThemable.java](../../voboost-components/src/main/java/ru/voboost/components/theme/IThemable.java)** — Интерфейс для распространения темы в контейнерных компонентах.

## Значения

| Enum                  | Строковое значение |
|-----------------------|-----------------|
| `Theme.FREE_LIGHT`    | `"free-light"`  |
| `Theme.FREE_DARK`     | `"free-dark"`   |
| `Theme.DREAMER_LIGHT` | `"dreamer-light"`|
| `Theme.DREAMER_DARK`  | `"dreamer-dark"` |

## API

```java
// Методы экземпляра
@NonNull String getValue()              // строковое представление для сериализации
boolean isLight()                        // true для FREE_LIGHT, DREAMER_LIGHT
boolean isDark()                         // true для FREE_DARK, DREAMER_DARK
boolean isFree()                         // true для FREE_LIGHT, FREE_DARK
boolean isDreamer()                      // true для DREAMER_LIGHT, DREAMER_DARK

// Статические методы
@NonNull static Theme fromValue(@Nullable String value)  // разбор строки, по умолчанию: FREE_DARK
static boolean isValidThemeValue(@Nullable String value) // строгая валидация
```

## Поведение разбора

Метод `fromValue` использует **сравнение без учёта регистра**:
- `"free-light"`, `"FREE-LIGHT"`, `"Free-Light"` — все возвращают `Theme.FREE_LIGHT`
- Недопустимые значения (null, пустая строка, неизвестные) по умолчанию дают `Theme.FREE_DARK`
- Для строгой валидации сначала проверяйте значения через `isValidThemeValue()`

**Примеры:**.
```java
// Простой разбор — используется FREE_DARK как запасное значение
Theme theme = Theme.fromValue(configValue);

// Строгая валидация — явная обработка некорректного ввода
if (Theme.isValidThemeValue(userInput)) {
    theme = Theme.fromValue(userInput);
} else {
    theme = Theme.FREE_DARK; // Явное запасное значение
    // Показать ошибку пользователю
}
```

## Потокобезопасность

Перечисление `Theme` неизменяемо и потокобезопасно:
- Все значения enum — константы (одиночки)
- `getValue()` возвращает неизменяемую строку
- `fromValue()` не имеет состояния и может вызываться конкурентно
- Синхронизация не требуется

## Архитектурное решение

Java enum (а не Kotlin), потому что:
1. Используется напрямую в Java-коде View
2. Без проблем работает в Kotlin
3. Не требует преобразования между слоями

Проекты-потребители (например, voboost-config) должны использовать именно это общее перечисление `Theme`, а не определять собственное, поскольку voboost-components — самостоятельная библиотека, которую можно применять вне инфраструктуры voboost.

## Лучшие практики

1. **Предпочитайте константы enum** методу `fromValue()`, где возможно
   ```java
   // Хорошо
   theme = Theme.FREE_LIGHT;
   // Допустимо (для динамических значений)
   theme = Theme.fromValue(configValue);
   ```

2. **Валидируйте внешний ввод** перед разбором
   ```java
   if (Theme.isValidThemeValue(userInput)) {
       theme = Theme.fromValue(userInput);
   } else {
       theme = Theme.FREE_DARK; // Запасное значение по умолчанию
   }
   ```

3. **Используйте вспомогательные методы** для проверки типа
   ```java
   if (theme.isLight()) {
       // Применить стилизацию для светлой темы
   }
   ```

## Интерфейс IThemable

Компоненты, поддерживающие переключение тем, реализуют `IThemable`:

```java
public interface IThemable {
    void setTheme(Theme theme);           // Установить тему для этого компонента
    Theme getCurrentTheme();              // Текущая тема или null, если не задана
    void propagateTheme(Theme theme);      // Распространить на дочерние элементы
}
```

**Шаблоны реализации:**.
- **Контейнерные компоненты** (Screen, Panel, Section, Tabs): берут snapshot непосредственных `IThemable`-детей и один раз вызывают `setTheme()` каждого; глубиной владеют setter-ы детей
- **Листовые компоненты** (Button, Checkbox, Text, Radio, Select): реализуют `propagateTheme()` как пустой метод (Text, Toast) или делегируют примитиву через `primitive.setTheme()` (Button, Checkbox, Radio, Select); Hint пробрасывает в собственный `setTheme`
- **Тематичные не-IThemable** (Dialog, Popup): предоставляют `setTheme(Theme)`/`getCurrentTheme()`, но НЕ реализуют `IThemable`. Это overlay-контейнеры (не custom View с дочерними элементами для проброса темы), поэтому реализация интерфейса намеренно пропущена
