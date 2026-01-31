package ru.voboost

import android.content.Context
import ru.voboost.config.ConfigManager
import ru.voboost.feature.FeatureContext

/**
 * Main application orchestrator.
 * Coordinates feature management based on configuration.
 */
class Main(
    private val context: Context,
    private val paths: Paths,
    private val fridaManager: FridaManager,
    private val vehicleManager: VehicleManager,
) {
    companion object {
        private const val LOG = "Main"
    }

    private val featureManager = FeatureManager()
    private lateinit var configManager: ConfigManager

    /**
     * Start the application.
     *
     * @return Result indicating success or failure
     */
    fun start(): Result<Unit> {
        return try {
            configManager = ConfigManager(context)
            val configResult = configManager.loadConfig()

            configResult.fold(
                onSuccess = { config ->
                    Logger.init(paths.logDirectory, level = "info")

                    Logger.info(LOG, "Starting Voboost")
                    vehicleManager.getVehicleModel().fold(
                        onSuccess = { model ->
                            vehicleManager.getVehicleYear().fold(
                                onSuccess = { year ->
                                    Logger.info(LOG, "Vehicle: $model ($year)")
                                },
                                onFailure = { },
                            )
                        },
                        onFailure = { },
                    )
                    Logger.info(LOG, "Config: ${paths.configFile}")

                    val context =
                        FeatureContext(
                            config = config,
                            fridaManager = fridaManager,
                            paths = paths,
                            vehicleManager = vehicleManager,
                        )

                    featureManager.applyConfig(context)
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
                    val context =
                        FeatureContext(
                            config = config,
                            fridaManager = fridaManager,
                            paths = paths,
                            vehicleManager = vehicleManager,
                        )
                    featureManager.stopAll(context)
                },
                onFailure = { error ->
                    Logger.error(LOG, "Failed to load config during shutdown: ${error.message}")
                },
            )
            fridaManager.shutdown()
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to shutdown: ${e.message}")
            Result.failure(e)
        }
    }
}
