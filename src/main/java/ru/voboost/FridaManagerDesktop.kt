package ru.voboost

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Desktop-specific Frida injection using frida CLI.
 * Finds Java processes by class name and injects by PID.
 */
class FridaManagerDesktop : FridaManager {
    companion object {
        private const val LOG = "FridaManagerDesktop"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val injections = ConcurrentHashMap<String, InternalInjectionInfo>()

    private data class InternalInjectionInfo(
        val targetProcess: String,
        val scriptPath: String,
        val process: Process,
        val job: Job,
    )

    override fun injectScript(
        targetProcess: String,
        scriptPath: String,
        params: JSONObject?,
    ): Result<String> {
        return try {
            val id = UUID.randomUUID().toString()
            val pidResult = findProcessPid(targetProcess)

            pidResult.fold(
                onSuccess = { pid ->
                    val command =
                        buildList {
                            add("frida")
                            add("-p")
                            add(pid.toString())
                            add("-l")
                            add(scriptPath)
                            params?.let {
                                add("--parameters")
                                add(it.toString())
                            }
                        }

                    Logger.info(LOG, "Injecting: $scriptPath -> $targetProcess (PID: $pid)")

                    val process =
                        ProcessBuilder(command)
                            .redirectErrorStream(true)
                            .start()

                    val job =
                        scope.launch {
                            launch { captureOutput(process.inputStream) }
                            launch {
                                val exitCode = process.waitFor()
                                injections.remove(id)
                                Logger.info(LOG, "Exited: $scriptPath (code: $exitCode)")
                            }
                        }

                    injections[id] = InternalInjectionInfo(targetProcess, scriptPath, process, job)
                    Result.success(id)
                },
                onFailure = { error ->
                    Result.failure(error)
                },
            )
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to inject script: ${e.message}")
            Result.failure(e)
        }
    }

    override fun stopInjection(injectionId: String): Result<Unit> {
        return try {
            injections.remove(injectionId)?.let { info ->
                info.process.destroy()
                info.job.cancel()
                Result.success(Unit)
            } ?: Result.failure(Exception("Injection not found: $injectionId"))
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to stop injection: ${e.message}")
            Result.failure(e)
        }
    }

    override fun stopInjectionsForTarget(targetProcess: String): Result<Unit> {
        return try {
            val toRemove = injections.filter { it.value.targetProcess == targetProcess }
            toRemove.forEach { (id, info) ->
                info.process.destroy()
                info.job.cancel()
                injections.remove(id)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to stop injections for target: ${e.message}")
            Result.failure(e)
        }
    }

    override fun getActiveInjections(): Result<List<FridaManager.InjectionInfo>> {
        return try {
            val activeInjections =
                injections.map { (id, info) ->
                    FridaManager.InjectionInfo(
                        id = id,
                        targetProcess = info.targetProcess,
                        scriptPath = info.scriptPath,
                    )
                }
            Result.success(activeInjections)
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to get active injections: ${e.message}")
            Result.failure(e)
        }
    }

    override fun shutdown(): Result<Unit> {
        return try {
            injections.values.forEach {
                it.process.destroy()
                it.job.cancel()
            }
            injections.clear()
            scope.cancel()
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to shutdown: ${e.message}")
            Result.failure(e)
        }
    }

    private fun findProcessPid(targetProcess: String): Result<Int> {
        return try {
            val result =
                ProcessBuilder("pgrep", "-f", targetProcess)
                    .start()
                    .inputStream.bufferedReader().readText().trim()

            val pid =
                result.lines()
                    .firstOrNull { it.isNotBlank() }
                    ?.toIntOrNull()

            if (pid != null) {
                Result.success(pid)
            } else {
                Result.failure(
                    IllegalStateException(
                        "Process not found: $targetProcess\n" +
                            "Make sure the stub is running: java $targetProcess",
                    ),
                )
            }
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to find process: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun captureOutput(stream: java.io.InputStream) {
        try {
            BufferedReader(InputStreamReader(stream)).use { reader ->
                reader.lineSequence().forEach { line ->
                    Logger.raw(line)
                }
            }
        } catch (e: Exception) {
            Logger.error(LOG, "Error capturing output: ${e.message}")
        }
    }
}
