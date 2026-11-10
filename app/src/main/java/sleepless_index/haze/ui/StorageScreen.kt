package sleepless_index.haze.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sleepless_index.haze.data.MediaItem
import sleepless_index.haze.data.formatDuration
import sleepless_index.haze.data.formatSize
import sleepless_index.haze.data.thumbRequest
import sleepless_index.haze.ui.theme.GIcons

private data class AlbumSize(val id: Long, val name: String, val count: Int, val bytes: Long)

private data class StorageStats(
    val photoBytes: Long,
    val videoBytes: Long,
    val photos: Int,
    val videos: Int,
    val bigVideos: List<MediaItem>,
    val bigPhotos: List<MediaItem>,
    val albums: List<AlbumSize>,
)

private fun computeStats(all: List<MediaItem>): StorageStats {
    val videos = all.filter { it.isVideo }
    val photos = all.filter { !it.isVideo }
    val albums = all.groupBy { it.bucketId }
        .map { (id, list) -> AlbumSize(id, list[0].bucketName.ifBlank { "Unknown" }, list.size, list.sumOf { it.sizeBytes }) }
        .sortedByDescending { it.bytes }
        .take(8)
    return StorageStats(
        photoBytes = photos.sumOf { it.sizeBytes },
        videoBytes = videos.sumOf { it.sizeBytes },
        photos = photos.size,
        videos = videos.size,
        bigVideos = videos.sortedByDescending { it.sizeBytes }.take(10),
        bigPhotos = photos.sortedByDescending { it.sizeBytes }.take(10),
        albums = albums,
    )
}

/** "18.4" and "GB" so the number can be shown big and the unit small. */
private fun splitSize(bytes: Long): Pair<String, String> {
    val gb = bytes / 1073741824.0
    if (gb >= 1) return "%.1f".format(gb) to "GB"
    val mb = bytes / 1048576.0
    if (mb >= 1) return "%.0f".format(mb) to "MB"
    return "%.0f".format(bytes / 1024.0) to "KB"
}

@Composable
private fun TabPill(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) cs.primary else androidx.compose.ui.graphics.Color.Transparent, tween(220), label = "tabBg")
    val fg by animateColorAsState(if (selected) cs.onPrimary else cs.onSurfaceVariant, tween(220), label = "tabFg")
    Box(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(bg)
            .bounceClick(role = androidx.compose.ui.semantics.Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, color = fg)
    }
}

@Composable
private fun StorageDivider(start: Int) {
    Box(Modifier.padding(start = start.dp).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
}

@Composable
private fun AlbumRow(a: AlbumSize, max: Long, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().bounceClick(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(a.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("%,d items".format(a.count), fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
            Text(formatSize(a.bytes), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = cs.onBackground)
        }
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(cs.surfaceVariant)) {
            Box(
                Modifier
                    .fillMaxWidth((a.bytes.toFloat() / max).coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(cs.primary)
            )
        }
    }
}

@Composable
private fun MediaSizeRow(m: MediaItem, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(
            model = remember(m.uri) { thumbRequest(context, m.uri, 160, m.isVideo) },
            contentDescription = m.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceVariant),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(m.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    m.bucketName.ifBlank { null },
                    if (m.isVideo && m.durationMs > 0) formatDuration(m.durationMs) else null,
                ).joinToString("  ·  "),
                fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(formatSize(m.sizeBytes), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = cs.onBackground)
    }
}

@Composable
fun StorageScreen(
    vm: GalleryViewModel,
    onBack: () -> Unit,
    onOpen: (List<MediaItem>, Int) -> Unit,
    onOpenAlbum: (Long) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val all by vm.items.collectAsStateWithLifecycle()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val stats by produceState<StorageStats?>(null, all) {
        value = withContext(Dispatchers.Default) { computeStats(all) }
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    BackHandler { onBack() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = "Storage",
            subtitle = "",
            leading = {
                GIconButton(GIcons.Back, "Back", onClick = onBack)
                Spacer(Modifier.size(12.dp))
            },
        )
        val s = stats ?: return@Column
        val cardShape = RoundedCornerShape(20.dp)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 40.dp + navBottom),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val total = s.photoBytes + s.videoBytes
            val (num, unit) = splitSize(total)
            Column(
                Modifier.fillMaxWidth().clip(cardShape).background(cs.surface).border(BorderStroke(1.dp, cs.outline), cardShape)
                    .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(num, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground)
                    Text(unit, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text("%,d photos  ·  %,d videos".format(s.photos, s.videos), fontSize = 13.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                val pw = (s.photoBytes.toFloat() / total.coerceAtLeast(1)).coerceAtLeast(0.001f)
                val vw = (s.videoBytes.toFloat() / total.coerceAtLeast(1)).coerceAtLeast(0.001f)
                Row(Modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(
                        Modifier.weight(pw).fillMaxHeight()
                            .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 2.dp, bottomEnd = 2.dp))
                            .background(cs.primary)
                    )
                    Box(
                        Modifier.weight(vw).fillMaxHeight()
                            .clip(RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 6.dp, bottomEnd = 6.dp))
                            .background(cs.primary.copy(alpha = 0.4f))
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(cs.primary))
                        Text("Photos ${formatSize(s.photoBytes)}", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(cs.primary.copy(alpha = 0.4f)))
                        Text("Videos ${formatSize(s.videoBytes)}", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                }
            }

            val tabShape = RoundedCornerShape(21.dp)
            Row(
                Modifier.fillMaxWidth().clip(tabShape).background(cs.surface).border(BorderStroke(1.dp, cs.outline), tabShape).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf("Albums", "Videos", "Photos").forEachIndexed { i, label ->
                    TabPill(label, tab == i, Modifier.weight(1f)) { tab = i }
                }
            }

            Column(Modifier.fillMaxWidth().clip(cardShape).background(cs.surface).border(BorderStroke(1.dp, cs.outline), cardShape)) {
                when (tab) {
                    0 -> {
                        val max = s.albums.firstOrNull()?.bytes?.coerceAtLeast(1) ?: 1L
                        s.albums.forEachIndexed { i, a ->
                            if (i > 0) StorageDivider(16)
                            AlbumRow(a, max) { onOpenAlbum(a.id) }
                        }
                    }
                    1 -> s.bigVideos.forEachIndexed { i, m ->
                        if (i > 0) StorageDivider(76)
                        MediaSizeRow(m) { onOpen(s.bigVideos, i) }
                    }
                    else -> s.bigPhotos.forEachIndexed { i, m ->
                        if (i > 0) StorageDivider(76)
                        MediaSizeRow(m) { onOpen(s.bigPhotos, i) }
                    }
                }
                if ((tab == 0 && s.albums.isEmpty()) || (tab == 1 && s.bigVideos.isEmpty()) || (tab == 2 && s.bigPhotos.isEmpty())) {
                    Text("Nothing here yet", fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(20.dp))
                }
            }
        }
    }
}
