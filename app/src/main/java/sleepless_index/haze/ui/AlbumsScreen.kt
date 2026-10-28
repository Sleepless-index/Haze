package sleepless_index.haze.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import sleepless_index.haze.ui.theme.GIcons

/** Fades + slides a card in once, staggered by its position. */
@Composable
private fun StaggerIn(index: Int, content: @Composable () -> Unit) {
    var shown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val delay = (index.coerceAtMost(10)) * 45
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(320, delayMillis = delay)) +
            slideInVertically(tween(360, delayMillis = delay)) { it / 6 },
    ) {
        content()
    }
}

@Composable
private fun SmartTile(icon: ImageVector, title: String, count: String, tint: Color, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(cs.surface)
            .border(BorderStroke(1.dp, cs.outline), shape)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = cs.onBackground)
            Text(count, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun AlbumTile(
    name: String,
    count: Int,
    cover: sleepless_index.haze.data.MediaItem,
    bytes: Long? = null,
    pinned: Boolean = false,
    sd: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onLongClick = onLongClick, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val request = remember(cover.uri) {
            sleepless_index.haze.data.thumbRequest(ctx, cover.uri, 480, cover.isVideo)
        }
        Box {
            AsyncImage(
                model = request,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(cs.surfaceVariant),
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = pinned,
                modifier = Modifier.align(Alignment.TopEnd),
                enter = androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(0.55f, 380f)) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut(),
            ) {
                Box(
                    Modifier
                        .padding(8.dp)
                        .size(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(GIcons.Pin, contentDescription = "Pinned", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = sd,
                modifier = Modifier.align(Alignment.BottomEnd),
                enter = androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(0.55f, 380f)) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut(),
            ) {
                Box(
                    Modifier
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(GIcons.SdCard, contentDescription = "On SD card", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = cs.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (bytes != null) "%,d items · %s".format(count, sleepless_index.haze.data.formatSize(bytes)) else "%,d items".format(count),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, tint: Color? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val color = tint ?: cs.onBackground
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .bounceClick(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun AlbumActionsDialog(
    album: sleepless_index.haze.data.Album,
    pinned: Boolean,
    hidden: Boolean,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        title = { Text(album.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                if (!hidden) ActionRow(GIcons.Pin, if (pinned) "Unpin from top" else "Pin to top", onClick = onPin)
                ActionRow(GIcons.Edit, "Rename", onClick = onRename)
                ActionRow(GIcons.EyeOff, if (hidden) "Show album" else "Hide album", onClick = onHide)
                ActionRow(GIcons.Trash, "Delete album", tint = HeartRed, onClick = onDelete)
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun AlbumsScreen(
    vm: GalleryViewModel,
    onOpenAlbum: (Long) -> Unit,
    onOpenSmart: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenCleanup: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val albums by vm.albums.collectAsStateWithLifecycle()
    val favorites by vm.favoriteItems.collectAsStateWithLifecycle()
    val allItems by vm.items.collectAsStateWithLifecycle()
    val videos by vm.videos.collectAsStateWithLifecycle()
    val trashed by vm.trashed.collectAsStateWithLifecycle()
    val cleanup by vm.cleanup.collectAsStateWithLifecycle()
    val sizes = remember(allItems) {
        val m = HashMap<Long, Long>()
        for (it in allItems) m[it.bucketId] = (m[it.bucketId] ?: 0L) + it.sizeBytes
        m
    }
    val hiddenAlbums by vm.hiddenAlbums.collectAsStateWithLifecycle()
    val sdCtx = LocalContext.current
    val onSd by produceState(emptySet<Long>(), albums, hiddenAlbums) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val internal = sleepless_index.haze.data.MediaOps.storageRoots(sdCtx).first().second
            val out = HashSet<Long>()
            for (a in albums + hiddenAlbums) {
                val path = sleepless_index.haze.data.MediaOps.pathOf(sdCtx, a.cover.uri)
                if (path != null && !path.startsWith(internal)) out += a.bucketId
            }
            out
        }
    }
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var actionAlbum by remember { mutableStateOf<sleepless_index.haze.data.Album?>(null) }
    var renaming by remember { mutableStateOf<sleepless_index.haze.data.Album?>(null) }
    var deleting by remember { mutableStateOf<sleepless_index.haze.data.Album?>(null) }
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(Unit) { vm.loadTrash() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = "Albums",
            subtitle = "${albums.size} folders",
            trailing = {
                Box {
                    GIconButton(GIcons.More, "More options", onClick = { menu = true }, flat = true)
                    androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        CompactMenuItem(
                            text = { Text("Refresh") },
                            onClick = {
                                menu = false
                                vm.refresh()
                            },
                        )
                        if (hiddenAlbums.isNotEmpty()) {
                            CompactMenuItem(
                                text = { Text(if (showHidden) "Hide hidden albums" else "Hidden albums (${hiddenAlbums.size})") },
                                onClick = {
                                    menu = false
                                    showHidden = !showHidden
                                },
                            )
                        }
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp + navBottom),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (Build.VERSION.SDK_INT >= 30) {
                item(key = "smart_trash") {
                    StaggerIn(2) {
                        SmartTile(GIcons.Trash, "Trash", "%,d".format(trashed.size), cs.onBackground) {
                            onOpenSmart("trash")
                        }
                    }
                }
            }
            item(key = "smart_vault") {
                StaggerIn(3) {
                    SmartTile(GIcons.Lock, "Vault", "", cs.onBackground, onOpenVault)
                }
            }
            item(key = "smart_cleanup", span = { GridItemSpan(maxLineSpan) }) {
                StaggerIn(3) {
                    SmartTile(
                        GIcons.Sparkle, "Clean up",
                        if (cleanup.scanned && cleanup.freeableBytes > 0) "${sleepless_index.haze.data.formatSize(cleanup.freeableBytes)} to free" else "",
                        cs.onBackground, onOpenCleanup,
                    )
                }
            }
            item(key = "label_all", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "All albums",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onBackground,
                    modifier = Modifier.padding(start = 4.dp, top = 10.dp),
                )
            }
            if (favorites.isNotEmpty()) {
                item(key = "album_favorites") {
                    StaggerIn(2) {
                        AlbumTile(
                            name = "Favorites",
                            count = favorites.size,
                            bytes = favorites.sumOf { it.sizeBytes },
                            cover = favorites.first(),
                            onClick = { onOpenSmart("favorites") },
                        )
                    }
                }
            }
            if (videos.isNotEmpty()) {
                item(key = "album_videos") {
                    StaggerIn(3) {
                        AlbumTile(
                            name = "Videos",
                            count = videos.size,
                            bytes = videos.sumOf { it.sizeBytes },
                            cover = videos.first(),
                            onClick = { onOpenSmart("videos") },
                        )
                    }
                }
            }
            itemsIndexed(albums, key = { _, a -> "album_${a.bucketId}" }) { index, album ->
                StaggerIn(index + 4) {
                    AlbumTile(
                        name = album.name,
                        count = album.count,
                        cover = album.cover,
                        bytes = sizes[album.bucketId],
                        pinned = album.bucketId in prefs.pinned,
                        sd = album.bucketId in onSd,
                        onLongClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            actionAlbum = album
                        },
                        onClick = { onOpenAlbum(album.bucketId) },
                    )
                }
            }
            if (showHidden && hiddenAlbums.isNotEmpty()) {
                item(key = "label_hidden", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "Hidden albums",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = cs.onBackground,
                        modifier = Modifier.padding(start = 4.dp, top = 14.dp),
                    )
                }
                itemsIndexed(hiddenAlbums, key = { _, a -> "hidden_${a.bucketId}" }) { _, album ->
                    Box(Modifier.alpha(0.6f)) {
                        AlbumTile(
                            name = album.name,
                            count = album.count,
                            cover = album.cover,
                            sd = album.bucketId in onSd,
                            onLongClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                actionAlbum = album
                            },
                            onClick = { onOpenAlbum(album.bucketId) },
                        )
                    }
                }
            }
        }
    }

    actionAlbum?.let { a ->
        val isHidden = a.bucketId in prefs.hidden
        AlbumActionsDialog(
            album = a,
            pinned = a.bucketId in prefs.pinned,
            hidden = isHidden,
            onDismiss = { actionAlbum = null },
            onPin = { vm.togglePin(a); actionAlbum = null },
            onRename = { renaming = a; actionAlbum = null },
            onHide = { vm.setHidden(a, !isHidden); actionAlbum = null },
            onDelete = { deleting = a; actionAlbum = null },
        )
    }
    renaming?.let { a ->
        var text by remember(a.bucketId) { mutableStateOf(a.name) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = cs.surface,
            title = { Text("Rename album", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(60) },
                    singleLine = true,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = text.isNotBlank(),
                    onClick = { vm.renameAlbum(a, text); renaming = null },
                ) { Text("Rename") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { a ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = cs.surface,
            title = { Text("Delete \"${a.name}\"?", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = { Text("All ${a.count} items in this album will be moved to the trash.", color = cs.onSurfaceVariant) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { vm.deleteAlbum(a); deleting = null },
                ) { Text("Delete", color = HeartRed) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}
