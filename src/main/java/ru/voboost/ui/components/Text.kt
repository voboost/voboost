package ru.voboost.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.voboost.ui.i18n
import androidx.compose.material3.Text as MaterialText

/**
 * Text display styles
 */
enum class VoboostTextStyle {
    NORMAL,
    HEADING,
    CAPTION,
    LABEL
}

/**
 * Text display element
 */
data class Text(
    override val id: String,
    val textKey: String,
    val style: VoboostTextStyle = VoboostTextStyle.NORMAL,
    val color: Color? = null,
    val fontSize: TextUnit? = null,
    val fontWeight: FontWeight? = null,
    override val visibility: Flow<Boolean> = flowOf(true)
) : AbstractControl()

/**
 * Text renderer
 */
@Composable
fun textRenderer(
    element: Text,
    modifier: Modifier = Modifier
) {
    val isVisible by element.visibility.collectAsState(initial = true)

    if (isVisible) {
        MaterialText(
            text = i18n(element.textKey),
            modifier = modifier,
            color = element.color ?: Color.Unspecified,
            fontSize = element.fontSize ?: TextUnit.Unspecified,
            fontWeight = element.fontWeight
        )
    }
}

/**
 * Simple localized text component
 */
@Composable
fun text(
    textKey: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    style: androidx.compose.ui.text.TextStyle? = null
) {
    android.util.Log.d("Text", "text() called with textKey: $textKey")
    val localizedText = i18n(textKey)
    android.util.Log.d("Text", "i18n() returned: $localizedText for key: $textKey")

    MaterialText(
        text = localizedText,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        style = style ?: androidx.compose.ui.text.TextStyle.Default
    )
}
