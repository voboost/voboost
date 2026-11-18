# Исходные данные для отступов компонентов

[English](component-spacing.md) | **Русский**

Accessor отступов Settings генерируется из проверенных снимков исходников компонентов в [`tools/component-spacing`](../../voboost-agents/tools/component-spacing). Это входы сборки; их View не компилируются и не загружаются в Settings. `provenance.json` фиксирует исходные пути и identity снимков.

[Java-генератор](../../voboost-agents/tools/java/ru/voboost/spacing/ComponentSpacingGenerator.java) проверяет фиксированный набор, извлекает точные объявления и ветку checkbox с описанием и без него, отклоняет отсутствующие, неоднозначные или изменённые входы. Обычная генерация не требует соседнего checkout компонентов.

Из `voboost-agents` выполните:

```sh
./gradlew --offline generateComponentSpacing verifyComponentSpacing
./gradlew --offline test --tests ru.voboost.ui.qinggan.ComponentSpacingProvenanceTest
./gradlew --offline :stubs:settings:testDebugUnitTest --tests ru.voboost.ui.qinggan.SectionSpacingTest
```

`compileJava` запускает генератор перед компиляцией. Результат — `build/generated/sources/componentSpacing/ru/voboost/ui/qinggan/ComponentSpacing.java`; вручную его не редактируют. Проверки охватывают извлечение значений, байты результата и измерения layout в fixture. Host evidence не заменяет живые скриншоты и физическую приёмку. Сохраняйте hardware evidence `_1original.png`.

При намеренном изменении значений сначала сравните diff снимков и provenance с фактическими исходниками компонентов. Java producer поддерживает `--verify-source <components>` и `--refresh <components>` вместе с `--project <agents>`; текущая Gradle task предоставляет только генерацию, без поддерживаемой refresh-обёртки.

Снимки сохраняют вложенные [лицензию Apache 2.0](../../voboost-agents/tools/component-spacing/LICENSE) и [NOTICE](../../voboost-agents/tools/component-spacing/NOTICE); они не заменяют лицензию agents. См. [компоненты](components.ru.md), [разработку агентов](agent-development.ru.md) и [проверки](testing.ru.md).
