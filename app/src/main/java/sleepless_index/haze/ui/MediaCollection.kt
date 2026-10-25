package sleepless_index.haze.ui

import androidx.compose.animation.AnimatedContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import sleepless_index.haze.data.thumbRequest
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.util.lerp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import sleepless_index.haze.data.Entry
import sleepless_index.haze.data.LayoutMode
import sleepless_index.haze.data.MediaItem
import sleepless_index.haze.data.formatDuration
import sleepless_index.haze.data.monthLabel
import sleepless_index.haze.ui.theme.GIcons
import sleepless_index.haze.util.shareItems
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun DateHeader(entry: Entry.Header, horizontalPad: Dp, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = horizontalPad, end = horizontalPad, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = entry.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (entry.sub.isNotEmpty()) {
            Text(
                text = entry.sub,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaTile(
    item: MediaItem,
    isFav: Boolean,
    selectMode: Boolean,
    selected: Boolean,
    baseCorner: Dp,
    thumbPx: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme

    // One animated value drives scale, corners and dimming; it is only read while drawing,
    // so selecting a tile never recomposes it per frame.
    val sel by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "tileSel",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                val sc = 1f - 0.14f * sel
                scaleX = sc
                scaleY = sc
                val base = baseCorner.toPx()
                shape = RoundedCornerShape(lerp(base, maxOf(base, 14.dp.toPx()), sel.coerceIn(0f, 1f)))
                clip = true
            }
            .background(Color(0xFF1A1A1A))
    ) {
        val request = remember(item.uri, thumbPx) {
            thumbRequest(ctx, item.uri, thumbPx, item.isVideo)
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black.copy(alpha = (0.38f * sel).coerceIn(0f, 0.38f))) }
        )

        if (item.isVideo) {
            DurationBadge(item.durationMs, Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }

        if (item.trashedMs > 0L) {
            val left = ((item.trashedMs + 30L * 24 * 3600 * 1000 - System.currentTimeMillis()) / (24L * 3600 * 1000) + 1).coerceIn(0L, 30L)
            Text(
                text = if (left == 1L) "1 day" else "$left days",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }

        AnimatedVisibility(
            visible = isFav,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp),
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.38f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(GIcons.HeartFilled, contentDescription = null, tint = HeartRed, modifier = Modifier.size(19.dp))
            }
        }

        AnimatedVisibility(
            visible = selectMode,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            val fill by animateColorAsState(
                targetValue = if (selected) cs.primary else Color.Black.copy(alpha = 0.28f),
                animationSpec = tween(160),
                label = "boxFill",
            )
            val boxShape = RoundedCornerShape(6.dp)
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(boxShape)
                    .background(fill)
                    .border(
                        BorderStroke(1.5.dp, if (selected) cs.primary else Color.White.copy(alpha = 0.85f)),
                        boxShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(
                    visible = selected,
                    enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                ) {
                    Icon(GIcons.Check, contentDescription = null, tint = cs.onPrimary, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

/** Small "play + length" badge used on tiles and on the viewer's video preview. */
@Composable
fun DurationBadge(durationMs: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(start = 5.dp, end = 7.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(GIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
        Text(
            text = formatDuration(durationMs),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun MediaCollection(
    entries: List<Entry>,
    layout: LayoutMode,
    columns: Int,
    selected: Set<Long>,
    selectMode: Boolean,
    favorites: Set<Long>,
    onClick: (MediaItem) -> Unit,
    onSetSelected: (Long, Boolean) -> Unit,
    bottomPad: Dp,
    modifier: Modifier = Modifier,
    emptyText: String = "Nothing here yet",
) {
    if (entries.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = emptyText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        return
    }

    // Pinterest-style: one continuous feed. Date headers would force both columns to line up
    // and leave gaps, so the masonry layout skips them.
    val masonryEntries = remember(entries) { entries.filterIsInstance<Entry.Media>() }
    val idToItem = remember(entries) { entries.filterIsInstance<Entry.Media>().associate { it.item.id to it.item } }
    val currentOnClick by rememberUpdatedState(onClick)
    val keyToId = remember(entries) { entries.filterIsInstance<Entry.Media>().associate { it.key to it.item.id } }
    val currentLayout by rememberUpdatedState(layout)
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSet by rememberUpdatedState(onSetSelected)
    val dragScope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val staggeredState = rememberLazyStaggeredGridState()
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val gridThumb = (screenWidthPx / columns * 1.15f).toInt().coerceIn(160, 640)
    val masonryThumb = (screenWidthPx * 0.62f).toInt().coerceIn(320, 800)

    fun idAt(pos: Offset): Long? {
        val x = pos.x.toInt()
        val y = pos.y.toInt()
        val key: Any? = if (currentLayout == LayoutMode.GRID) {
            gridState.layoutInfo.visibleItemsInfo.firstOrNull {
                x >= it.offset.x && x < it.offset.x + it.size.width &&
                    y >= it.offset.y && y < it.offset.y + it.size.height
            }?.key
        } else {
            staggeredState.layoutInfo.visibleItemsInfo.firstOrNull {
                x >= it.offset.x && x < it.offset.x + it.size.width &&
                    y >= it.offset.y && y < it.offset.y + it.size.height
            }?.key
        }
        return (key as? String)?.let { keyToId[it] }
    }

    Box(
        modifier
            .fillMaxSize()
            // Plain taps are resolved here (tiles have no click handler of their own), so a
            // long-press + release can never be mistaken for a tap that would deselect.
            .pointerInput(Unit) {
                detectTapGestures(onTap = { pos ->
                    val id = idAt(pos)
                    val item = id?.let { idToItem[it] }
                    if (item != null) currentOnClick(item)
                })
            }
            // Long-press a tile, then drag across others to select (or unselect) many at once.
            .pointerInput(Unit) {
                var mode = true
                val visited = HashSet<Long>()
                var job: Job? = null
                var last = Offset.Zero
                detectDragGesturesAfterLongPress(
                    onDragStart = { pos ->
                        last = pos
                        visited.clear()
                        val id = idAt(pos)
                        if (id != null) {
                            mode = id !in currentSelected
                            visited += id
                            currentOnSet(id, mode)
                        }
                        dragging = true
                        job = dragScope.launch {
                            while (true) {
                                val dy = when {
                                    last.y < 140f -> -26f
                                    last.y > size.height - 140f -> 26f
                                    else -> 0f
                                }
                                if (dy != 0f) {
                                    if (currentLayout == LayoutMode.GRID) gridState.scrollBy(dy) else staggeredState.scrollBy(dy)
                                    val other = idAt(last)
                                    if (other != null && visited.add(other)) currentOnSet(other, mode)
                                }
                                delay(16)
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        last = change.position
                        val id = idAt(last)
                        if (id != null && visited.add(id)) currentOnSet(id, mode)
                    },
                    onDragEnd = {
                        job?.cancel()
                        dragging = false
                    },
                    onDragCancel = {
                        job?.cancel()
                        dragging = false
                    },
                )
            },
    ) {
        AnimatedContent(
            targetState = layout,
            transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) },
            label = "layoutSwitch",
        ) { mode ->
            Box(Modifier.fillMaxSize()) {
                if (mode == LayoutMode.GRID) {
                    LazyVerticalGrid(
                        state = gridState,
                        userScrollEnabled = !dragging,
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(bottom = bottomPad),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = entries,
                            key = { it.key },
                            span = { e ->
                                if (e is Entry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                            },
                            contentType = { it::class },
                        ) { e ->
                            when (e) {
                                is Entry.Header -> DateHeader(
                                    entry = e,
                                    horizontalPad = 20.dp,
                                    modifier = Modifier.animateItem(),
                                )
                                is Entry.Media -> MediaTile(
                                    item = e.item,
                                    isFav = e.item.id in favorites,
                                    selectMode = selectMode,
                                    selected = e.item.id in selected,
                                    baseCorner = 0.dp,
                                    thumbPx = gridThumb,
                                    onClick = { onClick(e.item) },
                                    modifier = Modifier
                                        .animateItem()
                                        .aspectRatio(1f),
                                )
                            }
                        }
                    }
                } else {
                    LazyVerticalStaggeredGrid(
                        state = staggeredState,
                        userScrollEnabled = !dragging,
                        columns = StaggeredGridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = bottomPad),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalItemSpacing = 6.dp,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = masonryEntries,
                            key = { it.key },
                            contentType = { it::class },
                            span = { e ->
                                StaggeredGridItemSpan.SingleLane
                            },
                        ) { e ->
                            when (e) {
                                is Entry.Header -> DateHeader(
                                    entry = e,
                                    horizontalPad = 14.dp,
                                    modifier = Modifier.animateItem(),
                                )
                                is Entry.Media -> MediaTile(
                                    item = e.item,
                                    isFav = e.item.id in favorites,
                                    selectMode = selectMode,
                                    selected = e.item.id in selected,
                                    baseCorner = 16.dp,
                                    thumbPx = masonryThumb,
                                    onClick = { onClick(e.item) },
                                    modifier = Modifier
                                        .animateItem()
                                        .fillMaxWidth()
                                        .aspectRatio(e.item.aspect),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Bottom action bar shown while selecting. */
@Composable
fun SelectionBar(
    vm: GalleryViewModel,
    source: List<MediaItem>,
    trash: Boolean,
    modifier: Modifier = Modifier,
) {
    val selected by vm.selected.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val has = selected.isNotEmpty()
    var picker by remember { mutableStateOf(0) }
    var confirmForever by remember { mutableStateOf(false) }

    PillBar(modifier) {
        if (!trash) {
            val allFav = has && selected.all { it in favs }
            PillItem(
                icon = GIcons.Share, label = "Share", selected = false,
                onClick = { if (has) shareItems(context, source.filter { it.id in selected }) },
                modifier = Modifier.weight(1f),
            )
            PillItem(
                icon = if (allFav) GIcons.HeartFilled else GIcons.Heart,
                label = if (allFav) "Unfavorite" else "Favorite",
                selected = allFav,
                onClick = { if (has) vm.toggleFavorite(selected) },
                modifier = Modifier.weight(1f),
            )
            PillItem(
                icon = GIcons.Copy, label = "Copy", selected = false,
                onClick = { if (has) picker = 1 },
                modifier = Modifier.weight(1f),
            )
            PillItem(
                icon = GIcons.Move, label = "Move", selected = false,
                onClick = { if (has) picker = 2 },
                modifier = Modifier.weight(1f),
            )
            PillItem(
                icon = GIcons.Trash, label = "Delete", selected = false,
                onClick = { if (has) vm.requestTrash(source.filter { it.id in selected }) },
                modifier = Modifier.weight(1f),
            )
        } else {
            PillItem(
                icon = GIcons.Restore, label = "Restore", selected = false,
                onClick = { if (has) vm.requestRestore(source.filter { it.id in selected }) },
                modifier = Modifier.weight(1f),
            )
            PillItem(
                icon = GIcons.Trash, label = "Delete forever", selected = false,
                onClick = { if (has) confirmForever = true },
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (confirmForever) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmForever = false },
            title = { Text("Delete forever?") },
            text = { Text("${selected.size} selected items will be permanently deleted. This can't be undone.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmForever = false
                    vm.requestDeleteForever(source.filter { it.id in selected })
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmForever = false }) { Text("Cancel") }
            },
        )
    }
    if (picker != 0) {
        AlbumPickerSheet(
            vm = vm,
            items = source.filter { it.id in selected },
            move = picker == 2,
            onDismiss = { picker = 0 },
        )
    }
}
