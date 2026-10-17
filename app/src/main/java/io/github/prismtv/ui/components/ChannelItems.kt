package io.github.prismtv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.prismtv.data.Catalog
import io.github.prismtv.data.Channel
import io.github.prismtv.ui.theme.LocalStyle
import kotlin.math.abs

private val LogoTile = Color(0xFFF3F3F7)

/** Channel logo on a light tile (most logos are made for light backgrounds), with a monogram fallback. */
@Composable
fun ChannelLogo(channel: Channel, modifier: Modifier = Modifier, corner: Dp = 10.dp) {
    val monoColor = remember(channel.key) { monogramColor(channel.name) }
    // The monogram is the fallback while the logo loads, or when there is none / it fails.
    var loaded by remember(channel.logo) { mutableStateOf(false) }
    Box(
        modifier.clip(RoundedCornerShape(corner)).background(LogoTile),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) Text(
            monogram(channel.name),
            color = monoColor,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            maxLines = 1,
        )
        if (channel.logo != null) {
            AsyncImage(
                model = channel.logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                modifier = Modifier.fillMaxSize().padding(5.dp),
            )
        }
    }
}

@Composable
fun ChannelRow(
    channel: Channel,
    catalog: Catalog,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val cs = MaterialTheme.colorScheme
    val subtitle = remember(channel, catalog) { subtitleFor(channel, catalog) }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .focusRing(RoundedCornerShape(LocalStyle.current.corner * 0.75f), scale = 1.01f, onMenu = onLongClick)
            .clip(RoundedCornerShape(LocalStyle.current.corner * 0.75f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() },
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(channel, Modifier.width(64.dp).height(44.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (favorite) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFC233), modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (channel.geoBlocked) Icon(Icons.Rounded.Public, "Geo-blocked", tint = cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
            channel.qualityBadge?.let { if (it != "SD") Badge(it) }
        }
        if (trailing != null) trailing()
        else IconButton(onClick = onLongClick) { Icon(Icons.Rounded.MoreVert, "More", tint = cs.onSurfaceVariant) }
    }
}

@Composable
fun ChannelTile(
    channel: Channel,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val corner = LocalStyle.current.corner * 0.8f
    Column(
        modifier
            .padding(6.dp)
            .focusRing(RoundedCornerShape(corner), scale = 1.06f, onMenu = onLongClick)
            .clip(RoundedCornerShape(corner))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() },
            )
            .padding(4.dp),
    ) {
        Box {
            ChannelLogo(channel, Modifier.fillMaxWidth().aspectRatio(16f / 11f), corner = corner)
            Row(Modifier.align(Alignment.TopEnd).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (favorite) Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                channel.qualityBadge?.let { q ->
                    if (q != "SD") Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.62f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(q, style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            channel.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Compact square card for horizontal rails (recents, favourites). */
@Composable
fun RailCard(channel: Channel, onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val corner = LocalStyle.current.corner * 0.8f
    Column(
        Modifier
            .width(112.dp)
            .focusRing(RoundedCornerShape(corner), scale = 1.06f, onMenu = onLongClick)
            .clip(RoundedCornerShape(corner))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() },
            )
            .padding(4.dp),
    ) {
        ChannelLogo(channel, Modifier.fillMaxWidth().aspectRatio(16f / 11f), corner = corner)
        Spacer(Modifier.height(6.dp))
        Text(channel.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

fun subtitleFor(c: Channel, catalog: Catalog): String {
    if (c.isCustom) return c.sources.firstOrNull()?.label ?: "Custom stream"
    val parts = ArrayList<String>(3)
    if (c.country.isNotEmpty()) {
        val flag = catalog.flag(c.country)
        parts.add(if (flag.isNotEmpty()) "$flag ${catalog.countryName(c.country)}" else catalog.countryName(c.country))
    }
    c.categories.firstOrNull()?.let { parts.add(catalog.categoryName(it)) }
    c.languages.firstOrNull()?.let { parts.add(catalog.languageName(it)) }
    if (c.sources.size > 1) parts.add("${c.sources.size} sources")
    return parts.joinToString(" · ").ifEmpty { "Uncategorised" }
}

private fun monogram(name: String): String {
    val words = name.split(' ', '-', '.').filter { it.isNotBlank() && it[0].isLetterOrDigit() }
    return when {
        words.isEmpty() -> name.take(2).uppercase()
        words.size == 1 -> words[0].take(3).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

private val MonoColors = listOf(
    Color(0xFF5B3FFF), Color(0xFF0A84FF), Color(0xFF00897B), Color(0xFF2E7D32),
    Color(0xFFEF6C00), Color(0xFFD81B60), Color(0xFF6D4C41), Color(0xFF8E24AA),
)

private fun monogramColor(name: String): Color = MonoColors[abs(name.hashCode()) % MonoColors.size]
