package ru.voboost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import ru.voboost.ui.components.screen
import ru.voboost.ui.ConfigViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ConfigViewModel with context - getInstance automatically calls initialize
        ConfigViewModel.getInstance(this)

        setContent {
            screen()
        }
    }
}
