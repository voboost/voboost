# Quick Start

**English** | [Русский](quick-start.ru.md)

You need a computer and a USB data cable for the first Voboost installation. After installation, features are configured in the vehicle.

## What to download

Download the `1.0.0-beta1` package for your computer:

- **Windows 10/11 x86-64** — [`voboost-installer-1.0.0-beta1.exe`](https://voboost.ru/voboost-installer-1.0.0-beta1.exe)
- **macOS Apple Silicon or Intel** — universal [`voboost-installer-1.0.0-beta1.dmg`](https://voboost.ru/voboost-installer-1.0.0-beta1.dmg)
- **Linux x86-64** — [`voboost-installer-1.0.0-beta1.tar.bz2`](https://voboost.ru/voboost-installer-1.0.0-beta1.tar.bz2)

Windows ships as one portable EXE. Its graphical interface requires Microsoft Edge WebView2 Runtime to be installed; the command-line interface works without WebView2. The installer extracts only `adb.exe`, `AdbWinApi.dll`, and `AdbWinUsbApi.dll` into its private temporary directory.

The macOS disk image contains `Voboost Installer.app` and a root `voboost-installer` link to the same universal executable. It includes one universal ADB binary.

The macOS and Windows packages may show a warning because the release is not signed by an operating-system-trusted publisher. Check that you downloaded the package from the official Voboost page and follow the [detailed instructions](install.md).

The Linux archive contains one headless `voboost-installer` executable. It does not include ADB. Install the system ADB package and make sure `adb version` works before starting it.

Use a USB cable that supports data transfer with the vehicle. A USB 2.0 Type-A to Type-A cable is the most reliable option; see the [cable selection guide](https://voyahchat.ru/cable) for recommendations for different computers.

## How to install

1. [Enable USB debugging](https://voyahchat.ru/usb) in the vehicle settings
2. Connect the computer to the vehicle
3. Start `voboost-installer` and select **Start Installation**
4. Read the short notice and the full license text
5. Accept the license and confirm installation
6. Follow the installer's instructions and wait for it to report success

The connection will temporarily drop during infotainment-system reboots. If the cable is disconnected, reconnect it and do not start installation again: the installer will continue the existing operation.

## How to enable a feature

1. Open the Voboost tab in the vehicle settings
2. Find the feature under Interface or Vehicle
3. Turn on the switch
4. Wait for confirmation

If the feature is incompatible with the vehicle or firmware, it remains off and displays the reason.

## Next steps

- [detailed installation guide](install.md)
- [managing modes and features](usage.md)
- [updates](updates.md)
- [complete removal](uninstall.md)
