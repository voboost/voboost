package ru.voboost

import java.io.File

/**
 * Platform-specific path resolution.
 * Implementations provide paths for Android and Desktop environments.
 */
interface Paths {
    /** Base directory for all voboost data */
    val dataDirectory: File

    /** Directory for log files */
    val logDirectory: File

    /** Directory containing Frida scripts */
    val scriptsDirectory: File

    /** Frida executable file */
    val fridaExecutable: File

    /** Configuration file */
    val configFile: File
}
