package io.github.prismtv.player

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import io.github.prismtv.AppGraph
import io.github.prismtv.data.Channel
import io.github.prismtv.data.Source

/** Hands a stream off to another app: VLC, MX Player, or whatever the user picks. */
object ExternalPlayers {
    const val VLC = "org.videolan.vlc"
    private const val MX = "com.mxtech.videoplayer.ad"
    private const val MX_PRO = "com.mxtech.videoplayer.pro"

    private fun baseIntent(ch: Channel, s: Source): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(s.url.toUri(), "video/*")
            putExtra("title", ch.name)
            val ua = s.userAgent ?: AppGraph.user.settings.userAgent.value
            val headers = buildList {
                add("User-Agent"); add(ua)
                s.referrer?.let { add("Referer"); add(it) }
            }
            // MX Player reads custom headers from this extra.
            putExtra("headers", headers.toTypedArray())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun openVlc(ctx: Context, ch: Channel, s: Source = ch.sources.first()) {
        val intent = baseIntent(ch, s).setPackage(VLC)
        try {
            ctx.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "VLC is not installed", Toast.LENGTH_SHORT).show()
            openStore(ctx, VLC)
        }
    }

    fun openMx(ctx: Context, ch: Channel, s: Source = ch.sources.first()) {
        for (pkg in listOf(MX, MX_PRO)) {
            try {
                ctx.startActivity(baseIntent(ch, s).setPackage(pkg)); return
            } catch (_: ActivityNotFoundException) {
            }
        }
        Toast.makeText(ctx, "MX Player is not installed", Toast.LENGTH_SHORT).show()
        openStore(ctx, MX)
    }

    fun openWith(ctx: Context, ch: Channel, s: Source = ch.sources.first()) {
        val chooser = Intent.createChooser(baseIntent(ch, s), "Open ${ch.name} with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "No video player app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openStore(ctx: Context, pkg: String) {
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$pkg".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            runCatching {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    fun copyLink(ctx: Context, ch: Channel, s: Source = ch.sources.first()) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(ch.name, s.url))
        Toast.makeText(ctx, "Stream link copied", Toast.LENGTH_SHORT).show()
    }

    fun share(ctx: Context, ch: Channel, s: Source = ch.sources.first()) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, ch.name)
            putExtra(Intent.EXTRA_TEXT, "${ch.name}\n${s.url}")
        }
        ctx.startActivity(Intent.createChooser(send, "Share stream").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openUrl(ctx: Context, url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** The list the user was browsing when they started playback, used for next/previous channel. */
object PlayQueue {
    @Volatile var keys: List<String> = emptyList()
}
