# Визуальная проверка компонентов

[English](components-visual-testing.md) | **Русский**

Тесты и image fixtures остаются в репозитории компонентов. BEM-тесты компонентов находятся рядом с Java-исходниками; demo tests и screenshots — в своих demo trees. Hardware `_1original.png` — неизменяемое доказательство. Их нельзя генерировать из реализации или заменять host-результатом.

После изменения rendering или внешнего вида выполните из `voboost-components`:

```sh
./gradlew :demo-pixel:demoPixelVerify
```

Просмотрите каждый созданный `.txt`, в том числе при успешном Gradle. Любое снижение совпадения относительно проверенного `expected-diff.json` — регрессия; общего порога приёмки 95% нет. Просмотрите actual и diff. Текущие crop regions и per-channel tolerance определяют тесты (`DemoRenderUtils` и конкретный named test), а не универсальное правило скриншотов.

| Файл | Значение |
| --- | --- |
| `*_1original.png` | сохранённый физический reference; не редактировать |
| `*_2actual.png` | текущий host render |
| `*_3diff.png` | изображение сравнения |
| `*_4magenta.png` | magenta-канал различий |
| `*.txt` | наблюдаемое совпадение и отчёт |
| `expected-diff.json` | проверенный baseline регрессий |

Baseline меняют только при намеренном, подтверждённом изменении rendering после просмотра reports и изображений. Автоматическое создание baseline при первом запуске не утверждает результат. Узкий release-тест с `--rerun-tasks --tests <exact-class>` диагностирует выбранный случай и не заменяет `demoPixelVerify`.

Для удаления генерируемых pixel images используйте только задачу владельца:

```sh
./gradlew :demo-pixel:demoPixelClearRoborazzi
```

Она удаляет `_2actual`, `_3diff`, `_4magenta` и сохраняет originals. Общая очистка каталога недопустима. Имена screenshot-каталогов описывают локальные fixtures и не разрешают пересоздание hardware evidence. Исходники, fixture rendering и автомобильная приёмка — разные границы доказательств. См. [проверки](testing.ru.md) и [pixel demo](components-demo-pixel.ru.md).
