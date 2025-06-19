# Technical Context: voboost

## Technologies Used

### Core Languages & Frameworks
- **Kotlin:**
  - Version: 1.9.0
  - Features leveraged:
    - Coroutines for async operations
    - Flow for reactive programming
    - Null safety throughout codebase
    - Extension functions for UI utilities
- **Android SDK:** Targeting Android 9 (API Level 28) for broad compatibility within the target vehicle ecosystem.
- **Android Jetpack Libraries:** Leveraging modern Android development components for robust and performant applications.
    - **Architecture Components:** ViewModel, LiveData, Room (if local DB is needed for specific features, not for config).
    - **Navigation Component:** For managing UI navigation between tabs and screens.
    - **Coroutines:** For asynchronous programming and structured concurrency.

### UI & UX
- **XML Layouts:** For defining the user interface structure for Activities and Fragments, ensuring compatibility with device hardware and visual consistency.
- **Material Design:** Adhering to Material Design guidelines where appropriate for a consistent and modern UI/UX, while also mimicking the car's native interface.
- **Custom Views/Components:** For unique UI elements like drag-and-drop, custom selectors, and message boxes.

### Configuration Management
- **Configuration Management:**
  - **Format:** JSON (.json extension)
  - **Library:** Kotlinx Serialization 1.5.1
    - Supports polymorphic serialization
    - Efficient Kotlin-native parsing
    - Custom comment support via annotations
  - **File Monitoring:** Android FileObserver
    - Real-time change detection
    - Event-based notifications
  - **Features:**
    - Schema validation (@Serializable classes)
    - Atomic writes with backup files
    - Example structure:
      ```json
      {
        "settings": {
          "language": "ru",
          "theme": "dark",
          "active-tab": "vehicle"
        },
        "vehicle": {
          "steering-wheel-buttons": ["radio", "climate"]
        }
      }
      ```
- **File I/O APIs:** Android's standard file system APIs for reading and writing configuration files.
- **FileObserver (or equivalent):** For monitoring changes to configuration files to trigger reactive updates.

### Network Communication (for Store Tab)
- **OkHttp:** For efficient HTTP client operations for downloading files and checking for updates.
- **Retrofit:** For type-safe HTTP client for API interactions (e.g., calling Huawei update servers).
- **Android's DownloadManager:** Potentially for background file downloads with system integration.

### Image Processing
- **Android's Bitmap APIs:** For loading, manipulating, and rendering images, specifically for applying grayscale filters.
- **UI Libraries:**
  - **Glide/Picasso (or similar image loading library):** For efficient image loading and caching in the UI
  - **Material Components:** Version 1.9.0
  - **ViewPager2:** For tabbed interface
  - **ConstraintLayout:** Version 2.1.4 for complex UIs
  - **RecyclerView:** Version 1.3.1 for app lists

### Testing
- **JUnit:** For unit testing business logic and data layers.
- **Mockito/MockK:** For mocking dependencies in tests.
- **AndroidX Test Library:** For instrumented and UI tests (e.g., Espresso).

## Development Setup

### IDE
- **Android Studio:** Official IDE for Android development, providing rich features including code editing, debugging, UI design tools, and profiling.

### Build System
- **Gradle:** Build automation system. Using Kotlin DSL for build scripts for better type safety and IDE support.

### Version Control
- **Git:** For source code management, hosted on a platform (e.g., GitHub, GitLab, Bitbucket) adhering to open-source principles.

## Technical Constraints

- **Android 9 (API Level 28):** The base API level constrains available platform features and requires adherence to its behavior changes and permissions model.
- **Device-Specific Customizations:** The need to mimic the car's native interface might require careful attention to styling and component choices.
- **System Permissions:** Certain operations (e.g., installing packages, modifying system settings) will require specific Android permissions, which need to be handled gracefully (runtime permissions).
- **Limited Resources (if implied by embedded system):** While not explicitly stated, embedded systems can sometimes have constrained CPU, memory, and storage. The design should be mindful of resource efficiency, especially for UI rendering and background processes.
- **Inter-process Communication:** If `voboost` and `voboost-service` need to communicate beyond shared file access, Android's IPC mechanisms (e.g., Bound Services, Broadcasts) would need to be considered.

## Dependencies

- **AndroidX Libraries:** Core Android libraries providing backward compatibility and modern features.
- **UI Management Components:**
  - **ViewPortManager:**
    - Uses WindowMetrics API
    - Reacts to configuration changes
    - Version: AndroidX Window 1.1.0
  - **IconManager:**
    - Uses ColorMatrix for grayscale
    - Integrates with Glide
    - Version: 1.0 (custom)

- **Dependency Versions:**
  - Kotlin: 1.9.0
  - Kotlinx Serialization: 1.5.1
  - Coroutines: 1.7.3
  - Glide: 4.15.1
  - AndroidX Core: 1.10.1
  - AndroidX Window: 1.1.0
- **Networking Libraries (OkHttp, Retrofit):** For network operations.
- **Image Loading Libraries (Glide/Picasso):** For efficient image handling.
- **Testing Libraries (JUnit, Mockito/MockK, AndroidX Test):** For ensuring code quality.