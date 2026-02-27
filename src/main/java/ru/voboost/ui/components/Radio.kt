package ru.voboost.ui.components

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import ru.voboost.components.i18n.Language
import ru.voboost.components.theme.Theme
import ru.voboost.ui.ConfigState
import ru.voboost.ui.resolveLanguage
import ru.voboost.ui.resolveTheme
import ru.voboost.components.radio.Radio as LibraryRadio
import ru.voboost.components.radio.RadioButton as LibraryRadioButton

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
 * Creates a native Radio view using the voboost-components library Radio.
 *
 * @param context Android context
 * @param element Radio data model
 * @param configState Application config state
 * @return Library Radio view
 */
fun createRadioView(
    context: Context,
    element: Radio,
    configState: ConfigState,
): LibraryRadio {
    val localeManager = configState.localeManager

    // Convert RadioButton to library RadioButton with resolved i18n labels
    val libraryButtons =
        element.options.map { option ->
            val labelMap: Map<String, String> =
                mapOf(
                    Language.EN.getCode() to localeManager.get(option.labelKey, Language.EN),
                    Language.RU.getCode() to localeManager.get(option.labelKey, Language.RU),
                )
            LibraryRadioButton(option.value, labelMap)
        }

    // Determine initial selected value
    val selectedValue =
        if (element.fieldPath != null) {
            val rawValue = configState.getFieldValue(element.fieldPath) ?: element.defaultValue
            when (element.fieldPath) {
                "settingsTheme" -> resolveTheme(rawValue).getValue()
                "settingsLanguage" -> resolveLanguage(rawValue).getCode()
                else -> rawValue
            }
        } else {
            element.defaultValue
        }

    // Get current language
    val currentLanguage: Language =
        if (configState.isInitialized()) {
            configState.languageFlow.value ?: Language.EN
        } else {
            Language.EN
        }

    // Get current theme
    val currentTheme: Theme =
        if (configState.isInitialized()) {
            configState.themeFlow.value ?: Theme.FREE_DARK
        } else {
            Theme.FREE_DARK
        }

    val radio =
        LibraryRadio(context).apply {
            setButtons(libraryButtons)
            setLanguage(currentLanguage)
            setTheme(currentTheme)
            setSelectedValue(selectedValue)

            setOnValueChangeListener(
                LibraryRadio.OnValueChangeListener { newValue ->
                    if (element.fieldPath != null) {
                        configState.scope.launch {
                            configState.updateField(element.fieldPath, newValue)
                        }
                    }
                },
            )
        }

    // Subscribe to config changes for reactive updates
    if (element.fieldPath != null) {
        configState.scope.launch {
            configState.fieldFlow(element.fieldPath).collect { value ->
                if (value != null) {
                    val resolvedValue =
                        when (element.fieldPath) {
                            "settingsTheme" -> resolveTheme(value).getValue()
                            "settingsLanguage" -> resolveLanguage(value).getCode()
                            else -> value
                        }
                    radio.setSelectedValue(resolvedValue)
                }
            }
        }
    }

    return radio
}
