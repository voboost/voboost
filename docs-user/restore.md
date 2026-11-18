# Restoring After a Stock Firmware Update

**English** | [Русский](restore.ru.md)

Voboost must be restored from a computer after a stock firmware update. New firmware may replace system files, startup rules, and the SELinux policy, so continuing to use the old installation is unsafe.

Restore does not roll back the stock firmware. It accepts the new stock state as the baseline, reinstalls compatible Voboost components, and preserves the user's previous selections where they remain applicable.

## Requirements

- A computer with `voboost-installer`
- A USB data cable
- USB debugging enabled
- System ADB on Linux if the selected package does not include it

## How to restore

1. Connect the computer to the vehicle
2. Start `voboost-installer`
3. Select **Restore after stock firmware update**
4. Review the detected firmware version and planned changes
5. Confirm the operation
6. Keep the cable connected until the installer reports success

From the command line, use:

```text
voboost-installer --restore
```

## What happens

Voboost first checks the state after the stock update, the installed app, and the saved data required for complete removal, then saves the new baseline policy. It then restores compatible files and startup rules and verifies that `voboost-daemon` starts with SELinux enforcing. If the app is missing, Restore makes no system changes and asks the user to perform a regular installation.

Restore reboots the infotainment system twice to make the transition safely. The computer connection temporarily drops during each reboot, and `voboost-installer` resumes the same operation after reconnecting.

## What is preserved

Voboost preserves the startup mode and confirmed agent selections. If an agent is no longer compatible with the new firmware, it remains off and displays the reason; other compatible features continue to work.

## Result

Restore is complete when `voboost-installer` reports success, `voboost-daemon` starts with SELinux enforcing, and the Voboost tab shows agent states for the new firmware.

The new stock state becomes the baseline for future [complete removal](uninstall.md): removal restores this state, not files from the previous firmware.

## If restore is interrupted

Do not start an installation over an unfinished restore. Turn on the infotainment system, restore the USB connection, and reopen `voboost-installer`: it will continue the saved operation or show why it stopped.

## Next steps

- [using Voboost](usage.md)
- [how Voboost updates](updates.md)
- [downloading logs](logs.md)
- [removing Voboost completely](uninstall.md)
