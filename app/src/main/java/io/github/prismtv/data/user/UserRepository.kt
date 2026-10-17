package io.github.prismtv.data.user

import android.content.Context
import io.github.prismtv.data.AppSettings
import io.github.prismtv.data.Catalog
import io.github.prismtv.data.Channel
import io.github.prismtv.data.Source
import io.github.prismtv.data.normalize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel as SignalChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class UserPlaylist(val id: String, val name: String, val keys: List<String> = emptyList(), val created: Long = 0)

@Serializable
data class HistoryItem(val key: String, val at: Long)

@Serializable
data class CustomChannel(val key: String, val name: String, val url: String, val logo: String? = null, val group: String? = null)

@Serializable
data class UserData(
    val favorites: List<String> = emptyList(),
    val hidden: List<String> = emptyList(),
    val playlists: List<UserPlaylist> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    val custom: List<CustomChannel> = emptyList(),
    /** Last known name for every key the user saved, used to re-link items when the database changes ids. */
    val names: Map<String, String> = emptyMap(),
    val settings: AppSettings = AppSettings(),
)

/** Favourites, hidden channels, playlists, history and settings, stored as one small JSON file. */
class UserRepository(context: Context) {
    private val file = File(context.filesDir, "user.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; coerceInputValues = true }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveSignal = SignalChannel<Unit>(SignalChannel.CONFLATED)

    private val _data = MutableStateFlow(load())
    val data: StateFlow<UserData> = _data.asStateFlow()
    val settings: AppSettings get() = _data.value.settings

    @Volatile private var customCache: Pair<List<CustomChannel>, Map<String, Channel>> = emptyList<CustomChannel>() to emptyMap()

    init {
        io.launch {
            for (s in saveSignal) {
                delay(250)
                runCatching { write(_data.value) }
            }
        }
    }

    private fun load(): UserData = runCatching {
        if (file.exists()) json.decodeFromString(UserData.serializer(), file.readText()) else UserData()
    }.getOrElse { UserData() }

    private fun write(d: UserData) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(UserData.serializer(), d))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    fun update(f: (UserData) -> UserData) {
        _data.update(f)
        saveSignal.trySend(Unit)
    }

    fun updateSettings(f: (AppSettings) -> AppSettings) = update { it.copy(settings = f(it.settings)) }

    private fun UserData.remember(ch: Channel): UserData =
        if (names[ch.key] == ch.name) this else copy(names = names + (ch.key to ch.name))

    // region favourites / hidden

    fun isFavorite(key: String) = key in _data.value.favorites

    fun toggleFavorite(ch: Channel) = update {
        if (ch.key in it.favorites) it.copy(favorites = it.favorites - ch.key)
        else it.copy(favorites = listOf(ch.key) + it.favorites).remember(ch)
    }

    fun hide(ch: Channel) = update {
        if (ch.key in it.hidden) it else it.copy(hidden = it.hidden + ch.key).remember(ch)
    }

    fun unhide(key: String) = update { it.copy(hidden = it.hidden - key) }
    fun unhideAll() = update { it.copy(hidden = emptyList()) }

    // endregion

    // region playlists

    fun createPlaylist(name: String, first: Channel? = null): String {
        val id = UUID.randomUUID().toString()
        update {
            val p = UserPlaylist(id, name.trim().ifEmpty { "Playlist" }, listOfNotNull(first?.key), System.currentTimeMillis())
            val d = it.copy(playlists = it.playlists + p)
            if (first != null) d.remember(first) else d
        }
        return id
    }

    fun renamePlaylist(id: String, name: String) = update { d ->
        d.copy(playlists = d.playlists.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it })
    }

    fun deletePlaylist(id: String) = update { d ->
        val p = d.playlists.find { it.id == id }
        val orphanCustom = p?.keys?.filter { k -> k.startsWith("c:") && d.playlists.none { it.id != id && k in it.keys } && k !in d.favorites }.orEmpty()
        d.copy(playlists = d.playlists.filter { it.id != id }, custom = d.custom.filter { it.key !in orphanCustom })
    }

    fun addToPlaylist(id: String, ch: Channel) = update { d ->
        d.copy(playlists = d.playlists.map { if (it.id == id && ch.key !in it.keys) it.copy(keys = it.keys + ch.key) else it }).remember(ch)
    }

    fun removeFromPlaylist(id: String, key: String) = update { d ->
        d.copy(playlists = d.playlists.map { if (it.id == id) it.copy(keys = it.keys - key) else it })
    }

    fun movePlaylistItem(id: String, key: String, delta: Int) = update { d ->
        d.copy(playlists = d.playlists.map { p ->
            if (p.id != id) return@map p
            val from = p.keys.indexOf(key)
            val to = (from + delta).coerceIn(0, p.keys.lastIndex)
            if (from < 0 || from == to) p else p.copy(keys = p.keys.toMutableList().apply { add(to, removeAt(from)) })
        })
    }

    /** Adds a user-supplied stream URL. Returns the new channel key. */
    fun addCustom(name: String, url: String, logo: String?, playlistId: String?, group: String? = null): String {
        val key = "c:" + UUID.randomUUID().toString()
        update { d ->
            val c = CustomChannel(key, name.trim().ifEmpty { url }, url.trim(), logo?.takeIf { it.isNotBlank() }, group)
            d.copy(
                custom = d.custom + c,
                playlists = if (playlistId == null) d.playlists else d.playlists.map { if (it.id == playlistId) it.copy(keys = it.keys + key) else it },
            )
        }
        return key
    }

    /** Imports many custom streams at once into a new playlist. */
    fun importPlaylist(name: String, items: List<CustomChannel>): String {
        val id = UUID.randomUUID().toString()
        update { d ->
            d.copy(
                custom = d.custom + items,
                playlists = d.playlists + UserPlaylist(id, name, items.map { it.key }, System.currentTimeMillis()),
            )
        }
        return id
    }

    fun customChannel(key: String): Channel? {
        if (!key.startsWith("c:")) return null
        return customChannels()[key]
    }

    fun customChannels(): Map<String, Channel> {
        val list = _data.value.custom
        val cached = customCache
        if (cached.first === list) return cached.second
        val map = list.associate { c ->
            c.key to Channel(
                key = c.key, name = c.name, altNames = emptyList(), network = null, logo = c.logo,
                country = "", categories = emptyList(), languages = emptyList(), nsfw = false, website = null,
                sources = listOf(Source(c.url, c.group ?: "Custom", null, 0, null, null, 0)), isCustom = true,
            )
        }
        customCache = list to map
        return map
    }

    // endregion

    // region history

    fun recordHistory(ch: Channel) = update { d ->
        val h = listOf(HistoryItem(ch.key, System.currentTimeMillis())) + d.history.filter { it.key != ch.key }
        d.copy(history = h.take(60)).remember(ch)
    }

    fun clearHistory() = update { it.copy(history = emptyList()) }
    fun removeHistory(key: String) = update { d -> d.copy(history = d.history.filter { it.key != key }) }

    // endregion

    /**
     * After a database refresh some channel ids may have changed. Anything the user saved that no
     * longer exists is matched again by name so favourites and playlists keep working.
     */
    fun remapKeys(catalog: Catalog) = update { d ->
        val known = catalog.byKey
        val replacements = HashMap<String, String>()
        fun resolve(key: String): String {
            if (key.startsWith("c:") || key in known) return key
            replacements[key]?.let { return it }
            val name = d.names[key] ?: return key
            val match = catalog.byName[normalize(name)]?.firstOrNull()?.key ?: return key
            replacements[key] = match
            return match
        }
        val favorites = d.favorites.map(::resolve).distinct()
        val hidden = d.hidden.map(::resolve).distinct()
        val playlists = d.playlists.map { p -> p.copy(keys = p.keys.map(::resolve).distinct()) }
        val history = d.history.map { it.copy(key = resolve(it.key)) }.distinctBy { it.key }
        if (replacements.isEmpty()) d
        else d.copy(
            favorites = favorites, hidden = hidden, playlists = playlists, history = history,
            names = d.names + replacements.mapNotNull { (old, new) -> d.names[old]?.let { new to it } },
        )
    }

    fun exportJson(): String = json.encodeToString(UserData.serializer(), _data.value)

    fun importJson(text: String): Boolean = runCatching {
        val d = json.decodeFromString(UserData.serializer(), text)
        update { d }
    }.isSuccess
}
