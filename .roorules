# Voboost Android App - Project Intelligence

## Global Rules (CRITICAL)
- This project follows ALL common rules from ../voboost-codestyle/.clinerules
- The rules below are PROJECT-SPECIFIC additions to the global rules
- NEVER duplicate global rules here - they are inherited automatically
- ALL code comments, documentation, and commit messages MUST be in English only
- NO Russian text in code files - use English for all technical documentation

## Project-Specific Patterns

### Application Architecture
- Android application for Voyah vehicles (Free, Dreamer models)
- Integration with voboost-config library (../voboost-config/) for vehicle configuration management
- Native vehicle interface matching Voyah design language
- Android 9 and Android 11 compatibility only

### Technology Stack
- Kotlin/Android for application development
- Android Views with vehicle-themed UI components
- voboost-config library integration for configuration handling
- MVVM architecture with Android Architecture Components
- Vehicle system integration capabilities

### Vehicle-Specific Patterns
- Voyah vehicle model detection and adaptation
- Automotive UI/UX design patterns
- Touch-optimized interface for in-vehicle use
- Vehicle system state monitoring and feedback
- Automotive-grade reliability and performance requirements

### Integration Approach
- voboost-config library as dependency for configuration operations
- Result pattern for consistent error handling
- Vehicle system API integration for configuration application
- Configuration persistence in vehicle storage systems
- Integration with existing Voyah infotainment systems

## Critical Implementation Details

### Vehicle Configuration Features
- Model-specific settings for Voyah Free and Dreamer
- Real-time vehicle configuration validation and application
- Configuration categories: Performance, Comfort, Safety, Connectivity, Energy
- Vehicle state monitoring with live feedback
- Configuration backup and restore functionality

### Android-Specific Requirements
- Target Android API levels 28 (Android 9) and 30 (Android 11)
- Automotive hardware optimization
- Touch interface optimized for vehicle environment
- High contrast display support for various lighting conditions
- Large touch targets for driving scenarios

### Vehicle System Integration
- Deep integration with Voyah vehicle systems
- Vehicle model detection and adaptation
- Real-time vehicle state monitoring
- Configuration application through vehicle APIs
- Integration with existing vehicle software stack

### Error Handling
- Graceful handling of vehicle system errors
- Configuration rollback capabilities
- Vehicle-specific error messages and recovery
- Automotive-grade error recovery procedures
- Comprehensive logging for vehicle diagnostics

### Color System Rules
- ALWAYS use `Color.TAB_SELECTED` instead of `VoboostColor.TAB_SELECTED`
- Import colors as `import ru.voboost.ui.theme.Color`
- Never use alias imports like `Color as VoboostColor`
- Components should resolve colors internally, not accept color parameters
- No theme parameters should be passed to components - use ThemeManager internally

### Performance Rules (CRITICAL for Automotive)
- NEVER create UI objects (panels, components) in Composable body - use `remember` for caching
- Cache expensive operations and object creation using `remember` with appropriate keys
- Avoid recreating panels/components on each recomposition - create once, reuse
- Use `remember(key)` to control when cached objects should be recreated
- Minimize recompositions by proper state management and key usage
- Performance is critical for automotive applications - prioritize speed and responsiveness

## Development Workflow (Project-Specific)
- Android application should provide native vehicle interface experience
- All changes should maintain automotive-grade reliability standards
- Integration with voboost-config library should be seamless
- Android 9 and 11 compatibility must be maintained
- Vehicle integration should follow Voyah platform standards
- NEVER run `./gradlew installDebug` - only use `./gradlew assembleDebug` to verify build
- User handles app installation and launch manually

## Key Success Factors
- Native Voyah vehicle interface experience with Jetpack Compose
- Seamless integration with voboost-config library
- Stable operation on Android 9 (API 28) - primary target
- Reliable vehicle configuration management with ConfigViewModel singleton
- Professional automotive-grade user experience with performance optimization
- Deep integration with Voyah vehicle systems
- Model-specific feature support (Free vs Dreamer)
- Automotive-grade performance with panel caching and optimized recomposition

## Implementation Patterns Discovered
- **ConfigViewModel Singleton Pattern**: Single instance across app lifecycle for centralized state management
- **Panel Caching Strategy**: Use `remember()` to cache panel instances and avoid recreation on recomposition
- **Compose Performance Optimization**: Critical for automotive applications - minimize object creation in Composable body
- **Localization Architecture**: Custom i18n system with LocalizedString class for type-safe string handling
- **Theme System**: Separate ColorLight/ColorDark files with unified Color access point
- **Build Integration**: Seamless integration with voboost-codestyle through gradle configuration
