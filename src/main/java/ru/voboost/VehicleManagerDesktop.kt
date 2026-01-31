package ru.voboost

import java.io.File

/**
 * Gets vehicle info from environment variables or desktop config.
 * For testing purposes.
 *
 * Environment variables:
 *   VOBOOST_VEHICLE_MODEL - "free" or "dreamer"
 *   VOBOOST_VEHICLE_YEAR - year number
 *   VOBOOST_FIRMWARE_VERSION - firmware version string
 *   VOBOOST_ACTIVATED - "true" or "false"
 *   VOBOOST_LANGUAGE - language code
 *   VOBOOST_DISABLED_FEATURES - comma-separated list of disabled features
 *
 * Or create ~/.voboost/vehicle.yaml with these values.
 */
class VehicleManagerDesktop(
    private val desktopConfigFile: File =
        File(System.getProperty("user.home"), ".voboost/vehicle.yaml"),
) : VehicleManager {
    override fun getVehicleInfo(): Result<VehicleManager.VehicleInfo> {
        return try {
            val model = getVehicleModel().getOrThrow()
            val year = getVehicleYear().getOrThrow()
            val firmware = getFirmwareVersion().getOrThrow()
            val status = getActivationStatus().getOrThrow()
            val features = getSupportedFeatures().getOrThrow()

            Result.success(
                VehicleManager.VehicleInfo(
                    model = model,
                    year = year,
                    firmware = firmware,
                    activationStatus = status,
                    supportedFeatures = features,
                ),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getVehicleModel(): Result<String> {
        return try {
            val model =
                System.getenv("VOBOOST_VEHICLE_MODEL")
                    ?: readConfig("model")
                    ?: "free"
            Result.success(model)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getVehicleYear(): Result<Int> {
        return try {
            val year =
                System.getenv("VOBOOST_VEHICLE_YEAR")?.toIntOrNull()
                    ?: readConfig("year")?.toIntOrNull()
                    ?: 2023
            Result.success(year)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getFirmwareVersion(): Result<String> {
        return try {
            val firmware =
                System.getenv("VOBOOST_FIRMWARE_VERSION")
                    ?: readConfig("firmware")
                    ?: "test-1.0"
            Result.success(firmware)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getActivationStatus(): Result<VehicleManager.ActivationStatus> {
        return try {
            val activated =
                System.getenv("VOBOOST_ACTIVATED")?.toBoolean()
                    ?: readConfig("activated")?.toBoolean()
                    ?: true
            val status =
                if (activated) {
                    VehicleManager.ActivationStatus.ACTIVATED
                } else {
                    VehicleManager.ActivationStatus.NOT_ACTIVATED
                }
            Result.success(status)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun isFeatureSupported(featureName: String): Result<Boolean> {
        return try {
            val disabled = System.getenv("VOBOOST_DISABLED_FEATURES")?.split(",") ?: emptyList()
            Result.success(featureName !in disabled)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getSupportedFeatures(): Result<List<String>> {
        return try {
            val allFeatures =
                listOf(
                    "weather-widget",
                    "keyboard-layout",
                    "settings-menu",
                    "vehicle-fuel-mode",
                )
            val disabled = System.getenv("VOBOOST_DISABLED_FEATURES")?.split(",") ?: emptyList()
            val supported = allFeatures.filter { it !in disabled }
            Result.success(supported)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun isVoyahFree(): Result<Boolean> {
        return try {
            val model = getVehicleModel().getOrThrow()
            Result.success(model.equals("free", ignoreCase = true))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun isVoyahDreamer(): Result<Boolean> {
        return try {
            val model = getVehicleModel().getOrThrow()
            Result.success(model.equals("dreamer", ignoreCase = true))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun readConfig(key: String): String? {
        if (!desktopConfigFile.exists()) return null
        return desktopConfigFile.readLines()
            .find { it.startsWith("$key:") }
            ?.substringAfter(":")
            ?.trim()
    }
}
