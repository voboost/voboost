# Voboost

**English** | [Русский](README.ru.md)

Voboost adds features to the Voyah Free and Dreamer infotainment system. After installation, users can turn them on and off from a dedicated tab in the vehicle settings.

beta1 includes seven stable agents:

- `settings` adds the Voboost tab to the vehicle settings
- `font-cyrillic` fixes Cyrillic text in the stock semibold font
- `keyboard-ru` replaces the Chinese and English layouts with Russian and English layouts
- `keyboard-en` keeps only the English layout
- `launcher-apps` shows third-party apps in the stock launcher on Chinese-market vehicles
- `vehicle-drive-forced-ev` restores Forced EV after switching to Electric or rebooting
- `vehicle-drive-pedestrian-warning` restores only a confirmed disabled pedestrian-warning choice after a CAN restart; a later manual enable cancels restoration

The unstable `logs` agent adds a Logs tab to the vehicle settings for records from the app, `voboost-daemon`, and agents. It is included in beta1 for development and diagnostics.

## Documentation

- [What Voboost is](docs-user/index.md)
- [Quick start](docs-user/quick-start.md)
- [Installation](docs-user/install.md)
- [Using Voboost](docs-user/usage.md)
- [Updates](docs-user/updates.md)
- [Restoring after a stock firmware update](docs-user/restore.md)
- [Complete removal](docs-user/uninstall.md)
- [Logs](docs-user/logs.md)
- [Roadmap](docs-user/roadmap.md)
- [Changelog](docs-user/changelog.md)
- [Developer documentation](docs-dev/index.md)
- [Glossary](GLOSSARY.md)

## Repositories

- [voboost](https://github.com/voboost/voboost) — product version, manifests, release orchestration, and documentation
- [voboost-app](https://github.com/voboost/voboost-app) — unprivileged Android OTA courier
- [voboost-agents](https://github.com/voboost/voboost-agents) — agent runtime, target DEX files, catalog, and OEM fixtures
- [voboost-inject](https://github.com/voboost/voboost-inject) — root daemon, injector, implant, and system operations
- [voboost-installer](https://github.com/voboost/voboost-installer) — offline installer for macOS, Windows, and Linux

## License

Original source code and materials are available under [MIT](LICENSE); a [Russian translation](LICENSE.ru.md) is provided for convenience. Third-party components and materials retain their own terms; see [THIRD_PARTY.md](THIRD_PARTY.md).

The current code license authority is this repository’s [LICENSE](LICENSE) and [Russian translation](LICENSE.ru.md). voboost-license preserves historical terms only; logo licensing is handled separately in voboost-logo.
