# Ресурсы ADB установщика

[English](installer-adb-resources.md) | **Русский**

Исходные ресурсы ADB находятся в [`src-tauri/resources/adb`](../../voboost-installer/src-tauri/resources/adb). Сохраняйте рядом с оригинальными бинарниками `ADB-LICENSE.txt` — точное уведомление Android Platform Tools 35.0.1 (`r35.0.1-11580240`) об условиях распространения. Legal notices и identity исходников остаются входами сборки.

Текущий [staging](../../voboost-installer/src/build/lib-resources.ts) копирует только Windows trio `adb.exe`, `AdbWinApi.dll`, `AdbWinUsbApi.dll` в `resources-staged/win`. `adb-resources-v1.txt` содержит платформу, точный набор файлов, размеры и digest; Rust `build.rs` проверяет манифест повторно. Генерируемым staging владеет `src/build/build-resources.ts`; вручную его не редактируют. Windows runtime удерживает проверенную identity executable/DLL до создания процесса. UPX не применяется.

Обычный staging macOS не содержит внешнего ADB: приложение связывает [прямую реализацию ADB](installer-native-adb.ru.md). Linux тоже не включает ADB; backend использует только `/usr/bin/adb`, `/usr/local/bin/adb`, `/opt/android-platform-tools/adb`, проверяя владельца, пути, файл и право выполнения. Изменяемый `PATH` не выбирает ADB. Установите пакет дистрибутива `android-tools-adb`/`android-tools` и необходимые udev rules.

## Происхождение сохранённого macOS-входа

Сохранённый universal `mac/adb` содержит arm64 и x86_64, но не является текущим обычным transport macOS. Identity байтов с ad-hoc-подписью — `e50256fbd434d08f510d467ec36f6bdce4224d3b8fd82fe0e8dd73f4da33edcc`. Исходный предоставленный файл `92105d0c0f006a6fbdd8a91e82b791d23e4746491062b62d5ecc34abecf9086b` сохранял upstream `Identifier=adb`, `TeamIdentifier=EQHXZ8M8AV`, но arm64-подпись была недействительна.

При явной подготовке исходников подпись исправили, затем Apple `strip -N` и детерминированная ad-hoc-подпись уменьшили 13 594 432 байта до 12 122 544. Loaded sections, dyld metadata, exports и обе архитектуры сохранены; дублирующие nlist symbols/strings удалены. Сохранённые сведения о подготовке фиксируют одинаковые `version`/`help` для arm64 и x86_64 через трансляцию, strict signatures и воспроизводимую identity после подписи. Это provenance, а не физическая USB-приёмка и не описание обычной упаковки native backend.

Проверка сохранённого входа из `voboost-installer`:

```sh
./scripts/verify-macos-adb-source.sh
```

## Намеренное обновление входов

Получите нужную версию Android Platform Tools и её notice, проверьте identity и соответствующие тесты, затем обновите только принадлежащие задаче входы. Сохраняйте Windows-бинарники вместе. Нельзя выполнять strip, объединение, подпись или исправление установленного bundle во время работы. Изменение подписи относится к явной подготовке исходников до фиксации identity; обычный staging копирует проверенные Windows-байты. Повторите platform source checks и packaging checks затронутых целей. Release signing и физическая проверка транспорта — отдельные gates.

См. [сборку установщика](installer-build.ru.md), [установщик](installer.ru.md) и [тест macOS provenance](../../voboost-installer/tests/macos-adb-source.test.ts).
