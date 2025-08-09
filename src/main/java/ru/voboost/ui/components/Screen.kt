package ru.voboost.ui.components

import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import ru.voboost.ui.theme.Color
import ru.voboost.ui.theme.pxToDp
import ru.voboost.ui.ConfigViewModel

@Composable
fun screen() {
    val context = LocalContext.current
    val configViewModel = ConfigViewModel.getInstance()
    val config by configViewModel.config.collectAsState()

    // Setup fullscreen mode when screen is first composed
    LaunchedEffect(Unit) {
        if (context is ComponentActivity) {
            setupFullscreenMode(context)
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.BACKGROUND)
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .offset(
                        x = (config?.settingsInterfaceShiftX ?: 0).pxToDp(),
                        y = (config?.settingsInterfaceShiftY ?: 0).pxToDp()
                    )
        ) {
            // Left Navigation Sidebar
            tabs()

            // Main Content Area
            panel()
        }
    }
}

private fun setupFullscreenMode(activity: ComponentActivity) {
    // Hide system UI (status bar and navigation bar)
    WindowCompat.setDecorFitsSystemWindows(activity.window, false)

    val windowInsetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    windowInsetsController.let { controller ->
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    // Keep screen on for vehicle use
    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    // Additional fullscreen flags for older Android versions
    @Suppress("DEPRECATION")
    activity.window.decorView.systemUiVisibility = (
        View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    )

    activity.enableEdgeToEdge()
}
