# Первичная установка

[English](install.md) | **Русский**

Desktop installer работает offline. Он содержит один подписанный product closure и не выбирает, не скачивает и не собирает другой выпуск.

## Перед передачей комплекта

`--install` сначала проверяет, можно ли продолжить установку того же проверенного комплекта. Для новой чистой установки он запускает или продолжает очистку через [общую операцию удаления](uninstall.ru.md). Установщик передаёт новый комплект в том же вызове только после обязательных проверок Android и QNX. Если объект остался или не хватает доказательств, установщик останавливается до передачи комплекта и сообщает причину. Отдельный Linux CLI останавливается, если для проверки отсутствия на QNX нужен нативный транспорт установщика. Отдельный путь `--update` применяет подписанный комплект к установленному продукту и сохраняет пользовательские настройки.

## Staging с компьютера

Установщик проверяет `voboost.json`, signature, обязательные package/version/certificate identity APK, size/SHA-256 каждого артефакта и точный плоский inventory. Он создаёт одну случайную транзакцию в `/data/voboost/install/<transaction-id>/`, первым записывает identity транзакции, загружает плоские файлы и запускает фиксированный `/data/voboost/voboost-install` без аргументов только после полной подготовки closure.

Компьютер не передаёт shell fragment и произвольный device path. После допуска транзакции device program единственной наблюдаемой authority становится `operation.json`, а desktop только восстанавливает соединение и наблюдает typed phases.

## Последовательность на машине

1. Проверить root, staging authority, подписи продукта и агентов, compatibility, APK identity и точный inventory
2. Захватить `operation.lock`, создать WAL и надёжно опубликовать `operation.json`
3. Сохранить штатное состояние для Restore и Uninstall
4. Установить постоянные native programs, плоские ABI libraries и SELinux policy; определить аппаратные сведения и атомарно сохранить root-owned `vehicle.json` до публикации init definition
5. Установить `voboost-app.apk` через PackageManager и дважды наблюсти package, certificate, `versionCode`, path, inode, size и SHA-256
6. Вычислить применимость агентов по сохранённым аппаратным сведениям и опубликовать первое immutable generation из внешних agent artifacts
7. Запустить `voboost-daemon`, проверить installed identity, generation и обязательные target statuses, а затем последними опубликовать installed inventory и `voboost.json`
8. Удалить transaction data и опубликовать terminal result

Приложение не извлекает агентов после `BOOT_COMPLETED`. `agent-common.dex`, target `agent-*.dex`, `agents.json` и signature поступают из closure установщика и допускаются device-owned root operation.

## Прерывание

До `operation.json` host продолжает только ту же transaction и manifest digest. После `operation.json` desktop restart, ADB reconnect, package replacement и reboot продолжают ту же device-owned operation. Другая операция получает `busy`.

## Проверка

Gate выпуска повторяет эту последовательность из пакетированного установщика и проверяет точный состав stage, продолжение операции, identity PackageManager, состояние SELinux Enforcing, полное immutable generation агентов, активный манифест и итоговый terminal receipt.

## Связанные страницы

- [Установщик](installer.ru.md)
- [Файлы](storage.ru.md)
- [Восстановление](restore.ru.md)
- [Удаление](uninstall.ru.md)
- [Безопасность](security.ru.md)
