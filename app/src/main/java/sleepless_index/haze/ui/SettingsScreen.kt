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
import androidx.compose.foundation.layout.heightIn
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
private fun ToggleRow(title: String, checked: Boolean, description: String? = null, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = if (description != null) 12.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground)
            if (description != null) Text(description, fontSize = 13.sp, lineHeight = 18.sp, color = cs.onSurfaceVariant)
        }
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

private enum class SettingsPage { Home, Appearance, Layout, Viewer, Video, Privacy, Storage }

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(cs.surfaceVariant)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

@Composable
private fun IconTile(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun NavRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground)
            Text(value, fontSize = 12.5.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Icon(GIcons.ChevronRight, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun InsetDivider() {
    Box(Modifier.padding(start = 66.dp).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
}

@Composable
private fun StatusRow(title: String, note: String, on: Boolean) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground)
            Text(note, fontSize = 12.5.sp, color = cs.onSurfaceVariant)
        }
        if (on) Icon(GIcons.Check, null, tint = cs.primary, modifier = Modifier.size(16.dp))
        Text(
            if (on) "On" else "Off", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = if (on) cs.primary else cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThemePreview() {
    val cs = MaterialTheme.colorScheme
    val tiles = listOf(
        0xFF3B5B8A to 0xFF1D2B44, 0xFFC9825A to 0xFF5B3A3A, 0xFF4F7A62 to 0xFF1F3A2C, 0xFF7A6AA8 to 0xFF2C2548,
        0xFF8A7A52 to 0xFF3A3220, 0xFF5A8AA8 to 0xFF203244, 0xFFA85A6A to 0xFF442030, 0xFF6A6A72 to 0xFF26262B,
    )
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(cs.background)
            .border(BorderStroke(1.dp, cs.outline), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Today", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = cs.onBackground, modifier = Modifier.weight(1f))
            Box(Modifier.size(22.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
                Icon(GIcons.Check, null, tint = cs.onPrimary, modifier = Modifier.size(13.dp))
            }
        }
        for (row in 0..1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0..3) {
                    val (a, b) = tiles[row * 4 + i]
                    Box(
                        Modifier
                            .weight(1f)
                            .height(62.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.linearGradient(listOf(Color(a), Color(b)))),
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    vm: GalleryViewModel,
    perms: PermState,
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenTrash: () -> Unit,
) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    var showCustom by remember { mutableStateOf(false) }
    var page by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(SettingsPage.Home) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val isPreset = Presets.any { it.second == prefs.accent }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }

    androidx.activity.compose.BackHandler(enabled = page != SettingsPage.Home) { page = SettingsPage.Home }

    val themeName = when (prefs.theme) { ThemeMode.AMOLED -> "AMOLED"; ThemeMode.DARK -> "Dark"; ThemeMode.LIGHT -> "Light" }
    val layoutName = if (prefs.layout == LayoutMode.GRID) "Grid" else "Masonry"
    fun onOff(b: Boolean) = if (b) "on" else "off"
    val lockName = when (VaultLockStore.typeOf(prefs.vaultLock)) {
        VaultLockStore.PIN -> "PIN"
        VaultLockStore.PATTERN -> "Pattern"
        VaultLockStore.PASSWORD -> "Password"
        else -> "Not set"
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = when (page) {
                SettingsPage.Home -> "Settings"
                SettingsPage.Appearance -> "Appearance"
                SettingsPage.Layout -> "Gallery layout"
                SettingsPage.Viewer -> "Viewer"
                SettingsPage.Video -> "Video"
                SettingsPage.Privacy -> "Privacy"
                SettingsPage.Storage -> "Storage and permissions"
            },
            subtitle = "",
            leading = {
                GIconButton(GIcons.Back, "Back", onClick = { if (page == SettingsPage.Home) onBack() else page = SettingsPage.Home })
                Box(Modifier.size(12.dp))
            },
        )
        androidx.compose.animation.AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
            label = "settingsPage",
            modifier = Modifier.fillMaxSize(),
        ) { p ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 6.dp, bottom = 100.dp + navBottom),
            ) {
                when (p) {
                    SettingsPage.Home -> {
                        val shape = RoundedCornerShape(22.dp)
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .clip(shape)
                                .background(cs.surface)
                                .border(BorderStroke(1.dp, cs.outline), shape)
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(
                                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(cs.background)
                                    .border(BorderStroke(1.dp, cs.outline), RoundedCornerShape(16.dp)),
                            ) {
                                Box(Modifier.padding(start = 10.dp, top = 14.dp).size(width = 34.dp, height = 26.dp).clip(RoundedCornerShape(14.dp)).background(cs.primary.copy(alpha = 0.55f)))
                                Box(Modifier.padding(start = 16.dp, top = 8.dp).size(width = 26.dp, height = 20.dp).clip(RoundedCornerShape(12.dp)).background(cs.primary.copy(alpha = 0.4f)))
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Haze", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground)
                                Text(
                                    if (version.isNotEmpty()) "Version $version · Local only, no network" else "Local only, no network",
                                    fontSize = 13.sp, color = cs.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        CardGroup {
                            NavRow(GIcons.Brightness, "Appearance", "$themeName · ${if (isPreset) "Default" else "Custom"} accent") { page = SettingsPage.Appearance }
                            InsetDivider()
                            NavRow(GIcons.Photos, "Gallery layout", "$layoutName · ${prefs.columns} columns") { page = SettingsPage.Layout }
                        }
                        Spacer(Modifier.height(14.dp))
                        CardGroup {
                            NavRow(GIcons.Image, "Viewer", "Blurred backgrounds ${onOff(prefs.blur)}") { page = SettingsPage.Viewer }
                            InsetDivider()
                            NavRow(GIcons.Play, "Video", "Resume ${onOff(prefs.resume)} · Autoplay ${onOff(prefs.autoplay)}") { page = SettingsPage.Video }
                        }
                        Spacer(Modifier.height(14.dp))
                        CardGroup {
                            NavRow(GIcons.Lock, "Privacy", "Vault $lockName · Location ${if (prefs.stripLocation) "removed" else "kept"} on share") { page = SettingsPage.Privacy }
                            InsetDivider()
                            NavRow(GIcons.SdCard, "Storage and permissions", "All files access ${onOff(perms.allFiles)}") { page = SettingsPage.Storage }
                        }
                    }

                    SettingsPage.Appearance -> {
                        ThemePreview()
                        SectionLabel("Theme")
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(ThemeMode.AMOLED to "AMOLED", ThemeMode.DARK to "Dark", ThemeMode.LIGHT to "Light").forEach { (mode, label) ->
                                ThemeChip(
                                    label = label,
                                    active = prefs.theme == mode,
                                    onClick = { vm.setTheme(mode) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        SectionLabel("Accent color")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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

                    SettingsPage.Layout -> {
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
                                    "Grid columns", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
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
                    }

                    SettingsPage.Viewer -> CardGroup {
                        ToggleRow("Blurred backgrounds", prefs.blur, "Soft blur behind photos and the details sheet.") { vm.setBlur(it) }
                    }

                    SettingsPage.Video -> CardGroup {
                        ToggleRow("Resume where you left off", prefs.resume) { vm.setResume(it) }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outline))
                        ToggleRow("Autoplay when opened", prefs.autoplay) { vm.setAutoplay(it) }
                    }

                    SettingsPage.Privacy -> {
                        SectionLabel("Vault")
                        CardGroup {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                IconTile(GIcons.Lock)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text("Vault lock", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground)
                                    Text(lockName, fontSize = 12.5.sp, color = cs.onSurfaceVariant)
                                }
                                Pill("Open") { onOpenVault() }
                            }
                            Text(
                                "Hidden photos stay inside Haze and need this lock to open. To change it, open the vault and tap the lock icon. A forgotten lock can't be recovered.",
                                fontSize = 13.sp, lineHeight = 19.sp, color = cs.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            )
                        }
                        SectionLabel("Sharing")
                        CardGroup {
                            ToggleRow(
                                "Remove location when sharing", prefs.stripLocation,
                                "Photos are copied without GPS before they leave Haze. Your originals are untouched.",
                            ) { vm.setStripLocation(it) }
                        }
                    }

                    SettingsPage.Storage -> {
                        SectionLabel("Permissions")
                        CardGroup {
                            StatusRow("Photos and videos", "Read access", perms.media)
                            Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(cs.outline))
                            StatusRow("All files access", "Faster moves, full EXIF and editing in place", perms.allFiles)
                            Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(cs.outline))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Manage permissions", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground, modifier = Modifier.weight(1f))
                                Pill("Manage") { onOpenPermissions() }
                            }
                        }
                        if (Build.VERSION.SDK_INT >= 30) {
                            SectionLabel("Trash")
                            CardGroup {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text("Trash", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground)
                                        Text("Items are deleted after ${prefs.trashDays} days", fontSize = 12.5.sp, color = cs.onSurfaceVariant)
                                    }
                                    Pill("Open") { onOpenTrash() }
                                }
                            }
                        }
                    }
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
