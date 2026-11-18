# Voboost Code Style

**English** | [Русский](codestyle-integration.ru.md)

Code formatting rules for all Voboost projects.

## Contents

| File | Description |
|------|-------------|
| `.editorconfig` | Core formatting rules for ktlint, IDE, and JavaScript |
| `AGENTS.md` | Global rules for AI assistants |
| `config-prettier.mjs` | Shared Prettier config |
| `config-eslint.mjs` | Shared ESLint config |
| `codestyle.gradle` | All-in-one: ktlint + Spotless + Checkstyle (apply this one) |
| `checkstyle.gradle` | Checkstyle only (alternative for fine-grained control) |
| `spotless.gradle` | Spotless only (alternative for fine-grained control) |
| `checkstyle.xml` | Checkstyle rules |

## Integration

### Gradle (Kotlin/Java)

Add to `build.gradle.kts`:

```kotlin
plugins {
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
    id("com.diffplug.spotless") version "6.25.0"
}

apply(from = "../voboost-codestyle/codestyle.gradle")
```

For multi-module projects:

```kotlin
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(from = "../voboost-codestyle/codestyle.gradle")
}
```

`codestyle.gradle` applies ktlint, Spotless, and Checkstyle together. For
fine-grained control, apply `checkstyle.gradle` and `spotless.gradle` separately
instead. See `integration-example.gradle.kts` for a full reference.

### JavaScript

Add to `package.json`:

```json
{
    "devDependencies": {
        "@eslint/js": "^9.39.1",
        "eslint": "^9.39.1",
        "eslint-config-prettier": "^10.0.1",
        "globals": "^16.0.0",
        "prettier": "^3.4.2"
    },
    "scripts": {
        "lint": "eslint src --fix --config ../voboost-codestyle/config-eslint.mjs && prettier --write 'src/**/*.js' --config ../voboost-codestyle/config-prettier.mjs"
    }
}
```

### .editorconfig

```bash
ln -s ../voboost-codestyle/.editorconfig .editorconfig
```

## License

Original source code and materials are available under [MIT](../../voboost-codestyle/LICENSE); a [Russian translation](../../voboost-codestyle/LICENSE.ru.md) is provided for convenience. Third-party components and materials retain their own terms; see [THIRD_PARTY.md](../../voboost-codestyle/THIRD_PARTY.md).
