package io.github.prismtv.ui

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.prismtv.AppGraph
import io.github.prismtv.data.Catalog
import io.github.prismtv.data.Channel
import io.github.prismtv.data.PlayerChoice
import io.github.prismtv.data.matchesQuery
import io.github.prismtv.data.queryTokens
import io.github.prismtv.data.user.UserData
import io.github.prismtv.player.ExternalPlayers
import io.github.prismtv.player.PlayQueue
import io.github.prismtv.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class Tab { HOME, BROWSE, LIBRARY, SETTINGS }
enum class GroupType(val label: String) { CATEGORY("Categories"), COUNTRY("Countries"), LANGUAGE("Languages"), REGION("Regions") }
enum class SortOrder(val label: String) { NAME("Name"), QUALITY("Best quality"), COUNTRY("Country") }

sealed interface Route {
    val key: String

    data class Root(val tab: Tab) : Route { override val key = "root-$tab" }
    data class Group(val type: GroupType, val id: String, val title: String) : Route { override val key = "group-$type-$id" }
    data class Playlist(val id: String) : Route { override val key = "playlist-$id" }
    data object Hidden : Route { override val key = "hidden" }
    data object About : Route { override val key = "about" }
}

@Immutable
data class Filters(
    val category: String? = null,
    val country: String? = null,
    val language: String? = null,
    val region: String? = null,
    val minHeight: Int = 0,
    val sort: SortOrder = SortOrder.NAME,
) {
    val active: Boolean get() = category != null || country != null || language != null || region != null || minHeight > 0
}

/** The channels the user can see after hidden/NSFW/label preferences are applied. */
@Immutable
class Visible(val catalog: Catalog, val all: List<Channel>, val custom: Map<String, Channel>) {
    fun find(key: String): Channel? = catalog.byKey[key] ?: custom[key]

    val countByCategory: Map<String, Int> by lazy { HashMap<String, Int>().also { m -> all.forEach { c -> c.categories.forEach { m[it] = (m[it] ?: 0) + 1 } } } }
    val countByCountry: Map<String, Int> by lazy { HashMap<String, Int>().also { m -> all.forEach { c -> if (c.country.isNotEmpty()) m[c.country] = (m[c.country] ?: 0) + 1 } } }
    val countByLanguage: Map<String, Int> by lazy { HashMap<String, Int>().also { m -> all.forEach { c -> c.languages.forEach { m[it] = (m[it] ?: 0) + 1 } } } }
    val countByRegion: Map<String, Int> by lazy {
        catalog.regions.associate { r -> r.code to r.countries.sumOf { countByCountry[it] ?: 0 } }
    }
    val uncategorised: Int by lazy { all.count { it.categories.isEmpty() } }

    companion object { val EMPTY = Visible(Catalog.EMPTY, emptyList(), emptyMap()) }
}

private data class VisibilityKey(
    val hidden: Set<String>, val nsfw: Boolean, val hideGeo: Boolean, val hide247: Boolean,
    val custom: List<io.github.prismtv.data.user.CustomChannel>,
)

@OptIn(FlowPreview::class)
class MainViewModel : ViewModel() {
    val user = AppGraph.user
    val repo = AppGraph.catalog

    val userData: StateFlow<UserData> = user.data

    val visible: StateFlow<Visible> = combine(
        repo.catalog.filterNotNull(),
        user.data.map {
            VisibilityKey(it.hidden.toSet(), it.settings.showNsfw, it.settings.hideGeoBlocked, it.settings.hideNot247, it.custom)
        }.distinctUntilChanged(),
    ) { catalog, k ->
        val list = catalog.channels.filter { c ->
            c.key !in k.hidden && (k.nsfw || !c.nsfw) && !(k.hideGeo && c.geoBlocked) && !(k.hide247 && c.not247)
        }
        val custom = user.customChannels().filterKeys { it !in k.hidden }
        Visible(catalog, list, custom)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, Visible.EMPTY)

    // region home search

    val query = MutableStateFlow("")
    val filters = MutableStateFlow(Filters())

    val homeResults: StateFlow<List<Channel>?> = combine(
        visible,
        query.debounce { if (it.isEmpty()) 0L else 140L }.distinctUntilChanged(),
        filters,
    ) { v, q, f -> filterChannels(v, v.all, q, f, includeCustom = true) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun filterChannels(v: Visible, base: List<Channel>, q: String, f: Filters, includeCustom: Boolean = false): List<Channel> {
        val tokens = queryTokens(q)
        val region = f.region?.let { code -> v.catalog.regions.find { it.code == code }?.countries }
        val src = if (includeCustom && tokens.isNotEmpty()) base + v.custom.values else base
        val out = src.filter { c ->
            (f.category == null || (if (f.category == UNCATEGORISED) c.categories.isEmpty() else f.category in c.categories)) &&
                (f.country == null || c.country == f.country) &&
                (f.language == null || f.language in c.languages) &&
                (region == null || c.country in region) &&
                (f.minHeight == 0 || c.maxHeight >= f.minHeight) &&
                (tokens.isEmpty() || matchesQuery(c.search, tokens))
        }
        val sorted = when (f.sort) {
            SortOrder.NAME -> out
            SortOrder.QUALITY -> out.sortedByDescending { it.maxHeight }
            SortOrder.COUNTRY -> out.sortedWith(compareBy({ v.catalog.countryName(it.country) }, { it.name.lowercase() }))
        }
        if (tokens.isEmpty() || f.sort != SortOrder.NAME) return sorted
        // Name matches first, then everything that matched on country, language or genre.
        val first = tokens.first()
        return sorted.sortedBy { c ->
            when {
                c.nameNorm.startsWith(first) -> 0
                c.nameNorm.contains(first) -> 1
                else -> 2
            }
        }
    }

    fun groupChannels(type: GroupType, id: String): Flow<List<Channel>> = visible.map { v ->
        val region = if (type == GroupType.REGION) v.catalog.regions.find { it.code == id }?.countries.orEmpty() else emptySet()
        v.all.filter { c ->
            when (type) {
                GroupType.CATEGORY -> if (id == UNCATEGORISED) c.categories.isEmpty() else id in c.categories
                GroupType.COUNTRY -> c.country == id
                GroupType.LANGUAGE -> id in c.languages
                GroupType.REGION -> c.country in region
            }
        }
    }.flowOn(Dispatchers.Default)

    // endregion

    // region library lists

    val favorites: StateFlow<List<Channel>> = combine(visible, user.data.map { it.favorites }.distinctUntilChanged()) { v, keys ->
        keys.mapNotNull { v.find(it) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favoriteKeys: StateFlow<Set<String>> = user.data.map { it.favorites.toSet() }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val history: StateFlow<List<Channel>> = combine(visible, user.data.map { it.history }.distinctUntilChanged()) { v, h ->
        h.mapNotNull { v.find(it.key) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun playlistChannels(id: String): Flow<List<Channel>> =
        combine(visible, user.data.map { d -> d.playlists.find { it.id == id }?.keys.orEmpty() }.distinctUntilChanged()) { v, keys ->
            keys.mapNotNull { v.find(it) }
        }.flowOn(Dispatchers.Default)

    val hiddenChannels: StateFlow<List<Channel>> = combine(repo.catalog.filterNotNull(), user.data.map { it.hidden }.distinctUntilChanged()) { c, keys ->
        keys.mapNotNull { c.byKey[it] ?: user.customChannel(it) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // endregion

    // region navigation

    var tab by mutableStateOf(Tab.HOME)
        private set
    private val stacks = mutableStateMapOf<Tab, List<Route>>().apply { Tab.entries.forEach { put(it, listOf(Route.Root(it))) } }
    val current: Route get() = stacks.getValue(tab).last()
    val canGoBack: Boolean get() = stacks.getValue(tab).size > 1 || tab != Tab.HOME
    val scrollToTop = MutableSharedFlow<Tab>(extraBufferCapacity = 1)

    /** Removed routes, so the UI can drop their saved state. */
    val dropped = MutableSharedFlow<Route>(extraBufferCapacity = 8)

    fun selectTab(t: Tab) {
        if (t == tab) {
            val stack = stacks.getValue(t)
            if (stack.size > 1) {
                stack.drop(1).forEach { dropped.tryEmit(it) }
                stacks[t] = listOf(stack.first())
            } else scrollToTop.tryEmit(t)
        } else tab = t
    }

    fun push(r: Route) {
        val stack = stacks.getValue(tab)
        if (stack.last() != r) stacks[tab] = stack + r
    }

    fun back(): Boolean {
        val stack = stacks.getValue(tab)
        return when {
            stack.size > 1 -> { dropped.tryEmit(stack.last()); stacks[tab] = stack.dropLast(1); true }
            tab != Tab.HOME -> { tab = Tab.HOME; true }
            else -> false
        }
    }

    // endregion

    // region sheets & actions

    var sheetChannel by mutableStateOf<Channel?>(null)
    var sheetContext by mutableStateOf<SheetContext?>(null)
    var playlistPickerFor by mutableStateOf<Channel?>(null)
    var choosePlayerFor by mutableStateOf<Pair<Channel, List<Channel>>?>(null)

    fun openSheet(ch: Channel, context: SheetContext? = null) {
        sheetContext = context
        sheetChannel = ch
    }

    fun play(ctx: Context, ch: Channel, queue: List<Channel>, sourceIndex: Int = 0, forceBuiltIn: Boolean = false) {
        val choice = if (forceBuiltIn) PlayerChoice.BUILT_IN else user.settings.defaultPlayer
        when (choice) {
            PlayerChoice.BUILT_IN -> {
                PlayQueue.keys = queue.map { it.key }
                PlayerActivity.start(ctx, ch.key, sourceIndex)
            }
            PlayerChoice.VLC -> {
                user.recordHistory(ch)
                ExternalPlayers.openVlc(ctx, ch, ch.sources.getOrElse(sourceIndex) { ch.sources.first() })
            }
            PlayerChoice.ASK -> choosePlayerFor = ch to queue
        }
    }

    // endregion

    companion object {
        const val UNCATEGORISED = "__none"
    }
}

/** Extra actions shown in the long-press sheet depending on where it was opened from. */
sealed interface SheetContext {
    data class InPlaylist(val playlistId: String) : SheetContext
    data object InHistory : SheetContext
}
