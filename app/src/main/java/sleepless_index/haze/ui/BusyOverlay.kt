package sleepless_index.haze.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Spinner over the whole app while something long runs (moving, copying, deleting...).
 * Blocks taps and the back button so a half-finished job can't be interrupted.
 */
@Composable
fun BusyOverlay(vm: GalleryViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var last by remember { mutableStateOf<Busy?>(null) }
    if (busy != null) last = busy
    val cs = MaterialTheme.colorScheme

    BackHandler(enabled = busy != null) {}
    AnimatedVisibility(visible = busy != null, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = busy != null,
                enter = scaleIn(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.85f) + fadeIn(),
                exit = scaleOut(targetScale = 0.9f) + fadeOut(),
            ) {
                Column(
                    Modifier
                        .widthIn(min = 200.dp, max = 280.dp)
                        .background(cs.surface, RoundedCornerShape(26.dp))
                        .padding(horizontal = 28.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val p = last?.progress
                    if (p != null) {
                        CircularProgressIndicator(
                            progress = { p.coerceIn(0f, 1f) },
                            modifier = Modifier.size(44.dp),
                            color = cs.primary,
                            trackColor = cs.surfaceVariant,
                            strokeWidth = 4.dp,
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(44.dp),
                            color = cs.primary,
                            trackColor = cs.surfaceVariant,
                            strokeWidth = 4.dp,
                        )
                    }
                    Text(
                        (last?.text ?: "") + if (p != null) "" else "…",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground, textAlign = TextAlign.Center,
                    )
                    if (p != null) {
                        Text("${(p * 100).toInt()}%", fontSize = 13.sp, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
