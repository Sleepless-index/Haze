package sleepless_index.haze.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import sleepless_index.haze.data.MediaItem
import sleepless_index.haze.data.formatSize
import sleepless_index.haze.data.thumbRequest
import sleepless_index.haze.ui.theme.GIcons

/** The bottom sheet every "are you sure?" in the app uses, so the answers read the same everywhere. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmSheet(
    title: String,
    subtitle: String,
    items: List<MediaItem>,
    notes: List<Pair<ImageVector, String>>,
    confirmLabel: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surface,
        contentColor = cs.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (items.isNotEmpty()) {
                    val shown = items.take(2)
                    Box(Modifier.size(width = if (shown.size > 1) 56.dp else 40.dp, height = 44.dp)) {
                        shown.forEachIndexed { i, it ->
                            AsyncImage(
                                model = remember(it.uri) { thumbRequest(context, it.uri, 96, it.isVideo) },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .padding(start = (i * 20).dp, top = 4.dp)
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(cs.surfaceVariant)
                                    .border(2.dp, cs.surface, RoundedCornerShape(10.dp)),
                            )
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground)
                    if (subtitle.isNotBlank()) Text(subtitle, fontSize = 14.sp, color = cs.onSurfaceVariant)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceVariant).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for ((icon, text) in notes) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Icon(icon, null, tint = cs.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp).size(18.dp))
                        Text(text, fontSize = 14.sp, lineHeight = 20.sp, color = cs.onBackground)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(cs.surfaceVariant)
                        .bounceClick(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground) }
                Box(
                    modifier = Modifier
                        .weight(1.4f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(if (destructive) cs.error else cs.primary)
                        .bounceClick(onClick = onConfirm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        confirmLabel, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = if (destructive) cs.onError else cs.onPrimary,
                    )
                }
            }
        }
    }
}

private fun subject(items: List<MediaItem>): String = when {
    items.size == 1 && items[0].isVideo -> "this video"
    items.size == 1 -> "this photo"
    else -> "${items.size} items"
}

private fun bytesOf(items: List<MediaItem>): String = formatSize(items.sumOf { it.sizeBytes })

/** Delete: to Trash on Android 11+, permanent before that. */
@Composable
fun TrashConfirm(items: List<MediaItem>, trashDays: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (Build.VERSION.SDK_INT >= 30) {
        ConfirmSheet(
            title = "Move ${subject(items)} to Trash?",
            subtitle = bytesOf(items),
            items = items,
            notes = listOf(
                GIcons.Trash to "They stay in Trash for $trashDays days. You can restore them any time before that.",
            ),
            confirmLabel = "Move to Trash",
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    } else {
        ConfirmSheet(
            title = "Delete ${subject(items)}?",
            subtitle = bytesOf(items),
            items = items,
            notes = listOf(GIcons.Trash to "This version of Android has no Trash, so they are deleted for good."),
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun VaultConfirm(items: List<MediaItem>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmSheet(
        title = "Move ${subject(items)} to the Vault?",
        subtitle = bytesOf(items),
        items = items,
        notes = listOf(
            GIcons.Lock to "They're hidden from your gallery and need your vault lock to open.",
            GIcons.Check to "The originals are removed after the move. Android may ask you to confirm once.",
        ),
        confirmLabel = "Move to Vault",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
fun MoveConfirm(items: List<MediaItem>, albumName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmSheet(
        title = "Move ${subject(items)} to $albumName?",
        subtitle = bytesOf(items),
        items = items,
        notes = listOf(
            GIcons.Move to "They leave their current album and are saved in $albumName.",
        ),
        confirmLabel = "Move",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** Permanent delete, from Trash. [everything] is the "Delete all" variant. */
@Composable
fun DeleteForeverConfirm(items: List<MediaItem>, everything: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmSheet(
        title = if (everything) "Empty Trash?" else "Delete ${subject(items)} forever?",
        subtitle = if (everything) "${items.size} items · ${bytesOf(items)}" else bytesOf(items),
        items = items,
        notes = listOf(
            GIcons.Trash to "This can't be undone. Deleted items can't be restored.",
        ),
        confirmLabel = "Delete forever",
        destructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
