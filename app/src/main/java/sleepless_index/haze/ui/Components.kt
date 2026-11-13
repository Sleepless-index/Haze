package sleepless_index.haze.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sleepless_index.haze.ui.theme.GIcons

/** Click with a little springy press-in, used on every button in the app. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.bounceClick(
    enabled: Boolean = true,
    role: Role = Role.Button,
    onLongClick: (() -> Unit)? = null,
    gentle: Boolean = false,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        targetValue = if (pressed) (if (gentle) 0.95f else 0.90f) else 1f,
        animationSpec = if (gentle) spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    this
        .graphicsLayer {
            scaleX = s
            scaleY = s
        }
        .then(
            if (onLongClick == null) {
                Modifier.clickable(
                    interactionSource = source,
                    indication = null,
                    enabled = enabled,
                    role = role,
                    onClick = onClick,
                )
            } else {
                Modifier.combinedClickable(
                    interactionSource = source,
                    indication = null,
                    enabled = enabled,
                    role = role,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
            },
        )
}

@Composable
fun GIconButton(
    icon: ImageVector,
    desc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    flat: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (flat) Color.Transparent else container)
            .bounceClick(onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 25.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            trailing()
        }
    }
}

@Composable
fun SelectionHeader(
    count: Int,
    onSelectAll: () -> Unit,
    onCancel: () -> Unit,
    allSelected: Boolean = false,
    onDeselectAll: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GIconButton(icon = GIcons.Close, desc = "Cancel selection", onClick = onCancel)
        Text(
            text = if (count == 0) "Select items" else "$count selected",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        )
        Text(
            text = if (allSelected) "Deselect all" else "Select all",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .bounceClick(onClick = { if (allSelected) onDeselectAll() else onSelectAll() })
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/** Floating rounded pill used for the tab bar and the selection action bar. */
@Composable
fun PillBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            content = content,
        )
    }
}

@Composable
fun PillItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val iconTint by animateColorAsState(
        if (selected) cs.primary else cs.onSurfaceVariant, tween(220), label = "pillIcon"
    )
    val labelColor by animateColorAsState(
        if (selected) cs.onBackground else cs.onSurfaceVariant, tween(220), label = "pillLabel"
    )
    val glow by animateFloatAsState(if (selected) 0.16f else 0f, tween(260), label = "pillGlow")
    val lift by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pillLift",
    )
    Box(
        modifier = modifier
            .height(56.dp)
            .padding(horizontal = 3.dp, vertical = 6.dp)
            .bounceClick(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 88.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cs.primary.copy(alpha = glow)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .size(22.dp)
                    .scale(lift),
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = labelColor,
                lineHeight = 12.sp,
                modifier = Modifier.offset(y = (-2).dp),
            )
        }
    }
}

data class NavTab(val route: String, val label: String, val icon: ImageVector)

val NavTabs = listOf(
    NavTab("photos", "Photos", GIcons.Photos),
    NavTab("albums", "Albums", GIcons.Albums),
    NavTab("search", "Search", GIcons.Search),
)

@Composable
fun NavPill(current: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    PillBar(modifier) {
        NavTabs.forEach { t ->
            PillItem(
                icon = t.icon,
                label = t.label,
                selected = t.route == current,
                onClick = { onSelect(t.route) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun BottomFade(modifier: Modifier = Modifier, height: Dp = 96.dp) {
    val bg = MaterialTheme.colorScheme.background
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(Brush.verticalGradient(listOf(Color.Transparent, bg.copy(alpha = 0.45f), bg.copy(alpha = 0.78f))))
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.7.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp),
    )
}

/** Proper red for favorite hearts. */
val HeartRed = androidx.compose.ui.graphics.Color(0xFFFF3B47)

/** Menu row sized to its text instead of Material's wide 112dp minimum. */
@Composable
fun CompactMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) { text() }
}


/** A soft moving sheen for thumbnails that are still loading. Only apply it until the image is in. */
fun Modifier.shimmer(base: Color = Color(0xFF1A1A1A)): Modifier = composed {
    val t by androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1300, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "shimmerT",
    )
    val hi = androidx.compose.ui.graphics.lerp(base, Color.White, 0.10f)
    this.drawBehind {
        val w = size.width.coerceAtLeast(1f)
        val x = -w + 2f * w * t
        drawRect(base)
        drawRect(
            androidx.compose.ui.graphics.Brush.linearGradient(
                colors = listOf(Color.Transparent, hi, Color.Transparent),
                start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset(x + w, size.height),
            ),
        )
    }
}
