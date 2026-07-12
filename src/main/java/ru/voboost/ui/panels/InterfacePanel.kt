package ru.voboost.ui.panels

import android.content.Context
import ru.voboost.components.i18n.Language
import ru.voboost.ui.ConfigState
import ru.voboost.ui.components.createConfigRadio
import ru.voboost.components.panel.Panel as LibraryPanel
import ru.voboost.components.section.Section as LibrarySection

/**
 * Creates the Interface panel.
 *
 * Layout: Panel -> Section (interface settings) with radio controls for:
 * - Phone numbers display format (`interfacePhoneNumbers`)
 * - Multi-display / moving apps between screens (`interfaceMultidisplay`)
 *
 * Each radio is bound to a ConfigState field via [createConfigRadio] and
 * updates the config reactively when the user selects a new value.
 */
fun createInterfacePanel(
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
            Language.EN.getCode() to localeManager.get("interface_settings", Language.EN),
            Language.RU.getCode() to localeManager.get("interface_settings", Language.RU),
        )

    val section =
        LibrarySection(context).apply {
            setTitle(sectionTitle)
            setTheme(theme)
            setLanguage(language)
            clipChildren = false
            clipToPadding = false
        }

    // Phone numbers: original / always-full-number
    section.addRadio(
        createConfigRadio(
            context = context,
            configState = configState,
            fieldPath = "interfacePhoneNumbers",
            options =
                listOf(
                    "phone_numbers_original" to "original",
                    "phone_numbers_always_full_number" to "always-full-number",
                ),
            defaultValue = "original",
            titleKey = "interface_phone_numbers",
        ),
    )

    // Multi-display: original / allow-for-all-applications
    section.addRadio(
        createConfigRadio(
            context = context,
            configState = configState,
            fieldPath = "interfaceMultidisplay",
            options =
                listOf(
                    "multidisplay_original" to "original",
                    "multidisplay_allow_for_all_applications" to "allow-for-all-applications",
                ),
            defaultValue = "original",
            titleKey = "interface_multidisplay",
        ),
    )

    panel.addView(section)
    return panel
}
