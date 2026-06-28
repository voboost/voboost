package ru.voboost

import org.json.JSONException
import org.json.JSONObject

/**
 * Reads inject-status.json from the daemon.
 *
 * The status file provides real-time information about the daemon state,
 * active injections, and any errors or warnings. The reader is tolerant
 * to partial/in-flight writes since the daemon writes atomically via temp + rename.
 *
 * ## Status Schema
 * ```json
 * {
 *   "daemon": "voboost-inject 1.0.0",
 *   "manifest": 1,
 *   "state": "ready|degraded",
 *   "killed": false,
 *   "panic": false,
 *   "injections": [
 *     {
 *       "id": "agent-id",
 *       "process": "com.target.process",
 *       "state": "active|failed|skipped-coexist|waiting|quarantined"
 *     }
 *   ]
 * }
 * ```
 */
class StatusReader(private val paths: Paths) {
    companion object {
        private const val LOG = "StatusReader"
    }

    /**
     * Daemon state enum.
     */
    enum class DaemonState {
        READY,
        DEGRADED,
    }

    /**
     * Per-injection state enum.
     */
    enum class InjectionState {
        ACTIVE,
        FAILED,
        SKIPPED_COEXIST,
        WAITING,
        QUARANTINED,
    }

    /**
     * Data class representing daemon status.
     *
     * @property daemon Daemon version string
     * @property manifest Manifest version number
     * @property state Overall daemon state
     * @property killed Whether daemon was killed
     * @property panic Whether daemon panicked
     * @property injections List of injection statuses
     */
    data class DaemonStatus(
        val daemon: String,
        val manifest: Int,
        val state: DaemonState,
        val killed: Boolean,
        val panic: Boolean,
        val injections: List<InjectionStatus>,
    )

    /**
     * Data class representing per-injection status.
     *
     * @property id Agent identifier
     * @property process Target process name
     * @property state Current injection state
     */
    data class InjectionStatus(
        val id: String,
        val process: String,
        val state: InjectionState,
    )

    /**
     * Reads the status file, returning null if file doesn't exist or is invalid.
     * Tolerates partial/in-flight writes by catching parse errors.
     *
     * @return DaemonStatus or null if unavailable
     */
    fun read(): DaemonStatus? {
        val statusFile = paths.injectStatusJson

        // Return null if file doesn't exist
        if (!statusFile.exists()) {
            Logger.debug(LOG, "Status file not found: ${statusFile.absolutePath}")
            return null
        }

        return try {
            val content = statusFile.readText()
            val json = JSONObject(content)
            parseStatus(json)
        } catch (e: JSONException) {
            // Log but don't fail - likely in-flight write
            Logger.debug(
                LOG,
                "Failed to parse status file (in-flight write?): ${e.message}",
            )
            null
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to read status file: ${e.message}")
            null
        }
    }

    /**
     * Parses status JSON into strongly-typed objects.
     */
    private fun parseStatus(json: JSONObject): DaemonStatus {
        val daemon = json.getString("daemon")
        val manifest = json.getInt("manifest")
        val stateStr = json.getString("state")
        val state =
            when (stateStr) {
                "ready" -> DaemonState.READY
                "degraded" -> DaemonState.DEGRADED
                else -> DaemonState.DEGRADED // Default to degraded on unknown
            }
        val killed = json.optBoolean("killed", false)
        val panic = json.optBoolean("panic", false)

        // Parse injections array
        val injections = mutableListOf<InjectionStatus>()
        val injectionsArray = json.optJSONArray("injections")
        if (injectionsArray != null) {
            for (i in 0 until injectionsArray.length()) {
                try {
                    val injectionJson = injectionsArray.getJSONObject(i)
                    val id = injectionJson.getString("id")
                    val process = injectionJson.getString("process")
                    val stateStr = injectionJson.getString("state")

                    val injectionState =
                        when (stateStr) {
                            "active" -> InjectionState.ACTIVE
                            "failed" -> InjectionState.FAILED
                            "skipped-coexist" -> InjectionState.SKIPPED_COEXIST
                            "waiting" -> InjectionState.WAITING
                            "quarantined" -> InjectionState.QUARANTINED
                            else -> InjectionState.FAILED // Default on unknown
                        }

                    injections.add(
                        InjectionStatus(
                            id = id,
                            process = process,
                            state = injectionState,
                        ),
                    )
                } catch (e: Exception) {
                    Logger.debug(LOG, "Failed to parse injection $i: ${e.message}")
                }
            }
        }

        return DaemonStatus(
            daemon = daemon,
            manifest = manifest,
            state = state,
            killed = killed,
            panic = panic,
            injections = injections,
        )
    }

    /**
     * Checks if the daemon is in ready state with active injections.
     *
     * @return true if daemon is ready and has at least one active injection
     */
    fun isReadyWithActiveInjections(): Boolean {
        val status = read() ?: return false
        return status.state == DaemonState.READY &&
            status.injections.any { it.state == InjectionState.ACTIVE }
    }

    /**
     * Gets the count of active injections.
     *
     * @return Number of active injections, or 0 if status unavailable
     */
    fun getActiveInjectionCount(): Int {
        val status = read() ?: return 0
        return status.injections.count { it.state == InjectionState.ACTIVE }
    }

    /**
     * Gets list of failed injection IDs.
     *
     * @return List of failed injection IDs, or empty if status unavailable
     */
    fun getFailedInjectionIds(): List<String> {
        val status = read() ?: return emptyList()
        return status.injections
            .filter { it.state == InjectionState.FAILED }
            .map { it.id }
    }
}
