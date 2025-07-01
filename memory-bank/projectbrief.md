# Voboost - Project Brief

## Primary Goal
Create an Android application for Voyah vehicles (Free, Dreamer) that provides a native vehicle interface for configuring car settings using the voboost-config library.

## Key Requirements

### Functional Requirements
- Native Android interface matching Voyah vehicle UI design
- Vehicle settings configuration and management
- Integration with voboost-config library for configuration handling
- Real-time configuration validation and application
- Vehicle-specific settings categories and options
- Touch-optimized interface for in-vehicle use

### Technical Requirements
- **Target Platform**: Android 9 and Android 11 only
- **Vehicle Models**: Voyah Free, Voyah Dreamer
- Kotlin/Android as primary development language
- Android UI components with vehicle-themed design
- Integration with voboost-config through dependency
- Vehicle system integration capabilities
- Optimized for automotive hardware specifications

### Vehicle Integration Requirements
- Use voboost-config for all configuration operations
- Result<T> pattern for consistent error handling
- Vehicle system API integration
- Configuration persistence in vehicle storage
- Integration with vehicle's existing systems
- Automotive-grade reliability and performance

## Architectural Principles
- Android MVVM architecture pattern
- Vehicle-specific UI/UX design language
- Asynchronous configuration processing
- Centralized vehicle state management
- Modular architecture for different vehicle models

## Success Criteria
- Native vehicle interface experience
- Seamless integration with voboost-config library
- Stable operation on target Android versions (9, 11)
- Reliable vehicle configuration management
- Professional automotive-grade user experience
- Integration with Voyah vehicle systems

## Constraints and Assumptions
- Android 9 and 11 compatibility only
- Voyah Free and Dreamer vehicle models only
- Dependency on voboost-config library
- Vehicle hardware and system constraints
- Automotive environment requirements (temperature, vibration, etc.)
- Integration with existing vehicle software stack

## Related Projects
- voboost-config: Core library for configuration management
- voboost-service: Android service for applying configuration to vehicle systems
- voboost-codestyle: Common code style rules for all Voboost projects

## Vehicle-Specific Features

### Voyah Integration
- Vehicle model detection (Free vs Dreamer)
- Model-specific configuration options
- Integration with vehicle's infotainment system
- Vehicle state monitoring and feedback
- Automotive UI patterns and interactions

### User Experience
- Touch-optimized interface for vehicle use
- High contrast display for various lighting conditions
- Large touch targets for driving scenarios
- Voice feedback and accessibility features
- Quick access to frequently used settings
