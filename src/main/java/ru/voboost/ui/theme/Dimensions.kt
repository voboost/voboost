package ru.voboost.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

object Dimensions {
    // Sidebar dimensions
    val SIDEBAR_WIDTH @Composable get() = 340.pxToDp()
    val SIDEBAR_PADDING_START @Composable get() = 36.pxToDp()

    // Tab item dimensions
    val TAB_ITEM_WIDTH @Composable get() = 268.pxToDp()
    val TAB_ITEM_HEIGHT @Composable get() = 100.pxToDp()
    val TAB_ITEM_SPACING @Composable get() = 40.pxToDp()
    val TAB_ITEM_CORNER_RADIUS @Composable get() = 20.pxToDp()

    // Main content dimensions
    val MAIN_CONTENT_PADDING @Composable get() = 16.pxToDp()
    val MAIN_CONTENT_CORNER_RADIUS @Composable get() = 12.pxToDp()

    // Text dimensions
    val TAB_TEXT_SIZE @Composable get() = 33.pxToDp()

    // RadioGroup dimensions
    val RADIO_GROUP_HEIGHT @Composable get() = 70.pxToDp()
    val RADIO_GROUP_CORNER_RADIUS @Composable get() = 34.pxToDp()
    val RADIO_GROUP_TEXT_SIZE @Composable get() = 24.pxToDp()
    val RADIO_GROUP_BORDER_WIDTH @Composable get() = 2.pxToDp()
    val RADIO_GROUP_ANIMATION_OVERSHOOT @Composable get() = 10.pxToDp()
    val RADIO_GROUP_ITEM_PADDING_HORIZONTAL @Composable get() = 32.pxToDp()
    val RADIO_GROUP_ITEM_MIN_WIDTH @Composable get() = 120.pxToDp()

    // Animation durations (in milliseconds)
    const val RADIO_GROUP_ANIMATION_DURATION = 40
}

@Composable
fun Int.pxToDp(): Dp {
    return with(LocalDensity.current) { this@pxToDp.toDp() }
}
