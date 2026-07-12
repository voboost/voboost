package ru.voboost.feature

import ru.voboost.Logger
import ru.voboost.PlanProducer

/**
 * Launcher replacement feature: navbar-launcher agent.
 *
 * Companion agent to [FeatureFridaLauncher] that handles the navigation bar
 * portion of the voboost launcher replacement. Gated by the same config key
 * `applicationsLauncher` (enabled when value is "voboost").
 *
 * ## Configuration
 * - **Agent ID**: `navbar-launcher`
 * - **Target Process**: `com.qinggan.app.launcher`
 * - **Config Key**: `applicationsLauncher`
 *
 * ## Activation Conditions
 * The feature is enabled when configuration value is set to "voboost".
 *
 * @see FeatureFridaLauncher
 * @see FeatureFrida
 * @since 1.0.0
 */
class FeatureFridaNavbarLauncher : FeatureFrida() {
    companion object {
        private const val TAG = "FeatureFridaNavbarLauncher"
    }

    /**
     * The agent ID for the navigation bar launcher replacement.
     */
    override val agentId: String = "navbar-launcher"

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
                Logger.debug(TAG, "Navbar launcher feature not supported on desktop")
                return Result.success(false)
            }

            // Check configuration value (shared with the other launcher agents)
            val configManager = ru.voboost.config.ConfigManager(context.androidContext)
            val configValue = configManager.getFieldValue("applicationsLauncher")
            if (configValue?.lowercase() != FeatureFridaLauncher.CONFIG_VALUE_VOBOOST) {
                Logger.debug(
                    TAG,
                    "Navbar launcher feature disabled in config: $configValue",
                )
                return Result.success(false)
            }

            Logger.info(TAG, "Launcher feature (navbar-launcher) should be enabled")
            Result.success(true)
        } catch (e: Exception) {
            Logger.error(
                TAG,
                "Failed to check if navbar launcher feature should be enabled: ${e.message}",
            )
            Result.failure(e)
        }
    }

    /**
     * Builds agent configuration for the navigation bar launcher replacement.
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
