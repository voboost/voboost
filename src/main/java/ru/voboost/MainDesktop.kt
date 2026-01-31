package ru.voboost

/**
 * Desktop entry point for Voboost.
 * Initializes desktop-specific implementations and runs the main application logic.
 * Includes shutdown hook for proper cleanup on exit.
 */
fun main() {
    val log = "MainDesktop"

    // Initialize desktop-specific implementations
    val paths = PathsDesktop()
    val fridaManager = FridaManagerDesktop()
    val vehicleManager = VehicleManagerDesktop()

    // Initialize logger with desktop paths
    Logger.init(
        logDirectory = paths.logDirectory,
        level = "info",
        retentionDays = 7,
    )

    Logger.info(log, "Starting Voboost Desktop")

    // Register shutdown hook for cleanup
    Runtime.getRuntime().addShutdownHook(
        Thread {
            Logger.info(log, "Shutting down Voboost Desktop")
            fridaManager.shutdown()
            Logger.shutdown()
        },
    )

    // Log vehicle information
    vehicleManager.getVehicleInfo().fold(
        onSuccess = { info ->
            Logger.info(log, "Vehicle: ${info.model} (${info.year})")
            Logger.info(log, "Firmware: ${info.firmware}")
            Logger.info(log, "Status: ${info.activationStatus}")
            Logger.info(log, "Features: ${info.supportedFeatures.joinToString(", ")}")
        },
        onFailure = { error ->
            Logger.error(log, "Failed to get vehicle info: ${error.message}")
        },
    )

    // Log configuration
    Logger.info(log, "Config file: ${paths.configFile}")
    Logger.info(log, "Scripts directory: ${paths.scriptsDirectory}")
    Logger.info(log, "Frida executable: ${paths.fridaExecutable}")

    Logger.info(log, "Running. Press Ctrl+C to exit.")

    // Keep the application running
    try {
        Thread.currentThread().join()
    } catch (e: InterruptedException) {
        Logger.info(log, "Interrupted")
    }
}
