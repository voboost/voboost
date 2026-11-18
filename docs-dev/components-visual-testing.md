# Component visual testing

**English** | [Русский](components-visual-testing.ru.md)

Keep tests and image fixtures in the owning components repository. BEM component tests stay beside their Java sources; demo tests and screenshots stay in their demo trees. Hardware `_1original.png` files are immutable evidence. Do not regenerate them from the implementation or replace them with host output.

After a rendering or visual change, run from `voboost-components`:

```sh
./gradlew :demo-pixel:demoPixelVerify
```

Inspect every generated `.txt` report, including when Gradle succeeds. Any lower match than the reviewed `expected-diff.json` baseline is a regression; there is no universal 95% acceptance threshold. Review the actual and difference images. Current crop regions and per-channel tolerance come from the test implementation (`DemoRenderUtils` and each named test), not a universal screenshot rule.

| File | Meaning |
| --- | --- |
| `*_1original.png` | retained physical reference; never edit |
| `*_2actual.png` | current host render |
| `*_3diff.png` | comparison image |
| `*_4magenta.png` | magenta difference channel |
| `*.txt` | observed match and comparison report |
| `expected-diff.json` | reviewed regression baseline |

Change a baseline only for an intentional, evidenced rendering change after reviewing its reports and images. Automatic first-run baseline creation does not approve a new result. A focused release test with `--rerun-tasks --tests <exact-class>` diagnoses the selected case and does not replace `demoPixelVerify`.

Use only the owned cleanup task for generated pixel images:

```sh
./gradlew :demo-pixel:demoPixelClearRoborazzi
```

It removes generated `_2actual`, `_3diff`, `_4magenta` images and preserves originals. Do not use a broad directory cleanup. Component screenshot folder names describe local test fixtures, not permission to regenerate hardware evidence. Source, fixture rendering and vehicle acceptance are separate evidence boundaries. See [testing](testing.md) and [pixel demo](components-demo-pixel.md).
