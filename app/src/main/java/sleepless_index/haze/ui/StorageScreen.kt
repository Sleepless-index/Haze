package sleepless_index.haze.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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

private data class AlbumSize(val name: String, val count: Int, val bytes: Long)

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
        .map { (_, list) -> AlbumSize(list[0].bucketName.ifBlank { "Unknown" }, list.size, list.sumOf { it.sizeBytes }) }
        .sortedByDescending { it.bytes }
        .take(6)
    return StorageStats(
        photoBytes = photos.sumOf { it.sizeBytes },
        videoBytes = videos.sumOf { it.sizeBytes },
        photos = photos.size,
        videos = videos.size,
        bigVideos = videos.sortedByDescending { it.sizeBytes }.take(8),
        bigPhotos = photos.sortedByDescending { it.sizeBytes }.take(6),
        albums = albums,
    )
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
private fun StorageDivider() {
    Box(Modifier.padding(start = 76.dp).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
}

@Composable
fun StorageScreen(
    vm: GalleryViewModel,
    onBack: () -> Unit,
    onOpen: (List<MediaItem>, Int) -> Unit,
    onOpenCleanup: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val all by vm.items.collectAsStateWithLifecycle()
    val cleanup by vm.cleanup.collectAsStateWithLifecycle()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val stats by produceState<StorageStats?>(null, all) {
        value = withContext(Dispatchers.Default) { computeStats(all) }
    }
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
        val s = stats
        if (s == null) return@Column
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp + navBottom),
        ) {
            val total = s.photoBytes + s.videoBytes
            CardGroup {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(formatSize(total), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground)
                    Text("${s.photos} photos  ·  ${s.videos} videos", fontSize = 13.sp, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    val pw = (s.photoBytes.toFloat() / total.coerceAtLeast(1)).coerceAtLeast(0.001f)
                    val vw = (s.videoBytes.toFloat() / total.coerceAtLeast(1)).coerceAtLeast(0.001f)
                    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
                        Box(Modifier.weight(pw).fillMaxHeight().background(cs.primary))
                        Box(Modifier.weight(vw).fillMaxHeight().background(cs.primary.copy(alpha = 0.35f)))
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(cs.primary))
                            Text("Photos ${formatSize(s.photoBytes)}", fontSize = 12.5.sp, color = cs.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(cs.primary.copy(alpha = 0.35f)))
                            Text("Videos ${formatSize(s.videoBytes)}", fontSize = 12.5.sp, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            CardGroup {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .bounceClick(onClick = onOpenCleanup)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Clean up", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground)
                        Text(
                            if (cleanup.scanned && cleanup.freeableBytes > 0) "${formatSize(cleanup.freeableBytes)} can be freed"
                            else "Duplicates, similar photos and large videos",
                            fontSize = 12.5.sp, color = cs.onSurfaceVariant,
                        )
                    }
                    Icon(GIcons.ChevronRight, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }

            if (s.albums.isNotEmpty()) {
                SectionLabel("Biggest albums")
                CardGroup {
                    val max = s.albums.first().bytes.coerceAtLeast(1)
                    s.albums.forEachIndexed { i, a ->
                        if (i > 0) StorageDivider()
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(a.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${a.count} items", fontSize = 12.sp, color = cs.onSurfaceVariant)
                                }
                                Text(formatSize(a.bytes), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = cs.onBackground)
                            }
                            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(cs.surfaceVariant)) {
                                Box(
                                    Modifier
                                        .fillMaxWidth((a.bytes.toFloat() / max).coerceIn(0.02f, 1f))
                                        .fillMaxHeight()
                                        .background(cs.primary)
                                )
                            }
                        }
                    }
                }
            }

            if (s.bigVideos.isNotEmpty()) {
                SectionLabel("Biggest videos")
                CardGroup {
                    s.bigVideos.forEachIndexed { i, m ->
                        if (i > 0) StorageDivider()
                        MediaSizeRow(m) { onOpen(s.bigVideos, i) }
                    }
                }
            }

            if (s.bigPhotos.isNotEmpty()) {
                SectionLabel("Biggest photos")
                CardGroup {
                    s.bigPhotos.forEachIndexed { i, m ->
                        if (i > 0) StorageDivider()
                        MediaSizeRow(m) { onOpen(s.bigPhotos, i) }
                    }
                }
            }
        }
    }
}
