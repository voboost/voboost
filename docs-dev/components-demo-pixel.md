# Component pixel demo

**English** | [Русский](components-demo-pixel.ru.md)

[demo-pixel](../../voboost-components/src/demo-pixel) renders components into a 1920×720 bitmap and compares against hardware screenshots. `PixelComparator` produces differences and compares matching-pixel percentage with `expected-diff.json`; a decrease from baseline is a regression. A missing baseline can be saved on first run, so a green test does not replace visual inspection.

Run from `voboost-components`:

```sh
./gradlew :demo-pixel:demoPixelVerify
```

A focused `:demo-pixel:testReleaseUnitTest --rerun-tasks --tests "ru.voboost.components.demo.pixel.<TestClassName>"` run diagnoses a case and does not replace full verification. The [visual procedure](components-visual-testing.md) covers reports, immutable originals, baseline and cleanup.

Build with `./gradlew :demo-pixel:assembleRelease`; authorized device installation is a separate `:demo-pixel:installRelease` action. A host bitmap does not establish vehicle runtime behavior.
