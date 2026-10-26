package sleepless_index.haze.editor

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import coil.request.videoFrameMillis
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sleepless_index.haze.ui.GIconButton
import sleepless_index.haze.ui.bounceClick
import sleepless_index.haze.ui.GalleryViewModel
import sleepless_index.haze.ui.theme.GIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.min
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix

internal data class Crop(val l: Float, val t: Float, val r: Float, val b: Float) {
    companion object {
        val Full = Crop(0f, 0f, 1f, 1f)
    }
}

internal val Aspects = listOf("Free" to 0f, "1:1" to 1f, "4:5" to 0.8f, "16:9" to 16f / 9f, "3:4" to 0.75f)

/** Largest centered crop with the given aspect (w/h, in pixels) inside a bw x bh image. */
internal fun fitAspect(aspect: Float, bw: Int, bh: Int): Crop {
    if (aspect <= 0f) return Crop.Full
    val imageAspect = bw.toFloat() / bh
    return if (aspect > imageAspect) {
        val h = (bw / aspect) / bh
        Crop(0f, (1f - h) / 2f, 1f, (1f + h) / 2f)
    } else {
        val w = (bh * aspect) / bw
        Crop((1f - w) / 2f, 0f, (1f + w) / 2f, 1f)
    }
}

internal fun displayedRect(boxW: Float, boxH: Float, bw: Int, bh: Int): Rect {
    val s = min(boxW / bw, boxH / bh)
    val dw = bw * s
    val dh = bh * s
    val left = (boxW - dw) / 2f
    val top = (boxH - dh) / 2f
    return Rect(left, top, left + dw, top + dh)
}

@Composable
internal fun CropOverlay(
    bw: Int,
    bh: Int,
    crop: Crop,
    aspect: Float,
    onCrop: (Crop) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val latestCrop by rememberUpdatedState(crop)
    val latestAspect by rememberUpdatedState(aspect)
    val latestOnCrop by rememberUpdatedState(onCrop)

    Canvas(
        modifier = modifier.pointerInput(bw, bh) {
            var mode = 0 // 1..4 corners (TL, TR, BL, BR), 5 move
            detectDragGestures(
                onDragStart = { pos ->
                    val d = displayedRect(size.width.toFloat(), size.height.toFloat(), bw, bh)
                    val c = latestCrop
                    val l = d.left + c.l * d.width
                    val r = d.left + c.r * d.width
                    val t = d.top + c.t * d.height
                    val b = d.top + c.b * d.height
                    val reach = 48.dp.toPx()
                    val corners = listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b))
                    var best = -1
                    var bestDist = reach
                    corners.forEachIndexed { i, p ->
                        val dist = hypot(pos.x - p.x, pos.y - p.y)
                        if (dist < bestDist) {
                            bestDist = dist
                            best = i
                        }
                    }
                    mode = when {
                        best >= 0 -> best + 1
                        pos.x in l..r && pos.y in t..b -> 5
                        else -> 0
                    }
                },
                onDragEnd = { mode = 0 },
                onDragCancel = { mode = 0 },
                onDrag = { change, delta ->
                    if (mode == 0) return@detectDragGestures
                    change.consume()
                    val d = displayedRect(size.width.toFloat(), size.height.toFloat(), bw, bh)
                    val dx = delta.x / d.width
                    val dy = delta.y / d.height
                    val c = latestCrop
                    val minSize = 0.08f
                    var nl = c.l
                    var nt = c.t
                    var nr = c.r
                    var nb = c.b
                    when (mode) {
                        5 -> {
                            val w = c.r - c.l
                            val h = c.b - c.t
                            nl = (c.l + dx).coerceIn(0f, 1f - w)
                            nt = (c.t + dy).coerceIn(0f, 1f - h)
                            nr = nl + w
                            nb = nt + h
                        }
                        1 -> {
                            nl = (c.l + dx).coerceIn(0f, c.r - minSize)
                            nt = (c.t + dy).coerceIn(0f, c.b - minSize)
                        }
                        2 -> {
                            nr = (c.r + dx).coerceIn(c.l + minSize, 1f)
                            nt = (c.t + dy).coerceIn(0f, c.b - minSize)
                        }
                        3 -> {
                            nl = (c.l + dx).coerceIn(0f, c.r - minSize)
                            nb = (c.b + dy).coerceIn(c.t + minSize, 1f)
                        }
                        4 -> {
                            nr = (c.r + dx).coerceIn(c.l + minSize, 1f)
                            nb = (c.b + dy).coerceIn(c.t + minSize, 1f)
                        }
                    }
                    val a = latestAspect
                    if (a > 0f && mode in 1..4) {
                        val hNorm = ((nr - nl) * bw / a) / bh
                        if (mode == 1 || mode == 2) nt = nb - hNorm else nb = nt + hNorm
                        if (nt < 0f || nb > 1f) return@detectDragGestures
                    }
                    latestOnCrop(Crop(nl, nt, nr, nb))
                },
            )
        },
    ) {
        val d = displayedRect(size.width, size.height, bw, bh)
        val l = d.left + crop.l * d.width
        val r = d.left + crop.r * d.width
        val t = d.top + crop.t * d.height
        val b = d.top + crop.b * d.height

        val dim = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(d)
            addRect(Rect(l, t, r, b))
        }
        drawPath(dim, Color.Black.copy(alpha = 0.55f))

        val line = Color.White.copy(alpha = 0.35f)
        val thin = 1.dp.toPx()
        for (i in 1..2) {
            val x = l + (r - l) * i / 3f
            val y = t + (b - t) * i / 3f
            drawLine(line, Offset(x, t), Offset(x, b), strokeWidth = thin)
            drawLine(line, Offset(l, y), Offset(r, y), strokeWidth = thin)
        }
        drawRect(
            color = Color.White,
            topLeft = Offset(l, t),
            size = Size(r - l, b - t),
            style = Stroke(width = 1.5.dp.toPx()),
        )

        val arm = 22.dp.toPx()
        val w = 4.dp.toPx()
        // One continuous L per corner, centred exactly on the frame line: no overlapping strokes.
        fun corner(cx: Float, cy: Float, sx: Float, sy: Float) {
            val p = Path().apply {
                moveTo(cx + arm * sx, cy)
                lineTo(cx, cy)
                lineTo(cx, cy + arm * sy)
            }
            drawPath(p, accent, style = Stroke(width = w, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        corner(l, t, 1f, 1f)
        corner(r, t, -1f, 1f)
        corner(l, b, 1f, -1f)
        corner(r, b, -1f, -1f)
    }
}

@Composable
internal fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val fg by animateColorAsState(if (active) cs.primary else cs.onBackground, tween(180), label = "chipFg")
    val line by animateColorAsState(if (active) cs.primary else cs.outline, tween(180), label = "chipLine")
    Text(
        text = label,
        color = fg,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .clip(RoundedCornerShape(17.dp))
            .border(BorderStroke(1.dp, line), RoundedCornerShape(17.dp))
            .bounceClick(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
internal fun TabPill(label: String, active: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (active) cs.surfaceVariant else Color.Transparent, tween(200), label = "tabBg")
    val fg by animateColorAsState(if (active) cs.primary else cs.onSurfaceVariant, tween(200), label = "tabFg")
    Text(
        text = label,
        color = fg,
        fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .bounceClick(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    )
}

@Composable
internal fun AdjustSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    shown: String,
    onChange: (Float) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(shown, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = cs.primary)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = cs.primary,
                activeTrackColor = cs.primary,
                inactiveTrackColor = cs.surfaceVariant,
            ),
        )
    }
}

@Composable
fun EditorScreen(vm: GalleryViewModel, itemId: Long, onClose: () -> Unit) {
    val item = remember(itemId) { vm.findItem(itemId) }
    if (item == null) {
        LaunchedEffect(Unit) { onClose() }
        Box(Modifier.fillMaxSize())
        return
    }

    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    val source by produceState<Bitmap?>(null, item.id) {
        value = withContext(Dispatchers.IO) {
            runCatching { ImageOps.decode(context, item.uri, 1600) }.getOrNull()
        }
    }

    var tab by remember { mutableIntStateOf(-1) }
    var quarter by remember { mutableIntStateOf(0) }
    var flip by remember { mutableStateOf(false) }
    var straighten by remember { mutableFloatStateOf(0f) }
    var aspectIdx by remember { mutableIntStateOf(0) }
    var crop by remember { mutableStateOf(Crop.Full) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var warmth by remember { mutableFloatStateOf(0f) }
    var filterIdx by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }

    val preview by produceState<Bitmap?>(null, source, quarter, flip, straighten) {
        val s = source
        value = if (s == null) null else withContext(Dispatchers.Default) {
            ImageOps.transform(s, quarter, flip, straighten)
        }
    }

    val matrixValues = remember(brightness, contrast, saturation, warmth, filterIdx) {
        ImageOps.colorMatrix(brightness, contrast, saturation, warmth, filterIdx)
    }
    val colorFilter = remember(matrixValues) { ColorFilter.colorMatrix(ComposeColorMatrix(matrixValues)) }

    val hasEdits = quarter % 4 != 0 || flip || straighten != 0f || crop != Crop.Full ||
        brightness != 0f || contrast != 1f || saturation != 1f || warmth != 0f || filterIdx != 0

    val history = rememberEditHistory(
        read = { PhotoEdit(quarter, flip, straighten, aspectIdx, crop, brightness, contrast, saturation, warmth, filterIdx) },
        write = {
            quarter = it.quarter; flip = it.flip; straighten = it.straighten; aspectIdx = it.aspectIdx; crop = it.crop
            brightness = it.brightness; contrast = it.contrast; saturation = it.saturation; warmth = it.warmth; filterIdx = it.filterIdx
        },
    )

    fun resetCrop() {
        aspectIdx = 0
        crop = Crop.Full
    }

    fun reset() {
        quarter = 0; flip = false; straighten = 0f; aspectIdx = 0; crop = Crop.Full
        brightness = 0f; contrast = 1f; saturation = 1f; warmth = 0f; filterIdx = 0
    }

    fun save() {
        if (saving || !hasEdits) return
        saving = true
        scope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    ImageOps.exportCopy(
                        context = context,
                        sourceUri = item.uri,
                        displayName = item.name,
                        mime = item.mime,
                        dateMs = item.dateMs,
                        quarter = quarter,
                        flip = flip,
                        straighten = straighten,
                        crop = floatArrayOf(crop.l, crop.t, crop.r, crop.b),
                        matrix = matrixValues,
                    )
                }.getOrNull()
            }
            saving = false
            if (uri != null) {
                vm.message("Saved a copy to Pictures/Edited")
                vm.refresh()
                onClose()
            } else {
                vm.message("Couldn't save the edit")
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GIconButton(GIcons.Close, "Close editor", onClick = onClose)
            Text(
                "Edit", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            )
            UndoRedo(history)
            if (hasEdits) {
                Text(
                    "Reset", color = cs.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .bounceClick(onClick = { reset() })
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            val saveBg by animateColorAsState(
                if (hasEdits) cs.primary else cs.surfaceVariant, tween(220), label = "saveBg"
            )
            val saveFg by animateColorAsState(
                if (hasEdits) cs.onPrimary else cs.onSurfaceVariant, tween(220), label = "saveFg"
            )
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(saveBg)
                    .bounceClick(enabled = hasEdits && !saving, onClick = { save() })
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (saving) {
                    CircularProgressIndicator(color = saveFg, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text("Save copy", color = saveFg, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = preview
            if (bmp == null) {
                CircularProgressIndicator(color = cs.primary, strokeWidth = 3.dp)
            } else {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = colorFilter,
                    modifier = Modifier.fillMaxSize(),
                )
                if (tab == 0) {
                    CropOverlay(
                        bw = bmp.width,
                        bh = bmp.height,
                        crop = crop,
                        aspect = Aspects[aspectIdx].second,
                        onCrop = { crop = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            TabPill("Crop", tab == 0) { tab = 0 }
            TabPill("Adjust", tab == 1) { tab = 1 }
            TabPill("Filters", tab == 2) { tab = 2 }
        }

        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "editorTab",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 190.dp),
        ) { current ->
            when (current) {
                -1 -> Box(Modifier.fillMaxWidth().heightIn(min = 190.dp), contentAlignment = Alignment.Center) {
                    Text("Choose a tool to start editing", fontSize = 13.sp, color = cs.onSurfaceVariant)
                }
                0 -> Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Aspects.take(4).forEachIndexed { i, (label, a) ->
                                Chip(label, aspectIdx == i) {
                                    aspectIdx = i
                                    val b = preview
                                    crop = if (b != null) fitAspect(a, b.width, b.height) else Crop.Full
                                }
                            }
                        }
                        GIconButton(
                            GIcons.RotateLeft, "Rotate left",
                            onClick = {
                                quarter = (quarter + 3) % 4
                                resetCrop()
                            },
                            size = 40.dp,
                        )
                        Box(Modifier.size(6.dp))
                        GIconButton(
                            GIcons.Flip, "Flip horizontally",
                            onClick = {
                                flip = !flip
                                resetCrop()
                            },
                            size = 40.dp,
                        )
                    }
                    AdjustSlider(
                        label = "Straighten",
                        value = straighten,
                        range = -30f..30f,
                        shown = (if (straighten > 0.05f) "+" else "") + "%.0f°".format(straighten),
                        onChange = { straighten = it },
                    )
                }
                1 -> Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    AdjustSlider("Brightness", brightness, -1f..1f, "%+.0f".format(brightness * 100)) { brightness = it }
                    AdjustSlider("Contrast", contrast, 0.5f..1.5f, "%+.0f".format((contrast - 1f) * 200)) { contrast = it }
                    AdjustSlider("Saturation", saturation, 0f..2f, "%+.0f".format((saturation - 1f) * 100)) { saturation = it }
                    AdjustSlider("Warmth", warmth, -1f..1f, "%+.0f".format(warmth * 100)) { warmth = it }
                }
                else -> LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(ImageOps.filters.size) { i ->
                        val f = ImageOps.filters[i]
                        val selected = filterIdx == i
                        val ring by animateColorAsState(if (selected) cs.primary else Color.Transparent, tween(180), label = "filterRing")
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.bounceClick(role = Role.RadioButton, onClick = { filterIdx = i }),
                        ) {
                            val b = preview
                            Box(
                                Modifier
                                    .size(84.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(BorderStroke(2.dp, ring), RoundedCornerShape(14.dp))
                                    .background(cs.surfaceVariant)
                            ) {
                                if (b != null) {
                                    Image(
                                        bitmap = b.asImageBitmap(),
                                        contentDescription = f.name,
                                        contentScale = ContentScale.Crop,
                                        colorFilter = f.matrix?.let { ColorFilter.colorMatrix(ComposeColorMatrix(it)) },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            Text(
                                f.name, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = if (selected) cs.primary else cs.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class PhotoEdit(
    val quarter: Int, val flip: Boolean, val straighten: Float, val aspectIdx: Int, val crop: Crop,
    val brightness: Float, val contrast: Float, val saturation: Float, val warmth: Float, val filterIdx: Int,
)
