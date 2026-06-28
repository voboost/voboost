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
 * Safely resolves a Theme from separate carModel and theme parameters.
 *
 * The config stores theme as two separate fields:
 * - settingsCarModel: "free" or "dreamer"
 * - settingsTheme: "dark" or "light"
 *
 * This function combines them to create the Theme enum.
 *
 * Returns the resolved Theme, or the provided default if resolution fails.
 */
internal fun resolveTheme(
    carModel: String?,
    theme: String?,
    default: Theme = Theme.FREE_DARK,
): Theme {
    if (carModel == null || theme == null) return default
    return try {
        val carModelUpper = carModel.uppercase()
        val themeUpper = theme.uppercase()
        Theme.valueOf("${carModelUpper}_$themeUpper")
    } catch (e: Exception) {
        default
    }
}

/**
 * Safely resolves a Theme from a legacy string value from YAML config.
 *
 * The config stores theme values as strings like "free-dark", "free-light", "dreamer-dark", "dreamer-light".
 * This function converts those strings to the Theme enum using Theme.fromValue().
 *
 * Returns the resolved Theme, or the provided default if resolution fails.
 */
internal fun resolveThemeLegacy(
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

    private val configState = MutableStateFlow<Config?>(null)
    val config: StateFlow<Config?> = configState.asStateFlow()

    // Version counter to force re-evaluation when config is mutated in place.
    // MutableStateFlow uses equals() for deduplication, and since Config is a
    // data class, two instances with the same field values are considered equal.
    // Incrementing this counter forces fieldFlow collectors to re-read values.
    private val configVersion = MutableStateFlow(0L)

    // Dedicated flows for language and theme that bypass Config equals() dedup.
    // Updated directly in updateField() for immediate, reliable emission.
    private val languageState = MutableStateFlow<Language?>(null)
    val languageFlow: StateFlow<Language?> = languageState.asStateFlow()

    private val themeState = MutableStateFlow<Theme?>(null)
    val themeFlow: StateFlow<Theme?> = themeState.asStateFlow()

    private val selectedTabState = MutableStateFlow<Tab?>(null)
    val selectedTab: StateFlow<Tab?> = selectedTabState.asStateFlow()

    private var context: Context? = null
    private var configManager: ConfigManager? = null
    private var daemonStatusState: StatusState? = null

    /** Current language value (non-null, with default) */
    val currentLanguage: Language
        get() = languageState.value ?: Language.EN

    /** Current theme value (non-null, with default) */
    val currentTheme: Theme
        get() = themeState.value ?: Theme.FREE_DARK

    /** Status state for daemon status (non-null, with default) */
    val statusState: StatusState
        get() = daemonStatusState ?: throw IllegalStateException("ConfigState not initialized")

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

        // Create StatusState for daemon status
        daemonStatusState = StatusState(ru.voboost.PathsAndroid(context))
        daemonStatusState?.startPolling()

        // Set up listener for configuration changes
        val listener =
            object : ru.voboost.config.OnConfigChangeListener {
                override fun onConfigChanged(
                    newConfig: Config,
                    diff: Config,
                ) {
                    configState.value = newConfig
                    languageState.value = resolveLanguage(newConfig.settingsLanguage)
                    themeState.value =
                        resolveTheme(
                            newConfig.settingsCarModel?.name,
                            newConfig.settingsTheme,
                        )
                }

                override fun onConfigError(error: Exception) {
                    // Error handling without logging
                }
            }

        // Initialize configuration: copy default if needed, load config, and start watching
        configManager?.let { manager ->
            manager.copyDefaultConfigIfNeeded().onSuccess {
                manager.loadConfig().onSuccess { config ->
                    configState.value = config
                    languageState.value = resolveLanguage(config.settingsLanguage)
                    themeState.value =
                        resolveTheme(
                            config.settingsCarModel?.name,
                            config.settingsTheme,
                        )
                    manager.startWatching(listener)
                }
            }
        }

        this.localeManager = LocaleManager(context, this)

        // Initialize selected tab from config
        scope.launch {
            config.filterNotNull().collect { config ->
                if (selectedTabState.value == null) {
                    selectedTabState.value = config.settingsActiveTab ?: Tab.settings
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
            combine(config, configVersion) { cfg, ver ->
                getFieldValue(fieldPath)
            }.stateIn(scope, SharingStarted.Eagerly, getFieldValue(fieldPath))
        }
    }

    // Update any field - uses library method directly
    suspend fun updateField(
        fieldPath: String,
        value: Any,
    ) {
        // Convert enum values before passing to ConfigManager
        val convertedValue: Any =
            when (fieldPath) {
                "settingsCarModel" -> ru.voboost.config.models.CarModel.valueOf(value.toString())
                "settingsStartup" -> ru.voboost.config.models.StartupMode.valueOf(value.toString())
                "vehiclePedestrianWarning" ->
                    ru.voboost.config.models.PedestrianWarning.valueOf(
                        value.toString(),
                    )
                "settingsActiveTab" -> ru.voboost.config.models.Tab.valueOf(value.toString())
                "vehicleFuelMode" -> ru.voboost.config.models.FuelMode.valueOf(value.toString())
                "vehicleDriveMode" -> ru.voboost.config.models.DriveMode.valueOf(value.toString())
                else -> value
            }

        val result = configManager?.setFieldValue(fieldPath, convertedValue)
        result?.onSuccess {
            // 1. Increment version to force cached fieldFlow re-evaluation.
            configVersion.value++
            // 2. Update dedicated flows for direct consumers.
            when (fieldPath) {
                "settingsLanguage" -> {
                    val lang = configManager?.getFieldValue("settingsLanguage")
                    languageState.value = resolveLanguage(lang)
                }
                "settingsTheme", "settingsCarModel" -> {
                    val carModel = configManager?.getFieldValue("settingsCarModel")
                    val themeStr = configManager?.getFieldValue("settingsTheme")
                    themeState.value = resolveTheme(carModel, themeStr)
                }
            }
            // 3. Update configState for any remaining direct collectors.
            //    This may be suppressed by equals() but that's OK -- the dedicated
            //    flows and fieldFlow cache handle the critical paths.
            configManager?.loadConfig()?.onSuccess { newConfig ->
                configState.value = newConfig
            }
        }
    }

    // Set selected tab
    fun setSelectedTab(tab: Tab) {
        selectedTabState.value = tab
        scope.launch {
            updateField("settingsActiveTab", tab)
        }
    }

    /** Shutdown and release resources */
    fun shutdown() {
        configManager?.stopWatching()
        daemonStatusState?.shutdown()
        scope.cancel()
        isInitialized = false
        context = null
        configManager = null
        daemonStatusState = null
        fieldFlowCache.clear()
        synchronized(Companion) {
            instance = null
            applicationContext = null
        }
    }
}
