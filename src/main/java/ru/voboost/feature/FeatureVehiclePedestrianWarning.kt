package ru.voboost.feature

import ru.voboost.Logger
import ru.voboost.PlanProducer
import ru.voboost.config.models.PedestrianWarning

/**
 * Pedestrian warning sound feature implementation.
 *
 * This feature controls the low-speed pedestrian warning sound by declaring an
 * agent configuration in inject.json. The daemon handles the actual injection into
 * the vehicle settings process.
 *
 * ## Configuration
 * - **Agent ID**: `pedestrian-warning`
 * - **Target Process**: `com.qinggan.app.vehiclesetting`
 * - **Config Key**: `vehiclePedestrianWarning`
 *
 * ## Activation Conditions
 * The feature is enabled when configuration value is set to "off".
 *
 * ## Agent Configuration
 * - `enabled`: Set to false to turn off pedestrian warning sound
 *
 * @see FeatureFrida
 * @since 1.0.0
 */
class FeatureVehiclePedestrianWarning : FeatureFrida() {
    companion object {
        private const val TAG = "FeatureVehiclePedestrianWarning"
        private const val CONFIG_VALUE_OFF = "off"
    }

    /**
     * The agent ID for pedestrian warning modifications.
     */
    override val agentId: String = "pedestrian-warning"

    /**
     * The target process for pedestrian warning modification.
     * Targets the Qinggan vehicle settings application.
     */
    override val targetProcess: String = "com.qinggan.app.vehiclesetting"

    /**
     * Checks if the pedestrian warning feature should be enabled.
     *
     * The feature is enabled when:
     * 1. Configuration value matches "off"
     *
     * @param context The feature context containing system dependencies
     * @return Result containing true if feature should be enabled, false otherwise
     */
    override fun shouldEnableImpl(context: FeatureContext): Result<Boolean> {
        return try {
            // Check configuration value
            val configValue = context.config.vehiclePedestrianWarning
            if (configValue != PedestrianWarning.off) {
                Logger.debug(
                    TAG,
                    "Pedestrian warning feature disabled in config: $configValue",
                )
                return Result.success(false)
            }

            Logger.info(TAG, "Pedestrian warning feature should be enabled")
            Result.success(true)
        } catch (e: Exception) {
            Logger.error(
                TAG,
                "Failed to check if pedestrian warning feature should be enabled: ${e.message}",
            )
            Result.failure(e)
        }
    }

    /**
     * Builds agent configuration for pedestrian warning modification.
     *
     * Configuration includes:
     * - `enabled`: Set to false to turn off pedestrian warning sound
     *
     * @param context The feature context containing system dependencies
     * @return Map of configuration key-value pairs for the agent
     */
    override fun getAgentConfig(context: FeatureContext): Map<String, Any?> {
        return mapOf(
            "enabled" to false,
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
