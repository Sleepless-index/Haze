package sleepless_index.haze.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sleepless_index.haze.data.Album
import sleepless_index.haze.data.DatePreset
import sleepless_index.haze.data.LenBucket
import sleepless_index.haze.data.ResBucket
import sleepless_index.haze.data.SearchFilter
import sleepless_index.haze.data.SizeBucket
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Short text for the custom date range, e.g. "Oct 1 – Oct 25". */
fun customRangeLabel(from: Long, to: Long): String {
    val f = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    val zone = ZoneId.systemDefault()
    return f.format(Instant.ofEpochMilli(from).atZone(zone)) + " – " + f.format(Instant.ofEpochMilli(to).atZone(zone))
}

/** One line per active advanced filter, used for the removable chips under the search bar. */
fun activeFilterLabels(f: SearchFilter): List<Pair<String, SearchFilter>> = buildList {
    if (f.date != DatePreset.ANY) {
        add((if (f.date == DatePreset.CUSTOM) customRangeLabel(f.customFrom, f.customTo) else f.date.label) to f.copy(date = DatePreset.ANY))
    }
    if (f.albumId != null) add(f.albumName to f.copy(albumId = null, albumName = ""))
    if (f.size != SizeBucket.ANY) add(f.size.label to f.copy(size = SizeBucket.ANY))
    if (f.res != ResBucket.ANY) add(f.res.label to f.copy(res = ResBucket.ANY))
    if (f.len != LenBucket.ANY) add(f.len.label to f.copy(len = LenBucket.ANY))
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchFilterSheet(
    filter: SearchFilter,
    albums: List<Album>,
    resultCount: Int,
    onChange: (SearchFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current

    fun pickRange() {
        val cal = java.util.Calendar.getInstance()
        android.app.DatePickerDialog(context, { _, y1, m1, d1 ->
            val from = java.util.Calendar.getInstance().apply { set(y1, m1, d1, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
            android.app.DatePickerDialog(context, { _, y2, m2, d2 ->
                val to = java.util.Calendar.getInstance().apply { set(y2, m2, d2, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
                onChange(filter.copy(date = DatePreset.CUSTOM, customFrom = minOf(from, to), customTo = maxOf(from, to)))
            }, y1, m1, d1).apply { setTitle("To"); show() }
        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH))
            .apply { setTitle("From"); show() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surface,
        contentColor = cs.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Filters", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground)

            SheetLabel("Date")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (d in DatePreset.entries) {
                    if (d == DatePreset.CUSTOM) {
                        FilterChipPill(
                            if (filter.date == DatePreset.CUSTOM) customRangeLabel(filter.customFrom, filter.customTo) else d.label,
                            filter.date == DatePreset.CUSTOM,
                        ) { pickRange() }
                    } else {
                        FilterChipPill(d.label, filter.date == d) { onChange(filter.copy(date = d)) }
                    }
                }
            }

            if (albums.isNotEmpty()) {
                SheetLabel("Album")
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChipPill("Any album", filter.albumId == null) { onChange(filter.copy(albumId = null, albumName = "")) }
                    for (a in albums) {
                        FilterChipPill(a.name, filter.albumId == a.bucketId) { onChange(filter.copy(albumId = a.bucketId, albumName = a.name)) }
                    }
                }
            }

            SheetLabel("File size")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in SizeBucket.entries) FilterChipPill(s.label, filter.size == s) { onChange(filter.copy(size = s)) }
            }

            SheetLabel("Resolution")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in ResBucket.entries) FilterChipPill(r.label, filter.res == r) { onChange(filter.copy(res = r)) }
            }

            SheetLabel("Video length")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (l in LenBucket.entries) FilterChipPill(l.label, filter.len == l) { onChange(filter.copy(len = l)) }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(cs.surfaceVariant)
                        .bounceClick(onClick = {
                            onChange(filter.copy(date = DatePreset.ANY, albumId = null, albumName = "", size = SizeBucket.ANY, res = ResBucket.ANY, len = LenBucket.ANY))
                        }),
                    contentAlignment = Alignment.Center,
                ) { Text("Reset", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = cs.onBackground) }
                Box(
                    modifier = Modifier
                        .weight(1.4f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(cs.primary)
                        .bounceClick(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Show ${"%,d".format(resultCount)} ${if (resultCount == 1) "result" else "results"}",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = cs.onPrimary,
                    )
                }
            }
        }
    }
}
