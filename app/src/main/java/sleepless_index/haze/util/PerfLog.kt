package sleepless_index.haze.util

import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A few timing facts about how fast the library loads and scrolls, readable in Settings > About. */
object PerfLog {
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines
    private val seen = HashSet<String>()

    fun sinceLaunchMs(): Long = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()

    fun add(message: String) {
        Log.i("HazePerf", message)
        synchronized(this) { _lines.value = (_lines.value + message).takeLast(30) }
    }

    /** Logs [message] only the first time [key] is seen this launch. */
    fun once(key: String, message: String) {
        val first = synchronized(seen) { seen.add(key) }
        if (first) add(message)
    }
}

/** Counts slow frames while [scrolling] is true and logs a summary when the scroll stops. */
@Composable
fun ScrollJankMonitor(scrolling: Boolean, label: String) {
    DisposableEffect(scrolling, label) {
        if (!scrolling) return@DisposableEffect onDispose { }
        var last = 0L
        var frames = 0
        var slow = 0
        var worst = 0L
        val choreographer = Choreographer.getInstance()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (last != 0L) {
                    val ms = (frameTimeNanos - last) / 1_000_000
                    frames++
                    if (ms > 24) slow++
                    if (ms > worst) worst = ms
                }
                last = frameTimeNanos
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)
        onDispose {
            choreographer.removeFrameCallback(callback)
            if (frames >= 30) PerfLog.add("Scroll ($label): $frames frames, $slow slow (over 24 ms), worst $worst ms")
        }
    }
}
