package ru.voboost.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.voboost.components.i18n.Language
import java.util.Locale

/**
 * Simple localization manager that gets language from Config through ConfigState
 */
class LocaleManager(
    private val context: Context,
    private val configState: ConfigState,
) {
    private val scope = configState.scope
    private val _currentLanguage: MutableStateFlow<Language>
    val currentLanguage: StateFlow<Language>

    // Cache localized resources to avoid recreating contexts repeatedly
    private var cachedLanguage: Language? = null
    private var cachedResources: Resources? = null

    init {
        val initialLang =
            configState.languageFlow.value
                ?: resolveLanguage(configState.config.value?.settingsLanguage)
        _currentLanguage = MutableStateFlow(initialLang)
        currentLanguage = _currentLanguage.asStateFlow()

        configState.languageFlow
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { newLanguage ->
                if (newLanguage != _currentLanguage.value) {
                    _currentLanguage.value = newLanguage
                    cachedLanguage = null
                    cachedResources = null
                }
            }
            .launchIn(scope)
    }

    fun getString(
        key: String,
        vararg args: Any,
    ): String {
        val currentLang = _currentLanguage.value

        val resId = getResourceIdFromString(key)

        // If resource ID is 0, the resource was not found - return the key as fallback
        if (resId == 0) {
            return key
        }

        return try {
            // Get or create cached resources for current language
            val resources = getLocalizedResources(currentLang)
            resources.getString(resId, *args)
        } catch (e: Exception) {
            // Fallback to key if any error occurs
            key
        }
    }

    /**
     * Get localized string for a specific language
     * Used by Radio adapter to build label maps for all languages
     *
     * @param key String resource key
     * @param language Target language
     * @param args Optional formatting arguments
     * @return Localized string for the specified language
     */
    fun get(
        key: String,
        language: Language,
        vararg args: Any,
    ): String {
        val resId = getResourceIdFromString(key)

        // If resource ID is 0, the resource was not found - return the key as fallback
        if (resId == 0) {
            return key
        }

        return try {
            val resources = getLocalizedResources(language)
            resources.getString(resId, *args)
        } catch (e: Exception) {
            key
        }
    }

    /**
     * Get localized string for a specific language using library Language enum
     * Used by Radio adapter to build label maps for all languages
     *
     * @param key String resource key
     * @param language Target language (from voboost-components)
     * @param args Optional formatting arguments
     * @return Localized string for the specified language
     */

    private fun getLocalizedResources(language: Language): Resources {
        // Return cached resources if language hasn't changed
        if (cachedLanguage == language && cachedResources != null) {
            return cachedResources!!
        }

        // Create new localized resources
        val resources =
            when (language) {
                Language.RU -> {
                    val config = Configuration(context.resources.configuration)
                    config.setLocale(Locale("ru"))
                    context.createConfigurationContext(config).resources
                }
                Language.EN -> {
                    val config = Configuration(context.resources.configuration)
                    config.setLocale(Locale.ENGLISH)
                    context.createConfigurationContext(config).resources
                }
            }

        // Cache the resources
        cachedLanguage = language
        cachedResources = resources

        return resources
    }

    @StringRes
    private fun getResourceIdFromString(key: String): Int {
        return context.resources.getIdentifier(key, "string", context.packageName)
    }
}

/**
 * Get a localized string from the config state.
 *
 * This is a PLAIN function (not @Composable). It reads the current language
 * synchronously from ConfigState and returns the localized string.
 *
 * For reactive updates (when language changes), views should subscribe to
 * configState.languageFlow and call this function again in the collector.
 *
 * @param key String resource key (e.g., "vehicle_drive_mode")
 * @param configState The application config state
 * @param args Optional formatting arguments
 * @return Localized string, or the key itself if not found
 */
fun i18n(
    key: String,
    configState: ConfigState,
    vararg args: Any,
): String {
    if (!configState.isInitialized()) return key
    return configState.localeManager.getString(key, *args)
}
