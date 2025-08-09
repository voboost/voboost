package ru.voboost.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.voboost.config.ConfigManager
import ru.voboost.config.models.Config
import ru.voboost.config.models.Language
import ru.voboost.config.models.Tab
import ru.voboost.config.models.Theme

class ConfigViewModel : ViewModel() {
    private val _config = MutableStateFlow<Config?>(null)
    val config: StateFlow<Config?> = _config.asStateFlow()

    private val _selectedTab = MutableStateFlow<Tab?>(null)
    val selectedTab: StateFlow<Tab?> = _selectedTab.asStateFlow()

    private var context: Context? = null
    private var configManager: ConfigManager? = null

    companion object {
        @Volatile
        private var instance: ConfigViewModel? = null

        @Volatile
        private var applicationContext: Context? = null

        fun setApplicationContext(context: Context) {
            applicationContext = context.applicationContext
        }

        fun getInstance(context: Context? = null): ConfigViewModel {
            // Store context if provided
            context?.let { setApplicationContext(it) }

            return instance ?: synchronized(this) {
                instance ?: ConfigViewModel().also {
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
        if (isInitialized) return
        this.context = context

        // Create ConfigManager instance (uses default config.yaml)
        configManager = ConfigManager(context)

        // Set up listener for configuration changes
        val listener =
            object : ru.voboost.config.OnConfigChangeListener {
                override fun onConfigChanged(
                    newConfig: Config,
                    diff: Config
                ) {
                    _config.value = newConfig
                }

                override fun onConfigError(error: Exception) {
                    // Handle error if needed
                }
            }

        // Initialize configuration: copy default if needed, load config, and start watching
        configManager?.let { manager ->
            manager.copyDefaultConfigIfNeeded().onSuccess {
                manager.loadConfig().onSuccess { config ->
                    _config.value = config
                    manager.startWatching(listener)
                }
            }
        }

        this.localeManager = LocaleManager(context, this)

        // Initialize selected tab from config
        viewModelScope.launch {
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

    // Create StateFlow for any field
    fun fieldFlow(fieldPath: String): StateFlow<String?> {
        return config
            .map { _ -> getFieldValue(fieldPath) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    // Update any field - uses library method directly
    suspend fun updateField(
        fieldPath: String,
        value: Any
    ) {
        Log.d("ConfigViewModel", "updateField called: $fieldPath = $value")

        // Convert string values to proper enum types for specific fields
        val actualValue =
            when (fieldPath) {
                "settingsLanguage" -> {
                    // Convert string to Language enum
                    if (value is String) {
                        try {
                            val enumValue = Language.valueOf(value)
                            Log.d("ConfigViewModel", "Converted string '$value' to Language enum: $enumValue")
                            enumValue
                        } catch (e: IllegalArgumentException) {
                            Log.w("ConfigViewModel", "Failed to convert '$value' to Language enum", e)
                            value // fallback to original value if conversion fails
                        }
                    } else {
                        value
                    }
                }
                "settingsTheme" -> {
                    // Convert string to Theme enum
                    if (value is String) {
                        try {
                            Theme.valueOf(value)
                        } catch (e: IllegalArgumentException) {
                            value // fallback to original value if conversion fails
                        }
                    } else {
                        value
                    }
                }
                else -> value // for other fields, use value as-is
            }

        Log.d("ConfigViewModel", "Setting field $fieldPath to actualValue: $actualValue")
        val result = configManager?.setFieldValue(fieldPath, actualValue)
        result?.onSuccess {
            Log.d("ConfigViewModel", "Field update successful, reloading config")
            // Force reload config to ensure it's updated immediately
            configManager?.getConfig()?.let { newConfig ->
                Log.d("ConfigViewModel", "New config loaded, language: ${newConfig.settingsLanguage}")
                _config.value = newConfig
            }
        }?.onFailure { error ->
            Log.e("ConfigViewModel", "Failed to update field $fieldPath", error)
        }
    }

    // Set selected tab
    fun setSelectedTab(tab: Tab) {
        _selectedTab.value = tab
        viewModelScope.launch {
            updateField("settingsActiveTab", tab)
        }
    }

    override fun onCleared() {
        super.onCleared()
        configManager?.stopWatching()
    }
}
