package sleepless_index.haze.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.coroutineContext

data class UpdateInfo(
    val version: String,
    val notes: List<String>,
    val size: Long,
    val date: String,
    val url: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val done: Long, val total: Long) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Looks for a newer release on GitHub, downloads its APK and hands it to the system installer. */
class Updater(private val app: android.app.Application, private val scope: CoroutineScope) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    private var job: Job? = null

    val currentVersion: String =
        runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "0"

    fun check() {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        job = scope.launch {
            _state.value = withContext(Dispatchers.IO) {
                runCatching { fetchLatest() }.getOrElse { UpdateState.Failed("Couldn't reach GitHub") }
            }
        }
    }

    fun download() {
        val info = (_state.value as? UpdateState.Available)?.info ?: return
        job = scope.launch {
            _state.value = UpdateState.Downloading(info, 0, info.size)
            val result = withContext(Dispatchers.IO) {
                runCatching { fetchApk(info) }.getOrElse {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    UpdateState.Failed("Download failed")
                }
            }
            _state.value = result
        }
    }

    fun cancel() {
        job?.cancel()
        File(app.cacheDir, "updates").listFiles()?.forEach { it.delete() }
        val s = _state.value
        if (s is UpdateState.Downloading) _state.value = UpdateState.Available(s.info)
    }

    /** False if Android first needs the user to allow installs from Haze. */
    fun install(context: Context): Boolean {
        val ready = _state.value as? UpdateState.Ready ?: return true
        if (!app.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.share", ready.file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "Haze")
            setRequestProperty("Accept", "application/vnd.github+json")
        }

    private fun fetchLatest(): UpdateState {
        val c = open("https://api.github.com/repos/Sleepless-index/Haze/releases/latest")
        try {
            if (c.responseCode == 404) return UpdateState.UpToDate
            if (c.responseCode != 200) return UpdateState.Failed("Couldn't reach GitHub")
            val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name").removePrefix("v")
            if (tag.isBlank() || !isNewer(tag, currentVersion)) return UpdateState.UpToDate
            val assets = json.optJSONArray("assets") ?: return UpdateState.UpToDate
            var url = ""
            var size = 0L
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    url = a.optString("browser_download_url")
                    size = a.optLong("size")
                    break
                }
            }
            if (url.isBlank()) return UpdateState.UpToDate
            val date = runCatching {
                OffsetDateTime.parse(json.optString("published_at")).format(DateTimeFormatter.ofPattern("MMM d"))
            }.getOrDefault("")
            return UpdateState.Available(UpdateInfo(tag, parseNotes(json.optString("body")), size, date, url))
        } finally {
            c.disconnect()
        }
    }

    private suspend fun fetchApk(info: UpdateInfo): UpdateState {
        val dir = File(app.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val part = File(dir, "haze-${info.version}.apk.part")
        val out = File(dir, "haze-${info.version}.apk")
        val c = open(info.url)
        try {
            if (c.responseCode != 200) return UpdateState.Failed("Download failed")
            val total = if (c.contentLengthLong > 0) c.contentLengthLong else info.size
            var done = 0L
            var lastEmit = 0L
            c.inputStream.use { input ->
                part.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 100) {
                            lastEmit = now
                            _state.value = UpdateState.Downloading(info, done, total)
                        }
                    }
                }
            }
            if (!part.renameTo(out)) return UpdateState.Failed("Download failed")
        } finally {
            c.disconnect()
            if (part.exists()) part.delete()
        }
        val pkg = app.packageManager.getPackageArchiveInfo(out.absolutePath, 0)?.packageName
        if (pkg != app.packageName) {
            out.delete()
            return UpdateState.Failed("Download was incomplete")
        }
        return UpdateState.Ready(info, out)
    }

    private fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").split('.', '-').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Turns GitHub's generated release notes into a few short lines. */
    private fun parseNotes(body: String): List<String> =
        body.lines()
            .map { it.trim() }
            .filter { it.startsWith("* ") || it.startsWith("- ") }
            .map { it.drop(2).substringBefore(" by @").substringBefore(" in https").trim() }
            .filter { it.isNotBlank() }
            .take(4)
}
