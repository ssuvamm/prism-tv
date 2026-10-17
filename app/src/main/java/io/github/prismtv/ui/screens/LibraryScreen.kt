package io.github.prismtv.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.prismtv.AppGraph
import io.github.prismtv.data.Channel
import io.github.prismtv.data.M3u
import io.github.prismtv.data.user.UserPlaylist
import io.github.prismtv.ui.MainViewModel
import io.github.prismtv.ui.Route
import io.github.prismtv.ui.SheetContext
import io.github.prismtv.ui.Tab
import io.github.prismtv.ui.components.ChannelGrid
import io.github.prismtv.ui.components.ChannelLogo
import io.github.prismtv.ui.components.ConfirmDialog
import io.github.prismtv.ui.components.EmptyState
import io.github.prismtv.ui.components.LargeTitle
import io.github.prismtv.ui.components.Panel
import io.github.prismtv.ui.components.Segmented
import io.github.prismtv.ui.components.TextInputDialog
import io.github.prismtv.ui.components.countLabel
import io.github.prismtv.ui.components.fullItem
import io.github.prismtv.ui.components.focusRing
import io.github.prismtv.ui.theme.LocalStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

private enum class LibTab(val label: String) { FAVORITES("Favourites"), PLAYLISTS("Playlists"), HISTORY("History") }

@Composable
fun LibraryScreen(vm: MainViewModel, bottomPadding: PaddingValues) {
    val ctx = LocalContext.current
    val v by vm.visible.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val favKeys by vm.favoriteKeys.collectAsStateWithLifecycle()
    val data by vm.userData.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(LibTab.FAVORITES) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val grid = data.settings.gridView
    val pad = PaddingValues(bottom = bottomPadding.calculateBottomPadding() + 12.dp, start = if (grid) 8.dp else 0.dp, end = if (grid) 8.dp else 0.dp)

    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val items = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { M3u.parse(it.readText()) } }.getOrNull()
            }
            if (items.isNullOrEmpty()) Toast.makeText(ctx, "No streams found in that file", Toast.LENGTH_SHORT).show()
            else {
                val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Imported"
                vm.user.importPlaylist(name, items)
                Toast.makeText(ctx, "Imported ${items.size} streams", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LargeTitle("Library")
        Segmented(LibTab.entries, tab, { it.label }, { tab = it }, Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp))

        when (tab) {
            LibTab.FAVORITES -> {
                val state = rememberLazyGridState()
                LaunchedEffect(Unit) { vm.scrollToTop.collect { if (it == Tab.LIBRARY) state.animateScrollToItem(0) } }
                ChannelGrid(
                    favorites, v.catalog, favKeys, grid, state,
                    onClick = { vm.play(ctx, it, favorites) }, onLongClick = { vm.openSheet(it) }, contentPadding = pad,
                    header = {
                        if (favorites.isEmpty()) fullItem("#empty") {
                            EmptyState(Icons.Rounded.StarBorder, "No favourites yet", "Long-press any channel and pick \"Add to favourites\".")
                        }
                    },
                )
            }
            LibTab.HISTORY -> {
                val state = rememberLazyGridState()
                ChannelGrid(
                    history, v.catalog, favKeys, grid, state,
                    onClick = { vm.play(ctx, it, history) }, onLongClick = { vm.openSheet(it, SheetContext.InHistory) }, contentPadding = pad,
                    header = {
                        if (history.isEmpty()) fullItem("#empty") {
                            EmptyState(Icons.Rounded.History, "Nothing watched yet", "Channels you play show up here.")
                        } else fullItem("#clear") {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { dialog = "clear-history" }) {
                                    Icon(Icons.Rounded.DeleteSweep, null); Spacer(Modifier.width(6.dp)); Text("Clear history")
                                }
                            }
                        }
                    },
                )
            }
            LibTab.PLAYLISTS -> {
                val state = rememberLazyListState()
                LazyColumn(state = state, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding.calculateBottomPadding() + 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item(key = "#actions") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { dialog = "new" }) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("New") }
                            FilledTonalButton(onClick = { importFile.launch(arrayOf("*/*")) }) { Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(6.dp)); Text("Import file") }
                            FilledTonalButton(onClick = { dialog = "import-url" }) { Icon(Icons.Rounded.Link, null); Spacer(Modifier.width(6.dp)); Text("URL") }
                        }
                    }
                    if (data.playlists.isEmpty()) item(key = "#empty") {
                        EmptyState(Icons.AutoMirrored.Rounded.PlaylistPlay, "No playlists", "Make your own line-up, or import an M3U playlist from a file or link.")
                    }
                    items(data.playlists, key = { it.id }) { p ->
                        PlaylistCard(p, p.keys.take(4).mapNotNull { v.find(it) }) { vm.push(Route.Playlist(p.id)) }
                    }
                }
            }
        }
    }

    when (dialog) {
        "new" -> TextInputDialog("New playlist", "Name", "Create", onConfirm = { vm.user.createPlaylist(it); dialog = null }, onDismiss = { dialog = null })
        "clear-history" -> ConfirmDialog("Clear history?", "This removes every channel from your watch history.", "Clear", { vm.user.clearHistory() }) { dialog = null }
        "import-url" -> TextInputDialog(
            "Import M3U from link", "Playlist name", "Import", secondLabel = "M3U URL",
            onConfirm = {},
            onConfirm2 = { name, url ->
                dialog = null
                scope.launch {
                    val items = withContext(Dispatchers.IO) {
                        runCatching {
                            AppGraph.net.client.newCall(Request.Builder().url(url.trim()).build()).execute().use { M3u.parse(it.body.string()) }
                        }.getOrNull()
                    }
                    if (items.isNullOrEmpty()) Toast.makeText(ctx, "Couldn't read a playlist from that link", Toast.LENGTH_SHORT).show()
                    else {
                        vm.user.importPlaylist(name, items)
                        Toast.makeText(ctx, "Imported ${items.size} streams", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun PlaylistCard(p: UserPlaylist, preview: List<Channel>, onClick: () -> Unit) {
    Panel(Modifier.fillMaxWidth().focusRing(RoundedCornerShape(LocalStyle.current.corner), 1.01f).clip(RoundedCornerShape(LocalStyle.current.corner)).clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(84.dp).height(52.dp)) {
                if (preview.isEmpty()) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp).align(Alignment.Center))
                }
                preview.take(3).forEachIndexed { i, ch ->
                    ChannelLogo(ch, Modifier.offset(x = (i * 16).dp, y = (i * 4).dp).width(52.dp).height(38.dp), corner = 8.dp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(countLabel(p.keys.size, "channel"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PlaylistScreen(vm: MainViewModel, route: Route.Playlist, bottomPadding: PaddingValues) {
    val ctx = LocalContext.current
    val data by vm.userData.collectAsStateWithLifecycle()
    val v by vm.visible.collectAsStateWithLifecycle()
    val favKeys by vm.favoriteKeys.collectAsStateWithLifecycle()
    val playlist = data.playlists.find { it.id == route.id }
    val flow = remember(route.id) { vm.playlistChannels(route.id) }
    val channels by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val state = rememberLazyGridState()
    val grid = data.settings.gridView
    val scope = rememberCoroutineScope()

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = M3u.export(channels, v.catalog)
        scope.launch(Dispatchers.IO) {
            val ok = runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }.isSuccess
            withContext(Dispatchers.Main) { Toast.makeText(ctx, if (ok) "Playlist exported" else "Export failed", Toast.LENGTH_SHORT).show() }
        }
    }

    if (playlist == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = 4.dp)) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text(playlist.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(countLabel(channels.size, "channel"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (channels.isNotEmpty()) IconButton(onClick = { val c = channels.random(); vm.play(ctx, c, channels) }) { Icon(Icons.Rounded.Shuffle, "Play random") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Playlist options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Add stream link") }, { menu = false; dialog = "add" }, leadingIcon = { Icon(Icons.Rounded.Link, null) })
                    DropdownMenuItem({ Text("Rename") }, { menu = false; dialog = "rename" }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                    DropdownMenuItem({ Text("Export as M3U") }, { menu = false; export.launch("${playlist.name}.m3u") }, leadingIcon = { Icon(Icons.Rounded.Upload, null) })
                    DropdownMenuItem({ Text("Delete playlist") }, { menu = false; dialog = "delete" }, leadingIcon = { Icon(Icons.Rounded.Delete, null) })
                }
            }
        }
        ChannelGrid(
            channels, v.catalog, favKeys, grid, state,
            onClick = { vm.play(ctx, it, channels) },
            onLongClick = { vm.openSheet(it, SheetContext.InPlaylist(playlist.id)) },
            contentPadding = PaddingValues(bottom = bottomPadding.calculateBottomPadding() + 12.dp, start = if (grid) 8.dp else 0.dp, end = if (grid) 8.dp else 0.dp),
            header = {
                if (channels.isEmpty()) fullItem("#empty") {
                    EmptyState(
                        Icons.AutoMirrored.Rounded.PlaylistPlay, "Empty playlist",
                        "Long-press any channel and choose \"Add to playlist\", or add your own stream link.",
                    ) { Button(onClick = { dialog = "add" }) { Text("Add stream link") } }
                }
            },
        )
    }

    when (dialog) {
        "rename" -> TextInputDialog("Rename playlist", "Name", "Save", initial = playlist.name, onConfirm = { vm.user.renamePlaylist(playlist.id, it); dialog = null }, onDismiss = { dialog = null })
        "delete" -> ConfirmDialog("Delete \"${playlist.name}\"?", "The channels stay in the app; only this list is removed.", "Delete", { vm.user.deletePlaylist(playlist.id); vm.back() }) { dialog = null }
        "add" -> TextInputDialog(
            "Add stream", "Name", "Add", secondLabel = "Stream URL (m3u8, mpd, mp4…)",
            onConfirm = {}, onConfirm2 = { name, url -> vm.user.addCustom(name, url, null, playlist.id); dialog = null },
            onDismiss = { dialog = null },
        )
    }
}
