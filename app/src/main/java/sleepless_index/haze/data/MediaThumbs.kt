package sleepless_index.haze.data

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.ImageRequest
import coil.request.Options
import coil.size.pxOrElse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Uses Android's own thumbnail cache (MediaStore.loadThumbnail) for gallery grids. It is much
 * faster than decoding the full photo or a video frame, so tiles come back instantly when
 * scrolling. Falls through to Coil's normal decoding if the system can't provide a thumbnail.
 */
class MediaThumbFetcher(
    private val context: Context,
    private val uri: Uri,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val w = options.size.width.pxOrElse { 512 }.coerceAtLeast(64)
        val h = options.size.height.pxOrElse { 512 }.coerceAtLeast(64)
        try {
            val bmp = context.contentResolver.loadThumbnail(uri, android.util.Size(w, h), null)
            // Never show a blurry upscaled thumbnail: let Coil decode properly instead.
            if (maxOf(bmp.width, bmp.height) < (maxOf(w, h) * 0.8f).toInt()) return@withContext null
            DrawableResult(
                drawable = BitmapDrawable(context.resources, bmp),
                isSampled = true,
                dataSource = DataSource.DISK,
            )
        } catch (e: Exception) {
            null
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (options.parameters.value<Boolean>("thumb") != true) return null
            if (data.authority != "media") return null
            return MediaThumbFetcher(context, data, options)
        }
    }
}

/** Request for a grid/cover thumbnail. */
fun thumbRequest(context: Context, uri: Uri, px: Int, video: Boolean = false): ImageRequest =
    ImageRequest.Builder(context)
        .data(uri)
        .size(px)
        .apply {
            // Videos: the system thumbnail cache is a win. Photos must NOT use it (it made them
            // look soft); they go through ThumbCacheFetcher, which keeps full sharpness.
            if (video) setParameter("thumb", true) else setParameter("tcache", true)
        }
        .build()

/**
 * Vault files are stored under random names with no extension, so Coil can't tell a video from
 * a photo and never decodes a frame. This reads the frame itself, and steps aside (returns null)
 * for anything that isn't a video.
 */
class VaultVideoFrameFetcher(
    private val context: Context,
    private val uri: Uri,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val path = uri.path ?: return@withContext null
        val w = options.size.width.pxOrElse { 512 }.coerceAtLeast(64)
        val h = options.size.height.pxOrElse { 512 }.coerceAtLeast(64)
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            if (retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") {
                return@withContext null
            }
            val durMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val atUs = minOf(durMs / 2, 1000L) * 1000L
            val frame = retriever.getScaledFrameAtTime(
                atUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, w, h,
            ) ?: return@withContext null
            DrawableResult(
                drawable = BitmapDrawable(context.resources, frame),
                isSampled = true,
                dataSource = DataSource.DISK,
            )
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != "file") return null
            val name = data.lastPathSegment ?: return null
            if (name.contains('.')) return null
            return VaultVideoFrameFetcher(context, data, options)
        }
    }
}
