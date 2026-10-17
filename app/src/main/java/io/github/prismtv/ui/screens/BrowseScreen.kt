package io.github.prismtv.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Church
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.rounded.Explicit
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.prismtv.ui.GroupType
import io.github.prismtv.ui.MainViewModel
import io.github.prismtv.ui.Route
import io.github.prismtv.ui.Tab
import io.github.prismtv.ui.components.ChannelGrid
import io.github.prismtv.ui.components.EmptyState
import io.github.prismtv.ui.components.LargeTitle
import io.github.prismtv.ui.components.Panel
import io.github.prismtv.ui.components.SearchField
import io.github.prismtv.ui.components.Segmented
import io.github.prismtv.ui.components.SectionHeader
import io.github.prismtv.ui.components.countLabel
import io.github.prismtv.ui.components.fullItem
import io.github.prismtv.ui.components.focusRing
import io.github.prismtv.data.matchesQuery
import io.github.prismtv.data.normalize
import io.github.prismtv.data.queryTokens
import io.github.prismtv.ui.theme.LocalStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun BrowseScreen(vm: MainViewModel, bottomPadding: PaddingValues) {
    val v by vm.visible.collectAsStateWithLifecycle()
    var type by rememberSaveable { mutableStateOf(GroupType.CATEGORY) }
    var q by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LargeTitle("Browse", subtitle = "by genre, place and language")
        Segmented(
            options = GroupType.entries,
            selected = type,
            label = { it.label },
            onSelect = { type = it; q = "" },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        SearchField(q, { q = it }, "Filter ${type.label.lowercase()}", Modifier.padding(horizontal = 16.dp, vertical = 10.dp))

        val entries = remember(v, type) { groupEntries(type, v) }
        val shown = remember(entries, q) {
            val n = normalize(q.trim())
            if (n.isEmpty()) entries else entries.filter { normalize(it.name).contains(n) }
        }
        val home = remember { homeCountry() }
        val grid = type == GroupType.CATEGORY

        key(type) {
            val state = rememberLazyGridState()
            LaunchedEffect(Unit) { vm.scrollToTop.collect { if (it == Tab.BROWSE) state.animateScrollToItem(0) } }
            LazyVerticalGrid(
                columns = if (grid) GridCells.Adaptive(150.dp) else GridCells.Fixed(1),
                state = state,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding.calculateBottomPadding() + 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(if (grid) 10.dp else 6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (type == GroupType.COUNTRY && q.isBlank()) {
                    entries.find { it.id == home }?.let { e ->
                        fullItem("#home") { SectionHeader("Your country", Modifier.padding(start = 0.dp)) }
                        fullItem("#home-row") { GroupRow(e, type) { vm.push(Route.Group(type, e.id, e.name)) } }
                        fullItem("#all") { SectionHeader("All countries") }
                    }
                }
                if (shown.isEmpty()) fullItem("#empty") { EmptyState(Icons.Rounded.Explore, "No matches", "Nothing here matches \"$q\".") }
                items(shown, key = { it.id }) { e ->
                    if (grid) CategoryTile(e) { vm.push(Route.Group(type, e.id, e.name)) }
                    else GroupRow(e, type) { vm.push(Route.Group(type, e.id, e.name)) }
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(e: GroupEntry, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Panel(Modifier.fillMaxWidth().height(104.dp).focusRing(RoundedCornerShape(LocalStyle.current.corner), 1.04f).clip(RoundedCornerShape(LocalStyle.current.corner)).clickable(onClick = onClick)) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(categoryIcon(e.id), null, tint = cs.primary, modifier = Modifier.size(22.dp))
            }
            Column {
                Text(e.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(countLabel(e.count, "channel"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GroupRow(e: GroupEntry, type: GroupType, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Panel(Modifier.fillMaxWidth().focusRing(RoundedCornerShape(LocalStyle.current.corner), 1.01f).clip(RoundedCornerShape(LocalStyle.current.corner)).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                e.flag.isNotEmpty() -> Text(e.flag, fontSize = 26.sp)
                type == GroupType.LANGUAGE -> Icon(Icons.Rounded.Translate, null, tint = cs.primary)
                else -> Icon(Icons.Rounded.Public, null, tint = cs.primary)
            }
            Spacer(Modifier.width(16.dp))
            Text(e.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("%,d".format(e.count), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
    }
}

/** All channels of one category, country, language or region, with its own search. */
@Composable
fun GroupScreen(vm: MainViewModel, route: Route.Group, bottomPadding: PaddingValues) {
    val ctx = LocalContext.current
    val v by vm.visible.collectAsStateWithLifecycle()
    val favKeys by vm.favoriteKeys.collectAsStateWithLifecycle()
    val data by vm.userData.collectAsStateWithLifecycle()
    val flow = remember(route) { vm.groupChannels(route.type, route.id) }
    val all by flow.collectAsStateWithLifecycle(initialValue = null)
    var q by rememberSaveable { mutableStateOf("") }
    var shown by remember { mutableStateOf(all) }
    LaunchedEffect(all, q) {
        val base = all ?: return@LaunchedEffect
        val tokens = queryTokens(q)
        shown = if (tokens.isEmpty()) base else withContext(Dispatchers.Default) { base.filter { matchesQuery(it.search, tokens) } }
    }
    val state = rememberLazyGridState()
    val grid = data.settings.gridView

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                val flag = if (route.type == GroupType.COUNTRY) v.catalog.flag(route.id) else ""
                Text(if (flag.isNotEmpty()) "$flag ${route.title}" else route.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                all?.let { Text(countLabel(it.size, "channel"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        SearchField(q, { q = it }, "Search in ${route.title}", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        val list = shown
        if (list != null) {
            ChannelGrid(
                channels = list, catalog = v.catalog, favorites = favKeys, grid = grid, state = state,
                onClick = { vm.play(ctx, it, list) }, onLongClick = { vm.openSheet(it) },
                contentPadding = PaddingValues(bottom = bottomPadding.calculateBottomPadding() + 12.dp, start = if (grid) 8.dp else 0.dp, end = if (grid) 8.dp else 0.dp),
                header = {
                    if (list.isEmpty()) fullItem("#empty") { EmptyState(Icons.Rounded.LiveTv, "No channels", "Nothing matches here right now.") }
                },
            )
        }
    }
}

private fun homeCountry(): String {
    val c = Locale.getDefault().country.uppercase()
    return if (c == "GB") "UK" else c
}

fun categoryIcon(id: String): ImageVector = when (id) {
    "auto" -> Icons.Rounded.DirectionsCar
    "animation" -> Icons.Rounded.Animation
    "business" -> Icons.Rounded.BusinessCenter
    "classic" -> Icons.Rounded.Theaters
    "comedy" -> Icons.Rounded.SentimentVerySatisfied
    "cooking" -> Icons.Rounded.Restaurant
    "culture" -> Icons.Rounded.Palette
    "documentary" -> Icons.Rounded.VideoLibrary
    "education" -> Icons.Rounded.School
    "entertainment" -> Icons.Rounded.Tv
    "family" -> Icons.Rounded.FamilyRestroom
    "general" -> Icons.Rounded.LiveTv
    "interactive" -> Icons.Rounded.TouchApp
    "kids" -> Icons.Rounded.ChildCare
    "legislative" -> Icons.Rounded.Gavel
    "lifestyle" -> Icons.Rounded.Weekend
    "movies" -> Icons.Rounded.Movie
    "music" -> Icons.Rounded.MusicNote
    "news" -> Icons.Rounded.Newspaper
    "outdoor" -> Icons.Rounded.Forest
    "public" -> Icons.Rounded.AccountBalance
    "relax" -> Icons.Rounded.SelfImprovement
    "religious" -> Icons.Rounded.Church
    "series" -> Icons.Rounded.AutoStories
    "science" -> Icons.Rounded.Science
    "shop" -> Icons.Rounded.ShoppingBag
    "sports" -> Icons.Rounded.SportsSoccer
    "travel" -> Icons.Rounded.Explore
    "weather" -> Icons.Rounded.WbSunny
    "xxx" -> Icons.Rounded.Explicit
    else -> Icons.Rounded.Category
}
