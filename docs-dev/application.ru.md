# Android-приложение

[English](application.md) | **Русский**

Android-приложение находится в соседнем репозитории [`voboost-app`](../../voboost-app). Корневой `src/` собирает один headless `voboost-app.apk`; в APK нет исходников агентов, `agents.json`, agent DEX и ресурсов OEM-интерфейса.

## Граница ответственности

Приложение остаётся единственным сетевым courier для OTA. Оно может прочитать подписанный `channel.json`, скачать и предварительно проверить один immutable `voboost.json`, потоково записать изменившиеся артефакты в закрытый каталог кандидата, обновить собственный APK через PackageInstaller и передать демону узкое предложение кандидата.

Приложение не выбирает применимые агенты, не публикует поколение, не записывает постоянные root-файлы, не интерпретирует native update-роли и не активирует выпуск. Перед root-изменениями `voboost-daemon` независимо проверяет полный манифест, совместимость, байты артефактов, каталог агентов, сведения о машине и выбранное поколение.

## Планирование и транспорт

[`BootReceiver`](../../voboost-app/src/main/java/ru/voboost/BootReceiver.kt) и [`PackageReplacedReceiver`](../../voboost-app/src/main/java/ru/voboost/PackageReplacedReceiver.kt) просят [`OtaScheduler`](../../voboost-app/src/main/java/ru/voboost/OtaScheduler.kt) запланировать одну постоянную задачу JobScheduler. [`OtaJobService`](../../voboost-app/src/main/java/ru/voboost/OtaJobService.kt) выполняет ограниченную проверку, продолжает загрузку, передаёт предложение через дескрипторы и определяет необходимость повтора.

Приложение и демон связываются через abstract stream socket `voboost.ota.v1`. Приложение передаёт манифест, подпись и изменившиеся артефакты как удерживаемые файловые дескрипторы с точным framing длины. Демон проверяет peer и заново подтверждает каждую authority до приёма предложения.

OTA-сокет проверяет UID и PID, полученные от ядра, требует отдельный обычный UID только для `ru.voboost` в `packages.list` и сверяет установленный APK с активной или ожидающей подписанной сборкой. Имя процесса из `/proc` не читается. Приём логов сохраняет отдельные проверки клиента.

## Последовательность OTA

1. JobScheduler запускает ограниченную проверку при доступной сети
2. Приложение проверяет точные подписанные байты `channel.json` и запрещает откат
3. Приложение скачивает и проверяет size, SHA-256 и подпись указанного `voboost.json`
4. Приложение запрашивает у демона только identity активного манифеста и не запрашивает применимость
5. Приложение скачивает все артефакты, у которых изменились имя, роль, размер или SHA-256
6. Оно публикует полный кандидат в закрытом каталоге приложения, записывая readiness последним
7. Оно отправляет один descriptor-bound request; демон собирает и проверяет полный кандидат из изменившихся и установленных байтов
8. PackageInstaller заменяет `voboost-app.apk`, только если изменился его SHA-256, а приложение продолжает ту же транзакцию после замены пакета или перезагрузки

## Идентичность APK

Product authoring открывает точный финальный APK через `aapt2` и `apksigner` из pinned Android build-tools `34.0.0`, требует одного проверенного v2/v3 signer, извлекает package/version/certificate identity и вычисляет хеш того же файла. Первая product schema допускает `versionCode` только в диапазоне `1..=2100000000`, а для beta1 наблюдённое значение обязано совпасть с `android.versionCode` выбранного source definition. Сохранённый APK evidence document нужен только для аудита: compositor заново извлекает каждое поле и отклоняет подмену до записи app-строки `voboost.json`.

## Хранилище и удаление

Закрытые download, передача в PackageInstaller, ограниченная очередь логов и состояние продолжения остаются в Android sandbox. У приложения нет записываемого root-поддерева, и оно не записывает `/data/voboost` напрямую.

В headless APK нет Activity или точки подтверждения удаления. Подтверждение пользователя принадлежит внедрённому интерфейсу Settings; root-демон принимает запрос, выполняет удаление и окончательно проверяет отсутствие Voboost.

## Проверки

Проверки выпуска охватывают host tests, Android lint, состав minified APK, точные package/version/signer, загрузку и продолжение, framing сокета, аутентификацию peer, продолжение PackageInstaller и обязательные emulator-, device- и vehicle-gates из product source definition.

Canonical app contracts находятся в [`voboost-app/openspec/specs`](../../voboost-app/openspec/specs). Контракты product manifest и публикации находятся в [`product-release`](../openspec/specs/product-release/spec.md).

## Связанные страницы

- [Архитектура](architecture.ru.md)
- [Обновления](updates.ru.md)
- [Контракты](contracts.ru.md)
- [Файлы](storage.ru.md)
- [Безопасность](security.ru.md)

## Реализация и разработка компонента

`voboost-app` собирает непривилегированный фоновый Android-компонент для обновления Voboost по сети.

Приложение проверяет подписанные `channel.json` и `voboost.json`, загружает все изменившиеся artifacts в одну возобновляемую app-private transaction и передаёт opaque typed candidate. Оно открывает provider с привязкой к дескриптору для PackageInstaller только по точному ответу daemon `install-apk`; ответы `pending` и `accepted` никогда не открывают его повторно. Приложение не вычисляет applicability агентов или порядок активации native-компонентов. Выбор агентов, внедрение, изменение root-состояния, SemVer продукта и публикация принадлежат другим репозиториям.

`JobScheduler` отвечает за периодические проверки и продолжение. Приложение связывается с `voboost-daemon` через abstract stream socket `voboost.ota.v1`, передаёт манифест, подпись и изменившиеся артефакты как удерживаемые файловые дескрипторы в canonical order и продолжает ту же транзакцию после замены пакета или перезагрузки. Демон аутентифицирует peer и независимо проверяет каждую authority до root-изменений.

Beta1 CI готовит неизменяемый вход для подписи командой `VOBOOST_EMBEDDED_PRODUCT_PUBLIC_KEY_PEM=<source-pinned-product.pem> scripts/prepare-beta1-unsigned.sh <output-apk>`. Adapter проверяет exact Ed25519 SPKI, упаковывает этот root без изменения source, сверяет packaged raw root, выполняет две чистые minified-сборки, требует byte-identical unsigned APK, отклоняет stale output, signing inputs и debug mode и никогда не получает keystore.

Курьер APK проверяет байты при копировании и повторно проверяет удерживаемый дескриптор чтения перед выдачей доступа установщику. Передача журнала сначала подключается, затем задаёт таймаут чтения, проверяет root-процесс и ограничивает длительность полного обмена. Построение OTA URL сохраняет экранирование пути и параметров. Удаление полностью выполняется на стороне root.

### Сборка

Нужны JDK 17 и версии Android SDK/NDK, закреплённые в Gradle.

```bash
./gradlew testUnit
./gradlew check
./gradlew buildRelease
```

После защищённой подписи в checkout с commit команда `./gradlew apkEvidence -PvoboostSourceCommit=<exact-head>` записывает canonical `voboost-app-evidence.json` рядом с APK. Этот файл не является authority: product release tooling должен независимо проверить в том же APK package, version code, signer certificate, размер и SHA-256 до создания `voboost.json`.

Для защищённой сборки нужны внешний signing keystore и абсолютный HTTPS `VOBOOST_CHANNEL_URL`, который оканчивается на `/channel.json`. Signing material и `local.properties` не являются входами репозитория.

Доставка Cluster использует независимое постоянное задание JobScheduler и защищённый платформенным разрешением wake receiver. Курьер передаёт только непрозрачные подписанные байты и наблюдения из root lease через парный mutual TLS; подписи, поколения и replay проверяют root и QNX. Host-тесты не доказывают Android descriptor interoperability и готовность QNX runtime.

При доставке в приборку аутентифицированный OTA-сокет передаёт проверенные копии только для чтения. Они получают отдельную метку `voboost_cluster_lease_file`; до передачи дескрипторов имена файлов удаляются. Курьер Android может использовать полученные дескрипторы демона и читать этот тип, но не может открывать или менять копии либо читать исходные файлы данных и поколений. Проверки подписанных файлов и срока выдачи сохраняются.
