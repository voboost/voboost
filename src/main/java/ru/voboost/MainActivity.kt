package ru.voboost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import ru.voboost.ui.screens.MainScreen
import ru.voboost.ui.theme.VoboostTheme
import ru.voboost.ui.viewmodel.ConfigViewModel
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : ComponentActivity() {
    private val configViewModel: ConfigViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Copy default config if needed
        copyDefaultConfigIfNeeded()

        setContent {
            VoboostTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LaunchedEffect(Unit) {
                        configViewModel.initialize(this@MainActivity)
                    }

                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        configViewModel = configViewModel
                    )
                }
            }
        }
    }

    private fun copyDefaultConfigIfNeeded() {
        val configFile = File(filesDir, "config.yaml")
        if (!configFile.exists()) {
            try {
                assets.open("config.yaml").use { inputStream ->
                    FileOutputStream(configFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: IOException) {
                // Handle error silently for now
            }
        }
    }
}
