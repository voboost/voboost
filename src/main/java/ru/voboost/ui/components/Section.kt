package ru.voboost.ui.components

import android.content.Context
import android.view.View
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.voboost.components.i18n.Language
import ru.voboost.ui.ConfigState
import ru.voboost.components.section.Section as LibrarySection

/**
 * Section definition - contains a list of UI control elements.
 * The visual rendering (title bar, background, rounded corners) is handled
 * by the library Section ViewGroup.
 */
data class Section(
    val id: String,
    val titleKey: String,
    val visibility: Flow<Boolean> = flowOf(true),
) {
    private val _elements = mutableListOf<AbstractControl>()
    val elements: List<AbstractControl> get() = _elements

    fun title(titleKey: String) {
        _elements.add(Text(id = "${id}_title", textKey = titleKey))
    }

    fun text(
        id: String,
        textKey: String,
        style: VoboostTextStyle = VoboostTextStyle.NORMAL,
        visibility: Flow<Boolean> = flowOf(true),
    ) {
        _elements.add(Text(id, textKey, style, visibility))
    }

    fun radio(
        id: String,
        fieldPath: String?,
        options: List<RadioButton>,
        defaultValue: String = "",
    ) {
        _elements.add(Radio(id, fieldPath, options, defaultValue))
    }

    fun radio(
        id: String,
        options: List<RadioButton>,
        defaultValue: String = "",
    ) {
        _elements.add(Radio(id, null, options, defaultValue))
    }
}

/**
 * Creates a native library Section ViewGroup from a Section data model.
 *
 * Builds the title map from string resources, creates the Section ViewGroup,
 * and adds native control Views as children.
 *
 * @param context Android context
 * @param section Section data model
 * @param configState Application config state
 * @return Library Section ViewGroup with control children
 */
fun createSectionView(
    context: Context,
    section: Section,
    configState: ConfigState,
): LibrarySection {
    // Build localized title map
    val titleMap = buildTitleMap(section.titleKey, configState)

    val sectionView =
        LibrarySection(context).apply {
            setTheme(configState.currentTheme)
            setLanguage(configState.currentLanguage)
            setTitle(titleMap)

            // Disable child clipping for Radio overshoot animation
            clipChildren = false
            clipToPadding = false
        }

    // Add control views as children
    section.elements.forEach { element ->
        val controlView: View =
            when (element) {
                is Radio -> createRadioView(context, element, configState)
                is Text -> createTextView(context, element, configState)
            }
        sectionView.addView(controlView)
    }

    return sectionView
}

/**
 * Builds a localized title map for the library Section.
 */
private fun buildTitleMap(
    titleKey: String,
    configState: ConfigState,
): Map<String, String> {
    if (!configState.isInitialized()) {
        return mapOf("en" to titleKey)
    }

    val localeManager = configState.localeManager
    return mapOf(
        "en" to localeManager.get(titleKey, Language.EN),
        "ru" to localeManager.get(titleKey, Language.RU),
    )
}

/**
 * Section builder function
 */
fun section(
    id: String,
    titleKey: String,
    visibility: Flow<Boolean> = flowOf(true),
    builder: Section.() -> Unit,
): Section {
    val section = Section(id, titleKey, visibility)
    section.builder()
    return section
}
