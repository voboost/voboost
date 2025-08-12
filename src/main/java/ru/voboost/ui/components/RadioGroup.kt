package ru.voboost.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import ru.voboost.ui.theme.Color
import ru.voboost.ui.theme.Dimensions
import ru.voboost.ui.ConfigViewModel

/**
 * RadioGroup option data class
 */
data class RadioGroupOption(
    val labelKey: String,
    val value: String
)

/**
 * RadioGroup element
 */
data class RadioGroup(
    override val id: String,
    val fieldPath: String?,
    val options: List<RadioGroupOption>,
    val defaultValue: String = "",
    override val visibility: Flow<Boolean> = flowOf(true)
) : AbstractControl()

/**
 * RadioGroup renderer
 */
@Composable
fun radioGroupRenderer(
    element: RadioGroup,
    configViewModel: ConfigViewModel
) {
    val isVisible by element.visibility.collectAsState(initial = true)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Handle both config-bound and standalone RadioGroups
    var standaloneValue by remember { mutableStateOf(element.defaultValue) }

    val selectedValue = if (element.fieldPath != null) {
        // Config-bound RadioGroup
        val valueFlow: StateFlow<String?> = configViewModel.fieldFlow(element.fieldPath)
        val selectedValueRaw by valueFlow.collectAsState()
        selectedValueRaw ?: element.defaultValue
    } else {
        // Standalone RadioGroup - use local state
        standaloneValue
    }

    // Get current language to trigger recomposition when language changes
    val currentLanguage = if (configViewModel.isInitialized()) {
        configViewModel.localeManager.currentLanguage.collectAsState().value
    } else null

    // Check if ConfigViewModel is initialized to avoid theme flashing
    val isConfigInitialized = configViewModel.isInitialized()

    // Get current theme reactively to trigger recomposition on theme changes
    val currentTheme = if (isConfigInitialized) {
        val themeFlow = configViewModel.fieldFlow("settingsTheme")
        val themeValue by themeFlow.collectAsState()
        themeValue
    } else null
    val isDarkTheme = currentTheme == "dark"

    if (isVisible) {
        // Find selected index
        val selectedIndex = element.options.indexOfFirst { it.value == selectedValue }.takeIf { it >= 0 } ?: 0

        // State to track item widths and positions - reset when language changes
        var itemWidths by remember(currentLanguage) { mutableStateOf(emptyList<Float>()) }
        var itemPositions by remember(currentLanguage) { mutableStateOf(emptyList<Float>()) }
        var hasEverAnimated by remember(currentLanguage) { mutableStateOf(false) }
        var previousSelectedValue by remember(currentLanguage) { mutableStateOf(selectedValue) }

        // Calculate target position and width for the background
        val targetX = if (itemPositions.isNotEmpty() && selectedIndex < itemPositions.size) {
            itemPositions[selectedIndex]
        } else 0f

        val targetWidth = if (itemWidths.isNotEmpty() && selectedIndex < itemWidths.size) {
            itemWidths[selectedIndex]
        } else 0f

        // Animation state for background position and width
        val animatedX = remember { Animatable(targetX) }
        val animatedWidth = remember { Animatable(targetWidth) }

        // Only animate when selection changes AND we've already rendered once
        LaunchedEffect(selectedValue) {
            if (selectedValue != previousSelectedValue && hasEverAnimated && itemWidths.isNotEmpty() && itemPositions.isNotEmpty()) {
                // Animate to new position (only on user selection change)
                launch {
                    animatedX.animateTo(
                        targetValue = targetX,
                        animationSpec = spring(
                            dampingRatio = Dimensions.TAB_ANIMATION_DAMPING_RATIO,
                            stiffness = Dimensions.TAB_ANIMATION_STIFFNESS,
                            visibilityThreshold = Dimensions.TAB_ANIMATION_VISIBILITY_THRESHOLD
                        )
                    )
                }
                launch {
                    animatedWidth.animateTo(
                        targetValue = targetWidth,
                        animationSpec = spring(
                            dampingRatio = Dimensions.TAB_ANIMATION_DAMPING_RATIO,
                            stiffness = Dimensions.TAB_ANIMATION_STIFFNESS,
                            visibilityThreshold = Dimensions.TAB_ANIMATION_VISIBILITY_THRESHOLD
                        )
                    )
                }
            }
            previousSelectedValue = selectedValue
        }

        // Initialize animation values and enable animations after first layout
        LaunchedEffect(itemWidths, itemPositions) {
            if (itemWidths.isNotEmpty() && itemPositions.isNotEmpty()) {
                if (!hasEverAnimated) {
                    // First time - snap to position immediately
                    animatedX.snapTo(targetX)
                    animatedWidth.snapTo(targetWidth)
                    hasEverAnimated = true
                } else {
                    // Layout changed (e.g., language change) - snap to new position
                    animatedX.snapTo(targetX)
                    animatedWidth.snapTo(targetWidth)
                }
            }
        }

        // Use key to force recomposition when language changes
        key(currentLanguage) {
            // Box container for proper layering
            Box(
                modifier = Modifier
                    .wrapContentWidth()
                    .height(Dimensions.RADIO_GROUP_HEIGHT)
            ) {
                // Background layer - bottom (matches content width exactly)
                Box(
                    modifier = Modifier
                        .wrapContentWidth()
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(Dimensions.RADIO_GROUP_CORNER_RADIUS))
                        .background(
                            // Always use direct colors to avoid theme flashing
                            if (isDarkTheme) {
                                ComposeColor(0xFF373F4A) // Dark theme background
                            } else {
                                ComposeColor(0xFFFFFFFF) // Light theme background
                            }
                        )
                ) {
                    // Invisible Row that matches the text structure to define background size
                    Row(
                        modifier = Modifier
                            .wrapContentWidth()
                            .fillMaxHeight()
                    ) {
                        element.options.forEachIndexed { _, option ->
                            Box(
                                modifier = Modifier
                                    .wrapContentWidth()
                                    .widthIn(min = Dimensions.RADIO_GROUP_ITEM_MIN_WIDTH)
                                    .fillMaxHeight()
                                    .padding(horizontal = Dimensions.RADIO_GROUP_ITEM_PADDING_HORIZONTAL),
                                contentAlignment = Alignment.Center
                            ) {
                                // Invisible text to measure size
                                text(
                                    textKey = option.labelKey,
                                    color = ComposeColor.Transparent,
                                    fontSize = with(density) { Dimensions.RADIO_GROUP_TEXT_SIZE.toSp() },
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Selection background - always use animated values (they're initialized correctly)
                if (targetWidth > 0) {
                    Box(
                        modifier = Modifier
                            .width(with(density) { animatedWidth.value.toDp() })
                            .fillMaxHeight()
                            .offset(x = with(density) { animatedX.value.toDp() })
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        ComposeColor(0xFF55A2EF), // Same gradient for both themes
                                        ComposeColor(0xFF2681DD)  // Same gradient for both themes
                                    )
                                ),
                                shape = RoundedCornerShape(Dimensions.RADIO_GROUP_CORNER_RADIUS)
                            )
                            .border(
                                width = Dimensions.RADIO_GROUP_BORDER_WIDTH,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        ComposeColor(0xFF8DC6FF), // Same border for both themes
                                        ComposeColor(0xFF519AE5), // Same border for both themes
                                        ComposeColor(0xFF1875D2)  // Same border for both themes
                                    )
                                ),
                                shape = RoundedCornerShape(Dimensions.RADIO_GROUP_CORNER_RADIUS)
                            )
                    )
                }

                // Text layer - top (must be last to be clickable)
                Layout(
                    content = {
                        // Radio group items - content-based width with consistent padding
                        element.options.forEachIndexed { _, option ->
                            val isSelected = option.value == selectedValue

                            Box(
                                modifier = Modifier
                                    .wrapContentWidth()
                                    .widthIn(min = Dimensions.RADIO_GROUP_ITEM_MIN_WIDTH)
                                    .fillMaxHeight()
                                    .padding(horizontal = Dimensions.RADIO_GROUP_ITEM_PADDING_HORIZONTAL)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (element.fieldPath != null) {
                                            // Config-bound RadioGroup - update configuration
                                            scope.launch {
                                                configViewModel.updateField(element.fieldPath, option.value)
                                            }
                                        } else {
                                            // Standalone RadioGroup - update local state
                                            standaloneValue = option.value
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                text(
                                    textKey = option.labelKey,
                                    color = if (isSelected) {
                                        ComposeColor(0xFFFFFFFF) // Same selected text for both themes
                                    } else {
                                        if (isDarkTheme) {
                                            ComposeColor(0xFFCACACA) // Dark theme unselected text
                                        } else {
                                            ComposeColor(0xFF2D3442) // Light theme unselected text
                                        }
                                    },
                                    fontSize = with(density) { Dimensions.RADIO_GROUP_TEXT_SIZE.toSp() },
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .wrapContentWidth()
                        .fillMaxHeight()
                ) { measurables, constraints ->
                    // Measure all children
                    val placeables = measurables.map { measurable ->
                        measurable.measure(constraints.copy(minWidth = 0))
                    }

                    // Calculate total width and individual positions
                    val totalWidth = placeables.sumOf { it.width }
                    val height = placeables.maxOfOrNull { it.height } ?: 0

                    // Update positions and widths for background animation
                    val newWidths = mutableListOf<Float>()
                    val newPositions = mutableListOf<Float>()
                    var currentX = 0

                    placeables.forEach { placeable ->
                        newWidths.add(placeable.width.toFloat())
                        newPositions.add(currentX.toFloat())
                        currentX += placeable.width
                    }

                    if (newWidths != itemWidths) {
                        itemWidths = newWidths
                        itemPositions = newPositions
                    }

                    layout(totalWidth, height) {
                        var xPosition = 0
                        placeables.forEach { placeable ->
                            placeable.placeRelative(x = xPosition, y = 0)
                            xPosition += placeable.width
                        }
                    }
                }
            }
        }
    }
}