package sleepless_index.haze.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import sleepless_index.haze.data.Album
import sleepless_index.haze.data.MediaItem
import sleepless_index.haze.data.MediaOps
import sleepless_index.haze.data.formatSize
import sleepless_index.haze.data.thumbRequest
import sleepless_index.haze.ui.theme.GIcons

/** Album grid shown for Copy and Move: pick an album, or create a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumPickerSheet(
    vm: GalleryViewModel,
    items: List<MediaItem>,
    move: Boolean,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val albums by vm.albums.collectAsStateWithLifecycle()
    val all by vm.items.collectAsStateWithLifecycle()
    val roots = remember { MediaOps.storageRoots(context) }
    var creating by remember { mutableStateOf(false) }
    var confirmAlbum by remember { mutableStateOf<Album?>(null) }

    val sizes = remember(all) {
        val m = HashMap<Long, Long>()
        for (it in all) m[it.bucketId] = (m[it.bucketId] ?: 0L) + it.sizeBytes
        m
    }
    // Which albums live on removable storage (shows the SD card badge).
    val onSd by produceState(emptySet<Long>(), albums) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            albums.filter { a ->
                MediaOps.pathOf(context, a.cover.uri)?.startsWith(roots.first().second) == false
            }.map { it.bucketId }.toSet()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.background,
        contentColor = cs.onBackground,
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Text(
                text = (if (move) "Move " else "Copy ") + items.size + " to…",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            )
            if (move && !MediaOps.hasAllFiles()) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        "Moving files needs All files access. Copying, deleting and everything else works without it.",
                        fontSize = 14.sp, color = cs.onSurfaceVariant,
                    )
                    Text(
                        "Allow All files access",
                        fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = cs.onPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(cs.primary)
                            .bounceClick(onClick = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                            .setData(android.net.Uri.parse("package:" + context.packageName))
                                    )
                                }
                                onDismiss()
                            })
                            .padding(horizontal = 28.dp, vertical = 13.dp),
                    )
                }
                return@Column
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "new") {
                    Column(Modifier.fillMaxWidth().bounceClick(onClick = { creating = true }), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .border(BorderStroke(1.dp, cs.outline), RoundedCornerShape(20.dp))
                                .background(cs.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.size(64.dp).border(BorderStroke(2.5.dp, cs.onSurfaceVariant), androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(GIcons.Plus, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(32.dp))
                            }
                        }
                        Text("New album", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
                items(albums, key = { it.bucketId }) { album ->
                    AlbumPickTile(
                        album = album,
                        here = items.isNotEmpty() && items.all { it.bucketId == album.bucketId },
                        bytes = sizes[album.bucketId] ?: 0L,
                        sd = album.bucketId in onSd,
                        onClick = {
                            if (move) {
                                confirmAlbum = album
                            } else {
                                vm.transferToAlbum(items, album, false)
                                onDismiss()
                            }
                        },
                    )
                }
            }
        }
    }

    confirmAlbum?.let { a ->
        MoveConfirm(
            items = items,
            albumName = a.name,
            onConfirm = { confirmAlbum = null; vm.transferToAlbum(items, a, true); onDismiss() },
            onDismiss = { confirmAlbum = null },
        )
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        var rootIdx by remember { mutableStateOf(0) }
        AlertDialog(
            onDismissRequest = { creating = false },
            containerColor = cs.surface,
            title = { Text("New album", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        singleLine = true,
                        label = { Text("Album name") },
                    )
                    if (roots.size > 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            roots.forEachIndexed { i, r -> PickChip(r.first, rootIdx == i) { rootIdx = i } }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        vm.transferToNew(items, roots[rootIdx].second, name, move)
                        creating = false
                        onDismiss()
                    },
                ) { Text(if (move) "Create & move" else "Create & copy") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AlbumPickTile(album: Album, here: Boolean, bytes: Long, sd: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxWidth().alpha(if (here) 0.35f else 1f).then(if (here) Modifier else Modifier.bounceClick(onClick = onClick)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(cs.surfaceVariant)) {
            AsyncImage(
                model = remember(album.cover.uri) { thumbRequest(ctx, album.cover.uri, 400, album.cover.isVideo) },
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (sd) {
                Icon(
                    GIcons.SdCard, "SD card", tint = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(22.dp),
                )
            }
        }
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(album.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (here) "Already here" else "%,d %s (%s)".format(album.count, if (album.count == 1) "item" else "items", formatSize(bytes)),
                fontSize = 12.sp, color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PickChip(label: String, active: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) cs.primary else cs.surfaceVariant)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (active) cs.onPrimary else cs.onBackground)
    }
}
