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
 * Android-specific Frida injection manager using frida-inject binary.
 * Manages script injection into target processes and tracks active injections.
 */
class FridaManagerAndroid(private val paths: Paths) : FridaManager {
    companion object {
        private const val LOG = "FridaManagerAndroid"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val injections = ConcurrentHashMap<String, InjectionInfo>()
    private val processes = ConcurrentHashMap<String, Process>()

    private data class InjectionInfo(
        val id: String,
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

            val command =
                buildList {
                    add(paths.fridaExecutable.absolutePath)
                    add("-n")
                    add(targetProcess)
                    add("-s")
                    add(scriptPath)
                    params?.let {
                        add("--parameters")
                        add(it.toString())
                    }
                }

            Logger.info(LOG, "Injecting script: $scriptPath -> $targetProcess")

            val process = Runtime.getRuntime().exec(command.toTypedArray())
            processes[id] = process

            val job =
                scope.launch {
                    launch { captureOutput(process.inputStream, "stdout") }
                    launch { captureOutput(process.errorStream, "stderr") }
                    launch {
                        val exitCode = process.waitFor()
                        injections.remove(id)
                        processes.remove(id)
                        Logger.info(LOG, "Process exited: $scriptPath (code: $exitCode)")
                    }
                }

            val info =
                InjectionInfo(
                    id = id,
                    targetProcess = targetProcess,
                    scriptPath = scriptPath,
                    process = process,
                    job = job,
                )
            injections[id] = info

            Logger.info(LOG, "Injection started: $id")
            Result.success(id)
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to inject script: ${e.message}")
            Result.failure(e)
        }
    }

    override fun stopInjection(injectionId: String): Result<Unit> {
        return try {
            val info = injections.remove(injectionId)
            if (info != null) {
                info.process.destroy()
                info.job.cancel()
                processes.remove(injectionId)
                Logger.info(LOG, "Stopped injection: $injectionId")
                Result.success(Unit)
            } else {
                Result.failure(IllegalArgumentException("Injection not found: $injectionId"))
            }
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to stop injection: ${e.message}")
            Result.failure(e)
        }
    }

    override fun stopInjectionsForTarget(targetProcess: String): Result<Unit> {
        return try {
            val toStop = injections.values.filter { it.targetProcess == targetProcess }
            toStop.forEach { info ->
                stopInjection(info.id)
            }
            Logger.info(LOG, "Stopped ${toStop.size} injection(s) for: $targetProcess")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to stop injections for target: ${e.message}")
            Result.failure(e)
        }
    }

    override fun getActiveInjections(): Result<List<FridaManager.InjectionInfo>> {
        return try {
            val activeList =
                injections.values.map {
                    FridaManager.InjectionInfo(
                        id = it.id,
                        targetProcess = it.targetProcess,
                        scriptPath = it.scriptPath,
                    )
                }
            Result.success(activeList)
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to get active injections: ${e.message}")
            Result.failure(e)
        }
    }

    override fun shutdown(): Result<Unit> {
        return try {
            Logger.info(LOG, "Shutting down (${injections.size} active injections)")
            injections.values.forEach { info ->
                info.process.destroy()
                info.job.cancel()
            }
            injections.clear()
            processes.clear()
            scope.cancel()
            Logger.info(LOG, "Shutdown complete")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to shutdown: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun captureOutput(
        stream: java.io.InputStream,
        streamName: String,
    ) {
        try {
            BufferedReader(InputStreamReader(stream)).use { reader ->
                reader.lineSequence().forEach { line ->
                    Logger.raw(line)
                }
            }
        } catch (e: Exception) {
            Logger.error(LOG, "Error capturing $streamName: ${e.message}")
        }
    }
}
