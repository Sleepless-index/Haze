package sleepless_index.haze.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sleepless_index.haze.data.Entry
import sleepless_index.haze.data.LayoutMode
import sleepless_index.haze.data.MediaItem
import sleepless_index.haze.data.MediaKind
import sleepless_index.haze.data.buildEntries
import sleepless_index.haze.data.monthLabel
import sleepless_index.haze.ui.theme.GIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val BottomBarSpace = 88.dp

@Composable
private fun navBottomInset() = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** Slides the selection bar (with its fade) up from the bottom. */
@Composable
private fun SelectionBarHost(
    visible: Boolean,
    vm: GalleryViewModel,
    source: List<MediaItem>,
    trash: Boolean,
    modifier: Modifier = Modifier,
    vault: Boolean = false,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(tween(260)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(160)),
    ) {
        Box {
            BottomFade(Modifier.align(Alignment.BottomCenter), height = 90.dp)
            if (vault) {
                VaultSelectionBar(vm, source, Modifier.align(Alignment.BottomCenter))
            } else {
                SelectionBar(vm = vm, source = source, trash = trash, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Photos tab
// ---------------------------------------------------------------------------------------------

@Composable
fun PhotosScreen(vm: GalleryViewModel, onOpen: (List<MediaItem>, Int) -> Unit, onOpenSettings: () -> Unit) {
    val entries by vm.photoEntries.collectAsStateWithLifecycle()
    val items by vm.photos.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val selectMode by vm.selectMode.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Crossfade(targetState = selectMode, animationSpec = tween(180), label = "photosHeader") { selecting ->
                if (selecting) {
                    SelectionHeader(
                        count = selected.size,
                        onSelectAll = { vm.selectAll(items.map { it.id }) },
                        onCancel = { vm.clearSelection() },
                        allSelected = items.isNotEmpty() && selected.size >= items.size,
                        onDeselectAll = { vm.deselectAll() },
                    )
                } else {
                    ScreenHeader(
                        title = "Photos",
                        subtitle = "%,d items".format(items.size),
                        trailing = {
                            GIconButton(GIcons.CheckSquare, "Select items", onClick = { vm.startSelect() }, flat = true)
                            Box {
                                GIconButton(GIcons.More, "More options", onClick = { menu = true }, flat = true)
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    CompactMenuItem(
                                        text = {
                                            Text(if (prefs.layout == LayoutMode.GRID) "Masonry layout" else "Grid layout")
                                        },
                                        onClick = {
                                            menu = false
                                            vm.setLayout(
                                                if (prefs.layout == LayoutMode.GRID) LayoutMode.MASONRY else LayoutMode.GRID
                                            )
                                        },
                                    )
                                    CompactMenuItem(
                                        text = { Text("Refresh") },
                                        onClick = {
                                            menu = false
                                            vm.refresh()
                                        },
                                    )
                                    CompactMenuItem(
                                        text = { Text("Settings") },
                                        onClick = {
                                            menu = false
                                            onOpenSettings()
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            }
            MediaCollection(
                entries = entries,
                layout = prefs.layout,
                columns = prefs.columns,
                selected = selected,
                selectMode = selectMode,
                favorites = favs,
                onClick = { item ->
                    if (selectMode) {
                        vm.toggle(item.id)
                    } else {
                        val idx = items.indexOfFirst { it.id == item.id }
                        if (idx >= 0) onOpen(items, idx)
                    }
                },
                onSetSelected = { id, on -> vm.setSelected(id, on) },
                bottomPad = BottomBarSpace + navBottomInset(),
                modifier = Modifier.weight(1f),
                emptyText = "No photos or videos found",
            )
        }
        SelectionBarHost(selectMode, vm, items, trash = false, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

// ---------------------------------------------------------------------------------------------
// Album / Favorites / Videos / Trash
// ---------------------------------------------------------------------------------------------

@Composable
fun CollectionScreen(
    vm: GalleryViewModel,
    title: String,
    items: List<MediaItem>,
    trash: Boolean,
    onBack: () -> Unit,
    onOpen: (List<MediaItem>, Int) -> Unit,
    flat: Boolean = false,
    vault: Boolean = false,
    trailingExtra: @Composable () -> Unit = {},
) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val selectMode by vm.selectMode.collectAsStateWithLifecycle()
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val entries = remember(items, flat, trash, vault) {
        // "flat" = one continuous grid with no date headers (Recent album and Trash)
        if (flat || trash || vault) items.map { Entry.Media(it) } else buildEntries(items)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Crossfade(targetState = selectMode, animationSpec = tween(180), label = "collectionHeader") { selecting ->
                if (selecting) {
                    SelectionHeader(
                        count = selected.size,
                        onSelectAll = { vm.selectAll(items.map { it.id }) },
                        onCancel = { vm.clearSelection() },
                        allSelected = items.isNotEmpty() && selected.size >= items.size,
                        onDeselectAll = { vm.deselectAll() },
                    )
                } else {
                    ScreenHeader(
                        title = title,
                        subtitle = "%,d items".format(items.size),
                        leading = {
                            GIconButton(GIcons.Back, "Back", onClick = onBack)
                            Box(Modifier.size(12.dp))
                        },
                        trailing = {
                            trailingExtra()
                            GIconButton(GIcons.CheckSquare, "Select items", onClick = { vm.startSelect() }, flat = true)
                        },
                    )
                }
            }
            MediaCollection(
                entries = entries,
                layout = prefs.layout,
                columns = prefs.columns,
                selected = selected,
                selectMode = selectMode,
                favorites = favs,
                onClick = { item ->
                    if (selectMode || trash) {
                        vm.toggle(item.id)
                    } else {
                        val idx = items.indexOfFirst { it.id == item.id }
                        if (idx >= 0) onOpen(items, idx)
                    }
                },
                onSetSelected = { id, on -> vm.setSelected(id, on) },
                bottomPad = BottomBarSpace + navBottomInset(),
                modifier = Modifier.weight(1f),
                emptyText = if (trash) "Trash is empty" else if (vault) "Your Vault is empty" else "Nothing here yet",
            )
        }
        if (trash && !selectMode && items.isNotEmpty()) {
            val scheme = androidx.compose.material3.MaterialTheme.colorScheme
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(scheme.surface)
                        .drawBehind { drawLine(scheme.outline, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
                        .bounceClick(onClick = { confirmDeleteAll = true })
                        .padding(top = 12.dp, bottom = 12.dp)
                        .padding(bottom = navBottomInset()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    androidx.compose.material3.Icon(
                        GIcons.Trash, contentDescription = null,
                        tint = scheme.onBackground, modifier = Modifier.size(24.dp),
                    )
                    androidx.compose.material3.Text(
                        "Delete all", color = scheme.onBackground,
                        fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        if (confirmDeleteAll) {
            DeleteForeverConfirm(
                items = items,
                everything = true,
                onConfirm = { confirmDeleteAll = false; vm.emptyTrash() },
                onDismiss = { confirmDeleteAll = false },
            )
        }
        SelectionBarHost(selectMode, vm, items, trash = trash, modifier = Modifier.align(Alignment.BottomCenter), vault = vault)
    }
}

// ---------------------------------------------------------------------------------------------
// Search tab
// ---------------------------------------------------------------------------------------------

@Composable
fun SearchScreen(vm: GalleryViewModel, onOpen: (List<MediaItem>, Int) -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val filter by vm.searchFilter.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val selectMode by vm.selectMode.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme

    var query by rememberSaveable { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val idle = query.isBlank() && !filter.isActive

    val results by produceState(emptyList<MediaItem>(), items, query, filter, favs) {
        val q = query.trim().lowercase(Locale.getDefault())
        value = if (q.isEmpty() && !filter.isActive) {
            emptyList()
        } else {
            val matches = filter.matcher(favs)
            withContext(Dispatchers.Default) {
                items.filter { m ->
                    matches(m) && (q.isEmpty() ||
                        m.name.lowercase(Locale.getDefault()).contains(q) ||
                        m.bucketName.lowercase(Locale.getDefault()).contains(q) ||
                        monthLabel(m.dateMs).lowercase(Locale.getDefault()).contains(q))
                }
            }
        }
    }
    val entries = remember(results) { buildEntries(results) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) cs.primary else cs.outline,
        animationSpec = tween(200),
        label = "searchBorder",
    )

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Crossfade(targetState = selectMode, animationSpec = tween(180), label = "searchHeader") { selecting ->
                if (selecting) {
                    SelectionHeader(
                        count = selected.size,
                        onSelectAll = { vm.selectAll(results.map { it.id }) },
                        onCancel = { vm.clearSelection() },
                        allSelected = results.isNotEmpty() && selected.size >= results.size,
                        onDeselectAll = { vm.deselectAll() },
                    )
                } else {
                    ScreenHeader(title = "Search", subtitle = "")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(cs.surface)
                    .border(BorderStroke(1.5.dp, borderColor), RoundedCornerShape(26.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(GIcons.Search, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Name, album or month", color = cs.onSurfaceVariant, fontSize = 15.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = cs.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(cs.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { vm.addRecentSearch(query); focusManager.clearFocus() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focused = it.isFocused },
                    )
                }
                AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn(tween(120)), exit = fadeOut(tween(120))) {
                    GIconButton(
                        GIcons.Close, "Clear search", onClick = { query = "" },
                        size = 30.dp, container = cs.surfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChipPill(
                    label = if (filter.advancedCount > 0) "Filters · ${filter.advancedCount}" else "Filters",
                    active = filter.advancedCount > 0,
                    onClick = { focusManager.clearFocus(); showFilters = true },
                )
                val quick = listOf("All", "Photos", "Videos", "GIFs", "Screenshots", "Favorites")
                val current = if (filter.favoritesOnly) 5 else when (filter.kind) {
                    MediaKind.ALL -> 0
                    MediaKind.PHOTO -> 1
                    MediaKind.VIDEO -> 2
                    MediaKind.GIF -> 3
                    MediaKind.SCREENSHOT -> 4
                }
                quick.forEachIndexed { i, label ->
                    FilterChipPill(label = label, active = current == i, onClick = {
                        vm.setSearchFilter(
                            filter.copy(
                                favoritesOnly = i == 5,
                                kind = when (i) {
                                    1 -> MediaKind.PHOTO
                                    2 -> MediaKind.VIDEO
                                    3 -> MediaKind.GIF
                                    4 -> MediaKind.SCREENSHOT
                                    else -> MediaKind.ALL
                                },
                            ),
                        )
                    })
                }
            }

            val active = activeFilterLabels(filter)
            if (active.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for ((label, without) in active) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(cs.primary.copy(alpha = 0.18f))
                                .bounceClick(onClick = { vm.setSearchFilter(without) })
                                .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground)
                            Icon(GIcons.Close, "Remove filter", tint = cs.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            if (idle) {
                val recents = prefs.recentSearches
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (recents.isEmpty()) {
                        Text(
                            "Search your library", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground,
                            modifier = Modifier.padding(top = 24.dp, start = 4.dp),
                        )
                        Text(
                            "Try a name, an album or a month. Filters narrow by date, size, resolution or video length.",
                            fontSize = 13.sp, lineHeight = 19.sp, color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        )
                    } else {
                        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Recent", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text(
                                "Clear", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant,
                                modifier = Modifier.clip(RoundedCornerShape(14.dp)).bounceClick(onClick = { vm.clearRecentSearches() }).padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                        for (r in recents) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .bounceClick(onClick = { query = r })
                                    .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(GIcons.Search, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Text(r, fontSize = 15.sp, color = cs.onBackground, maxLines = 1, modifier = Modifier.weight(1f))
                                GIconButton(
                                    GIcons.Close, "Remove", onClick = { vm.removeRecentSearch(r) },
                                    size = 36.dp, flat = true, tint = cs.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            } else {
                MediaCollection(
                    entries = entries,
                    layout = prefs.layout,
                    columns = prefs.columns,
                    selected = selected,
                    selectMode = selectMode,
                    favorites = favs,
                    onClick = { item ->
                        if (selectMode) {
                            vm.toggle(item.id)
                        } else {
                            vm.addRecentSearch(query)
                            val idx = results.indexOfFirst { it.id == item.id }
                            if (idx >= 0) onOpen(results, idx)
                        }
                    },
                    onSetSelected = { id, on -> vm.setSelected(id, on) },
                    bottomPad = BottomBarSpace + navBottomInset(),
                    modifier = Modifier.weight(1f),
                    emptyText = "No matches",
                )
            }
        }
        SelectionBarHost(selectMode, vm, results, trash = false, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showFilters) {
        SearchFilterSheet(
            filter = filter,
            albums = albums,
            resultCount = results.size,
            onChange = { vm.setSearchFilter(it) },
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
fun FilterChipPill(label: String, active: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (active) cs.primary else cs.surface, tween(200), label = "chipBg")
    val fg by animateColorAsState(if (active) cs.onPrimary else cs.onBackground, tween(200), label = "chipFg")
    val line by animateColorAsState(if (active) cs.primary else cs.outline, tween(200), label = "chipLine")
    Text(
        text = label,
        color = fg,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(BorderStroke(1.dp, line), RoundedCornerShape(20.dp))
            .bounceClick(role = androidx.compose.ui.semantics.Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

