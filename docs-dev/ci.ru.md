# Непрерывная интеграция

[English](ci.md) | **Русский**

Component CI проверяет exact source без private keys продукта. Product CI владеет cross-repository composition; source push, CI, решение о публикации, GitHub Release и channel update остаются разными шагами.

## Обязательные source inputs

Один source definition фиксирует полные commits `voboost-app`, `voboost-agents`, `voboost-inject`, `voboost-installer` и `voboost-logo`. Tagged revision `voboost` предоставляет product schemas, docs, release tooling и сам definition.

Каждый компонент предоставляет non-interactive host check и final release-build entry point. Jobs с ключами получают только проверенные request-bound inputs и protected-main tooling; source pull request не исполняется после допуска secrets.

## Product DAG

1. Собрать один local ephemeral beta1 set
2. Выполнить local vehicle-gate над этими exact bytes
3. Провести verifiable uninstall и записать clean state
4. Спланировать repository commits относительно проверенных local bytes
5. Выполнить commit/push source, не называя это product release
6. Запустить beta1 CI из exact pushed SHAs и trusted local delivery evidence: secretless preparation, protected signing и secretless final verification
7. Скачать exact CI artifact IDs и проверить hashes
8. Выполнить clean vehicle-gate над CI bytes, затем verifiable uninstall и clean-state verification
9. Отдельно решить вопрос публикации; при положительном решении опубликовать те же CI bytes без rebuild/re-sign

Source push запрещён до прохождения шага 3. Stable promotion переиспользует проверенные байты и не пересобирает их. Пропущенный gate остаётся отсутствующим evidence; source push и успешный component CI не означают готовность product publication.

## Доказательства и публикация

`beta1-evidence-ingest.yml` — trusted producer local и CI-vehicle evidence. Его public root закреплён в reviewed product source. Он проверяет canonical user-machine payload и raw Ed25519 signature, полный sorted artifact inventory, deterministic aggregate, chained receipts и собственную immutable producer identity. CI admission сравнивает exact product/component commit trees со снимками local gates; для CI evidence ingestion заново строит inventory из скачанных final artifacts.

Каждый workflow, проверяющий generated agent documentation (`beta1-ci.yml`, `verify.yml` и `sign-dev-vithar.yml`), получает `agents` из exact full commit в source definition, делает checkout в isolated path без credentials и проверяет clean HEAD до явной передачи release-mode root/commit. Генератор читает только metadata, paired documentation и OpenSpec; candidate agent code не запускается. Sibling-directory default разрешён только для local development и не может проверять либо создавать release evidence.

`beta1-ci.yml` собирает exact pushed SHAs. Read-only jobs создают immutable unsigned inputs. Protected jobs исполняют pinned product release tooling и требуют совпадения каждой derived signing identity с source-pinned role-separated trust document. Затем secretless job повторно скачивает final closure и независимо проверяет APK certificate, agent catalog, role-bound native executables, restore authority, product signatures, consumer-embedded roots, manifest и подписанную product/workflow/definition/trust identity.

После trusted clean-vehicle evidence publication повторно скачивает admitted bytes вне clean checkout и заново вычисляет archive и payload hashes и closed inventory непосредственно перед mutation. Workflow продолжает только совпадающий draft, никогда не перезаписывает несовпадающий asset и последним публикует подписанный channel pointer.

## Связанные страницы

- [Сборка](build.ru.md)
- [Выпуск](release.ru.md)
- [Тестирование](testing.ru.md)
- [Безопасность](security.ru.md)

Источник действующей лицензии кода — voboost/LICENSE. Прежний license pin сохранён только как исторические metadata; архив не является входом сборки или CI. Лицензирование логотипа рассматривается отдельно.
