# Pixel demo компонентов

[English](components-demo-pixel.md) | **Русский**

[demo-pixel](../../voboost-components/src/demo-pixel) рендерит компоненты на bitmap 1920×720 и сравнивает с hardware screenshots. `PixelComparator` формирует diff и сравнивает долю совпавших пикселей с `expected-diff.json`; снижение относительно baseline — регрессия. Отсутствующий baseline может быть записан при первом запуске, поэтому зелёный тест не заменяет просмотр результата.

Из `voboost-components` выполните:

```sh
./gradlew :demo-pixel:demoPixelVerify
```

Узкий запуск `:demo-pixel:testReleaseUnitTest --rerun-tasks --tests "ru.voboost.components.demo.pixel.<TestClassName>"` нужен для диагностики и не заменяет полную проверку. [Визуальная процедура](components-visual-testing.ru.md) описывает reports, immutable originals, baseline и очистку.

Сборка demo — `./gradlew :demo-pixel:assembleRelease`; установка на разрешённое устройство — отдельная `:demo-pixel:installRelease`. Host bitmap не доказывает автомобильный runtime.
