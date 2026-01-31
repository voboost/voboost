package ru.voboost

import org.json.JSONObject

/**
 * Helper class for managing the 11 Voboost agents across 5 process types.
 * Provides convenient methods for injecting specific agents and managing agent groups.
 */
class FridaAgentManager(private val fridaManager: FridaManager) {
    companion object {
        // Process names
        const val PROCESS_LAUNCHER = "com.qinggan.app.launcher"
        const val PROCESS_BLUETOOTH_PHONE = "com.qinggan.bluetoothphone"
        const val PROCESS_SYSTEM_SERVICE = "com.qinggan.systemservice"
        const val PROCESS_QGIME = "com.qinggan.app.qgime"
        const val PROCESS_VEHICLE_SETTING = "com.qinggan.app.vehiclesetting"

        // Script paths (base path)
        private const val SCRIPT_BASE_PATH = "/data/local/tmp/test"

        // Agent definitions
        val AGENT_LAUNCHER_WEATHER = "$SCRIPT_BASE_PATH/weather-widget-mod_debug.js"
        val AGENT_LAUNCHER_APP = "$SCRIPT_BASE_PATH/app-launcher-mod_debug.js"
        val AGENT_LAUNCHER_NAVBAR = "$SCRIPT_BASE_PATH/navbar-launcher-mod_debug.js"
        val AGENT_LAUNCHER_VIEWPORT = "$SCRIPT_BASE_PATH/app-viewport-mod_debug.js"

        val AGENT_PHONE_NUM = "$SCRIPT_BASE_PATH/phone-num-mod_debug.js"

        val AGENT_SYSTEM_MULTI_DISPLAY = "$SCRIPT_BASE_PATH/app-multi-display_debug.js"
        val AGENT_SYSTEM_TO_MENU = "$SCRIPT_BASE_PATH/voboost-to-menu-mod_debug.js"
        val AGENT_SYSTEM_FORCED_EV = "$SCRIPT_BASE_PATH/forced-ev-mod_debug.js"

        val AGENT_QGIME_KEYBOARD_RU = "$SCRIPT_BASE_PATH/keyboard-ru-mod_debug.js"
        val AGENT_QGIME_KEYBOARD_LOCK = "$SCRIPT_BASE_PATH/keyboard-lock-en-mod_debug.js"

        val AGENT_VEHICLE_MEDIA = "$SCRIPT_BASE_PATH/media-source-mod_debug.js"
    }

    /**
     * Data class representing an agent configuration
     */
    data class AgentConfig(
        val name: String,
        val scriptPath: String,
        val targetProcess: String,
        val defaultParams: JSONObject? = null,
    )

    // All available agents
    private val allAgents =
        mapOf(
            // Launcher agents (4)
            "weather-widget" to
                AgentConfig(
                    "Weather Widget", AGENT_LAUNCHER_WEATHER, PROCESS_LAUNCHER,
                ),
            "app-launcher" to
                AgentConfig(
                    "App Launcher", AGENT_LAUNCHER_APP, PROCESS_LAUNCHER,
                ),
            "navbar-launcher" to
                AgentConfig(
                    "Navbar Launcher", AGENT_LAUNCHER_NAVBAR, PROCESS_LAUNCHER,
                ),
            "app-viewport" to
                AgentConfig(
                    "App Viewport", AGENT_LAUNCHER_VIEWPORT, PROCESS_LAUNCHER,
                ),
            // Bluetooth phone agents (1)
            "phone-num" to
                AgentConfig(
                    "Phone Number", AGENT_PHONE_NUM, PROCESS_BLUETOOTH_PHONE,
                ),
            // System service agents (3)
            "app-multi-display" to
                AgentConfig(
                    "App Multi Display", AGENT_SYSTEM_MULTI_DISPLAY, PROCESS_SYSTEM_SERVICE,
                ),
            "voboost-to-menu" to
                AgentConfig(
                    "Voboost to Menu", AGENT_SYSTEM_TO_MENU, PROCESS_SYSTEM_SERVICE,
                ),
            "forced-ev" to
                AgentConfig(
                    "Forced EV", AGENT_SYSTEM_FORCED_EV, PROCESS_SYSTEM_SERVICE,
                ),
            // QGIME agents (2)
            "keyboard-ru" to
                AgentConfig(
                    "Keyboard RU", AGENT_QGIME_KEYBOARD_RU, PROCESS_QGIME,
                ),
            "keyboard-lock" to
                AgentConfig(
                    "Keyboard Lock", AGENT_QGIME_KEYBOARD_LOCK, PROCESS_QGIME,
                ),
            // Vehicle setting agents (1)
            "media-source" to
                AgentConfig(
                    "Media Source", AGENT_VEHICLE_MEDIA, PROCESS_VEHICLE_SETTING,
                ),
        )

    // Process groups
    private val processAgents =
        mapOf(
            PROCESS_LAUNCHER to
                listOf(
                    "weather-widget", "app-launcher", "navbar-launcher", "app-viewport",
                ),
            PROCESS_BLUETOOTH_PHONE to listOf("phone-num"),
            PROCESS_SYSTEM_SERVICE to
                listOf(
                    "app-multi-display", "voboost-to-menu", "forced-ev",
                ),
            PROCESS_QGIME to listOf("keyboard-ru", "keyboard-lock"),
            PROCESS_VEHICLE_SETTING to listOf("media-source"),
        )

    /**
     * Inject a specific agent by its key
     */
    fun injectAgent(
        agentKey: String,
        params: JSONObject? = null,
    ): Result<String> {
        val agent =
            allAgents[agentKey]
                ?: return Result.failure(IllegalArgumentException("Unknown agent: $agentKey"))
        val finalParams = params ?: agent.defaultParams
        return fridaManager.injectScript(
            agent.targetProcess,
            agent.scriptPath,
            finalParams,
        )
    }

    /**
     * Inject all agents for a specific process
     */
    fun injectAllAgentsForProcess(
        processName: String,
        paramsMap: Map<String, JSONObject>? = null,
    ): Result<List<String>> {
        return try {
            val agentKeys =
                processAgents[processName] ?: return Result.success(emptyList())
            val injectionIds = mutableListOf<String>()
            agentKeys.forEach { agentKey ->
                val params = paramsMap?.get(agentKey)
                injectAgent(agentKey, params).onSuccess { id ->
                    injectionIds.add(id)
                }
            }
            Result.success(injectionIds)
        } catch (e: Exception) {
            Logger.error("FridaAgentManager", "Failed to inject agents for process: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Inject all available agents
     */
    fun injectAllAgents(paramsMap: Map<String, JSONObject>? = null): Result<Map<String, String>> {
        return try {
            val injectionIds = mutableMapOf<String, String>()
            allAgents.forEach { (key, agent) ->
                val params = paramsMap?.get(key) ?: agent.defaultParams
                fridaManager.injectScript(agent.targetProcess, agent.scriptPath, params)
                    .onSuccess { id ->
                        injectionIds[key] = id
                    }
                    .onFailure { e ->
                        Logger.error(
                            "FridaAgentManager",
                            "Failed to inject agent $key: ${e.message}",
                        )
                    }
            }
            Result.success(injectionIds)
        } catch (e: Exception) {
            Logger.error("FridaAgentManager", "Failed to inject all agents: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Stop all agents for a specific process
     */
    fun stopAgentsForProcess(processName: String): Result<Unit> {
        return fridaManager.stopInjectionsForTarget(processName)
    }

    /**
     * Get all available agent configurations
     */
    fun getAllAgents(): Map<String, AgentConfig> = allAgents

    /**
     * Get agents for a specific process
     */
    fun getAgentsForProcess(processName: String): List<AgentConfig> {
        val agentKeys = processAgents[processName] ?: return emptyList()
        return agentKeys.mapNotNull { allAgents[it] }
    }

    /**
     * Get all process names
     */
    fun getAllProcessNames(): Set<String> = processAgents.keys

    /**
     * Create default parameters for weather widget agent
     */
    fun createWeatherParams(
        apiKey: String,
        language: String = "en",
    ): JSONObject {
        return JSONObject().apply {
            put("apiKey", apiKey)
            put("language", language)
        }
    }

    /**
     * Create default parameters for keyboard agents
     */
    fun createKeyboardParams(layout: String = "qwerty"): JSONObject {
        return JSONObject().apply {
            put("layout", layout)
            put("autoCorrect", true)
        }
    }

    /**
     * Convenience method to inject launcher agents with common parameters
     */
    fun injectLauncherAgents(
        weatherApiKey: String? = null,
        enableAppLauncher: Boolean = true,
        enableNavbar: Boolean = true,
        enableViewport: Boolean = true,
    ): Result<Map<String, String>> {
        return try {
            val injectionIds = mutableMapOf<String, String>()
            val paramsMap = mutableMapOf<String, JSONObject?>()

            if (weatherApiKey != null) {
                paramsMap["weather-widget"] = createWeatherParams(weatherApiKey)
            }

            if (enableAppLauncher) {
                injectAgent("app-launcher").onSuccess { id ->
                    injectionIds["app-launcher"] = id
                }
            }

            if (enableNavbar) {
                injectAgent("navbar-launcher").onSuccess { id ->
                    injectionIds["navbar-launcher"] = id
                }
            }

            if (enableViewport) {
                injectAgent("app-viewport").onSuccess { id ->
                    injectionIds["app-viewport"] = id
                }
            }

            // Inject weather widget with parameters if API key provided
            if (weatherApiKey != null) {
                injectAgent("weather-widget", paramsMap["weather-widget"]).onSuccess { id ->
                    injectionIds["weather-widget"] = id
                }
            }

            Result.success(injectionIds)
        } catch (e: Exception) {
            Logger.error("FridaAgentManager", "Failed to inject launcher agents: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Convenience method to inject keyboard agents
     */
    fun injectKeyboardAgents(
        enableRussian: Boolean = true,
        enableLock: Boolean = true,
        layout: String = "qwerty",
    ): Result<Map<String, String>> {
        return try {
            val injectionIds = mutableMapOf<String, String>()
            val params = createKeyboardParams(layout)

            if (enableRussian) {
                injectAgent("keyboard-ru", params).onSuccess { id ->
                    injectionIds["keyboard-ru"] = id
                }
            }

            if (enableLock) {
                injectAgent("keyboard-lock", params).onSuccess { id ->
                    injectionIds["keyboard-lock"] = id
                }
            }

            Result.success(injectionIds)
        } catch (e: Exception) {
            Logger.error("FridaAgentManager", "Failed to inject keyboard agents: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get status of all agents
     */
    fun getAgentStatus(): Result<Map<String, Boolean>> {
        return try {
            val activeInjectionsResult = fridaManager.getActiveInjections()
            activeInjectionsResult.fold(
                onSuccess = { activeInjections ->
                    val status = mutableMapOf<String, Boolean>()
                    allAgents.forEach { (key, agent) ->
                        val isActive =
                            activeInjections.any { injection ->
                                injection.targetProcess == agent.targetProcess &&
                                    injection.scriptPath == agent.scriptPath
                            }
                        status[key] = isActive
                    }
                    Result.success(status)
                },
                onFailure = { e ->
                    Logger.error("FridaAgentManager", "Failed to get agent status: ${e.message}")
                    Result.failure(e)
                },
            )
        } catch (e: Exception) {
            Logger.error("FridaAgentManager", "Failed to get agent status: ${e.message}")
            Result.failure(e)
        }
    }
}
