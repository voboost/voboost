package ru.voboost.ui.panels

import ru.voboost.config.models.Language
import ru.voboost.config.models.Tab
import ru.voboost.config.models.Theme
import ru.voboost.ui.components.Panel
import ru.voboost.ui.components.RadioGroupOption
import ru.voboost.ui.components.panel
import ru.voboost.ui.ConfigViewModel

fun createSettingsPanel(
    @Suppress("UNUSED_PARAMETER") configViewModel: ConfigViewModel
): Panel {
    return panel(Tab.settings, "tab_settings") {
        section("language_section", "language_label") {
            radioGroup(
                id = "language",
                fieldPath = "settingsLanguage",
                options = Language.values().map { RadioGroupOption("language_${it.name}", it.name) },
                defaultValue = Language.en.name
            )
        }

        section("appearance", "interface_appearance") {
            radioGroup(
                id = "theme",
                fieldPath = "settingsTheme",
                options =
                    listOf(
                        RadioGroupOption("theme_light", Theme.light.name),
                        RadioGroupOption("theme_dark", Theme.dark.name)
                    ),
                defaultValue = Theme.light.name
            )
        }

        section("test-radio-group", "TEST") {
            radioGroup(
                id = "test-radio-group",
                options =
                    listOf(
                        RadioGroupOption("test-radio-group-close", "close"),
                        RadioGroupOption("test-radio-group-normal", "normal"),
                        RadioGroupOption("test-radio-group-sync-with-music", "sync-with-music"),
                        RadioGroupOption("test-radio-group-sync-with-driving", "sync-with-driving")
                    ),
                defaultValue = "normal"
            )
        }
    }
}
