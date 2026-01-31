package ru.voboost.feature

import org.json.JSONObject
import ru.voboost.Logger

/**
 * Russian keyboard feature implementation.
 *
 * This feature enables Russian keyboard layout support by injecting a Frida script
 * into the Qinggan IME (Input Method Editor) process. The script modifies the keyboard
 * to add Russian language layout alongside existing layouts.
 *
 * ## Configuration
 * - **Feature ID**: `interface-keyboard`
 * - **Target Process**: `com.qinggan.app.qgime`
 * - **Script Name**: `keyboard-ru-mod`
 * - **Config Key**: `interfaceKeyboard`
 *
 * ## Activation Conditions
 * The feature is enabled when configuration value is set to "enable-russian".
 *
 * ## Script Parameters
 * - `layout`: Set to "russian" to enable Russian keyboard layout
 *
 * @see FeatureFrida
 * @since 1.0.0
 */
class FeatureFridaKeyboard : FeatureFrida() {
    companion object {
        private const val TAG = "FeatureFridaKeyboard"
        private const val CONFIG_VALUE_ENABLE = "enable-russian"
    }

    /**
     * The target process for keyboard modification.
     * Targets the Qinggan IME application.
     */
    override val targetProcess: String = "com.qinggan.app.qgime"

    /**
     * The Frida script name for keyboard modifications.
     */
    override val scriptName: String = "keyboard-ru-mod"

    /**
     * Checks if the Russian keyboard feature should be enabled.
     *
     * The feature is enabled when:
     * 1. Configuration value matches "enable-russian"
     * 2. Target process is available (checked by parent class)
     *
     * @param context The feature context containing system dependencies
     * @return Result containing true if feature should be enabled, false otherwise
     */
    override fun shouldEnableImpl(context: FeatureContext): Result<Boolean> {
        return try {
            // Check configuration value
            val configValue = context.config.interfaceKeyboard
            if (configValue != CONFIG_VALUE_ENABLE) {
                Logger.debug(
                    TAG,
                    "Russian keyboard feature disabled in config: $configValue",
                )
                return Result.success(false)
            }

            Logger.info(TAG, "Russian keyboard feature should be enabled")
            Result.success(true)
        } catch (e: Exception) {
            Logger.error(
                TAG,
                "Failed to check if Russian keyboard feature should be enabled: ${e.message}",
            )
            Result.failure(e)
        }
    }

    /**
     * Builds script parameters for Russian keyboard modification.
     *
     * Parameters include:
     * - `layout`: Set to "russian" to enable Russian keyboard layout
     *
     * @return JSONObject containing script parameters
     */
    override fun getScriptParameters(): JSONObject? {
        return try {
            JSONObject().apply {
                put("layout", "russian")
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
