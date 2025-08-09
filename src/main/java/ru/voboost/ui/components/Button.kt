package ru.voboost.ui.components

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import androidx.compose.material3.Button as MaterialButton

/**
 * Button styling options
 */
enum class ButtonStyle {
    PRIMARY,
    SECONDARY,
    OUTLINE,
    TEXT
}

/**
 * Button element
 */
data class Button(
    override val id: String,
    val labelKey: String,
    val onClick: suspend () -> Unit,
    val style: ButtonStyle = ButtonStyle.PRIMARY,
    override val visibility: Flow<Boolean> = flowOf(true)
) : AbstractControl()

/**
 * Button renderer
 */
@Composable
fun buttonRenderer(element: Button) {
    val scope = rememberCoroutineScope()

    val onClick = {
        scope.launch {
            element.onClick()
        }
    }

    when (element.style) {
        ButtonStyle.PRIMARY, ButtonStyle.SECONDARY -> {
            MaterialButton(onClick = { onClick() }) {
                text(textKey = element.labelKey)
            }
        }
        ButtonStyle.OUTLINE -> {
            OutlinedButton(onClick = { onClick() }) {
                text(textKey = element.labelKey)
            }
        }
        ButtonStyle.TEXT -> {
            TextButton(onClick = { onClick() }) {
                text(textKey = element.labelKey)
            }
        }
    }
}
