package ru.voboost.feature

import ru.voboost.Logger
import ru.voboost.PlanProducer

/**
 * Phone number display format feature implementation.
 *
 * This feature enables full phone number display (instead of the truncated
 * Chinese-style format) by declaring an agent configuration in inject.json.
 * The daemon handles the actual injection into the system service process.
 *
 * ## Configuration
 * - **Agent ID**: `phone-num`
 * - **Target Process**: `com.qinggan.systemservice`
 * - **Config Key**: `interfacePhoneNumbers`
 *
 * ## Activation Conditions
 * The feature is enabled when configuration value is set to "always-full-number".
 *
 * ## Agent Configuration
 * - `enableFullNumber`: Set to true to always show full phone numbers
 *
 * @see FeatureFrida
 * @since 1.0.0
 */
class FeatureFridaPhoneNumbers : FeatureFrida() {
    companion object {
        private const val TAG = "FeatureFridaPhoneNumbers"
        private const val CONFIG_VALUE_ENABLE = "always-full-number"
    }

    /**
     * The agent ID for phone number display modifications.
     */
    override val agentId: String = "phone-num"

    /**
     * The target process for phone number display modification.
     * Targets the Qinggan system service.
     */
    override val targetProcess: String = "com.qinggan.systemservice"

    /**
     * Checks if the phone number display feature should be enabled.
     *
     * The feature is enabled when configuration value matches "always-full-number".
     *
     * @param context The feature context containing system dependencies
     * @return Result containing true if feature should be enabled, false otherwise
     */
    override fun shouldEnableImpl(context: FeatureContext): Result<Boolean> {
        return try {
            // Desktop doesn't support this feature
            if (context.androidContext == null) {
                Logger.debug(TAG, "Phone number feature not supported on desktop")
                return Result.success(false)
            }

            // Check configuration value
            val configManager = ru.voboost.config.ConfigManager(context.androidContext)
            val configValue = configManager.getFieldValue("interfacePhoneNumbers")
            if (configValue?.lowercase() != CONFIG_VALUE_ENABLE) {
                Logger.debug(
                    TAG,
                    "Phone number feature disabled in config: $configValue",
                )
                return Result.success(false)
            }

            Logger.info(TAG, "Phone number feature should be enabled")
            Result.success(true)
        } catch (e: Exception) {
            Logger.error(
                TAG,
                "Failed to check if phone number feature should be enabled: ${e.message}",
            )
            Result.failure(e)
        }
    }

    /**
     * Builds agent configuration for phone number display modification.
     *
     * Configuration includes:
     * - `enableFullNumber`: Set to true to always show full phone numbers
     *
     * @param context The feature context containing system dependencies
     * @return Map of configuration key-value pairs for the agent
     */
    override fun getAgentConfig(context: FeatureContext): Map<String, Any?> {
        return mapOf(
            "enableFullNumber" to true,
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
