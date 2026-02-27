package ru.voboost.ui.components

import android.content.Context
import android.view.View
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import ru.voboost.components.i18n.Language
import ru.voboost.components.text.TextRole
import ru.voboost.ui.ConfigState
import ru.voboost.ui.i18n
import ru.voboost.components.text.Text as LibraryText

/**
 * Text display styles
 */
enum class VoboostTextStyle {
    NORMAL,
    HEADING,
    CAPTION,
    LABEL,
}

/**
 * Text display element
 */
data class Text(
    override val id: String,
    val textKey: String,
    val style: VoboostTextStyle = VoboostTextStyle.NORMAL,
    override val visibility: Flow<Boolean> = flowOf(true),
) : AbstractControl()

/**
 * Creates a native Text view.
 *
 * @param context Android context
 * @param element Text data model
 * @param configState Application config state
 * @return TextView displaying the localized text
 */
fun createTextView(
    context: Context,
    element: Text,
    configState: ConfigState,
): View {
    return LibraryText(context).apply {
        role = when (element.style) {
            VoboostTextStyle.HEADING -> TextRole.TITLE
            else -> TextRole.CONTROL
        }

        val titleMap = buildLocalizedMap(element.textKey, configState)
        setText(titleMap)
        
        if (configState.isInitialized()) {
            setTheme(configState.currentTheme)
            setLanguage(configState.currentLanguage)
        }

        // Subscribe to language changes for reactive updates
        configState.scope.launch {
            configState.languageFlow.collect { lang ->
                setLanguage(lang)
            }
        }
        
        configState.scope.launch {
            configState.themeFlow.collect { thm ->
                setTheme(thm)
            }
        }
    }
}

private fun buildLocalizedMap(textKey: String, configState: ConfigState): Map<Language, String> {
    if (!configState.isInitialized()) {
        return mapOf(Language.EN to textKey)
    }
    val localeManager = configState.localeManager
    return mapOf(
        Language.EN to localeManager.get(textKey, Language.EN),
        Language.RU to localeManager.get(textKey, Language.RU),
    )
}
