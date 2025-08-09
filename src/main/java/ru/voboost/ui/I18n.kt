package ru.voboost.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import ru.voboost.config.models.Language
import java.util.Locale

/**
 * Simple localization manager that gets language from Config through ConfigViewModel
 */
class LocaleManager(
    private val context: Context,
    private val configViewModel: ConfigViewModel
) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val _currentLanguage: MutableStateFlow<Language>
    val currentLanguage: StateFlow<Language>

    // Cache localized resources to avoid recreating contexts repeatedly
    private var cachedLanguage: Language? = null
    private var cachedResources: Resources? = null

    init {
        val initialConfig = configViewModel.config.value
        val initialLang = initialConfig?.settingsLanguage ?: Language.en
        _currentLanguage = MutableStateFlow(initialLang)
        currentLanguage = _currentLanguage.asStateFlow()

        configViewModel.config
            .map { it?.settingsLanguage }
            .distinctUntilChanged()
            .onEach { newLanguage ->
                Log.d("LocaleManager", "Config language changed to: $newLanguage")
                newLanguage?.let {
                    if (it != _currentLanguage.value) {
                        Log.d("LocaleManager", "Updating current language from ${_currentLanguage.value} to $it")
                        _currentLanguage.value = it
                        // Clear cache when language changes
                        cachedLanguage = null
                        cachedResources = null
                    } else {
                        Log.d("LocaleManager", "Language unchanged: $it")
                    }
                }
            }
            .launchIn(scope)
    }

    fun getString(
        key: String,
        vararg args: Any
    ): String {
        val currentLang = _currentLanguage.value
        Log.d("LocaleManager", "getString called for key: $key, current language: $currentLang")

        val resId = getResourceIdFromString(key)

        // If resource ID is 0, the resource was not found - return the key as fallback
        if (resId == 0) {
            Log.d("LocaleManager", "Resource not found for key: $key, returning key as fallback")
            return key
        }

        return try {
            // Get or create cached resources for current language
            val resources = getLocalizedResources(currentLang)
            val result = resources.getString(resId, *args)
            Log.d("LocaleManager", "getString result for key: $key, language: $currentLang, result: $result")
            result
        } catch (e: Exception) {
            Log.e("LocaleManager", "Error getting string for key: $key, language: $currentLang", e)
            // Fallback to key if any error occurs
            key
        }
    }

    private fun getLocalizedResources(language: Language): Resources {
        // Return cached resources if language hasn't changed
        if (cachedLanguage == language && cachedResources != null) {
            Log.d("LocaleManager", "Using cached resources for language: $language")
            return cachedResources!!
        }

        Log.d("LocaleManager", "Creating new localized resources for language: $language")

        // Create new localized resources
        val resources =
            when (language) {
                Language.ru -> {
                    val config = Configuration(context.resources.configuration)
                    config.setLocale(Locale("ru"))
                    context.createConfigurationContext(config).resources
                }
                Language.en -> {
                    val config = Configuration(context.resources.configuration)
                    config.setLocale(Locale.ENGLISH)
                    context.createConfigurationContext(config).resources
                }
            }

        // Cache the resources
        cachedLanguage = language
        cachedResources = resources
        Log.d("LocaleManager", "Cached new resources for language: $language")

        return resources
    }

    @StringRes
    private fun getResourceIdFromString(key: String): Int {
        return context.resources.getIdentifier(key, "string", context.packageName)
    }
}

/**
 * Main function for getting localized strings from Config
 * @param key String resource key (using underscore format, e.g., "vehicle_drive_mode")
 * @param args Optional formatting arguments
 * @return Localized string that updates when language changes in Config
 */
@Composable
fun i18n(
    key: String,
    vararg args: Any
): String {
    // Auto-initialize by getting ConfigViewModel instance
    val configViewModel = ConfigViewModel.getInstance()

    // Check if ConfigViewModel is initialized before accessing localeManager
    if (!configViewModel.isInitialized()) {
        // Return fallback string if not initialized yet
        return key // Return the key itself as fallback
    }

    val localeManager = configViewModel.localeManager

    // Subscribe to language changes from Config to trigger recomposition
    // This is critical - the currentLanguage state change will trigger recomposition
    val currentLanguage by localeManager.currentLanguage.collectAsState()

    // Use key directly for Android resource lookup
    // The localeManager.getString() will use the current language from the state
    return localeManager.getString(key, *args)
}
