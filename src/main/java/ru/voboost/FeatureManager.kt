package ru.voboost

import ru.voboost.feature.Feature
import ru.voboost.feature.FeatureContext

/**
 * Manages features based on configuration.
 */
class FeatureManager {
    companion object {
        private const val LOG = "FeatureManager"
    }

    private val features: List<Feature> =
        listOf(
            // Add feature implementations here as they are created
            // Example: FeatureFridaWeather(), FeatureFridaKeyboard()
        )

    private val enabledFeatures = mutableSetOf<String>()

    /**
     * Apply full configuration.
     *
     * @param context Feature context
     * @return Result indicating success or failure
     */
    fun applyConfig(context: FeatureContext): Result<Unit> {
        return try {
            features.forEach { feature ->
                val shouldEnableResult = feature.shouldEnable(context)
                shouldEnableResult.fold(
                    onSuccess = { shouldEnable ->
                        if (shouldEnable) {
                            feature.enable(context).fold(
                                onSuccess = {
                                    enabledFeatures.add(feature.javaClass.simpleName)
                                    Logger.info(LOG, "Enabled: ${feature.javaClass.simpleName}")
                                },
                                onFailure = { error ->
                                    Logger.error(
                                        LOG,
                                        "Failed to enable ${feature.javaClass.simpleName}: " +
                                            "${error.message}",
                                    )
                                },
                            )
                        }
                    },
                    onFailure = { error ->
                        Logger.error(
                            LOG,
                            "Failed to check if ${feature.javaClass.simpleName} " +
                                "should be enabled: ${error.message}",
                        )
                    },
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to apply config: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Stop all active features.
     *
     * @param context Feature context
     * @return Result indicating success or failure
     */
    fun stopAll(context: FeatureContext): Result<Unit> {
        return try {
            Logger.info(LOG, "Stopping all (${enabledFeatures.size} active)")
            features.forEach { feature ->
                feature.disable(context).fold(
                    onSuccess = {
                        Logger.debug(LOG, "Disabled: ${feature.javaClass.simpleName}")
                    },
                    onFailure = { error ->
                        Logger.error(
                            LOG,
                            "Failed to disable ${feature.javaClass.simpleName}: ${error.message}",
                        )
                    },
                )
            }
            enabledFeatures.clear()
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to stop all features: ${e.message}")
            Result.failure(e)
        }
    }
}
