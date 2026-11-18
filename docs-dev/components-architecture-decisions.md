# Component architecture decisions

**English** | [Русский](components-architecture-decisions.ru.md)

These accepted decisions explain the component architecture. Current contracts remain in [component OpenSpec](../../voboost-components/openspec/specs); source files establish the current APIs. See [components](components.md) for the public entry points.

## Self-contained components

Keep one Java implementation per component, with explicit theme and language values. Shared interfaces, immutable values and stateless utilities are allowed; a common implementation base or theme/language singleton is not. Components already extend different Android bases: `Panel`/`Button` use `LinearLayout`, `Section`/`Screen` use `ViewGroup`, and `Tabs` uses `View`. Their propagation also differs: containers inspect attached children, while Screen owns typed panels, tabs and toast with lifecycle guards.

A shared base would change these contracts; an interface that enumerates Android children would couple leaf contracts to container traversal. Small propagation duplication keeps components extractable, permits concurrent themes and avoids global observer/lifecycle state. Reconsider a small utility only when multiple containers share the same proven semantics; do not broaden the shared interfaces to hide different ownership models.

## Explicit builders

Keep component-specific Java builders, including margin setters and null checks, rather than introducing a generic `BaseBuilder`. A recursive generic base or delegate state adds API and type complexity while component-specific setters are still required. The present duplication keeps fluent return types and each builder's public contract visible. `Buttons` uses per-button configuration; other container builders do not share the margin API. Revisit only when actual common behavior justifies it.

## Separate Compose integration

Keep Compose-free View extensions in `Panel.kt` and `Section.kt`, factories in `ComponentFactory.kt`, and `AndroidView` wrappers and Compose-only helpers in `Component.compose.kt`. File names make the dependency boundary visible. Kotlin resolves top-level calls by package/name, so moving a function between these files does not itself change its API; imports and consumers still need verification. Shared data and View extensions must not acquire a Compose runtime dependency.

## Three public layers over one core

Java callers use constructors or `Component.create(...).build()`. Kotlin callers use receiver-lambda factories/extensions over the same Java builder. Compose callers use `@Composable` wrappers that delegate to the same View implementation. The three surfaces must preserve one behavior, configuration and listener contract; adapters do not own a parallel state machine.

The library compiles its wrappers with Compose dependencies declared `compileOnly`; a Compose application supplies its own runtime. The [Java demo](components-demo-java.md), [Kotlin demo](components-demo-kotlin.md) and [Compose demo](components-demo-compose.md) demonstrate those consumer boundaries. The headless product app is a separate repository and is not a component-demo consumer.
