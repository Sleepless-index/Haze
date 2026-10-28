package sleepless_index.haze.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class LayoutMode { GRID, MASONRY }

enum class ThemeMode { AMOLED, DARK, LIGHT }

/** Repeat behaviour of the player queue. */
enum class LoopMode { OFF, ONCE, REPEAT }


data class Prefs(
    val layout: LayoutMode = LayoutMode.GRID,
    val columns: Int = 3,
    val theme: ThemeMode = ThemeMode.AMOLED,
    val accent: Int = ACCENT_BLUE,
    val resume: Boolean = true,
    val autoplay: Boolean = true,
    val blur: Boolean = true,
    val loop: LoopMode = LoopMode.OFF,
    val shuffle: Boolean = false,
    val autoNext: Boolean = false,
    val bgPlay: Boolean = false,
    val trashDays: Int = 30,
    val pinned: Set<Long> = emptySet(),
    val hidden: Set<Long> = emptySet(),
    val vaultLock: String = "",
    val stripLocation: Boolean = true,
) {
    companion object {
        const val ACCENT_LIME = 0xFFD4FF3A.toInt()
        const val ACCENT_BLUE = 0xFF4C8DFF.toInt()
        const val ACCENT_PINK = 0xFFFF5FA2.toInt()
        const val ACCENT_WHITE = 0xFFF4F4F5.toInt()
        const val ACCENT_SKY = 0xFF5CC8FF.toInt()
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    private val kLayout = stringPreferencesKey("layout")
    private val kColumns = intPreferencesKey("columns")
    private val kTheme = stringPreferencesKey("theme")
    private val kAccent = intPreferencesKey("accent")
    private val kResume = booleanPreferencesKey("resume")
    private val kAutoplay = booleanPreferencesKey("autoplay")
    private val kBlur = booleanPreferencesKey("blur")
    private val kLoop = stringPreferencesKey("loopMode")
    private val kShuffle = booleanPreferencesKey("shuffle")
    private val kAutoNext = booleanPreferencesKey("autoNext")
    private val kBgPlay = booleanPreferencesKey("bgPlay")
    private val kTrashDays = intPreferencesKey("trashDays")
    private val kPinned = stringSetPreferencesKey("pinnedAlbums")
    private val kHidden = stringSetPreferencesKey("hiddenAlbums")
    private val kVaultLock = stringPreferencesKey("vaultLock")
    private val kStripLocation = booleanPreferencesKey("stripLocation")

    val flow: Flow<Prefs> = context.dataStore.data.map { p ->
        Prefs(
            layout = runCatching { LayoutMode.valueOf(p[kLayout] ?: "GRID") }.getOrDefault(LayoutMode.GRID),
            columns = (p[kColumns] ?: 3).coerceIn(2, 6),
            theme = runCatching { ThemeMode.valueOf(p[kTheme] ?: "AMOLED") }.getOrDefault(ThemeMode.AMOLED),
            accent = p[kAccent] ?: Prefs.ACCENT_BLUE,
            resume = p[kResume] ?: true,
            autoplay = p[kAutoplay] ?: true,
            blur = p[kBlur] ?: true,
            loop = runCatching { LoopMode.valueOf(p[kLoop] ?: "OFF") }.getOrDefault(LoopMode.OFF),
            shuffle = p[kShuffle] ?: false,
            autoNext = p[kAutoNext] ?: false,
            bgPlay = p[kBgPlay] ?: false,
            trashDays = p[kTrashDays] ?: 30,
            pinned = (p[kPinned] ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet(),
            hidden = (p[kHidden] ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet(),
            vaultLock = p[kVaultLock] ?: "",
            stripLocation = p[kStripLocation] ?: true,
        )
    }

    suspend fun setLayout(v: LayoutMode) { context.dataStore.edit { it[kLayout] = v.name } }
    suspend fun setColumns(v: Int) { context.dataStore.edit { it[kColumns] = v.coerceIn(2, 6) } }
    suspend fun setTheme(v: ThemeMode) { context.dataStore.edit { it[kTheme] = v.name } }
    suspend fun setAccent(v: Int) { context.dataStore.edit { it[kAccent] = v } }
    suspend fun setResume(v: Boolean) { context.dataStore.edit { it[kResume] = v } }
    suspend fun setAutoplay(v: Boolean) { context.dataStore.edit { it[kAutoplay] = v } }
    suspend fun setBlur(v: Boolean) { context.dataStore.edit { it[kBlur] = v } }

    suspend fun setLoop(v: LoopMode) { context.dataStore.edit { it[kLoop] = v.name } }
    suspend fun setShuffle(v: Boolean) { context.dataStore.edit { it[kShuffle] = v } }
    suspend fun setAutoNext(v: Boolean) { context.dataStore.edit { it[kAutoNext] = v } }
    suspend fun setBgPlay(v: Boolean) { context.dataStore.edit { it[kBgPlay] = v } }
    suspend fun setTrashDays(v: Int) { context.dataStore.edit { it[kTrashDays] = v } }

    suspend fun setStripLocation(v: Boolean) { context.dataStore.edit { it[kStripLocation] = v } }
    suspend fun setVaultLock(v: String) { context.dataStore.edit { it[kVaultLock] = v } }

    suspend fun setPinned(id: Long, on: Boolean) {
        context.dataStore.edit { p ->
            val cur = p[kPinned] ?: emptySet()
            p[kPinned] = if (on) cur + id.toString() else cur - id.toString()
        }
    }

    suspend fun setHidden(id: Long, on: Boolean) {
        context.dataStore.edit { p ->
            val cur = p[kHidden] ?: emptySet()
            p[kHidden] = if (on) cur + id.toString() else cur - id.toString()
        }
    }
}
