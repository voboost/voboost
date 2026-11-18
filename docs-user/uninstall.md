# Complete Removal

**English** | [Русский](uninstall.ru.md)

Voboost can be removed from the vehicle settings or through `voboost-installer`. Both methods start the same operation in the vehicle: it stops the agents, restores the saved baseline state, and removes every Voboost component.

## Removing from the vehicle

1. Open the Voboost tab in the vehicle settings
2. Open the Removal section
3. Select the removal button
4. Confirm the action in the separate dialog
5. Wait for the operation to complete

The Voboost tab may disappear immediately after confirmation. The infotainment system reboots only to stop programs, restore the baseline state, or verify the result. Do not press the button again.

## Removing from a computer

Connect the computer, start `voboost-installer`, and select **Remove**. From the command line, use:

```text
voboost-installer --uninstall
```

The CLI starts removal immediately, without a separate confirmation. The installer restores the connection after each required reboot and shows the operation's progress.

## What is restored

- Stock system files and settings saved before installation or the most recent restore
- The current firmware's SELinux policy
- Permissions and system settings changed by Voboost

If the original content cannot be verified, Voboost does not overwrite the file and reports an error. Partial cleanup is not reported as successful removal.

## What is removed

- The Voboost app
- `voboost-daemon`, its programs, and its libraries
- Agents and their saved settings
- The Voboost automatic startup rule
- SELinux rules added by Voboost
- Logs, updates, and temporary data
- The `/data/voboost` directory

The installer reports complete removal only after checking that the Voboost files, services and resources are gone and that modified stock settings have been restored. The checks cover Android, including localization overlays, and the instrument cluster where applicable. On profiles that require QNX verification, use the desktop installer with native instrument cluster support. If the installer cannot read a required system or verify its original state, it reports what blocked removal and why.

## If removal is interrupted

Reconnect the same vehicle and run `voboost-installer --uninstall` again or select **Remove**. The installer checks what remains and resumes removal when it has the required access and recovery data. It reports that Voboost is already removed only after verifying the current state. If removal stops on an error, [download the logs](logs.md) and leave the files in place.

## Next steps

- [quick start](quick-start.md)
- [installation](install.md)
- [restoring after a stock firmware update](restore.md)
- [roadmap](roadmap.md)
