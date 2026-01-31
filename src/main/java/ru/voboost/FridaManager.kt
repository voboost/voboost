package ru.voboost

import org.json.JSONObject

/**
 * Interface for managing Frida script injection into target processes.
 * Provides methods for injecting scripts, stopping injections, and cleanup operations.
 */
interface FridaManager {
    /**
     * Data class representing information about an active injection.
     *
     * @property id Unique identifier for the injection
     * @property targetProcess Target process name (e.g., "com.qinggan.app.launcher")
     * @property scriptPath Path to the injected script file
     */
    data class InjectionInfo(
        val id: String,
        val targetProcess: String,
        val scriptPath: String,
    )

    /**
     * Inject a Frida script into a target process.
     *
     * @param targetProcess Target process name (e.g., "com.qinggan.app.launcher")
     * @param scriptPath Path to script file (e.g., "/data/local/tmp/test/weather-widget-mod_debug.js")
     * @param params Optional parameters as JSONObject (passed to script via --parameters)
     * @return Result containing the injection ID for tracking, or error if injection failed
     */
    fun injectScript(
        targetProcess: String,
        scriptPath: String,
        params: JSONObject? = null,
    ): Result<String>

    /**
     * Stop a specific injection by ID.
     *
     * @param injectionId The ID of the injection to stop
     * @return Result indicating success or failure
     */
    fun stopInjection(injectionId: String): Result<Unit>

    /**
     * Stop all injections for a specific target process.
     *
     * @param targetProcess The target process name
     * @return Result indicating success or failure
     */
    fun stopInjectionsForTarget(targetProcess: String): Result<Unit>

    /**
     * Get list of currently active injections.
     *
     * @return Result containing list of active injection information
     */
    fun getActiveInjections(): Result<List<InjectionInfo>>

    /**
     * Shutdown all injections and perform cleanup operations.
     *
     * @return Result indicating success or failure
     */
    fun shutdown(): Result<Unit>
}
