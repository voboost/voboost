# Реестр контрактов

[English](contracts.md) | **Русский**

У каждого контракта один source owner и независимые parsers на границах consumers. Production repositories обмениваются schemas, fixtures, финальными артефактами и evidence, но не импортируют исходники соседнего репозитория.

| Контракт | Владелец | Producer | Consumers |
| --- | --- | --- | --- |
| source definition | `voboost` | product release request | product compositor |
| `voboost.json` и signature | `voboost` | product compositor и release signer | installer, app, daemon/system programs |
| `channel.json` и signature | `voboost` | protected publication | app |
| APK evidence | наблюдение финального подписанного APK в `voboost` | pinned Android tooling | аудит; compositor повторяет извлечение |
| `agents.json` и signature | `voboost-agents` | agent release build | product compositor, daemon, implant boundary |
| agent selection/generation | `voboost-inject` | daemon | implant и runtime status consumers |
| operation и installed status | `voboost-inject/contract` | device-owned system programs и daemon | installer, при необходимости app |
| uninstall request | граница Settings-host и root daemon | `SettingsUninstall` через runtime executor | daemon/uninstaller |
| packaged artifact source | `voboost-installer` | platform packager | common installer core |

## Product manifest

[`voboost.schema.json`](../release/voboost.schema.json) задаёт cross-language JSON value contract. [`SCHEMA-CONFORMANCE.md`](../release/SCHEMA-CONFORMANCE.md) отделяет ограничения Draft 2020-12 от semantic checks точных байтов, duplicate keys, order, hashing, catalog correlation и signature.

App artifact row всегда содержит точные APK size/SHA-256, `packageName = "ru.voboost"`, `versionCode` в диапазоне `1..=2100000000` и `certificateSha256` signer. Identity agent DEX должны точно совпадать с отдельно подписанным каталогом.

[`restore.schema.json`](../release/restore.schema.json) задаёт acyclic recovery document, связанный с release identity, digest точных байтов source definition и canonical digest restorable closure без restore pair. Raw signature проверяется под отдельно настроенным restore-role key до authoring product manifest.

## Каталог агентов

`agents.json` использует строгую schema2: один логический ID агента и поля `platform`, `domain`, `entryAbi` у каждой цели. Android-цели используют `android` / `android` / `android-java-v1`, обязательный `entryClass` и одинаковые байты DEX своего модуля. QNX-цели используют `qnx` / `cluster` / `voboost-agent3-v1`, не содержат `entryClass` и ссылаются на native ELF. Process, file, size, SHA-256 и boot policy принадлежат каждой цели. `clusterRuntime` задаёт native runtime при наличии QNX-целей; `core` задаёт Android `agent-common.dex`. Schema1 отклоняется; миграции нет.

Продуктовый комплект содержит один multicall ELF `voboost-cluster-daemon`, `voboost-cluster-startup.sh`, `libvoboost-cluster-inject.so`, `libvoboost-runtime-cluster.so` и `libagent-font-cyrillic.so` с каноническими ролями `qnx-*`. ELF-строки используют ABI `qnx-aarch64`; startup script не содержит ABI. Совместимость продукта требует `agentCatalogSchema = 2`.

Root Plan остаётся единственным источником желаемого состояния. Cluster delivery — производная подписанная проекция; доставка courier или загрузка модуля не означают `applied`. Состояние исполнения определяется подписанными наблюдениями целей и текущим поколением.

Демон вычисляет применимость по каталогу и `vehicle.json`; приложение не создаёт авторитетный selection receipt.

## Operation и runtime transport

Плоский crate [`voboost-inject/contract`](../../voboost-inject/contract) владеет canonical operation, install, generation и APK identity types для привилегированных binaries. Runtime pipe frames связывают generation, process start time, endpoint epoch, source agent, bounded payload и correlated request/ack identity.

## Conformance

Product Rust parser и Draft 2020-12 validator выполняют один общий accepted/rejected corpus. Kotlin app, native contract и installer parsers должны копировать corpus как immutable fixtures или получать точный release artifact; относительные source dependencies запрещены. Различие parser results блокирует cutover.

## Связанные страницы

- [Архитектура](architecture.ru.md)
- [Безопасность](security.ru.md)
- [Файлы](storage.ru.md)
- [Тестирование](testing.ru.md)

## Текущее состояние подключения

Демон публикует для каждой записи внедрения необязательный `currentEndpoint`: PID и время старта процесса, эпоху подключения, зарегистрированные generation/catalog и желаемую ревизию документа. Целочисленные идентификаторы передаются точными десятичными строками. Необязательный `acceptedStatus` содержит ревизию действительно принятого S и корневую квитанцию: номер записи, PID и время старта демона, идентификатор загрузки. Отсутствие этого поля означает, что подтверждение недоступно; желаемое состояние не является подтверждением. Замена подключения, в том числе при прежнем PID, отменяет старое S. При пассивной смене поколения зарегистрированное поколение процесса может отличаться от активного поколения в корне статуса.

Корневые метаданные L содержат ту же идентичность запуска демона и `desiredDocumentGeneration`. Для сопоставления нужны текущее принятое S до и после свежего наблюдения, независимо полученная идентичность процесса и демона и совпадающий контекст L. Исторические записи очереди и текст сообщения агента не заменяют отсутствующее подтверждение. Вспомогательная функция проверяет сопоставление; за порядок получения свежих наблюдений отвечает вызывающий код.

Для статуса резидентного демона действует отдельный предел чтения 1 МиБ. Превышение размера и оборванный JSON отклоняются; идентификаторы подключения не удаляются ради уменьшения документа. Расчёт beta1 охватывает все 52 конечных назначения проверенного каталога с максимальными details и квитанциями; выпускная проверка сопоставляет фактически собранный каталог. Предел загрузочного документа остаётся 16 КиБ. Тесты парсера и сериализатора подтверждают эти границы на хосте, но не работу Android или автомобиля.
