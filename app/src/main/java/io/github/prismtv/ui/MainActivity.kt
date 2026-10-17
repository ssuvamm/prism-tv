package io.github.prismtv.ui

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.prismtv.AppGraph
import io.github.prismtv.R
import io.github.prismtv.data.catalog.SyncStatus
import io.github.prismtv.player.ExternalPlayers
import io.github.prismtv.player.PlayQueue
import io.github.prismtv.player.PlayerActivity
import io.github.prismtv.ui.components.AppBackground
import io.github.prismtv.ui.components.ChannelActionsSheet
import io.github.prismtv.ui.components.Panel
import io.github.prismtv.ui.components.focusRing
import io.github.prismtv.ui.components.PlaylistPickerDialog
import io.github.prismtv.ui.components.SheetAction
import io.github.prismtv.ui.screens.AboutScreen
import io.github.prismtv.ui.screens.BrowseScreen
import io.github.prismtv.ui.screens.GroupScreen
import io.github.prismtv.ui.screens.HiddenScreen
import io.github.prismtv.ui.screens.HomeScreen
import io.github.prismtv.ui.screens.LibraryScreen
import io.github.prismtv.ui.screens.PlaylistScreen
import io.github.prismtv.ui.screens.SettingsScreen
import io.github.prismtv.ui.theme.LocalStyle
import io.github.prismtv.ui.theme.PrismTheme
import android.widget.Toast
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the system splash only until the local catalog is read; that takes a few hundred ms at most.
        splash.setKeepOnScreenCondition { !AppGraph.catalog.loaded.value }
        splash.setOnExitAnimationListener { provider ->
            // The icon view is missing when the system skips the icon (e.g. warm starts, recreation).
            val icon: View? = runCatching { provider.iconView }.getOrNull()
            val fade = ObjectAnimator.ofFloat(provider.view, View.ALPHA, 1f, 0f).setDuration(260)
            if (icon != null) {
                ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.35f).setDuration(260).start()
                ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.35f).setDuration(260).start()
            }
            fade.interpolator = AnticipateInterpolator()
            fade.doOnEndCompat { provider.remove() }
            fade.start()
        }
        enableEdgeToEdge()
        setContent {
            val data by vm.userData.collectAsStateWithLifecycle()
            PrismTheme(data.settings) {
                AppBackground { MainScreen(vm) }
            }
        }
    }
}

private fun ObjectAnimator.doOnEndCompat(block: () -> Unit) {
    addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) = block()
    })
}

@Composable
fun MainScreen(vm: MainViewModel) {
    val catalog by vm.repo.catalog.collectAsStateWithLifecycle()
    val loaded by vm.repo.loaded.collectAsStateWithLifecycle()
    val status by vm.repo.status.collectAsStateWithLifecycle()

    if (catalog == null) {
        if (loaded) FirstRunScreen(status) { vm.repo.clearError(); vm.repo.refresh(userInitiated = true) }
        return
    }

    val holder = rememberSaveableStateHolder()
    LaunchedEffect(Unit) { vm.dropped.collect { holder.removeState(it.key) } }
    BackHandler(enabled = vm.canGoBack) { vm.back() }

    val route = vm.current
    val showNav = route is Route.Root
    // TVs get a side rail (one D-pad press away); phones get the bottom bar.
    val tv = AppGraph.isTv
    val navPadding = PaddingValues(bottom = if (showNav && !tv) 96.dp else 16.dp)

    Row(Modifier.fillMaxSize()) {
    if (tv) SideRail(vm)
    Box(Modifier.weight(1f).fillMaxHeight()) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                val forward = targetState !is Route.Root
                if (initialState is Route.Root && targetState is Route.Root) fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                else if (forward) (slideInHorizontally(tween(240)) { it / 5 } + fadeIn(tween(200))) togetherWith fadeOut(tween(160))
                else fadeIn(tween(200)) togetherWith (slideOutHorizontally(tween(220)) { it / 5 } + fadeOut(tween(160)))
            },
            contentKey = { it.key },
            label = "route",
        ) { r ->
            holder.SaveableStateProvider(r.key) {
                when (r) {
                    is Route.Root -> when (r.tab) {
                        Tab.HOME -> HomeScreen(vm, navPadding)
                        Tab.BROWSE -> BrowseScreen(vm, navPadding)
                        Tab.LIBRARY -> LibraryScreen(vm, navPadding)
                        Tab.SETTINGS -> SettingsScreen(vm, navPadding)
                    }
                    is Route.Group -> GroupScreen(vm, r, navPadding)
                    is Route.Playlist -> PlaylistScreen(vm, r, navPadding)
                    Route.Hidden -> HiddenScreen(vm, navPadding)
                    Route.About -> AboutScreen(vm, navPadding)
                }
            }
        }
        if (showNav && !tv) BottomNav(vm, Modifier.align(Alignment.BottomCenter))
    }
    }

    val c = catalog ?: return
    vm.sheetChannel?.let { ch -> ChannelActionsSheet(vm, ch, c) { vm.visible.value.all } }
    vm.playlistPickerFor?.let { ch ->
        val data by vm.userData.collectAsStateWithLifecycle()
        val ctx = LocalContext.current
        PlaylistPickerDialog(
            playlists = data.playlists,
            onPick = { p ->
                vm.user.addToPlaylist(p.id, ch); vm.playlistPickerFor = null
                Toast.makeText(ctx, "Added to ${p.name}", Toast.LENGTH_SHORT).show()
            },
            onCreate = { name ->
                vm.user.createPlaylist(name, ch); vm.playlistPickerFor = null
                Toast.makeText(ctx, "Added to $name", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { vm.playlistPickerFor = null },
        )
    }
    vm.choosePlayerFor?.let { (ch, queue) -> ChoosePlayerDialog(vm, ch, queue) }
}

@Composable
private fun ChoosePlayerDialog(vm: MainViewModel, ch: io.github.prismtv.data.Channel, queue: List<io.github.prismtv.data.Channel>) {
    val ctx = LocalContext.current
    fun close() { vm.choosePlayerFor = null }
    AlertDialog(
        onDismissRequest = ::close,
        title = { Text(ch.name) },
        text = {
            Column {
                SheetAction(Icons.Rounded.PlayArrow, "Built-in player") { close(); PlayQueue.keys = queue.map { it.key }; PlayerActivity.start(ctx, ch.key, 0) }
                SheetAction(Icons.Rounded.SmartDisplay, "VLC") { close(); vm.user.recordHistory(ch); ExternalPlayers.openVlc(ctx, ch) }
                SheetAction(Icons.AutoMirrored.Rounded.OpenInNew, "Another app") { close(); vm.user.recordHistory(ch); ExternalPlayers.openWith(ctx, ch) }
            }
        },
        confirmButton = { TextButton(onClick = ::close) { Text("Cancel") } },
    )
}

private data class NavItem(val tab: Tab, val label: String, val icon: ImageVector)

private val NAV = listOf(
    NavItem(Tab.HOME, "Home", Icons.Rounded.Home),
    NavItem(Tab.BROWSE, "Browse", Icons.Rounded.Explore),
    NavItem(Tab.LIBRARY, "Library", Icons.AutoMirrored.Rounded.LibraryBooks),
    NavItem(Tab.SETTINGS, "Settings", Icons.Rounded.Settings),
)

@Composable
private fun BottomNav(vm: MainViewModel, modifier: Modifier) {
    val spec = LocalStyle.current
    if (spec.floatingNav) {
        // Floating frosted pill, in the spirit of iOS tab bars.
        Panel(
            modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 10.dp).fillMaxWidth().height(66.dp),
            shape = RoundedCornerShape(33.dp),
            solid = true,
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                NAV.forEach { item ->
                    val sel = vm.tab == item.tab
                    val color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(
                        Modifier.weight(1f).focusRing(RoundedCornerShape(26.dp), 1.05f).clip(RoundedCornerShape(26.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { vm.selectTab(item.tab) }.padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(item.icon, item.label, tint = color)
                        Text(item.label, style = MaterialTheme.typography.labelSmall, color = color)
                    }
                }
            }
        }
    } else {
        NavigationBar(modifier, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            NAV.forEach { item ->
                NavigationBarItem(
                    selected = vm.tab == item.tab,
                    onClick = { vm.selectTab(item.tab) },
                    icon = { Icon(item.icon, null) },
                    label = { Text(item.label) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                )
            }
        }
    }
}

@Composable
private fun SideRail(vm: MainViewModel) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
        header = {
            Image(painterResource(R.drawable.app_logo), null, Modifier.padding(top = 20.dp, bottom = 12.dp).size(44.dp).clip(RoundedCornerShape(12.dp)))
        },
    ) {
        Spacer(Modifier.weight(1f))
        NAV.forEach { item ->
            NavigationRailItem(
                selected = vm.tab == item.tab,
                onClick = { vm.selectTab(item.tab) },
                icon = { Icon(item.icon, null) },
                label = { Text(item.label) },
                modifier = Modifier.focusRing(RoundedCornerShape(16.dp), 1.06f),
                colors = NavigationRailItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
            )
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.weight(1f))
    }
}

/** Shown only on the very first launch while the channel database downloads. */
@Composable
private fun FirstRunScreen(status: SyncStatus, onRetry: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(0.94f, 1.04f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(R.drawable.app_logo), null, Modifier.size(112.dp).scale(scale).clip(RoundedCornerShape(30.dp)))
        Spacer(Modifier.height(24.dp))
        Text("Prism TV", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        when (status) {
            is SyncStatus.Failed -> {
                Text("Couldn't download the channel list.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Text(status.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) { Text("Try again") }
            }
            is SyncStatus.Running -> {
                Text("Getting thousands of channels ready", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.width(220.dp).clip(RoundedCornerShape(4.dp)))
                Spacer(Modifier.height(8.dp))
                Text(status.step, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SyncStatus.Idle -> {
                Text("Getting thousands of channels ready", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                LinearProgressIndicator(modifier = Modifier.width(220.dp).clip(RoundedCornerShape(4.dp)))
            }
        }
    }
}
