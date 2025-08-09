package ru.voboost.ui.panels

import ru.voboost.config.models.Language
import ru.voboost.config.models.Tab
import ru.voboost.config.models.Theme
import ru.voboost.ui.components.Panel
import ru.voboost.ui.components.SelectOption
import ru.voboost.ui.components.panel
import ru.voboost.ui.ConfigViewModel

fun createSettingsPanel(
    @Suppress("UNUSED_PARAMETER") configViewModel: ConfigViewModel
): Panel {
    return panel(Tab.settings, "tab_settings") {
        section("language_section", "language_label") {
            select(
                id = "language",
                label = "language_label",
                fieldPath = "settingsLanguage",
                options = Language.values().map { SelectOption("language_${it.name}", it.name) },
                defaultValue = Language.en.name
            )
        }

        section("appearance", "interface_appearance") {
            select(
                id = "theme",
                label = "interface_theme",
                fieldPath = "settingsTheme",
                options =
                    listOf(
                        SelectOption("theme_light", Theme.light.name),
                        SelectOption("theme_dark", Theme.dark.name)
                    ),
                defaultValue = Theme.light.name
            )
        }
    }
}
