# Агент `settings` и OEM surface

[English](agent-settings.md) | **Русский**

Агент `settings` — единственный host вкладки Voboost в `com.qinggan.app.setting`. Исходники находятся в [`voboost-agents`](../../voboost-agents/src/main/java/ru/voboost/agents/settings); отдельного модуля `ui` и pipeline resource APK `ui-res` нет.

## Владение

Только host agent находит OEM fragment factory, parent container, constructors, theme и symbolic resources. Feature agents регистрируют semantic contributions через `AgentSettingsTabRegistry`; они не устанавливают собственные menu hooks и не строят параллельную иерархию Settings.

Host публикует contributions транзакционно. Route становится видимым только после полного создания и привязки surface. Отсутствующий, частичный или неоднозначный capability запускает очистку и возвращает точную unsupported reason без fallback View.

## OEM adapter

Production adapter находит один полный target-specific factory contract по живым OEM objects и symbolic resources. Он создаёт вкладки Voboost и Logs только после успешной привязки constructor, container, theme, resources и contribution registry.

Positive, partial, ambiguous, recreation, cleanup, theme, resource и mutation-acknowledgement fixtures проверяют границу host. Физическая геометрия остаётся отдельным vehicle-gate.

## Contributions и состояние

Contribution содержит стабильные semantic tag и order. Labels, options, desired state, availability и target statuses приходят по типизированному транспорту демона. Изменение остаётся pending до коррелированного подтверждения сохранения от владельца. При ошибке checkbox сохраняет последний выбор пользователя для повтора; runtime-применение подтверждается отдельно. Успешное включение каталожной альтернативы освобождает только более старый конфликтующий выбор и запрашивает состояние соседа у владельца. Более новый ввод защищён request token, а старые ответы — границей принятой `userRevision`.

Surface не читает `/data/voboost`, agent DEX и manifests напрямую. `UninstallActivity` в `voboost-app` остаётся отдельной Android-границей подтверждения удаления.

## Системный язык

После версии и режима запуска первой идёт секция «Локализация», над «Автомобиль». Она содержит зарегистрированные в каталоге `i18n-ru` и `i18n-en`; категория `interface` и checkbox-контракт сохраняются. Оба агента можно включить одновременно. Отключение одного сохраняет второй. Переключатели включают агентов, а язык выбирается отдельно на штатной странице Display; отдельного языкового значения в canonical intent и проекции Settings нет. Изменения Android Configuration и locale broadcast обновляют подписи по фактическому системному языку. Базовый resolver поддерживает упрощённый китайский и английский fallback; русский системный выбор станет доступен после допуска полного runtime перевода.

Адаптер точной Free A9 Settings направляет неизвестную locale через штатные английские setter и signal branches. Оригинальный presenter согласованно устанавливает английское radio, QGSettings и SP. Ручной выбор EN/ZH сохраняется. Этот адаптер не означает допуск штатного RU на A11, resource APK или QNX-перевода.

## Проверка

Settings gate проверяет complete, partial и ambiguous discovery; publication и cleanup; recreation; theme и resource lookup; mutation acknowledgement и физическую геометрию на указанной прошивке. При ошибке structural check host возвращает точную unsupported reason и не публикует частичный surface.

## Связанные страницы

- [Среда агентов](agents-runtime.ru.md)
- [Приложение](application.ru.md)
- [Контракты](contracts.ru.md)
- [Тестирование](testing.ru.md)
