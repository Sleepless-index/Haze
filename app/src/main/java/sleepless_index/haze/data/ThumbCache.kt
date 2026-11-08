package sleepless_index.haze.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.provider.MediaStore
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.Scale
import coil.size.pxOrElse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Photo thumbnails that stay sharp AND load fast on every launch after the first.
 *
 * The first time a photo is shown at a given size it is decoded from the ORIGINAL (down-scaled
 * by the system's ImageDecoder, so it is as sharp as before) and saved to our own cache at
 * quality 95. Next launches read that small file instead of re-decoding the full photo.
 *
 * The cache key includes the file's modified time and size, so an edited or replaced photo is
 * never shown stale. Anything unexpected (GIFs, odd formats, errors) returns null and Coil falls
 * back to its normal decode, so a photo can never end up missing or lower quality because of this.
 */
class ThumbCacheFetcher(
    private val context: Context,
    private val uri: Uri,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val reqW = options.size.width.pxOrElse { 512 }
        val reqH = options.size.height.pxOrElse { 512 }
        // Big requests (full-screen viewer) aren't worth caching.
        if (maxOf(reqW, reqH) > 1100) return@withContext null
        // Round up to a multiple of 64: fewer duplicate cache files, never less sharp.
        val w = roundUp(reqW)
        val h = roundUp(reqH)
        val fill = options.scale == Scale.FILL
        val stamp = stampOf(context, uri) ?: return@withContext null

        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val name = sha("v1|$uri|$stamp|$w|$h|$fill")
        val jpg = File(dir, "$name.j")
        val png = File(dir, "$name.p")

        for (f in arrayOf(jpg, png)) {
            if (!f.exists()) continue
            val bmp = BitmapFactory.decodeFile(f.path)
            if (bmp != null) {
                f.setLastModified(System.currentTimeMillis())
                return@withContext result(bmp)
            }
            f.delete() // corrupt: decode again below
        }

        try {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            val bmp = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val sw = info.size.width
                val sh = info.size.height
                val kw = w.toFloat() / sw
                val kh = h.toFloat() / sh
                val k = if (fill) maxOf(kw, kh) else minOf(kw, kh)
                // Only ever shrink: never upscale (that would be blurry).
                if (k < 1f) decoder.setTargetSize(maxOf(1, Math.round(sw * k)), maxOf(1, Math.round(sh * k)))
            }
            val target = if (bmp.hasAlpha()) png else jpg
            val tmp = File(dir, "$name.tmp")
            tmp.outputStream().use { out ->
                if (bmp.hasAlpha()) bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                else bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            if (!tmp.renameTo(target)) tmp.delete()
            result(bmp)
        } catch (e: Throwable) {
            null
        }
    }

    private fun result(bmp: Bitmap): FetchResult = DrawableResult(
        drawable = BitmapDrawable(context.resources, bmp),
        isSampled = true,
        dataSource = DataSource.DISK,
    )

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (options.parameters.value<Boolean>("tcache") != true) return null
            if (data.authority != "media") return null
            return ThumbCacheFetcher(context, data, options)
        }
    }

    companion object {
        const val DIR = "thumbs"
        private const val MAX_BYTES = 400L * 1024 * 1024

        private fun roundUp(v: Int) = ((v.coerceAtLeast(64) + 63) / 64) * 64

        private fun sha(s: String): String =
            MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

        /** "modified-size" of the file, or null for things we leave to Coil (GIFs, no row). */
        private fun stampOf(context: Context, uri: Uri): String? =
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.MIME_TYPE),
                    null, null, null,
                )?.use { c ->
                    if (!c.moveToFirst()) return@use null
                    if (c.getString(2) == "image/gif") null else "${c.getLong(0)}-${c.getLong(1)}"
                }
            } catch (e: Exception) {
                null
            }

        /** Keeps the cache under a size cap by dropping the least recently used files. */
        fun trim(context: Context) {
            val files = File(context.cacheDir, DIR).listFiles() ?: return
            var total = files.sumOf { it.length() }
            if (total <= MAX_BYTES) return
            for (f in files.sortedBy { it.lastModified() }) {
                if (total <= MAX_BYTES * 3 / 4) break
                total -= f.length()
                f.delete()
            }
        }
    }
}
