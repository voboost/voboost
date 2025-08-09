package ru.voboost.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.voboost.ui.i18n

/**
 * Info display styles
 */
enum class InfoStyle {
    NORMAL,
    WARNING,
    ERROR,
    SUCCESS
}

/**
 * Info display element
 */
data class Info(
    override val id: String,
    val textKey: String,
    val style: InfoStyle = InfoStyle.NORMAL,
    override val visibility: Flow<Boolean> = flowOf(true)
) : AbstractControl()

/**
 * Info renderer
 */
@Composable
fun infoRenderer(element: Info) {
    val isVisible by element.visibility.collectAsState(initial = true)

    if (isVisible) {
        val textStyle =
            when (element.style) {
                InfoStyle.NORMAL -> MaterialTheme.typography.bodyMedium
                InfoStyle.WARNING -> MaterialTheme.typography.bodyMedium
                InfoStyle.ERROR -> MaterialTheme.typography.bodyMedium
                InfoStyle.SUCCESS -> MaterialTheme.typography.bodyMedium
            }

        Text(
            text = i18n(element.textKey),
            style = textStyle,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}
