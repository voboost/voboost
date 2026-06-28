package ru.voboost

import android.content.Context
import java.io.File

/**
 * Path resolution interface for daemon-contract architecture.
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
 * ├── staging/                 # Staging area for OTA updates
 * └── scripts/                 # Carrier assets (scripts, not used by app)
 *     ├── script_3debug.js
 *     └── ...
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
 */
interface Paths {
    /**
     * App zone base directory.
     * /data/user/0/ru.voboost (equivalent to /data/data/ru.voboost)
     */
    val appZone: File

    /**
     * Path to the plan file produced by the app.
     * /data/data/ru.voboost/inject.json
     */
    val injectJson: File

    /**
     * Path to the status file written by the daemon.
     * /data/data/ru.voboost/inject-status.json
     */
    val injectStatusJson: File

    /**
     * Staging directory for OTA updates.
     * /data/data/ru.voboost/staging/
     */
    val stagingDir: File

    /**
     * Application configuration file.
     * /data/data/ru.voboost/config.yaml
     */
    val configFile: File

    /**
     * Application log files directory.
     * /data/data/ru.voboost/logs/
     */
    val logsDir: File

    /**
     * Scripts directory (carrier assets, not used by app directly).
     * /data/data/ru.voboost/scripts/
     *
     * NOTE: This is for carrier assets only. The app does not use these
     * scripts directly; they are provided for the daemon/harness.
     */
    val scriptsDirectory: File
}

/**
 * Android-specific path resolution for daemon-contract architecture.
 *
 * @param context Android application context
 */
class PathsAndroid(private val context: Context) : Paths {
    companion object {
        private const val LOG = "PathsAndroid"
    }

    /**
     * App zone base directory.
     * /data/user/0/ru.voboost (equivalent to /data/data/ru.voboost)
     */
    override val appZone: File
        get() = context.dataDir

    /**
     * Path to the plan file produced by the app.
     * /data/data/ru.voboost/inject.json
     */
    override val injectJson: File
        get() = File(appZone, "inject.json")

    /**
     * Path to the status file written by the daemon.
     * /data/data/ru.voboost/inject-status.json
     */
    override val injectStatusJson: File
        get() = File(appZone, "inject-status.json")

    /**
     * Staging directory for OTA updates.
     * /data/data/ru.voboost/staging/
     */
    override val stagingDir: File
        get() = File(appZone, "staging").also { it.mkdirs() }

    /**
     * Application configuration file.
     * /data/data/ru.voboost/config.yaml
     */
    override val configFile: File
        get() = File(appZone, "config.yaml")

    /**
     * Application log files directory.
     * /data/data/ru.voboost/logs/
     */
    override val logsDir: File
        get() = File(appZone, "logs").also { it.mkdirs() }

    /**
     * Scripts directory (carrier assets, not used by app directly).
     * /data/data/ru.voboost/scripts/
     *
     * NOTE: This is for carrier assets only. The app does not use these
     * scripts directly; they are provided for the daemon/harness.
     */
    override val scriptsDirectory: File
        get() = File(appZone, "scripts").also { it.mkdirs() }
}
