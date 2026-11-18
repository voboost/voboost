# Сборка

[English](build.md) | **Русский**

Каждый production repository владеет toolchain, lockfiles, canonical checks и финальной release build. `voboost/release` фиксирует точные commits и вызывает документированные entry points, но не повторяет внутренние шаги компонентов.

## Entry points компонентов

| Репозиторий | Host check | Release output |
| --- | --- | --- |
| `voboost-app` | корневой Gradle `check` | `voboost-app.apk` из `buildRelease` |
| `voboost-agents` | корневой Gradle `check` | `agents.json`, signature, `agent-common.dex`, target `agent-*.dex` из `buildRelease` |
| `voboost-inject` | Cargo/Make checks | `voboost-daemon`, системные программы, плоские ABI libraries, init и SELinux inputs |
| `voboost-installer` | frontend и Rust checks | platform package из одного проверенного product closure |
| `voboost` | Cargo release tests и независимые docs checks | canonical schemas, manifest, channel document, checksums и publication evidence |

В `voboost-app` и `voboost-agents` нет Node package. OpenSpec предоставляет pinned CI environment. Форматирование документации предоставляет `voboost-codestyle`, а rendering сайта принадлежит `voboost-site`.

## Финальные байты

Product compositor получает финальные component bytes, evidence каталога агентов и source definition. Он независимо запускает pinned Android tools над точным подписанным APK, требует совпадения `versionCode` с source-pinned `android.versionCode` и вычисляет хеш того же файла; сохранённый APK evidence остаётся только compare-only audit output. Одинаковые component bytes переиспользуются; неожиданные другие байты при том же commit владельца останавливают выпуск.

`restore.json` связывает Product SemVer, digest точных байтов source definition и canonical digest restorable closure без restore pair. Его raw Ed25519 signature проверяется до composition manifest. Installer packages собираются заново, потому что содержат новый manifest, release identity и license binding.

## Граница воспроизводимости

Host build компонента доказывает только указанную сборку. App adapter выполняет две clean minified builds одного commit и toolchain identity и требует byte-identical unsigned APK. Затем protected signing создаёт один точный signed APK; pinned Android tools извлекают и связывают package, `versionCode`, signer certificate, size и SHA-256 до создания `voboost.json`. После vehicle-gate байты выпуска не пересобираются и не переподписываются.

## Связанные страницы

- [Репозитории](repository.ru.md)
- [Выпуск](release.ru.md)
- [Тестирование](testing.ru.md)
- [Безопасность](security.ru.md)
