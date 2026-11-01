package sleepless_index.haze.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

/** Saves one frame of a video as a photo in Pictures/Haze. */
object FrameOps {

    fun save(context: Context, video: Uri, positionMs: Long, baseName: String): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, video)
            val frame = retriever.getFrameAtTime(
                positionMs * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST,
            ) ?: return false
            val name = "${baseName.substringBeforeLast('.').take(60)}_${positionMs / 1000}s.jpg"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Haze")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            val ok = resolver.openOutputStream(target)?.use { frame.compress(Bitmap.CompressFormat.JPEG, 95, it) } == true
            if (ok) {
                resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } else {
                resolver.delete(target, null, null)
            }
            ok
        } catch (_: Exception) {
            false
        } finally {
            runCatching { retriever.release() }
        }
    }
}
