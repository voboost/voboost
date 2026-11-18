# Voboost Components

UI component library for Voyah Free and Voyah Dreamer vehicles running
Android 9 and 11. Not compatible with Voyah Passion / Courage or HarmonyOS-based
vehicles. Pixel-perfect components matching the native vehicle settings
appearance, exposed through three layers: Java core + Kotlin factory +
Compose wrapper.

**English** | [Русский](components.ru.md)

## Why

The vehicle's native settings UI is fixed (1920x720, Android 9/11). This
library replicates it exactly so apps feel "at home" in the system. No
responsive design, no accessibility, no state persistence — the screen is
fixed and the fleet is known.

## Architecture

One Java core, three consumer-facing layers:

```
Layer 3 — Compose     Component.compose.kt   @Composable fun Component(...)
Layer 2 — Kotlin      ComponentFactory.kt     fun component(...) { ... }
Layer 1 — Java        Component.java          Component.create(...).build()
```

- **Layer 1 (Java core + Builder)**: rendering, touch, animation, theme,
  localization. The public API for Java apps.
- **Layer 2 (Kotlin factory)**: wraps the Builder so Kotlin apps never call
  `.create()` or `.build()`. Lives inside each component as
  `ComponentFactory.kt`.
- **Layer 3 (Compose wrapper)**: `@Composable` + `AndroidView` bridge for
  reactive Compose apps. Lives as `Component.compose.kt`.

All layers share one Java core; they do not fork it. Each component is
self-contained — there is no shared base implementation or global theme/language store. Thin
shared contracts and stateless utilities (`IThemable`, `ILocalizable`, `Theme`, `Language`,
`Font`, `I18nUtils`) keep cross-component behavior consistent.

See [docs/architecture-decisions.md](components-architecture-decisions.md) for the
rationale behind key architectural choices.

## Components

| Component | Description | Docs |
|---|---|---|
| Screen | Root container with screen lift and panel reconciliation | [Screen.md](component-screen.md) |
| Panel | Content container within the screen | [Panel.md](component-panel.md) |
| Tabs | Tab navigation with selection indicator animation | [Tabs.md](component-tabs.md) |
| Section | Collapsible content section with title and popup | [Section.md](component-section.md) |
| Radio | Radio button group with horizontal selection | [Radio.md](component-radio.md) |
| Button | Button with bold text, padding, description | [Button.md](component-button.md) |
| Buttons | Button group with title and per-button config | [Buttons.md](component-buttons.md) |
| Checkbox | Checkbox with label and animated knob | [Checkbox.md](component-checkbox.md) |
| Select | Wheel-based select with popup | [Select.md](component-select.md) |
| Text | Text rendering with role-based weight and color | [Text.md](component-text.md) |
| Hint | Hint text block | [Hint.md](component-hint.md) |
| Dialog | Modal dialog with title, message, buttons | [Dialog.md](component-dialog.md) |
| Popup | Info popup with title and body text | [Popup.md](component-popup.md) |
| Toast | Toast notification with duration and close button | [Toast.md](component-toast.md) |
| Font | Typeface loader (Regular, Bold ASCII, Bold Unicode) | [Font.md](component-font.md) |
| Theme | Theme enum (Free/Dreamer x Light/Dark) | [Theme.md](component-theme.md) |
| Language | Language enum (EN/RU) and localization utils | [Language.md](component-language.md) |

## Quick Start

### Prerequisites

- Build JDK 17 (source/bytecode target is Java 11)
- Android SDK API 28+ (compile SDK 34)
- Gradle 8.2 (via `./gradlew` wrapper)

### Build

```bash
./gradlew build          # Assemble, lint, test (release-only)
./gradlew format         # Auto-fix style (ktlint + Spotless)
./gradlew validate       # Tests + ktlint + Spotless gate
```

### Use in your app

```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":voboost-components"))
}
```

Layer 1 (Java):
```java
Button.create(context, theme, language, "OK", ButtonStyle.SECONDARY)
        .boldText(true)
        .build();
```

Layer 2 (Kotlin factory):
```kotlin
val panel = panel(context, Theme.FREE_DARK, Language.EN) {
    compact(true)
    addSection(section)
}
```

Layer 3 (Compose):
```kotlin
Button(text = "OK", theme = Theme.FREE_LIGHT, language = Language.EN, boldText = true)
```

## Demos

| Demo | Language | Why it exists |
|---|---|---|
| [demo-java](../../voboost-components/src/demo-java) | Java | Shows Layer 1 Builder API for Java apps |
| [demo-kotlin](../../voboost-components/src/demo-kotlin) | Kotlin | Shows Layer 1/2 from Kotlin (non-Compose) |
| [demo-compose](../../voboost-components/src/demo-compose) | Kotlin | Shows Layer 3 Compose wrappers, keyed reconciler |
| [demo-pixel](../../voboost-components/src/demo-pixel) | Java | Pixel-perfect regression tests against vehicle hardware screenshots |
| [demo-java-cunba](../../voboost-components/src/demo-java-cunba) | Java | CunBA 3 activation flow (Java integration) |
| [demo-kotlin-cunba](../../voboost-components/src/demo-kotlin-cunba) | Kotlin | CunBA 3 activation flow (Kotlin integration) |
| [demo-shared](../../voboost-components/src/demo-shared) | Java | Shared content, state, helpers for all demos |

```bash
./gradlew demos           # Build all demos
./gradlew demosTest       # Test all demos
./gradlew demosInstall    # Install all demos to connected device
./gradlew demoJavaStart   # Start Java demo on device
```

## Testing

Co-located BEM tests: `Component.test/ComponentTestUnit.java` (logic) and
`ComponentTestVisual.java` (Roborazzi visual regression).

```bash
./gradlew testUnit                        # All BEM unit tests
./gradlew testVisual                       # All Roborazzi visual tests
./gradlew testUnit --tests "*RadioTest*"   # Single component
./gradlew :demo-pixel:demoPixelVerify                 # Pixel regression vs hardware
```

Pixel tests compare against `_1original.png` reference images from real
vehicle hardware. They fail on regression — any drop in match percentage
from the recorded baseline. Never modify `_1original.png`.

## Contributing

1. Read [AGENTS.md](../../voboost-components/AGENTS.md) — project rules and constraints
2. Each component is self-contained: Java core + Kotlin factory + Compose
   wrapper + tests in one source directory; usage guides live in product `docs-dev`
3. All sizes in pixels (not dp), all hex colors lowercase
4. Run `./gradlew :demo-pixel:demoPixelVerify` after any visual change
5. Run `./gradlew format` before committing
6. See [docs/architecture-decisions.md](components-architecture-decisions.md)
   for settled design decisions (do not relitigate)

## Build Versions

| Component | Version | Rationale |
|---|---|---|
| Build JDK | 17 | Required by Gradle/AGP runtime |
| Java target | 11 | Source and bytecode for Android 9/11 |
| AGP | 8.2.2 | Project-pinned Android build plugin |
| Gradle | 8.2 | Checked-in wrapper version |
| Kotlin | 2.0.21 | Compose compiler plugin matches |
| Compile SDK | 34 | AndroidX requirement |
| Min SDK | 28 | Android 9 automotive fleet |
| Target SDK | 28 | Android 9 baseline |

Release-only build scheme: `debug` variant is disabled. `./gradlew build`
produces a single release variant for the library and every demo.

## License

Apache License 2.0. See [LICENSE](../../voboost-components/LICENSE).
