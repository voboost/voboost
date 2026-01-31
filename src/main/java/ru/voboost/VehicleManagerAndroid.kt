package ru.voboost

import android.content.Context

/**
 * Android-specific vehicle manager implementation.
 * Retrieves vehicle information from Android system properties and car APIs.
 */
class VehicleManagerAndroid(private val context: Context) : VehicleManager {
    companion object {
        private const val LOG = "VehicleManagerAndroid"
        private const val PROP_MODEL = "ro.voyah.model"
        private const val PROP_YEAR = "ro.voyah.year"
        private const val PROP_FIRMWARE = "ro.voyah.firmware"
        private const val PROP_ACTIVATED = "ro.voyah.activated"
    }

    override fun getVehicleInfo(): Result<VehicleManager.VehicleInfo> {
        return try {
            val model = getVehicleModel().getOrThrow()
            val year = getVehicleYear().getOrThrow()
            val firmware = getFirmwareVersion().getOrThrow()
            val activationStatus = getActivationStatus().getOrThrow()
            val supportedFeatures = getSupportedFeatures().getOrThrow()

            val info =
                VehicleManager.VehicleInfo(
                    model = model,
                    year = year,
                    firmware = firmware,
                    activationStatus = activationStatus,
                    supportedFeatures = supportedFeatures,
                )
            Result.success(info)
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to get vehicle info: ${e.message}")
            Result.failure(e)
        }
    }

    override fun getVehicleModel(): Result<String> {
        return try {
            val model = readSystemProperty(PROP_MODEL) ?: "free"
            Result.success(model)
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to get vehicle model: ${e.message}")
            Result.failure(e)
        }
    }

    override fun getVehicleYear(): Result<Int> {
        return try {
            val yearStr = readSystemProperty(PROP_YEAR)
            val year = yearStr?.toIntOrNull() ?: 2023
            Result.success(year)
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to get vehicle year: ${e.message}")
            Result.failure(e)
        }
    }

    override fun getFirmwareVersion(): Result<String> {
        return try {
            val firmware = readSystemProperty(PROP_FIRMWARE) ?: "unknown"
            Result.success(firmware)
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to get firmware version: ${e.message}")
            Result.failure(e)
        }
    }

    override fun getActivationStatus(): Result<VehicleManager.ActivationStatus> {
        return try {
            val activated = readSystemProperty(PROP_ACTIVATED)
            val status =
                when (activated) {
                    "true" -> VehicleManager.ActivationStatus.ACTIVATED
                    "false" -> VehicleManager.ActivationStatus.NOT_ACTIVATED
                    else -> VehicleManager.ActivationStatus.UNKNOWN
                }
            Result.success(status)
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to get activation status: ${e.message}")
            Result.failure(e)
        }
    }

    override fun isFeatureSupported(featureName: String): Result<Boolean> {
        return try {
            val model = getVehicleModel().getOrElse { "free" }
            val supported =
                when (featureName) {
                    "vehicle-fuel-mode" -> model.equals("free", ignoreCase = true)
                    "vehicle-battery-management" -> true
                    "vehicle-climate-control" -> true
                    "vehicle-media-control" -> true
                    else -> true
                }
            Result.success(supported)
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to check feature support: ${e.message}")
            Result.failure(e)
        }
    }

    override fun getSupportedFeatures(): Result<List<String>> {
        return try {
            val model = getVehicleModel().getOrElse { "free" }
            val features =
                mutableListOf(
                    "vehicle-battery-management",
                    "vehicle-climate-control",
                    "vehicle-media-control",
                )

            if (model.equals("free", ignoreCase = true)) {
                features.add("vehicle-fuel-mode")
            }

            Result.success(features)
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to get supported features: ${e.message}")
            Result.failure(e)
        }
    }

    override fun isVoyahFree(): Result<Boolean> {
        return try {
            val model = getVehicleModel().getOrElse { "free" }
            Result.success(model.equals("free", ignoreCase = true))
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to check if Voyah Free: ${e.message}")
            Result.failure(e)
        }
    }

    override fun isVoyahDreamer(): Result<Boolean> {
        return try {
            val model = getVehicleModel().getOrElse { "free" }
            Result.success(model.equals("dreamer", ignoreCase = true))
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to check if Voyah Dreamer: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Read a system property value.
     * Returns null if property doesn't exist or cannot be read.
     */
    private fun readSystemProperty(name: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", name))
            val result = process.inputStream.bufferedReader().readLine()?.trim()
            process.waitFor()
            if (result.isNullOrEmpty()) null else result
        } catch (e: Exception) {
            Logger.debug(LOG, "Failed to read system property $name: ${e.message}")
            null
        }
    }
}
