package ru.voboost.feature

import ru.voboost.Logger
import ru.voboost.config.models.DriveMode

/**
 * Drive mode feature (native, adb-applied).
 *
 * This feature holds the vehicle drive mode preference (`vehicleDriveMode`).
 * It is a config-only feature in the daemon-contract architecture: the UI radio
 * buttons change the config value, and the actual `adb` commands to apply the
 * drive mode are executed out-of-band (not via Frida injection). No agent is
 * declared in inject.json for this feature.
 *
 * ## Configuration
 * - **Config Key**: `vehicleDriveMode`
 * - **Values**: `original`, `eco`, `comfort`, `sport`, `snow`, `outing`,
 *   `individual`
 *
 * ## Activation Conditions
 * The feature is considered "active" (has a non-default value to apply) when
 * the config value is anything other than `original`. The actual application
 * of the drive mode is handled separately.
 *
 * @see FeatureNative
 * @see DriveMode
 * @since 1.0.0
 */
class FeatureNativeDriveMode : FeatureNative() {
    companion object {
        private const val TAG = "FeatureNativeDriveMode"
    }

    /**
     * Checks if the drive mode feature has a non-default value to apply.
     *
     * @param context The feature context containing system dependencies
     * @return Result containing true if feature has a non-default value
     */
    override fun shouldEnable(context: FeatureContext): Result<Boolean> {
        return try {
            if (context.androidContext == null) {
                return Result.success(false)
            }
            val mode = context.config.vehicleDriveMode
            Result.success(mode != null && mode != DriveMode.original)
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to check drive mode feature: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Applies the drive mode. Config-only in Phase 1: the adb commands are
     * executed out-of-band, so this is a no-op placeholder.
     *
     * @param context The feature context containing system dependencies
     * @return Result indicating success
     */
    override fun enable(context: FeatureContext): Result<Unit> {
        Logger.debug(TAG, "Drive mode feature enable (config-only, no-op in Phase 1)")
        return Result.success(Unit)
    }

    /**
     * Reverts the drive mode. Config-only in Phase 1: no-op placeholder.
     *
     * @param context The feature context containing system dependencies
     * @return Result indicating success
     */
    override fun disable(context: FeatureContext): Result<Unit> {
        Logger.debug(TAG, "Drive mode feature disable (config-only, no-op in Phase 1)")
        return Result.success(Unit)
    }
}
