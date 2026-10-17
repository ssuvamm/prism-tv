package io.github.prismtv.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.prismtv.data.Catalog
import io.github.prismtv.ui.Filters
import io.github.prismtv.ui.GroupType
import io.github.prismtv.ui.MainViewModel
import io.github.prismtv.ui.SortOrder
import io.github.prismtv.ui.Tab
import io.github.prismtv.ui.Visible
import io.github.prismtv.ui.components.ChannelGrid
import io.github.prismtv.ui.components.EmptyState
import io.github.prismtv.ui.components.LargeTitle
import io.github.prismtv.ui.components.PickerSheet
import io.github.prismtv.ui.components.SearchField
import io.github.prismtv.ui.components.SectionHeader
import io.github.prismtv.ui.components.countLabel
import io.github.prismtv.ui.components.fullItem
import io.github.prismtv.ui.components.rail

private enum class FilterKind { CATEGORY, COUNTRY, LANGUAGE, REGION, QUALITY, SORT }

@Composable
fun HomeScreen(vm: MainViewModel, bottomPadding: PaddingValues) {
    val ctx = LocalContext.current
    val v by vm.visible.collectAsStateWithLifecycle()
    val results by vm.homeResults.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    val favKeys by vm.favoriteKeys.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val data by vm.userData.collectAsStateWithLifecycle()
    val grid = data.settings.gridView
    val state = rememberLazyGridState()
    var picker by remember { mutableStateOf<FilterKind?>(null) }

    LaunchedEffect(Unit) { vm.scrollToTop.collect { if (it == Tab.HOME) state.animateScrollToItem(0) } }

    // Jump to the top only when the search itself changes, never on recomposition or when returning from the player.
    var lastSearch by rememberSaveable { mutableStateOf(query to filters.hashCode()) }
    LaunchedEffect(query, filters) {
        val now = query to filters.hashCode()
        if (now != lastSearch) {
            lastSearch = now
            state.scrollToItem(0)
        }
    }

    val browsing = query.isBlank() && !filters.active

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LargeTitle("Prism TV", subtitle = countLabel(v.all.size, "channel")) {
            IconButton(onClick = { vm.user.updateSettings { it.copy(gridView = !it.gridView) } }) {
                Icon(if (grid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView, if (grid) "List view" else "Grid view")
            }
        }
        SearchField(
            value = query,
            onChange = { vm.query.value = it },
            placeholder = "Channels, countries, languages…",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        FilterRow(filters, v.catalog, onOpen = { picker = it }, onClear = { vm.filters.value = Filters(sort = filters.sort) })

        val list = results
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            ChannelGrid(
                channels = list,
                catalog = v.catalog,
                favorites = favKeys,
                grid = grid,
                state = state,
                onClick = { vm.play(ctx, it, list) },
                onLongClick = { vm.openSheet(it) },
                contentPadding = PaddingValues(bottom = bottomPadding.calculateBottomPadding() + 12.dp, start = if (grid) 8.dp else 0.dp, end = if (grid) 8.dp else 0.dp),
                header = {
                    if (browsing) {
                        rail("recent", "Continue watching", history.take(20), { vm.play(ctx, it, history) }, { vm.openSheet(it) })
                        rail("fav", "Favourites", favorites.take(30), { vm.play(ctx, it, favorites) }, { vm.openSheet(it) })
                        fullItem("#all") { SectionHeader("All channels") }
                    } else {
                        fullItem("#count") { SectionHeader(countLabel(list.size, "result")) }
                    }
                    if (list.isEmpty()) fullItem("#empty") {
                        EmptyState(Icons.Rounded.SearchOff, "Nothing found", "Try a different word, or clear some filters.")
                    }
                },
            )
        }
    }

    when (picker) {
        FilterKind.CATEGORY -> GroupPicker(GroupType.CATEGORY, v, filters.category, { id -> vm.filters.value = filters.copy(category = id) }) { picker = null }
        FilterKind.COUNTRY -> GroupPicker(GroupType.COUNTRY, v, filters.country, { id -> vm.filters.value = filters.copy(country = id) }) { picker = null }
        FilterKind.LANGUAGE -> GroupPicker(GroupType.LANGUAGE, v, filters.language, { id -> vm.filters.value = filters.copy(language = id) }) { picker = null }
        FilterKind.REGION -> GroupPicker(GroupType.REGION, v, filters.region, { id -> vm.filters.value = filters.copy(region = id) }) { picker = null }
        FilterKind.QUALITY -> PickerSheet(
            title = "Minimum quality",
            items = QUALITIES,
            label = { it.second },
            count = { null },
            selected = { it.first == filters.minHeight },
            onPick = { vm.filters.value = filters.copy(minHeight = it?.first ?: 0); picker = null },
            onDismiss = { picker = null },
            searchable = false,
        )
        FilterKind.SORT -> PickerSheet(
            title = "Sort by",
            items = SortOrder.entries,
            label = { it.label },
            count = { null },
            selected = { it == filters.sort },
            onPick = { vm.filters.value = filters.copy(sort = it ?: SortOrder.NAME); picker = null },
            onDismiss = { picker = null },
            searchable = false,
        )
        null -> Unit
    }
}

private val QUALITIES = listOf(720 to "HD (720p and up)", 1080 to "Full HD (1080p and up)", 2160 to "4K")

@Composable
private fun FilterRow(f: Filters, catalog: Catalog, onOpen: (FilterKind) -> Unit, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chip(f.category?.let { if (it == MainViewModel.UNCATEGORISED) "Uncategorised" else catalog.categoryName(it) } ?: "Category", f.category != null) { onOpen(FilterKind.CATEGORY) }
        Chip(f.country?.let { "${catalog.flag(it)} ${catalog.countryName(it)}".trim() } ?: "Country", f.country != null) { onOpen(FilterKind.COUNTRY) }
        Chip(f.language?.let { catalog.languageName(it) } ?: "Language", f.language != null) { onOpen(FilterKind.LANGUAGE) }
        Chip(f.region?.let { code -> catalog.regions.find { it.code == code }?.name ?: code } ?: "Region", f.region != null) { onOpen(FilterKind.REGION) }
        Chip(QUALITIES.find { it.first == f.minHeight }?.second?.substringBefore(" (") ?: "Quality", f.minHeight > 0) { onOpen(FilterKind.QUALITY) }
        FilterChip(
            selected = f.sort != SortOrder.NAME,
            onClick = { onOpen(FilterKind.SORT) },
            label = { Text(f.sort.label) },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(18.dp)) },
        )
        if (f.active) TextButton(onClick = onClear) {
            Icon(Icons.Rounded.Close, null, Modifier.size(18.dp)); Spacer(Modifier.height(0.dp)); Text("Clear")
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(18.dp)) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
    )
}

/** Picker listing every category / country / language / region with channel counts. */
@Composable
fun GroupPicker(type: GroupType, v: Visible, selected: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val entries = remember(v, type) { groupEntries(type, v) }
    PickerSheet(
        title = type.label,
        items = entries,
        label = { it.label },
        count = { it.count },
        selected = { it.id == selected },
        onPick = { onPick(it?.id); onDismiss() },
        onDismiss = onDismiss,
    )
}

data class GroupEntry(val id: String, val label: String, val name: String, val count: Int, val flag: String = "")

fun groupEntries(type: GroupType, v: Visible): List<GroupEntry> {
    val c = v.catalog
    return when (type) {
        GroupType.CATEGORY -> c.categories.values.mapNotNull { cat ->
            val n = v.countByCategory[cat.id] ?: 0
            if (n == 0) null else GroupEntry(cat.id, cat.name, cat.name, n)
        }.sortedBy { it.name } + listOfNotNull(
            if (v.uncategorised > 0) GroupEntry(MainViewModel.UNCATEGORISED, "Uncategorised", "Uncategorised", v.uncategorised) else null,
        )
        GroupType.COUNTRY -> v.countByCountry.map { (code, n) ->
            val name = c.countryName(code)
            val flag = c.flag(code)
            GroupEntry(code, if (flag.isEmpty()) name else "$flag  $name", name, n, flag)
        }.sortedBy { it.name }
        GroupType.LANGUAGE -> v.countByLanguage.map { (code, n) ->
            val name = c.languageName(code)
            GroupEntry(code, name, name, n)
        }.sortedByDescending { it.count }
        GroupType.REGION -> c.regions.mapNotNull { r ->
            val n = v.countByRegion[r.code] ?: 0
            if (n == 0) null else GroupEntry(r.code, r.name, r.name, n)
        }
    }
}
