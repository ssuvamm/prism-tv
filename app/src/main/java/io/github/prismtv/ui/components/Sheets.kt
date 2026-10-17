package io.github.prismtv.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.prismtv.data.Catalog
import io.github.prismtv.data.Channel
import io.github.prismtv.data.user.UserPlaylist
import io.github.prismtv.player.ExternalPlayers
import io.github.prismtv.ui.MainViewModel
import io.github.prismtv.ui.SheetContext

@Composable
fun ChannelActionsSheet(vm: MainViewModel, ch: Channel, catalog: Catalog, queue: () -> List<Channel>) {
    val ctx = LocalContext.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val favKeys by vm.favoriteKeys.collectAsStateWithLifecycle()
    val favorite = ch.key in favKeys
    val sheetContext = vm.sheetContext
    var showSources by remember { mutableStateOf(false) }
    fun close() { vm.sheetChannel = null }

    ModalBottomSheet(onDismissRequest = ::close, sheetState = state) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ChannelLogo(ch, Modifier.width(72.dp).height(50.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(ch.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(subtitleFor(ch, catalog), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)

            SheetAction(Icons.Rounded.PlayArrow, "Play") { close(); vm.play(ctx, ch, queue(), forceBuiltIn = true) }
            SheetAction(Icons.Rounded.SmartDisplay, "Play in VLC") { close(); vm.user.recordHistory(ch); ExternalPlayers.openVlc(ctx, ch) }
            SheetAction(Icons.AutoMirrored.Rounded.OpenInNew, "Open with another app") { close(); vm.user.recordHistory(ch); ExternalPlayers.openWith(ctx, ch) }
            if (ch.sources.size > 1) {
                SheetAction(Icons.AutoMirrored.Rounded.QueueMusic, "Choose source (${ch.sources.size})") { showSources = !showSources }
                if (showSources) ch.sources.forEachIndexed { i, s ->
                    Row(
                        Modifier.fillMaxWidth().clickable { close(); vm.play(ctx, ch, queue(), i, forceBuiltIn = true) }
                            .padding(start = 64.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(s.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (s.geoBlocked) Badge("Geo-blocked", MaterialTheme.colorScheme.error)
                        if (s.not247) Badge("Not 24/7", MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SheetAction(
                if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                if (favorite) "Remove from favourites" else "Add to favourites",
                tint = if (favorite) Color(0xFFFFB300) else null,
            ) { vm.user.toggleFavorite(ch); close() }
            SheetAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist") { close(); vm.playlistPickerFor = ch }

            when (sheetContext) {
                is SheetContext.InPlaylist -> {
                    SheetAction(Icons.Rounded.ArrowUpward, "Move up") { vm.user.movePlaylistItem(sheetContext.playlistId, ch.key, -1); close() }
                    SheetAction(Icons.Rounded.ArrowDownward, "Move down") { vm.user.movePlaylistItem(sheetContext.playlistId, ch.key, 1); close() }
                    SheetAction(Icons.Rounded.Delete, "Remove from this playlist") { vm.user.removeFromPlaylist(sheetContext.playlistId, ch.key); close() }
                }
                SheetContext.InHistory -> SheetAction(Icons.Rounded.Delete, "Remove from history") { vm.user.removeHistory(ch.key); close() }
                null -> Unit
            }

            SheetAction(Icons.Rounded.VisibilityOff, "Hide channel") { vm.user.hide(ch); close() }
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SheetAction(Icons.Rounded.ContentCopy, "Copy stream link") { ExternalPlayers.copyLink(ctx, ch); close() }
            SheetAction(Icons.Rounded.Share, "Share") { close(); ExternalPlayers.share(ctx, ch) }
            ch.website?.let { site -> SheetAction(Icons.Rounded.Language, "Visit website") { close(); ExternalPlayers.openUrl(ctx, site) } }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun SheetAction(icon: ImageVector, label: String, tint: Color? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().focusRing(RoundedCornerShape(10.dp), 1f).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun PlaylistPickerDialog(
    playlists: List<UserPlaylist>,
    onPick: (UserPlaylist) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(playlists.isEmpty()) }
    if (creating) {
        TextInputDialog(
            title = "New playlist",
            label = "Name",
            confirm = "Create",
            onConfirm = { onCreate(it) },
            onDismiss = { if (playlists.isEmpty()) onDismiss() else creating = false },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text("New playlist", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                items(playlists, key = { it.id }) { p ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Text(p.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("${p.keys.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    confirm: String,
    initial: String = "",
    secondLabel: String? = null,
    secondInitial: String = "",
    onConfirm: (String) -> Unit,
    onConfirm2: ((String, String) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var text2 by rememberSaveable { mutableStateOf(secondInitial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus))
                if (secondLabel != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(text2, { text2 = it }, label = { Text(secondLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank() && (secondLabel == null || text2.isNotBlank()),
                onClick = { if (onConfirm2 != null) onConfirm2(text, text2) else onConfirm(text) },
            ) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Searchable single-choice list used by the filter chips. */
@Composable
fun <T> PickerSheet(
    title: String,
    items: List<T>,
    label: (T) -> String,
    count: (T) -> Int?,
    selected: (T) -> Boolean,
    onPick: (T?) -> Unit,
    onDismiss: () -> Unit,
    searchable: Boolean = true,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var q by remember { mutableStateOf("") }
    val shown = remember(items, q) {
        if (q.isBlank()) items else items.filter { label(it).contains(q.trim(), ignoreCase = true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.navigationBarsPadding()) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onPick(null) }) { Text("Any") }
            }
            if (searchable) SearchField(q, { q = it }, "Search", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                items(shown) { item ->
                    val sel = selected(item)
                    Row(
                        Modifier.fillMaxWidth().focusRing(RoundedCornerShape(10.dp), 1f).clickable { onPick(item) }.padding(horizontal = 20.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label(item), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                            color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        count(item)?.let { Text("$it", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}
