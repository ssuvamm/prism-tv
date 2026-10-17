package io.github.prismtv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.prismtv.data.Catalog
import io.github.prismtv.data.Channel
import io.github.prismtv.ui.theme.LocalStyle

/**
 * The one channel list used everywhere. It is a lazy grid in both modes so switching list/grid
 * keeps scroll position, and items are keyed by channel id so updates never jump the list.
 */
@Composable
fun ChannelGrid(
    channels: List<Channel>,
    catalog: Catalog,
    favorites: Set<String>,
    grid: Boolean,
    state: LazyGridState,
    onClick: (Channel) -> Unit,
    onLongClick: (Channel) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    header: LazyGridScope.() -> Unit = {},
    footer: LazyGridScope.() -> Unit = {},
) {
    LazyVerticalGrid(
        columns = if (grid) GridCells.Adaptive(112.dp) else GridCells.Fixed(1),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        header()
        if (grid) {
            items(channels, key = { it.key }, contentType = { 1 }) { ch ->
                ChannelTile(ch, ch.key in favorites, { onClick(ch) }, { onLongClick(ch) }, Modifier.padding(horizontal = 2.dp))
            }
        } else {
            items(channels, key = { it.key }, contentType = { 0 }) { ch ->
                ChannelRow(ch, catalog, ch.key in favorites, { onClick(ch) }, { onLongClick(ch) })
            }
        }
        footer()
    }
}

fun LazyGridScope.fullItem(key: String, content: @Composable LazyGridItemScope.() -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = "full", content = content)

fun LazyGridScope.rail(key: String, title: String, channels: List<Channel>, onClick: (Channel) -> Unit, onLongClick: (Channel) -> Unit) {
    if (channels.isEmpty()) return
    fullItem("#h-$key") { SectionHeader(title) }
    fullItem("#r-$key") {
        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(channels, key = { it.key }) { ch -> RailCard(ch, { onClick(ch) }, { onLongClick(ch) }) }
        }
    }
}

/** Pill-style segmented control that adapts to the current style. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val corner = LocalStyle.current.corner
    Panel(modifier.fillMaxWidth().height(42.dp), shape = RoundedCornerShape(corner)) {
        Row(Modifier.fillMaxSize().padding(3.dp)) {
            options.forEach { o ->
                val sel = o == selected
                val bg by animateColorAsState(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent, label = "seg")
                val fg by animateColorAsState(if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, label = "segfg")
                Box(
                    Modifier.weight(1f).fillMaxSize().focusRing(RoundedCornerShape(corner - 3.dp), 1f).clip(RoundedCornerShape(corner - 3.dp)).background(bg).clickable { onSelect(o) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label(o), color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

fun countLabel(n: Int, one: String, many: String = one + "s"): String =
    "%,d %s".format(n, if (n == 1) one else many)
