package sleepless_index.haze.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Metadata edits: date taken and GPS removal. Writes throw SecurityException when permission is needed. */
object MetaOps {
    private val gpsTags = listOf(
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_SPEED,
        ExifInterface.TAG_GPS_SPEED_REF, ExifInterface.TAG_GPS_IMG_DIRECTION,
        ExifInterface.TAG_GPS_IMG_DIRECTION_REF, ExifInterface.TAG_GPS_DEST_LATITUDE,
        ExifInterface.TAG_GPS_DEST_LONGITUDE, ExifInterface.TAG_GPS_AREA_INFORMATION,
    )

    fun clearGps(exif: ExifInterface) {
        for (t in gpsTags) exif.setAttribute(t, null)
    }

    fun edit(context: Context, uri: Uri, isVideo: Boolean, dateMs: Long?, stripGps: Boolean) {
        val resolver = context.contentResolver
        if (!isVideo && (dateMs != null || stripGps)) {
            val pfd = try { resolver.openFileDescriptor(uri, "rw") } catch (e: java.io.FileNotFoundException) { null }
            pfd?.use {
                val exif = try { ExifInterface(it.fileDescriptor) } catch (e: Exception) { null }
                if (exif != null) {
                    if (stripGps) clearGps(exif)
                    if (dateMs != null) {
                        val s = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(dateMs))
                        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, s)
                        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, s)
                        exif.setAttribute(ExifInterface.TAG_DATETIME, s)
                    }
                    try { exif.saveAttributes() } catch (e: java.io.IOException) { /* unsupported format: MediaStore date still applies */ }
                }
            }
        }
        if (dateMs != null) {
            val v = ContentValues().apply { put(MediaStore.MediaColumns.DATE_TAKEN, dateMs) }
            resolver.update(uri, v, null, null)
        }
    }

    /** Copies [uri] into the app cache with location removed; returns null if it can't be done. */
    fun strippedCopy(context: Context, uri: Uri, name: String): java.io.File? = try {
        val dir = java.io.File(context.cacheDir, "share").apply { mkdirs() }
        val out = java.io.File(dir, name.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
        val src = try { MediaStore.setRequireOriginal(uri) } catch (e: Exception) { uri }
        val input = try { context.contentResolver.openInputStream(src) } catch (e: SecurityException) { context.contentResolver.openInputStream(uri) }
        input?.use { i -> out.outputStream().use { o -> i.copyTo(o) } } ?: throw java.io.IOException()
        ExifInterface(out.absolutePath).also { clearGps(it); it.saveAttributes() }
        out
    } catch (e: Exception) { null }

    fun hasGps(context: Context, uri: Uri): Boolean = try {
        val src = try { MediaStore.setRequireOriginal(uri) } catch (e: Exception) { uri }
        val input = try { context.contentResolver.openInputStream(src) } catch (e: SecurityException) { null }
        input?.use { ExifInterface(it).latLong != null } ?: false
    } catch (e: Exception) { false }
}
