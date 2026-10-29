package sleepless_index.haze

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import sleepless_index.haze.data.MediaItem
import sleepless_index.haze.data.Prefs
import sleepless_index.haze.ui.ZoomableImage
import sleepless_index.haze.ui.theme.GalleryTheme

/** Lets other apps open images and videos in this gallery ("Open with"). */
class ExternalViewActivity : ComponentActivity() {

    private val vm: sleepless_index.haze.ui.GalleryViewModel by androidx.activity.viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val uri: Uri? = intent?.data
            ?: if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") intent?.getParcelableExtra(Intent.EXTRA_STREAM)
        if (uri == null) {
            finish()
            return
        }
        val mime = intent?.type ?: contentResolver.getType(uri) ?: ""
        val video = mime.startsWith("video/")
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: ""
        var size = 0L
        runCatching {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) {
                    it.getString(0)?.let { n -> name = n }
                    size = it.getLong(1)
                }
            }
        }
        var w = 0
        var h = 0
        var dur = 0L
        if (video) {
            // Needed so the player knows the orientation and length before it has loaded anything.
            runCatching {
                val r = android.media.MediaMetadataRetriever()
                try {
                    r.setDataSource(this, uri)
                    w = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    h = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    dur = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    val rot = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
                } finally { r.release() }
            }
        }
        val item = MediaItem(
            id = -1L, uri = uri, name = name, mime = mime, isVideo = video,
            dateMs = 0L, width = w, height = h, durationMs = dur, sizeBytes = size,
            bucketId = 0L, bucketName = "", relativePath = "",
        )
        if (video) vm.setViewerList(listOf(item))
        setContent {
            GalleryTheme(Prefs()) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    if (video) sleepless_index.haze.ui.PlayerScreen(vm, -1L, onBack = { finish() }, onOpenVideo = {}) else ZoomableImage(
                        item = item,
                        insetTop = 0.dp,
                        insetBottom = 0.dp,
                        onTap = {},
                        onSwipeDown = { finish() },
                    )
                }
            }
        }
    }
}
