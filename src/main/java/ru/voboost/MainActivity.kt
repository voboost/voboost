package ru.voboost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import ru.voboost.ui.ConfigViewModel
import ru.voboost.ui.components.screen

class MainActivity : ComponentActivity() {
    private lateinit var paths: Paths
    private lateinit var fridaManager: FridaManager
    private lateinit var vehicleManager: VehicleManager
    private lateinit var agentManager: FridaAgentManager

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

        // Initialize ConfigViewModel
        ConfigViewModel.getInstance(this)

        setContent {
            screen()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fridaManager.shutdown()
        Logger.shutdown()
    }
}
