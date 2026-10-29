package sleepless_index.haze.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sleepless_index.haze.data.MediaItem
import sleepless_index.haze.data.WallpaperOps
import sleepless_index.haze.ui.theme.GIcons

/** Where the wallpaper should go: home screen, lock screen or both. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperSheet(vm: GalleryViewModel, item: MediaItem, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surface,
        contentColor = cs.onBackground,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Set as wallpaper", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp))
            Text(
                "The photo is cropped from the centre to fit your screen.",
                fontSize = 13.5.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
            WallpaperRow(GIcons.Image, "Home screen") { onDismiss(); vm.setWallpaper(item, WallpaperOps.HOME) }
            WallpaperRow(GIcons.Lock, "Lock screen") { onDismiss(); vm.setWallpaper(item, WallpaperOps.LOCK) }
            WallpaperRow(GIcons.Image, "Home and lock screen") { onDismiss(); vm.setWallpaper(item, WallpaperOps.BOTH) }
        }
    }
}

@Composable
private fun WallpaperRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(cs.surfaceVariant)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = cs.onBackground, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground)
    }
}
