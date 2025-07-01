# Active Context: voboost

## Current Work Focus
The current primary focus is on establishing the foundational project structure and setting up the initial development environment. This includes:
1.  **Project Initialization:** Creating the basic Android project for `voboost`.
2.  **Memory Bank Setup:** Documenting the initial project context, principles, and technical decisions through the core Memory Bank files. This is crucial for clear communication and alignment with RooCode's workflow.

## Recent Changes
- The project has been defined as `voboost`, an Android application targeting Android 9.
- **Language Decision Finalized:** Kotlin 1.9.0 officially selected after thorough analysis. Key factors:
  - 40% reduction in code size vs Java
  - Built-in null safety prevents common crashes
  - Coroutines simplify async config operations
  - Kotlinx Serialization for JSON handling
  - Modern Android development standard
- The high-level architecture has been conceptualized, emphasizing modularity with a shared `voboost-config` library.
- Initial specifications for UI, theming, internationalization, and custom components have been captured.
- The `projectbrief.md`, `productContext.md`, `systemPatterns.md`, and `techContext.md` Memory Bank files have been created. This provides a solid initial documentation base.

## Next Steps
## Current Phase: UI Implementation & Config Library Development
1. **Core Configuration Library:**
   - Implement FileObserver for config changes
   - Setup Kotlinx Serialization parser
   - Create reactive Flow pipeline
   - Build diff/merge capabilities

2. **UI Foundations:**
   - Create MainActivity with ViewModel
   - Setup ViewPager2 for tabs
   - Implement ThemeManager
   - Create base fragments for each tab

3. **Next Deliverables:**
   - Store tab download component
   - Applications tab drag-and-drop
   - Interface theming controls
   - Vehicle parameter editors

## Active Decisions and Considerations
- **Technical Decisions Finalized:**
  - JSON with .json extension
  - Kotlinx Serialization selected
  - Reactive architecture with Flows
  - Material Components 1.9.0 for UI
  - Glide 4.15.1 for images
  - ViewPortManager for display metrics
  - IconManager for grayscale handling

- **Open Items:**
  - Huawei API integration details
  - Final vehicle parameter schema
  - Advanced theming requirements
- **UI Component Libraries:** Research into existing Android libraries for tabbed interfaces and custom UI components will be conducted during the implementation phase.
- **Huawei Integration:** The exact mechanism and APIs for checking new versions from a "Huawei" server for the "Store" tab needs further investigation and clarification. Is this referring to Huawei AppGallery Connect APIs, or a custom internal Huawei server?
- **Android Permissions:** A thorough review of necessary Android permissions for package management (installing, uninstalling apps) and accessing configuration files will be required. Special attention will be paid to `REQUEST_INSTALL_PACKAGES` and potential system-level permissions on vehicle head units.