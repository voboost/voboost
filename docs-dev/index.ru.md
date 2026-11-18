# Документация разработчика

[English](index.md) | **Русский**

Документация описывает собранную архитектуру Voboost, владельцев репозиториев, runtime-контракты, выпуск и границы проверок.

## Первое знакомство

Перед техническими страницами прочитайте [описание Voboost](../docs-user/index.ru.md), [использование](../docs-user/usage.ru.md), [обновления](../docs-user/updates.ru.md), [восстановление](../docs-user/restore.ru.md) и [удаление](../docs-user/uninstall.ru.md). Эти страницы задают наблюдаемое поведение, которое реализация не должна менять незаметно для пользователя.

1. [Архитектура](architecture.ru.md) — компоненты, владельцы и основные потоки
2. [Репозитории и исходники](repository.ru.md) — границы product repositories и путь изменения
3. [Контракты](contracts.ru.md) — схемы, wire protocols, версии и ограничения
4. [Приложение](application.ru.md) — `ru.voboost`, APK OTA, staging и запрос удаления
5. [Native runtime](native.ru.md) — daemon, injector, implant, LSPlant, ABI и очистка
6. [Среда агентов](agents-runtime.ru.md) — каталог, выбор, поколение, bootstrap и runtime state
7. [Безопасность](security.ru.md) — ключи, подписанные роли, допуск процессов, SELinux и файловые проверки
8. [Словарь](../GLOSSARY.ru.md) — общие пользовательские и инженерные термины

## Работа над компонентом

| Компонент | Основная страница | Смежные контракты | Обязательные gates |
| --- | --- | --- | --- |
| Android-приложение | [Приложение](application.ru.md) | [Контракты](contracts.ru.md), [Файлы](storage.ru.md), [Логи](logs.ru.md) | [Тестирование](testing.ru.md) |
| Агент | [Разработка агента](agent-development.ru.md) | [Среда агентов](agents-runtime.ru.md), [Статус агентов](agents-status.ru.md) | метаданные, контракт, жизненный цикл и очистка |
| Вкладка Voboost | [Агент `settings`](agent-settings.ru.md) | [Контракты](contracts.ru.md), [Удаление](uninstall.ru.md) | представление состояния, ABI ресурсов, жизненный цикл интерфейса и запрос удаления |
| Native runtime | [Native runtime](native.ru.md) | [Контракты](contracts.ru.md), [Безопасность](security.ru.md) | допуск, откат `ptrace`, ABI, время жизни FD и JNI, очистка |
| Системная операция | [Установка](install.ru.md), [Обновления](updates.ru.md), [Восстановление](restore.ru.md), [Удаление](uninstall.ru.md) | [Файлы](storage.ru.md), [Безопасность](security.ru.md) | точки прерывания, идемпотентность, откат и конечный результат |
| Установщик | [`voboost-installer`](installer.ru.md) | [Контракты](contracts.ru.md), [Выпуск](release.ru.md) | равенство GUI и CLI, фиксированные команды, переподключение и проверка артефактов |
| Выпуск и CI | [Выпуск](release.ru.md), [CI](ci.ru.md) | [Сборка](build.ru.md), [Безопасность](security.ru.md) | готовность, подпись, происхождение, продвижение и отсутствие пересборки |

## Добавить или изменить агента

1. Зафиксируйте пользовательское поведение в OpenSpec и добавьте gate
2. Опишите метаданные, применимость, целевые процессы, контракты и артефакты по [руководству по разработке агента](agent-development.ru.md)
3. Реализуйте агент, его жизненный цикл и очистку; CI должен работать только с зафиксированными контрактами, stubs и тестами
4. Обновите [статус агентов](agents-status.ru.md) из metadata и применённого OpenSpec
5. Выполните проверки на компьютере, эмуляторе, устройстве и в автомобиле, не подменяя один уровень другим

Текущий инвентарь исходников отражён в [статусе агентов](agents-status.ru.md); классификация metadata не доказывает runtime-приёмку. Ограничения исторического снимка описаны в [состоянии beta0](beta0-state.ru.md).

## Изменить межкомпонентный протокол

1. Найдите владельца в [реестре контрактов](contracts.ru.md)
2. Измените принадлежащую владельцу схему или определение кадра
3. Примените OpenSpec с требованиями совместимости и отрицательными сценариями
4. Обновите producer и все consumers
5. Добавьте корректные и некорректные контрольные примеры, проверки пределов и несовпадения версий
6. Пересоберите артефакты, не копируя сгенерированные файлы между репозиториями вручную

## Источники истины

| Вопрос | Источник |
| --- | --- |
| Наблюдаемое пользовательское поведение | страницы в `docs-user` |
| Обязательный контракт | принятый OpenSpec |
| Поля и пределы формата | схема и типизированный разбор у владельца |
| Исполняемое поведение | исходники и генераторы в репозитории-владельце компонента |
| Доказательство | именованная проверка с точной средой и артефактом |
| Текущий состав агентов | metadata и применённый OpenSpec, опубликованные в `agents-status` |

## Основные команды

```text
(cd ../voboost-app && ./gradlew check)
(cd ../voboost-agents && ./gradlew check)

(cd ../voboost-inject && make lint && make test)

(cd ../voboost-installer && npm test -- --run)

cargo test --manifest-path release/Cargo.toml
node tools/check-target-docs.ts
```

Команда подтверждает только заявленный ею уровень. Полный перечень сред и границ доказательств приведён в [тестировании](testing.ru.md).

## Общие руководства компонентов

Общие руководства, процедуры и примеры находятся здесь. Документы каждого агента остаются рядом с его Agent.json и кодом; переходите к ним через [статус агентов](agents-status.ru.md). Правила инструментов, OpenSpec, лицензии, provenance и fixture evidence сохраняются у своих владельцев.

- [Voboost Components](components.ru.md)
- [Архитектурные решения компонентов](components-architecture-decisions.ru.md)
- [Визуальная проверка компонентов](components-visual-testing.ru.md)
- [Java demo компонентов](components-demo-java.ru.md)
- [Kotlin demo компонентов](components-demo-kotlin.ru.md)
- [Compose demo компонентов](components-demo-compose.ru.md)
- [Pixel demo компонентов](components-demo-pixel.ru.md)
- [Инструменты переводов](translation-tooling.ru.md)
- [Исходные данные для отступов компонентов](component-spacing.ru.md)
- [Клиентский probe лаунчера](launcher-client-probe.ru.md)
- [Native runtime шрифтов](cluster-font-runtime.ru.md)
- [PW visual observer и маска приборной панели](cluster-pw-visual-observer.ru.md)
- [Native-зависимости](native-dependencies.ru.md)
- [QNX link inputs](cluster-link-inputs.ru.md)
- [Темы и стили установщика](installer-themes.ru.md)
- [Интернационализация (i18n)](installer-i18n.ru.md)
- [Сборка и распространение](installer-build.ru.md)
- [Чек-лист реализации](installer-implementation-checklist.ru.md)
- [Обработка ошибок](installer-error-handling.ru.md)
- [Конфигурация Tauri](installer-tauri-config.ru.md)
- [Диагностика проблем](installer-troubleshooting.ru.md)
- [Межрепозиторный контракт выпуска](installer-cross-repository-release-contract.ru.md)
- [Приёмка нативного GUI](installer-gui-acceptance.ru.md)
- [Минимальная приёмка GUI beta1](installer-gui-test-cases.ru.md)
- [Прямой ADB для macOS](installer-native-adb.ru.md)
- [Ресурсы ADB установщика](installer-adb-resources.ru.md)
- [Защищённое сопряжение приборки](installer-cluster-pairing.ru.md)
- [Пересобрать и установить изменённый Voboost](build-modified.ru.md)
- [Voboost Code Style](codestyle-integration.ru.md)

- [Журнал резервирования отправки Voyemu](voyemu-journal.ru.md) — отдельный компонент хоста и его ограничения
