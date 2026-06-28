package ru.voboost

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Centralized logger for Voboost system.
 * Called by Frida scripts via Java.use("ru.voboost.Logger").
 *
 * Thread-safe: uses SingleThreadExecutor to serialize all writes.
 * Rotation: old logs cleaned on init().
 *
 * Log files are stored in Context.dataDir/logs/ (e.g., /data/data/ru.voboost/logs/)
 */
class Logger private constructor(
    private val logDirectory: File,
    private var currentLevel: Level = Level.INFO,
    private val retentionDays: Int = 7,
    private val consoleOutput: Boolean = false,
) {
    /**
     * Log levels with priority ordering.
     * Lower priority = more important (always logged).
     * Tags match Frida console output convention: [-] error, [+] info, [*] debug
     */
    enum class Level(val priority: Int, val tag: String) {
        NONE(0, ""),
        ERROR(1, "[-]"),
        INFO(2, "[+]"),
        DEBUG(3, "[*]"),
    }

    // State for this instance
    private var initialized = false

    // Thread-safe executor for serialized writes
    private val executor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "Logger").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        }

    // Date formatters (created per-use for thread safety)
    private fun dateFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun timestampFormat() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /**
     * Initialize the logger instance. Must be called before logging.
     * Performs cleanup of old log files.
     */
    fun initialize() {
        if (initialized) return

        // Create directory if needed
        val dir = logDirectory
        if (!dir.exists()) {
            dir.mkdirs()
        }

        // Mark as initialized before cleanup so cleanup can log
        initialized = true

        // Clean old logs on startup
        cleanOldLogs()

        logError(
            "Logger",
            "Logger initialized: level=${currentLevel.name.lowercase()}, " +
                "retention=${retentionDays}d",
        )
    }

    /**
     * Log an error message.
     * @param source Script or component name
     * @param message Log message
     */
    fun logError(
        source: String,
        message: String,
    ) {
        log(Level.ERROR, source, message)
    }

    /**
     * Log an info message.
     * @param source Script or component name
     * @param message Log message
     */
    fun logInfo(
        source: String,
        message: String,
    ) {
        log(Level.INFO, source, message)
    }

    /**
     * Log a debug message.
     * @param source Script or component name
     * @param message Log message
     */
    fun logDebug(
        source: String,
        message: String,
    ) {
        log(Level.DEBUG, source, message)
    }

    /**
     * Log raw output without timestamp or formatting.
     * Used for capturing Frida console output as-is (already formatted with timestamp from JS).
     * @param line Raw log line
     */
    fun logRaw(line: String) {
        if (!initialized) return

        // Async write to avoid blocking caller
        // Reuses existing writeToFile method - no code duplication
        executor.execute {
            writeToFile(line)
        }
    }

    /**
     * Set the current log level at runtime.
     * @param level Log level: "none", "error", "info", "debug"
     */
    fun setLogLevel(level: String) {
        currentLevel =
            try {
                Level.valueOf(level.uppercase())
            } catch (e: IllegalArgumentException) {
                Level.INFO
            }
    }

    /**
     * Get current log level as string.
     */
    fun getLogLevel(): String = currentLevel.name.lowercase()

    /**
     * Flush pending writes and shutdown executor.
     * Call on app termination for clean shutdown.
     */
    internal fun shutdown() {
        executor.shutdown()
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun log(
        level: Level,
        source: String,
        message: String,
    ) {
        // Skip if logging disabled or level too verbose
        if (currentLevel == Level.NONE) return
        if (level.priority > currentLevel.priority) return
        if (!initialized) return

        // Capture timestamp immediately (before async execution)
        val timestamp = timestampFormat().format(Date())
        val logLine = "$timestamp ${level.tag} $source: $message"

        // Print to console if enabled
        if (consoleOutput) {
            println(logLine)
        }

        // Async write to avoid blocking caller
        executor.execute {
            writeToFile(logLine)
        }
    }

    private fun writeToFile(logLine: String) {
        try {
            val today = dateFormat().format(Date())
            val logFile = File(logDirectory, "voboost-$today.log")

            FileWriter(logFile, true).use { writer ->
                writer.write(logLine)
                writer.write("\n")
            }
        } catch (e: Exception) {
            // Silent failure - can't log logging errors
            // Could fallback to Android Log.e() if needed
        }
    }

    private fun cleanOldLogs() {
        try {
            val dir = logDirectory
            if (!dir.exists()) return

            val cutoffDate =
                Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -retentionDays)
                }.time

            val df = dateFormat()
            var deletedCount = 0

            dir.listFiles()?.forEach { file ->
                if (file.name.startsWith("voboost-") && file.name.endsWith(".log")) {
                    val dateStr =
                        file.name
                            .removePrefix("voboost-")
                            .removeSuffix(".log")
                    try {
                        val fileDate = df.parse(dateStr)
                        if (fileDate != null && fileDate.before(cutoffDate)) {
                            if (file.delete()) {
                                deletedCount++
                            }
                        }
                    } catch (e: Exception) {
                        // Skip files with invalid date format
                    }
                }
            }

            if (deletedCount > 0) {
                logDebug("Logger", "Cleaned up $deletedCount old log file(s)")
            }
        } catch (e: Exception) {
            logError("Logger", "Failed to clean old logs: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════
    // COMPANION OBJECT - provides static-like API for production
    // ═══════════════════════════════════════════════════════════
    companion object {
        @Volatile
        private var instance: Logger? = null

        /**
         * Factory method for tests - creates NEW independent instance
         */
        @JvmStatic
        fun create(
            logDirectory: File,
            level: String = "info",
            retentionDays: Int = 7,
            console: Boolean = false,
        ): Logger {
            val logger =
                Logger(
                    logDirectory = logDirectory,
                    currentLevel =
                        try {
                            Level.valueOf(level.uppercase())
                        } catch (e: IllegalArgumentException) {
                            Level.INFO
                        },
                    retentionDays = retentionDays,
                    consoleOutput = console,
                )
            logger.initialize()
            return logger
        }

        /**
         * Initialize the global logger. Must be called before logging.
         * Performs cleanup of old log files.
         *
         * @param context Android context to get filesDir
         * @param level Log level: "none", "error", "info", "debug"
         * @param retentionDays Days to keep old logs (default: 7)
         */
        @JvmStatic
        @Synchronized
        fun init(
            context: Context,
            level: String = "info",
            retentionDays: Int = 7,
            console: Boolean = false,
        ) {
            if (instance != null) return

            instance =
                Logger(
                    logDirectory = File(context.dataDir, "logs"),
                    currentLevel =
                        try {
                            Level.valueOf(level.uppercase())
                        } catch (e: IllegalArgumentException) {
                            Level.INFO
                        },
                    retentionDays = retentionDays,
                    consoleOutput = console,
                )
            instance?.initialize()
        }

        /**
         * Initialize logger with explicit directory.
         * Works on both Android and Desktop.
         *
         * @param logDirectory Directory for log files
         * @param level Log level: "none", "error", "info", "debug"
         * @param retentionDays Days to keep old logs (default: 7)
         */
        @JvmStatic
        @Synchronized
        fun init(
            logDirectory: File,
            level: String = "info",
            retentionDays: Int = 7,
            console: Boolean = false,
        ) {
            if (instance != null) return
            instance =
                Logger(
                    logDirectory = logDirectory,
                    currentLevel =
                        try {
                            Level.valueOf(level.uppercase())
                        } catch (e: IllegalArgumentException) {
                            Level.INFO
                        },
                    retentionDays = retentionDays,
                    consoleOutput = console,
                )
            instance?.initialize()
        }

        /**
         * Log an error message.
         * @param source Script or component name
         * @param message Log message
         */
        @JvmStatic
        fun error(
            source: String,
            message: String,
        ) {
            instance?.logError(source, message)
        }

        /**
         * Log an info message.
         * @param source Script or component name
         * @param message Log message
         */
        @JvmStatic
        fun info(
            source: String,
            message: String,
        ) {
            instance?.logInfo(source, message)
        }

        /**
         * Log a debug message.
         * @param source Script or component name
         * @param message Log message
         */
        @JvmStatic
        fun debug(
            source: String,
            message: String,
        ) {
            instance?.logDebug(source, message)
        }

        /**
         * Set the current log level at runtime.
         * @param level Log level: "none", "error", "info", "debug"
         */
        @JvmStatic
        fun setLevel(level: String) {
            instance?.setLogLevel(level)
        }

        /**
         * Get current log level as string.
         */
        @JvmStatic
        fun getLevel(): String = instance?.getLogLevel() ?: "info"

        /**
         * Log raw output without timestamp or formatting.
         * Used for capturing Frida console output as-is.
         * @param line Raw log line (already formatted with timestamp from JS)
         */
        @JvmStatic
        fun raw(line: String) {
            instance?.logRaw(line)
        }

        /**
         * Flush pending writes and shutdown executor.
         * Call on app termination for clean shutdown.
         */
        @JvmStatic
        fun shutdown() {
            instance?.shutdown()
            instance = null
        }
    }
}
