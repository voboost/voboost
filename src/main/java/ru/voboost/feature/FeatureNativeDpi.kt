package ru.voboost.feature

import ru.voboost.Logger

/**
 * DPI density feature (native, adb-applied).
 *
 * This feature holds the DPI density preference (`applicationsDpi`). It is a
 * config-only feature in the daemon-contract architecture: the UI radio buttons
 * change the config value, and the actual `adb` commands to apply the density
 * are executed out-of-band (not via Frida injection). No agent is declared in
 * inject.json for this feature.
 *
 * ## Configuration
 * - **Config Key**: `applicationsDpi`
 * - **Values**: `small`, `normal`, `large`, `huge`
 *
 * ## Activation Conditions
 * The feature is considered "active" (has a non-default value to apply) when
 * the config value is anything other than the platform default. The actual
 * application of the density is handled separately.
 *
 * @see FeatureNative
 * @since 1.0.0
 */
class FeatureNativeDpi : FeatureNative() {
    companion object {
        private const val TAG = "FeatureNativeDpi"
        private const val CONFIG_KEY = "applicationsDpi"
    }

    /**
     * Checks if the DPI feature has a non-default value to apply.
     *
     * @param context The feature context containing system dependencies
     * @return Result containing true if feature has a non-default value
     */
    override fun shouldEnable(context: FeatureContext): Result<Boolean> {
        return try {
            if (context.androidContext == null) {
                return Result.success(false)
            }
            val configManager = ru.voboost.config.ConfigManager(context.androidContext)
            val value = configManager.getFieldValue(CONFIG_KEY)
            // "normal" is the platform default; any other value means an
            // override is requested.
            Result.success(value != null && value.lowercase() != "normal")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to check DPI feature: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Applies the DPI density. Config-only in Phase 1: the adb commands are
     * executed out-of-band, so this is a no-op placeholder.
     *
     * @param context The feature context containing system dependencies
     * @return Result indicating success
     */
    override fun enable(context: FeatureContext): Result<Unit> {
        Logger.debug(TAG, "DPI feature enable (config-only, no-op in Phase 1)")
        return Result.success(Unit)
    }

    /**
     * Reverts the DPI density. Config-only in Phase 1: no-op placeholder.
     *
     * @param context The feature context containing system dependencies
     * @return Result indicating success
     */
    override fun disable(context: FeatureContext): Result<Unit> {
        Logger.debug(TAG, "DPI feature disable (config-only, no-op in Phase 1)")
        return Result.success(Unit)
    }
}
