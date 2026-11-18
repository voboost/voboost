# Voboost Documentation

**English** | [Русский](index.ru.md)

Voboost adds features to the Android 9 and 11 infotainment systems in the Voyah Free and Dreamer by changing how stock apps behave. Users install Voboost from a computer, then manage its features from a dedicated tab in the vehicle settings.

## How Voboost works

Voboost installs `voboost-daemon`, a daemon with root privileges that starts with the infotainment system. It stores the user's choices, watches for stock apps to start, and runs the required agents inside them.

Each agent provides one feature. `voboost-daemon` injects it into a suitable stock app, where the agent checks the available capabilities and performs only its intended actions. If a required capability is missing, the agent makes no changes and reports in the settings that the feature is unavailable.

Agents can change the behavior of a stock feature, add a setting for it, or create a new capability from vehicle functions. For example, one agent replaces the Chinese and English keyboard with Russian and English layouts, while another restores a confirmed disabled pedestrian-warning choice after a CAN restart without overriding a later manual enable.

```text
user choice
    ↓
voboost-daemon
    ↓
agents in stock apps
    ↓
new vehicle features and settings
```

## What beta1 includes

Six stable agents provide individual user features:

- `font-cyrillic` fixes Cyrillic text in the stock semibold font on Chinese-market vehicles
- `keyboard-ru` replaces the Chinese and English layouts with Russian and English layouts
- `keyboard-en` keeps only the English layout
- `launcher-apps` shows third-party apps in the stock launcher on Chinese-market vehicles
- `vehicle-drive-forced-ev` restores the selected Forced EV mode after switching to Electric or rebooting
- `vehicle-drive-pedestrian-warning` restores only a confirmed disabled pedestrian-warning choice after a CAN restart; a later manual enable cancels restoration

The seventh stable agent, `settings`, creates the Voboost tab and lets users manage the other agents. The unstable `logs` agent adds a separate Logs tab for development and diagnostics.

An error or incompatibility in one agent does not stop other Voboost features.

## Startup modes

Voboost supports three modes:

- **Interface** — selected agents run and the Voboost tab is visible in the vehicle settings
- **Hidden** — selected agents run and the Voboost tab is hidden
- **Disabled** — Voboost and its agents do not start

The selected mode persists after a reboot. Use `voboost-installer` to restore the interface from Hidden or Disabled mode.

## Next steps

- [quick start](quick-start.md)
- [detailed installation guide](install.md)
- [managing features](usage.md)
- [updates](updates.md)
- [restoring after a stock firmware update](restore.md)
- [complete removal](uninstall.md)
- [viewing and downloading logs](logs.md)
- [roadmap](roadmap.md)
- [changelog](changelog.md)
- [glossary](../GLOSSARY.md)

Developers should continue with the [developer documentation](../docs-dev/index.md) after reading the user documentation.
