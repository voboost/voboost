# Logs

**English** | [Русский](logs.ru.md)

Logs help explain why a feature did not start, an update stopped, or `voboost-daemon` failed to launch. In beta1 they are downloaded as one archive through `voboost-installer`; the Logs tab is not part of the beta1 delivery.

Voboost uses three levels:

- `debug` — details about checks and decisions
- `info` — successful actions and state changes
- `error` — errors that prevented an action from completing

## The Logs tab

The `logs` agent is being developed as a Logs tab showing records from three sources — the Voboost app, `voboost-daemon`, and agents — with filters by source, agent, level, time, and text. beta1 does not ship this agent, so the tab is unavailable in the vehicle; the agent statuses are listed in the [agent status table](../docs-dev/agents-status.md).

## Downloading logs through the installer

The `logs` agent does not need to be enabled to download logs. Connect the computer, start `voboost-installer`, select **Download logs**, and choose a directory.

From the command line, specify an absolute path to an existing directory; the directory itself must not be a symbolic link. It may contain other files:

```text
voboost-installer --logs <directory>
```

After connecting, the installer retrieves diagnostic data from the vehicle and creates `voboost-logs-<UTC-date>-<UTC-time>-<random-token>.tar.xz` on the computer. The archive uses system XZ compression. The timestamp uses `YYYY-MM-DD-HHMMSS`; the random token is 32 lowercase hexadecimal characters. It does not create a separate archive in the vehicle.

## Archive contents

- `manifest.json` — `schemaVersion: 1`, `format: "VBOOSTLOG1"`, a header with `schemaVersion`, `recordsLength`, and `contextLength`, and the exact payload filenames `records.jsonl` and `context.json`
- `records.jsonl` — retained records from the app, the Android daemon, agents and the instrument cluster
- `context.json` — collection results, installer session and available Voboost state

The installer reads retained Voboost files and available Android diagnostics directly over ADB. It can collect diagnostics on a clean or partially installed system, even when the Voboost daemon is missing. The archive includes the current installer session and the outcome for each source. If only some vehicle data can be read, the installer reports a partial result and saves the available data. If no vehicle data can be read, it reports a failure and saves the computer-side installer log. Both outcomes give a nonzero CLI exit code. The installer reports the archive path.

The Android daemon writes the instrument cluster events it receives to the same Voboost log as Android events. The QNX cluster buffers pending events in RAM. A loss of power can erase events that have yet to reach Android.

## Before sharing

The archive belongs to the user and is never sent automatically. It can be opened and reviewed before being shared with a developer.

The collector does not read private key files, session seeds, NV contents, user files, or arbitrary memory dumps. Known secret patterns are redacted. Android and OEM messages may contain other personal data, so review the archive before sharing it. Logs may contain component versions, vehicle state, agent names, and user actions.

## After removal

Complete removal erases logs from the vehicle. Archives previously saved on a computer remain until deleted manually.

## Next steps

- [using Voboost](usage.md)
- [restoring Voboost after a stock firmware update](restore.md)
- [removing Voboost completely](uninstall.md)
