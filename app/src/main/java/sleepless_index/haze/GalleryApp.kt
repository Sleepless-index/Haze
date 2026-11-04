package sleepless_index.haze

import android.app.Application
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import sleepless_index.haze.data.AppDatabase
import sleepless_index.haze.data.MediaRepository
import sleepless_index.haze.data.SettingsStore

class GalleryApp : Application(), ImageLoaderFactory {

    lateinit var db: AppDatabase
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var repository: MediaRepository
        private set

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(this, AppDatabase::class.java, "gallery.db").build()
        settings = SettingsStore(this)
        repository = MediaRepository(this)
    }

    override fun newImageLoader(): ImageLoader {
        val app = this
        return ImageLoader.Builder(this)
            .components {
                add(sleepless_index.haze.data.MediaThumbFetcher.Factory(app))
                add(sleepless_index.haze.data.VaultVideoFrameFetcher.Factory(app))
                add(VideoFrameDecoder.Factory())
                add(ImageDecoderDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil"))
                    .maxSizePercent(0.03)
                    .maximumMaxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            .crossfade(140)
            .build()
    }
}
