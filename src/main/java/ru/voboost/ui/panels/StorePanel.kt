package ru.voboost.ui.panels

import android.content.Context
import ru.voboost.ui.ConfigState
import ru.voboost.components.panel.Panel as LibraryPanel

fun createStorePanel(
    context: Context,
    configState: ConfigState,
): LibraryPanel {
    return LibraryPanel(context).apply {
        setTheme(configState.currentTheme)
    }
}
