package ru.voboost.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import ru.voboost.ui.i18n
import ru.voboost.ui.ConfigViewModel

/**
 * Toggle/Switch element
 */
data class Toggle(
    override val id: String,
    val labelKey: String,
    val fieldPath: String,
    val defaultValue: Boolean = false,
    override val visibility: Flow<Boolean> = flowOf(true)
) : AbstractControl()

/**
 * Toggle renderer
 */
@Composable
fun toggleRenderer(
    element: Toggle,
    configViewModel: ConfigViewModel
) {
    val isVisible by element.visibility.collectAsState(initial = true)
    val valueFlow: StateFlow<String?> = configViewModel.fieldFlow(element.fieldPath)
    val isChecked by valueFlow.collectAsState()
    val checkedValue = isChecked?.toBoolean() ?: element.defaultValue
    val scope = rememberCoroutineScope()

    if (isVisible) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = i18n(element.labelKey))
            Switch(
                checked = checkedValue,
                onCheckedChange = { newValue ->
                    scope.launch {
                        configViewModel.updateField(element.fieldPath, newValue)
                    }
                }
            )
        }
    }
}
