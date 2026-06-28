package ru.voboost.feature

import ru.voboost.Logger
import ru.voboost.PlanProducer

/**
 * Abstract base class for Frida-based features in daemon-contract architecture.
 *
 * In the new architecture, features no longer perform direct injection. Instead,
 * they declare their agent configuration through the planEntry() method, which
 * is collected by FeatureManager and passed to PlanProducer to generate inject.json.
 *
 * Concrete implementations must provide:
 * - agentId: The agent identifier matching the daemon manifest
 * - targetProcess: The target process name (for documentation/mapping)
 * - planEntry(): Returns the agent configuration or null if feature not active
 * - shouldEnableImpl(): Feature-specific enablement logic
 *
 * ## Example
 * ```kotlin
 * class FeatureFridaWeather : FeatureFrida() {
 *     override val agentId = "weather-widget"
 *     override val targetProcess = "com.qinggan.app.launcher"
 *
 *     override fun shouldEnableImpl(context: FeatureContext): Result<Boolean> {
 *         // Check config, vehicle model, etc.
 *         return Result.success(configValue == "enable-non-chinese-cities")
 *     }
 *
 *     override fun planEntry(context: FeatureContext): PlanProducer.AgentEntry? {
 *         if (!shouldEnableImpl(context).getOrDefault(false)) return null
 *         return PlanProducer.AgentEntry(
 *             id = agentId,
 *             enabled = true,
 *             config = mapOf("language" to "en")
 *         )
 *     }
 * }
 * ```
 */
abstract class FeatureFrida : Feature {
    companion object {
        private const val TAG = "FeatureFrida"
    }

    /**
     * The agent identifier matching the daemon manifest.
     * Concrete implementations must provide this value.
     */
    abstract val agentId: String

    /**
     * The target process name for documentation/mapping purposes.
     * This is NOT used for injection in daemon-contract architecture.
     */
    abstract val targetProcess: String

    /**
     * Checks if the feature should be enabled based on current system state.
     * This method delegates to shouldEnableImpl() for feature-specific checks.
     *
     * @param context The feature context containing system dependencies
     * @return Result containing true if feature should be enabled, false otherwise
     */
    override fun shouldEnable(context: FeatureContext): Result<Boolean> {
        return try {
            shouldEnableImpl(context)
        } catch (e: Exception) {
            Logger.error(
                TAG,
                "Failed to check if $agentId should be enabled: ${e.message}",
            )
            Result.failure(e)
        }
    }

    /**
     * Implementation-specific check for whether the feature should be enabled.
     * Concrete implementations should override this method to provide their
     * specific enablement logic (config checks, vehicle model checks, etc.).
     *
     * Default implementation returns true (feature should be enabled).
     *
     * @param context The feature context containing system dependencies
     * @return Result containing true if feature should be enabled, false otherwise
     */
    protected open fun shouldEnableImpl(context: FeatureContext): Result<Boolean> {
        return Result.success(true)
    }

    /**
     * Returns the agent configuration for inject.json, or null if not active.
     * This is the key method in daemon-contract architecture - it declares
     * what should be injected rather than performing injection itself.
     *
     * Default implementation checks shouldEnableImpl() and returns null if
     * not enabled, otherwise returns a basic enabled entry with empty config.
     *
     * Concrete implementations should override this to provide agent-specific
     * configuration in the config map.
     *
     * @param context The feature context containing system dependencies
     * @return AgentEntry for inject.json, or null if feature not active
     */
    open fun planEntry(context: FeatureContext): PlanProducer.AgentEntry? {
        val shouldEnableResult = shouldEnableImpl(context)
        val shouldEnable = shouldEnableResult.getOrDefault(false)

        if (!shouldEnable) {
            Logger.debug(TAG, "Feature $agentId not active, skipping plan entry")
            return null
        }

        return PlanProducer.AgentEntry(
            id = agentId,
            enabled = true,
            config = getAgentConfig(context),
        )
    }

    /**
     * Returns the agent-specific configuration map.
     * Concrete implementations should override this to provide their
     * specific configuration values.
     *
     * Default implementation returns empty map.
     *
     * @param context The feature context containing system dependencies
     * @return Map of configuration key-value pairs for the agent
     */
    protected open fun getAgentConfig(context: FeatureContext): Map<String, Any?> {
        return emptyMap()
    }

    /**
     * Enables the feature - no-op in daemon-contract architecture.
     * Actual injection is handled by the daemon based on inject.json.
     *
     * @param context The feature context containing system dependencies
     * @return Result.success(Unit) - always succeeds
     */
    override fun enable(context: FeatureContext): Result<Unit> {
        Logger.debug(TAG, "Feature $agentId enable requested (no-op in daemon-contract)")
        return Result.success(Unit)
    }

    /**
     * Disables the feature - no-op in daemon-contract architecture.
     * Actual injection lifecycle is managed by the daemon.
     *
     * @param context The feature context containing system dependencies
     * @return Result.success(Unit) - always succeeds
     */
    override fun disable(context: FeatureContext): Result<Unit> {
        Logger.debug(TAG, "Feature $agentId disable requested (no-op in daemon-contract)")
        return Result.success(Unit)
    }
}
