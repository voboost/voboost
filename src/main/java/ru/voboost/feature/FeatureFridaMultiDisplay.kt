package ru.voboost.feature

import ru.voboost.Logger
import ru.voboost.PlanProducer

/**
 * Multi-display feature implementation.
 *
 * This feature allows all applications to be moved between the vehicle's
 * displays (central and passenger) by declaring an agent configuration in
 * inject.json. The daemon handles the actual injection into the system
 * service process.
 *
 * ## Configuration
 * - **Agent ID**: `app-multi-display`
 * - **Target Process**: `com.qinggan.systemservice`
 * - **Config Key**: `interfaceMultidisplay`
 *
 * ## Activation Conditions
 * The feature is enabled when configuration value is set to
 * "allow-for-all-applications".
 *
 * ## Agent Configuration
 * - `allowForAllApplications`: Set to true to allow all apps on secondary displays
 *
 * @see FeatureFrida
 * @since 1.0.0
 */
class FeatureFridaMultiDisplay : FeatureFrida() {
    companion object {
        private const val TAG = "FeatureFridaMultiDisplay"
        private const val CONFIG_VALUE_ENABLE = "allow-for-all-applications"
    }

    /**
     * The agent ID for multi-display modifications.
     */
    override val agentId: String = "app-multi-display"

    /**
     * The target process for multi-display modification.
     * Targets the Qinggan system service.
     */
    override val targetProcess: String = "com.qinggan.systemservice"

    /**
     * Checks if the multi-display feature should be enabled.
     *
     * The feature is enabled when configuration value matches
     * "allow-for-all-applications".
     *
     * @param context The feature context containing system dependencies
     * @return Result containing true if feature should be enabled, false otherwise
     */
    override fun shouldEnableImpl(context: FeatureContext): Result<Boolean> {
        return try {
            // Desktop doesn't support this feature
            if (context.androidContext == null) {
                Logger.debug(TAG, "Multi-display feature not supported on desktop")
                return Result.success(false)
            }

            // Check configuration value
            val configManager = ru.voboost.config.ConfigManager(context.androidContext)
            val configValue = configManager.getFieldValue("interfaceMultidisplay")
            if (configValue?.lowercase() != CONFIG_VALUE_ENABLE) {
                Logger.debug(
                    TAG,
                    "Multi-display feature disabled in config: $configValue",
                )
                return Result.success(false)
            }

            Logger.info(TAG, "Multi-display feature should be enabled")
            Result.success(true)
        } catch (e: Exception) {
            Logger.error(
                TAG,
                "Failed to check if multi-display feature should be enabled: ${e.message}",
            )
            Result.failure(e)
        }
    }

    /**
     * Builds agent configuration for multi-display modification.
     *
     * Configuration includes:
     * - `allowForAllApplications`: Set to true to allow all apps on
     *   secondary displays
     *
     * @param context The feature context containing system dependencies
     * @return Map of configuration key-value pairs for the agent
     */
    override fun getAgentConfig(context: FeatureContext): Map<String, Any?> {
        return mapOf(
            "allowForAllApplications" to true,
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
