package ru.voboost.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import ru.voboost.ui.i18n
import ru.voboost.ui.ConfigViewModel

/**
 * Slider element
 */
data class Slider(
    override val id: String,
    val labelKey: String,
    val fieldPath: String,
    val range: IntRange,
    val defaultValue: Int = 0,
    val step: Int = 1,
    override val visibility: Flow<Boolean> = flowOf(true)
) : AbstractControl()

/**
 * Slider renderer
 */
@Composable
fun sliderRenderer(
    element: Slider,
    configViewModel: ConfigViewModel
) {
    val isVisible by element.visibility.collectAsState(initial = true)
    val valueFlow: StateFlow<String?> = configViewModel.fieldFlow(element.fieldPath)
    val currentValueRaw by valueFlow.collectAsState()
    val currentValue = currentValueRaw?.toIntOrNull() ?: element.defaultValue
    val scope = rememberCoroutineScope()
    var sliderValue by remember { mutableFloatStateOf(currentValue.toFloat()) }

    if (isVisible) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(text = "${i18n(element.labelKey)}: $currentValue")
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    scope.launch {
                        configViewModel.updateField(element.fieldPath, sliderValue.toInt())
                    }
                },
                valueRange = element.range.first.toFloat()..element.range.last.toFloat(),
                steps = if (element.step > 1) (element.range.last - element.range.first) / element.step - 1 else 0
            )
        }
    }
}
