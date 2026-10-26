package sleepless_index.haze.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import sleepless_index.haze.data.MediaItem
import sleepless_index.haze.data.MediaOps
import sleepless_index.haze.data.formatDuration
import sleepless_index.haze.data.formatSize
import sleepless_index.haze.ui.theme.GIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class InfoRow(val icon: ImageVector, val title: String, val value: String)

private class Info(val rows: List<InfoRow>)

fun copyToClipboard(context: Context, item: MediaItem) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newUri(context.contentResolver, item.name, item.uri))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
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
    val info by produceState(Info(emptyList()), item.id) { value = readInfo(context, item) }
    val dateFmt = DateTimeFormatter.ofPattern("MMMM d, yyyy  •  h:mm a", Locale.getDefault())
    val whenText = dateFmt.format(Instant.ofEpochMilli(item.dateMs).atZone(ZoneId.systemDefault()))
    val card = RoundedCornerShape(24.dp)
    var renaming by remember { mutableStateOf(false) }
    var removingLocation by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (translucent) cs.surface.copy(alpha = 0.72f) else cs.surface,
        contentColor = cs.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header card: when it was taken
            Box(Modifier.fillMaxWidth().clip(card).background((if (translucent) cs.surfaceVariant.copy(alpha = 0.6f) else cs.surfaceVariant)).padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(whenText, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = cs.onBackground, modifier = Modifier.align(Alignment.CenterStart).padding(end = 60.dp))
                if (item.id >= 0) {
                    Text(
                        "Edit", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .clip(RoundedCornerShape(20.dp))
                            .background(cs.surface.copy(alpha = 0.7f))
                            .bounceClick(onClick = { pickDate(context, item.dateMs, onEditDate) })
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
            }

            // Detail rows
            Column(
                Modifier.fillMaxWidth().clip(card).background((if (translucent) cs.surfaceVariant.copy(alpha = 0.6f) else cs.surfaceVariant)).padding(vertical = 6.dp),
            ) {
                for (r in info.rows) InfoLine(
                    r,
                    onEdit = when {
                        r.title == "Label" && item.id >= 0 -> ({ renaming = true })
                        r.title == "Location" && item.id >= 0 -> ({ removingLocation = true })
                        else -> null
                    },
                    editLabel = if (r.title == "Location") "Remove" else "Edit",
                )
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

@Composable
private fun InfoLine(row: InfoRow, onEdit: (() -> Unit)? = null, editLabel: String = "Edit") {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(cs.surface.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(row.icon, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(row.title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground)
            Text(row.value, fontSize = 12.5.sp, color = cs.onSurfaceVariant)
        }
        if (onEdit != null) {
            Text(
                editLabel, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(cs.surface.copy(alpha = 0.7f))
                    .bounceClick(onClick = onEdit)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
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
        val rows = mutableListOf<InfoRow>()
        rows += InfoRow(GIcons.Image, "Label", item.name)

        // Real on-disk path (e.g. /storage/XXXX-XXXX/Download/...), falling back to the folder.
        val path = if (item.id >= 0) {
            runCatching {
                context.contentResolver.query(item.uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            }.getOrNull()
        } else null
        val folder = path?.substringBeforeLast('/')
            ?: ("/storage/emulated/0/" + item.relativePath).trimEnd('/')
        rows += InfoRow(GIcons.Info, "Path", if (item.id < 0) "Vault (private to this app)" else folder)

        var ppi: String? = null
        var exif: ExifInterface? = null
        if (!item.isVideo && item.id >= 0) {
            // With All files access the original file (GPS and all) can be read straight from disk.
            exif = (if (path != null && MediaOps.hasAllFiles()) runCatching { ExifInterface(path) }.getOrNull() else null)
                ?: runCatching { openExif(context, MediaStore.setRequireOriginal(item.uri)) }.getOrNull()
                ?: runCatching { openExif(context, item.uri) }.getOrNull()
            val x = exif?.getAttributeDouble(ExifInterface.TAG_X_RESOLUTION, 0.0) ?: 0.0
            val y = exif?.getAttributeDouble(ExifInterface.TAG_Y_RESOLUTION, 0.0) ?: 0.0
            if (x > 0 && y > 0) ppi = "%.1f × %.1f PPI".format(x, y)
        }

        val dim = mutableListOf<String>()
        if (item.width > 0 && item.height > 0) {
            dim += "${item.width} × ${item.height}"
            val mp = item.width.toLong() * item.height / 1_000_000.0
            if (!item.isVideo && mp >= 0.1) dim += "%.1f MP".format(mp)
        }
        ppi?.let { dim += it }
        if (item.isVideo && item.durationMs > 0) dim += formatDuration(item.durationMs)
        dim += formatSize(item.sizeBytes)
        rows += InfoRow(GIcons.Photos, "Dimensions", dim.joinToString("  •  "))

        if (exif != null) {
            val camera = listOfNotNull(
                exif.getAttribute(ExifInterface.TAG_MAKE),
                exif.getAttribute(ExifInterface.TAG_MODEL),
            ).joinToString(" ").trim()
            if (camera.isNotEmpty()) rows += InfoRow(GIcons.Image, "Camera", camera)
            val parts = mutableListOf<String>()
            exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { parts += "f/$it" }
            exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.toDoubleOrNull()?.let {
                parts += if (it < 1.0 && it > 0.0) "1/${(1.0 / it).toInt()}s" else "${it}s"
            }
            exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { parts += "ISO $it" }
            if (parts.isNotEmpty()) rows += InfoRow(GIcons.Brightness, "Exposure", parts.joinToString("  •  "))
            val ll = exif.latLong
            if (ll != null) rows += InfoRow(GIcons.Info, "Location", "%.5f, %.5f".format(ll[0], ll[1]))
        }
        if (item.mime.isNotBlank()) rows += InfoRow(GIcons.Info, "Type", item.mime)
        Info(rows)
    }
