package ru.voboost.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.voboost.config.models.Tab
import ru.voboost.ui.i18n
import ru.voboost.ui.panels.createApplicationsPanel
import ru.voboost.ui.panels.createInterfacePanel
import ru.voboost.ui.panels.createSettingsPanel
import ru.voboost.ui.panels.createStorePanel
import ru.voboost.ui.panels.createVehiclePanel
import ru.voboost.ui.ConfigViewModel

/**
 * Panel represents a complete configuration screen
 */
data class Panel(
    override val id: String,
    val titleKey: String,
    val sections: List<Section>,
    override val visibility: Flow<Boolean> = flowOf(true)
) : AbstractControl()

/**
 * Panel renderer
 */
@Composable
fun panelRenderer(
    panel: Panel,
    configViewModel: ConfigViewModel
) {
    val isVisible by panel.visibility.collectAsState(initial = true)

    if (isVisible) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
        ) {
            Text(
                text = i18n(panel.titleKey),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            panel.sections.forEach { section ->
                sectionRenderer(section, configViewModel)
            }
        }
    }
}

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
        block: Section.() -> Unit
    ) {
        val section = Section(id, titleKey)
        section.block()
        sections.add(section)
    }

    fun build(): Panel {
        return Panel(id, titleKey, sections.toList(), visibilityCondition)
    }
}

/**
 * Create a panel with DSL
 */
fun panel(
    id: String,
    title: String,
    block: PanelBuilder.() -> Unit
): Panel {
    val builder = PanelBuilder(id, title)
    builder.block()
    return builder.build()
}

/**
 * Create a panel for a specific tab
 */
fun panel(
    tab: Tab,
    title: String,
    block: PanelBuilder.() -> Unit
): Panel {
    val builder = PanelBuilder(tab.name, title)
    builder.block()
    return builder.build()
}

@Composable
fun panel() {
    val configViewModel = ConfigViewModel.getInstance()
    val selectedTab by configViewModel.selectedTab.collectAsState()

    // Get current language to use as cache key
    val currentLanguage by configViewModel.localeManager.currentLanguage.collectAsState()

    // Cache panels to avoid recreation on each recomposition, but recreate when language changes
    val panels =
        remember(configViewModel, currentLanguage) {
            mapOf(
                Tab.store to createStorePanel(configViewModel),
                Tab.applications to createApplicationsPanel(configViewModel),
                Tab.`interface` to createInterfacePanel(configViewModel),
                Tab.vehicle to createVehiclePanel(configViewModel),
                Tab.settings to createSettingsPanel(configViewModel)
            )
        }

    val panel = panels[selectedTab]
    if (panel != null) {
        panelRenderer(panel, configViewModel)
    }
}
