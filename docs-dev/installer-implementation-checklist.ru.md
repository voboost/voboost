# Чек-лист реализации

[English](installer-implementation-checklist.md) | **Русский**

## Сначала контракт

- Измените постоянную спецификацию или активный delta до изменения поведения
- Для каждого Scenario укажите точный именованный Gate либо полный `Unverified` с Maps, причиной и способом закрытия
- Не выдавайте build, lint, suite или source scan за поведенческий Gate

## Безопасность и поток данных

- Проверяйте подпись, canonical bytes, size, SHA-256, role и замкнутый inventory до использования
- Не принимайте от GUI команды, device paths, phases и аргументы привилегированных программ
- Удерживайте per-device lock на протяжении операции; временные файлы публикуйте атомарно и восстанавливайте fail-closed
- Не записывайте secrets, tokens, license content или лишние персональные данные в logs

## Frontend

- Состояния loading/error/empty и отмена операции должны быть явными
- Все пользовательские строки должны иметь EN/RU варианты и сохранять плейсхолдеры
- UI не должен заявлять успех раньше terminal verification

## Тесты и документация

- Добавьте точечные regression tests для нормального и hostile paths
- Синхронизируйте EN/RU docs, glossaries, OpenSpec и фактические команды
- Не изменяйте архитектурную документацию или планы механически ради прохождения parity

## Локальные gates

Запустите formatting, lint, TypeScript, Vitest, Rust fmt/clippy/tests, build, strict OpenSpec, documentation/parity/forbidden checks и `git diff --check`. Фиксируйте точные результаты и границы, которые они не доказывают.

## Evidence поставки

Отдельно фиксируйте source/host, CI/signing, published bytes, packaged GUI, device/reboot/SELinux и vehicle evidence. Один слой не заменяет другой.
