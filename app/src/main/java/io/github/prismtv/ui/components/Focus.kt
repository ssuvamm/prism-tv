package io.github.prismtv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp

/**
 * Visible focus for D-pad / remote navigation: a coloured outline and a slight lift. Place it before
 * the clickable modifier. On touch devices nothing takes focus this way, so it costs nothing there.
 * [onMenu] lets the remote's Menu key do what a long-press does on a phone.
 */
fun Modifier.focusRing(
    shape: Shape = RoundedCornerShape(12.dp),
    scale: Float = 1.03f,
    onMenu: (() -> Unit)? = null,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (focused) scale else 1f, label = "focus-scale")
    val color = MaterialTheme.colorScheme.primary
    var m = this
        .onFocusChanged { focused = it.hasFocus }
        .graphicsLayer { scaleX = s; scaleY = s }
        .border(if (focused) 2.5.dp else 0.dp, if (focused) color else Color.Transparent, shape)
    if (onMenu != null) {
        m = m.onKeyEvent { e ->
            if (e.type == KeyEventType.KeyUp && e.key == Key.Menu) { onMenu(); true } else false
        }
    }
    m
}
