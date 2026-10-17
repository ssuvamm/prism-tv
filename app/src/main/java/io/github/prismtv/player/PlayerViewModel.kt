package io.github.prismtv.player

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import android.util.Log
import io.github.prismtv.AppGraph
import io.github.prismtv.BuildConfig
import io.github.prismtv.data.Channel
import io.github.prismtv.data.Source
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Immutable
data class PlayerUi(
    val channel: Channel? = null,
    val sourceIndex: Int = 0,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = true,
    val buffering: Boolean = true,
    val error: String? = null,
    val isLive: Boolean = true,
    val timeshift: Boolean = false,
    val recording: Boolean = false,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val message: String? = null,
    val sleepAt: Long? = null,
    val hasPrev: Boolean = false,
    val hasNext: Boolean = false,
    /** Number of selectable qualities / audio tracks / subtitle tracks (not counting "Auto"/"Off"). */
    val videoOptions: Int = 0,
    val audioOptions: Int = 0,
    val textOptions: Int = 0,
    val qualityLabel: String = "Auto",
)

@Immutable
data class TrackOption(val label: String, val group: Tracks.Group?, val index: Int, val selected: Boolean)

@OptIn(UnstableApi::class)
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val settings get() = AppGraph.user.settings
    private val http = OkHttpDataSource.Factory(AppGraph.net.client)
    private val dataSources = DefaultDataSource.Factory(app, http)

    val player: ExoPlayer = run {
        val preloadMs = settings.preloadSeconds.coerceIn(1, 15) * 1000
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ maxOf(30_000, preloadMs * 3),
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ preloadMs,
                /* bufferForPlaybackAfterRebufferMs = */ preloadMs,
            )
            .setBackBuffer(60_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        ExoPlayer.Builder(app)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSources))
            .setLoadControl(loadControl)
            .setRenderersFactory(DefaultRenderersFactory(app).setEnableDecoderFallback(true))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
    }

    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()

    private var queue: List<String> = emptyList()
    private var triedAltMime = false
    private var recorder: TimeshiftRecorder? = null
    private var pausedAt = 0L
    private var sleepJob: Job? = null
    private var messageJob: Job? = null
    private var userPaused = false

    val currentSource: Source? get() = _ui.value.channel?.sources?.getOrNull(_ui.value.sourceIndex)

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                _ui.update {
                    it.copy(
                        buffering = state == Player.STATE_BUFFERING,
                        error = if (state == Player.STATE_READY) null else it.error,
                        isLive = if (state == Player.STATE_READY) player.isCurrentMediaItemLive else it.isLive,
                    )
                }
                if (BuildConfig.DEBUG && state == Player.STATE_READY) {
                    Log.d(TAG, "ready live=${player.isCurrentMediaItemLive} timeshift=${_ui.value.timeshift} pos=${player.currentPosition} dur=${player.duration}")
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                _ui.update { it.copy(playWhenReady = playWhenReady) }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _ui.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                val w = (size.width * size.pixelWidthHeightRatio).toInt()
                _ui.update { it.copy(videoWidth = w, videoHeight = size.height) }
                refreshTracks()
            }

            override fun onTracksChanged(tracks: Tracks) = refreshTracks()

            override fun onPlayerError(error: PlaybackException) = handleError(error)
        })
    }

    // region opening channels

    fun open(key: String, sourceIndex: Int) {
        AppGraph.catalog.find(key)?.let { ch ->
            queue = PlayQueue.keys
            play(ch, sourceIndex)
            return
        }
        // Cold start (e.g. restored after the process was killed): wait for the catalog to load from disk.
        viewModelScope.launch {
            AppGraph.catalog.loaded.first { it }
            val ch = AppGraph.catalog.find(key)
            if (ch == null) {
                _ui.update { it.copy(error = "This channel is no longer available.", buffering = false) }
            } else {
                queue = PlayQueue.keys
                play(ch, sourceIndex)
            }
        }
    }

    fun openUrl(url: String, title: String?) {
        val name = title ?: Uri.parse(url).host ?: "Stream"
        val ch = Channel(
            "x:$url", name, emptyList(), null, null, "", emptyList(), emptyList(), false, null,
            listOf(Source(url, "Link", null, 0, null, null, 0)), isCustom = true,
        )
        queue = emptyList()
        play(ch, 0)
    }

    private fun play(ch: Channel, sourceIndex: Int) {
        stopRecorder()
        userPaused = false
        val idx = queue.indexOf(ch.key)
        _ui.update {
            PlayerUi(
                channel = ch, sourceIndex = sourceIndex.coerceIn(0, (ch.sources.size - 1).coerceAtLeast(0)),
                sleepAt = it.sleepAt, hasPrev = idx > 0, hasNext = idx >= 0 && idx < queue.lastIndex,
            )
        }
        if (!ch.key.startsWith("x:")) AppGraph.user.recordHistory(ch)
        triedAltMime = false
        playSource()
    }

    private fun headersFor(s: Source): Map<String, String> = buildMap {
        put("User-Agent", s.userAgent ?: settings.userAgent.value)
        s.referrer?.let { ref ->
            put("Referer", ref)
            Uri.parse(ref).let { u -> if (u.scheme != null && u.host != null) put("Origin", "${u.scheme}://${u.host}") }
        }
    }

    private fun playSource() {
        val ch = _ui.value.channel ?: return
        val s = ch.sources.getOrNull(_ui.value.sourceIndex) ?: return
        val headers = headersFor(s)
        http.setUserAgent(headers.getValue("User-Agent"))
        http.setDefaultRequestProperties(headers - "User-Agent")
        // Sit comfortably behind the live edge so the preload buffer can always fill, and never speed up to
        // chase the edge. Chasing it is what causes endless re-buffering and quality flapping on live streams.
        val preloadMs = settings.preloadSeconds.coerceIn(1, 15) * 1000L
        val live = MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(preloadMs + 10_000)
            .setMinOffsetMs(preloadMs + 2_000)
            .setMinPlaybackSpeed(0.97f)
            .setMaxPlaybackSpeed(1f)
            .build()
        val item = MediaItem.Builder()
            .setUri(s.url)
            .setLiveConfiguration(live)
            .setMimeType(mimeFor(s.url, triedAltMime))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(ch.name).setArtworkUri(ch.logo?.let(Uri::parse)).build())
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }

    private fun mimeFor(url: String, alt: Boolean): String? {
        val path = url.substringBefore('?').lowercase(Locale.ROOT)
        val known = when {
            path.endsWith(".m3u8") || path.endsWith(".m3u") || path.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
            path.endsWith(".mp4") || path.endsWith(".ts") || path.endsWith(".mkv") || path.endsWith(".webm") ||
                path.endsWith(".mp3") || path.endsWith(".aac") || path.endsWith(".flv") -> null
            else -> MimeTypes.APPLICATION_M3U8 // Most links with odd extensions (.php, none) are HLS.
        }
        if (!alt) return known
        // Second attempt: flip between HLS and letting ExoPlayer sniff the container.
        return if (known == MimeTypes.APPLICATION_M3U8) null else MimeTypes.APPLICATION_M3U8
    }

    fun next() = step(1)
    fun previous() = step(-1)

    private fun step(delta: Int) {
        val cur = _ui.value.channel ?: return
        val idx = queue.indexOf(cur.key)
        if (idx < 0) return
        val target = queue.getOrNull(idx + delta)?.let { AppGraph.catalog.find(it) } ?: return
        play(target, 0)
    }

    fun switchSource(index: Int) {
        val ch = _ui.value.channel ?: return
        if (index !in ch.sources.indices) return
        stopRecorder()
        _ui.update { it.copy(sourceIndex = index, error = null, timeshift = false, recording = false) }
        triedAltMime = false
        playSource()
    }

    fun retry() {
        _ui.update { it.copy(error = null) }
        triedAltMime = false
        playSource()
    }

    // endregion

    // region errors

    private fun handleError(e: PlaybackException) {
        val ui = _ui.value
        if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            if (ui.timeshift) player.seekTo(0) else player.seekToDefaultPosition()
            player.prepare()
            return
        }
        if (ui.timeshift) {
            toast("Timeshift ended, back to live")
            goLive()
            return
        }
        val parsing = e.errorCode in setOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        )
        if (parsing && !triedAltMime) {
            triedAltMime = true
            playSource()
            return
        }
        val ch = ui.channel
        if (settings.autoFallback && ch != null && ui.sourceIndex + 1 < ch.sources.size) {
            val next = ui.sourceIndex + 1
            toast("Source ${ui.sourceIndex + 1} failed, trying ${next + 1} of ${ch.sources.size}")
            _ui.update { it.copy(sourceIndex = next) }
            triedAltMime = false
            playSource()
            return
        }
        _ui.update { it.copy(error = describe(e), buffering = false) }
    }

    private fun describe(e: PlaybackException): String {
        val cause = e.cause
        return when {
            cause is HttpDataSource.InvalidResponseCodeException -> when (cause.responseCode) {
                403 -> "The broadcaster refused the connection (HTTP 403). The channel is probably geo-blocked."
                404, 410 -> "This stream is offline (HTTP ${cause.responseCode})."
                else -> "The stream server returned HTTP ${cause.responseCode}."
            }
            e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Couldn't reach the stream. It may be offline or blocked on your network."
            e.errorCode in PlaybackException.ERROR_CODE_DECODER_INIT_FAILED..PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED ->
                "Your device can't decode this stream. Try opening it in VLC."
            e.errorCode == PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> "Insecure connection blocked."
            else -> "This stream isn't playing right now (${e.errorCodeName})."
        }
    }

    // endregion

    // region play / pause / timeshift

    fun togglePlay() {
        if (player.playWhenReady) pause() else resume()
    }

    fun pause() {
        userPaused = true
        player.pause()
        pausedAt = SystemClock.elapsedRealtime()
        val ui = _ui.value
        val minutes = settings.timeshiftMinutes
        if (ui.timeshift || recorder != null || minutes <= 0 || !player.isCurrentMediaItemLive) return
        val manifest = player.currentManifest as? HlsManifest ?: return
        // Streams with audio in a separate playlist would come back silent from a video-only recording.
        if (manifest.multivariantPlaylist.audios.any { it.url != null }) return
        val s = currentSource ?: return
        val rec = TimeshiftRecorder(
            AppGraph.net.client,
            File(getApplication<Application>().cacheDir, "timeshift"),
            headersFor(s),
            minutes * 60_000_000L,
        )
        if (rec.start(manifest.mediaPlaylist, player.currentPosition * 1000)) {
            recorder = rec
            _ui.update { it.copy(recording = true) }
        }
    }

    fun resume() = resume(forceTimeshift = false, deltaMs = 0)

    /**
     * [forceTimeshift] switches to the recorded buffer even after a short pause; [deltaMs] shifts the
     * start position (used by the 10 second buttons while paused).
     */
    private fun resume(forceTimeshift: Boolean, deltaMs: Long) {
        userPaused = false
        val rec = recorder
        if (rec != null && !_ui.value.timeshift) {
            val pausedFor = SystemClock.elapsedRealtime() - pausedAt
            if ((forceTimeshift || pausedFor > 8_000) && rec.segmentCount > 0 && !rec.failed) {
                val item = MediaItem.Builder()
                    .setUri(Uri.fromFile(rec.playlistFile))
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setMinPlaybackSpeed(1f).setMaxPlaybackSpeed(1f).build())
                    .setMediaMetadata(player.mediaMetadata)
                    .build()
                if (BuildConfig.DEBUG) Log.d(TAG, "timeshift start offset=${rec.startOffsetMs} recorded=${rec.recordedMs}")
                player.setMediaItem(item, (rec.startOffsetMs + deltaMs).coerceAtLeast(0))
                player.prepare()
                player.play()
                _ui.update { it.copy(timeshift = true) }
                return
            }
            // A short pause is covered by the in-memory buffer.
            stopRecorder()
        }
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }

    fun goLive() {
        stopRecorder()
        triedAltMime = false
        playSource()
    }

    private fun stopRecorder() {
        recorder?.stop()
        recorder = null
        _ui.update { it.copy(timeshift = false, recording = false) }
    }

    fun recordedMs(): Long = recorder?.recordedMs ?: 0
    fun pausedForMs(): Long = if (userPaused && pausedAt > 0) SystemClock.elapsedRealtime() - pausedAt else 0

    fun seekBy(ms: Long) {
        val ui = _ui.value
        if (ui.recording && !ui.timeshift) {
            // Paused live TV: jump into the recorded buffer, offset by the requested amount.
            resume(forceTimeshift = true, deltaMs = ms)
            return
        }
        if (!player.isCurrentMediaItemSeekable) return
        val target = (player.currentPosition + ms).coerceAtLeast(0)
        val dur = player.duration
        player.seekTo(if (dur != C.TIME_UNSET) target.coerceAtMost(dur) else target)
    }

    fun seekTo(ms: Long) = player.seekTo(ms)

    /** Leaving the app without PiP: stop playback but don't start recording in the background. */
    fun onBackground() {
        if (!player.playWhenReady) return
        userPaused = true
        pausedAt = SystemClock.elapsedRealtime()
        player.pause()
    }

    // endregion

    // region tracks, sleep timer

    fun videoTracks(): List<TrackOption> = tracksOf(C.TRACK_TYPE_VIDEO) { f ->
        buildString {
            if (f.height > 0) append(f.height).append('p') else append("Video")
            if (f.frameRate > 0) append(' ').append(f.frameRate.toInt()).append("fps")
            if (f.bitrate > 0) append(" · ").append(String.format(Locale.US, "%.1f Mbps", f.bitrate / 1_000_000f))
        }
    }

    fun audioTracks(): List<TrackOption> = tracksOf(C.TRACK_TYPE_AUDIO) { f ->
        listOfNotNull(f.label, f.language?.let { Locale.forLanguageTag(it).displayLanguage.ifBlank { it } }, f.codecs).firstOrNull() ?: "Audio"
    }

    fun textTracks(): List<TrackOption> = tracksOf(C.TRACK_TYPE_TEXT) { f ->
        f.label ?: f.language?.let { Locale.forLanguageTag(it).displayLanguage } ?: "Subtitles"
    }

    private fun tracksOf(type: Int, label: (Format) -> String): List<TrackOption> {
        val out = ArrayList<TrackOption>()
        val overridden = player.trackSelectionParameters.overrides.keys.any { it.type == type }
        val disabled = type in player.trackSelectionParameters.disabledTrackTypes
        out.add(TrackOption(if (type == C.TRACK_TYPE_TEXT) "Off" else "Auto", null, -1, if (type == C.TRACK_TYPE_TEXT) disabled || !overridden && player.currentTracks.groups.none { it.type == type && it.isSelected } else !overridden))
        for (g in player.currentTracks.groups) {
            if (g.type != type) continue
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                out.add(TrackOption(label(g.getTrackFormat(i)), g, i, overridden && g.isTrackSelected(i)))
            }
        }
        return if (type == C.TRACK_TYPE_VIDEO) out.distinctBy { it.label } else out
    }

    private fun refreshTracks() {
        val video = videoTracks()
        val audio = audioTracks()
        val text = textTracks()
        val manual = video.firstOrNull { it.group != null && it.selected }
        val h = player.videoFormat?.height ?: 0
        val label = manual?.label?.substringBefore(' ') ?: if (h > 0) "Auto · ${h}p" else "Auto"
        _ui.update {
            it.copy(videoOptions = video.size - 1, audioOptions = audio.size - 1, textOptions = text.size - 1, qualityLabel = label)
        }
    }

    fun selectTrack(type: Int, option: TrackOption) {
        val b = player.trackSelectionParameters.buildUpon()
        if (option.group == null) {
            b.clearOverridesOfType(type)
            b.setTrackTypeDisabled(type, type == C.TRACK_TYPE_TEXT)
        } else {
            b.setTrackTypeDisabled(type, false)
            b.setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.index))
        }
        player.trackSelectionParameters = b.build()
        refreshTracks()
    }

    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) {
            _ui.update { it.copy(sleepAt = null) }
            return
        }
        val at = System.currentTimeMillis() + minutes * 60_000L
        _ui.update { it.copy(sleepAt = at) }
        sleepJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            player.pause()
            _ui.update { it.copy(sleepAt = null, message = "Sleep timer: playback paused") }
        }
    }

    fun toggleFavorite() {
        _ui.value.channel?.let { if (!it.key.startsWith("x:")) AppGraph.user.toggleFavorite(it) }
    }

    fun toast(msg: String) {
        messageJob?.cancel()
        _ui.update { it.copy(message = msg) }
        messageJob = viewModelScope.launch {
            delay(2600)
            _ui.update { it.copy(message = null) }
        }
    }

    fun queueChannels(): List<Channel> = queue.mapNotNull { AppGraph.catalog.find(it) }

    // endregion

    private companion object {
        const val TAG = "PrismPlayer"
    }

    override fun onCleared() {
        stopRecorder()
        player.release()
    }
}
