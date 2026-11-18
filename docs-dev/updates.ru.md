# Обновления продукта

[English](updates.md) | **Русский**

Каждое опубликованное изменение создаёт новый Product SemVer Voboost и один полный подписанный `voboost.json`. Отдельно опубликованных версий app, agents и native runtime нет.

## Поиск и загрузка

Android-приложение читает ограниченный подписанный `channel.json`, запрещает откат и скачивает immutable `voboost.json` по точным URL, size, SHA-256 и подписи. Оно сравнивает полный список артефактов с identity активного манифеста демона и скачивает все изменившиеся артефакты. Приложение не вычисляет применимость агентов и native update-роли.

JobScheduler отвечает за периодическую проверку и продолжение. Приложение передаёт descriptor-bound proposal через `voboost.ota.v1`; демон аутентифицирует peer, получает манифест, подпись и артефакты через `SCM_RIGHTS` и собирает полный кандидат, не доверяя выбранным приложением путям или ролям.

## Проверка кандидата

Приложение записывает изменившиеся байты в закрытый каталог и последним публикует readiness. В proposal нет shell command, результата применимости и произвольного root path.

`voboost-daemon` независимо проверяет release signature, schema, compatibility, точный закрытый inventory, все полученные байты и каждый повторно используемый установленный файл. Он вычисляет применимость по `agents.json` и root-owned `vehicle.json`, а затем создаёт одно полное candidate state.

## Активация по изменившимся ролям

| Изменившиеся байты | Активация |
| --- | --- |
| Только product metadata и product-derived recovery authority | проверить установленный комплект и опубликовать новый манифест без перезагрузки |
| Каталог агентов или target DEX | создать новое immutable generation |
| `agent-common.dex` | пересобрать полное generation применимых агентов |
| `voboost-app.apk` | заменить пакет через PackageInstaller и продолжить транзакцию |
| `voboost-daemon`, native libraries, init или SELinux | выполнить root update и post-reboot verification |
| Смешанные роли | выполнить одну product transaction в порядке, заданном compatibility |

`restore.json` и `restore.json.sig` пересоздаются для каждого Product SemVer, потому что разрешают восстановление полного установленного состояния продукта. Их нельзя повторно использовать только из-за неизменившегося commit `voboost-inject`.

## Commit и rollback

Установленный `voboost.json` становится активным последним, после активации и повторной проверки каждой изменившейся роли. До этого установленной authority остаётся предыдущая версия. При ошибке используются прежнее generation, прежние native bytes и WAL той же транзакции; вторая операция не создаётся.

Уже загруженный DEX остаётся в OEM-процессе. Если обязательный затронутый процесс использует прежнее generation, обновление получает состояние `reboot-required` и ждёт подтверждения пользователя. Voboost не завершает target process и не перезагружает мультимедиа автоматически.

## Проверки выпуска

Каждый выпуск проходит проверки schema/parser, APK evidence, матриц артефактов, продолжения PackageInstaller, native activation, rollback, emulator-, device- и заявленных vehicle-gates над одним immutable candidate. Результаты относятся только к названным артефакту и среде.

## Связанные страницы

- [Приложение](application.ru.md)
- [Архитектура](architecture.ru.md)
- [Файлы](storage.ru.md)
- [Безопасность](security.ru.md)
- [Выпуск](release.ru.md)
