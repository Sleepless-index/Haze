package sleepless_index.haze.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.math.abs

/** A set of look-alike files. [keepId] is the one Haze suggests keeping (null for plain lists). */
data class CleanupGroup(val items: List<MediaItem>, val keepId: Long?) {
    val removable: List<MediaItem> get() = items.filter { it.id != keepId }
    val reclaimBytes: Long get() = removable.sumOf { it.sizeBytes }
}

data class CleanupResult(
    val duplicates: List<CleanupGroup> = emptyList(),
    val similar: List<CleanupGroup> = emptyList(),
    val screenshots: List<MediaItem> = emptyList(),
    val largeVideos: List<MediaItem> = emptyList(),
    val scanned: Boolean = false,
) {
    val duplicateBytes: Long get() = duplicates.sumOf { it.reclaimBytes }
    val similarBytes: Long get() = similar.sumOf { it.reclaimBytes }
    val screenshotBytes: Long get() = screenshots.sumOf { it.sizeBytes }
    val largeVideoBytes: Long get() = largeVideos.sumOf { it.sizeBytes }

    /** What the headline promises: everything except videos, which are the user's call. */
    val freeableBytes: Long get() = duplicateBytes + similarBytes + screenshotBytes

    /** Drops files that no longer exist, and groups that stopped being groups. */
    fun pruned(present: Set<Long>): CleanupResult {
        fun prune(groups: List<CleanupGroup>) = groups.mapNotNull { g ->
            val left = g.items.filter { it.id in present }
            if (left.size < 2) null
            else CleanupGroup(left, if (g.keepId != null && left.any { it.id == g.keepId }) g.keepId else left.first().id)
        }
        return copy(
            duplicates = prune(duplicates),
            similar = prune(similar),
            screenshots = screenshots.filter { it.id in present },
            largeVideos = largeVideos.filter { it.id in present },
        )
    }
}

object Cleanup {
    private const val SCREENSHOT_AGE_MS = 90L * 24 * 60 * 60 * 1000
    const val LARGE_VIDEO_BYTES = 500L * 1024 * 1024
    private const val BURST_WINDOW_MS = 10L * 60 * 1000
    private const val HASH_DISTANCE = 8

    /** [progress] is called with 0..1 as the slow steps advance. */
    suspend fun scan(context: Context, all: List<MediaItem>, progress: (Float) -> Unit): CleanupResult =
        withContext(Dispatchers.Default) {
            val now = System.currentTimeMillis()
            val library = all.filter { it.id >= 0 }

            progress(0.02f)
            val duplicates = findDuplicates(context, library) { progress(0.02f + it * 0.5f) }
            val removedByDuplicate = duplicates.flatMap { it.removable }.map { it.id }.toHashSet()

            val similar = findSimilar(
                context,
                library.filter { !it.isVideo && it.id !in removedByDuplicate },
            ) { progress(0.52f + it * 0.46f) }

            val screenshots = library
                .filter { !it.isVideo && it.id !in removedByDuplicate && now - it.dateMs > SCREENSHOT_AGE_MS && isScreenshot(it) }
                .sortedBy { it.dateMs }
            val large = library
                .filter { it.isVideo && it.sizeBytes >= LARGE_VIDEO_BYTES }
                .sortedByDescending { it.sizeBytes }

            progress(1f)
            CleanupResult(duplicates, similar, screenshots, large, scanned = true)
        }

    private fun isScreenshot(m: MediaItem): Boolean =
        m.relativePath.contains("screenshot", ignoreCase = true) || m.name.startsWith("Screenshot", ignoreCase = true)

    // ---- exact duplicates ------------------------------------------------------------------

    private suspend fun findDuplicates(context: Context, items: List<MediaItem>, progress: (Float) -> Unit): List<CleanupGroup> {
        val bySize = items.filter { it.sizeBytes > 0 }.groupBy { it.sizeBytes }.filterValues { it.size > 1 }
        val candidates = bySize.values.flatten()
        if (candidates.isEmpty()) return emptyList()
        var done = 0
        val hashes = HashMap<String, String>()
        for (chunk in candidates.chunked(4)) {
            coroutineScope {
                chunk.map { m -> async { m.uri.toString() to hashFile(context, m) } }.awaitAll()
            }.forEach { (key, h) -> if (h != null) hashes[key] = h }
            done += chunk.size
            progress(done.toFloat() / candidates.size)
        }
        return bySize.values.flatMap { sameSize ->
            sameSize.filter { it.uri.toString() in hashes }.groupBy { hashes[it.uri.toString()] }.values.filter { it.size > 1 }.map { g ->
                // Keep the oldest copy; a shorter name usually means "IMG.jpg" rather than "IMG (1).jpg".
                val ordered = g.sortedWith(compareBy({ it.dateMs }, { it.name.length }, { it.id }))
                CleanupGroup(ordered, ordered.first().id)
            }
        }.sortedByDescending { it.reclaimBytes }
    }

    private fun hashFile(context: Context, m: MediaItem): String? = try {
        val md = MessageDigest.getInstance("SHA-1")
        context.contentResolver.openInputStream(m.uri)?.use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        } ?: throw java.io.IOException()
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) { null }

    // ---- similar photos --------------------------------------------------------------------

    private class Print(val hash: Long, val sharpness: Double)

    private suspend fun findSimilar(context: Context, photos: List<MediaItem>, progress: (Float) -> Unit): List<CleanupGroup> {
        val sorted = photos.sortedBy { it.dateMs }
        if (sorted.size < 2) return emptyList()

        // Only photos taken close together can be retakes, so only those get fingerprinted.
        val candidate = BooleanArray(sorted.size)
        for (i in sorted.indices) {
            val near = (i > 0 && sorted[i].dateMs - sorted[i - 1].dateMs <= BURST_WINDOW_MS) ||
                (i < sorted.size - 1 && sorted[i + 1].dateMs - sorted[i].dateMs <= BURST_WINDOW_MS)
            candidate[i] = near
        }
        val todo = sorted.indices.filter { candidate[it] }
        if (todo.isEmpty()) return emptyList()

        val prints = HashMap<Int, Print>()
        var done = 0
        for (chunk in todo.chunked(4)) {
            coroutineScope {
                chunk.map { i -> async { i to fingerprint(context, sorted[i]) } }.awaitAll()
            }.forEach { (i, p) -> if (p != null) prints[i] = p }
            done += chunk.size
            progress(done.toFloat() / todo.size)
        }

        // Union-find over neighbours inside the time window.
        val parent = IntArray(sorted.size) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != r) { val n = parent[c]; parent[c] = r; c = n }
            return r
        }
        for (i in sorted.indices) {
            val pi = prints[i] ?: continue
            var j = i + 1
            while (j < sorted.size && sorted[j].dateMs - sorted[i].dateMs <= BURST_WINDOW_MS) {
                val pj = prints[j]
                if (pj != null && sameShape(sorted[i], sorted[j]) &&
                    java.lang.Long.bitCount(pi.hash xor pj.hash) <= HASH_DISTANCE
                ) {
                    parent[find(j)] = find(i)
                }
                j++
            }
        }
        val groups = HashMap<Int, MutableList<Int>>()
        for (i in sorted.indices) if (prints.containsKey(i)) groups.getOrPut(find(i)) { mutableListOf() }.add(i)

        return groups.values.filter { it.size > 1 }.map { idx ->
            val best = idx.maxWithOrNull(compareBy<Int>({ prints[it]!!.sharpness }, { sorted[it].sizeBytes }))!!
            CleanupGroup(idx.map { sorted[it] }, sorted[best].id)
        }.sortedByDescending { it.reclaimBytes }
    }

    private fun sameShape(a: MediaItem, b: MediaItem): Boolean {
        if (a.width <= 0 || a.height <= 0 || b.width <= 0 || b.height <= 0) return true
        val ra = a.width.toDouble() / a.height
        val rb = b.width.toDouble() / b.height
        return abs(ra - rb) < 0.05
    }

    private fun fingerprint(context: Context, m: MediaItem): Print? {
        val loaded = try {
            context.contentResolver.loadThumbnail(m.uri, Size(192, 192), null)
        } catch (e: Exception) { return null }
        // Hardware bitmaps can't be read pixel by pixel, so work on a software copy.
        val thumb = if (loaded.config == Bitmap.Config.HARDWARE) {
            val copy = loaded.copy(Bitmap.Config.ARGB_8888, false)
            loaded.recycle()
            copy ?: return null
        } else loaded
        return try {
            Print(differenceHash(thumb), sharpness(thumb))
        } finally {
            thumb.recycle()
        }
    }

    /** 64-bit "is this pixel brighter than its right neighbour" fingerprint of a 9x8 grey copy. */
    private fun differenceHash(src: Bitmap): Long {
        val small = Bitmap.createScaledBitmap(src, 9, 8, true)
        val px = IntArray(72)
        small.getPixels(px, 0, 9, 0, 0, 9, 8)
        if (small !== src) small.recycle()
        var h = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (grey(px[y * 9 + x]) > grey(px[y * 9 + x + 1])) h = h or (1L shl bit)
                bit++
            }
        }
        return h
    }

    /** Variance of the Laplacian: higher means more fine detail, i.e. a sharper photo. */
    private fun sharpness(src: Bitmap): Double {
        val w = 96
        val h = (96.0 * src.height / src.width.coerceAtLeast(1)).toInt().coerceIn(32, 192)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()
        val g = IntArray(w * h) { grey(px[it]) }
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val v = (g[(y - 1) * w + x] + g[(y + 1) * w + x] + g[y * w + x - 1] + g[y * w + x + 1] - 4 * g[y * w + x]).toDouble()
                sum += v
                sumSq += v * v
                n++
            }
        }
        if (n == 0) return 0.0
        val mean = sum / n
        return sumSq / n - mean * mean
    }

    private fun grey(c: Int): Int {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }
}
