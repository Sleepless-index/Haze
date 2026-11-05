package sleepless_index.haze.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import java.io.File

/**
 * Albums hidden "everywhere" carry a .nomedia file, so the system media library (and every app that
 * reads it) stops listing them. Haze reads those folders straight from disk instead, so the albums
 * still open under Hidden albums.
 */
object HiddenFolders {
    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp", "avif")
    private val videoExt = setOf("mp4", "mkv", "mov", "webm", "3gp", "avi", "m4v")
    private val cache = HashMap<String, MediaItem>()

    /** "bucketId|folder path|album name" */
    fun entry(bucketId: Long, path: String, name: String) = "$bucketId|$path|$name"

    fun parse(entry: String): Triple<Long, String, String>? {
        val p = entry.split("|", limit = 3)
        if (p.size < 3) return null
        val id = p[0].toLongOrNull() ?: return null
        return Triple(id, p[1], p[2])
    }

    fun pathFor(entries: Set<String>, bucketId: Long): String? =
        entries.firstNotNullOfOrNull { e -> parse(e)?.takeIf { it.first == bucketId }?.second }

    /** True when the folder has other folders with pictures in it, which a .nomedia would hide as well. */
    fun hasSubAlbums(dir: File): Boolean =
        dir.listFiles()?.any { sub ->
            sub.isDirectory && !sub.name.startsWith(".") &&
                (sub.listFiles()?.any { it.isFile && it.extension.lowercase() in imageExt + videoExt } == true)
        } == true

    fun mediaFiles(dir: File): List<File> =
        dir.listFiles()?.filter { it.isFile && it.extension.lowercase() in imageExt + videoExt } ?: emptyList()

    @Synchronized
    fun load(context: Context, entries: Set<String>): List<MediaItem> {
        val root = Environment.getExternalStorageDirectory().absolutePath
        val out = ArrayList<MediaItem>()
        for (e in entries) {
            val (bucket, path, name) = parse(e) ?: continue
            for (f in mediaFiles(File(path))) {
                val video = f.extension.lowercase() in videoExt
                val key = f.absolutePath + "|" + f.lastModified() + "|" + f.length()
                out += cache.getOrPut(key) { build(f, bucket, name, video, root) }
            }
        }
        return out
    }

    private fun build(f: File, bucket: Long, name: String, video: Boolean, root: String): MediaItem {
        val ext = f.extension.lowercase()
        var w = 0
        var h = 0
        var dur = 0L
        if (video) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(f.absolutePath)
                w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            } catch (_: Exception) {
            } finally {
                runCatching { r.release() }
            }
        } else {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching { BitmapFactory.decodeFile(f.absolutePath, o) }
            w = o.outWidth.coerceAtLeast(0)
            h = o.outHeight.coerceAtLeast(0)
        }
        val rel = (f.parentFile?.absolutePath ?: "").removePrefix(root).trim('/') + "/"
        return MediaItem(
            id = (1L shl 40) or (f.absolutePath.hashCode().toLong() and 0xFFFFFFFFL),
            uri = Uri.fromFile(f),
            name = f.name,
            mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: if (video) "video/*" else "image/*",
            isVideo = video,
            dateMs = f.lastModified(),
            width = w,
            height = h,
            durationMs = dur,
            sizeBytes = f.length(),
            bucketId = bucket,
            bucketName = name,
            relativePath = rel,
        )
    }
}
