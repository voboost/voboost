# Using Voboost

**English** | [Русский](usage.ru.md)

The Voboost tab in the vehicle settings controls agents and startup mode. Choose the system language separately in the stock settings. Agent selections and startup mode are saved across reboots.

## Tab sections

- **Voboost** — version and startup mode
- **Localization** — Russian and English translation agents
- **Vehicle** — driving features and vehicle settings
- **Interface** — font, keyboard, and launcher features
- **Unstable** — features for development and verification
- **Removal** — complete Voboost removal

A section appears only when the installed catalog contains relevant agents. Localization is the first feature section, above Vehicle.

## Turning features on and off

After a switch is pressed, it waits for a Saved receipt from `voboost-daemon`. On failure, the choice remains visible for retry and a notification explains the problem. Saving the choice and applying the feature are verified separately.

Turning an agent off stops its actions and returns the feature it manages to its saved state. It does not affect other agents.

## beta1 features

### Localization

`i18n-ru` adds Russian translation with English for untranslated text. `i18n-en` translates remaining Chinese text into English and repairs the stock English fallback for unknown locales. Available agents appear under Localization.

Both checkboxes can be enabled together. Disabling one preserves the other. They enable agents without changing the current language: select it separately in the stock settings. The actual system language determines what is displayed.

### Cyrillic in the semibold font

`font-cyrillic` fixes Cyrillic text in Chinese-market stock apps that select an unsuitable semibold font. Latin and Chinese text continue to use the stock fonts.

### Russian and English keyboards

`keyboard-ru` replaces the Chinese and English layouts with Russian and English layouts. `keyboard-en` keeps only the English layout.

These agents are mutually exclusive: enabling one disables the other. If both features are off, the vehicle uses the stock keyboard.

### Third-party apps in the launcher

`launcher-apps` shows installed apps that can be opened from the stock launcher on Chinese-market vehicles. The list updates automatically after an app is installed, removed, or updated.

### Forced EV

`vehicle-drive-forced-ev` remembers the user's Forced EV selection and restores it after the vehicle switches to Electric or reboots. The agent does not enable Forced EV on its own: the user must select the mode first.

### Pedestrian warning sound

`vehicle-drive-pedestrian-warning` preserves only a confirmed choice to disable the pedestrian warning sound. After the CAN transport goes down and comes back, it restores that disabled choice if the stock system enabled the sound. A normal later manual enable is accepted as the new choice and cancels restoration.

### Logs

The unstable `logs` agent adds a Logs tab below the Voboost tab. It is intended for development and diagnostics and does not affect the other agents.

## Startup modes

The Voboost mode can be changed at the top of the tab:

- **Interface** — selected agents run and the Voboost tab is visible
- **Hidden** — selected agents run and the Voboost tab is hidden
- **Disabled** — Voboost and its agents do not start

To restore the tab from Hidden or Disabled mode, use `voboost-installer` and select Interface mode.

## Managing an installed release from a computer

When the installer finds Voboost, it opens Management instead of Welcome. From there you can change the startup mode, run Restore, download logs, or start Uninstall. Upgrade appears only when the installer contains a newer compatible release.

If the installer contains the same version, management actions remain available and reinstall is replaced by Restore. If it is older, Restore, logs, mode, and Uninstall remain available, but downgrade is not offered. An active operation always opens Current Operation and resumes before any other screen.

## After a reboot

Voboost preserves the startup mode and agent selections. `voboost-daemon` starts only enabled and compatible agents, and the tab shows their confirmed state.

## Next steps

- [how Voboost updates](updates.md)
- [viewing and downloading logs](logs.md)
- [restoring Voboost after a stock firmware update](restore.md)
- [removing Voboost completely](uninstall.md)
