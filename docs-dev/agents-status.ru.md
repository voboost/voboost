# Статус агентов

[English](agents-status.md) | **Русский**

Страница создаётся из метаданных и применённого OpenSpec в соседнем репозитории `voboost-agents`. Ручного второго списка статусов нет.

Состав: **18 stable**, **0 unstable**, **0 planned**.

Обозначения применимости: chip — процессор `8155` или `nxp`; vehicle — `free` или `dreamer`; версия Android; powertrain — `ev` или `hybrid`; market — `china` или `global`.

Колонка «Следующий вклад» показывает число требований OpenSpec со статусом Unverified. Если такого блока нет, planned-агент всё равно требует проверки на устройстве и в автомобиле перед переводом в stable.

| Агент | Статус | Процессы | Применимость | Следующий вклад | Документы |
| --- | --- | --- | --- | --- | --- |
| `font-cyrillic` | `stable` | 42 процессов; точный список — в документе агента | 8155/nxp; free/dreamer; A9/11; ev/hybrid; china | Открытых проверок: 4 | [Документ](../../voboost-agents/src/main/java/ru/voboost/agents/font/cyrillic/FontCyrillicAgent.ru.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-font-cyrillic/spec.md) |
| `i18n-en` | `stable` | 4 процессов; точный список — в документе агента | 8155; free; A9/11; ev/hybrid; china | Открытых проверок: 2 | [Документ](../../voboost-agents/src/main/java/ru/voboost/agents/i18n/en/I18nEnAgent.ru.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-i18n-en/spec.md) |
| `i18n-ru` | `stable` | 7 процессов; точный список — в документе агента | 8155; free; A9/11; ev/hybrid; china | Открытых проверок: 5 | [Документ](../../voboost-agents/src/main/java/ru/voboost/agents/i18n/ru/I18nRuAgent.ru.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-i18n-ru/spec.md) |
| `keyboard-en` | `stable` | `com.qinggan.app.qgime`, `com.qinggan.app.qgime.second` | 8155/nxp; free/dreamer; A9/11; ev/hybrid; china | Открытых проверок: 4 | [Документ](../../voboost-agents/src/main/java/ru/voboost/agents/keyboard/en/KeyboardEnAgent.ru.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-keyboard-en/spec.md) |
| `keyboard-ru` | `stable` | `com.qinggan.app.qgime`, `com.qinggan.app.qgime.second` | 8155/nxp; free/dreamer; A9/11; ev/hybrid; china | Открытых проверок: 4 | [Документ](../../voboost-agents/src/main/java/ru/voboost/agents/keyboard/ru/KeyboardRuAgent.ru.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-keyboard-ru/spec.md) |
| `launcher` | `stable` | 4 процессов; точный список — в документе агента | 8155/nxp; free/dreamer; A9/11; ev/hybrid; china | Открытых проверок: 11 | [Документ](../../voboost-agents/src/main/java/ru/voboost/agents/launcher/apps/LauncherAgent.ru.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-launcher/spec.md) |
| `login-activation-skip` | `stable` | `com.qinggan.app.login` | 8155; free/dreamer; A9/11; ev/hybrid; china | Открытых проверок: 1 | [Документ](../../voboost-agents/src/main/java/ru/voboost/agents/login/activationskip/LoginActivationSkipAgent.ru.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-login-activation-skip/spec.md) |
| `media-apps` | `stable` | `com.qinggan.app.launcher`, `com.qinggan.media` | 8155; free; A9/11; hybrid; china/global | Открытых проверок: 1 | [Документ](../../voboost-agents/src/main/java/ru/voboost/agents/media/apps/MediaAppsAgent.ru.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-media-apps/spec.md) |
| `media-popup-external-apps` | `stable` | `com.qinggan.app.islandapp` | 8155; free/dreamer; A9/11; ev/hybrid; china/global | Открытых проверок: 3 | [Документ](../../voboost-agents/src/main/java/ru/voboost/agents/media/popupexternalapps/MediaPopupExternalAppsAgent.ru.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-media-popup-external-apps/spec.md) |
| `phone` | `stable` | `com.qinggan.bluetoothphone`, `com.android.bluetooth`, `com.qinggan.systempolicy` | 8155; free; A9/11; ev/hybrid; china | Открытых проверок: 2 | [Документ](../../voboost-agents/src/main/java/ru/voboost/agents/phone/PhoneAgent.ru.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-phone/spec.md) |
| `settings` | `stable` | `com.qinggan.app.setting` | 8155/nxp; free/dreamer; A9/11; ev/hybrid; china/global | Открытых проверок: 8 | [Документ](../../voboost-agents/src/main/java/ru/voboost/agents/settings/SettingsAgent.ru.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-settings/spec.md) |
| `settings-engineering-menu` | `stable` | `com.qinggan.app.setting` | 8155; free; A9/11; ev/hybrid; china | Открытых проверок: 1 | [Документ](../../voboost-agents/src/main/java/ru/voboost/agents/settings/engineeringmenu/SettingsEngineeringMenuAgent.ru.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-settings-engineering-menu/spec.md) |
| `steering-media-app-switcher` | `stable` | `com.qinggan.keymanager.service` | 8155/nxp; free/dreamer; A9/11; ev/hybrid; china/global | Открытых проверок: 1 | [Документ](../../voboost-agents/src/main/java/ru/voboost/agents/steering/mediaappswitcher/SteeringMediaAppSwitcherAgent.ru.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-steering-media-app-switcher/spec.md) |
| `steering-media-session` | `stable` | `com.qinggan.keymanager.service` | 8155/nxp; free/dreamer; A9/11; ev/hybrid; china/global | Открытых проверок: 2 | [Документ](../../voboost-agents/src/main/java/ru/voboost/agents/steering/mediasession/SteeringMediaSessionAgent.ru.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-steering-media-session/spec.md) |
| `vehicle-display-auto-lift` | `stable` | `com.qinggan.app.vehiclesetting` | 8155; free; A9; ev/hybrid; china/global | Открытых проверок: 1 | [Документ](../../voboost-agents/src/main/java/ru/voboost/agents/vehicle/display/autolift/VehicleDisplayAutoLiftAgent.ru.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-vehicle-display-auto-lift/spec.md) |
| `vehicle-drive-forced-ev` | `stable` | `com.qinggan.app.vehiclesetting`, `com.qinggan.app.launcher`, `com.qinggan.canbus.service` | 8155; free; A9/11; hybrid; china/global | Открытых проверок: 18 | [Документ](../../voboost-agents/src/main/java/ru/voboost/agents/vehicle/drive/forcedev/VehicleDriveForcedEvAgent.ru.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md) |
| `vehicle-drive-outing` | `stable` | `com.qinggan.app.vehicle`, `com.qinggan.app.vehiclesetting` | 8155; free/dreamer; A9/11; ev/hybrid; china/global | Открытых проверок: 3 | [Документ](../../voboost-agents/src/main/java/ru/voboost/agents/vehicle/drive/outing/VehicleDriveOutingAgent.ru.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-vehicle-drive-outing/spec.md) |
| `vehicle-drive-pedestrian-warning` | `stable` | `com.qinggan.app.vehiclesetting`, `com.qinggan.app.launcher`, `com.qinggan.canbus.service` | 8155; free; A9/11; ev/hybrid; china/global | Открытых проверок: 13 | [Документ](../../voboost-agents/src/main/java/ru/voboost/agents/vehicle/drive/pedestrianwarning/VehicleDrivePedestrianWarningAgent.ru.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md) |

## Непроверенные требования

Ссылки ведут на точные требования OpenSpec. Названия не копируются в этот файл, поэтому источником формулировки остаётся спецификация.

### `font-cyrillic`

- [Непроверенное требование 1](../../voboost-agents/openspec/specs/agent-font-cyrillic/spec.md#unverified-regular-asset-android-callback-integration)
- [Непроверенное требование 2](../../voboost-agents/openspec/specs/agent-font-cyrillic/spec.md#unverified-target-system-wide-font-integration)
- [Непроверенное требование 3](../../voboost-agents/openspec/specs/agent-font-cyrillic/spec.md#unverified-exact-qnx-font-loading)
- [Непроверенное требование 4](../../voboost-agents/openspec/specs/agent-font-cyrillic/spec.md#unverified-actual-navi-focused-paint-and-visible-cyrillic-spacing)

### `i18n-en`

- [Непроверенное требование 1](../../voboost-agents/openspec/specs/agent-i18n-en/spec.md#unverified-independent-english-runtime-admission)
- [Непроверенное требование 2](../../voboost-agents/openspec/specs/agent-i18n-en/spec.md#unverified-oem-selected-locale-runtime-admission)

### `i18n-ru`

- [Непроверенное требование 1](../../voboost-agents/openspec/specs/agent-i18n-ru/spec.md#unverified-multi-owner-source-and-delivery-acceptance)
- [Непроверенное требование 2](../../voboost-agents/openspec/specs/agent-i18n-ru/spec.md#unverified-vehiclesetting-inactive-role-and-fallback-acceptance)
- [Непроверенное требование 3](../../voboost-agents/openspec/specs/agent-i18n-ru/spec.md#unverified-systemui-guard-source-prototype-and-runtime-acceptance)
- [Непроверенное требование 4](../../voboost-agents/openspec/specs/agent-i18n-ru/spec.md#unverified-outside-air-prefix-and-owner-lifecycle-acceptance)
- [Непроверенное требование 5](../../voboost-agents/openspec/specs/agent-i18n-ru/spec.md#unverified-effective-unknown-language-acceptance)

### `keyboard-en`

- [Непроверенное требование 1](../../voboost-agents/openspec/specs/agent-keyboard-en/spec.md#unverified-host-evidence-for-english-keyboard-uses-one-exact-structural-qgime-contract)
- [Непроверенное требование 2](../../voboost-agents/openspec/specs/agent-keyboard-en/spec.md#unverified-host-evidence-for-english-keyboard-coercion-and-presentation-are-exact)
- [Непроверенное требование 3](../../voboost-agents/openspec/specs/agent-keyboard-en/spec.md#unverified-host-evidence-for-english-keyboard-stop-restores-stock-presentation)
- [Непроверенное требование 4](../../voboost-agents/openspec/specs/agent-keyboard-en/spec.md#unverified-target-qgime-integration)

### `keyboard-ru`

- [Непроверенное требование 1](../../voboost-agents/openspec/specs/agent-keyboard-ru/spec.md#unverified-host-evidence-for-russian-keyboard-uses-one-exact-structural-qgime-contract)
- [Непроверенное требование 2](../../voboost-agents/openspec/specs/agent-keyboard-ru/spec.md#unverified-host-evidence-for-russian-layout-assets-and-key-behavior-are-one-owned-state)
- [Непроверенное требование 3](../../voboost-agents/openspec/specs/agent-keyboard-ru/spec.md#unverified-host-evidence-for-russian-keyboard-callback-faults-are-lifecycle-fenced)
- [Непроверенное требование 4](../../voboost-agents/openspec/specs/agent-keyboard-ru/spec.md#unverified-target-qgime-integration)

### `launcher`

- [Непроверенное требование 1](../../voboost-agents/openspec/specs/agent-launcher/spec.md#unverified-screen-specific-styling-on-the-final-oem-displays)
- [Непроверенное требование 2](../../voboost-agents/openspec/specs/agent-launcher/spec.md#unverified-selected-activity-icon-packaged-acceptance)
- [Непроверенное требование 3](../../voboost-agents/openspec/specs/agent-launcher/spec.md#unverified-oem-theme-delivery-and-concurrent-capture)
- [Непроверенное требование 4](../../voboost-agents/openspec/specs/agent-launcher/spec.md#unverified-window-executor-android-bridge)
- [Непроверенное требование 5](../../voboost-agents/openspec/specs/agent-launcher/spec.md#unverified-live-conditional-launcher-locale)
- [Непроверенное требование 6](../../voboost-agents/openspec/specs/agent-launcher/spec.md#unverified-icon-cache-target-lifecycle)
- [Непроверенное требование 7](../../voboost-agents/openspec/specs/agent-launcher/spec.md#unverified-launcher-android-integration)
- [Непроверенное требование 8](../../voboost-agents/openspec/specs/agent-launcher/spec.md#unverified-cache-receipt-target-lifecycle)
- [Непроверенное требование 9](../../voboost-agents/openspec/specs/agent-launcher/spec.md#unverified-native-disclosure-and-package-actions)
- [Непроверенное требование 10](../../voboost-agents/openspec/specs/agent-launcher/spec.md#unverified-cross-repository-canonical-mutation-transport)
- [Непроверенное требование 11](../../voboost-agents/openspec/specs/agent-launcher/spec.md#unverified-configured-application-density-and-display-modes-in-the-guest)

### `login-activation-skip`

- [Непроверенное требование 1](../../voboost-agents/openspec/specs/agent-login-activation-skip/spec.md#unverified-a9-and-a11-login-startup-integration)

### `media-apps`

- [Непроверенное требование 1](../../voboost-agents/openspec/specs/agent-media-apps/spec.md#unverified-selection-jni-and-catalog-integration)

### `media-popup-external-apps`

- [Непроверенное требование 1](../../voboost-agents/openspec/specs/agent-media-popup-external-apps/spec.md#unverified-popup-existing-client-target-integration)
- [Непроверенное требование 2](../../voboost-agents/openspec/specs/agent-media-popup-external-apps/spec.md#unverified-popup-takeover-target-integration)
- [Непроверенное требование 3](../../voboost-agents/openspec/specs/agent-media-popup-external-apps/spec.md#unverified-popup-artwork-target-integration)

### `phone`

- [Непроверенное требование 1](../../voboost-agents/openspec/specs/agent-phone/spec.md#unverified-phone-runtime-integration)
- [Непроверенное требование 2](../../voboost-agents/openspec/specs/agent-phone/spec.md#unverified-phone-date-fallback-vehicle-rendering)

### `settings`

- [Непроверенное требование 1](../../voboost-agents/openspec/specs/agent-settings/spec.md#unverified-daemon-confirmed-republish-on-a-live-process)
- [Непроверенное требование 2](../../voboost-agents/openspec/specs/agent-settings/spec.md#unverified-stock-notification-presentation-in-the-live-settings-process)
- [Непроверенное требование 3](../../voboost-agents/openspec/specs/agent-settings/spec.md#unverified-live-component-derived-settings-spacing)
- [Непроверенное требование 4](../../voboost-agents/openspec/specs/agent-settings/spec.md#unverified-live-content-sized-qinggan-actions)
- [Непроверенное требование 5](../../voboost-agents/openspec/specs/agent-settings/spec.md#unverified-live-settings-mutation-feedback)
- [Непроверенное требование 6](../../voboost-agents/openspec/specs/agent-settings/spec.md#unverified-live-settings-mutation-publication-progress)
- [Непроверенное требование 7](../../voboost-agents/openspec/specs/agent-settings/spec.md#unverified-live-independent-section-metadata)
- [Непроверенное требование 8](../../voboost-agents/openspec/specs/agent-settings/spec.md#unverified-live-queued-tab-margin-cleanup)

### `settings-engineering-menu`

- [Непроверенное требование 1](../../voboost-agents/openspec/specs/agent-settings-engineering-menu/spec.md#unverified-oem-engineering-menu-integration)

### `steering-media-app-switcher`

- [Непроверенное требование 1](../../voboost-agents/openspec/specs/agent-steering-media-app-switcher/spec.md#unverified-switcher-shared-state-target-integration)

### `steering-media-session`

- [Непроверенное требование 1](../../voboost-agents/openspec/specs/agent-steering-media-session/spec.md#unverified-media-press-target-integration)
- [Непроверенное требование 2](../../voboost-agents/openspec/specs/agent-steering-media-session/spec.md#unverified-oem-media-session-authority-and-vehicle-behavior)

### `vehicle-display-auto-lift`

- [Непроверенное требование 1](../../voboost-agents/openspec/specs/agent-vehicle-display-auto-lift/spec.md#unverified-android-drive-preferences-integration)

### `vehicle-drive-forced-ev`

- [Непроверенное требование 1](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-target-forced-ev-startup-application-timing)
- [Непроверенное требование 2](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-host-evidence-for-forced-ev-declarations-compile-into-immutable-signed-definitions)
- [Непроверенное требование 3](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-retired-fev-host-integration-evidence-for-forced-ev-selection-admits-exactly-one-structural-contract)
- [Непроверенное требование 4](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-retired-fev-host-integration-evidence-for-explicit-off-delegates-vehicle-mutation-after-durable-disarm)
- [Непроверенное требование 5](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-native-owned-input-and-physical-off-acceptance)
- [Непроверенное требование 6](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-host-evidence-for-forced-ev-actions-use-bound-handles-one-mutation-and-verified-readback)
- [Непроверенное требование 7](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-target-vehicle-agent-behavior)
- [Непроверенное требование 8](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-retired-fev-host-integration-evidence-for-sticky-intent-distinguishes-explicit-input-from-ordinary-mode-state)
- [Непроверенное требование 9](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-retired-java-controller-evidence-for-forced-ev-startup-state-reads-recover-across-bounded-cycles)
- [Непроверенное требование 10](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-retired-java-controller-evidence-for-forced-ev-observes-acc-power-separately-from-transport)
- [Непроверенное требование 11](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-retired-fev-host-integration-evidence-for-startup-restoration-readiness-follows-vehicle-readback)
- [Непроверенное требование 12](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-current-timing-runtime-acceptance)
- [Непроверенное требование 13](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-retired-java-controller-evidence-for-forced-ev-explicit-off-limits-physical-changes-to-the-declared-release-action)
- [Непроверенное требование 14](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-explicit-off-diagnostic-runtime-integration)
- [Непроверенное требование 15](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-retired-fev-host-integration-evidence-for-free-launcher-drawer-preserves-explicit-switch-intent)
- [Непроверенное требование 16](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-oem-input-during-owner-or-lifecycle-transitions)
- [Непроверенное требование 17](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-installed-electric-drain-and-off-acceptance)
- [Непроверенное требование 18](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-installed-forced-ev-role-acceptance)

### `vehicle-drive-outing`

- [Непроверенное требование 1](../../voboost-agents/openspec/specs/agent-vehicle-drive-outing/spec.md#unverified-outing-physical-policy-acceptance)
- [Непроверенное требование 2](../../voboost-agents/openspec/specs/agent-vehicle-drive-outing/spec.md#unverified-outing-editor-runtime-acceptance)
- [Непроверенное требование 3](../../voboost-agents/openspec/specs/agent-vehicle-drive-outing/spec.md#unverified-native-individual-template-rendering)

### `vehicle-drive-pedestrian-warning`

- [Непроверенное требование 1](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-cluster-pw-visual-runtime-acceptance)
- [Непроверенное требование 2](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-target-pedestrian-warning-startup-application-timing)
- [Непроверенное требование 3](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-retired-java-controller-evidence-for-pedestrian-warning-fatal-failures-are-never-retried)
- [Непроверенное требование 4](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-target-vehicle-agent-behavior)
- [Непроверенное требование 5](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-retired-java-controller-evidence-for-pedestrian-warning-selects-one-declarative-structural-can-contract)
- [Непроверенное требование 6](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-retired-java-controller-evidence-for-pedestrian-warning-restoration-requires-accepted-ack-and-disabled-readback)
- [Непроверенное требование 7](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-retired-java-controller-evidence-for-pedestrian-warning-startup-state-reads-recover-across-bounded-cycles)
- [Непроверенное требование 8](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-retired-java-controller-evidence-for-absent-pedestrian-warning-intent-adopts-the-initial-oem-value)
- [Непроверенное требование 9](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-retired-java-controller-evidence-for-pedestrian-warning-observes-acc-power-separately-from-transport)
- [Непроверенное требование 10](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-retired-java-controller-evidence-for-pedestrian-warning-timestamps-actual-oem-getter-observations)
- [Непроверенное требование 11](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-current-timing-runtime-acceptance)
- [Непроверенное требование 12](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-installed-pedestrian-warning-role-acceptance)
- [Непроверенное требование 13](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-oem-input-during-owner-or-lifecycle-transitions)

## Как изменить статус

Статус меняется только в метаданных агента. Перед переводом в stable должны быть готовы контракт, остановка и удаление, заглушки, тесты, парная документация, OpenSpec и обязательные проверки целевой среды.

После изменения выполните генератор и полные проверки агентов. Сгенерированная страница должна совпасть с метаданными и OpenSpec байт-в-байт.
