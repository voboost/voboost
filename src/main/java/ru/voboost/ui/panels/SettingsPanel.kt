package ru.voboost.ui.panels

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.launch
import ru.voboost.BuildConfig
import ru.voboost.StatusReader
import ru.voboost.components.i18n.Language
import ru.voboost.components.section.Section
import ru.voboost.components.theme.Theme
import ru.voboost.ui.ConfigState
import ru.voboost.ui.components.createConfigRadio
import ru.voboost.components.panel.Panel as LibraryPanel
import ru.voboost.components.section.Section as LibrarySection

// Monospace text size (sp) for the diagnostic output.
private const val DIAGNOSTIC_TEXT_SP = 12f

// Fixed diagnostic content height (px). The daemon-status section is the
// last section in the Settings panel and should fill the screen viewport
// (720px design height) with a proper bottom margin. The height is fixed
// (not computed at runtime): screen height (720) minus the Section overhead
// (title bar 98 + content padding top 40 + content padding bottom 2 +
// bottom margin 25 = 165) = 555px. This makes the whole section (title +
// content + margins) exactly fill the 720px viewport. If the Section
// overhead constants in voboost-components change, update this value to
// match (720 - new overhead).
private const val DIAGNOSTIC_HEIGHT_PX = 555

/**
 * Creates the Settings panel.
 *
 * Layout: Panel -> Section (theme/car model/language) +
 * SectionSettingsDaemonStatus (collapsible diagnostic output).
 *
 * The daemon status section uses a title-checkbox as a toggle: when unchecked
 * the section collapses to its title bar; when checked it expands to a
 * full-height diagnostic text view that fills the viewport when scrolled to.
 */
fun createSettingsPanel(
    context: Context,
    configState: ConfigState,
): LibraryPanel {
    val panel =
        LibraryPanel(context).apply {
            setTheme(configState.currentTheme)
        }

    val localeManager = configState.localeManager
    val theme = configState.currentTheme
    val language = configState.currentLanguage

    // Settings header shows the actual app version (e.g. "Voboost 1.0.0-beta5").
    // Android string resources cannot reference BuildConfig, so the title is
    // built at runtime from BuildConfig.VERSION_NAME. The "settings_title"
    // resource is kept only as a fallback label (without the version).
    val versionTitle = "Voboost ${BuildConfig.VERSION_NAME}"
    val sectionTitle =
        mapOf(
            Language.EN.getCode() to versionTitle,
            Language.RU.getCode() to versionTitle,
        )

    val section =
        LibrarySection(context).apply {
            setTitle(sectionTitle)
            setTheme(theme)
            setLanguage(language)
            clipChildren = false
            clipToPadding = false
        }

    // Theme: dark / light
    section.addRadio(
        createConfigRadio(
            context = context,
            configState = configState,
            fieldPath = "settingsTheme",
            options =
                listOf(
                    "theme_option_dark" to "dark",
                    "theme_option_light" to "light",
                ),
            defaultValue = "dark",
        ),
    )

    // Car model: free / dreamer
    section.addRadio(
        createConfigRadio(
            context = context,
            configState = configState,
            fieldPath = "settingsCarModel",
            options =
                listOf(
                    "car_model_free" to "free",
                    "car_model_dreamer" to "dreamer",
                ),
            defaultValue = "free",
        ),
    )

    // Language: en / ru
    section.addRadio(
        createConfigRadio(
            context = context,
            configState = configState,
            fieldPath = "settingsLanguage",
            options =
                listOf(
                    "language_en" to "en",
                    "language_ru" to "ru",
                ),
            defaultValue = "en",
        ),
    )

    panel.addView(section)

    // Add collapsible daemon status (diagnostic) section.
    val daemonStatusSection =
        createSectionSettingsDaemonStatus(
            context = context,
            configState = configState,
            localeManager = localeManager,
            theme = theme,
            language = language,
        )
    panel.addView(daemonStatusSection)

    // The Panel strips the bottom margin from the last Section child. The
    // daemon-status section is the last section and its DIAGNOSTIC_HEIGHT_PX
    // (555) is computed assuming the Section's BOTTOM_MARGIN (25) is counted
    // in its measured height (title 98 + padding 40 + content 555 + padding 2
    // + margin 25 = 720, filling the viewport). Re-enable the bottom margin
    // after the section is added (the Panel only re-runs its margin logic on
    // add/remove, so this persists) and clear the now-redundant Panel bottom
    // padding so the section fills the viewport exactly instead of being 25px
    // short.
    // No custom margin/padding overrides — the Section builder applies
    // BOTTOM_MARGIN (25px) by default. All sections use the standard builder.

    return panel
}

/**
 * Creates the collapsible daemon status section.
 *
 * The section title acts as a checkbox toggle (radio button) placed above the
 * diagnostic content. When toggled off the section collapses to its title bar;
 * when toggled on it expands to a fixed-height diagnostic text view that fills
 * the available screen height when scrolled into view.
 *
 * @return the configured [Section] (already added to the panel by the caller)
 */
private fun createSectionSettingsDaemonStatus(
    context: Context,
    configState: ConfigState,
    localeManager: ru.voboost.ui.LocaleManager,
    theme: Theme,
    language: Language,
): Section {
    val title =
        mapOf(
            Language.EN.getCode() to localeManager.get("daemon_status_title", Language.EN),
            Language.RU.getCode() to localeManager.get("daemon_status_title", Language.RU),
        )

    val section =
        Section(context).apply {
            setTitle(title)
            setTheme(theme)
            setLanguage(language)
            clipChildren = false
            clipToPadding = false
        }

    // Fixed-height diagnostic text view. Height is chosen so the whole section
    // (title bar + content + margins) fills the panel viewport when scrolled
    // into view, satisfying the "full-height section" requirement. The height
    // is the fixed DIAGNOSTIC_HEIGHT_PX constant (see above), not computed at
    // runtime, so it is independent of the vertical interface shift and of the
    // device's reported display height.

    val diagnosticText =
        TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, DIAGNOSTIC_TEXT_SP)
            setTypeface(Typeface.MONOSPACE)
            setTextColor(getDiagnosticTextColor(theme))
            setBackgroundColor(getDiagnosticBackgroundColor(theme))
            setPadding(
                dpToPx(context, 12f),
                dpToPx(context, 12f),
                dpToPx(context, 12f),
                dpToPx(context, 12f),
            )
            gravity = Gravity.TOP or Gravity.START
            // Accessibility: the diagnostic region is a single focusable
            // block. TalkBack announces the localized title (contentDescription)
            // and then reads the monospace diagnostic text as one region.
            contentDescription =
                localeManager.get("daemon_status_title", language)
            isFocusable = true
            isFocusableInTouchMode = true
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES
            isVerticalScrollBarEnabled = true
            setHorizontallyScrolling(true)
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    DIAGNOSTIC_HEIGHT_PX,
                )
        }
    section.addView(diagnosticText)

    // Title-checkbox acts as the show/hide toggle for the debug section.
    // Starts expanded so the diagnostic output is visible by default.
    section.setTitleCheckbox(true) { isChecked ->
        diagnosticText.visibility =
            if (isChecked) TextView.VISIBLE else TextView.GONE
    }

    // Subscribe to daemon status updates and render them into the text view.
    configState.scope.launch {
        configState.statusState.status.collect { status ->
            diagnosticText.text = formatDaemonStatus(status)
        }
    }

    // Re-apply theme colors when the theme changes.
    configState.scope.launch {
        configState.themeFlow.collect { newTheme ->
            val currentTheme = newTheme ?: Theme.FREE_DARK
            diagnosticText.setTextColor(getDiagnosticTextColor(currentTheme))
            diagnosticText.setBackgroundColor(getDiagnosticBackgroundColor(currentTheme))
        }
    }

    // Re-render the accessibility label when the language changes. The
    // diagnostic body is English-only (see formatDaemonStatus), so only the
    // contentDescription needs to be re-localized.
    configState.scope.launch {
        configState.languageFlow.collect { newLanguage ->
            val currentLanguage = newLanguage ?: Language.EN
            diagnosticText.contentDescription =
                localeManager.get("daemon_status_title", currentLanguage)
        }
    }

    return section
}

/**
 * Formats daemon status into a multi-line diagnostic string.
 *
 * This is a developer-facing diagnostic surface (monospace, shown in the
 * Settings panel), so labels are intentionally English-only for stable
 * machine-readable-ish output. The visible title and the accessibility
 * contentDescription are localized separately by the caller.
 */
internal fun formatDaemonStatus(status: StatusReader.DaemonStatus?): String {
    if (status == null) {
        return "Daemon: unavailable"
    }

    val flags = mutableListOf<String>()
    if (status.killed) flags.add("KILLED")
    if (status.panic) flags.add("PANIC")

    val builder = StringBuilder()
    builder.append("Daemon: ${status.daemon}")
    builder.append("\nState: ${status.state.name}")
    if (flags.isNotEmpty()) {
        builder.append("\nFlags: ${flags.joinToString(", ")}")
    }
    builder.append("\nManifest: ${status.manifest}")

    if (status.injections.isEmpty()) {
        builder.append("\nInjections: none")
    } else {
        builder.append("\nInjections:")
        status.injections.forEach { injection ->
            builder.append("\n- ${injection.id}: ${injection.state.name} (${injection.process})")
        }
    }

    return builder.toString()
}

/**
 * Returns the diagnostic text color for the given theme.
 */
private fun getDiagnosticTextColor(theme: Theme): Int =
    when (theme) {
        Theme.FREE_DARK, Theme.DREAMER_DARK -> Color.parseColor("#E0E0E0")
        Theme.FREE_LIGHT, Theme.DREAMER_LIGHT -> Color.parseColor("#212121")
    }

/**
 * Returns the diagnostic background color for the given theme.
 */
private fun getDiagnosticBackgroundColor(theme: Theme): Int =
    when (theme) {
        Theme.FREE_DARK, Theme.DREAMER_DARK -> Color.parseColor("#1E1E1E")
        Theme.FREE_LIGHT, Theme.DREAMER_LIGHT -> Color.parseColor("#F5F5F5")
    }

/**
 * Converts dp to px.
 */
private fun dpToPx(
    context: Context,
    dp: Float,
): Int {
    return (dp * context.resources.displayMetrics.density).toInt()
}
