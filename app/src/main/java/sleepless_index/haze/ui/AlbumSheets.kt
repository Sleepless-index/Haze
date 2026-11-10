package sleepless_index.haze.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import sleepless_index.haze.data.Album
import sleepless_index.haze.data.formatSize
import sleepless_index.haze.data.thumbRequest
import sleepless_index.haze.ui.theme.GIcons

@Composable
private fun ActionTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .height(84.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(cs.surfaceVariant)
            .bounceClick(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, null, tint = cs.onBackground, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground, maxLines = 1)
    }
}

/** What you can do with an album: pin, change its cover, rename, hide, delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumActionsSheet(
    album: Album,
    bytes: Long?,
    pinned: Boolean,
    hidden: Boolean,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onCover: () -> Unit,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
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
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AsyncImage(
                    model = remember(album.cover.uri) { thumbRequest(context, album.cover.uri, 160, album.cover.isVideo) },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(cs.surfaceVariant),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(album.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "%,d items".format(album.count) + (if (bytes != null) "  ·  ${formatSize(bytes)}" else ""),
                        fontSize = 13.sp, color = cs.onSurfaceVariant,
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!hidden) ActionTile(GIcons.Pin, if (pinned) "Unpin" else "Pin", Modifier.weight(1f), onPin)
                ActionTile(GIcons.Image, "Cover", Modifier.weight(1f), onCover)
                ActionTile(GIcons.Edit, "Rename", Modifier.weight(1f), onRename)
                ActionTile(GIcons.EyeOff, if (hidden) "Show" else "Hide", Modifier.weight(1f), onHide)
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(HeartRed.copy(alpha = 0.12f))
                    .bounceClick(onClick = onDelete),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                Icon(GIcons.Trash, null, tint = HeartRed, modifier = Modifier.size(20.dp))
                Text("Delete album", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = HeartRed)
            }
        }
    }
}

/** Hiding hides the album from other apps too, so this only asks for a yes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HideAlbumSheet(album: Album, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surface,
        contentColor = cs.onBackground,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                "Hide ${album.name}?",
                fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground,
                modifier = Modifier.padding(horizontal = 4.dp),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(26.dp)).background(cs.surfaceVariant).bounceClick(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground) }
                Box(
                    Modifier.weight(1.3f).height(52.dp).clip(RoundedCornerShape(26.dp)).background(cs.primary).bounceClick(onClick = onConfirm),
                    contentAlignment = Alignment.Center,
                ) { Text("Hide album", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = cs.onPrimary) }
            }
        }
    }
}
