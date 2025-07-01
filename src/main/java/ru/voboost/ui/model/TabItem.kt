package ru.voboost.ui.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class TabItem(
    val title: String,
    val icon: ImageVector,
    val route: String
) {
    LAUNCHER("Launcher", Icons.Filled.Home, "launcher"),
    APPLICATIONS("Applications", Icons.Filled.Apps, "applications"),
    INTERFACE("Interface", Icons.Filled.Build, "interface"),
    VEHICLE("Vehicle", Icons.Filled.DirectionsCar, "vehicle"),
    SETTINGS("Settings", Icons.Filled.Settings, "settings")
}
