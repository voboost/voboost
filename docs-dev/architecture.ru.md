# Архитектура

[English](architecture.md) | **Русский**

Voboost собирается из независимых исходных репозиториев, но выпускается только как единый продукт. Корневой репозиторий `voboost` владеет Product SemVer, `voboost.json`, `channel.json`, release definitions, координацией публикации и пользовательской и developer-документацией.

## Ответственность компонентов

| Компонент | Владеет | Не владеет |
| --- | --- | --- |
| `voboost-app` | непривилегированной OTA-загрузкой, закрытым кандидатом, self-update APK, подтверждением удаления | применимостью агентов, root-изменениями, публикацией |
| `voboost-agents` | средой агентов, подписанным `agents.json`, общим и target DEX, OEM-заглушками | OTA-сетью, установщиком, root-файлами |
| `voboost-inject` | `voboost-daemon`, injector, implant, системными операциями, root-состоянием, выбором и поколениями | HTTP/TLS, desktop UI, выбором выпуска |
| `voboost-installer` | offline host UX, ADB-транспортом, упакованным комплектом, наблюдением операции | сборками компонентов, загрузкой артефактов, публикацией |
| `voboost` | identity продукта, глобальными схемами, точным source definition, сборкой выпуска, документацией | Android-, agent- и native-исходниками продукта |

Репозиторий `voboost-inject` собирает две постоянные программы: `voboost-daemon` владеет постоянным состоянием, конфигурацией и файловыми логами; `voboost-inject` проверяет данные агентов, выбирает процессы и управляет внедрением. Android init запускает inject после службы владельца и останавливает его при остановке или перезапуске владельца.

Выбор пользователя принадлежит демону и хранится один раз в `/data/voboost/agents.json` (схема 2), отдельно от подписанного каталога агентов. Демон сохраняет `userRevision` и метаданные действий для каждого поля. Установка, смена поколения, откат и повторы сохраняют этот выбор. Значения по умолчанию заполняют только поля, которые пользователь ещё не выбирал.

Квитанция Saved подтверждает надёжную запись; применение в Android или CAN имеет отдельный результат. Ошибка или задержка применения не возвращает чекбокс обратно. Обычная смена режима запуска действует со следующей загрузки; явный жест восстановления скрытого интерфейса может открыть его в текущей загрузке.

## Комплект продукта

У каждой публикации один Product SemVer и один подписанный `voboost.json`. Манифест содержит полный список versionless device artifacts, значения совместимости, точные size/SHA-256 и обязательные package/version/signer identity для `voboost-app.apk`.

Агенты находятся вне APK. `voboost-agents` создаёт `agents.json`, `agents.json.sig`, `agent-common.dex` и один `agent-*.dex` для каждого логического модуля, общий для его target processes. `voboost-inject` проверяет каталог и вычисляет применимость по root-owned `vehicle.json`; приложение и установщик не дублируют этот выбор.

`restore.json` и `restore.json.sig` являются product-derived recovery authority. Документ связывает release identity, digest точных байтов source definition и canonical digest всех restorable artifact rows без restore pair, поэтому циклического хеша нет. Пара меняется с каждым product release identity.

## Установка и runtime

1. Offline installer проверяет один упакованный подписанный комплект продукта и размещает плоские артефакты во staging
2. Фиксированная программа установки на машине повторно проверяет комплект и создаёт первое root-owned generation
3. Android init запускает `/data/voboost/voboost-daemon` в отдельном SELinux domain
4. `voboost-inject` проверяет установленное состояние и данные агентов, определяет применимость по сведениям о машине и проверяет identity целевого процесса; постоянное состояние он запрашивает через аутентифицированный протокол владельца
5. Injector загружает implant точного ABI; implant получает `agent-common.dex` и только target-specific DEX через удерживаемые дескрипторы
6. Host agents единолично управляют общими OEM surfaces, а feature agents передают contributions через process-local registry

## OTA

Приложение находит выпуск через подписанный `channel.json`, скачивает immutable signed `voboost.json` и все изменившиеся артефакты. Оно передаёт байты, но не результат применимости и не произвольный путь. `voboost-inject` собирает полный кандидат, проверяет все identity, создаёт generation или native transaction и последним делает активным новый установленный `voboost.json`.

Целевые процессы сохраняют уже загруженный DEX до естественного перезапуска процесса или подтверждённой пользователем перезагрузки. Voboost не завершает OEM-процессы автоматически.

## Граница проверок

Host-gates охватывают source definition, схемы, разбор манифеста, извлечение APK identity через pinned tooling, acyclic restore production и verification и reuse артефактов. Product signatures у consumers, межъязыковый разбор, offline packages, emulator, device, SELinux Enforcing и автомобиль сохраняют независимые gates.

## Связанные страницы

- [Репозитории](repository.ru.md)
- [Приложение](application.ru.md)
- [Среда агентов](agents-runtime.ru.md)
- [Native runtime](native.ru.md)
- [Обновления](updates.ru.md)
- [Выпуск](release.ru.md)
