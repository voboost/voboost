package ru.voboost

/**
 * Interface for accessing vehicle information and managing vehicle-specific features.
 * Provides methods for retrieving vehicle details, checking activation status,
 * and determining feature support for different vehicle models.
 */
interface VehicleManager {
    /**
     * Data class representing comprehensive vehicle information.
     *
     * @property model Vehicle model (e.g., "Free", "Dreamer")
     * @property year Vehicle manufacturing year
     * @property firmware Current firmware version
     * @property activationStatus Current activation status of the vehicle
     * @property supportedFeatures List of features supported by this vehicle
     */
    data class VehicleInfo(
        val model: String,
        val year: Int,
        val firmware: String,
        val activationStatus: ActivationStatus,
        val supportedFeatures: List<String>,
    )

    /**
     * Enum representing possible activation states of the vehicle.
     */
    enum class ActivationStatus {
        /**
         * Vehicle is fully activated and all features are available.
         */
        ACTIVATED,

        /**
         * Vehicle is not activated and features are limited.
         */
        NOT_ACTIVATED,

        /**
         * Vehicle activation status is unknown or could not be determined.
         */
        UNKNOWN,
    }

    /**
     * Get comprehensive information about the current vehicle.
     *
     * @return Result containing VehicleInfo with all vehicle details, or error if retrieval failed
     */
    fun getVehicleInfo(): Result<VehicleInfo>

    /**
     * Get the model of the current vehicle.
     *
     * @return Result containing the vehicle model string, or error if retrieval failed
     */
    fun getVehicleModel(): Result<String>

    /**
     * Get the manufacturing year of the current vehicle.
     *
     * @return Result containing the vehicle year, or error if retrieval failed
     */
    fun getVehicleYear(): Result<Int>

    /**
     * Get the current firmware version of the vehicle.
     *
     * @return Result containing the firmware version string, or error if retrieval failed
     */
    fun getFirmwareVersion(): Result<String>

    /**
     * Get the current activation status of the vehicle.
     *
     * @return Result containing the activation status, or error if retrieval failed
     */
    fun getActivationStatus(): Result<ActivationStatus>

    /**
     * Check if a specific feature is supported by the current vehicle.
     *
     * @param featureName The name of the feature to check
     * @return Result containing true if feature is supported, false otherwise, or error if check failed
     */
    fun isFeatureSupported(featureName: String): Result<Boolean>

    /**
     * Get the list of all features supported by the current vehicle.
     *
     * @return Result containing list of supported feature names, or error if retrieval failed
     */
    fun getSupportedFeatures(): Result<List<String>>

    /**
     * Check if the vehicle is a Voyah Free model.
     *
     * @return Result containing true if vehicle is Free model, false otherwise, or error if check failed
     */
    fun isVoyahFree(): Result<Boolean>

    /**
     * Check if the vehicle is a Voyah Dreamer model.
     *
     * @return Result containing true if vehicle is Dreamer model, false otherwise, or error if check failed
     */
    fun isVoyahDreamer(): Result<Boolean>
}
