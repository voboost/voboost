# Репозитории и исходники

[English](repository.md) | **Русский**

Voboost использует отдельные репозитории с одним владельцем каждой границы исходников. Product release definition в `voboost/release` фиксирует полные commits; компоненты не импортируют production source соседнего репозитория по относительному пути.

| Репозиторий | Владеет исходниками |
| --- | --- |
| [`voboost`](..) | Product SemVer, `voboost.json`, `channel.json`, сборкой выпуска, `docs-user`, `docs-dev`, планами |
| [`voboost-app`](../../voboost-app) | Headless Android OTA courier и self-update APK |
| [`voboost-agents`](../../voboost-agents) | Java runtime агентов, генерацией DEX/каталога, OEM-заглушками |
| [`voboost-inject`](../../voboost-inject) | Root-демоном, injector, implant, install/restore/uninstall, SELinux, общим contract crate |
| [`voboost-installer`](../../voboost-installer) | Offline Windows/macOS/Linux UX и ADB-транспортом |
| `voboost-site` | четырьмя targets документации из одного tagged product checkout |
| `voboost-license` | архивом исторических лицензий; не входит в сборку выпуска |
| `voboost-logo` | исходниками брендинга |
| `voboost-codestyle` | общими правилами текста и исходного кода |

## Целевая структура

В `voboost-app` находятся корневой `src/` и одно Android-приложение. В нём нет `app/`, agent project и Node package.

В `voboost-agents` находятся корневые `src/main/java/ru/voboost/agents` и `ru/voboost/reflect`, а Android fixture subprojects — в `stubs/`. В нём нет верхнеуровневых `agents/`, `reflect/`, `ui/`, `ui-res/`, Node package и production pipeline resource APK.

`voboost-inject` сохраняет `daemon/`, `injector/`, `implant/`, каталоги системных программ, `selinux/` и `shared/`. Межбинарные контракты находятся в плоских модулях [`contract`](../../voboost-inject/contract); package и binary демона называются `voboost-daemon`.

В `voboost` находятся product documentation, release definitions и tooling, глобальные схемы, product OpenSpec и планы. Android-, agent-, installer- и native production source остаются в репозиториях-владельцах.

## Правила ссылок на исходники

Developer-страницы ссылаются на конкретный файл или существующий source directory в соседнем checkout. Для ссылки на каталог не требуется искусственный `index.md`. При публикации site заменяет repository-relative source links на immutable repository URLs из точного product tag и не копирует исходники в документацию.

## Граница сборки

Каждый production repository предоставляет собственный `check` и аналог `build-release`. Product release tooling вызывает документированные entry points на точных commits, получает только финальные артефакты и evidence и не повторяет внутренние шаги Gradle, D8, NDK, Cargo или installer.

## Связанные страницы

- [Архитектура](architecture.ru.md)
- [Сборка](build.ru.md)
- [Контракты](contracts.ru.md)
- [Выпуск](release.ru.md)
