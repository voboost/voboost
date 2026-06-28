package ru.voboost.feature

import ru.voboost.Logger
import ru.voboost.PlanProducer

/**
 * Weather widget feature implementation.
 *
 * This feature enables weather widget modifications to support non-Chinese cities
 * by declaring an agent configuration in inject.json. The daemon handles the actual
 * injection into the launcher process.
 *
 * ## Configuration
 * - **Agent ID**: `weather-widget`
 * - **Target Process**: `com.qinggan.app.launcher`
 * - **Config Key**: `interfaceWidgetWeather`
 *
 * ## Activation Conditions
 * The feature is enabled when:
 * 1. Configuration value is set to "enable-non-chinese-cities"
 * 2. The feature is supported by the vehicle
 *
 * ## Agent Configuration
 * - `enableNonChineseCities`: Always set to true when enabled
 * - `language`: Current vehicle language setting
 *
 * @see FeatureFrida
 * @since 1.0.0
 */
class FeatureFridaWeather : FeatureFrida() {
    companion object {
        private const val TAG = "FeatureFridaWeather"
        private const val FEATURE_ID = "interface-widget-weather"
        private const val CONFIG_VALUE_ENABLE = "enable-non-chinese-cities"
    }

    /**
     * The agent ID for weather widget modifications.
     */
    override val agentId: String = "weather-widget"

    /**
     * The target process for weather widget modification.
     * Targets the Qinggan launcher application.
     */
    override val targetProcess: String = "com.qinggan.app.launcher"

    /**
     * Checks if the weather widget feature should be enabled.
     *
     * The feature is enabled when:
     * 1. Configuration value matches "enable-non-chinese-cities"
     * 2. The feature is supported by the vehicle
     *
     * @param context The feature context containing system dependencies
     * @return Result containing true if feature should be enabled, false otherwise
     */
    override fun shouldEnableImpl(context: FeatureContext): Result<Boolean> {
        return try {
            // Desktop doesn't support this feature
            if (context.androidContext == null) {
                Logger.debug(TAG, "Weather widget feature not supported on desktop")
                return Result.success(false)
            }

            // Check configuration value
            val configManager = ru.voboost.config.ConfigManager(context.androidContext)
            val configValue = configManager.getFieldValue("interfaceWidgetWeather")
            if (configValue?.lowercase() != CONFIG_VALUE_ENABLE) {
                Logger.debug(
                    TAG,
                    "Weather widget feature disabled in config: $configValue",
                )
                return Result.success(false)
            }

            // Check if feature is supported by vehicle
            val isSupportedResult = context.vehicleManager.isFeatureSupported(FEATURE_ID)
            isSupportedResult.fold(
                onSuccess = { isSupported ->
                    if (!isSupported) {
                        Logger.info(
                            TAG,
                            "Weather widget feature not supported by vehicle",
                        )
                        return Result.success(false)
                    }
                },
                onFailure = { error ->
                    Logger.error(
                        TAG,
                        "Failed to check feature support: ${error.message}",
                    )
                    return Result.failure(error)
                },
            )

            Logger.info(TAG, "Weather widget feature should be enabled")
            Result.success(true)
        } catch (e: Exception) {
            Logger.error(
                TAG,
                "Failed to check if weather widget feature should be enabled: ${e.message}",
            )
            Result.failure(e)
        }
    }

    /**
     * Builds agent configuration for weather widget modification.
     *
     * Configuration includes:
     * - `enableNonChineseCities`: Set to true to enable non-Chinese city support
     * - `language`: Current vehicle language setting for localization
     *
     * @param context The feature context containing system dependencies
     * @return Map of configuration key-value pairs for the agent
     */
    override fun getAgentConfig(context: FeatureContext): Map<String, Any?> {
        return mapOf(
            "enableNonChineseCities" to true,
            // Default to English for international cities
            "language" to "en",
        )
    }

    /**
     * Returns the plan entry for inject.json.
     *
     * @param context The feature context containing system dependencies
     * @return AgentEntry for inject.json, or null if feature not active
     */
    override fun planEntry(context: FeatureContext): PlanProducer.AgentEntry? {
        val shouldEnableResult = shouldEnableImpl(context)
        val shouldEnable = shouldEnableResult.getOrDefault(false)

        if (!shouldEnable) {
            return null
        }

        return PlanProducer.AgentEntry(
            id = agentId,
            enabled = true,
            config = getAgentConfig(context),
        )
    }
}
