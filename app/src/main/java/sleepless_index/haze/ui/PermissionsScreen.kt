package sleepless_index.haze.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sleepless_index.haze.ui.theme.GIcons

/** What the app has been allowed to do. All three are required. */
data class PermState(
    val images: Boolean,
    val videos: Boolean,
    val allFiles: Boolean,
) {
    val media: Boolean get() = images && videos
    val all: Boolean get() = media && allFiles
}

/**
 * Explains the single permission the app needs and whether it has been granted.
 * First-run gate when [onBack] is null; reachable from Settings afterwards.
 */
@Composable
fun PermissionsScreen(
    state: PermState,
    onGrantAll: () -> Unit,
    onRequestMedia: () -> Unit,
    onRequestAllFiles: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(cs.primary.copy(alpha = 0.16f), cs.background, cs.background)))
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(48.dp)) {
                if (onBack != null) {
                    GIconButton(GIcons.Back, "Back", onClick = onBack, modifier = Modifier.align(Alignment.CenterStart))
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text(
                    "Permissions",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = cs.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Gallery needs these to show and manage your media. Tap one to learn why.",
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                )
                PermCard(
                    icon = GIcons.Image,
                    title = "Read media images",
                    optional = false,
                    granted = state.images,
                    why = "Needed to show the photos stored on your phone. Nothing is uploaded anywhere.",
                    onAllow = onRequestMedia,
                )
                PermCard(
                    icon = GIcons.VideoAlbum,
                    title = "Read media videos",
                    optional = false,
                    granted = state.videos,
                    why = "Needed to show and play your videos.",
                    onAllow = onRequestMedia,
                )
                PermCard(
                    icon = GIcons.Move,
                    title = "Access to all files",
                    optional = false,
                    granted = state.allFiles,
                    why = "Lets Gallery move and rename files and albums (also on the SD card), read a photo's location, and delete without extra prompts.",
                    onAllow = onRequestAllFiles,
                )
                Box(Modifier.size(8.dp))
            }
            Text(
                if (state.all) "All set" else "Allow access",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = cs.onPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(cs.primary)
                    .bounceClick(onClick = { if (!state.all) onGrantAll() else onBack?.invoke() })
                    .padding(vertical = 11.dp),
            )
        }
    }
}

@Composable
private fun PermCard(
    icon: ImageVector,
    title: String,
    optional: Boolean,
    granted: Boolean,
    why: String,
    onAllow: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(220), label = "chevron")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cs.surface.copy(alpha = 0.9f))
            .bounceClick(onClick = { open = !open })
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .animateContentSize(tween(220)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = cs.primary, modifier = Modifier.size(18.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = cs.onBackground)
                Text(
                    (if (optional) "Optional · " else "") + if (granted) "Granted" else "Not granted",
                    fontSize = 12.sp,
                    color = if (granted) cs.primary else cs.onSurfaceVariant,
                )
            }
            Icon(GIcons.ChevronDown, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp).rotate(turn))
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(tween(220)) + fadeIn(tween(220)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
        ) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(why, fontSize = 12.sp, color = cs.onSurfaceVariant)
                if (!granted) {
                    Text(
                        "Allow",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = cs.onPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(cs.primary)
                            .bounceClick(onClick = onAllow)
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
