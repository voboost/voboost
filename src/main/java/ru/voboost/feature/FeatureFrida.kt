package ru.voboost.feature

import ru.voboost.Logger
import java.io.File

/**
 * Abstract base class for Frida-based features.
 * Provides common functionality for script injection lifecycle management
 * and script file resolution based on log level.
 *
 * This class handles the boilerplate of Frida script injection, including
 * script path resolution, injection lifecycle, and cleanup operations.
 * Concrete implementations only need to provide the target process name
 * and script file name.
 */
abstract class FeatureFrida : Feature {
    /**
     * The target process name for script injection.
     * Concrete implementations must provide this value.
     *
     * @return The target process name (e.g., "com.qinggan.app.launcher")
     */
    protected abstract val targetProcess: String

    /**
     * The base script file name without extension or log level suffix.
     * Concrete implementations must provide this value.
     *
     * @return The base script file name (e.g., "weather-widget-mod")
     */
    protected abstract val scriptName: String

    /**
     * Optional parameters to pass to the Frida script.
     * Default implementation returns null.
     *
     * @return JSONObject with parameters or null if no parameters needed
     */
    protected open fun getScriptParameters(): org.json.JSONObject? = null

    /**
     * The current injection ID for tracking purposes.
     * This is managed internally by the base class.
     */
    private var injectionId: String? = null

    /**
     * Checks if the feature should be enabled based on current system state.
     * This method first delegates to shouldEnableImpl() for feature-specific checks
     * (config, vehicle model, etc.), and only if those pass, checks target process availability.
     * This order is more efficient as it avoids unnecessary process checks when the feature
     * is disabled in configuration.
     *
     * @param context The feature context containing system dependencies
     * @return Result containing true if feature should be enabled, false otherwise
     */
    override fun shouldEnable(context: FeatureContext): Result<Boolean> {
        return try {
            // First check feature-specific conditions (config, vehicle model, etc.)
            val shouldEnableResult = shouldEnableImpl(context)

            // If feature-specific checks fail, return early
            shouldEnableResult.fold(
                onSuccess = { shouldEnable ->
                    if (!shouldEnable) {
                        return Result.success(false)
                    }
                },
                onFailure = { error ->
                    return Result.failure(error)
                },
            )

            // Only check target process availability if feature should be enabled
            val isTargetAvailable = checkTargetProcessAvailability(context)
            if (!isTargetAvailable) {
                Logger.info(
                    "FeatureFrida",
                    "Target process not available: $targetProcess",
                )
                return Result.success(false)
            }

            Result.success(true)
        } catch (e: Exception) {
            Logger.error(
                "FeatureFrida",
                "Failed to check if feature should be enabled: ${e.message}",
            )
            Result.failure(e)
        }
    }

    /**
     * Implementation-specific check for whether the feature should be enabled.
     * This method is called after the target process availability check passes.
     * Concrete implementations should override this method to provide their
     * specific enablement logic.
     *
     * Default implementation returns true (feature should be enabled if target is available).
     *
     * @param context The feature context containing system dependencies
     * @return Result containing true if feature should be enabled, false otherwise
     */
    protected open fun shouldEnableImpl(context: FeatureContext): Result<Boolean> {
        return Result.success(true)
    }

    /**
     * Enables the feature by injecting the appropriate Frida script.
     * Resolves script file based on current log level and manages injection lifecycle.
     *
     * @param context The feature context containing system dependencies
     * @return Result indicating success or failure of enable operation
     */
    override fun enable(context: FeatureContext): Result<Unit> {
        return try {
            // Check if already enabled
            if (injectionId != null) {
                Logger.info(
                    "FeatureFrida",
                    "Feature already enabled for target: $targetProcess",
                )
                return Result.success(Unit)
            }

            // Resolve script path based on log level
            val scriptPath = resolveScriptPath(context)
            if (!scriptPath.exists()) {
                val error = "Script file not found: $scriptPath"
                Logger.error("FeatureFrida", error)
                return Result.failure(Exception(error))
            }

            // Get script parameters
            val params = getScriptParameters()

            // Inject the script
            val injectResult =
                context.fridaManager.injectScript(
                    targetProcess = targetProcess,
                    scriptPath = scriptPath.absolutePath,
                    params = params,
                )

            injectResult.fold(
                onSuccess = { id: String ->
                    injectionId = id
                    Logger.info(
                        "FeatureFrida",
                        "Successfully enabled feature for target: $targetProcess " +
                            "with injection ID: $id",
                    )
                    Result.success(Unit)
                },
                onFailure = { error: Throwable ->
                    Logger.error(
                        "FeatureFrida",
                        "Failed to inject script for target: $targetProcess, " +
                            "error: ${error.message}",
                    )
                    Result.failure(error)
                },
            )
        } catch (e: Exception) {
            Logger.error(
                "FeatureFrida",
                "Failed to enable feature for target: $targetProcess, error: ${e.message}",
            )
            Result.failure(e)
        }
    }

    /**
     * Disables the feature by stopping the active injection.
     * Performs cleanup of all feature-related changes.
     *
     * @param context The feature context containing system dependencies
     * @return Result indicating success or failure of disable operation
     */
    override fun disable(context: FeatureContext): Result<Unit> {
        return try {
            val currentInjectionId = injectionId
            if (currentInjectionId == null) {
                Logger.info(
                    "FeatureFrida",
                    "Feature not enabled for target: $targetProcess",
                )
                return Result.success(Unit)
            }

            // Stop the injection
            val stopResult = context.fridaManager.stopInjection(currentInjectionId)

            stopResult.fold(
                onSuccess = { _: Unit ->
                    injectionId = null
                    Logger.info(
                        "FeatureFrida",
                        "Successfully disabled feature for target: $targetProcess",
                    )
                    Result.success(Unit)
                },
                onFailure = { error: Throwable ->
                    Logger.error(
                        "FeatureFrida",
                        "Failed to stop injection for target: $targetProcess, " +
                            "error: ${error.message}",
                    )
                    Result.failure(error)
                },
            )
        } catch (e: Exception) {
            Logger.error(
                "FeatureFrida",
                "Failed to disable feature for target: $targetProcess, error: ${e.message}",
            )
            Result.failure(e)
        }
    }

    /**
     * Resolves the script file path based on current log level.
     * Uses debug version of script if log level is debug, otherwise uses standard version.
     *
     * @param context The feature context containing system dependencies
     * @return File object representing the resolved script path
     */
    private fun resolveScriptPath(context: FeatureContext): File {
        val logLevel = Logger.getLevel()
        val scriptFileName =
            if (logLevel == "debug") {
                "${scriptName}_debug.js"
            } else {
                "$scriptName.js"
            }

        return File(context.paths.scriptsDirectory, scriptFileName)
    }

    /**
     * Checks if the target process is available for injection.
     * Default implementation always returns true, but can be overridden
     * by concrete implementations to perform specific checks.
     *
     * @param context The feature context containing system dependencies
     * @return true if target process is available, false otherwise
     */
    protected open fun checkTargetProcessAvailability(context: FeatureContext): Boolean {
        // Default implementation assumes target is always available
        // Concrete implementations can override this to perform actual checks
        return true
    }

    /**
     * Gets the current injection ID for this feature.
     *
     * @return The injection ID if feature is enabled, null otherwise
     */
    fun getInjectionId(): String? = injectionId

    /**
     * Checks if the feature is currently enabled.
     *
     * @return true if feature is enabled (injection active), false otherwise
     */
    fun isEnabled(): Boolean = injectionId != null
}
