# Voboost Code Style

[English](codestyle-integration.md) | **Русский**

Правила форматирования кода для всех проектов Voboost.

## Содержимое

| Файл | Описание |
|------|----------|
| `.editorconfig` | Базовые правила форматирования для ktlint, IDE и JavaScript |
| `AGENTS.md` | Глобальные правила для AI-ассистентов |
| `config-prettier.mjs` | Общая конфигурация Prettier |
| `config-eslint.mjs` | Общая конфигурация ESLint |
| `codestyle.gradle` | Всё сразу: ktlint + Spotless + Checkstyle (подключать этот) |
| `checkstyle.gradle` | Только Checkstyle (альтернатива для тонкой настройки) |
| `spotless.gradle` | Только Spotless (альтернатива для тонкой настройки) |
| `checkstyle.xml` | Правила Checkstyle |

## Интеграция

### Gradle (Kotlin/Java)

Добавьте в `build.gradle.kts`:

```kotlin
plugins {
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
    id("com.diffplug.spotless") version "6.25.0"
}

apply(from = "../voboost-codestyle/codestyle.gradle")
```

Для многомодульных проектов:

```kotlin
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(from = "../voboost-codestyle/codestyle.gradle")
}
```

`codestyle.gradle` подключает ktlint, Spotless и Checkstyle вместе. Для тонкой настройки примените отдельно `checkstyle.gradle` и `spotless.gradle`. Полный пример — в `integration-example.gradle.kts`.

### JavaScript

Добавьте в `package.json`:

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

## Лицензия

Собственные исходники и материалы распространяются по [MIT](../../voboost-codestyle/LICENSE); [русский перевод](../../voboost-codestyle/LICENSE.ru.md) предоставлен для справки. Сторонние компоненты и материалы сохраняют собственные условия; см. [THIRD_PARTY.md](../../voboost-codestyle/THIRD_PARTY.md).
