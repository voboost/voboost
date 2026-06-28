package ru.voboost.ui.components

import android.content.Context
import kotlinx.coroutines.launch
import ru.voboost.components.i18n.Language
import ru.voboost.ui.ConfigState
import ru.voboost.ui.resolveLanguage
import ru.voboost.components.radio.Radio as LibraryRadio
import ru.voboost.components.radio.RadioButton as LibraryRadioButton

/**
 * Creates a Radio control bound to a ConfigState field.
 * Uses the library Radio builder with default margins (applied by Section.addRadio).
 *
 * @param context Android context
 * @param configState Application config state
 * @param fieldPath Config field path (e.g., "settingsTheme")
 * @param options List of (labelKey, value) pairs for radio buttons
 * @param defaultValue Default value when config field is null
 * @return Library Radio view ready to be added to a Section via addRadio()
 */
fun createConfigRadio(
    context: Context,
    configState: ConfigState,
    fieldPath: String,
    options: List<Pair<String, String>>,
    defaultValue: String,
    titleKey: String? = null,
): LibraryRadio {
    val localeManager = configState.localeManager
    val theme = configState.currentTheme
    val language = configState.currentLanguage

    val buttons =
        options.map { (labelKey, value) ->
            LibraryRadioButton(
                value,
                mapOf(
                    Language.EN.getCode() to localeManager.get(labelKey, Language.EN),
                    Language.RU.getCode() to localeManager.get(labelKey, Language.RU),
                ),
            )
        }

    val rawValue = configState.getFieldValue(fieldPath) ?: defaultValue
    val selectedValue =
        when (fieldPath) {
            "settingsLanguage" -> resolveLanguage(rawValue).getCode()
            else -> rawValue
        }

    val builder =
        LibraryRadio.create(context, theme, language, buttons, selectedValue)
            .onValueChange { newValue ->
                configState.scope.launch {
                    configState.updateField(fieldPath, newValue)
                }
            }

    if (titleKey != null) {
        builder.title(
            mapOf(
                Language.EN.getCode() to localeManager.get(titleKey, Language.EN),
                Language.RU.getCode() to localeManager.get(titleKey, Language.RU),
            ),
        )
    }

    val radio = builder.build()

    // Subscribe to config changes for reactive updates
    configState.scope.launch {
        configState.fieldFlow(fieldPath).collect { value ->
            if (value != null) {
                val resolvedValue =
                    when (fieldPath) {
                        "settingsLanguage" -> resolveLanguage(value).getCode()
                        else -> value
                    }
                radio.setSelectedValue(resolvedValue)
            }
        }
    }

    return radio
}
