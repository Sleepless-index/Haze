package sleepless_index.haze.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sleepless_index.haze.data.UpdateState

private fun mb(bytes: Long) = "%.1f".format(bytes / 1048576.0)

@Composable
private fun Pill(text: String, accent: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (accent) cs.primary else cs.surfaceVariant)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (accent) cs.onPrimary else cs.onBackground,
        )
    }
}

@Composable
private fun UpdateRow(title: String, sub: String?, trailing: @Composable () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground)
            if (sub != null) Text(sub, fontSize = 12.5.sp, color = cs.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun UpdateDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline)
    )
}

@Composable
fun UpdatesSection(vm: GalleryViewModel) {
    val cs = MaterialTheme.colorScheme
    val updater = vm.updater
    val state by updater.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val current = "Haze ${updater.currentVersion}"

    SectionLabel("Updates")
    CardGroup(Modifier.animateContentSize(tween(260))) {
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "updates",
        ) { s ->
            Column {
                when (s) {
                    UpdateState.Idle -> UpdateRow(current, "Check for a newer version") {
                        Pill("Check", false) { updater.check() }
                    }

                    UpdateState.Checking -> UpdateRow(current, "Checking…") {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = cs.primary,
                            trackColor = Color.Transparent,
                        )
                    }

                    UpdateState.UpToDate -> UpdateRow(current, "Up to date") {
                        Pill("Check", false) { updater.check() }
                    }

                    is UpdateState.Failed -> UpdateRow(current, s.message) {
                        Pill("Try again", false) { updater.check() }
                    }

                    is UpdateState.Available -> {
                        val sub = listOf(
                            if (s.info.size > 0) "${mb(s.info.size)} MB" else "",
                            s.info.date,
                        ).filter { it.isNotEmpty() }.joinToString(" · ")
                        UpdateRow("Haze ${s.info.version}", sub.ifEmpty { null }) {
                            Box(
                                Modifier
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(cs.primary.copy(alpha = 0.16f))
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("New", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cs.primary)
                            }
                        }
                        if (s.info.notes.isNotEmpty()) {
                            UpdateDivider()
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                s.info.notes.forEach { n ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(
                                            Modifier
                                                .padding(top = 8.dp)
                                                .size(5.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(cs.primary)
                                        )
                                        Text(n, fontSize = 14.sp, lineHeight = 21.sp, color = cs.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        UpdateDivider()
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(cs.primary)
                                    .bounceClick(onClick = { updater.download() }),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Download", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = cs.onPrimary)
                            }
                        }
                    }

                    is UpdateState.Downloading -> {
                        val frac = if (s.total > 0) (s.done.toFloat() / s.total).coerceIn(0f, 1f) else 0f
                        val shown by animateFloatAsState(frac, tween(160), label = "dl")
                        UpdateRow(
                            "Downloading ${s.info.version}",
                            if (s.total > 0) "${mb(s.done)} of ${mb(s.total)} MB" else "${mb(s.done)} MB",
                        ) {
                            Text("${(frac * 100).toInt()}%", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = cs.primary)
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(cs.surfaceVariant)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(shown)
                                    .height(4.dp)
                                    .background(cs.primary)
                            )
                        }
                        UpdateDivider()
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .bounceClick(onClick = { updater.cancel() }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = cs.onBackground)
                        }
                    }

                    is UpdateState.Ready -> UpdateRow("Haze ${s.info.version} is ready", "Downloaded") {
                        Pill("Install", true) {
                            if (!updater.install(context)) {
                                Toast.makeText(
                                    context,
                                    "Allow Haze to install apps, then tap Install again",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    }
                }
            }
        }
    }
}
