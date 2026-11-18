# Файлы на машине

[English](storage.md) | **Русский**

Целевой постоянный корень — `/data/voboost`. Android-приложение хранит downloads и resume state только в собственном sandbox; OEM target processes не читают product root.

## Постоянный корень

```text
/data/voboost/
├── root.json
├── operation.lock
├── operation.json
├── voboost.json
├── voboost.json.sig
├── voboost-daemon
├── voboost-install
├── voboost-restore
├── voboost-uninstall
├── voboost-selinux
├── voboost.rc
├── restore.json
├── restore.json.sig
├── libimplant-arm64-v8a.so
├── libimplant-armeabi-v7a.so
├── liblsplant-arm64-v8a.so
├── liblsplant-armeabi-v7a.so
├── vehicle.json
├── agents-enabled.json
├── status.json
├── generations/
├── state/
├── logs/
├── run/
├── install/
├── recovery/
└── uninstall/
```

Имена артефактов плоские и не содержат версию. Каталоги используются только для времени жизни transaction, generation, state, logs, runtime и recovery.

## Владение

Постоянные файлы принадлежат root и имеют права по роли. Приложение не записывает постоянные файлы. Узко подготовленный incoming candidate остаётся недоверенным, пока демон не откроет его без перехода по ссылкам, не проверит ownership/type/link count/size/hash и не опубликует root-owned bytes.

Каждый durable document разбирается с ограничениями и публикуется через временный соседний файл, file `fsync`, atomic rename и parent-directory `fsync`. Operation и generation publication используют общий `operation.lock`; активная install/update/restore/uninstall исключает другую root mutation.

## Поколения

Generation содержит `agents.json`, signature, `agents-selected.json`, `agent-common.dex` и все target DEX из подписанного каталога. Применимость влияет только на `agents-selected.json` и загрузку в процессы. Указателями `current` и `previous` владеет демон. Candidate и quarantine не становятся активными без полной проверки.

## Recovery authority

`restore.json` не является вторым product manifest и не переиспользуется как чистый inject artifact. Он связывает release identity, SHA-256 точных байтов source definition и canonical digest всех restorable artifact rows, кроме restore pair; исключение самого документа и signature убирает цикл хеша. Raw Ed25519 signature, фиксированные recovery programs, installed inventory и init-owned services разрешают восстановление одного точного installed product state. Если эти authorities не проходят проверку, состояние называется `unrecoverable`.

## Связанные страницы

- [Установка](install.ru.md)
- [Обновления](updates.ru.md)
- [Восстановление](restore.ru.md)
- [Удаление](uninstall.ru.md)
- [Безопасность](security.ru.md)
