package ru.voboost.feature

import ru.voboost.FridaManager
import ru.voboost.Paths
import ru.voboost.VehicleManager
import ru.voboost.config.models.Config

/**
 * Context containing all dependencies required for feature operations.
 * This provides centralized access to core system components.
 *
 * @property config The configuration object
 * @property fridaManager The Frida manager for script execution
 * @property paths The paths manager for file system operations
 * @property vehicleManager The vehicle manager for vehicle interactions
 */
data class FeatureContext(
    val config: Config,
    val fridaManager: FridaManager,
    val paths: Paths,
    val vehicleManager: VehicleManager,
)

/**
 * Interface for system features that can be enabled or disabled.
 * Features are modular components that extend Voboost functionality.
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
     * This method should only be called after successful shouldEnable check.
     *
     * @param context The feature context containing system dependencies
     * @return Result indicating success or failure of enable operation
     */
    fun enable(context: FeatureContext): Result<Unit>

    /**
     * Disables the feature and reverts system modifications.
     * This method should safely clean up all feature-related changes.
     *
     * @param context The feature context containing system dependencies
     * @return Result indicating success or failure of disable operation
     */
    fun disable(context: FeatureContext): Result<Unit>
}
