# Installation

**English** | [Русский](install.ru.md)

The first Voboost installation is performed from a computer over USB. The installer checks the connection, signed bundle, and original vehicle state, shows every action, and resumes the same operation after each reboot.

## Requirements

- A Voyah Free or Dreamer with a supported Android 9 or 11 infotainment system
- A computer running macOS, Windows, or Linux
- `voboost-installer-1.0.0-beta1.exe`, `voboost-installer-1.0.0-beta1.dmg`, or `voboost-installer-1.0.0-beta1.tar.bz2`
- A USB data cable
- USB debugging enabled in the vehicle settings
- Microsoft Edge WebView2 Runtime for the Windows graphical interface
- System ADB on Linux

## Preparing the computer

Download one package: the portable Windows x86-64 EXE, the universal macOS DMG for Apple Silicon and Intel, or the Linux x86-64 tar.bz2. The macOS and Windows packages may warn about an unknown publisher; continue only if you downloaded the file from the official Voboost page.

The Windows EXE contains the complete offline product and extracts only the official ADB trio into a private temporary directory. The macOS DMG contains `Voboost Installer.app` and a root `voboost-installer` command-line link. The Linux tar.bz2 contains exactly one headless `voboost-installer` executable and preserves mode `0755`.

On Linux, check the system ADB installation:

```text
adb version
```

If the command is not found, install the ADB package using your distribution's package manager.

## Installing from the graphical interface

1. [Enable USB debugging](https://voyahchat.ru/usb) in the vehicle settings
2. Connect the computer and vehicle with a [suitable cable](https://voyahchat.ru/cable)
3. Start `voboost-installer`
4. Select the connected vehicle if the installer finds more than one device
5. Select **Start Installation**
6. Read the short notice, then the full license text in the same window, and accept it
7. Review the planned changes and confirm installation
8. Keep the cable connected until the installer reports success

## Installing from the command line

```text
voboost-installer -i
```

Installation does not display a mandatory license screen or ask for license acceptance. License texts and third-party notices remain available in the distribution.

```text
voboost-installer -i
```

Use this flag only after reading the license. The CLI does not ask for a separate confirmation of changes. Other checks remain enabled.

## Clean installation and update

A clean `--install` removes the previous Voboost installation and its settings. The installer resumes an interrupted installation of the same verified bundle when it can. For a new installation, it starts or resumes removal, verifies Android and the instrument cluster where applicable, and installs the new bundle in the same command. If a required check fails, the installer reports the reason before sending the new bundle to the vehicle. On profiles that require QNX verification, use the desktop installer with native instrument cluster support.

To keep your Voboost settings, use `voboost-installer --update` for an installed product. See [Updates](updates.md).

## What happens to the vehicle

The first `voboost-daemon` startup requires root privileges and dedicated SELinux rules. Before making changes, the installer saves the original state of the current firmware, temporarily switches SELinux to permissive, installs Voboost, adds the minimum rules required by Voboost, and returns SELinux to enforcing.

Installation reboots the infotainment system twice. ADB disconnects during each reboot, but the operation continues in the vehicle and the installer resumes its display after reconnecting.

## Result

Installation is complete when `voboost-installer` reports success, `voboost-daemon` starts after the reboot, and the Voboost tab appears in the vehicle settings. User-facing agents are off by default.

Open the Voboost tab and enable the required features. If the tab does not appear, restore the USB connection and [download the logs](logs.md).

## If installation is interrupted

Do not start a new installation or delete files manually. Turn on the infotainment system, restore the USB connection, and reopen `voboost-installer`: it will find the unfinished operation and continue it or show a safe recovery method.

## Next steps

- [using Voboost](usage.md)
- [how Voboost updates](updates.md)
- [restoring Voboost after a stock firmware update](restore.md)
- [removing Voboost completely](uninstall.md)
