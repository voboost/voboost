package ru.voboost.ui.panels

import ru.voboost.config.models.Tab
import ru.voboost.ui.components.Panel
import ru.voboost.ui.components.panel
import ru.voboost.ui.ConfigViewModel

fun createStorePanel(
    @Suppress("UNUSED_PARAMETER") configViewModel: ConfigViewModel
): Panel {
    return panel(Tab.store, "tab_store") {
        section("placeholder", "tab_store") {
            info(
                id = "store_placeholder",
                textKey = "store_placeholder_text"
            )
        }
    }
}
