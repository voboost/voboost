package ru.voboost.feature

import ru.voboost.Logger
import ru.voboost.PlanProducer

/**
 * Forced EV mode feature implementation for Voyah Free.
 *
 * This feature enables forced electric vehicle (EV) mode by declaring an agent
 * configuration in inject.json. The daemon handles the actual injection into
 * the system service process.
 *
 * **Important**: This feature is only available for Voyah Free model vehicles.
 * It will not be enabled on Voyah Dreamer or other models.
 *
 * ## Configuration
 * - **Agent ID**: `forced-ev`
 * - **Target Process**: `com.qinggan.systemservice`
 * - **Config Key**: `vehicleFuelMode`
 *
 * ## Activation Conditions
 * The feature is enabled when:
 * 1. Configuration value is set to "electric-forced"
 * 2. Vehicle model is "free" (Voyah Free only)
 *
 * ## Agent Configuration
 * - `mode`: Set to "forced" to enable forced EV mode
 *
 * @see FeatureFrida
 * @since 1.0.0
 */
class FeatureFridaForcedEV : FeatureFrida() {
    companion object {
        private const val TAG = "FeatureFridaForcedEV"
        private const val CONFIG_VALUE_FORCED = "electric-forced"
        private const val VEHICLE_MODEL_FREE = "free"
    }

    /**
     * The agent ID for forced EV mode modifications.
     */
    override val agentId: String = "forced-ev"

    /**
     * The target process for fuel mode modification.
     * Targets the Qinggan system service.
     */
    override val targetProcess: String = "com.qinggan.systemservice"

    /**
     * Checks if the forced EV mode feature should be enabled.
     *
     * The feature is enabled when:
     * 1. Configuration value matches "electric-forced"
     * 2. Vehicle model is "free" (Voyah Free only)
     *
     * @param context The feature context containing system dependencies
     * @return Result containing true if feature should be enabled, false otherwise
     */
    override fun shouldEnableImpl(context: FeatureContext): Result<Boolean> {
        return try {
            // Desktop doesn't support this feature
            if (context.androidContext == null) {
                Logger.debug(TAG, "Forced EV mode feature not supported on desktop")
                return Result.success(false)
            }

            // Check configuration value
            val configManager = ru.voboost.config.ConfigManager(context.androidContext)
            val configValue = configManager.getFieldValue("vehicleFuelMode")
            if (configValue?.lowercase() != CONFIG_VALUE_FORCED) {
                Logger.debug(
                    TAG,
                    "Forced EV mode feature disabled in config: $configValue",
                )
                return Result.success(false)
            }

            // Check vehicle model - only available for Voyah Free
            val vehicleModelResult = context.vehicleManager.getVehicleModel()
            vehicleModelResult.fold(
                onSuccess = { model ->
                    if (model.lowercase() != VEHICLE_MODEL_FREE) {
                        Logger.info(
                            TAG,
                            "Forced EV mode feature only available for Voyah Free, " +
                                "current model: $model",
                        )
                        return Result.success(false)
                    }
                },
                onFailure = { error ->
                    Logger.error(
                        TAG,
                        "Failed to get vehicle model: ${error.message}",
                    )
                    return Result.failure(error)
                },
            )

            Logger.info(TAG, "Forced EV mode feature should be enabled")
            Result.success(true)
        } catch (e: Exception) {
            Logger.error(
                TAG,
                "Failed to check if forced EV mode feature should be enabled: ${e.message}",
            )
            Result.failure(e)
        }
    }

    /**
     * Builds agent configuration for forced EV mode modification.
     *
     * Configuration includes:
     * - `mode`: Set to "forced" to enable forced electric-only operation
     *
     * @param context The feature context containing system dependencies
     * @return Map of configuration key-value pairs for the agent
     */
    override fun getAgentConfig(context: FeatureContext): Map<String, Any?> {
        return mapOf(
            "mode" to "forced",
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
