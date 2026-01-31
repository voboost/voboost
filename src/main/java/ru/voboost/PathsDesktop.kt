package ru.voboost

import java.io.File

/**
 * Desktop-specific path resolution.
 * Uses ~/.voboost as base directory.
 * Scripts are read from voboost-script/build directory.
 */
class PathsDesktop(
    baseDir: File = File(System.getProperty("user.home"), ".voboost"),
    private val scriptsDir: File = File("../voboost-script/build").absoluteFile,
) : Paths {
    override val dataDirectory: File = baseDir.also { it.mkdirs() }

    override val logDirectory: File
        get() = File(dataDirectory, "logs").also { it.mkdirs() }

    override val scriptsDirectory: File
        get() = scriptsDir

    override val fridaExecutable: File
        get() = File("/usr/local/bin/frida")

    override val configFile: File
        get() = File("../voboost-config/src/config.yaml").absoluteFile
}
