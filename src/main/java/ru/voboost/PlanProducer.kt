package ru.voboost

import org.json.JSONArray
import org.json.JSONObject
import ru.voboost.config.models.Config
import java.io.File

/**
 * Produces inject.json plan from configuration and active features.
 *
 * The plan file is the application's contract with the voboost-inject daemon.
 * It specifies which agents should be injected, their configuration, and global
 * settings like startup mode and disabled state.
 *
 * ## Plan Schema
 * ```json
 * {
 *   "version": 0,
 *   "startup": "none|hidden|interface",
 *   "disabled": false,
 *   "agents": [
 *     {
 *       "id": "agent-id",
 *       "enabled": true,
 *       "config": { ... }
 *     }
 *   ]
 * }
 * ```
 *
 * ## Constraints
 * - Entire plan must not exceed 1 MiB
 * - Each agent config must not exceed 64 KiB
 * - Plan is written atomically (temp file + rename)
 */
class PlanProducer(private val paths: Paths) {
    companion object {
        private const val LOG = "PlanProducer"
        private const val MAX_PLAN_SIZE = 1_048_576 // 1 MiB
        private const val MAX_AGENT_CONFIG_SIZE = 65_536 // 64 KiB
        private const val PLAN_VERSION = 0
    }

    /**
     * Data class representing a single agent entry in the plan.
     *
     * @property id Agent identifier matching daemon manifest
     * @property enabled Whether the agent should be active
     * @property config Opaque configuration object forwarded to agent
     */
    data class AgentEntry(
        val id: String,
        val enabled: Boolean = true,
        val config: Map<String, Any?> = emptyMap(),
    )

    /**
     * Produces inject.json from configuration and agent entries.
     *
     * @param config Application configuration
     * @param agents List of agent entries to include in the plan
     * @param disabled Global kill-switch (optional)
     * @return Result indicating success or failure
     */
    fun produce(
        config: Config,
        agents: List<AgentEntry>,
        disabled: Boolean = false,
    ): Result<Unit> {
        return try {
            // Build the plan JSON
            val planJson = buildPlanJson(config, agents, disabled)

            // Validate size constraints
            val planString = planJson.toString()
            val planSize = planString.toByteArray().size

            if (planSize > MAX_PLAN_SIZE) {
                val error =
                    "Plan exceeds 1 MiB limit ($planSize bytes). " +
                        "Reduce agent count or config sizes."
                Logger.error(LOG, error)
                return Result.failure(IllegalStateException(error))
            }

            // Validate individual agent config sizes
            for (agent in agents) {
                val configSize =
                    JSONObject(agent.config).toString().toByteArray().size
                if (configSize > MAX_AGENT_CONFIG_SIZE) {
                    val error =
                        "Agent ${agent.id} config exceeds 64 KiB limit " +
                            "($configSize bytes)"
                    Logger.error(LOG, error)
                    return Result.failure(IllegalStateException(error))
                }
            }

            // Write atomically (temp file + rename)
            writeAtomically(paths.injectJson, planString)

            Logger.info(
                LOG,
                "Plan produced successfully: ${agents.size} agents, " +
                    "$planSize bytes",
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to produce plan: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Builds the plan JSON structure.
     */
    private fun buildPlanJson(
        config: Config,
        agents: List<AgentEntry>,
        disabled: Boolean,
    ): JSONObject {
        val planJson = JSONObject()

        planJson.put("version", PLAN_VERSION)

        // Map startup mode to daemon's startup gate
        // "off" -> "none" (no injection requested)
        val startupValue =
            when (config.settingsStartup?.name) {
                "off" -> "none"
                "hidden" -> "hidden"
                "interface" -> "interface"
                else -> "interface" // Default fallback
            }
        planJson.put("startup", startupValue)

        // Global kill-switch
        planJson.put("disabled", disabled)

        // Build agents array
        val agentsArray = JSONArray()
        for (agent in agents) {
            val agentJson = JSONObject()
            agentJson.put("id", agent.id)
            agentJson.put("enabled", agent.enabled)

            // Add config object
            val configJson = JSONObject(agent.config)
            agentJson.put("config", configJson)

            agentsArray.put(agentJson)
        }
        planJson.put("agents", agentsArray)

        return planJson
    }

    /**
     * Writes content to file atomically using temp + rename.
     */
    private fun writeAtomically(
        targetFile: File,
        content: String,
    ) {
        // Create temp file in same directory
        val tempFile =
            File(targetFile.parentFile, ".${targetFile.name}.tmp")

        try {
            // Write to temp file
            tempFile.writeText(content)

            // Rename atomically
            if (!tempFile.renameTo(targetFile)) {
                throw IllegalStateException("Failed to rename temp file to target")
            }
        } catch (e: Exception) {
            // Clean up temp file on failure
            tempFile.delete()
            throw e
        }
    }

    /**
     * Removes the plan file if it exists.
     *
     * @return Result indicating success or failure
     */
    fun removePlan(): Result<Unit> {
        return try {
            if (paths.injectJson.exists()) {
                paths.injectJson.delete()
                Logger.info(LOG, "Plan file removed")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to remove plan: ${e.message}")
            Result.failure(e)
        }
    }
}
