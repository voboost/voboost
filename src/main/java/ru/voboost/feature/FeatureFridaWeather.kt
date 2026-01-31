package ru.voboost.feature

import org.json.JSONObject
import ru.voboost.Logger

/**
 * Weather widget feature implementation.
 *
 * This feature enables weather widget modifications to support non-Chinese cities
 * by injecting a Frida script into the launcher process. The script modifies the
 * weather widget behavior to allow city selection and weather data display for
 * international locations.
 *
 * ## Configuration
 * - **Feature ID**: `interface-widget-weather`
 * - **Target Process**: `com.qinggan.app.launcher`
 * - **Script Name**: `weather-widget-mod`
 * - **Config Key**: `interfaceWidgetWeather`
 *
 * ## Activation Conditions
 * The feature is enabled when:
 * 1. Configuration value is set to "enable-non-chineese-cities"
 * 2. The feature is supported by the vehicle
 *
 * ## Script Parameters
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
        private const val CONFIG_VALUE_ENABLE = "enable-non-chineese-cities"
    }

    /**
     * The target process for weather widget modification.
     * Targets the Qinggan launcher application.
     */
    override val targetProcess: String = "com.qinggan.app.launcher"

    /**
     * The Frida script name for weather widget modifications.
     */
    override val scriptName: String = "weather-widget-mod"

    /**
     * Checks if the weather widget feature should be enabled.
     *
     * The feature is enabled when:
     * 1. Configuration value matches "enable-non-chineese-cities"
     * 2. The feature is supported by the vehicle
     * 3. Target process is available (checked by parent class)
     *
     * @param context The feature context containing system dependencies
     * @return Result containing true if feature should be enabled, false otherwise
     */
    override fun shouldEnableImpl(context: FeatureContext): Result<Boolean> {
        return try {
            // Check configuration value
            val configValue = context.config.interfaceWidgetWeather
            if (configValue != CONFIG_VALUE_ENABLE) {
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
     * Builds script parameters for weather widget modification.
     *
     * Parameters include:
     * - `enableNonChineseCities`: Set to true to enable non-Chinese city support
     * - `language`: Current vehicle language setting for localization
     *
     * @return JSONObject containing script parameters
     */
    override fun getScriptParameters(): JSONObject? {
        return try {
            JSONObject().apply {
                put("enableNonChineseCities", true)
                // Language will be determined by the script based on system settings
                // but we can pass it explicitly if needed
                put("language", "en") // Default to English for international cities
            }
        } catch (e: Exception) {
            Logger.error(
                TAG,
                "Failed to build script parameters: ${e.message}",
            )
            null
        }
    }
}
