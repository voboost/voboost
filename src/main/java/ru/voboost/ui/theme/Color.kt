package ru.voboost.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ru.voboost.config.models.Theme
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Color object that provides theme-aware colors
 * Usage: Color.TAB_SELECTED returns appropriate color for current theme
 */
object Color {
    // Brand primary colors
    val PRIMARY @Composable get() = getColor(ColorToken.PRIMARY)
    val SECONDARY @Composable get() = getColor(ColorToken.SECONDARY)
    val SECONDARY_VARIANT @Composable get() = getColor(ColorToken.SECONDARY_VARIANT)

    // Background colors
    val BACKGROUND @Composable get() = getColor(ColorToken.BACKGROUND)
    val SURFACE @Composable get() = getColor(ColorToken.SURFACE)
    val SURFACE_VARIANT @Composable get() = getColor(ColorToken.SURFACE_VARIANT)

    // Text colors
    val ON_PRIMARY @Composable get() = getColor(ColorToken.ON_PRIMARY)
    val ON_SECONDARY @Composable get() = getColor(ColorToken.ON_SECONDARY)
    val ON_BACKGROUND @Composable get() = getColor(ColorToken.ON_BACKGROUND)
    val ON_SURFACE @Composable get() = getColor(ColorToken.ON_SURFACE)

    // Status colors
    val SUCCESS @Composable get() = getColor(ColorToken.SUCCESS)
    val ERROR @Composable get() = getColor(ColorToken.ERROR)
    val WARNING @Composable get() = getColor(ColorToken.WARNING)

    // Navigation colors
    val TAB_SELECTED @Composable get() = getColor(ColorToken.TAB_SELECTED)
    val TAB_UNSELECTED @Composable get() = getColor(ColorToken.TAB_UNSELECTED)
    val TAB_BACKGROUND @Composable get() = getColor(ColorToken.TAB_BACKGROUND)
    val TAB_SELECTED_BACKGROUND @Composable get() = getColor(ColorToken.TAB_SELECTED_BACKGROUND)

    // Configuration colors
    val CONFIG_CHANGED_TEXT @Composable get() = getColor(ColorToken.CONFIG_CHANGED_TEXT)
    val CONFIG_NORMAL_TEXT @Composable get() = getColor(ColorToken.CONFIG_NORMAL_TEXT)

    // RadioGroup colors
    val RADIO_GROUP_BACKGROUND @Composable get() = getColor(ColorToken.RADIO_GROUP_BACKGROUND)
    val RADIO_GROUP_SELECTED_TEXT @Composable get() = getColor(ColorToken.RADIO_GROUP_SELECTED_TEXT)
    val RADIO_GROUP_UNSELECTED_TEXT @Composable get() = getColor(ColorToken.RADIO_GROUP_UNSELECTED_TEXT)
    val RADIO_GROUP_SELECTED_GRADIENT_TOP @Composable get() = getColor(ColorToken.RADIO_GROUP_SELECTED_GRADIENT_TOP)
    val RADIO_GROUP_SELECTED_GRADIENT_BOTTOM @Composable get() = getColor(ColorToken.RADIO_GROUP_SELECTED_GRADIENT_BOTTOM)
    val RADIO_GROUP_SELECTED_BORDER_TOP @Composable get() = getColor(ColorToken.RADIO_GROUP_SELECTED_BORDER_TOP)
    val RADIO_GROUP_SELECTED_BORDER_SIDE @Composable get() = getColor(ColorToken.RADIO_GROUP_SELECTED_BORDER_SIDE)
    val RADIO_GROUP_SELECTED_BORDER_BOTTOM @Composable get() = getColor(ColorToken.RADIO_GROUP_SELECTED_BORDER_BOTTOM)

    @Composable
    private fun getColor(token: ColorToken): ComposeColor {
        val isDarkTheme = isDarkTheme()
        val colorMap = if (isDarkTheme) darkColors else lightColors
        return colorMap[token] ?: throw IllegalArgumentException("Color $token not found")
    }

    @Composable
    private fun isDarkTheme(): Boolean {
        val systemDarkTheme = isSystemInDarkTheme()

        // Try to get ConfigViewModel instance safely
        val configViewModel =
            runCatching {
                ru.voboost.ui.ConfigViewModel.getInstance()
            }.getOrNull()

        return if (configViewModel != null && configViewModel.isInitialized()) {
            // Use reactive theme detection to trigger recomposition on theme changes
            val currentTheme by configViewModel.fieldFlow("settingsTheme").collectAsState()
            val theme = currentTheme?.let {
                try {
                    Theme.valueOf(it)
                } catch (e: IllegalArgumentException) {
                    Theme.light
                }
            } ?: Theme.light

            when (theme) {
                Theme.light -> false
                Theme.dark -> true
                else -> systemDarkTheme // fallback for any other values
            }
        } else {
            // If ConfigViewModel is not available, fall back to system theme
            systemDarkTheme
        }
    }
}

internal enum class ColorToken {
    PRIMARY,
    SECONDARY,
    SECONDARY_VARIANT,
    BACKGROUND,
    SURFACE,
    SURFACE_VARIANT,
    ON_PRIMARY,
    ON_SECONDARY,
    ON_BACKGROUND,
    ON_SURFACE,
    SUCCESS,
    ERROR,
    WARNING,
    TAB_SELECTED,
    TAB_UNSELECTED,
    TAB_BACKGROUND,
    TAB_SELECTED_BACKGROUND,
    CONFIG_CHANGED_TEXT,
    CONFIG_NORMAL_TEXT,
    RADIO_GROUP_BACKGROUND,
    RADIO_GROUP_SELECTED_TEXT,
    RADIO_GROUP_UNSELECTED_TEXT,
    RADIO_GROUP_SELECTED_GRADIENT_TOP,
    RADIO_GROUP_SELECTED_GRADIENT_BOTTOM,
    RADIO_GROUP_SELECTED_BORDER_TOP,
    RADIO_GROUP_SELECTED_BORDER_SIDE,
    RADIO_GROUP_SELECTED_BORDER_BOTTOM
}
