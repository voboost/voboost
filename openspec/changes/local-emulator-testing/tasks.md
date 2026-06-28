# local-emulator-testing

## 1. App-zone paths and plan producer (app-daemon-contract)

- [ ] 1.1 В `PathsAndroid` зафиксировать app-zone `/data/user/0/ru.voboost`
      (он же `/data/data/ru.voboost`) как единственное место записи приложения;
      добавить пути `inject.json`, `inject-status.json`, `staging/`
- [ ] 1.2 Реализовать продюсер плана: `FeatureManager`/`config.yaml` →
      `inject.json` (`version`, `startup`, `disabled`, per-agent
      `enabled` + opaque `config`); atomic write (temp + rename); размер ≤ 1 MiB,
      per-agent `config` ≤ 64 KiB
- [ ] 1.3 Значение `startup:"none"` трактовать как gate (приложение не
      требует инъекций); `disabled:true` — kill-switch на стороне продюсера
- [ ] 1.4 Реализовать читатель `inject-status.json` (atomic read, толерантный
      к partial/in-flight write): `state`, `killed`, `panic`,
      `injections[].{id,process,state}`
- [ ] 1.5 Поверхность статуса в UI (Compose) через `@Preview`; никакого
      прямого вызова инъектора из UI/фич

## 2. Remove direct injection and desktop (app-daemon-contract)

- [ ] 2.1 Удалить ассет `src/main/assets/bin/frida-inject` (≈56 МБ)
- [ ] 2.2 Удалить `FridaManagerAndroid.kt` (`/data/local/tmp/su` + `frida-inject`)
- [ ] 2.3 Удалить `FridaManagerDesktop.kt`, `MainDesktop.kt`,
      `PathsDesktop.kt`, интерфейс `FridaManager.kt`, `FridaAgentManager.kt`,
      `ScriptExtractor.kt`
- [ ] 2.4 Удалить Gradle-таску `downloadFridaInject` и инъекционный смысл
      `runDesktop` (если остаётся — только как no-op/removed)
- [ ] 2.5 Убрать упоминания `VehicleManager*Desktop`, `FridaManager*` из
      `Main.kt`, `MainActivity.kt`, `VoboostService.kt`; `Main` становится
      платформо-независимым оркестратором плана+статуса
- [ ] 2.6 Подтвердить: в `src/` и `build.gradle.kts` не осталось строк
      `frida-inject`, `/data/local/tmp/su`, `FridaManager`, `MainDesktop`,
      `PathsDesktop`, `ScriptExtractor`, `downloadFridaInject`

## 3. APK carrier assets (app-daemon-contract)

- [ ] 3.1 Заменить `bin/frida-inject` на `bin/voboost-inject` (arm64 демон);
      добавить `agents/*.js`, подписанный `manifest.json` + `manifest.sig`,
      `release-manifest.json` + `release-manifest.json.sig`
- [ ] 3.2 Документировать: app НЕ пишет root-zone; ассеты провижинятся в
      `/data/voboost` инсталлером/харнесом, не приложением

## 4. Device provisioning (device-provisioning)

- [ ] 4.1 Зафиксировать root-zone `/data/voboost` (`root:root`, `0700`) с
      поддиректориями `agents/`, `logs/`, `run/` и бинарником демона
- [ ] 4.2 Init-хук `/system/etc/init/voboost.rc` (service, root, restart-on-exit,
      single-instance через pidfile + flock); поддержка manual start для
      быстрых тестов
- [ ] 4.3 SELinux: `setenforce 0` для эмулятора; задокументировать, что на
      устройстве — init-домен с политикой
- [ ] 4.4 Скрипт проверки готовности устройства (root, zone, hook, демон,
      SELinux), exit 0/non-zero

## 5. Emulator test harness (emulator-test-harness)

- [ ] 5.1 `tools/emulator/boot.sh`: `emulator -avd free -no-snapshot`; `adb root`;
      `setenforce 0`; ждать `boot_completed`
- [ ] 5.2 `tools/emulator/provision.sh`: raw-adb push демона+агентов+манифеста
      в `/data/voboost` (700); init-хук ИЛИ manual start; установка stub-APK + app
- [ ] 5.3 `tools/emulator/install.sh`: headless `voboost-install --auto-install
      <apk> -s <serial>` (провижининг + smoke-test инсталлера)
- [ ] 5.4 `tools/emulator/run-test.sh`: запись `inject.json`; старт target'ов;
      poll `inject-status.json` + `/data/voboost/logs/*.log`; assert по сценариям
      `integration-tests.md`; silent on success, exit code для CI
- [ ] 5.5 Перенести/адаптировать сценарии `integration-tests.md` (10): spawn-gate,
      attach, js/native routing, resume, quarantine, kill-switch, startup-gate,
      boot-gate, config-delivery, coexist-skip

## 6. Tests and validation

- [ ] 6.1 Robolectric/JUnit: план-транслятор (`config.yaml`→`inject.json`),
      статус-парсер, atomic write/read, размерные лимиты (negative)
- [ ] 6.2 Compose `@Preview` для статус-поверхности
- [ ] 6.3 `npx @fission-ai/openspec validate local-emulator-testing --strict`
      проходит; `--all` проходит
- [ ] 6.4 CI: gradle-таска `emulatorTest` гоняет `run-test.sh` против AVD `free`
