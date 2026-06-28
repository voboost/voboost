package ru.voboost

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import ru.voboost.ui.ConfigState
import ru.voboost.ui.components.buildScreen

class MainActivity : Activity() {
    private lateinit var paths: PathsAndroid
    private lateinit var vehicleManager: VehicleManagerAndroid
    private lateinit var planProducer: PlanProducer
    private lateinit var statusReader: StatusReader
    private lateinit var configState: ConfigState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start Voboost service
        val serviceIntent = Intent(this, VoboostService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Initialize paths
        paths = PathsAndroid(applicationContext)

        // Initialize logger
        Logger.init(this, level = "debug")

        // Initialize managers
        vehicleManager = VehicleManagerAndroid(applicationContext)
        planProducer = PlanProducer(paths)
        statusReader = StatusReader(paths)

        // Log vehicle info
        vehicleManager.getVehicleInfo().onSuccess { info ->
            Logger.info(
                "MainActivity",
                "Vehicle: ${info.model} (${info.year}), Firmware: ${info.firmware}",
            )
        }.onFailure { error ->
            Logger.debug("MainActivity", "Could not get vehicle info: ${error.message}")
        }

        // Initialize ConfigState
        configState = ConfigState.getInstance(this)

        // Setup fullscreen mode BEFORE setContentView
        setupFullscreenMode()

        // Build and set the native view hierarchy
        val screen = buildScreen(this, configState)
        setContentView(screen)
    }

    override fun onDestroy() {
        super.onDestroy()
        configState.shutdown()
        Logger.shutdown()
    }

    @Suppress("DEPRECATION")
    private fun setupFullscreenMode() {
        // Hide system UI for automotive display
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        // Keep screen on for vehicle use
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
