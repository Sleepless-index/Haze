package sleepless_index.haze.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaRepository(private val context: Context) {

    private val resolver: ContentResolver = context.contentResolver

    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    private var observer: ContentObserver? = null
    private var debounceJob: Job? = null

    suspend fun refresh() {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val list = withContext(Dispatchers.IO) { query(trashed = false) }
        sleepless_index.haze.util.PerfLog.add("Library query: %,d items in %d ms".format(list.size, android.os.SystemClock.elapsedRealtime() - startedAt))
        sleepless_index.haze.util.PerfLog.once("library_ready", "Library ready ${sleepless_index.haze.util.PerfLog.sinceLaunchMs()} ms after launch")
        _items.value = list
        _loaded.value = true
    }

    suspend fun queryTrashed(): List<MediaItem> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 30) query(trashed = true) else emptyList()
    }

    /**
     * Calls [onChange] (debounced, never overlapping) whenever photos or videos change in the
     * system media database. Watches only the image and video tables, so unrelated file churn
     * (downloads, documents, caches) doesn't trigger a re-scan.
     */
    fun startObserving(scope: CoroutineScope, onChange: suspend () -> Unit) {
        if (observer != null) return
        var running = false
        var dirty = false
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                debounceJob?.cancel()
                debounceJob = scope.launch {
                    delay(600)
                    if (running) { dirty = true; return@launch }
                    running = true
                    try {
                        do {
                            dirty = false
                            onChange()
                        } while (dirty)
                    } finally {
                        running = false
                    }
                }
            }
        }
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, obs)
        resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, obs)
        observer = obs
    }

    fun stopObserving() {
        observer?.let { resolver.unregisterContentObserver(it) }
        observer = null
        debounceJob?.cancel()
    }

    private fun query(trashed: Boolean): List<MediaItem> {
        val filesUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.ORIENTATION,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
        ) + (if (Build.VERSION.SDK_INT >= 30) arrayOf(MediaStore.MediaColumns.DATE_EXPIRES) else emptyArray())
        val args = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
                )
            )
            if (Build.VERSION.SDK_INT >= 30) {
                putInt(
                    MediaStore.QUERY_ARG_MATCH_TRASHED,
                    if (trashed) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE
                )
            }
        }

        val out = ArrayList<MediaItem>()
        val cursor = resolver.query(filesUri, projection, args, null) ?: return out
        cursor.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val iMime = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val iType = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val iModified = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val iW = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val iH = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val iDur = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val iOri = c.getColumnIndexOrThrow(MediaStore.MediaColumns.ORIENTATION)
            val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val iBucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val iBucketName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val iPath = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val iExpires = if (Build.VERSION.SDK_INT >= 30) c.getColumnIndex(MediaStore.MediaColumns.DATE_EXPIRES) else -1

            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val isVideo = c.getInt(iType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val taken = c.getLong(iTaken)
                val added = c.getLong(iAdded)
                val modified = c.getLong(iModified)
                val dateMs = when {
                    taken > 0 -> taken
                    added > 0 -> added * 1000
                    else -> modified * 1000
                }
                var w = c.getInt(iW)
                var h = c.getInt(iH)
                val orientation = c.getInt(iOri)
                if (orientation == 90 || orientation == 270) {
                    val t = w; w = h; h = t
                }
                val base = if (isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                out.add(
                    MediaItem(
                        id = id,
                        uri = ContentUris.withAppendedId(base, id),
                        name = c.getString(iName) ?: "",
                        mime = c.getString(iMime) ?: "",
                        isVideo = isVideo,
                        dateMs = dateMs,
                        width = w,
                        height = h,
                        durationMs = c.getLong(iDur),
                        sizeBytes = c.getLong(iSize),
                        bucketId = c.getLong(iBucket),
                        bucketName = c.getString(iBucketName) ?: "",
                        relativePath = c.getString(iPath) ?: "",
                        trashedMs = if (trashed && iExpires >= 0 && !c.isNull(iExpires)) {
                            // The system keeps trash for 30 days; DATE_EXPIRES is that deadline in seconds.
                            c.getLong(iExpires) * 1000 - 30L * 24 * 3600 * 1000
                        } else 0L,
                    )
                )
            }
        }
        out.sortByDescending { it.dateMs }
        return out
    }
}
