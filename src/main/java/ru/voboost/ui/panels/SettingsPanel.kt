package ru.voboost.ui.panels

import android.content.Context
import kotlinx.coroutines.launch
import ru.voboost.components.i18n.Language
import ru.voboost.ui.ConfigState
import ru.voboost.ui.components.DaemonStatusSection
import ru.voboost.ui.components.createConfigRadio
import ru.voboost.components.panel.Panel as LibraryPanel
import ru.voboost.components.section.Section as LibrarySection

fun createSettingsPanel(
    context: Context,
    configState: ConfigState,
): LibraryPanel {
    val panel =
        LibraryPanel(context).apply {
            setTheme(configState.currentTheme)
        }

    val localeManager = configState.localeManager
    val theme = configState.currentTheme
    val language = configState.currentLanguage

    val sectionTitle =
        mapOf(
            Language.EN.getCode() to localeManager.get("settings_title", Language.EN),
            Language.RU.getCode() to localeManager.get("settings_title", Language.RU),
        )

    val section =
        LibrarySection(context).apply {
            setTitle(sectionTitle)
            setTheme(theme)
            setLanguage(language)
            clipChildren = false
            clipToPadding = false
        }

    // Theme: dark / light
    section.addRadio(
        createConfigRadio(
            context = context,
            configState = configState,
            fieldPath = "settingsTheme",
            options =
                listOf(
                    "theme_option_dark" to "dark",
                    "theme_option_light" to "light",
                ),
            defaultValue = "dark",
        ),
    )

    // Car model: free / dreamer
    section.addRadio(
        createConfigRadio(
            context = context,
            configState = configState,
            fieldPath = "settingsCarModel",
            options =
                listOf(
                    "car_model_free" to "free",
                    "car_model_dreamer" to "dreamer",
                ),
            defaultValue = "free",
        ),
    )

    // Language: en / ru
    section.addRadio(
        createConfigRadio(
            context = context,
            configState = configState,
            fieldPath = "settingsLanguage",
            options =
                listOf(
                    "language_en" to "en",
                    "language_ru" to "ru",
                ),
            defaultValue = "en",
        ),
    )

    panel.addView(section)

    // Add daemon status section
    val daemonStatusSection = DaemonStatusSection(context, localeManager)
    daemonStatusSection.setTheme(theme)
    daemonStatusSection.setLanguage(language)
    panel.addView(daemonStatusSection.container)

    // Subscribe to status updates
    configState.scope.launch {
        configState.statusState.status.collect { status ->
            daemonStatusSection.updateStatus(status)
        }
    }

    // Subscribe to theme/language changes to update daemon status section
    configState.scope.launch {
        configState.themeFlow.collect { newTheme ->
            val currentTheme = newTheme ?: ru.voboost.components.theme.Theme.FREE_DARK
            daemonStatusSection.setTheme(currentTheme)
        }
    }

    configState.scope.launch {
        configState.languageFlow.collect { newLanguage ->
            val currentLanguage = newLanguage ?: Language.EN
            daemonStatusSection.setLanguage(currentLanguage)
        }
    }

    return panel
}
