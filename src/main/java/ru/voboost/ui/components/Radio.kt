package ru.voboost.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import ru.voboost.components.radio.Radio as LibraryRadio
import ru.voboost.components.radio.RadioButton as LibraryRadioButton
import ru.voboost.components.i18n.Language
import ru.voboost.components.theme.Theme
import ru.voboost.ui.ConfigViewModel

/**
 * Radio button option data class
 * Matches library naming for consistency
 */
data class RadioButton(
    val labelKey: String,
    val value: String,
)

/**
 * Radio element for use in Section DSL
 */
data class Radio(
    override val id: String,
    val fieldPath: String?,
    val options: List<RadioButton>,
    val defaultValue: String = "",
    override val visibility: Flow<Boolean> = flowOf(true),
) : AbstractControl()

/**
 * Radio renderer using voboost-components Radio
 *
 * This adapter:
 * 1. Converts RadioButton to library RadioButton with resolved i18n labels
 * 2. Uses Theme enum directly from voboost-components
 * 3. Uses Language enum directly from voboost-components
 * 4. Maintains ConfigViewModel integration for config-bound Radios
 */
@Composable
fun radioRenderer(
    element: Radio,
    configViewModel: ConfigViewModel,
) {
    val isVisible by element.visibility.collectAsState(initial = true)
    val scope = rememberCoroutineScope()

    // Handle both config-bound and standalone Radios
    var standaloneValue by remember { mutableStateOf(element.defaultValue) }

    val selectedValue =
        if (element.fieldPath != null) {
            // Config-bound Radio
            val valueFlow: StateFlow<String?> = configViewModel.fieldFlow(element.fieldPath)
            val selectedValueRaw by valueFlow.collectAsState()
            selectedValueRaw ?: element.defaultValue
        } else {
            // Standalone Radio - use local state
            standaloneValue
        }

    // Get current language - now using library's Language enum
    val currentLanguage: Language =
        if (configViewModel.isInitialized()) {
            val langFlow = configViewModel.fieldFlow("settingsLanguage")
            val langValue by langFlow.collectAsState()
            langValue?.let {
                try {
                    Language.valueOf(it.uppercase())
                } catch (e: IllegalArgumentException) {
                    Language.EN
                }
            } ?: Language.EN
        } else {
            Language.EN
        }

    // Get current theme - now using library's Theme enum
    val currentTheme: Theme =
        if (configViewModel.isInitialized()) {
            val themeFlow = configViewModel.fieldFlow("settingsTheme")
            val themeValue by themeFlow.collectAsState()
            themeValue?.let {
                Theme.fromValue(it)
            } ?: Theme.FREE_LIGHT
        } else {
            Theme.FREE_LIGHT
        }

    if (isVisible) {
        // Convert RadioButton to library RadioButton with resolved i18n labels
        val libraryButtons = remember(element.options, currentLanguage) {
            val localeManager = configViewModel.localeManager
            element.options.map { option ->
                // Build label map for all supported languages
                val labelMap = mapOf(
                    Language.EN.getCode() to localeManager.get(option.labelKey, Language.EN),
                    Language.RU.getCode() to localeManager.get(option.labelKey, Language.RU)
                )
                LibraryRadioButton(option.value, labelMap)
            }
        }

        // Use library Radio component with enum-based API
        LibraryRadio(
            buttons = libraryButtons,
            lang = currentLanguage,    // Pass Language enum directly
            theme = currentTheme,      // Pass Theme enum directly
            value = selectedValue,
            onValueChange = { newValue ->
                if (element.fieldPath != null) {
                    // Config-bound Radio - update configuration
                    scope.launch {
                        configViewModel.updateField(element.fieldPath, newValue)
                    }
                } else {
                    // Standalone Radio - update local state
                    standaloneValue = newValue
                }
            }
        )
    }
}
