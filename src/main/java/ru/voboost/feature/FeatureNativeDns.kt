package ru.voboost.feature

import ru.voboost.Logger

/**
 * DNS provider feature (native, adb-applied).
 *
 * This feature holds the DNS provider preference (`applicationsDns`). It is a
 * config-only feature in the daemon-contract architecture: the UI radio buttons
 * change the config value, and the actual `adb` commands to apply the DNS are
 * executed out-of-band (not via Frida injection). No agent is declared in
 * inject.json for this feature.
 *
 * ## Configuration
 * - **Config Key**: `applicationsDns`
 * - **Values**: `original`, `default`, `yandex`, `one`
 *
 * ## Activation Conditions
 * The feature is considered "active" (has a non-default value to apply) when
 * the config value is anything other than "original". The actual application
 * of the DNS is handled separately.
 *
 * @see FeatureNative
 * @since 1.0.0
 */
class FeatureNativeDns : FeatureNative() {
    companion object {
        private const val TAG = "FeatureNativeDns"
        private const val CONFIG_KEY = "applicationsDns"
        private const val DEFAULT_VALUE = "original"
    }

    /**
     * Checks if the DNS feature has a non-default value to apply.
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
            Result.success(value != null && value.lowercase() != DEFAULT_VALUE)
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to check DNS feature: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Applies the DNS provider. Config-only in Phase 1: the adb commands are
     * executed out-of-band, so this is a no-op placeholder.
     *
     * @param context The feature context containing system dependencies
     * @return Result indicating success
     */
    override fun enable(context: FeatureContext): Result<Unit> {
        Logger.debug(TAG, "DNS feature enable (config-only, no-op in Phase 1)")
        return Result.success(Unit)
    }

    /**
     * Reverts the DNS provider. Config-only in Phase 1: no-op placeholder.
     *
     * @param context The feature context containing system dependencies
     * @return Result indicating success
     */
    override fun disable(context: FeatureContext): Result<Unit> {
        Logger.debug(TAG, "DNS feature disable (config-only, no-op in Phase 1)")
        return Result.success(Unit)
    }
}
