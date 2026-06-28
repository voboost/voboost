package ru.voboost

import ru.voboost.config.ConfigManager
import ru.voboost.feature.FeatureContext

/**
 * Main application orchestrator.
 * Coordinates feature management based on configuration.
 *
 * In daemon-contract architecture, Main orchestrates plan production and
 * status reading rather than direct injection.
 */
class Main(
    private val configManager: ConfigManager,
    private val paths: PathsAndroid,
    private val vehicleManager: VehicleManagerAndroid,
    private val planProducer: PlanProducer,
    private val statusReader: StatusReader,
) {
    companion object {
        private const val LOG = "Main"
    }

    private val featureManager = FeatureManager()

    /**
     * Start the application.
     *
     * @return Result indicating success or failure
     */
    fun start(): Result<Unit> {
        return try {
            val configResult = configManager.loadConfig()

            configResult.fold(
                onSuccess = { config ->

                    Logger.info(LOG, "Starting Voboost")
                    Logger.info(LOG, "Startup mode: ${config.settingsStartup}")

                    // Check startup mode - exit if off
                    if (config.settingsStartup == ru.voboost.config.models.StartupMode.off) {
                        Logger.info(LOG, "Startup mode is 'off', exiting")
                        return Result.success(Unit)
                    }

                    vehicleManager.getVehicleModel().fold(
                        onSuccess = { model ->
                            vehicleManager.getVehicleYear().fold(
                                onSuccess = { year ->
                                    Logger.info(LOG, "Vehicle: $model ($year)")
                                },
                                onFailure = { error ->
                                    Logger.debug(
                                        LOG,
                                        "Could not get vehicle year: ${error.message}",
                                    )
                                },
                            )
                        },
                        onFailure = { error ->
                            Logger.debug(LOG, "Could not get vehicle model: ${error.message}")
                        },
                    )
                    Logger.info(LOG, "Config: ${paths.configFile}")

                    val featureContext =
                        FeatureContext(
                            androidContext = null,
                            vehicleManager = vehicleManager,
                            config = config,
                            paths = paths,
                        )

                    featureManager.applyConfig(featureContext, planProducer)
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Logger.error(LOG, "Failed to load config: ${error.message}")
                    Result.failure(error)
                },
            )
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to start: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Shutdown the application.
     *
     * @return Result indicating success or failure
     */
    fun shutdown(): Result<Unit> {
        return try {
            Logger.info(LOG, "Shutting down")
            val configResult = configManager.loadConfig()
            configResult.fold(
                onSuccess = { config ->
                    val featureContext =
                        FeatureContext(
                            androidContext = null,
                            vehicleManager = vehicleManager,
                            config = config,
                            paths = paths,
                        )
                    featureManager.stopAll(featureContext, planProducer)
                },
                onFailure = { error ->
                    Logger.error(LOG, "Failed to load config during shutdown: ${error.message}")
                },
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to shutdown: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Produce plan from current configuration.
     *
     * @return Result indicating success or failure
     */
    fun producePlan(): Result<Unit> {
        return try {
            val configResult = configManager.loadConfig()
            configResult.fold(
                onSuccess = { config ->
                    val featureContext =
                        FeatureContext(
                            androidContext = null,
                            vehicleManager = vehicleManager,
                            config = config,
                            paths = paths,
                        )
                    featureManager.applyConfig(featureContext, planProducer)
                },
                onFailure = { error ->
                    Logger.error(LOG, "Failed to load config for plan production: ${error.message}")
                    Result.failure(error)
                },
            )
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to produce plan: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Read current daemon status.
     *
     * @return DaemonStatus or null if unavailable
     */
    fun readStatus(): StatusReader.DaemonStatus? {
        return statusReader.read()
    }
}
