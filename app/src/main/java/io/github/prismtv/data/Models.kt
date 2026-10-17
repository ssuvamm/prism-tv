package io.github.prismtv.data

import androidx.compose.runtime.Immutable
import java.text.Normalizer
import java.util.Locale

/** One playable URL for a channel. A channel can have several (different feeds or qualities). */
@Immutable
class Source(
    val url: String,
    val label: String,
    val quality: String?,
    val height: Int,
    val userAgent: String?,
    val referrer: String?,
    val flags: Int,
) {
    val geoBlocked: Boolean get() = flags and GEO_BLOCKED != 0
    val not247: Boolean get() = flags and NOT_247 != 0

    companion object {
        const val GEO_BLOCKED = 1
        const val NOT_247 = 2
    }
}

@Immutable
class Channel(
    val key: String,
    val name: String,
    val altNames: List<String>,
    val network: String?,
    val logo: String?,
    val country: String,
    val categories: List<String>,
    val languages: List<String>,
    val nsfw: Boolean,
    val website: String?,
    val sources: List<Source>,
    val isCustom: Boolean = false,
) {
    val maxHeight: Int = sources.maxOfOrNull { it.height } ?: 0
    val geoBlocked: Boolean = sources.isNotEmpty() && sources.all { it.geoBlocked }
    val not247: Boolean = sources.isNotEmpty() && sources.all { it.not247 }
    val nameNorm: String = normalize(name)

    /** Lower-cased, accent-free haystack used for search. Filled in once when the catalog is loaded. */
    var search: String = nameNorm
        internal set

    val qualityBadge: String?
        get() = when {
            maxHeight >= 2160 -> "4K"
            maxHeight >= 1080 -> "FHD"
            maxHeight >= 720 -> "HD"
            maxHeight > 0 -> "SD"
            else -> null
        }
}

@Immutable
data class Country(val code: String, val name: String, val flag: String)

@Immutable
data class Language(val code: String, val name: String)

@Immutable
data class Category(val id: String, val name: String)

@Immutable
data class Region(val code: String, val name: String, val countries: Set<String>)

@Immutable
class Catalog(
    val channels: List<Channel>,
    val countries: Map<String, Country>,
    val languages: Map<String, Language>,
    val categories: Map<String, Category>,
    val regions: List<Region>,
    val updatedAt: Long,
) {
    val byKey: Map<String, Channel> = HashMap<String, Channel>(channels.size * 2).apply {
        channels.forEach { put(it.key, it) }
    }
    val byName: Map<String, List<Channel>> by lazy { channels.groupBy { it.nameNorm } }

    fun countryName(code: String): String = countries[code]?.name ?: if (code.isEmpty()) "International" else code
    fun flag(code: String): String = countries[code]?.flag ?: ""
    fun categoryName(id: String): String = categories[id]?.name ?: id.replaceFirstChar { it.titlecase() }
    fun languageName(code: String): String = languages[code]?.name ?: code

    /** Builds the search haystack for every channel so that queries match names, countries, languages and genres. */
    fun indexSearch() {
        for (c in channels) indexChannel(c)
    }

    fun indexChannel(c: Channel) {
        val sb = StringBuilder(c.nameNorm)
        c.altNames.forEach { sb.append(' ').append(normalize(it)) }
        c.network?.let { sb.append(' ').append(normalize(it)) }
        if (c.country.isNotEmpty()) sb.append(' ').append(normalize(countryName(c.country)))
        c.languages.forEach { sb.append(' ').append(normalize(languageName(it))) }
        c.categories.forEach { sb.append(' ').append(it) }
        c.search = sb.toString()
    }

    companion object {
        val EMPTY = Catalog(emptyList(), emptyMap(), emptyMap(), emptyMap(), emptyList(), 0)
    }
}

private val MARKS = Regex("\\p{Mn}+")

fun normalize(s: String): String {
    val lower = s.lowercase(Locale.ROOT)
    // Fast path: plain ASCII needs no decomposition.
    if (lower.all { it.code < 128 }) return lower
    return MARKS.replace(Normalizer.normalize(lower, Normalizer.Form.NFD), "")
}

/** Matches when every word of the query appears somewhere in the haystack. */
fun matchesQuery(haystack: String, tokens: List<String>): Boolean {
    for (t in tokens) if (!haystack.contains(t)) return false
    return true
}

fun queryTokens(query: String): List<String> =
    normalize(query.trim()).split(' ').filter { it.isNotBlank() }
