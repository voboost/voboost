package ru.voboost.feature

import org.json.JSONObject
import ru.voboost.Logger

/**
 * Settings menu integration feature implementation.
 *
 * This feature integrates Voboost settings into the vehicle's native settings menu
 * by injecting a Frida script into the vehicle settings process. The script adds
 * a menu entry that launches the Voboost settings activity, providing seamless
 * access to Voboost configuration from the vehicle's interface.
 *
 * **Important**: This feature is always enabled when the startup mode allows it.
 * The shouldEnable method always returns true, as the feature is controlled by
 * the startup mode configuration rather than a specific feature flag.
 *
 * ## Configuration
 * - **Feature ID**: `settings-menu`
 * - **Target Process**: `com.qinggan.app.vehiclesetting`
 * - **Script Name**: `voboost-to-menu-mod`
 * - **Config Key**: Controlled by `settingsStartup` mode
 *
 * ## Activation Conditions
 * The feature is always enabled (returns true from shouldEnable).
 * Actual activation is controlled by the startup mode configuration.
 *
 * ## Script Parameters
 * - `menuTitle`: Display title for the menu entry ("Voboost")
 * - `activityClass`: Full class name of the settings activity to launch
 *
 * @see FeatureFrida
 * @since 1.0.0
 */
class FeatureFridaSettingsMenu : FeatureFrida() {
    companion object {
        private const val TAG = "FeatureFridaSettingsMenu"
        private const val MENU_TITLE = "Voboost"
        private const val ACTIVITY_CLASS = "ru.voboost.ui.SettingsActivity"
    }

    /**
     * The target process for settings menu modification.
     * Targets the Qinggan vehicle settings application.
     */
    override val targetProcess: String = "com.qinggan.app.vehiclesetting"

    /**
     * The Frida script name for settings menu integration.
     */
    override val scriptName: String = "voboost-to-menu-mod"

    /**
     * Checks if the settings menu integration feature should be enabled.
     *
     * This feature always returns true when the target process is available,
     * as it is controlled by the startup mode configuration rather than a
     * specific feature flag. The actual decision to enable or disable the
     * feature is made at a higher level based on the settingsStartup
     * configuration value.
     *
     * Target process availability is checked by the parent class.
     *
     * @param context The feature context containing system dependencies
     * @return Result containing true (always enabled when target is available)
     */
    override fun shouldEnableImpl(context: FeatureContext): Result<Boolean> {
        return try {
            // This feature is always enabled when the target process is available
            // The actual control is done by the startup mode configuration
            Logger.info(TAG, "Settings menu integration feature should be enabled")
            Result.success(true)
        } catch (e: Exception) {
            Logger.error(
                TAG,
                "Failed to check if settings menu integration feature should be enabled: " +
                    "${e.message}",
            )
            Result.failure(e)
        }
    }

    /**
     * Builds script parameters for settings menu integration.
     *
     * Parameters include:
     * - `menuTitle`: Display title for the Voboost menu entry
     * - `activityClass`: Full class name of the settings activity to launch
     *
     * @return JSONObject containing script parameters
     */
    override fun getScriptParameters(): JSONObject? {
        return try {
            JSONObject().apply {
                put("menuTitle", MENU_TITLE)
                put("activityClass", ACTIVITY_CLASS)
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
