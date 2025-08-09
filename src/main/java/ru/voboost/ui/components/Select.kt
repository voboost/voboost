package ru.voboost.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
 * Select option data class
 */
data class SelectOption(
    val labelKey: String,
    val value: String
)

/**
 * Select/Dropdown element
 */
data class Select(
    override val id: String,
    val labelKey: String,
    val fieldPath: String,
    val options: List<SelectOption>,
    val defaultValue: String = "",
    override val visibility: Flow<Boolean> = flowOf(true)
) : AbstractControl()

/**
 * Select renderer
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun selectRenderer(
    element: Select,
    configViewModel: ConfigViewModel
) {
    val isVisible by element.visibility.collectAsState(initial = true)
    val valueFlow: StateFlow<String?> = configViewModel.fieldFlow(element.fieldPath)
    val selectedValueRaw by valueFlow.collectAsState()
    val selectedValue = selectedValueRaw ?: element.defaultValue
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }

    if (isVisible) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
        ) {
            Text(text = i18n(element.labelKey))
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value =
                        element.options.find { it.value == selectedValue }?.labelKey?.let { i18n(it) }
                            ?: selectedValue,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    element.options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(i18n(option.labelKey)) },
                            onClick = {
                                scope.launch {
                                    configViewModel.updateField(element.fieldPath, option.value)
                                }
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
