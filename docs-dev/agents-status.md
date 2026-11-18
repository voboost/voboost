# Agent status

**English** | [Русский](agents-status.ru.md)

This page is generated from metadata and applied OpenSpec in the sibling `voboost-agents` repository. There is no second manually maintained status list.

Composition: **18 stable**, **0 unstable**, **0 planned**.

Applicability notation: chip (`8155` or `nxp`); vehicle (`free` or `dreamer`); Android version; powertrain (`ev` or `hybrid`); market (`china` or `global`).

The Next contribution column shows the number of OpenSpec Unverified requirements. A planned agent without an explicit Unverified block still requires device and vehicle verification before promotion to stable.

| Agent | Status | Processes | Applicability | Next contribution | Documents |
| --- | --- | --- | --- | --- | --- |
| `font-cyrillic` | `stable` | 42 processes; see the agent document for the exact list | 8155/nxp; free/dreamer; A9/11; ev/hybrid; china | Open checks: 4 | [Document](../../voboost-agents/src/main/java/ru/voboost/agents/font/cyrillic/FontCyrillicAgent.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-font-cyrillic/spec.md) |
| `i18n-en` | `stable` | 4 processes; see the agent document for the exact list | 8155; free; A9/11; ev/hybrid; china | Open checks: 2 | [Document](../../voboost-agents/src/main/java/ru/voboost/agents/i18n/en/I18nEnAgent.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-i18n-en/spec.md) |
| `i18n-ru` | `stable` | 7 processes; see the agent document for the exact list | 8155; free; A9/11; ev/hybrid; china | Open checks: 5 | [Document](../../voboost-agents/src/main/java/ru/voboost/agents/i18n/ru/I18nRuAgent.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-i18n-ru/spec.md) |
| `keyboard-en` | `stable` | `com.qinggan.app.qgime`, `com.qinggan.app.qgime.second` | 8155/nxp; free/dreamer; A9/11; ev/hybrid; china | Open checks: 4 | [Document](../../voboost-agents/src/main/java/ru/voboost/agents/keyboard/en/KeyboardEnAgent.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-keyboard-en/spec.md) |
| `keyboard-ru` | `stable` | `com.qinggan.app.qgime`, `com.qinggan.app.qgime.second` | 8155/nxp; free/dreamer; A9/11; ev/hybrid; china | Open checks: 4 | [Document](../../voboost-agents/src/main/java/ru/voboost/agents/keyboard/ru/KeyboardRuAgent.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-keyboard-ru/spec.md) |
| `launcher` | `stable` | 4 processes; see the agent document for the exact list | 8155/nxp; free/dreamer; A9/11; ev/hybrid; china | Open checks: 11 | [Document](../../voboost-agents/src/main/java/ru/voboost/agents/launcher/apps/LauncherAgent.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-launcher/spec.md) |
| `login-activation-skip` | `stable` | `com.qinggan.app.login` | 8155; free/dreamer; A9/11; ev/hybrid; china | Open checks: 1 | [Document](../../voboost-agents/src/main/java/ru/voboost/agents/login/activationskip/LoginActivationSkipAgent.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-login-activation-skip/spec.md) |
| `media-apps` | `stable` | `com.qinggan.app.launcher`, `com.qinggan.media` | 8155; free; A9/11; hybrid; china/global | Open checks: 1 | [Document](../../voboost-agents/src/main/java/ru/voboost/agents/media/apps/MediaAppsAgent.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-media-apps/spec.md) |
| `media-popup-external-apps` | `stable` | `com.qinggan.app.islandapp` | 8155; free/dreamer; A9/11; ev/hybrid; china/global | Open checks: 3 | [Document](../../voboost-agents/src/main/java/ru/voboost/agents/media/popupexternalapps/MediaPopupExternalAppsAgent.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-media-popup-external-apps/spec.md) |
| `phone` | `stable` | `com.qinggan.bluetoothphone`, `com.android.bluetooth`, `com.qinggan.systempolicy` | 8155; free; A9/11; ev/hybrid; china | Open checks: 2 | [Document](../../voboost-agents/src/main/java/ru/voboost/agents/phone/PhoneAgent.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-phone/spec.md) |
| `settings` | `stable` | `com.qinggan.app.setting` | 8155/nxp; free/dreamer; A9/11; ev/hybrid; china/global | Open checks: 8 | [Document](../../voboost-agents/src/main/java/ru/voboost/agents/settings/SettingsAgent.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-settings/spec.md) |
| `settings-engineering-menu` | `stable` | `com.qinggan.app.setting` | 8155; free; A9/11; ev/hybrid; china | Open checks: 1 | [Document](../../voboost-agents/src/main/java/ru/voboost/agents/settings/engineeringmenu/SettingsEngineeringMenuAgent.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-settings-engineering-menu/spec.md) |
| `steering-media-app-switcher` | `stable` | `com.qinggan.keymanager.service` | 8155/nxp; free/dreamer; A9/11; ev/hybrid; china/global | Open checks: 1 | [Document](../../voboost-agents/src/main/java/ru/voboost/agents/steering/mediaappswitcher/SteeringMediaAppSwitcherAgent.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-steering-media-app-switcher/spec.md) |
| `steering-media-session` | `stable` | `com.qinggan.keymanager.service` | 8155/nxp; free/dreamer; A9/11; ev/hybrid; china/global | Open checks: 2 | [Document](../../voboost-agents/src/main/java/ru/voboost/agents/steering/mediasession/SteeringMediaSessionAgent.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-steering-media-session/spec.md) |
| `vehicle-display-auto-lift` | `stable` | `com.qinggan.app.vehiclesetting` | 8155; free; A9; ev/hybrid; china/global | Open checks: 1 | [Document](../../voboost-agents/src/main/java/ru/voboost/agents/vehicle/display/autolift/VehicleDisplayAutoLiftAgent.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-vehicle-display-auto-lift/spec.md) |
| `vehicle-drive-forced-ev` | `stable` | `com.qinggan.app.vehiclesetting`, `com.qinggan.app.launcher`, `com.qinggan.canbus.service` | 8155; free; A9/11; hybrid; china/global | Open checks: 18 | [Document](../../voboost-agents/src/main/java/ru/voboost/agents/vehicle/drive/forcedev/VehicleDriveForcedEvAgent.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md) |
| `vehicle-drive-outing` | `stable` | `com.qinggan.app.vehicle`, `com.qinggan.app.vehiclesetting` | 8155; free/dreamer; A9/11; ev/hybrid; china/global | Open checks: 3 | [Document](../../voboost-agents/src/main/java/ru/voboost/agents/vehicle/drive/outing/VehicleDriveOutingAgent.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-vehicle-drive-outing/spec.md) |
| `vehicle-drive-pedestrian-warning` | `stable` | `com.qinggan.app.vehiclesetting`, `com.qinggan.app.launcher`, `com.qinggan.canbus.service` | 8155; free; A9/11; ev/hybrid; china/global | Open checks: 13 | [Document](../../voboost-agents/src/main/java/ru/voboost/agents/vehicle/drive/pedestrianwarning/VehicleDrivePedestrianWarningAgent.md) · [OpenSpec](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md) |

## Unverified requirements

Links point to the exact OpenSpec requirements. Titles are not copied here, so the specification remains the wording authority.

### `font-cyrillic`

- [Unverified requirement 1](../../voboost-agents/openspec/specs/agent-font-cyrillic/spec.md#unverified-regular-asset-android-callback-integration)
- [Unverified requirement 2](../../voboost-agents/openspec/specs/agent-font-cyrillic/spec.md#unverified-target-system-wide-font-integration)
- [Unverified requirement 3](../../voboost-agents/openspec/specs/agent-font-cyrillic/spec.md#unverified-exact-qnx-font-loading)
- [Unverified requirement 4](../../voboost-agents/openspec/specs/agent-font-cyrillic/spec.md#unverified-actual-navi-focused-paint-and-visible-cyrillic-spacing)

### `i18n-en`

- [Unverified requirement 1](../../voboost-agents/openspec/specs/agent-i18n-en/spec.md#unverified-independent-english-runtime-admission)
- [Unverified requirement 2](../../voboost-agents/openspec/specs/agent-i18n-en/spec.md#unverified-oem-selected-locale-runtime-admission)

### `i18n-ru`

- [Unverified requirement 1](../../voboost-agents/openspec/specs/agent-i18n-ru/spec.md#unverified-multi-owner-source-and-delivery-acceptance)
- [Unverified requirement 2](../../voboost-agents/openspec/specs/agent-i18n-ru/spec.md#unverified-vehiclesetting-inactive-role-and-fallback-acceptance)
- [Unverified requirement 3](../../voboost-agents/openspec/specs/agent-i18n-ru/spec.md#unverified-systemui-guard-source-prototype-and-runtime-acceptance)
- [Unverified requirement 4](../../voboost-agents/openspec/specs/agent-i18n-ru/spec.md#unverified-outside-air-prefix-and-owner-lifecycle-acceptance)
- [Unverified requirement 5](../../voboost-agents/openspec/specs/agent-i18n-ru/spec.md#unverified-effective-unknown-language-acceptance)

### `keyboard-en`

- [Unverified requirement 1](../../voboost-agents/openspec/specs/agent-keyboard-en/spec.md#unverified-host-evidence-for-english-keyboard-uses-one-exact-structural-qgime-contract)
- [Unverified requirement 2](../../voboost-agents/openspec/specs/agent-keyboard-en/spec.md#unverified-host-evidence-for-english-keyboard-coercion-and-presentation-are-exact)
- [Unverified requirement 3](../../voboost-agents/openspec/specs/agent-keyboard-en/spec.md#unverified-host-evidence-for-english-keyboard-stop-restores-stock-presentation)
- [Unverified requirement 4](../../voboost-agents/openspec/specs/agent-keyboard-en/spec.md#unverified-target-qgime-integration)

### `keyboard-ru`

- [Unverified requirement 1](../../voboost-agents/openspec/specs/agent-keyboard-ru/spec.md#unverified-host-evidence-for-russian-keyboard-uses-one-exact-structural-qgime-contract)
- [Unverified requirement 2](../../voboost-agents/openspec/specs/agent-keyboard-ru/spec.md#unverified-host-evidence-for-russian-layout-assets-and-key-behavior-are-one-owned-state)
- [Unverified requirement 3](../../voboost-agents/openspec/specs/agent-keyboard-ru/spec.md#unverified-host-evidence-for-russian-keyboard-callback-faults-are-lifecycle-fenced)
- [Unverified requirement 4](../../voboost-agents/openspec/specs/agent-keyboard-ru/spec.md#unverified-target-qgime-integration)

### `launcher`

- [Unverified requirement 1](../../voboost-agents/openspec/specs/agent-launcher/spec.md#unverified-screen-specific-styling-on-the-final-oem-displays)
- [Unverified requirement 2](../../voboost-agents/openspec/specs/agent-launcher/spec.md#unverified-selected-activity-icon-packaged-acceptance)
- [Unverified requirement 3](../../voboost-agents/openspec/specs/agent-launcher/spec.md#unverified-oem-theme-delivery-and-concurrent-capture)
- [Unverified requirement 4](../../voboost-agents/openspec/specs/agent-launcher/spec.md#unverified-window-executor-android-bridge)
- [Unverified requirement 5](../../voboost-agents/openspec/specs/agent-launcher/spec.md#unverified-live-conditional-launcher-locale)
- [Unverified requirement 6](../../voboost-agents/openspec/specs/agent-launcher/spec.md#unverified-icon-cache-target-lifecycle)
- [Unverified requirement 7](../../voboost-agents/openspec/specs/agent-launcher/spec.md#unverified-launcher-android-integration)
- [Unverified requirement 8](../../voboost-agents/openspec/specs/agent-launcher/spec.md#unverified-cache-receipt-target-lifecycle)
- [Unverified requirement 9](../../voboost-agents/openspec/specs/agent-launcher/spec.md#unverified-native-disclosure-and-package-actions)
- [Unverified requirement 10](../../voboost-agents/openspec/specs/agent-launcher/spec.md#unverified-cross-repository-canonical-mutation-transport)
- [Unverified requirement 11](../../voboost-agents/openspec/specs/agent-launcher/spec.md#unverified-configured-application-density-and-display-modes-in-the-guest)

### `login-activation-skip`

- [Unverified requirement 1](../../voboost-agents/openspec/specs/agent-login-activation-skip/spec.md#unverified-a9-and-a11-login-startup-integration)

### `media-apps`

- [Unverified requirement 1](../../voboost-agents/openspec/specs/agent-media-apps/spec.md#unverified-selection-jni-and-catalog-integration)

### `media-popup-external-apps`

- [Unverified requirement 1](../../voboost-agents/openspec/specs/agent-media-popup-external-apps/spec.md#unverified-popup-existing-client-target-integration)
- [Unverified requirement 2](../../voboost-agents/openspec/specs/agent-media-popup-external-apps/spec.md#unverified-popup-takeover-target-integration)
- [Unverified requirement 3](../../voboost-agents/openspec/specs/agent-media-popup-external-apps/spec.md#unverified-popup-artwork-target-integration)

### `phone`

- [Unverified requirement 1](../../voboost-agents/openspec/specs/agent-phone/spec.md#unverified-phone-runtime-integration)
- [Unverified requirement 2](../../voboost-agents/openspec/specs/agent-phone/spec.md#unverified-phone-date-fallback-vehicle-rendering)

### `settings`

- [Unverified requirement 1](../../voboost-agents/openspec/specs/agent-settings/spec.md#unverified-daemon-confirmed-republish-on-a-live-process)
- [Unverified requirement 2](../../voboost-agents/openspec/specs/agent-settings/spec.md#unverified-stock-notification-presentation-in-the-live-settings-process)
- [Unverified requirement 3](../../voboost-agents/openspec/specs/agent-settings/spec.md#unverified-live-component-derived-settings-spacing)
- [Unverified requirement 4](../../voboost-agents/openspec/specs/agent-settings/spec.md#unverified-live-content-sized-qinggan-actions)
- [Unverified requirement 5](../../voboost-agents/openspec/specs/agent-settings/spec.md#unverified-live-settings-mutation-feedback)
- [Unverified requirement 6](../../voboost-agents/openspec/specs/agent-settings/spec.md#unverified-live-settings-mutation-publication-progress)
- [Unverified requirement 7](../../voboost-agents/openspec/specs/agent-settings/spec.md#unverified-live-independent-section-metadata)
- [Unverified requirement 8](../../voboost-agents/openspec/specs/agent-settings/spec.md#unverified-live-queued-tab-margin-cleanup)

### `settings-engineering-menu`

- [Unverified requirement 1](../../voboost-agents/openspec/specs/agent-settings-engineering-menu/spec.md#unverified-oem-engineering-menu-integration)

### `steering-media-app-switcher`

- [Unverified requirement 1](../../voboost-agents/openspec/specs/agent-steering-media-app-switcher/spec.md#unverified-switcher-shared-state-target-integration)

### `steering-media-session`

- [Unverified requirement 1](../../voboost-agents/openspec/specs/agent-steering-media-session/spec.md#unverified-media-press-target-integration)
- [Unverified requirement 2](../../voboost-agents/openspec/specs/agent-steering-media-session/spec.md#unverified-oem-media-session-authority-and-vehicle-behavior)

### `vehicle-display-auto-lift`

- [Unverified requirement 1](../../voboost-agents/openspec/specs/agent-vehicle-display-auto-lift/spec.md#unverified-android-drive-preferences-integration)

### `vehicle-drive-forced-ev`

- [Unverified requirement 1](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-target-forced-ev-startup-application-timing)
- [Unverified requirement 2](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-host-evidence-for-forced-ev-declarations-compile-into-immutable-signed-definitions)
- [Unverified requirement 3](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-retired-fev-host-integration-evidence-for-forced-ev-selection-admits-exactly-one-structural-contract)
- [Unverified requirement 4](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-retired-fev-host-integration-evidence-for-explicit-off-delegates-vehicle-mutation-after-durable-disarm)
- [Unverified requirement 5](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-native-owned-input-and-physical-off-acceptance)
- [Unverified requirement 6](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-host-evidence-for-forced-ev-actions-use-bound-handles-one-mutation-and-verified-readback)
- [Unverified requirement 7](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-target-vehicle-agent-behavior)
- [Unverified requirement 8](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-retired-fev-host-integration-evidence-for-sticky-intent-distinguishes-explicit-input-from-ordinary-mode-state)
- [Unverified requirement 9](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-retired-java-controller-evidence-for-forced-ev-startup-state-reads-recover-across-bounded-cycles)
- [Unverified requirement 10](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-retired-java-controller-evidence-for-forced-ev-observes-acc-power-separately-from-transport)
- [Unverified requirement 11](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-retired-fev-host-integration-evidence-for-startup-restoration-readiness-follows-vehicle-readback)
- [Unverified requirement 12](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-current-timing-runtime-acceptance)
- [Unverified requirement 13](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-retired-java-controller-evidence-for-forced-ev-explicit-off-limits-physical-changes-to-the-declared-release-action)
- [Unverified requirement 14](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-explicit-off-diagnostic-runtime-integration)
- [Unverified requirement 15](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-retired-fev-host-integration-evidence-for-free-launcher-drawer-preserves-explicit-switch-intent)
- [Unverified requirement 16](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-oem-input-during-owner-or-lifecycle-transitions)
- [Unverified requirement 17](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-installed-electric-drain-and-off-acceptance)
- [Unverified requirement 18](../../voboost-agents/openspec/specs/agent-vehicle-drive-forced-ev/spec.md#unverified-installed-forced-ev-role-acceptance)

### `vehicle-drive-outing`

- [Unverified requirement 1](../../voboost-agents/openspec/specs/agent-vehicle-drive-outing/spec.md#unverified-outing-physical-policy-acceptance)
- [Unverified requirement 2](../../voboost-agents/openspec/specs/agent-vehicle-drive-outing/spec.md#unverified-outing-editor-runtime-acceptance)
- [Unverified requirement 3](../../voboost-agents/openspec/specs/agent-vehicle-drive-outing/spec.md#unverified-native-individual-template-rendering)

### `vehicle-drive-pedestrian-warning`

- [Unverified requirement 1](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-cluster-pw-visual-runtime-acceptance)
- [Unverified requirement 2](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-target-pedestrian-warning-startup-application-timing)
- [Unverified requirement 3](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-retired-java-controller-evidence-for-pedestrian-warning-fatal-failures-are-never-retried)
- [Unverified requirement 4](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-target-vehicle-agent-behavior)
- [Unverified requirement 5](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-retired-java-controller-evidence-for-pedestrian-warning-selects-one-declarative-structural-can-contract)
- [Unverified requirement 6](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-retired-java-controller-evidence-for-pedestrian-warning-restoration-requires-accepted-ack-and-disabled-readback)
- [Unverified requirement 7](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-retired-java-controller-evidence-for-pedestrian-warning-startup-state-reads-recover-across-bounded-cycles)
- [Unverified requirement 8](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-retired-java-controller-evidence-for-absent-pedestrian-warning-intent-adopts-the-initial-oem-value)
- [Unverified requirement 9](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-retired-java-controller-evidence-for-pedestrian-warning-observes-acc-power-separately-from-transport)
- [Unverified requirement 10](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-retired-java-controller-evidence-for-pedestrian-warning-timestamps-actual-oem-getter-observations)
- [Unverified requirement 11](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-current-timing-runtime-acceptance)
- [Unverified requirement 12](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-installed-pedestrian-warning-role-acceptance)
- [Unverified requirement 13](../../voboost-agents/openspec/specs/agent-vehicle-drive-pedestrian-warning/spec.md#unverified-oem-input-during-owner-or-lifecycle-transitions)

## Changing a status

Status changes only in agent metadata. Before promotion to stable, the contract, stop and removal behavior, stubs, tests, paired documentation, OpenSpec, and required target-environment checks must be ready.

After a change, run the generator and the complete agent checks. The generated page must match metadata and OpenSpec byte for byte.
