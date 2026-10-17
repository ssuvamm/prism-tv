package io.github.prismtv.data.catalog

import android.content.Context
import android.os.Process
import io.github.prismtv.data.Catalog
import io.github.prismtv.data.Channel
import io.github.prismtv.data.user.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.Executors

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data class Running(val step: String, val progress: Float) : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

/**
 * Owns the channel catalog.
 *
 * Refresh policy: on launch the age of the local copy is checked and, if it is older than the
 * configured interval (36 hours by default), a new copy is fetched in the background at low
 * priority. A fresh copy is never swapped in while the user is looking at the app; it waits
 * until the app goes to the background (or the next launch). Only the very first download,
 * or one the user asked for explicitly, is applied straight away.
 */
class CatalogRepository(
    context: Context,
    client: OkHttpClient,
    private val user: UserRepository,
    private val scope: CoroutineScope,
) {
    private val file = File(context.filesDir, "catalog.bin")
    private val builder = CatalogBuilder(client)
    private val mutex = Mutex()
    private val lowPriority = Executors.newFixedThreadPool(3) { r ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            r.run()
        }, "catalog-sync").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val _catalog = MutableStateFlow<Catalog?>(null)
    val catalog: StateFlow<Catalog?> = _catalog.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    /** True once the on-disk copy has been read (or found missing). */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    @Volatile private var pending: Catalog? = null
    @Volatile var inForeground: Boolean = false
        private set

    fun start() {
        scope.launch(Dispatchers.IO) {
            val disk = runCatching { CatalogStore.read(file) }.getOrNull()
            if (disk != null) {
                disk.indexSearch()
                _catalog.value = disk
            }
            _loaded.value = true
            refreshIfStale()
        }
    }

    fun find(key: String): Channel? =
        _catalog.value?.byKey?.get(key) ?: user.customChannel(key)

    fun isStale(): Boolean {
        val c = _catalog.value ?: return true
        val maxAge = user.settings.autoUpdateHours * 3_600_000L
        return System.currentTimeMillis() - c.updatedAt >= maxAge
    }

    fun refreshIfStale() {
        if (_loaded.value && isStale()) refresh(userInitiated = false)
    }

    /** Called from the background worker; resolves once the download has finished. */
    suspend fun refreshNow(userInitiated: Boolean): Boolean = doRefresh(userInitiated)

    fun refresh(userInitiated: Boolean): Job = scope.launch { doRefresh(userInitiated) }

    private suspend fun doRefresh(userInitiated: Boolean): Boolean {
        if (mutex.isLocked) return false
        return mutex.withLock {
            val firstRun = _catalog.value == null
            val showProgress = firstRun || userInitiated
            if (showProgress) _status.value = SyncStatus.Running("Connecting to iptv-org", 0f)
            try {
                // Silent refreshes run on low-priority threads so scrolling and playback never stutter.
                val io = if (showProgress) Dispatchers.IO else lowPriority
                val fresh = withContext(io) {
                    val c = builder.build(io) { step, p ->
                        if (showProgress) _status.value = SyncStatus.Running(step, p)
                    }
                    CatalogStore.write(file, c)
                    c.indexSearch()
                    c
                }
                if (showProgress || !inForeground) apply(fresh) else pending = fresh
                _status.value = SyncStatus.Idle
                true
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _status.value = if (showProgress) SyncStatus.Failed(e.message ?: "Network error") else SyncStatus.Idle
                false
            }
        }
    }

    private fun apply(c: Catalog) {
        pending = null
        user.remapKeys(c)
        _catalog.value = c
    }

    fun onForeground() {
        inForeground = true
    }

    /** The app left the screen: a good moment to swap in a newer catalog without anyone noticing. */
    fun onBackground() {
        inForeground = false
        pending?.let { apply(it) }
    }

    fun clearError() {
        if (_status.value is SyncStatus.Failed) _status.value = SyncStatus.Idle
    }
}
