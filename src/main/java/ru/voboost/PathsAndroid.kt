package ru.voboost

import android.content.Context
import java.io.File

/**
 * Android-specific path resolution.
 * Uses Context.filesDir as base directory for all Voboost data.
 */
class PathsAndroid(private val context: Context) : Paths {
    override val dataDirectory: File
        get() = context.filesDir

    override val logDirectory: File
        get() = File(dataDirectory, "logs").also { it.mkdirs() }

    override val scriptsDirectory: File
        get() = File(dataDirectory, "scripts").also { it.mkdirs() }

    override val fridaExecutable: File
        get() = File(dataDirectory, "frida/frida-inject")

    override val configFile: File
        get() = File(dataDirectory, "config.yaml")
}
