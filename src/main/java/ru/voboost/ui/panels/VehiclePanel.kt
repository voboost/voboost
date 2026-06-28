package ru.voboost.ui.panels

import android.content.Context
import ru.voboost.components.i18n.Language
import ru.voboost.config.models.PedestrianWarning
import ru.voboost.ui.ConfigState
import ru.voboost.ui.components.createConfigRadio
import ru.voboost.components.panel.Panel as LibraryPanel
import ru.voboost.components.section.Section as LibrarySection

fun createVehiclePanel(
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
            Language.EN.getCode() to localeManager.get("vehicle_settings", Language.EN),
            Language.RU.getCode() to localeManager.get("vehicle_settings", Language.RU),
        )

    val section =
        LibrarySection(context).apply {
            setTitle(sectionTitle)
            setTheme(theme)
            setLanguage(language)
            clipChildren = false
            clipToPadding = false
        }

    section.addRadio(
        createConfigRadio(
            context = context,
            configState = configState,
            fieldPath = "vehiclePedestrianWarning",
            options =
                PedestrianWarning.values().map { warn ->
                    "pedestrian_warning_${warn.name}" to warn.name
                },
            defaultValue = PedestrianWarning.original.name,
            titleKey = "vehicle_pedestrian_warning",
        ),
    )

    panel.addView(section)
    return panel
}
