package ru.voboost.ui.panels

import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.launch
import ru.voboost.components.button.Button
import ru.voboost.components.button.ButtonStyle
import ru.voboost.components.i18n.Language
import ru.voboost.components.theme.Theme
import ru.voboost.ui.ConfigState
import ru.voboost.ui.components.createConfigRadio
import ru.voboost.components.panel.Panel as LibraryPanel
import ru.voboost.components.section.Section as LibrarySection

// Verified package ids on the target device (see task spec).
private const val PACKAGE_YANDEX_NAVI = "ru.yandex.yandexnavi"
private const val PACKAGE_YANDEX_MUSIC = "ru.yandex.music"

/**
 * Creates the Applications panel.
 *
 * Layout: Panel -> Section (applications settings) with radio controls for:
 * - Launcher replacement (`applicationsLauncher`)
 * - DPI density (`applicationsDpi`)
 * - DNS provider (`applicationsDns`)
 *
 * Followed by Section (Yandex Navigator) + Section (Yandex Music), each holding
 * a single "Launch" button that starts the corresponding app via
 * [Context.getLaunchIntentForPackage]. If the app is not installed a localized
 * toast is shown.
 *
 * The button label ("Launch") and the section titles are localized, so the
 * panel subscribes to theme/language flows to re-apply them when the user
 * changes language or theme (the Screen reactive walker only recurses into
 * Section/Radio children, not Button children).
 */
fun createApplicationsPanel(
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

    // Applications settings section (launcher / dpi / dns)
    val settingsSection = createApplicationsSettingsSection(context, configState)
    panel.addView(settingsSection)

    val naviSection =
        createAppSection(
            context = context,
            configState = configState,
            theme = theme,
            language = language,
            titleKey = "section_yandex_navigator",
            packageName = PACKAGE_YANDEX_NAVI,
        )
    panel.addView(naviSection)

    val musicSection =
        createAppSection(
            context = context,
            configState = configState,
            theme = theme,
            language = language,
            titleKey = "section_yandex_music",
            packageName = PACKAGE_YANDEX_MUSIC,
        )
    panel.addView(musicSection)

    // The Panel strips the bottom margin from the last Section child and relies
    // on its own PADDING_BOTTOM (25px) for the trailing gap. For the Yandex
    // Music section to visually match the other sections (a 25px transparent
    // strip drawn inside the section bounds, not a full-bleed background), re-
    // enable its bottom margin after it has been added. The Panel only re-runs
    // its margin logic on add/remove, so this override persists. Clear the now
    // redundant Panel bottom padding so the trailing gap stays a single 25px
    // (matching the gap between the other sections) instead of doubling to 50.
    // No custom margin/padding overrides — the Section builder applies
    // BOTTOM_MARGIN (25px) by default, and the Panel has no extra padding.
    // All sections are created via the standard builder (LibrarySection),
    // so margins are consistent by default.

    return panel
}

/**
 * Creates the applications settings Section with radio controls for launcher,
 * DPI and DNS.
 */
private fun createApplicationsSettingsSection(
    context: Context,
    configState: ConfigState,
): LibrarySection {
    val localeManager = configState.localeManager
    val theme = configState.currentTheme
    val language = configState.currentLanguage

    val sectionTitle =
        mapOf(
            Language.EN.getCode() to localeManager.get("applications_settings", Language.EN),
            Language.RU.getCode() to localeManager.get("applications_settings", Language.RU),
        )

    val section =
        LibrarySection(context).apply {
            setTitle(sectionTitle)
            setTheme(theme)
            setLanguage(language)
            clipChildren = false
            clipToPadding = false
        }

    // Launcher: original / voboost
    section.addRadio(
        createConfigRadio(
            context = context,
            configState = configState,
            fieldPath = "applicationsLauncher",
            options =
                listOf(
                    "launcher_original" to "original",
                    "launcher_voboost" to "voboost",
                ),
            defaultValue = "original",
            titleKey = "applications_launcher",
        ),
    )

    // DPI: small / normal / large / huge
    section.addRadio(
        createConfigRadio(
            context = context,
            configState = configState,
            fieldPath = "applicationsDpi",
            options =
                listOf(
                    "dpi_small" to "small",
                    "dpi_normal" to "normal",
                    "dpi_large" to "large",
                    "dpi_huge" to "huge",
                ),
            defaultValue = "normal",
            titleKey = "applications_dpi",
        ),
    )

    // DNS: original / default / yandex / one
    section.addRadio(
        createConfigRadio(
            context = context,
            configState = configState,
            fieldPath = "applicationsDns",
            options =
                listOf(
                    "dns_original" to "original",
                    "dns_default" to "default",
                    "dns_yandex" to "yandex",
                    "dns_one" to "one",
                ),
            defaultValue = "original",
            titleKey = "applications_dns",
        ),
    )

    return section
}

/**
 * Creates a Section with a single "Launch" button for the given app package.
 *
 * The section title is localized via [titleKey]. The button launches the app
 * via [Context.getLaunchIntentForPackage]; if the app is not installed a
 * localized toast ("App not installed") is shown.
 */
private fun createAppSection(
    context: Context,
    configState: ConfigState,
    theme: Theme,
    language: Language,
    titleKey: String,
    packageName: String,
): LibrarySection {
    val localeManager = configState.localeManager

    val sectionTitle =
        mapOf(
            Language.EN.getCode() to localeManager.get(titleKey, Language.EN),
            Language.RU.getCode() to localeManager.get(titleKey, Language.RU),
        )

    val section =
        LibrarySection(context).apply {
            setTitle(sectionTitle)
            setTheme(theme)
            setLanguage(language)
            clipChildren = false
            clipToPadding = false
        }

    val launchLabel = localeManager.get("button_launch", language)
    // Buttons in the car are always SECONDARY (see AGENTS.md UI rules).
    val button =
        Button
            .create(context, theme, language, launchLabel, ButtonStyle.SECONDARY)
            .onClick { launchApp(context, configState, packageName) }
            .build()

    section.addButton(button)

    // Re-apply theme when it changes (the Screen reactive walker does not
    // recurse into Button children, so the panel handles it itself).
    configState.scope.launch {
        configState.themeFlow.collect { newTheme ->
            val currentTheme = newTheme ?: Theme.FREE_DARK
            button.setTheme(currentTheme)
        }
    }

    // Re-apply the localized button label when the language changes.
    configState.scope.launch {
        configState.languageFlow.collect { newLanguage ->
            val currentLanguage = newLanguage ?: Language.EN
            button.setLanguage(currentLanguage)
            button.setText(localeManager.get("button_launch", currentLanguage))
        }
    }

    return section
}

/**
 * Launches the app with the given package id.
 *
 * Uses [Context.getLaunchIntentForPackage] to obtain the launcher intent. If
 * the app is not installed (intent is null) or starting the activity throws,
 * a localized "App not installed" toast is shown.
 */
private fun launchApp(
    context: Context,
    configState: ConfigState,
    packageName: String,
) {
    val localeManager = configState.localeManager
    val language = configState.currentLanguage
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent == null) {
        Toast.makeText(
            context,
            localeManager.get("app_not_installed", language),
            Toast.LENGTH_SHORT,
        ).show()
        return
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(
            context,
            localeManager.get("app_not_installed", language),
            Toast.LENGTH_SHORT,
        ).show()
    }
}
