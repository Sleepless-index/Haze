package sleepless_index.haze.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sets a photo as the wallpaper right here, so the home / lock choice is always honoured. */
object WallpaperOps {
    const val HOME = WallpaperManager.FLAG_SYSTEM
    const val LOCK = WallpaperManager.FLAG_LOCK
    const val BOTH = WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK

    /** [which] is [HOME], [LOCK] or [BOTH]. The photo is centre-cropped to the screen's shape. */
    suspend fun set(context: Context, item: MediaItem, which: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val dm = context.resources.displayMetrics
            val sw = dm.widthPixels
            val sh = dm.heightPixels
            val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, item.uri)) { dec, info, _ ->
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val iw = info.size.width
                val ih = info.size.height
                // Just big enough to cover the screen; never blow a small photo up here.
                val scale = minOf(1f, maxOf(sw / iw.toFloat(), sh / ih.toFloat()))
                if (scale < 1f) dec.setTargetSize((iw * scale).toInt().coerceAtLeast(1), (ih * scale).toInt().coerceAtLeast(1))
            }
            val screenAspect = sw.toFloat() / sh
            val aspect = decoded.width.toFloat() / decoded.height
            val cw = if (aspect > screenAspect) (decoded.height * screenAspect).toInt() else decoded.width
            val ch = if (aspect > screenAspect) decoded.height else (decoded.width / screenAspect).toInt()
            val cropped = Bitmap.createBitmap(
                decoded, (decoded.width - cw) / 2, (decoded.height - ch) / 2,
                cw.coerceAtLeast(1), ch.coerceAtLeast(1),
            )
            WallpaperManager.getInstance(context).setBitmap(cropped, null, true, which)
            true
        }.getOrDefault(false)
    }
}
