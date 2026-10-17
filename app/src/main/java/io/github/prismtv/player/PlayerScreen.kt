package io.github.prismtv.player

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import io.github.prismtv.ui.components.focusRing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.github.prismtv.AppGraph
import io.github.prismtv.ui.components.ChannelLogo
import kotlinx.coroutines.delay
import java.util.Locale

private sealed interface Hud {
    data class Brightness(val level: Float) : Hud
    data class Volume(val level: Float) : Hud
    data class Text(val text: String) : Hud
}

private enum class PlayerDialog { SOURCES, QUALITY, AUDIO, SUBTITLES, SLEEP, INFO }

private val RESIZE_MODES = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Crop to fill",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch",
)

@Composable
fun PlayerScreen(vm: PlayerViewModel, activity: PlayerActivity) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val data by AppGraph.user.data.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val inPip = activity.inPip

    var controls by activity::controlsVisible
    val isTv = AppGraph.isTv
    val playFocus = remember { FocusRequester() }
    var touchedAt by remember { mutableLongStateOf(0L) }
    var locked by rememberSaveable { mutableStateOf(false) }
    val resize = data.settings.resizeMode.coerceIn(0, RESIZE_MODES.lastIndex)
    fun setResize(m: Int) = AppGraph.user.updateSettings { it.copy(resizeMode = m) }
    var hud by remember { mutableStateOf<Hud?>(null) }
    var hudAt by remember { mutableLongStateOf(0L) }
    var panel by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<PlayerDialog?>(null) }
    var menu by remember { mutableStateOf(false) }
    var openMenu by remember { mutableStateOf(false) }

    // Playback position, polled while visible.
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(C.TIME_UNSET) }
    var recorded by remember { mutableLongStateOf(0L) }
    var pausedFor by remember { mutableLongStateOf(0L) }
    LaunchedEffect(controls, ui.channel) {
        while (true) {
            position = vm.player.currentPosition
            duration = vm.player.duration
            recorded = vm.recordedMs()
            pausedFor = vm.pausedForMs()
            delay(if (controls) 500 else 1500)
        }
    }

    // Hide after a few seconds whenever playback is wanted, even while it is still buffering.
    LaunchedEffect(controls, touchedAt, ui.playWhenReady, panel, menu, openMenu, dialog) {
        if (controls && ui.playWhenReady && ui.error == null && !panel && !menu && !openMenu && dialog == null) {
            delay(4000)
            controls = false
        }
    }
    LaunchedEffect(hudAt) {
        if (hud != null) { delay(900); hud = null }
    }
    LaunchedEffect(ui.videoWidth, ui.videoHeight, ui.isPlaying) { activity.refreshPipParams() }
    LaunchedEffect(controls) { if (!controls) activity.hideSystemBars() }

    val favorite = ui.channel?.key?.let { it in data.favorites } == true
    fun touch() { touchedAt = System.nanoTime() }

    BackHandler(enabled = panel) { panel = false }
    // On a TV the first Back press just hides the controls, like every other TV player.
    BackHandler(enabled = isTv && controls && !panel && dialog == null && ui.error == null) { controls = false }

    // Remote users need focus somewhere visible: put it on Play whenever the controls appear.
    LaunchedEffect(controls, activity.remoteWake) {
        if (isTv && controls && ui.error == null) {
            delay(80)
            runCatching { playFocus.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { touch(); false }) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = false
                    setKeepContentOnPlayerReset(true)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    player = vm.player
                }
            },
            update = { it.resizeMode = RESIZE_MODES[resize].first },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        if (!inPip) {
            // Gesture layer: tap toggles controls, double-tap seeks, vertical swipes adjust brightness (left) and volume (right),
            // pinch switches between fit and crop.
            val edge = with(density) { 48.dp.toPx() }
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(locked) {
                        detectTapGestures(
                            onTap = { controls = !controls; touch() },
                            onDoubleTap = { off ->
                                if (!locked) {
                                    val forward = off.x > size.width / 2
                                    val ui0 = vm.ui.value
                                    if (ui0.timeshift || ui0.recording || !ui0.isLive) {
                                        vm.seekBy(if (forward) 10_000 else -10_000)
                                        hud = Hud.Text(if (forward) "+10 s" else "-10 s"); hudAt = System.nanoTime()
                                    } else vm.togglePlay()
                                }
                            },
                        )
                    }
                    .pointerInput(locked) {
                        if (locked) return@pointerInput
                        var left = true
                        var level = 0f
                        var active = false
                        detectVerticalDragGestures(
                            onDragStart = { off ->
                                active = off.y > edge && off.y < size.height - edge
                                left = off.x < size.width / 2
                                level = if (left) activity.currentBrightness() else activity.currentVolume()
                            },
                            onDragEnd = { active = false },
                            onDragCancel = { active = false },
                        ) { change, dy ->
                            if (!active) return@detectVerticalDragGestures
                            change.consume()
                            level = (level - dy / (size.height * 0.75f)).coerceIn(0f, 1f)
                            if (left) {
                                activity.setBrightness(level); hud = Hud.Brightness(level)
                            } else {
                                activity.setVolume(level); hud = Hud.Volume(level)
                            }
                            hudAt = System.nanoTime()
                        }
                    }
                    .pointerInput(locked) {
                        if (locked) return@pointerInput
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var zoom = 1f
                            do {
                                val ev = awaitPointerEvent()
                                if (ev.changes.count { it.pressed } >= 2) zoom *= ev.calculateZoom()
                            } while (ev.changes.any { it.pressed })
                            if (zoom > 1.15f && resize != 1) { setResize(1); hud = Hud.Text("Crop to fill"); hudAt = System.nanoTime() }
                            else if (zoom < 0.87f && resize != 0) { setResize(0); hud = Hud.Text("Fit"); hudAt = System.nanoTime() }
                        }
                    },
            )
        }

        // Only show the spinner when buffering lasts long enough to notice; brief stalls stay invisible.
        var showSpinner by remember { mutableStateOf(false) }
        LaunchedEffect(ui.buffering) {
            if (ui.buffering) { delay(450); showSpinner = true } else showSpinner = false
        }
        if (showSpinner && ui.buffering && ui.error == null && !(controls && !locked && !inPip)) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(if (inPip) 28.dp else 48.dp))
                if (!inPip) {
                    Spacer(Modifier.height(10.dp))
                    Text("Buffering…", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        hud?.let { h -> HudView(h, Modifier.align(if (h is Hud.Brightness) Alignment.CenterStart else if (h is Hud.Volume) Alignment.CenterEnd else Alignment.Center)) }

        ui.error?.let { err ->
            if (!inPip) ErrorView(
                err, ui.channel?.sources?.size ?: 0, ui.sourceIndex,
                onRetry = vm::retry,
                onNextSource = { vm.switchSource(ui.sourceIndex + 1) },
                onVlc = { ui.channel?.let { ExternalPlayers.openVlc(ctx, it, vm.currentSource ?: it.sources.first()) } },
                onBack = { activity.finish() },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (!inPip && locked && controls) {
            IconButton(
                onClick = { locked = false; touch() },
                modifier = Modifier.align(Alignment.CenterStart).safeDrawingPadding().padding(16.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
            ) { Icon(Icons.Rounded.Lock, "Unlock", tint = Color.White) }
        }

        AnimatedVisibility(!inPip && controls && !locked, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(140.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))))
                Box(Modifier.fillMaxWidth().height(170.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))))

                // Top bar
                Row(
                    Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(modifier = Modifier.focusRing(CircleShape, 1.12f), onClick = { activity.finish() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
                    ui.channel?.let { ch ->
                        ChannelLogo(ch, Modifier.width(52.dp).height(36.dp), corner = 8.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ch.name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val src = vm.currentSource
                            val sub = listOfNotNull(
                                // Feed name only; the database's nominal quality is often not what actually plays.
                                src?.label?.substringBefore(" · "),
                                if (ui.videoHeight > 0) "${ui.videoHeight}p" else null,
                                if (ch.sources.size > 1) "source ${ui.sourceIndex + 1}/${ch.sources.size}" else null,
                            ).joinToString(" · ")
                            Text(sub, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } ?: Spacer(Modifier.weight(1f))
                    if (ui.channel?.key?.startsWith("x:") == false) {
                        IconButton(modifier = Modifier.focusRing(CircleShape, 1.12f), onClick = { vm.toggleFavorite(); touch() }) {
                            Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Favourite", tint = if (favorite) Color(0xFFFFC233) else Color.White)
                        }
                    }
                    Box {
                        IconButton(modifier = Modifier.focusRing(CircleShape, 1.12f), onClick = { openMenu = true }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open in another app", tint = Color.White) }
                        DropdownMenu(openMenu, { openMenu = false }) {
                            val ch = ui.channel
                            val src = vm.currentSource
                            if (ch != null && src != null) {
                                DropdownMenuItem({ Text("VLC") }, { openMenu = false; vm.pause(); ExternalPlayers.openVlc(ctx, ch, src) }, leadingIcon = { Icon(Icons.Rounded.SmartDisplay, null) })
                                DropdownMenuItem({ Text("MX Player") }, { openMenu = false; vm.pause(); ExternalPlayers.openMx(ctx, ch, src) }, leadingIcon = { Icon(Icons.Rounded.SmartDisplay, null) })
                                DropdownMenuItem({ Text("Other app…") }, { openMenu = false; vm.pause(); ExternalPlayers.openWith(ctx, ch, src) }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) })
                            }
                        }
                    }
                    if (!isTv) IconButton(modifier = Modifier.focusRing(CircleShape, 1.12f), onClick = { activity.enterPip() }) { Icon(Icons.Rounded.PictureInPictureAlt, "Picture in picture", tint = Color.White) }
                    Box {
                        IconButton(modifier = Modifier.focusRing(CircleShape, 1.12f), onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More", tint = Color.White) }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(
                                { Text(ui.sleepAt?.let { "Sleep timer (${((it - System.currentTimeMillis()) / 60000) + 1} min)" } ?: "Sleep timer") },
                                { menu = false; dialog = PlayerDialog.SLEEP }, leadingIcon = { Icon(Icons.Rounded.Bedtime, null) },
                            )
                            DropdownMenuItem({ Text("Stream info") }, { menu = false; dialog = PlayerDialog.INFO }, leadingIcon = { Icon(Icons.Rounded.Info, null) })
                        }
                    }
                }

                // Centre transport (the error card takes this spot when something went wrong)
                if (ui.error == null) Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Live TV has nothing to skip through. The 10 second jumps only appear once the user has paused
                    // (the stream is being recorded) or is watching from the timeshift buffer, or for on-demand links.
                    val canSkip = ui.timeshift || ui.recording || !ui.isLive
                    if (canSkip) RoundButton(Icons.Rounded.Replay10, "Back 10 seconds") { vm.seekBy(-10_000); touch() }
                    RoundButton(if (ui.playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", big = true, loading = ui.buffering && ui.playWhenReady, modifier = Modifier.focusRequester(playFocus)) { vm.togglePlay(); touch() }
                    if (canSkip) RoundButton(Icons.Rounded.Forward10, "Forward 10 seconds") { vm.seekBy(10_000); touch() }
                }

                // Bottom bar
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    val seekable = duration != C.TIME_UNSET && duration > 0 && (ui.timeshift || !ui.isLive)
                    if (seekable) {
                        var dragging by remember { mutableStateOf(false) }
                        var dragValue by remember { mutableFloatStateOf(0f) }
                        Slider(
                            value = if (dragging) dragValue else (position.toFloat() / duration).coerceIn(0f, 1f),
                            onValueChange = { dragging = true; dragValue = it; touch() },
                            onValueChangeFinished = { vm.seekTo((dragValue * duration).toLong()); dragging = false },
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                        )
                    }
                    val status: @Composable () -> Unit = {
                        when {
                            ui.timeshift -> {
                                val behind = (duration - position).coerceAtLeast(0)
                                Pill("TIMESHIFT  -${fmt(behind)}", MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                FilledTonalButton(onClick = { vm.goLive(); touch() }, modifier = Modifier.focusRing(RoundedCornerShape(20.dp), 1.05f)) { Text("Go live") }
                            }
                            ui.isLive -> {
                                Pill(if (ui.recording && !ui.isPlaying) "PAUSED  ${fmt(pausedFor)}" else "LIVE", if (ui.recording && !ui.isPlaying) Color(0xFFFFA000) else Color(0xFFE53935))
                                if (ui.recording && !ui.isPlaying) {
                                    Spacer(Modifier.width(8.dp))
                                    Text("Recording to cache · ${fmt(recorded)} saved", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                }
                            }
                            seekable -> Text("${fmt(position)} / ${fmt(duration)}", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    // Source, quality, audio and subtitles live right in the player, not in a menu.
                    val trackButtons: @Composable () -> Unit = {
                        ui.channel?.let { ch ->
                            if (ch.sources.size > 1) BarButton(Icons.Rounded.SettingsInputAntenna, "Source ${ui.sourceIndex + 1}/${ch.sources.size}") { dialog = PlayerDialog.SOURCES }
                        }
                        BarButton(Icons.Rounded.HighQuality, ui.qualityLabel) { dialog = PlayerDialog.QUALITY }
                        if (ui.audioOptions > 1) BarButton(Icons.Rounded.Audiotrack, "Audio") { dialog = PlayerDialog.AUDIO }
                        if (ui.textOptions > 0) BarButton(Icons.Rounded.ClosedCaption, "Subtitles") { dialog = PlayerDialog.SUBTITLES }
                    }
                    val utilities: @Composable () -> Unit = {
                        if (!isTv) IconButton(modifier = Modifier.focusRing(CircleShape, 1.12f), onClick = { locked = true; touch() }) { Icon(Icons.Rounded.LockOpen, "Lock controls", tint = Color.White) }
                        IconButton(modifier = Modifier.focusRing(CircleShape, 1.12f), onClick = {
                            val next = (resize + 1) % RESIZE_MODES.size
                            setResize(next)
                            hud = Hud.Text(RESIZE_MODES[next].second); hudAt = System.nanoTime(); touch()
                        }) { Icon(Icons.Rounded.AspectRatio, "Resize", tint = Color.White) }
                        if (!isTv) IconButton(modifier = Modifier.focusRing(CircleShape, 1.12f), onClick = { activity.toggleOrientation(); touch() }) { Icon(Icons.Rounded.ScreenRotation, "Rotate", tint = Color.White) }
                        if (vm.queueChannels().isNotEmpty()) IconButton(modifier = Modifier.focusRing(CircleShape, 1.12f), onClick = { panel = true }) { Icon(Icons.AutoMirrored.Rounded.ViewList, "Channel list", tint = Color.White) }
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        if (maxWidth >= 640.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                status()
                                Spacer(Modifier.weight(1f))
                                trackButtons()
                                Spacer(Modifier.width(6.dp))
                                utilities()
                            }
                        } else {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) { status() }
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) { trackButtons() }
                                    utilities()
                                }
                            }
                        }
                    }
                }
            }
        }

        ui.message?.let { msg ->
            Box(
                Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 64.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.72f)).padding(horizontal = 16.dp, vertical = 10.dp),
            ) { Text(msg, color = Color.White, style = MaterialTheme.typography.bodyMedium) }
        }

        AnimatedVisibility(
            panel && !inPip,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            ChannelPanel(vm, ui) { panel = false }
        }
    }

    when (dialog) {
        PlayerDialog.SOURCES -> ui.channel?.let { ch ->
            OptionsDialog("Sources", ch.sources.mapIndexed { i, s ->
                buildString {
                    append(s.label)
                    if (s.geoBlocked) append("  (geo-blocked)")
                    if (s.not247) append("  (not 24/7)")
                } to (i == ui.sourceIndex)
            }, onPick = { vm.switchSource(it) }) { dialog = null }
        }
        PlayerDialog.QUALITY -> TrackDialog("Quality", vm.videoTracks(), { vm.selectTrack(C.TRACK_TYPE_VIDEO, it) }) { dialog = null }
        PlayerDialog.AUDIO -> TrackDialog("Audio track", vm.audioTracks(), { vm.selectTrack(C.TRACK_TYPE_AUDIO, it) }) { dialog = null }
        PlayerDialog.SUBTITLES -> TrackDialog("Subtitles", vm.textTracks(), { vm.selectTrack(C.TRACK_TYPE_TEXT, it) }) { dialog = null }
        PlayerDialog.SLEEP -> {
            val opts = listOf(null, 15, 30, 45, 60, 90, 120)
            OptionsDialog("Sleep timer", opts.map { (if (it == null) "Off" else "$it minutes") to false }, onPick = { vm.setSleepTimer(opts[it]) }) { dialog = null }
        }
        PlayerDialog.INFO -> InfoDialog(vm) { dialog = null }
        null -> Unit
    }
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    desc: String,
    big: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val size = if (big) 72.dp else 52.dp
    Box(
        modifier.size(size).focusRing(CircleShape, 1.12f).clip(CircleShape).background(Color.Black.copy(alpha = if (big) 0.45f else 0.3f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.fillMaxSize().padding(4.dp))
        Icon(icon, desc, tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f), modifier = Modifier.size(if (big) 42.dp else 28.dp))
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.padding(horizontal = 3.dp).focusRing(shape, 1.06f).clip(shape)
            .background(Color.White.copy(alpha = 0.14f)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(color).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HudView(h: Hud, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    when (h) {
        is Hud.Text -> Box(modifier.clip(shape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text(h.text, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        is Hud.Brightness, is Hud.Volume -> {
            val level = if (h is Hud.Brightness) h.level else (h as Hud.Volume).level
            val icon = if (h is Hud.Brightness) Icons.Rounded.BrightnessMedium else Icons.AutoMirrored.Rounded.VolumeUp
            Column(
                modifier.padding(horizontal = 36.dp).width(52.dp).clip(shape).background(Color.Black.copy(alpha = 0.6f)).padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, null, tint = Color.White)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.width(6.dp).height(130.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.25f))) {
                    Box(Modifier.align(Alignment.BottomCenter).width(6.dp).fillMaxHeight(level).clip(RoundedCornerShape(3.dp)).background(Color.White))
                }
                Spacer(Modifier.height(8.dp))
                Text("${(level * 100).toInt()}", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ErrorView(
    message: String,
    sources: Int,
    current: Int,
    onRetry: () -> Unit,
    onNextSource: () -> Unit,
    onVlc: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.padding(32.dp).clip(RoundedCornerShape(24.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Can't play this channel", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(message, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRetry) { Icon(Icons.Rounded.Replay, null); Spacer(Modifier.width(6.dp)); Text("Retry") }
            if (current + 1 < sources) FilledTonalButton(onClick = onNextSource) { Text("Next source") }
            OutlinedButton(onClick = onVlc) { Text("Open in VLC", color = Color.White) }
        }
        TextButton(onClick = onBack) { Text("Back to channels", color = Color.White.copy(alpha = 0.8f)) }
    }
}

@Composable
private fun ChannelPanel(vm: PlayerViewModel, ui: PlayerUi, onClose: () -> Unit) {
    val channels = remember(ui.channel) { vm.queueChannels() }
    val start = remember(channels, ui.channel) { channels.indexOfFirst { it.key == ui.channel?.key }.coerceAtLeast(0) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (start - 3).coerceAtLeast(0))
    val currentFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(120); runCatching { currentFocus.requestFocus() } }
    Column(
        Modifier.fillMaxHeight().width(320.dp).background(Color.Black.copy(alpha = 0.85f)).safeDrawingPadding(),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Channels", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close") }
        }
        LazyColumn(state = state) {
            items(channels, key = { it.key }) { ch ->
                val current = ch.key == ui.channel?.key
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (current) Modifier.focusRequester(currentFocus) else Modifier)
                        .focusRing(RoundedCornerShape(8.dp), 1f)
                        .background(if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent)
                        .clickable { vm.open(ch.key, 0) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChannelLogo(ch, Modifier.width(48.dp).height(34.dp), corner = 6.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(ch.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun OptionsDialog(title: String, options: List<Pair<String, Boolean>>, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(options.size) { i ->
                    val (label, sel) = options[i]
                    Row(
                        Modifier.fillMaxWidth().focusRing(RoundedCornerShape(10.dp), 1f).clip(RoundedCornerShape(10.dp)).clickable { onPick(i); onDismiss() }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(sel, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun TrackDialog(title: String, tracks: List<TrackOption>, onPick: (TrackOption) -> Unit, onDismiss: () -> Unit) {
    if (tracks.size <= 1) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text("This stream offers only one option.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        return
    }
    OptionsDialog(title, tracks.map { it.label to it.selected }, { onPick(tracks[it]) }, onDismiss)
}

@Composable
private fun InfoDialog(vm: PlayerViewModel, onDismiss: () -> Unit) {
    val p = vm.player
    val v = p.videoFormat
    val a = p.audioFormat
    val lines = listOfNotNull(
        vm.currentSource?.url?.let { "URL" to it },
        v?.let { "Video" to listOfNotNull(it.sampleMimeType, if (it.width > 0) "${it.width}×${it.height}" else null, if (it.frameRate > 0) "${it.frameRate.toInt()} fps" else null).joinToString(" · ") },
        v?.bitrate?.takeIf { it > 0 }?.let { "Video bitrate" to String.format(Locale.US, "%.2f Mbps", it / 1e6) },
        a?.let { "Audio" to listOfNotNull(it.sampleMimeType, if (it.channelCount > 0) "${it.channelCount} ch" else null, if (it.sampleRate > 0) "${it.sampleRate} Hz" else null).joinToString(" · ") },
        "Buffered" to "${((p.bufferedPosition - p.currentPosition).coerceAtLeast(0) / 1000)} s",
        "DNS" to AppGraph.net.provider.label,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stream info") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lines.forEach { (k, value) ->
                    Column {
                        Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
}
