# Roadmap

**English** | [Русский](roadmap.ru.md)

This roadmap describes areas of work but does not promise dates or versions. A new feature becomes available only after its activation, operation, deactivation, and complete removal have been verified.

## beta1

beta1 includes seven stable agents:

- `settings` adds the Voboost tab to the vehicle settings
- `font-cyrillic` fixes Cyrillic text in the stock semibold font on Chinese-market vehicles
- `keyboard-ru` replaces the Chinese and English layouts with Russian and English layouts
- `keyboard-en` keeps only the English layout
- `launcher-apps` shows third-party apps in the stock launcher on Chinese-market vehicles
- `vehicle-drive-forced-ev` restores the selected Forced EV mode after switching to Electric or rebooting
- `vehicle-drive-pedestrian-warning` restores a confirmed disabled pedestrian-warning choice after a CAN restart and accepts a later manual enable as the new choice

The unstable `logs` agent is also included in beta1. It adds a Logs tab for development and diagnostics and appears in the Unstable section.

beta1 covers computer-based installation, automatic updates, installed-product management, restoration after a stock firmware update, log downloads, and complete removal.

## Launcher and home screen

- App folders
- Navigation bar position and visibility settings
- Additional home-screen cards
- Navigation app shortcuts
- Media app selection and placement

## Navigation and driver display

- Full-screen navigation on Android 9 and 11
- Media projection to the driver display
- Continued playback when the center display is lowered
- Turn-by-turn navigation guidance
- Projected navigation integration

## Audio, phone, and steering wheel

- Navigation guidance over music
- Separate passenger audio
- Full phone numbers for Bluetooth calls
- Switching media apps from the steering wheel
- Active media session controls

## Vehicle features

- Driving mode visibility settings
- Additional Outing mode settings
- Suspension height control on highways
- Suspension state display
- Vehicle and turn signal state display
- Automatic center display raising
- Rhythmic exterior lighting
- Apollo availability display

## Sign-in and system actions

- Skipping the activation screen
- Face ID state display
- Engineering menu access
- TBox reboot

## Longer-term plans

- Features requiring additional system privileges
- Changes to core Android processes
- Features that do not yet have safe and unambiguous vehicle state controls
- Additional agent capabilities delivered through product releases without reinstalling an unchanged app
- Support for other vehicles and hardware platforms

## When a feature becomes available

A planned agent is not included in the build and is not shown to users. To become `stable`, it must:

1. Detect compatibility through available stock app capabilities
2. Turn on and off safely
3. Restore the confirmed selection after a reboot
4. Stop all activity after removal
5. Have clear user documentation
6. Pass all required device checks
7. Confirm the result in a vehicle

Until every condition is met, the agent remains planned.

## How to help

Developers can choose a task on the [Agent Status](../docs-dev/agents-status.md) page and begin with [Contributing](../docs-dev/contributing.md).

When reporting a problem, include the Voboost version, vehicle model, and action performed, and attach the [log archive](logs.md).
