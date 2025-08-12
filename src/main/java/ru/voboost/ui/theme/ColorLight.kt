package ru.voboost.ui.theme

import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Light theme color scheme for Voboost
 * Optimized for automotive environment during daylight conditions
 */
internal val lightColors =
    mapOf(
        ColorToken.PRIMARY to ComposeColor(0xFF2196F3),
        ColorToken.SECONDARY to ComposeColor(0xFF03DAC6),
        ColorToken.SECONDARY_VARIANT to ComposeColor(0xFF018786),
        ColorToken.BACKGROUND to ComposeColor(0xFFF6F8FB),
        ColorToken.SURFACE to ComposeColor(0xFFFFFFFF),
        ColorToken.SURFACE_VARIANT to ComposeColor(0xFFF0F0F0),
        ColorToken.ON_PRIMARY to ComposeColor(0xFFFFFFFF),
        ColorToken.ON_SECONDARY to ComposeColor(0xFF000000),
        ColorToken.ON_BACKGROUND to ComposeColor(0xFF1A1A1A),
        ColorToken.ON_SURFACE to ComposeColor(0xFF1A1A1A),
        ColorToken.SUCCESS to ComposeColor(0xFF4CAF50),
        ColorToken.ERROR to ComposeColor(0xFFF44336),
        ColorToken.WARNING to ComposeColor(0xFFFF9800),
        ColorToken.TAB_SELECTED to ComposeColor(0xFF4099F3),
        ColorToken.TAB_UNSELECTED to ComposeColor(0xFF2D3442),
        ColorToken.TAB_BACKGROUND to ComposeColor(0xFFF6F8FB),
        ColorToken.TAB_SELECTED_BACKGROUND to ComposeColor(0xFFE3F2FD),
        ColorToken.CONFIG_CHANGED_TEXT to ComposeColor(0xFFF44336),
        ColorToken.CONFIG_NORMAL_TEXT to ComposeColor(0xFF1A1A1A),
        // RadioGroup colors for light theme
        ColorToken.RADIO_GROUP_BACKGROUND to ComposeColor(0xFFFFFFFF),
        ColorToken.RADIO_GROUP_SELECTED_TEXT to ComposeColor(0xFFFFFFFF),
        ColorToken.RADIO_GROUP_UNSELECTED_TEXT to ComposeColor(0xFF2D3442),
        ColorToken.RADIO_GROUP_SELECTED_GRADIENT_TOP to ComposeColor(0xFF55A2EF),
        ColorToken.RADIO_GROUP_SELECTED_GRADIENT_BOTTOM to ComposeColor(0xFF2681DD),
        ColorToken.RADIO_GROUP_SELECTED_BORDER_TOP to ComposeColor(0xFF8DC6FF),
        ColorToken.RADIO_GROUP_SELECTED_BORDER_SIDE to ComposeColor(0xFF519AE5),
        ColorToken.RADIO_GROUP_SELECTED_BORDER_BOTTOM to ComposeColor(0xFF1875D2)
    )
