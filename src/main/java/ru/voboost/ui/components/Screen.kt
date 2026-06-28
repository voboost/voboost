package ru.voboost.ui.components

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import ru.voboost.components.i18n.Language
import ru.voboost.components.tabs.TabItem
import ru.voboost.components.theme.Theme
import ru.voboost.config.models.Tab
import ru.voboost.ui.ConfigState
import ru.voboost.ui.panels.createApplicationsPanel
import ru.voboost.ui.panels.createInterfacePanel
import ru.voboost.ui.panels.createSettingsPanel
import ru.voboost.ui.panels.createStorePanel
import ru.voboost.ui.panels.createVehiclePanel
import ru.voboost.components.panel.Panel as LibraryPanel
import ru.voboost.components.screen.Screen as LibraryScreen
import ru.voboost.components.tabs.Tabs as LibraryTabs

private const val TAB_VEHICLE_MORE = "vehicle_more"

/**
 * Tab definitions for the main navigation.
 *
 * Layout:
 * - Store, Applications, Interface: disabled (placeholder)
 * - Vehicle: pedestrian warning settings
 * - Settings: theme, car model, language
 * - Vehicle >: launches native CarSettingActivity
 */
private val TAB_ITEMS: List<TabItem> =
    listOf(
        TabItem("store", mapOf("en" to "Store", "ru" to "Магазин"), false),
        TabItem("applications", mapOf("en" to "Applications", "ru" to "Приложения"), false),
        TabItem("interface", mapOf("en" to "Interface", "ru" to "Интерфейс"), false),
        TabItem("vehicle", mapOf("en" to "Vehicle", "ru" to "Машина")),
        TabItem("settings", mapOf("en" to "Settings", "ru" to "Настройки")),
        TabItem(
            TAB_VEHICLE_MORE,
            mapOf("en" to "Vehicle", "ru" to "Машина"),
            true,
            30,
            true,
        ),
    )

/**
 * Maps Tab enum values to TabItem string values.
 */
private fun Tab.toTabValue(): String = this.name

/**
 * Maps TabItem string values back to Tab enum.
 * Non-matching values (e.g., vehicle_more) fall back to settings.
 */
private fun String.toTab(): Tab =
    try {
        Tab.valueOf(this)
    } catch (e: IllegalArgumentException) {
        Tab.settings
    }

/**
 * Builds the complete native Screen view hierarchy.
 *
 * Creates Screen -> Tabs -> Panels using library components directly.
 * Sets up reactive updates by subscribing to ConfigState flows.
 */
fun buildScreen(
    context: Context,
    configState: ConfigState,
): LibraryScreen {
    val config = configState.config.value
    val currentTheme = configState.currentTheme
    val currentLanguage = configState.currentLanguage
    val selectedTabValue = configState.selectedTab.value?.toTabValue() ?: "settings"

    val offsetX = config?.settingsInterfaceShiftX ?: 145
    val offsetY = config?.settingsInterfaceShiftY ?: 50

    // Create Screen
    val screen =
        LibraryScreen(context).apply {
            setTheme(currentTheme)
            setOffsetX(offsetX)
            setOffsetY(offsetY)
            clipChildren = false
            clipToPadding = false
        }

    // Create Tabs
    val tabs =
        LibraryTabs(context).apply {
            setTheme(currentTheme)
            setLanguage(currentLanguage)
            setItems(TAB_ITEMS)
            setSelectedValue(selectedTabValue)

            setOnValueChangeListener { newValue ->
                if (newValue == TAB_VEHICLE_MORE) {
                    // Launch native vehicle settings app
                    try {
                        val intent =
                            Intent().apply {
                                component =
                                    ComponentName(
                                        "com.qinggan.app.vehiclesetting",
                                        "com.qinggan.app.vehiclesetting.CarSettingActivity",
                                    )
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Expected to fail on non-Voyah devices
                    }
                    // Do not save vehicle_more as active tab
                } else {
                    configState.setSelectedTab(newValue.toTab())
                }
            }
        }
    screen.setTabs(tabs)

    // Create panels (one per tab item)
    val panels =
        arrayOf(
            createStorePanel(context, configState),
            createApplicationsPanel(context, configState),
            createInterfacePanel(context, configState),
            createVehiclePanel(context, configState),
            createSettingsPanel(context, configState),
            // empty for Vehicle >
            LibraryPanel(context).apply { setTheme(currentTheme) },
        )
    screen.setPanels(panels)

    // Set initial active panel
    val initialIndex = TAB_ITEMS.indexOfFirst { it.value == selectedTabValue }
    if (initialIndex >= 0) {
        screen.setActivePanel(initialIndex)
    }

    // Subscribe to reactive state changes
    setupReactiveUpdates(screen, tabs, panels, configState)

    return screen
}

/**
 * Sets up reactive subscriptions for theme, language, tab selection, and offset changes.
 */
private fun setupReactiveUpdates(
    screen: LibraryScreen,
    tabs: LibraryTabs,
    panels: Array<LibraryPanel>,
    configState: ConfigState,
) {
    // React to theme changes
    configState.scope.launch {
        configState.themeFlow.collect { theme ->
            val currentTheme = theme ?: Theme.FREE_DARK
            screen.setTheme(currentTheme)
            tabs.setTheme(currentTheme)
            panels.forEach { panel ->
                panel.setTheme(currentTheme)
                updateSectionThemes(panel, currentTheme, configState.currentLanguage)
            }
        }
    }

    // React to language changes
    configState.scope.launch {
        configState.languageFlow.collect { language ->
            val currentLanguage = language ?: Language.EN
            tabs.setLanguage(currentLanguage)
            panels.forEach { panel ->
                updateSectionLanguages(panel, currentLanguage)
            }
        }
    }

    // React to tab selection changes
    configState.scope.launch {
        configState.selectedTab.collect { tab ->
            val tabValue = tab?.toTabValue() ?: "settings"
            tabs.setSelectedValue(tabValue)

            val activeIndex = TAB_ITEMS.indexOfFirst { it.value == tabValue }
            if (activeIndex >= 0) {
                screen.setActivePanel(activeIndex)
            }
        }
    }

    // React to config changes (for offsets)
    configState.scope.launch {
        configState.config.collect { config ->
            config?.let {
                screen.setOffsetX(it.settingsInterfaceShiftX ?: 145)
                screen.setOffsetY(it.settingsInterfaceShiftY ?: 50)
            }
        }
    }
}

private fun updateSectionThemes(
    panel: LibraryPanel,
    theme: Theme,
    language: Language,
) {
    for (i in 0 until panel.childCount) {
        val child = panel.getChildAt(i)
        when (child) {
            is ru.voboost.components.section.Section -> {
                child.setTheme(theme)
                child.setLanguage(language)
                updateRadioThemes(child, theme, language)
            }
        }
    }
}

private fun updateSectionLanguages(
    panel: LibraryPanel,
    language: Language,
) {
    for (i in 0 until panel.childCount) {
        val child = panel.getChildAt(i)
        when (child) {
            is ru.voboost.components.section.Section -> {
                child.setLanguage(language)
                updateRadioLanguages(child, language)
            }
        }
    }
}

private fun updateRadioThemes(
    section: ru.voboost.components.section.Section,
    theme: Theme,
    language: Language,
) {
    for (i in 0 until section.childCount) {
        val child = section.getChildAt(i)
        if (child is ru.voboost.components.radio.Radio) {
            child.setTheme(theme)
            child.setLanguage(language)
        }
    }
}

private fun updateRadioLanguages(
    section: ru.voboost.components.section.Section,
    language: Language,
) {
    for (i in 0 until section.childCount) {
        val child = section.getChildAt(i)
        if (child is ru.voboost.components.radio.Radio) {
            child.setLanguage(language)
        }
    }
}
