package sleepless_index.haze.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import sleepless_index.haze.data.CleanupGroup
import sleepless_index.haze.data.MediaItem
import sleepless_index.haze.data.formatDuration
import sleepless_index.haze.data.formatSize
import sleepless_index.haze.data.thumbRequest
import sleepless_index.haze.ui.theme.GIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
private fun CleanCard(content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(cs.surface)
            .border(BorderStroke(1.dp, cs.outline), shape),
    ) { content() }
}

@Composable
private fun CleanRow(
    icon: ImageVector,
    tileAlpha: Float,
    title: String,
    sub: String,
    size: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .then(if (enabled) Modifier.bounceClick(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(cs.primary.copy(alpha = if (enabled) tileAlpha else 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (enabled && tileAlpha >= 0.5f) cs.onPrimary else cs.onBackground, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) cs.onBackground else cs.onSurfaceVariant)
            Text(sub, fontSize = 12.5.sp, color = cs.onSurfaceVariant)
        }
        if (enabled) {
            Text(size, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = cs.onBackground)
            Icon(GIcons.ChevronRight, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun RowDivider() {
    Box(Modifier.padding(start = 70.dp).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
}

@Composable
private fun SoftButton(label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(cs.primary.copy(alpha = 0.22f))
            .bounceClick(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    )
}

// ---------------------------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------------------------

@Composable
fun CleanupScreen(
    vm: GalleryViewModel,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onOpenTrash: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val raw by vm.cleanup.collectAsStateWithLifecycle()
    val all by vm.items.collectAsStateWithLifecycle()
    val progress by vm.cleanupProgress.collectAsStateWithLifecycle()
    val freed by vm.cleanupFreed.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val present = remember(all) { all.mapTo(HashSet()) { it.id } }
    val result = remember(raw, present) { raw.pruned(present) }
    val leave = { vm.clearCleanupFreed(); onBack() }

    LaunchedEffect(Unit) { vm.scanCleanup() }
    var lastCount by remember { mutableIntStateOf(all.size) }
    LaunchedEffect(all.size) {
        // Photos coming back (an undo) can form groups again, so look once more.
        if (all.size > lastCount) vm.scanCleanup(force = true)
        lastCount = all.size
    }
    BackHandler { leave() }

    val dupCount = result.duplicates.sumOf { it.removable.size }
    val categories = listOf(
        Triple("duplicates", dupCount, result.duplicateBytes),
        Triple("similar", result.similar.size, result.similarBytes),
        Triple("screenshots", result.screenshots.size, result.screenshotBytes),
        Triple("videos", result.largeVideos.size, result.largeVideoBytes),
    )

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = "Clean up",
            subtitle = "",
            leading = {
                GIconButton(GIcons.Back, "Back", onClick = leave)
                Spacer(Modifier.size(12.dp))
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 6.dp, bottom = 100.dp + navBottom),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val done = freed
            if (done != null) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(cs.primary.copy(alpha = 0.12f))
                        .border(BorderStroke(1.dp, cs.primary.copy(alpha = 0.3f)), RoundedCornerShape(26.dp))
                        .padding(horizontal = 20.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
                        Icon(GIcons.Check, null, tint = cs.onPrimary, modifier = Modifier.size(28.dp))
                    }
                    Text("${formatSize(done.bytes)} freed", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground)
                    Text(
                        if (Build.VERSION.SDK_INT >= 30)
                            "${done.ids.size} items are in Trash. They'll be deleted for good in ${prefs.trashDays} days."
                        else "${done.ids.size} items were deleted.",
                        fontSize = 14.sp, lineHeight = 20.sp, color = cs.onSurfaceVariant,
                    )
                    if (Build.VERSION.SDK_INT >= 30) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                            SoftButton("Undo") { vm.undoCleanup() }
                            SoftButton("Open Trash") { onOpenTrash() }
                        }
                    }
                }
                SectionLabel("Next up", Modifier.padding(top = 0.dp))
            } else {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(cs.primary.copy(alpha = 0.12f))
                        .border(BorderStroke(1.dp, cs.primary.copy(alpha = 0.3f)), RoundedCornerShape(26.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (progress != null && !result.scanned) "Looking through your library" else "You can free up",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant,
                        )
                        Text(
                            if (result.scanned) formatSize(result.freeableBytes) else "…",
                            fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground,
                        )
                    }
                    val p = progress
                    if (p != null) {
                        LinearProgressIndicator(
                            progress = { p },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = cs.primary,
                            trackColor = cs.primary.copy(alpha = 0.2f),
                        )
                    } else if (result.freeableBytes > 0) {
                        val parts = listOf(result.duplicateBytes, result.similarBytes, result.screenshotBytes).filter { it > 0 }
                        Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            parts.forEachIndexed { i, b ->
                                Box(Modifier.weight(b.toFloat()).height(8.dp).background(cs.primary.copy(alpha = 1f - i * 0.28f)))
                            }
                        }
                    }
                    Text(
                        if (Build.VERSION.SDK_INT >= 30) "Nothing is deleted until you review it. Removed items go to Trash for ${prefs.trashDays} days."
                        else "Nothing is deleted until you review it.",
                        fontSize = 13.sp, lineHeight = 19.sp, color = cs.onSurfaceVariant,
                    )
                }
            }

            CleanCard {
                val rows = categories.filter { done == null || it.second > 0 }
                rows.forEachIndexed { i, (kind, count, bytes) ->
                    if (i > 0) RowDivider()
                    val (icon, title, sub, alpha) = when (kind) {
                        "duplicates" -> CleanMeta(GIcons.Copy, "Duplicates", if (count > 0) "$count exact copies" else "No duplicates found", 1f)
                        "similar" -> CleanMeta(GIcons.Photos, "Similar photos", if (count > 0) "$count groups of bursts and retakes" else "No similar photos found", 0.75f)
                        "screenshots" -> CleanMeta(GIcons.Image, "Screenshots", if (count > 0) "$count older than 90 days" else "No old screenshots", 0.5f)
                        else -> CleanMeta(GIcons.Play, "Large videos", if (count > 0) "$count over 500 MB" else "No large videos", 0.3f)
                    }
                    CleanRow(icon, alpha, title, sub, formatSize(bytes), enabled = count > 0, onClick = { onOpen(kind) })
                }
                if (rows.isEmpty()) {
                    Text("Everything is tidy.", fontSize = 14.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                }
            }
            if (done == null) {
                Text(
                    "Large videos aren't counted in the total because you decide what to keep. Scanning happens on this phone only.",
                    fontSize = 12.5.sp, lineHeight = 18.sp, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 22.dp),
                )
            }
        }
    }
}

private data class CleanMeta(val icon: ImageVector, val title: String, val sub: String, val alpha: Float)

// ---------------------------------------------------------------------------------------------
// Review
// ---------------------------------------------------------------------------------------------

@Composable
fun CleanupReviewScreen(
    vm: GalleryViewModel,
    kind: String,
    onBack: () -> Unit,
    onOpen: (List<MediaItem>, Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val raw by vm.cleanup.collectAsStateWithLifecycle()
    val all by vm.items.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val present = remember(all) { all.mapTo(HashSet()) { it.id } }
    val result = remember(raw, present) { raw.pruned(present) }

    val grouped = kind == "duplicates" || kind == "similar"
    val groups: List<CleanupGroup> = when (kind) {
        "duplicates" -> result.duplicates
        "similar" -> result.similar
        "screenshots" -> listOfNotNull(CleanupGroup(result.screenshots, null).takeIf { it.items.isNotEmpty() })
        else -> listOfNotNull(CleanupGroup(result.largeVideos, null).takeIf { it.items.isNotEmpty() })
    }
    val title = when (kind) {
        "duplicates" -> "Duplicates"
        "similar" -> "Similar photos"
        "screenshots" -> "Screenshots"
        else -> "Large videos"
    }

    var index by rememberSaveable { mutableIntStateOf(0) }
    val safeIndex = index.coerceIn(0, (groups.size - 1).coerceAtLeast(0))
    val group = groups.getOrNull(safeIndex)
    // Taps flip the suggested choice: the suggestion itself isn't stored.
    val flipped = remember { mutableStateMapOf<Long, Boolean>() }
    var confirm by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<List<MediaItem>?>(null) }

    fun isSelected(m: MediaItem, g: CleanupGroup): Boolean =
        (grouped && m.id != g.keepId) != (flipped[m.id] == true)

    val chosen = if (group == null) emptyList() else group.items.filter { isSelected(it, group) }

    // Once the system confirms the removal (the files leave the library), count it as freed.
    LaunchedEffect(present) {
        val p = pending ?: return@LaunchedEffect
        if (p.none { it.id in present }) {
            val bytes = p.sumOf { it.sizeBytes }
            vm.addCleanupFreed(p.map { it.id }, bytes)
            vm.message("${formatSize(bytes)} freed")
            pending = null
        }
    }
    LaunchedEffect(groups.isEmpty(), pending, result.scanned) {
        if (result.scanned && groups.isEmpty() && pending == null) onBack()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GIconButton(GIcons.Back, "Back", onClick = onBack)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground)
                    if (group != null) {
                        Text(
                            if (grouped) {
                                val whenText = DateTimeFormatter.ofPattern("h:mm a, MMM d", Locale.getDefault())
                                    .format(Instant.ofEpochMilli(group.items.first().dateMs).atZone(ZoneId.systemDefault()))
                                "Group ${safeIndex + 1} of ${groups.size} · taken $whenText"
                            } else {
                                "${group.items.size} items · ${formatSize(group.items.sumOf { it.sizeBytes })}"
                            },
                            fontSize = 13.sp, color = cs.onSurfaceVariant,
                        )
                    }
                }
                if (grouped && groups.size > 1) {
                    GIconButton(GIcons.Back, "Previous group", onClick = { index = (safeIndex - 1 + groups.size) % groups.size }, size = 40.dp)
                    GIconButton(GIcons.ChevronRight, "Next group", onClick = { index = (safeIndex + 1) % groups.size }, size = 40.dp)
                }
            }

            if (group != null) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 190.dp + navBottom),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(group.items, key = { "${it.id}_${it.isVideo}" }) { m ->
                        val sel = isSelected(m, group)
                        val isKeep = m.id == group.keepId && !sel
                        val shape = RoundedCornerShape(22.dp)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.8f)
                                .clip(shape)
                                .background(cs.surfaceVariant)
                                .border(BorderStroke(2.5.dp, if (isKeep) cs.primary else cs.outline), shape)
                                .bounceClick(
                                    onLongClick = { onOpen(group.items, group.items.indexOf(m)) },
                                    onClick = {
                                        val next = !sel
                                        val after = group.items.count { if (it.id == m.id) next else isSelected(it, group) }
                                        if (grouped && after >= group.items.size) {
                                            vm.message("Keep at least one")
                                        } else {
                                            flipped[m.id] = !(flipped[m.id] ?: false)
                                        }
                                    },
                                ),
                        ) {
                            AsyncImage(
                                model = remember(m.uri) { thumbRequest(context, m.uri, 480, m.isVideo) },
                                contentDescription = m.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (isKeep) {
                                Row(
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(10.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(cs.primary)
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(GIcons.Check, null, tint = cs.onPrimary, modifier = Modifier.size(12.dp))
                                    Text(
                                        if (kind == "similar") "Keep · Best" else "Keep",
                                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cs.onPrimary,
                                    )
                                }
                            }
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (sel) cs.error else Color.Black.copy(alpha = 0.35f))
                                    .then(if (sel) Modifier else Modifier.border(2.dp, Color.White, RoundedCornerShape(9.dp))),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (sel) Icon(GIcons.Check, "Selected to remove", tint = cs.onError, modifier = Modifier.size(16.dp))
                            }
                            Text(
                                if (m.isVideo) "${formatDuration(m.durationMs)} · ${formatSize(m.sizeBytes)}" else formatSize(m.sizeBytes),
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }

        if (group != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, cs.background, cs.background)))
                    .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp + navBottom),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (grouped) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .bounceClick(onClick = { if (safeIndex < groups.size - 1) index = safeIndex + 1 else onBack() }),
                        contentAlignment = Alignment.Center,
                    ) { Text("Keep all and skip", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant) }
                }
                val can = chosen.isNotEmpty()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(27.dp))
                        .background(if (can) cs.primary else cs.surfaceVariant)
                        .then(if (can) Modifier.bounceClick(onClick = { confirm = true }) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (can) "Remove ${chosen.size} · frees ${formatSize(chosen.sumOf { it.sizeBytes })}" else "Select what to remove",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = if (can) cs.onPrimary else cs.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (confirm) {
        val toRemove = chosen
        TrashConfirm(
            items = toRemove,
            trashDays = prefs.trashDays,
            onConfirm = {
                confirm = false
                pending = toRemove
                vm.requestTrash(toRemove)
            },
            onDismiss = { confirm = false },
        )
    }
}
