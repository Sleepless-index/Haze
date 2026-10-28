package sleepless_index.haze.ui

import android.app.Application
import android.net.Uri
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

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val g = app as GalleryApp
    private val repo = g.repository
    private val dao = g.db.dao()

    // ---- settings -------------------------------------------------------
    val prefs: StateFlow<Prefs> =
        g.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Prefs())

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

    /** Visible albums with pinned ones first. */
    val albums: StateFlow<List<Album>> = combine(items, prefs.map { it.pinned }.distinctUntilChanged()) { list, pinned ->
        val all = buildAlbums(list)
        all.filter { it.bucketId in pinned } + all.filter { it.bucketId !in pinned }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hiddenAlbums: StateFlow<List<Album>> = combine(repo.items, prefs.map { it.hidden }.distinctUntilChanged()) { list, hidden ->
        if (hidden.isEmpty()) emptyList() else buildAlbums(list.filter { it.bucketId in hidden })
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

    fun refresh() {
        viewModelScope.launch {
            repo.refresh()
            _trashed.value = repo.queryTrashed()
            purgeTrash()
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
        val days = 30
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

    fun setHidden(album: Album, hidden: Boolean) {
        viewModelScope.launch { g.settings.setHidden(album.bucketId, hidden) }
        message(if (hidden) "Hid \"${album.name}\" — find it under Hidden albums" else "\"${album.name}\" is visible again")
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

    fun setViewerList(list: List<MediaItem>) {
        _viewerList.value = list
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

    fun requestTrash(list: List<MediaItem>) {
        if (list.isEmpty()) return
        pendingIds = list.map { it.id }
        _events.tryEmit(UiEvent.Trash(list.map { it.uri }))
    }

    fun requestRestore(list: List<MediaItem>) {
        if (list.isEmpty()) return
        pendingIds = list.map { it.id }
        _events.tryEmit(UiEvent.Restore(list.map { it.uri }))
    }

    fun requestDeleteForever(list: List<MediaItem>) {
        if (list.isEmpty()) return
        pendingIds = list.map { it.id }
        _events.tryEmit(UiEvent.DeleteForever(list.map { it.uri }))
    }

    fun rename(item: MediaItem, newName: String) {
        if (newName == item.name) return
        _events.tryEmit(UiEvent.Rename(item.uri, newName, item.id))
    }

    fun editMeta(item: MediaItem, dateMs: Long?, stripGps: Boolean) {
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
            refresh()
        }
        pendingIds = emptyList()
    }

    // ---- copy / move to album --------------------------------------------
    fun transferToAlbum(list: List<MediaItem>, album: Album, move: Boolean) {
        if (list.isEmpty()) return
        viewModelScope.launch {
            val dir = MediaOps.albumAbsDir(getApplication(), album)
            if (dir == null) message("Couldn't find that album's folder") else doTransfer(list, dir, move)
        }
    }

    fun transferToNew(list: List<MediaItem>, root: String, name: String, move: Boolean) {
        if (list.isEmpty()) return
        val clean = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "New album" }
        viewModelScope.launch { doTransfer(list, "$root/Pictures/$clean", move) }
    }

    private suspend fun doTransfer(list: List<MediaItem>, dir: String, move: Boolean) {
        val app = getApplication<Application>()
        val name = dir.trimEnd('/').substringAfterLast('/')
        val verb = if (move) "Moved" else "Copied"
        if (MediaOps.hasAllFiles()) {
            if (list.size > 2) message(if (move) "Moving…" else "Copying…")
            val n = MediaOps.transfer(app, list, dir, move)
            clearSelection()
            if (move && n > 0) _viewerList.value = _viewerList.value.filter { v -> list.none { it.id == v.id } }
            refresh()
            message(if (n > 0) "$verb $n to $name" else "Couldn't ${if (move) "move" else "copy"} those files")
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
        val done = MediaOps.copyItems(app, list, rel)
        clearSelection()
        message(if (done.isEmpty()) "Couldn't copy those files" else "Copied ${done.size} to $name")
        refresh()
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
            val stored = vaultStore.add(list)
            clearSelection()
            loadVault()
            if (stored.isEmpty()) {
                message("Couldn't move those files to the Vault")
            } else {
                message("Moved ${stored.size} to the Vault. Confirm to remove the originals.")
                // Permanently remove the originals so they don't linger in the gallery or trash.
                requestDeleteForever(stored)
            }
        }
    }

    fun vaultRestore(list: List<MediaItem>) {
        if (list.isEmpty()) return
        viewModelScope.launch {
            val n = vaultStore.restore(list)
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
            vaultStore.delete(list)
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
