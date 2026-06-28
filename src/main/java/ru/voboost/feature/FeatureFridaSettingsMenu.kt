package ru.voboost.feature

import ru.voboost.Logger
import ru.voboost.PlanProducer

/**
 * Settings menu integration feature implementation.
 *
 * This feature integrates Voboost settings into the vehicle's native settings menu
 * by declaring an agent configuration in inject.json. The daemon handles the actual
 * injection into the vehicle settings process.
 *
 * **Important**: This feature is always enabled when the startup mode allows it.
 * The shouldEnable method always returns true when startup mode is "interface",
 * as the feature is controlled by the startup mode configuration rather than a
 * specific feature flag.
 *
 * ## Configuration
 * - **Agent ID**: `settings-menu`
 * - **Target Process**: `com.qinggan.app.vehiclesetting`
 * - **Config Key**: Controlled by `settingsStartup` mode
 *
 * ## Activation Conditions
 * The feature is enabled when startup mode is "interface".
 *
 * ## Agent Configuration
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
        private const val ACTIVITY_CLASS = "ru.voboost.MainActivity"
    }

    /**
     * The agent ID for settings menu integration.
     */
    override val agentId: String = "settings-menu"

    /**
     * The target process for settings menu modification.
     * Targets the Qinggan vehicle settings application.
     */
    override val targetProcess: String = "com.qinggan.app.vehiclesetting"

    /**
     * Checks if the settings menu integration feature should be enabled.
     *
     * This feature is only enabled when the startup mode is set to "interface",
     * which means the UI should be integrated into the vehicle menu.
     *
     * @param context The feature context containing system dependencies
     * @return Result containing true if startup mode is "interface", false otherwise
     */
    override fun shouldEnableImpl(context: FeatureContext): Result<Boolean> {
        return try {
            val shouldEnable = context.config.settingsStartup?.name == "interface"

            if (shouldEnable) {
                Logger.info(
                    TAG,
                    "Settings menu integration feature should be enabled (startup mode: interface)",
                )
            } else {
                Logger.info(
                    TAG,
                    "Settings menu disabled (startup: ${context.config.settingsStartup})",
                )
            }

            Result.success(shouldEnable)
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
     * Builds agent configuration for settings menu integration.
     *
     * Configuration includes:
     * - `menuTitle`: Display title for the Voboost menu entry
     * - `activityClass`: Full class name of the settings activity to launch
     *
     * @param context The feature context containing system dependencies
     * @return Map of configuration key-value pairs for the agent
     */
    override fun getAgentConfig(context: FeatureContext): Map<String, Any?> {
        return mapOf(
            "menuTitle" to MENU_TITLE,
            "activityClass" to ACTIVITY_CLASS,
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
