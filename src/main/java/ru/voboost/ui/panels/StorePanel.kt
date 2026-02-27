package ru.voboost.ui.panels

import ru.voboost.config.models.Tab
import ru.voboost.ui.ConfigState
import ru.voboost.ui.components.PanelDefinition
import ru.voboost.ui.components.panel

fun createStorePanel(
    @Suppress("UNUSED_PARAMETER") configState: ConfigState,
): PanelDefinition {
    return panel(Tab.store, "tab_store") {
        section("store_placeholder", "tab_store") {
            text(
                id = "store_placeholder",
                textKey = "store_placeholder_text",
            )
        }
    }
}
