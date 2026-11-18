# Updates

**English** | [Русский](updates.ru.md)

Voboost is updated as one product. A new release can change the Android app, one or more agents, the native runtime, the installer, documentation, or only release metadata. The version identifies the complete set, not one component.

## Automatic updates

The Android app checks a small signed `channel.json`, then verifies one complete signed `voboost.json`. It downloads only files whose exact identity changed. Agents are separate files and are not embedded in `voboost-app.apk`; changing one agent does not require downloading or reinstalling the APK when the APK bytes are unchanged.

The app only downloads and prepares files. `voboost-daemon` checks the complete set again, decides which agents apply to the vehicle, and performs any root-owned activation.

The app schedules checks through JobScheduler and resumes interrupted downloads. The daemon keeps every signed agent file in the immutable generation; vehicle applicability changes only which files it selects and loads. A later firmware change can therefore select an already installed agent without downloading the same SHA-256 again.

## Reboot

Agent-only changes can be prepared without reinstalling the Android app. A reboot is required only when changed bytes cannot be activated safely in running processes, for example when a required process has already loaded an old agent generation or the native runtime changed. Voboost waits for confirmation and does not reboot the infotainment system automatically.

## Failure and rollback

The previous release remains active until every changed part has been verified. If activation fails, Voboost keeps or restores the previous verified generation and native files. Do not delete files under `/data/voboost` manually; collect [logs](logs.md) and use the installer recovery action that is available for the observed state.

## Related pages

- [Quick start](quick-start.md)
- [Restore after a stock firmware update](restore.md)
- [Logs](logs.md)
- [Uninstall](uninstall.md)
