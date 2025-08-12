package ru.voboost.ui.components

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.voboost.config.models.Tab
import ru.voboost.ui.theme.Color
import ru.voboost.ui.theme.Dimensions
import ru.voboost.ui.ConfigViewModel
import androidx.compose.ui.graphics.Color as ComposeColor

// Available tabs
val TABS =
    listOf(
        Tab.store,
        Tab.applications,
        Tab.`interface`,
        Tab.vehicle,
        Tab.settings
    )

@Composable
fun tabs(modifier: Modifier = Modifier) {
    val configViewModel = ConfigViewModel.getInstance()
    val selectedTab by configViewModel.selectedTab.collectAsState()

    // Calculate the Y offset for the animated background
    val selectedIndex = TABS.indexOf(selectedTab)
    val density = LocalDensity.current

    // Calculate target Y position for the background
    val targetY = with(density) {
        selectedIndex.toFloat() * (Dimensions.TAB_ITEM_HEIGHT + Dimensions.TAB_ITEM_SPACING).toPx()
    }

    // Animated Y offset for the background
    val animatedY = remember { Animatable(targetY) }

    // Animate to new position when selected tab changes - uniform spring animation
    LaunchedEffect(selectedTab) {
        animatedY.animateTo(
            targetValue = targetY,
            animationSpec = spring(
                dampingRatio = Dimensions.TAB_ANIMATION_DAMPING_RATIO,
                stiffness = Dimensions.TAB_ANIMATION_STIFFNESS,
                visibilityThreshold = Dimensions.TAB_ANIMATION_VISIBILITY_THRESHOLD
            )
        )
    }

    Box(
        modifier = modifier
            .width(Dimensions.SIDEBAR_WIDTH)
            .fillMaxHeight()
            .background(Color.TAB_BACKGROUND)
            .padding(start = Dimensions.SIDEBAR_PADDING_START)
    ) {
        // Animated background that slides between tab positions
        Box(
            modifier = Modifier
                .width(Dimensions.TAB_ITEM_WIDTH)
                .height(Dimensions.TAB_ITEM_HEIGHT)
                .offset(y = with(density) { animatedY.value.toDp() })
                .background(
                    Color.TAB_SELECTED_BACKGROUND,
                    RoundedCornerShape(Dimensions.TAB_ITEM_CORNER_RADIUS)
                )
        )

        // Tab items (text only, no individual backgrounds)
        Column {
            TABS.forEachIndexed { index, tab ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(Dimensions.TAB_ITEM_SPACING))
                }

                tab(
                    tab = tab,
                    isSelected = selectedTab == tab,
                    onClick = { configViewModel.setSelectedTab(tab) }
                )
            }
        }
    }
}

@Composable
private fun tab(
    tab: Tab,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Get current language from LocaleManager to trigger recomposition
    val configViewModel = ConfigViewModel.getInstance()
    val currentLanguage =
        if (configViewModel.isInitialized()) {
            configViewModel.localeManager.currentLanguage.collectAsState().value
        } else {
            null
        }

    // Log language changes for debugging
    Log.d("Tabs", "Tab ${tab.name} recomposing with language: $currentLanguage")

    // Instant text color change (no animation) - this is the first phase
    val textColor = if (isSelected) Color.TAB_SELECTED else Color.TAB_UNSELECTED

    // Use currentLanguage in the key to force recomposition
    Box(
        modifier =
            Modifier
                .width(Dimensions.TAB_ITEM_WIDTH)
                .height(Dimensions.TAB_ITEM_HEIGHT)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Don't cache the textKey - let it be recalculated on each recomposition
        val textKey = "tab_${tab.name.lowercase()}"
        Log.d("Tabs", "Using textKey: $textKey for tab ${tab.name} with language: $currentLanguage")

        // Force text() to recompose by passing currentLanguage as a key
        key(currentLanguage) {
            text(
                textKey = textKey,
                color = textColor,
                fontSize = with(LocalDensity.current) { Dimensions.TAB_TEXT_SIZE.toSp() },
                fontWeight = FontWeight.Normal
            )
        }
    }
}

