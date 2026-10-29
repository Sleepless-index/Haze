package sleepless_index.haze.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

/**
 * Progress for long jobs (moving, copying, deleting...).
 * Most jobs show a small pill above the bottom bar and leave the app usable; it only appears once a
 * job has run for a moment so quick ones don't flash. Jobs that must not be touched (setting a
 * wallpaper) use a blocking card in the middle instead.
 */
@Composable
fun BusyOverlay(vm: GalleryViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var last by remember { mutableStateOf<Busy?>(null) }
    if (busy != null) last = busy
    val cs = MaterialTheme.colorScheme

    // Wait a beat before showing anything.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(busy != null) {
        if (busy != null) {
            delay(300)
            shown = true
        } else {
            shown = false
        }
    }
    val visible = busy != null && shown
    val blocking = last?.blocking == true

    if (blocking) {
        BackHandler(enabled = busy != null) {}
        AnimatedVisibility(visible = busy != null, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .widthIn(min = 200.dp, max = 280.dp)
                        .background(cs.surface, RoundedCornerShape(26.dp))
                        .padding(horizontal = 28.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp), color = cs.primary, trackColor = cs.surfaceVariant, strokeWidth = 4.dp,
                    )
                    Text(
                        (last?.text ?: "") + "…",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground, textAlign = TextAlign.Center,
                    )
                }
            }
        }
        return
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(180)) { it / 2 } + fadeOut(tween(160)),
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = navBottom + 96.dp),
        ) {
            val p = last?.progress
            val shape = RoundedCornerShape(32.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .shadow(12.dp, shape)
                    .clip(shape)
                    .background(cs.surface)
                    .border(1.dp, cs.outline, shape)
                    .padding(start = 14.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (p != null) {
                    val smooth by animateFloatAsState(p.coerceIn(0f, 1f), tween(250), label = "busyRing")
                    CircularProgressIndicator(
                        progress = { smooth },
                        modifier = Modifier.size(34.dp), color = cs.primary, trackColor = cs.surfaceVariant, strokeWidth = 4.dp,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(34.dp), color = cs.primary, trackColor = cs.surfaceVariant, strokeWidth = 4.dp,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(last?.text ?: "", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = cs.onBackground, maxLines = 1)
                    last?.detail?.let {
                        Text(it, fontSize = 12.5.sp, color = cs.onSurfaceVariant, maxLines = 1)
                    }
                    if (last?.detail == null && p != null) {
                        Text("${(p * 100).toInt()}%", fontSize = 12.5.sp, color = cs.onSurfaceVariant)
                    }
                }
                if (last?.cancellable == true) {
                    Box(
                        Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(cs.surfaceVariant)
                            .bounceClick(onClick = { vm.cancelBusy() })
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Cancel", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground)
                    }
                } else {
                    Box(Modifier.size(6.dp))
                }
            }
        }
    }
}
