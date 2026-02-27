package ru.voboost

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import ru.voboost.ui.ConfigState
import ru.voboost.ui.components.buildScreen

class MainActivity : Activity() {
    private lateinit var paths: Paths
    private lateinit var fridaManager: FridaManager
    private lateinit var vehicleManager: VehicleManager
    private lateinit var agentManager: FridaAgentManager
    private lateinit var configState: ConfigState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize paths
        paths = PathsAndroid(applicationContext)

        // Initialize logger
        Logger.init(this, level = "debug")

        // Initialize managers
        fridaManager = FridaManagerAndroid(paths)
        vehicleManager = VehicleManagerAndroid(applicationContext)
        agentManager = FridaAgentManager(fridaManager)

        // Log vehicle info
        vehicleManager.getVehicleInfo().onSuccess { info ->
            Logger.info(
                "MainActivity",
                "Vehicle: ${info.model} (${info.year}), Firmware: ${info.firmware}",
            )
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
        fridaManager.shutdown()
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
