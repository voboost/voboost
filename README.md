# voboost

Android application for Voyah vehicles with Jetpack Compose UI and voboost-config integration.

## What's implemented

### Core Application
- **MainActivity** - Entry point with ConfigViewModel initialization
- **Jetpack Compose UI** with Material Design 3
- **Fullscreen mode** with system UI hiding for vehicle use
- **Screen always on** flag for automotive environment

### Configuration System
- **ConfigViewModel singleton** - Centralized configuration state management
- **voboost-config integration** - YAML configuration loading and real-time file watching
- **Reactive configuration updates** via StateFlow
- **Field-level access** with dynamic field reading/writing
- **Automatic config initialization** - copies default config from assets if needed

### UI Architecture
- **Screen component** - Main layout with sidebar navigation and content area
- **Interface positioning** - X/Y offset support from configuration
- **Tab system** - Navigation between different panels
- **Panel system** - Modular content areas

### Localization System (i18n)
- **Multi-language support** - English and Russian
- **Reactive language switching** - Updates UI without restart
- **LocaleManager** - Handles resource loading and caching
- **Resource fallback** - Returns key name if translation missing
- **Type-safe localization** with `i18n()` composable function

### Theme System
- **Color management** - Light/Dark theme support with Color, ColorDark, ColorLight
- **Dimensions and Typography** - Centralized design tokens
- **Material Design 3** integration

### Build Configuration
- **Android API 28** (Android 9 compatibility)
- **Kotlin 1.9.25** with Compose
- **voboost-config dependency** as project reference
- **ktlint integration** with voboost-codestyle

## Project Structure

```
src/main/java/ru/voboost/
├── MainActivity.kt                    # Application entry point
├── ui/
│   ├── components/                    # Compose UI components
│   ├── panels/                        # Configuration panels
│   ├── theme/                         # Theme system
│   ├── ConfigViewModel.kt             # Singleton configuration state
│   └── I18n.kt                        # Localization system
```

## Technical Details

### ConfigViewModel Features
- **Singleton pattern** with thread-safe initialization
- **voboost-config integration** - ConfigManager wrapper
- **Real-time file watching** with OnConfigChangeListener
- **Dynamic field access** - `getFieldValue()` and `updateField()`
- **StateFlow integration** - Reactive UI updates
- **Enum conversion** - Automatic string to enum conversion for Language/Theme

### Localization Features
- **Resource caching** - Avoids repeated context creation
- **Reactive updates** - StateFlow-based language changes
- **Error handling** - Graceful fallback to key names
- **Android Resources integration** - Uses standard string resources

### Screen Features
- **Fullscreen mode** - Hides system bars for vehicle use
- **Keep screen on** - Prevents sleep during vehicle operation
- **Interface positioning** - X/Y offset from configuration
- **Edge-to-edge display** - Modern Android UI patterns

## Build Commands

```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew test
```

## Requirements

- Android API 28+ (Android 9+)
- voboost-config library (project dependency)
- Kotlin/Compose development environment
