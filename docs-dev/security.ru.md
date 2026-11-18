# Модель безопасности

[English](security.md) | **Русский**

Voboost не доверяет сети, release hosting, candidate от приложения, WebView input, OEM processes и произвольным device paths. Доверие дают только фиксированные встроенные roots, точные подписанные байты, закрытые schemas и повторная проверка на границах привилегий.

## Подписанные identity

| Identity | Защищает | Consumers |
| --- | --- | --- |
| Android APK signature | package `ru.voboost`, source-controlled `versionCode`, сертификат signer | PackageManager, installer, daemon APK verifier |
| Product release role | точные байты `voboost.json` и полный набор identity артефактов | installer, app, daemon/system programs |
| Channel role | точный mutable pointer `channel.json` | app |
| Agent catalog role | точную семантику `agents.json` и identity DEX | release verifier, daemon, implant boundary |
| Restore role | точную product-derived authority `restore.json` | init-owned recovery programs |
| Native executable role | точные финальные байты и runtime role привилегированных executable | install/update/recovery verifiers |

Private keys находятся только в protected signing environments. Trust roots встраиваются в разрешённых consumers и не загружаются из release, channel pointer, app storage и `/data/voboost`.

## Уровни проверки

Приложение отклоняет неверный channel или product manifest до загрузки. Перед root mutation демон повторно проверяет product signature, schema, compatibility, inventory, size/SHA-256, catalog и candidate bytes. Проверка в менее привилегированном процессе не даёт полномочий более привилегированному consumer.

Product authoring извлекает APK identity из точных подписанных байтов через pinned `aapt2` и `apksigner`, требует `versionCode` в диапазоне `1..=2100000000` и его совпадения с `android.versionCode` из source definition, а затем связывает наблюдённые package/version/certificate с size/SHA-256 того же файла и app commit. Evidence JSON не является authority и не может переопределить свежее извлечение.

Secretless final verification также разбирает exact signed APK как bounded ZIP, требует ровно один `assets/config/release-public.pem`, извлекает из canonical Ed25519 SPKI raw key и сравнивает его с source-pinned `productPublicKeyHex`. Валидная APK signature не разрешает missing, duplicate, malformed или substituted embedded product root.

Product authoring также проверяет canonical `restore.json` и raw Ed25519 signature под настроенным restore-role public key. Документ связывает Product SemVer, SHA-256 точных байтов source definition и canonical digest всех restorable artifact rows, кроме самой restore pair, поэтому циклического хеша нет.

Применимость агентов вычисляет только демон по проверенной семантике каталога и root-owned сведениям о машине. OEM target processes и агенты не получают общего доступа к root filesystem. Каждый агент ограничен catalog ID, target, capability, владельцем state и typed pipes.

## Файловые границы и процессы

Root paths фиксированы или выводятся из проверенных transaction/generation identity. Symlink, hard link, special file, неожиданный child, неверный owner/mode и изменение identity во время чтения отклоняются. Publication использует durable atomic replacement.

Для injection нужны стабильные PID/start time, точное имя процесса, UID, SELinux context, executable и ABI. Injector повторяет admission после остановки thread group и восстанавливает всё временное состояние ptrace при отказе.

## Граница доказательств

Host tests покрывают product parser/schema, связь APK evidence, native contract и role-signature fixtures, отрицательные process-admission cases и компиляцию SELinux policy. Cross-consumer product signatures, protected keys, authenticity упакованного installer, physical device, runtime SELinux Enforcing и автомобиль остаются отдельными gates.

## Связанные страницы

- [Архитектура](architecture.ru.md)
- [Контракты](contracts.ru.md)
- [Файлы](storage.ru.md)
- [Выпуск](release.ru.md)
- [Тестирование](testing.ru.md)

При проверке политики после перезагрузки правила доступа и переходы по имени файла сравниваются как полные наборы, независимо от порядка сериализации. После выравнивания этих проверенных наборов все остальные сериализованные данные политики должны совпасть. Изменение прав, назначения переходов, типов и прочего содержимого остаётся ошибкой; проверки обязательных правил и контекстов по-прежнему выполняются на активной политике.
