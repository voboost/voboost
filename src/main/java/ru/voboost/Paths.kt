package ru.voboost

import android.content.Context
import java.io.File

/**
 * Android path resolution for daemon-contract architecture.
 *
 * All application writes are confined to the app zone `/data/user/0/ru.voboost`
 * (equivalent to `/data/data/ru.voboost`, one inode). The application never
 * writes to the root zone `/data/voboost`; root-zone provisioning is the
 * responsibility of the operator installer or test harness.
 *
 * ## Directory Structure (App Zone)
 * ```
 * /data/data/ru.voboost/
 * ├── config.yaml              # Application configuration
 * ├── inject.json              # Plan produced by app, read by daemon
 * ├── inject-status.json       # Status written by daemon, read by app
 * ├── logs/                    # Application log files
 * │   └── voboost-YYYY-MM-DD.log
 * └── staging/                 # Staging area for OTA updates
 * ```
 *
 * ## Directory Structure (Root Zone - NOT WRITTEN BY APP)
 * ```
 * /data/voboost/               # Root zone (provisioned by harness/installer)
 * ├── agents/                  # Agent JavaScript files
 * ├── manifest.json            # Agent manifest
 * ├── manifest.sig             # Manifest signature
 * ├── logs/                    # Daemon logs
 * └── run/                     # Daemon runtime files
 * ```
 *
 * Production code constructs this with an Android [Context]; unit tests use the
 * internal [Paths] constructor with an explicit base directory to avoid
 * needing a Context.
 *
 * @param appZone App zone base directory all paths resolve from
 */
class Paths internal constructor(
    /**
     * App zone base directory.
     * /data/user/0/ru.voboost (equivalent to /data/data/ru.voboost)
     */
    val appZone: File,
) {
    /**
     * Production constructor: resolve the app zone from the Android [Context].
     *
     * @param context Android application context
     */
    constructor(context: Context) : this(context.dataDir)

    /**
     * Path to the plan file produced by the app.
     * /data/data/ru.voboost/inject.json
     */
    val injectJson: File
        get() = File(appZone, "inject.json")

    /**
     * Path to the status file written by the daemon.
     * /data/data/ru.voboost/inject-status.json
     */
    val injectStatusJson: File
        get() = File(appZone, "inject-status.json")

    /**
     * Staging directory for OTA updates.
     * /data/data/ru.voboost/staging/
     */
    val stagingDir: File
        get() = File(appZone, "staging").also { it.mkdirs() }

    /**
     * Application configuration file.
     * /data/data/ru.voboost/config.yaml
     */
    val configFile: File
        get() = File(appZone, "config.yaml")

    /**
     * Application log files directory.
     * /data/data/ru.voboost/logs/
     */
    val logsDir: File
        get() = File(appZone, "logs").also { it.mkdirs() }
}
