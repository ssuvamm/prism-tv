package io.github.prismtv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.type
import io.github.prismtv.AppGraph
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.prismtv.ui.theme.LocalStyle

/** Full-screen backdrop: flat colour for Material styles, soft gradient with light blobs for glassy ones. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val spec = LocalStyle.current
    val bg = MaterialTheme.colorScheme.background
    val base = remember(spec) {
        if (spec.backdrop.size > 1) Brush.verticalGradient(spec.backdrop) else SolidColor(bg)
    }
    Box(
        modifier
            .fillMaxSize()
            .background(if (spec.backdrop.size > 1) base else SolidColor(bg))
            .drawBehind {
                if (spec.blobs.isEmpty()) return@drawBehind
                val a = if (spec.dark) 0.38f else 0.55f
                val w = size.width
                val h = size.height
                val spots = listOf(
                    Offset(w * 0.1f, h * 0.08f) to w * 0.85f,
                    Offset(w * 0.95f, h * 0.35f) to w * 0.75f,
                    Offset(w * 0.25f, h * 0.85f) to w * 0.9f,
                )
                spots.forEachIndexed { i, (center, radius) ->
                    val c = spec.blobs[i % spec.blobs.size]
                    drawCircle(
                        Brush.radialGradient(listOf(c.copy(alpha = a), c.copy(alpha = 0f)), center, radius),
                        radius, center,
                    )
                }
            },
        content = content,
    )
}

/** The basic card. Frosted and outlined in glass themes, tonal in Material themes. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LocalStyle.current.corner),
    /** Bars that float over scrolling content need to be close to opaque to stay readable. */
    solid: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val spec = LocalStyle.current
    val cs = MaterialTheme.colorScheme
    val m = if (spec.translucent) {
        val fill = when {
            solid -> lerp(cs.background, cs.surface, 0.5f).copy(alpha = 0.94f)
            spec.dark -> Color.White.copy(alpha = 0.07f + (1 - spec.surfaceAlpha) * 0.02f)
            else -> Color.White.copy(alpha = spec.surfaceAlpha)
        }
        val edge = Brush.linearGradient(
            listOf(Color.White.copy(alpha = spec.borderAlpha), Color.White.copy(alpha = spec.borderAlpha * 0.15f)),
        )
        modifier.clip(shape).background(fill).border(BorderStroke(1.dp, edge), shape)
    } else {
        val border = if (spec.borderAlpha > 0f) Modifier.border(1.dp, Color.White.copy(alpha = spec.borderAlpha), shape) else Modifier
        modifier.clip(shape).background(cs.surfaceContainer).then(border)
    }
    Box(m, content = content)
}

@Composable
fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val cs = MaterialTheme.colorScheme
    Panel(modifier.fillMaxWidth().height(50.dp).focusRing(RoundedCornerShape(25.dp), 1f), shape = RoundedCornerShape(25.dp)) {
        Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = cs.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                    cursorBrush = SolidColor(cs.primary),
                    // On a TV, moving the D-pad focus across the field must not pop up the keyboard; OK opens it.
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, showKeyboardOnFocus = !AppGraph.isTv),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { e ->
                        // Up/Down leave the field so a remote can never get stuck in it.
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionDown -> { keyboard?.hide(); focus.moveFocus(FocusDirection.Down) }
                            Key.DirectionUp -> { keyboard?.hide(); focus.moveFocus(FocusDirection.Up) }
                            else -> false
                        }
                    }.onKeyEvent { e ->
                        val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                        if (AppGraph.isTv && ok && e.type == KeyEventType.KeyUp) { keyboard?.show(); true } else false
                    },
                )
            }
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, "Clear", tint = cs.onSurfaceVariant) }
            } else Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.invoke()
    }
}

@Composable
fun LargeTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        actions()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

@Composable
fun Badge(text: String, color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

val Dp.half: Dp get() = this / 2
