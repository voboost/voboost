package ru.voboost.ui.components

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.voboost.config.models.Tab
import ru.voboost.ui.ConfigState
import ru.voboost.components.panel.Panel as LibraryPanel

/**
 * Panel definition - describes the content of a panel (title + sections).
 * This is a DATA MODEL, not a visual component.
 * The visual rendering is handled by the library Panel ViewGroup.
 */
data class PanelDefinition(
    val id: String,
    val titleKey: String,
    val sections: List<Section>,
    val visibility: Flow<Boolean> = flowOf(true),
)

/**
 * Panel builder DSL
 */
class PanelBuilder(private val id: String, private val titleKey: String) {
    private val sections = mutableListOf<Section>()
    private var visibilityCondition: Flow<Boolean> = flowOf(true)

    fun visibility(condition: Flow<Boolean>) {
        this.visibilityCondition = condition
    }

    fun section(
        id: String,
        titleKey: String,
        block: Section.() -> Unit,
    ) {
        val section = Section(id, titleKey)
        section.block()
        sections.add(section)
    }

    fun build(): PanelDefinition {
        return PanelDefinition(id, titleKey, sections.toList(), visibilityCondition)
    }
}

/**
 * Create a panel definition with DSL
 */
fun panel(
    id: String,
    title: String,
    block: PanelBuilder.() -> Unit,
): PanelDefinition {
    val builder = PanelBuilder(id, title)
    builder.block()
    return builder.build()
}

/**
 * Create a panel definition for a specific tab
 */
fun panel(
    tab: Tab,
    title: String,
    block: PanelBuilder.() -> Unit,
): PanelDefinition {
    val builder = PanelBuilder(tab.name, title)
    builder.block()
    return builder.build()
}

/**
 * Creates a native library Panel ViewGroup from a PanelDefinition.
 *
 * The Panel contains a ScrollView with a vertical LinearLayout holding
 * all Section ViewGroups. This allows scrolling when sections overflow.
 *
 * @param context Android context
 * @param panelDef Panel definition data model
 * @param configState Application config state
 * @return Library Panel ViewGroup with Section children
 */
fun buildPanelView(
    context: Context,
    panelDef: PanelDefinition,
    configState: ConfigState,
): LibraryPanel {
    val panel =
        LibraryPanel(context).apply {
            setTheme(configState.currentTheme)
        }

    // Add sections directly to panel. LibraryPanel handles internal scrolling automatically.
    panelDef.sections.forEach { section ->
        val sectionView = createSectionView(context, section, configState)
        panel.addView(sectionView)
    }

    return panel
}
