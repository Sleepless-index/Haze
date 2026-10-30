@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package sleepless_index.haze.ui

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.app.PictureInPictureParams
import android.os.Build
import android.provider.Settings
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import sleepless_index.haze.data.MediaItem
import sleepless_index.haze.data.MediaOps
import sleepless_index.haze.data.LoopMode
import sleepless_index.haze.PlaybackService
import sleepless_index.haze.util.shareItems
import androidx.compose.ui.graphics.graphicsLayer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession
import androidx.compose.runtime.key
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.zIndex
import sleepless_index.haze.PlaybackBridge
import sleepless_index.haze.R
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import sleepless_index.haze.data.formatDuration
import sleepless_index.haze.ui.theme.GIcons
import sleepless_index.haze.util.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.media3.common.MediaItem as ExoMediaItem

private val PlayerChrome = Color(0xB8141414)

/** side: 0 = top centre, -1 = left edge (brightness, rewind), 1 = right edge (volume, forward). */
private class Hud(val icon: ImageVector, val text: String, val fraction: Float? = null, val side: Int = 0)

private fun trackLabel(group: Tracks.Group, index: Int, prefix: String): String {
    val f = group.getTrackFormat(0)
    val lang = f.language
        ?.let { Locale.forLanguageTag(it).displayLanguage }
        ?.takeIf { it.isNotBlank() && !it.equals("und", ignoreCase = true) }
    val codec = f.sampleMimeType?.substringAfter('/')?.uppercase(Locale.US)
    val label = f.label
    return listOfNotNull("$prefix ${index + 1}", label ?: lang, codec).joinToString(" · ")
}

@Composable
private fun PlayerChip(text: String, icon: ImageVector?, accent: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(PlayerChrome)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Text(
            text = text,
            color = if (accent) cs.primary else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RoundChrome(icon: ImageVector, desc: String, size: Int = 44, tint: Color = Color.White, onClick: () -> Unit) {
    GIconButton(
        icon = icon, desc = desc, onClick = onClick,
        size = size.dp, container = PlayerChrome, tint = tint,
    )
}

@Composable
private fun SkipButton(
    icon: ImageVector,
    desc: String,
    enabled: Boolean,
    box: Dp = 44.dp,
    iconSize: Dp = 26.dp,
    background: Color = Color.Transparent,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(box)
            .clip(CircleShape)
            .background(background)
            .then(if (enabled) Modifier.bounceClick(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription = desc,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f),
            modifier = Modifier.size(iconSize),
        )
    }
}

/** A subtitle line we draw ourselves (SRT / VTT), so it shows no matter what the player decides. */
private class SubCue(val start: Long, val end: Long, val text: String)

private val SubTimeRe = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})[,.](\d{1,3})""")

private fun subTime(m: MatchResult): Long {
    val h = m.groupValues[1].toLongOrNull() ?: 0L
    val mi = m.groupValues[2].toLong()
    val sec = m.groupValues[3].toLong()
    val ms = m.groupValues[4].padEnd(3, '0').toLong()
    return ((h * 60 + mi) * 60 + sec) * 1000 + ms
}

private fun parseSubtitles(raw: String): List<SubCue> {
    val text = raw.replace("\r\n", "\n").replace('\r', '\n')
    val out = ArrayList<SubCue>()
    for (block in text.split(Regex("\n\\s*\n"))) {
        val lines = block.trim().split("\n")
        val ti = lines.indexOfFirst { "-->" in it }
        if (ti < 0) continue
        val times = SubTimeRe.findAll(lines[ti]).toList()
        if (times.size < 2) continue
        val start = subTime(times[0])
        val end = subTime(times[1])
        val body = lines.drop(ti + 1).joinToString("\n")
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("\\{\\\\[^}]*\\}"), "")
            .trim()
        if (body.isNotEmpty() && end > start) out.add(SubCue(start, end, body))
    }
    return out.sortedBy { it.start }
}

private fun decodeSubtitleBytes(raw: ByteArray): String {
    val utf8 = String(raw, Charsets.UTF_8).removePrefix("\uFEFF")
    return if ('\uFFFD' in utf8) String(raw, charset("windows-1252")) else utf8
}

private fun subtitleConfig(uri: android.net.Uri, name: String): ExoMediaItem.SubtitleConfiguration {
    val lower = name.lowercase(Locale.US)
    val mime = when {
        lower.endsWith(".vtt") -> MimeTypes.TEXT_VTT
        lower.endsWith(".ass") || lower.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        lower.endsWith(".ttml") || lower.endsWith(".xml") || lower.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP
    }
    return ExoMediaItem.SubtitleConfiguration.Builder(uri)
        .setMimeType(mime)
        .setLanguage("und")
        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
        .setLabel(name.substringBeforeLast('.').ifBlank { "Subtitles" })
        .build()
}

/** Subtitle files sitting next to the video with the same name (needs All files access). */
private fun findSiblingSubtitles(context: Context, item: MediaItem): List<ExoMediaItem.SubtitleConfiguration> {
    val path = MediaOps.pathOf(context, item.uri) ?: return emptyList()
    val base = path.substringBeforeLast('.')
    val exts = listOf("srt", "vtt", "ass", "ssa", "ttml")
    return exts.mapNotNull { ext ->
        val f = java.io.File("$base.$ext")
        if (f.isFile) subtitleConfig(android.net.Uri.fromFile(f), f.name) else null
    }
}

/** Thin full-width seek bar with a round thumb. */
@Composable
private fun SeekBar(
    fraction: Float,
    scrubbing: Boolean,
    onChange: (Float) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var widthPx by remember { mutableFloatStateOf(1f) }
    val thumb by animateDpAsState(if (scrubbing) 20.dp else 14.dp, tween(120), label = "seekThumb")
    Box(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .height(32.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { o ->
                    onChange((o.x / widthPx).coerceIn(0f, 1f))
                    onFinish()
                })
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> onChange((o.x / widthPx).coerceIn(0f, 1f)) },
                    onDragEnd = { onFinish() },
                    onDragCancel = { onFinish() },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onChange((change.position.x / widthPx).coerceIn(0f, 1f))
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.32f)),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(3.dp)
                .clip(CircleShape)
                .background(cs.primary),
        )
        Box(
            Modifier
                .offset {
                    val thumbPx = with(density) { thumb.toPx() }
                    IntOffset((fraction.coerceIn(0f, 1f) * widthPx - thumbPx / 2f).roundToInt(), 0)
                }
                .size(thumb)
                .clip(CircleShape)
                .background(cs.primary),
        )
    }
}


private fun exoItemFor(v: MediaItem, subs: List<ExoMediaItem.SubtitleConfiguration>): ExoMediaItem =
    ExoMediaItem.Builder()
        .setUri(v.uri)
        .setMediaId(v.id.toString())
        .setMediaMetadata(MediaMetadata.Builder().setTitle(v.name).build())
        .setSubtitleConfigurations(subs)
        .build()

@Composable
fun PlayerScreen(vm: GalleryViewModel, itemId: Long, onBack: () -> Unit, onOpenVideo: (Long) -> Unit) {
    val startItem = remember(itemId) { vm.findItem(itemId) }
    if (startItem == null) {
        LaunchedEffect(Unit) { onBack() }
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    val context = LocalContext.current
    val activity = context.findActivity()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val config = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isLand = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val narrow = config.screenWidthDp < 520

    // The queue: every video in the list the viewer was opened from. ExoPlayer holds all of it,
    // so next / previous / loop / shuffle keep working with the screen off.
    val originalQueue = remember(itemId) {
        val vids = vm.viewerList.value.filter { it.isVideo }
        if (vids.any { it.id == startItem.id }) vids else listOf(startItem)
    }
    // Live queue order. Shuffle really reorders it (and ExoPlayer's playlist) so the list matches playback.
    val queue = remember(itemId) {
        androidx.compose.runtime.mutableStateListOf<sleepless_index.haze.data.MediaItem>().apply { addAll(originalQueue) }
    }
    val startIdx = remember(queue) { queue.indexOfFirst { it.id == startItem.id }.coerceAtLeast(0) }
    var curIdx by remember { mutableIntStateOf(startIdx) }
    val item = queue.getOrElse(curIdx) { startItem }

    // Subtitle files loaded by the user (or found next to the video), per video.
    val subsById = remember { mutableMapOf<Long, List<ExoMediaItem.SubtitleConfiguration>>() }
    val customSubs = remember { mutableStateMapOf<Long, List<SubCue>>() }
    val customNames = remember { mutableStateMapOf<Long, String>() }
    var customOn by remember { mutableStateOf(true) }

    val player = remember {
        val factory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, factory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .apply {
                val subs = if (startItem.id >= 0 && MediaOps.hasAllFiles()) findSiblingSubtitles(context, startItem) else emptyList()
                subsById[startItem.id] = subs
                setMediaItems(
                    queue.mapIndexed { i, v -> exoItemFor(v, if (i == startIdx) subs else emptyList()) },
                    startIdx,
                    C.TIME_UNSET,
                )
                playWhenReady = false
                prepare()
            }
    }

    val session = remember(player) {
        MediaSession.Builder(context, player).setId("gallery_" + java.util.UUID.randomUUID()).build()
    }

    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val idx = player.currentMediaItemIndex
            val cur = queue.getOrNull(idx)
            if (cur != null) {
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Subtitles"
                scope.launch {
                    // SRT / VTT are parsed and drawn by the app itself.
                    val cues = withContext(Dispatchers.IO) {
                        runCatching {
                            val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            if (raw == null) emptyList() else parseSubtitles(decodeSubtitleBytes(raw))
                        }.getOrDefault(emptyList())
                    }
                    if (cues.isNotEmpty()) {
                        customSubs[cur.id] = cues
                        customNames[cur.id] = name.substringBeforeLast('.').ifBlank { "Subtitles" }
                        customOn = true
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                    } else {
                        // Other formats (ASS, TTML...) go through the player.
                        val list = (subsById[cur.id] ?: emptyList()).filter { it.uri != uri } + subtitleConfig(uri, name)
                        subsById[cur.id] = list
                        val pos = player.currentPosition
                        val was = player.playWhenReady
                        player.replaceMediaItem(idx, exoItemFor(cur, list))
                        player.seekTo(idx, pos)
                        player.playWhenReady = was
                        customOn = false
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setSelectUndeterminedTextLanguage(true)
                            .build()
                    }
                }
            }
        }
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // ---- state ----------------------------------------------------------
    var isPlaying by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(startItem.durationMs) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var error by remember { mutableStateOf<String?>(null) }
    var controls by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var fill by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var boost by remember { mutableStateOf(false) }
    var inPip by remember { mutableStateOf(false) }
    var hud by remember { mutableStateOf<Hud?>(null) }
    var speedMenu by remember { mutableStateOf(false) }
    var audioMenu by remember { mutableStateOf(false) }
    var subsMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var queueSheet by remember { mutableStateOf(false) }
    var mirror by remember { mutableStateOf(false) }
    var timerMin by remember { mutableIntStateOf(0) }
    var navTick by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    val menuOpen = speedMenu || audioMenu || subsMenu || moreMenu || queueSheet
    var lastHud by remember { mutableStateOf<Hud?>(null) }
    if (hud != null) lastHud = hud

    val hasNext = remember(navTick, curIdx) { player.hasNextMediaItem() }
    val hasPrev = remember(navTick, curIdx) { player.hasPreviousMediaItem() }

    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var brightness by remember {
        val cur = activity?.window?.attributes?.screenBrightness ?: -1f
        val value = if (cur >= 0f) {
            cur
        } else {
            runCatching {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
            }.getOrDefault(0.5f)
        }
        mutableFloatStateOf(value)
    }

    fun applyBrightness(v: Float) {
        brightness = v.coerceIn(0.02f, 1f)
        val window = activity?.window ?: return
        val lp = window.attributes
        lp.screenBrightness = brightness
        window.attributes = lp
    }

    fun seekBy(ms: Long) {
        val dur = if (player.duration > 0) player.duration else Long.MAX_VALUE
        player.seekTo((player.currentPosition + ms).coerceIn(0L, dur))
    }

    fun cycleLoop() {
        val next = when (prefs.loop) {
            LoopMode.OFF -> LoopMode.ONCE
            LoopMode.ONCE -> LoopMode.REPEAT
            LoopMode.REPEAT -> LoopMode.OFF
        }
        vm.setLoop(next)
        hud = Hud(
            if (next == LoopMode.REPEAT) GIcons.RepeatOne else GIcons.Repeat,
            when (next) {
                LoopMode.OFF -> "Loop off"
                LoopMode.ONCE -> "Loop once"
                LoopMode.REPEAT -> "Loop repeat"
            },
        )
    }

    fun toggleBgPlay() {
        val on = !prefs.bgPlay
        vm.setBgPlay(on)
        if (on && Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        hud = Hud(GIcons.Headphones, if (on) "Background play on" else "Background play off")
    }

    fun cycleTimer() {
        timerMin = when (timerMin) {
            0 -> 15
            15 -> 30
            30 -> 60
            else -> 0
        }
        hud = Hud(GIcons.Timer, if (timerMin > 0) "Sleep in $timerMin min" else "Timer off")
    }

    fun togglePlay() {
        if (player.isPlaying) {
            player.pause()
        } else {
            val atEnd = player.playbackState == Player.STATE_ENDED ||
                (player.duration > 0 && player.currentPosition >= player.duration - 300)
            if (atEnd) player.seekTo(player.currentMediaItemIndex, 0)
            player.play()
        }
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ratio = (item.width.toFloat() / item.height.coerceAtLeast(1).toFloat()).coerceIn(0.5f, 2.2f)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational((ratio * 1000).toInt(), 1000))
                .build()
            runCatching { activity?.enterPictureInPictureMode(params) }
        }
    }

    // ---- player wiring ----------------------------------------------------
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onTracksChanged(t: Tracks) {
                tracks = t
            }

            override fun onMediaItemTransition(mediaItem: ExoMediaItem?, reason: Int) {
                val idx = player.currentMediaItemIndex
                curIdx = idx
                navTick++
                // "Loop once" has done its extra play: go back to normal.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT && prefs.loop == LoopMode.ONCE) vm.setLoop(LoopMode.OFF)
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                    val next = queue.getOrNull(idx) ?: return
                    position = 0L
                    duration = next.durationMs
                    ended = false
                    if (next.id >= 0 && prefs.resume) {
                        scope.launch {
                            val saved = vm.resumePosition(next.id)
                            if (saved != null && saved > 3000 && player.currentMediaItemIndex == idx && player.currentPosition < 2000) {
                                player.seekTo(saved)
                            }
                        }
                    }
                    if (prefs.bgPlay && player.playWhenReady) PlaybackService.start(context, next.name)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (oldPosition.mediaItemIndex != newPosition.mediaItemIndex) {
                    val old = queue.getOrNull(oldPosition.mediaItemIndex)
                    if (old != null && old.id >= 0) vm.saveResume(old.id, oldPosition.positionMs, old.durationMs)
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) controls = true
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                navTick++
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                navTick++
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && player.duration > 0) duration = player.duration
                ended = state == Player.STATE_ENDED
                if (ended) {
                    controls = true
                    PlaybackService.stop(context)
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                error = e.errorCodeName
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (prefs.resume && startItem.id >= 0) {
            val saved = vm.resumePosition(startItem.id)
            if (saved != null && saved > 3000) player.seekTo(startIdx, saved)
        }
        player.playWhenReady = prefs.autoplay
    }

    // Queue behaviour: loop (off / one / all), shuffle, and whether a finished video moves on to the next.
    LaunchedEffect(prefs.loop, prefs.shuffle, prefs.autoNext) {
        player.repeatMode = when (prefs.loop) {
            LoopMode.OFF -> Player.REPEAT_MODE_OFF
            LoopMode.ONCE, LoopMode.REPEAT -> Player.REPEAT_MODE_ONE
        }
        player.shuffleModeEnabled = false
        player.pauseAtEndOfMediaItems = !(prefs.autoNext || prefs.shuffle)
        navTick++
    }

    // Shuffle moves the videos around in the queue (the one playing stays put at the top);
    // turning it off puts them back in their original order.
    LaunchedEffect(prefs.shuffle) {
        val target: List<sleepless_index.haze.data.MediaItem> = if (prefs.shuffle) {
            val cur = queue.getOrNull(player.currentMediaItemIndex)
            if (cur == null) queue.toList() else listOf(cur) + queue.filter { it !== cur }.shuffled()
        } else {
            val have = queue.map { it.id }.toSet()
            originalQueue.filter { it.id in have }
        }
        if (target.size == queue.size) {
            for (i in target.indices) {
                val j = queue.indexOfFirst { it.id == target[i].id }
                if (j >= 0 && j != i) {
                    player.moveMediaItem(j, i)
                    queue.add(i, queue.removeAt(j))
                }
            }
            curIdx = player.currentMediaItemIndex
            navTick++
        }
    }

    // Each video opens in the orientation that suits it.
    // Unless you've rotated it yourself: then your choice is remembered for every video.
    LaunchedEffect(curIdx) {
        val remembered = vm.prefs.value.playerOrientation
        val land = when (remembered) {
            1 -> true
            2 -> false
            else -> item.width >= item.height
        }
        activity?.requestedOrientation = if (land) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    // Sleep timer.
    LaunchedEffect(timerMin) {
        if (timerMin > 0) {
            delay(timerMin * 60_000L)
            player.pause()
            timerMin = 0
        }
    }

    // Background play keeps a notification (and so the process) alive while playing.
    LaunchedEffect(prefs.bgPlay, isPlaying) {
        if (prefs.bgPlay && (isPlaying || PlaybackBridge.player != null)) PlaybackService.start(context, item.name)
        if (!prefs.bgPlay) PlaybackService.stop(context)
    }

    // Keep this video's position fresh so it survives the app being killed or switching videos.
    LaunchedEffect(player) {
        while (true) {
            delay(5000)
            val cur = queue.getOrNull(player.currentMediaItemIndex)
            if (player.isPlaying && cur != null && cur.id >= 0) {
                vm.saveResume(cur.id, player.currentPosition, player.duration.coerceAtLeast(0))
            }
        }
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            delay(250)
        }
    }

    LaunchedEffect(controls, isPlaying, interaction, menuOpen, scrubbing) {
        if (controls && isPlaying && !menuOpen && !scrubbing) {
            delay(3500)
            controls = false
        }
    }

    LaunchedEffect(hud, boost) {
        if (hud != null && !boost) {
            delay(900)
            hud = null
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !prefs.bgPlay) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(activity) {
        val componentActivity = activity as? ComponentActivity
        val consumer = Consumer<PictureInPictureModeChangedInfo> { info ->
            inPip = info.isInPictureInPictureMode
        }
        componentActivity?.addOnPictureInPictureModeChangedListener(consumer)
        onDispose { componentActivity?.removeOnPictureInPictureModeChangedListener(consumer) }
    }

    DisposableEffect(Unit) {
        val window = activity?.window
        val ctrl = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        ctrl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctrl?.hide(WindowInsetsCompat.Type.systemBars())
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            ctrl?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (window != null) {
                val lp = window.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = lp
            }
        }
    }

    DisposableEffect(player) {
        PlaybackBridge.player = player
        PlaybackBridge.session = session
        onDispose {
            if (PlaybackBridge.player === player) {
                PlaybackBridge.player = null
                PlaybackBridge.session = null
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val cur = queue.getOrNull(player.currentMediaItemIndex)
            if (cur != null && cur.id >= 0) vm.saveResume(cur.id, player.currentPosition, player.duration.coerceAtLeast(0))
            PlaybackService.stop(context)
            session.release()
            player.release()
        }
    }

    // ---- UI -------------------------------------------------------------
    val exoPlayer = player
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Mirroring needs a TextureView (a SurfaceView ignores view transforms).
        key(mirror) {
            AndroidView(
                factory = { ctx ->
                    val v = if (mirror) {
                        android.view.LayoutInflater.from(ctx).inflate(R.layout.player_texture, null, false) as PlayerView
                    } else {
                        PlayerView(ctx)
                    }
                    v.apply {
                        useController = false
                        this.player = exoPlayer
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                update = {
                    it.resizeMode = if (fill) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = if (mirror) -1f else 1f },
            )
        }

        // Subtitles loaded from a file
        val loadedCues = customSubs[item.id]
        if (loadedCues != null && customOn) {
            val now = position
            val active = loadedCues.filter { now >= it.start && now < it.end }
            if (active.isNotEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp)
                        .padding(bottom = if (controls && !locked && !inPip) 130.dp else 36.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Text(
                        text = active.joinToString("\n") { it.text },
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x99000000))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // gesture layer
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(locked, inPip) {
                    if (!locked && !inPip) {
                        detectTapGestures(
                            onPress = {
                                val job = scope.launch {
                                    delay(450)
                                    boost = true
                                    player.setPlaybackSpeed(2f)
                                    hud = Hud(GIcons.Play, "2x speed")
                                }
                                tryAwaitRelease()
                                job.cancel()
                                if (boost) {
                                    boost = false
                                    player.setPlaybackSpeed(speed)
                                }
                            },
                            onTap = {
                                controls = !controls
                                interaction++
                            },
                            onDoubleTap = { o ->
                                if (o.x < size.width / 2f) {
                                    seekBy(-10_000)
                                    hud = Hud(GIcons.FastRewind, "10s", null, -1)
                                } else {
                                    seekBy(10_000)
                                    hud = Hud(GIcons.FastForward, "10s", null, 1)
                                }
                            },
                        )
                    }
                }
                .pointerInput(locked, inPip) {
                    if (!locked && !inPip) {
                        var mode = 0
                        var startX = 0f
                        var startPos = 0L
                        var accum = 0f
                        var target = 0L
                        var volAccum = 0f
                        detectDragGestures(
                            onDragStart = { start ->
                                mode = 0
                                startX = start.x
                                startPos = player.currentPosition
                                target = startPos
                                accum = 0f
                                volAccum = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                            },
                            onDragEnd = { mode = 0 },
                            onDragCancel = { mode = 0 },
                            onDrag = { change, drag ->
                                change.consume()
                                if (mode == 0) {
                                    // Horizontal swipes do nothing (seeking is the bar's job).
                                    mode = if (abs(drag.x) > abs(drag.y)) {
                                        4
                                    } else if (startX < size.width / 2f) {
                                        2
                                    } else {
                                        3
                                    }
                                }
                                when (mode) {
                                    2 -> {
                                        applyBrightness(brightness - drag.y / size.height * 1.2f)
                                        hud = Hud(GIcons.Brightness, "${(brightness * 100).roundToInt()}%", brightness, -1)
                                    }
                                    3 -> {
                                        volAccum = (volAccum - drag.y / size.height * maxVol * 1.2f)
                                            .coerceIn(0f, maxVol.toFloat())
                                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, volAccum.roundToInt(), 0)
                                        hud = Hud(GIcons.Volume, "${(volAccum / maxVol * 100).roundToInt()}%", volAccum / maxVol, 1)
                                    }
                                    else -> {}
                                }
                            },
                        )
                    }
                }
        )

        // gesture HUD
        AnimatedVisibility(
            visible = hud != null && hud?.side == 0,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(2f)
                .displayCutoutPadding()
                .padding(top = 88.dp),
            enter = fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.85f),
            exit = fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.9f),
        ) {
            val h = lastHud
            if (h != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(PlayerChrome)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(h.icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(20.dp))
                    Text(h.text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    if (h.fraction != null) {
                        Box(
                            Modifier
                                .width(70.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(h.fraction.coerceIn(0f, 1f))
                                    .background(cs.primary)
                            )
                        }
                    }
                }
            }
        }

        // side HUDs: rewind / brightness on the left, forward / volume on the right
        for (side in listOf(-1, 1)) {
            AnimatedVisibility(
                visible = hud != null && hud?.side == side,
                modifier = Modifier
                    .align(if (side < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .displayCutoutPadding()
                    .padding(horizontal = if (narrow) 12.dp else 28.dp),
                enter = fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.85f),
                exit = fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.9f),
            ) {
                val h = lastHud
                if (h != null && h.side == side && h.fraction == null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(PlayerChrome)
                            .width(92.dp)
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(h.icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(h.text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                    }
                } else if (h != null && h.side == side) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(PlayerChrome)
                            .width(if (h.fraction != null) 58.dp else 72.dp)
                            .padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(h.icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(22.dp))
                        if (h.fraction != null) {
                            Box(
                                Modifier
                                    .width(5.dp)
                                    .height(88.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(h.fraction.coerceIn(0f, 1f))
                                        .background(cs.primary)
                                )
                            }
                        }
                        Text(
                            h.text,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.width(44.dp),
                        )
                    }
                }
            }
        }

        // error
        if (error != null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Can't play this video", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(error ?: "", color = Color(0xFF8A8A8E), fontSize = 12.sp)
            }
        }

        // lock indicator
        AnimatedVisibility(
            visible = locked && !inPip,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .displayCutoutPadding()
                .padding(start = 24.dp),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            RoundChrome(GIcons.Lock, "Unlock controls", 52) {
                locked = false
                controls = true
            }
        }

        // controls
        val showControls = controls && !locked && !inPip
        AnimatedVisibility(
            visible = showControls,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(160)),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color(0xC7000000), Color.Transparent)))
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD1000000))))
                )

                // top bar
                AnimatedVisibility(
                    visible = showControls,
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = slideInVertically(tween(220)) { -it / 2 },
                    exit = slideOutVertically(tween(180)) { -it / 2 },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .displayCutoutPadding()
                            .padding(start = 12.dp, end = 12.dp, top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RoundChrome(GIcons.Back, "Back") { onBack() }
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            val info = buildString {
                                append(item.mime.substringAfter('/', "").uppercase(Locale.US))
                                if (item.width > 0) append("  ·  ${item.width} x ${item.height}")
                            }
                            Text(info, color = Color(0xFFA8A8AC), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        if (isLand) {
                            // Landscape has room up here, so the handier options live on the bar.
                            RoundChrome(GIcons.Subtitles, "Subtitles") { subsMenu = true }
                            RoundChrome(
                                GIcons.Headphones, "Background play",
                                tint = if (prefs.bgPlay) cs.primary else Color.White,
                            ) { toggleBgPlay() }
                            RoundChrome(
                                GIcons.Timer, if (timerMin > 0) "Timer, $timerMin min" else "Timer",
                                tint = if (timerMin > 0) cs.primary else Color.White,
                            ) { cycleTimer() }
                            RoundChrome(GIcons.Share, "Share") { shareItems(context, listOf(item)) }
                        }
                        RoundChrome(GIcons.Pip, "Picture in picture") { enterPip() }
                        Box {
                            RoundChrome(GIcons.More, "More options") { moreMenu = true }
                            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                if (!isLand) CompactMenuItem(
                                    text = { PlayerMenuRow(GIcons.Headphones, "Background play", prefs.bgPlay) },
                                    onClick = {
                                        moreMenu = false
                                        val on = !prefs.bgPlay
                                        vm.setBgPlay(on)
                                        if (on && Build.VERSION.SDK_INT >= 33) {
                                            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        hud = Hud(GIcons.Headphones, if (on) "Background play on" else "Background play off")
                                    },
                                )
                                if (!isLand) CompactMenuItem(
                                    text = { PlayerMenuRow(GIcons.Subtitles, "Subtitles") },
                                    onClick = {
                                        moreMenu = false
                                        subsMenu = true
                                    },
                                )
                                CompactMenuItem(
                                    text = { PlayerMenuRow(GIcons.Shuffle, "Shuffle", prefs.shuffle) },
                                    onClick = {
                                        moreMenu = false
                                        vm.setShuffle(!prefs.shuffle)
                                        hud = Hud(GIcons.Shuffle, if (!prefs.shuffle) "Shuffle on" else "Shuffle off")
                                    },
                                )
                                CompactMenuItem(
                                    text = { PlayerMenuRow(GIcons.FastForward, "Autoplay next", prefs.autoNext) },
                                    onClick = {
                                        moreMenu = false
                                        vm.setAutoNext(!prefs.autoNext)
                                        hud = Hud(GIcons.FastForward, if (!prefs.autoNext) "Autoplay next on" else "Autoplay next off")
                                    },
                                )
                                if (!isLand) CompactMenuItem(
                                    text = { PlayerMenuRow(GIcons.Timer, if (timerMin > 0) "Timer · $timerMin min" else "Timer", timerMin > 0) },
                                    onClick = {
                                        moreMenu = false
                                        timerMin = when (timerMin) {
                                            0 -> 15
                                            15 -> 30
                                            30 -> 60
                                            else -> 0
                                        }
                                        hud = Hud(GIcons.Timer, if (timerMin > 0) "Sleep in $timerMin min" else "Timer off")
                                    },
                                )
                                CompactMenuItem(
                                    text = { PlayerMenuRow(GIcons.Flip, "Mirror", mirror) },
                                    onClick = {
                                        moreMenu = false
                                        mirror = !mirror
                                    },
                                )
                                if (!isLand) CompactMenuItem(
                                    text = { PlayerMenuRow(GIcons.Share, "Share") },
                                    onClick = {
                                        moreMenu = false
                                        shareItems(context, listOf(item))
                                    },
                                )
                            }
                            DropdownMenu(expanded = subsMenu, onDismissRequest = { subsMenu = false }) {
                                val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                                CompactMenuItem(
                                    text = { Text("Off") },
                                    onClick = {
                                        subsMenu = false
                                        customOn = false
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                                    },
                                )
                                textGroups.forEachIndexed { i, g ->
                                    CompactMenuItem(
                                        text = { Text(trackLabel(g, i, "Subtitle")) },
                                        onClick = {
                                            subsMenu = false
                                            customOn = false
                                            player.trackSelectionParameters = player.trackSelectionParameters
                                                .buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0))
                                                .build()
                                        },
                                    )
                                }
                                customNames[item.id]?.let { nm ->
                                    CompactMenuItem(
                                        text = { Text(nm) },
                                        onClick = {
                                            subsMenu = false
                                            customOn = true
                                            player.trackSelectionParameters = player.trackSelectionParameters
                                                .buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                                        },
                                    )
                                }
                                if (textGroups.isEmpty() && customNames[item.id] == null) {
                                    CompactMenuItem(text = { Text("No subtitles in this file") }, enabled = false, onClick = {})
                                }
                                CompactMenuItem(
                                    text = { Text("Load subtitle file…") },
                                    onClick = {
                                        subsMenu = false
                                        subtitlePicker.launch(arrayOf("*/*"))
                                    },
                                )
                            }
                        }
                    }
                }

                // centre controls: previous / play / next, no background
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (narrow) 20.dp else 36.dp),
                ) {
                    SkipButton(GIcons.SkipPrevious, "Previous video", hasPrev, box = 56.dp, iconSize = 28.dp, background = PlayerChrome) {
                        player.seekToPreviousMediaItem()
                        player.play()
                        interaction++
                    }
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(PlayerChrome)
                            .bounceClick(onClick = { togglePlay(); interaction++ }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Crossfade(targetState = isPlaying, animationSpec = tween(160), label = "playPause") { playing ->
                            Icon(
                                imageVector = if (playing) GIcons.Pause else GIcons.Play,
                                contentDescription = if (playing) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                    SkipButton(GIcons.SkipNext, "Next video", hasNext, box = 56.dp, iconSize = 28.dp, background = PlayerChrome) {
                        player.seekToNextMediaItem()
                        player.play()
                        interaction++
                    }
                }

                // bottom bar
                AnimatedVisibility(
                    visible = showControls,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(tween(240)) { it / 2 },
                    exit = slideOutVertically(tween(180)) { it / 2 },
                ) {
                        val landNow = config.orientation == Configuration.ORIENTATION_LANDSCAPE
                        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }

                        val audioChip: @Composable () -> Unit = {
                            // Only worth showing when the file really has several audio tracks.
                            if (audioGroups.size > 1) {
                                Box {
                                    val selectedIdx = audioGroups.indexOfFirst { it.isSelected }
                                    PlayerChip(
                                        text = "Audio ${selectedIdx.coerceAtLeast(0) + 1}",
                                        icon = GIcons.Audio,
                                    ) { audioMenu = true }
                                    DropdownMenu(expanded = audioMenu, onDismissRequest = { audioMenu = false }) {
                                        audioGroups.forEachIndexed { i, g ->
                                            CompactMenuItem(
                                                text = { Text(trackLabel(g, i, "Audio")) },
                                                onClick = {
                                                    audioMenu = false
                                                    player.trackSelectionParameters = player.trackSelectionParameters
                                                        .buildUpon()
                                                        .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0))
                                                        .build()
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val lockButton: @Composable () -> Unit = {
                            if (landNow) {
                                PlayerChip("Lock", GIcons.Lock) {
                                    locked = true
                                    controls = false
                                }
                            } else {
                                RoundChrome(GIcons.Lock, "Lock", 36) {
                                    locked = true
                                    controls = false
                                }
                            }
                        }

                        val speedChip: @Composable () -> Unit = {
                            Box {
                                PlayerChip("${if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()}x", null, accent = true) {
                                    speedMenu = true
                                }
                                DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp ->
                                        CompactMenuItem(
                                            text = { Text("${sp}x") },
                                            onClick = {
                                                speedMenu = false
                                                speed = sp
                                                player.setPlaybackSpeed(sp)
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        val loopButton: @Composable () -> Unit = {
                            RoundChrome(
                                if (prefs.loop == LoopMode.REPEAT) GIcons.RepeatOne else GIcons.Repeat,
                                "Loop",
                                36,
                                tint = if (prefs.loop == LoopMode.OFF) Color.White else cs.primary,
                            ) { cycleLoop() }
                        }
                        val queueButton: @Composable () -> Unit = {
                            RoundChrome(GIcons.Playlist, "Queue", 36) { queueSheet = true }
                        }
                        val rotateButton: @Composable () -> Unit = {
                            RoundChrome(GIcons.ScreenRotate, "Rotate screen", 36) {
                                vm.setPlayerOrientation(if (landNow) 2 else 1)
                                activity?.requestedOrientation = if (landNow) {
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
                            }
                        }
                        val fitButton: @Composable () -> Unit = {
                            RoundChrome(GIcons.Fit, "Fit or fill screen", 36) { fill = !fill }
                        }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .displayCutoutPadding()
                            .padding(bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = formatDuration(if (scrubbing) (scrubValue * duration).toLong() else position) +
                                " / " + formatDuration(duration),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .padding(start = 24.dp, top = 6.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(PlayerChrome)
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                        val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                        SeekBar(
                            fraction = if (scrubbing) scrubValue else fraction,
                            scrubbing = scrubbing,
                            onChange = {
                                scrubbing = true
                                scrubValue = it
                            },
                            onFinish = {
                                val target = (scrubValue * duration).toLong()
                                player.seekTo(target)
                                position = target
                                scrubbing = false
                                interaction++
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                        ) {
                            Row(
                                modifier = Modifier.align(Alignment.CenterStart),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                lockButton()
                                speedChip()
                                audioChip()
                            }
                            Row(
                                modifier = Modifier.align(Alignment.CenterEnd),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                loopButton()
                                queueButton()
                                rotateButton()
                                fitButton()
                            }
                        }
                    }
                }
            }
        }
        QueuePanel(
            visible = queueSheet,
            queue = queue,
            currentIndex = curIdx,
            loop = prefs.loop,
            shuffle = prefs.shuffle,
            onLoop = { cycleLoop() },
            onShuffle = { vm.setShuffle(!prefs.shuffle) },
            onPick = { i ->
                queueSheet = false
                player.seekTo(i, 0L)
                player.play()
            },
            onDismiss = { queueSheet = false },
        )
    }
}
