# Voboost 1.0.0-beta1 — приёмка

Цель: готовый DMG для проверки на машине и внешнего бета-тестирования, подготовленный build-only CI и чистые локальные beta1-коммиты. Push, tag, публикация и запуск удалённого workflow этой подготовкой не разрешены. Исходные27 кейсов сохраняются в [исполняемом плане](beta1-functional-plan.json) и [runner](run-emulator-beta1-gate.sh).

## Текущее состояние — 25 октября 2026, DMG321 собран; runtime не принят

Последний технически проверенный пакет — **ARM64 DMG321**, 11 505 521 байт: `/Users/vitaly/voboost/local-builds-new/beta1-current321/voboost-installer-1.0.0-beta1-arm64.dmg`. Полная source-сборка, before/after source freeze, упаковка, codesign, offline closure/roles и hdiutil verify PASS. В closure63 файла: три Android владельца локализации и шесть APK/ledger пар, включая VehicleSetting A9/A11. Host-бинарник320 использован повторно после проверки неизменности исходников и зависимостей; повторная компиляция не требовалась. Установка321 и функциональная runtime-приёмка не выполнены. Checkpoint: `plans/2026-10-25/2026-10-25-build321-six-profile-package-checkpoint.md`.

Предыдущий технически проверенный пакет — **ARM64 DMG318**, 11 284 792 байта: `/Users/vitaly/voboost/local-builds-new/beta1-current318/voboost-installer-1.0.0-beta1-arm64.dmg`. Полная source-сборка, упаковка, readonly-mount, codesign, offline closure и role fixtures прошли. В состав вошёл QNX PW light вместе с прежней Android-частью. Это не подтверждение готовности к машине: установка318 в эмулятор и runtime-прогон ещё не выполнялись. Доказательства: `plans/2026-10-25/development-build318-full-source-retry3.log`, `package318-arm64.log`, `.work162/arm318-package/receipt.json`.

Предыдущий пакет — **ARM64 DMG317**, 10 527 461 байт: `/Users/vitaly/voboost/local-builds-new/beta1-current317/voboost-installer-1.0.0-beta1-arm64.dmg`. Полная source closure и упаковка прошли; receipt: `.work162/arm317-package/receipt.json`, source build: `plans/2026-10-24/build317-source-sdk-forwarded.log`. Runtime317 начат: JSON status/UI/трёхэкранный capture PASS. Установка дошла reboot1/install-agents и выявила SELinux CREATE denial в resource authority journal; install не завершён. Подробности: `plans/2026-10-24/runtime317/RESULT.md`. Сессия штатно остановлена после экспорта логов.

### Открыто после проверки321

- [ ] Удалить оставшийся legacy APK-SHA/SDK28 compatibility gate из `ui/qinggan/OemLanguageFallback.java`, заменить точными A9/A11 пробами; сохранить штатные языки A11. Подтвердить unknown-language → OEM English через фактическую конфигурацию, не подставной EN Context. Исправление в работе и не входит в321.
- [ ] Подключить reviewed Network/SystemUI/Phone и Media в согласованном batch Owner7/Variant14; текущая поставка321 остаётся Owner3/Variant6. Все runtime-пункты остаются открыты.

### Подтверждено

- [x] Phone date fallback после321: optional date-adapter LinkageError воспроизведён реальным старым тестом и изолирован; финально36 phone host-тестов/6классов без ошибок, source review CLEAN. Нативный bridge exception уже перехватывался HookEngine; это отдельная покрытая контрактная проверка, не новый воспроизведённый сбой. Логи `phone-date-fallback-linkage-red.log`, `phone-date-fallback-final-green.log`. Runtime не проверен, в DMG321 не включено.

- [x] Canonical Network/SystemUI/BluetoothPhone ресурсные наборы A9/A11 прошли independent data/compiled review. Они ещё не зарегистрированы в321. SystemUI helper13 host-тестов PASS/CLEAN, тоже пока не подключён. Source handoffs05-50,05-55,06-00,06-01 в `plans/2026-10-25/`.

- [x] DMG320: официальный пересобранный observer на выделенной A9 вернул ready/root absent с пустым stderr и совпавшей boot identity; сессия остановлена. Это проверка пассивного чтения, не установки или агентов. Evidence: `plans/2026-10-25/runtime320-observer/proof.json`, `2026-10-25-build320-package-and-official-observer-checkpoint.md`.

- [x] VehicleSetting registration после320: exact третий Android owner, общий DEX, шесть APK/ledger пар и запрет отсутствующих/лишних файлов; 64 Java tests и фактическая сборка6profiles PASS, независимое source/staging review CLEAN. Native/root owner integration и финальное semantic data review завершены CLEAN и включены в DMG321; runtime NOT_RUN. Evidence: `plans/2026-10-25/vehiclesetting-registration-six-profile-evidence.json`, `vehiclesetting-registration-six-profile-bundle.log`.

- [x] Launcher i18n, Java/Rust source: независимые владельцы Setting/Launcher, четыре точных профиля, общий барьер удаления и readback всех объявленных владельцев реализованы и прошли review. Java55 PASS; lifecycle65 на программу и inventory37 PASS, strict Clippy PASS. APK Launcher A9/A11 собраны, независимое source/data/APK review всех704/710 записей CLEAN; регистрация Setting/Launcher payload/target/catalog завершена и вошла в DMG319/320. Допуск Settings отделён от общей полноты остальных владельцев: общий signed inventory барьер сохраняется, runtime полной локализации не подтверждён. Checkpoints: `plans/2026-10-25/2026-10-25-04-17-launcher-root-delivery-source-checkpoint.md`, `2026-10-25-04-27-launcher-resource-source-build.md`, `2026-10-25-04-40-launcher-i18n-java-checkpoint.md`, `2026-10-25-04-33-launcher-final-independent-review.md`.

- [x] Native locale observer: Python wrapper заменён штатным `make test-cluster-locale-observer`; обычный запуск, ASan/UBSan и два одновременных запуска PASS, временные outputs раздельные и удаляются. Независимое review CLEAN; exact host gate включён в OpenSpec/evidence, после архивации strict34/evidence/English-only PASS. Actual QNX probes, Node.Locale и UI lifecycle этим не подтверждены. `plans/2026-10-25/2026-10-25-locale-observer-contract-archived-checkpoint.md`.

- [x] Host-драйверы phone/Outing исправлены от доказанных ложных PASS: source/window fence, current publisher/policy/CAN, подтверждённый Save и безопасный cleanup. Root совместный прогон119/119, независимое full-diff review CLEAN. Это тестовая инфраструктура после DMG318, не runtime-приёмка. Checkpoint: `plans/2026-10-25/2026-10-25-03-46-runtime-drivers-checkpoint.md`.

- [x] Подготовка CI/source input: архивируемый license-репозиторий удалён из обоих обязательных парсеров, текущих pins и overrides. Rust58, Node27+12, точечный workflow1, strict Clippy/fmt/OpenSpec и независимое ревью прошли. Это изменение после DMG318, runtime не подтверждает. Отчёт: `plans/2026-10-25/2026-10-25-03-34-license-source-retirement-result.md`.

- [x] Итоговый inventory поддерживает ровно два уже реализованных формата KZBF: indexed A9 и inline A11. Шесть тестов и фактические шесть пар/12 файлов прошли; неизвестные форматы и неверные owner/locale/module-пары по-прежнему отклоняются. Независимое ревью CLEAN. Доказательства: `plans/2026-10-25/beta1-inline-resource-actual-six-receipt.json`.

- [x] Свежая интеграция PW: product, runtime/font, i18n и PW production links PASS; PW81560 байт, семь проверенных импортов, без глобальных конструкторов. Каталог и bundle прошли: один владелец PW, прежний Android DEX и один QNX light target; native-байты совпадают с результатом сборки. Отсутствующий модуль отклоняется до staging. Независимое ревью CLEAN. Доказательства: `plans/2026-10-25/pw-catalog-actual-bundle-evidence.json` и `pw-catalog-actual-bundle.log`. Выполнение PW на QNX остаётся NOT_RUN.

- [x] Доставка Android-переводов A9/A11: восстановленный исходник и общий барьер удаления проверены заново. Install — 64, Restore — 59, Uninstall — 59 тестов PASS; strict Clippy трёх программ PASS; независимое ревью CLEAN. Все пакеты проверяются до первой финальной записи. Доказательства: `plans/2026-10-25/2026-10-25-plural-i18n-removal-final-proof.md`. Проверки PM/OMS в эмуляторе ещё не выполнены.
- [x] Совместный host-прогон драйверов launcher, phone, i18n QNX, приёмки системного переключателя и проводки case22: 85 тестов PASS, без пропусков. Лог: `plans/2026-10-25/runtime-drivers-and-case22-final-combined.log`. Это проверка драйверов, не прохождение сценариев в эмуляторе.
- [x] PW: обработка ошибок чтения живого UI прошла девять ASan/UBSan-вариантов и независимое ревью CLEAN. Собственное изменение снимается при ошибке чтения; ошибка во время изменения сохраняет требование перезагрузки процесса. Лог: `.work162/pw-qnx-eh/backend-readexception-sanitizer.log`. QNX light добавлен в development-каталог к тому же владельцу PW; runtime ещё не проверен.
- [x] Сборка318: свежие QNX product, runtime/font, i18n и PW сборки, полный source closure и ARM64 DMG прошли.
- [ ] Установка и runtime318: остаются неприменёнными SELinux-политика журнала и транзакция OEM-конфигурации QNX. Предыдущие автоматические отказы не обойдены; ответы на запросы согласования ещё не получены.

- [x] Canonical SDK integration: новые2header проверяются точными source bytes/ABI contracts без новых SHA-lock; actual QNX runtime/font PASS4s и nativei18n PASS32s. Root checkpoint: plans/2026-10-25/qnx-canonical-sdk-integration.json. Гостевое выполнение не принято.
- [x] Phone source: добавлен SystemPolicy target с actual A9/A11 probes и bounded lookup для +7/разделителей, generation fence перед публикацией. 25 host tests и независимое review PASS/CLEAN. Evidence: plans/2026-10-25/2026-10-25-phone-cluster-source-audit.md. Звонок/рендер приборки остаются NOT_RUN.
- [x] Resource staging: bounded read и captured APK/ledger bytes закрывают concurrent growth/reopen races; root повторил Android+QNX16tests PASS. Exclusive closure owner проверен по build reservation/private staging. Evidence: plans/2026-10-25/2026-10-25-01-28-handoff-resource-stage-bounds.md.

- [x] Официальный --observe экспортирует полный per-target ClusterSnapshot через data.files.runtime.data.cluster; native boot/process identity не подменяется Android publisher. Status25 + management32 + observer10 =67 PASS,1 existing ignored; root сверил фактические логи и schema source, независимое review CLEAN. Runtime acceptance ещё NOT_RUN. Evidence: plans/2026-10-25/2026-10-25-cluster-official-observe-export.md.

- [x] QNX multi-owner source contract: Receipt и daemon snapshot теперь используют (id, targetProcess); Applied требует все ожидаемые owners. Проверены 37 daemon +11 contract tests, strict Clippy и независимое review. Runtime acceptance остаётся открытой. Evidence: plans/2026-10-25/2026-10-25-cluster-per-target-observations.md.
- [x] Свежая production i18n module link с отдельными confirmed English/Chinese/Russian reason: PASS6s; все3 строки проверены в новом ELF. Общий runner86 native-команд и focused Java PASS. Evidence: plans/2026-10-25/2026-10-25-i18n-module-confirmed-language-link.json и .work162/i18n-loaded-origin/native-86-builtin-observability.log. Это source/build, не runtime acceptance.
- [x] Bounded resource read race закрыта: actual growth/short-read/truncation regression,22TS tests и независимое rereview CLEAN; actual6pairs12files336283B повторный staging exact. Evidence `plans/2026-10-25/qnx-resource-bounded-growth-tests.log`, `qnx-resource-fixed-reader-actual-six-pairs.log`.
- [x] Version preflight318: productcurrent+namedrelease snapshot synchronized1000012 сexistingAPK,4testsPASS и independentreviewCLEAN. Новый dry-run `plans/2026-10-25/development-build318-plan-v2.json`, прежний план сохранён.

- [x] Actual generateManifest + bundle contract PASS20s: rootпрочитал agents.json, i18n-ru имеет3QNXtargets gauge/point/light с одним libagent-i18n-ru.so84368B; font-cyrillic остался1targetgauge. Nativeprocess descriptors проверяются согласованно, duplicates rejected, independentreviewclean. Evidence `plans/2026-10-24/qnx-i18n-three-target-catalog.log`; runtime не принят.
- [x] DMG318 source config прошёл штатный dry-run: `plans/2026-10-25/development-build318-plan.json`. Полная сборка/упаковка318 теперь прошла; source startup delivery остаётся в работе.

- [x] Production i18n module link теперь PASS5s: `voboost-agents/build/cluster/product/libagent-i18n-ru.so`,84368B,8CPP+2ASM, noUndefined/EH/layout0constructor/source gates. Root отдельно проверил actualartifact и все27 source/input files по receipt; drift отсутствует. Evidence `plans/2026-10-24/qnx-i18n-production-module-link-forwarder.log`. runtimeEvidence=NOT_RUN, catalogAdmission=false; прежний FAIL_UnwindResume закрыт этой сборкой, не старым layout-log.

- [ ] QNX physical startup/restart delivery: current startup.sh лишь export LD_PRELOAD/bootfile; исследование actualOEM execve выявило explicit environment у clusterboot/sysmgr. Нужно доказать доставку внедрения при штатном старте и supervisor restart отдельно от Voyemu directspawn. Владельцы: hostcap — startup delivery, root_delivery — restart journal; Voyemu уведомлён о сохранении собственного adapter env. До закрытия physical QNX readiness не подтверждена.
- [x] Production sourceclosure route nativei18n добавлен; obsolete Python prerequisite удалён. Root проверил actual17TS testsPASS `plans/2026-10-24/sourceclosure-native-module-route-tests-v2.log`. Первый actualmodulelink выявил _Unwind_Resume; исправление подтверждено новой production-сборкой ниже.

- [x] A11 data-only inline KZBF0x30003: Java producer и Rust closed-metadata parser согласованы на actualgauge/light. Root прочитал parser и логи: gauge15, signedchain6, light1 PASS; запрещены plugins/code/extra metadata/compression. Старый indexed формат сохранён. Evidence `.work162/resource-pack-inline-java-{gauge,signed,light}.log`. Независимое review clean; genuineQNXloader и runtime mappings ещё не проверены.

- [x] Runtime SDK inventory исправлен с3до5 заголовков; оба новых header drift/missing регрессионно отвергаются. Root проверил JUnit9/0fail и фактическую target-сборку runtime+font с экспортированным SDK: PASS6s, `plans/2026-10-24/qnx-runtime-sdk-five-headers.log`. Независимое review ещё ожидается; i18n native link и guest execution этим не подтверждены.

- [x] Свежая QNX product-сборка после multi-owner/UI-frame изменений: PASS16s, terminal0, source snapshot и экспорт5 SDK headers совпадают с исходниками. Root проверил фактический log/checkpoint: `plans/2026-10-24/2026-10-24-qnx-java-multiowner-uiframe-build-freeze.json`. Implant3316416B, daemon908008B. Последующие ET_EXEC/producer изменения и QNX runtime сюда не входят.

- [x] Private QNX RU integration: независимое повторное ревью закрыло false-APPLIED для EN/ZH. Успех допустим только для подтверждённой исходной локали и того же фактического словаря; неизвестный builtin остаётся pending до изменения. RU требует полного readback и свежего system watermark. Это source review, не runtime acceptance. Report: `plans/2026-10-24/2026-10-24-i18n-private-native-integration.md`.
- [x] Два разрыва runtime-cluster исправлены в исходниках: owner-specific readiness для i18n в point/light проверяется каждый frame; узкое stopping-report window сохраняет точный RELOAD_REQUIRED. Font/PW readiness не менялась. Root прочитал production diff и фактический лог: общий Java runner77 host-команд и focused Java PASS, независимое review clean. Evidence `.work162/i18n-loaded-origin/native-77-runtime-integration.log`. Полный QNX runtime цикл остаётся открытым.

- [x] Runtime316: QNX signed new-boot absence verified and accepted by root; точная запись 17:47:29 в `plans/2026-10-24/runtime316/uninstall-original298.log`. Исправления создания `service.log` с режимом `0600`, узкого удаления старого `service.log` с режимом `0666` и fresh-boot authority revalidation прошли native runtime. Прежний блокер recovery315 закрыт этим прогоном.
- [x] Сессия316 штатно остановлена; полный лог 126 МБ сжат системным gzip9 до 3,6 МБ, релевантный контекст сохранён. Authority не переписывалась.
- [x] Общий host-прогон launcher/phone/Outing: **135 тестов, 26 классов, PASS**. Лог: `plans/2026-10-24/beta317-launcher-phone-outing-host.log`. Это объединённая проверка исходников, не эмуляторная приёмка.
- [x] Applications checkbox: 18 focused host tests и clean review; local desired state без визуального отката, disabled на время запроса, 3 быстрых повтора и последующий backoff15–60 с. Newest intent защищён от старого section retry; native checkbox и геометрия сохранены.
- [x] Phone lookup: четыре прежних сбоя со скобками/NBSP воспроизведены и исправлены,19 телефонных host-тестов PASS. Формирование номера для набора не менялось; реальный HFP/приборка этим не подтверждены.
- [x] Инструменты переводов переведены на **Java**: 48 тестов и штатный Gradle build PASS, Python-инструменты этой задачи удалены и не используются. Ledger компактный, 1670 runtime contracts. Лог: `plans/2026-10-24/i18n-java-normal-minified-build-and-tests.log`.
- [x] Rust probe-only/two-phase ресурсный workflow интегрирован: inert staging → фактический compatibility probe → OMS enable → RU readback → admission. Target APK hash не служит compatibility gate; целостность доставленных ресурсов проверяется отдельно. Host-проверки и строгий daemon Clippy PASS; checkpoint: `plans/2026-10-24/2026-10-24-i18n-two-phase-source-result.md`. Android/QNX runtime ещё не принят.

- [x] После freeze317 интегрирован safe-code None cleanup (ещё не в DMG317): install39, restore32, uninstall32, contract7 tests и strict Clippy четырёх пакетов PASS. Init/policy и полный boot/PM цикл ещё не включены и не приняты.
- [x] Acceptance runner317 адаптирован к SDK/ADB Voyemu, 4 runner tests PASS; actual session serial/port обязательны. Установка выполнена до отказа journal staging; успешная установка не подтверждена.

- [x] External machine JSON ownership E2E: actual session.start → status/ready → fresh UI → input.tap → visible all_app_view → session.stop PASS. Evidence `runtime317/external-owned-api-acceptance.json`. Полный agent-launcher runtime этим не закрыт.
- [x] Actual LauncherWindowAgent A9/A11 host graph2/2 PASS; removing replay/restore bytecode mutants обаRED. Dedicated fixtures не входят вDEX. Отчёт `2026-10-24-launcher-actual-replay-host-gap.md`.

- [x] Source retry-storm fix: main handoff9 + generation62 + focused ACK6 tests PASS, RED old100 starts reproduced, independent review clean. Source заморожен; runtime fix пока не установлен. Отчёт `2026-10-24-install-continuation-retry-storm.md`.
- [x] Лайм TV HD: actual retained APK selected-source/frame/cache host audit55/55 PASS, без внешней Cloud Space рамки. Исходный TV-body сохранён. Старый physical deployment этим не подтверждён; отчет `2026-10-24-lime-current-selected-source-audit.md`.

- [x] После317 добавлена ограниченная диагностика native event transport: 9/9 host-тестов, включая фактический путь незарегистрированной JNI, форматирование и независимое review clean. Не более одной записи на операцию без payload; обычный null ACK не ошибка. В DMG317 изменения ещё нет. Отчёт `2026-10-24-events-native-transport-diagnostics.md`.
- [x] Исторический прогон runtime318 (не DMG318): фактический SettingsProvider `content://settings` принадлежит system process PID1789; прямой GET_system возвращает `value=null`. Это не подтверждение сохранённой EN locale. Evidence `runtime318-outing/provider-owner.txt`, `provider-direct-call.txt`.
- [ ] Исторические partial-install прогоны317/318 до сборки DMG318: Applications возвращает Voboost Fixture после включения секции, но завершение ACK не принято. Outing включение отправлено18:25:07Z, ACK timeout18:25:17Z, checkbox остаётся false. Порог и возврат режима не проверены. JSON сессии остановлены, официальный архив318 проверен parser. Evidence `runtime317-functional/FUNCTIONAL-CHECKPOINT.md`, `runtime318-outing/mutation-recent.json`.

- [x] Следующий кандидат: общая compileJava и development buildDex +verifyProductionDexOwnership PASS. Реальные class_defs подтверждают все8классов None helper в agent-i18n-ru.dex, PW Java helper и bounded event diagnostics в agent-common.dex. Evidence `2026-10-24-next-candidate-dex.log`, `2026-10-24-next-candidate-dex-class-definitions.json`. Это ещё не подписанная closure и не новый DMG.

- [x] QNX i18n source gates: Java/Gradle native runner28команд PASS,5 AArch64 объектов PASS, проверочная ELF-линковка4команды PASS. Root сверил фактический LOCAL HIDDEN GNU tail-forwarder LDAR/BR и новые imports по оригинальным библиотекам. Modern-origin regression воспроизводит прежний stale-after-throw defect (RED), исправление GREEN. Evidence `.work162/i18n-loaded-origin/{modern-red.log,final-elf-hidden-symbols.txt,final-elf-tailbranch.asm,final-elf-new-imports.json}`. Startup observer route и гостевое выполнение не приняты; proof ELF не входит в DMG.

- [x] Production QNX Java wiring: обе Python invocation build-closure заменены на Gradle Java tasks с JSON аргументами; 16 product tests PASS. Actual native Java build и Gradle runtime/font build PASS; qnxExecuted=false. Scratch перенесён в project .work162, independent review clean. Подробности: `plans/2026-10-24/2026-10-24-production-qnx-java-wiring.md`. Это ещё не новый DMG и не runtime-приёмка.

- [x] Bootstrap production recheck: все шесть прежних structural-probe patches фактически применены (reverse apply checks), pending bootstrap integration отсутствует. Действующий locale_identity gate отдельно у владельца замены на probes. Evidence `plans/2026-10-24/2026-10-24-bootstrap-production-recheck.md`.

- [x] Причина ACK runtime317/318 локализована: запросы launcher и Outing дошли до демона, но mutation guard отказал `operation lock is busy` при незавершённой install-agents той же транзакции. Silent timeout соответствует текущему протоколу; launcher worker продолжал повторы. Полная функциональная приёмка остаётся открыта. Evidence `plans/2026-10-24/2026-10-24-applications-ack-export317-318-diagnosis.md`.

- [x] Outing transient feedback: actual-hook RED воспроизвёл потерю Eco после read exception; исправление отзывает current control authority и сохраняет previous только в той же ACC-сессии. 90 Outing host tests PASS, independent review clean. SDK34 init только для host stubs; runtime не принят. Evidence `plans/2026-10-24/2026-10-24-outing-transient-feedback-fix.json`.

- [x] Объединённые Java инструменты после KZB21 и builder cleanup16: 130 tests, 0 failures/errors. Root проверил actual XML и Gradle exit0; receipt `plans/2026-10-24/2026-10-24-qnx-java-combined130.json`. QNX evidence classifier: 6 product tests PASS, независимое review ещё идёт.
- [x] QNX языковой пакет: actual Java A11 gauge payload прошёл подписанный product/catalog и native Authority::import до защищённого generation directory; tamper rejected, desired остаётся None. Это host staging, не загрузка словаря OEM. Evidence `.work162/pi-beta1/glm319-qnx-resource-pack/actual-native-signed-pack-staging.log`.

- [x] QNX structural identity replacement: удалены firmware SHA compatibility gates и четыре +8 assumptions; настоящий GNU RTTI/properties, provider-before-OEM и Release/Acquire admission прошли 57 host-команд и independent review. Полная замороженная Java native сборка PASS13s, включая GNU/EH/layout/import/source snapshot; builder20 tests PASS. Evidence `plans/2026-10-24/2026-10-24-qnx-java-object-probe-build-freeze.json`. Startup gateways по-прежнему не подключены, QNX runtime не принят.

- [x] Outing автоматический owner-session driver подготовлен: actual JSON UI/vehicle/CAN + официальный persisted/runtime observer; both UI/policy enable gate, threshold40/41→previousEco, finally speed0 даже при потерянном ответе. Root повторил 7 transport-double tests PASS. Реальный прогон NOT_RUN до успешной установки. Команда: `plans/2026-10-24/2026-10-24-outing-runtime-driver.md`.

- [x] Local product CI cleanup: strict all-targets Clippy PASS, release57 tests PASS, rustfmt check PASS; independent review clean. Проверены реальные source-level блокеры, без suppressions. Remote CI не запускался, final pins остаются после source acceptance. Evidence `plans/2026-10-24/2026-10-24-product-ci-clippy-cleanup.md`.

- [x] PW structural getter proof: actual A9/A11 ELF +13 negative/actual tests PASS, bounded ELF/DWARF work, exact PLT/relocation and caller-buffer self-copy rejection; strict all-targets Clippy и независимое review PASS. Runtime mapped-owner admission/visual mask остаются отдельно открыты. Evidence `plans/2026-10-24/2026-10-24-pw-readback-structural-proof.md`.

- [x] Архитектура private QNX RU: direct signed-pack loading +actualtyped selecteddictionary+ALLexpectedvalues исключает необходимость early global origin/startup hooks. Они остаются неприменёнными и не являются prerequisite новогоprivate route. Actualruntime/disable ещё открыты. Decision `plans/2026-10-24/2026-10-24-private-qnx-load-route-decision.md`.

### Что остаётся до готового кандидата

- [x] Согласованная source freeze, полная source closure и ARM64 DMG317 собраны; проверка пакета и hdiutil verify PASS. NDK переиспользован из Voyemu, отдельная копия NDK не создавалась.
- [x] Три потока логов по128 МиБ; 51 daemon +18 installer parser +6 archive tests PASS, независимый Pi review clean. Проверка реального архива33 МиБ воспроизводит старый предел32 МиБ и проходит с новым400 МиБ. Подробности: `plans/2026-10-24/2026-10-24-log128-result.md`.
- [x] STATE_EXTENT исправлен владельцем Voyemu; наша машина запущена с Android/QNX, baseline JSON API и3 кадра приняты.
- [x] Реальный штатный log export317 PASS, архив262КиБ проверен продуктовым parser (20,331 записей; могут включать старые boots).
- [ ] Исправить отказ resource journal staging; новый SELinux journal type пока отклонён автоматической проверкой разрешений и не применён. Подготовить конкретный proposal/тесты для human approval.
- [x] Узкий source/host fix частых install retries проверен заново: ACK8 + handoff9 PASS, независимое source review. Два отдельных process-local бюджета по5 с backoff; initial ACK start отдельно. Отчёт: `plans/2026-10-25/2026-10-25-03-50-install-retry-current-verification.md`.
- [ ] Runtime install retry: повторно принять штатную установку нового кандидата и наблюдение Android/init после снятия journal AVC; source/host результат не закрывает этот пункт.
- [ ] Android old298 uninstall: desktop admission относится к другой загрузке, daemon корректно отказывает; public operation/sidecar и штатный log export подтверждают mismatch. Новую миграцию старого dev-билда не строим; подготовить свежую **ту же -voboost машину** из профиля для текущего кандидата. QNX recovery316 уже завершён и повторным открытым блокером не считается.
- [ ] Launcher на полной новой сборке: скрытие/возврат строк и папок, порядок после reboot; live global/local DPI и individual display modes для работающих приложений; ru-RU только при эффективно включённом i18n-ru; checkbox/section state, плавность и FPS A/B. 135 host-тестов не заменяет эти проверки.
- [ ] Установить все сохранённые пользовательские APK, включая SpeedCN, и снять все страницы launcher с metal и штатными иконками. Итоговый вид на новой сборке ещё не принят.
- [ ] Outing: runtime порога скорости, возврата в предыдущий режим, подтверждённого режима в toast, отмены устаревших уведомлений и диалога без скачков. Host исправления проверены; реальное поведение текущего полного кандидата ещё нужно подтвердить.
- [ ] Phone: OEM PBAP/HFP connect/reconnect, русские имя и фамилия через пробел, кириллица без межбуквенных пробелов, входящий popup и фактический исходящий dial с `+7`; снимки мультимедиа и QNX. Актуальный API перечисляет phone operations; владелец Voyemu сообщил о собственном phone/QNX прогоне. Готовый экземпляр ещё не передан, поэтому Voboost runtime этими сообщениями не принят.
- [ ] QNX font: полный install → enable → apply → disable → reboot → restore/uninstall цикл. Успех recovery316 не подменяет применение шрифта и его обратимость; A11 отдельно не принят.
- [ ] i18n-ru SOURCE: сейчас ресурсные компоненты покрывают только Setting A9/A11. Для полного перевода мультимедиа нужны отдельные owner inventories/probes/ресурсные пакеты Launcher, VehicleSetting, SettingNetwork, SystemUI, BluetoothPhone и Media. Это исходная работа, не только runtime-барьер; проектируется следующий этап Launcher A9/A11 с проверкой per-owner доставки/очистки.
- [ ] i18n-ru runtime: полный probe/admission, фактические RU/EN fallback строки, системный языковой picker, обновление пользовательских приложений, отключение и postboot resource absence. Полный A9/A11 Android/QNX перевод и native loader cycle ещё не подтверждены.
- [ ] PW OFF indicator: довести collector/mask до проверенного обратимого visual hook и rendered truth table; exact A11 source research/host tests не доказывают runtime скрытие индикатора.
- [ ] Engineering menu, activation skip, automatic display lift A9: development source/host gates есть, runtime включение/отключение не принято. Физическое опускание панели проверяется на машине.
- [ ] FEV/PW: причина последней медленной активации на физической машине 24 октября не доказана; машины уже не было при USB-проверке, логи этой загрузки не забраны. Сохранённые старые логи и startup retry fixes не выдают за доказанную причину нового эпизода. Не назначать полный повтор неизменённых сценариев без связи с изменениями.
- [ ] Экспорт private логов: emulator export подтверждён, новую реализацию на физической машине ещё проверить при подключении.
- [x] Исходные 27 ID и runner сверены с системным picker и multi-owner i18n: cases6/7 используют OEM EN/ZH, case22 требует отдельного admitted RU/system-picker и Android/QNX gauge/point/light proof. Отсутствующие receipts остаются NOT_RUN/unavailable и блокируют готовность; обязательные runtime gates этим аудитом не закрыты. GUI11 и Intel остаются отложенными.
- [ ] Финальная гигиена исходников/артефактов, подготовленный CI и локальные beta1-коммиты без push. Текущая готовность для внешних бета-тестеров ещё не подтверждена.

## История контрольных точек — не текущий рабочий список

Разделы ниже сохранены как доказательства прошлых прогонов. Их слова «текущий», «следующий», старые blockers и допущения относятся только к указанной контрольной точке; актуальное состояние и очередь находятся выше. В частности, QNX recovery315 уже закрыт runtime316, DMG305 и308 заменены317, Python в инструментах этой задачи заменён Java.

### История — Runtime311 — восстановление QNX, 24 октября

Подписанный recovery SDK310 собран. Штатный запуск подтвердил upgrade helper305→308 при сохранённой original298 authority; qtime исправление прошло native preparation. Финализация не завершена: `managed purge member authority rejected`. Read-only metadata локализовали service.log root:root mode0666 size0; остальные члены0600/0700. Повтор после reboot дополнительно обнаружил `native final receipt kernel differs`; pairing сохранён. Ручного chmod/удаления не было. Evidence: plans/2026-10-24/runtime311/{uninstall-original298.log,qnx-managed-tree.txt}. Сессия штатно остановлена (4396мс). Исполнитель исправляет creator/log policy и interrupted-finalization, затем следующий настоящий прогон.

### История — Работа над кандидатом308 — 24 октября

ARM64 DMG308 собран и независимо проверен: 10 019 083 байт, SHA256 fca23d45acba29f3472b9daa2dbd7b2d991531020eff4485f8e28b720ad9e658; manifest 5fa9e19d6806957f2f6054976a1cf2ea9dd8262a01eec6f1e9250d2e54b1edd3. Старый собственный DMG305 удалён, необходимые closure/authority/SDKprovider сохранены. Исправление QNX qtime прошло63 host-теста, независимое review и штатную QNX cross-build; фактическое restore-offline ещё не повторено. Runtime309 повторил штатное восстановление, но остановился до запуска native helper: retained helper305 differs. Authority сохранена. Изменение host staging отклонено auto-review, готовится отдельный reviewable patch и mock tests для явного одобрения. Сессия309 остановлена.

- [ ] Launcher: live DPI/config replay, условная ru-RU от эффективно включённого i18n-ru;29 focused PASS. Review исправил atomic config/LEFT_INSET, дополнительная гонка original binding исправлена,30 focused PASS и final review clean. Нужна реальная проверка WindowState/client config.
- [ ] Outing: подтверждённый режим в toast;78 host PASS до последнего review. Main queue с отменой и повторной проверкой исправлена;87 host PASS и повторное review clean.
- [ ] Engineering menu: unstable/defaultfalse/development,26 focused PASS, priority/spec/catalog gates PASS, независимое review clean. Нужна runtime-проверка пяти нажатий и отключения.
- [ ] Automatic display lift A9: порт и6 behavioural tests PASS, две регрессии воспроизведены. Review clean, unstable checkboxdefaultfalse входит в308; физический запрет опускания не подтверждён.
- [ ] Activation skip: порт16 host PASS, обе lifecycle гонки исправлены, review clean; unstable checkboxdefaultfalse входит в308.
- [ ] Phone: host-преобразования+7/имя-фамилия проверены. Voyemu подтвердил no-op BT adapter; ему передана реализация реального synthetic PBAP/HFP→OEM→QNX пути. Скриншоты/реальный dial payload не получены.
- [ ] i18n-ru: ресурсный генератор18 host PASS; реальный маленький RRO APK собран и подписан для admission. Полный RU агент/переводы/picker/lifecycle не готовы; Android и QNX исполнители продолжают. QNX bridge host+ASan/UBSan не заменяет resource-loader runtime.
- [ ] PW cluster indicator: exact A11 6.11.1 soundEnable299 (303 — turnRight); визуальная truth table ещё не доказана, маска не включена.

Машина24октября с последней задержкой FEV/PW по USB не обнаружена; логи этой загрузки не получены. Это не доказательство причины задержки. Это исторический план; актуальная очередь указана в начале документа.

### История — Кандидат305 — собран и проверен, runtime-приёмка продолжается

ARM64 DMG: `/Users/vitaly/voboost/local-builds-new/beta1-current305/voboost-installer-1.0.0-beta1-arm64.dmg`,9 961 481 байт, SHA256 `84604fee7441af7ccf254ce062a6a0c11d4658a062b17288f777a584f5fd4a73`. Manifest `f2c30f555bbaef7e4901d8fb05731b6e543f510a698943e4aa872ff000b3f0f5`. Sourceclosure/ARMbuild/packageverify/read-onlymount/codesign/inventory/role roots PASS. На дату этого исторического checkpoint запись описывала текущий DMG; теперь актуален верх документа. Старый собственный DMG304 удалён после lsof; closure и исходная298 authority сохранены.

Включены девять пользовательских агентов: Settings, font-cyrillic, keyboard-en, keyboard-ru, launcher, phone, Forced EV, Pedestrian Warning, Outing; ui-qinggan — общий модуль. Пропуск активации, engineering menu и i18n-ru ещё не включены как готовые агенты.

Source305 включает исправления Outing startup/missed feedback/previous state и first draw, Applications checkbox/expander/collapse/action groups, штатных radio resources и default titleless spacing, root preparation для log export с retained vehicleDiagnostics, native logging callback, discovery500ms и transient retry rearm. Host/review evidence: Outing76PASS; UI40PASS; component30PASS; generator7PASS; copy gate1+7PASS; FEV782PASS/3ignored плюс latest frozen runtime27PASS. Это НЕ физическая приёмка FEV и НЕ полная runtime-приёмка305.

Restore-offline реализован и source-reviewed: native61/host42/root verifier1 PASS. Следующая runtime-задача — восстановление незавершённого удаления original298 в тестовой машине, настоящий reboot и подписанное отсутствие, затем установка305 и QNX font lifecycle. Исправления GUI full11 и Intel по прежнему отложены. Машина реального пользователя сейчас недоступна; причина конкретного FEV сбоя24октября остаётся неподтверждённой без private segments/state.

### История — Актуальные замечания машины — 24 октября, после source304

- [ ] FEV: причина периодического невосстановления пока не доказана. Сохранены logcat 17,4 МБ и boot/process snapshot; приватные сегменты/state не получены из shell SELinux context. В logcat демон жив, CPU64–66%, audit storm. Исправлен доказанный overhead discovery20раз/с вместо2раз/с;23 watcher+8 scheduler tests PASS, независимый review CLEAN; физическая проверка pending.
- [ ] Экспорт логов: исправлены CLI/GUI wait→root→wait→fixed exporter и добавлен bounded retained vehicleDiagnostics (FEV/PW flags/startup/injections) без полного state/секретов.5 installer+23 status tests PASS, review CLEAN; физический экспорт pending. Это не утверждение, что SDK pull был штатным экспортом.
- [ ] Outing UI: убрать промежуточный (i) до первого draw; сверить шрифты и отступы с Individual. Исполнитель outing_layout_audit.
- [ ] Outing поведение: настроенная скорость выхода и возврат прежних режимов вместо Comfort; проверить startup и delayed OEM пути A9/A11. Исполнитель outing_vehicle_behavior.
- [ ] Applications: только component defaults; group checkbox сворачивает section с анимацией; original apps без настроек; обычный checkbox плюс отдельный right/down expander; radio/action hints справа, stop/uninstall в один ряд; устранить задержки раскрытия. Исполнитель launcher_ui_corrections.
- [ ] Setting: default правый отступ Uninstall hint; отдельные штатные radio gradients Setting/VehicleSetting. Исполнитель settings_native_spacing_gradients, Section меняет только launcher_ui_corrections.
- [ ] Штатные иконки: измерены14 ресурсов A9/A11, transform/prewarm интегрирован для измеренного150×150 holder;22 targeted tests PASS. Визуальная runtime-приёмка pending; неизвестная геометрия A11 SecondAllAppAdapter оставлена штатной.

Следующие исходные контрольные пункты исторические: разрешение на restore-offline уже получено, реализация продолжается; не считать прежнюю запись об ожидании разрешения актуальной.

### История — Текущий контрольный пункт — 24 октября, source304 / runtime в работе

ARM64 DMG304 собран и проверен: `/Users/vitaly/voboost/local-builds-new/beta1-current304/voboost-installer-1.0.0-beta1-arm64.dmg`,9 877 930 байт, SHA f651833c841be9ba84b73cd9b13743b3cddd36a4faca55ff12001cd516d16377; manifest6c065525c23df36e0835a5dbe99eeb94a730868164e5adaa59a53fffe6b685fb. Sourcebuild/mount/codesign/33payload/offline/direct-iokit/roles PASS. Implant linker исправлен:2LOAD с сохранённымRELRO;12layout+8interface tests, независимое review CLEAN. Полная closure304 побайтно совпала с separatelysigned диагностическим вариантом, который actualLD_PRELOAD+id прошёл в QNX; baseline298 давалFATAL. Это подтверждает loader/id-main, НЕ font/agentactivation. Оригинальный298 остаётся pendinguninstall. Новый restore-offline design не реализован: auto-review требует отдельного разрешения пользователя, запрос отправлен. VM после диагностического witness штатно остановлена. Launcher/phone/Outing и полныйQNXfontcycle на304 NOT_RUN. Следующий executable SDKCLI304 собирается. Evidence: plans/2026-10-24/product304-runtime-byte-equivalence.json, qnx303-layout/preload-witness-result.json, qnx304-production-linker-freeze.md и plans/2026-10-23/beta1-integration/dmg304-verification.json.

### История — Предыдущий контрольный пункт — 24 октября, source303 / runtime в работе

ARM64 DMG303 проверен: `/Users/vitaly/voboost/local-builds-new/beta1-current303/voboost-installer-1.0.0-beta1-arm64.dmg`,9 877 675 байт, SHA e09bd0a1723caf6c50caf5eaee963bb945ba5be05599c4fb1aec9056a4615d6d; manifest bd42c7e7e4b79590261d7c0e8cdf42a88f154859b251dc8189ddd4f2aafaa533. Полный sourcebuild, production ARM build, mount/codesign/33payload/offline/direct-iokit/rolefixtures PASS. Включено исправление install implant mode0700 и verified recovery-mode. Actual original298 recovery подтвердил same-product mode600→700; автоматический genuine QNX guest-reset gen1→2 и network/UTC PASS без assistedAPI. Полный uninstall не завершён: original298 ELF получил loader RELRO errno87. Отдельный подписанный2LOAD diagnostic сохраняет RELRO и проходит этот этап; последний ldd ENOMEM повторяется на штатнойcrypto приdlclose, поэтому не доказывает нехваткуRAM/отказcandidate. Прямой preload witness ещё в работе. Original298 payload/keys/authority не подменялись. Restore-offline только спроектирован, не применён из-заauto-review и ожидания отдельного разрешения. Runtime launcher/phone/Outing на303 ещё NOT_RUN. Доказательства: plans/2026-10-24/qnx303/, qnx303-layout/, qnx303-executable-mode-repair-freeze.md и plans/2026-10-23/beta1-integration/dmg303-verification.json.

### История — Предыдущий контрольный пункт — 24 октября, source302 / runtime в работе

ARM64 DMG302 собран и проверен: `/Users/vitaly/voboost/local-builds-new/beta1-current302/voboost-installer-1.0.0-beta1-arm64.dmg`,9 873 974 байта, SHA256 e56e0e47eeeeb28efb5fe37c2ce65516461adf4ef4783459c1ac237ffbacd2eb; manifest f4d6afd25302bdb5ec48bb6709536b0cdcd70cabeb42e5bdd55552a6098df91a. Реальный read-only mount, codesign strict, ARM64,33 payload files, offline closure, direct-iokit и3role fixtures PASS. Изменения после301: универсальный выбор inset adaptive foreground над отдельно объявленным opaque flat background (Lime actual APK + regression55 PASS, review CLEAN, metal cachev47); QNX off-frame identity/exactA9 profile и3pthread imports (target checks/link/independent reviews сохранены). Production installer бинарник не менялся и использован повторно. Runtime302 NOT RUN;301 также не установлен. Phone current-provider assessment12 tests PASS, PBAP/имя/приборка остаются NOT_RUN. Полный QNX цикл ожидает cold-accepted runtime Voyemu; старый CLI запускать запрещено владельцем Voyemu. Доказательства: plans/2026-10-23/beta1-integration/dmg302-verification.json и plans/2026-10-24/development-build302-retry.log.

Runtime302,24 октября: исправленная машина Voyemu передана и запущена; gen1 сеть/UTC PASS, explicit cluster.restart1→2 восстановил сеть. Original298 recovery запросил native reboot; подтверждены exit0 и host flush, затем root assisted restart2→3. Signed restoration не получена, CLI exit1; authority/pairing сохранены. Диагностика выявила product defect: installed implant0600, ldd PermissionDenied, CLI0700 разрешает все зависимости включая exact OEM crypto. Ring содержит target rpcd preloadFATAL; Voyemu подтвердил rpcd как намеренный renderer target. Узкое signed same-SHA mode600→700 recovery и исправление новых installs проходят tests/review;302 **не устанавливать**, пока new-install mode не исправлен в следующем продукте. Phone/Outing harness исправлены по actual provider wrapper result.value.data,14+14 tests PASS; legacy flat wrapper отвергается. Evidence: plans/2026-10-24/qnx302/.

### История — Предыдущий контрольный пункт — 23 октября, source301

ARM64 DMG301 собран из замороженных исходников и проверен: 9 873 199 байт, SHA-256 dc88f9cbb2632822a5cfb2d76798aad717fd1b9c9609fb6fab11513d50eefdc4; manifest SHA-256 975a450e92e808221d53583d749968c527d338149d78d72c528834d4a3a3844b. Подпись, архитектура,33 файла payload, verify-package, direct-iokit и три role fixtures прошли после реального read-only mount. Product и installer release builds завершены с exit0. Включены recovery phase diagnostics, courier wake, системный язык/A9 English fallback и геометрия штатных background-иконок. Общий JVM-прогон прошёл после устранения параллельной записи outputs; stock22 и app courier8 targeted tests passed. Intel не собирается. Доказательства: plans/2026-10-23/beta1-integration/dmg301-verification.json. Runtime301 ещё не выполнен: исходный298 остаётся в незавершённом удалении QNX. Android штатно синхронизирован и остановлен; QNX RAM и диски сохранены для перехода на исправленный Voyemu runtime. Original298 authority не меняется.

| Направление | Свежие доказательства | Осталось до приёмки |
| --- | --- | --- |
| Launcher / Applications | Host-проверки конфигурации, раскрытия настроек и replay прошли; устранены повторные manager queries при bind. Канонический daemon:760 passed,2 ignored | Runtime новых секций/checkbox/масштаба/display/stop/uninstall; original app preferences end-to-end исправлены:46 Java+7 config+22 planner passed; нужна runtime проверка; A11 и реальные папки отдельно |
| Иконка Лайм TV HD | В302 реализована отдельная adaptive-layer policy;55 tests PASS, независимое review CLEAN. Actual APK выбирает authored-adaptive-vector-separate-alpha, соседние и цветные иконки неизменны | Гостевая проверка302;301 остаётся с прежним дефектом |
| Outing | Исправлены native slider geometry, native close и потеря второй оси Save;34 host tests включая Phone passed | Реальный OEM диалог, Save обеих осей, Cancel/X/Back, повторное открытие и штатное применение |
| Phone | Исправлен bare86 lookup;15 phone tests passed, combined suite passed. Runtime evidence gate:6 tests passed | Настоящий PBAP import без FN, полный номер/имя на multimedia/cluster. Текущий Voyemu API не предоставляет PBAP peer; host gate не подтверждает поведение |
| QNX | Recovery native54+workflow53+cluster46 host tests passed; signed observer301 verified; CLI bootstrap с диагностикой24 passed; исходный298 не меняется | Завершить caller recovery без подмены original298 product, activation, signed uninstall; полный font enable/apply/disable/reboot/restore ещё не принят |
| FEV / PW | Код этих агентов в проходе не менялся; физические логи переданы отдельному исследователю | Причина периодического незапуска не доказана; native phase timestamps и worker/collection timing добавлены. Раздельная ротация уровней:51 focused tests passed; не повторять неизменённые сценарии ради упаковки |
| PW OFF-индикатор | Найден owner light и отдельный gauge fault; A9 property377/data60/strategy19, A11 property303 исследованы | Доказать visual truth table/reversible hook; это исследование, не реализованный патч |
| Русский язык | Дизайн готов; следование системному языку и A9 unknown→English реализуются, targeted host tests прошли | Полный JVM suite и runtime не приняты. QNX locale bridge требует совместимого QNX GNU C++ toolchain; RU ещё не поставляется как готовый агент |
| CI / commits | Предыдущий аудит сохранён, push не выполнялся | После runtime acceptance promote Phone/Outing roster/mirrors/fixtures, leaf pins и clean commit DAG |
| GUI / --force | Полный GUI runtime отложен. --force возвращён в работу: полная переустановка с новой транзакцией, включая другой manifest;6 целевых host-тестов прошли | Runtime --force не принят; повреждённая активная generation требует отдельного repair-пути. Не блокирует QNX |

Текущий QNX recovery301,24 октября: исходный298 по-прежнему ожидает подписанного завершения удаления;301 не установлен. После настоящего QNX reboot API-assisted generation2 выявлены inactive bridge и утечка LD_PRELOAD в инфраструктурные процессы. Сессия635aabe0 штатно завершена после reviewed rescue: получены настоящие guest UTC/flush ACK, QMP pause/quit ACK, host fsync дисков и child exit0; затем официальный session.stop PASS. Доказательства: Voyemu .local/tmp/a9-restart-network-20261024/rescue-execution.jsonl и plans/2026-10-23/qnx301/rescue-clean-stop-result.json. Исправленный runtime/profile ожидается от владельца Voyemu. Acceptance CLI301 прошёл --verify-package; pinned read-only output.voboost provider подготовлен в .work162/providers301/voboost-provider.json для регистрации при следующем старте. Outing harness:12 tests PASS, canonical Save двух осей проверяется отдельно от ещё неподтверждённых wire ACK и поведения. Эти проверки не означают runtime-приёмку.

### История — Исторический контрольный пункт — candidate296

DMG295 для ARM64 и Intel собраны и проверены: подписи, архитектуры и manifest `491ddaedeee13136ef966359f07fedd64beb11bea584af2f5fa118e0ee154a7e`. ARM64 — 9 804 506 байт, Intel — 10 070 279 байт. Source296 и acceptance CLI296 готовы; Android/QNX payload совпадает с295, изменён только installer: host pairing credentials очищаются после durable authority, signed Pending и finish-install ACK. 37 release-тестов и независимое ревью прошли. DMG296 готовы: ARM64 — 9 807 534 байта, Intel — 10 077 391 байт. Подписи, точные архитектуры и33 файла payload проверены; ARM verify-package/direct-iokit/role roots прошли. Доказательства: plans/2026-10-23/beta1-integration/dmg296-verification.json. Runtime295/296 и Intel runtime не проверены. Локальные кандидаты подписаны отдельной development authority243.

Runtime292 подтвердил сохранение порядка лаунчера после перезагрузки и штатное CLI-удаление с девятью проверками отсутствия. QNX bootstrap остановился на доказанной ошибке native entropy unavailable; проверка OEM `/var` прошла. Voyemu восстановил штатный random provider в живой сессии (PID20922400), повторный signed helper подтвердил entropy proof. Это диагностическое восстановление, а не постоянная правка образа и не полный QNX-цикл. Машина затем штатно остановлена; постоянную правку provider готовит владелец Voyemu. Доказательства: `plans/2026-10-23/qnx292/entropy-provider-proof/`, `qnx292/uninstall-before294.log` и отчёты в `plans/2026-10-23/`.

| Направление | Подтверждено | Следующий обязательный шаг |
| --- | --- | --- |
| Launcher | Runtime292: Applications выше Network, Voboost ниже System; четыре квадрата открывают лаунчер. Voboost Fixture скрывается и возвращается; после reboot сохранены все18 наблюдаемых позиций без дубликатов. Landscape, ru_RU, DPI240 подтверждены; отключение вернуло свойства существующей activity, обычный запуск плиткой применил их снова | В294 включены исправления начального содержимого Applications и повторного применения к уже открытой activity; нужна runtime-проверка. Штатная папка без реального OEM baseline и A11 runtime не подтверждены |
| Outing | Executor включает VehicleSetting; исправление асинхронного ACK, rejection, timeout и отмены прошло5 host-тестов и review, включено в294 | Реальная кнопка в режиме Outing, редактор, подтверждённая запись, закрытие при выходе и отключении; runtime294 ещё не выполнен |
| Phone | Runtime288: окно BluetoothPhone без подключённого peer; оба target оставались waiting/target-waiting, внедрение не подтверждено. A9/A11 hooks и lifecycle прошли14 host-тестов; OEM encoder сохраняет пробелы и UTF-8 в пяти host-проверках | PBAP, настоящий звонок, reconnect, имя на приборке и A11 runtime не проверены; отдельного QNX phone-модуля нет |
| QNX | OEM /var contract прошёл runtime292. Причина bootstrap локализована: отсутствовал штатный entropy provider; после диагностического запуска signed helper прошёл entropy proof. Finalizer, Settings worker и recovery проверены host/cross-build | Постоянное восстановление provider в Voyemu; затем официальная установка, семь стадий font enable/apply/disable/reboot и удаление после нового kernel boot с проверенным отсутствием. Полный цикл ещё не пройден; A11 QNX отдельно не принят |
| Удаление | Обычное CLI-удаление292 прошло девять absence checks (`qnx292/uninstall-before294.log`) | Полный QNX uninstall и автономное удаление из Settings на итоговом кандидате ещё не приняты |
| CI | Build-only workflow, QNX link interfaces и shared qconn checkout подготовлены. Историческая запись подтверждает загрузку пяти новых CI-only secrets и удаление их временных локальных копий; публичные build-only roots настроены | Текущий GitHub token невалиден: свежая metadata-проверка secrets не выполнена. Publication trust отдельно unprovisioned. Финальные component pins и первый удалённый CI-run ещё не подтверждены |
| Git | Восемь репозиториев ранее прошли diff --check; состав новых исходников и тестов сохранён в workspace inventory | После runtime-приёмки обновить diff-аудит и pins, показать состав и подготовить один beta1-коммит на репозиторий; без push |

Известный дефект `--force`: восстановление повреждённого активного поколения не реализовано. Новый future-regression `offline_full_candidate_reconstructs_with_a_damaged_unchanged_agent_payload` помечен `ignored` до разрешённого repair-hook; обычный CI его не выполняет (**NOT_RUN**, не PASS). Реальный RED сохранён в `.work162/force-damaged-generation-red.log`, design — `plans/2026-10-24/2026-10-24-00-20-force-generation-repair-design.md`. Существующие force-тесты и production verification не ослаблены.

GUI отложен по указанию пользователя. `--force` возвращён в работу как неблокирующая задача; проверяется восстановление повреждённого активного поколения. Неизменённые FEV/PW не прогоняются повторно из-за упаковки; нестабильный запуск FEV на машине требует диагностики именно на машине. Трёхпальцевый жест остаётся непроверенным: текущий JSON API не выражает одновременный multi-touch. Host-тесты и диагностические исправления не заменяют гостевую приёмку.

### История — Исторический контрольный пункт — 22 октября, candidate282

Кандидат280 собран для ARM64/Intel и проверен hdiutil, но не готов к передаче: GUI выявил блокеры переустановки и Restore. Runtime281 прошёл чистую GUI-установку, точную независимую healthy-проверку, завершение/повторное открытие GUI и все три режима запуска. Restore281 выявил неверное расположение QNX runtime; исправление с реальной root/generation filesystem regression прошло48 host-тестов и ещё требует гостевой проверки в282. Прежние FAIL сохранены в workspace plans/2026-10-22/gui280/ и gui281/; они не переписываются в PASS.

| Проверка | Состояние и доказательства | Осталось |
| --- | --- | --- |
| Launcher hide/return/order | PASS279: скрытие и возврат пользовательского приложения; перемещение и сохранение после reboot | Штатная папка: NOT RUN, нет реального OEM baseline; не подменять synthetic insert |
| Application windows | PASS279: portrait fixture фактически landscape, ru-RU, DPI240, без пересечения dock/status | Отключение и возврат исходных свойств |
| font-cyrillic Video | PASS282: пять warm и один cold reboot, каждый с новым boot и текущим manifest/endpoint | Историческая причина FAIL279 не доказана; не объявлять объяснённой |
| GUI обычный путь | PASS281: установка, Finish/reopen, exact healthy, hidden/none/interface; все накопленные вехи видны | Restore282, uninstall282 и визуальная вычитка итоговых окон |
| GUI runner | 11 пользовательских кейсов,28 host-тестов; ожидание закрытия лога вместо гонки; отдельная пауза перед Restore | Native export/ошибки подключения/degraded/fault cases отмечать по реальному доказательству, не выдавать SKIP за PASS |
| Переустановка | Host regression: та же стадия сохраняется; при потере admission/Enforcing-ответа операция наблюдается без повторного применения; initial root/remount/permissive возвращается в Enforcing | Реальная same-content force, degraded и повтор после неподтверждённого upload |
| Restore | Native282 завершил restore-complete/healthy;48 host-тестов role/path fixes | GUI observer отклонил boot epoch; добавлена точная диагностика пары, нужен свежий цикл |
| Удаление | PASS280: GUI удалил279, независимое absent; CLI emergency после failed Restore вернул исходную policy, Enforcing и9absence checks | Финальная GUI-проверка и Settings uninstall на итоговом продукте |
| Diagnostic CLI crash | Воспроизведён SIGABRT при --status без GUI test port; исправлено: CLI exit0 без порта, неверный GUI config exit2 | Исправление включается в следующую итоговую упаковку; production не содержит gui-test |
| QNX | Исправлены K slot/все readers/JNI/rebind и Java replay; обе ABI и host gates PASS. Font FT success/disable drain/sticky failure и persisted startup проверены host; точная Free6.0.3 совместимость подтверждена | Полный runtime NOT RUN: combined build Voyemu прерван graphics worker RPC header zero; исправление lifecycle отдельно. Нет автоматического gauge restart |
| CI / Git | Build-only CI подготовка и один локальный beta1-коммит остаются целями, push запрещён | Обновить устаревшие ADB evidence gates, проверить окончательные источники, чистые деревья/коммиты после runtime-приёмки |

FEV/PW и принятый материал иконок не перепроверяются ради неизменённых упаковочных деталей. Повтор требуется при изменении затрагивающего их runtime/контракта. Отложенные36 агентов исследованы отдельно; Outing имеет приоритет следующей разработки, но не добавляется незаметно в стабилизируемые семь.

### История — Историческая интеграция274 — 22 октября

Runtime274: подписанный комплект установлен штатным CLI, обе перезагрузки установки завершены. Launcher и Settings подключены; `system_server` ещё не подключён. Сохранение visibility подтверждено, но фактический hide/restore не принят: найдено устаревание списка уже созданного OEM adapter. Исправление rebind проверено 9 Robolectric-тестами (0 failures, 0 skipped), требуется новая сборка и гостевая проверка. Пользователь отдельно разрешил узкие SELinux-права для `system_server`; Enforcing, запрет kill и записи в proc сохраняются.

Штатные папки отсутствуют в сохранённых OEM списках обоих экранов; H97A их поддерживает. Проверка папок остаётся NOT RUN до подготовки реального штатного списка, synthetic agent insert не считается проверкой.

Это новый объём после проверенного269; прежние результаты ниже относятся к своим сборкам и не подтверждают новые функции.

- [x] Host: canonical visibility/inventory `agent-launcher`, запись через daemon ACK, stale request и lifetime fences — 88 проверок.
- [ ] Общая сборка `agent-launcher`: Applications, штатные папки, скрытые позиции, landscape, ru-RU и DPI240.
- [ ] Voyemu A9: скрытие/возврат пользовательского приложения и штатной папки, порядок после перемещения видимых элементов и перезагрузки.
- [ ] Voyemu A9: фактическая ориентация портретного приложения, locale/density и границы окна; отключение без перезапуска system_server.
- [ ] Повторная переустановка с проверенной подготовкой нового stage; сохранение настроек и файлов восстановления.
- [x] Host: раздельные ARM/Intel staging и команды упаковки — 24 проверки, внешний ADB в macOS payload запрещён.
- [ ] Итоговые ARM/Intel DMG: сборка, inventory, подписи, размер, GUI и CLI acceptance.
- [ ] QNX: полная подписанная поставка, provisioning и фактический гостевой запуск; cross-build не является runtime-приёмкой.
- [ ] Повторная гигиена репозиториев и один локальный beta1-коммит без push после приёмки исходников.

### История — Runtime275 и исправление276

- [x] Общий signed closure275 собран, manifest b0f625e077580b220e48b2d54fc6d5daee2a492f2a0fbfb04e3b5476d2ea2648; exact offline closure CLI verify PASS.
- [x] Штатный CLI uninstall274: все9absence checks PASS13:21:35.
- [ ] Clean CLI install275: файлы/APK verified, readiness FAIL (panic quarantine после native SIGSEGV system_server); установка не принята.
- [x] Причина crash локализована: Dobby memcpy в PROT_NONE trampoline после unchecked mprotect. Четыре tombstone сохранены, точный native stack символизирован.
- [ ] Checked mprotect + propagation и отдельно разрешённое `system_server self:process execmem`; signed276, чистая установка и повтор всех launcher runtime проверок.
- [x] Host reinstall read-only InspectInstalled admission regression PASS; definitive JSON admission rejection regression PASS. Guest повторная переустановка ещё не принята.

[Подробный результат275](../../plans/2026-10-22/launcher275/runtime-result.md) находится в workspace plans; log paths приведены там. На время исправления VM штатно остановлена.

### История — Упрощение установщика268 и ZIP269

Каждый английский документ лицензии открывается отдельным пунктом native menu Licenses в простом окне полного текста с прокруткой. Поиск, выбор документа внутри окна, вводный текст и локализация этого окна удалены; справочный русский перевод MIT не показывается в меню. Проверенные license bytes, bounded reads, digest checks и разделение разрешений сохранены.

Экспорт логов269 использует tar.xz со сжатием уровня9 системными средствами ОС. Прямые зависимости zip/flate2 удалены из установщика; архив содержит те же manifest.json, records.jsonl и context.json. Сохраняются лимиты32 MiB payload/64 KiB manifest, приватные временные данные, timeout120s, bounded stderr и публикация без перезаписи. Host runner декодирует системным XZ и строго проверяет USTAR, все три элемента, пределы и identity/source/freshness. Прежний ZIP consumer удалён.

ICNS содержит одно представление1024 вместо десяти: 176 956 →74 946 байт. AppKit успешно декодирует и рисует его в семи размерах16–1024; рендеры меньших размеров отличаются от прежних отдельных растров. SVG и исходный PNG сохранены. Промежуточная упаковка268 прошла mount/signature/inventory verification. Окончательная упаковка269 и её GUI-проверка фиксируются в plans/2026-10-21/runtime269-build/, а не объявляются пройденными этим описанием.

В267 штатная установка packaged CLI в Voyemu A9 завершилась healthy; удаление кнопкой Settings после перезагрузки дало route=absent. Эмулятор остановлен. Эти проверки не объявляют заново пройденными неизменённые FEV/PW или полный функциональный цикл.

### История — Минимизация исполняемых файлов267

В установщике профиль s заменён на z, в native Rust общий профиль 3 заменён на z. Для C SELinux применяется Oz ко всем translation units, включая main, плюс отдельные секции функций/данных и удаление недостижимых секций линкером. Алгоритмы, диагностика, криптография, Tauri features и Cargo.lock не менялись; эксперимент с удалением logging-зависимостей не дал экономии и откатан. Исправлены существующие тестовые несоответствия: ожидаемый permission языка меню и заголовки host SELinux, соответствующие prepared source producer.

Измеренная universal-кандидатура установщика занимает 8 914 560 байт вместо 10 813 152. Native unsigned candidate: daemon 2 101 120, install 965 120, restore 897 744, uninstall 803 088, SELinux 533 248 байт. Это размеры отдельных файлов, а не итогового DMG. Итоговый267, его реальная упаковка, штатная установка/удаление и финальная гигиена подтверждаются только отдельными отчётами plans/2026-10-21/runtime267-build/; этот раздел сам по себе не объявляет их пройденными.

Исследование API ADB и общей native-библиотеки отделено от текущей сборки. Beta1 сохраняет испытанный ADB35.0.1 и самостоятельные root-программы; исследование ADB не блокирует передачу DMG внешнему тестеру.

### История — Минимизация265 и заключительная упаковка266

В265 семь PNG заменены в интерфейсе на 12 WebP (2 789 979 →145 584 байт); исходные PNG сохранены в репозитории. ICNS после ImageOptim занимает 176 956 байт вместо 356 638, все 10 представлений, включая Retina, пиксели и цветовые метаданные проверены. Общие DEX больше не дублируются на каждый процесс: 9 файлов вместо 54 при сохранении всех 53 целевых строк. APK уменьшен с сохранением полного OTA courier; одинаковые полные license bodies хранятся один раз с attribution mapping.

Промежуточный DMG265 размером 18 733 136 байт проверен после настоящего mount. Его CLI установил продукт в выделенный Voyemu A9; после перезагрузки management сообщил healthy, демон запущен, общий DEX шрифта активен в нескольких процессах, вкладка Settings найдена через JSON API. Штатное CLI-удаление завершилось всеми 9 проверками отсутствия. VM остановлена. Это целевая приёмка изменённых catalog/install boundaries, а не новый полный27-case PASS; FEV/PW ради упаковки не повторялись. Доказательства: `plans/2026-10-21/runtime265-build/`.

Окно «Лицензии» отдельно проверено в настоящем GUI: открытие без активации окна, русский интерфейс, чтение всех 13 точных packaged texts включая ADB, выбор большого Rust notice и снимок окна. Отдельный source companion убран из локального и CI вывода; внутренний verified source input сохранён. Две ошибки обработки иконок закрыты отдельно: консервативный fallback для непрозрачной векторной плашки с AA-кромкой и защита от деления на ноль при недостатке образцов фона. Все 65 профильных проверок прошли; 65 PNG тестовых APK сохранены побайтно. Материал и освещение иконок не менялись. Заключительная упаковка266 должна повторно подтвердить состав, подписи, хеши и гигиену 8 репозиториев; запись этого раздела не объявляет ещё не собранный266 готовым.

### История — Дополнительная GUI-приёмка263 и упаковка264

Пользователь выбрал вариант «Упрощённый объём» для всех пяти иллюстраций подключения. Настоящие снимки Hardware/Software version в обеих темах сохранены без изменений. Открытие лога больше не размонтирует содержимое шага: текущая страница инструкции и введённые значения сохраняются, скрытые контролы недоступны. Регрессионный тест воспроизводит прежний сброс; GUI-runner проверяет возврат к той же странице.

GUI263 проверен: штатный цикл установки, управления и удаления дал11 применимых PASS и3 SKIP в `plans/2026-10-21/runtime263-build/gui-normal/report.json`; отдельные `gui-transport-dark/report.json` и `gui-no-device-dark/report.json` дали по4 PASS. Итого19 PASS,0 FAIL и3 SKIP;103 стабильных снимка настоящих окон собраны в `plans/2026-10-21/runtime263-build/GUI-GALLERY.md`. ФизическийUSB и естественное время фоновых WebKit-анимаций не подтверждены. После проверки VM выключена.

В264 изменена упаковка macOS: в DMG нет архивов и деревьев исходников. Соответствующие исходники проверяются как внутренний вход сборки; отдельный архив рядом с установщиком не выдаётся. Лицензии и notices остаются в приложении. Повтор установки, FEV иGUI не требуется при неизменных соответствующих компонентах. Для264 требуется отдельно проверить настоящий mount, состав контейнера, packaged CLI и отсутствие исходников; до сохранения этих наблюдений PASS264 не заявляется. Прежние FAIL сохраняются без изменения.

### История — Дополнительная GUI-приёмка260 и261

Проход260 выполнен:11 применимых проверок штатногоGUI-цикла,4 проверки ADB-транспорта и4 проверки без устройства прошли. Логи,JSON иPNG находятся в `plans/2026-10-21/runtime260-build/` рабочего пространства. ФизическийUSB и естественное время фоновых WebKit-анимаций этим проходом не подтверждаются.

| № | Замечание | Подтверждение260 |
| --- | --- | --- |
| 1 | Копирование лога доступно на каждом экране | PASS:101 unit-тест; вJSON кнопка видима с закрытым и открытым логом, включаяWelcome |
| 2 | Копирование рядом с кнопкой «Лог» | PASS:40px иконная кнопка, расстояние между границами4px |
| 3 | Узнаваемая графика кабелей и портов | Кабели приняты; две панели сверены с фото A9/A11,PNG просмотрены в обеих темах иGUI; варианты стиля представлены отдельно |
| 4 | Нет лишней ссылки «Открыть лог» после установки | PASS: unit иJSON, только общий переключатель в нижней полосе |
| 5 | Установка без технических абзацев | PASS: только просьба не отключать кабель и автомобиль |
| 6 | Нет Full details | PASS: компактный лог без чекбокса; копируется именно видимый текст, полная диагностика остаётся в архиве |
| 7 | Понятный экран перед установкой | PASS: EN/RU вычитаны, полныйMIT побайтово неизменён |
| 8 | У удаления нет красной рамки | PASS: обычная общая карточка управления,PNG просмотрен |
| 9 | Уведомление не сдвигает содержимое | PASS: Toast в портале; координаты версии до/после трёх смен режима совпали |
| 10 | Нет тавтологии на первом экране | PASS: EN/RU вычитка иPNG |
| 11 | Сохраняются крупные этапы установки | PASS: накопленный список реально полученных backend-вех наблюдался вGUI |

Дополнительное измерение260 обнаружило11px прокрутки на странице двухUSB-панелей:545px видимой области против556px содержимого. Исправление261 сокращает пустую высоту картинок180→140px; диагностический эксперимент дал545/545px. JSON-наблюдатель теперь включаетmain; регрессия воспроизводит прежний пропуск контейнера. Итоговая проверка именно нового пакета261 сохраняется в `plans/2026-10-21/runtime261-build/`; установка и удаление повторяются только при изменении соответствующего кода.

### История — Контрольный пункт 21 октября 2026

Пакет257 установлен штатным CLI;43 сохранённых пользовательских APK, включая SpeedCN, проверены тремя группами. Все страницы metal-лаунчера сохранены и просмотрены. Системная клавиатура Voyah исключена. Принятый мягкий градиент внутренних акцентов сохранён. Фактические хеши и снимки: `plans/2026-10-21/runtime257-build/RESULT.md` рабочего пространства.

| Область | Подтверждённое состояние | Приёмка следующего пакета |
| --- | --- | --- |
| FEV/PW, раннее применение, языки, шрифты, клавиатуры, launcher order | Предыдущая функциональная приёмка сохранена; эти компоненты не меняются ради упаковки GUI | Повторять только при изменении соответствующих компонентов; физическая приёмка отдельно |
| Settings: конфигурация запуска и удаление | PASS248: запись без немедленного применения, индикатор и девять absence checks | В258 этот код не меняется |
| GUI install257 | PASS: EN/RU license, Log, установка/Finish, hidden/none/interface; independent exact health | PASS258: все шесть вех наблюдались в реальном UI; управление помещается без прокрутки |
| GUI Restore257 | PASS: отмена и настоящее восстановление, independent exact health | PASS258: отмена и восстановление, independent exact health |
| GUI Uninstall257 | PASS: отмена и удаление за29,3с до проверенного отсутствия после reboot | PASS258: отмена и удаление; независимое отсутствие после reboot |
| Фоновый GUI-стенд | Фоновый WebKit иногда не завершает анимацию; interruption и unstable capture не считаются PASS | Только обычные фоновые окна; always-on-top запрещён; animation advance не доказывает timing |
| Иконка macOS | Обнаружено удаление ICNS при пересоздании Resources; исправлено, TS16/Rust8 PASS | PASS258: иконка в DMG, точный SHA и plist проверены; NSWorkspace разрешает её через macOS |
| GUI: ход и размер окна | Новые замечания пользователя включены в258 | В258 реализовано; основные обычные экраны проверены через JSON и PNG, лог и лицензия прокручиваются отдельно |
| Ошибки соединения и нет устройства | PASS249, отдельные transport/no-device receipts | Изменённые экраны проверяются отдельно; физический USB не моделируется ADB disconnect |
| CI build-only | Локальный аудит21октября: product5/5 и installer8/8, YAML/pins/public roots PASS | Удалённая сборка только после отдельного push и разрешённого запуска |
| Git | Аудит21октября:8clean, один beta1-коммит, дата04.10.2026 20:53+03:00; recovery refs | Перед упаковкой повторно проверять pins и clean, один commit поверх реальных remote main; без push |

Исходники фиксируются до сборки. Итоговый хеш пакета, runtime-приёмка и окончательная гигиена сохраняются отдельно в `plans/2026-10-21/beta1-final258/` рабочего пространства; запись этого файла сама по себе не заменяет проверку готового пакета. Последующая упаковка с обновлённой инструкцией не меняет код установщика или агентов; её отдельные хеши и привязка исходников сохраняются в том же итоговом отчёте. Прежний подробный журнал сохранён там как `checklist-before258.md`; исходные FAIL не переписываются. Полный единый27-case PASS не заявляется: используются конкретные доказательства неизменённых компонентов.


## Поставка и ограничения

Локальный DMG использует явно выбранную development authority. Новые CI-only ключи и публичные корни — отдельный процесс, описанный в [CI-инструкции](BETA1-BUILD-CI.ru.md). После будущего push нужно подтвердить реальный CI-run; локальные тесты его не заменяют. До перехода с dev на CI authority старую версию полностью удаляют прежним установщиком.

Сохранённая попытка установки242 на физическую машину20октября не завершилась: CLI остановился из-за SELinux после reboot. Это не считается успешной установкой. Проверка окончательного DMG на реальном автомобиле и холодное включение остаются физической приёмкой. Инструкция бета-тестеру: [BETA1-TESTER-NOTES.ru.md](BETA1-TESTER-NOTES.ru.md).

Шесть активных кодовых репозиториев используют MIT. Logo сохраняет отдельную прежнюю лицензию; выбор публичных условий логотипа пользователем ещё не завершён. `voboost-license` содержит архивное уведомление и не участвует в сборке.

## Исполняемые кейсы

Текущие ожидания, исходные 27 ID. Исходные27 ID сохранены. Cases6/7 — базовый системный OEM EN/ZH picker с возвратом наблюдённого исходного языка. Cases20/21/23 — компоненты и startup-контролы в системных EN/ZH и admitted RU; отсутствие RU не превращается в условный PASS. Case22 — системная локаль, вклад i18n-ru, persistence и фактические Android/QNX gauge/point/light apply/disable receipts. Прежнее требование unchanged OEM locale и измерение удалённого Voboost language radio исключены. Без официального полного proof новый i18n subcheck остаётся NOT_RUN/unavailable и блокирует готовность. Автоматический результат требует receipts точного DMG и загрузки; визуальные subchecks требуют просмотра PNG. Исторические FAIL/PASS и архивные отчёты не изменены; FEV/PW не повторяются ради упаковки, GUI11 и Intel не возвращены в обязательный scope.

| Номер | ID | Проверка |
|---|---|---|
| 1 | `cli-install-first` | Install the exact beta1 kit through the packaged CLI |
| 2 | `cli-uninstall` | Remove beta1 through the packaged CLI |
| 3 | `cli-absence` | Verify all nine absence checks and Enforcing |
| 4 | `cli-install-second` | Install the same exact kit again through the packaged CLI |
| 5 | `settings-tab` | Open OEM Settings and verify the Voboost tab |
| 6 | `language-opposite` | Select the opposite OEM system EN/ZH language, preserving the observed original language, and verify system-driven Voboost captions |
| 7 | `language-return` | Restore the observed original OEM system EN/ZH language and verify system-driven Voboost captions |
| 8 | `agent-enablement-conflict` | Enable available stable agents and verify keyboard-en and keyboard-ru cannot coexist |
| 9 | `font-cyrillic` | Verify actual Cyrillic rendering with the font agent |
| 10 | `keyboard-en` | Verify visible EN keyboard and text input |
| 11 | `keyboard-ru-en` | Verify visible RU/EN keyboard and text input |
| 12 | `launcher-persistence` | Install the approved Voboost Fixture fixture, verify appearance, move it and verify its saved position after reboot |
| 13 | `forced-ev-persistence` | Verify reboot resets Forced EV without the agent and preserves it with the agent |
| 14 | `pedestrian-warning-persistence` | Without the agent, disable Pedestrian Warning and verify reboot enables it; with the agent, verify it remains disabled after reboot |
| 15 | `hidden-tbox-recovery` | Select the actual hidden Settings radio and current description, then verify agents after reboot, tab absence and live TBOX recovery |
| 16 | `none-no-processes` | Select the actual none Settings radio and current description, verify post-reboot Voboost process absence and return only through the computer installer |
| 17 | `settings-uninstall` | Restore interface, require owned Voboost Fixture cleanup after cases12/18, use the Settings uninstall action and verify original clean state |
| 18 | `catalog-classification` | Actual package UID/flags and stock catalog preservation |
| 19 | `navi-input-spacing` | Actual Navi Moscow, Yo and mixed input codepoints/render |
| 20 | `control-layout` | Measure shared checkbox/radio/section layout under OEM EN/ZH and admitted i18n-ru; no Voboost language radio is expected |
| 21 | `action-button-layout` | Verify action padding, wrapping and clicks under OEM EN/ZH and admitted i18n-ru; missing RU proof is not a pass |
| 22 | `chinese-language-persistence` | Verify the actual system language picker, admitted Russian contribution, process/resource locale and reboot persistence; require current Android and QNX gauge/point/light apply/disable readback |
| 23 | `startup-descriptions` | Verify startup descriptions and startup controls under OEM system EN/ZH and admitted i18n-ru; computer-only return from none, cancel and rejected mutation readback |
| 24 | `initial-state-seeding` | Authoritative initial false/true seeding and root readback |
| 25 | `state-outage-recovery` | Source-bound retry/cancel plus actual multi-target load and observed outage |
| 26 | `every-guest-reboot` | Independent exact prepared inventory, Enforcing and UI evidence for every guest boot including installer intermediate boots |
| 27 | `root-boundary` | Root/policy authority and ordinary diagnostic boundary |

### История — работа283

На момент283: строится новая подписанная closure. Реальный force282 выявил две ошибки: production route пропускал root/remount/permissive; затем полный reapply пытался заменить собственный restore helper. Исправления прошли targeted host и38 native tests; реальный force новым продуктом ещё NOT RUN. Предыдущие FAIL не переписываются. Эмулятор остановлен на время подготовки.
