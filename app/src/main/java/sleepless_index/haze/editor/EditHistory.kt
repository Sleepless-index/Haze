package sleepless_index.haze.editor

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import sleepless_index.haze.ui.GIconButton
import sleepless_index.haze.ui.theme.GIcons

/** Snapshot-based undo/redo. [T] must be a value type (data class) so equal states compare equal. */
class EditHistory<T>(initial: T, private val read: () -> T, private val write: (T) -> Unit) {
    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()
    private var current: T = initial
    private var version by mutableIntStateOf(0)

    val canUndo: Boolean get() { version; return undoStack.isNotEmpty() || read() != current }
    val canRedo: Boolean get() { version; return redoStack.isNotEmpty() }

    /** Records the live state if it changed since the last commit. */
    fun commit() {
        val live = read()
        if (live == current) return
        undoStack.addLast(current)
        if (undoStack.size > 100) undoStack.removeFirst()
        current = live
        redoStack.clear()
        version++
    }

    fun undo() {
        commit()
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(current)
        current = prev
        write(prev)
        version++
    }

    fun redo() {
        commit()
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(current)
        current = next
        write(next)
        version++
    }

    /** Forget everything and treat the live state as the new baseline (used after Reset-less rebases). */
    fun rebase() {
        undoStack.clear(); redoStack.clear(); current = read(); version++
    }
}

@OptIn(FlowPreview::class)
@Composable
fun <T> rememberEditHistory(read: () -> T, write: (T) -> Unit): EditHistory<T> {
    val readState = rememberUpdatedState(read)
    val writeState = rememberUpdatedState(write)
    val history = remember { EditHistory(read(), { readState.value() }, { writeState.value(it) }) }
    LaunchedEffect(history) {
        snapshotFlow { readState.value() }.debounce(350).collect { history.commit() }
    }
    return history
}

@Composable
fun UndoRedo(history: EditHistory<*>) {
    Row {
        GIconButton(
            GIcons.Undo, "Undo", onClick = { history.undo() }, flat = true,
            modifier = Modifier.alpha(if (history.canUndo) 1f else 0.35f),
        )
        GIconButton(
            GIcons.Redo, "Redo", onClick = { history.redo() }, flat = true,
            modifier = Modifier.alpha(if (history.canRedo) 1f else 0.35f),
        )
    }
}
