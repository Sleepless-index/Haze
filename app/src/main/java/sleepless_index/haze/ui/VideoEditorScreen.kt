@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package sleepless_index.haze.ui

import android.view.ViewGroup
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.TextureView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.media3.ui.AspectRatioFrameLayout
import sleepless_index.haze.R
import sleepless_index.haze.editor.AdjustSlider
import sleepless_index.haze.editor.Crop as CropRect
import sleepless_index.haze.editor.CropOverlay
import sleepless_index.haze.editor.fitAspect
import sleepless_index.haze.editor.Aspects as CropAspects
import sleepless_index.haze.editor.Chip
import sleepless_index.haze.editor.TabPill
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.Crop
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import sleepless_index.haze.data.MediaOps
import sleepless_index.haze.data.formatDuration
import sleepless_index.haze.ui.theme.GIcons
import java.io.File
import java.io.FileInputStream
import kotlin.coroutines.resume
import kotlin.math.roundToInt

private const val MIN_CLIP_MS = 500L
private val HandleW = 22.dp
private val StripH = 56.dp

private enum class Tool(val label: String) { Trim("Trim"), Crop("Crop"), Adjust("Adjust"), Filters("Filters") }

private data class Look(
    val name: String,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val warmth: Float,
    val gray: Boolean,
)

private val Looks = listOf(
    Look("None", 0f, 0f, 0f, 0f, false),
    Look("B&W", 0f, 0.1f, 0f, 0f, true),
    Look("Noir", -0.05f, 0.4f, 0f, 0f, true),
    Look("Warm", 0.02f, 0.05f, 12f, 0.6f, false),
    Look("Cool", 0f, 0.05f, 5f, -0.6f, false),
    Look("Vivid", 0.02f, 0.2f, 45f, 0f, false),
    Look("Fade", 0.1f, -0.25f, -25f, 0.1f, false),
)

/** Turns the editor's choices into Media3 effects (used for the live preview and the export). */
private fun buildEffects(
    quarter: Int,
    flip: Boolean,
    crop: CropRect,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    warmth: Float,
    gray: Boolean,
): List<Effect> {
    val out = ArrayList<Effect>()
    if (quarter % 4 != 0) {
        out.add(ScaleAndRotateTransformation.Builder().setRotationDegrees((quarter % 4) * 90f).build())
    }
    if (flip) {
        out.add(ScaleAndRotateTransformation.Builder().setScale(-1f, 1f).build())
    }
    if (crop != CropRect.Full) out.add(Crop(crop.l * 2f - 1f, crop.r * 2f - 1f, 1f - crop.b * 2f, 1f - crop.t * 2f))
    if (brightness != 0f) out.add(Brightness(brightness.coerceIn(-1f, 1f)))
    if (contrast != 0f) out.add(Contrast(contrast.coerceIn(-1f, 1f)))
    if (saturation != 0f) out.add(HslAdjustment.Builder().adjustSaturation(saturation.coerceIn(-100f, 100f)).build())
    if (warmth != 0f) {
        out.add(
            RgbAdjustment.Builder()
                .setRedScale(1f + warmth * 0.25f)
                .setBlueScale(1f - warmth * 0.25f)
                .build()
        )
    }
    if (gray) out.add(RgbFilter.createGrayscaleFilter())
    return out
}

/** 4x5 colour matrix for the live preview, approximating the export effects. */
private fun colorArray(b: Float, c: Float, s: Float, w: Float, gray: Boolean): FloatArray {
    val m = android.graphics.ColorMatrix()
    m.setSaturation(if (gray) 0f else (1f + s / 100f).coerceAtLeast(0f))
    val sc = 1f + c
    val t = 128f * (1f - sc) + b * 255f
    m.postConcat(android.graphics.ColorMatrix(floatArrayOf(
        sc, 0f, 0f, 0f, t,
        0f, sc, 0f, 0f, t,
        0f, 0f, sc, 0f, t,
        0f, 0f, 0f, 1f, 0f,
    )))
    m.postConcat(android.graphics.ColorMatrix(floatArrayOf(
        1f + 0.25f * w, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f - 0.25f * w, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )))
    return m.array
}

/** Video editor: trim, crop, rotate, flip, adjust and filters. Saves the result as a new video. */
@Composable
fun VideoEditorScreen(vm: GalleryViewModel, itemId: Long, onClose: () -> Unit) {
    val item = remember(itemId) { vm.findItem(itemId) }
    if (item == null) {
        LaunchedEffect(Unit) { onClose() }
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(item.uri))
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = false
            prepare()
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var tool by remember { mutableStateOf<Tool?>(null) }
    var durationMs by remember { mutableLongStateOf(item.durationMs.coerceAtLeast(1L)) }
    var startMs by remember { mutableLongStateOf(0L) }
    var endMs by remember { mutableLongStateOf(item.durationMs.coerceAtLeast(1L)) }
    var playhead by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var mute by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var transformer by remember { mutableStateOf<Transformer?>(null) }

    var quarter by remember { mutableIntStateOf(0) }
    var flip by remember { mutableStateOf(false) }
    var aspectIdx by remember { mutableIntStateOf(0) }
    var crop by remember { mutableStateOf(CropRect.Full) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(0f) }
    var warmth by remember { mutableFloatStateOf(0f) }
    var gray by remember { mutableStateOf(false) }
    var lookIdx by remember { mutableIntStateOf(0) }

    val effects = remember(quarter, flip, crop, brightness, contrast, saturation, warmth, gray) {
        buildEffects(
            quarter, flip, crop,
            brightness, contrast, saturation, warmth, gray,
        )
    }
    val previewMatrix = remember(brightness, contrast, saturation, warmth, gray) {
        if (brightness == 0f && contrast == 0f && saturation == 0f && warmth == 0f && !gray) null
        else colorArray(brightness, contrast, saturation, warmth, gray)
    }
    val hasEdits = startMs > 0L || endMs < durationMs || mute || effects.isNotEmpty()
    val srcWpx = item.width.coerceAtLeast(1)
    val srcHpx = item.height.coerceAtLeast(1)
    val fW = if (quarter % 2 != 0) srcHpx else srcWpx
    val fH = if (quarter % 2 != 0) srcWpx else srcHpx

    // Keeps the playhead in sync, picks up the real duration, and stops at the end of the range.
    LaunchedEffect(player) {
        while (true) {
            val pos = player.currentPosition
            playhead = pos
            playing = player.isPlaying
            val d = player.duration
            if (d > 0 && d != durationMs) {
                val wasFull = endMs >= durationMs
                durationMs = d
                if (wasFull || endMs > d) endMs = d
            }
            if (player.isPlaying && endMs > startMs && pos >= endMs) {
                player.pause()
                player.seekTo(startMs)
                playhead = startMs
            }
            delay(40)
        }
    }

    fun seekPreview(ms: Long) {
        player.pause()
        player.seekTo(ms.coerceIn(0L, durationMs))
        playhead = ms
    }

    fun togglePlay() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.currentPosition < startMs || player.currentPosition >= endMs - 100L) player.seekTo(startMs)
            player.play()
        }
    }

    val history = sleepless_index.haze.editor.rememberEditHistory(
        read = {
            VideoEdit(
                quarter, flip, aspectIdx, crop, brightness, contrast, saturation, warmth, gray, lookIdx,
                startMs, if (endMs >= durationMs) -1L else endMs, mute,
            )
        },
        write = {
            quarter = it.quarter; flip = it.flip; aspectIdx = it.aspectIdx; crop = it.crop
            brightness = it.brightness; contrast = it.contrast; saturation = it.saturation; warmth = it.warmth
            gray = it.gray; lookIdx = it.lookIdx
            startMs = it.startMs; endMs = if (it.endMs < 0) durationMs else it.endMs; mute = it.mute
            seekPreview(startMs)
        },
    )

    fun resetCrop() {
        aspectIdx = 0
        crop = CropRect.Full
    }

    fun resetAll() {
        quarter = 0; flip = false; resetCrop()
        brightness = 0f; contrast = 0f; saturation = 0f; warmth = 0f; gray = false; lookIdx = 0
        startMs = 0L; endMs = durationMs; mute = false
        seekPreview(0L)
    }

    fun applyLook(i: Int) {
        lookIdx = i
        val l = Looks[i]
        brightness = l.brightness
        contrast = l.contrast
        saturation = l.saturation
        warmth = l.warmth
        gray = l.gray
    }

    fun save() {
        if (exporting) return
        player.pause()
        exporting = true
        progress = 0f
        scope.launch {
            val out = File(context.cacheDir, "edit_${System.currentTimeMillis()}.mp4")
            val ok = runCatching {
                suspendCancellableCoroutine<Boolean> { cont ->
                    val clip = ExoMediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                    val media = ExoMediaItem.Builder().setUri(item.uri).setClippingConfiguration(clip).build()
                    val edited = EditedMediaItem.Builder(media)
                        .setRemoveAudio(mute)
                        .setEffects(Effects(emptyList(), effects))
                        .build()
                    val t = Transformer.Builder(context)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (cont.isActive) cont.resume(true)
                            }
                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException,
                            ) {
                                if (cont.isActive) cont.resume(false)
                            }
                        })
                        .build()
                    transformer = t
                    cont.invokeOnCancellation { t.cancel() }
                    t.start(edited, out.absolutePath)
                    scope.launch {
                        val holder = ProgressHolder()
                        while (cont.isActive) {
                            if (t.getProgress(holder) != Transformer.PROGRESS_STATE_NOT_STARTED) {
                                progress = holder.progress / 100f
                            }
                            delay(150)
                        }
                    }
                }
            }.getOrDefault(false)
            transformer = null

            val saved = if (ok && out.exists() && out.length() > 0) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val base = item.name.substringBeforeLast('.', item.name)
                        FileInputStream(out).use { input ->
                            MediaOps.insert(
                                context = context,
                                input = input,
                                name = "${base}_edit.mp4",
                                mime = "video/mp4",
                                isVideo = true,
                                dateMs = item.dateMs,
                                relativeDir = "Movies/Edited",
                            )
                        }
                    }.getOrNull()
                }
            } else null
            runCatching { out.delete() }
            exporting = false
            if (saved != null) {
                vm.message("Saved a copy to Movies/Edited")
                vm.refresh()
                onClose()
            } else {
                vm.message("Couldn't save this video")
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GIconButton(GIcons.Close, "Close editor", onClick = { if (!exporting) onClose() })
            Text(
                "Edit", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground,
                modifier = Modifier.weight(1f).padding(start = 14.dp),
            )
            sleepless_index.haze.editor.UndoRedo(history)
            if (hasEdits) {
                Text(
                    "Reset", color = cs.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .bounceClick(onClick = { resetAll() })
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            val saveBg by animateColorAsState(if (hasEdits) cs.primary else cs.surfaceVariant, tween(220), label = "saveBg")
            val saveFg by animateColorAsState(if (hasEdits) cs.onPrimary else cs.onSurfaceVariant, tween(220), label = "saveFg")
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(saveBg)
                    .bounceClick(enabled = hasEdits && !exporting, onClick = { save() })
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Save copy", color = saveFg, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        // Preview: rotation and flip are applied to the view, colour through the layer paint.
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val odd = quarter % 2 != 0
                val frame = fW.toFloat() / fH
                val boxAspect = maxWidth.value / maxHeight.value
                val bw = if (boxAspect > frame) maxHeight * frame else maxWidth
                val bh = if (boxAspect > frame) maxHeight else maxWidth / frame

                Box(
                    modifier = Modifier
                        .requiredSize(bw, bh)
                        .clip(RectangleShape)
                        .graphicsLayer { scaleX = if (flip) -1f else 1f },
                    contentAlignment = Alignment.Center,
                ) {
                    AndroidView(
                        modifier = Modifier
                            .requiredSize(if (odd) bh else bw, if (odd) bw else bh)
                            .graphicsLayer { rotationZ = -(quarter % 4) * 90f },
                        factory = { ctx ->
                            val exo = player
                            val v = android.view.LayoutInflater.from(ctx).inflate(R.layout.player_texture, null, false) as PlayerView
                            v.apply {
                                useController = false
                                this.player = exo
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        update = { pv ->
                            val tv = pv.videoSurfaceView as? TextureView
                            val m = previewMatrix
                            tv?.setLayerPaint(
                                if (m == null) null else Paint().apply { colorFilter = ColorMatrixColorFilter(android.graphics.ColorMatrix(m)) }
                            )
                        },
                    )
                }
                if (tool == Tool.Crop) {
                    CropOverlay(
                        bw = fW,
                        bh = fH,
                        crop = crop,
                        aspect = CropAspects[aspectIdx].second,
                        onCrop = { crop = it },
                        modifier = Modifier.requiredSize(bw, bh),
                    )
                } else {
                    Box(
                        Modifier
                            .requiredSize(bw, bh)
                            .pointerInput(Unit) { detectTapGestures { togglePlay() } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!playing) {
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xB8141414)),
                                contentAlignment = Alignment.Center,
                            ) {
                                androidx.compose.material3.Icon(GIcons.Play, "Play", tint = Color.White, modifier = Modifier.size(26.dp))
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Tool.values().forEach { t -> TabPill(t.label, tool == t) { tool = t } }
        }

        AnimatedContent(
            targetState = tool,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "videoEditorTab",
            modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp),
        ) { current ->
            when (current) {
                null -> Box(Modifier.fillMaxWidth().heightIn(min = 190.dp), contentAlignment = Alignment.Center) {
                    Text("Choose a tool to start editing", fontSize = 13.sp, color = cs.onSurfaceVariant)
                }
                Tool.Trim -> Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(startMs), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground)
                        Text("${formatTime(endMs - startMs)} selected", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant)
                        Text(formatTime(endMs), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground)
                    }
                    TrimStrip(
                        item = item,
                        durationMs = durationMs,
                        startMs = startMs,
                        endMs = endMs,
                        playheadMs = playhead,
                        accent = cs.primary,
                        onStart = { startMs = it; seekPreview(it) },
                        onEnd = { endMs = it; seekPreview((it - 300L).coerceAtLeast(startMs)) },
                        onScrub = { seekPreview(it.coerceIn(startMs, endMs)) },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GIconButton(
                            if (playing) GIcons.Pause else GIcons.Play,
                            if (playing) "Pause preview" else "Play preview",
                            onClick = { togglePlay() },
                            size = 40.dp,
                        )
                        Chip(if (mute) "Audio off" else "Audio on", mute) { mute = !mute }
                    }
                }

                Tool.Crop -> Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CropAspects.take(4).forEachIndexed { i, (label, a) ->
                                Chip(label, aspectIdx == i) {
                                    aspectIdx = i
                                    crop = fitAspect(a, fW, fH)
                                }
                            }
                        }
                        GIconButton(
                            GIcons.RotateLeft, "Rotate left",
                            onClick = { quarter = (quarter + 1) % 4; resetCrop() },
                            size = 40.dp,
                        )
                        Box(Modifier.size(6.dp))
                        GIconButton(
                            GIcons.Flip, "Flip horizontally",
                            onClick = { flip = !flip; resetCrop() },
                            size = 40.dp,
                        )
                    }
                    Text("Drag the corners to crop", fontSize = 12.sp, color = cs.onSurfaceVariant)
                }

                Tool.Adjust -> Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    AdjustSlider("Brightness", brightness, -0.5f..0.5f, "%+.0f".format(brightness * 200)) { brightness = it; lookIdx = -1 }
                    AdjustSlider("Contrast", contrast, -0.5f..0.5f, "%+.0f".format(contrast * 200)) { contrast = it; lookIdx = -1 }
                    AdjustSlider("Saturation", saturation, -100f..100f, "%+.0f".format(saturation)) { saturation = it; lookIdx = -1 }
                    AdjustSlider("Warmth", warmth, -1f..1f, "%+.0f".format(warmth * 100)) { warmth = it; lookIdx = -1 }
                }

                Tool.Filters -> LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(Looks.size) { i ->
                        val l = Looks[i]
                        val selected = lookIdx == i
                        val ring by animateColorAsState(if (selected) cs.primary else Color.Transparent, tween(180), label = "lookRing")
                        val thumb = remember(item.uri) {
                            ImageRequest.Builder(context)
                                .data(item.uri)
                                .videoFrameMillis((durationMs / 3).coerceAtLeast(0L))
                                .size(200)
                                .build()
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.bounceClick(role = Role.RadioButton, onClick = { applyLook(i) }),
                        ) {
                            Box(
                                Modifier
                                    .size(84.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(BorderStroke(2.dp, ring), RoundedCornerShape(14.dp))
                                    .background(cs.surfaceVariant)
                            ) {
                                val arr = colorArray(l.brightness, l.contrast, l.saturation, l.warmth, l.gray)
                                AsyncImage(
                                    model = thumb,
                                    contentDescription = l.name,
                                    contentScale = ContentScale.Crop,
                                    colorFilter = if (i == 0) null else ColorFilter.colorMatrix(ComposeColorMatrix(arr)),
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Text(
                                l.name, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = if (selected) cs.primary else cs.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (exporting) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(cs.surface)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Saving…", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground)
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.width(220.dp),
                    color = cs.primary,
                    trackColor = cs.surfaceVariant,
                )
                Text("${(progress * 100).roundToInt()}%", fontSize = 13.sp, color = cs.onSurfaceVariant)
                Chip("Cancel", false) { transformer?.cancel() }
            }
        }
    }
}

/** Frame strip with two draggable handles and a playhead. */
@Composable
internal fun TrimStrip(
    item: sleepless_index.haze.data.MediaItem,
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    playheadMs: Long,
    accent: Color,
    onStart: (Long) -> Unit,
    onEnd: (Long) -> Unit,
    onScrub: (Long) -> Unit,
) {
    val ctx = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    var outerPx by remember { mutableFloatStateOf(1f) }
    val handlePx = with(density) { HandleW.toPx() }
    val trackPx = (outerPx - 2f * handlePx).coerceAtLeast(1f)

    val dur by rememberUpdatedState(durationMs)
    val start by rememberUpdatedState(startMs)
    val end by rememberUpdatedState(endMs)
    val w by rememberUpdatedState(trackPx)

    fun xOf(ms: Long): Float = (ms.toFloat() / durationMs.coerceAtLeast(1L)) * trackPx

    // The outer box is wide enough to contain both handles, so they are always touchable.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(StripH)
            .onSizeChanged { outerPx = it.width.toFloat().coerceAtLeast(1f) },
    ) {
        // Track: frames, dimming, selection frame and playhead
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = HandleW),
        ) {
            Row(Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))) {
                val count = 8
                for (i in 0 until count) {
                    val at = (dur * (i + 0.5f) / count).toLong()
                    val req = remember(item.uri, at) {
                        ImageRequest.Builder(ctx)
                            .data(item.uri)
                            .videoFrameMillis(at)
                            .size(160)
                            .build()
                    }
                    AsyncImage(
                        model = req,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF1A1A1A)),
                    )
                }
            }

            // Tap or drag on the frames to move the preview
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { o -> onScrub(((o.x / w) * dur).toLong()) }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            onScrub(((change.position.x / w) * dur).toLong())
                        }
                    }
            )

            // Dim everything outside the selection
            Box(
                Modifier
                    .width(with(density) { xOf(startMs).toDp() })
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.6f))
            )
            Box(
                Modifier
                    .offset { IntOffset(xOf(endMs).roundToInt(), 0) }
                    .width(with(density) { (trackPx - xOf(endMs)).coerceAtLeast(0f).toDp() })
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.6f))
            )

            // Selection frame
            Box(
                Modifier
                    .offset { IntOffset(xOf(startMs).roundToInt(), 0) }
                    .width(with(density) { (xOf(endMs) - xOf(startMs)).coerceAtLeast(0f).toDp() })
                    .fillMaxHeight()
                    .border(2.dp, accent, RoundedCornerShape(4.dp))
            )

            // Playhead
            if (playheadMs in startMs..endMs) {
                Box(
                    Modifier
                        .offset { IntOffset(xOf(playheadMs).roundToInt() - 1.dp.roundToPx(), 0) }
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Color.White)
                )
            }
        }

        // Handles: the start handle sits left of the selection, the end handle right of it.
        Handle(
            modifier = Modifier
                .offset { IntOffset(xOf(startMs).roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dx ->
                        change.consume()
                        val next = (start + (dx / w * dur).toLong()).coerceIn(0L, end - MIN_CLIP_MS)
                        onStart(next)
                    }
                },
            accent = accent,
        )
        Handle(
            modifier = Modifier
                .offset { IntOffset((handlePx + xOf(endMs)).roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dx ->
                        change.consume()
                        val next = (end + (dx / w * dur).toLong()).coerceIn(start + MIN_CLIP_MS, dur)
                        onEnd(next)
                    }
                },
            accent = accent,
        )
    }
}

@Composable
private fun Handle(modifier: Modifier, accent: Color) {
    Box(
        modifier = modifier
            .width(HandleW)
            .fillMaxHeight()
            .clip(RoundedCornerShape(5.dp))
            .background(accent),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.Black.copy(alpha = 0.45f))
        )
    }
}

private fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L)
    val tenths = (total % 1000) / 100
    return "${formatDuration(total)}.$tenths"
}

private data class VideoEdit(
    val quarter: Int, val flip: Boolean, val aspectIdx: Int, val crop: CropRect,
    val brightness: Float, val contrast: Float, val saturation: Float, val warmth: Float,
    val gray: Boolean, val lookIdx: Int, val startMs: Long, val endMs: Long, val mute: Boolean,
)
