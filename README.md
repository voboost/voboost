# Voboost

An Android application for Voyah vehicles (Free, Dreamer) that provides a native vehicle interface for configuring car settings with seamless integration to the voboost-config library.

## Overview

Voboost delivers a in-vehicle configuration experience for Voyah Free and Dreamer models. Built with Kotlin/Android and integrated with the voboost-config library, it provides a native vehicle interface that matches the Voyah design language while offering comprehensive vehicle settings management.

## Features

- **Native Vehicle Interface**: UI designed to match Voyah vehicle infotainment system
- **Vehicle Settings Management**: Comprehensive configuration of vehicle parameters
- **Real-time Validation**: Instant feedback on configuration changes
- **Model-Specific Options**: Tailored settings for Voyah Free and Dreamer models
- **Touch-Optimized**: Interface designed for in-vehicle touch interaction
- **Vehicle Integration**: Deep integration with Voyah vehicle systems

## Target Platform

- **Android Versions**: Android 9 and Android 11
- **Vehicle Models**: Voyah Free, Voyah Dreamer
- **Hardware**: Automotive-grade Android hardware in Voyah vehicles

## Technology Stack

- **Language**: Kotlin/Android
- **UI Framework**: Android Views with vehicle-themed components
- **Configuration Library**: voboost-config
- **Architecture**: MVVM with Android Architecture Components
- **Build System**: Gradle with Kotlin DSL
- **Code Style**: Unified Voboost code style

## Project Structure

```
voboost/
├── app/src/main/kotlin/      # Main Android application source
├── app/src/test/kotlin/      # Unit tests
├── app/src/androidTest/      # Android instrumentation tests
├── app/src/main/res/         # Android resources (layouts, drawables, etc.)
├── memory-bank/              # Project documentation
├── .clinerules              # Project-specific rules
├── .editorconfig            # Code style configuration (symlink)
└── build.gradle.kts         # Build configuration
```

## Development Setup

### Prerequisites

- Android Studio Arctic Fox or newer
- JDK 11 or higher
- Android SDK with API levels 28 (Android 9) and 30 (Android 11)
- Voyah vehicle hardware or emulator setup

### Building

```bash
./gradlew assembleDebug
```

### Running

```bash
./gradlew installDebug
```

### Testing

```bash
./gradlew test
./gradlew connectedAndroidTest
```

## Integration with voboost-config

This application integrates with the voboost-config library for:

- Vehicle configuration parsing and validation
- YAML structure management for vehicle settings
- Error handling and reporting
- Configuration diff utilities for vehicle state changes

## Vehicle-Specific Features

### Voyah Integration
- Automatic vehicle model detection (Free vs Dreamer)
- Model-specific configuration options and UI
- Integration with vehicle's existing infotainment system
- Vehicle state monitoring and real-time feedback

## Code Style

This project follows the unified Voboost code style defined in `../voboost-codestyle/`. Key rules:

- All code, comments, and documentation in English
- Kotlin code style with 4-space indentation
- Result<T> pattern for error handling
- KDoc comments for public APIs
- Files must end with blank line

## Contributing

1. Follow the code style guidelines
2. Write comprehensive tests (unit and instrumentation)
3. Test on target Android versions (9, 11)
4. Update documentation as needed
5. Ensure all tests pass before committing

## Related Projects

- [voboost-config](../voboost-config/): Core configuration library
- [voboost-service](../voboost-service/): Android service for configuration application
- [voboost-codestyle](../voboost-codestyle/): Unified code style rules

## License

[License information to be added]

