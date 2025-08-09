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
}

@Composable
fun Int.pxToDp(): Dp {
    return with(LocalDensity.current) { this@pxToDp.toDp() }
}
