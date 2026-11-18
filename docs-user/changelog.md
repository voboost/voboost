# Changelog

**English** | [Русский](changelog.ru.md)

This page lists changes visible to users. Internal refactoring, tests, and renames that do not change behavior are omitted.

## 1.0.0-beta1

> ’Cause it’s beta than nothin’.

### Added

- universal `voboost-installer-1.0.0-beta1.dmg` for macOS Apple Silicon and Intel, portable `voboost-installer-1.0.0-beta1.exe` for Windows x86-64, and one-entry `voboost-installer-1.0.0-beta1.tar.bz2` for Linux x86-64
- First installation from a computer with operation resumption after required reboots
- Interface, Hidden, and Disabled startup modes
- Voboost tab in the vehicle settings
- Correct Cyrillic text in the stock semibold font on Chinese-market vehicles
- Russian and English layouts instead of Chinese and English layouts
- English-only keyboard mode
- Third-party apps in the stock launcher on Chinese-market vehicles
- Forced EV restoration after switching to Electric or rebooting
- Restoration of a confirmed disabled pedestrian-warning choice after a CAN restart, with a later manual enable canceling restoration
- Unstable `logs` agent with a Logs tab in the Unstable section
- One log stream for the app, `voboost-daemon`, and agents
- Diagnostic log downloads through `voboost-installer`
- Automatic app and agent updates without a reboot
- Updates to changed `voboost-daemon` files with one reboot
- Restoration after a stock firmware update
- Complete removal with restoration of the saved baseline state and only the required reboots

This release provides the complete install, update, management, restore, log export, and uninstall lifecycle for Voyah Free and Dreamer vehicles running Android 9 and 11.
