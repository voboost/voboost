# Project Brief: voboost

## Core Purpose
To provide a dedicated Android application for configuring the parameters of Voyah Free and Voyah Dreamer vehicles.

## Guiding Principles
- **Open Source:** All components and code are intended to be open source.
- **Modularity:** Functionality that can be developed as independent components/applications should be separated. Configuration should be external and dynamically loaded.
- **Native UI Adherence:** The application's appearance should closely mimic the existing car settings interface, aiming for minimal native interface modification and maximal configuration.
- **Flexibility:** Avoid hardcoding; all changeable parameters should be configurable and reversible.

## Current Application Focus: `voboost`
- This application will primarily focus on reading and writing configuration files.
- It will feature a user interface (UI) to interact with these configurations.

## Related Service: `voboost-service`
- A separate application, `voboost-service`, will handle applying configuration changes directly to the car's settings. This is outside the scope of the current `voboost` application.

## Key Technical Decisions
- **Language:** Kotlin (officially chosen after detailed analysis against Java)
  - **Advantages:**
    - Native null safety prevents common crashes
    - Coroutines simplify async config file operations
    - More concise syntax (estimated 40% less code)
    - Modern Android development (Google's preferred language)
    - Better library support for reactive programming
    - Smaller APK size (typically 10-15% reduction)
    - Better performance in most benchmarks
  - **Version:** Kotlin 1.9.0
- **Communication Language:** All code comments, commit messages, Memory Bank content, issues and PR comments will be in English.

## Shared Libraries
- **`voboost-lib-config`**: A shared library to manage configuration files.
    - **Implementation Details:**
      - Uses JSON format with Kotlinx Serialization library
      - Supports dot notation for nested sections (e.g., "settings.language")
      - Implements FileObserver for real-time change detection
      - Provides reactive updates via Kotlin Flows
      - Supports atomic partial updates
      - Maintains change history for rollback capability

## `voboost` UI Features
- **Multilingual Support:** English (default) and Russian.
- **Theming:** Dark (default) and Light themes.
- **UI Offset:** Configurable X and Y axis shifts.
- **Dynamic UI:** Adapts to viewport changes.
- **Icon Manipulation:** Ability to render icons black-and-white with a grayscale palette.

## Core UI Components
- **UI Architecture:**
  - **`MainActivity`**: Main application entry point with ViewModel
  - **Tab Structure**:
    - Store: Applications store, provides ability to install, uninstall, upgrade applications
    - Applications: Provides an ability to setup installed applications to vehicle user interface
    - Interface: Provides an ability to change vehicle user interface settings
    - Vehicle: Provides an ability to change vehicle settings
    - Settings: Provides an ability to change application settings
  - **`ThemeManager`**:
    - Handles dark/light theme switching
    - Applies grayscale filtering to icons
    - Manages dynamic viewport adjustments
  - **`LayoutManager`**:
    - Manages application layout shift defined in application settings
    - Manages dynamic viewport adjustments
- **Custom Components**:
    - Tabs (5 tabs at left with panes at right)
    - Radio (toggle with dynamic background animation on switching)
    - Checkbox
    - Drag-and-drop component for reordering items (e.g., applications on the steering wheel).
    - Message box (text + yes/no buttons).
    - Full-screen select component for single-item selection.

## `Store` Tab Specifics
- File download component with progress indicators.
- Information retrieval for installed packages.
- New version check from a server (e.g., Huawei).
- Application installation, permission granting, and uninstallation.