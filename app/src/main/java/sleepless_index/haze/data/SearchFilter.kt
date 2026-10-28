package sleepless_index.haze.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val MB = 1024L * 1024
private const val DAY = 24L * 60 * 60 * 1000

fun MediaItem.isGif(): Boolean = mime.equals("image/gif", ignoreCase = true) || name.endsWith(".gif", ignoreCase = true)

fun MediaItem.isScreenshot(): Boolean =
    relativePath.contains("screenshot", ignoreCase = true) || name.startsWith("Screenshot", ignoreCase = true)

enum class MediaKind(val label: String) {
    ALL("All"), PHOTO("Photos"), VIDEO("Videos"), GIF("GIFs"), SCREENSHOT("Screenshots"),
}

enum class DatePreset(val label: String) {
    ANY("Any time"), WEEK("Past week"), MONTH("Past month"), QUARTER("Past 3 months"),
    THIS_YEAR("This year"), LAST_YEAR("Last year"), CUSTOM("Custom range"),
}

enum class SizeBucket(val label: String, val min: Long, val max: Long) {
    ANY("Any size", 0, Long.MAX_VALUE),
    UNDER_1("Under 1 MB", 0, MB),
    MB_1_10("1 – 10 MB", MB, 10 * MB),
    MB_10_100("10 – 100 MB", 10 * MB, 100 * MB),
    OVER_100("Over 100 MB", 100 * MB, Long.MAX_VALUE),
    OVER_500("Over 500 MB", 500 * MB, Long.MAX_VALUE),
    OVER_1000("Over 1 GB", 1024 * MB, Long.MAX_VALUE),
}

enum class ResBucket(val label: String, val minMp: Double, val maxMp: Double) {
    ANY("Any resolution", 0.0, Double.MAX_VALUE),
    UNDER_2("Under 2 MP", 0.0, 2.0),
    MP_2_8("2 – 8 MP", 2.0, 8.0),
    MP_8_12("8 – 12 MP", 8.0, 12.0),
    OVER_12("Over 12 MP", 12.0, Double.MAX_VALUE),
}

enum class LenBucket(val label: String, val min: Long, val max: Long) {
    ANY("Any length", 0, Long.MAX_VALUE),
    UNDER_30S("Under 30 s", 0, 30_000),
    S30_5M("30 s – 5 min", 30_000, 300_000),
    M5_20("5 – 20 min", 300_000, 1_200_000),
    OVER_20("Over 20 min", 1_200_000, Long.MAX_VALUE),
}

/** Everything the Search tab can narrow by, apart from the typed text. */
data class SearchFilter(
    val kind: MediaKind = MediaKind.ALL,
    val favoritesOnly: Boolean = false,
    val date: DatePreset = DatePreset.ANY,
    val customFrom: Long = 0L,
    val customTo: Long = 0L,
    val albumId: Long? = null,
    val albumName: String = "",
    val size: SizeBucket = SizeBucket.ANY,
    val res: ResBucket = ResBucket.ANY,
    val len: LenBucket = LenBucket.ANY,
) {
    /** How many of the filters hidden behind the Filters sheet are on. */
    val advancedCount: Int
        get() = listOf(date != DatePreset.ANY, albumId != null, size != SizeBucket.ANY, res != ResBucket.ANY, len != LenBucket.ANY).count { it }

    val isActive: Boolean get() = kind != MediaKind.ALL || favoritesOnly || advancedCount > 0

    private fun dateRange(now: Long): LongRange? {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        fun start(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
        val yearStart = today.withDayOfYear(1)
        return when (date) {
            DatePreset.ANY -> null
            DatePreset.WEEK -> (now - 7 * DAY)..now
            DatePreset.MONTH -> (now - 30 * DAY)..now
            DatePreset.QUARTER -> (now - 90 * DAY)..now
            DatePreset.THIS_YEAR -> start(yearStart)..now
            DatePreset.LAST_YEAR -> start(yearStart.minusYears(1)) until start(yearStart)
            DatePreset.CUSTOM -> customFrom..(customTo + DAY - 1)
        }
    }

    /** Builds the matcher once so a whole library can be tested cheaply. */
    fun matcher(favs: Set<Long>, now: Long = System.currentTimeMillis()): (MediaItem) -> Boolean {
        val range = dateRange(now)
        return fun(m: MediaItem): Boolean {
            if (favoritesOnly && m.id !in favs) return false
            when (kind) {
                MediaKind.ALL -> {}
                MediaKind.PHOTO -> if (m.isVideo) return false
                MediaKind.VIDEO -> if (!m.isVideo) return false
                MediaKind.GIF -> if (m.isVideo || !m.isGif()) return false
                MediaKind.SCREENSHOT -> if (m.isVideo || !m.isScreenshot()) return false
            }
            if (albumId != null && m.bucketId != albumId) return false
            if (range != null && m.dateMs !in range) return false
            if (size != SizeBucket.ANY && (m.sizeBytes < size.min || m.sizeBytes >= size.max)) return false
            if (res != ResBucket.ANY) {
                val mp = m.width.toDouble() * m.height / 1_000_000.0
                if (mp < res.minMp || mp >= res.maxMp) return false
            }
            if (len != LenBucket.ANY) {
                if (!m.isVideo || m.durationMs < len.min || m.durationMs >= len.max) return false
            }
            return true
        }
    }
}
