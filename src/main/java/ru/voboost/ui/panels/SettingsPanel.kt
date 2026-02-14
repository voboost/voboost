package ru.voboost.ui.panels

import ru.voboost.config.models.Tab
import ru.voboost.ui.ConfigViewModel
import ru.voboost.ui.components.Panel
import ru.voboost.ui.components.RadioButton
import ru.voboost.ui.components.panel
import ru.voboost.components.theme.Theme      // From library
import ru.voboost.components.i18n.Language    // From library

fun createSettingsPanel(
    @Suppress("UNUSED_PARAMETER") configViewModel: ConfigViewModel,
): Panel {
    return panel(Tab.settings, "tab_settings") {
        section("language_section", "language_label") {
            radio(
                id = "language",
                fieldPath = "settingsLanguage",
                options =
                    Language.values().map {
                        RadioButton(
                            "language_${it.name.lowercase()}",
                            it.getCode(),
                        )
                    },
                defaultValue = Language.EN.getCode(),
            )
        }

        section("appearance", "interface_appearance") {
            radio(
                id = "theme",
                fieldPath = "settingsTheme",
                options =
                    listOf(
                        RadioButton("theme_free_light", Theme.FREE_LIGHT.getValue()),
                        RadioButton("theme_free_dark", Theme.FREE_DARK.getValue()),
                        RadioButton("theme_dreamer_light", Theme.DREAMER_LIGHT.getValue()),
                        RadioButton("theme_dreamer_dark", Theme.DREAMER_DARK.getValue()),
                    ),
                defaultValue = Theme.FREE_LIGHT.getValue(),
            )
        }

        section("test-radio-group", "TEST") {
            radio(
                id = "test-radio",
                options =
                    listOf(
                        RadioButton("test-radio-close", "close"),
                        RadioButton("test-radio-normal", "normal"),
                        RadioButton("test-radio-sync-with-music", "sync-with-music"),
                        RadioButton("test-radio-sync-with-driving", "sync-with-driving"),
                    ),
                defaultValue = "normal",
            )
        }
    }
}
