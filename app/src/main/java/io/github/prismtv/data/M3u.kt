package io.github.prismtv.data

import io.github.prismtv.data.user.CustomChannel
import java.util.UUID

/** Minimal M3U/M3U8 playlist reader and writer for importing and exporting user playlists. */
object M3u {
    private val ATTR = Regex("([a-zA-Z0-9-]+)=\"([^\"]*)\"")

    fun parse(text: String): List<CustomChannel> {
        val out = ArrayList<CustomChannel>()
        var name: String? = null
        var logo: String? = null
        var group: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = ATTR.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                    group = attrs["group-title"]?.takeIf { it.isNotBlank() }
                    name = line.substringAfterLast(',', "").trim().ifEmpty { attrs["tvg-name"] }
                }
                line.startsWith("#") -> Unit
                line.contains("://") -> {
                    out.add(CustomChannel("c:" + UUID.randomUUID(), name ?: line.substringAfterLast('/'), line, logo, group))
                    name = null; logo = null; group = null
                }
            }
        }
        return out
    }

    fun export(channels: List<Channel>, catalog: Catalog?): String = buildString {
        append("#EXTM3U\n")
        for (c in channels) {
            val s = c.sources.firstOrNull() ?: continue
            append("#EXTINF:-1")
            if (!c.isCustom && !c.key.startsWith("u:")) append(" tvg-id=\"").append(c.key).append('"')
            c.logo?.let { append(" tvg-logo=\"").append(it).append('"') }
            val group = c.categories.firstOrNull()?.let { catalog?.categoryName(it) ?: it }
            group?.let { append(" group-title=\"").append(it).append('"') }
            append(',').append(c.name).append('\n')
            s.userAgent?.let { append("#EXTVLCOPT:http-user-agent=").append(it).append('\n') }
            s.referrer?.let { append("#EXTVLCOPT:http-referrer=").append(it).append('\n') }
            append(s.url).append('\n')
        }
    }
}
