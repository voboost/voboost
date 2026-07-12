package ru.voboost.feature

import ru.voboost.Logger
import ru.voboost.PlanProducer

/**
 * Launcher replacement feature: launcher-allapps agent.
 *
 * Replaces the hardcoded app list in the Qinggan launcher with a dynamic
 * view that shows ALL user-installed applications. When enabled, the agent
 * injects every launchable third-party app into the launcher's "All Apps"
 * grid on every screen (driver and passenger).
 *
 * The companion agents [FeatureFridaNavbarLauncher] (`navbar-launcher`) and
 * [FeatureFridaVoboostToMenu] (`voboost-to-menu`) handle the navigation bar and
 * the menu entry respectively.
 *
 * All three agents are gated by the same config key `applicationsLauncher`
 * and are enabled together when the value is "voboost".
 *
 * ## Configuration
 * - **Agent ID**: `launcher-allapps`
 * - **Target Process**: `com.qinggan.app.launcher`
 * - **Config Key**: `applicationsLauncher`
 *
 * ## Activation Conditions
 * The feature is enabled when configuration value is set to "voboost".
 *
 * @see FeatureFridaNavbarLauncher
 * @see FeatureFridaVoboostToMenu
 * @see FeatureFrida
 * @since 1.0.0
 */
class FeatureFridaLauncher : FeatureFrida() {
    companion object {
        private const val TAG = "FeatureFridaLauncher"
        internal const val CONFIG_VALUE_VOBOOST = "voboost"
    }

    /**
     * The agent ID for the launcher allapps replacement.
     */
    override val agentId: String = "launcher-allapps"

    /**
     * The target process for launcher replacement.
     * Targets the Qinggan launcher application.
     */
    override val targetProcess: String = "com.qinggan.app.launcher"

    /**
     * Checks if the launcher replacement feature should be enabled.
     *
     * The feature is enabled when configuration value matches "voboost".
     *
     * @param context The feature context containing system dependencies
     * @return Result containing true if feature should be enabled, false otherwise
     */
    override fun shouldEnableImpl(context: FeatureContext): Result<Boolean> {
        return try {
            // Desktop doesn't support this feature
            if (context.androidContext == null) {
                Logger.debug(TAG, "Launcher feature not supported on desktop")
                return Result.success(false)
            }

            // Check configuration value
            val configManager = ru.voboost.config.ConfigManager(context.androidContext)
            val configValue = configManager.getFieldValue("applicationsLauncher")
            if (configValue?.lowercase() != CONFIG_VALUE_VOBOOST) {
                Logger.debug(
                    TAG,
                    "Launcher feature disabled in config: $configValue",
                )
                return Result.success(false)
            }

            Logger.info(TAG, "Launcher feature (launcher-allapps) should be enabled")
            Result.success(true)
        } catch (e: Exception) {
            Logger.error(
                TAG,
                "Failed to check if launcher feature should be enabled: ${e.message}",
            )
            Result.failure(e)
        }
    }

    /**
     * Builds agent configuration for the launcher allapps replacement.
     *
     * @param context The feature context containing system dependencies
     * @return Map of configuration key-value pairs for the agent
     */
    override fun getAgentConfig(context: FeatureContext): Map<String, Any?> {
        return mapOf(
            "enabled" to true,
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
