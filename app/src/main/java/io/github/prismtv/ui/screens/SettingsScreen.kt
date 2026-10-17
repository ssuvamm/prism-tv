package io.github.prismtv.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Explicit
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.ImageNotSupported
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import io.github.prismtv.BuildConfig
import io.github.prismtv.data.DarkMode
import io.github.prismtv.data.DnsProvider
import io.github.prismtv.data.PlayerChoice
import io.github.prismtv.data.ThemeStyle
import io.github.prismtv.data.UserAgentChoice
import io.github.prismtv.data.catalog.SyncStatus
import io.github.prismtv.ui.MainViewModel
import io.github.prismtv.ui.Route
import io.github.prismtv.ui.components.ChannelRow
import io.github.prismtv.ui.components.EmptyState
import io.github.prismtv.ui.components.LargeTitle
import io.github.prismtv.ui.components.Panel
import io.github.prismtv.ui.components.focusRing
import io.github.prismtv.ui.theme.AccentPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(vm: MainViewModel, bottomPadding: PaddingValues) {
    val ctx = LocalContext.current
    val data by vm.userData.collectAsStateWithLifecycle()
    val catalog by vm.repo.catalog.collectAsStateWithLifecycle()
    val status by vm.repo.status.collectAsStateWithLifecycle()
    val s = data.settings
    val scope = rememberCoroutineScope()
    var choice by remember { mutableStateOf<ChoiceSpec<*>?>(null) }

    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = vm.user.exportJson()
        scope.launch(Dispatchers.IO) {
            val ok = runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }.isSuccess
            withContext(Dispatchers.Main) { Toast.makeText(ctx, if (ok) "Backup saved" else "Backup failed", Toast.LENGTH_SHORT).show() }
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull() }
            val ok = text != null && vm.user.importJson(text)
            Toast.makeText(ctx, if (ok) "Backup restored" else "That file isn't a Prism TV backup", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(bottom = bottomPadding.calculateBottomPadding() + 16.dp),
    ) {
        LargeTitle("Settings")

        Group("Appearance") {
            Text("Style", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeStyle.entries.forEach { st ->
                    StylePreview(st, st == s.style) { vm.user.updateSettings { it.copy(style = st) } }
                }
            }
            Text("Accent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AccentDot(null, s.accent == null) { vm.user.updateSettings { it.copy(accent = null) } }
                AccentPalette.forEach { c -> AccentDot(c, s.accent == c) { vm.user.updateSettings { it.copy(accent = c) } } }
            }
            val darkLocked = s.style == ThemeStyle.AMOLED || s.style == ThemeStyle.NEON
            Item(Icons.Rounded.BrightnessMedium, "Light / dark", if (darkLocked) "Always dark in this style" else s.darkMode.label, enabled = !darkLocked) {
                choice = ChoiceSpec("Light / dark", DarkMode.entries, s.darkMode, { it.label }) { m -> vm.user.updateSettings { it.copy(darkMode = m) } }
            }
            Toggle(Icons.Rounded.SwapHoriz, "Grid view", "Show channels as tiles instead of a list", s.gridView) { v -> vm.user.updateSettings { it.copy(gridView = v) } }
        }

        Group("Player") {
            Item(Icons.Rounded.PlayCircle, "Open channels with", s.defaultPlayer.label) {
                choice = ChoiceSpec("Open channels with", PlayerChoice.entries, s.defaultPlayer, { it.label }) { p -> vm.user.updateSettings { it.copy(defaultPlayer = p) } }
            }
            SliderItem(Icons.Rounded.HighQuality, "Preload before playing", "${s.preloadSeconds} s of video is buffered before playback starts (more = fewer stalls)", s.preloadSeconds, 2..15) { v ->
                vm.user.updateSettings { it.copy(preloadSeconds = v) }
            }
            SliderItem(Icons.Rounded.Timer, "Pause buffer (timeshift)", if (s.timeshiftMinutes == 0) "Off" else "Pause live TV for up to ${s.timeshiftMinutes} min, recorded to local cache", s.timeshiftMinutes, 0..15) { v ->
                vm.user.updateSettings { it.copy(timeshiftMinutes = v) }
            }
            Toggle(Icons.Rounded.SwapHoriz, "Try other sources automatically", "If a stream fails, switch to the next one for that channel", s.autoFallback) { v -> vm.user.updateSettings { it.copy(autoFallback = v) } }
            Toggle(Icons.Rounded.ScreenRotation, "Start in landscape", null, s.startLandscape) { v -> vm.user.updateSettings { it.copy(startLandscape = v) } }
            Toggle(Icons.Rounded.PictureInPicture, "Picture-in-picture on home", "Keep watching in a small window when you leave the app", s.autoPip) { v -> vm.user.updateSettings { it.copy(autoPip = v) } }
            Item(Icons.Rounded.Hub, "User agent", s.userAgent.label) {
                choice = ChoiceSpec("User agent", UserAgentChoice.entries, s.userAgent, { it.label }) { u -> vm.user.updateSettings { it.copy(userAgent = u) } }
            }
        }

        Group("Network") {
            Item(Icons.Rounded.Dns, "Private DNS", "${s.dns.label} · used for every request") {
                choice = ChoiceSpec("DNS over HTTPS", DnsProvider.entries, s.dns, { it.label }) { d -> vm.user.updateSettings { it.copy(dns = d) } }
            }
        }

        Group("Channels") {
            Item(Icons.Rounded.VisibilityOff, "Hidden channels", "${data.hidden.size} hidden") { vm.push(Route.Hidden) }
            Toggle(Icons.Rounded.Public, "Hide geo-blocked channels", "Channels that only work in their home country", s.hideGeoBlocked) { v -> vm.user.updateSettings { it.copy(hideGeoBlocked = v) } }
            Toggle(Icons.Rounded.Schedule, "Hide part-time channels", "Channels that are not on air 24/7", s.hideNot247) { v -> vm.user.updateSettings { it.copy(hideNot247 = v) } }
            Toggle(Icons.Rounded.Explicit, "Show adult channels", null, s.showNsfw) { v -> vm.user.updateSettings { it.copy(showNsfw = v) } }
        }

        Group("Channel database") {
            val updated = catalog?.updatedAt ?: 0L
            val sub = when (val st = status) {
                is SyncStatus.Running -> st.step
                is SyncStatus.Failed -> "Last update failed: ${st.message}"
                SyncStatus.Idle -> if (updated == 0L) "Never updated" else
                    "${catalog?.channels?.size ?: 0} channels · updated " + DateUtils.getRelativeTimeSpanString(updated, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            }
            Item(Icons.Rounded.CloudSync, "Update now", sub, enabled = status !is SyncStatus.Running) { vm.repo.refresh(userInitiated = true) }
            (status as? SyncStatus.Running)?.let { LinearProgressIndicator(progress = { it.progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) }
            Item(Icons.Rounded.Update, "Check for updates every", "${s.autoUpdateHours} hours, quietly in the background") {
                choice = ChoiceSpec("Check every", listOf(12, 24, 36, 48, 72, 168), s.autoUpdateHours, { if (it == 168) "Week" else "$it hours" }) { h -> vm.user.updateSettings { it.copy(autoUpdateHours = h) } }
            }
            Item(Icons.Rounded.ImageNotSupported, "Clear logo cache", "Logos will download again as you scroll") {
                scope.launch(Dispatchers.IO) {
                    val loader = SingletonImageLoader.get(ctx)
                    loader.memoryCache?.clear()
                    loader.diskCache?.clear()
                    withContext(Dispatchers.Main) { Toast.makeText(ctx, "Logo cache cleared", Toast.LENGTH_SHORT).show() }
                }
            }
        }

        Group("Backup") {
            Item(Icons.Rounded.Backup, "Back up favourites and playlists", "Saves a JSON file you can restore later") { backup.launch("prismtv-backup.json") }
            Item(Icons.Rounded.Restore, "Restore from backup", null) { restore.launch(arrayOf("application/json", "text/plain", "*/*")) }
        }

        Group("Support Prism TV") {
            Item(Icons.Rounded.VolunteerActivism, "Pay with PayPal", Donate.PAYPAL_EMAIL) {
                if (!Donate.open(ctx, Donate.PAYPAL_URL)) Donate.copy(ctx, "PayPal", Donate.PAYPAL_EMAIL)
            }
            Item(Icons.Rounded.CurrencyRupee, "Pay with UPI", Donate.UPI_ID) {
                // Some UPI apps refuse payments started by another app, so the ID is always copied too.
                Donate.copy(ctx, "UPI ID", Donate.UPI_ID)
                Donate.open(ctx, Donate.UPI_URL)
            }
        }

        Group("About") {
            Item(Icons.Rounded.Info, "About Prism TV", "Version ${BuildConfig.VERSION_NAME}") { vm.push(Route.About) }
        }
    }

    choice?.let { c -> ChoiceDialog(c) { choice = null } }
}

/** Donation links shown in Settings. Opening falls back to copying, e.g. on TVs without a browser or UPI app. */
private object Donate {
    const val PAYPAL_EMAIL = "ssuvamm@gmail.com"
    const val PAYPAL_URL = "https://www.paypal.com/cgi-bin/webscr?cmd=_xclick&business=ssuvamm%40gmail.com&item_name=Support+Prism+TV&currency_code=USD"
    const val UPI_ID = "ssuvamm@oksbi"
    const val UPI_URL = "upi://pay?pa=ssuvamm@oksbi&pn=Prism%20TV&tn=Support%20Prism%20TV&cu=INR"

    fun open(ctx: Context, url: String): Boolean = try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    fun copy(ctx: Context, label: String, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(ctx, "$label copied: $text", Toast.LENGTH_LONG).show()
    }
}

class ChoiceSpec<T>(val title: String, val options: List<T>, val selected: T, val label: (T) -> String, val onPick: (T) -> Unit)

@Composable
private fun <T> ChoiceDialog(c: ChoiceSpec<T>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(c.title) },
        text = {
            Column {
                c.options.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .selectable(o == c.selected, role = Role.RadioButton) { c.onPick(o); onDismiss() }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(o == c.selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(c.label(o), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 8.dp),
    )
    Panel(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

@Composable
private fun Item(icon: ImageVector, title: String, subtitle: String?, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().focusRing(RoundedCornerShape(12.dp), 1f).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f))
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Toggle(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().focusRing(RoundedCornerShape(12.dp), 1f).clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderItem(icon: ImageVector, title: String, subtitle: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            modifier = Modifier.padding(start = 40.dp),
        )
    }
}

@Composable
private fun StylePreview(style: ThemeStyle, selected: Boolean, onClick: () -> Unit) {
    val colors = when (style) {
        ThemeStyle.MATERIAL_YOU -> listOf(Color(0xFF6750A4), Color(0xFFE8DEF8))
        ThemeStyle.GLASS -> listOf(Color(0xFF6A4CFF), Color(0xFF00B4FF))
        ThemeStyle.LIQUID -> listOf(Color(0xFF0A84FF), Color(0xFFFF375F))
        ThemeStyle.AMOLED -> listOf(Color(0xFF000000), Color(0xFF263238))
        ThemeStyle.NEON -> listOf(Color(0xFFFF2BD6), Color(0xFF00E5FF))
        ThemeStyle.SUNSET -> listOf(Color(0xFFFF6B3D), Color(0xFFFF3B7F))
    }
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.width(112.dp).focusRing(shape, 1.05f).clip(shape).clickable(onClick = onClick)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(colors))) {
            Box(Modifier.padding(8.dp).width(56.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(Color.White.copy(alpha = 0.35f)))
            Box(Modifier.align(Alignment.BottomStart).padding(8.dp).width(70.dp).height(18.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.22f)))
            if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(style.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Text(style.blurb, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, minLines = 2)
    }
}

@Composable
private fun AccentDot(color: Long?, selected: Boolean, onClick: () -> Unit) {
    val c = color?.let { Color(it) }
    Box(
        Modifier.size(38.dp).clip(CircleShape)
            .background(c?.let { Brush.linearGradient(listOf(it, it)) } ?: Brush.sweepGradient(listOf(Color(0xFFFF5C8A), Color(0xFFFFC857), Color(0xFF5CF2B5), Color(0xFF6BD5FF), Color(0xFFA990FF), Color(0xFFFF5C8A))))
            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
            .focusRing(CircleShape, 1.15f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun HiddenScreen(vm: MainViewModel, bottomPadding: PaddingValues) {
    val hidden by vm.hiddenChannels.collectAsStateWithLifecycle()
    val catalog by vm.repo.catalog.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = 8.dp)) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Hidden channels", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (hidden.isNotEmpty()) TextButton(onClick = { vm.user.unhideAll() }) { Text("Show all") }
        }
        if (hidden.isEmpty()) {
            EmptyState(Icons.Rounded.VisibilityOff, "Nothing hidden", "Long-press a channel and choose \"Hide channel\" to tuck it away. It will show up here.")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = bottomPadding.calculateBottomPadding() + 12.dp)) {
                items(hidden, key = { it.key }) { ch ->
                    ChannelRow(ch, catalog ?: return@items, false, onClick = {}, onLongClick = {}, trailing = {
                        TextButton(onClick = { vm.user.unhide(ch.key) }) { Text("Unhide") }
                    })
                }
            }
        }
    }
}
