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

        // Initialize OTA client if configured
        initializeOtaClient()

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
        stopFeatures()
        Logger.shutdown()
        super.onDestroy()
    }

    /**
     * Initialize OTA client with configuration.
     */
    private fun initializeOtaClient() {
        try {
            // OTA base URL - configurable via BuildConfig or constant
            val baseUrl = BuildConfig.OTA_BASE_URL

            if (baseUrl.isEmpty() || baseUrl == "TODO_SET_PRODUCTION_URL") {
                Logger.debug(LOG, "OTA base URL not configured, skipping OTA client initialization")
                return
            }

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
                    baseUrl = baseUrl,
                    publicKeyFile = publicKeyFile,
                    currentAppVersion = BuildConfig.VERSION_NAME,
                    daemonVersionReader = {
                        OtaVersion.extractDaemonVersion(statusReader.read()?.daemon)
                    },
                    context = applicationContext,
                )

            otaClient = OtaClient(paths, otaConfig)
            Logger.info(LOG, "OTA client initialized with base URL: $baseUrl")

            // Start periodic OTA checks
            startPeriodicOtaChecks()
        } catch (e: Exception) {
            Logger.error(LOG, "Failed to initialize OTA client: ${e.message}")
        }
    }

    /**
     * Start periodic OTA update checks.
     */
    private fun startPeriodicOtaChecks() {
        serviceScope.launch {
            while (true) {
                try {
                    checkOtaUpdates()
                } catch (e: Exception) {
                    Logger.error(LOG, "OTA check failed: ${e.message}")
                }

                delay(OTA_CHECK_INTERVAL_MS)
            }
        }
    }

    /**
     * Check for OTA updates and stage if available.
     */
    private fun checkOtaUpdates() {
        val client =
            otaClient ?: run {
                Logger.debug(LOG, "OTA client not initialized")
                return
            }

        try {
            Logger.info(LOG, "Checking for OTA updates...")

            // Check for updates (without applying)
            val hasUpdates = client.checkForUpdates()

            if (hasUpdates) {
                Logger.info(LOG, "OTA updates available, initiating download and staging")
                val staged = client.checkAndUpdate()

                if (staged) {
                    Logger.info(LOG, "OTA updates staged successfully")
                } else {
                    Logger.debug(LOG, "No OTA updates staged")
                }
            } else {
                Logger.debug(LOG, "No OTA updates available")
            }
        } catch (e: Exception) {
            Logger.error(LOG, "OTA update check failed: ${e.message}")
            // Don't crash the service on OTA errors
        }
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
