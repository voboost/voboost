package ru.voboost.feature

import android.content.Context
import ru.voboost.PathsAndroid
import ru.voboost.VehicleManagerAndroid
import ru.voboost.config.models.Config

/**
 * Context containing all dependencies required for feature operations.
 * This provides centralized access to core system components.
 *
 * @property androidContext The Android application context
 * @property vehicleManager The vehicle manager for vehicle interactions
 * @property config The application configuration
 * @property paths Path resolution for platform-specific directories
 */
data class FeatureContext(
    val androidContext: Context?,
    val vehicleManager: VehicleManagerAndroid,
    val config: Config,
    val paths: PathsAndroid,
)

/**
 * Interface for system features that can be enabled or disabled.
 * Features are modular components that extend Voboost functionality.
 *
 * In the daemon-contract architecture, features no longer perform direct
 * injection. Instead, they declare their agent configuration through the
 * planEntry() method, which is collected by FeatureManager and passed to
 * PlanProducer to generate inject.json.
 */
interface Feature {
    /**
     * Checks if the feature should be enabled based on current system state.
     * This method performs validation without modifying system state.
     *
     * @param context The feature context containing system dependencies
     * @return Result containing true if feature should be enabled, false otherwise
     */
    fun shouldEnable(context: FeatureContext): Result<Boolean>

    /**
     * Enables the feature and applies necessary system modifications.
     * In daemon-contract architecture, this is a no-op since injection
     * is handled by the daemon based on inject.json.
     *
     * @param context The feature context containing system dependencies
     * @return Result indicating success or failure of enable operation
     */
    fun enable(context: FeatureContext): Result<Unit>

    /**
     * Disables the feature and reverts system modifications.
     * In daemon-contract architecture, this is a no-op since injection
     * lifecycle is managed by the daemon.
     *
     * @param context The feature context containing system dependencies
     * @return Result indicating success or failure of disable operation
     */
    fun disable(context: FeatureContext): Result<Unit>
}
