package sleepless_index.haze.ui

import kotlinx.coroutines.launch
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sleepless_index.haze.data.MediaItem
import sleepless_index.haze.data.MediaOps
import sleepless_index.haze.data.formatDuration
import sleepless_index.haze.data.formatSize
import sleepless_index.haze.ui.theme.GIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

private data class TechRow(val label: String, val value: String, val editable: Boolean = false)

private data class Info(
    val path: String,
    val folderName: String,
    val sizeTop: String,
    val sizeSub: String,
    val resTop: String,
    val resSub: String,
    val thirdLabel: String,
    val thirdTop: String,
    val thirdSub: String,
    val camera: String? = null,
    val make: String? = null,
    val specs: List<String> = emptyList(),
    val latLong: DoubleArray? = null,
    val tech: List<TechRow> = emptyList(),
)

fun copyToClipboard(context: Context, item: MediaItem) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newUri(context.contentResolver, item.name, item.uri))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

private fun aspectText(w: Int, h: Int): String {
    if (w <= 0 || h <= 0) return ""
    val r = w.toDouble() / h
    val known = listOf(
        "1:1" to 1.0, "4:3" to 4 / 3.0, "3:4" to 3 / 4.0, "3:2" to 1.5, "2:3" to 2 / 3.0,
        "16:9" to 16 / 9.0, "9:16" to 9 / 16.0, "2:1" to 2.0, "1:2" to 0.5,
    )
    known.firstOrNull { abs(it.second - r) < 0.02 }?.let { return it.first }
    return "%.2f:1".format(r)
}

private fun typeName(item: MediaItem): String =
    item.mime.substringAfter('/', "").removePrefix("x-").uppercase().ifBlank { if (item.isVideo) "VIDEO" else "IMAGE" }

private fun basicInfo(item: MediaItem): Info {
    val w = item.width
    val h = item.height
    val aspect = aspectText(w, h)
    val mp = w.toLong() * h / 1_000_000.0
    val resTop: String
    val resSub: String
    if (item.isVideo) {
        resTop = if (w > 0 && h > 0) "${min(w, h)}p" else "—"
        resSub = if (w > 0 && h > 0) "$w × $h" else ""
    } else {
        resTop = if (mp >= 0.1) "%.1f MP".format(mp) else if (w > 0 && h > 0) "$w × $h" else "—"
        resSub = if (w > 0 && h > 0) "$w × $h" else ""
    }
    return Info(
        path = if (item.id < 0) "Vault (private to this app)" else ("/storage/emulated/0/" + item.relativePath).trimEnd('/'),
        folderName = if (item.id < 0) "Vault" else item.relativePath.trimEnd('/').substringAfterLast('/').ifBlank { "Storage" },
        sizeTop = formatSize(item.sizeBytes),
        sizeSub = typeName(item),
        resTop = resTop,
        resSub = resSub,
        thirdLabel = if (item.isVideo) "DURATION" else "ASPECT",
        thirdTop = if (item.isVideo) formatDuration(item.durationMs) else aspect.ifBlank { "—" },
        thirdSub = if (item.isVideo) aspect else "",
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(
    item: MediaItem,
    translucent: Boolean,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onEditDate: (Long) -> Unit = {},
    onRemoveLocation: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val info by produceState(basicInfo(item), item.id) { value = readInfo(context, item) }
    val zone = ZoneId.systemDefault()
    val moment = Instant.ofEpochMilli(item.dateMs).atZone(zone)
    val dayText = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()).format(moment)
    val timeText = DateTimeFormatter.ofPattern("yyyy  ·  h:mm a", Locale.getDefault()).format(moment)
    val relText = DateUtils.getRelativeTimeSpanString(item.dateMs, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString()
    val cardShape = RoundedCornerShape(20.dp)
    val cardBg = if (translucent) cs.surfaceVariant.copy(alpha = 0.6f) else cs.surfaceVariant
    var renaming by remember { mutableStateOf(false) }
    var removingLocation by remember { mutableStateOf(false) }
    var techOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scroll = rememberScrollState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (translucent) cs.surface.copy(alpha = 0.72f) else cs.surface,
        contentColor = cs.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // When it was taken
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(dayText, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground)
                    Text("$timeText  ·  $relText", fontSize = 14.sp, color = cs.onSurfaceVariant)
                    if (info.camera != null || info.specs.isNotEmpty()) {
                        Text(
                            (listOfNotNull(info.camera) + info.specs).joinToString("  ·  "),
                            fontSize = 13.sp, color = cs.onSurfaceVariant, maxLines = 2,
                        )
                    }
                }
                if (item.id >= 0) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(cardBg)
                            .bounceClick(onClick = { pickDate(context, item.dateMs, onEditDate) })
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(GIcons.Edit, null, tint = cs.onBackground, modifier = Modifier.size(16.dp))
                        Text("Edit", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground)
                    }
                }
            }

            // File name
            Row(
                modifier = Modifier.fillMaxWidth().clip(cardShape).background(cardBg).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(GIcons.Image, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Text(
                    item.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (item.id >= 0) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).bounceClick(onClick = { renaming = true }),
                        contentAlignment = Alignment.Center,
                    ) { Icon(GIcons.Edit, "Rename", tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
                }
            }

            // Size, resolution, third stat
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("SIZE", info.sizeTop, info.sizeSub, cardBg, Modifier.weight(1f))
                StatCard("RESOLUTION", info.resTop, info.resSub, cardBg, Modifier.weight(1f))
                StatCard(info.thirdLabel, info.thirdTop, info.thirdSub, cardBg, Modifier.weight(1f))
            }

            // Camera
            if (info.camera != null || info.specs.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cardBg).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(GIcons.Camera, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Text(info.camera ?: "Camera", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground)
                        info.make?.let { Text(it, fontSize = 13.sp, color = cs.onSurfaceVariant) }
                    }
                    if (info.specs.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (s in info.specs) {
                                Text(
                                    s, fontSize = 12.5.sp, fontFamily = FontFamily.Monospace, color = cs.onBackground,
                                    textAlign = TextAlign.Center, maxLines = 1,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(cs.surface)
                                        .padding(vertical = 9.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Location
            info.latLong?.let { ll ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cardBg)) {
                    MapPlaceholder()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Location", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground)
                            Text(
                                "%.5f, %.5f".format(ll[0], ll[1]),
                                fontSize = 12.5.sp, fontFamily = FontFamily.Monospace, color = cs.onSurfaceVariant,
                            )
                        }
                        if (item.id >= 0) {
                            Text(
                                "Remove", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.error,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(cs.error.copy(alpha = 0.14f))
                                    .bounceClick(onClick = { removingLocation = true })
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                            )
                        }
                    }
                }
            }

            // Where the file lives
            Row(
                modifier = Modifier.fillMaxWidth().clip(cardShape).background(cardBg).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(GIcons.Folder, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(info.folderName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground)
                    Text(
                        info.path, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = cs.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.id >= 0) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).bounceClick(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("path", info.path))
                            Toast.makeText(context, "Path copied", Toast.LENGTH_SHORT).show()
                        }),
                        contentAlignment = Alignment.Center,
                    ) { Icon(GIcons.Copy, "Copy path", tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
                }
            }

            // Technical details: folded away until asked for
            if (info.tech.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cardBg)
                        .animateContentSize(androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 380f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick(onClick = {
                                // Keep the sheet exactly where it is (expanded) and the scroll offset
                                // untouched while the card grows or shrinks.
                                val keep = scroll.value
                                techOpen = !techOpen
                                scope.launch {
                                    sheetState.expand()
                                    if (scroll.value != keep) scroll.scrollTo(keep.coerceAtMost(scroll.maxValue))
                                }
                            })
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(GIcons.Info, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Text("Technical details", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground, modifier = Modifier.weight(1f))
                        if (techOpen) {
                            Text(
                                "Copy all", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(cs.surface)
                                    .bounceClick(onClick = {
                                        val text = buildString {
                                            appendLine("Name: ${item.name}")
                                            appendLine("Path: ${info.path}")
                                            appendLine("Size: ${info.sizeTop} (${item.sizeBytes} bytes)")
                                            info.tech.forEach { appendLine("${it.label}: ${it.value}") }
                                        }.trim()
                                        copyText(context, text, "Technical details copied")
                                    })
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                        val chevron by androidx.compose.animation.core.animateFloatAsState(
                            if (techOpen) 180f else 0f,
                            androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 420f),
                            label = "techChevron",
                        )
                        Icon(GIcons.ChevronDown, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp).rotate(chevron))
                    }
                    if (techOpen) {
                        Text(
                            "Tap a value to copy it. Rows marked Editable can be changed from this sheet; the rest are read-only.",
                            fontSize = 12.5.sp, lineHeight = 18.sp, color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 6.dp),
                        )
                        for (row in info.tech) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bounceClick(onClick = { copyText(context, row.value, "${row.label} copied") })
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(row.label, fontSize = 12.5.sp, color = cs.onSurfaceVariant, modifier = Modifier.width(104.dp))
                                Text(row.value, fontSize = 13.sp, color = cs.onBackground, modifier = Modifier.weight(1f))
                                if (row.editable) {
                                    Text(
                                        "Editable", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = cs.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(cs.primary.copy(alpha = 0.16f))
                                            .padding(horizontal = 7.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }
                        Box(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    if (removingLocation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { removingLocation = false },
            containerColor = cs.surface,
            title = { Text("Remove location?", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = { Text("The GPS position will be erased from this photo's file. This can't be undone.", color = cs.onSurfaceVariant) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { removingLocation = false; onRemoveLocation() }) { Text("Remove") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { removingLocation = false }) { Text("Cancel") } },
        )
    }
    if (renaming) RenameDialog(item.name, onDismiss = { renaming = false }, onConfirm = { renaming = false; onRename(it) })
}

@Composable
private fun StatCard(label: String, top: String, sub: String, bg: Color, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier.clip(RoundedCornerShape(18.dp)).background(bg).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = cs.onSurfaceVariant, maxLines = 1)
        Text(top, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = cs.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub.ifBlank { " " }, fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A drawn stand-in for a map: Haze never goes online, so this only marks that the photo has a position. */
@Composable
private fun MapPlaceholder() {
    val cs = MaterialTheme.colorScheme
    val line = cs.outline
    val road = cs.onSurfaceVariant.copy(alpha = 0.28f)
    val pin = cs.primary
    Canvas(Modifier.fillMaxWidth().height(104.dp).background(cs.background.copy(alpha = 0.55f))) {
        val grid = 26.dp.toPx()
        var y = grid
        while (y < size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += grid
        }
        var x = 60.dp.toPx()
        while (x < size.width) {
            drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += 70.dp.toPx()
        }
        val p = Path().apply {
            moveTo(-10f, size.height * 0.86f)
            cubicTo(size.width * 0.17f, size.height * 0.67f, size.width * 0.25f, size.height * 0.38f, size.width * 0.45f, size.height * 0.46f)
            cubicTo(size.width * 0.65f, size.height * 0.55f, size.width * 0.78f, size.height * 0.2f, size.width + 10f, size.height * 0.1f)
        }
        drawPath(p, road, style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round))
        val c = Offset(size.width * 0.5f, size.height * 0.42f)
        drawCircle(pin.copy(alpha = 0.22f), 18.dp.toPx(), c)
        drawCircle(pin, 8.dp.toPx(), c)
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ext = current.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
    var text by remember { mutableStateOf(current.removeSuffix(ext)) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        title = { Text("Rename", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                suffix = { Text(ext, color = cs.onSurfaceVariant) },
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { val n = text.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_"); if (n.isNotEmpty()) onConfirm(n + ext) },
            ) { Text("Rename") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Platform date then time pickers; reports the chosen moment in epoch millis. */
private fun pickDate(context: Context, initialMs: Long, onPicked: (Long) -> Unit) {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = initialMs }
    android.app.DatePickerDialog(context, { _, y, m, d ->
        android.app.TimePickerDialog(context, { _, h, min ->
            val r = java.util.Calendar.getInstance().apply { set(y, m, d, h, min, 0); set(java.util.Calendar.MILLISECOND, 0) }
            onPicked(r.timeInMillis)
        }, c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), false).show()
    }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show()
}

private fun openExif(context: Context, uri: android.net.Uri): ExifInterface? =
    context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }

private suspend fun readInfo(context: Context, item: MediaItem): Info =
    withContext(Dispatchers.IO) {
        var info = basicInfo(item)

        // Real on-disk path (e.g. /storage/XXXX-XXXX/Download/...), falling back to the folder.
        if (item.id >= 0) {
            val path = runCatching {
                context.contentResolver.query(item.uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            }.getOrNull()
            if (path != null) {
                val folder = path.substringBeforeLast('/')
                info = info.copy(path = folder, folderName = folder.substringAfterLast('/').ifBlank { info.folderName })
            }

            if (item.isVideo) {
                info = info.copy(tech = videoTech(context, item))
            }
            if (!item.isVideo) {
                info = info.copy(tech = listOf(TechRow("Format", item.mime.ifBlank { "Unknown" })))
                // With All files access the original file (GPS and all) can be read straight from disk.
                val exif = (if (path != null && MediaOps.hasAllFiles()) runCatching { ExifInterface(path) }.getOrNull() else null)
                    ?: runCatching { openExif(context, MediaStore.setRequireOriginal(item.uri)) }.getOrNull()
                    ?: runCatching { openExif(context, item.uri) }.getOrNull()
                if (exif != null) {
                    val x = exif.getAttributeDouble(ExifInterface.TAG_X_RESOLUTION, 0.0)
                    val y = exif.getAttributeDouble(ExifInterface.TAG_Y_RESOLUTION, 0.0)
                    if (x > 0 && y > 0) {
                        info = info.copy(
                            thirdLabel = "DENSITY",
                            thirdTop = "%.0f PPI".format(x),
                            thirdSub = aspectText(item.width, item.height),
                        )
                    }
                    val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim().orEmpty()
                    val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim().orEmpty()
                    // Some phones repeat the brand inside the model name.
                    val shownModel = if (make.isNotEmpty() && model.startsWith(make, ignoreCase = true)) model.drop(make.length).trim() else model
                    val specs = mutableListOf<String>()
                    exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.toDoubleOrNull()?.let { specs += "f/%s".format(trimNumber(it)) }
                    exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.toDoubleOrNull()?.let {
                        specs += if (it < 1.0 && it > 0.0) "1/${(1.0 / it).toInt()}s" else "${trimNumber(it)}s"
                    }
                    exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { specs += "ISO $it" }
                    val focal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
                    if (focal > 0) specs += "${trimNumber(focal)}mm"
                    info = info.copy(
                        tech = photoTech(context, item, exif),
                        camera = (shownModel.ifEmpty { make }).ifEmpty { null },
                        make = if (shownModel.isNotEmpty() && make.isNotEmpty()) make else null,
                        specs = specs,
                        latLong = exif.latLong,
                    )
                }
            }
        }
        info
    }

private fun trimNumber(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)

private fun copyText(context: Context, text: String, toast: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Haze", text))
    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}

private fun photoTech(context: Context, item: MediaItem, exif: ExifInterface): List<TechRow> {
    val rows = mutableListOf<TechRow>()
    fun add(label: String, value: String?, editable: Boolean = false) {
        if (!value.isNullOrBlank()) rows += TechRow(label, value.trim(), editable)
    }
    add("Format", item.mime.ifBlank { "Unknown" })
    add("Date taken", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL), editable = true)
    add("Camera", listOfNotNull(exif.getAttribute(ExifInterface.TAG_MAKE), exif.getAttribute(ExifInterface.TAG_MODEL)).joinToString(" "))
    add("Lens", exif.getAttribute(ExifInterface.TAG_LENS_MODEL))
    add("Software", exif.getAttribute(ExifInterface.TAG_SOFTWARE))
    exif.getAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE)?.toDoubleOrNull()?.let { add("Exposure bias", "%+.1f EV".format(it)) }
    exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0).takeIf { it > 0 }?.let { add("35 mm equivalent", "$it mm") }
    exif.getAttributeInt(ExifInterface.TAG_FLASH, -1).takeIf { it >= 0 }?.let { add("Flash", if (it and 1 == 1) "Fired" else "Did not fire") }
    exif.getAttributeInt(ExifInterface.TAG_WHITE_BALANCE, -1).takeIf { it >= 0 }?.let { add("White balance", if (it == 0) "Auto" else "Manual") }
    val space = runCatching {
        context.contentResolver.openInputStream(item.uri)?.use { input ->
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, o)
            o.outColorSpace?.name
        }
    }.getOrNull()
    add("Color space", space)
    val xmp = runCatching { exif.getAttribute(ExifInterface.TAG_XMP) }.getOrNull()
    if (xmp != null && xmp.contains("hdrgm", ignoreCase = true)) add("HDR", "Ultra HDR (gain map)")
    exif.latLong?.let { add("GPS position", "%.5f, %.5f".format(it[0], it[1]), editable = true) }
    return rows
}

private fun codecName(mime: String): String = when (mime.lowercase()) {
    "video/avc" -> "H.264 (AVC)"
    "video/hevc" -> "H.265 (HEVC)"
    "video/av01" -> "AV1"
    "video/x-vnd.on2.vp9" -> "VP9"
    "video/x-vnd.on2.vp8" -> "VP8"
    "video/mp4v-es" -> "MPEG-4"
    "video/3gpp" -> "H.263"
    "video/dolby-vision" -> "Dolby Vision"
    "audio/mp4a-latm" -> "AAC"
    "audio/opus" -> "Opus"
    "audio/vorbis" -> "Vorbis"
    "audio/ac3" -> "AC-3"
    "audio/eac3" -> "E-AC-3"
    "audio/mpeg" -> "MP3"
    "audio/flac" -> "FLAC"
    "audio/raw" -> "PCM"
    "audio/3gpp" -> "AMR-NB"
    "audio/amr-wb" -> "AMR-WB"
    else -> mime.substringAfter('/').uppercase()
}

private fun videoTech(context: Context, item: MediaItem): List<TechRow> {
    val rows = mutableListOf<TechRow>()
    rows += TechRow("Container", item.mime.ifBlank { "Unknown" })

    // Codecs come from the file's tracks.
    val video = mutableListOf<TechRow>()
    val audio = mutableListOf<TechRow>()
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(context, item.uri, null)
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) {
                video += TechRow("Video codec", codecName(mime))
            } else if (mime.startsWith("audio/")) {
                audio += TechRow("Audio codec", codecName(mime))
                val rate = if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) f.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 0
                val ch = if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 0
                if (rate > 0 || ch > 0) {
                    audio += TechRow("Audio", listOfNotNull(
                        if (rate > 0) "%.1f kHz".format(rate / 1000.0) else null,
                        if (ch > 0) "$ch ch" else null,
                    ).joinToString("  ·  "))
                }
            }
        }
    } catch (e: Exception) {
        // Some containers can't be probed; the rows that did load are still shown.
    } finally {
        extractor.release()
    }
    rows += video

    val mmr = MediaMetadataRetriever()
    try {
        mmr.setDataSource(context, item.uri)
        if (Build.VERSION.SDK_INT >= 28 && item.durationMs > 0) {
            val frames = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toLongOrNull() ?: 0L
            if (frames > 0) rows += TechRow("Frame rate", "%.0f fps".format(frames * 1000.0 / item.durationMs))
        }
        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.takeIf { it > 0 }
            ?.let { rows += TechRow("Bitrate", "%.1f Mbps".format(it / 1_000_000.0)) }
        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()?.takeIf { it != 0 }
            ?.let { rows += TechRow("Rotation", "$it°") }
        if (Build.VERSION.SDK_INT >= 30) {
            when (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)?.toIntOrNull()) {
                6 -> rows += TechRow("HDR", "HDR10 (PQ)")
                7 -> rows += TechRow("HDR", "HLG")
            }
            when (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_STANDARD)?.toIntOrNull()) {
                1 -> rows += TechRow("Color standard", "BT.709")
                6 -> rows += TechRow("Color standard", "BT.2020")
            }
        }
    } catch (e: Exception) {
        // Same here: show whatever could be read.
    } finally {
        mmr.release()
    }
    rows += audio
    return rows
}
