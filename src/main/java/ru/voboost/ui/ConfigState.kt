package ru.voboost.ui

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.voboost.components.i18n.Language
import ru.voboost.components.theme.Theme
import ru.voboost.config.ConfigManager
import ru.voboost.config.models.Config
import ru.voboost.config.models.Tab
import java.util.concurrent.ConcurrentHashMap

/**
 * Safely resolves a Theme from a string value from YAML config.
 *
 * The config stores theme values as strings like "free-dark", "free-light", "dreamer-dark", "dreamer-light".
 * This function converts those strings to the Theme enum using Theme.fromValue().
 *
 * Returns the resolved Theme, or the provided default if resolution fails.
 */
internal fun resolveTheme(
    value: String?,
    default: Theme = Theme.FREE_DARK,
): Theme {
    if (value == null) return default
    return try {
        Theme.fromValue(value)
    } catch (e: Exception) {
        default
    }
}

/**
 * Safely resolves a Language from a string value from YAML config.
 *
 * The config stores language values as strings like "en", "ru".
 * This function converts those strings to the Language enum using Language.fromCode().
 *
 * Returns the resolved Language, or the provided default if resolution fails.
 */
internal fun resolveLanguage(
    value: String?,
    default: Language = Language.EN,
): Language {
    if (value == null) return default
    return try {
        Language.fromCode(value)
    } catch (e: Exception) {
        default
    }
}

/**
 * Application configuration state manager.
 *
 * Plain Kotlin singleton (not a ViewModel) that manages config state
 * and provides reactive StateFlow values for UI components.
 *
 * Lifecycle: Created once, destroyed when shutdown() is called.
 */
class ConfigState private constructor() {
    /**
     * Shared CoroutineScope for all UI operations.
     * Views use this scope to subscribe to flows and launch updates.
     * Cancelled in shutdown().
     */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _config = MutableStateFlow<Config?>(null)
    val config: StateFlow<Config?> = _config.asStateFlow()

    // Version counter to force re-evaluation when config is mutated in place.
    // MutableStateFlow uses equals() for deduplication, and since Config is a
    // data class, two instances with the same field values are considered equal.
    // Incrementing this counter forces fieldFlow collectors to re-read values.
    private val _configVersion = MutableStateFlow(0L)

    // Dedicated flows for language and theme that bypass Config equals() dedup.
    // Updated directly in updateField() for immediate, reliable emission.
    private val _language = MutableStateFlow<Language?>(null)
    val languageFlow: StateFlow<Language?> = _language.asStateFlow()

    private val _theme = MutableStateFlow<Theme?>(null)
    val themeFlow: StateFlow<Theme?> = _theme.asStateFlow()

    private val _selectedTab = MutableStateFlow<Tab?>(null)
    val selectedTab: StateFlow<Tab?> = _selectedTab.asStateFlow()

    private var context: Context? = null
    private var configManager: ConfigManager? = null

    /** Current language value (non-null, with default) */
    val currentLanguage: Language
        get() = _language.value ?: Language.EN

    /** Current theme value (non-null, with default) */
    val currentTheme: Theme
        get() = _theme.value ?: Theme.FREE_DARK

    companion object {
        @Volatile
        private var instance: ConfigState? = null

        @Volatile
        private var applicationContext: Context? = null

        fun setApplicationContext(context: Context) {
            applicationContext = context.applicationContext
        }

        fun getInstance(context: Context? = null): ConfigState {
            // Store context if provided
            context?.let { setApplicationContext(it) }

            return instance ?: synchronized(this) {
                instance ?: ConfigState().also {
                    instance = it
                    // Initialize automatically with stored context
                    applicationContext?.let { ctx -> it.initialize(ctx) }
                }
            }
        }
    }

    lateinit var localeManager: LocaleManager
        private set

    private var isInitialized = false

    fun isInitialized(): Boolean = isInitialized

    fun initialize(context: Context) {
        if (isInitialized) {
            return
        }
        this.context = context

        // Create ConfigManager instance (uses default config.yaml)
        configManager = ConfigManager(context)

        // Set up listener for configuration changes
        val listener =
            object : ru.voboost.config.OnConfigChangeListener {
                override fun onConfigChanged(
                    newConfig: Config,
                    diff: Config,
                ) {
                    _config.value = newConfig
                    _language.value = resolveLanguage(newConfig.settingsLanguage)
                    _theme.value = resolveTheme(newConfig.settingsTheme)
                }

                override fun onConfigError(error: Exception) {
                    // Error handling without logging
                }
            }

        // Initialize configuration: copy default if needed, load config, and start watching
        configManager?.let { manager ->
            manager.copyDefaultConfigIfNeeded().onSuccess {
                manager.loadConfig().onSuccess { config ->
                    _config.value = config
                    _language.value = resolveLanguage(config.settingsLanguage)
                    _theme.value = resolveTheme(config.settingsTheme)
                    manager.startWatching(listener)
                }
            }
        }

        this.localeManager = LocaleManager(context, this)

        // Initialize selected tab from config
        scope.launch {
            config.filterNotNull().collect { config ->
                if (_selectedTab.value == null) {
                    _selectedTab.value = config.settingsActiveTab ?: Tab.settings
                }
            }
        }

        isInitialized = true
    }

    // Dynamic access to any field - uses ready API from voboost-config
    fun getFieldValue(fieldPath: String): String? {
        return try {
            configManager?.getFieldValue(fieldPath)
        } catch (e: Exception) {
            null
        }
    }

    // Cache for fieldFlow instances to prevent creating new StateFlows on
    // every recomposition. Each fieldPath gets exactly one shared StateFlow.
    private val fieldFlowCache = ConcurrentHashMap<String, StateFlow<String?>>()

    // Create StateFlow for any field
    fun fieldFlow(fieldPath: String): StateFlow<String?> {
        return fieldFlowCache.getOrPut(fieldPath) {
            combine(config, _configVersion) { cfg, ver ->
                getFieldValue(fieldPath)
            }.stateIn(scope, SharingStarted.Eagerly, getFieldValue(fieldPath))
        }
    }

    // Update any field - uses library method directly
    suspend fun updateField(
        fieldPath: String,
        value: Any,
    ) {
        // Pass raw string values directly to ConfigManager
        // The mapping between YAML strings and component enums happens on the READ path only
        // via resolveLanguage() and resolveTheme() functions
        val result = configManager?.setFieldValue(fieldPath, value)
        result?.onSuccess {
            // 1. Increment version to force cached fieldFlow re-evaluation.
            _configVersion.value++
            // 2. Update dedicated flows for direct consumers.
            when (fieldPath) {
                "settingsLanguage" -> {
                    val lang = configManager?.getFieldValue("settingsLanguage")
                    _language.value = resolveLanguage(lang)
                }
                "settingsTheme" -> {
                    val themeStr = configManager?.getFieldValue("settingsTheme")
                    _theme.value = resolveTheme(themeStr)
                }
            }
            // 3. Update _config for any remaining direct collectors.
            //    This may be suppressed by equals() but that's OK -- the dedicated
            //    flows and fieldFlow cache handle the critical paths.
            configManager?.loadConfig()?.onSuccess { newConfig ->
                _config.value = newConfig
            }
        }
    }

    // Set selected tab
    fun setSelectedTab(tab: Tab) {
        _selectedTab.value = tab
        scope.launch {
            updateField("settingsActiveTab", tab)
        }
    }

    /** Shutdown and release resources */
    fun shutdown() {
        configManager?.stopWatching()
        scope.cancel()
        isInitialized = false
        context = null
        configManager = null
        fieldFlowCache.clear()
        synchronized(Companion) {
            instance = null
            applicationContext = null
        }
    }
}
