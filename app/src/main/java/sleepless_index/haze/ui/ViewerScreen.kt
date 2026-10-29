package sleepless_index.haze.ui

import android.content.Context
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import sleepless_index.haze.data.MediaItem
import sleepless_index.haze.data.formatDuration
import sleepless_index.haze.data.formatSize
import sleepless_index.haze.ui.theme.GIcons
import sleepless_index.haze.util.shareItems
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ChromeBg = Color(0xCC141414)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(
    vm: GalleryViewModel,
    startIndex: Int,
    onBack: () -> Unit,
    onPlay: (MediaItem) -> Unit,
    onEdit: (MediaItem) -> Unit,
) {
    val list by vm.viewerList.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()

    if (list.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, list.lastIndex)) { list.size }
    var chrome by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    var confirmVault by remember { mutableStateOf(false) }
    var wallpaperSheet by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    // null = closed, false = copy to album, true = move to album
    var picker by remember { mutableStateOf<Boolean?>(null) }
    val context = LocalContext.current
    val inVault = list[pagerState.currentPage.coerceIn(0, list.lastIndex)].id < 0
    val current = list[pagerState.currentPage.coerceIn(0, list.lastIndex)]
    val isFav = current.id in favs

    val dateFmt = remember { DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()) }
    val timeFmt = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()) }
    val zoned = Instant.ofEpochMilli(current.dateMs).atZone(ZoneId.systemDefault())

    val topBarH = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
    val bottomBarH = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 14.dp + 56.dp + 10.dp +
        (if (list.size > 1) 88.dp else 0.dp)
    val insetTop by animateDpAsState(if (chrome) topBarH else 0.dp, tween(220), label = "insetTop")
    val insetBottom by animateDpAsState(if (chrome) bottomBarH else 0.dp, tween(220), label = "insetBottom")

    val blurOn = vm.prefs.collectAsStateWithLifecycle().value.blur
    val blurDp by animateDpAsState(if (showInfo && blurOn) 28.dp else 0.dp, tween(260), label = "infoBlur")
    Box(Modifier.fillMaxSize().background(Color.Black).blur(blurDp)) {
        // One backdrop for the current item, behind the pager, so neighbouring pages' blur can't leak in.
        if (blurOn) {
            androidx.compose.animation.Crossfade(
                targetState = current,
                animationSpec = tween(250),
                label = "ambientBackdrop",
                modifier = Modifier.fillMaxSize().clipToBounds(),
            ) { AmbientBackdrop(it) }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 12.dp,
            beyondViewportPageCount = 1,
            key = { index -> list.getOrNull(index)?.id ?: index.toLong() },
        ) { page ->
            val item = list[page]
            Box(Modifier.fillMaxSize()) {
            if (item.isVideo) {
                VideoPage(
                    item = item,
                    insetTop = insetTop,
                    insetBottom = insetBottom,
                    onTap = { chrome = !chrome },
                    onPlay = { onPlay(item) },
                )
            } else {
                ZoomableImage(
                    item = item,
                    onTap = { chrome = !chrome },
                    insetTop = insetTop,
                    insetBottom = insetBottom,
                    onSwipeDown = onBack,
                )
            }
            }
        }

        AnimatedVisibility(
            visible = chrome,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 2 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { -it / 2 },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GIconButton(GIcons.Back, "Back", onClick = onBack, container = ChromeBg, tint = Color.White, flat = true)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = dateFmt.format(zoned),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = timeFmt.format(zoned),
                        color = Color(0xFFA8A8AC),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Box {
                    GIconButton(GIcons.More, "More options", onClick = { menu = true }, container = ChromeBg, tint = Color.White, flat = true)
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        CompactMenuItem(
                            text = { Text("Details") },
                            onClick = {
                                menu = false
                                showInfo = true
                            },
                        )
                        if (!inVault) {
                            if (!current.isVideo) {
                                CompactMenuItem(
                                    text = { Text("Set as wallpaper") },
                                    onClick = {
                                        menu = false
                                        wallpaperSheet = true
                                    },
                                )
                                CompactMenuItem(
                                    text = { Text("Set as contact photo") },
                                    onClick = {
                                        menu = false
                                        setAsContactPhoto(context, current)
                                    },
                                )
                                CompactMenuItem(
                                    text = { Text("Print photo") },
                                    onClick = {
                                        menu = false
                                        printImage(context, current)
                                    },
                                )
                            }
                            CompactMenuItem(
                                text = { Text("Copy to album") },
                                onClick = {
                                    menu = false
                                    picker = false
                                },
                            )
                            CompactMenuItem(
                                text = { Text("Move to album") },
                                onClick = {
                                    menu = false
                                    picker = true
                                },
                            )
                            CompactMenuItem(
                                text = { Text("Move to Vault") },
                                onClick = {
                                    menu = false
                                    confirmVault = true
                                },
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = chrome && !current.isVideo && !inVault,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = bottomBarH + 6.dp),
            enter = fadeIn(tween(180)) + scaleIn(tween(200)),
            exit = fadeOut(tween(140)) + scaleOut(tween(160)),
        ) {
            GIconButton(
                GIcons.Lens, "Search this image",
                onClick = { sleepless_index.haze.util.searchImage(context, current) },
                size = 40.dp,
                container = Color(0xCC4A4A4E),
                tint = Color.White,
            )
        }

        AnimatedVisibility(
            visible = chrome,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(180)) + slideInVertically(tween(240)) { it / 2 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 2 },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (list.size > 1) FilmStrip(list, pagerState)
                if (inVault) {
                    PillBar {
                        PillItem(
                            icon = GIcons.Unlock, label = "Unhide", selected = false,
                            onClick = { vm.vaultRestore(listOf(current)) },
                            modifier = Modifier.weight(1f),
                        )
                        PillItem(
                            icon = GIcons.Trash, label = "Delete", selected = false,
                            onClick = { vm.vaultDelete(listOf(current)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else PillBar {
                    PillItem(
                        icon = GIcons.Share, label = "Share", selected = false,
                        onClick = { sleepless_index.haze.util.shareItems(context, listOf(current), vm.prefs.value.stripLocation) },
                        modifier = Modifier.weight(1f),
                    )
                    PillItem(
                        icon = if (isFav) GIcons.HeartFilled else GIcons.Heart,
                        label = "Favorite",
                        selected = isFav,
                        onClick = { vm.toggleFavorite(listOf(current.id)) },
                        modifier = Modifier.weight(1f),
                    )
                    PillItem(
                        icon = GIcons.Edit, label = "Edit", selected = false,
                        onClick = { onEdit(current) },
                        modifier = Modifier.weight(1f),
                    )
                    PillItem(
                        icon = GIcons.Trash, label = "Delete", selected = false,
                        onClick = { confirmTrash = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (showInfo) {
        InfoSheet(item = current, translucent = blurOn, onDismiss = { showInfo = false }, onRename = { vm.rename(current, it) },
            onEditDate = { vm.editMeta(current, it, false) }, onRemoveLocation = { vm.editMeta(current, null, true) })
    }
    if (confirmTrash) {
        TrashConfirm(
            items = listOf(current),
            trashDays = vm.prefs.value.trashDays,
            onConfirm = { confirmTrash = false; vm.requestTrash(listOf(current)) },
            onDismiss = { confirmTrash = false },
        )
    }
    if (confirmVault) {
        VaultConfirm(
            items = listOf(current),
            onConfirm = { confirmVault = false; vm.moveToVault(listOf(current)) },
            onDismiss = { confirmVault = false },
        )
    }
    if (wallpaperSheet) WallpaperSheet(vm, current, onDismiss = { wallpaperSheet = false })
    picker?.let { move ->
        AlbumPickerSheet(vm = vm, items = listOf(current), move = move, onDismiss = { picker = null })
    }
}

private fun attachIntent(item: MediaItem) = android.content.Intent(android.content.Intent.ACTION_ATTACH_DATA)
    .setDataAndType(item.uri, item.mime.ifBlank { "image/*" })
    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)

/** Sends the photo to a Contacts app to use as a contact photo; falls back to the generic chooser. */
private fun setAsContactPhoto(context: Context, item: MediaItem) {
    val intent = attachIntent(item)
    // Several apps answer "Set as" (wallpaper, contact photo...). Go straight to the Contacts one.
    val matches = context.packageManager.queryIntentActivities(intent, 0)
    val contacts = matches.firstOrNull { it.activityInfo.packageName.contains("contacts", ignoreCase = true) }
        ?: matches.firstOrNull { it.loadLabel(context.packageManager).toString().contains("contact", ignoreCase = true) }
    val started = contacts != null && runCatching {
        context.startActivity(
            android.content.Intent(intent).setClassName(contacts.activityInfo.packageName, contacts.activityInfo.name)
        )
    }.isSuccess
    if (!started) {
        runCatching { context.startActivity(android.content.Intent.createChooser(intent, "Set as contact photo")) }
    }
}

private fun printImage(context: Context, item: MediaItem) {
    runCatching {
        androidx.print.PrintHelper(context).apply {
            scaleMode = androidx.print.PrintHelper.SCALE_MODE_FIT
        }.printBitmap(item.name.ifBlank { "Photo" }, item.uri)
    }
}

@Composable
private fun VideoPage(
    item: MediaItem,
    insetTop: Dp,
    insetBottom: Dp,
    onTap: () -> Unit,
    onPlay: () -> Unit,
) {
    val ctx = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
            .padding(top = insetTop, bottom = insetBottom),
        contentAlignment = Alignment.Center,
    ) {
        val request = remember(item.uri) { sleepless_index.haze.data.thumbRequest(ctx, item.uri, 1280, false) }
        // The frame hugs the video, so the duration badge sits on the video's own bottom-right corner.
        Box(
            modifier = Modifier
                .aspectRatio(
                    if (item.width > 0 && item.height > 0) item.width.toFloat() / item.height else 16f / 9f,
                    matchHeightConstraintsFirst = false,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = request,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xB8141414))
                    .bounceClick(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    GIcons.Play, contentDescription = "Play video",
                    tint = Color.White, modifier = Modifier.size(36.dp),
                )
            }
            DurationBadge(item.durationMs, Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
    }
}

@Composable
internal fun ZoomableImage(
    item: MediaItem,
    insetTop: Dp,
    insetBottom: Dp,
    onTap: () -> Unit,
    onSwipeDown: () -> Unit,
) {
    val ctx = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var imgSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val barsPx by rememberUpdatedState(with(density) { (insetTop + insetBottom).toPx() })
    // The picture sits between the bars, so its centre is a little off the screen centre.
    val shiftY by rememberUpdatedState(with(density) { (insetTop - insetBottom).toPx() } / 2f)

    // When turned sideways the picture must shrink/grow so it still fits between the bars.
    val fitFactor = run {
        val availW = box.width.toFloat()
        val availH = (box.height - barsPx).coerceAtLeast(1f)
        if (imgSize.width <= 0 || imgSize.height <= 0 || availW <= 0f) {
            1f
        } else {
            val iw = imgSize.width.toFloat()
            val ih = imgSize.height.toFloat()
            val s0 = minOf(availW / iw, availH / ih)
            val s1 = minOf(availW / ih, availH / iw)
            val t = kotlin.math.abs(kotlin.math.sin(Math.toRadians(rotation.toDouble()))).toFloat()
            1f + (s1 / s0 - 1f) * t
        }
    }

    /** Size the picture takes on screen at [s] zoom and (quarter-turn) rotation [rot]. */
    fun displayed(rot: Float, s: Float): Pair<Float, Float> {
        val iw = imgSize.width.toFloat()
        val ih = imgSize.height.toFloat()
        if (iw <= 0f || ih <= 0f) return box.width * s to box.height * s
        val availW = box.width.toFloat()
        val availH = (box.height - barsPx).coerceAtLeast(1f)
        val quarter = Math.round(rot / 90f) % 2 != 0
        val fit = if (quarter) minOf(availW / ih, availH / iw) else minOf(availW / iw, availH / ih)
        return (if (quarter) ih else iw) * fit * s to (if (quarter) iw else ih) * fit * s
    }

    /** Keeps the picture covering the screen while zoomed (no empty gaps at the edges). */
    fun clampOffset(o: Offset, s: Float, rot: Float): Offset {
        val (w, h) = displayed(rot, s)
        val vw = box.width.toFloat()
        val vh = box.height.toFloat()
        val x = if (w <= vw) 0f else o.x.coerceIn((vw - w) / 2f, (w - vw) / 2f)
        val y = if (h <= vh) {
            0f
        } else {
            o.y.coerceIn(vh / 2f - h / 2f - shiftY, h / 2f - vh / 2f - shiftY)
        }
        return Offset(x, y)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { box = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        // Double-tap zooms in on the spot you tapped (or back out).
                        val c = tap - Offset(box.width / 2f, box.height / 2f + shiftY)
                        scope.launch {
                            val fromS = scale
                            val fromO = offset
                            val toS = if (fromS > 1.05f) 1f else 3f
                            val toO = if (toS == 1f) Offset.Zero else clampOffset(c - (c - fromO) * (toS / fromS), toS, rotation)
                            animate(
                                initialValue = 0f,
                                targetValue = 1f,
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                            ) { t, _ ->
                                scale = fromS + (toS - fromS) * t
                                offset = Offset(fromO.x + (toO.x - fromO.x) * t, fromO.y + (toO.y - fromO.y) * t)
                            }
                        }
                    },
                )
            }
            // Two fingers: zoom + (deliberate) twist around the point between them.
            // One finger: pan while zoomed, hand over to the pager at the edges, swipe down to close.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var twisted = false
                    var twistRaw = 0f
                    var everMulti = false
                    var totalPan = Offset.Zero
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            everMulti = true
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val dRot = event.calculateRotation()
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val c = centroid - Offset(box.width / 2f, box.height / 2f + shiftY)

                            var rotDelta = 0f
                            if (twisted) {
                                rotDelta = dRot
                            } else if (scale <= 1.05f) {
                                // Thumbs wobble while pinching: only start rotating after a clear twist,
                                // and start from zero so the picture never jumps.
                                twistRaw += dRot
                                if (kotlin.math.abs(twistRaw) > 12f) {
                                    twisted = true
                                    rotDelta = twistRaw - (if (twistRaw > 0f) 12f else -12f)
                                }
                            }
                            // While twisting, zoom is locked so the rotation stays smooth.
                            val newScale = if (twisted) scale else (scale * zoom).coerceIn(1f, 6f)
                            val ratio = newScale / scale
                            val rad = Math.toRadians(rotDelta.toDouble())
                            val cs = kotlin.math.cos(rad).toFloat()
                            val sn = kotlin.math.sin(rad).toFloat()
                            val v = offset - c
                            var next = Offset(
                                c.x + ratio * (v.x * cs - v.y * sn) + pan.x,
                                c.y + ratio * (v.x * sn + v.y * cs) + pan.y,
                            )
                            rotation += rotDelta
                            scale = newScale
                            if (!twisted) next = clampOffset(next, scale, rotation)
                            offset = next
                            event.changes.forEach { it.consume() }
                        } else if (pressed == 1 && !twisted) {
                            val pan = event.calculatePan()
                            totalPan += pan
                            if (scale > 1.01f) {
                                val raw = offset + pan
                                val clamped = clampOffset(raw, scale, rotation)
                                offset = clamped
                                val hitEdgeX = raw.x != clamped.x && kotlin.math.abs(pan.x) > kotlin.math.abs(pan.y)
                                // At the edge of a zoomed picture, let the pager take the swipe.
                                if (!hitEdgeX) event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (twisted) {
                        // Settle on the nearest quarter turn (and back to fit if barely zoomed).
                        val fromR = rotation
                        val toR = Math.round(fromR / 90f) * 90f
                        val fromS = scale
                        val toS = if (fromS < 1.15f) 1f else fromS
                        val fromO = offset
                        val toO = clampOffset(fromO, toS, toR)
                        scope.launch {
                            animate(
                                initialValue = 0f,
                                targetValue = 1f,
                                animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
                            ) { t, _ ->
                                rotation = fromR + (toR - fromR) * t
                                scale = fromS + (toS - fromS) * t
                                offset = Offset(fromO.x + (toO.x - fromO.x) * t, fromO.y + (toO.y - fromO.y) * t)
                            }
                        }
                    } else if (!everMulti && scale <= 1.01f &&
                        totalPan.y > 200f && totalPan.y > 2f * kotlin.math.abs(totalPan.x)
                    ) {
                        onSwipeDown()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val request = remember(item.uri) { ImageRequest.Builder(ctx).data(item.uri).size(2048).build() }
        AsyncImage(
            model = request,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                val d = state.result.drawable
                if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                    imgSize = IntSize(d.intrinsicWidth, d.intrinsicHeight)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = insetTop, bottom = insetBottom)
                .graphicsLayer {
                    val sc = scale * fitFactor
                    scaleX = sc
                    scaleY = sc
                    rotationZ = rotation
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}


/** Soft colour wash taken from the photo itself, shown behind it (like the ambient blur in a lot of galleries). */
@Composable
private fun AmbientBackdrop(item: MediaItem) {
    val ctx = LocalContext.current
    val req = remember(item.uri) {
        coil.request.ImageRequest.Builder(ctx)
            .data(item.uri)
            .size(48)
            // Own cache key, otherwise Coil hands back the full-size bitmap already loaded for the photo.
            .memoryCacheKey("ambient_" + item.id)
            .apply { if (item.isVideo) setParameter("thumb", true) }
            .build()
    }
    Box(Modifier.fillMaxSize()) {
        coil.compose.AsyncImage(
            model = req,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
            modifier = Modifier.fillMaxSize().blur(64.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
    }
}


/**
 * Thumbnail strip under the photo. The current photo sits in the middle, larger and at its own aspect ratio,
 * with room around it, and the strip slides with the pager. Items are placed by hand so it stays exactly centred.
 */
@Composable
private fun FilmStrip(list: List<MediaItem>, pagerState: androidx.compose.foundation.pager.PagerState) {
    val ctx = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val baseWpx = with(density) { 28.dp.toPx() }
    val baseHpx = with(density) { 44.dp.toPx() }
    val bigHpx = with(density) { 68.dp.toPx() }
    val marginPx = with(density) { 10.dp.toPx() }
    val stride = baseWpx + with(density) { 3.dp.toPx() }
    val aspects = remember(list) {
        FloatArray(list.size) { k ->
            val m = list[k]
            if (m.width > 0 && m.height > 0) (m.width.toFloat() / m.height).coerceIn(0.55f, 1.1f) else 0.66f
        }
    }
    fun bigW(k: Int): Float = bigHpx * aspects[k]
    fun extra(k: Int): Float = if (k in aspects.indices) bigW(k) + 2f * marginPx - baseWpx else 0f

    val flingJob = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    val dragState = androidx.compose.foundation.gestures.rememberDraggableState { dx ->
        val span = (pagerState.layoutInfo.pageSize + pagerState.layoutInfo.pageSpacing).toFloat()
        pagerState.dispatchRawDelta(-dx / stride * span)
    }
    Box(
        Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .height(80.dp)
            // Drag the strip to scrub through photos. Letting go with speed keeps it gliding, slowing to a stop on a photo.
            .draggable(
                state = dragState,
                orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                onDragStarted = { flingJob[0]?.cancel() },
                onDragStopped = { velocity ->
                    flingJob[0]?.cancel()
                    flingJob[0] = scope.launch {
                        val last = list.lastIndex.toFloat()
                        var prev = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, last)
                        val span = (pagerState.layoutInfo.pageSize + pagerState.layoutInfo.pageSpacing).toFloat()
                        fun moveTo(v: Float) {
                            val c = v.coerceIn(0f, last)
                            pagerState.dispatchRawDelta((c - prev) * span)
                            prev = c
                        }
                        // Content follows the finger, so the position moves opposite to the drag velocity.
                        androidx.compose.animation.core.animateDecay(
                            initialValue = prev,
                            initialVelocity = -velocity / stride,
                            animationSpec = androidx.compose.animation.core.FloatExponentialDecaySpec(
                                frictionMultiplier = 0.8f,
                                absVelocityThreshold = 0.4f,
                            ),
                        ) { value, _ -> moveTo(value) }
                        // Ease onto the nearest photo.
                        androidx.compose.animation.core.animate(
                            initialValue = prev,
                            targetValue = prev.roundToInt().toFloat(),
                            animationSpec = androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 300f),
                        ) { value, _ -> moveTo(value) }
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val page = pagerState.currentPage
        for (i in (page - 7)..(page + 7)) {
            if (i !in list.indices) continue
            androidx.compose.runtime.key(i) {
                val item = list[i]
                val req = remember(item.uri) {
                    coil.request.ImageRequest.Builder(ctx)
                        .data(item.uri)
                        .size(200)
                        .apply { if (item.isVideo) setParameter("thumb", true) }
                        .build()
                }
                coil.compose.AsyncImage(
                    model = req,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .offset {
                            val pos = pagerState.currentPage + pagerState.currentPageOffsetFraction
                            val a = kotlin.math.floor(pos).toInt()
                            val f = pos - a
                            val ga = extra(a) * (1f - f)
                            val gb = extra(a + 1) * f
                            val own = when (i) { a -> ga; a + 1 -> gb; else -> 0f }
                            val before = when { i <= a -> 0f; i == a + 1 -> ga; else -> ga + gb }
                            val sPos = (a * stride + ga / 2f) + f * (((a + 1) * stride + ga + gb / 2f) - (a * stride + ga / 2f))
                            IntOffset(((i - pos) * stride + before + own / 2f - (sPos - pos * stride)).roundToInt(), 0)
                        }
                        .layout { m, _ ->
                            val pos = pagerState.currentPage + pagerState.currentPageOffsetFraction
                            val near = (1f - kotlin.math.abs(i - pos)).coerceIn(0f, 1f)
                            val w = (baseWpx + (bigW(i) - baseWpx) * near).roundToInt()
                            val h = (baseHpx + (bigHpx - baseHpx) * near).roundToInt()
                            val p = m.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
                            layout(w, h) { p.place(0, 0) }
                        }
                        .graphicsLayer {
                            val pos = pagerState.currentPage + pagerState.currentPageOffsetFraction
                            alpha = 0.6f + 0.4f * (1f - kotlin.math.abs(i - pos)).coerceIn(0f, 1f)
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2A2A2A))
                        .clickable { scope.launch { pagerState.animateScrollToPage(i) } },
                )
            }
        }
    }
}
