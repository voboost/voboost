# Компонент Popup

[Screen](component-screen.ru.md) | [Tabs](component-tabs.ru.md) | [Panel](component-panel.ru.md) | [Section](component-section.ru.md) | [Radio](component-radio.ru.md) | [Checkbox](component-checkbox.ru.md) | [Button](component-button.ru.md) | [Buttons](component-buttons.ru.md) | [Select](component-select.ru.md) | [Dialog](component-dialog.ru.md) | **Popup** | [Toast](component-toast.ru.md) | [Text](component-text.ru.md) | [Hint](component-hint.ru.md) | [Font](component-font.ru.md) | [Theme](component-theme.ru.md) | [Language](component-language.ru.md).

[English](component-popup.md) | **Русский**

## Архитектура

- **[Popup.java](../../voboost-components/src/main/java/ru/voboost/components/popup/Popup.java)** — подкласс `android.app.Dialog`, предоставляющий overlay-контейнер с анимациями
- **Нет обёртки для Compose** — базовая инфраструктура, используемая [Dialog](component-dialog.ru.md) и [Select](component-select.ru.md)

## Назначение

Popup — это **overlay-контейнер**, предоставляющий:
- Полупрозрачный затемняющий фон
- Скруглённую панель содержимого с цветами темы
- Анимации появления/исчезновения (масштаб + fade), 300мс / 150мс
- Закрытие по касанию вне панели (настраивается)

Dialog и Select агрегируют Popup как контейнер, подставляя собственное содержимое.

## API

```java
Popup popup = new Popup(context);
popup.setTheme(Theme.FREE_DARK);
popup.getCurrentTheme();
popup.setPopupContentView(myView);
popup.setDismissOnTouchOutside(true);
popup.setAnimationDuration(300);   // -1 восстанавливает значение по умолчанию
popup.setOnDismissAction(() -> { /* очистка */ });
popup.clearOnDismissAction();
popup.show();
popup.dismissWithAnimation();
popup.getOverlayViewForTest();     // только для тестов: полный overlay-view
```

Длительность анимации принимает `-1` или положительное число миллисекунд. Ноль и значения меньше `-1` вызывают `IllegalArgumentException`.

### Управление жизненным циклом

Popup разворачивает вложенные `ContextWrapper` с защитой от циклов. Если найденная Activity реализует `LifecycleOwner`, для текущей сессии показа регистрируется один observer: повторный `show()` заменяет его, а любой путь закрытия удаляет ровно один раз. Уничтожение Activity закрывает popup. Контекст без Activity поддерживается как явно непривязанная сессия без автоматического lifecycle-закрытия.

Однозначный API callback-а — `setOnDismissAction`. Старый overload `setOnDismissListener(Runnable)` сохранён для совместимости; для очистки используйте `clearOnDismissAction()`, поскольку Java-вызов с `null` неоднозначен с platform overload. Action вызывается один раз на закрытую сессию показа.

### Рекомендации

- Всегда вызывайте `dismissWithAnimation()` вместо `dismiss()` для единообразного UX
- Popup управляет собственным жизненным циклом — не храните ссылки после показа
- View содержимого добавляется в контейнер popup — не добавляйте его в другие родительские view

## Размеры

| Свойство | Значение |
|----------|-------|
| Ширина панели | 1300px |
| Радиус скругления | 20px |
| Padding | 0px |
| Длительность появления / скрытия | 300мс / 150мс |
| Диапазон масштаба | 0.8 → 1.0 |

## Структура файлов

```
popup/
├── Popup.java         # Основная реализация
├── PopupTheme.java    # Цвета, размеры
├── Popup.test/
│   ├── PopupTestUnit.java
│   └── PopupTestVisual.java
└── Popup.screenshots/
```
