# Voboost

Android application for Voyah vehicles (Free, Dreamer models) that provides system customization through Frida script injection.

## Features

- **Weather widget customization** - Enable non-Chinese cities in weather widget
- **Russian keyboard support** - Add Russian keyboard layout to vehicle input system
- **Forced EV mode** - Force electric-only mode on Voyah Free (prevents automatic hybrid switching)
- **Multi-display application support** - Enable apps on passenger display
- **Phone number formatting** - Format phone numbers according to regional standards
- **Settings menu integration** - Add Voboost menu item to vehicle settings

## Architecture

The application uses a platform-agnostic core that runs on both Android and Desktop:

### Core Components

- **[`Main`](src/main/java/ru/voboost/Main.kt)** - Main orchestrator that coordinates feature management based on configuration
- **[`FeatureManager`](src/main/java/ru/voboost/FeatureManager.kt)** - Manages feature lifecycle (enable/disable/reload)
- **[`FridaManager`](src/main/java/ru/voboost/FridaManager.kt)** - Platform-specific Frida script injection (Android/Desktop implementations)
- **[`VehicleManager`](src/main/java/ru/voboost/VehicleManager.kt)** - Platform-specific vehicle information access
- **[`Paths`](src/main/java/ru/voboost/Paths.kt)** - Platform-specific path resolution for config, scripts, and logs

### Feature System

Features are modular components that extend Voboost functionality:

- **[`Feature`](src/main/java/ru/voboost/feature/Feature.kt)** - Base interface for all features
- **[`FeatureFrida`](src/main/java/ru/voboost/feature/FeatureFrida.kt)** - Abstract base for Frida-based features
- **[`FeatureNative`](src/main/java/ru/voboost/feature/FeatureNative.kt)** - Abstract base for native Android features

### Implemented Features

- **[`FeatureFridaWeather`](src/main/java/ru/voboost/feature/FeatureFridaWeather.kt)** - Weather widget customization
- **[`FeatureFridaKeyboard`](src/main/java/ru/voboost/feature/FeatureFridaKeyboard.kt)** - Russian keyboard support
- **[`FeatureFridaForcedEV`](src/main/java/ru/voboost/feature/FeatureFridaForcedEV.kt)** - Forced EV mode (Free only)
- **[`FeatureFridaSettingsMenu`](src/main/java/ru/voboost/feature/FeatureFridaSettingsMenu.kt)** - Settings menu integration

## Building

### Android

Build the APK for installation on vehicle:

```bash
./gradlew assembleDebug
```

**Note:** Do not use `./gradlew installDebug` - build only. Installation and launch are handled manually on the vehicle.

### Desktop Testing

Build and run desktop version for local testing:

```bash
./gradlew runDesktop
```

## Desktop Testing

For local development without Android emulator or physical device:

### Prerequisites

1. **Install Frida tools:**
   ```bash
   pip3 install frida-tools
   ```

2. **Install Java (JDK 11+):**
   ```bash
   brew install openjdk@11
   ```

3. **Build Frida scripts:**
   ```bash
   cd ../voboost-script
   npm install
   npm run build
   ```

### 3-Step Testing Workflow

#### Step 1: Start stub process

Compile and run a stub application that simulates an Android process:

```bash
cd ../voboost-stubs/apps
javac com/qinggan/app/launcher/LauncherStub.java
java com.qinggan.app.launcher.LauncherStub
```

Keep this terminal running.

#### Step 2: Configure vehicle (optional)

Set environment variables to simulate vehicle configuration:

```bash
export VOBOOST_VEHICLE_MODEL=free
export VOBOOST_VEHICLE_YEAR=2023
export VOBOOST_LANGUAGE=ru
```

Or create `~/.voboost/vehicle.yaml`:

```yaml
model: free
year: 2023
language: ru
```

#### Step 3: Run desktop

In another terminal, run the desktop version:

```bash
cd ../voboost
./gradlew runDesktop
```

Expected output:

```
[+] MainDesktop: Starting Voboost Desktop
[+] MainDesktop: Vehicle: free (2023)
[+] MainDesktop: Config file: /path/to/config.yaml
[+] Main: Starting Voboost
[+] FeatureManager: Enabled: interface-widget-weather
[+] MainDesktop: Running. Press Ctrl+C to exit.
```

### Available Stubs

| Stub | Process Name | Features |
|------|--------------|----------|
| LauncherStub | com.qinggan.app.launcher | Weather widget, App launcher, Navbar |
| BluetoothPhoneStub | com.qinggan.bluetoothphone | Phone number formatting |
| SystemServiceStub | com.qinggan.systemservice | Multi-display, Settings menu, Forced EV |
| QgimeStub | com.qinggan.app.qgime | Russian keyboard |
| VehicleSettingStub | com.qinggan.app.vehiclesetting | Media source |

## Configuration

Configuration is managed by the [`voboost-config`](../voboost-config) library. The configuration file is located at:

- **Android:** `/data/data/ru.voboost/files/config.yaml`
- **Desktop:** `~/.voboost/config.yaml` or `../voboost-config/src/config.yaml`

### Key Settings

#### Application Settings

- **`settings-language`**: `en` | `ru` - Interface language
- **`settings-theme`**: `light` | `dark` - Interface theme
- **`settings-interface-shift-x`**: Integer - Horizontal interface offset (pixels)
- **`settings-interface-shift-y`**: Integer - Vertical interface offset (pixels)
- **`settings-active-tab`**: `store` | `settings` | etc. - Default active tab

#### Vehicle Configuration

- **`vehicle-model`**: `free` | `dreamer` - Vehicle model
- **`vehicle-fuel-mode`**: `original` | `electric` | `electric-forced` | `hybrid` | `save`
  - `original` - No modification
  - `electric` - Electric mode preference
  - `electric-forced` - Force electric-only mode (Free only, prevents automatic hybrid switching)
  - `hybrid` - Hybrid mode preference
  - `save` - Energy saving mode
- **`vehicle-drive-mode`**: `comfort` | `sport` | `eco` - Driving mode preference

#### Interface Features

- **`interface-widget-weather`**: `original` | `enable-non-chineese-cities`
  - `original` - Default weather widget behavior
  - `enable-non-chineese-cities` - Enable weather for non-Chinese cities

- **`interface-keyboard`**: `original` | `disable-chinese` | `enable-russian`
  - `original` - Default keyboard layouts
  - `disable-chinese` - Hide Chinese keyboard
  - `enable-russian` - Add Russian keyboard layout

### Configuration Example

```yaml
# Application Settings
settings-language: ru
settings-theme: light
settings-interface-shift-x: 0
settings-interface-shift-y: 0
settings-active-tab: store

# Vehicle Configuration
vehicle-fuel-mode: electric-forced
vehicle-drive-mode: comfort
vehicle-model: free

# Interface Features
interface-widget-weather: enable-non-chineese-cities
interface-keyboard: enable-russian
```

## Logging System

The application uses a centralized logging system ([`Logger`](src/main/java/ru/voboost/Logger.kt)) that writes logs to files.

### Log Levels

| Level | Tag | When to Use |
|-------|-----|-------------|
| **info** | `[+]` | Important events (startup, user actions, significant operations) |
| **debug** | `[*]` | Technical details (method calls, intermediate values, validation) |
| **error** | `[-]` | Errors and exceptions |

### Log Files

- **Android location:** `/data/data/ru.voboost/files/logs/`
- **Desktop location:** `~/.voboost/logs/`
- **Filename pattern:** `voboost-YYYY-MM-DD.log`
- **Rotation:** Daily
- **Retention:** 7 days

### Log Format

```
YYYY-MM-DD HH:mm:ss.SSS [tag] [source] message
```

Example:
```
2024-12-14 14:30:45.123 [+] Main: Starting Voboost
2024-12-14 14:30:45.456 [*] FeatureManager: Checking feature: interface-widget-weather
2024-12-14 14:30:46.012 [-] FridaManager: Failed to inject script
```

## Project Structure

```
src/main/java/ru/voboost/
├── Main.kt                           # Main orchestrator
├── MainActivity.kt                   # Android entry point
├── MainDesktop.kt                    # Desktop entry point
├── BootReceiver.kt                   # Auto-start on boot
├── Logger.kt                         # Centralized logging
├── Paths.kt                          # Path resolution interface
├── FridaManager.kt                   # Frida injection interface
├── FridaManagerAndroid.kt            # Android Frida implementation
├── FridaManagerDesktop.kt            # Desktop Frida implementation
├── FridaAgentManager.kt              # Frida agent lifecycle management
├── VehicleManager.kt                 # Vehicle info interface
├── FeatureManager.kt                 # Feature lifecycle management
└── feature/
    ├── Feature.kt                    # Feature interface
    ├── FeatureFrida.kt               # Frida feature base
    ├── FeatureNative.kt              # Native feature base
    ├── FeatureFridaWeather.kt        # Weather widget feature
    ├── FeatureFridaKeyboard.kt       # Russian keyboard feature
    ├── FeatureFridaForcedEV.kt       # Forced EV mode feature
    └── FeatureFridaSettingsMenu.kt   # Settings menu feature
```

## Development Guidelines

- **[Compose Performance Guidelines](docs/COMPOSE.md)** - Critical performance rules for Jetpack Compose
- **[Color System](src/main/java/ru/voboost/ui/theme/Color.kt)** - Theme-aware color usage patterns

## Requirements

- **Android API 28+** (Android 9+) - Primary target is Android 9 (API 28)
- **voboost-config library** - Configuration management (project dependency)
- **voboost-script** - Frida scripts for feature implementation
- **Kotlin/Compose** - Development environment
- **Frida** - Dynamic instrumentation toolkit

## Related Projects

- **[voboost-config](../voboost-config)** - Configuration management library
- **[voboost-script](../voboost-script)** - Frida scripts for feature implementation
- **[voboost-stubs](../voboost-stubs)** - Stub applications for desktop testing
- **[voboost-codestyle](../voboost-codestyle)** - Code style and linting rules


## License

Dual-licensed:

- [PolyForm Noncommercial 1.0.0](https://github.com/voboost/voboost-license/blob/main/LICENSE) — free for personal use
- [Commercial license](https://github.com/voboost/voboost-license/blob/main/COMMERCIAL.md) — required otherwise
