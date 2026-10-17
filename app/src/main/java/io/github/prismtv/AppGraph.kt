package io.github.prismtv

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.prismtv.data.catalog.CatalogRepository
import io.github.prismtv.data.sync.SyncScheduler
import io.github.prismtv.data.user.UserRepository
import io.github.prismtv.net.Net
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/** Tiny service locator. Everything here lives as long as the process. */
object AppGraph {
    private fun detectTv(app: Application): Boolean {
        val ui = app.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val pm = app.packageManager
        return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature("amazon.hardware.fire_tv") ||
            !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }

    lateinit var app: Application
        private set
    lateinit var user: UserRepository
        private set
    lateinit var net: Net
        private set
    lateinit var catalog: CatalogRepository
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Android TV, Google TV, Fire TV and other remote-driven devices. */
    var isTv: Boolean = false
        private set

    fun init(app: Application) {
        this.app = app
        user = UserRepository(app)
        isTv = detectTv(app)
        if (isTv && !user.settings.tvDefaultsApplied) {
            // Tiles read better from the sofa, and PiP is rarely wanted on a TV.
            user.updateSettings { it.copy(gridView = true, autoPip = false, tvDefaultsApplied = true) }
        }
        net = Net(app, user.settings.dns)
        catalog = CatalogRepository(app, net.client, user, scope)

        scope.launch {
            user.data.map { it.settings.dns }.distinctUntilChanged().collect { net.setProvider(it) }
        }
        scope.launch {
            user.data.map { it.settings.autoUpdateHours }.distinctUntilChanged().drop(1).collect {
                SyncScheduler.schedule(app, it, replace = true)
            }
        }
        SyncScheduler.schedule(app, user.settings.autoUpdateHours, replace = false)

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                catalog.onForeground()
                catalog.refreshIfStale()
            }
            override fun onStop(owner: LifecycleOwner) = catalog.onBackground()
        })

        scope.launch(Dispatchers.IO) { File(app.cacheDir, "timeshift").deleteRecursively() }
        catalog.start()
    }
}
