package sleepless_index.haze.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.InputStream

object MediaOps {

    /** True when the app has "All files access" and can move files directly. */
    fun hasAllFiles(): Boolean = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()

    fun pathOf(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()

    /** Storage volumes the user can put albums on: internal first, then SD card / USB. */
    fun storageRoots(context: Context): List<Pair<String, String>> {
        val internal = Environment.getExternalStorageDirectory().absolutePath
        val out = mutableListOf("Internal storage" to internal)
        runCatching {
            context.getExternalFilesDirs(null).filterNotNull().forEach { f ->
                val root = f.absolutePath.substringBefore("/Android/data")
                if (root != internal && out.none { it.second == root }) {
                    out += (if (out.size == 1) "SD card" else "Storage ${out.size}") to root
                }
            }
        }
        return out
    }

    /** The folder an existing album really lives in, on whichever volume it is. */
    suspend fun albumAbsDir(context: Context, album: Album): String? = withContext(Dispatchers.IO) {
        pathOf(context, album.cover.uri)?.substringBeforeLast('/')
    }

    /**
     * Moves or copies files into [destDir] (absolute path, any volume). Needs All files access.
     * A move on the same volume is an instant rename; across volumes (internal to SD card) it is
     * copy + delete, only deleting the original after the copy checks out.
     * Returns how many files were transferred.
     */
    suspend fun transfer(context: Context, items: List<MediaItem>, destDir: String, move: Boolean, onProgress: (Int) -> Unit = {}): Int =
        withContext(Dispatchers.IO) {
            val dir = File(destDir).also { it.mkdirs() }
            if (!dir.isDirectory) return@withContext 0
            val touched = ArrayList<String>()
            var done = 0
            for ((index, item) in items.withIndex()) {
                kotlin.coroutines.coroutineContext.ensureActive()
                onProgress(index)
                val src = pathOf(context, item.uri)?.let { File(it) } ?: continue
                if (!src.exists()) continue
                if (move && src.parentFile?.absolutePath == dir.absolutePath) continue
                var dest = File(dir, src.name)
                var n = 1
                while (dest.exists()) {
                    dest = File(dir, "${src.nameWithoutExtension} ($n)" + (if (src.extension.isNotEmpty()) ".${src.extension}" else ""))
                    n++
                }
                val ok = if (move) {
                    src.renameTo(dest) || runCatching {
                        src.copyTo(dest)
                        if (dest.length() == src.length()) src.delete() else { dest.delete(); false }
                    }.getOrDefault(false)
                } else {
                    runCatching { src.copyTo(dest); dest.setLastModified(src.lastModified()); true }.getOrDefault(false)
                }
                if (ok) {
                    done++
                    if (move) touched += src.absolutePath
                    touched += dest.absolutePath
                }
            }
            if (touched.isNotEmpty()) {
                val latch = java.util.concurrent.CountDownLatch(touched.size)
                MediaScannerConnection.scanFile(context, touched.toTypedArray(), null) { _, _ -> latch.countDown() }
                latch.await(15, java.util.concurrent.TimeUnit.SECONDS)
            }
            done
        }

    /** Where a brand-new album folder is created. */
    fun newAlbumDir(name: String): String {
        val clean = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "New album" }
        return "Pictures/$clean/"
    }

    /** Folder an existing album lives in (kept inside Pictures/ or DCIM/, which MediaStore allows). */
    fun albumDir(album: Album): String {
        val rp = album.cover.relativePath
        return if (rp.startsWith("Pictures/") || rp.startsWith("DCIM/")) rp else newAlbumDir(album.name)
    }

    /**
     * Copies items into [relativeDir] as new MediaStore entries. Originals are never touched.
     * Returns the items that were copied successfully.
     */
    suspend fun copyItems(context: Context, items: List<MediaItem>, relativeDir: String, onProgress: (Int) -> Unit = {}): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val done = ArrayList<MediaItem>()
            for ((index, item) in items.withIndex()) {
                onProgress(index)
                val ok = runCatching {
                    context.contentResolver.openInputStream(item.uri)?.use { input ->
                        insert(context, input, item.name, item.mime, item.isVideo, item.dateMs, relativeDir)
                    } != null
                }.getOrDefault(false)
                if (ok) done += item
            }
            done
        }

    /** Writes [input] into MediaStore. Videos fall back to Movies/ if Pictures/ is refused. */
    fun insert(
        context: Context,
        input: InputStream,
        name: String,
        mime: String,
        isVideo: Boolean,
        dateMs: Long,
        relativeDir: String,
    ): Uri? {
        val resolver = context.contentResolver
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val dirs = if (isVideo && relativeDir.startsWith("Pictures/")) {
            listOf(relativeDir, "Movies/" + relativeDir.removePrefix("Pictures/"))
        } else {
            listOf(relativeDir)
        }
        for (dir in dirs) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name.ifBlank { if (isVideo) "video.mp4" else "image.jpg" })
                if (mime.isNotBlank()) put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (dir.endsWith("/")) dir else "$dir/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                if (dateMs > 0) put(MediaStore.MediaColumns.DATE_TAKEN, dateMs)
            }
            val uri = try {
                resolver.insert(collection, values)
            } catch (e: IllegalArgumentException) {
                null
            } ?: continue
            try {
                resolver.openOutputStream(uri)?.use { out -> input.copyTo(out) }
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
                return uri
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                return null
            }
        }
        return null
    }

    /**
     * Renames an album's folder on disk (needs All files access) and re-scans so MediaStore follows.
     * Returns null on success or a short reason for the failure.
     */
    suspend fun renameFolder(context: Context, items: List<MediaItem>, dir: String, newName: String): String? =
        withContext(Dispatchers.IO) {
            val src = File(dir)
            if (!src.isDirectory) return@withContext "Couldn't find that folder"
            val dest = File(src.parentFile, newName)
            if (dest.exists()) return@withContext "A folder named \"$newName\" already exists"
            val oldPaths = items.mapNotNull { pathOf(context, it.uri) }
            if (!src.renameTo(dest)) return@withContext "Couldn't rename the folder"
            val touched = oldPaths + oldPaths.map { dest.absolutePath + "/" + it.substringAfterLast('/') }
            val latch = java.util.concurrent.CountDownLatch(touched.size.coerceAtLeast(1))
            if (touched.isEmpty()) latch.countDown()
            MediaScannerConnection.scanFile(context, touched.toTypedArray(), null) { _, _ -> latch.countDown() }
            latch.await(20, java.util.concurrent.TimeUnit.SECONDS)
            null
        }
}
