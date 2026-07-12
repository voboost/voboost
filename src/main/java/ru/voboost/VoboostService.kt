package ru.voboost

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.voboost.config.models.StartupMode
import ru.voboost.feature.FeatureContext
import ru.voboost.ota.OtaClient
import ru.voboost.ota.OtaConfig
import ru.voboost.ota.OtaVersion
import java.io.File

/**
 * Voboost foreground service that handles startup logic.
 *
 * This service is started on device boot and manages the application lifecycle
 * based on the configured startup mode:
 * - `off`: Stop service immediately
 * - `hidden`: Start features without UI integration
 * - `interface`: Start features and integrate into vehicle menu
 *
 * Running as a foreground service prevents the system from killing it.
 *
 * In daemon-contract architecture, the service produces inject.json plan
 * instead of performing direct injection.
 */
class VoboostService : Service() {
    companion object {
        private const val LOG = "VoboostService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "VoboostServiceChannel"
        private const val CHANNEL_NAME = "Voboost Service"
        private const val OTA_CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L // 12 hours

        // Delay before re-checking OTA readiness when config or daemon
        // status is not yet available. The first periodic check used to run
        // before config was loaded and before the daemon had written
        // inject-status.json, so it would no-op or fail on every boot until
        // both were ready (R4-VBS-02). Gate the check and back off briefly
        // instead of hammering the cycle.
        private const val OTA_NOT_READY_DELAY_MS = 30 * 1000L // 30 seconds
    }

    private lateinit var paths: PathsAndroid
    private lateinit var vehicleManager: VehicleManagerAndroid
    private lateinit var planProducer: PlanProducer
    private lateinit var statusReader: StatusReader
    private lateinit var featureManager: FeatureManager
    private lateinit var configManager: ru.voboost.config.ConfigManager
    private var config: ru.voboost.config.models.Config? = null

    // OTA client
    private var otaClient: OtaClient? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Logger.init(this, level = "debug")
        Logger.info(LOG, "Service created")

        // Create notification channel for foreground service
        createNotificationChannel()

        // Initialize paths
        paths = PathsAndroid(applicationContext)

        // Initialize config manager
        configManager = ru.voboost.config.ConfigManager(applicationContext)

        // Initialize managers
        vehicleManager = VehicleManagerAndroid(applicationContext)
        planProducer = PlanProducer(paths)
        statusReader = StatusReader(paths)
        featureManager = FeatureManager()

        // NOTE: OTA client initialization is deferred to onStartCommand(),
        // after configManager.loadConfig() has completed. Running it here in
        // onCreate() caused the first periodic check to fire before config
        // was loaded, so it was skipped ("config not ready") and delayed 30s
        // (M1). Initializing after config load lets the first check run
        // immediately on the normal cycle.

        // Log vehicle info
        vehicleManager.getVehicleInfo().onSuccess { info ->
            Logger.info(
                LOG,
                "Vehicle: ${info.model} (${info.year}), Firmware: ${info.firmware}",
            )
        }.onFailure { error ->
            Logger.debug(LOG, "Could not get vehicle info: ${error.message}")
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        Logger.info(LOG, "Service started")

        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification())

        // Read configuration and check startup mode
        val startupMode = readStartupMode()
        Logger.info(LOG, "Startup mode: $startupMode")

        // Load config for feature context
        val configResult = configManager.loadConfig()
        if (configResult.isFailure) {
            Logger.error(LOG, "Failed to load config: ${configResult.exceptionOrNull()?.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        config = configResult.getOrThrow()

        // Initialize the OTA client now that config is loaded, so the first
        // periodic check runs against a ready config instead of being
        // skipped and delayed 30s (M1). Guarded against re-init on repeated
        // onStartCommand() invocations.
        if (otaClient == null) {
            initializeOtaClient()
        }

        when (startupMode) {
            StartupMode.off -> {
                Logger.info(LOG, "Startup mode is 'off', stopping service")
                stopSelf()
                return START_NOT_STICKY
            }
            StartupMode.hidden -> {
                Logger.info(LOG, "Startup mode is 'hidden', starting features without UI")
                startFeatures()
            }
            StartupMode.`interface` -> {
                Logger.info(
                    LOG,
                    "Startup mode is 'interface', starting features with UI integration",
                )
                startFeatures()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Logger.info(LOG, "Service destroyed")
        // Cancel the service coroutine scope so the periodic OTA check
        // coroutine stops running instead of leaking past the service lifetime.
        serviceScope.cancel()
        stopFeatures()
        Logger.shutdown()
        super.onDestroy()
    }

    /**
     * Initialize OTA client with configuration.
     */
    private fun initializeOtaClient() {
        try {
            // OTA manifest URL - configurable via BuildConfig. Full URL to the
            // unified manifest.json (GitHub raw for production, file:// for
            // local emulator testing). The signature URL is derived by
            // replacing the .json suffix with .sig.
            val manifestUrl = BuildConfig.OTA_MANIFEST_URL

            if (manifestUrl.isEmpty() || manifestUrl == "TODO_SET_PRODUCTION_URL") {
                Logger.debug(
                    LOG,
                    "OTA manifest URL not configured, skipping OTA client initialization",
                )
                return
            }

            // Release track (dev/testing/production). Read from the on-device
            // config.yaml `ota.track` key (default production). The emulator
            // test overrides this to "testing" via the config file on device.
            val track = readOtaTrack()

            // Public key file for manifest verification
            val publicKeyFile = File(filesDir, "config/release-public.pem")
            if (!publicKeyFile.exists()) {
                Logger.debug(LOG, "Public key file not found: ${publicKeyFile.absolutePath}")
                return
            }

            // APK-level OTA config: app version from BuildConfig.VERSION_NAME,
            // daemon version read from inject-status.json's `daemon` field.
            val otaConfig =
                OtaConfig(
                    manifestUrl = manifestUrl,
                    track = track,
                    publicKeyFile = publicKeyFile,
                    currentAppVersion = BuildConfig.VERSION_NAME,
                    daemonVersionReader = {
                        OtaVersion.extractDaemonVersion(statusReader.read()?.daemon)
                    },
                    context = applicationContext,
                )

            otaClient = OtaClient(paths, otaConfig)
            Logger.info(LOG, "OTA client initialized (manifest=$manifestUrl, track=$track)")

            // Start periodic OTA checks
            startPeriodicOtaChecks()
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to initialize OTA client: ${e.message}")
        }
    }

    /**
     * Read the `ota.track` setting from the on-device config.yaml.
     *
     * The config is flat kebab-case YAML (e.g. `ota.track: testing`). Returns
     * the configured track, or [OtaConfig.DEFAULT_TRACK] ("production") when
     * the key is absent or the file cannot be read. Read directly from the
     * config file rather than the typed [ru.voboost.config.models.Config]
     * model so the `ota.track` key does not require a change to the
     * voboost-config library.
     */
    private fun readOtaTrack(): String {
        return try {
            val configFile = paths.configFile
            if (!configFile.exists()) return OtaConfig.DEFAULT_TRACK
            val line = configFile.readLines().find { it.startsWith("ota.track:") }
            val value = line?.substringAfter("ota.track:")?.trim()
            if (value.isNullOrEmpty()) OtaConfig.DEFAULT_TRACK else value
        } catch (e: Exception) {
            Logger.debug(LOG, "Failed to read ota.track, defaulting to production: ${e.message}")
            OtaConfig.DEFAULT_TRACK
        }
    }

    /**
     * Start periodic OTA update checks.
     */
    private fun startPeriodicOtaChecks() {
        serviceScope.launch {
            while (true) {
                var delayMs = OTA_CHECK_INTERVAL_MS
                try {
                    delayMs = checkOtaUpdates()
                } catch (e: Exception) {
                    Logger.error(LOG, "OTA check failed: ${e.message}")
                }

                delay(delayMs)
            }
        }
    }

    /**
     * Check for OTA updates and stage if available.
     *
     * Returns the delay (ms) the caller should wait before the next cycle.
     * Returns [OTA_NOT_READY_DELAY_MS] when config or daemon status is not
     * yet available, so the first check does not run before the config is
     * loaded and the daemon has written inject-status.json (R4-VBS-02).
     * Otherwise returns [OTA_CHECK_INTERVAL_MS].
     */
    private fun checkOtaUpdates(): Long {
        val client =
            otaClient ?: run {
                Logger.debug(LOG, "OTA client not initialized")
                return OTA_CHECK_INTERVAL_MS
            }

        // Gate: do not run the OTA cycle until config is loaded and the
        // daemon has published a status file. The first periodic check used
        // to fire before either was ready (R4-VBS-02), no-op'ing or failing
        // on every boot until they became available. Back off briefly and
        // re-check instead of consuming a full 12h interval on a no-op.
        if (configManager.getConfig() == null) {
            Logger.debug(LOG, "OTA check skipped: config not ready")
            return OTA_NOT_READY_DELAY_MS
        }
        if (statusReader.read() == null) {
            Logger.debug(LOG, "OTA check skipped: daemon status not ready")
            return OTA_NOT_READY_DELAY_MS
        }

        try {
            Logger.info(LOG, "Checking for OTA updates...")

            // checkAndUpdate() fetches+verifies the manifest once and
            // short-circuits (returns false, no download/staging) when no
            // channel has a newer APK. Calling checkForUpdates() first would
            // perform a second manifest fetch+verify per cycle, so we rely on
            // checkAndUpdate() alone.
            val staged = client.checkAndUpdate()

            if (staged) {
                Logger.info(LOG, "OTA updates staged successfully")
            } else {
                Logger.debug(LOG, "No OTA updates available")
            }
        } catch (e: Exception) {
            Logger.error(LOG, "OTA update check failed: ${e.message}")
            // Don't crash the service on OTA errors
        }
        return OTA_CHECK_INTERVAL_MS
    }

    /**
     * Read the startup mode from configuration.
     */
    private fun readStartupMode(): StartupMode {
        return try {
            configManager.copyDefaultConfigIfNeeded()
            val configResult = configManager.loadConfig()

            configResult.getOrNull()?.settingsStartup ?: StartupMode.`interface`
        } catch (e: Exception) {
            Logger.error(
                LOG,
                "Failed to read startup mode: ${e.message}, defaulting to 'interface'",
            )
            StartupMode.`interface`
        }
    }

    /**
     * Start all features based on configuration.
     */
    private fun startFeatures() {
        val currentConfig =
            config ?: run {
                Logger.error(LOG, "Config not initialized, cannot start features")
                return
            }

        val context =
            FeatureContext(
                androidContext = applicationContext,
                vehicleManager = vehicleManager,
                config = currentConfig,
                paths = paths,
            )
        featureManager.applyConfig(context, planProducer)
    }

    /**
     * Stop all active features.
     */
    private fun stopFeatures() {
        val currentConfig =
            config ?: run {
                Logger.debug(LOG, "Config not initialized, skipping feature stop")
                return
            }

        val context =
            FeatureContext(
                androidContext = applicationContext,
                vehicleManager = vehicleManager,
                config = currentConfig,
                paths = paths,
            )
        featureManager.stopAll(context, planProducer)
    }

    /**
     * Create notification channel for foreground service (required for API 26+).
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Voboost background service"
                }

            val notificationManager =
                getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create notification for foreground service.
     */
    private fun createNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Voboost")
                .setContentText("Running in background")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Voboost")
                .setContentText("Running in background")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .build()
        }
    }
}
