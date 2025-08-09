package ru.voboost.ui.panels

import ru.voboost.config.models.Tab
import ru.voboost.ui.components.Panel
import ru.voboost.ui.components.panel
import ru.voboost.ui.ConfigViewModel

fun createInterfacePanel(
    @Suppress("UNUSED_PARAMETER") configViewModel: ConfigViewModel
): Panel {
    return panel(Tab.`interface`, "tab_interface") {
        section("placeholder", "tab_interface") {
            info(
                id = "interface_placeholder",
                textKey = "interface_placeholder_text"
            )
        }
    }
}
