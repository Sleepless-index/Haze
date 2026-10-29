package sleepless_index.haze.ui

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.Canvas
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sleepless_index.haze.data.LayoutMode
import sleepless_index.haze.data.Prefs
import sleepless_index.haze.data.ThemeMode
import sleepless_index.haze.ui.theme.GIcons

private val Presets = listOf(
    "Default" to Prefs.ACCENT_BLUE,
)

@Composable
private fun LayoutCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val border by animateColorAsState(if (selected) cs.primary else cs.outline, tween(220), label = "cardBorder")
    val width by animateDpAsState(if (selected) 2.dp else 1.dp, tween(220), label = "cardBorderW")
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(cs.surface)
            .border(BorderStroke(width, border), shape)
            .bounceClick(role = Role.RadioButton, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(84.dp)) { preview() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = cs.onBackground,
                modifier = Modifier.weight(1f),
            )
            val pop by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "cardCheck",
            )
            Icon(
                GIcons.Check, contentDescription = null, tint = cs.primary,
                modifier = Modifier
                    .size(18.dp)
                    .scale(pop),
            )
        }
    }
}

@Composable
private fun GridPreview() {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(2) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) {
                    Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(3.dp)).background(cs.outline))
                }
            }
        }
    }
}

@Composable
private fun MasonryPreview() {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.weight(3f).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(cs.outline))
            Box(Modifier.weight(2f).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(cs.outline))
        }
        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.weight(2f).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(cs.outline))
            Box(Modifier.weight(3f).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(cs.outline))
        }
    }
}

@Composable
private fun CardGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(cs.surface)
            .border(BorderStroke(1.dp, cs.outline), shape),
    ) { content() }
}

@Composable
private fun SegmentRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground, modifier = Modifier.weight(1f))
        Row(
            Modifier.clip(shape).background(cs.background).border(BorderStroke(1.dp, cs.outline), shape).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                val bg by animateColorAsState(if (on) cs.primary else Color.Transparent, tween(220), label = "segBg")
                val fg by animateColorAsState(if (on) cs.onPrimary else cs.onSurfaceVariant, tween(220), label = "segFg")
                Box(
                    Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(bg)
                        .bounceClick(role = Role.RadioButton, onClick = { onSelect(i) })
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = fg)
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = cs.onPrimary,
                checkedTrackColor = cs.primary,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = cs.onSurfaceVariant,
                uncheckedTrackColor = cs.surfaceVariant,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, label: String, content: @Composable () -> Unit = {}, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ring by animateColorAsState(if (selected) cs.onBackground else Color.Transparent, tween(200), label = "swatchRing")
    val pop by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "swatchPop",
    )
    Box(
        modifier = Modifier
            .size(44.dp)
            .scale(pop)
            .border(2.dp, ring, CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .background(color)
            .bounceClick(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun CustomColorDialog(initial: Int, onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val hsv = remember {
        val a = FloatArray(3)
        android.graphics.Color.colorToHSV(initial, a)
        a
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color.hsv(hue, sat, value)
    var hex by remember { mutableStateOf(hexOf(color)) }
    var hexError by remember { mutableStateOf(false) }

    fun pushHex() {
        hex = hexOf(Color.hsv(hue, sat, value))
        hexError = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        titleContentColor = cs.onBackground,
        textContentColor = cs.onBackground,
        title = { Text("Custom accent", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Saturation (left to right) and brightness (bottom to top) box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .pointerInput(Unit) {
                            fun set(p: Offset) {
                                sat = (p.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (p.y / size.height).coerceIn(0f, 1f)
                                pushHex()
                            }
                            detectTapGestures { set(it) }
                        }
                        .pointerInput(Unit) {
                            fun set(p: Offset) {
                                sat = (p.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (p.y / size.height).coerceIn(0f, 1f)
                                pushHex()
                            }
                            detectDragGestures(onDragStart = { set(it) }) { change, _ ->
                                change.consume()
                                set(change.position)
                            }
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
                        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                        val c = Offset(sat * size.width, (1f - value) * size.height)
                        drawCircle(Color.Black.copy(alpha = 0.35f), 12.dp.toPx(), c)
                        drawCircle(Color.White, 11.dp.toPx(), c, style = Stroke(3.dp.toPx()))
                        drawCircle(Color.hsv(hue, sat, value), 8.dp.toPx(), c)
                    }
                }

                // Hue bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .pointerInput(Unit) {
                            detectTapGestures {
                                hue = (it.x / size.width).coerceIn(0f, 1f) * 360f
                                pushHex()
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    hue = (it.x / size.width).coerceIn(0f, 1f) * 360f
                                    pushHex()
                                },
                            ) { change, _ ->
                                change.consume()
                                hue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                                pushHex()
                            }
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(
                            Brush.horizontalGradient(
                                (0..6).map { Color.hsv(it * 60f, 1f, 1f) }
                            )
                        )
                        val x = (hue / 360f) * size.width
                        drawCircle(Color.Black.copy(alpha = 0.35f), 13.dp.toPx(), Offset(x, size.height / 2f))
                        drawCircle(Color.White, 12.dp.toPx(), Offset(x, size.height / 2f), style = Stroke(3.dp.toPx()))
                        drawCircle(Color.hsv(hue, 1f, 1f), 9.dp.toPx(), Offset(x, size.height / 2f))
                    }
                }

                // Preview + hex code
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(color)
                    )
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { raw ->
                            val cleaned = raw.uppercase().filter { it == '#' || it in '0'..'9' || it in 'A'..'F' }.take(7)
                            hex = if (cleaned.startsWith("#")) cleaned else "#$cleaned".take(7)
                            val digits = hex.removePrefix("#")
                            if (digits.length == 6) {
                                val parsed = digits.toInt(16)
                                val a = FloatArray(3)
                                android.graphics.Color.colorToHSV(0xFF000000.toInt() or parsed, a)
                                hue = a[0]; sat = a[1]; value = a[2]
                                hexError = false
                            } else {
                                hexError = true
                            }
                        },
                        singleLine = true,
                        isError = hexError,
                        label = { Text("Hex code") },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(color.toArgb()) }, enabled = !hexError) {
                Text("Apply", color = if (hexError) cs.onSurfaceVariant else cs.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = cs.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        },
    )
}

private fun hexOf(c: Color): String = "#%06X".format(c.toArgb() and 0xFFFFFF)

@Composable
fun SettingsScreen(vm: GalleryViewModel, onBack: () -> Unit, onOpenPermissions: () -> Unit) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    var showCustom by remember { mutableStateOf(false) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val isPreset = Presets.any { it.second == prefs.accent }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = "Settings",
            subtitle = "",
            leading = {
                GIconButton(GIcons.Back, "Back", onClick = onBack)
                Box(Modifier.size(12.dp))
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp + navBottom),
        ) {
            val divider: @Composable () -> Unit = {
                Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(cs.outline))
            }

            SectionLabel("Appearance")
            CardGroup {
                val modes = listOf(ThemeMode.AMOLED, ThemeMode.DARK, ThemeMode.LIGHT)
                SegmentRow(
                    title = "Theme",
                    options = listOf("AMOLED", "Dark", "Light"),
                    selected = modes.indexOf(prefs.theme).coerceAtLeast(0),
                    onSelect = { vm.setTheme(modes[it]) },
                )
                divider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(start = 16.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Accent", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground, modifier = Modifier.weight(1f))
                    Presets.forEach { (name, argb) ->
                        Swatch(
                            color = Color(argb),
                            selected = prefs.accent == argb,
                            label = name,
                            onClick = { vm.setAccent(argb) },
                        )
                    }
                    Swatch(
                        color = if (isPreset) cs.surfaceVariant else Color(prefs.accent),
                        selected = !isPreset,
                        label = "Custom",
                        content = {
                            if (isPreset) {
                                Icon(GIcons.Plus, contentDescription = "Custom color", tint = cs.onBackground, modifier = Modifier.size(18.dp))
                            }
                        },
                        onClick = { showCustom = true },
                    )
                }
            }

            SectionLabel("Gallery")
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LayoutCard(
                    title = "Grid",
                    selected = prefs.layout == LayoutMode.GRID,
                    onClick = { vm.setLayout(LayoutMode.GRID) },
                    modifier = Modifier.weight(1f),
                ) { GridPreview() }
                LayoutCard(
                    title = "Masonry",
                    selected = prefs.layout == LayoutMode.MASONRY,
                    onClick = { vm.setLayout(LayoutMode.MASONRY) },
                    modifier = Modifier.weight(1f),
                ) { MasonryPreview() }
            }

            Spacer(Modifier.height(12.dp))
            CardGroup {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Photos per row", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = cs.onBackground, modifier = Modifier.weight(1f),
                    )
                    GIconButton(
                        GIcons.Minus, "Fewer columns", onClick = { vm.setColumns(prefs.columns - 1) },
                        size = 34.dp, tint = if (prefs.columns > 2) cs.onBackground else cs.onSurfaceVariant,
                    )
                    AnimatedContent(
                        targetState = prefs.columns,
                        transitionSpec = {
                            val up = targetState > initialState
                            val enter = slideInVertically(tween(180)) { if (up) -it else it } + fadeIn(tween(180))
                            val exit = slideOutVertically(tween(180)) { if (up) it else -it } + fadeOut(tween(120))
                            (enter togetherWith exit).using(SizeTransform(clip = false))
                        },
                        label = "columnCount",
                        modifier = Modifier.padding(horizontal = 14.dp),
                    ) { n ->
                        Text("$n", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground)
                    }
                    GIconButton(
                        GIcons.Plus, "More columns", onClick = { vm.setColumns(prefs.columns + 1) },
                        size = 34.dp, tint = if (prefs.columns < 6) cs.onBackground else cs.onSurfaceVariant,
                    )
                }
            }

            SectionLabel("Viewer and video")
            CardGroup {
                ToggleRow("Blurred backgrounds", prefs.blur) { vm.setBlur(it) }
                divider()
                ToggleRow("Resume videos where you left off", prefs.resume) { vm.setResume(it) }
                divider()
                ToggleRow("Autoplay videos", prefs.autoplay) { vm.setAutoplay(it) }
            }

            SectionLabel("Storage")
            CardGroup {
                var daysMenu by remember { mutableStateOf(false) }
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .bounceClick(onClick = { daysMenu = true })
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Keep deleted items for", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground, modifier = Modifier.weight(1f))
                        Text("${prefs.trashDays} days", fontSize = 13.sp, color = cs.onSurfaceVariant)
                        Icon(GIcons.ChevronRight, null, tint = cs.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp).size(16.dp))
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = daysMenu,
                        onDismissRequest = { daysMenu = false },
                    ) {
                        listOf(7, 14, 30).forEach { d ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("$d days", fontWeight = if (d == prefs.trashDays) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    vm.setTrashDays(d)
                                    daysMenu = false
                                },
                            )
                        }
                    }
                }
                divider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .bounceClick(onClick = onOpenPermissions)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Permissions", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground, modifier = Modifier.weight(1f))
                    Icon(GIcons.ChevronRight, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
        }
    }

    if (showCustom) {
        CustomColorDialog(
            initial = prefs.accent,
            onDismiss = { showCustom = false },
            onApply = {
                vm.setAccent(it)
                showCustom = false
            },
        )
    }
}

@Composable
private fun ThemeChip(label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (active) cs.primary else cs.surface, tween(220), label = "themeChipBg")
    val fg by animateColorAsState(if (active) cs.onPrimary else cs.onBackground, tween(220), label = "themeChipFg")
    val line by animateColorAsState(if (active) cs.primary else cs.outline, tween(220), label = "themeChipLine")
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(bg)
            .border(BorderStroke(1.dp, line), RoundedCornerShape(23.dp))
            .bounceClick(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}
