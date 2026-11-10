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
fun SearchScreen(vm: GalleryViewModel, onOpen: (List<MediaItem>, Int) -> Unit, onOpenAlbum: (Long) -> Unit = {}) {
    val items by vm.items.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val selectMode by vm.selectMode.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val results by produceState(emptyList<MediaItem>(), items, query, filter, favs) {
        val q = query.trim().lowercase(Locale.getDefault())
        value = if (q.isEmpty() && filter == 0) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) {
                val words = q.split(' ', ',').filter { it.isNotEmpty() }
                val loc = Locale.getDefault()
                val now = System.currentTimeMillis()
                val day = 86_400_000L
                // Natural time words narrow by date and are removed from the text match.
                val cal = java.util.Calendar.getInstance()
                var from = Long.MIN_VALUE
                var to = Long.MAX_VALUE
                val text = ArrayList<String>()
                var i = 0
                while (i < words.size) {
                    val w = words[i]
                    val next = words.getOrNull(i + 1)
                    when {
                        w == "today" -> { cal.timeInMillis = now; cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0); from = cal.timeInMillis }
                        w == "yesterday" -> { cal.timeInMillis = now; cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0); to = cal.timeInMillis; from = to - day }
                        w == "last" && next == "week" -> { from = now - 14 * day; to = now - 7 * day; i++ }
                        w == "last" && next == "month" -> { from = now - 60 * day; to = now - 30 * day; i++ }
                        w == "last" && next == "year" -> { cal.timeInMillis = now; val y = cal.get(java.util.Calendar.YEAR) - 1; cal.clear(); cal.set(y, 0, 1); from = cal.timeInMillis; cal.set(y + 1, 0, 1); to = cal.timeInMillis; i++ }
                        w == "week" && text.lastOrNull() == "this" -> { text.removeAt(text.lastIndex); from = now - 7 * day }
                        w == "month" && text.lastOrNull() == "this" -> { text.removeAt(text.lastIndex); from = now - 30 * day }
                        else -> text.add(w)
                    }
                    i++
                }
                fun close(a: String, b: String): Boolean {
                    if (a.length < 4) return false
                    // One typo allowed: same length with one differing char, or one char missing/extra.
                    if (kotlin.math.abs(a.length - b.length) > 1) return false
                    var x = 0; var y = 0; var miss = 0
                    while (x < a.length && y < b.length) {
                        if (a[x] == b[y]) { x++; y++; continue }
                        if (++miss > 1) return false
                        if (a.length > b.length) x++ else if (a.length < b.length) y++ else { x++; y++ }
                    }
                    return miss + (a.length - x) + (b.length - y) <= 1
                }
                items.filter { m ->
                    val typeOk = when (filter) {
                        1 -> !m.isVideo
                        2 -> m.isVideo
                        3 -> m.id in favs
                        else -> true
                    }
                    typeOk && m.dateMs in from..to && (text.isEmpty() || run {
                        val hay = buildString {
                            append(m.name.lowercase(loc)).append(' ')
                            append(m.bucketName.lowercase(loc)).append(' ')
                            append(monthLabel(m.dateMs).lowercase(loc)).append(' ')
                            append(if (m.isVideo) "video videos" else "photo photos image").append(' ')
                        }
                        val hayWords = hay.split(' ', '_', '-', '.').filter { it.isNotEmpty() }
                        text.all { w -> w in hay || hayWords.any { close(w, it) } }
                    })
                }
            }
        }
    }
    val albumHits = remember(albums, query) {
        val q = query.trim().lowercase(Locale.getDefault())
        if (q.length < 2) emptyList() else albums.filter { it.name.lowercase(Locale.getDefault()).contains(q) }.take(10)
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
                        Text("Name, album, month or year", color = cs.onSurfaceVariant, fontSize = 15.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = cs.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(cs.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
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
            ) {
                listOf("All", "Photos", "Videos", "Favorites").forEachIndexed { i, label ->
                    FilterChipPill(label = label, active = filter == i, onClick = { filter = i })
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
                        val idx = results.indexOfFirst { it.id == item.id }
                        if (idx >= 0) onOpen(results, idx)
                    }
                },
                onSetSelected = { id, on -> vm.setSelected(id, on) },
                bottomPad = BottomBarSpace + navBottomInset(),
                modifier = Modifier.weight(1f),
                header = if (albumHits.isNotEmpty() && !selectMode) {
                    {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    albumHits.forEach { a ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(cs.surface)
                                .border(BorderStroke(1.dp, cs.outline), RoundedCornerShape(16.dp))
                                .bounceClick(onClick = { onOpenAlbum(a.bucketId) })
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            coil.compose.AsyncImage(
                                model = remember(a.cover.uri) { sleepless_index.haze.data.thumbRequest(ctx, a.cover.uri, 120, a.cover.isVideo) },
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceVariant),
                            )
                            Column(Modifier.padding(end = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(a.name, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground, maxLines = 1)
                                Text("%,d items".format(a.count), fontSize = 13.sp, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
                    }
                } else null,
                emptyText = if (query.isBlank() && filter == 0) "Search your library" else "No matches",
            )
        }
        SelectionBarHost(selectMode, vm, results, trash = false, modifier = Modifier.align(Alignment.BottomCenter))
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

