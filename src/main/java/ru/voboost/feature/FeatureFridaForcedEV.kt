package ru.voboost.feature

import org.json.JSONObject
import ru.voboost.Logger
import ru.voboost.config.models.FuelMode

/**
 * Forced EV mode feature implementation for Voyah Free.
 *
 * This feature enables forced electric vehicle (EV) mode by injecting a Frida script
 * into the system service process. The script modifies the fuel mode behavior to
 * force the vehicle to operate in electric-only mode, preventing the combustion
 * engine from starting.
 *
 * **Important**: This feature is only available for Voyah Free model vehicles.
 * It will not be enabled on Voyah Dreamer or other models.
 *
 * ## Configuration
 * - **Feature ID**: `vehicle-fuel-mode`
 * - **Target Process**: `com.qinggan.systemservice`
 * - **Script Name**: `forced-ev-mod`
 * - **Config Key**: `vehicleFuelMode`
 *
 * ## Activation Conditions
 * The feature is enabled when:
 * 1. Configuration value is set to "electric-forced"
 * 2. Vehicle model is "free" (Voyah Free only)
 *
 * ## Script Parameters
 * - `mode`: Set to "forced" to enable forced EV mode
 *
 * @see FeatureFrida
 * @since 1.0.0
 */
class FeatureFridaForcedEV : FeatureFrida() {
    companion object {
        private const val TAG = "FeatureFridaForcedEV"
        private val CONFIG_VALUE_FORCED = FuelMode.electric_forced
        private const val VEHICLE_MODEL_FREE = "free"
    }

    /**
     * The target process for fuel mode modification.
     * Targets the Qinggan system service.
     */
    override val targetProcess: String = "com.qinggan.systemservice"

    /**
     * The Frida script name for forced EV mode modifications.
     */
    override val scriptName: String = "forced-ev-mod"

    /**
     * Checks if the forced EV mode feature should be enabled.
     *
     * The feature is enabled when:
     * 1. Configuration value matches "electric-forced"
     * 2. Vehicle model is "free" (Voyah Free only)
     * 3. Target process is available (checked by parent class)
     *
     * @param context The feature context containing system dependencies
     * @return Result containing true if feature should be enabled, false otherwise
     */
    override fun shouldEnableImpl(context: FeatureContext): Result<Boolean> {
        return try {
            // Check configuration value
            val configValue = context.config.vehicleFuelMode
            if (configValue != CONFIG_VALUE_FORCED) {
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
     * Builds script parameters for forced EV mode modification.
     *
     * Parameters include:
     * - `mode`: Set to "forced" to enable forced electric-only operation
     *
     * @return JSONObject containing script parameters
     */
    override fun getScriptParameters(): JSONObject? {
        return try {
            JSONObject().apply {
                put("mode", "forced")
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
