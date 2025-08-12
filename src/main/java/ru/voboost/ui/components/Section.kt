package ru.voboost.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.voboost.ui.i18n
import ru.voboost.ui.ConfigViewModel

/**
 * Section element containing multiple controls
 */
data class Section(
    override val id: String,
    val titleKey: String,
    override val visibility: Flow<Boolean> = flowOf(true)
) : AbstractControl() {
    private val _elements = mutableListOf<AbstractControl>()
    val elements: List<AbstractControl> get() = _elements

    fun title(titleKey: String) {
        _elements.add(Text(id = "${id}_title", textKey = titleKey))
    }

    fun info(
        id: String,
        textKey: String,
        style: InfoStyle = InfoStyle.NORMAL,
        visibility: Flow<Boolean> = flowOf(true)
    ) {
        _elements.add(Info(id, textKey, style, visibility))
    }

    fun button(
        id: String,
        labelKey: String,
        onClick: suspend () -> Unit,
        style: ButtonStyle = ButtonStyle.PRIMARY,
        visibility: Flow<Boolean> = flowOf(true)
    ) {
        _elements.add(Button(id, labelKey, onClick, style, visibility))
    }

    fun slider(
        id: String,
        label: String,
        fieldPath: String,
        range: IntRange,
        defaultValue: Int = 0,
        step: Int = 1
    ) {
        _elements.add(Slider(id, label, fieldPath, range, defaultValue, step))
    }

    fun toggle(
        id: String,
        label: String,
        fieldPath: String,
        defaultValue: Boolean = false
    ) {
        _elements.add(Toggle(id, label, fieldPath, defaultValue))
    }

    fun select(
        id: String,
        label: String,
        fieldPath: String,
        options: List<SelectOption>,
        defaultValue: String = ""
    ) {
        _elements.add(Select(id, label, fieldPath, options, defaultValue))
    }

    fun radioGroup(
        id: String,
        fieldPath: String?,
        options: List<RadioGroupOption>,
        defaultValue: String = ""
    ) {
        _elements.add(RadioGroup(id, fieldPath, options, defaultValue))
    }

    fun radioGroup(
        id: String,
        options: List<RadioGroupOption>,
        defaultValue: String = ""
    ) {
        _elements.add(RadioGroup(id, null, options, defaultValue))
    }

    fun textInput(
        id: String,
        label: String,
        fieldPath: String,
        defaultValue: String = "",
        placeholder: String? = null,
        maxLength: Int? = null
    ) {
        _elements.add(TextInput(id, label, fieldPath, defaultValue, placeholder, maxLength))
    }
}

/**
 * Section renderer
 */
@Composable
fun sectionRenderer(
    section: Section,
    configViewModel: ConfigViewModel
) {
    val isVisible by section.visibility.collectAsState(initial = true)

    if (isVisible) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
        ) {
            Text(
                text = i18n(section.titleKey),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            section.elements.forEach { element ->
                when (element) {
                    is Toggle -> toggleRenderer(element, configViewModel)
                    is Slider -> sliderRenderer(element, configViewModel)
                    is Select -> selectRenderer(element, configViewModel)
                    is RadioGroup -> radioGroupRenderer(element, configViewModel)
                    is TextInput -> textInputRenderer(element, configViewModel)
                    is Info -> infoRenderer(element)
                    is Button -> buttonRenderer(element)
                    is Text -> textRenderer(element)
                    is Panel -> panelRenderer(element, configViewModel)
                    is Section -> sectionRenderer(element, configViewModel)
                }
            }
        }
    }
}

/**
 * Section builder function
 */
fun section(
    id: String,
    titleKey: String,
    visibility: Flow<Boolean> = flowOf(true),
    builder: Section.() -> Unit
): Section {
    val section = Section(id, titleKey, visibility)
    section.builder()
    return section
}

