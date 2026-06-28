package ru.voboost

import ru.voboost.feature.Feature
import ru.voboost.feature.FeatureContext
import ru.voboost.feature.FeatureFrida
import ru.voboost.feature.FeatureFridaForcedEV
import ru.voboost.feature.FeatureFridaKeyboard
import ru.voboost.feature.FeatureFridaSettingsMenu
import ru.voboost.feature.FeatureFridaWeather
import ru.voboost.feature.FeatureVehiclePedestrianWarning

/**
 * Manages features based on configuration.
 *
 * In daemon-contract architecture, FeatureManager collects agent configurations
 * from active features and passes them to PlanProducer to generate inject.json.
 * The daemon handles actual injection based on the plan.
 */
class FeatureManager {
    companion object {
        private const val LOG = "FeatureManager"
    }

    private val features: List<Feature> =
        listOf(
            FeatureFridaWeather(),
            FeatureFridaKeyboard(),
            FeatureFridaForcedEV(),
            FeatureFridaSettingsMenu(),
            FeatureVehiclePedestrianWarning(),
        )

    private val enabledFeatures = mutableSetOf<String>()

    /**
     * Mapping of agent id → target process, sourced from [FeatureFrida] definitions.
     * Used by the OTA client to populate the daemon manifest's per-agent process
     * (the release manifest carries no process field).
     */
    fun getAgentProcessMapping(): Map<String, String> =
        features.filterIsInstance<FeatureFrida>().associate { it.agentId to it.targetProcess }

    /**
     * Apply full configuration by producing inject.json plan.
     *
     * Collects agent configurations from active features and passes them to
     * PlanProducer to generate inject.json. The daemon handles actual injection.
     *
     * @param context Feature context
     * @param planProducer Plan producer instance
     * @return Result indicating success or failure
     */
    fun applyConfig(
        context: FeatureContext,
        planProducer: PlanProducer,
    ): Result<Unit> {
        return try {
            // Collect agent entries from active features
            val agentEntries = mutableListOf<PlanProducer.AgentEntry>()
            val newlyEnabled = mutableSetOf<String>()

            features.forEach { feature ->
                val shouldEnableResult = feature.shouldEnable(context)
                shouldEnableResult.fold(
                    onSuccess = { shouldEnable ->
                        if (shouldEnable) {
                            enabledFeatures.add(feature.javaClass.simpleName)

                            // Try to get plan entry from feature
                            if (feature is ru.voboost.feature.FeatureFrida) {
                                val planEntry = feature.planEntry(context)
                                if (planEntry != null) {
                                    agentEntries.add(planEntry)
                                    newlyEnabled.add(feature.javaClass.simpleName)
                                    Logger.info(
                                        LOG,
                                        "Added agent: ${planEntry.id} " +
                                            "from ${feature.javaClass.simpleName}",
                                    )
                                }
                            }

                            // Call enable for side effects (no-op in daemon-contract)
                            feature.enable(context).fold(
                                onSuccess = {
                                    Logger.debug(
                                        LOG,
                                        "Enabled: ${feature.javaClass.simpleName}",
                                    )
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

            // Produce the plan
            if (agentEntries.isEmpty()) {
                Logger.info(LOG, "No active features, removing plan")
                planProducer.removePlan()
            } else {
                val produceResult =
                    planProducer.produce(
                        context.config,
                        agentEntries,
                        disabled = false,
                    )
                produceResult.fold(
                    onSuccess = {
                        Logger.info(
                            LOG,
                            "Plan produced successfully: ${newlyEnabled.size} agents",
                        )
                    },
                    onFailure = { error ->
                        Logger.error(LOG, "Failed to produce plan: ${error.message}")
                        return Result.failure(error)
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
     * In daemon-contract architecture, this removes the plan by producing
     * an empty agent list. The daemon handles cleanup.
     *
     * @param context Feature context
     * @param planProducer Plan producer instance
     * @return Result indicating success or failure
     */
    fun stopAll(
        context: FeatureContext,
        planProducer: PlanProducer,
    ): Result<Unit> {
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

            // Remove plan by producing empty agent list
            planProducer.removePlan()

            Result.success(Unit)
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to stop all features: ${e.message}")
            Result.failure(e)
        }
    }
}
