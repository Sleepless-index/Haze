package sleepless_index.haze.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import sleepless_index.haze.data.thumbRequest
import sleepless_index.haze.ui.theme.GIcons

/** Pick which photo or video stands for an album on the Albums tab. */
@Composable
fun CoverPickerScreen(vm: GalleryViewModel, bucketId: Long, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val all by vm.allItems.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val hiddenAlbums by vm.hiddenAlbums.collectAsStateWithLifecycle()
    val list = remember(all, bucketId) { all.filter { it.bucketId == bucketId } }
    val album = (albums + hiddenAlbums).firstOrNull { it.bucketId == bucketId }
    var picked by remember(bucketId) { mutableLongStateOf(album?.cover?.id ?: -1L) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    BackHandler { onBack() }

    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(
                title = "Choose cover",
                subtitle = album?.name ?: "",
                leading = {
                    GIconButton(GIcons.Back, "Back", onClick = onBack)
                    Spacer(Modifier.size(12.dp))
                },
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 3.dp, end = 3.dp, top = 8.dp, bottom = 110.dp + navBottom),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(list, key = { it.id }) { m ->
                    val on = m.id == picked
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(cs.surfaceVariant)
                            .then(if (on) Modifier.border(3.dp, cs.primary, RoundedCornerShape(8.dp)) else Modifier)
                            .clickable { picked = m.id },
                    ) {
                        AsyncImage(
                            model = remember(m.uri) { thumbRequest(context, m.uri, 360, m.isVideo) },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (on) {
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).clip(RoundedCornerShape(11.dp)).background(cs.primary),
                                contentAlignment = Alignment.Center,
                            ) { Icon(GIcons.Check, null, tint = cs.onPrimary, modifier = Modifier.size(14.dp)) }
                        }
                    }
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(cs.background)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp + navBottom),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(26.dp)).background(cs.surfaceVariant).bounceClick(onClick = {
                    album?.let { vm.setCover(it, null) }
                    onBack()
                }),
                contentAlignment = Alignment.Center,
            ) { Text("Reset to default", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground) }
            Box(
                Modifier.weight(1.3f).height(52.dp).clip(RoundedCornerShape(26.dp)).background(cs.primary).bounceClick(onClick = {
                    album?.let { vm.setCover(it, picked) }
                    onBack()
                }),
                contentAlignment = Alignment.Center,
            ) { Text("Use as cover", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = cs.onPrimary) }
        }
    }
}
