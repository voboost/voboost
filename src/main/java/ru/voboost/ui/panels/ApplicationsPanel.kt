package ru.voboost.ui.panels

import ru.voboost.config.models.Tab
import ru.voboost.ui.components.Panel
import ru.voboost.ui.components.panel
import ru.voboost.ui.ConfigViewModel

fun createApplicationsPanel(
    @Suppress("UNUSED_PARAMETER") configViewModel: ConfigViewModel
): Panel {
    return panel(Tab.applications, "tab_applications") {
        section("placeholder", "tab_applications") {
            info(
                id = "applications_placeholder",
                textKey = "applications_placeholder_text"
            )
        }
    }
}
