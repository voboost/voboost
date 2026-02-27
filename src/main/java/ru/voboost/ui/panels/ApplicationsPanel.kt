package ru.voboost.ui.panels

import ru.voboost.config.models.Tab
import ru.voboost.ui.ConfigState
import ru.voboost.ui.components.PanelDefinition
import ru.voboost.ui.components.panel

fun createApplicationsPanel(
    @Suppress("UNUSED_PARAMETER") configState: ConfigState,
): PanelDefinition {
    return panel(Tab.applications, "tab_applications") {
        section("applications_placeholder", "tab_applications") {
            text(
                id = "applications_placeholder",
                textKey = "applications_placeholder_text",
            )
        }
    }
}
