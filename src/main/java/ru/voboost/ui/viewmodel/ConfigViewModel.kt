package ru.voboost.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.voboost.config.ConfigManager
import ru.voboost.config.OnConfigChangeListener
import ru.voboost.config.models.Config

data class ConfigUiState(
    val isLoading: Boolean = false,
    val config: Config? = null,
    val error: String? = null,
    val isConfigValid: Boolean = true
)

class ConfigViewModel : ViewModel(), OnConfigChangeListener {
    private val configManager = ConfigManager()
    private val configFileName = "config.yaml"

    private val _uiState = MutableStateFlow(ConfigUiState(isLoading = true))
    val uiState: StateFlow<ConfigUiState> = _uiState.asStateFlow()

    private var context: Context? = null

    fun initialize(context: Context) {
        this.context = context
        loadConfig()
        startWatchingConfig()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            context?.let { ctx ->
                val result = configManager.loadConfig(ctx, configFileName)

                result.fold(
                    onSuccess = { config ->
                        _uiState.value =
                            ConfigUiState(
                                isLoading = false,
                                config = config,
                                error = null,
                                isConfigValid = true
                            )
                    },
                    onFailure = { error ->
                        _uiState.value =
                            ConfigUiState(
                                isLoading = false,
                                config = null,
                                error = error.message,
                                isConfigValid = false
                            )
                    }
                )
            }
        }
    }

    private fun startWatchingConfig() {
        context?.let { ctx ->
            configManager.startWatching(ctx, configFileName, this)
        }
    }

    override fun onConfigChanged(
        newConfig: Config,
        diff: Config
    ) {
        viewModelScope.launch {
            val hasChanges = configManager.hasDiffAnyChanges(diff)

            if (hasChanges && configManager.isValidConfig(newConfig)) {
                _uiState.value =
                    _uiState.value.copy(
                        config = newConfig,
                        error = null,
                        isConfigValid = true
                    )
            }
        }
    }

    override fun onConfigError(error: Exception) {
        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    error = error.message,
                    isConfigValid = false
                )
        }
    }

    fun reloadConfig() {
        loadConfig()
    }

    fun getConfigValue(fieldPath: String): Any? {
        return _uiState.value.config?.let { config ->
            configManager.getFieldValue(config, fieldPath)
        }
    }

    override fun onCleared() {
        super.onCleared()
        configManager.stopWatching()
    }
}
