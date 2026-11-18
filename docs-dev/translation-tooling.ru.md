# Инструменты переводов

[English](translation-tooling.md) | **Русский**

Генерация переводов, выбор ресурсов, валидация и интеграция со сборкой используют Java и существующую Gradle-сборку agents. Инструменты находятся в [`tools/java/ru/voboost/i18n`](../../voboost-agents/tools/java/ru/voboost/i18n), тесты — в [`tools/java-test/ru/voboost/i18n`](../../voboost-agents/tools/java-test/ru/voboost/i18n). Их source sets для Java 17 используют XML/ZIP API из JDK и существующую зависимость `org.json`; они не попадают в Android DEX или ресурсный APK.

Из `voboost-agents`, с настроенными SDK и JDK:

```sh
./gradlew --offline testI18nResources
./gradlew --offline buildI18nResources
```

`buildI18nResources` выполняется, когда выбран `i18n-ru`. Явные входы объявляют A9/A11-профили для `setting`, `launcher`, `vehiclesetting`, `settingnetwork`, `systemui`, `bluetoothphone` и `media`. Каждый профиль в [`i18n/ru`](../../voboost-agents/src/i18n/ru) содержит `strings.xml`, `english.xml`, `AndroidManifest.xml` и `completion-ledger.json`. Сборка сверяет точный набор и количество ресурсов, readback, контракт совместимости, набор английских ключей и mutable overlay без кода с пакетом из ledger.

Результат в `build/i18n-resources/` — неподписанные `resource-i18n-ru-<profile>.apk`, парные JSON ledger и `resource-artifacts.json`. Каждый RU APK содержит только `values-ru`, включая подготовленный английский fallback для непереведённых русских строк. Полный `english.xml` остаётся входом подготовки/fallback и не устанавливается как собственная английская локализация. Парный ledger проверяет фактический English по неизменённым штатным английским строкам. Подлинность обеспечивает подпись product closure; генерация не подписывает ресурсы и не подтверждает runtime-допуск.

`buildI18nEnglishResources` использует ограниченные снимки `english-native.xml` и `english-delta-ledger.json`, выбирая только подготовленные исправления тех строк штатного English, где остаётся Han. Разрешаются локальные ссылки и default/English fallback; внешние ссылки и непроверенная зависимость от qualifiers исключаются. Компиляция/readback сохраняет штатный английский текст строк и английские элементы смешанных текстовых массивов и plurals one/other. Шесть непустых профилей — Setting A11, Launcher A9/A11, SystemUI A9 и Media A9/A11 (102 ресурса). Нулевые профили не получают фиктивный overlay. Каждый результат имеет отдельную EN identity и точные типизированные ключи штатного/исправленного readback. Общий schema2 index объявляет четырнадцать RU и шесть EN пар. Штатный baseline проверяется после отключения root-частью собственных EN overlays; исправления из кеша не считаются штатным доказательством. Совместимость определяется фактическим owner-local Resources probe без APK/firmware fingerprints.

Генератор поддерживает styled mixed XML, строки, generic/string arrays и plurals. Он сохраняет пробелы смешанного текста и наследование namespace, отключает внешние XML entities, проверяет позиционные форматы, размер массивов, plural quantities, замкнутость ссылок и английский fallback. Для fallback нужен явный английский вход. Малого admission-подмножества недостаточно для полного owner; исторический `src/i18n/ru/settings-a9.xml` — только такой probe.

Совместимость проверяется по конкретным методам, типам ресурсов, индексированным форматам и фактическому readback. Digest APK или прошивки, включая значения ledger, не должен выбирать совместимость. Сохранённый source provenance — историческое доказательство, а не allowlist.

Ресурсы QNX генерируются отдельно; замените путь фактическим manifest входов:

```sh
./gradlew --offline buildI18nQnxResources -PvoboostQnxLocaleInputs=path/to/manifest.json
```

Manifest указывает фактические каталоги штатных ресурсов либо QNX image и выбранные bundles; его разбирает [KzbProductionBuild](../../voboost-agents/tools/java/ru/voboost/i18n/kzb/KzbProductionBuild.java). Задача пишет пары pack/ledger и `qnx-resource-artifacts.json` в `build/i18n-qnx-resources/`; она не подписывает, не размещает и не активирует их.

Host-сборка не подтверждает Android overlay admission, чтение в обычных/skin contexts, cleanup, инвалидацию после restart, ранний startup cleanup или QNX ACK. В Android ledger остаются `admission=unverified` и `runtimeApplied=false`. См. [локальную документацию агента](../../voboost-agents/src/main/java/ru/voboost/agents/i18n/ru/I18nRuAgent.ru.md), [разработку агентов](agent-development.ru.md) и [проверки](testing.ru.md).
