# Конфигурация Tauri

[English](installer-tauri-config.md) | **Русский**

## Источник истины

Каноническая конфигурация приложения находится в `src-tauri/tauri.conf.json`, permissions — в `src-tauri/permissions/app.toml`, capabilities — в `src-tauri/capabilities/default.json`. Generated schemas не редактируются вручную. Изменение разрешений должно быть минимальным и сопровождаться тестом фактически используемой команды.

## Жизненный цикл окна GUI

Installer использует одно основное окно с фиксированной последовательностью экранов. Закрытие окна во время операции инициирует контролируемую отмену, но не считается terminal device result. Повторный запуск обязан заново установить доверенное состояние, а не продолжать UI-фазу из памяти.

## Включение ресурсов

В bundle входят только ресурсы, перечисленные build pipeline: platform ADB, локализованный content и проверяемые release inputs. Staging проверяет существование, тип, размер и digest; случайные файлы рабочего дерева не должны попадать в пакет.

## Инструменты TypeScript-сборки

Build scripts находятся в `src/build`, используют общие helpers и запускаются через scripts `package.json`. Они должны работать из clean checkout, использовать canonical paths и не зависеть от глобально установленных пакетов.

## Локальная и release-подпись

Локальная разработка использует только явно разрешённые development roots. Release keys доступны раздельным protected environments и не передаются в frontend или publisher. Успешная локальная подпись не является доказательством production signing.

## Rust toolchain

Rust dependencies закреплены `Cargo.lock`; обязательны `cargo fmt`, clippy с запретом warnings и tests. Platform-specific код должен иметь честные тесты или `Unverified`, не обещая неподдерживаемую subprocess-tree семантику.
