package sleepless_index.haze.ui

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.File
import sleepless_index.haze.data.HiddenFolders
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import sleepless_index.haze.GalleryApp
import sleepless_index.haze.data.Album
import sleepless_index.haze.data.Entry
import sleepless_index.haze.data.FavoriteEntity
import sleepless_index.haze.data.LayoutMode
import sleepless_index.haze.data.MediaItem
import sleepless_index.haze.data.LoopMode
import sleepless_index.haze.data.Prefs
import sleepless_index.haze.data.MediaOps
import sleepless_index.haze.data.ResumeEntity
import sleepless_index.haze.data.VaultStore
import sleepless_index.haze.data.ThemeMode
import sleepless_index.haze.data.buildAlbums
import sleepless_index.haze.data.buildEntries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One-off requests the Activity has to carry out (they need system dialogs). */
/** What this clean-up session has moved to Trash so far. */
data class CleanupFreed(val ids: List<Long>, val bytes: Long)

sealed interface UiEvent {
    data class Trash(val uris: List<Uri>) : UiEvent
    data class Restore(val uris: List<Uri>) : UiEvent
    data class DeleteForever(val uris: List<Uri>) : UiEvent
    data class Rename(val uri: Uri, val name: String, val id: Long) : UiEvent
    data class EditMeta(val uri: Uri, val id: Long, val isVideo: Boolean, val dateMs: Long?, val stripGps: Boolean) : UiEvent
    data class Message(val text: String) : UiEvent
}

/** A long-running operation shown as a spinner over the whole app. [progress] is 0..1 when known. */
data class Busy(
    val text: String,
    val progress: Float? = null,
    val detail: String? = null,
    val cancellable: Boolean = false,
    val blocking: Boolean = false,
)

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val g = app as GalleryApp
    private val repo = g.repository
    private val dao = g.db.dao()

    // ---- settings -------------------------------------------------------
    val prefs: StateFlow<Prefs> =
        g.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Prefs())

    val updater = sleepless_index.haze.data.Updater(app, viewModelScope)

    fun setPlayerOrientation(v: Int) = viewModelScope.launch { g.settings.setPlayerOrientation(v) }
    fun setTrashDays(v: Int) = viewModelScope.launch { g.settings.setTrashDays(v) }
    fun setLayout(v: LayoutMode) = viewModelScope.launch { g.settings.setLayout(v) }
    fun setColumns(v: Int) = viewModelScope.launch { g.settings.setColumns(v) }
    fun setTheme(v: ThemeMode) = viewModelScope.launch { g.settings.setTheme(v) }
    fun setAccent(v: Int) = viewModelScope.launch { g.settings.setAccent(v) }
    fun setResume(v: Boolean) = viewModelScope.launch { g.settings.setResume(v) }
    fun setAutoplay(v: Boolean) = viewModelScope.launch { g.settings.setAutoplay(v) }
    fun setBlur(v: Boolean) = viewModelScope.launch { g.settings.setBlur(v) }
    fun setLoop(v: LoopMode) = viewModelScope.launch { g.settings.setLoop(v) }
    fun setShuffle(v: Boolean) = viewModelScope.launch { g.settings.setShuffle(v) }
    fun setAutoNext(v: Boolean) = viewModelScope.launch { g.settings.setAutoNext(v) }
    fun setStripLocation(v: Boolean) = viewModelScope.launch { g.settings.setStripLocation(v) }
    fun setVaultLock(v: String) = viewModelScope.launch { g.settings.setVaultLock(v) }
    fun setBgPlay(v: Boolean) = viewModelScope.launch { g.settings.setBgPlay(v) }
    fun setFrameTools(v: Boolean) = viewModelScope.launch { g.settings.setFrameTools(v) }

    // ---- media ----------------------------------------------------------
    /** Everything MediaStore returned, including items in hidden albums. */
    val allItems: StateFlow<List<MediaItem>> = repo.items

    val items: StateFlow<List<MediaItem>> = combine(repo.items, prefs.map { it.hidden }.distinctUntilChanged()) { list, hidden ->
        if (hidden.isEmpty()) list else list.filter { it.bucketId !in hidden }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val loaded: StateFlow<Boolean> = repo.loaded

    val photos: StateFlow<List<MediaItem>> = items

    val photoEntries: StateFlow<List<Entry>> = items
        .map { buildEntries(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Swaps in the cover the user picked, when that photo is still in the album. */
    private fun withCovers(albums: List<Album>, list: List<MediaItem>, covers: Set<String>): List<Album> {
        if (covers.isEmpty()) return albums
        val picked = covers.mapNotNull { c ->
            val p = c.split(":")
            if (p.size == 2) p[0].toLongOrNull()?.let { b -> p[1].toLongOrNull()?.let { i -> b to i } } else null
        }.toMap()
        return albums.map { a ->
            val id = picked[a.bucketId] ?: return@map a
            val m = list.firstOrNull { it.id == id && it.bucketId == a.bucketId }
            if (m != null) a.copy(cover = m) else a
        }
    }

    init {
        // Folders hidden with a .nomedia file are read from disk, so the repository needs to know about them.
        viewModelScope.launch {
            prefs.map { it.hiddenFolders }.distinctUntilChanged().collect { folders ->
                repo.hiddenFolders = folders
                if (folders.isNotEmpty()) repo.refresh()
            }
        }
    }

    /** Visible albums with pinned ones first. */
    val albums: StateFlow<List<Album>> = combine(
        items,
        prefs.map { it.pinned }.distinctUntilChanged(),
        prefs.map { it.covers }.distinctUntilChanged(),
    ) { list, pinned, covers ->
        val all = withCovers(buildAlbums(list), list, covers)
        all.filter { it.bucketId in pinned } + all.filter { it.bucketId !in pinned }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hiddenAlbums: StateFlow<List<Album>> = combine(
        repo.items,
        prefs.map { it.hidden }.distinctUntilChanged(),
        prefs.map { it.covers }.distinctUntilChanged(),
    ) { list, hidden, covers ->
        if (hidden.isEmpty()) emptyList() else {
            val inHidden = list.filter { it.bucketId in hidden }
            withCovers(buildAlbums(inHidden), inHidden, covers)
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val videos: StateFlow<List<MediaItem>> = items
        .map { list -> list.filter { it.isVideo } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favorites: StateFlow<Set<Long>> = dao.observeFavoriteIds()
        .map { it.toHashSet() as Set<Long> }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val favoriteItems: StateFlow<List<MediaItem>> = combine(items, favorites) { list, favs ->
        list.filter { it.id in favs }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _trashed = MutableStateFlow<List<MediaItem>>(emptyList())
    val trashed: StateFlow<List<MediaItem>> = _trashed

    // ---- clean up ----------------------------------------------------------------------------
    private val _cleanup = MutableStateFlow(sleepless_index.haze.data.CleanupResult())
    val cleanup: StateFlow<sleepless_index.haze.data.CleanupResult> = _cleanup
    private val _cleanupProgress = MutableStateFlow<Float?>(null)
    val cleanupProgress: StateFlow<Float?> = _cleanupProgress
    private val _cleanupFreed = MutableStateFlow<CleanupFreed?>(null)
    val cleanupFreed: StateFlow<CleanupFreed?> = _cleanupFreed
    private var lastCleanupScan = 0L

    /** Looks for duplicates and clutter in the background. Cheap to call repeatedly. */
    fun scanCleanup(force: Boolean = false) {
        if (_cleanupProgress.value != null) return
        val fresh = _cleanup.value.scanned && System.currentTimeMillis() - lastCleanupScan < 10 * 60 * 1000L
        if (fresh && !force) return
        _cleanupProgress.value = 0f
        viewModelScope.launch {
            val result = runCatching {
                sleepless_index.haze.data.Cleanup.scan(getApplication<Application>(), items.value) { _cleanupProgress.value = it }
            }.getOrNull()
            if (result != null) {
                _cleanup.value = result
                lastCleanupScan = System.currentTimeMillis()
            }
            _cleanupProgress.value = null
        }
    }

    fun addCleanupFreed(ids: List<Long>, bytes: Long) {
        val cur = _cleanupFreed.value
        _cleanupFreed.value = CleanupFreed((cur?.ids ?: emptyList()) + ids, (cur?.bytes ?: 0L) + bytes)
    }

    fun clearCleanupFreed() { _cleanupFreed.value = null }

    /** Puts back everything the clean-up just moved to Trash. */
    fun undoCleanup() {
        val freed = _cleanupFreed.value ?: return
        requestRestore(_trashed.value.filter { it.id in freed.ids })
        _cleanupFreed.value = null
    }

    private val _busy = MutableStateFlow<Busy?>(null)
    val busy: StateFlow<Busy?> = _busy

    /** Shows (or, with null text, hides) the spinner. Safe to call from any thread. */
    fun setBusy(text: String?, progress: Float? = null, detail: String? = null, cancellable: Boolean = false, blocking: Boolean = false) {
        _busy.value = text?.let { Busy(it, progress, detail, cancellable, blocking) }
    }

    private var busyJob: kotlinx.coroutines.Job? = null

    /** Stops the running move / copy after the file it is on. Whatever was already done stays done. */
    fun cancelBusy() {
        busyJob?.cancel()
    }

    fun refresh(thenClearBusy: Boolean = false) {
        viewModelScope.launch {
            try {
                repo.refresh()
                _trashed.value = repo.queryTrashed()
                purgeTrash()
            } finally {
                if (thenClearBusy) _busy.value = null
            }
        }
    }

    fun loadTrash() {
        viewModelScope.launch {
            _trashed.value = repo.queryTrashed()
            purgeTrash()
        }
    }

    /** Permanently removes items that have sat in the trash longer than the chosen number of days. */
    private suspend fun purgeTrash() {
        val days = prefs.value.trashDays
        if (!MediaOps.hasAllFiles()) return
        val cutoff = System.currentTimeMillis() - days * 24L * 3600 * 1000
        val old = _trashed.value.filter { it.trashedMs in 1 until cutoff }
        if (old.isEmpty()) return
        withContext(Dispatchers.IO) {
            old.forEach { runCatching { getApplication<Application>().contentResolver.delete(it.uri, null, null) } }
        }
        _trashed.value = repo.queryTrashed()
    }

    fun emptyTrash() {
        val all = _trashed.value
        if (all.isEmpty()) message("Trash is already empty") else requestDeleteForever(all)
    }

    // ---- album management -------------------------------------------------
    fun togglePin(album: Album) {
        viewModelScope.launch { g.settings.setPinned(album.bucketId, album.bucketId !in prefs.value.pinned) }
    }

    /**
     * Hiding adds a .nomedia file to the album's folder, so other apps (Discord, other galleries, photo
     * pickers) stop seeing it too. Folders that hold other albums, or that can't be changed, are hidden in Haze only.
     */
    fun setHidden(album: Album, hidden: Boolean) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            if (hidden) {
                val dir = MediaOps.albumAbsDir(app, album)?.let { File(it) }
                var everywhere = false
                if (dir != null && MediaOps.hasAllFiles() && dir.isDirectory) {
                    everywhere = withContext(Dispatchers.IO) {
                        if (HiddenFolders.hasSubAlbums(dir)) false
                        else runCatching { File(dir, ".nomedia").let { it.exists() || it.createNewFile() } }.getOrDefault(false)
                    }
                }
                if (everywhere && dir != null) {
                    g.settings.setHiddenFolder(HiddenFolders.entry(album.bucketId, dir.absolutePath, album.name), album.bucketId, true)
                    repo.hiddenFolders = repo.hiddenFolders.filterNot { it.startsWith("${album.bucketId}|") }.toSet() +
                        HiddenFolders.entry(album.bucketId, dir.absolutePath, album.name)
                }
                g.settings.setHidden(album.bucketId, true)
                if (everywhere && dir != null) {
                    withContext(Dispatchers.IO) {
                        val paths = (HiddenFolders.mediaFiles(dir).map { it.absolutePath } + File(dir, ".nomedia").absolutePath + dir.absolutePath).toTypedArray()
                        scanPaths(app, paths)
                    }
                    delay(1500)
                    repo.refresh()
                    message("Hid \"${album.name}\" from other apps too")
                } else {
                    message("Hid \"${album.name}\" in Haze only")
                }
            } else {
                val folder = HiddenFolders.pathFor(prefs.value.hiddenFolders, album.bucketId)
                g.settings.setHidden(album.bucketId, false)
                if (folder != null) {
                    val dir = File(folder)
                    val files = withContext(Dispatchers.IO) {
                        runCatching { File(dir, ".nomedia").delete() }
                        HiddenFolders.mediaFiles(dir).map { it.absolutePath }
                    }
                    g.settings.setHiddenFolder("", album.bucketId, false)
                    repo.hiddenFolders = repo.hiddenFolders.filterNot { it.startsWith("${album.bucketId}|") }.toSet()
                    withContext(Dispatchers.IO) { scanPaths(app, (files + dir.absolutePath).toTypedArray()) }
                    delay(1500)
                    repo.refresh()
                }
                message("\"${album.name}\" is visible again")
            }
        }
    }

    private suspend fun scanPaths(app: Application, paths: Array<String>) {
        if (paths.isEmpty()) return
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
            var left = paths.size
            android.media.MediaScannerConnection.scanFile(app, paths, null) { _, _ ->
                left--
                if (left <= 0 && cont.isActive) cont.resumeWith(Result.success(Unit))
            }
            // Never wait forever on the scanner.
            Handler(Looper.getMainLooper()).postDelayed({ if (cont.isActive) cont.resumeWith(Result.success(Unit)) }, 8000)
        }
    }

    fun setCover(album: Album, itemId: Long?) {
        viewModelScope.launch {
            g.settings.setCover(album.bucketId, itemId)
            message(if (itemId == null) "Cover reset" else "Cover updated")
        }
    }

    fun renameAlbum(album: Album, newName: String) {
        val clean = newName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (clean.isBlank() || clean == album.name) return
        if (!MediaOps.hasAllFiles()) {
            message("Renaming an album needs All files access (Settings > Permissions)")
            return
        }
        viewModelScope.launch {
            val app = getApplication<Application>()
            val dir = MediaOps.albumAbsDir(app, album)
            if (dir == null) {
                message("Couldn't find that album's folder")
                return@launch
            }
            val list = repo.items.value.filter { it.bucketId == album.bucketId }
            val err = MediaOps.renameFolder(app, list, dir, clean)
            if (err != null) {
                message(err)
            } else {
                // The album gets a new bucket id, so carry pin/hide over is not possible; drop the old ones.
                g.settings.setPinned(album.bucketId, false)
                g.settings.setHidden(album.bucketId, false)
                refresh()
                message("Renamed to $clean")
            }
        }
    }

    fun deleteAlbum(album: Album) {
        val list = repo.items.value.filter { it.bucketId == album.bucketId }
        requestTrash(list)
    }

    /**
     * Called whenever the app comes back to the front. A download or a new photo made while the app
     * was away can be missed by the change observer, so look again, ask the system to index any
     * files it hasn't seen, and look once more after the scan has had a moment.
     */
    fun onReturn() {
        refresh()
        viewModelScope.launch {
            MediaOps.scanRecent(getApplication())
            delay(2000)
            repo.refresh()
        }
    }

    fun startObserving() = repo.startObserving(viewModelScope) {
        repo.refresh()
        _trashed.value = repo.queryTrashed()
    }

    override fun onCleared() {
        repo.stopObserving()
        super.onCleared()
    }

    // ---- selection ------------------------------------------------------
    val selectMode = MutableStateFlow(false)
    val selected = MutableStateFlow<Set<Long>>(emptySet())

    fun startSelect() {
        selectMode.value = true
    }

    fun toggle(id: Long) {
        val cur = selected.value
        val next = if (id in cur) cur - id else cur + id
        selected.value = next
        selectMode.value = next.isNotEmpty()
    }

    fun setSelected(id: Long, on: Boolean) {
        val cur = selected.value
        val next = if (on) cur + id else cur - id
        selected.value = next
        selectMode.value = next.isNotEmpty()
    }

    fun deselectAll() {
        selected.value = emptySet()
    }

    fun selectAll(ids: Collection<Long>) {
        selected.value = ids.toHashSet()
        selectMode.value = ids.isNotEmpty()
    }

    fun clearSelection() {
        selected.value = emptySet()
        selectMode.value = false
    }

    // ---- viewer ---------------------------------------------------------
    private val _viewerList = MutableStateFlow<List<MediaItem>>(emptyList())
    val viewerList: StateFlow<List<MediaItem>> = _viewerList

    /** Which library items belong in the open viewer's list, when that can be worked out (null = fixed list). */
    private var viewerScope: ((MediaItem) -> Boolean)? = null

    fun setViewerList(list: List<MediaItem>) {
        _viewerList.value = list
        viewerScope = inferViewerScope(list)
    }

    /** Recognises lists that are "everything", "all videos", "all photos" or "one album", so new files can join them. */
    private fun inferViewerScope(list: List<MediaItem>): ((MediaItem) -> Boolean)? {
        if (list.isEmpty() || list.any { it.id < 0 }) return null
        val lib = items.value
        val ids = list.mapTo(HashSet()) { it.id }
        val bucket = list[0].bucketId
        val candidates = listOf<(MediaItem) -> Boolean>(
            { true },
            { it.isVideo },
            { !it.isVideo },
            { it.bucketId == bucket },
        )
        return candidates.firstOrNull { p -> lib.count(p) == ids.size && lib.all { !p(it) || it.id in ids } }
    }

    init {
        // A photo downloaded while the viewer is open joins the list, so there's no need to leave and come back.
        viewModelScope.launch {
            items.collect { lib ->
                val scope = viewerScope ?: return@collect
                val cur = _viewerList.value
                if (cur.isEmpty()) return@collect
                val present = lib.mapTo(HashSet()) { it.id }
                val have = cur.mapTo(HashSet()) { it.id }
                val kept = cur.filter { it.id in present }
                val fresh = lib.filter { scope(it) && it.id !in have }
                if (fresh.isEmpty() && kept.size == cur.size) return@collect
                val out = kept.toMutableList()
                for (m in fresh) {
                    val at = out.indexOfFirst { it.dateMs <= m.dateMs }
                    if (at < 0) out.add(m) else out.add(at, m)
                }
                _viewerList.value = out
            }
        }
    }

    fun findItem(id: Long): MediaItem? =
        _viewerList.value.firstOrNull { it.id == id }
            ?: items.value.firstOrNull { it.id == id }
            ?: _trashed.value.firstOrNull { it.id == id }
            ?: _vault.value.firstOrNull { it.id == id }

    // ---- favorites ------------------------------------------------------
    fun toggleFavorite(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val favs = favorites.value
        val allFav = ids.all { it in favs }
        viewModelScope.launch(Dispatchers.IO) {
            if (allFav) {
                ids.chunked(500).forEach { dao.removeFavorites(it) }
            } else {
                dao.addFavorites(ids.map { FavoriteEntity(it) })
            }
        }
    }

    // ---- delete / trash -------------------------------------------------
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events

    private var pendingIds: List<Long> = emptyList()

    /** Files of an album hidden from other apps aren't in the media library, so the system can't trash or edit them. */
    private fun needsShowFirst(list: List<MediaItem>): Boolean {
        val blocked = list.any { it.uri.scheme == "file" && it.id >= 0 }
        if (blocked) message("Show the album again first")
        return blocked
    }

    fun requestTrash(list: List<MediaItem>) {
        if (list.isEmpty() || needsShowFirst(list)) return
        pendingIds = list.map { it.id }
        _events.tryEmit(UiEvent.Trash(list.map { it.uri }))
    }

    fun requestRestore(list: List<MediaItem>) {
        if (list.isEmpty() || needsShowFirst(list)) return
        pendingIds = list.map { it.id }
        _events.tryEmit(UiEvent.Restore(list.map { it.uri }))
    }

    fun requestDeleteForever(list: List<MediaItem>) {
        if (list.isEmpty() || needsShowFirst(list)) return
        pendingIds = list.map { it.id }
        _events.tryEmit(UiEvent.DeleteForever(list.map { it.uri }))
    }

    fun rename(item: MediaItem, newName: String) {
        if (newName == item.name || needsShowFirst(listOf(item))) return
        _events.tryEmit(UiEvent.Rename(item.uri, newName, item.id))
    }

    fun editMeta(item: MediaItem, dateMs: Long?, stripGps: Boolean) {
        if (needsShowFirst(listOf(item))) return
        _events.tryEmit(UiEvent.EditMeta(item.uri, item.id, item.isVideo, dateMs, stripGps))
    }

    fun onRenamed(id: Long, newName: String) {
        _viewerList.value = _viewerList.value.map { if (it.id == id) it.copy(name = newName) else it }
        refresh()
    }

    fun onSystemDialogResult(ok: Boolean) {
        if (ok) {
            val gone = pendingIds.toHashSet()
            _viewerList.value = _viewerList.value.filter { it.id !in gone }
            clearSelection()
            setBusy("Updating your library")
            refresh(thenClearBusy = true)
        } else {
            _busy.value = null
        }
        pendingIds = emptyList()
    }

    fun countText(n: Int) = if (n == 1) "1 item" else "$n items"

    // ---- copy / move to album --------------------------------------------
    fun transferToAlbum(list: List<MediaItem>, album: Album, move: Boolean) {
        if (list.isEmpty()) return
        if (_busy.value != null) { message("Wait for the current job to finish"); return }
        busyJob = viewModelScope.launch {
            val dir = MediaOps.albumAbsDir(getApplication(), album)
            if (dir == null) message("Couldn't find that album's folder") else doTransfer(list, dir, move)
        }
    }

    fun transferToNew(list: List<MediaItem>, root: String, name: String, move: Boolean) {
        if (list.isEmpty()) return
        val clean = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "New album" }
        if (_busy.value != null) { message("Wait for the current job to finish"); return }
        busyJob = viewModelScope.launch { doTransfer(list, "$root/Pictures/$clean", move) }
    }

    private suspend fun doTransfer(list: List<MediaItem>, dir: String, move: Boolean) {
        val app = getApplication<Application>()
        val name = dir.trimEnd('/').substringAfterLast('/')
        val verb = if (move) "Moved" else "Copied"
        if (MediaOps.hasAllFiles()) {
            val label = (if (move) "Moving " else "Copying ") + countText(list.size)
            val detail = "to $name"
            setBusy(label, 0f, detail, cancellable = true)
            var cancelled = false
            val n = try {
                MediaOps.transfer(app, list, dir, move) { done -> setBusy(label, done / list.size.toFloat(), detail, cancellable = true) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelled = true
                0
            } finally {
                setBusy("Updating your library")
            }
            clearSelection()
            if (move && n > 0) _viewerList.value = _viewerList.value.filter { v -> list.none { it.id == v.id } }
            refresh(thenClearBusy = true)
            message(
                when {
                    cancelled -> "Stopped. Anything already done stays done."
                    n > 0 -> "$verb $n to $name"
                    else -> "Couldn't ${if (move) "move" else "copy"} those files"
                },
            )
            return
        }
        // Without All files access only copying into internal Pictures/ DCIM is possible.
        if (move) {
            message("Moving needs All files access (Settings > Permissions)")
            return
        }
        val internal = android.os.Environment.getExternalStorageDirectory().absolutePath
        if (!dir.startsWith("$internal/")) {
            message("The SD card needs All files access (Settings > Permissions)")
            return
        }
        val rel = dir.removePrefix("$internal/").trimEnd('/') + "/"
        val label = "Copying " + countText(list.size)
        setBusy(label, 0f)
        val done = try {
            MediaOps.copyItems(app, list, rel) { d -> setBusy(label, d / list.size.toFloat()) }
        } finally {
            setBusy("Updating your library")
        }
        clearSelection()
        message(if (done.isEmpty()) "Couldn't copy those files" else "Copied ${done.size} to $name")
        refresh(thenClearBusy = true)
    }

    // ---- vault ----------------------------------------------------------
    private val vaultStore = VaultStore(app)
    private val _vault = MutableStateFlow<List<MediaItem>>(emptyList())
    val vault: StateFlow<List<MediaItem>> = _vault
    val vaultUnlocked = MutableStateFlow(false)

    fun unlockVault() {
        vaultUnlocked.value = true
        loadVault()
    }

    fun lockVault() {
        vaultUnlocked.value = false
    }

    fun loadVault() {
        viewModelScope.launch { _vault.value = vaultStore.list() }
    }

    fun moveToVault(list: List<MediaItem>) {
        if (list.isEmpty()) return
        viewModelScope.launch {
            setBusy("Moving ${countText(list.size)} to the Vault")
            val stored = try { vaultStore.add(list) } finally { _busy.value = null }
            clearSelection()
            loadVault()
            if (stored.isEmpty()) {
                message("Couldn't move those files to the Vault")
            } else {
                // With All files access the originals are removed right away, with no prompt to wait for.
                message(
                    if (sleepless_index.haze.data.MediaOps.hasAllFiles()) "Moved ${stored.size} to the Vault"
                    else "Moved ${stored.size} to the Vault. Confirm to remove the originals."
                )
                // Permanently remove the originals so they don't linger in the gallery or trash.
                requestDeleteForever(stored)
            }
        }
    }

    fun vaultRestore(list: List<MediaItem>) {
        if (list.isEmpty()) return
        viewModelScope.launch {
            setBusy("Restoring ${countText(list.size)}")
            val n = try { vaultStore.restore(list) } finally { _busy.value = null }
            clearSelection()
            _viewerList.value = _viewerList.value.filter { v -> list.none { it.id == v.id } }
            loadVault()
            refresh()
            message(if (n > 0) "Restored $n to your gallery" else "Couldn't restore those files")
        }
    }

    fun vaultDelete(list: List<MediaItem>) {
        if (list.isEmpty()) return
        viewModelScope.launch {
            setBusy("Deleting ${countText(list.size)}")
            try { vaultStore.delete(list) } finally { _busy.value = null }
            clearSelection()
            _viewerList.value = _viewerList.value.filter { v -> list.none { it.id == v.id } }
            loadVault()
        }
    }

    // ---- video resume ---------------------------------------------------
    suspend fun resumePosition(id: Long): Long? = dao.resumePosition(id)

    fun saveResume(id: Long, positionMs: Long, durationMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val nearEnd = durationMs > 0 && positionMs > durationMs - 4000
            if (positionMs < 3000 || nearEnd) dao.clearResume(id) else dao.saveResume(ResumeEntity(id, positionMs))
        }
    }

    fun message(text: String) {
        _events.tryEmit(UiEvent.Message(text))
    }
}
