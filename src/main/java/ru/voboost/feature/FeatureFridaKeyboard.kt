package ru.voboost.feature

import ru.voboost.Logger
import ru.voboost.PlanProducer

/**
 * Russian keyboard feature implementation.
 *
 * This feature enables Russian keyboard layout support by declaring an agent
 * configuration in inject.json. The daemon handles the actual injection into
 * the Qinggan IME (Input Method Editor) process.
 *
 * ## Configuration
 * - **Agent ID**: `keyboard-ru`
 * - **Target Process**: `com.qinggan.app.qgime`
 * - **Config Key**: `interfaceKeyboard`
 *
 * ## Activation Conditions
 * The feature is enabled when configuration value is set to "enable-russian".
 *
 * ## Agent Configuration
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
     * The agent ID for keyboard modifications.
     */
    override val agentId: String = "keyboard-ru"

    /**
     * The target process for keyboard modification.
     * Targets the Qinggan IME application.
     */
    override val targetProcess: String = "com.qinggan.app.qgime"

    /**
     * Checks if the Russian keyboard feature should be enabled.
     *
     * The feature is enabled when:
     * 1. Configuration value matches "enable-russian"
     *
     * @param context The feature context containing system dependencies
     * @return Result containing true if feature should be enabled, false otherwise
     */
    override fun shouldEnableImpl(context: FeatureContext): Result<Boolean> {
        return try {
            // Desktop doesn't support this feature
            if (context.androidContext == null) {
                Logger.debug(TAG, "Russian keyboard feature not supported on desktop")
                return Result.success(false)
            }

            // Check configuration value
            val configManager = ru.voboost.config.ConfigManager(context.androidContext)
            val configValue = configManager.getFieldValue("interfaceKeyboard")
            if (configValue?.lowercase() != CONFIG_VALUE_ENABLE) {
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
     * Builds agent configuration for Russian keyboard modification.
     *
     * Configuration includes:
     * - `layout`: Set to "russian" to enable Russian keyboard layout
     *
     * @param context The feature context containing system dependencies
     * @return Map of configuration key-value pairs for the agent
     */
    override fun getAgentConfig(context: FeatureContext): Map<String, Any?> {
        return mapOf(
            "layout" to "russian",
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
