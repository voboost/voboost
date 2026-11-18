# Voboost

[English](README.md) | **Русский**

Voboost добавляет функции в мультимедиа Voyah Free и Dreamer. После установки пользователь включает и выключает их в отдельной вкладке настроек машины.

В beta1 входят семь stable-агентов:

- `settings` добавляет вкладку Voboost в настройки машины
- `font-cyrillic` исправляет кириллицу в штатном полужирном шрифте
- `keyboard-ru` добавляет русскую и английскую раскладки вместо китайской и английской
- `keyboard-en` оставляет только английскую раскладку
- `launcher-apps` показывает сторонние приложения в штатном лаунчере китайских машин
- `vehicle-drive-forced-ev` восстанавливает Forced EV после переключения в Electric или перезагрузки
- `vehicle-drive-pedestrian-warning` восстанавливает только подтверждённое отключение звука после перезапуска CAN; последующее ручное включение отменяет восстановление

Unstable-агент `logs` добавляет в настройки машины вкладку Logs для просмотра записей приложения, `voboost-daemon` и агентов. Он входит в beta1 для разработки и диагностики.

## Документация

- [Что такое Voboost](docs-user/index.ru.md)
- [Быстрый старт](docs-user/quick-start.ru.md)
- [Установка](docs-user/install.ru.md)
- [Как пользоваться](docs-user/usage.ru.md)
- [Обновления](docs-user/updates.ru.md)
- [Восстановление после штатной прошивки](docs-user/restore.ru.md)
- [Полное удаление](docs-user/uninstall.ru.md)
- [Логи](docs-user/logs.ru.md)
- [Планы развития](docs-user/roadmap.ru.md)
- [Что изменилось](docs-user/changelog.ru.md)
- [Документация разработчика](docs-dev/index.ru.md)
- [Словарь](GLOSSARY.ru.md)

## Репозитории

- [voboost](https://github.com/voboost/voboost) — версия продукта, манифесты, сборка выпуска и документация
- [voboost-app](https://github.com/voboost/voboost-app) — непривилегированное Android-приложение для OTA
- [voboost-agents](https://github.com/voboost/voboost-agents) — runtime агентов, target DEX, каталог и OEM fixtures
- [voboost-inject](https://github.com/voboost/voboost-inject) — root-демон, injector, implant и системные операции
- [voboost-installer](https://github.com/voboost/voboost-installer) — офлайн-установщик для macOS, Windows и Linux

## Лицензия

Собственные исходники и материалы распространяются по [MIT](LICENSE); [русский перевод](LICENSE.ru.md) предоставлен для справки. Сторонние компоненты и материалы сохраняют собственные условия; см. [THIRD_PARTY.md](THIRD_PARTY.md).

Источник действующей лицензии кода — [LICENSE](LICENSE) и [русский перевод](LICENSE.ru.md) этого репозитория. voboost-license сохраняет только исторические условия; лицензирование логотипа рассматривается отдельно в voboost-logo.
