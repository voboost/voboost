# Выпуск продукта

[English](release.md) | **Русский**

Только product repository `voboost` создаёт Git tag Voboost, GitHub Release и подписанный channel pointer. Component workflows собирают и проверяют inputs, но не публикуют пользовательские component releases.

## Source definition

`release/<release-id>.toml` связывает один canonical Product SemVer и channel с полными commits app, agents, inject, installer, license и logo; точным Android `versionCode`; положительными compatibility values; обязательными host/emulator/device/vehicle gates и optional более старым base release. Ручного режима build/reuse в нём нет. Для beta1 задано `android.versionCode = 1000008`.

## Сборка выпуска

1. Checkout каждого полного commit в независимый clean worktree
2. Запустить принадлежащие компоненту check и release-build entry points
3. Извлечь package, version-code и signer identity из точного подписанного APK через pinned Android tools и сравнить сохранённый evidence output, если он есть
4. Сравнить каждый финальный component artifact с проверенным base release
5. Переиспользовать одинаковые байты и отклонить изменившиеся байты при прежнем commit владельца
6. Создать `restore.json` из release identity, digest точных байтов source definition и canonical restorable closure без restore pair и проверить raw Ed25519 signature
7. Проверить каталог агентов и target DEX closure
8. Создать canonical `voboost.json` с обязательными package/version/certificate identity APK
9. Последними подписать точные байты манифеста и product identity, затем передать один immutable directory installer packagers

Локальный flat Rust crate реализует проверку definition, bounded APK inspection через pinned tooling, compare-only evidence output, restore producer/verifier, schema и semantic parsing manifest/channel/restore, hashing closure, automatic byte decisions, локальный publication plan и checksums. Он не выполняет checkout, Git tag, upload и publication. Restore signing запускается только с явно переданным внешним raw private-key file; production key в репозитории нет.

`release/build-local-beta1.sh` задаёт одну local ephemeral build session: фиксирует source snapshots, проверяет и повторно использует одну явно выбранную сохранённую локальную authority, вызывает app/native adapters, собирает macOS installer с теми же roots, подписывает и повторно проверяет closure/package bytes, сохраняет только public identities и полные hashes, затем ещё раз проверяет trees после копирования output. Скрипт не выбирает authority через glob и не создаёт заменяющие ключи. Output внутри source, temp, build/cache или через symlink alias запрещён до сборки. Product verifier различает artifact directory ровно из 67 файлов и подписанный closure ровно из 69 файлов и никогда не разрешает необъявленные extras. Интерфейс `build-mac-dmg` с восемью аргументами до упаковки проверяет отдельный каталог соответствующих исходников с файлами `corresponding-source.tar.gz`, `corresponding-source.json` и `BUILD-MODIFIED.md`. Локальный output сохраняет эти файлы в `voboost-corresponding-source-<release>` рядом с DMG. В DMG нет архивов и деревьев исходников; читаемые лицензии и notices остаются в приложении.

## Защищённая публикация

Secretless CI preparation создаёт unsigned immutable component inputs. Protected signing jobs применяют роли Android, agents, native executable, restore и product; следующий secretless job повторно скачивает и проверяет signed closure. Три secretless post-closure job собирают exact installer commit для Windows x86-64, Linux x86-64 и universal macOS, проверяют embedded roots product-owned tooling, повторно открывают каждый package и агрегируют exact EXE/DMG/tar.bz2 set.

После trusted clean vehicle evidence для exact successful CI artifact IDs/digests/payload hashes protected publication workflow делает checkout exact product commit, доказывает clean state, скачивает release bytes вне checkout и создаёт либо продолжает exact tag и draft через authenticated GitHub APIs. Существующие assets сравниваются, отсутствующие загружаются, затем весь draft повторно скачивается до публикации. Release bytes не пересобираются, не перепаковываются и не переподписываются.

Последовательность beta1: local ephemeral build, local vehicle-gate, verifiable uninstall/clean state, планирование commits, commit/push source, CI exact SHAs, download exact CI artifacts, clean CI vehicle-gate с повторным uninstall/clean-state receipt и только затем отдельное решение о публикации. Source push не является product release и запрещён до прохождения локальной vehicle-последовательности.

Trusted evidence ingestion связывает подписанные user-machine receipts со snapshot Git trees либо exact CI artifact identity, а также с product commit, CI workflow blob и source-definition digest. Publication требует это evidence. Channel pointer меняется только после публикации immutable release assets и их повторного скачивания и проверки.

## Связанные страницы

- [Сборка](build.ru.md)
- [Обновления](updates.ru.md)
- [Безопасность](security.ru.md)
- [Тестирование](testing.ru.md)
- [Контракт `release/`](../release/README.md)
