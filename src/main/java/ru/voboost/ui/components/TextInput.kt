package ru.voboost.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
 * Text input element
 */
data class TextInput(
    override val id: String,
    val labelKey: String,
    val fieldPath: String,
    val defaultValue: String = "",
    val placeholderKey: String? = null,
    val maxLength: Int? = null,
    override val visibility: Flow<Boolean> = flowOf(true)
) : AbstractControl()

/**
 * TextInput renderer
 */
@Composable
fun textInputRenderer(
    element: TextInput,
    configViewModel: ConfigViewModel
) {
    val isVisible by element.visibility.collectAsState(initial = true)
    val valueFlow: StateFlow<String?> = configViewModel.fieldFlow(element.fieldPath)
    val currentValueRaw by valueFlow.collectAsState()
    val currentValue = currentValueRaw ?: element.defaultValue
    val scope = rememberCoroutineScope()
    var textValue by remember { mutableStateOf(currentValue) }

    if (isVisible) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(text = i18n(element.labelKey))
            OutlinedTextField(
                value = textValue,
                onValueChange = { newValue ->
                    val finalValue =
                        if (element.maxLength != null && newValue.length > element.maxLength) {
                            newValue.take(element.maxLength)
                        } else {
                            newValue
                        }
                    textValue = finalValue
                    scope.launch {
                        configViewModel.updateField(element.fieldPath, finalValue)
                    }
                },
                placeholder = element.placeholderKey?.let { { Text(i18n(it)) } }
            )
        }
    }
}
