package io.github.prismtv.player

import androidx.media3.common.C
import androidx.media3.common.util.UriUtil
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/**
 * Local DVR for live HLS.
 *
 * When the user pauses a live channel, this keeps downloading the media playlist's segments to
 * the cache directory and maintains a local sliding-window playlist that points at them. On
 * resume the player switches to that local playlist, so playback continues exactly where it was
 * paused even if the broadcaster's own window has long moved on. The window is capped at the
 * configured timeshift length and older segments are deleted as new ones arrive.
 */
class TimeshiftRecorder(
    private val client: OkHttpClient,
    private val dir: File,
    private val headers: Map<String, String>,
    private val maxDurationUs: Long,
) {
    private class Entry(
        val file: String,
        val durationUs: Long,
        val discontinuity: Boolean,
        val map: String?,
        val key: String?,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val entries = ArrayDeque<Entry>()
    private val initFiles = HashMap<String, String>()
    private var firstSeq = 0L
    private var discontinuitySeq = 0
    private var nextRemoteSeq = -1L
    private var lastRemoteDiscontinuity = -1
    private var gapPending = false
    private var targetDurationS = 6

    val playlistFile = File(dir, "live.m3u8")

    /** Offset into the first recorded segment where the user paused. Zero once that segment has rolled off. */
    @Volatile var startOffsetMs: Long = 0
        private set

    @Volatile var recordedMs: Long = 0
        private set

    @Volatile var segmentCount: Int = 0
        private set

    @Volatile var failed: Boolean = false
        private set

    /** Starts recording from the segment that contains [positionUs]. Returns false if the stream can't be recorded. */
    fun start(initial: HlsMediaPlaylist, positionUs: Long): Boolean {
        if (initial.hasEndTag || initial.segments.isEmpty()) return false
        if (initial.protectionSchemes != null || initial.segments.any { it.drmInitData != null }) return false
        dir.deleteRecursively()
        if (!dir.mkdirs() && !dir.isDirectory) return false

        var idx = initial.segments.indexOfFirst { it.relativeStartTimeUs + it.durationUs > positionUs }
        if (idx < 0) idx = initial.segments.lastIndex
        val seg = initial.segments[idx]
        startOffsetMs = max(0L, (positionUs - seg.relativeStartTimeUs) / 1000)
        nextRemoteSeq = initial.mediaSequence + idx
        targetDurationS = max(1, ceil(initial.targetDurationUs / 1_000_000.0).toInt())

        job = scope.launch { loop(initial) }
        return true
    }

    fun stop() {
        job?.cancel()
        scope.cancel()
        dir.deleteRecursively()
    }

    private suspend fun loop(initial: HlsMediaPlaylist) {
        var playlist = initial
        val url = initial.baseUri
        var failures = 0
        while (scope.isActive) {
            try {
                if (nextRemoteSeq < playlist.mediaSequence) {
                    // We fell behind the broadcaster's window (slow network); skip ahead.
                    nextRemoteSeq = playlist.mediaSequence
                    gapPending = true
                }
                playlist.segments.forEachIndexed { i, seg ->
                    val seq = playlist.mediaSequence + i
                    if (seq >= nextRemoteSeq) {
                        record(playlist, seg, seq)
                        nextRemoteSeq = seq + 1
                    }
                }
                if (playlist.hasEndTag) break
                delay(max(1000L, playlist.targetDurationUs / 2000))
                playlist = fetchPlaylist(url)
                failures = 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures++
                if (failures > 8) { failed = true; break }
                delay(1500)
                runCatching { playlist = fetchPlaylist(url) }
            }
        }
    }

    private fun fetchPlaylist(url: String): HlsMediaPlaylist {
        val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val parsed = HlsPlaylistParser().parse(android.net.Uri.parse(url), resp.body.byteStream())
            return parsed as? HlsMediaPlaylist ?: throw IOException("Not a media playlist")
        }
    }

    private fun download(url: String, offset: Long, length: Long): ByteArray {
        val rb = Request.Builder().url(url)
        headers.forEach { (k, v) -> rb.header(k, v) }
        if (length != C.LENGTH_UNSET.toLong()) rb.header("Range", "bytes=$offset-${offset + length - 1}")
        client.newCall(rb.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            return resp.body.bytes()
        }
    }

    private fun record(playlist: HlsMediaPlaylist, seg: HlsMediaPlaylist.Segment, seq: Long) {
        val base = playlist.baseUri
        val segUrl = UriUtil.resolve(base, seg.url)
        val ext = segUrl.substringBefore('?').substringAfterLast('/').substringAfterLast('.', "ts").take(4).lowercase(Locale.ROOT)
            .let { if (it.all { c -> c.isLetterOrDigit() }) it else "ts" }
        val name = "s$seq.$ext"
        File(dir, name).writeBytes(download(segUrl, seg.byteRangeOffset, seg.byteRangeLength))

        val map = seg.initializationSegment?.let { init ->
            val initUrl = UriUtil.resolve(base, init.url)
            val id = "$initUrl@${init.byteRangeOffset}"
            initFiles.getOrPut(id) {
                val f = "init${initFiles.size}.mp4"
                File(dir, f).writeBytes(download(initUrl, init.byteRangeOffset, init.byteRangeLength))
                f
            }
        }
        val key = seg.fullSegmentEncryptionKeyUri?.let { keyUri ->
            val iv = seg.encryptionIV?.let { if (it.startsWith("0x", true)) it else "0x" + it.padStart(32, '0') }
                ?: String.format(Locale.US, "0x%032x", seq)
            "#EXT-X-KEY:METHOD=AES-128,URI=\"${UriUtil.resolve(base, keyUri)}\",IV=$iv"
        }
        val remoteDisc = playlist.discontinuitySequence + seg.relativeDiscontinuitySequence
        val discontinuity = (lastRemoteDiscontinuity >= 0 && remoteDisc != lastRemoteDiscontinuity) || (gapPending && entries.isNotEmpty())
        lastRemoteDiscontinuity = remoteDisc
        gapPending = false

        synchronized(this) {
            entries.addLast(Entry(name, seg.durationUs, discontinuity, map, key))
            targetDurationS = max(targetDurationS, ceil(seg.durationUs / 1_000_000.0).toInt())
            var total = entries.sumOf { it.durationUs }
            while (total > maxDurationUs && entries.size > 2) {
                val gone = entries.removeFirst()
                File(dir, gone.file).delete()
                firstSeq++
                if (gone.discontinuity) discontinuitySeq++
                total -= gone.durationUs
                startOffsetMs = 0
            }
            recordedMs = total / 1000
            segmentCount = entries.size
            writePlaylist()
        }
    }

    private fun writePlaylist() {
        val sb = StringBuilder(4096)
        sb.append("#EXTM3U\n#EXT-X-VERSION:6\n")
        sb.append("#EXT-X-TARGETDURATION:").append(targetDurationS).append('\n')
        sb.append("#EXT-X-MEDIA-SEQUENCE:").append(firstSeq).append('\n')
        sb.append("#EXT-X-DISCONTINUITY-SEQUENCE:").append(discontinuitySeq).append('\n')
        var lastMap: String? = null
        var lastKey: String? = null
        for (e in entries) {
            if (e.discontinuity) sb.append("#EXT-X-DISCONTINUITY\n")
            if (e.map != null && e.map != lastMap) sb.append("#EXT-X-MAP:URI=\"").append(e.map).append("\"\n")
            lastMap = e.map
            if (e.key != lastKey) {
                sb.append(e.key ?: "#EXT-X-KEY:METHOD=NONE").append('\n')
                lastKey = e.key
            }
            sb.append(String.format(Locale.US, "#EXTINF:%.3f,\n", e.durationUs / 1_000_000.0)).append(e.file).append('\n')
        }
        val tmp = File(dir, "live.m3u8.tmp")
        tmp.writeText(sb.toString())
        if (!tmp.renameTo(playlistFile)) {
            playlistFile.delete()
            tmp.renameTo(playlistFile)
        }
    }
}
