package io.github.prismtv.data.catalog

import android.util.JsonReader
import android.util.JsonToken
import io.github.prismtv.data.Catalog
import io.github.prismtv.data.Category
import io.github.prismtv.data.Channel
import io.github.prismtv.data.Country
import io.github.prismtv.data.Language
import io.github.prismtv.data.Region
import io.github.prismtv.data.Source
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicInteger

/**
 * Downloads the iptv-org database (https://github.com/iptv-org/api) and folds it into one
 * channel list. Every file is parsed as a stream while it downloads, so memory stays flat.
 */
class CatalogBuilder(private val client: OkHttpClient) {

    private class RawChannel(
        val name: String,
        val altNames: List<String>,
        val network: String?,
        val country: String,
        val categories: List<String>,
        val nsfw: Boolean,
        val website: String?,
    )

    private class RawStream(
        val channel: String?,
        val feed: String?,
        val title: String,
        val url: String,
        val quality: String?,
        val labels: List<String>,
        val userAgent: String?,
        val referrer: String?,
    )

    private class RawFeed(val name: String, val isMain: Boolean, val languages: List<String>)

    private class Logo(val url: String, val score: Int)

    suspend fun build(io: CoroutineDispatcher, onProgress: (String, Float) -> Unit): Catalog = coroutineScope {
        val total = FILES.size
        val done = AtomicInteger(0)
        fun tick(name: String) {
            val n = done.incrementAndGet()
            onProgress("Loaded $name", n.toFloat() / (total + 1))
        }
        onProgress("Downloading channel database", 0.02f)

        val channelsD = async(io) { fetch("channels") { parseChannels(it) }.also { tick("channels") } }
        val streamsD = async(io) { fetch("streams") { parseStreams(it) }.also { tick("streams") } }
        val logosD = async(io) { fetch("logos") { parseLogos(it) }.also { tick("logos") } }
        val feedsD = async(io) { fetch("feeds") { parseFeeds(it) }.also { tick("feeds") } }
        val countriesD = async(io) { fetch("countries") { parseCountries(it) }.also { tick("countries") } }
        val languagesD = async(io) { fetch("languages") { parseLanguages(it) }.also { tick("languages") } }
        val categoriesD = async(io) { fetch("categories") { parseCategories(it) }.also { tick("categories") } }
        val regionsD = async(io) { fetch("regions") { parseRegions(it) }.also { tick("regions") } }
        val blockD = async(io) { fetch("blocklist") { parseBlocklist(it) }.also { tick("blocklist") } }

        val rawChannels = channelsD.await()
        val streams = streamsD.await()
        val logos = logosD.await()
        val feeds = feedsD.await()
        val countries = countriesD.await()
        val allLanguages = languagesD.await()
        val categories = categoriesD.await()
        val regions = regionsD.await()
        val blocked = blockD.await()

        onProgress("Building channel list", 0.95f)
        withContext(Dispatchers.Default) {
            assemble(rawChannels, streams, logos, feeds, countries, allLanguages, categories, regions, blocked)
        }
    }

    private fun <T> fetch(name: String, parse: (JsonReader) -> T): T {
        val req = Request.Builder().url("$API/$name.json").header("Accept", "application/json").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Failed to download $name.json (HTTP ${resp.code})")
            JsonReader(InputStreamReader(resp.body.byteStream(), Charsets.UTF_8).buffered(64 * 1024)).use { reader ->
                return parse(reader)
            }
        }
    }

    private fun assemble(
        rawChannels: Map<String, RawChannel>,
        streams: List<RawStream>,
        logos: Map<String, Logo>,
        feeds: Map<String, RawFeed>,
        countries: Map<String, Country>,
        allLanguages: Map<String, Language>,
        categories: Map<String, Category>,
        regions: List<Region>,
        blocked: Set<String>,
    ): Catalog {
        val grouped = LinkedHashMap<String, MutableList<RawStream>>()
        val loose = ArrayList<RawStream>()
        val seenUrls = HashSet<String>()
        for (s in streams) {
            if (!seenUrls.add(s.url)) continue
            val id = s.channel
            if (id != null && id in blocked) continue
            if (id != null && rawChannels.containsKey(id)) grouped.getOrPut(id) { ArrayList(2) }.add(s)
            else loose.add(s)
        }

        val usedLanguages = HashSet<String>()
        val out = ArrayList<Channel>(grouped.size + loose.size)

        for ((id, list) in grouped) {
            val ch = rawChannels.getValue(id)
            val langs = LinkedHashSet<String>()
            for (s in list) {
                val feed = s.feed?.let { feeds["$id@$it"] } ?: feeds["$id@"]
                feed?.languages?.let { langs.addAll(it) }
            }
            if (langs.isEmpty()) feeds["$id@"]?.languages?.let { langs.addAll(it) }
            usedLanguages.addAll(langs)

            val sources = list.map { s ->
                val feedName = s.feed?.let { feeds["$id@$it"]?.name }
                val label = buildString {
                    append(feedName ?: s.title.removePrefix(ch.name).trim(' ', '-', '(', ')').ifBlank { "Main" })
                    s.quality?.let { append(" · ").append(it) }
                }
                toSource(s, label)
            }.sortedWith(SOURCE_ORDER)

            val logo = logos[id]?.url ?: list.firstNotNullOfOrNull { s -> s.feed?.let { logos["$id@$it"]?.url } }
            out.add(
                Channel(
                    key = id,
                    name = ch.name,
                    altNames = ch.altNames,
                    network = ch.network,
                    logo = logo,
                    country = ch.country,
                    categories = ch.categories,
                    languages = langs.toList(),
                    nsfw = ch.nsfw || "xxx" in ch.categories,
                    website = ch.website,
                    sources = sources,
                )
            )
        }

        for (s in loose) {
            val title = cleanTitle(s.title)
            out.add(
                Channel(
                    key = "u:" + s.url,
                    name = title,
                    altNames = emptyList(),
                    network = null,
                    logo = null,
                    country = "",
                    categories = emptyList(),
                    languages = emptyList(),
                    nsfw = false,
                    website = null,
                    sources = listOf(toSource(s, s.quality ?: "Main")),
                )
            )
        }

        // Alphabetical, ignoring leading symbols so "&TV" sorts under T rather than before A.
        out.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { c -> c.name.trimStart { !it.isLetterOrDigit() }.ifEmpty { c.name } })

        val usedCountries = out.mapTo(HashSet()) { it.country }
        val usedCategories = out.flatMapTo(HashSet()) { it.categories }
        return Catalog(
            channels = out,
            countries = countries.filterKeys { it in usedCountries || regions.any { r -> it in r.countries } },
            languages = allLanguages.filterKeys { it in usedLanguages },
            categories = categories.filterKeys { it in usedCategories },
            regions = regions.filter { r -> r.countries.any { it in usedCountries } },
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun toSource(s: RawStream, label: String): Source {
        var flags = 0
        for (l in s.labels) {
            if (l.equals("Geo-blocked", true)) flags = flags or Source.GEO_BLOCKED
            if (l.equals("Not 24/7", true)) flags = flags or Source.NOT_247
        }
        return Source(
            url = s.url,
            label = label,
            quality = s.quality,
            height = s.quality?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0,
            userAgent = s.userAgent,
            referrer = s.referrer,
            flags = flags,
        )
    }

    private fun cleanTitle(t: String): String =
        t.replace(QUALITY_SUFFIX, "").replace(BRACKET_SUFFIX, "").trim().ifEmpty { t }

    // region parsers

    private fun parseChannels(r: JsonReader): Map<String, RawChannel> {
        val out = HashMap<String, RawChannel>(40_000)
        r.objects {
            var id: String? = null
            var name = ""
            var alt = emptyList<String>()
            var network: String? = null
            var country = ""
            var cats = emptyList<String>()
            var nsfw = false
            var website: String? = null
            while (r.hasNext()) when (r.nextName()) {
                "id" -> id = r.str()
                "name" -> name = r.str() ?: ""
                "alt_names" -> alt = r.strList()
                "network" -> network = r.str()
                "country" -> country = r.str() ?: ""
                "categories" -> cats = r.strList()
                "is_nsfw" -> nsfw = r.bool()
                "website" -> website = r.str()
                else -> r.skipValue()
            }
            if (id != null) out[id] = RawChannel(name.ifEmpty { id }, alt, network, country, cats, nsfw, website)
        }
        return out
    }

    private fun parseStreams(r: JsonReader): List<RawStream> {
        val out = ArrayList<RawStream>(20_000)
        r.objects {
            var channel: String? = null
            var feed: String? = null
            var title = ""
            var url: String? = null
            var quality: String? = null
            var labels = emptyList<String>()
            var ua: String? = null
            var ref: String? = null
            while (r.hasNext()) when (r.nextName()) {
                "channel" -> channel = r.str()
                "feed" -> feed = r.str()
                "title" -> title = r.str() ?: ""
                "url" -> url = r.str()
                "quality" -> quality = r.str()
                "labels" -> labels = r.strList()
                "user_agent" -> ua = r.str()
                "referrer" -> ref = r.str()
                else -> r.skipValue()
            }
            if (!url.isNullOrBlank()) out.add(RawStream(channel, feed, title.ifEmpty { url }, url, quality, labels, ua, ref))
        }
        return out
    }

    private fun parseLogos(r: JsonReader): Map<String, Logo> {
        val out = HashMap<String, Logo>(40_000)
        r.objects {
            var channel: String? = null
            var feed: String? = null
            var inUse = false
            var width = 0
            var format: String? = null
            var url: String? = null
            while (r.hasNext()) when (r.nextName()) {
                "channel" -> channel = r.str()
                "feed" -> feed = r.str()
                "in_use" -> inUse = r.bool()
                "width" -> width = r.int()
                "format" -> format = r.str()
                "url" -> url = r.str()
                else -> r.skipValue()
            }
            if (channel != null && url != null) {
                var score = 0
                if (inUse) score += 100
                if (!format.equals("SVG", true)) score += 10
                if (width in 96..1024) score += 5
                val key = if (feed == null) channel else "$channel@$feed"
                val current = out[key]
                if (current == null || score > current.score) out[key] = Logo(url, score)
            }
        }
        return out
    }

    private fun parseFeeds(r: JsonReader): Map<String, RawFeed> {
        val out = HashMap<String, RawFeed>(60_000)
        r.objects {
            var channel: String? = null
            var id: String? = null
            var name = ""
            var main = false
            var langs = emptyList<String>()
            while (r.hasNext()) when (r.nextName()) {
                "channel" -> channel = r.str()
                "id" -> id = r.str()
                "name" -> name = r.str() ?: ""
                "is_main" -> main = r.bool()
                "languages" -> langs = r.strList()
                else -> r.skipValue()
            }
            if (channel != null && id != null) {
                val feed = RawFeed(name.ifEmpty { id }, main, langs)
                out["$channel@$id"] = feed
                if (main) out["$channel@"] = feed
            }
        }
        return out
    }

    private fun parseCountries(r: JsonReader): Map<String, Country> {
        val out = LinkedHashMap<String, Country>()
        r.objects {
            var code: String? = null
            var name = ""
            var flag = ""
            while (r.hasNext()) when (r.nextName()) {
                "code" -> code = r.str()
                "name" -> name = r.str() ?: ""
                "flag" -> flag = r.str() ?: ""
                else -> r.skipValue()
            }
            if (code != null) out[code] = Country(code, name.ifEmpty { code }, flag)
        }
        return out
    }

    private fun parseLanguages(r: JsonReader): Map<String, Language> {
        val out = HashMap<String, Language>(9000)
        r.objects {
            var code: String? = null
            var name = ""
            while (r.hasNext()) when (r.nextName()) {
                "code" -> code = r.str()
                "name" -> name = r.str() ?: ""
                else -> r.skipValue()
            }
            if (code != null) out[code] = Language(code, name.ifEmpty { code })
        }
        return out
    }

    private fun parseCategories(r: JsonReader): Map<String, Category> {
        val out = LinkedHashMap<String, Category>()
        r.objects {
            var id: String? = null
            var name = ""
            while (r.hasNext()) when (r.nextName()) {
                "id" -> id = r.str()
                "name" -> name = r.str() ?: ""
                else -> r.skipValue()
            }
            if (id != null) out[id] = Category(id, name.ifEmpty { id })
        }
        return out
    }

    private fun parseRegions(r: JsonReader): List<Region> {
        val out = ArrayList<Region>()
        r.objects {
            var code: String? = null
            var name = ""
            var countries = emptyList<String>()
            while (r.hasNext()) when (r.nextName()) {
                "code" -> code = r.str()
                "name" -> name = r.str() ?: ""
                "countries" -> countries = r.strList()
                else -> r.skipValue()
            }
            if (code != null) out.add(Region(code, name.ifEmpty { code }, countries.toSet()))
        }
        return out.sortedBy { it.name }
    }

    private fun parseBlocklist(r: JsonReader): Set<String> {
        val out = HashSet<String>()
        r.objects {
            var channel: String? = null
            while (r.hasNext()) when (r.nextName()) {
                "channel" -> channel = r.str()
                else -> r.skipValue()
            }
            channel?.let { out.add(it) }
        }
        return out
    }

    // endregion

    companion object {
        const val API = "https://iptv-org.github.io/api"
        private val FILES = listOf(
            "channels", "streams", "logos", "feeds", "countries", "languages", "categories", "regions", "blocklist",
        )
        private val QUALITY_SUFFIX = Regex("\\s*\\(\\d{3,4}[pi]\\)")
        private val BRACKET_SUFFIX = Regex("\\s*\\[[^]]*]")

        // Prefer streams that work everywhere and run all day, then the sharpest picture.
        private val SOURCE_ORDER = compareBy<Source>({ it.geoBlocked }, { it.not247 }, { -it.height })
    }
}

private inline fun JsonReader.objects(block: () -> Unit) {
    beginArray()
    while (hasNext()) {
        beginObject()
        block()
        endObject()
    }
    endArray()
}

private fun JsonReader.str(): String? =
    if (peek() == JsonToken.NULL) { nextNull(); null } else nextString()

private fun JsonReader.bool(): Boolean = when (peek()) {
    JsonToken.NULL -> { nextNull(); false }
    JsonToken.BOOLEAN -> nextBoolean()
    else -> { skipValue(); false }
}

private fun JsonReader.int(): Int = when (peek()) {
    JsonToken.NUMBER -> nextDouble().toInt()
    else -> { skipValue(); 0 }
}

private fun JsonReader.strList(): List<String> {
    if (peek() != JsonToken.BEGIN_ARRAY) { skipValue(); return emptyList() }
    val out = ArrayList<String>(2)
    beginArray()
    while (hasNext()) str()?.let { out.add(it) }
    endArray()
    return out
}
