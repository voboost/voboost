package ru.voboost.ui.panels

import ru.voboost.config.models.Tab
import ru.voboost.ui.ConfigState
import ru.voboost.ui.components.PanelDefinition
import ru.voboost.ui.components.panel

fun createInterfacePanel(
    @Suppress("UNUSED_PARAMETER") configState: ConfigState,
): PanelDefinition {
    return panel(Tab.`interface`, "tab_interface") {
        section("interface_placeholder", "tab_interface") {
            text(
                id = "interface_placeholder",
                textKey = "interface_placeholder_text",
            )
        }
    }
}
