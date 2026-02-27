package ru.voboost.ui.components

import android.content.Context
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

/**
 * Tab definitions matching the TABS list from the old implementation.
 * Each tab has a value (used for selection) and localized labels.
 */
private val TAB_ITEMS: List<TabItem> =
    listOf(
        TabItem("store", mapOf("en" to "Store", "ru" to "Магазин")),
        TabItem("applications", mapOf("en" to "Applications", "ru" to "Приложения")),
        TabItem("interface", mapOf("en" to "Interface", "ru" to "Интерфейс")),
        TabItem("vehicle", mapOf("en" to "Vehicle", "ru" to "Автомобиль")),
        TabItem("settings", mapOf("en" to "Settings", "ru" to "Настройки")),
    )

/**
 * Maps Tab enum values to TabItem string values for bridging ConfigState and library Tabs.
 */
private fun Tab.toTabValue(): String = this.name

/**
 * Maps TabItem string values back to Tab enum for ConfigState.
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
 * This is the main entry point for the UI. It creates:
 * - Screen (root container with offset support)
 * - Tabs (tab bar)
 * - Panel[] (one per tab, each containing Sections with controls)
 *
 * It also sets up reactive updates by subscribing to ConfigState flows.
 *
 * @param context Android context (Activity)
 * @param configState Application config state
 * @return The root Screen view, ready to be passed to setContentView()
 */
fun buildScreen(
    context: Context,
    configState: ConfigState,
): LibraryScreen {
    val config = configState.config.value
    val currentTheme = configState.currentTheme
    val currentLanguage = configState.currentLanguage
    val selectedTabValue = configState.selectedTab.value?.toTabValue() ?: "settings"

    // Get offsets from config (defaults match library Screen defaults)
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
                configState.setSelectedTab(newValue.toTab())
            }
        }
    screen.setTabs(tabs)

    // Create panel definitions
    val panelDefinitions =
        listOf(
            createStorePanel(configState),
            createApplicationsPanel(configState),
            createInterfacePanel(configState),
            createVehiclePanel(configState),
            createSettingsPanel(configState),
        )

    // Build native Panel views from definitions
    val panels =
        panelDefinitions.map { panelDef ->
            buildPanelView(context, panelDef, configState)
        }.toTypedArray()

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
 * Sets up reactive subscriptions to update the view hierarchy when
 * config state changes (theme, language, tab selection, offsets).
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

/**
 * Updates theme on all Section children within a Panel.
 * Handles both direct Section children and ScrollView > LinearLayout > Section hierarchy.
 */
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
            is android.widget.ScrollView -> {
                if (child.childCount > 0) {
                    val scrollChild = child.getChildAt(0)
                    if (scrollChild is android.widget.LinearLayout) {
                        for (j in 0 until scrollChild.childCount) {
                            val layoutChild = scrollChild.getChildAt(j)
                            if (layoutChild is ru.voboost.components.section.Section) {
                                layoutChild.setTheme(theme)
                                layoutChild.setLanguage(language)
                                updateRadioThemes(layoutChild, theme, language)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Updates language on all Section children within a Panel.
 */
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
            is android.widget.ScrollView -> {
                if (child.childCount > 0) {
                    val scrollChild = child.getChildAt(0)
                    if (scrollChild is android.widget.LinearLayout) {
                        for (j in 0 until scrollChild.childCount) {
                            val layoutChild = scrollChild.getChildAt(j)
                            if (layoutChild is ru.voboost.components.section.Section) {
                                layoutChild.setLanguage(language)
                                updateRadioLanguages(layoutChild, language)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Updates theme on Radio children within a Section.
 */
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

/**
 * Updates language on Radio children within a Section.
 */
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
